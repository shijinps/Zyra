import os
import json
import logging
import urllib.request
import urllib.parse
from typing import Dict, Any, Optional

logger = logging.getLogger("ZyraTools")

def get_driving_eta(origin: str, destination: str) -> str:
    """Calculates road distance and driving ETA between two locations."""
    try:
        def geocode(place: str):
            q = urllib.parse.quote(place)
            url = f"https://nominatim.openstreetmap.org/search?q={q}&format=json&limit=1"
            req = urllib.request.Request(url, headers={"User-Agent": "ZyraAssistant/1.0"})
            with urllib.request.urlopen(req, timeout=5) as r:
                res = json.loads(r.read().decode())
                if res:
                    return float(res[0]["lat"]), float(res[0]["lon"])
            return None, None

        lat1, lon1 = geocode(origin)
        lat2, lon2 = geocode(destination)

        if not lat1 or not lat2:
            return f"I couldn't locate '{origin}' or '{destination}', Shijin."

        route_url = f"https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false"
        req = urllib.request.Request(route_url, headers={"User-Agent": "ZyraAssistant/1.0"})
        with urllib.request.urlopen(req, timeout=6) as r:
            data = json.loads(r.read().decode())
            if not data.get("routes"):
                return f"No drivable route found between {origin} and {destination}."
            route = data["routes"][0]
            dist_km = round(route["distance"] / 1000, 1)
            dur_mins = round(route["duration"] / 60)
            
            hours = dur_mins // 60
            mins = dur_mins % 60
            time_str = f"{hours} hr {mins} mins" if hours > 0 else f"{mins} mins"
            return f"Distance from {origin} to {destination} is {dist_km} km. Estimated driving time is {time_str}."
    except Exception as e:
        logger.error(f"Routing error: {e}")
        return f"Couldn't calculate distance: {e}"

def get_market_price(asset: str) -> str:
    """Retrieves live prices for stocks (NSE/BSE/US) or cryptocurrencies."""
    query = asset.strip().lower()
    
    # 1. Crypto check
    crypto_aliases = {
        "btc": "bitcoin", "bitcoin": "bitcoin",
        "eth": "ethereum", "ethereum": "ethereum",
        "sol": "solana", "solana": "solana",
        "doge": "dogecoin", "dogecoin": "dogecoin",
        "xrp": "ripple", "ripple": "ripple"
    }
    
    crypto_id = crypto_aliases.get(query)
    if crypto_id:
        try:
            url = f"https://api.coingecko.com/api/v3/simple/price?ids={crypto_id}&vs_currencies=inr,usd"
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=5) as r:
                data = json.loads(r.read().decode())
                prices = data.get(crypto_id, {})
                inr = prices.get("inr", 0)
                usd = prices.get("usd", 0)
                return f"{crypto_id.capitalize()} is currently trading at ₹{inr:,} (\\${usd:,})."
        except Exception as e:
            logger.error(f"Crypto fetch error: {e}")

    # 2. Stock check (Yahoo Finance)
    sym_query = query.upper()
    candidates = [sym_query]
    if "." not in sym_query:
        candidates = [f"{sym_query}.NS", sym_query]

    for sym in candidates:
        try:
            url = f"https://query1.finance.yahoo.com/v8/finance/chart/{sym}?interval=1d"
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=5) as r:
                data = json.loads(r.read().decode())
                meta = data["chart"]["result"][0]["meta"]
                price = meta.get("regularMarketPrice")
                currency = meta.get("currency", "INR")
                name = meta.get("longName") or meta.get("shortName") or meta.get("symbol")
                return f"{name} ({sym}) is currently trading at {price} {currency}."
        except Exception:
            continue

    return f"I couldn't fetch the market price for '{asset}', Shijin."

def translate_phrase(text: str, target_language: str = "Malayalam") -> str:
    """Translates text into the target language using Gemini AI."""
    gemini_key = os.getenv("GEMINI_API_KEY", "")
    if not gemini_key:
        return f"Gemini API key is required for instant translation."

    prompt = (
        f"Translate the following phrase accurately into {target_language}. "
        f"Provide the native script translation followed by phonetic English pronunciation in brackets if applicable. "
        f"Keep the answer concise (1 sentence).\n\nPhrase: \"{text}\""
    )
    
    try:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={gemini_key}"
        payload = {"contents": [{"parts": [{"text": prompt}]}]}
        req_data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=req_data, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=6) as r:
            res = json.loads(r.read().decode())
            return res["candidates"][0]["content"]["parts"][0]["text"].strip()
    except Exception as e:
        logger.error(f"Translation error: {e}")
        return f"Couldn't translate: {e}"
