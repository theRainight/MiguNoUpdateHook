$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$repo = Split-Path (Split-Path $root -Parent) -Parent
$bt = 'F:\flutterSDK\android-sdk-windows\build-tools\35.0.0'
$androidJar = 'F:\flutterSDK\android-sdk-windows\platforms\android-35\android.jar'
$xposedApiJar = Join-Path $repo 'work\libxposed\api-aar\classes.jar'
$outDir = Join-Path $repo 'outputs'

Remove-Item -Recurse -Force "$root\build\classes", "$root\build\dex", "$root\build\res" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$root\build\classes", "$root\build\dex", "$root\build\res", $outDir | Out-Null

$sources = @(Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -source 8 -target 8 -classpath "$androidJar;$xposedApiJar" -d "$root\build\classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'module javac failed' }

Push-Location "$root\build\classes"
& jar cf "..\module-classes.jar" .
Pop-Location

& "$bt\d8.bat" --min-api 26 --output "$root\build\dex" "$root\build\module-classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$root\build\res\compiled.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

& "$bt\aapt2.exe" link -o "$root\build\unsigned.apk" -I $androidJar --manifest "$root\AndroidManifest.xml" "$root\build\res\compiled.zip" --min-sdk-version 26 --target-sdk-version 35 --version-code 101 --version-name 2.1-api102 --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Push-Location "$root\build\dex"
& jar uf "..\unsigned.apk" classes.dex
Pop-Location

Push-Location "$root\meta"
& jar uf "..\build\unsigned.apk" META-INF
Pop-Location

& "$bt\zipalign.exe" -f -p 4 "$root\build\unsigned.apk" "$root\build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

if (!(Test-Path "$root\build\debug.keystore")) {
  & keytool -genkeypair -v -keystore "$root\build\debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" | Out-Null
}

$apk = "$root\build\MiguNoUpdateHook-API102.apk"
& "$bt\apksigner.bat" sign --ks "$root\build\debug.keystore" --ks-pass pass:android --key-pass pass:android --out $apk "$root\build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'sign failed' }

& "$bt\apksigner.bat" verify --verbose $apk
Copy-Item -Force $apk (Join-Path $outDir 'MiguNoUpdateHook-API102.apk')
Get-Item (Join-Path $outDir 'MiguNoUpdateHook-API102.apk') | Select-Object FullName,Length
