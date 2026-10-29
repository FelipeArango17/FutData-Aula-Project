# FutData · Información y flujo inicial 28 Octubre 2026

Guía de referencia para ponerse al día con el estado actual del proyecto, levantar el entorno local.

> Esta guía describe el estado de `develop` al 28 de octubre de 2026. No contiene credenciales, tokens ni contraseñas. Los archivos `.env` son locales y nunca deben subirse al repositorio.

## ----------------------------------------------------------------------------------

## Servicio - Capa y su Dirección Exacta para pruebas del sistema

n8n --> Docker Activo - http://localhost:5678
Frontend --> http://localhost:5173
Backend --> http://localhost:8080

## Credenciales para pruebas del login

Acudiente / acudiente@pradofc.co
Entrenador / entrenador@pradofc.co
Tesorero / tesorero@pradofc.co
Administrador / administrador@pradofc.co

Contraseña para cualquier entidad: Demo2026!
Código de Verificación: 123456

## Nuevo Deportista (Vista Administrador) y /inscripcion

Nuevo Deportista --> http://localhost:5173/administrador/deportistas/nuevo
Pensada para el personal interno del club

/inscripcion --> http://localhost:5173/inscripcion
Pensada para un acudiente o usuario externo que todavía no tiene cuenta

## ----------------------------------------------------------------------------------

## 1. Estado actual del proyecto

FutData es un monorepo para la gestión integral de un club de fútbol formativo.

| Carpeta | Contenido |
|---|---|
| `frontend/` | Aplicación React con Vite y Tailwind CSS |
| `backend/` | API REST en Java 21 con Spring Boot y Maven |
| `n8n/` | Automatizaciones de correo ejecutadas en Docker |
| `docs/` | Contratos, decisiones, diagramas y documentación de diseño |

La rama de integración es `develop`. El trabajo diario debe partir de esta rama. `main` representa los releases y recibe cambios mediante Pull Request.

El árbol de trabajo debe estar limpio antes de comenzar:

```powershell
git status
git switch develop
git pull
```

## 2. Cambios funcionales incorporados recientemente

### 2.1 Registro y gestión de deportistas

Se incorporó:

- Registro de deportistas desde el panel administrativo.
- Validaciones compartidas para formularios.
- Listado de deportistas activos, inactivos o todos.
- Búsqueda por nombre o documento.
- Vista de tabla en pantallas amplias y tarjetas en pantallas pequeñas.
- Consulta de ficha individual.
- Edición de datos personales.
- Historial de cambios.
- Manejo de deportista inexistente y falta de permisos.

Rutas principales:

```text
/administrador/deportistas
/administrador/deportistas/nuevo
/administrador/deportistas/:id
```

Archivos relevantes:

- `frontend/src/pages/administrador/Deportistas.jsx`
- `frontend/src/pages/deportistas/DeportistaNuevo.jsx`
- `frontend/src/pages/deportistas/FichaDeportista.jsx`
- `frontend/src/services/deportistasService.js`
- `frontend/src/utils/validaciones.js`

### 2.2 Inscripción pública en línea

La nueva ruta pública es:

```text
/inscripcion
```

Permite diligenciar:

- Datos del deportista.
- Datos del acudiente.
- Correo y teléfono del acudiente.
- Autorización para el tratamiento de datos.

El formulario también incluye:

- Validaciones de campos.
- Confirmación al cancelar si hay datos escritos.
- Reintento ante una falla de red simulada.
- Respuesta genérica para no revelar si un documento ya estaba registrado.

Archivos relevantes:

- `frontend/src/pages/inscripcion/Inscripcion.jsx`
- `frontend/src/components/inscripcion/FormularioInscripcion.jsx`
- `frontend/src/services/inscripcionService.js`

Estado actual:

- La inscripción funciona con datos en memoria.
- Todavía no persiste en una API real.
- No envía correo de confirmación.
- El consentimiento utiliza la versión provisional `borrador`.
- Faltan la política definitiva de tratamiento de datos, Captcha o límite contra abuso y la revisión administrativa antes de activar la vinculación.

### 2.3 Registro manual de pagos

Rutas:

```text
/tesorero/pagos/registrar
/administrador/pagos/registrar
```

El flujo permite:

1. Buscar un deportista activo por nombre o documento.
2. Seleccionar el mes.
3. Consultar el cobro existente.
4. Ver el monto precargado cuando corresponde.
5. Escoger el método de pago manual:
   - Efectivo.
   - Transferencia.
6. Indicar fecha de pago.
7. Agregar referencia y nota interna opcionales.
8. Confirmar el registro.
9. Actualizar el estado de cuenta.

Se manejan, entre otros, estos errores:

- Pago ya registrado.
- Monto diferente al cobro.
- Deportista inexistente.
- Deportista inactivo.
- Falta de permisos.
- Falla de red.

Archivos relevantes:

- `frontend/src/pages/tesorero/RegistrarPago.jsx`
- `frontend/src/pages/tesorero/Pagos.jsx`
- `frontend/src/services/pagosService.js`

Limitaciones actuales:

- Los pagos están en memoria.
- Wompi todavía no está integrado.
- No existen abonos.
- No se puede anular ni corregir un pago.
- La tarifa mensual aún debe definirse.
- La descarga del estado de cuenta es un CSV provisional.
- El comprobante y la notificación al acudiente todavía no están implementados.

## 3. Backend disponible

El backend utiliza:

- Java 21.
- Spring Boot 3.5.
- Maven Wrapper.
- JDBC con `JdbcTemplate`/`NamedParameterJdbcTemplate`.
- HikariCP.
- PostgreSQL de Supabase.
- OpenAPI y Swagger UI.
- Arquitectura por capas: controller, DTO, fachada, service, reglas, DAO y persistencia.

Documentación principal:

- `backend/README.md`
- `backend/src/main/resources/application.yml`

### URLs locales

Cuando el backend esté levantado:

```text
http://localhost:8080/api/health
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

El endpoint `/api/health` puede responder `503` si no hay base de datos disponible. Esto no significa necesariamente que la aplicación no haya iniciado.

### Variables del backend

El perfil `local` usa valores por defecto para facilitar el desarrollo. Para conectar una base real se deben configurar, según corresponda:

```text
DB_URL
DB_USER
DB_PASSWORD
CORS_ORIGINS
N8N_BASE_URL
N8N_WEBHOOK_TOKEN
```

En Windows se recomienda crear `backend/.env` a partir del ejemplo disponible:

```powershell
cd backend
Copy-Item .env.example .env
```

Editar el archivo con valores locales. `backend/.env` está ignorado por Git.

> El backend todavía no tiene autenticación completa, validación de JWT, 2FA ni todos los módulos de negocio. No debe desplegarse públicamente todavía.

## 4. Requisitos del entorno

Instalar o tener disponible:

- Git.
- GitHub CLI (`gh`), si se trabajará con Pull Requests desde terminal.
- Node.js LTS.
- JDK 21.
- Docker Desktop.
- Un editor como Visual Studio Code o IntelliJ IDEA.
- Cliente PostgreSQL opcional, como DBeaver o pgAdmin.

Verificar:

```powershell
git --version
gh --version
node -v
java -version
docker --version
```

## 5. Levantamiento completo del entorno

El flujo local utiliza tres procesos:

1. n8n en Docker, puerto `5678`.
2. Backend Spring Boot, puerto `8080`.
3. Frontend Vite, normalmente puerto `5173`.

Se recomienda abrir tres terminales independientes.

### Terminal 1: levantar n8n

```powershell
cd n8n
Copy-Item .env.example .env
```

Completar `n8n/.env`:

```env
N8N_ENCRYPTION_KEY=
N8N_BASE_URL=http://localhost:5678
N8N_WEBHOOK_TOKEN=
SMTP_FROM="FutData <tu-cuenta@gmail.com>"
CORREO_PRUEBA=tu-cuenta@gmail.com
```

Generar valores aleatorios en PowerShell:

```powershell
-join ((1..32) | % { '{0:x2}' -f (Get-Random -Max 256) })
```

Usar valores distintos para `N8N_ENCRYPTION_KEY` y `N8N_WEBHOOK_TOKEN`.

La llave de cifrado debe conservarse. Si se pierde, las credenciales guardadas en n8n dejan de funcionar y deben crearse nuevamente.

Levantar el contenedor:

```powershell
docker compose up -d
docker compose ps
docker compose logs -f n8n
```

Abrir:

```text
http://localhost:5678
```

La primera vez se debe crear la cuenta propietaria local.

### Terminal 2: levantar el backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Comprobar:

```text
http://localhost:8080/api/health
```

Para compilar, ejecutar pruebas y generar el JAR:

```powershell
.\mvnw.cmd verify
```

El artefacto se genera en:

```text
backend/target/futdata-backend.jar
```

### Terminal 3: levantar el frontend

La primera vez:

```powershell
cd frontend
npm ci
```

Iniciar Vite:

```powershell
npm run dev
```

Abrir la URL mostrada por Vite, normalmente:

```text
http://localhost:5173
```

## 6. Configuración de n8n

### 6.1 Qué hace el flujo

El flujo `FutData · Notificaciones` recibe un `POST` del backend y:

1. Valida el token `X-FutData-Token`.
2. Valida la estructura de la petición.
3. Identifica el evento.
4. Valida sus datos.
5. Construye el correo.
6. Lo envía mediante SMTP.
7. Registra las fallas sin guardar datos sensibles.

n8n no genera ni valida códigos 2FA, no consulta la base de datos y no decide la lógica del negocio.

### 6.2 Eventos soportados

#### `CODIGO_2FA`

Envía el código de verificación a un usuario.

Respuestas esperadas:

| Estado | Significado |
|---|---|
| `200` | SMTP aceptó el correo |
| `400` | Datos inválidos |
| `401` | Token ausente o incorrecto |
| `502` | Falló el envío SMTP |

El código debe enviarse como texto para conservar ceros iniciales:

```json
{
  "evento": "CODIGO_2FA",
  "datos": {
    "correo": "jugador@ejemplo.com",
    "nombre": "Ana",
    "codigo": "012345",
    "vigenciaMinutos": 10
  }
}
```

#### `CAMBIO_ENTRENAMIENTO`

Envía avisos por creación, modificación o cancelación de un entrenamiento.

Tipos válidos:

```text
CREADO
MODIFICADO
CANCELADO
```

Responde `202` después de validar y continúa el envío de forma asíncrona. Cada destinatario recibe un correo individual.

Reglas importantes:

- Se aceptan entre 1 y 200 destinatarios.
- Los duplicados se eliminan sin distinguir mayúsculas y minúsculas.
- Los correos inválidos se descartan.
- Si no queda ningún correo válido, responde `400`.
- Los destinatarios no se exponen entre sí.
- La fecha usa `AAAA-MM-DD`.
- La hora usa `HH:mm`.
- La zona horaria del correo es `America/Bogota`.

Contrato completo:

`docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`

### 6.3 Crear la credencial SMTP

Para Gmail:

1. Activar la verificación en dos pasos.
2. Crear una contraseña de aplicación en Google.
3. En n8n crear una credencial de tipo SMTP.
4. Nombrarla exactamente:

```text
SMTP FutData
```

Configuración habitual:

| Campo | Valor |
|---|---|
| Host | `smtp.gmail.com` |
| Puerto | `465` |
| SSL/TLS | Activado |
| Usuario | Cuenta de Gmail |
| Contraseña | Contraseña de aplicación |

También se puede usar el puerto `587` con STARTTLS.

### 6.4 Importar y publicar el flujo

Desde la interfaz:

```text
Overview → Create → Workflow → ... → Import from File
```

Seleccionar:

```text
n8n/workflows/futdata-notificaciones.json
```

Después verificar que los nodos `Enviar correo 2FA` y `Enviar aviso de entrenamiento` utilicen `SMTP FutData`.

También se puede importar por CLI:

```powershell
docker compose exec n8n n8n import:workflow --input=/opt/futdata/workflows/futdata-notificaciones.json
```

Finalmente, abrir el flujo y pulsar **Publish**. El webhook queda disponible en:

```text
http://localhost:5678/webhook/futdata/notificaciones
```

Para actualizar un flujo ya importado, no se debe importar repetidamente desde la interfaz, porque se crean duplicados. Usar:

```powershell
docker compose up -d
docker compose exec n8n n8n import:workflow --input=/opt/futdata/workflows/futdata-notificaciones.json
docker compose exec n8n n8n publish:workflow --id=FutDataNotif036
docker compose restart n8n
```

## 7. Pruebas de n8n

Desde `n8n/`:

### Código 2FA

```powershell
powershell -File scripts/probar-2fa.ps1
```

Con PowerShell 7:

```powershell
pwsh -File scripts/probar-2fa.ps1
```

Se esperan tres casos correctos:

- Petición válida: `200`.
- Petición sin token: `401`.
- Evento inválido: `400`.

### Cambio de entrenamiento

```powershell
powershell -File scripts/probar-entrenamiento.ps1
```

Se prueban:

- `CREADO`.
- `MODIFICADO`.
- `CANCELADO`.
- Destinatarios duplicados.
- Lista vacía.
- Tipo inválido.
- Sin token.
- Más de 200 destinatarios.

Debe llegar un correo por caso válido a `CORREO_PRUEBA`.

### Prueba opcional de falla SMTP

Solo realizarla si se puede restaurar la credencial:

```powershell
powershell -File scripts/probar-2fa.ps1 -ProbarFalla
powershell -File scripts/probar-entrenamiento.ps1 -ProbarFalla
```

La prueba requiere romper temporalmente el puerto de la credencial SMTP. Después se debe restaurar a `465` o `587` y repetir las pruebas normales.

Las fallas se registran en:

```text
n8n/logs/fallas-notificaciones.jsonl
```

El log debe contener únicamente información no sensible: fecha, evento, correo enmascarado, error recortado e identificador de ejecución.

## 8. Recorrido funcional recomendado

Una vez levantados los tres servicios:

1. Abrir `/inscripcion`.
2. Probar validaciones con campos vacíos.
3. Probar una fecha inválida.
4. Probar el consentimiento sin marcar.
5. Completar y enviar una inscripción de prueba.
6. Ir al panel administrador.
7. Abrir el listado de deportistas.
8. Buscar por nombre.
9. Buscar por documento.
10. Cambiar entre activos, inactivos y todos.
11. Abrir una ficha.
12. Editar un dato.
13. Guardar y revisar el historial.
14. Entrar al rol tesorero.
15. Abrir el registro manual de pago.
16. Buscar un deportista activo.
17. Seleccionar el mes.
18. Revisar el cobro existente.
19. Registrar un pago con confirmación.
20. Probar un pago repetido o con un monto incorrecto.
21. Ejecutar los scripts de n8n.
22. Revisar la bandeja de prueba y el log de fallas.

## 9. Validación técnica antes de subir cambios

### Frontend

```powershell
cd frontend
npm run lint
npm test
npm run build
```

### Backend

```powershell
cd backend
.\mvnw.cmd verify
```

El CI de GitHub ejecuta:

- `npm ci`.
- `npm run lint`.
- `npm test`.
- `npm run build`.
- `./mvnw -B -ntp verify` cuando corresponde.

## 10. Flujo de trabajo Git para la jornada

Actualizar la rama base:

```powershell
git switch develop
git pull
```

Crear una rama por tarea:

```powershell
git switch -c feature/nombre-corto
```

Trabajar y validar:

```powershell
git status
```

Usar commits con el formato:

```text
tipo(alcance): descripción corta AB#<ID>
```

Ejemplo:

```text
feat(frontend): conectar registro de pagos AB#999
```

Subir la rama:

```powershell
git push -u origin feature/nombre-corto
```

Crear el Pull Request hacia `develop`:

```powershell
gh pr create --base develop --title "feat(frontend): descripción AB#999" --body "Qué se hizo y cómo probarlo"
```

Después de la revisión y aprobación, quien abrió el PR puede hacer el merge. Al terminar:

```powershell
git switch develop
git pull
git push origin --delete feature/nombre-corto
git branch -d feature/nombre-corto
git fetch --prune
```

No usar `--delete-branch` al hacer un release de `develop` hacia `main`.

## 11. Pendientes conocidos

- Autenticación real con Supabase.
- Validación de JWT y roles en backend.
- Implementación completa de 2FA.
- Persistencia real de deportistas, inscripciones y pagos.
- Conexión frontend-backend.
- Integración de Wompi.
- Confirmación de inscripción por correo.
- Comprobantes de pago.
- Definición de tarifas, abonos, anulaciones y correcciones.
- Estado de cuenta final en PDF.
- Política definitiva de tratamiento de datos.
- Captcha o control de abuso en la inscripción pública.
- Revisión administrativa de nuevas inscripciones.
- Integración completa backend ↔ n8n para los eventos de negocio.
- Migración futura del log JSONL de fallas a una tabla protegida en Supabase.

## 12. Referencias

- `README.md`
- `backend/README.md`
- `n8n/README.md`
- `docs/info-sprints/sprint-1/diccionario-datos.md`
- `docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`
- `n8n/workflows/futdata-notificaciones.json`
- `frontend/package.json`
