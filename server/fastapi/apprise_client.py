import os
import json
import logging
import urllib.request
from typing import Optional, List, Dict, Any

logger = logging.getLogger("ZyraApprise")

APPRISE_URL = os.getenv("APPRISE_URL", "http://apprise:8000")
DEFAULT_TARGET_URLS = os.getenv("APPRISE_TARGET_URLS", "")

def send_notification(
    title: str, 
    body: str, 
    notify_type: str = "info", 
    target_urls: Optional[str] = None
) -> Dict[str, Any]:
    """
    Sends multi-channel alerts via Apprise API to Discord, Telegram, Email, etc.
    """
    urls_to_notify = target_urls or DEFAULT_TARGET_URLS
    if not urls_to_notify:
        logger.info("No Apprise target URLs configured. Set APPRISE_TARGET_URLS.")
        return {"status": "skipped", "reason": "No notification URLs defined"}

    payload = {
        "urls": urls_to_notify,
        "title": f"Zyra Alert: {title}",
        "body": body,
        "type": notify_type
    }

    endpoint = f"{APPRISE_URL.rstrip('/')}/notify"
    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        endpoint,
        data=req_data,
        headers={"Content-Type": "application/json", "User-Agent": "ZyraApprise/1.0"}
    )

    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = resp.read().decode("utf-8")
            logger.info(f"Apprise notification sent: {title}")
            return {"status": "success", "response": data}
    except Exception as e:
        logger.error(f"Failed to dispatch notification via Apprise: {e}")
        return {"status": "error", "message": str(e)}
