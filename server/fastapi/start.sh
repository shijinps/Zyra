#!/bin/bash

# Start FastAPI (Now includes the Gemini 3.1 Voice Gateway)
uvicorn main:app --host 0.0.0.0 --port 8000
