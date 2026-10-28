#!/usr/bin/env bash
# Prueba el webhook de notificaciones de FutData (HU-036).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-2fa.sh
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
set -euo pipefail

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

echo "Webhook: $URL"

probar "Código 2FA válido" 200 -H "X-FutData-Token: $TOKEN" <<JSON
{"evento":"CODIGO_2FA","datos":{"correo":"$CORREO","nombre":"Jugador de prueba","codigo":"123456","vigenciaMinutos":10}}
JSON

probar "Sin token" 401 <<JSON
{"evento":"CODIGO_2FA","datos":{"correo":"$CORREO","nombre":"Jugador de prueba","codigo":"123456","vigenciaMinutos":10}}
JSON

probar "Evento inválido" 400 -H "X-FutData-Token: $TOKEN" <<JSON
{"evento":"EVENTO_INEXISTENTE","datos":{"correo":"$CORREO"}}
JSON

if (( fallos > 0 )); then
  echo "$fallos prueba(s) fallaron." >&2
  exit 1
fi
echo "Todas las pruebas pasaron. Revisa la bandeja de $CORREO."
