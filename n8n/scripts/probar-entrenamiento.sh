#!/usr/bin/env bash
# Prueba el evento CAMBIO_ENTRENAMIENTO del webhook de notificaciones de FutData (HU-026).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-entrenamiento.sh
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# Con --probar-falla (y la credencial SMTP rota a propósito, ver n8n/README.md) cambia los casos
# que envían correos por uno que espera 202 y revisa que las fallas lleguen al registro.
set -euo pipefail

PROBAR_FALLA=0
for arg in "$@"; do
  case "$arg" in
    --probar-falla) PROBAR_FALLA=1 ;;
    *) echo "Uso: bash n8n/scripts/probar-entrenamiento.sh [--probar-falla]" >&2; exit 2 ;;
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

# Envía un POST y compara el código HTTP. El cuerpo llega por stdin como bytes UTF-8
# (con <<<, no con una tubería: así probar corre en este shell y cuenta los fallos).
# Si DESTINATARIOS está definido, revisa también ese campo de la respuesta 202
# (correos que n8n va a enviar, sin duplicados ni inválidos).
probar() {
  local nombre="$1" esperado="$2"; shift 2
  local salida codigo cuerpo problema=""
  if ! salida="$(curl -sS -m 15 -w $'\n%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' "$@" --data-binary @-)"; then
    # Sin respuesta HTTP: n8n apagado o la petición superó los 15 s.
    echo "FALLA $nombre -> sin respuesta en 15 s. ¿Está n8n encendido? (docker compose ps)"
    fallos=$((fallos + 1))
    return
  fi
  codigo="${salida##*$'\n'}"
  cuerpo="${salida%$'\n'*}"
  if [[ "$codigo" != "$esperado" ]]; then
    problema="esperaba $esperado, recibió $codigo"
  elif [[ -n "${DESTINATARIOS:-}" ]] && ! grep -q "\"destinatarios\":$DESTINATARIOS[,}]" <<<"$cuerpo"; then
    problema="esperaba destinatarios = $DESTINATARIOS"
  fi
  if [[ -n "$problema" ]]; then
    echo "FALLA $nombre -> $problema $cuerpo"
    fallos=$((fallos + 1))
  else
    echo "OK    $nombre -> $codigo $cuerpo"
  fi
}

leer_log() {
  if [[ -f "$LOG_FALLAS" ]]; then grep -v '^[[:space:]]*$' "$LOG_FALLAS" || true; fi
}

# Espera hasta 150 s a que aparezcan $2 líneas CAMBIO_ENTRENAMIENTO nuevas en el log y revisa
# que no tengan datos sensibles. El envío sigue después del 202 y cada rechazo del SMTP tarda
# ~21 s con Docker Desktop, así que hay que darle tiempo. Los demás argumentos son textos prohibidos.
revisar_log() {
  local antes="$1" esperadas="$2"; shift 2
  local nuevas="" cantidad=0 problema="" limite=$((SECONDS + 150)) prohibido campo
  while (( cantidad < esperadas && SECONDS < limite )); do
    sleep 3
    nuevas="$(leer_log | tail -n +$((antes + 1)) | grep '"evento":"CAMBIO_ENTRENAMIENTO"' || true)"
    cantidad="$(grep -c . <<<"$nuevas" || true)"
  done
  if (( cantidad < esperadas )); then
    problema="en 150 s llegaron $cantidad de $esperadas líneas CAMBIO_ENTRENAMIENTO a $LOG_FALLAS"
  else
    for prohibido in "$@"; do
      grep -qF -e "$prohibido" <<<"$nuevas" && problema="una línea nueva tiene un correo completo o el token"
    done
    for campo in fecha evento correoEnmascarado error idEjecucion; do
      while IFS= read -r linea; do
        grep -q "\"$campo\":" <<<"$linea" || problema="falta el campo $campo"
      done <<<"$nuevas"
    done
  fi
  if [[ -n "$problema" ]]; then
    echo "FALLA Registro de las fallas -> $problema"
    fallos=$((fallos + 1))
  else
    echo "OK    Registro de las fallas -> $cantidad líneas"
    sed 's/^/      /' <<<"$nuevas"
  fi
}

ENTRENAMIENTO='{"categoria":"Sub-12","fecha":"2026-11-04","horaInicio":"16:00","lugar":"Cancha 1","entrenador":"Carlos Pérez"}'

# Imprime un CAMBIO_ENTRENAMIENTO. $1 = tipoCambio, $2 = arreglo JSON de destinatarios,
# $3 = campos extra de datos (JSON sin llaves, opcional), $4 = entrenamiento (opcional).
cuerpo() {
  printf '{"evento":"CAMBIO_ENTRENAMIENTO","datos":{"tipoCambio":"%s","entrenamiento":%s,"destinatarios":%s%s}}' \
    "$1" "${4:-$ENTRENAMIENTO}" "$2" "${3:+,$3}"
}

echo "Webhook: $URL"

PRUEBA="[{\"correo\":\"$CORREO\",\"nombre\":\"Jugador de prueba\"}]"

if (( ! PROBAR_FALLA )); then
  DESTINATARIOS=1 probar "CREADO" 202 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo CREADO "$PRUEBA")"
  MODIFICADO="$(cuerpo MODIFICADO "$PRUEBA" \
    '"anterior":{"fecha":"2026-11-04","horaInicio":"16:00","lugar":"Cancha 1","entrenador":"Carlos Pérez"},"motivo":"La cancha 1 está en mantenimiento"' \
    '{"categoria":"Sub-12","fecha":"2026-11-05","horaInicio":"17:30","lugar":"Cancha 2 & Gimnasio","entrenador":"Carlos Pérez"}')"
  DESTINATARIOS=1 probar "MODIFICADO" 202 -H "X-FutData-Token: $TOKEN" <<<"$MODIFICADO"
  DESTINATARIOS=1 probar "CANCELADO" 202 -H "X-FutData-Token: $TOKEN" \
    <<<"$(cuerpo CANCELADO "$PRUEBA" '"motivo":"Lluvia <fuerte> & cancha inundada"')"
  # Mismo correo en mayúsculas y uno inválido: n8n debe enviar un solo correo.
  DUPLICADOS="[{\"correo\":\"$CORREO\",\"nombre\":\"Jugador de prueba\"},{\"correo\":\"${CORREO^^}\",\"nombre\":\"Duplicado\"},{\"correo\":\"no-es-un-correo\",\"nombre\":\"Inválido\"}]"
  DESTINATARIOS=1 probar "Destinatarios duplicados" 202 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo CREADO "$DUPLICADOS")"
fi

probar "Lista de destinatarios vacía" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo CREADO '[]')"
probar "tipoCambio inválido" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo REPROGRAMADO "$PRUEBA")"
probar "Sin token" 401 <<<"$(cuerpo CREADO "$PRUEBA")"
# Dominio .invalid (reservado, nunca existe) por si la validación fallara.
MUCHOS="[$(for i in $(seq 1 201); do printf '{"correo":"prueba-%d@ejemplo.invalid","nombre":"Prueba %d"},' "$i" "$i"; done)"
MUCHOS="${MUCHOS%,}]"
probar "Más de 200 destinatarios" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo CREADO "$MUCHOS")"

if (( PROBAR_FALLA )); then
  # Dos destinatarios: comprueba que la falla del primero no frena el intento con el segundo.
  ALIAS="${CORREO/@/+falla@}"
  DOS="[{\"correo\":\"$CORREO\",\"nombre\":\"Jugador de prueba\"},{\"correo\":\"$ALIAS\",\"nombre\":\"Acudiente de prueba\"}]"
  antes="$(leer_log | wc -l)"
  DESTINATARIOS=2 probar "Falla de envío SMTP (responde 202 igual)" 202 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo CREADO "$DOS")"
  revisar_log "$antes" 2 "$CORREO" "$ALIAS" "$TOKEN"
fi

if (( fallos > 0 )); then
  echo "$fallos prueba(s) fallaron." >&2
  exit 1
fi
if (( PROBAR_FALLA )); then
  echo 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
else
  echo "Todas las pruebas pasaron. Revisa la bandeja de $CORREO: deben llegar 4 correos (CREADO, MODIFICADO, CANCELADO y uno de la prueba de duplicados)."
fi
