import asyncio
import os
import logging
import numpy as np
from livekit import api, rtc
from faster_whisper import WhisperModel

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ZyraVoice")

# Valid API Key/Secret required
LIVEKIT_URL = os.getenv("LIVEKIT_URL", "wss://zyra-sbn0wnp2.livekit.cloud")
LIVEKIT_API_KEY = os.getenv("LIVEKIT_API_KEY", "APIZPNMthaHjTcn")
LIVEKIT_API_SECRET = os.getenv("LIVEKIT_API_SECRET", "1PJ87iRj891ilWmrmIGeUY2Nuai595JVyK2qm3c2Pwd")

# Initialize Whisper (Downloads model on first run)
logger.info("Loading Whisper Model...")
model = WhisperModel("tiny.en", device="cpu", compute_type="int8") # Use 'cuda' if GPU available
logger.info("Whisper Model Loaded.")

async def main():
    logger.info("Starting Zyra Voice Agent...")
    
    # 1. Connect to Room
    room = rtc.Room()
    
    @room.on("track_published")
    def on_track_published(publication, participant):
        logger.info(f"Track Published: {publication.sid} from {participant.identity}")
        
    @room.on("track_subscribed")
    def on_track_subscribed(track, publication, participant):
        logger.info(f"Track Subscribed: {track.kind} from {participant.identity}")
        if track.kind == rtc.TrackKind.KIND_AUDIO:
            asyncio.create_task(handle_audio(track))

    try:
        # Connect to 'zyra-room'
        await room.connect(LIVEKIT_URL, await api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
                           .with_identity("zyra-server")
                           .with_name("Zyra Host")
                           .with_grants(api.VideoGrants(room_join=True, room="zyra-room"))
                           .to_jwt())
        logger.info("Connected to Room: zyra-room")
        
        # Keep running
        while True:
            await asyncio.sleep(1)
            
    except Exception as e:
        logger.error(f"Error connecting: {e}")
    finally:
        await room.disconnect()

async def handle_audio(track: rtc.RemoteAudioTrack):
    logger.info("Handling Audio Stream...")
    audio_stream = rtc.AudioStream(track)
    
    # Simple accumulator for VAD-like chunking (very basic)
    # In production, use Silero VAD or WebRTC VAD
    logger.info("Listening for audio frames...")
    
    async for frame in audio_stream:
        # data is int16 pcm
        # For this demo, we won't implement full VAD/buffering here to keep it simple.
        # fast-whisper expects float32 usually, or file path.
        # We'll just log that we got usage.
        # Implementing full streaming STT here requires a buffer loop.
        pass
    
    logger.info("Audio stream ended.")

if __name__ == "__main__":
    asyncio.run(main())
