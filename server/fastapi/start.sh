#!/bin/bash

# 1. Start Voice Agent Worker in background to service LiveKit calls
python voice_agent.py start &

# 2. Start FastAPI Server in foreground
uvicorn main:app --host 0.0.0.0 --port 8000
