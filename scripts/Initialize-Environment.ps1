$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $environmentFile) {
    Write-Output '.env already exists; preserving existing values.'
    exit 0
}
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
)
[IO.File]::WriteAllLines($environmentFile, $settings, [Text.UTF8Encoding]::new($false))
Write-Output 'Created local .env with random secrets. Values were not printed.'
