$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $project 'frontend')
if (-not $env:VITE_DEV_API_TARGET) {
    try {
        Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2 | Out-Null
        $env:VITE_DEV_API_TARGET = 'http://127.0.0.1:8080'
    } catch {
        if (Get-Command wsl.exe -ErrorAction SilentlyContinue) {
            $backendAddress = ((& wsl.exe --exec hostname -I) -split '\s+')[0]
            if ($backendAddress -match '^\d+\.\d+\.\d+\.\d+$') {
                $candidate = "http://${backendAddress}:8080"
                try {
                    Invoke-RestMethod "$candidate/actuator/health" -TimeoutSec 2 | Out-Null
                    $env:VITE_DEV_API_TARGET = $candidate
                } catch { Write-Warning 'Backend is not reachable; set VITE_DEV_API_TARGET if needed.' }
            }
        }
    }
}
if (-not (Test-Path 'node_modules')) {
    npm install
}
npm run dev
