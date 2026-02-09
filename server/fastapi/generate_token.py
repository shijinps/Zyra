
import os
import asyncio
from livekit import api

# Use the same credentials as voice_agent.py
LIVEKIT_API_KEY = "APIZPNMthaHjTcn"
LIVEKIT_API_SECRET = "1PJ87iRj891ilWmrmIGeUY2Nuai595JVyK2qm3c2Pwd"

async def generate_token():
    token = api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET) \
        .with_identity("zyra-android-user") \
        .with_name("Android User") \
        .with_grants(api.VideoGrants(room_join=True, room="zyra-room")) \
        .to_jwt()
    print(f"TOKEN:{token}")

if __name__ == "__main__":
    asyncio.run(generate_token())
