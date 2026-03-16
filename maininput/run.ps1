# VC++ → C# 変換実行スクリプト
# 使用方法: .\run.ps1 [ファイル名.cpp] [-NoDiscover] [-NoExcel]
# 引数省略時はディレクトリ全体を変換（Excel 出力なし）。ファイル指定時は Excel 出力あり（-NoExcel で無効化可）

param(
    [string]$InputFile = "",
    [switch]$NoDiscover,
    [switch]$NoExcel
)

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RulesDir = Join-Path $ProjectRoot "src\main\resources\rules"
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
# 引数なし（ディレクトリ一括）のときは Excel を出さない。ファイル指定時は -NoExcel で無効化可能
if ($NoExcel -or $InputFile -eq "") {
    $ExtraArgs += " --no-excel"
}

& mvn -q exec:java `
    "-Dexec.mainClass=io.github.takahino.cpp2csharp.Main" `
    "-Dexec.args=$ExtraArgs $Target $RulesDir"

Pop-Location
