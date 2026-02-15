# API Keys Summary

## ✅ Existing API Keys (Already Configured)

### IBM Quantum API
- **Status:** ✅ Configured
- **Location:** `server/fastapi/quantum.py`
- **Token:** `snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo`
- **Usage:** Quantum circuits, superposition checks, TSP optimization

### Cisco Quantum QRNG API
- **Status:** ✅ Configured
- **Location:** `server/fastapi/quantum.py`
- **Key:** `gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G`
- **Usage:** True random number generation, mood determination

---

## 🔑 Required API Keys (Need to Add)

### Google Maps API
- **Status:** ⏳ Pending
- **Cost:** FREE ($200/month credit = ~40,000 requests)
- **Required For:** Spatial intelligence, route calculation, places search
- **Setup Guide:** See `google_maps_api_setup.md`

**Quick Steps:**
1. Go to https://console.cloud.google.com/
2. Create project: "Zyra Spatial Intelligence"
3. Enable APIs: Directions, Places, Geocoding
4. Create API key
5. Restrict by IP (your VPS IP)
6. Add to `local.properties`:
   ```properties
   GOOGLE_MAPS_API_KEY=AIzaSyD...your-key-here
   ```

---

## 📝 Add to local.properties

Create or update `local.properties` in project root:

```properties
# Android SDK (already exists)
sdk.dir=C:\\Users\\SHIJIN P S\\AppData\\Local\\Android\\Sdk

# API Keys (add these)
GOOGLE_MAPS_API_KEY=YOUR_KEY_HERE
IBM_QUANTUM_API_KEY=snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo
CISCO_QUANTUM_API_KEY=gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G
```

---

## 🔧 Update build.gradle.kts

Add BuildConfig fields to `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        // ... existing config ...
        
        // Read API keys from local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        
        buildConfigField(
            "String",
            "GOOGLE_MAPS_API_KEY",
            "\"${properties.getProperty("GOOGLE_MAPS_API_KEY", "")}\""
        )
        
        buildConfigField(
            "String",
            "IBM_QUANTUM_API_KEY",
            "\"${properties.getProperty("IBM_QUANTUM_API_KEY", "snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo")}\""
        )
        
        buildConfigField(
            "String",
            "CISCO_QUANTUM_API_KEY",
            "\"${properties.getProperty("CISCO_QUANTUM_API_KEY", "gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G")}\""
        )
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

---

## 🌐 n8n Environment Variables

Add to your n8n instance (via Coolify):

```bash
GOOGLE_MAPS_API_KEY=YOUR_KEY_HERE
IBM_QUANTUM_API_KEY=snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo
CISCO_QUANTUM_API_KEY=gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G
```

Then use in workflows:
```
={{$env.GOOGLE_MAPS_API_KEY}}
```

---

## ✅ Checklist

- [x] IBM Quantum API configured
- [x] Cisco Quantum API configured
- [ ] Google Maps API key obtained
- [ ] `local.properties` updated
- [ ] `build.gradle.kts` updated with BuildConfig
- [ ] n8n environment variables set
- [ ] Workflows updated with API keys
- [ ] Test API calls

---

## 🔒 Security Notes

1. **Never commit `local.properties` to Git**
   - Already in `.gitignore`
   - Contains sensitive API keys

2. **Restrict Google Maps API key**
   - By IP address (your VPS)
   - By Android app (package name + SHA-1)

3. **Rotate keys regularly**
   - Every 90 days recommended
   - Update in all locations

4. **Monitor usage**
   - Google Maps: https://console.cloud.google.com/apis/dashboard
   - Set budget alerts ($10/month)

---

## 📊 API Usage Estimates

| API | Monthly Requests | Cost |
|-----|-----------------|------|
| Google Maps (Directions) | ~3,000 | $0 (free tier) |
| Google Maps (Places) | ~3,000 | $0 (free tier) |
| IBM Quantum | ~100 circuits | Free tier |
| Cisco Quantum QRNG | ~1,000 | Free tier |

**Total Monthly Cost: $0** (within free tiers)

---

## 🎉 Ready to Deploy!

Once Google Maps API key is added, all APIs will be configured and ready for production use.
