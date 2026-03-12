import asyncio
from livekit.agents import JobContext, WorkerOptions, cli, voice, llm
from livekit.plugins import google
import psycopg2
import os
import json
from rapidfuzz import process, fuzz

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
            text="Context: Zyco is riding his bike in Kochi. Keep responses concise. Address the user as 'Sir'. "
                 "If the user asks to call someone, you MUST call `initiate_call_sequence`. "
                 "If the user says yes or no to proceeding with a call, you MUST call `handle_user_confirmation`."
        )
    )

    # Start the conversation
    session.start(ctx.room)
    await session.say("Hello Zyco, Zyra is online. Ready for the ride?")

if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
