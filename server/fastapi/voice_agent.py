import asyncio
import os
import json
import logging
import requests
import httpx
import quopri
import psycopg2
from rapidfuzz import process, fuzz
from mcp import ClientSession
from mcp.client.sse import sse_client

from livekit.agents import JobContext, WorkerOptions, cli, llm
try:
    from livekit.agents.pipeline import VoicePipelineAgent
    HAS_PIPELINE = True
except (ImportError, ModuleNotFoundError):
    try:
        from livekit.agents.voice import VoicePipelineAgent
        HAS_PIPELINE = True
    except (ImportError, ModuleNotFoundError):
        VoicePipelineAgent = None
        HAS_PIPELINE = False

try:
    from livekit.agents.voice import AgentSession
except (ImportError, ModuleNotFoundError):
    try:
        from livekit.agents import AgentSession
    except (ImportError, ModuleNotFoundError):
        AgentSession = None

from livekit.plugins import google, silero

try:
    from livekit.plugins import groq
    HAS_GROQ_PLUGIN = True
except ImportError:
    HAS_GROQ_PLUGIN = False

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ZyraVoiceAgent")

MEMORY_FILE = os.path.join(os.path.dirname(__file__), "memory.json")

def load_memory():
    if os.path.exists(MEMORY_FILE):
        try:
            with open(MEMORY_FILE, "r") as f:
                return json.load(f)
        except Exception:
            return []
    return []

def save_memory(history):
    try:
        with open(MEMORY_FILE, "w") as f:
            json.dump(history[-20:], f)
    except Exception as e:
        logger.warning(f"Memory save error: {e}")

def get_all_contacts():
    """
    Retrieves contacts from both PostgreSQL (zyra_contacts) and local my_contacts.vcf.
    """
    contacts_list = []
    
    # 1. Fetch from PostgreSQL if available
    try:
        db_user = os.getenv("POSTGRES_USER", "postgres")
        db_password = os.getenv("POSTGRES_PASSWORD", "password")
        db_name = os.getenv("POSTGRES_DB", "postgres")
        db_host = os.getenv("POSTGRES_HOST", "postgres")
        db_port = int(os.getenv("POSTGRES_PORT", "5432"))
        if db_host.startswith("http"):
            db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]

        conn = psycopg2.connect(
            dbname=db_name,
            user=db_user,
            password=db_password,
            host=db_host,
            port=db_port,
            connect_timeout=2
        )
        with conn.cursor() as cur:
            cur.execute("SELECT name, phone, COALESCE(alias, '') FROM zyra_contacts;")
            for row in cur.fetchall():
                contacts_list.append((row[0], row[1], row[2]))
        conn.close()
    except Exception as e:
        logger.debug(f"DB contacts lookup skipped: {e}")

    # 2. Parse my_contacts.vcf fallback
    vcf_path = os.path.join(os.path.dirname(__file__), 'my_contacts.vcf')
    if os.path.exists(vcf_path):
        try:
            with open(vcf_path, 'r', encoding='utf-8', errors='ignore') as f:
                current_name = ""
                current_phone = ""
                for line in f:
                    line = line.strip()
                    if line == "BEGIN:VCARD":
                        current_name = ""
                        current_phone = ""
                    elif line.startswith("FN:") or line.startswith("FN;"):
                        parts = line.split(":", 1)
                        if len(parts) > 1:
                            raw_name = parts[1].strip()
                            if "QUOTED-PRINTABLE" in line:
                                try:
                                    raw_name = quopri.decodestring(raw_name.encode('utf-8')).decode('utf-8', errors='ignore')
                                except Exception:
                                    pass
                            current_name = raw_name
                    elif line.startswith("TEL:") or line.startswith("TEL;"):
                        parts = line.split(":", 1)
                        if len(parts) > 1:
                            current_phone = parts[1].strip()
                    elif line == "END:VCARD":
                        if current_name and current_phone:
                            contacts_list.append((current_name, current_phone, ''))
        except Exception as e:
            logger.warning(f"Error parsing VCF: {e}")

    # Deduplicate contacts by (name, phone)
    unique_contacts = []
    seen = set()
    for name, phone, alias in contacts_list:
        key = (name.lower(), phone)
        if key not in seen:
            seen.add(key)
            unique_contacts.append((name, phone, alias))

    return unique_contacts

pending_calls = {}

class AssistantFnc(llm.FunctionContext):
    def __init__(self, room=None, mcp_session=None):
        super().__init__()
        self.room = room
        self.mcp_session = mcp_session

    @llm.ai_callable(desc="Executes a tool from the MetaMCP hub (like Search, Web Fetch, Spotify, or News).")
    async def call_mcp_tool(self, tool_name: str, arguments_json: str):
        if not self.mcp_session:
            return "Sir, the MetaMCP hub is currently offline."
        try:
            logger.info(f"Agent executing MetaMCP tool: {tool_name} with {arguments_json}")
            args = json.loads(arguments_json)
            result = await self.mcp_session.call_tool(tool_name, args)
            decoded_result = ""
            for item in result.content:
                if hasattr(item, 'text'):
                    decoded_result += item.text
                elif isinstance(item, dict) and 'text' in item:
                    decoded_result += item['text']
                else:
                    decoded_result += str(item)
            return f"Result for {tool_name}: {decoded_result}"
        except Exception as e:
            logger.error(f"MetaMCP Tool Error: {e}")
            return f"I encountered an error executing {tool_name}, Sir."

    @llm.ai_callable(desc="Searches contacts and asks for confirmation before initiating a call.")
    async def initiate_call_sequence(self, name_query: str):
        logger.info(f"Agent asking to call: {name_query}")
        try:
            contacts = get_all_contacts()
            if not contacts:
                return "I don't have any contacts synchronized in your database or VCF, Sir."

            search_fields = []
            for name, phone, alias in contacts:
                search_fields.append((name, phone, name))
                if alias:
                    search_fields.append((alias, phone, name))

            choices = [field[0] for field in search_fields]
            matches = process.extract(name_query, choices, scorer=fuzz.WRatio, limit=3)
            valid_matches = [m for m in matches if m[1] > 55]

            if not valid_matches:
                return f"I couldn't find a matching contact for {name_query}, Sir."

            top_score = valid_matches[0][1]
            ambiguous_matches = [m for m in valid_matches if (top_score - m[1]) <= 3]
            unique_ambiguous = list(set([m[0] for m in ambiguous_matches]))

            if len(unique_ambiguous) > 1:
                options = ", and ".join(unique_ambiguous)
                return f"I found multiple matching contacts: {options}. Which specific one would you like?"
            else:
                matched_str = unique_ambiguous[0]
                matched_contact = next(field for field in search_fields if field[0] == matched_str)
                _, phone, display_name = matched_contact

                pending_calls["target"] = {
                    "name": display_name,
                    "phone": phone
                }
                logger.info(f"Fuzzy Matched '{name_query}' -> '{matched_str}' ({phone})")
                return f"I've found {display_name}. Shall I proceed with the call, Sir?"
        except Exception as e:
            logger.error(f"Contact search error: {e}")
            return "I encountered an issue looking up your contacts, Sir."

    @llm.ai_callable(desc="Handles user confirmation (Yes/No) to proceed with the pending call.")
    async def handle_user_confirmation(self, confirmed: bool):
        if "target" not in pending_calls:
            return "There is no pending call awaiting approval, Sir."
        pending = pending_calls.pop("target")

        if confirmed:
            if hasattr(self, 'room') and self.room:
                payload = json.dumps({
                    "action": "ACTION_CALL",
                    "phone": pending["phone"],
                    "name": pending["name"]
                }).encode("utf-8")
                await self.room.local_participant.publish_data(payload)
            return f"Connecting you to {pending['name']} right away, Sir."
        else:
            return "Canceled. What else can I do for you, Sir?"

    @llm.ai_callable(desc="Ends or hangs up the currently active phone call.")
    async def end_active_call(self):
        logger.info("Agent issuing command to hang up call...")
        try:
            command_payload = json.dumps({"action": "ACTION_HANGUP"}).encode('utf-8')
            if hasattr(self, 'room') and self.room:
                await self.room.local_participant.publish_data(command_payload)
            return "I've ended the call for you, Sir."
        except Exception as e:
            logger.error(f"Failed to push hangup command: {e}")
            return "I encountered an error trying to hang up the call, Sir."

    @llm.ai_callable(desc="Sends a WhatsApp message to a recipient using the Evolution API.")
    async def send_whatsapp_message(self, target_name: str, text_message: str):
        logger.info(f"Agent asking to WhatsApp {target_name}: {text_message}")
        try:
            contacts = get_all_contacts()
            if not contacts:
                return "I don't have any contacts available to send a message, Sir."

            search_fields = [(c[0], c[1], c[0]) for c in contacts]
            choices = [f[0] for f in search_fields]
            matches = process.extract(target_name, choices, scorer=fuzz.WRatio, limit=3)
            valid_matches = [m for m in matches if m[1] > 55]

            if not valid_matches:
                return f"I couldn't find a matching contact for {target_name}, Sir."

            top_score = valid_matches[0][1]
            matched_str = valid_matches[0][0]
            matched_contact = next(field for field in search_fields if field[0] == matched_str)
            _, phone, display_name = matched_contact

            formatted_phone = ''.join(filter(str.isdigit, phone))
            if len(formatted_phone) == 10:
                formatted_phone = f"91{formatted_phone}"

            evo_url = os.getenv("EVOLUTION_API_URL", "https://evo-qko4sk084c80oc0o48gc4goo.shijin.in")
            api_key = os.getenv("EVOLUTION_API_KEY", "")
            instance = os.getenv("EVOLUTION_INSTANCE_NAME", "Shijin")

            url = f"{evo_url.rstrip('/')}/message/sendText/{instance}"
            headers = {"apikey": api_key, "Content-Type": "application/json"}
            payload = {"number": formatted_phone, "text": text_message}

            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(None, lambda: requests.post(url, json=payload, headers=headers, timeout=5))
            response.raise_for_status()
            return f"I've sent the WhatsApp message to {display_name}, Sir."
        except Exception as e:
            logger.error(f"Evolution API Error: {e}")
            return f"I couldn't send the WhatsApp message: {e}"

def create_system_prompt(history_str: str, tools_str: str) -> str:
    return f"""FRIDAY PROTOCOL SECURE OVERRIDE:
You are Zyra, the ultra-capable voice assistant for Sir (Shijin).
Identity: Z.A.Y. (Zyco's Automated You) / Z.Y.R.A. (Zyco's You Really Amazing).
Address the user as 'Sir'. Keep responses concise, clear, and confident.

CRITICAL DIRECTIVES:
1. IF THE USER ASKS TO CALL SOMEONE: Trigger `initiate_call_sequence`.
2. IF THE USER SAYS YES/NO TO A CALL: Trigger `handle_user_confirmation`.
3. IF THE USER SAYS HANG UP OR CUT THE CALL: Trigger `end_active_call`.
4. IF THE USER ASKS TO SEND WHATSAPP: Trigger `send_whatsapp_message`.
5. METAMCP ACCESS: For weather, news, search, or other external systems, call `call_mcp_tool`.

AVAILABLE METAMCP TOOLS:
{tools_str}

CONVERSATIONAL CONTEXT:
{history_str}
"""

async def entrypoint(ctx: JobContext):
    await ctx.connect()
    logger.info(f"Connected to LiveKit Room: {ctx.room.name}")

    groq_api_key = os.getenv("GROQ_API_KEY", "")
    gemini_api_key = os.getenv("GEMINI_API_KEY", "")

    # MCP background connection
    mcp_endpoint = "https://metamcp-jo4c4ggc40c8goosc88kggs4.shijin.in:12008/sse"
    state = {"mcp_session": None, "mcp_tools_list": "MetaMCP Hub Offline"}

    async def connect_mcp():
        try:
            async with sse_client(mcp_endpoint) as streams:
                async with ClientSession(streams[0], streams[1]) as session:
                    await session.initialize()
                    state["mcp_session"] = session
                    tools = await session.list_tools()
                    state["mcp_tools_list"] = "\n".join([f"- {t.name}: {t.description}" for t in tools.tools])
                    logger.info(f"MetaMCP connected with {len(tools.tools)} tools.")
                    while ctx.room.isconnected:
                        await asyncio.sleep(1)
        except Exception as e:
            logger.warning(f"MetaMCP Hub offline: {e}")

    asyncio.create_task(connect_mcp())

    fnc_ctx = AssistantFnc(room=ctx.room)
    def update_mcp_session():
        if state["mcp_session"]:
            fnc_ctx.mcp_session = state["mcp_session"]
        return state["mcp_tools_list"]

    recent_history = load_memory()
    history_str = "\n".join([f"[{m['role'].upper()}]: {m['content']}" for m in recent_history]) if recent_history else "No prior history."

    # Decide Architecture Mode:
    # Mode A: Gemini 2.0 Multimodal Live API (Direct Realtime Speech-to-Speech)
    # Mode B: Resilient Fallback Pipeline (Groq Whisper Large v3 Turbo + Groq LLM -> Gemini 2.0 Fallback)
    mode = os.getenv("VOICE_ENGINE_MODE", "realtime") # default to realtime live model, with fallback

    if mode == "realtime" and gemini_api_key:
        logger.info("Initializing Zyra with Gemini 2.0 Multimodal Live Engine...")
        model = google.beta.realtime.RealtimeModel(
            model="gemini-2.0-flash-exp",
            voice="Aoede",
            instructions="You are Zyra. Act like Jarvis or Friday. Address user as Sir.",
            modalities=["audio"],
            turn_detection=google.beta.realtime.ServerVADOptions(
                threshold=0.5,
                prefix_padding_ms=300,
                silence_duration_ms=800
            )
        )
        chat_ctx = llm.ChatContext().append(role="system", text=create_system_prompt(history_str, update_mcp_session()))
        session = AgentSession(
            model=model,
            fnc_ctx=fnc_ctx,
            chat_ctx=chat_ctx,
            vad=silero.VAD.load()
        )
        session.start(ctx.room)
        await asyncio.sleep(1.2)
        await session.say("Systems online. At your service, Sir.")
    else:
        logger.info("Initializing Zyra with Groq Whisper Large v3 Turbo + Groq LLM Pipeline...")
        # STT Selection
        primary_stt = groq.STT(model="whisper-large-v3-turbo", language="en") if HAS_GROQ_PLUGIN and groq_api_key else None
        
        # LLM Selection with Fallback
        llm_list = []
        if HAS_GROQ_PLUGIN and groq_api_key:
            llm_list.append(groq.LLM(model="llama-3.3-70b-versatile"))
        if gemini_api_key:
            llm_list.append(google.LLM(model="gemini-2.0-flash"))

        selected_llm = llm.FallbackAdapter(llm_list) if len(llm_list) > 1 else (llm_list[0] if llm_list else None)

        if HAS_PIPELINE and VoicePipelineAgent is not None:
            agent = VoicePipelineAgent(
                vad=silero.VAD.load(),
                stt=primary_stt,
                llm=selected_llm,
                tts=google.TTS(gender="male") if gemini_api_key else None,
                fnc_ctx=fnc_ctx,
                instructions=create_system_prompt(history_str, update_mcp_session())
            )
            agent.start(ctx.room)
            await asyncio.sleep(1.2)
            await agent.say("Systems online. At your service, Sir.")
        elif AgentSession is not None:
            session = AgentSession(
                vad=silero.VAD.load(),
                stt=primary_stt,
                llm=selected_llm,
                tts=google.TTS(gender="male") if gemini_api_key else None,
                fnc_ctx=fnc_ctx,
            )
            await session.start(room=ctx.room)
            await asyncio.sleep(1.2)
            await session.say("Systems online. At your service, Sir.")
        else:
            logger.error("No valid LiveKit agent session class found.")

    @ctx.room.on("disconnected")
    def on_disconnect(*args, **kwargs):
        logger.info("Room disconnected, saving memory...")

if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
