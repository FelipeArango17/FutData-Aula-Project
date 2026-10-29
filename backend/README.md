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
| `N8N_BASE_URL` | URL de n8n, sin barra final | `http://localhost:5678` | Obligatoria |
| `N8N_WEBHOOK_TOKEN` | Secreto compartido con n8n (cabecera `X-FutData-Token`) | vacío | Obligatoria |
| `SUPABASE_URL`, `SUPABASE_JWKS_URL` | Para HU-001 (validar el JWT). **Todavía no se usan** | | |
| `PORT` | Puerto HTTP | `8080` | `8080` |
| `CLUB_NOMBRE`, `CLUB_NIT`, `CLUB_DIRECCION`, `CLUB_TELEFONO`, `CLUB_CORREO` | Datos del club en los PDF ([Comprobante de pago](#comprobante-de-pago-pdf)) | Datos reales del club | Igual que en local |
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
│   └── reports          Reportes y exportaciones: comprobante de pago en PDF (OpenPDF)
└── config               CORS, OpenAPI, propiedades tipadas (futdata.cors, futdata.n8n, futdata.club)
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

## Comprobante de pago (PDF)

`util.reports` genera el comprobante de un pago en PDF (HU-018, tarea 327), con [OpenPDF](https://github.com/LibrePDF/OpenPDF) 3.0.5 (licencia LGPL 2.1 / MPL 2.0). **Solo lo genera en memoria** y devuelve los bytes. Guardarlo en Supabase Storage y entregar el enlace de descarga es la **tarea 328**. Enviarlo por n8n y el estado de cuenta en PDF son de otras historias.

| Clase | Qué es |
| :--- | :--- |
| `IGeneradorComprobante` | `byte[] generar(ComprobantePagoDTO datos)`. Es lo único que usa el servicio |
| `ComprobantePdfGenerator` | Implementación con OpenPDF (`@Component`) |
| `ComprobantePagoDTO` | Datos del comprobante (record). No depende de la base de datos |
| `ComprobanteInvalidoException` | Datos inválidos; el manejo global de errores responde `400` |
| `PlantillaReportePdf` | Encabezado, pie, colores y fuentes comunes, para reutilizar en el estado de cuenta |
| `FormatoColombiano`, `NumeroEnLetras` | `$ 90.000`, `28 de octubre de 2026`, `NIT 811.032.309-1`, `313 731 7366` y "noventa mil pesos M/CTE" |

### Usarlo desde un servicio

El servicio de pagos consulta el pago con sus DAO, arma el DTO solo con lo que puede ver el acudiente y recibe el PDF:

```java
@Service
public class PagoServiceImpl implements IPagoService {

    private final IGeneradorComprobante comprobantes;      // util.reports, inyectado por constructor
    // ... IPagoDAO, IDeportistaDAO

    private byte[] comprobanteDe(Pago pago, Deportista deportista, Acudiente acudiente) {
        ComprobantePagoDTO datos = new ComprobantePagoDTO(
                "FD-2026-000123",                            // lo asigna quien llama (ver abajo)
                Instant.now(),                               // fecha de emisión; se imprime en hora de Colombia
                deportista.getNombreCompleto(),
                deportista.getCategoria(),                   // opcional
                acudiente.getNombreCompleto(),
                pago.getConcepto(),
                pago.getMonto(),                             // long, pesos sin decimales
                pago.getFechaPago(),                         // LocalDate, no futura
                MetodoPago.desdeEtiqueta(pago.getMetodo()),  // "Efectivo", "Transferencia", "PSE", "Nequi", "Tarjeta"
                pago.getReferenciaWompi());                  // solo PSE, Nequi o Tarjeta; null en pagos manuales
        return comprobantes.generar(datos);                  // tarea 328: subirlo a Storage y guardar recibo_url
    }
}
```

Si un dato no es válido, `generar()` lanza `ComprobanteInvalidoException` con todos los errores (`campo: mensaje`, sin los valores). Desde un endpoint, el manejador global responde `400`:

```json
{ "estado": 400, "mensaje": "Los datos del comprobante no son válidos: concepto: es obligatorio; monto: debe ser mayor que 0", ... }
```

Reglas: `numeroComprobante` (máximo 40 caracteres), `fechaEmision`, `nombreDeportista`, `nombreAcudiente` (máximo 120), `concepto` (máximo 200), `fechaPago` y `metodo` son obligatorios. `monto` debe ser mayor que 0. `fechaPago` no puede ser posterior a hoy en Colombia. `referenciaWompi` (máximo 100) solo se acepta en pagos en línea.

**Número de comprobante.** Formato sugerido: `FD-AAAA-NNNNNN`, con el año del pago y un consecutivo de 6 dígitos, por ejemplo `FD-2026-000123`. El generador no lo inventa ni comprueba que sea único: el consecutivo lo debe llevar la base de datos (tarea 328 o la tabla `Pago`).

### Qué lleva y qué no

| Lleva | No lleva, a propósito |
| :--- | :--- |
| Número y fecha de emisión | Quién registró el pago |
| Deportista, categoría (si hay) y acudiente | Nota interna |
| Concepto, fecha y método de pago | Referencia del pago manual |
| Referencia Wompi (solo pagos en línea) | Documento de identidad del deportista (es menor de edad) |
| Monto en número (`$ 90.000`) y en letras (`noventa mil pesos M/CTE`) | Datos de tarjeta o cuenta bancaria |

El DTO **no tiene campos** para lo de la segunda columna, así que no se puede imprimir por error; `ComprobantePdfGeneratorTest` lo comprueba. Los metadatos del PDF solo tienen el título (`Comprobante de pago FD-2026-000123`), el autor (el nombre del club) y el creador (`FutData`), sin datos personales.

**Diseño.** Tamaño carta, en español.
- Encabezado con el club y el título; tabla de datos; el monto en un recuadro.
- Pie en cada página: fecha y hora de emisión, "Documento generado por FutData", la aclaración de que no es factura electrónica y el número de página.
- Colores de `docs/design-system/tokens.css`. Se lee bien impreso en blanco y negro: texto oscuro, fondos muy claros, bordes grises medios y nada que dependa solo del color.
- Fuente Helvetica estándar: tiene tildes y ñ, y no hay que incrustarla.

### Datos del club

Salen de `futdata.club` (`config.ClubProperties`). Son datos públicos, con valor por defecto en `application.yml` en todos los perfiles, y se pueden cambiar con variables de entorno:

| Variable | Por defecto | Se imprime |
| :--- | :--- | :--- |
| `CLUB_NOMBRE` | `Asociación Deportiva Prado Fútbol Club` | Igual |
| `CLUB_NIT` | `811032309-1` | `NIT 811.032.309-1` |
| `CLUB_DIRECCION` | `Calle 40 Sur 75b-81, San Antonio de Prado, Medellín, Antioquia` | Igual |
| `CLUB_TELEFONO` | `3137317366` | `Tel. 313 731 7366` |
| `CLUB_CORREO` | `prafofutbolclub@gmail.com` | Igual |

- Si una variable queda vacía (`CLUB_NIT=`), esa línea no se imprime.
- `backend/.env` se lee en ISO-8859-1: para escribir tildes ahí usa `ó` y similares (ver `.env.example`), o define la variable en el sistema.

**Pendiente de confirmar con el club:**
- El correo `prafofutbolclub@gmail.com`: ¿"prafo" está bien escrito o es "prado"?
- El logo: no hay un PNG en el repositorio, así que el comprobante sale sin logo. Cuando haya uno, se agrega en `PlantillaReportePdf`.
- El texto legal del pie ("No es una factura electrónica"), con el tesorero.

### Ver un ejemplo

`./mvnw verify` (o `./mvnw test -Dtest=ComprobantePdfGeneratorTest`) deja PDF de ejemplo en `target/comprobantes-ejemplo/`: `comprobante-pago-manual.pdf`, `comprobante-pago-en-linea.pdf` y `comprobante-concepto-largo.pdf`. `target/` está ignorado por Git.

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
| `400` | Bean Validation falla, el JSON está mal formado, falta un parámetro o tiene un tipo inválido, o el servicio lanza `ComprobanteInvalidoException` | `nombre: no debe estar vacío` |
| `404` | La ruta no existe o el servicio lanza `RecursoNoEncontradoException` | `No existe la ruta GET /api/no-existe` |
| `405` / `415` | Método HTTP no permitido o tipo de contenido no soportado | `El método POST no está permitido en esta ruta` |
| `500` | Cualquier error no controlado. **Nunca incluye la traza ni el mensaje interno**; el detalle queda en el log del servidor | `Ocurrió un error interno. Intenta de nuevo más tarde.` |
| `503` | Solo `/api/health`, cuando la base de datos no responde (su cuerpo es el de salud, no este) | |

En el frontend basta con leer `error.response.data.mensaje` (Axios) para mostrar el error.

## Integración con n8n (tarea 340)

`config.N8nProperties` ya expone `futdata.n8n.base-url`, `webhook-token` y `timeout` (5 s, como pide el [contrato del webhook](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md)). `INotificador` y `N8nClient` (en `datasource.external`) se implementan en la tarea 340 inyectando ese record.

## Pruebas

```bash
./mvnw verify
```

No necesitan base de datos, credenciales ni red: cargan `src/test/resources/pruebas.properties`, que tiene prioridad sobre `backend/.env`, y simulan la base de datos con Mockito.

| Prueba | Qué revisa |
| :--- | :--- |
| `FutDataApplicationTests` | El contexto arranca sin base de datos y sin JPA; propiedades de n8n (timeout 5 s, token oculto en `toString`); OpenAPI y Swagger UI |
| `NumeroEnLetrasTest`, `FormatoColombianoTest` | Montos en letras (1, 21, 100, 101, 1.000, 150.000, 1.000.000, 1.250.500...), pesos, fechas en hora de Colombia, NIT y celular |
| `ComprobantePdfGeneratorTest` | Texto del PDF leído con PDFBox: datos, monto en número y en letras, fechas, club (y líneas vacías), tildes y ñ, concepto largo, metadatos sin datos personales, que no salgan la nota ni quién registró, y DTO inválido. Deja ejemplos en `target/comprobantes-ejemplo/` |
| `ComprobanteConfiguracionTest` | Valores por defecto de `futdata.club` y el generador como bean |
| `SaludControllerTest` | `GET /api/health`: `200` con la base de datos arriba, `503` con la base de datos abajo |
| `SaludDAOImplTest` | `SELECT 1` con timeout de 2 s; `false` si no hay conexión |
| `ManejadorGlobalErroresTest` | Formato uniforme para `400`, `404`, `405` y `500`, sin traza |
| `CorsConfigTest` | Acepta el origen configurado y rechaza otros |
| `VerificadorVariablesProdTest` | El perfil `prod` exige las variables obligatorias |

El CI (`.github/workflows/ci.yml`, job *Backend*) corre `./mvnw -B verify` en cada PR o push a `develop`/`main` que cambie `backend/`.

## Docker

```bash
docker build -t futdata-backend .
docker run --rm -p 8080:8080 --env-file .env futdata-backend
```

- Dos etapas: compila con Maven sobre JDK 21 y ejecuta el `.jar` sobre JRE 21, con un usuario sin privilegios (`futdata`), en el puerto 8080.
- Usa el perfil `prod`, así que exige las variables obligatorias. `--env-file .env` las toma de `backend/.env`; ese archivo nunca entra en la imagen (está en `.dockerignore`).
- Las pruebas no corren dentro del build; las corre el CI.
- La imagen trae un `HEALTHCHECK` que comprueba que el puerto HTTP acepta conexiones (no consulta `/api/health`: sin base de datos ese endpoint responde `503` aunque la aplicación esté viva).
- Para levantar el backend junto con n8n con un solo comando (`docker compose up -d --build` desde la raíz), ver [Levantar todo con un solo comando](../README.md#87-levantar-todo-con-un-solo-comando).
