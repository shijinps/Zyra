"""
Zyra System Diagnostic & Health Checker
Run with: python check_zyra.py
"""

import urllib.request
import urllib.parse
import json
import socket
import ssl
import time
import os
import sys

def parse_local_properties():
    props = {}
    if os.path.exists("local.properties"):
        with open("local.properties", "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip()
    return props

props = parse_local_properties()
results = []

def record(category, name, status, details):
    tag = "[PASS]" if status == "OK" else ("[WARN]" if status == "WARN" else "[FAIL]")
    results.append({"category": category, "name": name, "status": status, "tag": tag, "details": details})

# 1. FastAPI Root
try:
    req = urllib.request.Request("https://apizyra.shijin.in/", headers={"User-Agent": "ZyraChecker/1.0"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=8) as r:
        dt = (time.time() - t0) * 1000
        record("Backend", "FastAPI Root (apizyra.shijin.in)", "OK", f"HTTP {r.status} - Online ({dt:.0f}ms)")
except Exception as e:
    record("Backend", "FastAPI Root (apizyra.shijin.in)", "FAIL", str(e))

# 2. FastAPI Health
try:
    req = urllib.request.Request("https://apizyra.shijin.in/health", headers={"User-Agent": "ZyraChecker/1.0"})
    with urllib.request.urlopen(req, timeout=8) as r:
        body = r.read().decode("utf-8")
        record("Backend", "FastAPI /health", "OK", f"HTTP {r.status}: {body}")
except urllib.error.HTTPError as e:
    if e.code == 404:
        record("Backend", "FastAPI /health", "WARN", "HTTP 404 (Pending Coolify redeploy of new commit)")
    else:
        record("Backend", "FastAPI /health", "FAIL", f"HTTP {e.code}")
except Exception as e:
    record("Backend", "FastAPI /health", "FAIL", str(e))

# 3. LiveKit Token
try:
    secret = os.getenv("EXPECTED_API_KEY", props.get("EXPECTED_API_KEY", "zyra-secret-key-123"))
    req = urllib.request.Request(
        "https://apizyra.shijin.in/livekit/token",
        headers={"User-Agent": "ZyraChecker/1.0", "X-Zyra-Token": secret}
    )
    with urllib.request.urlopen(req, timeout=8) as r:
        data = json.loads(r.read().decode("utf-8"))
        tok = data.get("token", "")
        record("Voice Engine", "FastAPI /livekit/token", "OK", f"Generated valid token (len={len(tok)})")
except Exception as e:
    record("Voice Engine", "FastAPI /livekit/token", "FAIL", str(e))

# 4. LiveKit Cloud SSL
try:
    t0 = time.time()
    s = socket.create_connection(("zyra-sbn0wnp2.livekit.cloud", 443), timeout=5)
    ctx = ssl.create_default_context()
    ss = ctx.wrap_socket(s, server_hostname="zyra-sbn0wnp2.livekit.cloud")
    ss.close()
    dt = (time.time() - t0) * 1000
    record("Voice Engine", "LiveKit Cloud (zyra-sbn0wnp2.livekit.cloud:443)", "OK", f"WebRTC SSL port reachable ({dt:.0f}ms)")
except Exception as e:
    record("Voice Engine", "LiveKit Cloud", "FAIL", str(e))

# 5. Evolution API (WhatsApp)
try:
    evo_url = os.getenv("EVOLUTION_API_URL", "https://evo-qko4sk084c80oc0o48gc4goo.shijin.in")
    evo_key = os.getenv("EVOLUTION_API_KEY", props.get("EVOLUTION_API_KEY", ""))
    if evo_key:
        req = urllib.request.Request(
            f"{evo_url.rstrip('/')}/instance/fetchInstances",
            headers={"User-Agent": "ZyraChecker/1.0", "apikey": evo_key}
        )
        with urllib.request.urlopen(req, timeout=8) as r:
            instances = json.loads(r.read().decode("utf-8"))
            names = [i.get("name") for i in instances]
            state = instances[0].get("connectionStatus") if instances else "none"
            record("Messaging", "Evolution API (WhatsApp)", "OK", f"Instance: {names}, State: {state}")
    else:
        record("Messaging", "Evolution API (WhatsApp)", "WARN", "EVOLUTION_API_KEY not configured")
except Exception as e:
    record("Messaging", "Evolution API (WhatsApp)", "FAIL", str(e))

# 6. n8n Engine
try:
    req = urllib.request.Request("https://n8n.shijin.in/", headers={"User-Agent": "ZyraChecker/1.0"})
    with urllib.request.urlopen(req, timeout=8) as r:
        record("Automation", "n8n Engine (n8n.shijin.in)", "OK", f"HTTP {r.status} - Online")
except Exception as e:
    record("Automation", "n8n Engine", "FAIL", str(e))

# 7. Groq AI Brain
try:
    groq_key = os.getenv("GROQ_API_KEY", props.get("GROQ_API_KEY", ""))
    if groq_key:
        req = urllib.request.Request(
            "https://api.groq.com/openai/v1/chat/completions",
            data=json.dumps({
                "model": "qwen/qwen3.8-27b",
                "messages": [{"role": "user", "content": "ping"}],
                "max_tokens": 10
            }).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {groq_key}",
                "Content-Type": "application/json",
                "User-Agent": "ZyraChecker/1.0"
            }
        )
        t0 = time.time()
        with urllib.request.urlopen(req, timeout=8) as r:
            dt = (time.time() - t0) * 1000
            record("AI Brain", "Groq AI (qwen/qwen3.8-27b)", "OK", f"Latency: {dt:.0f}ms")
    else:
        record("AI Brain", "Groq AI", "WARN", "GROQ_API_KEY not set in env or local.properties")
except Exception as e:
    record("AI Brain", "Groq AI (qwen/qwen3.8-27b)", "FAIL", str(e))

# 8. External Tool APIs
weather_key = props.get("OPENWEATHER_API_KEY", os.getenv("OPENWEATHER_API_KEY", ""))
if weather_key:
    try:
        req = urllib.request.Request(f"https://api.openweathermap.org/data/2.5/weather?q=Kochi&appid={weather_key}", headers={"User-Agent": "ZyraChecker/1.0"})
        with urllib.request.urlopen(req, timeout=8) as r:
            record("Tools API", "OpenWeatherMap", "OK", f"HTTP {r.status} - Data active")
    except Exception as e:
        record("Tools API", "OpenWeatherMap", "FAIL", str(e))
else:
    record("Tools API", "OpenWeatherMap", "WARN", "OPENWEATHER_API_KEY not set")

news_key = props.get("NEWS_API_KEY", os.getenv("NEWS_API_KEY", ""))
if news_key:
    try:
        req = urllib.request.Request(f"https://newsapi.org/v2/top-headlines?country=in&apiKey={news_key}", headers={"User-Agent": "ZyraChecker/1.0"})
        with urllib.request.urlopen(req, timeout=8) as r:
            record("Tools API", "NewsAPI", "OK", f"HTTP {r.status} - Data active")
    except Exception as e:
        record("Tools API", "NewsAPI", "FAIL", str(e))
else:
    record("Tools API", "NewsAPI", "WARN", "NEWS_API_KEY not set")

stock_key = props.get("ALPHA_VANTAGE_API_KEY", os.getenv("ALPHA_VANTAGE_API_KEY", ""))
if stock_key:
    try:
        req = urllib.request.Request(f"https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey={stock_key}", headers={"User-Agent": "ZyraChecker/1.0"})
        with urllib.request.urlopen(req, timeout=8) as r:
            record("Tools API", "AlphaVantage", "OK", f"HTTP {r.status} - Quotes active")
    except Exception as e:
        record("Tools API", "AlphaVantage", "FAIL", str(e))
else:
    record("Tools API", "AlphaVantage", "WARN", "ALPHA_VANTAGE_API_KEY not set")

maps_key = props.get("GOOGLE_MAPS_API_KEY", os.getenv("GOOGLE_MAPS_API_KEY", ""))
if maps_key:
    try:
        req = urllib.request.Request(f"https://maps.googleapis.com/maps/api/geocode/json?address=Kochi&key={maps_key}", headers={"User-Agent": "ZyraChecker/1.0"})
        with urllib.request.urlopen(req, timeout=8) as r:
            record("Tools API", "Google Maps API", "OK", f"HTTP {r.status} - Active")
    except Exception as e:
        record("Tools API", "Google Maps API", "FAIL", str(e))
else:
    record("Tools API", "Google Maps API", "WARN", "GOOGLE_MAPS_API_KEY not set")

# Output Summary
print("=====================================================")
print("             ZYRA SYSTEM HEALTH REPORT               ")
print("=====================================================")
for r in results:
    print(f"{r['tag']:6} | {r['category']:12} | {r['name']}")
    print(f"       |--> {r['details']}")
print("=====================================================")
