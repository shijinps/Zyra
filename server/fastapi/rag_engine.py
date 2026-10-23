import os
import json
import math
import logging
import urllib.request
import urllib.error
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
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key={gemini_key}"
            payload = {
                "model": "models/text-embedding-004",
                "content": {"parts": [{"text": cleaned}]}
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
            logger.debug(f"Gemini embedding API call skipped: {e}. Using resilient local vectorizer.")

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
        """Initializes pgvector table if PostgreSQL is available."""
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
                        category VARCHAR(50) DEFAULT 'general',
                        source VARCHAR(50) DEFAULT 'dialogue',
                        embedding vector(768),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
            conn.close()
            logger.info("PostgreSQL pgvector memory table initialized successfully.")
        except Exception as e:
            logger.warning(f"PostgreSQL RAG init notice: {e}")
            try:
                conn.close()
            except Exception:
                pass

    def _load_local_store(self):
        if os.path.exists(LOCAL_STORE_PATH):
            try:
                with open(LOCAL_STORE_PATH, "r", encoding="utf-8") as f:
                    self.local_memories = json.load(f)
            except Exception as e:
                logger.warning(f"Failed to read {LOCAL_STORE_PATH}: {e}")
                self.local_memories = []

    def _save_local_store(self):
        try:
            with open(LOCAL_STORE_PATH, "w", encoding="utf-8") as f:
                json.dump(self.local_memories, f, ensure_ascii=False, indent=2)
        except Exception as e:
            logger.warning(f"Failed to persist {LOCAL_STORE_PATH}: {e}")

    def _init_defaults(self):
        """Seeds initial companion knowledge if store is completely empty."""
        if not self.local_memories:
            seeds = [
                ("Shijin is the creator, user, and close buddy of Zyra.", "identity"),
                ("Zyra (Zay) is Shijin's best friend, personal confidante, and AI companion.", "identity"),
                ("Zyra communicates with warm, punchy 1-2 sentence replies and never calls Shijin 'Sir'.", "personality"),
                ("Zyra can initiate phone calls, send WhatsApp messages, check weather, read latest news, and remember personal facts.", "capabilities")
            ]
            for content, cat in seeds:
                self.store_memory(content, category=cat, source="system_seed")

    def store_memory(self, content: str, category: str = "general", source: str = "dialogue") -> Dict:
        """
        Embeds and stores a new piece of information into PostgreSQL and local store.
        """
        cleaned = content.strip()
        if not cleaned:
            return {"error": "Content cannot be empty"}

        embedding = get_embedding(cleaned)
        now_iso = datetime.utcnow().isoformat()
        
        mem_id = f"mem_{int(datetime.utcnow().timestamp() * 1000)}"
        memory_item = {
            "id": mem_id,
            "content": cleaned,
            "category": category,
            "source": source,
            "embedding": embedding,
            "created_at": now_iso
        }
        self.local_memories.append(memory_item)
        self._save_local_store()

        conn = self._get_db_connection()
        if conn:
            try:
                conn.autocommit = True
                vec_str = f"[{','.join(map(str, embedding))}]"
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO zyra_memories (content, category, source, embedding)
                        VALUES (%s, %s, %s, %s::vector);
                        """,
                        (cleaned, category, source, vec_str)
                    )
                conn.close()
            except Exception as e:
                logger.debug(f"DB insert notice: {e}")
                try:
                    conn.close()
                except Exception:
                    pass

        logger.info(f"RAG: Stored new memory [{category}]: '{cleaned[:60]}...'")
        return {"status": "success", "id": mem_id, "content": cleaned}

    def search_memories(self, query: str, top_k: int = 4, threshold: float = 0.15) -> List[Dict]:
        """
        Semantic vector search over all historical facts, notes, and conversations.
        """
        cleaned_query = query.strip()
        if not cleaned_query:
            return []

        query_vec = get_embedding(cleaned_query)

        conn = self._get_db_connection()
        if conn:
            try:
                with conn.cursor() as cur:
                    vec_str = f"[{','.join(map(str, query_vec))}]"
                    cur.execute(
                        """
                        SELECT content, category, source, created_at, (1 - (embedding <=> %s::vector)) as score
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
                        score = float(r[4]) if r[4] is not None else 0.0
                        if score >= threshold:
                            results.append({
                                "content": r[0],
                                "category": r[1],
                                "source": r[2],
                                "created_at": str(r[3]),
                                "score": round(score, 3)
                            })
                    if results:
                        return results
            except Exception as e:
                logger.debug(f"Postgres vector search fallback to local store: {e}")
                try:
                    conn.close()
                except Exception:
                    pass

        scored_memories = []
        for m in self.local_memories:
            vec = m.get("embedding")
            if vec:
                sim = cosine_similarity(query_vec, vec)
                if sim >= threshold:
                    scored_memories.append({
                        "content": m["content"],
                        "category": m.get("category", "general"),
                        "source": m.get("source", "dialogue"),
                        "created_at": m.get("created_at", ""),
                        "score": round(sim, 3)
                    })

        scored_memories.sort(key=lambda x: x["score"], reverse=True)
        return scored_memories[:top_k]

    def get_context_block(self, query: str = "") -> str:
        """
        Retrieves top relevant memories and builds a concise text block for the system prompt.
        """
        if not self.local_memories:
            return "No previous memories found."

        matches = self.search_memories(query, top_k=5) if query else self.local_memories[-5:]
        if not matches:
            matches = self.local_memories[-5:]

        lines = []
        for m in matches:
            cat = f"[{m.get('category', 'general').upper()}]"
            lines.append(f"- {cat} {m.get('content', '')}")
        return "\n".join(lines)

    def auto_ingest_turn(self, text: str, role: str = "user"):
        """
        Lightweight heuristic to detect personal statements and automatically store them.
        """
        if role != "user":
            return
        cleaned = text.strip()
        lower = cleaned.lower()
        
        fact_triggers = [
            "my favorite", "i like", "i prefer", "i love", "i hate",
            "remember that", "remember this", "don't forget", "note down",
            "my name is", "my brother", "my sister", "my mother", "my father", "my wife",
            "my password", "my email", "my phone", "i take", "i live in", "my home", "my office"
        ]
        
        if any(trig in lower for trig in fact_triggers) and len(cleaned) > 10:
            self.store_memory(cleaned, category="user_fact", source="auto_chat")

rag_engine = RAGEngine()
