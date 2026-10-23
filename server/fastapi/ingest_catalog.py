import os
import sys
import json
import re

# Ensure path to local modules
sys.path.insert(0, os.path.dirname(__file__))

from rag_engine import rag_engine

CATALOG_MD_PATH = os.path.join(
    os.path.expanduser("~"),
    ".gemini",
    "antigravity",
    "brain",
    "02bdbfbf-a26b-4b28-9261-98839430935d",
    "zyra_intelligence_feed_categories.md"
)

def ingest_master_catalog():
    print(f"Loading master catalog from: {CATALOG_MD_PATH}")
    if not os.path.exists(CATALOG_MD_PATH):
        print("Catalog markdown file not found!")
        return

    with open(CATALOG_MD_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Split into sections by Markdown header ## or ###
    sections = re.split(r'\n(?=#{2,3}\s)', content)
    total_chunks = 0

    print(f"Extracted {len(sections)} sections from catalog.")

    for sec in sections:
        sec = sec.strip()
        if not sec:
            continue

        # Extract title
        first_line = sec.split('\n')[0].replace('#', '').strip()
        
        # Determine category tag
        cat_tag = "intelligence_catalog"
        lower_title = first_line.lower()
        if "malayalam news" in lower_title or "kerala" in lower_title:
            cat_tag = "news_malayalam"
        elif "national news" in lower_title:
            cat_tag = "news_national"
        elif "cybersecurity" in lower_title:
            cat_tag = "threat_intel"
        elif "cve" in lower_title or "vulnerability" in lower_title:
            cat_tag = "cve_intel"
        elif "podcast" in lower_title:
            cat_tag = "podcast_directory"
        elif "sports" in lower_title:
            cat_tag = "sports_feed"
        elif "novels" in lower_title or "literature" in lower_title:
            cat_tag = "literature_ebooks"
        elif "music" in lower_title:
            cat_tag = "music_directory"
        elif "ai" in lower_title or "deep learning" in lower_title:
            cat_tag = "ai_research"
        elif "linux" in lower_title or "open source" in lower_title:
            cat_tag = "linux_foss"
        elif "cloud" in lower_title or "devops" in lower_title:
            cat_tag = "devops_cloud"
        elif "stock" in lower_title or "finance" in lower_title:
            cat_tag = "finance_markets"

        # If the section is long, chunk it into sub-blocks of ~5-8 items
        lines = sec.split('\n')
        header = lines[0]
        item_lines = [l for l in lines[1:] if l.strip()]

        chunk_size = 6
        for i in range(0, max(len(item_lines), 1), chunk_size):
            chunk_items = item_lines[i:i+chunk_size]
            chunk_text = f"{header}\n" + "\n".join(chunk_items)
            
            rag_engine.store_memory(
                content=chunk_text,
                category=cat_tag,
                source="master_intelligence_catalog",
                role="system"
            )
            total_chunks += 1

    print(f"Successfully ingested {total_chunks} intelligence chunks into RAG store!")

if __name__ == "__main__":
    ingest_master_catalog()
