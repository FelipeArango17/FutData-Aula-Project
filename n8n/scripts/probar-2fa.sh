#!/usr/bin/env bash
# Prueba el webhook de notificaciones de FutData (HU-036).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-2fa.sh
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# Con --probar-falla (y la credencial SMTP rota a propósito, ver n8n/README.md) cambia el caso
# válido por uno que espera 502 y revisa que se agregue una línea al registro de fallas.
set -euo pipefail

PROBAR_FALLA=0
for arg in "$@"; do
  case "$arg" in
    --probar-falla) PROBAR_FALLA=1 ;;
    *) echo "Uso: bash n8n/scripts/probar-2fa.sh [--probar-falla]" >&2; exit 2 ;;
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

if [[ -z "$TOKEN" || -z "$CORREO" ]]; then
  echo "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $ENV_FILE." >&2
  exit 1
fi

fallos=0

# Envía un POST y compara el código HTTP. El cuerpo llega por stdin como bytes UTF-8.
probar() {
  local nombre="$1" esperado="$2"; shift 2
  local salida codigo
  salida="$(curl -sS -m 15 -w $'\n%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' "$@" --data-binary @-)"
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

# Revisa que la última petición haya agregado líneas al log y que no tengan datos sensibles.
revisar_log() {
  local antes="$1" nuevas ultima problema=""
  nuevas="$(leer_log | tail -n +$((antes + 1)))"
  ultima="$(tail -n 1 <<<"$nuevas")"
  if [[ -z "$nuevas" ]]; then
    problema="no se agregó ninguna línea a $LOG_FALLAS"
  elif grep -qF -e 123456 -e "$CORREO" -e "$TOKEN" <<<"$nuevas"; then
    problema="la línea nueva tiene el código, el correo completo o el token"
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

echo "Webhook: $URL"

VALIDO="{\"evento\":\"CODIGO_2FA\",\"datos\":{\"correo\":\"$CORREO\",\"nombre\":\"Jugador de prueba\",\"codigo\":\"123456\",\"vigenciaMinutos\":10}}"

if (( ! PROBAR_FALLA )); then
  probar "Código 2FA válido" 200 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
fi

probar "Sin token" 401 <<<"$VALIDO"

probar "Evento inválido" 400 -H "X-FutData-Token: $TOKEN" <<JSON
{"evento":"EVENTO_INEXISTENTE","datos":{"correo":"$CORREO"}}
JSON

if (( PROBAR_FALLA )); then
  antes="$(leer_log | wc -l)"
  probar "Falla de envío SMTP" 502 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
  revisar_log "$antes"
fi

if (( fallos > 0 )); then
  echo "$fallos prueba(s) fallaron." >&2
  exit 1
fi
if (( PROBAR_FALLA )); then
  echo 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
else
  echo "Todas las pruebas pasaron. Revisa la bandeja de $CORREO."
fi
