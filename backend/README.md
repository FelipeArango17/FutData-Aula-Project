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
> **La API valida el JWT de Supabase y los roles, pero todavía no exige el segundo paso del inicio de sesión (HU-002).** Ver [Seguridad](#seguridad). Hasta que exista, no conviene desplegar el backend en un entorno accesible desde internet.

## Requisitos

- JDK 21 (por ejemplo, Eclipse Temurin). `java -version` debe decir 21.
- No hace falta instalar Maven: `mvnw` descarga la versión correcta (3.9.16) la primera vez y comprueba su SHA-256 (`distributionSha256Sum` en `.mvn/wrapper/maven-wrapper.properties`).
- **En Linux, `unzip`** (`sudo apt install unzip`). Sin `unzip`, `mvnw` descarga el `.tar.gz` de Maven en lugar del `.zip`, y como la suma SHA-256 guardada es la del `.zip`, falla con *Failed to validate Maven distribution SHA-256*. Windows, macOS y los runners de GitHub Actions ya lo traen; el `Dockerfile` lo instala en su etapa de compilación.
- Opcional: Docker Desktop, para construir la imagen y para las pruebas de integración con PostgreSQL (sin Docker se omiten).

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
| http://localhost:8080/swagger-ui.html | Documentación interactiva de la API (solo con el perfil `dev`) |
| http://localhost:8080/v3/api-docs | Especificación OpenAPI en JSON (solo con el perfil `dev`) |

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
| `SUPABASE_URL` | URL del proyecto de Supabase: [Storage](#almacenamiento-de-comprobantes-supabase-storage) y las llaves públicas para validar los JWT ([Seguridad](#seguridad)) | vacía: la app arranca, pero no guarda comprobantes y rechaza todo token (`401`) | Obligatoria |
| `SUPABASE_SERVICE_ROLE_KEY` | Llave `service_role` para Storage. **Secreta, solo backend** | vacía: la app arranca, pero no guarda comprobantes | Obligatoria |
| `SUPABASE_BUCKET_COMPROBANTES`, `SUPABASE_VIGENCIA_URL_FIRMADA` | Bucket de comprobantes y vigencia de las URL firmadas | `comprobantes`, `5m` | Igual |
| `WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY`, `WOMPI_EVENTS_SECRET`, `WOMPI_INTEGRITY_SECRET` | Llaves de Wompi ([Pagos con Wompi](#pagos-con-wompi)). **Secretas** (menos la pública) | vacías: la app arranca, pero los endpoints de Wompi responden `503 PAGOS_NO_CONFIGURADOS` | Opcionales (sin ellas no hay pagos en línea) |
| `WOMPI_BASE_URL`, `WOMPI_REDIRECT_URL` | URL de la API de Wompi y a dónde vuelve el usuario tras pagar | sandbox `https://sandbox.wompi.co/v1`; sin redirección | Igual |
| `FUTDATA_MENSUALIDAD_MONTO`, `FUTDATA_MENSUALIDAD_DIA_VENCIMIENTO`, `FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA` | Cobro mensual: monto en pesos (**pendiente: lo define el club**), día de vencimiento y si se genera solo cada día | `0` (sin configurar), `5`, `false` | Igual |
| `FOTOS_BUCKET`, `FOTOS_VIGENCIA_URL_FIRMADA` | Bucket de las fotos de los deportistas y vigencia de su URL firmada, de 10 s a 60 min ([Foto](#foto)) | `fotos`, `60m` | Igual |
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
| `dev` | Opcional, junto con `local`: `SPRING_PROFILES_ACTIVE=local,dev` | Publica Swagger UI y `/v3/api-docs` sin token. En cualquier otro perfil están apagados. Nunca en `prod` |

Para usar otro perfil: `SPRING_PROFILES_ACTIVE=prod` (variable de entorno) o `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`. Con la documentación: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,dev`.

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
│   └── external         Clientes de servicios externos: n8n (INotificador / N8nClient), Supabase Storage
│                        (IAlmacenamientoArchivos / SupabaseStorageClient), Wompi
├── util
│   ├── security         Spring Security: JWT de Supabase, roles, 401/403. Con HU-002: códigos 2FA y auditoría
│   └── reports          Reportes y exportaciones: comprobante de pago en PDF (OpenPDF)
└── config               CORS, OpenAPI, reloj en hora de Colombia, propiedades tipadas (futdata.cors, futdata.n8n,
                         futdata.club, futdata.supabase)
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

## Deportistas (HU-008, bloque 2A)

La **categoría se calcula al leer** a partir del año de nacimiento (edad por año = año actual − año de nacimiento, con el reloj de Bogotá): no se guarda, y cambia sola cada 1 de enero. El club solo inscribe de 4 a 16 años por año de nacimiento. La edad cumplida (`edad`) también se calcula. El estado de la inscripción (`estadoInscripcion`) se calcula desde los documentos. Detalle en el diccionario de datos (D22).

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET /api/deportistas` | Lista paginada con filtros | `ADMINISTRADOR`, `ENTRENADOR` | `200`, `400`, `403` |
| `GET /api/deportistas/{id}` | Consulta un deportista | `ADMINISTRADOR` | `200`, `400`, `403`, `404` |
| `POST /api/deportistas` | Registra un deportista (queda activo) | `ADMINISTRADOR` | `201` con `Location`, `400`, `403`, `409` |
| `PUT /api/deportistas/{id}` | Reemplaza los campos editables | `ADMINISTRADOR` | `200`, `400`, `403`, `404`, `409` |
| `POST /api/deportistas/{id}/foto` | Sube o reemplaza la foto (multipart) | `ADMINISTRADOR` | `200`, `400`, `403`, `404`, `503` |
| `DELETE /api/deportistas/{id}/foto` | Quita la foto | `ADMINISTRADOR` | `200`, `403`, `404`, `503` |

Los errores `400` y `409` traen `campos` con los campos que hay que corregir (formato en [Formato de errores](#formato-de-errores-para-el-frontend)).

### Respuesta

```json
{"id": "6f1c2a9e-…", "nombre": "Ana", "apellido": "Pérez", "tipoDocumento": "TI", "documento": "1098765432",
 "fechaNacimiento": "2016-08-20", "edad": 10, "telefono": "3001234567", "direccion": "Calle 10 # 20-30",
 "posicion": "Delantera", "foto": "https://…/storage/v1/object/sign/fotos/…?token=…",
 "categoria": "Semillero", "idCategoria": "0a0a…", "grupo": "tarde", "activo": true, "estadoInscripcion": "PENDIENTE"}
```

- `categoria` e `idCategoria`: la categoría calculada; ambas `null` si el deportista queda fuera de 4 a 16 años (al leer no es error).
- `grupo`: `mañana` o `tarde`, solo si la categoría calculada es Cachorros o Semillero; en otra categoría sale `null` aunque esté guardado.
- `foto`: **URL firmada temporal** (60 min, `FOTOS_VIGENCIA_URL_FIRMADA`), nunca la ruta del bucket; `null` si no tiene foto o si Storage no respondió (la respuesta no falla por eso).
- `estadoInscripcion`: `COMPLETA` si cada uno de los 4 documentos obligatorios (`DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION`, `AUTORIZACION_HABEAS_DATA`) tiene al menos uno `APROBADO`; si no, `PENDIENTE`.
- **Para el `ENTRENADOR`** no vienen `documento` ni `estadoInscripcion` (ni la clave), y `telefono` y `direccion` vienen en `null`. Sí recibe categoría, grupo y la URL de la foto.

### `GET /api/deportistas` (paginado)

Ya **no devuelve un arreglo**: `{"content": [...], "page": 0, "size": 10, "totalElements": 23, "totalPages": 3}`. El frontend que leía la lista como arreglo debe leer `content`.

| Parámetro | Valores | Por defecto |
| :--- | :--- | :--- |
| `page` | Desde 0 | `0` |
| `size` | De 1 a 100 | `10` |
| `sort` | `campo` o `campo,asc` o `campo,desc`. Campos: `nombre`, `apellido`, `documento`, `fechaNacimiento`, `fechaRegistro`. El `ENTRENADOR` solo puede `nombre`, `apellido` y `fechaNacimiento`. Siempre se desempata por id (orden estable entre páginas) | `apellido,asc` (luego nombre) |
| `activo` | `true` o `false` | Todos |
| `categoria` | Nombre exacto de una de las 9 (`Párvulos`, `Cachorros`, `Semillero`, `Sub-11` … `Sub-16`), calculada con el año actual | Todas |

Un valor inválido responde `400` con todos los campos con error a la vez, por ejemplo `size: debe estar entre 1 y 100`. Una página después de la última responde `content` vacío.

### `POST` y `PUT`

Cuerpo:

```json
{"nombre": "Ana", "apellido": "Pérez", "tipoDocumento": "RC", "documento": "1098765432",
 "fechaNacimiento": "2018-04-10", "telefono": "3001234567", "direccion": "Calle 10 # 20-30",
 "posicion": "Delantera", "grupo": "tarde", "activo": true}
```

- `tipoDocumento`: `TI`, `RC`, `CE` o `PASAPORTE` (sin `CC`). `documento`: de 6 a 40 caracteres, solo dígitos salvo el pasaporte (letras y números, se guarda en mayúsculas). **Único solo por el número**, sin importar el tipo: un repetido responde `409` con `documento: ya está registrado en otro deportista`, sin decir de quién es.
- `fechaNacimiento` (`yyyy-MM-dd`): en el `POST`, o en el `PUT` si cambia, debe dar de 4 a 16 años por año de nacimiento; si no, `400` (`por año de nacimiento tiene 17 años en 2026; el club solo inscribe de 4 a 16 años`). Si en el `PUT` no cambia, no se valida: se puede editar o desactivar a quien ya se pasó de 16.
- `telefono`: 10 dígitos. `direccion`: de 5 a 250 caracteres. `posicion`: opcional, máximo 50.
- `grupo`: opcional, `mañana` o `tarde`. Solo se acepta si la categoría calculada con la fecha que queda guardada es Cachorros o Semillero; si no, `400`. `null` lo deja sin grupo.
- `activo`: en el `POST` se ignora (el deportista se crea activo); en el `PUT` es **obligatorio**.
- El `PUT` reemplaza nombre, apellido, tipo y número de documento, fecha de nacimiento, teléfono, dirección, posición, grupo y activo. No toca la foto.

### Foto

`POST /api/deportistas/{id}/foto` con `multipart/form-data` y la parte **`archivo`**:

- **JPG o PNG**, validado por los **bytes iniciales** del archivo, no por la extensión ni por el `Content-Type` del cliente: un PDF renombrado `.jpg` responde `400`.
- **Máximo 5 MB** (5 242 880 bytes; exactamente 5 MB se acepta). Vacío o más grande: `400` con `campos: ["archivo"]`. Spring deja pasar hasta 10 MB (`spring.servlet.multipart`) para que el mensaje lo dé el servicio; más de 10 MB también responde `400`.
- Se guarda en el bucket privado `fotos` (`FOTOS_BUCKET`) con la ruta `{idDeportista}/{uuid}.jpg|png` (nunca el nombre del archivo del cliente), y la ruta va a `deportista.foto`.
- Reemplazo: sube la nueva, actualiza la base y solo después borra la anterior. Si ese borrado falla, se registra y la respuesta igual es `200`.
- Responde el deportista con `foto` como URL firmada.

`DELETE /api/deportistas/{id}/foto` borra el archivo y deja `foto` en `null`. Si Storage no responde, no cambia nada (`503`) y se puede reintentar.

La lista firma las fotos de toda la página en **una sola** llamada a Storage (`POST /storage/v1/object/sign/{bucket}` con varias rutas).

## Horario del club (bloque 4A)

Plantilla fija de entrenamientos de lunes a sábado (tabla `franja_horario`, migración 007). Cada franja es de una categoría, un día, una hora de inicio y fin, y una sede; el entrenador y el grupo son opcionales. La plantilla real del club (30 franjas) se carga una vez con `supabase/seeds/franjas_club.sql` (ver `docs/GUIA-SUPABASE-EQUIPO.md`).

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET /api/horario-club` | Lista con filtros opcionales | `ADMINISTRADOR`, `TESORERO`, `ENTRENADOR`, `ACUDIENTE` | `200`, `400`, `403` |
| `GET /api/horario-club/{id}` | Consulta una franja | `ADMINISTRADOR`, `TESORERO`, `ENTRENADOR`, `ACUDIENTE` | `200`, `400`, `403`, `404` |
| `POST /api/horario-club` | Crea una franja | `ADMINISTRADOR` | `201` con `Location`, `400`, `403`, `409` |
| `PUT /api/horario-club/{id}` | Reemplaza **todos** los campos | `ADMINISTRADOR` | `200`, `400`, `403`, `404`, `409` |
| `DELETE /api/horario-club/{id}` | Elimina una franja | `ADMINISTRADOR` | `204` sin cuerpo, `400`, `403`, `404` |

Lectura para los cuatro roles; edición solo para el administrador (los demás reciben `403`). Un `{id}` que no es UUID responde `400`. En `PUT` y `DELETE`, una franja que no existe responde `404` **antes** de validar los datos del cuerpo (un cuerpo que ni siquiera es JSON legible sí responde `400` primero, porque Spring lo lee antes de llegar al controlador).

### Filtros de `GET /api/horario-club`

| Parámetro | Valores | Si es inválido |
| :--- | :--- | :--- |
| `categoria` | Nombre exacto de una de las 9 (`Párvulos`, `Cachorros`, `Semillero`, `Sub-11` … `Sub-16`) | `400` (`categoria: debe ser una de Párvulos, …, Sub-16`) |
| `entrenador` | UUID del entrenador | `400` (`El parámetro 'entrenador' tiene un valor inválido`) |
| `sede` | `Horizontes` o `UVA El Paraíso` (exacto, con tilde) | `400` (`sede: debe ser Horizontes o UVA El Paraíso`) |

Son combinables; vacíos cuentan como ausentes. `categoria` y `sede` inválidos a la vez salen juntos en un solo `400`. La lista va ordenada por día, hora de inicio, categoría (orden oficial, por edad) e id.

### Cuerpo (`POST` y `PUT`)

```json
{"idCategoria": "6f1c2a9e-…", "dia": 2, "horaInicio": "16:30", "horaFin": "18:00",
 "sede": "UVA El Paraíso", "idEntrenador": null, "grupo": "tarde"}
```

- `idCategoria`: obligatorio; si no existe, `400` en `idCategoria`.
- `dia`: obligatorio, de 1 (lunes) a 6 (sábado).
- `horaInicio` y `horaFin`: obligatorias, **`HH:mm` de 24 horas** con dos dígitos (`07:30`, `19:00`). `7:30`, `07:30:00` o `24:00` responden `400` con `debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00`. `horaFin` debe ser posterior a `horaInicio`.
- `sede`: `Horizontes` o `UVA El Paraíso`.
- `idEntrenador`: opcional. **`null` = "por turnos"** (sin entrenador fijo: el club rota quién dirige, como en Párvulos, Cachorros y Semillero). Si viene, el entrenador debe existir (`400` en `idEntrenador`) y estar **activo**. El estado solo se revisa en el `POST` y en el `PUT` que **cambia** el entrenador: un `PUT` que conserva el mismo entrenador se acepta aunque se haya desactivado después, para poder corregir las horas de esa franja.
- `grupo`: opcional, `mañana` o `tarde`, y solo en Cachorros y Semillero (en otra categoría, `400`: `solo se asigna en Cachorros y Semillero; la franja es de Sub-12`).

Todos los errores de validación salen juntos en un solo `400`, con `campos`.

**Limitación conocida:** un `dia` decimal (por ejemplo `2.5`) no da error: Jackson lo trunca a `2`. El frontend siempre manda enteros. Un `dia` como texto (`"lunes"`) o un UUID mal formado sí responden `400` nombrando el campo.

### Respuesta

```json
{"idFranja": "0b8e1f6a-…", "idCategoria": "6f1c2a9e-…", "categoria": "Sub-12", "dia": 1,
 "horaInicio": "19:00", "horaFin": "20:00", "sede": "Horizontes",
 "idEntrenador": "e0a1…", "entrenador": "Luis Pérez", "entrenadorActivo": true, "grupo": null}
```

- Horas siempre en `HH:mm`.
- Del entrenador solo van su nombre completo y `entrenadorActivo`; ningún otro dato personal. Sin entrenador ("por turnos"), `idEntrenador`, `entrenador` y `entrenadorActivo` son `null`.

### Cruces

- **El mismo entrenador** no puede tener dos franjas que se solapen el mismo día. Lo decide la restricción `franja_cruce_entrenador_ex` de la base (sin consulta previa, así que dos peticiones al mismo tiempo tampoco dejan un cruce): responde **`409`** con el mensaje exacto **`Ese entrenador ya tiene otra franja que se cruza con este horario`** y `campos: ["idEntrenador"]`.
- **Las franjas contiguas no se cruzan:** una que termina a las 19:00 y otra que empieza a las 19:00 se aceptan.
- En un `PUT`, la franja no choca consigo misma.
- **Categorías distintas sí pueden coincidir** en día, hora y sede, y las franjas "por turnos" (`idEntrenador` null) nunca chocan entre sí.

Pendiente (bloque 5): al desactivar un entrenador, sus franjas se conservan con él (salen con `entrenadorActivo: false`); qué hacer con ellas se decide con el alta de entrenadores.

## Usuarios y roles (HU-004)

La API administra las cuentas de acceso y sus roles (`administrador`, `tesorero`, `entrenador` y `acudiente`). Al crear un usuario, el backend crea la cuenta en Supabase Auth mediante la invitación por correo y luego registra su perfil en `public.usuario` y `public.usuario_rol`. La llave `SUPABASE_SERVICE_ROLE_KEY` solo se usa en el backend.

| Método y ruta | Descripción | Respuestas |
| :--- | :--- | :--- |
| `GET /api/usuarios` | Lista usuarios y su rol | `200` |
| `GET /api/usuarios/yo` | Consulta el perfil del usuario autenticado (exige el 2FA completado) | `200`, `403`, `404` |
| `GET /api/usuarios/{id}` | Consulta un usuario | `200`, `400`, `404` |
| `POST /api/usuarios` | Crea e invita un usuario | `201`, `400`, `409` |
| `PUT /api/usuarios/{id}/rol` | Cambia el rol | `200`, `400`, `404`, `409` |
| `POST /api/usuarios/{id}/revocar` | Revoca el acceso sin borrar la cuenta | `200`, `404`, `409` |
| `POST /api/usuarios/{id}/restablecer` | Restablece el acceso | `200`, `404`, `409` |

El cuerpo de creación contiene `nombre`, `apellido`, `correo` y `rol`. Los errores de validación y conflictos incluyen `campos`; un correo existente responde `409` con `campos: ["correo"]`. La API no permite dejar el sistema sin un administrador activo.

> **Supabase:** la migración `002_modelo_datos.sql` añade `public.usuario.apellido` para conservar nombres y apellidos por separado. Ejecútala antes de usar `POST /api/usuarios` sobre una base existente y confirma que `SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` estén configuradas.

## Verificación 2FA (HU-002)

La API genera códigos aleatorios de seis dígitos, conserva únicamente un hash con sal en `codigo_verificacion` y los envía mediante el evento `CODIGO_2FA` de n8n. Cada código vence en 10 minutos, permite cinco intentos, bloquea durante 15 minutos al quinto intento fallido y limita el reenvío a uno por minuto.

| Método y ruta | Descripción | Respuestas |
| :--- | :--- | :--- |
| `POST /api/auth/2fa/codigo` | Genera y envía el código | `202`, `400`, `404`, `409`, `503` |
| `POST /api/auth/2fa/reenviar` | Reenvía respetando el minuto de espera | `200`, `400`, `404`, `409`, `503` |
| `POST /api/auth/2fa/verificar` | Valida el código de seis dígitos y marca como verificada la sesión del token | `200`, `400`, `404`, `409` |
| `POST /api/auth/logout` | Cierra la sesión del token: deja de estar verificada (las demás del usuario no cambian) | `204`, `401`, `403` |

Las migraciones `003_codigo_verificacion_2fa.sql`, `005_seguridad_sesion_2fa.sql` y `006_sesion_verificada_por_sesion.sql` deben ejecutarse en Supabase, en orden, antes de probar estos endpoints. Nunca se devuelve ni se guarda el código en texto plano; la respuesta solo incluye el correo enmascarado, la vigencia y el tiempo para reenvío. Los errores 2FA incluyen `codigo`, `campos` y, cuando aplica, `msRestantes`.

## Entrenadores (HU-025)

API para registrar, consultar, editar, activar y desactivar entrenadores, y para asignarles categorías (tarea 335, CU-10). Reemplaza los datos en memoria de `frontend/src/services/entrenadoresService.js`. La fuente de verdad de la base de datos son las migraciones de [`supabase/migrations/`](../supabase/migrations/), en orden; las tablas `entrenador` y `categoria` las ajusta [`004_ajustes_modelo.sql`](../supabase/migrations/004_ajustes_modelo.sql). Lo que todavía no encaja está en [Pendientes](#pendientes--diferencias-con-el-frontend-y-el-esquema).

**Permisos:** `/api/entrenadores` solo para `ADMINISTRADOR`; `GET /api/categorias` para `ADMINISTRADOR` y `ENTRENADOR` (`@PreAuthorize` en cada controlador, ver [Seguridad](#seguridad)).

| Capa | Clase | Qué hace |
| :--- | :--- | :--- |
| `api.controllers` | `EntrenadorController`, `CategoriaController` | HTTP; `201` con `Location` al crear |
| `api.dto` | `EntrenadorRequestDTO`, `EntrenadorResponseDTO`, `AsignarCategoriasRequestDTO`, `CambiarEstadoEntrenadorRequestDTO`, `CategoriaResponseDTO`, `EntrenadorMapper` | Lo que viaja por la API |
| `services.facade` | `IEntrenadorService` | `listar`, `consultar`, `crear`, `editar`, `asignarCategorias`, `cambiarEstado`, `listarCategorias` |
| `services.impl` | `EntrenadorServiceImpl` | Limpia y valida, revisa duplicados y aplica las reglas de categorías; `@Transactional` |
| `domain.rules` | `ReglasEntrenador` | Limpieza, validaciones, categorías oficiales y tipos de documento |
| `domain.entities` | `Entrenador`, `Categoria` | Entidades (no salen por la API) |
| `persistence.dao` / `impl` | `IEntrenadorDAO` / `EntrenadorDAOImpl`, `ICategoriaDAO` / `CategoriaDAOImpl` | SQL con `NamedParameterJdbcTemplate` |

### Endpoints

| Método y ruta | Qué hace | Respuestas |
| :--- | :--- | :--- |
| `GET /api/entrenadores` | Lista ordenada por apellido y nombre, con sus categorías | `200` |
| `GET /api/entrenadores/{id}` | Un entrenador | `200`, `400` (el id no es UUID), `404` |
| `POST /api/entrenadores` | Registra los datos personales (sin categorías) | `201` con `Location: /api/entrenadores/{id}`, `400`, `409` |
| `PUT /api/entrenadores/{id}` | Edita los datos personales; **no** cambia las categorías | `200`, `400`, `404`, `409` |
| `PUT /api/entrenadores/{id}/categorias` | Deja al entrenador con exactamente esas categorías | `200`, `400`, `404`, `409` |
| `PATCH /api/entrenadores/{id}/estado` | Activa o desactiva (`{"activo": false}`). Desactivar deja sin entrenador sus categorías | `200`, `400`, `404` |
| `GET /api/categorias` | Las categorías en el orden oficial, cada una con su entrenador (o `null`) | `200` |

### Forma de datos

Los mismos nombres de campo que el frontend, más `fechaNacimiento` y `direccion`, que están en la tabla:

```json
{
  "id": "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b",
  "nombre": "Luis",
  "apellido": "Pérez",
  "tipoDocumento": "CC",
  "documento": "1098765432",
  "fechaNacimiento": "1990-05-14",
  "telefono": "3001112233",
  "correo": "luis.perez@pradofc.co",
  "especialidad": "Porteros",
  "categorias": ["Semillero", "Sub-12"],
  "horarioTrabajo": "Lunes a viernes, 3:30 p. m. a 7:30 p. m.",
  "direccion": null,
  "activo": true
}
```

| Campo | Columna | Nota |
| :--- | :--- | :--- |
| `id` | `id_entrenador` | **UUID** (texto). El frontend hoy usa números (`1`, `2`) |
| `tipoDocumento` | `tipo_documento` | **Código** de la base: `CC`, `CE` o `PASAPORTE` (ver la tabla de abajo). La tabla también permite `TI`, pero no se acepta para entrenadores |
| `documento` | `documento_identidad` | Texto: conserva los ceros iniciales. El pasaporte puede tener letras y se guarda en mayúsculas |
| `fechaNacimiento` | `fecha_nacimiento` | `AAAA-MM-DD` |
| `categorias` | `categoria.id_entrenador` | Nombres, en el orden oficial: `Párvulos`, `Cachorros`, `Semillero` y de `Sub-11` a `Sub-16`. Solo en las respuestas |
| `horarioTrabajo`, `direccion` | `horario_trabajo`, `direccion` | Opcionales. Vacíos se guardan y se devuelven como `null` (el frontend usaba `''`) |
| `activo` | `activo` | Solo en las respuestas; se cambia con `PATCH /api/entrenadores/{id}/estado`. Un inactivo conserva sus datos, pero no dirige categorías |

Al crear o editar, si el cuerpo trae `id`, `categorias` o `activo`, **se ignoran**. Las columnas `documentos` e `id_usuario` no se leen ni se cambian (la cuenta de usuario es de CU-06).

**Equivalencia de tipos de documento (para Felipe).** La pantalla muestra el texto y la API recibe y devuelve el código:

| Texto en la pantalla (`TIPOS_DOCUMENTO`) | Código en la API y en la base |
| :--- | :--- |
| `Cédula de ciudadanía` | `CC` |
| `Cédula de extranjería` | `CE` |
| `Pasaporte` | `PASAPORTE` |
| *(no está en la pantalla)* Tarjeta de identidad | `TI`: **se rechaza** (`400`), porque es de menores de edad y un entrenador tiene al menos 18 años |

```js
// Sugerencia para entrenadoresService.js
export const TIPOS_DOCUMENTO = [
  { codigo: 'CC', texto: 'Cédula de ciudadanía' },
  { codigo: 'CE', texto: 'Cédula de extranjería' },
  { codigo: 'PASAPORTE', texto: 'Pasaporte' },
]
```

### Validaciones

El servicio primero **quita los espacios sobrantes** (al principio, al final y los repetidos dentro de los textos de una línea). Pasa a mayúsculas el tipo de documento y el número de pasaporte, y a minúsculas el correo. Después valida (`ReglasEntrenador`):

| Campo | Regla | Mensaje |
| :--- | :--- | :--- |
| `nombre`, `apellido` | Obligatorios, máximo 100 caracteres | `es obligatorio` / `debe tener como máximo 100 caracteres` |
| `tipoDocumento` | `CC`, `CE` o `PASAPORTE` (sin distinguir mayúsculas). `TI` se rechaza | `debe ser CC, CE o PASAPORTE` / `TI no se acepta: la tarjeta de identidad es de menores de edad` |
| `documento` con `CC` o `CE` | Solo dígitos, de 6 a 12 | `solo puede tener números, sin puntos ni espacios` / `debe tener entre 6 y 12 dígitos` |
| `documento` con `PASAPORTE` | Letras sin tilde y dígitos, de 6 a 12, sin espacios ni signos. Se guarda en mayúsculas: `ab123456` y `AB123456` son el mismo documento | `solo puede tener letras y números, sin espacios ni otros signos` / `debe tener entre 6 y 12 caracteres` |
| `telefono` | Exactamente 10 dígitos | `debe tener exactamente 10 dígitos, sin espacios` |
| `correo` | `algo@dominio.ext`, sin espacios (la misma revisión que el frontend), máximo 320 | `no es válido` |
| `especialidad` | Obligatoria, máximo 100 | `es obligatoria` |
| `fechaNacimiento` | Obligatoria (la tabla la exige), no futura, al menos 18 años cumplidos hoy en Colombia | `no puede ser una fecha futura` / `el entrenador debe tener al menos 18 años` |
| `direccion`, `horarioTrabajo` | Opcionales, máximo 250 | `debe tener como máximo 250 caracteres` |

Todos los errores salen juntos en un `400`. Los mensajes nunca repiten el valor recibido.

**Duplicados.** El mismo tipo **y** número de documento, o el mismo correo sin distinguir mayúsculas, en **otro** entrenador, responden `409`. Al editar, el entrenador no choca consigo mismo. El mensaje dice qué campo se repite, nunca de quién: primero el documento y después el correo, como el formulario. Si dos peticiones pasan la revisión al mismo tiempo, la restricción `unique` de la tabla rechaza la segunda, y el DAO la traduce al mismo `409` (`DatoDuplicadoException`).

### Categorías

Según las decisiones **D1** y **D7**, cada categoría tiene **como máximo un entrenador**, y las migraciones 004 y 007 crean las 9 categorías del club (Párvulos, Cachorros, Semillero y Sub-11 a Sub-16) sin entrenador. `PUT /api/entrenadores/{id}/categorias` recibe la lista **completa**:

```json
{ "categorias": ["Semillero", "Sub-12"], "reasignar": true }
```

- **Tomar una categoría sin entrenador** la asigna directamente.
- **Tomar una categoría de otro entrenador** la pasa de ese entrenador a este. Exige `"reasignar": true`. Sin él, la respuesta es `409` y dice cuáles tienen otro entrenador, por ejemplo `Semillero ya tiene otro entrenador. Confirma la reasignación para pasarla a este entrenador.` La pantalla puede pedir la confirmación y reenviar con `"reasignar": true`.
- **Quitar una categoría** (no enviarla) la deja **sin entrenador**. Una lista vacía le quita todas.
- Un **entrenador inactivo** no puede recibir categorías: `409` con `El entrenador está inactivo. Actívalo antes de asignarle categorías.` y `campos: ["activo"]`.
- Un nombre que no es categoría del club (`Sub-99`, `sub-12`, `Sub-9`) responde `400`. Un nombre oficial que no está en la tabla responde `400` con `Sub-14 todavía no está creada en la base de datos` (no debería pasar después de la migración 004).
- El orden y los repetidos de la lista no importan.
- Todo pasa en **una transacción**: las categorías se bloquean (`SELECT ... FOR UPDATE`) mientras se decide, así que dos asignaciones al mismo tiempo no se pisan, y un `409` no deja cambios a medias.

**Desactivar** (`PATCH /api/entrenadores/{id}/estado` con `{"activo": false}`) deja sin entrenador todas sus categorías, en la misma transacción, y conserva sus datos. **Reactivarlo** (`{"activo": true}`) no le devuelve las categorías: se asignan de nuevo. Pedir el estado que ya tiene no cambia nada. Sin `activo` en el cuerpo, `400`.

`GET /api/categorias`:

```json
[
  { "id": "0b6c3f1e-9d2a-4c8b-a1e7-5f4d3c2b1a09", "nombre": "Sub-12", "edadMinima": 12, "edadMaxima": 12,
    "entrenador": { "id": "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b", "nombre": "Andrés", "apellido": "Mora" } }
]
```

Del entrenador solo van el id, el nombre y el apellido: ni documento, ni teléfono, ni correo. Una categoría sin entrenador llega con `"entrenador": null`.

### Ejemplos

**Registrar:**

```http
POST /api/entrenadores
Content-Type: application/json

{ "nombre": " Luis ", "apellido": "Pérez", "tipoDocumento": "CC", "documento": "1098765432",
  "fechaNacimiento": "1990-05-14", "telefono": "3001112233", "correo": "Luis.Perez@pradofc.co",
  "especialidad": "Porteros", "horarioTrabajo": "", "direccion": "Calle 40 Sur 75b-81" }
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/entrenadores/6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b

{ "id": "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b", "nombre": "Luis", "apellido": "Pérez", "tipoDocumento": "CC",
  "documento": "1098765432", "fechaNacimiento": "1990-05-14", "telefono": "3001112233",
  "correo": "luis.perez@pradofc.co", "especialidad": "Porteros", "categorias": [],
  "horarioTrabajo": null, "direccion": "Calle 40 Sur 75b-81" }
```

**Errores** (formato de [Formato de errores](#formato-de-errores-para-el-frontend), con `campos` para marcar el formulario):

```json
{ "timestamp": "2026-10-29T15:02:11.481Z", "estado": 400, "error": "Bad Request",
  "mensaje": "fechaNacimiento: es obligatoria; telefono: debe tener exactamente 10 dígitos, sin espacios",
  "ruta": "/api/entrenadores", "campos": ["fechaNacimiento", "telefono"] }
```

```json
{ "timestamp": "2026-10-29T15:03:40.127Z", "estado": 409, "error": "Conflict",
  "mensaje": "documento: ya está registrado en otro entrenador",
  "ruta": "/api/entrenadores", "campos": ["documento"] }
```

| Código | Cuándo | `campos` |
| :--- | :--- | :--- |
| `400` | Validaciones de arriba; fecha con otro formato (`fechaNacimiento: tiene un valor o un formato inválido`); JSON mal formado; id que no es UUID; categoría desconocida o sin crear | Los campos con error (no en JSON mal formado ni en id inválido) |
| `404` | No existe un entrenador con ese id | — |
| `409` | Documento o correo de otro entrenador; categorías de otro sin `reasignar`; categorías quitadas | `["documento"]`, `["correo"]`, `["documento", "correo"]` o `["categorias"]` |

En el frontend, el `409` de duplicado se lleva al formulario como hoy (`error.duplicado.campo`): `campo = error.response.data.campos[0]`.

### Lo que debe adaptar Felipe en el frontend

Para cambiar `entrenadoresService.js` por la API (Axios), sin tocar el backend:

1. **Una categoría tiene como máximo un entrenador (D1).** Desmarcarla la deja sin entrenador. Hoy el formulario permite varios entrenadores con la misma categoría.
   - Si se marca una categoría que tiene otro entrenador, el `409` trae el mensaje. La pantalla debe pedir confirmación y reenviar con `"reasignar": true`. Las que no tienen entrenador se asignan sin confirmar.
   - Registrar con categorías son **dos llamadas**: `POST /api/entrenadores` y después `PUT /api/entrenadores/{id}/categorias`. Si la segunda falla, el entrenador queda registrado sin categorías y se puede reintentar.
   - La API no exige al menos una categoría (`categoriasMinimo`); si la pantalla la quiere, es solo del formulario.
2. **`fechaNacimiento` es obligatoria** (`AAAA-MM-DD`, no futura, 18 años o más). El formulario no la pide todavía: sin ella, todo registro responde `400`.
3. **Ids UUID** (texto), no números. `Number(id)` ya no sirve: compara los ids como texto. `ID_ENTRENADOR_EJEMPLO = 1` (en `entrenamientosService.js`) tampoco servirá con datos reales.
4. **Códigos de documento:** se envían y se reciben `CC`, `CE` o `PASAPORTE`, con la equivalencia de [Forma de datos](#forma-de-datos). `TI` no se acepta (`400`).
5. **Pasaporte alfanumérico:** con `PASAPORTE`, el documento puede tener letras y números, de 6 a 12, sin espacios. Llega en mayúsculas. Con `CC` y `CE` siguen solo dígitos. Hay que cambiar `validarDocumento` (`utils/validaciones.js`), que hoy pide solo dígitos para todos.
6. **Activo e inactivo:** cada entrenador llega con `activo`. Para desactivar o reactivar: `PATCH /api/entrenadores/{id}/estado` con `{"activo": false}` o `true`. Conviene mostrar los inactivos aparte (o con una etiqueta) y no ofrecerles categorías: asignárselas responde `409` con `campos: ["activo"]`.
7. **Categorías sin entrenador:** en `GET /api/categorias`, `entrenador` puede ser `null`.
8. **`campos` en los errores:** los `400` del servicio y los `409` traen `campos`, la lista de campos con error o en conflicto. Con ella se marca el formulario: el duplicado que hoy es `error.duplicado.campo` pasa a ser `error.response.data.campos[0]`, y el mensaje para mostrar sigue en `mensaje`. El formulario usa sus propios textos para los duplicados (`textoDuplicado`), así que puede seguir con ellos.
9. **Sesión en todas las llamadas:** todo endpoint (salvo `GET /api/health`) exige `Authorization: Bearer <access_token de Supabase>`. Conviene un interceptor de Axios que lo agregue desde `supabase.auth.getSession()`, y `withCredentials: true` para la cookie de "Recordar este equipo". Un `401` significa sesión vencida (volver al inicio de sesión); un `403`, que el rol no alcanza. Ver [Seguridad](#seguridad).
10. **2FA sin correo en el cuerpo:** `POST /api/auth/2fa/codigo` y `/reenviar` van **sin cuerpo**, y `POST /api/auth/2fa/verificar` lleva solo `{"codigo": "012345"}`. Las tres con el token del primer paso.

### Pendientes / diferencias con el frontend y el esquema

- **`fecha_nacimiento` es obligatoria en la tabla, pero la pantalla no la pide** (punto 2 de arriba).
- **No se borran entrenadores**, solo se desactivan, para conservar su historial (decisión D8).
- **Entrenamientos de un entrenador desactivado:** cuando exista el módulo de entrenamientos (HU-026), hay que decidir qué pasa con sus entrenamientos `PROGRAMADO` futuros. Está marcado con `// TODO HU-026` en `EntrenadorServiceImpl.cambiarEstado`.
- **Ids UUID** en la base y la API, **números** en el frontend (punto 3).
- **`tipoDocumento` va por código** (punto 4). La tabla permite `TI`, pero la API la rechaza para entrenadores: todos son mayores de edad.
- `documentos` (carpeta de documentos) e `id_usuario` (cuenta de acceso, CU-06) no los maneja esta historia.
- Fuera del alcance: validar cruces de `horario_trabajo` (es texto libre) y crear la cuenta del entrenador (CU-06).

## Comprobante de pago (PDF)

`util.reports` genera el comprobante de un pago en PDF (HU-018, tarea 327), con [OpenPDF](https://github.com/LibrePDF/OpenPDF) 3.0.5 (licencia LGPL 2.1 / MPL 2.0). `util.reports` **solo lo genera en memoria** y devuelve los bytes. Guardarlo en Supabase Storage y firmar el enlace lo hace `IComprobanteService` (tarea 328, ver [Almacenamiento de comprobantes](#almacenamiento-de-comprobantes-supabase-storage)). Enviarlo por n8n y el estado de cuenta en PDF son de otras historias.

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
                pago.getFechaPago(),                         // LocalDate, ni futura ni posterior a la emisión
                MetodoPago.desdeEtiqueta(pago.getMetodo()),  // "Efectivo", "Transferencia", "PSE", "Nequi", "Tarjeta"
                pago.getReferenciaWompi());                  // obligatoria en PSE, Nequi o Tarjeta; null en pagos manuales
        return comprobantes.generar(datos);                  // o IComprobanteService.generarYGuardar(datos): sube y devuelve la ruta
    }
}
```

Si un dato no es válido, `generar()` lanza `ComprobanteInvalidoException` con todos los errores (`campo: mensaje`, sin los valores). Desde un endpoint, el manejador global responde `400`:

```json
{ "estado": 400, "mensaje": "Los datos del comprobante no son válidos: concepto: es obligatorio; monto: debe ser mayor que 0", ... }
```

Reglas: `numeroComprobante` (máximo 40 caracteres), `fechaEmision`, `nombreDeportista`, `nombreAcudiente` (máximo 120), `concepto` (máximo 200), `fechaPago` y `metodo` son obligatorios. `monto` debe ser mayor que 0. `fechaPago` no puede ser posterior a hoy ni a la fecha de emisión, las dos en hora de Colombia. `referenciaWompi` (máximo 100) es **obligatoria** en los pagos en línea (PSE, Nequi o Tarjeta), porque todos llegan por el webhook de Wompi, y se **rechaza** en los manuales (Efectivo o Transferencia).

**Número de comprobante.** Formato: `FD-AAAA-NNNNNN`, con el año en que se asigna el número y un consecutivo de 6 dígitos, por ejemplo `FD-2026-000123`. El generador no lo inventa ni comprueba que sea único (ver *Pendientes*).

### Qué lleva y qué no

| Lleva | No lleva, a propósito |
| :--- | :--- |
| Número y fecha de emisión | Quién registró el pago |
| Deportista, categoría (si hay) y acudiente | Nota interna |
| Concepto, fecha y método de pago | Referencia del pago manual |
| Referencia Wompi (obligatoria en pagos en línea) | Documento de identidad del deportista (es menor de edad) |
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
| `CLUB_CORREO` | `pradofutbolclub@gmail.com` | Igual |

- Si una variable queda vacía (`CLUB_NIT=`), esa línea no se imprime.
- `backend/.env` se lee en ISO-8859-1: para escribir tildes ahí usa `ó` y similares (ver `.env.example`), o define la variable en el sistema.

### Pendientes

- **Diagrama 02-pagos (propuesta, no aplicada).** Cambiar `IGeneradorComprobante +generarPdf(Pago) byte[]` por `+generar(ComprobantePagoDTO) byte[]`. Razón: el generador no debe recibir la entidad `Pago`. Con el DTO no depende de la base de datos (se prueba sin ella) y solo recibe lo que puede ver el acudiente: así no puede imprimir por error quién registró el pago, la nota interna ni la referencia manual, que sí están en el pago.
- **Consecutivo del comprobante.** El número `FD-AAAA-NNNNNN` lo genera la base de datos con `select siguiente_numero_comprobante()` (secuencia de la migración 004), así es único aunque dos pagos se registren al mismo tiempo. Lo usa `PagoDAOImpl` al aprobar un cobro ([Pagos con Wompi](#pagos-con-wompi)); `GET /api/pagos/{id}/comprobante` genera el PDF al momento, sin pasar por Storage.
- **`MetodoPago`** está dentro de `ComprobantePagoDTO` (`util.reports`). Cuando exista el módulo de pagos, pasarlo a `domain.entities`: es un concepto del dominio (columna `pago.metodo`), no del PDF.
- **Texto del pie, por confirmar con el tesorero:** "Este comprobante es un soporte del pago recibido por el club. No es una factura electrónica."
- **Logo.** No hay un PNG en el repositorio, así que el comprobante sale sin logo. Cuando haya uno, se agrega en `PlantillaReportePdf`.

### Ver un ejemplo

`./mvnw verify` (o `./mvnw test -Dtest=ComprobantePdfGeneratorTest`) deja PDF de ejemplo en `target/comprobantes-ejemplo/`: `comprobante-pago-manual.pdf`, `comprobante-pago-en-linea.pdf` y `comprobante-concepto-largo.pdf`. `target/` está ignorado por Git.

## Almacenamiento de comprobantes (Supabase Storage)

Los comprobantes en PDF se guardan en **Supabase Storage**, en un bucket **privado**. Solo el backend los sube y solo el backend entrega enlaces de descarga: **URL firmadas de corta duración**, según el rol de quien pide (HU-018, tarea 328). Todo funciona y se prueba **sin Supabase**: en local la app arranca igual, y usar Storage lanza `AlmacenamientoNoConfiguradoException`.

| Clase | Capa | Qué hace |
| :--- | :--- | :--- |
| `IAlmacenamientoArchivos` | `datasource.external` | `subir(bucket, ruta, contenido, contentType)` y `urlFirmada(bucket, ruta, vigencia)` |
| `SupabaseStorageClient` | `datasource.external` | Implementación con `RestClient` contra la API REST de Storage |
| `IComprobanteService` / `ComprobanteServiceImpl` | `services` | `generarYGuardar(ComprobantePagoDTO)` (genera con `IGeneradorComprobante` y sube) y `enlaceDescarga(ruta)` |
| `SupabaseProperties` | `config` | `futdata.supabase.*` |

### Lo que debe configurar Juan Sebastián en Supabase

1. **Crear el bucket** en *Storage → New bucket*:
   - nombre `comprobantes`;
   - **Public bucket desactivado** (privado);
   - *Restrict file size* en **5 MB**;
   - *Allowed MIME types*: `application/pdf`.
2. **No crear políticas** (RLS) para ese bucket en `storage.objects`. Sin políticas, nadie con la llave `anon` ni con la sesión de un usuario puede leer, listar ni subir. El backend entra con la llave `service_role`, que salta RLS.
3. **Pasar al backend**, por un canal privado (nunca por el chat del grupo ni por el repositorio):
   - `SUPABASE_URL`: *Project Settings → API → Project URL*.
   - `SUPABASE_SERVICE_ROLE_KEY`: *Project Settings → API Keys → `service_role` / secret*.
4. Cuando exista, avisar para correr la prueba real (`SupabaseStorageContraSupabaseTest`, ver abajo).

### Variables

| Variable | Por defecto | Perfil `prod` |
| :--- | :--- | :--- |
| `SUPABASE_URL` | vacía en local | Obligatoria |
| `SUPABASE_SERVICE_ROLE_KEY` | vacía en local | Obligatoria |
| `SUPABASE_BUCKET_COMPROBANTES` | `comprobantes` | Opcional |
| `SUPABASE_VIGENCIA_URL_FIRMADA` | `5m` (de 10 s a 1 h) | Opcional |

El timeout de conexión y de lectura es de 10 s (`futdata.supabase.timeout`, en `application.yml`). Es más largo que el de n8n porque un PDF puede pesar hasta 5 MB.

**La `service_role` key nunca va al frontend.** Salta todas las políticas de acceso: quien la tenga puede leer, cambiar y borrar **cualquier** archivo y **cualquier** fila de la base de datos. Por eso:
- Solo vive en las variables de entorno del backend. Nunca en el frontend (Vercel), en n8n, en el repositorio ni en un mensaje.
- El cliente la manda en las cabeceras `Authorization: Bearer` y `apikey`. **Nunca** aparece en logs, mensajes de excepción ni `toString` (`SupabaseStorageClientTest` lo comprueba con los logs en TRACE).
- El frontend recibe solo URL firmadas que vencen en minutos. Si la llave se filtra, hay que rotarla en Supabase de inmediato.

### Rutas: qué se guarda en `Pago.recibo_ruta`

Ruta: `AAAA/FD-AAAA-NNNNNN.pdf`: la carpeta es el año **del número** del comprobante. Ejemplo: `2026/FD-2026-000123.pdf`.
- No lleva nombres, documentos ni correos.
- El servicio exige que el número tenga el formato `FD-AAAA-NNNNNN`.
- **No** exige que ese año sea el de la fecha de pago. Un pago del 31 de diciembre registrado en enero recibe un número del año nuevo y queda en esa carpeta: `FD-2026-000001` → `2026/FD-2026-000001.pdf`.

**En `Pago.recibo_ruta` se guarda la ruta, no una URL** (la migración 004 renombró `recibo_url`). Las URL firmadas vencen a los pocos minutos: guardar una no serviría. Con la ruta, cada descarga pide una URL nueva.

Reglas del cliente, que se revisan antes de llamar a Storage (`ArchivoInvalidoException`):
- solo `application/pdf`;
- de 1 byte a 5 MB;
- bucket en minúsculas;
- ruta sin barra inicial, sin `..`, sin `//`, sin barra final y solo con letras sin tilde, dígitos, `.`, `-` y `_`.

El cliente **nunca sobrescribe**: sube con `x-upsert: false`, y si la ruta ya existe lanza `ArchivoYaExisteException`.

### URL firmadas

- Duran `SUPABASE_VIGENCIA_URL_FIRMADA` (5 min). Se piden con `POST /storage/v1/object/sign/{bucket}/{ruta}`.
- Llevan `download=FD-AAAA-NNNNNN.pdf`, para que el navegador descargue el archivo con ese nombre.
- **Quien tenga la URL puede descargar el archivo hasta que venza**: solo se entrega a quien ya pasó la verificación de rol, y nunca se guarda ni se registra en logs.

### Excepciones (`AlmacenamientoException` y subclases)

| Situación | Excepción |
| :--- | :--- |
| Tipo, tamaño, bucket o ruta inválidos (no se llama a Storage) | `ArchivoInvalidoException` |
| Falta `SUPABASE_URL` o `SUPABASE_SERVICE_ROLE_KEY`; `401` / `403` (llave inválida); `404` al subir (no existe el bucket) | `AlmacenamientoNoConfiguradoException` |
| `409`: ya hay un archivo en esa ruta | `ArchivoYaExisteException` |
| `404` al firmar: no existe el archivo | `ArchivoNoEncontradoException` |
| `400`, `413` u otro `4xx` | `AlmacenamientoRechazadoException` |
| `5xx`, `429`, tiempo de espera agotado, error de red o respuesta sin `signedURL` | `AlmacenamientoNoDisponibleException` |

Algunas versiones de Storage responden `400` con el código real en el cuerpo, por ejemplo `{"statusCode": "409", "error": "Duplicate"}`. El cliente lee ese código (y los campos `code` / `error`, como `KeyAlreadyExists` o `NoSuchKey`) para elegir la excepción. Sin reintentos automáticos.

### Endpoint de descarga (pendiente)

**Todavía no existe.** Depende del módulo de pagos; la validación del JWT y los roles ya están ([Seguridad](#seguridad)). Así será:

```text
GET /api/pagos/{id}/comprobante
  1. Busca el pago (404 si no existe o no tiene comprobante).
  2. Verifica el rol con el JWT: ADMINISTRADOR o TESORERO, o el ACUDIENTE de ese deportista. Si no, 403.
  3. comprobantes.enlaceDescarga(pago.reciboUrl)  ->  responde {"url": ...} o un 302 hacia la URL firmada.
```

El `// TODO` está en `ComprobanteServiceImpl.enlaceDescarga`. El manejador global ya responde cada excepción: `ArchivoInvalidoException` → `400`, `ArchivoNoEncontradoException` → `404`, `ArchivoYaExisteException` → `409` y las demás → `503`, con un mensaje genérico (el detalle va al log). Ver [Formato de errores](#formato-de-errores-para-el-frontend).

### Prueba real (opcional)

`SupabaseStorageContraSupabaseTest` llama al Supabase de verdad. Está **desactivada por defecto** (no corre en `./mvnw verify` ni en el CI). Hace lo siguiente:
1. Genera un comprobante de ejemplo y lo sube a `pruebas/comprobante-prueba-<marca de tiempo>.pdf`.
2. Comprueba que subirlo otra vez lanza `ArchivoYaExisteException`.
3. Pide la URL firmada, descarga el archivo y compara los bytes.
4. Comprueba que firmar un archivo que no existe lanza `ArchivoNoEncontradoException`.
5. Al final borra el archivo de prueba.

```powershell
$env:FUTDATA_PRUEBA_SUPABASE_REAL = "true"
$env:SUPABASE_URL = "https://tu-ref.supabase.co"
$env:SUPABASE_SERVICE_ROLE_KEY = "..."
.\mvnw.cmd test -Dtest=SupabaseStorageContraSupabaseTest
```

Lee las variables del sistema, no `backend/.env`. `SUPABASE_BUCKET_COMPROBANTES` es opcional.

### Pendientes

- **Proyecto de Supabase y bucket** (Juan Sebastián, lista de arriba). Después, correr la prueba real.
- **Módulo de pagos:** la columna `pago.recibo_ruta` (migración 004) guarda la ruta. `PagoServiceImpl` llamará a `generarYGuardar` al registrar o aprobar un pago (`// TODO` en `ComprobanteServiceImpl`).
- **Endpoint `GET /api/pagos/{id}/comprobante`** (ver arriba), con el módulo de pagos.

## Pagos con Wompi

Cobros del club (mensualidad, inscripción, torneo y uniforme) y pagos en línea con [Wompi](https://docs.wompi.co) (sandbox por ahora). Capas: `PagoController` y `WompiWebhookController` → `IPagoService` / `IPagoWompiService` → `IPagoDAO` (JDBC) y `IWompiClient`. Migración [`009_pagos_wompi.sql`](../supabase/migrations/009_pagos_wompi.sql).

**Reglas que no se negocian:** el monto sale siempre de la base de datos (el cliente nunca lo envía); el webhook solo aprueba si `amount_in_cents` es igual a `monto × 100`; ninguna llave sale en logs, respuestas ni errores; un acudiente que consulta o inicia el pago de un deportista que no es suyo recibe `404`, no `403`.

### Variables de entorno

Ver la tabla de [Variables de entorno](#variables-de-entorno) y `backend/.env.example`. Sandbox: llaves `pub_test_`, `prv_test_`, `test_events_`, `test_integrity_` con `https://sandbox.wompi.co/v1`. Producción: llaves `_prod_` con `https://production.wompi.co/v1`. **Nunca se mezclan**: si las llaves no son del ambiente de `WOMPI_BASE_URL`, los endpoints responden `503` como si faltaran. Sin llaves la aplicación arranca igual y los endpoints de Wompi responden:

```json
{ "estado": 503, "mensaje": "Los pagos en línea no están configurados. Contacta al administrador.", "codigo": "PAGOS_NO_CONFIGURADOS", ... }
```

### Endpoints

| Método y ruta | Quién | Qué hace |
| :--- | :--- | :--- |
| `POST /api/pagos` | `ADMINISTRADOR` | Crea un cobro `PENDIENTE`. Una mensualidad repetida del mismo deportista y periodo: `409` |
| `GET /api/pagos` | `ADMINISTRADOR`, `TESORERO` | Paginado: `page` (desde 0), `size` (1 a 100), `sort` (`vencimiento`, `monto`, `creadoEn` o `estado`, con `,asc` o `,desc`); filtros `estado`, `tipo`, `idDeportista`, `periodo` (`AAAA-MM`). Respuesta `{ content, totalElements, number, size }`; cada cobro trae `vencido` |
| `GET /api/pagos/mis-pagos` | `ACUDIENTE` | Los cobros de sus deportistas, con estado y `tieneComprobante`; sin nota interna ni datos de Wompi |
| `POST /api/pagos/{id}/iniciar-wompi` | `ACUDIENTE` dueño | Solo `PENDIENTE`. Referencia `FD-<id del pago>`, **reutilizada** si el cobro ya la tiene |
| `POST /api/pagos/{id}/reintentar` | `ACUDIENTE` dueño | Solo `RECHAZADO`: crea un `PENDIENTE` nuevo con los mismos datos; idempotente |
| `POST /api/pagos/webhook/wompi` | Wompi (pública) | Ver [Webhook](#webhook) |
| `POST /api/pagos/{id}/conciliar` | `ADMINISTRADOR`, `TESORERO` | Consulta a Wompi las transacciones de la referencia y aplica **las mismas reglas** del webhook |
| `POST /api/pagos/{id}/aprobar` | `ADMINISTRADOR`, `TESORERO` | Pago manual: `fechaPago` y `metodo` (`EFECTIVO` o `TRANSFERENCIA`) obligatorios |
| `POST /api/pagos/{id}/rechazar` | `ADMINISTRADOR`, `TESORERO` | Rechaza un cobro `PENDIENTE` (nota opcional) |
| `GET /api/pagos/{id}/comprobante` | dueño, `ADMINISTRADOR`, `TESORERO` | PDF (`application/pdf`) solo de un cobro `APROBADO` |
| `POST /api/pagos/generar-mensualidades` | `ADMINISTRADOR` | `{ "periodo": "2026-11" }`: crea la mensualidad `PENDIENTE` de cada deportista que no la tenga |

```bash
# Crear una mensualidad (administrador)
curl -X POST http://localhost:8080/api/pagos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"idDeportista":"<uuid>","tipo":"MENSUALIDAD","monto":90000,"fechaVencimiento":"2026-11-05","periodo":"2026-11-01"}'

# Datos del checkout (acudiente)
curl -X POST http://localhost:8080/api/pagos/<id>/iniciar-wompi -H "Authorization: Bearer $TOKEN"
```

```json
{
  "llavePublica": "pub_test_...",
  "referencia": "FD-7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11",
  "montoEnCentavos": 9000000,
  "moneda": "COP",
  "firmaIntegridad": "5e6bc69a...",
  "urlRedireccion": "http://localhost:5173/acudiente/pagos"
}
```

El frontend abre el Web Checkout con esos valores: `https://checkout.wompi.co/p/?public-key=...&currency=COP&amount-in-cents=...&reference=...&signature:integrity=...&redirect-url=...`. La `redirect-url` solo informa al usuario; **el pago se confirma por el webhook**. La firma de integridad es `SHA-256(<referencia><montoEnCentavos><moneda><secretoIntegridad>)` y se calcula siempre en el servidor.

### Webhook

`POST /api/pagos/webhook/wompi` es público (sin JWT, ver `RutasSin2FA`). Orden: (1) revisa la configuración y la firma **antes de abrir la base de datos**; (2) firma inválida, ausente o cuerpo ilegible: `401` y no se toca nada; (3) con firma válida **siempre `200`** (si no, Wompi reintenta a los 30 min, 3 h y 24 h); (4) solo procesa `transaction.updated`; (5) todo cambio de estado ocurre en una transacción con el cobro tomado `FOR UPDATE`.

**Firma.** El servicio lee `signature.properties` del propio evento (rutas dentro de `data`, en ese orden), concatena sus valores, agrega `timestamp` y el secreto de eventos, calcula SHA-256 en hexadecimal y lo compara en tiempo constante (`MessageDigest.isEqual`) con el encabezado `X-Event-Checksum` **y** con `signature.checksum`. La marca de tiempo es el campo `timestamp` del evento (así lo documenta Wompi); si no viniera, usa `signature.timestamp`.

**Máquina de estados** (E = estado del cobro, T = transacción del evento; la implementa `AplicadorTransaccionWompi`, que usan el webhook y la conciliación):

| T | E | Qué pasa |
| :--- | :--- | :--- |
| `APPROVED` | `PENDIENTE` | Monto igual: `APROBADO` con `fecha_pago` (hoy en Colombia), método (mapeo de abajo), `id_transaccion_wompi`, número de comprobante `FD-AAAA-NNNNNN` y aviso al acudiente ("Tu pago de {concepto} fue aprobado"). Monto distinto: no se aprueba, queda `PENDIENTE` y se audita `PAGO_MONTO_DISTINTO` |
| `APPROVED` | `RECHAZADO` | El reintento del checkout aprobó después de un rechazo. Si hay un hermano ya `APROBADO` (mismo deportista y tipo, y mismo periodo o concepto): no cambia nada y se audita `PAGO_POSIBLE_DUPLICADO`. Si no: primero los hermanos `PENDIENTE` pasan a `RECHAZADO` ("Reemplazado: el pago original fue aprobado por reintento", auditoría `PAGO_REEMPLAZADO`) y luego se aprueba como arriba (el índice único de mensualidad exige ese orden) |
| `APPROVED` | `APROBADO` | Mismo `id_transaccion_wompi`: evento repetido, se ignora. Otro id: auditoría `PAGO_POSIBLE_DUPLICADO`, sin cambios |
| `DECLINED`, `ERROR`, `VOIDED` | `PENDIENTE` | `RECHAZADO` y aviso ("Tu pago fue rechazado, puedes intentarlo de nuevo"). **No crea** un cobro nuevo: eso lo hace `/reintentar` |
| `VOIDED` | `APROBADO` (mismo id de transacción) | `RECHAZADO` y auditoría `PAGO_ANULADO_WOMPI` |
| otro `DECLINED`/`ERROR`/`VOIDED` | `RECHAZADO` o `APROBADO` | Se ignora |
| `PENDING` | cualquiera | No hace nada |

**Reintento del checkout.** Si el primer intento falla, el checkout de Wompi ofrece 3 minutos para reintentar con otro medio **con la misma referencia**, y notifica cada transacción por separado (cada una con su ID y su medio). Es normal recibir un `DECLINED` y luego un `APPROVED` con la misma referencia: por eso el cobro `RECHAZADO` puede pasar a `APROBADO`.

**Medios de pago** (`payment_method_type` → `pago.metodo`): `CARD` → `TARJETA`, `NEQUI` → `NEQUI`, `PSE` → `PSE`, `BANCOLOMBIA_QR` → `QR`, `BANCOLOMBIA_TRANSFER` → `BOTON_BANCOLOMBIA`, cualquier otro (`DAVIPLATA`, `PCOL`...) → `WOMPI_OTRO`, para no perder nunca un pago aprobado. Todos exigen referencia Wompi cuando el cobro está aprobado; `EFECTIVO` y `TRANSFERENCIA` son manuales.

### Aprobación manual y conciliación

- `aprobar` solo acepta `EFECTIVO` y `TRANSFERENCIA`; los pagos en línea los confirma Wompi (o `conciliar`). Un cobro que **ya se inició en Wompi** (tiene referencia) no se aprueba a mano: `409`, porque un pago en línea posterior quedaría sin cobro al cual aplicarse. Se concilia o se rechaza primero.
- `conciliar` consulta `GET {WOMPI_BASE_URL}/transactions?reference=...` con `WOMPI_PRIVATE_KEY` **antes** de abrir la transacción (no se mantiene un bloqueo mientras se espera la red) y aplica la máquina de estados a cada transacción de esa referencia. Sirve cuando el webhook no llegó o llegó incompleto. Si Wompi no responde: `503`.

### Cobros mensuales automáticos

`POST /api/pagos/generar-mensualidades` y, si `FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA=true`, un proceso diario a las 6:00 (hora de Colombia) crean la mensualidad `PENDIENTE` del mes de cada deportista que no la tenga (los rechazados no cuentan), con el monto y el día de vencimiento configurados (si el mes es más corto, el último día). Son idempotentes. **Pendiente:** el club debe definir `FUTDATA_MENSUALIDAD_MONTO`; en `0` no se genera nada. Hoy se generan para **todos** los deportistas porque la tabla no tiene un estado activo/inactivo (ver el bloque 2B en `docs/PRESENTACION-TECNICA.md`).

### Probar en sandbox

1. Crea una cuenta en el panel de Wompi y copia las llaves de pruebas a `backend/.env`: `WOMPI_PUBLIC_KEY` (`pub_test_`), `WOMPI_PRIVATE_KEY` (`prv_test_`), `WOMPI_EVENTS_SECRET` (`test_events_`) y `WOMPI_INTEGRITY_SECRET` (`test_integrity_`).
2. Abre un túnel público hacia el puerto 8080 (por ejemplo `ngrok http 8080` o `cloudflared tunnel --url http://localhost:8080`).
3. En el panel de Wompi, ambiente de pruebas, *Desarrolladores → Seguimiento de transacciones*, pon como URL de eventos `https://<tu-túnel>/api/pagos/webhook/wompi`. **Cada ambiente tiene su propia URL de eventos.**
4. Crea un cobro (`POST /api/pagos`), inicia el pago como acudiente (`iniciar-wompi`), abre el checkout y paga con los datos de prueba.

| Medio | Dato de prueba | Resultado |
| :--- | :--- | :--- |
| Tarjeta | `4242 4242 4242 4242` | `APPROVED` |
| Tarjeta | `4111 1111 1111 1111` | `DECLINED` |
| Tarjeta | cualquier otra | `ERROR` |
| Nequi | `3991111111` | `APPROVED` |
| Nequi | `3992222222` | `DECLINED` |
| PSE | el banco que aprueba / el banco que rechaza | `APPROVED` / `DECLINED` |
| Bancolombia QR | el estado se elige en el simulador | `APPROVED`, `DECLINED` o `ERROR` |

Las pruebas automáticas **nunca llaman a Wompi**: el cliente se simula y los checksums se calculan en la propia prueba. Los eventos reales se pueden repetir desde el panel de Wompi.

### Pendientes

- **Monto de la mensualidad.** Lo define el club (`FUTDATA_MENSUALIDAD_MONTO`).
- **Reintento del checkout.** Confirmar en el panel de Wompi si viene activo o hay que activarlo. El backend ya soporta el caso (mismo cobro, otra transacción), pero sin esa opción no se verá un `DECLINED` seguido de un `APPROVED`.
- **Estado del deportista.** Cuando exista la columna de activo/inactivo (bloque 2B), la generación mensual debe filtrar por ella.
- **Comprobante en Storage.** El PDF se genera al pedirlo (con la fecha de emisión igual al día del pago, para que no cambie entre descargas). `pago.recibo_ruta` no se usa; si se quiere guardar el PDF, `IComprobanteService.generarYGuardar` ya existe.
- **Aviso por correo.** Los avisos de pago se guardan en `notificacion_pago`; el envío por n8n queda para el módulo de avisos.

## Seguridad

Spring Security valida en cada petición el JWT que emite Supabase Auth (RF008, RNF001). Está en `util.security`:

| Clase | Qué hace |
| :--- | :--- |
| `SeguridadConfig` | Rutas públicas y protegidas, sin sesión de servidor (stateless), sin CSRF, CORS y `@EnableMethodSecurity` |
| `JwtValidator` | Valida la firma con las llaves públicas de Supabase, el vencimiento, el emisor y la audiencia; convierte el usuario en sus roles |
| `RespuestasSeguridad` | `401` y `403` con el [formato de errores](#formato-de-errores-para-el-frontend) de la API |
| `IUsuarioDAO` / `UsuarioDAOImpl` | Roles del usuario (`usuario_rol`), solo si su cuenta está activa |

**Cómo llega el token.** El frontend inicia sesión con Supabase Auth (`supabase.auth.signInWithPassword`) y envía el `access_token` en cada petición:

```text
Authorization: Bearer eyJhbGciOiJFUzI1NiIs...
```

**Qué se valida.** La firma, con las llaves de `{SUPABASE_URL}/auth/v1/.well-known/jwks.json` (ES256 o RS256), y que el token no esté vencido, que lo haya emitido `{SUPABASE_URL}/auth/v1` y que su audiencia sea `authenticated`. Si algo falla: `401`, sin decir qué (el motivo va al log).

> [!IMPORTANT]
> **El proyecto de Supabase debe firmar con llaves asimétricas** (Project Settings → JWT Keys / JWT Signing Keys). Si todavía usa el secreto compartido antiguo (HS256), el JWKS está vacío y **todo token responde `401`**. Juan Sebastián o quien administre el proyecto lo puede migrar desde el panel; los tokens viejos siguen sirviendo hasta que vencen.

**Roles.** El `sub` del token es `usuario.id_usuario`. Los roles salen de `usuario_rol` en cada petición (`ROLE_ADMINISTRADOR`, `ROLE_TESORERO`, `ROLE_ENTRENADOR`, `ROLE_ACUDIENTE`), así que quitar un rol o desactivar la cuenta (`usuario.activo = false`) surte efecto en la siguiente petición. Un usuario sin perfil en `usuario` o sin roles queda autenticado pero sin permisos: `403`. Cada controlador exige sus roles con `@PreAuthorize`:

| Ruta | Roles |
| :--- | :--- |
| `GET /api/health`, `/error`, `OPTIONS` (CORS), `POST /api/pagos/webhook/wompi` | Públicas, sin token (el webhook se autentica con la firma del evento) |
| Swagger (`/swagger-ui.html`, `/v3/api-docs`) | Pública **solo con el perfil `dev`**; en los demás perfiles está apagado |
| `POST /api/auth/2fa/codigo`, `/reenviar`, `/verificar` | Cualquier rol, con el JWT del primer paso y sin haber verificado el código |
| `POST /api/auth/logout`, `GET /api/usuarios/yo` | Cualquier rol, con la sesión verificada |
| `/api/usuarios/**` (salvo `/yo`) | `ADMINISTRADOR` |
| `/api/deportistas/**` (incluidos `PUT` y `/foto`) | `ADMINISTRADOR`; `GET /api/deportistas` (la lista) también `ENTRENADOR`, sin documento, teléfono, dirección ni estado de la inscripción |
| `/api/entrenadores/**` | `ADMINISTRADOR` |
| `POST /api/pagos`, `POST /api/pagos/generar-mensualidades` | `ADMINISTRADOR` |
| `GET /api/pagos`, `POST /api/pagos/{id}/conciliar`, `/aprobar`, `/rechazar` | `ADMINISTRADOR`, `TESORERO` |
| `GET /api/pagos/mis-pagos`, `POST /api/pagos/{id}/iniciar-wompi`, `/reintentar` | `ACUDIENTE` (solo sus deportistas; de otro, `404`) |
| `GET /api/pagos/{id}/comprobante` | `ADMINISTRADOR`, `TESORERO`, y el `ACUDIENTE` del deportista |
| `GET /api/categorias` | `ADMINISTRADOR`, `ENTRENADOR` |
| Cualquier otra ruta | Exige sesión; cada módulo nuevo declara sus roles |

**CORS con credenciales.** Se permiten cookies para la cookie HttpOnly de "Recordar este equipo" (HU-002): en Axios, `withCredentials: true`. Por eso `CORS_ORIGINS` tiene que tener orígenes exactos, nunca `*`.

**Probar desde Swagger.** Con los perfiles `local,dev`, abrir `/swagger-ui.html`, botón **Authorize** y pegar el `access_token` (sin la palabra `Bearer`). Un token se obtiene iniciando sesión en el frontend (en las herramientas del navegador, `localStorage` → `sb-...-auth-token`).

**En las pruebas.** `@WithMockUser(roles = "ADMINISTRADOR")` en las pruebas de MockMvc (el filtro de 2FA solo revisa los JWT). `SeguridadTest` recorre la cadena completa con roles y sesiones verificadas simulados, y `JwtValidatorTest` firma tokens de verdad con llaves generadas en la prueba y un JWKS local.

**Segundo paso (2FA).** `/api/auth/2fa/codigo`, `/reenviar` y `/verificar` exigen el JWT del primer paso y toman el usuario del `sub` del token: el cuerpo ya **no lleva el correo** (en `verificar` solo va `{"codigo": "012345"}`). Así nadie puede pedir ni probar códigos de otra cuenta.

**La verificación es por sesión.** Al verificar el código, `sesion_verificada` (migración 006) guarda el claim `session_id` del token con una vigencia de 12 horas (`ReglasVerificacion.VIGENCIA_SESION`). Así, verificar en el celular no habilita una sesión abierta en otro equipo. `SesionVerificadaFilter`, después de validar el JWT:
1. Sin `session_id` (o si no es un UUID): `401`.
2. Sin ningún rol en la base de datos (cuenta sin perfil, sin rol o con el acceso revocado): `403`, también en las rutas del 2FA.
3. Fuera de las rutas sin 2FA, si la sesión del token no está verificada o venció: `403` con `"codigo": "2FA_REQUERIDA"`.

Las rutas sin 2FA están todas en `util.security.RutasSin2FA`, en una sola lista documentada: las tres del segundo paso, el webhook de Wompi, `GET /api/health`, `/error` y `OPTIONS`; con el perfil `dev`, también la documentación. **Agregar una ruta ahí la abre:** hay que justificarla.

`POST /api/auth/logout` borra la sesión del token; el frontend también debe cerrar la sesión en Supabase Auth. Revocar el acceso de un usuario (`POST /api/usuarios/{id}/revocar`) borra todas sus sesiones verificadas: si se le restablece, debe verificar el código otra vez.

## Formato de errores (para el frontend)

Todas las respuestas de error tienen el mismo JSON:

```json
{
  "timestamp": "2026-10-28T23:15:30.123Z",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "edad: debe ser mayor que o igual a 5; nombre: no debe estar vacío",
  "ruta": "/api/entrenadores"
}
```

| Campo | Contenido |
| :--- | :--- |
| `timestamp` | Momento del error, ISO 8601 en UTC |
| `estado` | Código HTTP (igual al de la respuesta) |
| `error` | Nombre estándar del código HTTP, en inglés (`Bad Request`, `Not Found`...) |
| `mensaje` | Explicación en español que se puede mostrar al usuario. En validación: `campo: mensaje`, separados por `; ` y ordenados |
| `ruta` | Ruta de la petición, sin la cadena de consulta |
| `campos` | Solo en los errores de campos concretos (`400` del servicio o de un valor con formato inválido, y `409`): lista ordenada de los campos con error o en conflicto, para marcarlos en el formulario. Si no aplica, no aparece |

| Código | Cuándo | `mensaje` de ejemplo |
| :--- | :--- | :--- |
| `400` | Bean Validation falla, el JSON está mal formado o un valor no tiene el formato del campo (por ejemplo, una fecha), falta un parámetro o tiene un tipo inválido, o el servicio lanza `DatosInvalidosException`, `ComprobanteInvalidoException` o `ArchivoInvalidoException` | `nombre: no debe estar vacío` |
| `401` | Sin token, o con un token inválido o vencido. Cabecera `WWW-Authenticate: Bearer` | `Debes iniciar sesión. Si ya la iniciaste, venció: inicia sesión de nuevo.` |
| `403` | La cuenta no tiene el rol que pide la ruta, o está inactiva | `No tienes permiso para hacer esta acción.` |
| `404` | La ruta no existe, el servicio lanza `RecursoNoEncontradoException` o el archivo no está en Storage (`ArchivoNoEncontradoException`) | `No existe la ruta GET /api/no-existe` |
| `409` | El servicio lanza `ConflictoException` (un dato que ya tiene otro registro, o una regla que depende de otros registros), el archivo ya existe en Storage, o la base de datos rechaza la operación por una restricción que el servicio no tradujo | `documento: ya está registrado en otro entrenador` |
| `405` / `415` | Método HTTP no permitido o tipo de contenido no soportado | `El método POST no está permitido en esta ruta` |
| `500` | Cualquier error no controlado, o n8n mal configurado. **Nunca incluye la traza ni el mensaje interno**; el detalle queda en el log del servidor | `Ocurrió un error interno. Intenta de nuevo más tarde.` |
| `503` | Supabase Storage, n8n o Wompi no respondieron o no están configurados (mensaje genérico; el detalle va al log). Sin llaves de Wompi lleva `"codigo": "PAGOS_NO_CONFIGURADOS"`. En `/api/health`, la base de datos no responde (su cuerpo es el de salud, no este) | `No se pudo enviar el correo. Intenta de nuevo más tarde.` |

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

No necesitan credenciales ni red (salvo `N8nClientContraN8nLocalTest` y `SupabaseStorageContraSupabaseTest`, desactivadas por defecto): cargan `src/test/resources/pruebas.properties`, que tiene prioridad sobre `backend/.env`, y simulan la base de datos con Mockito o con dobles en memoria.

Las pruebas de los DAO (`EntrenadorDAOImplTest`, `EntrenadorServiceImplConPostgresTest`) usan un **PostgreSQL real en Docker** con [Testcontainers](https://testcontainers.com/):
- Antes de aplicar **todas** las migraciones de `supabase/migrations/` en orden de nombre (`PostgresDePrueba.migraciones()`; una nueva entra sola), crean un esquema `auth` mínimo (`auth.users(id uuid primary key)`), porque las migraciones lo referencian.
- Registran las categorías que crea la 004 (lo revisa `laMigracionCreaLasCincoCategoriasSinEntrenador`) y después cada prueba inserta sus propias categorías y entrenadores.
- **Sin Docker se omiten** con el aviso *Docker no está disponible: se omiten las pruebas de integración con PostgreSQL* y el build pasa igual (`DockerDisponible`). En el CI sí corren.
- **Sin Docker, con un PostgreSQL local:** define `FUTDATA_PRUEBA_DB_URL` (y si hace falta `FUTDATA_PRUEBA_DB_USER` y `FUTDATA_PRUEBA_DB_PASSWORD`) con una base **desechable**. Las pruebas borran sus esquemas `public` y `auth`, así que solo se acepta `localhost`, nunca Supabase:

  ```powershell
  $env:FUTDATA_PRUEBA_DB_URL = "jdbc:postgresql://localhost:5432/futdata_pruebas"
  $env:FUTDATA_PRUEBA_DB_PASSWORD = "..."
  .\mvnw.cmd verify
  ```

| Prueba | Qué revisa |
| :--- | :--- |
| `FutDataApplicationTests` | El contexto arranca sin base de datos y sin JPA; propiedades de n8n (timeout 5 s, token oculto en `toString`); sin el perfil `dev` no hay OpenAPI ni Swagger UI |
| `DocumentacionPerfilDevTest` | Con el perfil `dev`, OpenAPI (con `/api/health` y `/api/auth/logout`) y Swagger UI se publican sin token |
| `SesionVerificadaDAOImplTest` | Contra PostgreSQL: cada sesión se verifica por separado, el `sub` de otro usuario no sirve, vencimiento, logout de una sola sesión, revocación de todas, y la migración 006 aplicada sobre una base con la 005 y filas |
| `NumeroEnLetrasTest`, `FormatoColombianoTest` | Montos en letras (1, 21, 100, 101, 1.000, 150.000, 1.000.000, 1.250.500...), pesos, fechas en hora de Colombia, NIT y celular |
| `ComprobantePdfGeneratorTest` | Texto del PDF leído con PDFBox: datos, monto en número y en letras, fechas, club (y líneas vacías), tildes y ñ, concepto largo, metadatos sin datos personales, que no salgan la nota ni quién registró, y DTO inválido (incluida la referencia Wompi obligatoria en línea y la fecha de pago posterior a la emisión). Deja ejemplos en `target/comprobantes-ejemplo/` |
| `ComprobanteConfiguracionTest` | Valores por defecto de `futdata.club` y el generador como bean |
| `SupabaseStorageClientTest` | Peticiones a Storage (URL, método, `Authorization`, `apikey`, `x-upsert`, cuerpo), cada respuesta (`200`, `400`, `401`, `403`, `404`, `409`, `413`, `5xx`, timeout, red), validaciones de tipo, tamaño y ruta, y que la llave ni la URL firmada salgan en logs (TRACE) ni excepciones |
| `SupabaseStorageClientConfiguracionTest` | Sin `SUPABASE_URL` ni la llave en local, la app arranca y usar Storage lanza `AlmacenamientoNoConfiguradoException`; valores por defecto |
| `ComprobanteServiceImplTest` | Generar y guardar con dobles de prueba: ruta `AAAA/FD-AAAA-NNNNNN.pdf`, formato y año del número, bucket y vigencia del enlace |
| `SupabaseStorageContraSupabaseTest` | Opcional y desactivada: contra un Supabase real (ver [Almacenamiento](#almacenamiento-de-comprobantes-supabase-storage)) |
| `EnvExampleTest` | Los datos del club de `.env.example` se leen con tildes (escapes `\u`) |
| `ReglasEntrenadorTest` | Limpieza de espacios, tipo de documento y correo; obligatorios; `TI` rechazada; documento de 6 a 12 dígitos con `CC` y `CE`, y de 6 a 12 letras y números en mayúsculas con `PASAPORTE`; teléfono de 10; correo; fecha de nacimiento no futura y 18 años; largos de la tabla; orden oficial de las categorías |
| `EntrenadorServiceImplTest` | Con dobles en memoria de los DAO: crear y editar (datos limpios, `400` con todos los campos), duplicados de documento y correo sin datos del otro, la carrera traducida a `409`, `404`, y las reglas de categorías (reasignar, quitar, desconocidas, sin crear, lista vacía) |
| `EntrenadorServiceImplConPostgresTest` | Servicio con los DAO reales en PostgreSQL: reasignación, `409` sin cambios a medias y correo en minúsculas |
| `EntrenadorDAOImplTest` | SQL contra PostgreSQL con la migración 001: crear, buscar, listar ordenado con categorías, existencia de documento y correo, traducción de las restricciones `unique`, actualizar sin tocar `documentos` ni categorías, `FOR UPDATE` y reasignación |
| `EntrenadorControllerTest` | `/api/entrenadores` y `/api/categorias` con MockMvc: JSON de respuesta, `201` con `Location`, `400` (UUID inválido, fecha con otro formato, JSON mal formado, validación con `campos`), `404` y `409` |
| `DockerDisponibleTest` | Sin Docker la prueba se omite con el aviso; con Docker corre |
| `SaludControllerTest` | `GET /api/health`: `200` con la base de datos arriba, `503` con la base de datos abajo |
| `SaludDAOImplTest` | `SELECT 1` con timeout de 2 s; `false` si no hay conexión |
| `ManejadorGlobalErroresTest` | Formato uniforme para `400` (con `campos` en los del servicio y en un valor con formato inválido), `404`, `405`, `409` y `500`, sin traza |
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
- La imagen trae un `HEALTHCHECK` que comprueba que el puerto HTTP acepta conexiones (no consulta `/api/health`: sin base de datos ese endpoint responde `503` aunque la aplicación esté viva).
- Para levantar el backend junto con n8n con un solo comando (`docker compose up -d --build` desde la raíz), ver [Levantar todo con un solo comando](../README.md#87-levantar-todo-con-un-solo-comando).
