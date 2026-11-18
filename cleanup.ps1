# Remove Aliascall-only java files that are not part of the WAFA kit
$J = 'C:\wafa-app\android\app\src\main\java'
$keep = @('MainActivity.java','BillingBridge.java','CallConnectionService.java','CallMessagingService.java','CallOngoingService.java','IncomingCallActivity.java','NureumWidget.java')
$kit = 'C:\wafa-app\android\app\src\main\java\com\weaveapp\alias'
Get-ChildItem -Path $J -Recurse -File | ForEach-Object {
  $inKit = ($_.DirectoryName -eq $kit) -and ($keep -contains $_.Name)
  if (-not $inKit) { Remove-Item -Force $_.FullName; Write-Host ("removed " + $_.FullName) }
}
Write-Host ""
Write-Host "Remaining java files:"
Get-ChildItem -Path $J -Recurse -File | ForEach-Object { Write-Host ("  " + $_.FullName) }
