param([string]$SdkRoot=$env:ANDROID_HOME,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectDir=$PSScriptRoot
if (!$JavaHome) { $JavaHome='C:\Users\LENOVO\.codex\toolchains\cmfa-themes\jdk' }
if ($SdkRoot) { $platform=Join-Path $SdkRoot 'platforms\android-36'; $buildTools=Join-Path $SdkRoot 'build-tools\36.0.0' }
else { $platform=Join-Path $projectDir '.tools\platform36\android-36'; $buildTools=Join-Path $projectDir '.tools\buildtools36\android-16' }
$java=Join-Path $JavaHome 'bin\java.exe'; $javac=Join-Path $JavaHome 'bin\javac.exe'; $jar=Join-Path $JavaHome 'bin\jar.exe'
$androidJar=Join-Path $platform 'android.jar'
foreach ($required in @($java,$javac,$androidJar,(Join-Path $buildTools 'aapt2.exe'))) { if (!(Test-Path -LiteralPath $required)) { throw "Required tool missing: $required. Install JDK 17+ and Android SDK platform/build-tools 36." } }
function Run-Checked([string]$exe,[string[]]$arguments) { & $exe @arguments; if($LASTEXITCODE -ne 0) { throw "Build failed: $exe (exit $LASTEXITCODE)" } }
$buildDir=Join-Path $projectDir 'build\manual';$classes=Join-Path $buildDir 'classes';$dex=Join-Path $buildDir 'dex';$generated=Join-Path $buildDir 'generated';$dist=Join-Path $projectDir 'dist';$private=Join-Path $projectDir '.private'
# Clean only the verified, generated manual build directory inside this project.
if(Test-Path -LiteralPath $buildDir){$resolved=[IO.Path]::GetFullPath($buildDir);$expected=[IO.Path]::GetFullPath((Join-Path $projectDir 'build\manual'));if($resolved -ne $expected){throw 'Unexpected build directory'};Remove-Item -LiteralPath $resolved -Recurse -Force}
foreach($folder in @($classes,$dex,$generated,$dist,$private)){New-Item -ItemType Directory -Force -Path $folder | Out-Null}
$main=Join-Path $projectDir 'app\src\main';$resourceZip=Join-Path $buildDir 'resources.zip';$unsigned=Join-Path $buildDir 'unsigned.apk'
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('compile','--dir',(Join-Path $main 'res'),'-o',$resourceZip)
$buildManifest=Join-Path $buildDir 'AndroidManifest.xml';[xml]$manifest=Get-Content -LiteralPath (Join-Path $main 'AndroidManifest.xml') -Raw;$manifest.DocumentElement.SetAttribute('package','cn.kejian.timetable');$manifest.Save($buildManifest)
Run-Checked (Join-Path $buildTools 'aapt2.exe') @('link','-o',$unsigned,'--manifest',$buildManifest,'-I',$androidJar,'--java',$generated,'-A',(Join-Path $main 'assets'),'--min-sdk-version','26','--target-sdk-version','36','--version-code','6','--version-name','0.3.3',$resourceZip)
$sources=@(Get-ChildItem (Join-Path $main 'java'),$generated -Filter '*.java' -Recurse | ForEach-Object FullName)
$javacArgs=@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',$classes)+$sources
Run-Checked $javac $javacArgs
$classJar=Join-Path $buildDir 'classes.jar';Run-Checked $jar @('cf',$classJar,'-C',$classes,'.')
Run-Checked $java @('-cp',(Join-Path $buildTools 'lib\d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',$androidJar,'--output',$dex,$classJar)
Run-Checked $jar @('uf',$unsigned,'-C',$dex,'classes.dex')
$aligned=Join-Path $buildDir 'aligned.apk';Run-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','-p','4',$unsigned,$aligned)
$keystore=Join-Path $private 'local-test.jks'
# Local testing key. Keep this file private and retain it for future in-place updates.
if(!(Test-Path -LiteralPath $keystore)){Run-Checked (Join-Path $JavaHome 'bin\keytool.exe') @('-genkeypair','-keystore',$keystore,'-storepass','android','-keypass','android','-alias','kejian','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Kejian Local Test')}
$apk=Join-Path $dist 'Kejian-0.3.3.apk'
Run-Checked $java @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'sign','--v4-signing-enabled','false','--ks',$keystore,'--ks-key-alias','kejian','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Run-Checked $java @('-jar',(Join-Path $buildTools 'lib\apksigner.jar'),'verify','--verbose',$apk)
Run-Checked (Join-Path $buildTools 'zipalign.exe') @('-c','-p','4',$apk)
Get-Item -LiteralPath $apk | Select-Object FullName,Length
Get-FileHash -LiteralPath $apk -Algorithm SHA256
