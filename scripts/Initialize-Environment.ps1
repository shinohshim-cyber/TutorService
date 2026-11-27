$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env'
function New-LocalSecret {
    $secretBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
    return [Convert]::ToHexString($secretBytes).ToLowerInvariant()
}
$settings = @(
    'MIGRATION_DB_USER=tutor_migrator'
    ('MIGRATION_DB_PASSWORD=' + (New-LocalSecret))
    'APP_DB_USER=tutor_app'
    ('APP_DB_PASSWORD=' + (New-LocalSecret))
    'AI_DB_USER=tutor_ai'
    ('AI_DB_PASSWORD=' + (New-LocalSecret))
    ('REDIS_PASSWORD=' + (New-LocalSecret))
    ('CHECKPOINT_REDIS_PASSWORD=' + (New-LocalSecret))
    ('NEO4J_PASSWORD=' + (New-LocalSecret))
    ('AUTH_JWT_KEY=' + (New-LocalSecret))
    ('AUTH_REFRESH_HMAC_KEY=' + (New-LocalSecret))
)
if (Test-Path -LiteralPath $environmentFile) {
    $existing = [IO.File]::ReadAllLines($environmentFile)
    $keys = @($existing | Where-Object { $_ -match '^[A-Z_][A-Z0-9_]*=' } | ForEach-Object { ($_ -split '=',2)[0] })
    $missing = @($settings | Where-Object { ($_ -split '=',2)[0] -notin $keys })
    if ($missing.Count -eq 0) { Write-Output '.env already complete; preserving all values.'; exit 0 }
    [IO.File]::WriteAllLines($environmentFile, @($existing) + $missing, [Text.UTF8Encoding]::new($false))
    Write-Output 'Added missing local settings; preserved all existing values. Secrets were not printed.'
} else {
    [IO.File]::WriteAllLines($environmentFile, $settings, [Text.UTF8Encoding]::new($false))
    Write-Output 'Created local .env with random secrets. Values were not printed.'
}
