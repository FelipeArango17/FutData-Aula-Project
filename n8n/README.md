# n8n · Notificaciones de FutData

n8n envía los correos del sistema. El backend le pide cada envío con un `POST` al webhook `futdata/notificaciones`; n8n no genera ni valida códigos, solo arma el correo y lo envía por SMTP.

Contrato completo (campos, respuestas, cómo llamarlo desde Spring Boot): [`docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md).

| Archivo | Para qué |
| :--- | :--- |
| `docker-compose.yml` | n8n 2.45.4 en Docker para desarrollo local (puerto 5678) |
| `.env.example` | Variables necesarias. Se copia como `.env` (ignorado por Git) |
| `workflows/futdata-notificaciones.json` | Flujo `FutData · Notificaciones` para importar |
| `plantillas/codigo-2fa.html` / `.txt` | Correo del código 2FA (HTML y texto plano) |
| `scripts/probar-2fa.sh` / `.ps1` | Prueba del webhook: caso válido (200), sin token (401) y evento inválido (400) |

## Flujo

```text
Webhook POST ─► Validar token ─► Validar estructura ─► Evento (Switch) ─► Validar datos CODIGO_2FA ─► Enviar correo 2FA ─► Responder 200
                    │ no               │ no                 │ otro evento          │ no                      │ error SMTP
                    ▼                  ▼                    ▼                      ▼                         ▼
              Responder 401   Responder 400 estructura  Responder 400 evento  Responder 400 datos       Responder 502
```

El token se valida en el nodo `Validar token` comparando la cabecera `X-FutData-Token` con la variable `N8N_WEBHOOK_TOKEN` del `.env`. No se usa la opción *Header Auth* del Webhook porque esa opción responde `403` y el contrato pide `401`.

## Puesta en marcha

Requisitos: Docker Desktop encendido. Todos los comandos se ejecutan dentro de la carpeta `n8n/`.

### 1. Crear el `.env`

```bash
cp .env.example .env            # PowerShell: Copy-Item .env.example .env
```

Completa en `.env`:

- `N8N_ENCRYPTION_KEY` y `N8N_WEBHOOK_TOKEN`: dos valores aleatorios distintos (`openssl rand -hex 32`; en `.env.example` está el equivalente para PowerShell). Guarda la llave de cifrado: si se pierde, hay que volver a crear las credenciales.
- `SMTP_FROM`: el remitente, por ejemplo `"FutData <tu-cuenta@gmail.com>"`. Con Gmail debe ser la misma cuenta de la credencial SMTP.
- `CORREO_PRUEBA`: a dónde llega el correo de los scripts de prueba.

El backend usará el mismo `N8N_WEBHOOK_TOKEN` y `N8N_BASE_URL` en sus propias variables de entorno.

### 2. Levantar n8n

```bash
docker compose up -d
docker compose logs -f n8n      # esperar "Editor is now accessible via"; salir con Ctrl+C
```

Abre http://localhost:5678 y crea la cuenta de propietario (solo es local).

### 3. Crear la credencial "SMTP FutData" (Gmail, para desarrollo)

Gmail no acepta la contraseña normal por SMTP; se usa una **contraseña de aplicación**:

1. En la cuenta de Google que enviará los correos, activa la verificación en dos pasos.
2. Ve a https://myaccount.google.com/apppasswords, crea una contraseña con el nombre "FutData n8n" y copia los 16 caracteres (sin espacios).
3. En n8n: *Overview → Create → Credential → SMTP*, con estos valores:

| Campo | Valor |
| :--- | :--- |
| Nombre de la credencial | `SMTP FutData` (exacto: el flujo la busca por este nombre) |
| User | `tu-cuenta@gmail.com` |
| Password | la contraseña de aplicación |
| Host | `smtp.gmail.com` |
| Port | `465` |
| SSL/TLS | activado |

(Alternativa: puerto `587` con SSL/TLS desactivado; n8n usa STARTTLS. En ambos casos la conexión va cifrada con TLS.)

No hace falta una credencial *Header Auth*: el token del webhook se lee del `.env` (ver la sección **Flujo**).

### 4. Importar el flujo

Opción A, desde la interfaz: *Overview → Create → Workflow → menú `...` → Import from File* y elige `workflows/futdata-notificaciones.json`.

Opción B, por CLI (la carpeta `workflows/` está montada en el contenedor):

```bash
docker compose exec n8n n8n import:workflow --input=/opt/futdata/workflows/futdata-notificaciones.json
```

> En Git Bash para Windows, antepón `MSYS_NO_PATHCONV=1` al comando; si no, Git Bash convierte la ruta `/opt/...` en una ruta de Windows.

Después de importar, abre el nodo **Enviar correo 2FA** y comprueba que la credencial seleccionada sea `SMTP FutData`; si aparece un aviso de credencial, elígela en la lista y guarda el flujo.

### 5. Publicar (activar) el flujo

En el editor del flujo, pulsa **Publish**. Desde ese momento responde en:

```text
http://localhost:5678/webhook/futdata/notificaciones
```

Si importas una versión nueva del JSON sobre un flujo ya publicado, vuelve a pulsar **Publish** (o despublica y publica de nuevo); n8n 2.x sigue usando la versión publicada anterior hasta entonces.

### 6. Probar

```bash
bash scripts/probar-2fa.sh                       # Git Bash, macOS o Linux
```

```powershell
pwsh -File scripts/probar-2fa.ps1                # PowerShell 7
powershell -File scripts/probar-2fa.ps1          # Windows PowerShell 5.1
```

Deben salir tres líneas `OK` y llegar un correo a `CORREO_PRUEBA` con el código `123456`. Si Windows bloquea el `.ps1` por la política de ejecución, desbloquéalo una vez con `Unblock-File scripts/probar-2fa.ps1` o ejecuta el script con PowerShell 7.

## Privacidad de los datos

- El flujo no guarda ejecuciones (exitosas, fallidas ni manuales), así que el código no queda en el historial de n8n.
- n8n 2.x elimina esas ejecuciones de su base de datos tras un búfer; el `docker-compose.yml` lo deja en 0 y revisa cada minuto, así el código no permanece más de ~1 minuto.
- Por lo mismo, los fallos no aparecen en *Executions*. Para depurar un cambio, prueba el flujo con datos ficticios y activa temporalmente *Settings → Save successful production executions* en el flujo; desactívalo antes de exportarlo de nuevo.

## Cambiar el correo

`plantillas/codigo-2fa.html` y `.txt` son la fuente del contenido. El nodo **Enviar correo 2FA** del flujo tiene una copia con los marcadores `{{nombre}}`, `{{codigo}}` y `{{vigenciaMinutos}}` reemplazados por expresiones de n8n (`nombre` se escapa para HTML). Si cambias la plantilla, actualiza también el HTML y el texto de ese nodo y exporta el flujo de nuevo a `workflows/`.

## Comandos útiles

```bash
docker compose ps               # estado
docker compose restart n8n      # reiniciar
docker compose down             # apagar (conserva datos en el volumen futdata_n8n_data)
```
