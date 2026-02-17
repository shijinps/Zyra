# Quick Fix for n8n Timeout

## 🔴 Problem
Your n8n workflow is timing out at the "Parse Query" node after 60 seconds.

## 🎯 Most Likely Fix

**The "Parse Query" node is missing the Google Maps API key.**

### Steps to Fix:

1. **Open n8n**: Go to https://n8n.shijin.in

2. **Open the workflow**: "Zyra Spatial Intelligence"

3. **Click on "Parse Query" node** (or the node that's failing)

4. **Look for these fields**:
   - API Key
   - Google Maps API Key
   - Or similar field name

5. **Add your Google Maps API key**:
   ```
   (Your key from local.properties)
   ```

6. **Save the workflow**

7. **Test again** with the PowerShell command

---

## 🧪 Alternative: Test with Simpler Request

If the above doesn't work, let's test if the basic webhook works:

```powershell
# Simpler test without location data
$body = @{
    test = "hello"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" -Method Post -ContentType "application/json" -Body $body
```

---

## 📋 What to Check in n8n

1. Click on the **failed execution** (red one in the list)
2. Look at the **error message** in the "Parse Query" node
3. Check if it says:
   - "API key not found" → Add the key
   - "Invalid API key" → Check the key is correct
   - "Quota exceeded" → Enable the API in Google Cloud
   - Other error → Share the exact error message

---

## 🔍 Next Steps

**Please do this**:
1. Open the failed execution in n8n
2. Click on the "Parse Query" node
3. Take a screenshot of the error message
4. Share what the error says

This will help me give you the exact fix!
