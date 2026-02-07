#!/bin/bash

# Start FastAPI in background
uvicorn main:app --host 0.0.0.0 --port 8000 &

# Start LiveKit Voice Agent
python voice_agent.py &

# Wait for any process to exit
wait -n

# Exit with status of process that exited first
exit $?
