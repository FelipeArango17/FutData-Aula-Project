#!/usr/bin/env bash
# Prueba el evento DOCUMENTO_RECHAZADO del webhook de notificaciones de FutData (regla 8, bloque 3).
# Uso (desde cualquier carpeta):  bash n8n/scripts/probar-documento.sh [--probar-falla]
# Lee N8N_BASE_URL, N8N_WEBHOOK_TOKEN y CORREO_PRUEBA de n8n/.env.
# DOCUMENTO_RECHAZADO es asíncrono: n8n valida, responde 202 y después envía el correo (uno por acudiente).
# El enlace es FALSO (https://example.test/...): el correo llega, pero el botón no lleva a ningún lado.
# Con --probar-falla (y la credencial SMTP rota a propósito, ver n8n/README.md) cambia los casos que envían
# correos por uno que espera 202 y revisa que la falla llegue al registro sin el correo completo, los nombres,
# el comentario ni el enlace.
set -euo pipefail

PROBAR_FALLA=0
for arg in "$@"; do
  case "$arg" in
    --probar-falla) PROBAR_FALLA=1 ;;
    *) echo "Uso: bash n8n/scripts/probar-documento.sh [--probar-falla]" >&2; exit 2 ;;
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
ENLACE='https://example.test/acudiente/documentos'

if [[ -z "$TOKEN" || -z "$CORREO" ]]; then
  echo "Faltan N8N_WEBHOOK_TOKEN o CORREO_PRUEBA en $ENV_FILE." >&2
  exit 1
fi

# Textos que nunca deben salir en una respuesta 400 ni en el registro de fallas.
SECRETOS=("$CORREO" "Mateo Ríos" "Motivo-secreto" "example.test")

fallos=0

# Envía un POST y compara el código HTTP (el esperado puede ser varios separados por |, por ejemplo 401|403).
# El cuerpo llega por stdin como bytes UTF-8
# (con <<<, no con una tubería: así probar corre en este shell y cuenta los fallos).
# En una respuesta 400 revisa además que el detalle no traiga ninguno de los SECRETOS.
probar() {
  local nombre="$1" esperado="$2"; shift 2
  local salida codigo cuerpo problema="" secreto
  if ! salida="$(curl -sS -m 15 -w $'\n%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' "$@" --data-binary @-)"; then
    # Sin respuesta HTTP: n8n apagado o la petición superó los 15 s.
    echo "FALLA $nombre -> sin respuesta en 15 s. ¿Está n8n encendido? (docker compose ps)"
    fallos=$((fallos + 1))
    return
  fi
  codigo="${salida##*$'\n'}"
  cuerpo="${salida%$'\n'*}"
  if [[ "|$esperado|" != *"|$codigo|"* ]]; then
    problema="esperaba $esperado, recibió $codigo"
  elif [[ "$codigo" == "400" ]]; then
    for secreto in "${SECRETOS[@]}"; do
      if grep -qF -e "$secreto" <<<"$cuerpo"; then problema="la respuesta 400 trae datos personales"; fi
    done
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

# Espera hasta 150 s a que aparezcan $2 líneas DOCUMENTO_RECHAZADO nuevas en el log y revisa que no tengan
# datos sensibles. El envío sigue después del 202 y cada rechazo del SMTP tarda ~21 s con Docker Desktop, así
# que hay que darle tiempo. Los demás argumentos son textos prohibidos.
revisar_log() {
  local antes="$1" esperadas="$2"; shift 2
  local nuevas="" cantidad=0 problema="" limite=$((SECONDS + 150)) prohibido campo
  while (( cantidad < esperadas && SECONDS < limite )); do
    sleep 3
    nuevas="$(leer_log | tail -n +$((antes + 1)) | grep '"evento":"DOCUMENTO_RECHAZADO"' || true)"
    cantidad="$(grep -c . <<<"$nuevas" || true)"
  done
  if (( cantidad < esperadas )); then
    problema="en 150 s llegaron $cantidad de $esperadas líneas DOCUMENTO_RECHAZADO a $LOG_FALLAS"
  else
    for prohibido in "$@"; do
      if grep -qF -e "$prohibido" <<<"$nuevas"; then problema="una línea nueva tiene un dato que no debe registrarse"; fi
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

# Imprime un DOCUMENTO_RECHAZADO. Cada argumento opcional reemplaza un campo con su valor JSON ya escrito
# (con comillas): correo=, acudiente=, deportista=, documento=, comentario= o enlace=.
cuerpo() {
  local correo="\"$CORREO\"" acudiente='"Ana Mora"' deportista='"Mateo Ríos"' documento='"Documento de identidad"'
  local comentario='"Motivo-secreto: la imagen está borrosa."' enlace="\"$ENLACE\"" arg
  for arg in "$@"; do
    case "$arg" in
      correo=*) correo="${arg#correo=}" ;;
      acudiente=*) acudiente="${arg#acudiente=}" ;;
      deportista=*) deportista="${arg#deportista=}" ;;
      documento=*) documento="${arg#documento=}" ;;
      comentario=*) comentario="${arg#comentario=}" ;;
      enlace=*) enlace="${arg#enlace=}" ;;
    esac
  done
  printf '{"evento":"DOCUMENTO_RECHAZADO","datos":{"correo":%s,"nombreAcudiente":%s,"nombreDeportista":%s,"documento":%s,"comentario":%s,"enlaceDocumentos":%s}}' \
    "$correo" "$acudiente" "$deportista" "$documento" "$comentario" "$enlace"
}

# Una cadena de $1 caracteres "a" (entre comillas, lista para el JSON).
repetir() { printf '"%s"' "$(printf 'a%.0s' $(seq 1 "$1"))"; }

echo "Webhook: $URL"

VALIDO="$(cuerpo)"

if (( ! PROBAR_FALLA )); then
  # Varias líneas, tabulador, HTML y & en el comentario: el correo debe conservar los saltos de línea y mostrar
  # las marcas como texto, no como HTML.
  probar "Rechazo de Mateo Ríos (comentario de varias líneas con HTML)" 202 -H "X-FutData-Token: $TOKEN" \
    <<<"$(cuerpo 'comentario="Motivo-secreto: la imagen está borrosa.\r\nVuelve a subirla con mejor luz.\n\tGracias <b>&</b>"')"
  # Nombres neutros que manda el backend cuando un nombre llega vacío.
  probar "Nombres neutros (Acudiente y tu deportista)" 202 -H "X-FutData-Token: $TOKEN" \
    <<<"$(cuerpo 'acudiente="Acudiente"' 'deportista="tu deportista"' 'documento="Habeas Data"')"
  probar "Comentario de exactamente 300 caracteres" 202 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo "comentario=$(repetir 300)")"
fi

# Sin token: 401 en el flujo local y 403 en Cloud (Header Auth en el Webhook responde antes de ejecutar nodos).
probar "Sin token" "401|403" <<<"$VALIDO"
probar "Evento inválido" 400 -H "X-FutData-Token: $TOKEN" <<<"${VALIDO/DOCUMENTO_RECHAZADO/EVENTO_INEXISTENTE}"
# No se prueba "datos" que no sea un objeto: el nodo previo Validar estructura (común a todos los eventos) responde 500 en ese caso.
probar "Sin nombreAcudiente ni los demás campos" 400 -H "X-FutData-Token: $TOKEN" \
  <<<"$(printf '{"evento":"DOCUMENTO_RECHAZADO","datos":{"correo":"%s"}}' "$CORREO")"
probar "Correo inválido" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'correo="sin-arroba"')"
probar "nombreAcudiente vacío" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'acudiente=""')"
probar "nombreAcudiente con salto de línea" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'acudiente="Ana\nMora"')"
probar "nombreDeportista de 121 caracteres" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo "deportista=$(repetir 121)")"
probar "documento vacío" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'documento=""')"
probar "documento con salto de línea" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'documento="Paz\ny salvo"')"
probar "comentario vacío" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'comentario=""')"
probar "comentario solo con espacios y saltos de línea" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'comentario="  \n\t  "')"
probar "comentario de 301 caracteres" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo "comentario=$(repetir 301)")"
probar "comentario con un carácter de control (U+0001)" 400 -H "X-FutData-Token: $TOKEN" \
  <<<"$(cuerpo 'comentario="Motivo-secreto \u0001 mal"')"
probar "comentario que no es texto" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'comentario=123')"
probar "enlaceDocumentos sin http(s)" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'enlace="ftp://example.test/acudiente/documentos"')"
probar "enlaceDocumentos con espacios" 400 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo 'enlace="https://example.test/acudiente documentos"')"

if (( PROBAR_FALLA )); then
  # Dos acudientes (dos peticiones, una por acudiente): comprueba que la falla de una no impide la otra.
  ALIAS="${CORREO/@/+falla@}"
  antes="$(leer_log | wc -l)"
  probar "Falla de envío SMTP, acudiente 1 (responde 202 igual)" 202 -H "X-FutData-Token: $TOKEN" <<<"$VALIDO"
  probar "Falla de envío SMTP, acudiente 2 (responde 202 igual)" 202 -H "X-FutData-Token: $TOKEN" <<<"$(cuerpo "correo=\"$ALIAS\"")"
  revisar_log "$antes" 2 "$CORREO" "$ALIAS" "$TOKEN" "Mateo" "Motivo-secreto" "example.test"
fi

if (( fallos > 0 )); then
  echo "$fallos prueba(s) fallaron." >&2
  exit 1
fi
if (( PROBAR_FALLA )); then
  echo 'Todas las pruebas pasaron. Restaura el puerto de la credencial "SMTP FutData".'
else
  echo "Todas las pruebas pasaron. Revisa la bandeja de $CORREO: deben llegar 3 correos (comentario de varias líneas, nombres neutros y comentario de 300 caracteres). El enlace es falso: no lo abras esperando que funcione."
fi
