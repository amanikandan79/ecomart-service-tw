# Scan Runbook (Sonar + Black Duck)

## Required environment variables

### Sonar
- `SONAR_HOST_URL`
- `SONAR_TOKEN`
- `SONAR_PROJECT_KEY`

### Black Duck
- `BLACKDUCK_URL`
- `BLACKDUCK_API_TOKEN`
- `DETECT_PROJECT_NAME`
- `DETECT_PROJECT_VERSION_NAME`

## Run scans (from repo root, Windows PowerShell)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-sonar.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-blackduck.ps1
```
