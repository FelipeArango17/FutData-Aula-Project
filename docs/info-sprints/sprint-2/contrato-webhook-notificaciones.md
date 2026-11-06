# Contrato del webhook de notificaciones (n8n)

**Sprint:** Sprint 2  
**Historias:** HU-036 · Enviar código 2FA por correo con n8n (AB#51) · HU-026 · Aviso a participantes cuando cambia un entrenamiento (AB#338) · Regla 9 · Alta de entrenador con enlace para crear la contraseña · Regla 14 · Recordatorio de pago · Regla 8 · Aviso de documento rechazado (bloque 3)  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club

## Objetivo

Definir cómo el backend (Spring Boot) le pide a n8n que envíe una notificación. El contrato es el mismo para todos los eventos. Eventos implementados:

| Evento | Historia | Respuesta | Para qué |
| :--- | :--- | :--- | :--- |
| `CODIGO_2FA` | HU-036 | Asíncrona: `202` | Código de verificación al iniciar sesión |
| `CAMBIO_ENTRENAMIENTO` | HU-026 (CU-05) | Asíncrona: `202` | Aviso al entrenador, los jugadores y los acudientes cuando se crea, modifica o cancela un entrenamiento |
| `ENLACE_CONTRASENA` | Regla 9 (alta de entrenador y su reenvío) | **Síncrona**: `200` o `502` | Enlace para que la persona cree su propia contraseña (vence en 1 hora) |
| `RECORDATORIO_PAGO` | Regla 14 (cobros por ciclo de ingreso) | Asíncrona: `202` | Recordatorio al acudiente de una mensualidad creada con anticipación. Un correo por acudiente |
| `DOCUMENTO_RECHAZADO` | Regla 8 (bloque 3, documentos) | Asíncrona: `202` | Aviso al acudiente de que el administrador rechazó un documento de la inscripción de su deportista. Un correo por acudiente. **El backend ya lo envía y el JSON de Cloud con el evento ya está en el repositorio (`n8n/workflows/futdata-notificaciones_cloud.json`); falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento` (Omar)** (hasta entonces responde `400` "Evento no soportado") |

## Reparto de responsabilidades

| Parte | Hace | No hace |
| :--- | :--- | :--- |
| Backend (`AutenticacionServiceImpl`) | Genera el código de 6 dígitos (`GeneradorCodigo`), guarda solo su hash con vigencia de 10 min (`ReglasVerificacion.VIGENCIA_CODIGO`), valida intentos y reenvíos, y llama a `INotificador.notificar(evento, datos)` | Enviar correos |
| Backend (servicio de entrenamientos, HU-026) | Después de guardar la creación, edición o eliminación de un entrenamiento, resuelve los destinatarios (entrenador, acudientes y deportistas con correo) y llama a `INotificador.notificar("CAMBIO_ENTRENAMIENTO", datos)` | Enviar correos |
| Backend (`EntrenadorServiceImpl` con `CuentasDeAcceso`) | Crea la cuenta de Supabase Auth sin contraseña, genera el enlace (`hashed_token` de `generate_link`, nunca el `action_link`), lo arma con `FRONTEND_URL` y llama a `INotificador.notificar("ENLACE_CONTRASENA", datos)` después del commit del alta o en el reenvío | Enviar correos |
| Backend (`CobrosCicloServiceImpl`) | Al crear una mensualidad con anticipación, resuelve los acudientes con correo del deportista y llama a `INotificador.notificar("RECORDATORIO_PAGO", datos)`, una vez por acudiente | Enviar correos |
| Backend (`DocumentoServiceImpl`) | Después de confirmar el rechazo de un documento, resuelve los acudientes con correo válido del deportista y llama a `INotificador.notificar("DOCUMENTO_RECHAZADO", datos)`, una vez por acudiente; si falla, el rechazo queda hecho y responde `avisoEnviado: false` | Enviar correos |
| `N8nClient` (implementa `INotificador`) | Hace el `POST` al webhook de n8n con el secreto compartido | Decidir el contenido del correo |
| n8n (flujo `FutData · Notificaciones`) | Valida la petición, arma el correo con la plantilla y lo envía por SMTP con TLS | Generar, guardar ni validar códigos; consultar la base de datos |

## Endpoint

```http
POST {N8N_BASE_URL}/webhook/futdata/notificaciones
Content-Type: application/json; charset=utf-8
X-FutData-Token: {N8N_WEBHOOK_TOKEN}
```

- Es un endpoint **único** para todas las notificaciones; el tipo lo define el campo `evento`.
- `N8N_BASE_URL`: URL de n8n sin barra final. En desarrollo, `http://localhost:5678`.
- `N8N_WEBHOOK_TOKEN`: secreto compartido entre el backend y n8n. Va en variables de entorno de ambos lados, nunca en el código ni en el repositorio. Se recomienda un valor aleatorio de 32 bytes en hexadecimal (`openssl rand -hex 32`).
- Fuera de desarrollo, n8n debe publicarse solo por HTTPS.

## Cuerpo

El cuerpo replica la firma `notificar(evento String, datos Map)`:

```json
{
  "evento": "CODIGO_2FA",
  "datos": {
    "correo": "jugador@ejemplo.com",
    "nombre": "Ana",
    "codigo": "123456",
    "vigenciaMinutos": 10
  }
}
```

| Campo | Tipo | Regla |
| :--- | :--- | :--- |
| `evento` | string | Obligatorio. Nombre del evento en `MAYUSCULAS_CON_GUION_BAJO` |
| `datos` | objeto | Obligatorio, no vacío. Sus campos dependen del evento |

### Evento `CODIGO_2FA`

| Campo de `datos` | Tipo | Regla |
| :--- | :--- | :--- |
| `correo` | string | Obligatorio. Correo con formato válido |
| `nombre` | string | Obligatorio, no vacío. Se usa en el saludo (n8n lo escapa antes de insertarlo en el HTML) |
| `codigo` | string | Obligatorio. Exactamente 6 dígitos. **Enviarlo como texto**, no como número, para no perder ceros a la izquierda (`"012345"`) |
| `vigenciaMinutos` | número | Obligatorio, mayor que 0. Debe coincidir con `ReglasVerificacion.VIGENCIA_CODIGO` (10) |

El correo enviado usa la plantilla `n8n/plantillas/codigo-2fa.html` (con versión de texto plano en `codigo-2fa.txt`) y el asunto "Tu código de verificación de FutData".

### Evento `CAMBIO_ENTRENAMIENTO`

El administrador o el entrenador crea, edita o elimina un entrenamiento (CU-05). Después de guardar el cambio, el backend envía un solo `POST` con todos los destinatarios y n8n manda **un correo por destinatario**.

```json
{
  "evento": "CAMBIO_ENTRENAMIENTO",
  "datos": {
    "tipoCambio": "MODIFICADO",
    "entrenamiento": {
      "categoria": "Sub-12",
      "fecha": "2026-11-04",
      "horaInicio": "16:00",
      "lugar": "Cancha 2",
      "entrenador": "Carlos Pérez"
    },
    "anterior": {
      "fecha": "2026-11-03",
      "horaInicio": "15:00",
      "lugar": "Cancha 1",
      "entrenador": "Carlos Pérez"
    },
    "motivo": "La cancha 1 está en mantenimiento",
    "destinatarios": [
      { "correo": "entrenador@ejemplo.com", "nombre": "Carlos Pérez" },
      { "correo": "acudiente@ejemplo.com", "nombre": "Marta Gómez" },
      { "correo": "jugador@ejemplo.com", "nombre": "Juan Gómez" }
    ]
  }
}
```

| Campo de `datos` | Tipo | Regla |
| :--- | :--- | :--- |
| `tipoCambio` | string | Obligatorio. `CREADO`, `MODIFICADO` o `CANCELADO` (en mayúsculas) |
| `entrenamiento` | objeto | Obligatorio. Datos **actuales** del entrenamiento; en `CANCELADO`, los del entrenamiento que se canceló |
| `entrenamiento.categoria` | string | Obligatorio, no vacío, máximo 120 caracteres. Nombre de `Categoria`, por ejemplo `Sub-12` |
| `entrenamiento.fecha` | string | Obligatorio. Fecha real con formato `AAAA-MM-DD` (`Entrenamiento.fecha`) |
| `entrenamiento.horaInicio` | string | Obligatorio. Hora de 24 horas con formato `HH:mm`, de `00:00` a `23:59` (`Entrenamiento.hora_inicio`) |
| `entrenamiento.lugar` | string | Obligatorio, no vacío, máximo 120 caracteres |
| `entrenamiento.entrenador` | string | Obligatorio, no vacío, máximo 120 caracteres. Nombre y apellido del entrenador a cargo de la sesión |
| `anterior` | objeto | Opcional. **Solo se usa si `tipoCambio` es `MODIFICADO`**; con otro tipo se ignora sin validarlo. Trae los valores de antes del cambio: `fecha`, `horaInicio`, `lugar` y `entrenador`, cada uno opcional y con las mismas reglas de formato de `entrenamiento`. El correo marca como cambiado cada campo que venga en `anterior` con un valor distinto al actual |
| `motivo` | string | Opcional, máximo 500 caracteres. Se muestra en `MODIFICADO` y `CANCELADO`; en `CREADO` se ignora |
| `destinatarios` | arreglo | Obligatorio, **de 1 a 200 elementos**. Cada elemento es `{ "correo": string, "nombre": string }`; `nombre` es opcional (máximo 120 caracteres) y se usa en el saludo |

**Destinatarios.**

- El backend los resuelve: el entrenador a cargo, los acudientes y los deportistas de la categoría que tengan correo. n8n **no consulta la base de datos**; solo usa la lista que recibe.
- n8n quita los duplicados por correo sin distinguir mayúsculas (`Ana@Ejemplo.com` y `ana@ejemplo.com` reciben un solo correo; se usa el primero de la lista) y descarta los elementos sin un correo válido. No responde `400` por eso: un acudiente con un correo mal escrito no debe dejar sin aviso a los demás.
- El límite de 200 se cuenta sobre la lista recibida, antes de quitar duplicados. Si después de depurarla no queda ningún correo válido, la respuesta es `400`.
- Cada destinatario recibe su propio correo: nunca se ponen todos en *Para* ni en *CC*, así nadie ve las direcciones de los demás.

**Correo.** Plantilla `n8n/plantillas/cambio-entrenamiento.html` (y `.txt`). La fecha y la hora se escriben en formato colombiano (hora de Colombia, `America/Bogota`): `fecha` y `horaInicio` se interpretan como hora local de Colombia, sin conversión. Asunto según `tipoCambio`:

| `tipoCambio` | Asunto |
| :--- | :--- |
| `CREADO` | `Nuevo entrenamiento Sub-12 · mié 4 nov, 4:00 p. m.` |
| `MODIFICADO` | `Cambio en el entrenamiento Sub-12 · mié 4 nov, 4:00 p. m.` (fecha y hora nuevas) |
| `CANCELADO` | `Entrenamiento cancelado Sub-12 · mié 4 nov, 4:00 p. m.` |

Todos los textos de la petición (nombre, categoría, lugar, entrenador, motivo) se escapan antes de insertarlos en el HTML.

**Guardar el aviso.** Si el aviso se guarda o no en la base de datos (ampliar `NotificacionPago` a una tabla `Notificacion` con tipo, o enviarlo sin guardarlo) es la decisión **D4** de [`diccionario-datos.md`](../sprint-1/diccionario-datos.md#5-decisiones-pendientes-de-la-ampliación-del-sprint-2). La toma el backend y no cambia este contrato: n8n solo envía.

### Evento `ENLACE_CONTRASENA`

El administrador registra un entrenador (o le reenvía el enlace). Después de guardar el alta, el backend pide un correo con el enlace para que la persona cree **su propia** contraseña: no hay contraseña temporal (regla 9). Un solo destinatario.

```json
{
  "evento": "ENLACE_CONTRASENA",
  "datos": {
    "plantilla": "BIENVENIDA_ENTRENADOR",
    "correo": "entrenador@ejemplo.com",
    "nombre": "Carlos",
    "enlace": "https://futdata.example/crear-contrasena?token=…",
    "vigenciaHoras": 1
  }
}
```

| Campo de `datos` | Tipo | Regla |
| :--- | :--- | :--- |
| `plantilla` | string | Obligatorio. Una de la lista permitida del nodo `Validar datos ENLACE_CONTRASENA`; hoy solo `BIENVENIDA_ENTRENADOR` (el bloque 2B agregará `BIENVENIDA_ACUDIENTE`) |
| `correo` | string | Obligatorio. Correo con formato válido, máximo 254 caracteres |
| `nombre` | string | Obligatorio, no vacío, máximo 120 caracteres. Se usa en el saludo (n8n lo escapa antes de insertarlo en el HTML) |
| `enlace` | string | Obligatorio. Empieza por `http://` o `https://`, sin espacios, máximo 2000 caracteres. Es `{FRONTEND_URL}/crear-contrasena?token={hashed_token}`. **Secreto**: quien lo tenga puede crear la contraseña mientras no venza ni se use |
| `vigenciaHoras` | número | Obligatorio, mayor que 0. Debe coincidir con `CuentasDeAcceso.VIGENCIA_ENLACE_HORAS` (1) y con la vigencia de Supabase Auth (`mailer_otp_exp = 3600`) |

**Correo.** Plantilla `n8n/plantillas/bienvenida-entrenador.html` (y `.txt`), asunto "Bienvenido a FutData: crea tu contraseña". Tiene un botón "Crear mi contraseña", el enlace en texto por si el botón no funciona y la línea "Este enlace vence en 1 hora; si venció, pide al administrador que te lo reenvíe." (n8n escribe "1 hora" o "N horas" según `vigenciaHoras`). El nombre y el enlace se escapan antes de insertarlos en el HTML.

### Evento `RECORDATORIO_PAGO`

El cron diario (o el administrador) crea con anticipación el cobro de la mensualidad del ciclo de un deportista (regla 14). Después de crearlo, el backend envía **un `POST` por acudiente vinculado que tenga correo**. No se avisa de la inscripción ni del uniforme. Los campos van planos dentro de `datos`:

```json
{
  "evento": "RECORDATORIO_PAGO",
  "datos": {
    "correo": "acudiente@ejemplo.com",
    "nombreAcudiente": "Marta Gómez",
    "nombreDeportista": "Juan Gómez",
    "concepto": "Mensualidad noviembre 2026",
    "monto": 90000,
    "fechaVencimiento": "2026-11-30",
    "diasRestantes": 3,
    "enlacePagos": "https://futdata.example/acudiente/pagos"
  }
}
```

| Campo de `datos` | Tipo | Regla |
| :--- | :--- | :--- |
| `correo` | string | Obligatorio. Correo con formato válido, máximo 254 caracteres |
| `nombreAcudiente` | string | Obligatorio, no vacío, de una línea, máximo 120 caracteres. Se usa en el saludo |
| `nombreDeportista` | string | Obligatorio, no vacío, de una línea, máximo 120 caracteres |
| `concepto` | string | Obligatorio, no vacío, de una línea, máximo 120 caracteres |
| `monto` | número | Obligatorio. Pesos **enteros** (sin centavos), mayor que 0 |
| `fechaVencimiento` | string | Obligatorio. Fecha real con formato `AAAA-MM-DD` |
| `diasRestantes` | número | Obligatorio. Entero de 0 a 60 (el correo dice "hoy", "mañana" o "en N días") |
| `enlacePagos` | string | Obligatorio. Empieza por `http://` o `https://`, sin espacios, máximo 2000 caracteres. Es `{FRONTEND_URL}/acudiente/pagos`, sin datos secretos |

El correo (asunto "Recordatorio: la mensualidad de {nombreDeportista} vence {hoy, mañana o en N días}") escribe el monto como `$90.000` y la fecha como `DD/MM/AAAA`. Todos los textos se escapan antes de insertarlos en el HTML. Respuestas: ver [Respuestas de `RECORDATORIO_PAGO` y `DOCUMENTO_RECHAZADO`](#respuestas-de-recordatorio_pago-y-documento_rechazado).

### Evento `DOCUMENTO_RECHAZADO`

El administrador rechaza un documento de la inscripción de un deportista y escribe el motivo (regla 8, bloque 3). **Después de confirmar el rechazo** (nunca dentro de la transacción), el backend envía **un `POST` por acudiente vinculado al deportista que tenga un correo válido**, sin correos repetidos. Aprobar un documento no envía nada. Los campos van planos dentro de `datos`:

```json
{
  "evento": "DOCUMENTO_RECHAZADO",
  "datos": {
    "correo": "acudiente@ejemplo.com",
    "nombreAcudiente": "Marta Gómez",
    "nombreDeportista": "Juan Gómez",
    "documento": "Documento de identidad",
    "comentario": "La imagen está borrosa.\nVuelve a subirla con mejor luz.",
    "enlaceDocumentos": "https://futdata.example/acudiente/documentos"
  }
}
```

| Campo de `datos` | Tipo | Regla |
| :--- | :--- | :--- |
| `correo` | string | Obligatorio. Correo con formato válido (`^[^@\s]+@[^@\s]+\.[^@\s]+$`), máximo 254 caracteres |
| `nombreAcudiente` | string | Obligatorio, no vacío, de **una línea** (sin saltos de línea ni otros caracteres de control), máximo 120 caracteres. Si el acudiente no tiene nombre, el backend manda `Acudiente` |
| `nombreDeportista` | string | Obligatorio, no vacío, de una línea, máximo 120 caracteres. Nombre y apellido. Si llega vacío, el backend manda `tu deportista` |
| `documento` | string | Obligatorio, no vacío, de una línea, máximo 120 caracteres. La **etiqueta legible** del tipo, no el código: `Documento de identidad`, `Certificado de estudio`, `Formulario de inscripción`, `Habeas Data`, `Paz y salvo`, `Carta de renuncia` o `Traspaso nacional` |
| `comentario` | string | Obligatorio, no vacío, **máximo 300 caracteres**. Es el motivo que escribió el administrador, ya recortado. **Admite saltos de línea: `\n`, `\r` y `\t`** (es el único campo que los admite); cualquier otro carácter de control (`U+0000` a `U+001F`, `U+007F`) es inválido |
| `enlaceDocumentos` | string | Obligatorio. Empieza por `http://` o `https://`, sin espacios, máximo 2000 caracteres. Es `{FRONTEND_URL}/acudiente/documentos`, **sin datos secretos**. Si el enlace pasara de 2000 caracteres el backend no lo recorta (quedaría roto): no envía el aviso |

**Destinatarios.** El backend los resuelve (los acudientes vinculados al deportista con correo válido, sin repetir correos aunque cambien las mayúsculas); n8n **no consulta la base de datos** ni recibe una lista: cada petición lleva un solo `correo`. Si una llamada falla, el backend no envía las demás.

**Correo.** Plantilla `n8n/plantillas/documento-rechazado.html` (y `.txt`); el nodo `Enviar aviso de documento rechazado` del flujo de Cloud tiene una copia. Todos los textos de la petición (nombres, documento, comentario, enlace) se escapan antes de insertarlos en el HTML; los saltos de línea del comentario se muestran como saltos de línea.

**Datos personales.** Los nombres, el comentario y el correo son datos de menores y de sus familias: ni el backend ni n8n los escriben en sus registros (ver *Seguridad y registros*). El `detalle` de un `400` nunca los incluye.

## Respuestas

Todas las respuestas son JSON.

### Respuestas de `CODIGO_2FA`

| Código | Cuándo | Cuerpo |
| :--- | :--- | :--- |
| `202` | Los datos son válidos. n8n respondió de inmediato y envía el correo después | `{"aceptado": true}` |
| `400` | Falta `evento` o `datos` | `{"enviado": false, "error": "El cuerpo debe tener evento y datos"}` |
| `400` | El evento no está soportado | `{"enviado": false, "error": "Evento no soportado"}` |
| `400` | Faltan campos de `datos` o tienen formato inválido | `{"enviado": false, "error": "Faltan campos o tienen un formato inválido"}` |
| `401` | Falta la cabecera `X-FutData-Token` o su valor no coincide | `{"enviado": false, "error": "Token inválido"}` |

Otros códigos posibles que no genera el flujo, sino n8n o la red:

- `404`: el flujo no está publicado (activo) o la ruta está mal escrita.
- `5xx` o tiempo de espera agotado: n8n no está disponible.

**En este evento n8n no responde `502`.** Antes era síncrono (enviaba y después respondía `200` o `502`), pero el envío por SMTP tarda ~3 s y, con el túnel, el 2FA tardaba ~3,7 s de punta a punta contra un tiempo de espera de 5 s del backend. Ahora n8n valida el token, la estructura y los datos (esas fallas siguen respondiendo `401` y `400`), responde `202` y después envía, igual que `CAMBIO_ENTRENAMIENTO`.

> **Para el backend: `202` significa "recibido", no "entregado".** Si el SMTP falla después del `202`:
>
> - el backend ya respondió normalmente al front (`202` en `/2fa/codigo`, `200` en `/2fa/reenviar`) y el código **queda guardado**;
> - el usuario **no recibe el correo** y debe usar "Reenviar código", disponible al minuto (`INTERVALO_REENVIO`);
> - la falla **solo queda en el [registro de fallas](#registro-de-fallas-en-n8n) de n8n**, con el correo enmascarado y sin el código. El backend no se entera.
>
> El front solo recibe un error si n8n no acepta la petición: `503` si n8n no responde, da timeout o responde 5xx; `500` si n8n rechaza la petición (400 o 401: error de configuración o de contrato). En ambos casos el código no queda guardado.

`N8nClient` acepta `200` y `202` y no lee el campo `enviado`, así que funciona con el flujo anterior y con el nuevo: no importa si se despliega primero el backend o el flujo.

### Respuestas de `CAMBIO_ENTRENAMIENTO`

| Código | Cuándo | Cuerpo |
| :--- | :--- | :--- |
| `202` | Los datos son válidos. n8n respondió de inmediato y sigue enviando los correos en segundo plano | `{"aceptado": true, "destinatarios": 3}` (`destinatarios`: correos que se van a enviar, ya sin duplicados ni inválidos) |
| `400` | Falta `evento` o `datos`, o el evento no está soportado | Igual que en `CODIGO_2FA` (`{"enviado": false, "error": ...}`) |
| `400` | Faltan campos de `datos`, tienen formato inválido, `destinatarios` está vacío o tiene más de 200 elementos, o no queda ningún correo válido | `{"aceptado": false, "error": "Faltan campos o tienen un formato inválido", "detalle": "destinatarios debe tener entre 1 y 200 elementos"}` |
| `401` | Falta la cabecera `X-FutData-Token` o su valor no coincide | `{"enviado": false, "error": "Token inválido"}` |

`detalle` explica el primer problema encontrado, para depurar; nunca incluye correos ni nombres. Las respuestas comunes a todos los eventos (`401` y los dos primeros `400`) conservan el campo `enviado`: el backend debe decidir por el **código HTTP**, no por el nombre del campo.

**En este evento n8n no responde `502`.** El backend espera como máximo 5 s (ver [Cómo debe llamarlo `N8nClient`](#cómo-debe-llamarlo-n8nclient-spring-boot)), y n8n envía los correos uno por uno: con Gmail cada envío tarda alrededor de 1 a 2 s, así que un aviso a 30 personas tarda más de 30 s, y si el SMTP falla cada intento puede tardar ~20 s o más antes de rendirse. Esperar a que terminen todos los envíos haría que el backend siempre agotara su tiempo de espera. Por eso n8n valida, responde `202` y después envía. Cada envío fallido se escribe en el [registro de fallas](#registro-de-fallas-en-n8n) con el evento `CAMBIO_ENTRENAMIENTO` y el correo enmascarado, y una falla con un destinatario no frena a los demás.

> **Para el backend: `202` significa "recibido", no "entregado".** Confirma que n8n aceptó la petición y cuántos correos va a intentar enviar; no garantiza que el SMTP los haya aceptado ni que lleguen. Las fallas de envío solo quedan en el registro de fallas de n8n.

### Respuestas de `RECORDATORIO_PAGO` y `DOCUMENTO_RECHAZADO`

Los dos son asíncronos, igual que `CAMBIO_ENTRENAMIENTO`: n8n valida el token, la estructura y los datos, responde `202` y después envía el correo. Cada petición es de **un solo destinatario**.

| Código | Cuándo | Cuerpo |
| :--- | :--- | :--- |
| `202` | Los datos son válidos. n8n respondió de inmediato y envía el correo después | `{"aceptado": true}` |
| `400` | Falta `evento` o `datos`, o el evento no está soportado | Igual que en `CODIGO_2FA` (`{"enviado": false, "error": ...}`) |
| `400` | Faltan campos de `datos` o tienen un formato inválido | `{"aceptado": false, "error": "Faltan campos o tienen un formato inválido", "detalle": "comentario es obligatorio (máximo 300 caracteres)"}` |
| `401` | Falta la cabecera `X-FutData-Token` o su valor no coincide | `{"enviado": false, "error": "Token inválido"}` |

`detalle` explica el primer problema encontrado y nunca incluye el correo, los nombres, el comentario ni el enlace. **En estos eventos n8n no responde `502`.** Un `202` significa "recibido", no "entregado": si el SMTP falla después, solo queda en el [registro de fallas](#registro-de-fallas-en-n8n) de n8n. Para el backend, un `400` (contrato roto), un `401` (token mal configurado), un tiempo de espera o un error de red cuentan igual: el aviso falló. Mientras el flujo de n8n Cloud no tenga `DOCUMENTO_RECHAZADO`, la respuesta es `400` "Evento no soportado" y el rechazo devuelve `avisoEnviado: false`.

### Respuestas de `ENLACE_CONTRASENA`

| Código | Cuándo | Cuerpo |
| :--- | :--- | :--- |
| `200` | Los datos son válidos y el SMTP aceptó el correo | `{"enviado": true}` |
| `400` | Falta `evento` o `datos`, o el evento no está soportado | Igual que en `CODIGO_2FA` (`{"enviado": false, "error": ...}`) |
| `400` | Faltan campos de `datos`, tienen formato inválido o la plantilla no está permitida | `{"enviado": false, "error": "Faltan campos o tienen un formato inválido", "detalle": "enlace debe empezar por http:// o https:// y no tener espacios"}` |
| `401` | Falta la cabecera `X-FutData-Token` o su valor no coincide | `{"enviado": false, "error": "Token inválido"}` |
| `502` | El SMTP no aceptó el correo. Antes de responder, n8n escribe la línea en el [registro de fallas](#registro-de-fallas-en-n8n) | `{"enviado": false, "error": "No se pudo enviar el correo"}` |

`detalle` explica el primer problema encontrado y nunca incluye el correo, el nombre ni el enlace.

**Este evento es síncrono** (no usa el `202`): n8n envía y después responde, porque el backend necesita saber si el correo salió para responder `correoEnviado` en el alta o un error en el reenvío. El envío tarda ~3 s; por eso `N8nClient` le da **15 s** de espera (los demás eventos, 5 s). Con 5 s, un tiempo agotado con el correo ya enviado haría pensar al administrador que no salió, y el reenvío generaría un enlace nuevo que invalida el que sí llegó.

## Cómo crece el contrato

Los eventos nuevos **no cambian el endpoint ni el formato del cuerpo**. Para agregar uno (por ejemplo `PAGO_REGISTRADO` o `RECORDATORIO_PAGO`):

1. Definir aquí la tabla de campos de `datos` del evento.
2. En n8n, agregar una regla al nodo Switch `Evento` con el nombre del evento. Cada regla es una salida nueva.
3. Conectar esa salida a su propia validación de `datos`, su nodo de envío y sus nodos `Respond to Webhook`. Hay dos modelos:
   - **Síncrono** (un solo correo, cuando el backend necesita saber si salió; como `ENLACE_CONTRASENA`): enviar y luego responder `200` con su propio nodo; una falla responde `502`. No hay que tocar `¿Responder 502?`.
   - **Asíncrono** (como `CODIGO_2FA` y `CAMBIO_ENTRENAMIENTO`): responder `202` apenas se validan los datos y después enviar. Agregar el evento a la condición del nodo `¿Responder 502?` para que una falla no intente responder otra vez.

   Los nodos `¿Responder 502?` y `Responder 502` son de los eventos síncronos (hoy `ENLACE_CONTRASENA`). `¿Responder 502?` solo deja pasar los eventos que no son asíncronos: en el JSON de Cloud del repositorio excluye `CAMBIO_ENTRENAMIENTO`, `CODIGO_2FA`, `RECORDATORIO_PAGO` y `DOCUMENTO_RECHAZADO`. El flujo publicado hoy en n8n Cloud solo excluye los dos primeros hasta que se importe y publique el JSON del repositorio; mientras tanto, si el envío de un recordatorio falla después del `202`, el flujo intentaría responder otra vez.

   En ambos casos la salida de error del nodo de envío va a `Armar registro de falla`, que es común a todos los eventos. Ese nodo toma el correo del ítem que falló (campo `correo`) o, si no lo tiene, de `datos.correo`.
4. Agregar la plantilla en `n8n/plantillas/` y el caso en los scripts de prueba.

Mientras un evento no tenga rama, cae en la salida por defecto del Switch y responde `400 Evento no soportado`, así que el backend puede empezar a enviarlo sin romper nada.

## Cómo debe llamarlo `N8nClient` (Spring Boot)

Referencia para el backend. La implementación (`INotificador` y `N8nClient`, tarea 340), con un ejemplo de uso de cada evento y de cada excepción, está en [`backend/README.md`, sección "Notificaciones (n8n)"](../../../backend/README.md#notificaciones-n8n).

**Configuración** (`application.yml`, con valores desde variables de entorno):

```yaml
futdata:
  n8n:
    base-url: ${N8N_BASE_URL}
    webhook-token: ${N8N_WEBHOOK_TOKEN}
    timeout: 5s
    timeout-enlace: ${N8N_TIMEOUT_ENLACE:15s}   # solo ENLACE_CONTRASENA (síncrono)
```

**Cliente HTTP:** usar `RestClient` (Spring Boot 3.2+, síncrono, suficiente porque el login espera la respuesta) o `WebClient` si el resto del backend ya es reactivo. En ambos casos:

- `POST` a `{base-url}/webhook/futdata/notificaciones` con `Content-Type: application/json` y la cabecera `X-FutData-Token`.
- Cuerpo: `Map.of("evento", evento, "datos", datos)` serializado por Jackson. `codigo` como `String`.
- **Timeout de 5 s** de conexión y de lectura (por ejemplo, con `SimpleClientHttpRequestFactory` o `JdkClientHttpRequestFactory` y `setReadTimeout(Duration.ofSeconds(5))`). Un correo no puede bloquear el inicio de sesión indefinidamente. **Excepción: `ENLACE_CONTRASENA` usa 15 s** (`timeout-enlace`), porque es síncrono (ver [Respuestas de `ENLACE_CONTRASENA`](#respuestas-de-enlace_contrasena)).
- Sin reintentos automáticos: el usuario ya tiene el botón "Reenviar código", que respeta `INTERVALO_REENVIO = 1 min`.

**Manejo de respuestas de `CODIGO_2FA`:**

| Respuesta | Qué hace el backend |
| :--- | :--- |
| `202` (o `200` del flujo anterior) | Continúa y guarda el código; responde `Codigo2FAResponseDTO` con el correo enmascarado. **No** garantiza que el correo llegue (ver [Respuestas de `CODIGO_2FA`](#respuestas-de-codigo_2fa)) |
| `400` | Error de programación (contrato roto): registrar el evento y el mensaje de error, lanzar excepción interna. El código no queda guardado y el front recibe `500` |
| `401` | Configuración incorrecta del token: registrar como error de configuración. El código no queda guardado y el front recibe `500` |
| Timeout, error de red o 5xx | Informar al usuario que no se pudo enviar el código y que lo intente de nuevo (`503`). El código no queda guardado. n8n pudo no registrar nada, así que el registro del backend es el único |

**Manejo de respuestas de `CAMBIO_ENTRENAMIENTO`:**

El aviso se envía **después** de guardar el cambio del entrenamiento y su resultado nunca deshace ese cambio: si el aviso falla, el entrenamiento queda creado, modificado o cancelado igual. Se recomienda llamarlo al confirmar la transacción (por ejemplo, con `@TransactionalEventListener(phase = AFTER_COMMIT)`), para no avisar de un cambio que luego se revierte.

| Respuesta | Qué hace el backend |
| :--- | :--- |
| `202` | Continúa. Puede registrar en su auditoría el evento, el id del entrenamiento y `destinatarios`. **No** debe decirle al usuario que los correos "llegaron", solo que se enviará el aviso |
| `400` | Error de programación (contrato roto): registrar el evento y `detalle`. El cambio del entrenamiento ya está guardado; informar al usuario que no se pudo enviar el aviso |
| `401` | Configuración incorrecta del token: registrar como error de configuración |
| Timeout u otro error | Registrar en la auditoría e informar al usuario que no se pudo confirmar el envío del aviso. **No reintentar automáticamente**: si n8n sí recibió la petición y solo se demoró la respuesta, un reintento enviaría el aviso dos veces |

Se usa el mismo timeout de 5 s: n8n responde `202` en milisegundos porque solo valida antes de responder.

**Manejo de respuestas de `DOCUMENTO_RECHAZADO`:**

El rechazo ya está guardado cuando se pide el correo; el resultado del envío nunca lo deshace. El servicio resume el resultado en `avisoEnviado` (`true`, `false` o `null`; ver `backend/README.md`, sección "Documentos de la inscripción").

| Respuesta | Qué hace el backend |
| :--- | :--- |
| `202` (o `200`) | Continúa con el siguiente acudiente. **No** debe decirle al usuario que los correos "llegaron" |
| `400`, `401`, timeout, error de red u otro código | Registra solo el evento, el tipo de excepción y cuántos se enviaron antes; **no envía los demás** y el rechazo responde `200` con `avisoEnviado: false`. Sin reintentos |
| Sin acudientes con correo válido | No llama a n8n; `avisoEnviado: null` |

**Manejo de respuestas de `ENLACE_CONTRASENA`:**

El alta del entrenador ya está guardada cuando se pide el correo; el resultado del envío nunca la deshace.

| Respuesta | En el alta (`POST /api/entrenadores`) | En el reenvío (`POST /api/entrenadores/{id}/reenviar-enlace`) |
| :--- | :--- | :--- |
| `200` | `201` con `correoEnviado: true` | `204` |
| `502`, timeout, error de red u otro 5xx | `201` con `correoEnviado: false`; el administrador usa "Reenviar enlace" | `503` "No se pudo enviar el correo" (el enlace ya se generó y el cupo de un reenvío por minuto queda gastado) |
| `400` o `401` | `201` con `correoEnviado: false` y error en el log (contrato roto o token mal configurado) | `500` |

Sin reintentos automáticos: un reintento generaría otro enlace e invalidaría el anterior.

**Seguridad y registros:**

- **Nunca registrar el código** (ni el cuerpo completo de la petición) en logs, trazas de excepción ni en `AuditoriaLogger`. Registrar solo el evento, el `usuarioId` y el código HTTP de la respuesta. Si se activa el log de peticiones HTTP del cliente, debe excluir esta llamada.
- Tampoco registrar el valor de `X-FutData-Token`.
- El correo puede registrarse enmascarado (`j***@ejemplo.com`) si hace falta para soporte.
- En `CAMBIO_ENTRENAMIENTO`, no registrar la lista de `destinatarios` (correos y nombres de deportistas, muchos menores de edad); basta con el id del entrenamiento, `tipoCambio` y el número de destinatarios.
- En `DOCUMENTO_RECHAZADO`, no registrar el correo, los nombres, el `comentario` ni el enlace: basta con el evento, el tipo de excepción y los conteos (cuántos se enviaron antes de la falla y cuántos destinatarios había).
- En `ENLACE_CONTRASENA`, **nunca registrar el enlace** ni la parte `token=...`: con él se puede crear la contraseña de otra persona. Tampoco devolverlo en una respuesta de la API.
- **Ante un `502` (eventos síncronos), un timeout o un error de red, registrar también la falla en la auditoría del backend** (`AuditoriaLogger`) cuando exista: evento, `usuarioId`, código HTTP y fecha. El log de n8n no reemplaza esa auditoría. Está en otro equipo, no conoce al usuario y no ve los timeouts ni las fallas de red, que solo ve el backend.

**Pruebas:** con `MockRestServiceServer` (para `RestClient`) o un servidor simulado, verificar la cabecera, el cuerpo y el manejo de cada código de respuesta, y que el código no aparezca en los logs capturados.

## Datos que guarda n8n

El flujo está configurado para no guardar ejecuciones (exitosas, fallidas ni manuales). Aun así, n8n 2.x marca la ejecución como borrada y la elimina de su base de datos después de un búfer; `n8n/docker-compose.yml` reduce ese búfer a 0 y revisa cada minuto, de modo que los datos de una ejecución (que incluyen el código o el enlace) no permanecen más de ~1 minuto, por debajo de los 10 minutos de vigencia del código y de la hora del enlace. Como consecuencia, los fallos del flujo no quedan en el historial de n8n; para las fallas de envío está el registro de la sección siguiente.

## Registro de fallas en n8n

Cuando el SMTP falla, el flujo agrega una línea en formato JSON Lines a `/opt/futdata/logs/fallas-notificaciones.jsonl` (en el repo, `n8n/logs/`, ignorado por Git). En `CODIGO_2FA`, que ya respondió `202`, escribe una línea con el correo enmascarado; en `CAMBIO_ENTRENAMIENTO`, también después del `202`, una línea por cada destinatario al que no se pudo enviar. En un evento síncrono (`ENLACE_CONTRASENA`) la escribe antes de responder `502`. No depende del backend ni de la base de datos.

```json
{"fecha":"2026-10-28T09:42:06.626-05:00","evento":"CODIGO_2FA","correoEnmascarado":"o***3@gmail.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"4"}
{"fecha":"2026-11-02T18:05:11.203-05:00","evento":"CAMBIO_ENTRENAMIENTO","correoEnmascarado":"m***a@ejemplo.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"57"}
```

- Campos: `fecha` (ISO 8601, `America/Bogota`), `evento`, `correoEnmascarado`, `error` (mensaje del SMTP, máximo 500 caracteres, con las URL y cualquier `token=...` reemplazados por `[enlace]`, los correos por `[correo]` y los números de 6 dígitos por `[código]`) e `idEjecucion`.
- **Nunca** contiene el código, el enlace (ni `token=...`), el nombre, el correo completo, el token del webhook ni el cuerpo de la petición.
- Si escribir el registro falla, un evento síncrono responde `502` igual: para el backend, un `502` siempre significa "falló el envío", haya quedado o no la línea. En los eventos asíncronos la respuesta `202` ya salió y el log no la afecta.
- El registro de n8n complementa la auditoría del backend; no la reemplaza (ver *Seguridad y registros*).

Detalles, consulta del archivo y propuesta de tabla `fallas_notificacion` para Supabase: [`n8n/README.md`, sección "Registro de fallas"](../../../n8n/README.md#registro-de-fallas).
