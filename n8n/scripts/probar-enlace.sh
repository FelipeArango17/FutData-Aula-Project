#!/usr/bin/env bash
# Prueba el evento ENLACE_CONTRASENA del webhook de notificaciones de FutData (alta de entrenador, regla 9).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-enlace.sh [--probar-falla]
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# ENLACE_CONTRASENA es síncrono: n8n envía el correo y después responde 200 (o 502 si el SMTP falla).
# El enlace es FALSO (https://example.test/...?token=PRUEBA): el correo llega, pero el botón no lleva a ningún lado.
# Con --probar-falla (y la credencial SMTP rota a propósito, ver n8n/README.md) el caso válido espera 502 y revisa
# que la línea del registro de fallas no tenga el enlace, "token=" ni el correo completo.
set -euo pipefail

PROBAR_FALLA=0
for arg in "$@"; do
  case "$arg" in
    --probar-falla) PROBAR_FALLA=1 ;;
    *) echo "Uso: bash n8n/scripts/probar-enlace.sh [--probar-falla]" >&2; exit 2 ;;
  esac
done

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$DIR/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "No existe $ENV_FILE. Copia .env.example como .env y completa los valores." >&2
  exit 1
fi

leer_var() {
  # Toma la última definición de la variable, sin comillas ni retorno de carro.
  grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//' -e 's/"$//'
}

BASE_URL="$(leer_var N8N_BASE_URL)"
TOKEN="$(leer_var N8N_WEBHOOK_TOKEN)"
CORREO="$(leer_var CORREO_PRUEBA)"
URL="${BASE_URL:-http://localhost:5678}/webhook/futdata/notificaciones"
LOG_FALLAS="$DIR/logs/fallas-notificaciones.jsonl"
ENLACE='https://example.test/crear-contrasena?token=PRUEBA'

if [[ -z "$TOKEN" || -z "$CORREO" ]]; then
  echo "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $ENV_FILE." >&2
  exit 1
fi

fallos=0

# Envía un POST y compara el código HTTP. El cuerpo llega por stdin como bytes UTF-8.
# Espera hasta TIEMPO_MAX segundos (30 por defecto: el envío es síncrono).
probar() {
  local nombre="$1" esperado="$2"; shift 2
  local salida codigo tiempo="${TIEMPO_MAX:-30}"
  if ! salida="$(curl -sS -m "$tiempo" -w $'\n%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' "$@" --data-binary @-)"; then
    echo "FALLA $nombre -> sin respuesta en $tiempo s. ¿Está n8n encendido? (docker compose ps)"
    fallos=$((fallos + 1))
    return
  fi
  codigo="${salida##*$'\n'}"
  if [[ "$codigo" == "$esperado" ]]; then
    echo "OK    $nombre -> $codigo ${salida%$'\n'*}"
  else
    echo "FALLA $nombre -> esperaba $esperado, recibió $codigo ${salida%$'\n'*}"
    fallos=$((fallos + 1))
  fi
}

leer_log() {
  if [[ -f "$LOG_FALLAS" ]]; then grep -v '^[[:space:]]*$' "$LOG_FALLAS" || true; fi
}

# El registro se escribe antes del 502, así que la línea ya debe estar; se espera unos segundos por si el disco tarda.
revisar_log() {
  local antes="$1" nuevas="" ultima problema="" limite=$((SECONDS + 15)) campo
  while [[ -z "$nuevas" ]] && (( SECONDS < limite )); do
    nuevas="$(leer_log | tail -n +$((antes + 1)) | grep '"evento":"ENLACE_CONTRASENA"' || true)"
    if [[ -z "$nuevas" ]]; then sleep 1; fi
  done
  ultima="$(tail -n 1 <<<"$nuevas")"
  if [[ -z "$nuevas" ]]; then
    problema="no llegó ninguna línea ENLACE_CONTRASENA a $LOG_FALLAS"
  elif grep -qF -e "$ENLACE" -e example.test -e 'token=' -e PRUEBA <<<"$nuevas"; then
    problema='la línea nueva tiene el enlace o "token="'
  elif grep -qF -e "$CORREO" -e "$TOKEN" <<<"$nuevas"; then
    problema="la línea nueva tiene el correo completo o el token del webhook"
  else
    for campo in fecha evento correoEnmascarado error idEjecucion; do
      grep -q "\"$campo\":" <<<"$ultima" || problema="falta el campo $campo"
    done
  fi
  if [[ -n "$problema" ]]; then
    echo "FALLA Registro de la falla -> $problema"
    fallos=$((fallos + 1))
  else
    echo "OK    Registro de la falla -> $ultima"
  fi
}

# Datos del evento con un campo cambiado: datos_con '"correo":"x"' reemplaza ese campo.
datos_con() {
  local plantilla='"BIENVENIDA_ENTRENADOR"' correo="\"$CORREO\"" nombre='"Entrenador de prueba"' enlace="\"$ENLACE\"" vigencia=1
  case "${1:-}" in
    plantilla=*) plantilla="${1#plantilla=}" ;;
    correo=*) correo="${1#correo=}" ;;
    nombre=*) nombre="${1#nombre=}" ;;
    enlace=*) enlace="${1#enlace=}" ;;
    vigencia=*) vigencia="${1#vigencia=}" ;;
  esac
  printf '{"evento":"ENLACE_CONTRASENA","datos":{"plantilla":%s,"correo":%s,"nombre":%s,"enlace":%s,"vigenciaHoras":%s}}' \
    "$plantilla" "$correo" "$nombre" "$enlace" "$vigencia"
}

echo "Webhook: $URL"

VALIDO="$(datos_con)"

if (( ! PROBAR_FALLA )); then
  probar "Enlace válido (envía el correo)" 200 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
fi

probar "Sin token" 401 <<<"$VALIDO"

probar "Evento inválido" 400 -H "X-FutData-Token: $TOKEN" <<<"${VALIDO/ENLACE_CONTRASENA/EVENTO_INEXISTENTE}"

probar "Datos inválidos: plantilla no permitida" 400 -H "X-FutData-Token: $TOKEN" <<<"$(datos_con 'plantilla="OTRA"')"
probar "Datos inválidos: correo inválido" 400 -H "X-FutData-Token: $TOKEN" <<<"$(datos_con 'correo="sin-arroba"')"
probar "Datos inválidos: sin nombre" 400 -H "X-FutData-Token: $TOKEN" <<<"$(datos_con 'nombre=""')"
probar "Datos inválidos: enlace sin http(s)" 400 -H "X-FutData-Token: $TOKEN" \
  <<<"$(datos_con 'enlace="ftp://example.test/crear-contrasena?token=PRUEBA"')"
probar "Datos inválidos: vigenciaHoras no numérico" 400 -H "X-FutData-Token: $TOKEN" <<<"$(datos_con 'vigencia="una"')"
probar "Datos inválidos: vigenciaHoras 0" 400 -H "X-FutData-Token: $TOKEN" <<<"$(datos_con 'vigencia=0')"

if (( PROBAR_FALLA )); then
  antes="$(leer_log | wc -l)"
  # Síncrono: n8n intenta el envío, registra la falla y responde 502. El rechazo del SMTP puede tardar ~20 s
  # con Docker Desktop (hasta 2 min si el host no responde).
  TIEMPO_MAX=150 probar "Falla de envío SMTP" 502 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
  revisar_log "$antes"
fi

if (( fallos > 0 )); then
  echo "$fallos prueba(s) fallaron." >&2
  exit 1
fi
if (( PROBAR_FALLA )); then
  echo 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
else
  echo "Todas las pruebas pasaron. Revisa la bandeja de $CORREO (el enlace es falso: no lo abras esperando que funcione)."
fi
