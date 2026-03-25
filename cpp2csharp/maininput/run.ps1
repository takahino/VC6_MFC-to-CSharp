# VC++ → C# 変換実行スクリプト
# 使用方法: .\run.ps1 [ファイル名.cpp] [-NoDiscover]

param(
    [string]$InputFile = "",
    [switch]$NoDiscover
)

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RulesDir = Join-Path $ProjectRoot "cpp2csharp\src\main\resources\rules"
$InputDir = $PSScriptRoot

Push-Location $ProjectRoot

if ($InputFile -ne "") {
    $Target = Join-Path $InputDir $InputFile
} else {
    $Target = $InputDir
}

$ExtraArgs = ""
if (-not $NoDiscover -and $InputFile -eq "") {
    # $ExtraArgs += " --discover"
}

echo "mvn -q install -DskipTests"
& mvn -q install -DskipTests
echo "mvn -q exec:java -pl cpp2csharp"
& mvn -q exec:java -pl cpp2csharp `
    "-Dexec.mainClass=io.github.takahino.cpp2csharp.Main" `
    "-Dexec.args=$ExtraArgs $Target $RulesDir"

Pop-Location
