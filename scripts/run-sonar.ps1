param(
    [string]$ProjectKey = $env:SONAR_PROJECT_KEY
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) {
    throw "SONAR_HOST_URL is required."
}
if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) {
    throw "SONAR_TOKEN is required."
}
if ([string]::IsNullOrWhiteSpace($ProjectKey)) {
    throw "SONAR_PROJECT_KEY is required (argument or environment)."
}

./gradlew check
if (-not $?) {
    throw "Gradle check failed."
}

$scannerCmd = Get-Command "sonar-scanner" -ErrorAction SilentlyContinue
if (-not $scannerCmd) {
    $fallback = "C:\Users\amani\tools\bin\sonar-scanner.cmd"
    if (-not (Test-Path $fallback)) {
        throw "sonar-scanner command not found."
    }
    $scannerCmd = @{ Source = $fallback }
}

& $scannerCmd.Source `
  "-Dsonar.host.url=$($env:SONAR_HOST_URL)" `
  "-Dsonar.token=$($env:SONAR_TOKEN)" `
  "-Dsonar.projectKey=$ProjectKey"

if ($LASTEXITCODE -ne 0) {
    throw "Sonar scan failed with exit code $LASTEXITCODE."
}
