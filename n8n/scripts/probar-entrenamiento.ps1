# Prueba el evento CAMBIO_ENTRENAMIENTO del webhook de notificaciones de FutData (HU-026).
# Uso (desde cualquier carpeta):  pwsh -File n8n/scripts/probar-entrenamiento.ps1
#                          o:     powershell -ExecutionPolicy Bypass -File n8n/scripts/probar-entrenamiento.ps1
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# Con -ProbarFalla (y la credencial SMTP rota a propósito, ver n8n/README.md) cambia los casos
# que envían correos por uno que espera 202 y revisa que las fallas lleguen al registro.
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

if (-not $token -or -not $correo) {
    Write-Error "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $envFile."
}

$script:fallos = 0
$conToken = @{ 'X-FutData-Token' = $token }

# Envía un POST y compara el código HTTP. Si se indica $destinatarios, revisa también ese campo
# de la respuesta 202 (correos que n8n va a enviar, sin duplicados ni inválidos).
function Probar([string]$nombre, [int]$esperado, [hashtable]$cuerpo, [hashtable]$cabeceras = @{}, [int]$destinatarios = -1) {
    # Se envían bytes UTF-8 para que Windows PowerShell 5.1 no cambie las tildes.
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 6 -Compress))
    $params = @{
        Uri             = $url
        Method          = 'Post'
        Headers         = $cabeceras
        ContentType     = 'application/json; charset=utf-8'
        Body            = $bytes
        TimeoutSec      = 15
        UseBasicParsing = $true
    }
    try {
        $respuesta = Invoke-WebRequest @params
        $codigo = [int]$respuesta.StatusCode
        $detalle = $respuesta.Content
    } catch {
        if (-not $_.Exception.Response) {
            # Sin respuesta HTTP: n8n apagado o la petición superó los 15 s.
            Write-Host "FALLA $nombre -> sin respuesta en 15 s ($($_.Exception.Message)). ¿Está n8n encendido? (docker compose ps)" -ForegroundColor Red
            $script:fallos++
            return
        }
        $codigo = [int]$_.Exception.Response.StatusCode
        $detalle = $_.ErrorDetails.Message
    }
    $problema = $null
    if ($codigo -ne $esperado) {
        $problema = "esperaba $esperado, recibió $codigo"
    } elseif ($destinatarios -ge 0) {
        $recibidos = try { ($detalle | ConvertFrom-Json).destinatarios } catch { $null }
        if ($recibidos -ne $destinatarios) { $problema = "esperaba destinatarios = $destinatarios, recibió $recibidos" }
    }
    if ($problema) {
        Write-Host "FALLA $nombre -> $problema $detalle" -ForegroundColor Red
        $script:fallos++
    } else {
        Write-Host "OK    $nombre -> $codigo $detalle" -ForegroundColor Green
    }
}

function Leer-Log {
    if (Test-Path $logFallas) { @(Get-Content $logFallas -Encoding UTF8 | Where-Object { $_.Trim() }) } else { @() }
}

# Espera hasta $tiempoMax segundos a que aparezcan $esperadas líneas CAMBIO_ENTRENAMIENTO nuevas
# en el log y revisa que no tengan datos sensibles. El envío sigue después del 202 y cada rechazo
# del SMTP tarda ~21 s con Docker Desktop, así que hay que darle tiempo.
function Revisar-Log([int]$antes, [int]$esperadas, [string[]]$prohibidos, [int]$tiempoMax = 150) {
    $limite = (Get-Date).AddSeconds($tiempoMax)
    do {
        Start-Sleep -Seconds 3
        $nuevas = @(Leer-Log | Select-Object -Skip $antes | Where-Object { $_ -match '"evento":"CAMBIO_ENTRENAMIENTO"' })
    } while ($nuevas.Count -lt $esperadas -and (Get-Date) -lt $limite)

    $texto = $nuevas -join "`n"
    $campos = 'fecha', 'evento', 'correoEnmascarado', 'error', 'idEjecucion'
    $problema = $null
    if ($nuevas.Count -lt $esperadas) {
        $problema = "en $tiempoMax s llegaron $($nuevas.Count) de $esperadas líneas CAMBIO_ENTRENAMIENTO a $logFallas"
    } elseif (@($prohibidos | Where-Object { $texto.Contains($_) }).Count -gt 0) {
        $problema = 'una línea nueva tiene un correo completo o el token'
    } else {
        foreach ($linea in $nuevas) {
            try {
                $registro = $linea | ConvertFrom-Json
                $faltan = @($campos | Where-Object { -not $registro.$_ })
                if ($faltan) { $problema = "faltan campos: $($faltan -join ', ')" }
            } catch {
                $problema = 'una línea nueva no es JSON válido'
            }
        }
    }
    if ($problema) {
        Write-Host "FALLA Registro de las fallas -> $problema" -ForegroundColor Red
        $script:fallos++
    } else {
        Write-Host "OK    Registro de las fallas -> $($nuevas.Count) líneas" -ForegroundColor Green
        $nuevas | ForEach-Object { Write-Host "      $_" }
    }
}

# Arma el cuerpo de un CAMBIO_ENTRENAMIENTO. $extra agrega o reemplaza campos de datos.
function Cuerpo([string]$tipoCambio, [object[]]$destinatarios, [hashtable]$extra = @{}) {
    $datos = @{
        tipoCambio    = $tipoCambio
        entrenamiento = @{ categoria = 'Sub-12'; fecha = '2026-11-04'; horaInicio = '16:00'; lugar = 'Cancha 1'; entrenador = 'Carlos Pérez' }
        destinatarios = $destinatarios
    }
    foreach ($clave in $extra.Keys) { $datos[$clave] = $extra[$clave] }
    @{ evento = 'CAMBIO_ENTRENAMIENTO'; datos = $datos }
}

Write-Host "Webhook: $url"

$prueba = @(@{ correo = $correo; nombre = 'Jugador de prueba' })

if (-not $ProbarFalla) {
    Probar 'CREADO' 202 (Cuerpo 'CREADO' $prueba) $conToken 1
    Probar 'MODIFICADO' 202 (Cuerpo 'MODIFICADO' $prueba @{
            entrenamiento = @{ categoria = 'Sub-12'; fecha = '2026-11-05'; horaInicio = '17:30'; lugar = 'Cancha 2 & Gimnasio'; entrenador = 'Carlos Pérez' }
            anterior      = @{ fecha = '2026-11-04'; horaInicio = '16:00'; lugar = 'Cancha 1'; entrenador = 'Carlos Pérez' }
            motivo        = 'La cancha 1 está en mantenimiento'
        }) $conToken 1
    Probar 'CANCELADO' 202 (Cuerpo 'CANCELADO' $prueba @{ motivo = 'Lluvia <fuerte> & cancha inundada' }) $conToken 1
    # Mismo correo en mayúsculas y uno inválido: n8n debe enviar un solo correo.
    $duplicados = @(
        @{ correo = $correo; nombre = 'Jugador de prueba' },
        @{ correo = $correo.ToUpper(); nombre = 'Duplicado' },
        @{ correo = 'no-es-un-correo'; nombre = 'Inválido' }
    )
    Probar 'Destinatarios duplicados' 202 (Cuerpo 'CREADO' $duplicados) $conToken 1
}

Probar 'Lista de destinatarios vacía' 400 (Cuerpo 'CREADO' @()) $conToken
Probar 'tipoCambio inválido' 400 (Cuerpo 'REPROGRAMADO' $prueba) $conToken
Probar 'Sin token' 401 (Cuerpo 'CREADO' $prueba)
# Dominio .invalid (reservado, nunca existe) por si la validación fallara.
$muchos = @(1..201 | ForEach-Object { @{ correo = "prueba-$_@ejemplo.invalid"; nombre = "Prueba $_" } })
Probar 'Más de 200 destinatarios' 400 (Cuerpo 'CREADO' $muchos) $conToken

if ($ProbarFalla) {
    # Dos destinatarios: comprueba que la falla del primero no frena el intento con el segundo.
    $alias = $correo -replace '@', '+falla@'
    $dos = @(@{ correo = $correo; nombre = 'Jugador de prueba' }, @{ correo = $alias; nombre = 'Acudiente de prueba' })
    $antes = (Leer-Log).Count
    Probar 'Falla de envío SMTP (responde 202 igual)' 202 (Cuerpo 'CREADO' $dos) $conToken 2
    Revisar-Log $antes 2 @($correo, $alias, $token)
}

if ($script:fallos -gt 0) {
    Write-Host "$($script:fallos) prueba(s) fallaron." -ForegroundColor Red
    exit 1
}
if ($ProbarFalla) {
    Write-Host 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
} else {
    Write-Host "Todas las pruebas pasaron. Revisa la bandeja de ${correo}: deben llegar 4 correos (CREADO, MODIFICADO, CANCELADO y uno de la prueba de duplicados)."
}
