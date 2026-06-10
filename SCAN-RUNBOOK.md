# Scan Runbook (Sonar + Black Duck)

## 1. Required environment variables

### Sonar
- `SONAR_HOST_URL`
- `SONAR_TOKEN`
- `SONAR_PROJECT_KEY`

### Black Duck
- `BLACKDUCK_URL`
- `BLACKDUCK_API_TOKEN`
- `DETECT_PROJECT_NAME`
- `DETECT_PROJECT_VERSION_NAME`

## 2. Run scans

```powershell
# from repo root
powershell -ExecutionPolicy Bypass -File .\scripts\run-sonar.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-blackduck.ps1
```

Optional overrides:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-sonar.ps1 -ProjectKey "your-key"
powershell -ExecutionPolicy Bypass -File .\scripts\run-blackduck.ps1 -ProjectName "your-project" -ProjectVersion "1.0.0"
```

## 3. Tool commands (installed in this machine)
- `C:\Users\amani\tools\bin\sonar-scanner.cmd`
- `C:\Users\amani\tools\bin\blackduck-detect.cmd`
