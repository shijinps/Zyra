# Quick Test Script
# Run this in PowerShell to test your spatial intelligence workflow

Write-Host "Testing Zyra Spatial Intelligence Workflow..." -ForegroundColor Cyan
Write-Host ""

# Test data
$body = @{
    current_lat = 10.0266
    current_lng = 76.3122
    destination_lat = 10.5276
    destination_lng = 76.2144
    destination_name = "Thrissur"
    place_type = "atm"
    max_detour_minutes = 2
} | ConvertTo-Json

Write-Host "Sending request to n8n..." -ForegroundColor Yellow
Write-Host "URL: https://n8n.shijin.in/webhook/zyra/spatial-query" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri "https://n8n.shijin.in/webhook/zyra/spatial-query" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body `
        -ErrorAction Stop
    
    Write-Host "✅ SUCCESS!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Summary:" -ForegroundColor Cyan
    Write-Host $response.summary -ForegroundColor White
    Write-Host ""
    Write-Host "Places Found: $($response.places_count)" -ForegroundColor Cyan
    Write-Host ""
    
    if ($response.audio_url) {
        Write-Host "Audio URL: Present ✅" -ForegroundColor Green
        
        # Ask if user wants to save audio
        $save = Read-Host "Save audio to Desktop? (y/n)"
        if ($save -eq 'y') {
            $audioBase64 = $response.audio_url -replace 'data:audio/mpeg;base64,', ''
            $audioBytes = [Convert]::FromBase64String($audioBase64)
            $audioPath = "$env:USERPROFILE\Desktop\zyra_response.mp3"
            [IO.File]::WriteAllBytes($audioPath, $audioBytes)
            Write-Host "Audio saved to: $audioPath" -ForegroundColor Green
            Write-Host "You can play it now!" -ForegroundColor Yellow
        }
    }
    
    Write-Host ""
    Write-Host "🎉 All systems working!" -ForegroundColor Green
    
} catch {
    Write-Host "❌ ERROR!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error Message:" -ForegroundColor Yellow
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Cyan
    Write-Host "1. Check if workflow is Active in n8n" -ForegroundColor White
    Write-Host "2. Verify Groq and ElevenLabs credentials" -ForegroundColor White
    Write-Host "3. Check n8n execution logs for details" -ForegroundColor White
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
