param(
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [Parameter(Mandatory=$true)][string]$KotlinLib
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    # KotlinLib can be a Gradle distribution's lib directory (tested with 8.9).
    $stdlib = Get-ChildItem -LiteralPath $KotlinLib -Filter 'kotlin-stdlib-*.jar' |
        Where-Object { $_.Name -match '^kotlin-stdlib-[0-9]' } | Select-Object -First 1
    $annotations = Get-ChildItem -LiteralPath $KotlinLib -Filter 'annotations-*.jar' | Select-Object -First 1
    if (!$stdlib -or !$annotations) { throw 'Kotlin standard library / annotations not found' }
    $sources = @(Get-ChildItem app/src/main/java/com/cubetrace/app/core/cube/*.kt,
        app/src/main/java/com/cubetrace/app/core/model/*.kt,
        app/src/main/java/com/cubetrace/app/core/analysis/*.kt | Select-Object -ExpandProperty FullName)
    $sources += (Resolve-Path app/src/main/java/com/cubetrace/app/core/device/V10Protocol.kt).Path
    $sources += @(Get-ChildItem app/src/test/java/com/cubetrace/app/core/analysis/*.kt,
        app/src/test/java/com/cubetrace/app/core/cube/*.kt | Select-Object -ExpandProperty FullName)
    New-Item -ItemType Directory -Force scratch/core-checks | Out-Null
    & $JavaExe -cp "$KotlinLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
        -classpath "$($stdlib.FullName);$($annotations.FullName)" -d scratch/core-checks @sources
    if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
    foreach ($entry in @('analysis.AnalysisRegressionChecks', 'analysis.ReplayTimelineChecks', 'cube.PracticeScrambleChecks')) {
        & $JavaExe -cp "scratch/core-checks;$($stdlib.FullName)" "com.cubetrace.app.core.$entry"
        if ($LASTEXITCODE -ne 0) { throw "Failed: $entry" }
    }
} finally {
    Pop-Location
}
