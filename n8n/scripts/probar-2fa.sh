#!/usr/bin/env bash
# Prueba el webhook de notificaciones de FutData (HU-036).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-2fa.sh
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# CODIGO_2FA es asíncrono: n8n valida, responde 202 y después envía el correo.
# Con --probar-falla (y la credencial SMTP rota a propósito, ver n8n/README.md) el caso válido
# sigue esperando 202 y después espera a que aparezca la línea de la falla en el registro.
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
# Espera hasta TIEMPO_MAX segundos (15 por defecto).
probar() {
  local nombre="$1" esperado="$2"; shift 2
  local salida codigo tiempo="${TIEMPO_MAX:-15}"
  if ! salida="$(curl -sS -m "$tiempo" -w $'\n%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' "$@" --data-binary @-)"; then
    # Sin respuesta HTTP: n8n apagado o la petición superó el tiempo máximo.
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

# Espera hasta 150 s a que aparezca una línea CODIGO_2FA nueva en el log y revisa que no tenga
# datos sensibles. El envío sigue después del 202 y cada rechazo del SMTP tarda ~20 s con Docker
# Desktop (hasta 2 min si el host no responde), así que hay que darle tiempo.
revisar_log() {
  local antes="$1" nuevas="" ultima problema="" limite=$((SECONDS + 150)) campo
  while [[ -z "$nuevas" ]] && (( SECONDS < limite )); do
    sleep 3
    nuevas="$(leer_log | tail -n +$((antes + 1)) | grep '"evento":"CODIGO_2FA"' || true)"
  done
  ultima="$(tail -n 1 <<<"$nuevas")"
  if [[ -z "$nuevas" ]]; then
    problema="en 150 s no llegó ninguna línea CODIGO_2FA a $LOG_FALLAS"
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
  probar "Código 2FA válido" 202 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
fi

probar "Sin token" 401 <<<"$VALIDO"

probar "Evento inválido" 400 -H "X-FutData-Token: $TOKEN" <<JSON
{"evento":"EVENTO_INEXISTENTE","datos":{"correo":"$CORREO"}}
JSON

if (( PROBAR_FALLA )); then
  antes="$(leer_log | wc -l)"
  # n8n responde 202 antes de intentar el envío; la falla solo queda en el registro.
  probar "Falla de envío SMTP (responde 202 igual)" 202 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
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
