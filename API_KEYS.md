# API Keys Summary

## 🔑 API Keys

### Google Maps API
- **Status:** ✅ Configured
- **Cost:** FREE ($200/month credit = ~40,000 requests)
- **Required For:** Spatial intelligence, route calculation, places search
- **Setup Guide:** See `google_maps_api_setup.md`

---

## 📝 Add to local.properties

Create or update `local.properties` in project root:

```properties
# Android SDK (already exists)
sdk.dir=C:\\Users\\SHIJIN P S\\AppData\\Local\\Android\\Sdk

# API Keys
GOOGLE_MAPS_API_KEY=YOUR_KEY_HERE
PICOVOICE_ACCESS_KEY=YOUR_KEY_HERE
OPENWEATHER_API_KEY=YOUR_KEY_HERE
NEWS_API_KEY=YOUR_KEY_HERE
ALPHA_VANTAGE_API_KEY=YOUR_KEY_HERE
GOOGLE_TRANSLATE_API_KEY=YOUR_KEY_HERE
```

---

## 🔧 Update build.gradle.kts

BuildConfig fields in `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        // Read API keys from local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        
        buildConfigField(
            "String",
            "GOOGLE_MAPS_API_KEY",
            "\"${properties.getProperty("GOOGLE_MAPS_API_KEY", "")}\""
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
```

Then use in workflows:
```
={{$env.GOOGLE_MAPS_API_KEY}}
```

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
