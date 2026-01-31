from fastapi import FastAPI, HTTPException, Header, Security
from pydantic import BaseModel
import subprocess
import shutil
import os
import psycopg2
from typing import Optional

# ... (Existing code)

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
        """
        
        with conn.cursor() as cursor:
            cursor.execute(init_sql)
            print("ZYRA BRAIN: Database Memory Initialized Successfully.")
            
        conn.close()
    except Exception as e:
        print(f"ZYRA BRAIN WARNING: Database init failed (check env vars): {e}")

# ... (Rest of file)

app = FastAPI(title="Zyra Technical Brain")

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
    else:
        raise HTTPException(status_code=404, detail=f"Tool '{tool}' not found")

