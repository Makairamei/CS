# ============================================================
# change-server.ps1
# Ganti SERVER_URL di semua LicenseClient.kt sekaligus
# 
# Cara pakai:
#   .\change-server.ps1 -NewUrl "http://159.223.82.116:3000"
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$NewUrl
)

$NewUrl = $NewUrl.TrimEnd('/')
$pattern = 'private const val SERVER_URL = ".*?"'
$replacement = "private const val SERVER_URL = `"$NewUrl`""

$files = Get-ChildItem -Path $PSScriptRoot -Recurse -Filter "LicenseClient.kt" |
         Where-Object { $_.FullName -notlike "*build*" -and $_.FullName -notlike "*_template*" }

$updated = 0
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $newContent = $content -replace $pattern, $replacement
    if ($content -ne $newContent) {
        Set-Content $file.FullName $newContent -NoNewline
        $updated++
    }
}

Write-Host "✅ Done! Updated SERVER_URL to '$NewUrl' in $updated LicenseClient.kt files."
Write-Host ""
Write-Host "Next steps:"
Write-Host "  git add -A"
Write-Host "  git commit -m `"config: change server URL to $NewUrl`""
Write-Host "  git push origin main"
