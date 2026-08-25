# Packages the local resource pack into a distributable .zip and prints the
# server.properties lines (URL + sha1) needed to serve it to players.
#
# Usage (from repo root):   ./build-resourcepack.ps1
#
# The pack folder is gitignored, so this runs against your local copy. The zip
# is written to the repo root; upload it somewhere with a direct-download URL
# (e.g. a GitHub Release asset) and paste that URL below the sha1.

$ErrorActionPreference = "Stop"

$packDir = "ResourcePack/JKVTTResourcePack"
$outZip  = "jkvttresourcepack.zip"

if (-not (Test-Path (Join-Path $packDir "pack.mcmeta"))) {
    Write-Error "No pack.mcmeta found in $packDir - is the resource pack in place?"
    exit 1
}

# Rebuild from scratch so stale files never linger in the zip.
if (Test-Path $outZip) { Remove-Item $outZip -Force }

# Compress the CONTENTS of the pack folder (pack.mcmeta + assets/) so they land
# at the ZIP ROOT. Minecraft rejects a pack whose files are nested one level down.
Compress-Archive -Path (Join-Path $packDir "*") -DestinationPath $outZip -CompressionLevel Optimal

$sha1 = (Get-FileHash -Algorithm SHA1 -Path $outZip).Hash.ToLower()
$sizeKb = [math]::Round((Get-Item $outZip).Length / 1KB, 1)

Write-Host ""
Write-Host "Built $outZip ($sizeKb KB)" -ForegroundColor Green
Write-Host ""
Write-Host "Add these to server.properties (replace the URL with your hosted zip link):" -ForegroundColor Cyan
Write-Host "  resource-pack=https://YOUR-HOST/jkvttresourcepack.zip"
Write-Host "  resource-pack-sha1=$sha1"
Write-Host "  require-resource-pack=true"
Write-Host ""
Write-Host "Re-run this and update the sha1 every time you change the pack." -ForegroundColor DarkGray
