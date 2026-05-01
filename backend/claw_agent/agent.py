import os
import google.generativeai as genai
from tools.bike_lookup import lookup_vehicle
from dotenv import load_dotenv

# Load environment variables
load_dotenv()
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

if not GEMINI_API_KEY:
    raise ValueError("GEMINI_API_KEY not set in environment.")

# Configure Gemini
genai.configure(api_key=GEMINI_API_KEY)

# Initialize the model with the tool
model = genai.GenerativeModel(
    model_name='gemini-1.5-flash',
    tools=[lookup_vehicle],
    system_instruction=(
        "You are Zyra-Claw, an agentic AI assistant. "
        "You help the user quickly retrieve information while they are on the go. "
        "If the user asks to find information about a vehicle or license plate, you MUST use the lookup_vehicle tool. "
        "Return the information in a concise, readable format suitable for a quick glance or text-to-speech."
    )
)

# Keep a simple conversation history per user
user_sessions = {}

def process_message(user_id: str, message: str) -> str:
    """
    Processes a user message using Gemini and returns the response.
    """
    if user_id not in user_sessions:
        # Start a new chat session for this user
        user_sessions[user_id] = model.start_chat(enable_automatic_function_calling=True)
    
    chat = user_sessions[user_id]
    
    try:
        response = chat.send_message(message)
        return response.text
    except Exception as e:
        print(f"Error processing message: {e}")
        return "Sorry, I ran into an error while processing that request."
