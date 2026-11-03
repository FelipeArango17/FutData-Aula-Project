# Prueba el evento ENLACE_CONTRASENA del webhook de notificaciones de FutData (alta de entrenador, regla 9).
# Uso (desde cualquier carpeta):  pwsh -File n8n/scripts/probar-enlace.ps1
#                          o:     powershell -ExecutionPolicy Bypass -File n8n/scripts/probar-enlace.ps1
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# ENLACE_CONTRASENA es síncrono: n8n envía el correo y después responde 200 (o 502 si el SMTP falla).
# El enlace es FALSO (https://example.test/...?token=PRUEBA): el correo llega, pero el botón no lleva a ningún lado.
# Con -ProbarFalla (y la credencial SMTP rota a propósito, ver n8n/README.md) el caso válido espera 502 y revisa
# que la línea del registro de fallas no tenga el enlace, "token=" ni el correo completo.
param(
    [switch]$ProbarFalla
)
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
$logFallas = Join-Path (Split-Path $PSScriptRoot -Parent) 'logs/fallas-notificaciones.jsonl'
$enlace = 'https://example.test/crear-contrasena?token=PRUEBA'

if (-not $token -or -not $correo) {
    Write-Error "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $envFile."
}

$script:fallos = 0

function Probar([string]$nombre, [int]$esperado, [hashtable]$cuerpo, [hashtable]$cabeceras = @{}, [int]$tiempoMax = 30) {
    # Se envían bytes UTF-8 para que Windows PowerShell 5.1 no cambie las tildes.
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 5 -Compress))
    $params = @{
        Uri             = $url
        Method          = 'Post'
        Headers         = $cabeceras
        ContentType     = 'application/json; charset=utf-8'
        Body            = $bytes
        TimeoutSec      = $tiempoMax
        UseBasicParsing = $true
    }
    try {
        $respuesta = Invoke-WebRequest @params
        $codigo = [int]$respuesta.StatusCode
        $detalle = $respuesta.Content
    } catch {
        if (-not $_.Exception.Response) {
            Write-Host "FALLA $nombre -> sin respuesta en $tiempoMax s ($($_.Exception.Message)). ¿Está n8n encendido? (docker compose ps)" -ForegroundColor Red
            $script:fallos++
            return
        }
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

function Leer-Log {
    if (Test-Path $logFallas) { @(Get-Content $logFallas -Encoding UTF8 | Where-Object { $_.Trim() }) } else { @() }
}

# El registro se escribe antes del 502, así que la línea ya debe estar; se espera unos segundos por si el disco tarda.
function Revisar-Log([int]$antes, [int]$tiempoMax = 15) {
    $limite = (Get-Date).AddSeconds($tiempoMax)
    do {
        $nuevas = @(Leer-Log | Select-Object -Skip $antes | Where-Object { $_ -match '"evento":"ENLACE_CONTRASENA"' })
        if ($nuevas.Count -eq 0) { Start-Sleep -Seconds 1 }
    } while ($nuevas.Count -eq 0 -and (Get-Date) -lt $limite)

    $texto = $nuevas -join "`n"
    $campos = 'fecha', 'evento', 'correoEnmascarado', 'error', 'idEjecucion'
    $problema = $null
    if ($nuevas.Count -eq 0) {
        $problema = "no llegó ninguna línea ENLACE_CONTRASENA a $logFallas"
    } elseif ($texto.Contains($enlace) -or $texto.Contains('example.test') -or $texto.Contains('token=') -or $texto.Contains('PRUEBA')) {
        $problema = 'la línea nueva tiene el enlace o "token="'
    } elseif ($texto.Contains($correo) -or $texto.Contains($token)) {
        $problema = 'la línea nueva tiene el correo completo o el token del webhook'
    } else {
        try {
            $registro = $nuevas[-1] | ConvertFrom-Json
            $faltan = @($campos | Where-Object { -not $registro.$_ })
            $problema = if ($faltan) { "faltan campos: $($faltan -join ', ')" } else { $null }
        } catch {
            $problema = 'la línea nueva no es JSON válido'
        }
    }
    if ($problema) {
        Write-Host "FALLA Registro de la falla -> $problema" -ForegroundColor Red
        $script:fallos++
    } else {
        Write-Host "OK    Registro de la falla -> $($nuevas[-1])" -ForegroundColor Green
    }
}

Write-Host "Webhook: $url"

$datos = @{ plantilla = 'BIENVENIDA_ENTRENADOR'; correo = $correo; nombre = 'Entrenador de prueba'; enlace = $enlace; vigenciaHoras = 1 }
$valido = @{ evento = 'ENLACE_CONTRASENA'; datos = $datos }
$conToken = @{ 'X-FutData-Token' = $token }

if (-not $ProbarFalla) {
    Probar 'Enlace válido (envía el correo)' 200 $valido $conToken
}
Probar 'Sin token' 401 $valido
Probar 'Evento inválido' 400 @{ evento = 'EVENTO_INEXISTENTE'; datos = $datos } $conToken
$malos = [ordered]@{
    'Plantilla no permitida'     = @{ plantilla = 'OTRA' }
    'Correo inválido'            = @{ correo = 'sin-arroba' }
    'Sin nombre'                 = @{ nombre = '' }
    'Enlace sin http(s)'         = @{ enlace = 'ftp://example.test/crear-contrasena?token=PRUEBA' }
    'vigenciaHoras no numérico'  = @{ vigenciaHoras = 'una' }
    'vigenciaHoras 0'            = @{ vigenciaHoras = 0 }
}
foreach ($caso in $malos.Keys) {
    $copia = $datos.Clone()
    foreach ($campo in $malos[$caso].Keys) { $copia[$campo] = $malos[$caso][$campo] }
    Probar "Datos inválidos: $caso" 400 @{ evento = 'ENLACE_CONTRASENA'; datos = $copia } $conToken
}
if ($ProbarFalla) {
    $antes = (Leer-Log).Count
    # Síncrono: n8n intenta el envío, registra la falla y responde 502. El rechazo del SMTP puede tardar ~20 s
    # con Docker Desktop (hasta 2 min si el host no responde).
    Probar 'Falla de envío SMTP' 502 $valido $conToken 150
    Revisar-Log $antes
}

if ($script:fallos -gt 0) {
    Write-Host "$($script:fallos) prueba(s) fallaron." -ForegroundColor Red
    exit 1
}
if ($ProbarFalla) {
    Write-Host 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
} else {
    Write-Host "Todas las pruebas pasaron. Revisa la bandeja de $correo (el enlace es falso: no lo abras esperando que funcione)."
}
