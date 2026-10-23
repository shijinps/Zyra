#!/bin/bash

# Fallback for LiveKit URL
export LIVEKIT_URL="${LIVEKIT_URL:-wss://zyra-sbn0wnp2.livekit.cloud}"

# 1. Start Voice Agent Worker with auto-restart supervisor in background
echo "Starting Zyra Voice Agent Worker with auto-restart supervisor..."
(
  while true; do
    echo "[$(date)] Starting Zyra Voice Agent Worker..." >> /app/voice_agent.log
    python -u voice_agent.py start >> /app/voice_agent.log 2>&1
    EXIT_CODE=$?
    echo "[$(date)] Voice Agent process exited with code $EXIT_CODE. Restarting in 5s..." >> /app/voice_agent.log
    sleep 5
  done
) &

# 2. Start Telegram Knowledge Hub & PDF Bot (if TELEGRAM_BOT_TOKEN or BOT_TOKEN is set)
TG_TOKEN="${TELEGRAM_BOT_TOKEN:-$BOT_TOKEN}"
if [ -n "$TG_TOKEN" ]; then
  export TELEGRAM_BOT_TOKEN="$TG_TOKEN"
  echo "Starting Telegram Knowledge Hub Bot with auto-restart supervisor..."
  (
    while true; do
      echo "[$(date)] Starting Telegram Knowledge Hub Bot..." >> /app/telegram_bot.log
      python -u telegram_knowledge_bot.py >> /app/telegram_bot.log 2>&1
      EXIT_CODE=$?
      echo "[$(date)] Telegram Bot process exited with code $EXIT_CODE. Restarting in 5s..." >> /app/telegram_bot.log
      sleep 5
    done
  ) &
fi

# 2. Start FastAPI Server in foreground
echo "Starting FastAPI Brain Server..."
exec uvicorn main:app --host 0.0.0.0 --port 8000
