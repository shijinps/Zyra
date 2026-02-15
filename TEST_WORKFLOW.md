# Test the Spatial Intelligence Workflow

## 🧪 Quick Test (2 minutes)

### Test 1: Simple PowerShell Test

Open PowerShell and run this command:

```powershell
$body = @{
    current_lat = 10.0266
    current_lng = 76.3122
    destination_lat = 10.5276
    destination_lng = 76.2144
    destination_name = "Thrissur"
    place_type = "atm"
    max_detour_minutes = 2
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
```

**Expected Output:**
```json
{
  "summary": "I found 5 ATMs on your route to Thrissur. 1. Federal Bank ATM in Palarivattom, 1 minute detour, rated 4.2 stars...",
  "audio_url": "data:audio/mpeg;base64,...",
  "places_count": 5
}
```

---

## ✅ What Each Response Means

### ✅ Success Response
```json
{
  "summary": "I found X places...",
  "audio_url": "data:audio/mpeg;base64,...",
  "places_count": 5
}
```
**Meaning:** Everything works! 🎉
- Google Maps found the route ✅
- Found places along the route ✅
- Groq generated summary ✅
- ElevenLabs created audio ✅

### ❌ Error: "Unauthorized" or "401"
**Meaning:** API credentials are wrong
**Fix:** 
- Check Groq credential has `Bearer` prefix
- Verify API keys are correct
- Re-save credentials in n8n

### ❌ Error: "Webhook not found" or "404"
**Meaning:** Workflow is not active
**Fix:**
- Make sure workflow is toggled to "Active"
- Check webhook URL is correct

### ❌ Error: "Invalid API key" from Google Maps
**Meaning:** Google Maps API issue
**Fix:**
- Verify API key in workflow nodes
- Check Google Cloud Console that APIs are enabled

---

## 🔍 Test in n8n (Visual Test)

### Method 1: Test Individual Nodes

1. Open your workflow in n8n
2. Click on **"Groq - Summarize"** node
3. Click **"Execute Node"** button (bottom of panel)
4. If successful: You'll see green checkmark ✅
5. If error: You'll see red X with error message ❌

Repeat for **"ElevenLabs TTS"** node.

### Method 2: Test Entire Workflow

1. Click **"Execute Workflow"** button (bottom center, red button)
2. Watch the nodes light up as they execute
3. Green = Success ✅
4. Red = Error ❌
5. Click on any node to see its output

---

## 📊 Check Execution Logs

1. In n8n, click **"Executions"** (left sidebar)
2. You'll see a list of all workflow runs
3. Click on the latest execution
4. See:
   - ✅ Which nodes succeeded
   - ❌ Which nodes failed
   - 📄 Input/output data for each node
   - ⏱️ Execution time

---

## 🎯 Real-World Test

### Test Different Place Types

**Find Gas Stations:**
```powershell
$body = @{
    current_lat = 10.0266
    current_lng = 76.3122
    destination_lat = 10.5276
    destination_lng = 76.2144
    destination_name = "Thrissur"
    place_type = "gas_station"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
```

**Find Restaurants:**
```powershell
$body = @{
    current_lat = 10.0266
    current_lng = 76.3122
    destination_lat = 10.5276
    destination_lng = 76.2144
    destination_name = "Thrissur"
    place_type = "restaurant"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
```

---

## 🎤 Test Audio Output

The response includes `audio_url` with base64-encoded audio. To play it:

1. Copy the base64 string (after `data:audio/mpeg;base64,`)
2. Use an online base64 to audio converter
3. Or save it to a file:

```powershell
# Save audio from response
$response = Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
$audioBase64 = $response.audio_url -replace 'data:audio/mpeg;base64,', ''
$audioBytes = [Convert]::FromBase64String($audioBase64)
[IO.File]::WriteAllBytes("C:\Users\SHIJIN P S\Desktop\zyra_response.mp3", $audioBytes)
```

Then play `zyra_response.mp3` to hear Zyra's voice! 🎧

---

## ✅ Complete Test Checklist

- [ ] Workflow is Active (toggle is ON)
- [ ] Groq credential configured
- [ ] ElevenLabs credential configured
- [ ] PowerShell test returns JSON response
- [ ] Response includes "summary" field
- [ ] Response includes "audio_url" field
- [ ] Response includes "places_count" field
- [ ] Audio file can be played
- [ ] Different place types work (atm, restaurant, gas_station)
- [ ] Execution logs show all nodes succeeded

---

## 🎉 Success Indicators

**You know it's working when:**
1. ✅ PowerShell command returns JSON (not an error)
2. ✅ Summary mentions actual place names
3. ✅ Audio URL is present and playable
4. ✅ Places count > 0
5. ✅ n8n execution logs show green checkmarks
6. ✅ No errors in n8n execution details

---

## 🚀 Next: Test in Android App

Once the workflow test passes, update your Android app:

```kotlin
// In your network configuration
const val N8N_SPATIAL_ENDPOINT = "https://n8n.shijin.in/webhook/zyra/spatial-query"

// Test voice command
"Zyra, find ATMs on my route to Thrissur"
```

The app will call the n8n webhook and get the AI-generated response! 🎊
