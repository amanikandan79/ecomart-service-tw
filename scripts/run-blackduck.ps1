param(
    [string]$ProjectName = $env:DETECT_PROJECT_NAME,
    [string]$ProjectVersion = $env:DETECT_PROJECT_VERSION_NAME
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:BLACKDUCK_URL)) {
    throw "BLACKDUCK_URL is required."
}
if ([string]::IsNullOrWhiteSpace($env:BLACKDUCK_API_TOKEN)) {
    throw "BLACKDUCK_API_TOKEN is required."
}
if ([string]::IsNullOrWhiteSpace($ProjectName)) {
    throw "DETECT_PROJECT_NAME is required (argument or environment)."
}
if ([string]::IsNullOrWhiteSpace($ProjectVersion)) {
    throw "DETECT_PROJECT_VERSION_NAME is required (argument or environment)."
}

./gradlew check
if (-not $?) {
    throw "Gradle check failed."
}

$detectCmd = Get-Command "blackduck-detect" -ErrorAction SilentlyContinue
if (-not $detectCmd) {
    $fallback = "C:\Users\amani\tools\bin\blackduck-detect.cmd"
    if (-not (Test-Path $fallback)) {
        throw "blackduck-detect command not found."
    }
    $detectCmd = @{ Source = $fallback }
}

& $detectCmd.Source `
  "--blackduck.url=$($env:BLACKDUCK_URL)" `
  "--blackduck.api.token=$($env:BLACKDUCK_API_TOKEN)" `
  "--detect.project.name=$ProjectName" `
  "--detect.project.version.name=$ProjectVersion" `
  "--detect.source.path=." `
  "--detect.tools=DETECTOR" `
  "--detect.detector.buildless=true" `
  "--detect.cleanup=false"

if ($LASTEXITCODE -ne 0) {
    throw "Black Duck scan failed with exit code $LASTEXITCODE."
}
