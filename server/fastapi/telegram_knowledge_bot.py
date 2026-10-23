import os
import io
import logging
import asyncio
from typing import Optional

logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s", 
    level=logging.INFO
)
logger = logging.getLogger("TelegramKnowledgeBot")

try:
    from pypdf import PdfReader
except ImportError:
    PdfReader = None

try:
    from telegram import Update
    from telegram.ext import (
        ApplicationBuilder, 
        CommandHandler, 
        MessageHandler, 
        ContextTypes, 
        filters
    )
except ImportError:
    Update = None

try:
    from rag_engine import rag_engine
except ImportError:
    from server.fastapi.rag_engine import rag_engine

try:
    from spotify_controller import spotify_controller
except ImportError:
    from server.fastapi.spotify_controller import spotify_controller

TELEGRAM_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN") or os.getenv("BOT_TOKEN") or ""
GEMINI_KEY = os.getenv("GEMINI_API_KEY", "")

async def start_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    welcome = (
        "👋 *Hey! I am the Zyra Knowledge Hub & PDF Bot.*\n\n"
        "📚 *Capabilities:*\n"
        "• Send or forward any `.pdf` document here or in groups (`Z.series.security.labs`).\n"
        "• I extract *every page* of technical knowledge and embed it into Zyra's Cloud RAG Brain.\n"
        "• Use `/ask <question>` to query all your indexed PDFs and research documents.\n"
        "• Use `/stats` to view total indexed knowledge base chunks."
    )
    await update.message.reply_text(welcome, parse_mode="Markdown")

async def stats_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    total = len(rag_engine.local_memories)
    doc_chunks = sum(1 for m in rag_engine.local_memories if m.get("category") in ["pdf_knowledge", "cybersecurity_pdf"])
    dialogue_chunks = sum(1 for m in rag_engine.local_memories if m.get("category") == "daily_dialogue")
    
    reply = (
        f"📊 *Zyra Cloud Knowledge Stats:*\n\n"
        f"• *Total Permanent Memory Items:* `{total}`\n"
        f"• *Deep PDF Document Chunks:* `{doc_chunks}`\n"
        f"• *Daily Dialogue & Spoken Turns:* `{dialogue_chunks}`\n"
        f"• *Storage Engine:* `PostgreSQL (pgvector) + Resilient JSON Cache`"
    )
    await update.message.reply_text(reply, parse_mode="Markdown")

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    doc = update.message.document
    if not doc or not doc.file_name:
        return

    filename = doc.file_name
    if not filename.lower().endswith(".pdf"):
        return

    chat_title = update.effective_chat.title or "Direct Message"
    status_msg = await update.message.reply_text(
        f"⏳ Reading and extracting full technical knowledge from *{filename}*...",
        parse_mode="Markdown"
    )

    try:
        if PdfReader is None:
            await status_msg.edit_text("❌ `pypdf` is not installed on this environment.")
            return

        file_obj = await context.bot.get_file(doc.file_id)
        pdf_bytes = io.BytesIO()
        await file_obj.download_to_memory(pdf_bytes)
        pdf_bytes.seek(0)

        reader = PdfReader(pdf_bytes)
        total_pages = len(reader.pages)
        pages_text = []

        for idx, page in enumerate(reader.pages):
            txt = page.extract_text()
            if txt and txt.strip():
                pages_text.append(f"--- [Page {idx+1}/{total_pages}] ---\n{txt.strip()}")

        full_text = "\n\n".join(pages_text)
        if not full_text.strip():
            await status_msg.edit_text(
                f"⚠️ Could not extract text from *{filename}*. It might contain scanned image pages without OCR.",
                parse_mode="Markdown"
            )
            return

        # Ingest directly into the RAG engine
        result = rag_engine.chunk_and_ingest_document(
            title=filename,
            text=full_text,
            category="pdf_knowledge",
            source=f"telegram_{chat_title}"
        )

        stored = result.get("stored_chunks", 0)
        total = result.get("total_chunks", 0)

        await status_msg.edit_text(
            f"✅ *Deep Knowledge Indexed!*\n\n"
            f"📄 *File:* `{filename}`\n"
            f"📑 *Pages Processed:* `{total_pages}`\n"
            f"🧩 *Knowledge Chunks Embedded:* `{stored}/{total}`\n"
            f"🧠 *Synchronized with:* Zyra Voice on Android & Knowledge Hub",
            parse_mode="Markdown"
        )
        logger.info(f"Successfully processed and indexed PDF {filename} ({stored}/{total} chunks)")

    except Exception as e:
        logger.error(f"Error processing PDF {filename}: {e}")
        await status_msg.edit_text(f"❌ Failed to process PDF: `{e}`", parse_mode="Markdown")

async def ask_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = " ".join(context.args).strip() if context.args else ""
    if not query:
        await update.message.reply_text("Usage: `/ask <your question about any indexed PDF or topic>`", parse_mode="Markdown")
        return

    thinking_msg = await update.message.reply_text("🔍 Searching through indexed PDF knowledge base...", parse_mode="Markdown")
    try:
        matches = rag_engine.search_memories(query, top_k=4, threshold=0.10)
        if not matches:
            await thinking_msg.edit_text(f"No specific knowledge found matching: *{query}*.", parse_mode="Markdown")
            return

        results_text = []
        for i, m in enumerate(matches, 1):
            content = m.get("content", "").replace("*", "").replace("_", "")
            score = m.get("score", 0.0)
            results_text.append(f"*{i}.* {content[:350]}... `(match: {int(score*100)}%)`")

        response = (
            f"🎯 *Answers for:* _{query}_\n\n" +
            "\n\n".join(results_text)
        )
        await thinking_msg.edit_text(response, parse_mode="Markdown")

    except Exception as e:
        logger.error(f"Error in ask_cmd: {e}")
        await thinking_msg.edit_text(f"❌ Search error: `{e}`")

async def play_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    song = " ".join(context.args).strip() if context.args else ""
    res = spotify_controller.play_query(song)
    await update.message.reply_text(f"🎵 {res}")

async def pause_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    res = spotify_controller.pause()
    await update.message.reply_text(f"⏸️ {res}")

async def resume_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    res = spotify_controller.resume()
    await update.message.reply_text(f"▶️ {res}")

async def next_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    res = spotify_controller.next_track()
    await update.message.reply_text(f"⏭️ {res}")

async def nowplaying_cmd(update: Update, context: ContextTypes.DEFAULT_TYPE):
    res = spotify_controller.get_currently_playing()
    await update.message.reply_text(f"🎧 {res}")

def main():
    token = (os.getenv("TELEGRAM_BOT_TOKEN") or os.getenv("BOT_TOKEN") or "").strip()
    if not token:
        logger.warning("Neither TELEGRAM_BOT_TOKEN nor BOT_TOKEN is set. Telegram Knowledge Bot is disabled.")
        return

    logger.info("Starting Zyra Telegram Knowledge Hub & PDF Bot...")
    app = ApplicationBuilder().token(token).build()

    app.add_handler(CommandHandler("start", start_cmd))
    app.add_handler(CommandHandler("stats", stats_cmd))
    app.add_handler(CommandHandler("ask", ask_cmd))
    app.add_handler(CommandHandler("play", play_cmd))
    app.add_handler(CommandHandler("pause", pause_cmd))
    app.add_handler(CommandHandler("resume", resume_cmd))
    app.add_handler(CommandHandler("next", next_cmd))
    app.add_handler(CommandHandler("nowplaying", nowplaying_cmd))
    app.add_handler(MessageHandler(filters.Document.ALL, handle_document))

    app.run_polling()

if __name__ == "__main__":
    main()
