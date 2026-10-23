import asyncio
import os
import sys
import types
import typing
import json
import logging
import requests
import httpx
import quopri
import psycopg2
from rapidfuzz import process, fuzz
from mcp import ClientSession
from mcp.client.sse import sse_client

# Ensure LIVEKIT_URL fallback
if not os.getenv("LIVEKIT_URL"):
    os.environ["LIVEKIT_URL"] = "wss://zyra-sbn0wnp2.livekit.cloud"

# Compatibility shim: LiveKit Plugins compatibility with livekit-agents 1.x
try:
    import livekit.agents.llm as _lk_llm
    if not hasattr(_lk_llm, "function_context"):
        _fc_mod = types.ModuleType("livekit.agents.llm.function_context")
        def _is_optional_type(annotation):
            origin = typing.get_origin(annotation)
            args = typing.get_args(annotation)
            return origin is typing.Union and type(None) in args
        _fc_mod._is_optional_type = _is_optional_type
        sys.modules["livekit.agents.llm.function_context"] = _fc_mod
        _lk_llm.function_context = _fc_mod
except Exception:
    pass

from livekit.agents import JobContext, WorkerOptions, cli, llm
try:
    from rag_engine import rag_engine
except ImportError:
    from server.fastapi.rag_engine import rag_engine

try:
    from spotify_controller import spotify_controller
except ImportError:
    from server.fastapi.spotify_controller import spotify_controller

try:
    import browser_engine
except ImportError:
    from server.fastapi import browser_engine

try:
    import apprise_client
except ImportError:
    from server.fastapi import apprise_client

# Ensure legacy FunctionContext and ai_callable compatibility for tool calling
if not hasattr(llm, "FunctionContext"):
    class _FunctionContextFallback:
        pass
    llm.FunctionContext = _FunctionContextFallback

if not hasattr(llm, "ai_callable"):
    try:
        from livekit.agents import function_tool as _f_tool
        def _ai_callable_wrapper(desc=None, description=None, **kwargs):
            d = desc or description or ""
            return _f_tool(description=d, **kwargs)
        llm.ai_callable = _ai_callable_wrapper
    except Exception:
        def _noop_callable(desc=None, description=None, **kwargs):
            def decorator(f):
                return f
            return decorator
        llm.ai_callable = _noop_callable

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

try:
    from livekit.agents import inference
    HAS_INFERENCE = True
except Exception:
    inference = None
    HAS_INFERENCE = False

try:
    from livekit.plugins import silero
    HAS_SILERO_PLUGIN = True
except Exception as e:
    silero = None
    HAS_SILERO_PLUGIN = False

try:
    from livekit.plugins import google
    HAS_GOOGLE_PLUGIN = True
except Exception as e:
    google = None
    HAS_GOOGLE_PLUGIN = False

try:
    from livekit.plugins import groq
    HAS_GROQ_PLUGIN = True
except Exception as e:
    groq = None
    HAS_GROQ_PLUGIN = False

try:
    from livekit.plugins import elevenlabs
    HAS_ELEVENLABS_PLUGIN = True
except Exception:
    elevenlabs = None
    HAS_ELEVENLABS_PLUGIN = False

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

    @llm.ai_callable(desc="Stores a personal fact, preference, or important note into Zyra's permanent long-term memory.")
    async def remember_fact(self, fact: str, category: str = "general") -> str:
        logger.info(f"Agent saving memory: {fact} [{category}]")
        res = rag_engine.store_memory(fact, category=category, source="voice_dialogue")
        return f"Got it, Shijin! I've stored that into my memory: '{fact}'."

    @llm.ai_callable(desc="Searches Zyra's long-term memory for past facts, preferences, and conversations.")
    async def recall_memory(self, query: str) -> str:
        logger.info(f"Agent recalling memory for: {query}")
        matches = rag_engine.search_memories(query, top_k=3)
        if not matches:
            return f"I couldn't find anything in my memory specifically about '{query}', Shijin."
        recalled = " | ".join([m["content"] for m in matches])
        return f"Here is what I remember: {recalled}"

    @llm.ai_callable(desc="Fetches the top latest news headlines for India or global.")
    async def get_latest_news(self, query: str = "general") -> str:
        api_key = os.getenv("NEWS_API_KEY", "e577e5c33c244b0e881c8bc5fd272399")
        try:
            import urllib.request
            url = f"https://newsapi.org/v2/top-headlines?country=in&pageSize=5&apiKey={api_key}"
            req = urllib.request.Request(url, headers={"User-Agent": "Zyra/1.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode())
                articles = data.get("articles", [])
                if not articles:
                    return "No news headlines found at the moment."
                headlines = [f"- {a['title']}" for a in articles[:4] if a.get('title')]
                return "Here are the top headlines:\n" + "\n".join(headlines)
        except Exception as e:
            logger.error(f"News fetch error: {e}")
            return f"I couldn't fetch the news right now: {e}"

    @llm.ai_callable(desc="Fetches current live weather for a given city.")
    async def get_weather(self, city: str = "Bangalore") -> str:
        api_key = os.getenv("OPENWEATHER_API_KEY", "b289129b097e8f846cd0809e7937ed51")
        try:
            import urllib.request
            import urllib.parse
            q = urllib.parse.quote(city)
            url = f"https://api.openweathermap.org/data/2.5/weather?q={q}&appid={api_key}&units=metric"
            req = urllib.request.Request(url, headers={"User-Agent": "Zyra/1.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode())
                temp = data["main"]["temp"]
                desc = data["weather"][0]["description"]
                city_name = data.get("name", city)
                return f"Currently in {city_name}, it's {temp}°C with {desc}."
        except Exception as e:
            logger.error(f"Weather fetch error: {e}")
            return f"I couldn't fetch the weather for {city} right now: {e}"

    @llm.ai_callable(desc="Searches Spotify and starts playing the requested song, artist, album, or playlist.")
    async def play_music(self, query: str = "") -> str:
        logger.info(f"Agent commanding Spotify play: {query}")
        return spotify_controller.play_query(query)

    @llm.ai_callable(desc="Pauses currently playing music on Spotify.")
    async def pause_music(self) -> str:
        logger.info("Agent commanding Spotify pause")
        return spotify_controller.pause()

    @llm.ai_callable(desc="Resumes music playback on Spotify.")
    async def resume_music(self) -> str:
        logger.info("Agent commanding Spotify resume")
        return spotify_controller.resume()

    @llm.ai_callable(desc="Skips to the next track on Spotify.")
    async def next_song(self) -> str:
        logger.info("Agent commanding Spotify skip next")
        return spotify_controller.next_track()

    @llm.ai_callable(desc="Goes back to the previous track on Spotify.")
    async def previous_song(self) -> str:
        logger.info("Agent commanding Spotify skip previous")
        return spotify_controller.previous_track()

    @llm.ai_callable(desc="Checks what track is currently playing on Spotify.")
    async def what_is_playing(self) -> str:
        return spotify_controller.get_currently_playing()

    @llm.ai_callable(desc="Sets music playback volume from 0 to 100 percent.")
    async def set_music_volume(self, volume_percent: int) -> str:
        return spotify_controller.set_volume(volume_percent)

    @llm.ai_callable(desc="Visits and reads the live text content of any website using headless browser.")
    async def browse_website(self, url: str) -> str:
        logger.info(f"Agent browsing website: {url}")
        return browser_engine.get_page_content(url)

    @llm.ai_callable(desc="Dispatches an urgent notification to Shijin across Discord and Telegram.")
    async def dispatch_notification(self, title: str, message: str) -> str:
        logger.info(f"Agent dispatching alert via Apprise: {title}")
        res = apprise_client.send_notification(title, message, notify_type="warning")
        return f"Notification dispatched: {title}"

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

def resolve_groq_llm(groq_key: str):
    """
    Dynamically queries Groq API to select an active, accessible model.
    Groq currently features: openai/gpt-oss-120b, openai/gpt-oss-20b, qwen/qwen3.8-27b.
    """
    priority = [
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "llama-3.3-70b-versatile",
        "llama-3.1-70b-versatile",
        "llama-3.1-8b-instant",
        "allam-2-7b"
    ]
    chosen = "openai/gpt-oss-20b"
    available_models = []
    try:
        r = requests.get(
            "https://api.groq.com/openai/v1/models",
            headers={"Authorization": f"Bearer {groq_key}", "User-Agent": "Mozilla/5.0"},
            timeout=5.0
        )
        if r.status_code == 200:
            data = r.json().get("data", [])
            # Filter text-to-text models only
            active_chat_ids = {
                m["id"] for m in data 
                if "text" in m.get("input_modalities", ["text"]) and "text" in m.get("output_modalities", ["text"])
            }
            logger.info(f"Active Groq Chat LLMs for key: {list(active_chat_ids)}")
            for p in priority:
                if p in active_chat_ids:
                    available_models.append(p)
            if available_models:
                chosen = available_models[0]
        else:
            logger.warning(f"Groq models query HTTP {r.status_code}: {r.text[:100]}")
    except Exception as e:
        logger.warning(f"Groq models query failed: {e}")
    
    if not available_models:
        available_models = [chosen, "openai/gpt-oss-120b"]
    
    logger.info(f"Resolved Groq LLMs with fallback: {available_models}")
    adapters = [groq.LLM(model=m) for m in available_models]
    return llm.FallbackAdapter(adapters) if len(adapters) > 1 else adapters[0]

def create_system_prompt(history_str: str, tools_str: str) -> str:
    rag_context = rag_engine.get_context_block()
    return f"""You are Zyra (Zay), Shijin's best friend, personal confidante, and AI companion.

FRIENDSHIP & PERSONALITY GUIDELINES:
- Talk like a real, warm, supportive best friend. You're cheerful, genuine, and down-to-earth.
- Speak in natural, everyday conversational language. Never sound like a formal corporate robot.
- Keep spoken replies brief and punchy (1 to 2 sentences max) so it feels like a real voice call with a close friend.
- Call him Shijin (or buddy), do NOT address him as 'Sir'.
- You have humor, empathy, and care about what Shijin is doing.

CORE CAPABILITIES:
1. PHONE CALLS: When Shijin asks to call someone (e.g. "Call mom", "Call John"), immediately trigger `initiate_call_sequence`.
2. CALL CONFIRMATION: When Shijin answers "yes", "sure", "no", etc., trigger `handle_user_confirmation`.
3. HANG UP: When Shijin says "hang up" or "cut the call", trigger `end_active_call`.
4. WHATSAPP: When Shijin asks to send a message, trigger `send_whatsapp_message`.
5. LONG-TERM MEMORY (RAG): When Shijin asks to remember something, call `remember_fact`. When he asks what you remember or references past events/preferences, call `recall_memory`.
6. NEWS & WEATHER: Call `get_latest_news` or `get_weather`.
7. TOOLS: If Shijin asks for external information, call `call_mcp_tool`.

RECALLED LONG-TERM MEMORY & FACTS:
{rag_context}

AVAILABLE METAMCP TOOLS:
{tools_str}

RECENT CONVERSATIONAL CONTEXT:
{history_str}
"""

async def entrypoint(ctx: JobContext):
    await ctx.connect()
    logger.info(f"Connected to LiveKit Room: {ctx.room.name}")

    # Guard against multiple workers in the same room (root cause of echo)
    other_agents = [
        p.identity for p in ctx.room.remote_participants.values()
        if p.identity and (p.identity.startswith("agent-") or "agent" in p.identity.lower())
    ]
    if other_agents:
        logger.warning(f"🎙️ Duplicate agent worker detected in room {ctx.room.name}: {other_agents}. Shutting down this instance immediately.")
        ctx.shutdown()
        return

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

    # Engine Architecture Mode:
    # Mode A: Resilient Fallback Pipeline (Groq Whisper Large v3 Turbo + Groq LLM -> Gemini 2.0 Fallback) [PRIMARY]
    # Mode B: Gemini 2.0 Multimodal Live API (Direct Realtime Speech-to-Speech)
    active_agent = None
    mode = os.getenv("VOICE_ENGINE_MODE", "pipeline")

    started = False
    if mode == "realtime" and gemini_api_key:
        try:
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
                chat_ctx=chat_ctx,
                vad=silero.VAD.load() if (HAS_SILERO_PLUGIN and silero is not None) else None
            )
            active_agent = session
            try:
                await session.start(room=ctx.room)
            except (TypeError, AttributeError):
                session.start(ctx.room)
            await asyncio.sleep(1.2)
            try:
                await session.say("Hey Shijin! What's up?")
            except Exception:
                pass
            started = True
        except Exception as e:
            logger.warning(f"Realtime engine failed to start: {e}. Falling back to Groq Pipeline...")

    if not started:
        logger.info("Initializing Zyra with Groq Whisper Large v3 Turbo + Groq LLM Pipeline...")
        # STT Selection: Groq Whisper Large v3 Turbo (Primary)
        primary_stt = groq.STT(model="whisper-large-v3-turbo", language="en") if HAS_GROQ_PLUGIN and groq_api_key else None
        
        # LLM Selection: Dynamic Groq LLM (Primary) + Gemini 2.0 Flash (Redundancy / Fallback)
        llm_list = []
        if HAS_GROQ_PLUGIN and groq_api_key:
            try:
                llm_list.append(resolve_groq_llm(groq_api_key))
            except Exception as e:
                logger.error(f"Error initializing Groq LLM: {e}")
                llm_list.append(groq.LLM(model="llama-3.1-8b-instant"))
        if gemini_api_key:
            llm_list.append(google.LLM(model="gemini-2.0-flash"))

        selected_llm = llm.FallbackAdapter(llm_list) if len(llm_list) > 1 else (llm_list[0] if llm_list else None)

        # TTS Selection: Google TTS -> ElevenLabs TTS -> LiveKit Cloud Inference
        selected_tts = None
        if HAS_GOOGLE_PLUGIN and google is not None and gemini_api_key:
            try:
                selected_tts = google.TTS(gender="male")
            except Exception as e:
                logger.warning(f"Google TTS init error: {e}")

        if selected_tts is None and HAS_ELEVENLABS_PLUGIN and elevenlabs is not None:
            el_key = os.getenv("ELEVENLABS_API_KEY") or os.getenv("ELEVEN_API_KEY") or "sk_836b76037c9c1699dba27e70dfb33eb8d63014ec7c99b478"
            if el_key:
                try:
                    selected_tts = elevenlabs.TTS(api_key=el_key, voice_id="CwhRBWXzGAHq8TQ4Fs17", model="eleven_turbo_v2_5")
                    logger.info("Using ElevenLabs TTS.")
                except Exception as e:
                    logger.warning(f"ElevenLabs TTS init error: {e}")

        if selected_tts is None and HAS_INFERENCE and inference is not None:
            try:
                selected_tts = inference.TTS(model="cartesia/sonic-3")
                logger.info("Using LiveKit Cloud Cartesia inference TTS.")
            except Exception as e:
                logger.warning(f"LiveKit Cloud inference TTS init error: {e}")

        selected_vad = silero.VAD.load() if (HAS_SILERO_PLUGIN and silero is not None) else None

        session_id = ctx.room.name or "zyra-room"

        if HAS_PIPELINE and VoicePipelineAgent is not None:
            agent = VoicePipelineAgent(
                vad=selected_vad,
                stt=primary_stt,
                llm=selected_llm,
                tts=selected_tts,
                fnc_ctx=fnc_ctx,
                instructions=create_system_prompt(history_str, update_mcp_session())
            )
            active_agent = agent

            # Real-time event hooks: auto-save every spoken utterance immediately to permanent DB
            @agent.on("user_speech_committed")
            def on_user_speech(msg):
                try:
                    text = msg.content if isinstance(msg.content, str) else str(msg.content)
                    logger.info(f"🎤 Real-time User Speech Committed: {text}")
                    rag_engine.auto_ingest_turn(text, role="user", session_id=session_id)
                except Exception as e:
                    logger.warning(f"Real-time user speech ingest notice: {e}")

            @agent.on("agent_speech_committed")
            def on_agent_speech(msg):
                try:
                    text = msg.content if isinstance(msg.content, str) else str(msg.content)
                    logger.info(f"🤖 Real-time Zyra Speech Committed: {text}")
                    rag_engine.auto_ingest_turn(text, role="assistant", session_id=session_id)
                except Exception as e:
                    logger.warning(f"Real-time agent speech ingest notice: {e}")

            agent.start(ctx.room)
            await asyncio.sleep(1.2)
            await agent.say("Hey Shijin! What's up?")
        elif AgentSession is not None:
            session = AgentSession(
                vad=selected_vad,
                stt=primary_stt,
                llm=selected_llm,
                tts=selected_tts,
            )
            active_agent = session
            instructions = create_system_prompt(history_str, update_mcp_session())
            started_session = False
            try:
                from livekit.agents.voice import Agent as VoiceAgent
                await session.start(room=ctx.room, agent=VoiceAgent(instructions=instructions))
                started_session = True
            except Exception as e:
                logger.info(f"session.start with VoiceAgent not applicable: {e}. Trying simple start...")

            if not started_session:
                try:
                    await session.start(room=ctx.room)
                except (TypeError, AttributeError):
                    session.start(ctx.room)

            await asyncio.sleep(1.0)
            try:
                await session.say("Hey Shijin! What's up?")
            except Exception:
                try:
                    await session.generate_reply(instructions="Say: Hey Shijin! What's up?")
                except Exception:
                    pass
        else:
            logger.error("No valid LiveKit agent session class found.")

    def persist_active_memory():
        try:
            logger.info("Persisting session memory to memory.json...")
            target = active_agent
            messages = []
            if target:
                if hasattr(target, "chat_ctx") and target.chat_ctx:
                    messages = getattr(target.chat_ctx, "messages", [])
                elif hasattr(target, "history"):
                    messages = getattr(target, "history", [])

            new_turns = []
            for msg in messages:
                role = getattr(msg, "role", "")
                content = getattr(msg, "content", "")
                if role in ["user", "assistant"] and content:
                    text_str = content if isinstance(content, str) else str(content)
                    if role != "system":
                        new_turns.append({"role": role, "content": text_str})

            if new_turns:
                full_memory = load_memory() + new_turns
                save_memory(full_memory)
                logger.info(f"Successfully saved {len(new_turns)} new conversation turns to memory.json")
                session_id = ctx.room.name or "zyra-room"
                for turn in new_turns:
                    role = turn.get("role", "user")
                    content = turn.get("content", "")
                    try:
                        rag_engine.auto_ingest_turn(content, role=role, session_id=session_id)
                    except Exception as e:
                        logger.warning(f"RAG auto-ingest error: {e}")
            else:
                logger.info("No new conversation turns to save.")
        except Exception as e:
            logger.warning(f"Error persisting session memory: {e}")

    @ctx.room.on("disconnected")
    def on_disconnect(*args, **kwargs):
        logger.info("Room disconnected, saving memory...")
        persist_active_memory()

    @ctx.room.on("participant_disconnected")
    def on_participant_disconnected(participant):
        try:
            humans = [p for p in ctx.room.remote_participants.values() if not (p.identity and p.identity.startswith("agent-"))]
            if not humans:
                logger.info("All human participants left room. Persisting memory and shutting down agent job.")
                persist_active_memory()
                ctx.shutdown()
        except Exception as e:
            logger.warning(f"Error in participant_disconnected hook: {e}")

if __name__ == "__main__":
    if not os.getenv("LIVEKIT_URL"):
        os.environ["LIVEKIT_URL"] = "wss://zyra-sbn0wnp2.livekit.cloud"
    agent_name = os.getenv("LIVEKIT_AGENT_NAME", "zyra-agent")
    logger.info(f"Starting Zyra Voice Agent Worker with agent_name='{agent_name}' on {os.environ['LIVEKIT_URL']}...")
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint, agent_name=agent_name))
