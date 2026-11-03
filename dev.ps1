$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path -LiteralPath 'deploy/.env')) { throw 'Copy deploy/.env.example to deploy/.env and set random independent secrets first.' }
docker compose --env-file deploy/.env -f deploy/compose.yml up --build -d @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
