# Contrato del webhook de notificaciones (n8n)

**Sprint:** Sprint 2  
**Historia:** HU-036 · Enviar código 2FA por correo con n8n (AB#51)  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club

## Objetivo

Definir cómo el backend (Spring Boot) le pide a n8n que envíe una notificación. El contrato es el mismo para todos los eventos; esta historia implementa el primero: `CODIGO_2FA`.

## Reparto de responsabilidades

| Parte | Hace | No hace |
| :--- | :--- | :--- |
| Backend (`AutenticacionServiceImpl`) | Genera el código de 6 dígitos (`GeneradorCodigo`), guarda solo su hash con vigencia de 10 min (`ReglasVerificacion.VIGENCIA_CODIGO`), valida intentos y reenvíos, y llama a `INotificador.notificar(evento, datos)` | Enviar correos |
| `N8nClient` (implementa `INotificador`) | Hace el `POST` al webhook de n8n con el secreto compartido | Decidir el contenido del correo |
| n8n (flujo `FutData · Notificaciones`) | Valida la petición, arma el correo con la plantilla y lo envía por SMTP con TLS | Generar, guardar ni validar códigos |

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

## Respuestas

Todas las respuestas son JSON.

| Código | Cuándo | Cuerpo |
| :--- | :--- | :--- |
| `200` | El correo se entregó al servidor SMTP | `{"enviado": true}` |
| `400` | Falta `evento` o `datos` | `{"enviado": false, "error": "El cuerpo debe tener evento y datos"}` |
| `400` | El evento no está soportado | `{"enviado": false, "error": "Evento no soportado"}` |
| `400` | Faltan campos de `datos` o tienen formato inválido | `{"enviado": false, "error": "Faltan campos o tienen un formato inválido"}` |
| `401` | Falta la cabecera `X-FutData-Token` o su valor no coincide | `{"enviado": false, "error": "Token inválido"}` |
| `502` | Falló el envío por SMTP. n8n ya registró la falla (ver [Registro de fallas en n8n](#registro-de-fallas-en-n8n)) | `{"enviado": false, "error": "No se pudo enviar el correo"}` |

Otros códigos posibles que no genera el flujo, sino n8n o la red:

- `404`: el flujo no está publicado (activo) o la ruta está mal escrita.
- `5xx` distinto de `502` o tiempo de espera agotado: n8n no está disponible.

`200` significa que el servidor SMTP aceptó el mensaje, no que haya llegado a la bandeja del usuario.

## Cómo crece el contrato

Los eventos nuevos **no cambian el endpoint ni el formato del cuerpo**. Para agregar uno (por ejemplo `PAGO_REGISTRADO` o `RECORDATORIO_PAGO`):

1. Definir aquí la tabla de campos de `datos` del evento.
2. En n8n, agregar una regla al nodo Switch `Evento` con el nombre del evento. Cada regla es una salida nueva.
3. Conectar esa salida a su propia validación de `datos`, su nodo de envío y sus nodos `Respond to Webhook` (`200`, `400`), igual que la rama de `CODIGO_2FA`. La salida de error del nodo de envío va a `Armar registro de falla`, que es común a todos los eventos y termina en `Responder 502`.
4. Agregar la plantilla en `n8n/plantillas/` y el caso en los scripts de prueba.

Mientras un evento no tenga rama, cae en la salida por defecto del Switch y responde `400 Evento no soportado`, así que el backend puede empezar a enviarlo sin romper nada.

## Cómo debe llamarlo `N8nClient` (Spring Boot)

Referencia para cuando se implemente el backend. No hay código Java en esta historia.

**Configuración** (`application.yml`, con valores desde variables de entorno):

```yaml
futdata:
  n8n:
    base-url: ${N8N_BASE_URL}
    webhook-token: ${N8N_WEBHOOK_TOKEN}
```

**Cliente HTTP:** usar `RestClient` (Spring Boot 3.2+, síncrono, suficiente porque el login espera la respuesta) o `WebClient` si el resto del backend ya es reactivo. En ambos casos:

- `POST` a `{base-url}/webhook/futdata/notificaciones` con `Content-Type: application/json` y la cabecera `X-FutData-Token`.
- Cuerpo: `Map.of("evento", evento, "datos", datos)` serializado por Jackson. `codigo` como `String`.
- **Timeout de 5 s** de conexión y de lectura (por ejemplo, con `SimpleClientHttpRequestFactory` o `JdkClientHttpRequestFactory` y `setReadTimeout(Duration.ofSeconds(5))`). Un correo no puede bloquear el inicio de sesión indefinidamente.
- Sin reintentos automáticos: el usuario ya tiene el botón "Reenviar código", que respeta `INTERVALO_REENVIO = 1 min`.

**Manejo de respuestas:**

| Respuesta | Qué hace el backend |
| :--- | :--- |
| `200` | Continúa; responde `VerificacionDTO` con el correo enmascarado |
| `400` | Error de programación (contrato roto): registrar el evento y el mensaje de error, lanzar excepción interna |
| `401` | Configuración incorrecta del token: registrar como error de configuración |
| `502` | Informar al usuario que no se pudo enviar el código y que lo intente de nuevo. n8n ya registró la falla; el backend la registra además en su auditoría (ver abajo) |
| Timeout u otro error | Igual que `502`. Aquí n8n pudo no registrar nada, así que el registro del backend es el único |

**Seguridad y registros:**

- **Nunca registrar el código** (ni el cuerpo completo de la petición) en logs, trazas de excepción ni en `AuditoriaLogger`. Registrar solo el evento, el `usuarioId` y el código HTTP de la respuesta. Si se activa el log de peticiones HTTP del cliente, debe excluir esta llamada.
- Tampoco registrar el valor de `X-FutData-Token`.
- El correo puede registrarse enmascarado (`j***@ejemplo.com`) si hace falta para soporte.
- **Ante un `502`, registrar también la falla en la auditoría del backend** (`AuditoriaLogger`) cuando exista: evento, `usuarioId`, código HTTP y fecha. El log de n8n no reemplaza esa auditoría. Está en otro equipo, no conoce al usuario y no ve los timeouts ni las fallas de red, que solo ve el backend.

**Pruebas:** con `MockRestServiceServer` (para `RestClient`) o un servidor simulado, verificar la cabecera, el cuerpo y el manejo de cada código de respuesta, y que el código no aparezca en los logs capturados.

## Datos que guarda n8n

El flujo está configurado para no guardar ejecuciones (exitosas, fallidas ni manuales). Aun así, n8n 2.x marca la ejecución como borrada y la elimina de su base de datos después de un búfer; `n8n/docker-compose.yml` reduce ese búfer a 0 y revisa cada minuto, de modo que los datos de una ejecución (que incluyen el código) no permanecen más de ~1 minuto, por debajo de los 10 minutos de vigencia del código. Como consecuencia, los fallos del flujo no quedan en el historial de n8n; para las fallas de envío está el registro de la sección siguiente.

## Registro de fallas en n8n

Cuando el SMTP falla, antes de responder `502` el flujo agrega una línea en formato JSON Lines a `/opt/futdata/logs/fallas-notificaciones.jsonl` (en el repo, `n8n/logs/`, ignorado por Git). No depende del backend ni de la base de datos.

```json
{"fecha":"2026-10-28T09:42:06.626-05:00","evento":"CODIGO_2FA","correoEnmascarado":"o***3@gmail.com","error":"connect ECONNREFUSED 127.0.0.1:2525","idEjecucion":"4"}
```

- Campos: `fecha` (ISO 8601, `America/Bogota`), `evento`, `correoEnmascarado`, `error` (mensaje del SMTP, máximo 500 caracteres, con correos y números de 6 dígitos reemplazados) e `idEjecucion`.
- **Nunca** contiene el código, el nombre, el correo completo, el token ni el cuerpo de la petición.
- Si escribir el registro falla, el flujo responde `502` igual: para el backend, un `502` siempre significa "falló el envío", haya quedado o no la línea.
- El registro de n8n complementa la auditoría del backend; no la reemplaza (ver *Seguridad y registros*).

Detalles, consulta del archivo y propuesta de tabla `fallas_notificacion` para Supabase: [`n8n/README.md`, sección "Registro de fallas"](../../../n8n/README.md#registro-de-fallas).
