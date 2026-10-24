import os
import json
import logging
import urllib.request
import urllib.parse
from typing import Dict, Any, Optional

logger = logging.getLogger("ZyraBrowserless")

BROWSERLESS_URL = os.getenv("BROWSERLESS_URL", "http://browserless:3000")
BROWSERLESS_TOKEN = os.getenv("BROWSERLESS_TOKEN", "")

def get_page_content(url: str, wait_for_selector: Optional[str] = None) -> str:
    """
    Uses Browserless (Headless Chromium) to render JavaScript and extract
    the live text content of any website.
    """
    clean_url = url.strip()
    if not clean_url.startswith("http://") and not clean_url.startswith("https://"):
        clean_url = f"https://{clean_url}"

    api_endpoint = f"{BROWSERLESS_URL.rstrip('/')}/content"
    if BROWSERLESS_TOKEN:
        api_endpoint += f"?token={BROWSERLESS_TOKEN}"

    payload: Dict[str, Any] = {
        "url": clean_url,
        "bestAttempt": True,
        "gotoOptions": {"waitUntil": "networkidle2", "timeout": 15000}
    }
    if wait_for_selector:
        payload["waitForSelector"] = {"selector": wait_for_selector}

    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        api_endpoint,
        data=req_data,
        headers={"Content-Type": "application/json", "User-Agent": "ZyraBrowser/1.0"}
    )

    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            # Strip HTML tags into clean text
            from html.parser import HTMLParser
            class HTMLTextExtractor(HTMLParser):
                def __init__(self):
                    super().__init__()
                    self.text = []
                def handle_data(self, data):
                    d = data.strip()
                    if d:
                        self.text.append(d)
                def get_text(self):
                    return " ".join(self.text)

            parser = HTMLTextExtractor()
            parser.feed(html)
            text = parser.get_text()
            
            # Truncate to first 1200 characters for concise spoken voice context
            if len(text) > 1200:
                text = text[:1200] + "..."
            return f"Summary from {clean_url}: {text}"
    except Exception as e:
        logger.warning(f"Browserless error visiting {clean_url}: {e}. Falling back to direct HTTP fetch...")
        try:
            direct_req = urllib.request.Request(
                clean_url,
                headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
            )
            with urllib.request.urlopen(direct_req, timeout=12) as direct_resp:
                html = direct_resp.read().decode("utf-8", errors="ignore")
                from html.parser import HTMLParser
                class HTMLTextExtractor(HTMLParser):
                    def __init__(self):
                        super().__init__()
                        self.text = []
                    def handle_data(self, data):
                        d = data.strip()
                        if d:
                            self.text.append(d)
                    def get_text(self):
                        return " ".join(self.text)

                parser = HTMLTextExtractor()
                parser.feed(html)
                text = parser.get_text()
                if len(text) > 1200:
                    text = text[:1200] + "..."
                return f"Summary from {clean_url}: {text}"
        except Exception as direct_err:
            logger.error(f"Direct fetch also failed for {clean_url}: {direct_err}")
            return f"Couldn't browse {clean_url}: {direct_err}"

def take_screenshot(url: str, output_path: str = "/tmp/screenshot.png") -> str:
    """Captures a screenshot of a live web page using Browserless."""
    clean_url = url.strip()
    if not clean_url.startswith("http://") and not clean_url.startswith("https://"):
        clean_url = f"https://{clean_url}"

    api_endpoint = f"{BROWSERLESS_URL.rstrip('/')}/screenshot"
    if BROWSERLESS_TOKEN:
        api_endpoint += f"?token={BROWSERLESS_TOKEN}"

    payload = {
        "url": clean_url,
        "options": {"type": "png", "fullPage": False}
    }
    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        api_endpoint,
        data=req_data,
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            png_bytes = resp.read()
            with open(output_path, "wb") as f:
                f.write(png_bytes)
            return f"Screenshot of {clean_url} saved to {output_path}."
    except Exception as e:
        return f"Failed to capture screenshot: {e}"
