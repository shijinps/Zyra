import os
import json
import math
import logging
import urllib.request
import urllib.error
import hashlib
from typing import List, Dict, Optional, Tuple
from datetime import datetime

logger = logging.getLogger("ZyraRAG")
logger.setLevel(logging.INFO)

LOCAL_STORE_PATH = os.path.join(os.path.dirname(__file__), "rag_store.json")
EMBEDDING_DIM = 768

def _hash_vector(text: str, dim: int = EMBEDDING_DIM) -> List[float]:
    """
    Deterministic fall-back embedding generator based on character n-grams and hashing.
    Used when external embedding API is unreachable.
    """
    vec = [0.0] * dim
    words = text.lower().split()
    if not words:
        return vec
        
    for w in words:
        h = hash(w) % dim
        vec[h] += 1.0
        for i in range(len(w) - 2):
            bg = w[i:i+3]
            h_bg = hash(bg) % dim
            vec[h_bg] += 0.5
            
    norm = math.sqrt(sum(x * x for x in vec))
    if norm > 0:
        vec = [x / norm for x in vec]
    return vec

def get_embedding(text: str) -> List[float]:
    """
    Generates embedding for text using Google Gemini text-embedding-004.
    Falls back gracefully to local deterministic vector if offline or quota exceeded.
    """
    cleaned = text.strip()
    if not cleaned:
        return [0.0] * EMBEDDING_DIM

    gemini_key = os.getenv("GEMINI_API_KEY", "")
    if gemini_key:
        for model in ["models/gemini-embedding-001", "models/text-embedding-004"]:
            try:
                url = f"https://generativelanguage.googleapis.com/v1beta/{model}:embedContent?key={gemini_key}"
                payload = {
                    "model": model,
                    "content": {"parts": [{"text": cleaned}]},
                    "outputDimensionality": EMBEDDING_DIM
                }
                req_data = json.dumps(payload).encode("utf-8")
                req = urllib.request.Request(
                    url,
                    data=req_data,
                    headers={"Content-Type": "application/json", "User-Agent": "ZyraRAG/1.0"}
                )
                with urllib.request.urlopen(req, timeout=4) as response:
                    result = json.loads(response.read().decode("utf-8"))
                    values = result.get("embedding", {}).get("values")
                    if values and len(values) == EMBEDDING_DIM:
                        return values
            except Exception as e:
                logger.debug(f"Gemini embedding API call ({model}) skipped: {e}.")

    return _hash_vector(cleaned, EMBEDDING_DIM)

def cosine_similarity(v1: List[float], v2: List[float]) -> float:
    """Calculates cosine similarity between two normalized float vectors."""
    if not v1 or not v2 or len(v1) != len(v2):
        return 0.0
    dot = sum(a * b for a, b in zip(v1, v2))
    norm_a = math.sqrt(sum(a * a for a in v1))
    norm_b = math.sqrt(sum(b * b for b in v2))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)

class RAGEngine:
    def __init__(self):
        self.local_memories: List[Dict] = []
        self._recent_hashes: set = set()
        self._last_mtime: float = 0.0
        self._load_local_store()
        self._init_defaults()
        self._init_postgres()

    def _get_db_connection(self):
        try:
            import psycopg2
            db_user = os.getenv("POSTGRES_USER", "postgres")
            db_password = os.getenv("POSTGRES_PASSWORD", "password")
            db_name = os.getenv("POSTGRES_DB", "postgres")
            db_host = os.getenv("POSTGRES_HOST", "postgres")
            db_port = int(os.getenv("POSTGRES_PORT", "5432"))
            if db_host.startswith("http"):
                db_host = db_host.replace("https://", "").replace("http://", "").split("/")[0]

            conn = psycopg2.connect(
                dbname=db_name,
                user=db_user,
                password=db_password,
                host=db_host,
                port=db_port,
                connect_timeout=2
            )
            return conn
        except Exception:
            return None

    def _init_postgres(self):
        """Initializes permanent pgvector table with no expiration."""
        conn = self._get_db_connection()
        if not conn:
            logger.info("PostgreSQL unreachable, operating in local JSON vector mode.")
            return

        try:
            conn.autocommit = True
            with conn.cursor() as cur:
                try:
                    cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                except Exception as ext_e:
                    logger.warning(f"Could not install vector extension: {ext_e}")

                cur.execute("""
                    CREATE TABLE IF NOT EXISTS zyra_memories (
                        id SERIAL PRIMARY KEY,
                        content TEXT NOT NULL,
                        category VARCHAR(50) DEFAULT 'daily_dialogue',
                        source VARCHAR(50) DEFAULT 'voice',
                        role VARCHAR(20) DEFAULT 'user',
                        session_id VARCHAR(64) DEFAULT '',
                        embedding vector(768),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                # Migrations: ensure columns exist for older tables
                cur.execute("ALTER TABLE zyra_memories ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'user';")
                cur.execute("ALTER TABLE zyra_memories ADD COLUMN IF NOT EXISTS session_id VARCHAR(64) DEFAULT '';")
                cur.execute("CREATE INDEX IF NOT EXISTS idx_zyra_memories_created ON zyra_memories (created_at DESC);")
            conn.close()
            logger.info("PostgreSQL permanent pgvector memory table initialized successfully.")
        except Exception as e:
            logger.warning(f"PostgreSQL RAG init notice: {e}")
            try:
                conn.close()
            except Exception:
                pass

    def _load_local_store(self):
        if os.path.exists(LOCAL_STORE_PATH):
            try:
                mtime = os.path.getmtime(LOCAL_STORE_PATH)
                if mtime > self._last_mtime:
                    with open(LOCAL_STORE_PATH, "r", encoding="utf-8") as f:
                        loaded = json.load(f)
                        if isinstance(loaded, list):
                            self.local_memories = loaded
                            self._last_mtime = mtime
                            # Cache recent turn hashes to prevent duplicates
                            for m in self.local_memories[-300:]:
                                h = hashlib.md5(m.get("content", "").strip().lower().encode("utf-8")).hexdigest()
                                self._recent_hashes.add(h)
            except Exception as e:
                logger.warning(f"Failed to read {LOCAL_STORE_PATH}: {e}")

    def _save_local_store(self):
        try:
            # Permanent store: NO TRUNCATION, NO EXPIRY
            with open(LOCAL_STORE_PATH, "w", encoding="utf-8") as f:
                json.dump(self.local_memories, f, ensure_ascii=False, indent=2)
            if os.path.exists(LOCAL_STORE_PATH):
                self._last_mtime = os.path.getmtime(LOCAL_STORE_PATH)
        except Exception as e:
            logger.warning(f"Failed to persist {LOCAL_STORE_PATH}: {e}")

    def _init_defaults(self):
        """Seeds initial companion knowledge if store is completely empty."""
        if not self.local_memories:
            seeds = [
                ("Shijin is the creator, user, and close buddy of Zyra.", "identity", "user"),
                ("Zyra (Zay) is Shijin's best friend, personal confidante, and AI companion.", "identity", "assistant"),
                ("Zyra communicates with warm, punchy 1-2 sentence replies and never calls Shijin 'Sir'.", "personality", "assistant"),
                ("Zyra can initiate phone calls, send WhatsApp messages, check weather, read latest news, and remember all daily thoughts and discussions.", "capabilities", "assistant")
            ]
            for content, cat, r in seeds:
                self.store_memory(content, category=cat, source="system_seed", role=r)

    def store_memory(
        self, 
        content: str, 
        category: str = "daily_dialogue", 
        source: str = "voice",
        role: str = "user",
        session_id: str = ""
    ) -> Dict:
        """
        Embeds and permanently stores a conversation turn, thought, or fact into DB and local store.
        No deletion, no expiry.
        """
        self._load_local_store()
        cleaned = content.strip()
        if not cleaned:
            return {"error": "Content cannot be empty"}

        # Deduplication check
        h = hashlib.md5(cleaned.lower().encode("utf-8")).hexdigest()
        if h in self._recent_hashes:
            return {"status": "skipped", "reason": "duplicate_recent"}
        self._recent_hashes.add(h)
        if len(self._recent_hashes) > 500:
            self._recent_hashes.clear()

        embedding = get_embedding(cleaned)
        now_iso = datetime.utcnow().isoformat()
        mem_id = f"mem_{int(datetime.utcnow().timestamp() * 1000)}"

        memory_item = {
            "id": mem_id,
            "session_id": session_id,
            "role": role,
            "content": cleaned,
            "category": category,
            "source": source,
            "embedding": embedding,
            "created_at": now_iso
        }
        self.local_memories.append(memory_item)
        self._save_local_store()

        # Insert permanently into PostgreSQL
        conn = self._get_db_connection()
        if conn:
            try:
                conn.autocommit = True
                vec_str = f"[{','.join(map(str, embedding))}]"
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO zyra_memories (content, category, source, role, session_id, embedding, created_at)
                        VALUES (%s, %s, %s, %s, %s, %s::vector, NOW());
                        """,
                        (cleaned, category, source, role, session_id, vec_str)
                    )
                conn.close()
            except Exception as e:
                logger.debug(f"DB insert notice: {e}")
                try:
                    conn.close()
                except Exception:
                    pass

        logger.info(f"💾 RAG PERMANENT STORE [{category}|{role}]: '{cleaned[:60]}...'")
        return {"status": "success", "id": mem_id, "content": cleaned}

    def search_memories(self, query: str, top_k: int = 5, threshold: float = 0.15) -> List[Dict]:
        """
        Semantic vector search across all lifetime conversations and facts.
        """
        self._load_local_store()
        cleaned_query = query.strip()
        if not cleaned_query:
            return []

        query_vec = get_embedding(cleaned_query)

        # 1. Search PostgreSQL pgvector
        conn = self._get_db_connection()
        if conn:
            try:
                with conn.cursor() as cur:
                    vec_str = f"[{','.join(map(str, query_vec))}]"
                    cur.execute(
                        """
                        SELECT content, category, source, role, created_at, (1 - (embedding <=> %s::vector)) as score
                        FROM zyra_memories
                        WHERE embedding IS NOT NULL
                        ORDER BY embedding <=> %s::vector
                        LIMIT %s;
                        """,
                        (vec_str, vec_str, top_k)
                    )
                    rows = cur.fetchall()
                    conn.close()
                    results = []
                    for r in rows:
                        score = float(r[5]) if r[5] is not None else 0.0
                        if score >= threshold:
                            results.append({
                                "content": r[0],
                                "category": r[1],
                                "source": r[2],
                                "role": r[3],
                                "created_at": str(r[4]),
                                "score": round(score, 3)
                            })
                    if results:
                        return results
            except Exception as e:
                logger.debug(f"Postgres vector search fallback: {e}")
                try:
                    conn.close()
                except Exception:
                    pass

        # 2. Fallback to local cosine similarity with hybrid keyword matching
        scored_memories = []
        query_words = set(w.lower() for w in cleaned_query.split() if len(w) > 2)
        for m in self.local_memories:
            vec = m.get("embedding")
            sim = cosine_similarity(query_vec, vec) if vec else 0.0
            
            # Hybrid lexical boost: ensures exact channel/entity names match with high relevance
            content_lower = m.get("content", "").lower()
            overlap = sum(1 for w in query_words if w in content_lower)
            if query_words and overlap > 0:
                sim += 0.25 * (overlap / len(query_words))

            if sim >= threshold or overlap > 0:
                scored_memories.append({
                    "content": m["content"],
                    "category": m.get("category", "daily_dialogue"),
                    "source": m.get("source", "voice"),
                    "role": m.get("role", "user"),
                    "created_at": m.get("created_at", ""),
                    "score": round(sim, 3)
                })

        scored_memories.sort(key=lambda x: x["score"], reverse=True)
        return scored_memories[:top_k]

    def get_context_block(self, query: str = "") -> str:
        """
        Retrieves top relevant lifetime memories to inject into the active conversational context.
        """
        if not self.local_memories:
            return "No previous memories found."

        matches = self.search_memories(query, top_k=5) if query else self.local_memories[-6:]
        if not matches:
            matches = self.local_memories[-6:]

        lines = []
        for m in matches:
            role_label = "Shijin" if m.get("role") == "user" else "Zyra"
            lines.append(f"- [{role_label}]: {m.get('content', '')}")
        return "\n".join(lines)

    def auto_ingest_turn(self, text: str, role: str = "user", session_id: str = ""):
        """
        Automated real-time pipeline: Saves EVERY spoken utterance into the permanent database.
        Filters out only zero-content noise/single-word fillers.
        """
        cleaned = text.strip()
        if not cleaned or len(cleaned) < 4:
            return

        # Ignore trivial single-word fillers
        fillers = {"yes", "no", "ok", "okay", "yeah", "yep", "nope", "uh", "um", "hmm", "ah", "oh"}
        if cleaned.lower() in fillers:
            return

        # Distinguish high-value personal facts vs general daily dialogue
        fact_triggers = [
            "my favorite", "i like", "i prefer", "i love", "i hate",
            "remember that", "remember this", "don't forget", "note down",
            "my name is", "my brother", "my sister", "my mother", "my father", "my wife",
            "my password", "my email", "my phone", "i take", "i live in", "my home", "my office",
            "meeting", "flight", "doctor", "appointment", "tomorrow", "tonight", "next week"
        ]
        
        lower = cleaned.lower()
        if any(trig in lower for trig in fact_triggers):
            cat = "user_fact"
        else:
            cat = "daily_dialogue"

        self.store_memory(
            cleaned, 
            category=cat, 
            source="voice_stream", 
            role=role, 
            session_id=session_id
        )

    def chunk_and_ingest_document(
        self, 
        title: str, 
        text: str, 
        category: str = "pdf_knowledge", 
        source: str = "telegram_pdf_bot", 
        chunk_size: int = 700, 
        overlap: int = 100
    ) -> Dict:
        """
        Splits entire PDF / long-form document text into semantic overlapping chunks,
        embeds every chunk, and permanently stores them into pgvector/local RAG database.
        """
        cleaned_text = text.strip()
        if not cleaned_text:
            return {"status": "error", "message": "Document text is empty"}

        # Semantic paragraph-based chunking
        raw_paragraphs = [p.strip() for p in cleaned_text.split("\n") if p.strip()]
        chunks = []
        current = ""
        for p in raw_paragraphs:
            if len(current) + len(p) + 1 <= chunk_size:
                current = f"{current} {p}" if current else p
            else:
                if current:
                    chunks.append(current)
                current = p
        if current:
            chunks.append(current)

        # Fallback slicing if paragraph splitting resulted in zero chunks
        if not chunks:
            start = 0
            while start < len(cleaned_text):
                end = min(start + chunk_size, len(cleaned_text))
                chunks.append(cleaned_text[start:end])
                start += max(1, chunk_size - overlap)

        stored_count = 0
        total = len(chunks)
        for i, chunk in enumerate(chunks):
            content_with_meta = f"[{title} | Page/Section {i+1}/{total}]: {chunk.strip()}"
            res = self.store_memory(
                content=content_with_meta, 
                category=category, 
                source=source, 
                role="document"
            )
            if res.get("status") == "success":
                stored_count += 1

        logger.info(f"📚 Ingested document '{title}': {stored_count}/{total} chunks stored.")
        return {
            "status": "success",
            "document": title,
            "total_chunks": total,
            "stored_chunks": stored_count
        }

rag_engine = RAGEngine()
