import os
import json
import logging
import urllib.request
import urllib.parse
from typing import Dict, List, Optional
from datetime import datetime

logger = logging.getLogger("ZyraLiveStreamWorker")
logger.setLevel(logging.INFO)

try:
    from rag_engine import rag_engine
except ImportError:
    from server.fastapi.rag_engine import rag_engine

try:
    import browser_engine
except ImportError:
    from server.fastapi import browser_engine

# Curated High-Priority Live News Streams
LIVE_CHANNELS = [
    {
        "name": "Asianet News Live",
        "url": "https://www.youtube.com/@asianetnews/live",
        "language": "malayalam",
        "category": "news_malayalam_live"
    },
    {
        "name": "24 News Live",
        "url": "https://www.youtube.com/@24onlive/live",
        "language": "malayalam",
        "category": "news_malayalam_live"
    },
    {
        "name": "Manorama News Live",
        "url": "https://www.youtube.com/@manoramanews/live",
        "language": "malayalam",
        "category": "news_malayalam_live"
    },
    {
        "name": "Mathrubhumi News Live",
        "url": "https://www.youtube.com/@MathrubhumiNews/live",
        "language": "malayalam",
        "category": "news_malayalam_live"
    },
    {
        "name": "NDTV 24x7 Live",
        "url": "https://www.youtube.com/@ndtv/live",
        "language": "english",
        "category": "news_national_live"
    },
    {
        "name": "India Today Live",
        "url": "https://www.youtube.com/@IndiaToday/live",
        "language": "english",
        "category": "news_national_live"
    }
]

# Cache of last known titles to avoid duplicate RAG insertions
_last_known_titles: Dict[str, str] = {}

def extract_live_metadata(channel_info: Dict[str, str]) -> Optional[Dict[str, str]]:
    """
    Extracts live stream title and description using Browserless Headless Chrome.
    Fast, uses zero video bandwidth.
    """
    channel_name = channel_info["name"]
    live_url = channel_info["url"]
    
    try:
        # Use browser_engine to extract clean page text
        page_summary = browser_engine.get_page_content(live_url)
        if not page_summary or "Couldn't browse" in page_summary:
            logger.warning(f"Could not reach {channel_name} live page.")
            return None

        # Clean and extract title
        # In YouTube live pages, the title is prominent in the summary
        first_segment = page_summary.split(".")[0].replace("Summary from", "").strip()
        
        # Check against last known title
        prev_title = _last_known_titles.get(channel_name, "")
        if first_segment == prev_title:
            logger.debug(f"No new title change for {channel_name}.")
            return None

        _last_known_titles[channel_name] = first_segment
        now_str = datetime.utcnow().strftime("%Y-%m-%d %H:%M UTC")

        headline = f"[{channel_name} Live Update - {now_str}]: {first_segment}"
        
        # Ingest into RAG Vector Store
        rag_engine.store_memory(
            content=headline,
            category=channel_info.get("category", "news_malayalam_live"),
            source="youtube_live_stream",
            role="system"
        )
        logger.info(f"Ingested live news headline: {headline}")
        return {"channel": channel_name, "headline": headline}
    except Exception as e:
        logger.error(f"Error extracting live metadata for {channel_name}: {e}")
        return None

def poll_all_live_streams() -> List[Dict[str, str]]:
    """
    Iterates through all curated live streams and ingests new breaking updates.
    """
    updates = []
    for ch in LIVE_CHANNELS:
        res = extract_live_metadata(ch)
        if res:
            updates.append(res)
    return updates

if __name__ == "__main__":
    print("Testing Live Stream Poller...")
    res = poll_all_live_streams()
    print(f"Extraction completed. Found {len(res)} updates.")
