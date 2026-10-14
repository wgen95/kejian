param([string]$JavaHome=$env:JAVA_HOME,[string]$SdkRoot=$env:ANDROID_HOME,[string]$NodeExecutable='node')
$ErrorActionPreference='Stop'
$sourceDir=$PSScriptRoot
$projectDir=[IO.Path]::GetFullPath((Join-Path $sourceDir '..\..'))
if (!$JavaHome) { $JavaHome='C:\Users\LENOVO\.codex\toolchains\cmfa-themes\jdk' }
$java=Join-Path $JavaHome 'bin\java.exe'
$javac=Join-Path $JavaHome 'bin\javac.exe'
$jar=Join-Path $JavaHome 'bin\jar.exe'
if ($SdkRoot) { $toolsDir=Join-Path $SdkRoot 'build-tools\36.0.0'; $androidJar=Join-Path $SdkRoot 'platforms\android-36\android.jar' }
else { $toolsDir=Join-Path $projectDir '.tools\buildtools36\android-16'; $androidJar=Join-Path $projectDir '.tools\platform36\android-36\android.jar' }
$keystore=Join-Path $projectDir '.private\local-test.jks'
foreach($required in @($java,$javac,$jar,$androidJar,(Join-Path $toolsDir 'aapt2.exe'),(Join-Path $toolsDir 'zipalign.exe'),(Join-Path $toolsDir 'lib\d8.jar'),(Join-Path $toolsDir 'lib\apksigner.jar'),$keystore)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required tool or matching local signing key missing: $required" }
}
if (!(Get-Command $NodeExecutable -ErrorAction SilentlyContinue)) { throw "Node executable missing: $NodeExecutable" }
# Native test APK and all generated native harness artifacts stay under build/native-test.
# The existing fixture generator also writes its reusable browser fixture to build/browser-test.
$testDir=Join-Path $projectDir 'build\native-test'
$out=Join-Path $testDir 'out'
foreach($folder in @($out,(Join-Path $out 'classes'),(Join-Path $out 'dex'),(Join-Path $testDir 'assets'))){New-Item -ItemType Directory -Force -Path $folder | Out-Null}
function Run([string]$file,[string[]]$arguments){& $file @arguments;if($LASTEXITCODE -ne 0){throw "Harness build failed ($LASTEXITCODE)"}}
Run $NodeExecutable @((Join-Path $projectDir 'tests\make-browser-fixture.cjs'))
Copy-Item -LiteralPath (Join-Path $projectDir 'build\browser-test\fixture.html') -Destination (Join-Path $testDir 'assets\fixture.html') -Force
$unsigned=Join-Path $out 'unsigned.apk'
Run (Join-Path $toolsDir 'aapt2.exe') @('link','-o',$unsigned,'--manifest',(Join-Path $sourceDir 'AndroidManifest.xml'),'-I',$androidJar,'-A',(Join-Path $testDir 'assets'),'--min-sdk-version','29','--target-sdk-version','36','--version-code','1','--version-name','test-only')
$sources=@(Get-ChildItem -LiteralPath (Join-Path $sourceDir 'java') -Recurse -Filter '*.java'|ForEach-Object FullName)
Run $javac (@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',(Join-Path $out 'classes'))+$sources)
$classesJar=Join-Path $out 'classes.jar'
Run $jar @('cf',$classesJar,'-C',(Join-Path $out 'classes'),'.')
Run $java @('-cp',(Join-Path $toolsDir 'lib\d8.jar'),'com.android.tools.r8.D8','--release','--min-api','29','--lib',$androidJar,'--output',(Join-Path $out 'dex'),$classesJar)
Run $jar @('uf',$unsigned,'-C',(Join-Path $out 'dex'),'classes.dex')
$aligned=Join-Path $out 'aligned.apk'
Run (Join-Path $toolsDir 'zipalign.exe') @('-f','-p','4',$unsigned,$aligned)
$apk=Join-Path $testDir 'Kejian-native-test.apk'
# Only read the existing private key; never generate, bundle, or publish a key or this test APK.
Run $java @('-jar',(Join-Path $toolsDir 'lib\apksigner.jar'),'sign','--v4-signing-enabled','false','--ks',$keystore,'--ks-key-alias','kejian','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Run $java @('-jar',(Join-Path $toolsDir 'lib\apksigner.jar'),'verify','--verbose',$apk)
Get-Item -LiteralPath $apk | Select-Object FullName,Length
