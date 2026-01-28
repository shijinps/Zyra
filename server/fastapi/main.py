from fastapi import FastAPI, HTTPException, Header, Security
from pydantic import BaseModel
import subprocess
import shutil
from typing import Optional

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
