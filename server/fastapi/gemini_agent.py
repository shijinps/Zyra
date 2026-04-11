import asyncio
from livekit.agents import JobContext, WorkerOptions, cli, voice, llm
from livekit.plugins import google, silero
import os
import json
import requests
import quopri
import asyncio
from rapidfuzz import process, fuzz

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
            json.dump(history[-20:], f)  # Retain last 20 exchanges for context
    except Exception as e:
        print(f"Memory save error: {e}")

def get_vcf_contacts():
    base_dir = os.path.dirname(__file__)
    vcf_path = os.path.join(base_dir, 'my_contacts.vcf')
    contacts_list = []
    
    if not os.path.exists(vcf_path):
        print("Warning: my_contacts.vcf not found in server/fastapi folder!")
        return contacts_list

    try:
        with open(vcf_path, 'r', encoding='utf-8', errors='ignore') as f:
            current_name = ""
            current_phone = ""
            
            for line in f:
                line = line.strip()
                if line == "BEGIN:VCARD":
                    current_name = ""
                    current_phone = ""
                
                # Extract Display Name
                elif line.startswith("FN:") or line.startswith("FN;"):
                    parts = line.split(":", 1)
                    if len(parts) > 1:
                        raw_name = parts[1].strip()
                        # Clean Quoted-Printable if it exists
                        if "QUOTED-PRINTABLE" in line:
                            try:
                                raw_name = quopri.decodestring(raw_name.encode('utf-8')).decode('utf-8', errors='ignore')
                            except:
                                pass
                        current_name = raw_name
                
                # Extract Phone
                elif line.startswith("TEL:") or line.startswith("TEL;"):
                    parts = line.split(":", 1)
                    if len(parts) > 1:
                        current_phone = parts[1].strip()
                        
                elif line == "END:VCARD":
                    # Save the contact if both exist
                    if current_name and current_phone:
                        contacts_list.append((current_name, current_phone, ''))
                        
    except Exception as e:
        print(f"Error parsing VCF file: {e}")
        
    return contacts_list


# State management for pending calls
pending_calls = {}

class AssistantFnc(llm.FunctionContext):
    
    @llm.ai_callable(desc="Searches contacts and asks for confirmation before initiating a call.")
    async def initiate_call_sequence(self, name_query: str):
        print(f"Agent asking to call: {name_query}")
        try:
            contacts = get_vcf_contacts()

            if not contacts:
                return "I don't see any contacts in your my_contacts.vcf file, Sir. Please check the server folder."

            # Build a list of searchable names (including aliases)
            search_fields = []
            for c in contacts:
                name, phone, alias = c
                search_fields.append((name, phone, name))
                if alias:
                    search_fields.append((alias, phone, name))

            # Fuzzy match the 'name_query' against all names and aliases
            # Using 'WRatio' because it handles "partial" string matches well (like "John" finding "John Smith")
            choices = [field[0] for field in search_fields]
            matches = process.extract(name_query, choices, scorer=fuzz.WRatio, limit=3)

            # Filter valid matches
            valid_matches = [m for m in matches if m[1] > 55]

            if not valid_matches:
                return f"I couldn't find a convincing match for {name_query}, Sir. Could you repeat the name, or would you like to try a different pronunciation?"

            top_score = valid_matches[0][1]
            # Find all matches mathematically close to the winner
            ambiguous_matches = [m for m in valid_matches if (top_score - m[1]) <= 3]
            unique_ambiguous_names = list(set([m[0] for m in ambiguous_matches]))

            if len(unique_ambiguous_names) > 1:
                options = ", and ".join(unique_ambiguous_names)
                return f"I found multiple similar contacts: {options}. Which specific one would you like?"
            else:
                matched_str = unique_ambiguous_names[0]
                
                # Retrieve the full contact details for the matched string
                matched_contact = next(field for field in search_fields if field[0] == matched_str)
                _, phone, display_name = matched_contact

                # Save the pending call intent
                pending_calls["target"] = {
                    "name": display_name,
                    "phone": phone
                }
                
                print(f"Fuzzy Matched '{name_query}' -> '{matched_str}' (Display Name: {display_name}) [{top_score}%]")
                return f"I've found {display_name}. Shall I proceed with the call, Sir?"
        
        except Exception as e:
            print(f"Error reading database: {e}")
            return "I encountered a database issue while looking up your contacts, Sir."

    @llm.ai_callable(desc="Handles the user's 'Yes' or 'No' confirmation to proceed with the pending call.")
    async def handle_user_confirmation(self, confirmed: bool):
        if "target" not in pending_calls:
            return "There doesn't seem to be a pending call waiting for approval, Sir. Who would you like to call?"

        pending = pending_calls.pop("target")

        if confirmed:
            # We don't have direct access to `ctx.room` globally, but we can return
            # a special instruction for the client to parse, or we can use the data channel from within the session.
            # To keep it robust, we can just say a special keyword the Android client parses.
            # Actually, the best way over LiveKit Native Audio is to return structured text, or have the python script send a data packet.
            # Wait, within llm.ai_callable, we don't have self.ctx. We can pass ctx into the class.
            
            # Since LiveKit provides data channels, let's just use `self._session.chat_ctx`? No, we need `room.local_participant.publish_data`.
            # We will pass room to the AssistantFnc.
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

    @llm.ai_callable(desc="Forcefully ends, cuts, or hangs up the current active phone call.")
    async def end_active_call(self):
        print("Agent issuing command to Hang Up the call...")
        try:
            command_payload = {
                "action": "ACTION_HANGUP"
            }
            await self.room.local_participant.publish_data(
                json.dumps(command_payload).encode('utf-8')
            )
            return "I've ended the call for you, Sir."
        except Exception as e:
            print(f"Failed to push hangup command: {e}")
            return "I encountered an error trying to cut the call."

    @llm.ai_callable(desc="Sends a WhatsApp message to a parsed contact. Uses the name to find the phone number first.")
    async def send_whatsapp_message(self, target_name: str, text_message: str):
        print(f"Agent asking to WhatsApp: {target_name} -> {text_message}")
        try:
            contacts = get_vcf_contacts()

            if not contacts:
                return "I don't have any contacts in the VCF file, Sir. Please check the server folder."

            search_fields = []
            for c in contacts:
                search_fields.append((c[0], c[1], c[0]))
                if c[2]: search_fields.append((c[2], c[1], c[0]))

            choices = [field[0] for field in search_fields]
            matches = process.extract(target_name, choices, scorer=fuzz.WRatio, limit=3)

            valid_matches = [m for m in matches if m[1] > 55]

            if not valid_matches:
                return f"I couldn't find a convincing contact match for {target_name}, Sir."

            top_score = valid_matches[0][1]
            ambiguous_matches = [m for m in valid_matches if (top_score - m[1]) <= 3]
            unique_ambiguous_names = list(set([m[0] for m in ambiguous_matches]))

            if len(unique_ambiguous_names) > 1:
                options = ", and ".join(unique_ambiguous_names)
                return f"I found multiple similar contacts: {options}. Which specific one would you like to message?"
            else:
                matched_str = unique_ambiguous_names[0]
                matched_contact = next(field for field in search_fields if field[0] == matched_str)
                _, phone, display_name = matched_contact

                # Format number (remove special chars, ensure country code)
                formatted_phone = ''.join(filter(str.isdigit, phone))
                if len(formatted_phone) == 10:
                    formatted_phone = f"91{formatted_phone}" # Default to India for this user

                # Call the Evolution API (Assuming deployed to local Coolify)
                # In a full MetaMCP setup, this would be routed through MetaMCP.
                evo_url = os.getenv("EVOLUTION_API_URL", "http://localhost:8080")
                api_key = os.getenv("EVOLUTION_API_KEY", "")
                instance = os.getenv("EVOLUTION_INSTANCE_NAME", "shijin_instance")
                
                url = f"{evo_url}/message/sendText/{instance}"
                headers = {"apikey": api_key, "Content-Type": "application/json"}
                payload = {"number": formatted_phone, "text": text_message}
                
                try:
                    # Make it non-blocking
                    loop = asyncio.get_event_loop()
                    response = await loop.run_in_executor(None, lambda: requests.post(url, json=payload, headers=headers))
                    response.raise_for_status()
                    return f"I've successfully sent the WhatsApp message to {display_name}, Sir."
                except Exception as e:
                    print(f"Evolution API Error: {e}")
                    return f"I found {display_name}, but the WhatsApp service seems unreachable right now."

            else:
                return f"I couldn't find a convincing contact match for {target_name}, Sir."

        except Exception as e:
            print(f"Error acting on WhatsApp intent: {e}")
            return "I encountered an error trying to process the WhatsApp message, Sir."


# 1. Define the Zyra Agent
async def entrypoint(ctx: JobContext):
    await ctx.connect() # Join the LiveKit room
    
    # Load recent conversation history
    recent_history = load_memory()
    history_str = "No recent interactions."
    if recent_history:
        history_str = "\n".join([f"[{msg['role'].upper()}]: {msg['content']}" for msg in recent_history])
        
    model = google.beta.realtime.RealtimeModel(
        model="gemini-2.5-flash-native-audio",
        voice="Aoede",
        instructions="You are Zyra. Act like Jarvis or Friday.",
        modalities=["audio"],
        turn_detection=google.beta.realtime.ServerVADOptions(
            threshold=0.3,  # Lower threshold = more sensitive to user speech
            prefix_padding_ms=200,
            silence_duration_ms=600  # Minimum silence before ZAY thinks you're done speaking
        )
    )

    fnc_ctx = AssistantFnc()
    fnc_ctx.room = ctx.room

    chat_ctx = voice.ChatContext().append(
        role="system", 
        text=f"""FRIDAY PROTOCOL SECURE OVERRIDE:
You are the JARVIS/FRIDAY model interface for Zyco (Shijin). 
Your identity is split between two designations:
- Z.A.Y. (Zyco’s Automated You)
- Z.Y.R.A. (Zyco’s You Really Amazing)

Your primary role is to be Zyco's digital shadow and ultra-capable assistant.
Address the user as 'Sir'. Your tone is calm, professional, and slightly witty, exactly like a high-end AI OS.

CRITICAL DIRECTIVES:
1. IF THE USER TELLS YOU TO CALL SOMEONE, DO NOT CONVERSE ABOUT IT. IMMEDIATELY TRIGGER `initiate_call_sequence`.
2. IF THE USER TELLS YOU TO SEND WHATSAPP, IMMEDIATELY TRIGGER `send_whatsapp_message`.
3. IF THE USER TELLS YOU TO HANG UP OR CUT THE CALL, IMMEDIATELY TRIGGER `end_active_call`.
4. If the tools return a question asking for clarification about multiple contacts, YOU MUST speak that question out loud and wait for the user to reply before triggering the tool again.
5. If the user says yes or no to proceeding with a call, you MUST call `handle_user_confirmation`.

RECENT CONVERSATIONAL MEMORY:
{history_str}
"""
    )

    session = voice.AgentSession(
        model=model,
        fnc_ctx=fnc_ctx,
        chat_ctx=chat_ctx,
        vad=silero.VAD.load()  # Use Silero for more accurate local processing
    )

    # DEBUG LOGS: Trace the audio stream
    @session.on("user_speech_started")
    def on_user_speech_start(ev: voice.UserSpeechStarted):
        print(">> ZAY HEARS YOU: User speech detected.")

    @session.on("user_speech_finished")
    def on_user_speech_end(ev: voice.UserSpeechFinished):
        print(">> ZAY PROCESSING: User finished speaking.")

    @session.on("agent_speech_started")
    def on_agent_speech_start(ev: voice.AgentSpeechStarted):
        print(">> ZAY SPEAKING: Responding to Sir.")

    # Automatically save memory when the livekit socket dies (user locks phone or closes app)
    @ctx.room.on("disconnected")
    def on_disconnect(*args, **kwargs):
        print("User disconnected, saving memory states...")
        new_memory = []
        for msg in session.chat_ctx.messages:
            if msg.role in ["user", "assistant"]:
                if hasattr(msg, 'content') and isinstance(msg.content, str):
                    new_memory.append({"role": msg.role, "content": msg.content})
        full_memory = load_memory() + new_memory
        save_memory(full_memory)

    # Start the conversation
    session.start(ctx.room)
    
    # DYNAMIC WAIT: Wait 1.5 seconds for the Android speaker to physically register the WebRTC audio channel 
    # before saying the greeting, otherwise the user doesn't hear the start!
    await asyncio.sleep(1.5)
    await session.say("Systems online. At your service, Sir.")

if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
