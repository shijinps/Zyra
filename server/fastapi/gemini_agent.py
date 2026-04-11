import asyncio
from livekit.agents import JobContext, WorkerOptions, cli, voice, llm
from livekit.plugins import google
import psycopg2
import os
import json
from rapidfuzz import process, fuzz
import requests

# Helper for Postgres connection
def get_db_connection():
    db_user = os.getenv("POSTGRES_USER", "postgres")
    db_password = os.getenv("POSTGRES_PASSWORD", "password")
    db_name = os.getenv("POSTGRES_DB", "postgres")
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    if db_host.startswith("http"):
        db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]
    return psycopg2.connect(dbname=db_name, user=db_user, password=db_password, host=db_host)

# State management for pending calls
pending_calls = {}

class AssistantFnc(llm.FunctionContext):
    
    @llm.ai_callable(desc="Searches contacts and asks for confirmation before initiating a call.")
    async def initiate_call_sequence(self, name_query: str):
        print(f"Agent asking to call: {name_query}")
        try:
            conn = get_db_connection()
            with conn.cursor() as cursor:
                # Get all contacts to perform fuzzy matching
                cursor.execute("SELECT name, phone, alias FROM zyra_contacts")
                contacts = cursor.fetchall()
            conn.close()

            if not contacts:
                return "I don't see any contacts in your synchronized database yet, Sir. You might want to sync them from the app first."

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
            best_match = process.extractOne(name_query, choices, scorer=fuzz.WRatio)

            if best_match and best_match[1] > 65: # 65% match confidence threshold
                matched_str = best_match[0]
                
                # Retrieve the full contact details for the matched string
                matched_contact = next(field for field in search_fields if field[0] == matched_str)
                _, phone, display_name = matched_contact

                # Save the pending call intent
                pending_calls["target"] = {
                    "name": display_name,
                    "phone": phone
                }
                
                print(f"Fuzzy Matched '{name_query}' -> '{matched_str}' (Display Name: {display_name}) [{best_match[1]}%]")
                return f"I've found {display_name}. Shall I proceed with the call, Sir?"
            else:
                return f"I couldn't find a convincing match for {name_query}, Sir. Could you repeat the name, or would you like to try a different pronunciation?"
        
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

    @llm.ai_callable(desc="Sends a WhatsApp message to a parsed contact. Uses the name to find the phone number first.")
    async def send_whatsapp_message(self, target_name: str, text_message: str):
        print(f"Agent asking to WhatsApp: {target_name} -> {text_message}")
        try:
            conn = get_db_connection()
            with conn.cursor() as cursor:
                cursor.execute("SELECT name, phone, alias FROM zyra_contacts")
                contacts = cursor.fetchall()
            conn.close()

            if not contacts:
                return "I don't have any contacts synchronized, Sir. Please check the Android app."

            search_fields = []
            for c in contacts:
                search_fields.append((c[0], c[1], c[0]))
                if c[2]: search_fields.append((c[2], c[1], c[0]))

            choices = [field[0] for field in search_fields]
            best_match = process.extractOne(target_name, choices, scorer=fuzz.WRatio)

            if best_match and best_match[1] > 65:
                matched_str = best_match[0]
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
    
    # 2. Setup the Gemini Live Brain
    # Using the stable 2026 model ID
    model = google.beta.realtime.RealtimeModel(
        model="gemini-2.5-flash-native-audio",
        voice="Aoede", # A clear, human-like voice
        instructions="You are Zyra. Proactively assist Zyco with MCA and SysAdmin tasks."
    )

    # Instantiate the tools and pass the room
    fnc_ctx = AssistantFnc()
    fnc_ctx.room = ctx.room

    # 3. Create the Session
    # This automatically handles Voice Activity Detection (VAD) and interruptions
    session = voice.AgentSession(
        model=model,
        fnc_ctx=fnc_ctx,
        chat_ctx=voice.ChatContext().append(
            role="system", 
            text="Context: Shijin (Zyco) is the user. Keep responses concise and conversational. Address the user as 'Sir'. "
                 "If the user asks to call someone, you MUST call `initiate_call_sequence`. "
                 "If the user asks to send a WhatsApp, you MUST call `send_whatsapp_message` and pass the message content. "
                 "If the user says yes or no to proceeding with a call, you MUST call `handle_user_confirmation`."
        )
    )

    # Start the conversation
    session.start(ctx.room)
    await session.say("Hello Zyco, Zyra is online. Ready for the ride?")

if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
