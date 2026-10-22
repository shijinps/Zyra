# Zyra Intelligence - Deployment Checklist

## ✅ Pre-Deployment Checklist

### API Keys Configuration

- [ ] **Google Maps API Key**
  - Get key from: https://console.cloud.google.com/
  - Free tier: $200/month credit (~40,000 requests)
  - Add to `local.properties`: `GOOGLE_MAPS_API_KEY=your-key-here`
  

### Build Configuration

- [ ] Update `app/build.gradle.kts` with BuildConfig fields:
```kotlin
buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${properties.getProperty("GOOGLE_MAPS_API_KEY")}\"")
buildConfigField("String", "IBM_QUANTUM_API_KEY", "\"${properties.getProperty("IBM_QUANTUM_API_KEY", "snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo")}\"")
buildConfigField("String", "CISCO_QUANTUM_API_KEY", "\"${properties.getProperty("CISCO_QUANTUM_API_KEY", "")}\"")
```

- [ ] Enable BuildConfig:
```kotlin
buildFeatures {
    buildConfig = true
}
```

### n8n Workflows

- [x] `mcp_bridge_workflow.json` created
- [x] `spatial_intelligence_workflow.json` created
- [ ] Import both workflows to n8n (via Coolify)
- [ ] Connect Groq credentials in n8n
- [ ] Connect ElevenLabs credentials in n8n
- [ ] Update Google Maps API key in spatial workflow
- [ ] Activate both workflows

### GitHub Deployment

- [ ] Commit all intelligence modules
- [ ] Push to GitHub
- [ ] Pull in n8n container
- [ ] Verify workflows imported correctly

---

## 🚀 Deployment Steps

### Step 1: Get Google Maps API Key

See [google_maps_api_setup.md](google_maps_api_setup.md) for detailed instructions.

**Quick steps:**
1. Go to https://console.cloud.google.com/
2. Create project: "Zyra Spatial Intelligence"
3. Enable APIs: Directions, Places, Geocoding
4. Create API key
5. Restrict by IP (your VPS IP)
6. Copy key to `local.properties`

### Step 2: Update local.properties

Add these lines to `local.properties`:
```properties
GOOGLE_MAPS_API_KEY=AIzaSyD...your-key-here
```

### Step 3: Update build.gradle.kts

Add BuildConfig fields (see Build Configuration above)

### Step 4: Commit to GitHub

```bash
# Add all new intelligence files
git add app/src/main/java/com/zyra/app/data/EnhancedMemoryModule.kt
git add app/src/main/java/com/zyra/app/data/SmartContactManager.kt
git add app/src/main/java/com/zyra/app/logic/*.kt
git add app/src/main/java/com/zyra/app/integration/
git add server/n8n/*.json

# Commit
git commit -m "feat: Add 16 intelligence modules + spatial intelligence"

# Push
git push origin main
```

### Step 5: Deploy n8n Workflows

**Option A: Via Coolify UI**
1. SSH to your VPS
2. Access Coolify dashboard
3. Navigate to n8n service
4. Import `mcp_bridge_workflow.json`
5. Import `spatial_intelligence_workflow.json`

**Option B: Via Git Pull in n8n Container**
1. SSH to VPS
2. Access n8n container: `docker exec -it <n8n-container> /bin/sh`
3. Pull from GitHub: `git pull origin main`
4. Restart n8n: `docker restart <n8n-container>`

### Step 6: Configure n8n Workflows

1. Open n8n web interface
2. Go to **Workflows**
3. Open "Zyra Spatial Intelligence"
4. Update nodes:
   - **Get Route**: Add Google Maps API key
   - **Search Places**: Add Google Maps API key
   - **Groq - Summarize**: Connect Groq credentials
   - **ElevenLabs TTS**: Connect ElevenLabs credentials
5. Click **Active** to enable
6. Note webhook URL

### Step 7: Test Integration

**Test 1: Basic Intelligence**
```
"Zyra, call Mom"
"Message her"  // Should resolve "her" to Mom
```

**Test 2: Spatial Intelligence**
```
"Zyra, find ATMs on my route to Thrissur"
// Should return places with detour times
```


## 📊 Verification

### Android App
- [ ] App builds successfully
- [ ] No BuildConfig errors
- [ ] IntelligenceSystem initializes
- [ ] LocationPulseService updates SpatialIntelligence
- [ ] Voice commands recognized

### n8n Workflows
- [ ] Both workflows active
- [ ] Webhook URLs accessible
- [ ] Groq credentials working
- [ ] ElevenLabs credentials working
- [ ] Google Maps API calls successful

### End-to-End
- [ ] Voice command triggers n8n workflow
- [ ] Spatial query returns results
- [ ] Groq generates summary
- [ ] ElevenLabs speaks response
- [ ] Response time < 5 seconds

---

## 🔒 Security Checklist

- [ ] `local.properties` in `.gitignore`
- [ ] No API keys committed to GitHub
- [ ] Google Maps key restricted by IP
- [ ] Budget alerts set ($10/month)
- [ ] n8n webhooks use HTTPS
- [ ] Environment variables secured in Coolify

---

## 📈 Monitoring

### Google Maps Usage
- Dashboard: https://console.cloud.google.com/apis/dashboard
- Check daily requests
- Monitor costs (should be $0 with free tier)

### n8n Workflows
- Check execution logs
- Monitor error rates
- Track response times

### Android App
- Monitor battery usage
- Check location accuracy
- Review crash reports

---

## 🆘 Troubleshooting

### "BuildConfig not found"
- **Fix:** Sync Gradle, rebuild project

### "API key not valid"
- **Fix:** Check restrictions, verify key copied correctly

### "n8n workflow not triggering"
- **Fix:** Check webhook URL, verify workflow is active

### "Spatial query returns no results"
- **Fix:** Verify Google Maps API enabled, check quota

---

## ✅ Final Checklist

- [ ] All API keys configured
- [ ] BuildConfig updated
- [ ] Code committed to GitHub
- [ ] n8n workflows deployed
- [ ] Workflows activated
- [ ] End-to-end test passed
- [ ] Monitoring set up
- [ ] Documentation reviewed

---

## 🎉 You're Ready to Deploy!

Once all checkboxes are complete, Zyra will have:
- ✅ 16 intelligence modules
- ✅ Spatial intelligence with Google Maps
- ✅ Voice-first interface
- ✅ Production-ready deployment

**Next:** Follow the steps above and test your first intelligent query! 🚀
