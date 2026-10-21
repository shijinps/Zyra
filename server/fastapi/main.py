from fastapi import FastAPI, HTTPException, Header, Security, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
import subprocess
import shutil
import os
import psycopg2
import asyncio
import json
import base64
from typing import Optional, List
from livekit import api
from google import genai
import time
import uuid

# ... (Existing code)


app = FastAPI(title="Zyra Technical Brain")

@app.get("/health")
def health_check():
    """
    Minimal public health check for Zyra Brain and dependencies.
    """
    db_ok = False
    try:
        db_user = os.getenv("POSTGRES_USER", "postgres")
        db_password = os.getenv("POSTGRES_PASSWORD", "password")
        db_name = os.getenv("POSTGRES_DB", "postgres")
        db_host = os.getenv("POSTGRES_HOST", "postgres")
        if db_host.startswith("http"):
            db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]
        conn = psycopg2.connect(
            dbname=db_name,
            user=db_user,
            password=db_password,
            host=db_host,
            connect_timeout=2
        )
        conn.close()
        db_ok = True
    except Exception:
        db_ok = False

    return {
        "status": "healthy" if db_ok else "degraded",
        "database": db_ok,
        "livekit_configured": bool(os.getenv("LIVEKIT_API_KEY") and os.getenv("LIVEKIT_API_SECRET"))
    }

@app.on_event("startup")
def startup_db_client():
    """
    Automates Database Setup on Container Start.
    Connects to Postgres and runs init_memory.sql.
    """
    try:
        # Get DB Credentials from Environment (set in Coolify)
        db_user = os.getenv("POSTGRES_USER", "postgres")
        db_password = os.getenv("POSTGRES_PASSWORD", "password")
        db_name = os.getenv("POSTGRES_DB", "postgres")
        db_host = os.getenv("POSTGRES_HOST", "postgres")
        
        # SANITIZATION: Remove protocol if user accidentally pasted a URL
        if db_host.startswith("http"):
            db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]

        conn = psycopg2.connect(
            dbname=db_name,
            user=db_user,
            password=db_password,
            host=db_host
        )
        conn.autocommit = True
        
        # Hardcoded init SQL for automation (matches init_memory.sql)
        init_sql = """
        CREATE EXTENSION IF NOT EXISTS vector;
        CREATE EXTENSION IF NOT EXISTS postgis;
        
        CREATE TABLE IF NOT EXISTS n8n_chat_histories (
            id SERIAL PRIMARY KEY,
            session_id TEXT NOT NULL,
            message JSONB NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        CREATE TABLE IF NOT EXISTS kochi_incidents (
            id SERIAL PRIMARY KEY,
            description TEXT NOT NULL,
            severity VARCHAR(50),
            geom GEOMETRY(Point, 4326),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS incidents_geom_idx ON kochi_incidents USING GIST (geom);
        
        CREATE TABLE IF NOT EXISTS zyra_contacts (
            id SERIAL PRIMARY KEY,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            alias TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(name, phone)
        );
        """
        
        with conn.cursor() as cursor:
            cursor.execute(init_sql)
            print("ZYRA BRAIN: Database Memory Initialized Successfully.")
            
        conn.close()
    except Exception as e:
        print(f"ZYRA BRAIN WARNING: Database init failed (check env vars): {e}")

# ... (Rest of file)


# Security
API_KEY_NAME = "X-Zyra-Token"
# In production, load this from environment variables
EXPECTED_API_KEY = "zyra-secret-key-123" 

class Target(BaseModel):
    host: str

@app.get("/")
def read_root():
    return {"status": "Zyra Brain Online"}

def verify_token(x_zyra_token: str = Header(...)):
    if x_zyra_token != EXPECTED_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid Trust Token")

@app.post("/tools/ping")
def run_ping(target: Target, token: str = Security(verify_token)):
    """
    Executes a standard ping command.
    """
    host = target.host
    # Basic injection prevention
    if any(c in host for c in [';', '&', '|', '$']):
        raise HTTPException(status_code=400, detail="Invalid characters in host")

    try:
        # -c 4 for count, -W 2 for timeout
        cmd = ["ping", "-c", "4", "-W", "2", host]
        # Adjust for Windows if running locally without Docker
        if shutil.which("ping") is None:
             return {"error": "Ping tool not found"}

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        return {
            "success": result.returncode == 0,
            "output": result.stdout,
            "error": result.stderr
        }
    except Exception as e:
        return {"success": False, "error": str(e)}

@app.post("/tools/nmap")
def run_nmap(target: Target, token: str = Security(verify_token)):
    """
    Executes a fast nmap scan.
    """
    host = target.host
    if any(c in host for c in [';', '&', '|', '$']):
        raise HTTPException(status_code=400, detail="Invalid characters in host")
        
    if shutil.which("nmap") is None:
        raise HTTPException(status_code=500, detail="nmap not installed on server")

    try:
        # -F for fast scan, -T4 for speed
        cmd = ["nmap", "-F", "-T4", host]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return {
            "success": result.returncode == 0,
            "output": result.stdout
        }
    except Exception as e:
        return {"success": False, "error": str(e)}

@app.post("/tools/dig")
def run_dig(target: Target, token: str = Security(verify_token)):
    """
    Executes a DNS lookup.
    """
    host = target.host
    if any(c in host for c in [';', '&', '|', '$']):
        raise HTTPException(status_code=400, detail="Invalid characters in host")

    try:
        cmd = ["dig", "+short", host]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return {
            "success": result.returncode == 0,
            "output": result.stdout
        }
    except Exception as e:
        return {"success": False, "error": str(e)}

# --- New Ultra Logic Extensions ---
import requests

class PulseData(BaseModel):
    latitude: float
    longitude: float
    battery_level: Optional[int] = None
    timestamp: Optional[str] = None
    activity: Optional[str] = None
    session_id: Optional[str] = None

@app.post("/pulse")
def receive_pulse(data: PulseData, token: str = Security(verify_token)):
    """
    Receives GPS/Context pulse from Android.
    Forwards to n8n Webhook for 'Proactive' logic.
    """
    # 1. Forward to n8n Proactive Webhook (Replace URL with your actual n8n webhook)
    try:
        # TODO: User must configure this URL in env vars
        n8n_webhook_url = "https://n8n.shijin.in/webhook/zyra-pulse" 
        requests.post(n8n_webhook_url, json=data.dict(), timeout=2)
    except Exception as e:
        print(f"Warning: Failed to forward pulse to n8n: {e}")

    # 2. Return success so Android knows we got it
    return {"status": "Pulse Received", "lat": data.latitude, "lon": data.longitude}

class ToolRequest(BaseModel):
    tool_name: str
    parameters: dict

@app.post("/tool/execute")
def execute_tool(request: ToolRequest, token: str = Security(verify_token)):
    """
    Unified endpoint for n8n/MCP to execute tools dynamically.
    """
    tool = request.tool_name
    params = request.parameters
    
    if tool == "ping":
        return run_ping(Target(host=params.get("host")), token)
    elif tool == "nmap":
        return run_nmap(Target(host=params.get("host")), token)
    elif tool == "dig":
        return run_dig(Target(host=params.get("host")), token)

class Notification(BaseModel):
    title: str
    message: str
    priority: Optional[str] = "normal"

@app.post("/notify")
def send_notification(notification: Notification, token: str = Security(verify_token)):
    """
    Receives alerts from n8n (e.g., "Proactive Incident Found").
    In a real app, this would push to FCM (Firebase).
    For now, it logs the alert so you can see it in Coolify.
    """
    print(f"🚨 ZYRA ALERT: {notification.title} - {notification.message} [{notification.priority}]")
    return {"status": "Alert Logged", "data": notification.dict()}

@app.get("/livekit/token")
def get_livekit_token(token: str = Security(verify_token)):
    """
    Generates a fresh LiveKit Access Token for the Android client.
    Requires LIVEKIT_API_KEY and LIVEKIT_API_SECRET to be set in env.
    """
    api_key = os.getenv("LIVEKIT_API_KEY")
    api_secret = os.getenv("LIVEKIT_API_SECRET")
    
    if not api_key or not api_secret:
        raise HTTPException(
            status_code=500, 
            detail="LiveKit API credentials not configured on server."
        )

    # Token lasts for 1 hour by default
    participant_id = f"zyra-android-{uuid.uuid4().hex[:8]}"
    token_gen = api.AccessToken(api_key, api_secret) \
        .with_identity(participant_id) \
        .with_name("Sir") \
        .with_grants(api.VideoGrants(
            room_join=True,
            room="zyra-room",
            can_publish=True,
            can_subscribe=True,
            can_publish_data=True
        ))
    
    return {"token": token_gen.to_jwt()}


# --- Contacts Sync ---
from typing import List

class Contact(BaseModel):
    name: str
    phone: str
    alias: Optional[str] = None

class SyncContactsRequest(BaseModel):
    contacts: List[Contact]

@app.post("/sync/contacts")
def sync_contacts(request: SyncContactsRequest, token: str = Security(verify_token)):
    """
    Receives contacts from Android and saves them to the DB.
    """
    db_user = os.getenv("POSTGRES_USER", "postgres")
    db_password = os.getenv("POSTGRES_PASSWORD", "password")
    db_name = os.getenv("POSTGRES_DB", "postgres")
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    
    if db_host.startswith("http"):
        db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]

    try:
        conn = psycopg2.connect(
            dbname=db_name,
            user=db_user,
            password=db_password,
            host=db_host
        )
        conn.autocommit = True
        
        with conn.cursor() as cursor:
            # Batch upsert contacts
            for contact in request.contacts:
                cursor.execute(
                    """
                    INSERT INTO zyra_contacts (name, phone, alias) 
                    VALUES (%s, %s, %s)
                    ON CONFLICT (name, phone) DO UPDATE SET 
                    alias = EXCLUDED.alias
                    """,
                    (contact.name, contact.phone, contact.alias)
                )
        conn.close()
        return {"status": "Success", "synced_count": len(request.contacts)}
    except Exception as e:
        print(f"Error syncing contacts: {e}")
        raise HTTPException(status_code=500, detail="Database error during sync")

# --- 2026 Gemini Voice Gateway (Direct-to-Brain) ---
# This bypasses LiveKit and connects Android directly to Gemini 3.1 Flash-Live

@app.websocket("/voice")
async def voice_endpoint(websocket: WebSocket):
    """
    Real-time Voice Gateway for Zyra.
    Connects Android App -> FastAPI -> Gemini 3.1 Flash-Live.
    """
    await websocket.accept()
    print("🎙️ ZYRA VOICE: Android Client Connected.")

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("❌ ZYRA VOICE ERROR: GEMINI_API_KEY not found in environment.")
        await websocket.close(code=1008)
        return

    # Initialize 2026 GenAI Client
    client = genai.Client(api_key=api_key, http_options={'api_version': 'v1beta'})
    
    # Configure the 'Ultra-Cheap' Talker (Gemini 3.1 Flash-Lite)
    config = {
        "model": "models/gemini-3.1-flash-lite-001",
        "generation_config": {
            "response_modalities": ["TEXT"] # Text is 100x cheaper than Audio output
        }
    }

    try:
        async with client.aio.live.connect(model=config["model"], config=config) as session:
            
            async def receive_from_android():
                """Forwards audio chunks from Android to Gemini."""
                try:
                    async for message in websocket.iter_json():
                        # Expecting: {"audio": "base64_data"}
                        if "audio" in message:
                            audio_data = base64.b64decode(message["audio"])
                            await session.send(audio_data, end_of_turn=False)
                except Exception as e:
                    print(f"ZYRA VOICE: Android input stream closed: {e}")

            async def send_to_android():
                """Forwards text replies from Gemini to Android for local TTS."""
                try:
                    async for response in session.receive():
                        if response.text:
                            # Send text to Android. 
                            # The Android app uses its built-in TTS (Free) to speak.
                            await websocket.send_json({"text": response.text})
                except Exception as e:
                    print(f"ZYRA VOICE: Gemini output stream closed: {e}")

            # Run both directions in parallel
            await asyncio.gather(receive_from_android(), send_to_android())

    except WebSocketDisconnect:
        print("🎙️ ZYRA VOICE: Android Client Disconnected.")
    except Exception as e:
        print(f"❌ ZYRA VOICE CRITICAL ERROR: {e}")
        await websocket.close(code=1011)
