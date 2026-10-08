param([string]$SdkRoot = "D:/GPT/tools/android-sdk")
$ErrorActionPreference = "Stop"
$toolBin = Join-Path $SdkRoot "ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin"
$romBuild = Join-Path $PSScriptRoot "build"
New-Item -ItemType Directory -Force $romBuild | Out-Null
& "$toolBin/clang.exe" --target=arm-none-eabi -mcpu=arm7tdmi -c "$PSScriptRoot/bringup.s" -o "$romBuild/bringup.o"
if ($LASTEXITCODE -ne 0) { throw "Homebrew assembly failed" }
& "$toolBin/ld.lld.exe" -Ttext=0x08000000 -e _start "$romBuild/bringup.o" -o "$romBuild/bringup.elf"
if ($LASTEXITCODE -ne 0) { throw "Homebrew link failed" }
& "$toolBin/llvm-objcopy.exe" -O binary "$romBuild/bringup.elf" "$romBuild/bringup.gba"
if ($LASTEXITCODE -ne 0) { throw "Homebrew extraction failed" }
$assetDir = Join-Path $PSScriptRoot "../core-mgba/src/androidTest/assets"
New-Item -ItemType Directory -Force $assetDir | Out-Null
Copy-Item -LiteralPath "$romBuild/bringup.gba" -Destination "$assetDir/bringup.gba"
$appAssetDir = Join-Path $PSScriptRoot "../app/src/androidTest/assets"
New-Item -ItemType Directory -Force $appAssetDir | Out-Null
Copy-Item -LiteralPath "$romBuild/bringup.gba" -Destination "$appAssetDir/bringup.gba"
Get-FileHash -LiteralPath "$romBuild/bringup.gba" -Algorithm SHA256
& "$toolBin/clang.exe" --target=arm-none-eabi -mcpu=arm7tdmi -c "$PSScriptRoot/persistence.s" -o "$romBuild/persistence.o"
if ($LASTEXITCODE -ne 0) { throw "Persistence homebrew assembly failed" }
& "$toolBin/ld.lld.exe" -Ttext=0x08000000 -e _start "$romBuild/persistence.o" -o "$romBuild/persistence.elf"
if ($LASTEXITCODE -ne 0) { throw "Persistence homebrew link failed" }
& "$toolBin/llvm-objcopy.exe" -O binary "$romBuild/persistence.elf" "$romBuild/persistence.gba"
if ($LASTEXITCODE -ne 0) { throw "Persistence homebrew extraction failed" }
Copy-Item -LiteralPath "$romBuild/persistence.gba" -Destination "$assetDir/persistence.gba"
Copy-Item -LiteralPath "$romBuild/persistence.gba" -Destination "$appAssetDir/persistence.gba"
Get-FileHash -LiteralPath "$romBuild/persistence.gba" -Algorithm SHA256
& 'C:/Users/minc/AppData/Local/Programs/Python/Python314/python.exe' "$PSScriptRoot/visual-patterns.py"
if ($LASTEXITCODE -ne 0) { throw "Visual patterns generation failed" }
Push-Location $romBuild
try {
    foreach($patternName in @('color-pattern','lcd-pattern')) {
        & "$toolBin/clang.exe" --target=arm-none-eabi -mcpu=arm7tdmi -c "$patternName.s" -o "$patternName.o"
        if ($LASTEXITCODE -ne 0) { throw "Visual assembly failed" }
        & "$toolBin/ld.lld.exe" -Ttext=0x08000000 -e _start "$patternName.o" -o "$patternName.elf"
        if ($LASTEXITCODE -ne 0) { throw "Visual link failed" }
        & "$toolBin/llvm-objcopy.exe" -O binary "$patternName.elf" "$patternName.gba"
        if ($LASTEXITCODE -ne 0) { throw "Visual extraction failed" }
        Copy-Item -LiteralPath "$patternName.gba" -Destination "$assetDir/$patternName.gba"
        Copy-Item -LiteralPath "$patternName.gba" -Destination "$appAssetDir/$patternName.gba"
        Copy-Item -LiteralPath "$patternName.rgba" -Destination "$assetDir/$patternName.rgba"
        Get-FileHash -LiteralPath "$patternName.gba" -Algorithm SHA256
    }
} finally { Pop-Location }
