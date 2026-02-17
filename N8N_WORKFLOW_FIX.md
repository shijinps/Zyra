# n8n Workflow Fix - Corrected JavaScript Code

## 🔴 Current Problem

The "Parse Query" node is expecting parameters like:
- `destination_lat`, `destination_lng`, `destination_name`
- `max_detour_minutes`

But your test is sending:
- `latitude`, `longitude`, `place_type`, `radius`

These don't match, so the workflow fails!

---

## ✅ Solution: Replace the JavaScript Code

### Step 1: Open the "Parse Query" Node in n8n

1. Go to https://n8n.shijin.in
2. Open "Zyra Spatial Intelligence" workflow
3. Click on **"Parse Query"** node
4. Click on **"Parameters"** tab

### Step 2: Replace ALL the Code

**Delete everything** in the code editor and paste this:

```javascript
// Simple parser for spatial query
const body = $input.item.json.body || $input.item.json;

// Return the data in the format the next nodes expect
return {
  latitude: body.latitude || 12.9716,
  longitude: body.longitude || 77.5946,
  place_type: body.place_type || 'restaurant',
  radius: body.radius || 1000
};
```

### Step 3: Save and Test

1. Click **"Execute Node"** to test the code
2. If it shows output with latitude, longitude, etc., it's working!
3. Click **"Save"** (top right)
4. Run the PowerShell test again

---

## 🎯 Alternative: Even Simpler Fix

If the above still doesn't work, use this **ultra-simple** version:

```javascript
// Just pass through the webhook data
return $input.item.json.body || $input.item.json;
```

This just passes the data through without any processing.

---

## 🔍 Next Node Issue

After fixing "Parse Query", the **next node** (probably "Get Route" or "Sample Route Points") might also fail because it's expecting different data.

**Quick fix for the whole workflow:**

1. **Simplify the workflow** to just return a test response
2. **Skip all the complex route/place logic** for now
3. **Just test that the webhook works**

### Test Workflow (Minimal)

Replace the entire workflow logic with this simple test:

1. **Webhook** receives data
2. **Code** node returns static response:
   ```javascript
   return {
     summary: "Test successful! Webhook is working.",
     audio_url: "https://example.com/test.mp3",
     place_count: 0,
     status: "ok"
   };
   ```
3. **Respond to Webhook** sends the response

This tests if the basic n8n setup works without calling any external APIs.

---

## 📋 What to Do Now

**Option 1: Fix the Parse Query code** (paste the simple JavaScript above)

**Option 2: Create a minimal test workflow** (just return static data)

**Option 3: Share the n8n workflow JSON** so I can fix it completely and send you a corrected version

Which option would you prefer? I recommend **Option 3** - I can create a working workflow JSON file that you can import directly into n8n!
