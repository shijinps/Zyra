import os
from fastmcp import FastMCP
import requests
import json

# Initialize FastMCP Server
mcp = FastMCP("Zyra_WhatsApp_Bridge")

# Retrieve configuration from environment variables
# This ensures secrets are not hardcoded.
# Evolution API base URL (e.g., https://evo.yourcoolifydomain.com)
EVO_URL = os.getenv("EVOLUTION_API_URL", "http://localhost:8080")
# The Global API Key for your Evolution API instance
GLOBAL_API_KEY = os.getenv("EVOLUTION_API_KEY", "")
# The name of the WhatsApp instance you created in the Evolution Manager (default: zyra_instance)
INSTANCE_NAME = os.getenv("EVOLUTION_INSTANCE_NAME", "shijin_instance")

HEADERS = {
    "apikey": GLOBAL_API_KEY,
    "Content-Type": "application/json"
}

@mcp.tool()
def send_whatsapp_message(number: str, text: str) -> str:
    """
    Sends a WhatsApp message to a specific number using Evolution API.
    
    Args:
        number: The phone number to send to, including country code (e.g., "919876543210").
        text: The text content of the message to send.
        
    Returns:
        JSON string containing the status of the message delivery.
    """
    # Endpoint for Evolution API v2 to send text
    url = f"{EVO_URL}/message/sendText/{INSTANCE_NAME}"
    
    payload = {
        "number": number,
        "text": text
    }
    
    try:
        response = requests.post(url, json=payload, headers=HEADERS)
        response.raise_for_status()
        return json.dumps({"status": "success", "response": response.json()})
    except requests.exceptions.HTTPError as e:
        return json.dumps({"status": "error", "message": f"HTTP Error: {str(e)}", "details": response.text})
    except Exception as e:
        return json.dumps({"status": "error", "message": str(e)})


@mcp.tool()
def check_whatsapp_status() -> str:
    """
    Checks the connection status of the WhatsApp bot instance.
    
    Returns:
        JSON string indicating if the phone is currently connected to WhatsApp.
    """
    url = f"{EVO_URL}/instance/connectionState/{INSTANCE_NAME}"
    
    try:
        response = requests.get(url, headers=HEADERS)
        response.raise_for_status()
        return json.dumps({"status": "success", "state": response.json()})
    except Exception as e:
        return json.dumps({"status": "error", "message": str(e)})

if __name__ == "__main__":
    # Runs the MCP SSE / stdio server so MetaMCP can connect to it.
    mcp.run()
