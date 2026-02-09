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
        await room.connect(LIVEKIT_URL, api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
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
    
    logger.info("Listening for audio frames...")
    
    buffer = bytearray()
    # LiveKit usually provides 16kHz or 48kHz. Assuming 16kHz for Voice (or resample if needed).
    # Whisper expects 16kHz. 
    # Let's accumulate ~2 seconds of audio: 16000 * 2 bytes * 2 sec = 64000 bytes
    BUFFER_SIZE = 64000 
    
    async for frame in audio_stream:
        # frame.data is int16 pcm
        buffer.extend(frame.data)
        
        if len(buffer) >= BUFFER_SIZE:
            # COPY buffer to process to avoid blocking stream reading (theoretically)
            # In python async, this block might block the loop unless we offload.
            # But for "tiny" model on CPU, it might be fast enough for a demo.
            
            data_to_process = buffer[:]
            buffer = bytearray() # Reset buffer
            
            try:
                # Convert to float32 for Whisper
                audio_np = np.frombuffer(data_to_process, dtype=np.int16).astype(np.float32) / 32768.0
                
                # Check for silence (simple RMS) so we don't transcribe silence
                rms = np.sqrt(np.mean(audio_np**2))
                if rms < 0.01: # Silence threshold
                     continue
                
                # Run Inference (Offload to thread ideally, but direct call here for simplicity)
                # We use run_in_executor to avoid blocking the async loop for too long
                loop = asyncio.get_event_loop()
                await loop.run_in_executor(None, process_transcription, audio_np)
                
            except Exception as e:
                logger.error(f"Transcription Error: {e}")
    
    logger.info("Audio stream ended.")

def process_transcription(audio_np):
    try:
        segments, info = model.transcribe(audio_np, beam_size=5, language="en")
        for segment in segments:
            text = segment.text.strip()
            if text:
                logger.info(f"🎤 USER: {text}")
    except Exception as e:
        logger.error(f"Whisper Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
