from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from ppadb.client import Client as AdbClient
import time
import os

app = FastAPI(title="Zyra ADB Bridge", description="Controls Cloud Android via ADB")

class TapRequest(BaseModel):
    x: int
    y: int

class TextRequest(BaseModel):
    text: str

class AppLaunchRequest(BaseModel):
    package_name: str
    activity_name: str = ""

def get_device():
    # Connect to the local adb server
    # The adb server might be running inside the container or accessible via network
    # For now, we connect to 127.0.0.1:5037 (default local ADB server)
    client = AdbClient(host="127.0.0.1", port=5037)
    
    # In a Docker environment, we might need to connect to the redroid container explicitly
    # client.remote_connect("redroid", 5555)
    
    devices = client.devices()
    if len(devices) == 0:
        raise HTTPException(status_code=503, detail="No Android devices attached.")
    return devices[0]

@app.get("/health")
def health_check():
    try:
        device = get_device()
        return {"status": "ok", "device_serial": device.serial}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/action/tap")
def action_tap(req: TapRequest):
    device = get_device()
    device.shell(f"input tap {req.x} {req.y}")
    return {"status": "success", "action": f"tapped at {req.x}, {req.y}"}

@app.post("/action/type")
def action_type(req: TextRequest):
    device = get_device()
    # Escape spaces for ADB input
    escaped_text = req.text.replace(" ", "%s")
    device.shell(f"input text {escaped_text}")
    return {"status": "success", "action": f"typed text: {req.text}"}

@app.post("/action/launch")
def action_launch(req: AppLaunchRequest):
    device = get_device()
    if req.activity_name:
        cmd = f"am start -n {req.package_name}/{req.activity_name}"
    else:
        # Launch default main activity
        cmd = f"monkey -p {req.package_name} -c android.intent.category.LAUNCHER 1"
    
    output = device.shell(cmd)
    return {"status": "success", "output": output}

@app.get("/action/screenshot")
def action_screenshot():
    device = get_device()
    # Take a screenshot and return as bytes or save locally
    result = device.screencap()
    # Here we would normally return the image or process it via OCR
    # For now, we just indicate success
    return {"status": "success", "message": f"Screenshot taken, {len(result)} bytes"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
