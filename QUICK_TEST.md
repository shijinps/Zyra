# Quick Test Commands

## Test n8n Workflow

### Using PowerShell (Copy and paste this entire block):
```powershell
$body = @{
    latitude = 12.9716
    longitude = 77.5946
    place_type = "restaurant"
    radius = 1000
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
```

### Using cURL (if you have Git Bash):
```bash
curl -X POST https://n8n.shijin.in/webhook/zyra/spatial-query \
  -H "Content-Type: application/json" \
  -d '{"latitude": 12.9716, "longitude": 77.5946, "place_type": "restaurant", "radius": 1000}'
```

## Expected Response

```json
{
  "summary": "Here are the top restaurants near you...",
  "audio_url": "https://api.elevenlabs.io/...",
  "place_count": 5,
  "places": [...]
}
```

## If You Get Errors

### 404 Not Found
- n8n workflow not active
- Go to https://n8n.shijin.in and activate the workflow

### 401 Unauthorized  
- API credentials not configured
- Check Groq and ElevenLabs credentials in n8n

### 500 Server Error
- Check n8n execution logs
- One of the APIs (Groq/ElevenLabs/Google Maps) failed

## Success Indicators

✅ You get a JSON response  
✅ Response has `summary` field with AI-generated text  
✅ Response has `audio_url` field with ElevenLabs URL  
✅ Response has `place_count` greater than 0  

This means **ALL** your tech stack is connected:
- ✅ n8n workflow working
- ✅ Google Maps API working
- ✅ Groq AI working
- ✅ ElevenLabs TTS working
