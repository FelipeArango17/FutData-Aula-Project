# Prueba el evento DOCUMENTO_RECHAZADO del webhook de notificaciones de FutData (regla 8, bloque 3).
# Uso (desde cualquier carpeta):  pwsh -File n8n/scripts/probar-documento.ps1
#                          o:     powershell -ExecutionPolicy Bypass -File n8n/scripts/probar-documento.ps1
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# DOCUMENTO_RECHAZADO es asíncrono: n8n valida, responde 202 y después envía el correo (uno por acudiente).
# El enlace es FALSO (https://example.test/...): el correo llega, pero el botón no lleva a ningún lado.
# Con -ProbarFalla (y la credencial SMTP rota a propósito, ver n8n/README.md) cambia los casos que envían
# correos por uno que espera 202 y revisa que la falla llegue al registro sin el correo completo, los nombres,
# el comentario ni el enlace.
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
$enlace = 'https://example.test/acudiente/documentos'

if (-not $token -or -not $correo) {
    Write-Error "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $envFile."
}

# Textos que nunca deben salir en una respuesta 400 ni en el registro de fallas.
$secretos = @($correo, 'Mateo Ríos', 'Motivo-secreto', 'example.test')

$script:fallos = 0
$conToken = @{ 'X-FutData-Token' = $token }

# Envía un POST y compara el código HTTP (el esperado puede ser varios, por ejemplo @(401, 403)). En una
# respuesta 400 revisa además que el detalle no traiga ninguno de los $secretos.
function Probar([string]$nombre, [int[]]$esperado, [object]$cuerpo, [hashtable]$cabeceras = @{}) {
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
    if ($codigo -notin $esperado) {
        $problema = "esperaba $($esperado -join ' o '), recibió $codigo"
    } elseif ($codigo -eq 400 -and @($secretos | Where-Object { $detalle -and $detalle.Contains($_) }).Count -gt 0) {
        $problema = 'la respuesta 400 trae datos personales'
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

# Espera hasta $tiempoMax segundos a que aparezcan $esperadas líneas DOCUMENTO_RECHAZADO nuevas en el log y
# revisa que no tengan datos sensibles. El envío sigue después del 202 y cada rechazo del SMTP tarda ~21 s
# con Docker Desktop, así que hay que darle tiempo.
function Revisar-Log([int]$antes, [int]$esperadas, [string[]]$prohibidos, [int]$tiempoMax = 150) {
    $limite = (Get-Date).AddSeconds($tiempoMax)
    do {
        Start-Sleep -Seconds 3
        $nuevas = @(Leer-Log | Select-Object -Skip $antes | Where-Object { $_ -match '"evento":"DOCUMENTO_RECHAZADO"' })
    } while ($nuevas.Count -lt $esperadas -and (Get-Date) -lt $limite)

    $texto = $nuevas -join "`n"
    $campos = 'fecha', 'evento', 'correoEnmascarado', 'error', 'idEjecucion'
    $problema = $null
    if ($nuevas.Count -lt $esperadas) {
        $problema = "en $tiempoMax s llegaron $($nuevas.Count) de $esperadas líneas DOCUMENTO_RECHAZADO a $logFallas"
    } elseif (@($prohibidos | Where-Object { $texto.Contains($_) }).Count -gt 0) {
        $problema = 'una línea nueva tiene un dato que no debe registrarse'
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

# Arma el cuerpo de un DOCUMENTO_RECHAZADO. $extra agrega o reemplaza campos de datos.
function Cuerpo([hashtable]$extra = @{}) {
    $datos = [ordered]@{
        correo           = $correo
        nombreAcudiente  = 'Ana Mora'
        nombreDeportista = 'Mateo Ríos'
        documento        = 'Documento de identidad'
        comentario       = 'Motivo-secreto: la imagen está borrosa.'
        enlaceDocumentos = $enlace
    }
    foreach ($clave in $extra.Keys) { $datos[$clave] = $extra[$clave] }
    @{ evento = 'DOCUMENTO_RECHAZADO'; datos = $datos }
}

Write-Host "Webhook: $url"

if (-not $ProbarFalla) {
    # Varias líneas, tabulador, HTML y & en el comentario: el correo debe conservar los saltos de línea y mostrar
    # las marcas como texto, no como HTML.
    Probar 'Rechazo de Mateo Ríos (comentario de varias líneas con HTML)' 202 (Cuerpo @{
            comentario = "Motivo-secreto: la imagen está borrosa.`r`nVuelve a subirla con mejor luz.`n`tGracias <b>&</b>"
        }) $conToken
    # Nombres neutros que manda el backend cuando un nombre llega vacío.
    Probar 'Nombres neutros (Acudiente y tu deportista)' 202 (Cuerpo @{
            nombreAcudiente = 'Acudiente'; nombreDeportista = 'tu deportista'; documento = 'Habeas Data'
        }) $conToken
    Probar 'Comentario de exactamente 300 caracteres' 202 (Cuerpo @{ comentario = ('a' * 300) }) $conToken
}

# Sin token: 401 en el flujo local y 403 en Cloud (Header Auth en el Webhook responde antes de ejecutar nodos).
Probar 'Sin token' @(401, 403) (Cuerpo)
Probar 'Evento inválido' 400 (@{ evento = 'EVENTO_INEXISTENTE'; datos = (Cuerpo).datos }) $conToken
# No se prueba "datos" que no sea un objeto: el nodo previo Validar estructura (común a todos los eventos) responde 500 en ese caso.
Probar 'Sin nombreAcudiente ni los demás campos' 400 @{ evento = 'DOCUMENTO_RECHAZADO'; datos = @{ correo = $correo } } $conToken
Probar 'Correo inválido' 400 (Cuerpo @{ correo = 'sin-arroba' }) $conToken
Probar 'nombreAcudiente vacío' 400 (Cuerpo @{ nombreAcudiente = '' }) $conToken
Probar 'nombreAcudiente con salto de línea' 400 (Cuerpo @{ nombreAcudiente = "Ana`nMora" }) $conToken
Probar 'nombreDeportista de 121 caracteres' 400 (Cuerpo @{ nombreDeportista = ('a' * 121) }) $conToken
Probar 'documento vacío' 400 (Cuerpo @{ documento = '' }) $conToken
Probar 'documento con salto de línea' 400 (Cuerpo @{ documento = "Paz`ny salvo" }) $conToken
Probar 'comentario vacío' 400 (Cuerpo @{ comentario = '' }) $conToken
Probar 'comentario solo con espacios y saltos de línea' 400 (Cuerpo @{ comentario = "  `n`t  " }) $conToken
Probar 'comentario de 301 caracteres' 400 (Cuerpo @{ comentario = ('a' * 301) }) $conToken
Probar 'comentario con un carácter de control (U+0001)' 400 (Cuerpo @{ comentario = "Motivo-secreto $([char]1) mal" }) $conToken
Probar 'comentario que no es texto' 400 (Cuerpo @{ comentario = 123 }) $conToken
Probar 'enlaceDocumentos sin http(s)' 400 (Cuerpo @{ enlaceDocumentos = 'ftp://example.test/acudiente/documentos' }) $conToken
Probar 'enlaceDocumentos con espacios' 400 (Cuerpo @{ enlaceDocumentos = 'https://example.test/acudiente documentos' }) $conToken

if ($ProbarFalla) {
    # Dos acudientes (dos peticiones, una por acudiente): comprueba que la falla de una no impide la otra.
    $alias = $correo -replace '@', '+falla@'
    $antes = (Leer-Log).Count
    Probar 'Falla de envío SMTP, acudiente 1 (responde 202 igual)' 202 (Cuerpo) $conToken
    Probar 'Falla de envío SMTP, acudiente 2 (responde 202 igual)' 202 (Cuerpo @{ correo = $alias }) $conToken
    Revisar-Log $antes 2 @($correo, $alias, $token, 'Mateo', 'Motivo-secreto', 'example.test')
}

if ($script:fallos -gt 0) {
    Write-Host "$($script:fallos) prueba(s) fallaron." -ForegroundColor Red
    exit 1
}
if ($ProbarFalla) {
    Write-Host 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
} else {
    Write-Host "Todas las pruebas pasaron. Revisa la bandeja de ${correo}: deben llegar 3 correos (comentario de varias líneas, nombres neutros y comentario de 300 caracteres). El enlace es falso: no lo abras esperando que funcione."
}
