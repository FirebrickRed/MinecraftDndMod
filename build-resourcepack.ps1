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

# Build the zip manually with FORWARD-SLASH entry names. PowerShell's Compress-Archive
# (and .NET's CreateFromDirectory on Windows PowerShell 5.1) write Windows BACKSLASHES into
# the zip entries, which Minecraft cannot read — the pack downloads but every asset path is
# broken. So we add each file ourselves, rooting entries at the pack folder with '/' separators.
Add-Type -AssemblyName System.IO.Compression | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

$packRoot = (Resolve-Path $packDir).Path.TrimEnd('\','/')
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path (Get-Location) $outZip), 'Create')
try {
    foreach ($file in Get-ChildItem -LiteralPath $packDir -Recurse -File) {
        $entryName = $file.FullName.Substring($packRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $file.FullName, $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

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
