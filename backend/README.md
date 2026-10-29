# Backend de FutData (Spring Boot)

API REST en JSON de FutData. Sigue la arquitectura en capas del PPI con los patrones **Fachada**, **DAO** y **DTO** (diagramas en [`docs/diagramas-clases/`](../docs/diagramas-clases/)).

| | |
| :--- | :--- |
| Lenguaje y framework | Java 21, Spring Boot 3.5 |
| Construcción | Maven con wrapper (`mvnw`), artefacto `target/futdata-backend.jar` |
| Persistencia | JDBC sobre HikariCP (`JdbcTemplate` / `NamedParameterJdbcTemplate`). **Sin JPA, Hibernate ni Spring Data** |
| Base de datos | PostgreSQL de Supabase, por JDBC con SSL (`sslmode=require`) |
| Documentación de la API | springdoc-openapi: Swagger UI en `/swagger-ui.html`, especificación en `/v3/api-docs` |
| Paquete raíz | `co.edu.futdata` |

> [!WARNING]
> **La API todavía no tiene autenticación.** El inicio de sesión, la validación del JWT de Supabase, el 2FA y los roles llegan con HU-001, HU-002 y HU-005. Hasta entonces el backend **no debe desplegarse** en un entorno accesible desde internet.

## Requisitos

- JDK 21 (por ejemplo, Eclipse Temurin). `java -version` debe decir 21.
- No hace falta instalar Maven: `mvnw` descarga la versión correcta (3.9.16) la primera vez y comprueba su SHA-256 (`distributionSha256Sum` en `.mvn/wrapper/maven-wrapper.properties`).
- **En Linux, `unzip`** (`sudo apt install unzip`). Sin `unzip`, `mvnw` descarga el `.tar.gz` de Maven en lugar del `.zip`, y como la suma SHA-256 guardada es la del `.zip`, falla con *Failed to validate Maven distribution SHA-256*. Windows, macOS y los runners de GitHub Actions ya lo traen; el `Dockerfile` lo instala en su etapa de compilación.
- Opcional: Docker Desktop, para construir la imagen.

## Correr el backend

Todos los comandos, desde la carpeta `backend/`.

```bash
./mvnw spring-boot:run          # Git Bash, macOS o Linux
```

```powershell
.\mvnw.cmd spring-boot:run      # PowerShell
```

Arranca con el perfil `local` en http://localhost:8080. **No necesita credenciales**: sin base de datos, la aplicación arranca igual y `/api/health` responde `503` con `"baseDeDatos": "DOWN"`.

| URL | Qué es |
| :--- | :--- |
| http://localhost:8080/api/health | Estado del backend y de la base de datos |
| http://localhost:8080/swagger-ui.html | Documentación interactiva de la API |
| http://localhost:8080/v3/api-docs | Especificación OpenAPI en JSON |

En **IntelliJ IDEA**: abre la carpeta `backend/` (o la raíz del repo) como proyecto Maven y ejecuta `FutDataApplication`.

Otras formas:

```bash
./mvnw verify                                   # compila, corre las pruebas y genera target/futdata-backend.jar
java -jar target/futdata-backend.jar            # ejecuta el .jar (perfil local)
```

## Variables de entorno

La configuración está en [`src/main/resources/application.yml`](src/main/resources/application.yml) y toma los valores de variables de entorno. **Nunca se suben llaves ni contraseñas al repositorio.**

| Variable | Para qué | Perfil `local` (por defecto) | Perfil `prod` |
| :--- | :--- | :--- | :--- |
| `DB_URL` | URL JDBC de PostgreSQL de Supabase, con `?sslmode=require` | `jdbc:postgresql://localhost:5432/postgres` | Obligatoria |
| `DB_USER` | Usuario de la base de datos | `postgres` | Obligatoria |
| `DB_PASSWORD` | Contraseña de la base de datos | vacía | Obligatoria |
| `CORS_ORIGINS` | Orígenes del frontend permitidos, separados por coma | `http://localhost:5173` | Obligatoria |
| `N8N_BASE_URL` | URL de n8n, sin barra final ([Notificaciones](#notificaciones-n8n)) | `http://localhost:5678` | Obligatoria |
| `N8N_WEBHOOK_TOKEN` | Secreto compartido con n8n (cabecera `X-FutData-Token`) | vacío: la app arranca, pero no envía notificaciones | Obligatoria |
| `SUPABASE_URL`, `SUPABASE_JWKS_URL` | Para HU-001 (validar el JWT). **Todavía no se usan** | | |
| `PORT` | Puerto HTTP | `8080` | `8080` |
| `DB_POOL_MAX` | Conexiones máximas del pool | `5` | `5` |

Con el perfil `prod`, si falta una variable obligatoria la aplicación **no arranca** y dice cuáles faltan.

**Supabase:** copia la URL desde *Project Settings → Database → Connection string → JDBC*, modo *Session pooler* (puerto 5432), y conserva `sslmode=require`. Si usas el *Transaction pooler* (puerto 6543), agrega también `&prepareThreshold=0`.

### Cómo cargarlas en Windows

**Opción 1, archivo `.env` (recomendada).** El backend lee `backend/.env` solo, al arrancar:

```powershell
Copy-Item .env.example .env      # desde backend/; luego edita .env
```

- Formato `CLAVE=valor`, una por línea, **sin comillas** (se lee como un archivo `.properties`).
- Funciona con `mvnw`, con `java -jar` y con IntelliJ, siempre que el directorio de trabajo sea `backend/` o la raíz del repo.
- `backend/.env` está ignorado por Git.
- Si una variable existe también en el sistema, gana la del sistema.

**Opción 2, PowerShell.** Solo para esa ventana de la terminal:

```powershell
$env:DB_URL = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
$env:DB_USER = "postgres.tu-ref-de-proyecto"
$env:DB_PASSWORD = "tu-contraseña"
.\mvnw.cmd spring-boot:run
```

**Opción 3, IntelliJ IDEA.** *Run → Edit Configurations… →* `FutDataApplication` *→ Environment variables*. Pega las variables separadas por `;`, por ejemplo `DB_USER=postgres.tu-ref;DB_PASSWORD=...`, o usa el botón del campo para agregarlas una por una. Si prefieres, deja el campo vacío y usa el `.env` (opción 1).

### Perfiles

| Perfil | Cuándo | Qué cambia |
| :--- | :--- | :--- |
| `local` | Por defecto al ejecutar sin indicar perfil | Valores por defecto para trabajar sin credenciales y logs `DEBUG` de `co.edu.futdata` |
| `prod` | En la imagen Docker (`SPRING_PROFILES_ACTIVE=prod`) | Sin valores por defecto: exige las variables obligatorias. SSL obligatorio si `DB_URL` no indica `sslmode` |

Para usar otro perfil: `SPRING_PROFILES_ACTIVE=prod` (variable de entorno) o `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`.

## Estructura de capas

```text
co.edu.futdata
├── api
│   ├── controllers      @RestController: reciben HTTP, validan y llaman a la fachada. Manejo global de errores
│   └── dto              Records de entrada/salida (RequestDTO, ResponseDTO) y mappers DTO <-> entidad
├── services
│   ├── facade           Interfaces I<Recurso>Service (patrón Fachada) y sus excepciones
│   └── impl             <Recurso>ServiceImpl: coordina reglas, DAO y servicios externos
├── domain
│   ├── entities         Entidades del dominio (POJO, sin anotaciones de Spring ni JSON)
│   └── rules            Reglas<Recurso>: validaciones y constantes del negocio
├── persistence
│   ├── dao              Interfaces I<Recurso>DAO (patrón DAO)
│   └── impl             <Recurso>DAOImpl: SQL con JdbcTemplate y RowMapper
├── datasource
│   ├── db               Pool HikariCP de Supabase (lo configura Spring Boot; "ConexionPool" en el PPI)
│   └── external         Clientes de servicios externos: n8n (INotificador / N8nClient), Wompi
├── util
│   ├── security         JWT, códigos 2FA, auditoría (HU-001, HU-002, HU-005)
│   └── reports          Reportes y exportaciones
└── config               CORS, OpenAPI, propiedades tipadas (futdata.cors, futdata.n8n)
```

Cada paquete tiene un `package-info.java` que explica qué va ahí.

### Ejemplo de flujo (según la Fig. 1, `00-patrones`)

Una petición recorre las capas siempre en este orden, y cada capa conoce solo la **interfaz** de la siguiente:

```text
Controller ──► DTO ──► IService ──► ServiceImpl ──► IDAO ──► DAOImpl ──► HikariCP (Supabase)
(api.controllers) (api.dto) (services.facade) (services.impl) (persistence.dao) (persistence.impl) (datasource.db)
```

El endpoint de salud ya sigue ese flujo y sirve de ejemplo real:

| Capa | Clase | Qué hace |
| :--- | :--- | :--- |
| `api.controllers` | `SaludController` | `GET /api/health`: llama a la fachada y elige `200` o `503` |
| `api.dto` | `SaludResponseDTO` | `{"estado", "baseDeDatos", "version"}` |
| `services.facade` | `ISaludService` | `consultar()` |
| `services.impl` | `SaludServiceImpl` | Pregunta al DAO y arma el DTO con la versión |
| `persistence.dao` | `ISaludDAO` | `baseDeDatosDisponible()` |
| `persistence.impl` | `SaludDAOImpl` | `SELECT 1` con `JdbcTemplate` y timeout de 2 s |

Para un módulo de negocio, el flujo completo con mapper, reglas y entidad queda así. Es un esquema ilustrativo del inicio de sesión de la Fig. 4 (`03-autenticacion`); ese código todavía no existe y las rutas y tablas son de ejemplo:

```java
// api.controllers: solo HTTP; depende de la interfaz de la fachada
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IAutenticacionService autenticacion;             // inyección por constructor

    @PostMapping("/codigo")
    public SesionVerificadaDTO verificarCodigo(@Valid @RequestBody CodigoRequestDTO peticion) { ... }
}

// services.impl: coordina reglas, DAO y servicios externos, todos por su interfaz
@Service
public class AutenticacionServiceImpl implements IAutenticacionService {
    private final ICodigoVerificacionDAO codigos;                  // persistence.dao
    private final INotificador notificador;                        // datasource.external
    // ReglasVerificacion (domain.rules) decide si se puede intentar o reenviar
}

// persistence.impl: todo el SQL vive aquí, siempre con parámetros
@Repository
public class CodigoVerificacionDAOImpl implements ICodigoVerificacionDAO {
    private final NamedParameterJdbcTemplate jdbc;

    public CodigoVerificacion buscarVigente(UUID usuarioId) {
        return jdbc.query("SELECT ... FROM codigo_verificacion WHERE id_usuario = :usuarioId AND expira_en > now()",
                Map.of("usuarioId", usuarioId), FILA_A_ENTIDAD).stream().findFirst().orElse(null);
    }
}
```

### Agregar un módulo nuevo

Para un recurso `Entrenamiento` (HU-026):

1. **Entidad** `domain.entities.Entrenamiento`, con los campos del diccionario de datos (`docs/info-sprints/sprint-1/diccionario-datos.md`).
2. **Reglas** `domain.rules.ReglasEntrenamiento`, si hay validaciones de negocio, con su prueba unitaria.
3. **DAO**: interfaz `persistence.dao.IEntrenamientoDAO` e implementación `persistence.impl.EntrenamientoDAOImpl` (`@Repository`), con SQL parametrizado y un `RowMapper`.
4. **DTO**: `api.dto.EntrenamientoRequestDTO` (con anotaciones de Bean Validation), `EntrenamientoResponseDTO` y `EntrenamientoMapper`.
5. **Fachada**: interfaz `services.facade.IEntrenamientoService` e implementación `services.impl.EntrenamientoServiceImpl` (`@Service`, `@Transactional` si escribe en varias tablas). Si no encuentra el recurso, lanza `RecursoNoEncontradoException` (responde `404`).
6. **Controlador** `api.controllers.EntrenamientoController` bajo `/api/entrenamientos`, con `@Tag` y `@Operation` para Swagger.
7. **Pruebas**: reglas y servicio con pruebas unitarias (DAO simulado con Mockito); controlador con `MockMvc`. Las pruebas no deben necesitar la base de datos real.

Reglas para todos los módulos:

- Inyección por constructor, nunca `@Autowired` en atributos.
- El controlador nunca usa un DAO ni una entidad; la entidad nunca sale por la API.
- Nombres de clases, métodos y mensajes en español, como en los diagramas del PPI.
- No registrar en logs datos sensibles (contraseñas, códigos 2FA, tokens, correos completos).

## Formato de errores (para el frontend)

Todas las respuestas de error tienen el mismo JSON:

```json
{
  "timestamp": "2026-10-28T23:15:30.123Z",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "edad: debe ser mayor que o igual a 5; nombre: no debe estar vacío",
  "ruta": "/api/entrenamientos"
}
```

| Campo | Contenido |
| :--- | :--- |
| `timestamp` | Momento del error, ISO 8601 en UTC |
| `estado` | Código HTTP (igual al de la respuesta) |
| `error` | Nombre estándar del código HTTP, en inglés (`Bad Request`, `Not Found`...) |
| `mensaje` | Explicación en español que se puede mostrar al usuario. En validación: `campo: mensaje`, separados por `; ` y ordenados |
| `ruta` | Ruta de la petición, sin la cadena de consulta |

| Código | Cuándo | `mensaje` de ejemplo |
| :--- | :--- | :--- |
| `400` | Bean Validation falla, el JSON está mal formado, falta un parámetro o tiene un tipo inválido | `nombre: no debe estar vacío` |
| `404` | La ruta no existe o el servicio lanza `RecursoNoEncontradoException` | `No existe la ruta GET /api/no-existe` |
| `405` / `415` | Método HTTP no permitido o tipo de contenido no soportado | `El método POST no está permitido en esta ruta` |
| `500` | Cualquier error no controlado. **Nunca incluye la traza ni el mensaje interno**; el detalle queda en el log del servidor | `Ocurrió un error interno. Intenta de nuevo más tarde.` |
| `503` | Solo `/api/health`, cuando la base de datos no responde (su cuerpo es el de salud, no este) | |

En el frontend basta con leer `error.response.data.mensaje` (Axios) para mostrar el error.

## Notificaciones (n8n)

Los correos los envía n8n. El backend se los pide con `INotificador.notificar(evento, datos)`; la implementación es `N8nClient` (ambos en `datasource.external`), que hace el `POST` al webhook según el [contrato del webhook](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md). Ese documento es la fuente de verdad de los campos de cada evento y de las respuestas.

| | |
| :--- | :--- |
| Endpoint | `POST {N8N_BASE_URL}/webhook/futdata/notificaciones`, `Content-Type: application/json;charset=UTF-8`, cabecera `X-FutData-Token: {N8N_WEBHOOK_TOKEN}` |
| Cuerpo | `{"evento": ..., "datos": {...}}` |
| Timeout | 5 s de conexión y 5 s de lectura (`futdata.n8n.timeout`) |
| Reintentos | Ninguno. Si n8n recibió la petición y solo se demoró, un reintento enviaría el correo dos veces |
| Eventos | `INotificador.CODIGO_2FA` e `INotificador.CAMBIO_ENTRENAMIENTO` (constantes; no escribir la cadena a mano) |

`N8nClient` **no arma los `datos`**: cada servicio construye los de su evento (HU-002 el código 2FA, HU-026 el aviso de entrenamiento) y depende solo de la interfaz `INotificador`, inyectada por constructor.

### Resultado y excepciones

`notificar()` no devuelve nada: si retorna, n8n aceptó la notificación. Si no, lanza una de estas excepciones (no comprobadas, todas hijas de `NotificacionException`, con `getEvento()` y `getCodigoHttp()`):

| Respuesta de n8n | Resultado | Qué significa |
| :--- | :--- | :--- |
| `200` (`CODIGO_2FA`) | Retorna | El SMTP aceptó el correo |
| `202` (`CAMBIO_ENTRENAMIENTO`) | Retorna | n8n **recibió** el aviso y lo envía en segundo plano. No significa "entregado" |
| `400` | `NotificacionRechazadaException` | Los `datos` no cumplen el contrato: error de programación. `getDetalle()` trae la explicación de n8n, o `null` si parecía tener datos sensibles (correos, 6 dígitos seguidos o un valor de `datos`; los valores de `evento` y `tipoCambio` no cuentan, porque son fijos) |
| `401` | `NotificadorMalConfiguradoException` | `N8N_WEBHOOK_TOKEN` no coincide con el de n8n |
| Falta `N8N_BASE_URL` o `N8N_WEBHOOK_TOKEN` (perfil local) | `NotificadorMalConfiguradoException` | La app arranca, pero no puede enviar. En `prod` la app ni siquiera arranca |
| `502`, timeout, error de red u otro código | `NotificacionNoEnviadaException` | No se pudo confirmar el envío. `getCodigoHttp()` está vacío si no hubo respuesta |
| `evento` o `datos` vacíos | `IllegalArgumentException` | Error de programación; no se llama a n8n |

Los mensajes de las excepciones están en español y nunca contienen el código, correos, nombres, destinatarios ni el token, así que se pueden registrar. **No se muestran tal cual al usuario**: el servicio decide qué decirle.

### Auditoría: la hace el servicio

`N8nClient` no conoce al usuario, así que **no escribe en la auditoría**. La registra el servicio que llama a `notificar()`, al atrapar la excepción, porque él sí tiene el `usuarioId`. Ante `NotificacionNoEnviadaException` es obligatorio: si fue un timeout o un error de red, n8n pudo no registrar nada y ese es el único registro. Se registra el evento, el `usuarioId`, el código HTTP (si hubo respuesta) y la fecha; nunca los `datos`.

```java
} catch (NotificacionNoEnviadaException e) {
    auditoria.registrar(e.getEvento(), usuario.getId(),
            e.getCodigoHttp().isPresent() ? String.valueOf(e.getCodigoHttp().getAsInt()) : "sin respuesta");
    throw ...;
}
```

`AuditoriaLogger` y su firma llegan con HU-005; la llamada de arriba es ilustrativa.

### Ejemplo: `CODIGO_2FA` (HU-002, `AutenticacionServiceImpl`)

Síncrono: el inicio de sesión espera la respuesta.

```java
@Service
public class AutenticacionServiceImpl implements IAutenticacionService {

    private final INotificador notificador;
    // ... DAO, GeneradorCodigo, ReglasVerificacion, AuditoriaLogger

    private void enviarCodigo(Usuario usuario, String codigo) {
        Map<String, Object> datos = Map.of(
                "correo", usuario.getCorreo(),
                "nombre", usuario.getNombre(),
                "codigo", codigo,                                        // String: conserva los ceros ("012345")
                "vigenciaMinutos", ReglasVerificacion.VIGENCIA_CODIGO.toMinutes());
        try {
            notificador.notificar(INotificador.CODIGO_2FA, datos);
        } catch (NotificacionNoEnviadaException e) {
            // 502, timeout o red: avisar al usuario; puede usar "Reenviar código" (INTERVALO_REENVIO = 1 min).
            // Registrar en la auditoría: evento, usuarioId, código HTTP (si hubo) y fecha. Obligatorio.
            throw new CodigoNoEnviadoException("No pudimos enviarte el código. Intenta de nuevo en un momento.");
        } catch (NotificacionRechazadaException | NotificadorMalConfiguradoException e) {
            // 400 (datos mal armados), 401 o sin configuración: error nuestro, no del usuario.
            log.error("No se pudo pedir el código 2FA del usuario {}: {}", usuario.getId(), e.getMessage());
            throw e;                                                     // termina en el 500 genérico
        }
    }
}
```

`CodigoNoEnviadoException` es de ejemplo: HU-002 decide su nombre y su código HTTP.

### Ejemplo: `CAMBIO_ENTRENAMIENTO` (HU-026, servicio de entrenamientos)

Asíncrono: se envía **después** de guardar y su resultado **nunca** deshace el cambio. Se recomienda llamarlo al confirmar la transacción (`@TransactionalEventListener(phase = AFTER_COMMIT)`), para no avisar de un cambio que luego se revierte.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void avisarCambio(EntrenamientoCambiado cambio) {
    Map<String, Object> datos = Map.of(
            "tipoCambio", cambio.tipo().name(),                         // CREADO, MODIFICADO o CANCELADO
            "entrenamiento", Map.of("categoria", ..., "fecha", "2026-11-04", "horaInicio", "16:00",
                    "lugar", ..., "entrenador", ...),
            "destinatarios", destinatarios);                            // 1 a 200 {correo, nombre}; los resuelve el backend
    try {
        notificador.notificar(INotificador.CAMBIO_ENTRENAMIENTO, datos);
        // 202: decirle al usuario "se enviará el aviso", nunca "los correos llegaron".
    } catch (NotificacionRechazadaException e) {
        // 400: contrato roto. Registrar el evento y e.getDetalle(); informar que no se pudo enviar el aviso.
    } catch (NotificadorMalConfiguradoException e) {
        // 401 o sin configuración: registrar como error de configuración.
    } catch (NotificacionNoEnviadaException e) {
        // Timeout u otro error: registrar en la auditoría e informar que no se pudo confirmar el aviso.
        // NO reintentar: si n8n sí lo recibió, saldría dos veces.
    }
}
```

En la auditoría de este evento basta con el id del entrenamiento, `tipoCambio` y el número de destinatarios; **nunca** la lista.

### Qué se registra

`N8nClient` solo registra el evento, el código HTTP, la duración y, si n8n lo devuelve, el número de destinatarios:

```text
INFO  Notificación CAMBIO_ENTRENAMIENTO aceptada por n8n: HTTP 202, 9 ms, 3 destinatarios
WARN  Notificación CODIGO_2FA no enviada: HTTP 502, 1840 ms
```

Nunca el cuerpo, el código 2FA, correos, nombres, destinatarios ni el token. El cuerpo se serializa a bytes antes de dárselo a `RestClient` para que tampoco aparezca si alguien activa `logging.level.org.springframework.web=DEBUG`. **No agregues un interceptor ni un log de peticiones HTTP que incluya esta llamada.** Los `// TODO auditoría (AuditoriaLogger)` de `N8nClient` marcan en qué fallas debe registrarse la auditoría; la escribe el servicio (ver [Auditoría: la hace el servicio](#auditoría-la-hace-el-servicio)).

### Probar contra el n8n local (opcional)

`N8nClientContraN8nLocalTest` llama al n8n de verdad. Está **desactivada por defecto** (no corre en `./mvnw verify` ni en el CI) y **envía correos reales** a `FUTDATA_N8N_CORREO_PRUEBA`: un código 2FA `123456` y un aviso de entrenamiento `CREADO`. También prueba un `400` y un `401`, que no envían correos.

1. Levanta n8n y publica el flujo (ver [`n8n/README.md`](../n8n/README.md)).
2. Desde `backend/`, con el mismo token de `n8n/.env`:

```powershell
$env:FUTDATA_PRUEBA_N8N_REAL = "true"
$env:N8N_BASE_URL = "http://localhost:5678"
$env:N8N_WEBHOOK_TOKEN = "el-de-n8n/.env"
$env:FUTDATA_N8N_CORREO_PRUEBA = "tu-correo@ejemplo.com"
.\mvnw.cmd test -Dtest=N8nClientContraN8nLocalTest
```

```bash
FUTDATA_PRUEBA_N8N_REAL=true N8N_BASE_URL=http://localhost:5678 N8N_WEBHOOK_TOKEN=... \
FUTDATA_N8N_CORREO_PRUEBA=tu-correo@ejemplo.com ./mvnw test -Dtest=N8nClientContraN8nLocalTest
```

Esta prueba lee las variables del sistema, no `backend/.env`.

## Pruebas

```bash
./mvnw verify
```

No necesitan base de datos, credenciales ni red (salvo `N8nClientContraN8nLocalTest`, desactivada por defecto): cargan `src/test/resources/pruebas.properties`, que tiene prioridad sobre `backend/.env`, y simulan la base de datos con Mockito.

| Prueba | Qué revisa |
| :--- | :--- |
| `FutDataApplicationTests` | El contexto arranca sin base de datos y sin JPA; propiedades de n8n (timeout 5 s, token oculto en `toString`); OpenAPI y Swagger UI |
| `SaludControllerTest` | `GET /api/health`: `200` con la base de datos arriba, `503` con la base de datos abajo |
| `SaludDAOImplTest` | `SELECT 1` con timeout de 2 s; `false` si no hay conexión |
| `ManejadorGlobalErroresTest` | Formato uniforme para `400`, `404`, `405` y `500`, sin traza |
| `CorsConfigTest` | Acepta el origen configurado y rechaza otros |
| `VerificadorVariablesProdTest` | El perfil `prod` exige las variables obligatorias, incluidas las de n8n |
| `N8nClientTest` | Petición a n8n (URL, método, `Content-Type`, `X-FutData-Token`, cuerpo), cada respuesta (`200`, `202`, `400`, `401`, `502`, otros, timeout, red) y que el código, el token, los correos y los destinatarios no salgan en logs ni excepciones |
| `N8nClientConfiguracionTest` | Sin `N8N_BASE_URL` ni `N8N_WEBHOOK_TOKEN` en local, la app arranca y `notificar()` lanza `NotificadorMalConfiguradoException` |
| `N8nClientContraN8nLocalTest` | Opcional y desactivada: contra el n8n local real (ver [Notificaciones (n8n)](#notificaciones-n8n)) |

El CI (`.github/workflows/ci.yml`, job *Backend*) corre `./mvnw -B verify` en cada PR o push a `develop`/`main` que cambie `backend/`.

## Docker

```bash
docker build -t futdata-backend .
docker run --rm -p 8080:8080 --env-file .env futdata-backend
```

- Dos etapas: compila con Maven sobre JDK 21 y ejecuta el `.jar` sobre JRE 21, con un usuario sin privilegios (`futdata`), en el puerto 8080.
- Usa el perfil `prod`, así que exige las variables obligatorias. `--env-file .env` las toma de `backend/.env`; ese archivo nunca entra en la imagen (está en `.dockerignore`).
- Las pruebas no corren dentro del build; las corre el CI.
