# WAFA Android kit - applies onto C:\wafa-app\android\app
# Before: copy the whole folder C:\baronu-app  ->  C:\wafa-app   (Baronu = Burum Android v30 base)
$ErrorActionPreference = 'Stop'
$R = 'C:\wafa-app'
$T = "$R\android\app"
$K = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not (Test-Path "$T\build.gradle")) { Write-Host "ERROR: $T\build.gradle not found. Copy C:\baronu-app to C:\wafa-app first."; exit 1 }
$utf8 = New-Object System.Text.UTF8Encoding $false

# 1) remove other app java folders (WAFA uses the Burum code in com\weaveapp\alias)
foreach ($d in @("$T\src\main\java\com\weaveapp\aliascall", "$T\src\main\java\com\weaveapp\baronu", "$T\src\main\java\com\weaveapp\wafa")) {
  if (Test-Path $d) { Remove-Item -Recurse -Force $d; Write-Host "removed $d" }
}
# 2) remove old launcher icons (.webp) - the kit uses .png and both together = "Duplicate resources"
Get-ChildItem "$T\src\main\res" -Recurse -Include 'ic_launcher*.webp','splash.webp' -ErrorAction SilentlyContinue | ForEach-Object { Remove-Item -Force $_.FullName; Write-Host ("removed " + $_.Name) }
# 2b) copy kit files (java, manifest, icons, splash, build.gradle)
Copy-Item -Recurse -Force "$K\app\*" "$T\"
Write-Host "copied kit files"
if (-not (Test-Path "$T\src\main\res\xml\file_paths.xml")) {
  Copy-Item -Force "$K\extra\file_paths.xml" "$T\src\main\res\xml\file_paths.xml"; Write-Host "added file_paths.xml"
}
# 3) strings.xml
$p = "$T\src\main\res\values\strings.xml"
$s = [IO.File]::ReadAllText($p)
$s = [regex]::Replace($s, '(<string name="app_name">)[^<]*(</string>)', '${1}WAFA${2}')
$s = [regex]::Replace($s, '(<string name="title_activity_main">)[^<]*(</string>)', '${1}WAFA${2}')
$s = [regex]::Replace($s, '(<string name="package_name">)[^<]*(</string>)', '${1}com.weaveapp.wafa${2}')
$s = [regex]::Replace($s, '(<string name="custom_url_scheme">)[^<]*(</string>)', '${1}com.weaveapp.wafa${2}')
[IO.File]::WriteAllText($p, $s, $utf8); Write-Host "strings.xml done"
# 4) capacitor.config.json (android copy + project root)
foreach ($p in @("$T\src\main\assets\capacitor.config.json", "$R\capacitor.config.json")) {
  if (-not (Test-Path $p)) { continue }
  $s = [IO.File]::ReadAllText($p)
  $s = [regex]::Replace($s, '"appId"\s*:\s*"[^"]*"', '"appId": "com.weaveapp.wafa"')
  $s = [regex]::Replace($s, '"appName"\s*:\s*"[^"]*"', '"appName": "WAFA"')
  $s = [regex]::Replace($s, '"url"\s*:\s*"[^"]*"', '"url": "https://wafa.kr/wafa_start.html"')
  $s = $s -replace 'baronu\.kr', 'wafa.kr' -replace 'aliascall\.com', 'wafa.kr'
  [IO.File]::WriteAllText($p, $s, $utf8); Write-Host "$p done"
}
# 5) check google-services.json
if (-not (Test-Path "$T\google-services.json")) { Write-Host "WARNING: google-services.json missing in $T" }
elseif (-not (Select-String -Path "$T\google-services.json" -Pattern 'com.weaveapp.wafa' -Quiet)) { Write-Host "WARNING: google-services.json does not include com.weaveapp.wafa - download it again from Firebase" }
else { Write-Host "google-services.json OK" }
Write-Host ""
Write-Host "DONE. Open C:\wafa-app\android in Android Studio -> File > Sync Project with Gradle Files -> Build > Generate Signed App Bundle."
