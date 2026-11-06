# n8n · Notificaciones de FutData

n8n envía los correos del sistema. El backend le pide cada envío con un `POST` al webhook `futdata/notificaciones`; n8n no genera ni valida códigos ni consulta la base de datos, solo arma el correo y lo envía por SMTP.

Contrato completo (campos, respuestas, cómo llamarlo desde Spring Boot): [`docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md).

| Evento | Historia | Respuesta | Correo |
| :--- | :--- | :--- | :--- |
| `CODIGO_2FA` | HU-036 | `202` apenas valida los datos; envía después | Código de verificación a un usuario |
| `CAMBIO_ENTRENAMIENTO` | HU-026 | `202` apenas valida los datos; envía después | Aviso de entrenamiento creado, modificado o cancelado, un correo por destinatario (hasta 200) |
| `ENLACE_CONTRASENA` | Regla 9 | **Síncrona**: envía y después responde `200` (o `502` si el SMTP falla) | Enlace para que un entrenador nuevo cree su contraseña (vence en 1 hora) |

| Archivo | Para qué |
| :--- | :--- |
| `docker-compose.yml` | n8n 2.45.4 en Docker para desarrollo local (puerto 5678) |
| `.env.example` | Variables necesarias. Se copia como `.env` (ignorado por Git) |
| `workflows/futdata-notificaciones.json` | Flujo `FutData · Notificaciones` para importar |
| `plantillas/codigo-2fa.html` / `.txt` | Correo del código 2FA (HTML y texto plano) |
| `plantillas/cambio-entrenamiento.html` / `.txt` | Aviso de cambio de entrenamiento (HTML y texto plano) |
| `plantillas/bienvenida-entrenador.html` / `.txt` | Plantilla `BIENVENIDA_ENTRENADOR` de `ENLACE_CONTRASENA`: bienvenida y botón para crear la contraseña |
| `plantillas/documento-rechazado.html` / `.txt` | Aviso de `DOCUMENTO_RECHAZADO` al acudiente: el documento rechazado, el motivo (con sus saltos de línea) y un botón a la sección de documentos |
| `scripts/probar-2fa.sh` / `.ps1` | Prueba de `CODIGO_2FA`: caso válido (202), sin token (401), evento inválido (400) y, opcional, falla de envío (202 y línea en el registro) |
| `scripts/probar-entrenamiento.sh` / `.ps1` | Prueba de `CAMBIO_ENTRENAMIENTO`: creado, modificado, cancelado y duplicados (202), lista vacía, tipo inválido y más de 200 destinatarios (400), sin token (401) y, opcional, falla de envío |
| `scripts/probar-enlace.sh` / `.ps1` | Prueba de `ENLACE_CONTRASENA` con un enlace falso: caso válido (200), sin token (401), evento inválido (400), seis casos de datos inválidos (400) y, opcional, falla de envío (502 y línea en el registro sin el enlace) |
| `scripts/probar-documento.sh` / `.ps1` | Prueba de `DOCUMENTO_RECHAZADO` con un enlace falso: tres casos válidos (202: comentario de varias líneas con HTML, nombres neutros y comentario de 300 caracteres), sin token (401 o 403), evento inválido (400), catorce casos de datos inválidos (400, sin datos personales en el detalle) y, opcional, falla de envío (202 y línea en el registro sin el correo, los nombres, el comentario ni el enlace) |
| `logs/` | Registro de fallas de envío `fallas-notificaciones.jsonl` (los `.jsonl` están ignorados por Git) |

## Flujo

```text
Webhook POST ─► Validar token ─► Validar estructura ─► Evento (Switch) ─┬─ CODIGO_2FA ──────────► (rama 2FA)
                    │ no               │ no                             ├─ CAMBIO_ENTRENAMIENTO ► (rama entrenamiento)
                    │                  │                                ├─ ENLACE_CONTRASENA ───► (rama enlace)
                    ▼                  ▼                                └─ otro evento ─────────► Responder 400 evento
              Responder 401   Responder 400 estructura

Rama 2FA (asíncrona):
Validar datos CODIGO_2FA ─► Responder 202 2FA ─► Enviar correo 2FA
        │ no                                           │ error SMTP
        ▼                                              ▼
Responder 400 datos                    (registro de fallas) ─► fin, sin respuesta

Rama entrenamiento (asíncrona):
Validar datos CAMBIO_ENTRENAMIENTO ─► ¿Datos válidos? ─► Responder 202 ─► Armar un correo por destinatario ─► Enviar aviso de entrenamiento
                                            │ no                                                                   │ error SMTP (por destinatario)
                                            ▼                                                                      ▼
                                 Responder 400 entrenamiento                                               (registro de fallas) ─► fin, sin respuesta

Rama enlace (síncrona):
Validar datos ENLACE_CONTRASENA ─► ¿Datos de enlace válidos? ─► Enviar correo enlace ─► Responder 200 enlace
                                            │ no                        │ error SMTP
                                            ▼                           ▼
                                  Responder 400 enlace       (registro de fallas) ─► Responder 502

Registro de fallas (común):
Armar registro de falla ─► Serializar línea JSONL ─► Convertir a archivo ─► Guardar falla en log ─► ¿Responder 502? ─► Responder 502 (eventos síncronos: ENLACE_CONTRASENA)
                                                                                                         │ CODIGO_2FA o CAMBIO_ENTRENAMIENTO
                                                                                                         ▼
                                                                                                   (ya respondió 202)
```

La rama de error SMTP agrega una línea al [registro de fallas](#registro-de-fallas). Los cuatro nodos del registro y `¿Responder 502?` tienen *On Error → Continue*: si el registro falla, la respuesta `502` de un evento síncrono sale igual. `CODIGO_2FA` y `CAMBIO_ENTRENAMIENTO` ya respondieron `202`, así que `¿Responder 502?` no vuelve a responder. `ENLACE_CONTRASENA` es síncrono: su falla sigue hasta **Responder 502**.

**Rama `ENLACE_CONTRASENA`:**

- **Validar datos ENLACE_CONTRASENA** (nodo *Code*) revisa que `plantilla` esté en la lista permitida (hoy solo `BIENVENIDA_ENTRENADOR`), el formato de `correo`, que `nombre` no esté vacío, que `enlace` empiece por `http://` o `https://` sin espacios y que `vigenciaHoras` sea un número mayor que 0. Escapa el nombre y el enlace para el HTML y arma el texto de la vigencia ("1 hora" o "N horas"). Nunca lanza errores: devuelve `valido` y `detalle`, y `detalle` nunca trae el correo, el nombre ni el enlace.
- **Enviar correo enlace** envía la plantilla `BIENVENIDA_ENTRENADOR` (ver [Cambiar el correo](#cambiar-el-correo)) y **después** **Responder 200 enlace** responde `{"enviado": true}`. Si el SMTP falla, su salida de error va al registro de fallas y termina en **Responder 502**.
- Es síncrona porque el backend necesita saber si el correo salió (`correoEnviado` en el alta, error en el reenvío). El backend espera hasta 15 s solo en este evento; el envío tarda ~3 s.
- **El enlace es secreto**: el registro de fallas lo reemplaza por `[enlace]`, igual que cualquier `token=...`.

**Rama `CODIGO_2FA`:** **Responder 202 2FA** responde `{"aceptado": true}` después de validar el token, la estructura y los datos, y antes de enviar. El SMTP tarda ~3 s y el backend espera máximo 5 s. Si el envío falla, el backend ya respondió y el código quedó guardado: el usuario no recibe el correo y debe usar "Reenviar código" (disponible al minuto). La falla solo queda en el registro, con el correo enmascarado y sin el código (ver el contrato).

**Rama `CAMBIO_ENTRENAMIENTO`:**

- **Validar datos CAMBIO_ENTRENAMIENTO** (nodo *Code*) revisa `tipoCambio`, los campos del entrenamiento, el formato de fecha (`AAAA-MM-DD`, fecha real) y hora (`HH:mm`), `anterior` (solo si `tipoCambio` es `MODIFICADO`), `motivo` y que `destinatarios` tenga de 1 a 200 elementos. Luego quita los destinatarios duplicados por correo (sin distinguir mayúsculas) y descarta los correos inválidos. Nunca lanza errores: devuelve `valido` y `detalle`.
- **Responder 202** responde `{"aceptado": true, "destinatarios": n}` antes de enviar. El backend espera máximo 5 s y un aviso a 30 personas tarda bastante más, por eso esta rama no responde `502` (ver el contrato).
- **Armar un correo por destinatario** (nodo *Code*) arma un ítem por destinatario con el asunto, el HTML y el texto. Tiene una copia de las plantillas (ver [Cambiar el correo](#cambiar-el-correo)).
- **Enviar aviso de entrenamiento** envía un correo por ítem, con un solo destinatario en *Para* (nunca todos en *Para* ni en *CC*), el remitente `$env.SMTP_FROM` y la credencial `SMTP FutData`. Su salida de error (*On Error → Continue (using error output)*) lleva cada envío fallido al registro de fallas, y n8n sigue con los demás destinatarios.

> **Límite de Gmail.** Una cuenta de Gmail permite unos 500 correos al día (menos en cuentas nuevas) y Google puede bloquearla temporalmente si se pasa. Un aviso a una categoría completa son decenas de correos, así que Gmail solo sirve para desarrollo. En producción se necesita un SMTP transaccional (por ejemplo Amazon SES, Brevo, Mailgun o SendGrid), como indica el PPI. Para cambiarlo basta con editar la credencial `SMTP FutData` y `SMTP_FROM`; el flujo no cambia.

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

Después de importar, abre los nodos **Enviar correo 2FA** y **Enviar aviso de entrenamiento** y comprueba que la credencial seleccionada sea `SMTP FutData`; si aparece un aviso de credencial, elígela en la lista y guarda el flujo.

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

(En Git Bash, antepón `MSYS_NO_PATHCONV=1` a los dos `exec`.) Después abre el flujo, revisa la credencial de los tres nodos de envío (2FA, entrenamiento y enlace) como en el paso 4 y, si la cambiaste, pulsa **Publish** otra vez: n8n 2.x sigue usando la versión publicada anterior hasta que se publica la nueva.

### 6. Probar

```bash
bash scripts/probar-2fa.sh                       # Git Bash, macOS o Linux
```

```powershell
pwsh -File scripts/probar-2fa.ps1                # PowerShell 7
powershell -File scripts/probar-2fa.ps1          # Windows PowerShell 5.1
```

Deben salir tres líneas `OK` y llegar un correo a `CORREO_PRUEBA` con el código `123456`. Si Windows bloquea el `.ps1` por la política de ejecución, desbloquéalo una vez con `Unblock-File scripts/probar-2fa.ps1` o ejecuta el script con PowerShell 7.

Para el aviso de entrenamiento:

```bash
bash scripts/probar-entrenamiento.sh
```

```powershell
pwsh -File scripts/probar-entrenamiento.ps1
powershell -File scripts/probar-entrenamiento.ps1
```

Deben salir ocho líneas `OK`:

| Caso | Espera |
| :--- | :--- |
| `CREADO`, `MODIFICADO` y `CANCELADO` con `CORREO_PRUEBA` | `202` con `destinatarios: 1` |
| Destinatarios duplicados: `CORREO_PRUEBA`, el mismo en mayúsculas y uno inválido | `202` con `destinatarios: 1` |
| Lista de destinatarios vacía | `400` |
| `tipoCambio` inválido | `400` |
| Sin token | `401` |
| Más de 200 destinatarios (dominio `.invalid`, que nunca existe) | `400` |

Como el envío sigue después del `202`, revisa la bandeja de `CORREO_PRUEBA`: deben llegar **4 correos** (nuevo, cambio con lo anterior tachado y lo nuevo resaltado, cancelado con su motivo, y uno solo de la prueba de duplicados). Si falta alguno, revisa `logs/fallas-notificaciones.jsonl`.

Para el enlace de crear contraseña:

```bash
bash scripts/probar-enlace.sh
```

```powershell
pwsh -File scripts/probar-enlace.ps1
powershell -File scripts/probar-enlace.ps1
```

Deben salir nueve líneas `OK`:

| Caso | Espera |
| :--- | :--- |
| Enlace válido con `CORREO_PRUEBA` (envía el correo) | `200` con `{"enviado": true}` |
| Sin token | `401` |
| Evento inválido | `400` |
| Datos inválidos: plantilla no permitida, correo inválido, sin nombre, enlace sin `http(s)`, `vigenciaHoras` no numérico y `vigenciaHoras` 0 | `400` cada uno |

Como el evento es síncrono, el `200` ya confirma que el SMTP aceptó el correo. El enlace del script es **falso** (`https://example.test/crear-contrasena?token=PRUEBA`): el correo llega con el botón, pero no lleva a ningún lado.

#### Caso opcional: falla de envío

Comprueba que una falla real del SMTP quede en el [registro de fallas](#registro-de-fallas), que `CODIGO_2FA` responda `202` igual y que `ENLACE_CONTRASENA` responda `502`. Hay que romper la credencial a propósito:

1. Confirma que n8n está encendido: `docker compose ps` debe mostrar `futdata-n8n` con estado `Up`. Si no muestra nada, el contenedor está detenido (por ejemplo, después de reiniciar el equipo o Docker Desktop); levántalo con `docker compose up -d`.
2. En n8n abre la credencial `SMTP FutData`, cambia **Port** a un puerto donde no escuche nada (por ejemplo `2525`) y guárdala. Al guardar, n8n prueba la conexión y muestra *Couldn't connect with these settings … ECONNREFUSED*: es lo esperado, la credencial queda guardada igual. El flujo publicado la usa al momento, sin volver a publicar.
3. Ejecuta los scripts con el parámetro de falla:

   ```bash
   bash scripts/probar-2fa.sh --probar-falla
   bash scripts/probar-entrenamiento.sh --probar-falla
   bash scripts/probar-enlace.sh --probar-falla
   bash scripts/probar-documento.sh --probar-falla
   ```

   ```powershell
   pwsh -File scripts/probar-2fa.ps1 -ProbarFalla
   pwsh -File scripts/probar-entrenamiento.ps1 -ProbarFalla
   pwsh -File scripts/probar-enlace.ps1 -ProbarFalla
   pwsh -File scripts/probar-documento.ps1 -ProbarFalla
   ```

   En este modo se omite el caso *Código 2FA válido*. Corren *Sin token*, *Evento inválido* y *Falla de envío SMTP (responde 202 igual)*, que espera `202` de inmediato, porque n8n responde antes de enviar. Después el script espera hasta 150 s a que aparezca una línea `CODIGO_2FA` nueva en `logs/fallas-notificaciones.jsonl`, y revisa que sea JSON con los cinco campos y que no contenga el código `123456`, el `CORREO_PRUEBA` completo ni el token. Deben salir cuatro líneas `OK`.

   La línea tarda: con Docker Desktop en Windows la conexión rechazada demora unos 20 s, y si el host no responde nodemailer espera hasta 2 min. Por eso la espera del registro es de hasta 150 s (las respuestas, 15 s). Si sale `sin respuesta`, revisa el paso 1.

   En `probar-entrenamiento` se omiten los cuatro casos que envían correos. Corren los cuatro casos de error (`400` y `401`) y *Falla de envío SMTP (responde 202 igual)*, con dos destinatarios: `CORREO_PRUEBA` y el mismo con `+falla` antes de la `@`. Ese caso espera `202` de inmediato, porque n8n responde antes de enviar. Después el script espera hasta 150 s (cada rechazo tarda ~21 s y los envíos van uno tras otro) a que aparezcan **dos** líneas `CAMBIO_ENTRENAMIENTO` en el log, una por destinatario, lo que muestra que la falla del primero no frenó al segundo. Revisa que sean JSON con los cinco campos y que no tengan ninguno de los dos correos completos ni el token. Deben salir seis líneas `OK`.

   En `probar-enlace` se omite el caso *Enlace válido*. Corren los ocho casos de error (`400` y `401`) y *Falla de envío SMTP*, que espera **`502`**, porque el evento es síncrono: n8n intenta enviar, escribe la línea y después responde (hasta 150 s, por la demora del rechazo). Luego revisa la línea `ENLACE_CONTRASENA` nueva: JSON con los cinco campos y **sin** el enlace, sin `token=`, sin `example.test`, sin el `CORREO_PRUEBA` completo y sin el token del webhook. Deben salir diez líneas `OK`. Con el SMTP roto el backend real agotaría sus 15 s antes del `502`; para él es lo mismo: el correo no salió.
4. **Restaura el puerto** de la credencial (`465`, o `587`) y vuelve a correr los scripts sin el parámetro para confirmar los `202` y `200` y que lleguen los correos.

Los scripts leen el log desde `n8n/logs/` en tu equipo, así que solo sirven con el n8n de este `docker-compose.yml`.

## Registro de fallas

n8n no guarda ejecuciones (ver [Privacidad de los datos](#privacidad-de-los-datos)), así que una falla de envío no aparece en *Executions*. Por eso la rama de error de cada nodo de envío agrega una línea a un archivo propio: en `CODIGO_2FA` y `ENLACE_CONTRASENA`, una línea con el correo enmascarado; en `CAMBIO_ENTRENAMIENTO`, una línea por cada destinatario al que no se pudo enviar. En los dos primeros asíncronos la respuesta `202` ya salió; en `ENLACE_CONTRASENA`, que es síncrono, la línea se escribe antes de responder `502`. No depende del backend ni de la base de datos.

| | |
| :--- | :--- |
| Archivo en el equipo | `n8n/logs/fallas-notificaciones.jsonl` (ignorado por Git) |
| Ruta en el contenedor | `/opt/futdata/logs/fallas-notificaciones.jsonl` |
| Formato | [JSON Lines](https://jsonlines.org/): un objeto JSON por línea, UTF-8, siempre agregado al final |
| Escrito por | Nodo **Guardar falla en log** (*Read/Write Files from Disk*, opción *Append*) |

Ejemplo de línea:

```json
{"fecha":"2026-10-28T09:42:06.626-05:00","evento":"CODIGO_2FA","correoEnmascarado":"o***3@gmail.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"4"}
{"fecha":"2026-11-02T18:05:11.203-05:00","evento":"CAMBIO_ENTRENAMIENTO","correoEnmascarado":"m***a@ejemplo.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"57"}
```

| Campo | Contenido |
| :--- | :--- |
| `fecha` | Momento de la falla, ISO 8601 con zona `America/Bogota` (`-05:00`) |
| `evento` | Evento de la petición, por ejemplo `CODIGO_2FA` |
| `correoEnmascarado` | Primera y última letra del usuario del correo, y el dominio: `o***3@gmail.com`. Si el usuario tiene 1 o 2 letras, solo la primera: `a***@ejemplo.com` |
| `error` | Mensaje del error SMTP, recortado a 500 caracteres. Se reemplazan los correos por `[correo]` y los números de 6 dígitos por `[código]` |
| `idEjecucion` | Id de la ejecución de n8n. Sirve para cruzar con los logs del contenedor (`docker compose logs n8n`); la ejecución en sí no se guarda |

**Por qué no tiene datos sensibles.** El nodo **Armar registro de falla** construye la línea campo por campo; no copia la petición. Nunca incluye el código, el enlace de crear contraseña, el nombre, el correo completo, el token, el cuerpo ni los datos del entrenamiento. El mensaje del SMTP puede traer la dirección del destinatario (por ejemplo, en un rechazo `550`) o parte del contenido, por eso se limpia: primero cualquier URL y cualquier `token=...` se reemplazan por `[enlace]`, después los correos por `[correo]` y los números de 6 dígitos por `[código]`. El correo enmascarado basta para que soporte ubique el caso sin exponer la dirección. `scripts/probar-2fa.* --probar-falla`, `scripts/probar-entrenamiento.* --probar-falla` y `scripts/probar-enlace.* --probar-falla` lo verifican.

**Si el registro falla**, por ejemplo por permisos o disco lleno, los cuatro nodos del registro y **¿Responder 502?** siguen (*On Error → Continue*) y **Responder 502** sale igual en un evento síncrono. El backend nunca se queda sin respuesta por culpa del log. En `CODIGO_2FA` y `CAMBIO_ENTRENAMIENTO` la respuesta `202` ya salió antes de enviar, así que el log no la afecta.

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

**Eventos nuevos.** Los nodos del registro toman `evento` del Webhook y el correo del ítem que falló (campo `correo`, como en `CAMBIO_ENTRENAMIENTO`, donde hay un ítem por destinatario) o, si no lo tiene, de `datos.correo` (como en `CODIGO_2FA`). Así sirven para cualquier evento: conecta la salida de error del nodo de envío nuevo a **Armar registro de falla**. Si no hay correo en ninguno de los dos, el campo queda como `***`. Si el evento responde antes de enviar (asíncrono, como `CODIGO_2FA` y `CAMBIO_ENTRENAMIENTO`), agrégalo a la condición de **¿Responder 502?**; si es síncrono, déjalo fuera para que la falla responda `502`.

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

- El flujo no guarda ejecuciones (exitosas, fallidas ni manuales), así que el código 2FA, el enlace de crear contraseña y las listas de destinatarios de los avisos (correos y nombres de deportistas, muchos menores de edad) no quedan en el historial de n8n.
- n8n 2.x elimina esas ejecuciones de su base de datos tras un búfer; el `docker-compose.yml` lo deja en 0 y revisa cada minuto, así esos datos no permanecen más de ~1 minuto (el enlace vence en 1 hora).
- Por lo mismo, los fallos no aparecen en *Executions*. Las fallas de envío sí quedan, sin datos sensibles, en el [registro de fallas](#registro-de-fallas). Para depurar otro cambio, prueba el flujo con datos ficticios y activa temporalmente *Settings → Save successful production executions* en el flujo; desactívalo antes de exportarlo de nuevo.

## Cambiar el correo

`plantillas/codigo-2fa.html` y `.txt` son la fuente del contenido. El nodo **Enviar correo 2FA** del flujo tiene una copia con los marcadores `{{nombre}}`, `{{codigo}}` y `{{vigenciaMinutos}}` reemplazados por expresiones de n8n (`nombre` se escapa para HTML). Si cambias la plantilla, actualiza también el HTML y el texto de ese nodo y exporta el flujo de nuevo a `workflows/`.

`plantillas/cambio-entrenamiento.html` y `.txt` son la fuente del aviso de entrenamiento. El nodo **Armar un correo por destinatario** tiene una copia exacta de cada una en las constantes `PLANTILLA_HTML` y `PLANTILLA_TEXTO`, y reemplaza los marcadores en una sola pasada (un `{{...}}` escrito en un dato de la petición no se vuelve a procesar):

| Marcador | Contenido |
| :--- | :--- |
| `{{asunto}}` | Asunto según `tipoCambio`, por ejemplo `Nuevo entrenamiento Sub-12 · mié 4 nov, 4:00 p. m.` |
| `{{vistaPrevia}}` | Texto que muestran los clientes de correo junto al asunto (solo HTML) |
| `{{etiqueta}}`, `{{etiquetaFondo}}`, `{{etiquetaTexto}}` | Etiqueta del tipo de cambio y sus colores de `tokens.css`: verde (*success*) para nuevo, ámbar (*warning*) para modificado, rojo (*danger*) para cancelado (solo HTML) |
| `{{saludo}}` | `Hola, {nombre}:` o `Hola:` si el destinatario no trae nombre |
| `{{intro}}` | Frase según `tipoCambio` |
| `{{filasDetalle}}` / `{{detalle}}` | Categoría, fecha, hora, lugar y entrenador. En `MODIFICADO`, cada campo que cambió muestra lo anterior tachado y lo nuevo resaltado en verde (en texto: `(antes: ...)`) |
| `{{bloqueMotivo}}` / `{{motivo}}` | Motivo, si viene, en `MODIFICADO` y `CANCELADO` |
| `{{categoria}}` | Nombre de la categoría, en el pie |

La fecha y la hora se escriben en formato colombiano (`miércoles 4 de noviembre de 2026`, `4:00 p. m.`, zona `America/Bogota`). Todos los textos de la petición se escapan para HTML antes de insertarlos. Si cambias una plantilla, pega su contenido nuevo en la constante correspondiente del nodo y exporta el flujo.

`plantillas/bienvenida-entrenador.html` y `.txt` son la fuente de la plantilla `BIENVENIDA_ENTRENADOR` de `ENLACE_CONTRASENA`. El nodo **Enviar correo enlace** tiene una copia con estos marcadores reemplazados por expresiones de n8n:

| Marcador | En el HTML | En el texto |
| :--- | :--- | :--- |
| `{{nombre}}` | `$json.nombreHtml` (escapado) | `$json.nombre` |
| `{{enlace}}` | `$json.enlaceHtml` (escapado, también en el `href`) | `$json.enlace` |
| `{{vigencia}}` | `$json.vigencia`: "1 hora" o "N horas", según `vigenciaHoras` | igual |

Los arma **Validar datos ENLACE_CONTRASENA**. Si cambias la plantilla, actualiza también el HTML y el texto de ese nodo y exporta el flujo. **Plantilla nueva** (por ejemplo `BIENVENIDA_ACUDIENTE` en el bloque 2B): agrégala a la lista `PLANTILLAS` del nodo de validación y dale su propio nodo de envío (o un Switch por `plantilla` antes del envío), con la salida de éxito a **Responder 200 enlace** y la de error a **Armar registro de falla**.

## Comandos útiles

```bash
docker compose up -d            # levantar o aplicar cambios del docker-compose.yml
docker compose ps               # estado
docker compose restart n8n      # reiniciar
docker compose down             # apagar (conserva datos en el volumen futdata_n8n_data)
```
