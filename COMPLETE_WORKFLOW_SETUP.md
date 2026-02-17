# Complete n8n Workflow Setup Guide

## 🎉 Success So Far

✅ Basic webhook working  
✅ n8n responding correctly  
✅ Android app built successfully  

## 🚀 Next: Add Full API Integration

I've created a complete workflow file: `n8n_complete_workflow.json`

### Workflow Flow

```
Webhook → Google Maps → Groq AI → ElevenLabs → Respond
```

1. **Webhook** receives location request
2. **Google Maps** finds nearby places
3. **Groq AI** summarizes the places
4. **ElevenLabs** converts to speech
5. **Respond** sends back summary + audio

## 📋 Setup Steps

### Step 1: Add API Keys

Before importing, you need to add your API keys to the workflow file:

1. Open `n8n_complete_workflow.json` in a text editor
2. Replace `YOUR_GOOGLE_MAPS_API_KEY` with your actual key
3. Replace `YOUR_ELEVENLABS_API_KEY` with your actual key
4. Save the file

### Step 2: Import Workflow

1. Go to https://n8n.shijin.in
2. Click **"+"** → **"Import from File"**
3. Select `n8n_complete_workflow.json`
4. Click **"Import"**

### Step 3: Configure Credentials

The workflow uses Groq API credentials. You need to set this up:

1. In n8n, go to **"Credentials"** (left sidebar)
2. Click **"+ Add Credential"**
3. Search for **"Groq API"**
4. Add your Groq API key: `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`
5. Save

### Step 4: Activate & Test

1. **Deactivate** the simple test workflow
2. **Activate** the complete workflow
3. Test with PowerShell:

```powershell
$body = @{
    latitude = 12.9716
    longitude = 77.5946
    place_type = "restaurant"
    radius = 1000
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial" -Method Post -ContentType "application/json" -Body $body
```

## ✅ Expected Response

```json
{
  "summary": "Here are some great restaurants near you: Cafe Noir, The Spice Route, Urban Kitchen...",
  "audio_url": "data:audio/mpeg;base64,//uQx...",
  "place_count": 20,
  "places": [
    { "name": "Cafe Noir", "rating": 4.5, "vicinity": "MG Road" },
    ...
  ]
}
```

## 🔧 Troubleshooting

### If Google Maps fails:
- Check API key is correct
- Verify Places API is enabled in Google Cloud Console

### If Groq fails:
- Check credentials are configured in n8n
- Verify API key is valid

### If ElevenLabs fails:
- Check API key is correct
- Verify you haven't exceeded free tier limit (10,000 chars/month)

## 📱 Android App Integration

Once the workflow works, update your Android app:

1. Find the spatial intelligence code
2. Update webhook URL to: `https://n8n.shijin.in/webhook/zyra/spatial`
3. Test voice commands!

## 🎯 Summary

**Current Status**: Basic webhook ✅  
**Next Step**: Import complete workflow with API keys  
**Final Goal**: Voice-controlled spatial intelligence in Android app
