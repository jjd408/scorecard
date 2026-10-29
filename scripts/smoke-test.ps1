# Installs the .msi that scripts/package.sh built, starts Scorecard, and checks that it comes up: it must
# create its database and still be running a few seconds later. It also reports the window title and takes a
# screenshot where the desktop allows, into target\smoke. For the Windows build machine in
# .github/workflows/release.yml (installing needs administrator rights).
$ErrorActionPreference = 'Stop'

$out = Join-Path $PWD 'target\smoke'
New-Item -ItemType Directory -Force $out | Out-Null
$msi = (Get-ChildItem 'target\installers\*.msi' | Select-Object -First 1).FullName

$install = Start-Process msiexec.exe -Wait -PassThru `
    -ArgumentList '/i', "`"$msi`"", '/qn', '/l*v', "`"$out\install.log`""
if ($install.ExitCode -ne 0) { throw "msiexec failed with exit code $($install.ExitCode); see install.log" }

$exe = Join-Path $env:ProgramFiles 'Scorecard\Scorecard.exe'
if (-not (Test-Path $exe)) { throw "Scorecard.exe is not where the installer should put it: $exe" }
$app = Start-Process $exe -PassThru

$db = Join-Path $env:USERPROFILE '.scorecard\scorecard.db'
for ($i = 0; $i -lt 60 -and -not (Test-Path $db); $i++) { Start-Sleep -Seconds 1 }
if (-not (Test-Path $db)) { throw "The app did not create its database at $db" }
Start-Sleep -Seconds 5
$running = Get-Process -Id $app.Id -ErrorAction SilentlyContinue
if (-not $running) { throw 'Scorecard exited' }
Write-Host "Window title: '$($running.MainWindowTitle)'"

try {
    Add-Type -AssemblyName System.Windows.Forms, System.Drawing
    $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
    $bitmap = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
    [System.Drawing.Graphics]::FromImage($bitmap).CopyFromScreen($screen.Left, $screen.Top, 0, 0, $bitmap.Size)
    $bitmap.Save("$out\windows.png")
} catch {
    Write-Host "No screenshot: $_"
}

Stop-Process -Id $app.Id
Write-Host "Smoke test passed: Scorecard installed, started, and created $db"
