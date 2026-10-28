# Prueba el webhook de notificaciones de FutData (HU-036).
# Uso (desde cualquier carpeta):  pwsh -File n8n/scripts/probar-2fa.ps1
#                          o:     powershell -ExecutionPolicy Bypass -File n8n/scripts/probar-2fa.ps1
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (-not (Test-Path $envFile)) {
    Write-Error "No existe $envFile. Copia .env.example como .env y completa los valores."
}

$vars = @{}
foreach ($linea in Get-Content $envFile -Encoding UTF8) {
    if ($linea -match '^\s*([A-Z0-9_]+)=(.*)$') {
        $vars[$Matches[1]] = $Matches[2].Trim().Trim('"')
    }
}

$baseUrl = if ($vars['N8N_BASE_URL']) { $vars['N8N_BASE_URL'] } else { 'http://localhost:5678' }
$token = $vars['N8N_WEBHOOK_TOKEN']
$correo = $vars['CORREO_PRUEBA']
$url = "$baseUrl/webhook/futdata/notificaciones"

if (-not $token -or -not $correo) {
    Write-Error "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $envFile."
}

$script:fallos = 0

function Probar([string]$nombre, [int]$esperado, [hashtable]$cuerpo, [hashtable]$cabeceras = @{}) {
    # Se envían bytes UTF-8 para que Windows PowerShell 5.1 no cambie las tildes.
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 5 -Compress))
    $params = @{
        Uri         = $url
        Method      = 'Post'
        Headers     = $cabeceras
        ContentType = 'application/json; charset=utf-8'
        Body        = $bytes
        TimeoutSec  = 15
    }
    try {
        $respuesta = Invoke-RestMethod @params
        $codigo = 200
        $detalle = $respuesta | ConvertTo-Json -Compress
    } catch {
        if (-not $_.Exception.Response) { throw }
        $codigo = [int]$_.Exception.Response.StatusCode
        $detalle = $_.ErrorDetails.Message
    }
    if ($codigo -eq $esperado) {
        Write-Host "OK    $nombre -> $codigo $detalle" -ForegroundColor Green
    } else {
        Write-Host "FALLA $nombre -> esperaba $esperado, recibió $codigo $detalle" -ForegroundColor Red
        $script:fallos++
    }
}

Write-Host "Webhook: $url"

$valido = @{
    evento = 'CODIGO_2FA'
    datos  = @{ correo = $correo; nombre = 'Jugador de prueba'; codigo = '123456'; vigenciaMinutos = 10 }
}

Probar 'Código 2FA válido' 200 $valido @{ 'X-FutData-Token' = $token }
Probar 'Sin token' 401 $valido
Probar 'Evento inválido' 400 @{ evento = 'EVENTO_INEXISTENTE'; datos = @{ correo = $correo } } @{ 'X-FutData-Token' = $token }

if ($script:fallos -gt 0) {
    Write-Host "$($script:fallos) prueba(s) fallaron." -ForegroundColor Red
    exit 1
}
Write-Host "Todas las pruebas pasaron. Revisa la bandeja de $correo."
