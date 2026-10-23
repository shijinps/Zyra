# Zyra Ecosystem: Master Reference Setup & Category Checklist

This document is the single-source-of-truth reference for configuring, testing, and verifying every category in the Zyra ecosystem.

---

## 🗺️ Category Overview & Live Status Matrix

| Category | Core Component | Service / Port / URL | Status |
| :--- | :--- | :--- | :--- |
| **1. Knowledge & RAG** | SQLite Vector + Telegram Bot | `https://apizyra.shijin.in/rag` & `@ragzyra13253bot` | 🟢 Verified Live |
| **2. Music & Media** | Spotify Web API OAuth | `https://apizyra.shijin.in/spotify` | 🟢 Authorized & Verified |
| **3. Headless Browsing** | Browserless Chrome | `https://browserless.shijin.in` | 🟢 Live & Pressure Tested |
| **4. Telemetry & Metrics**| Beszel Hub + Agent | `https://beszel.shijin.in` (Port 8090) | 🟢 Monitoring VPS |
| **5. Notifications** | Apprise + Webhook Receiver | `https://apizyra.shijin.in/alerts` | 🟢 Multi-channel Ready |
| **6. Android Companion** | LiveKit WebRTC + Android app | `wss://zyra-sbn0wnp2.livekit.cloud` | 🟢 Voice & Native Actions |
| **7. External Intelligence**| OpenWeather + NewsAPI + MetaMCP| Fast Weather/News APIs | 🟢 Connected |

---

## ⚡ Quick One-Liner Ecosystem Health Check

Run this single command on your terminal to check the health of all external services simultaneously:

```bash
echo "=== 1. Zyra API Health ===" && curl -s -I "https://apizyra.shijin.in/health" | head -n 1 && \
echo "=== 2. Browserless Pressure ===" && curl -s "https://browserless.shijin.in/pressure?token=km6Gy3562QXPWZJB7a8mZenXVR6Xyn9c" && echo "" && \
echo "=== 3. Spotify Status ===" && curl -s "https://apizyra.shijin.in/spotify/status" && echo "" && \
echo "=== 4. RAG Memory Count ===" && curl -s "https://apizyra.shijin.in/rag/memories" | grep -o '"total_memories":[0-9]*' && echo "" && \
echo "=== 5. Beszel Hub Status ===" && curl -s -I "https://beszel.shijin.in" | head -n 1
```
