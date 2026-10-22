#!/bin/bash

# Fallback for LiveKit URL
export LIVEKIT_URL="${LIVEKIT_URL:-wss://zyra-sbn0wnp2.livekit.cloud}"

# 1. Start Voice Agent Worker in background to service LiveKit calls
echo "Starting Zyra Voice Agent Worker..."
python -u voice_agent.py start > /app/voice_agent.log 2>&1 &

# 2. Start FastAPI Server in foreground
echo "Starting FastAPI Brain Server..."
exec uvicorn main:app --host 0.0.0.0 --port 8000
