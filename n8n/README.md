# n8n · Notificaciones de FutData

n8n envía los correos del sistema. El backend le pide cada envío con un `POST` al webhook `futdata/notificaciones`; n8n no genera ni valida códigos, solo arma el correo y lo envía por SMTP.

Contrato completo (campos, respuestas, cómo llamarlo desde Spring Boot): [`docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md).

| Archivo | Para qué |
| :--- | :--- |
| `docker-compose.yml` | n8n 2.45.4 en Docker para desarrollo local (puerto 5678) |
| `.env.example` | Variables necesarias. Se copia como `.env` (ignorado por Git) |
| `workflows/futdata-notificaciones.json` | Flujo `FutData · Notificaciones` para importar |
| `plantillas/codigo-2fa.html` / `.txt` | Correo del código 2FA (HTML y texto plano) |
| `scripts/probar-2fa.sh` / `.ps1` | Prueba del webhook: caso válido (200), sin token (401), evento inválido (400) y, opcional, falla de envío (502) |
| `logs/` | Registro de fallas de envío `fallas-notificaciones.jsonl` (los `.jsonl` están ignorados por Git) |

## Flujo

```text
Webhook POST ─► Validar token ─► Validar estructura ─► Evento (Switch) ─► Validar datos CODIGO_2FA ─► Enviar correo 2FA ─► Responder 200
                    │ no               │ no                 │ otro evento          │ no                      │ error SMTP
                    ▼                  ▼                    ▼                      ▼                         ▼
              Responder 401   Responder 400 estructura  Responder 400 evento  Responder 400 datos  Armar registro de falla
                                                                                                             ▼
                                                                                                  Serializar línea JSONL
                                                                                                             ▼
                                                                                                    Convertir a archivo
                                                                                                             ▼
                                                                                                   Guardar falla en log
                                                                                                             ▼
                                                                                                       Responder 502
```

La rama de error SMTP agrega una línea al [registro de fallas](#registro-de-fallas) antes de responder `502`. Esos cuatro nodos tienen *On Error → Continue*: si el registro falla, la respuesta `502` sale igual.

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

#### Actualizar un flujo ya importado

No importes el JSON nuevo desde la interfaz: *Import from File* crea un flujo con otro `id` y queda duplicado. Por CLI, el JSON trae el `id` `FutDataNotif036`, así que reemplaza el flujo existente:

```bash
docker compose up -d            # aplica cambios del docker-compose.yml (no borra el volumen)
docker compose exec n8n n8n import:workflow --input=/opt/futdata/workflows/futdata-notificaciones.json
docker compose exec n8n n8n publish:workflow --id=FutDataNotif036
docker compose restart n8n      # la publicación por CLI se aplica al reiniciar
```

(En Git Bash, antepón `MSYS_NO_PATHCONV=1` a los dos `exec`.) Después abre el flujo, revisa la credencial de **Enviar correo 2FA** como en el paso 4 y, si la cambiaste, pulsa **Publish** otra vez: n8n 2.x sigue usando la versión publicada anterior hasta que se publica la nueva.

### 6. Probar

```bash
bash scripts/probar-2fa.sh                       # Git Bash, macOS o Linux
```

```powershell
pwsh -File scripts/probar-2fa.ps1                # PowerShell 7
powershell -File scripts/probar-2fa.ps1          # Windows PowerShell 5.1
```

Deben salir tres líneas `OK` y llegar un correo a `CORREO_PRUEBA` con el código `123456`. Si Windows bloquea el `.ps1` por la política de ejecución, desbloquéalo una vez con `Unblock-File scripts/probar-2fa.ps1` o ejecuta el script con PowerShell 7.

#### Caso opcional: falla de envío (502)

Comprueba que una falla real del SMTP responda `502` y quede en el [registro de fallas](#registro-de-fallas). Hay que romper la credencial a propósito:

1. Confirma que n8n está encendido: `docker compose ps` debe mostrar `futdata-n8n` con estado `Up`. Si no muestra nada, el contenedor está detenido (por ejemplo, después de reiniciar el equipo o Docker Desktop); levántalo con `docker compose up -d`.
2. En n8n abre la credencial `SMTP FutData`, cambia **Port** a un puerto donde no escuche nada (por ejemplo `2525`) y guárdala. Al guardar, n8n prueba la conexión y muestra *Couldn't connect with these settings … ECONNREFUSED*: es lo esperado, la credencial queda guardada igual. El flujo publicado la usa al momento, sin volver a publicar.
3. Ejecuta los scripts con el parámetro de falla:

   ```bash
   bash scripts/probar-2fa.sh --probar-falla
   ```

   ```powershell
   pwsh -File scripts/probar-2fa.ps1 -ProbarFalla
   ```

   En este modo se omite el caso `200` (con el SMTP roto no puede pasar). Corren *Sin token*, *Evento inválido* y *Falla de envío SMTP*, que espera `502`. Después el script revisa que `logs/fallas-notificaciones.jsonl` tenga una línea nueva, que sea JSON con los cinco campos y que no contenga el código `123456`, el `CORREO_PRUEBA` completo ni el token. Deben salir cuatro líneas `OK`.

   El caso *Falla de envío SMTP* tarda: con Docker Desktop en Windows la conexión rechazada demora unos 20 s, y si el host no responde nodemailer espera hasta 2 min. Por eso ese caso espera hasta 150 s (los demás, 15 s). Si sale `sin respuesta`, revisa el paso 1.
4. **Restaura el puerto** de la credencial (`465`, o `587`) y vuelve a correr el script sin el parámetro para confirmar el `200`.

El script lee el log desde `n8n/logs/` en tu equipo, así que solo sirve con el n8n de este `docker-compose.yml`.

## Registro de fallas

n8n no guarda ejecuciones (ver [Privacidad de los datos](#privacidad-de-los-datos)), así que una falla de envío no aparece en *Executions*. Por eso la rama de error del nodo de envío agrega una línea a un archivo propio antes de responder `502`. No depende del backend ni de la base de datos.

| | |
| :--- | :--- |
| Archivo en el equipo | `n8n/logs/fallas-notificaciones.jsonl` (ignorado por Git) |
| Ruta en el contenedor | `/opt/futdata/logs/fallas-notificaciones.jsonl` |
| Formato | [JSON Lines](https://jsonlines.org/): un objeto JSON por línea, UTF-8, siempre agregado al final |
| Escrito por | Nodo **Guardar falla en log** (*Read/Write Files from Disk*, opción *Append*) |

Ejemplo de línea:

```json
{"fecha":"2026-10-28T09:42:06.626-05:00","evento":"CODIGO_2FA","correoEnmascarado":"o***3@gmail.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"4"}
```

| Campo | Contenido |
| :--- | :--- |
| `fecha` | Momento de la falla, ISO 8601 con zona `America/Bogota` (`-05:00`) |
| `evento` | Evento de la petición, por ejemplo `CODIGO_2FA` |
| `correoEnmascarado` | Primera y última letra del usuario del correo, y el dominio: `o***3@gmail.com`. Si el usuario tiene 1 o 2 letras, solo la primera: `a***@ejemplo.com` |
| `error` | Mensaje del error SMTP, recortado a 500 caracteres. Se reemplazan los correos por `[correo]` y los números de 6 dígitos por `[código]` |
| `idEjecucion` | Id de la ejecución de n8n. Sirve para cruzar con los logs del contenedor (`docker compose logs n8n`); la ejecución en sí no se guarda |

**Por qué no tiene datos sensibles.** El nodo **Armar registro de falla** construye la línea campo por campo; no copia la petición. Nunca incluye el código, el nombre, el correo completo, el token ni el cuerpo. El mensaje del SMTP puede traer la dirección del destinatario (por ejemplo, en un rechazo `550`), por eso se limpia de correos, y de paso de cualquier número de 6 dígitos. El correo enmascarado basta para que soporte ubique el caso sin exponer la dirección. `scripts/probar-2fa.* --probar-falla` lo verifica.

**Si el registro falla**, por ejemplo por permisos o disco lleno, los cuatro nodos del registro siguen (*On Error → Continue*) y **Responder 502** sale igual. El backend nunca se queda sin respuesta por culpa del log.

**Configuración en `docker-compose.yml`:**

- `./logs` se monta como `/opt/futdata/logs` con escritura. La carpeta debe existir (por eso `logs/.gitkeep`); el archivo lo crea n8n en la primera falla.
- `N8N_RESTRICT_FILE_ACCESS_TO=/opt/futdata/logs`: los nodos de archivos de n8n 2.x solo pueden usar las rutas de esta variable (separadas por `;`). Su valor por defecto es `~/.n8n-files`, así que sin ella el nodo respondería *Access to the file is not allowed*.
- En Linux el contenedor escribe como el usuario `node` (uid 1000). Si tu usuario tiene otro uid y aparece ese error, da permiso de escritura a la carpeta: `chmod o+w logs`. Con Docker Desktop (Windows o macOS) no hace falta.

**Consultarlo** (desde `n8n/`):

```powershell
Get-Content logs/fallas-notificaciones.jsonl -Tail 20             # últimas 20 fallas
Get-Content logs/fallas-notificaciones.jsonl -Tail 0 -Wait        # seguir en vivo (Ctrl+C para salir)
Get-Content logs/fallas-notificaciones.jsonl | ConvertFrom-Json | Format-Table fecha, evento, correoEnmascarado, error
```

```bash
tail -n 20 logs/fallas-notificaciones.jsonl                       # últimas 20 fallas
tail -f logs/fallas-notificaciones.jsonl                          # seguir en vivo (Ctrl+C para salir)
```

El archivo crece sin límite. Para vaciarlo basta con borrarlo; n8n lo vuelve a crear.

**Eventos nuevos.** Los nodos del registro toman `evento` y `correo` del Webhook, así que sirven para cualquier evento. Conecta la salida de error del nodo de envío nuevo a **Armar registro de falla**. Si el evento no trae `datos.correo`, el campo queda como `***`.

### Propuesta: tabla `fallas_notificacion` en Supabase

> Solo es una propuesta; la tabla **no** existe todavía. Sirve para migrar el log cuando exista la base de datos.

Mismos campos que el log, en `snake_case`:

```sql
create table fallas_notificacion (
  id                  bigint generated always as identity primary key,
  fecha               timestamptz  not null,
  evento              text         not null,
  correo_enmascarado  text         not null,
  error               varchar(500) not null,
  id_ejecucion        text         not null,
  creado_en           timestamptz  not null default now()
);

create index fallas_notificacion_fecha_idx on fallas_notificacion (fecha desc);
```

- La tabla solo admite datos no sensibles. No agregar columnas para el código, el nombre, el correo completo ni el cuerpo de la petición.
- Activar RLS sin políticas públicas: solo el backend (con la *service role*) inserta y consulta.
- Migración del log: cada línea del `.jsonl` es una fila (`correoEnmascarado` → `correo_enmascarado`, `idEjecucion` → `id_ejecucion`). Después, en el flujo se cambia **Guardar falla en log** por un nodo *Supabase* o *Postgres* que inserte la fila, y se mantiene *On Error → Continue*.

## Privacidad de los datos

- El flujo no guarda ejecuciones (exitosas, fallidas ni manuales), así que el código no queda en el historial de n8n.
- n8n 2.x elimina esas ejecuciones de su base de datos tras un búfer; el `docker-compose.yml` lo deja en 0 y revisa cada minuto, así el código no permanece más de ~1 minuto.
- Por lo mismo, los fallos no aparecen en *Executions*. Las fallas de envío sí quedan, sin datos sensibles, en el [registro de fallas](#registro-de-fallas). Para depurar otro cambio, prueba el flujo con datos ficticios y activa temporalmente *Settings → Save successful production executions* en el flujo; desactívalo antes de exportarlo de nuevo.

## Cambiar el correo

`plantillas/codigo-2fa.html` y `.txt` son la fuente del contenido. El nodo **Enviar correo 2FA** del flujo tiene una copia con los marcadores `{{nombre}}`, `{{codigo}}` y `{{vigenciaMinutos}}` reemplazados por expresiones de n8n (`nombre` se escapa para HTML). Si cambias la plantilla, actualiza también el HTML y el texto de ese nodo y exporta el flujo de nuevo a `workflows/`.

## Comandos útiles

```bash
docker compose up -d            # levantar o aplicar cambios del docker-compose.yml
docker compose ps               # estado
docker compose restart n8n      # reiniciar
docker compose down             # apagar (conserva datos en el volumen futdata_n8n_data)
```
