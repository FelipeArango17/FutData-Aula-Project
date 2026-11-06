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
| `N8N_TIMEOUT_ENLACE` | Tiempo de espera solo para `ENLACE_CONTRASENA`, que es síncrono (los demás eventos usan 5 s) | `15s` | Igual |
| `FRONTEND_URL` | URL pública del frontend, sin barra final. Arma el enlace del correo: `{FRONTEND_URL}/crear-contrasena?token=...` ([Cuenta de acceso](#cuenta-de-acceso-y-enlace-para-crear-la-contraseña-bloque-5)) | `http://localhost:5173` | Obligatoria (la URL de Vercel) |
| `SUPABASE_URL` | URL del proyecto de Supabase: [Storage](#almacenamiento-de-comprobantes-supabase-storage) y las llaves públicas para validar los JWT ([Seguridad](#seguridad)) | vacía: la app arranca, pero no guarda comprobantes y rechaza todo token (`401`) | Obligatoria |
| `SUPABASE_SERVICE_ROLE_KEY` | Llave `service_role` para Storage. **Secreta, solo backend** | vacía: la app arranca, pero no guarda comprobantes | Obligatoria |
| `SUPABASE_BUCKET_COMPROBANTES`, `SUPABASE_VIGENCIA_URL_FIRMADA` | Bucket de comprobantes y vigencia de las URL firmadas | `comprobantes`, `5m` | Igual |
| `WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY`, `WOMPI_EVENTS_SECRET`, `WOMPI_INTEGRITY_SECRET` | Llaves de Wompi ([Pagos con Wompi](#pagos-con-wompi)). **Secretas** (menos la pública) | vacías: la app arranca, pero los endpoints de Wompi responden `503 PAGOS_NO_CONFIGURADOS` | Opcionales (sin ellas no hay pagos en línea) |
| `WOMPI_BASE_URL`, `WOMPI_REDIRECT_URL` | URL de la API de Wompi y a dónde vuelve el usuario tras pagar | sandbox `https://sandbox.wompi.co/v1`; sin redirección | Igual |
| `FUTDATA_MENSUALIDAD_MONTO`, `FUTDATA_MENSUALIDAD_DIA_VENCIMIENTO`, `FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA` | Cobro mensual: monto en pesos (**pendiente: lo define el club**), día de vencimiento y si se genera solo cada día | `0` (sin configurar), `5`, `false` | Igual |
| `FUTDATA_COBROS_INSCRIPCION_MONTO`, `FUTDATA_COBROS_UNIFORME_MONTO` | Cobros de ingreso por ciclo: pesos de la inscripción y del uniforme ([Cobros por ciclo de ingreso](#cobros-por-ciclo-de-ingreso)) | `150000`, `90000` | Igual |
| `FUTDATA_COBROS_DIAS_ANTICIPACION`, `FUTDATA_COBROS_VENTANA_INGRESO_DIAS` | Días de anticipación del cobro del ciclo y días desde el registro en que se cobran inscripción y uniforme | `5`, `7` | Igual |
| `N8N_CRON_TOKEN` | Secreto de la tarea diaria de n8n para `POST /api/pagos/generar-ciclos` (cabecera `X-Cron-Token`). **Secreto.** Sin ella esa vía queda deshabilitada | vacía | **Opcional** (no la exige el perfil `prod`) |
| `FOTOS_BUCKET`, `FOTOS_VIGENCIA_URL_FIRMADA` | Bucket de las fotos de los deportistas y vigencia de su URL firmada, de 10 s a 60 min ([Foto](#foto)) | `fotos`, `60m` | Igual |
| `DOCUMENTOS_BUCKET`, `DOCUMENTOS_VIGENCIA_URL_FIRMADA` | Bucket de los documentos de la inscripción y vigencia de su URL firmada de descarga, de 10 s a 60 min ([Documentos](#documentos-de-la-inscripción-bloque-3)). Son documentos de menores: la vigencia es corta | `documentos`, `300s` | Igual |
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
| `categoria` | Nombre exacto de una de las 9 (`Párvulos`, `Cachorros`, `Semillero`, `Sub-11` … `Sub-16`), calculada con el año actual. Se puede **repetir** (`categoria=Sub-11&categoria=Sub-12`) o separar con comas | Todas (para el `ENTRENADOR`, las suyas) |

Un valor inválido responde `400` con todos los campos con error a la vez, por ejemplo `size: debe estar entre 1 y 100`. Una página después de la última responde `content` vacío.

#### Qué ve cada rol (bloque 7)

| Rol | Resultado de `GET /api/deportistas` |
| :--- | :--- |
| `ADMINISTRADOR` | Todos los deportistas (también quien queda fuera de 4 a 16 años), con `documento`, `telefono`, `direccion` y `estadoInscripcion`. `categoria` filtra por esos nombres |
| `ENTRENADOR` | **Solo los de las categorías que tiene a cargo** (`categoria.id_entrenador` = su fila de `entrenador`, hallada por `entrenador.id_usuario` = `sub` del token), sin `documento` ni `estadoInscripcion`. Sin `categoria`: todas las suyas. Con `categoria`: se cruza con las suyas |
| `TESORERO`, `ACUDIENTE` | `403` |

Reglas del entrenador: pedir una categoría que existe pero **no es suya** da `200` con `content` vacío y `totalElements` 0 (no `403`); un nombre que no existe da `400`; sin categorías asignadas (o sin fila de entrenador) la lista sale vacía; quien queda fuera de 4 a 16 años no sale; un entrenador inactivo conserva sus categorías. La categoría se compara con la **calculada por edad** (no con `deportista.id_categoria`) y la regla se resuelve en SQL, así que `totalElements` y `totalPages` ya reflejan el filtro. El servicio falla cerrado: un rol que no sea `ADMINISTRADOR` ni `ENTRENADOR` nunca obtiene la lista completa. La ficha `GET /api/deportistas/{id}` sigue siendo solo del `ADMINISTRADOR`.

```bash
# Entrenador con Sub-11 y Sub-12 asignadas: pidiendo Sub-11 y Sub-13 solo salen los de Sub-11
curl "http://localhost:8080/api/deportistas?categoria=Sub-11&categoria=Sub-13&activo=true&size=100" \
  -H "Authorization: Bearer $TOKEN_ENTRENADOR"
# Pidiendo solo Sub-13 (existe, pero no es suya): 200 con {"content": [], "totalElements": 0, ...}
```

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

## Mis jugadores del acudiente (bloque 2C)

Lo que el acudiente ve de **sus propios** jugadores. Solo el rol `ACUDIENTE`.

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET /api/acudientes/me/deportistas` | Los jugadores vinculados al acudiente del token (arreglo, sin paginar) | `ACUDIENTE` | `200`, `401`, `403` |
| `GET /api/acudientes/me/deportistas/{id}` | La ficha de uno de ellos | `ACUDIENTE` | `200`, `400`, `401`, `403`, `404` |

**Regla de propiedad.** El acudiente sale siempre del token: el `sub` del JWT es `usuario.id_usuario`, y de ahí se llega a su fila de `acudiente` (`acudiente.id_usuario`). Ningún parámetro, cuerpo ni cabecera puede indicar de qué acudiente se trata. La propiedad se decide **en el SQL** (`JOIN acudiente_deportista` + `acudiente`, con `WHERE acudiente.id_usuario = :usuario`), no en el controlador. Un deportista que no existe y uno de otro acudiente responden igual: `404` con el mensaje "No existe un deportista con ese id." (nunca `403`).

### Lista

```json
[{"id": "6f1c2a9e-…", "nombre": "Luis", "apellido": "Gómez", "fechaNacimiento": "2018-03-03", "edad": 8,
  "categoria": "Cachorros", "idCategoria": "0a0a…", "grupo": "tarde", "posicion": "Delantero",
  "foto": "https://…/storage/v1/object/sign/fotos/…?token=…", "parentesco": "Madre", "activo": true,
  "estadoInscripcion": "PENDIENTE"}]
```

- Ordenada por apellido, nombre e id. Incluye a los jugadores inactivos (`activo: false`). Si el acudiente no tiene jugadores vinculados, o su cuenta no tiene fila en `acudiente`, responde `200` con `[]`.
- **No trae** `documento`, `tipoDocumento`, `telefono` ni `direccion`.
- `categoria` e `idCategoria` son `null` si queda fuera de 4 a 16 años; `grupo` solo en Cachorros y Semillero (si no, `null`); `foto` es una URL firmada temporal (60 min) o `null` si no tiene foto o Storage no respondió (no falla por eso).
- `parentesco` es el del vínculo con **este** acudiente, sin tilde (`Madre`, `Padre`, `Abuelo`, `Abuela`, `Tia`, `Tio`, `Otro`) o `null`. El frontend lo muestra con tilde.
- `estadoInscripcion`: `COMPLETA` o `PENDIENTE`, igual que en `/api/deportistas`.

### Ficha

Los mismos campos de la lista más `tipoDocumento`, `documento`, `telefono`, `direccion` y `documentosFaltantes`: los tipos de documento obligatorio que aún no tienen uno `APROBADO`, en el orden `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION`, `AUTORIZACION_HABEAS_DATA`. Es `[]` si y solo si `estadoInscripcion` es `COMPLETA`. El estado de cada documento y el comentario de rechazo se consultan con `GET /api/deportistas/{id}/documentos` ([Documentos](#documentos-de-la-inscripción-bloque-3)).

### Errores

| Código | Cuándo |
| :--- | :--- |
| `400` | El `{id}` no es un UUID |
| `401` | Sin token, token inválido o vencido, o un token sin el claim `session_id` |
| `403` | La cuenta no tiene el rol `ACUDIENTE`; **o** la sesión del token no completó el 2FA: en ese caso la respuesta es `403` con `codigo: "2FA_REQUERIDA"` (no `401`) |
| `404` | El deportista no existe o no es de este acudiente (mismo mensaje) |

Los errores usan el formato de [Formato de errores](#formato-de-errores-para-el-frontend).

> **Sin el bloque 2B no hay vínculos reales.** Hasta que la inscripción única cree acudientes y vínculos, ninguna cuenta real tiene filas en `acudiente` (con `id_usuario`) ni en `acudiente_deportista`, así que la lista sale vacía hasta que se vinculen a mano (SQL en Supabase) o con un script de siembra. Las pruebas (`MisJugadoresConPostgresTest`) crean sus propios datos.

## Documentos de la inscripción (bloque 3)

Subir, listar, descargar, aprobar y rechazar los documentos de un deportista (los 7 tipos de la regla 7: `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION` y `AUTORIZACION_HABEAS_DATA` son obligatorios; `PAZ_Y_SALVO`, `CARTA_RENUNCIA` y `TRASPASO_NACIONAL`, si aplican). Sin migración: usa la tabla `documento` (004 y 007) y el bucket privado `documentos` (007).

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `POST /api/deportistas/{id}/documentos` | Sube un documento (multipart: `tipo`, `archivo`, `fechaVencimiento` opcional). Queda `PENDIENTE` | `ADMINISTRADOR`, `ACUDIENTE` dueño | `201`, `400`, `401`, `403`, `404`, `409`, `503` |
| `GET /api/deportistas/{id}/documentos` | Los documentos del deportista con `estadoInscripcion` y `documentosFaltantes` | `ADMINISTRADOR`, `ACUDIENTE` dueño | `200`, `400`, `401`, `403`, `404` |
| `GET /api/documentos/{id}/descarga` | `{ url, vigenciaSegundos }`: URL firmada de vida corta, con `Cache-Control: no-store` | `ADMINISTRADOR`, `ACUDIENTE` dueño | `200`, `400`, `401`, `403`, `404`, `503` |
| `POST /api/documentos/{id}/aprobar` | Aprueba un documento `PENDIENTE` (sin cuerpo) | `ADMINISTRADOR` | `200`, `400`, `401`, `403`, `404`, `409` |
| `POST /api/documentos/{id}/rechazar` | Rechaza un documento `PENDIENTE` con `{ "comentario": "..." }` y avisa a los acudientes | `ADMINISTRADOR` | `200`, `400`, `401`, `403`, `404`, `409` |

**Quién ve qué.** Son documentos de identidad de menores: solo el `ADMINISTRADOR` y el `ACUDIENTE` dueño del deportista. El `ENTRENADOR` y el `TESORERO` reciben `403` por rol. El usuario sale siempre del token y la propiedad se decide en el SQL (`acudiente_deportista` + `acudiente.id_usuario`), como en el 2C: un deportista de otro acudiente responde `404` con el mismo mensaje que uno inexistente ("No existe un deportista con ese id."), y un documento ajeno, "No existe un documento con ese id." (nunca `403`). Aprobar y rechazar son **solo del administrador**: un acudiente, incluso el dueño, recibe `403`. El servicio falla cerrado ante cualquier rol que no sea de los dos.

### Subir

`POST /api/deportistas/{id}/documentos` con `multipart/form-data`:

- `tipo`: uno de los 7 tipos, en mayúsculas exactas.
- `archivo`: **PDF, JPG o PNG**, de hasta **5 MB** (5 242 880 bytes; exactamente 5 MB se acepta). El tipo se decide **solo por los bytes iniciales** del archivo (`%PDF-`, `FF D8 FF`, firma PNG): la extensión, el nombre y el `Content-Type` que mande el cliente se ignoran (un PNG guardado como `.jpg` o declarado como PDF se acepta y se guarda como PNG; un texto llamado `.pdf` se rechaza). El PDF se valida por su cabecera: no se revisa si es válido ni si tiene virus. Spring deja pasar hasta 10 MB (`spring.servlet.multipart`) para que el mensaje lo dé el servicio; más de 10 MB también responde `400`.
- `fechaVencimiento`: opcional, `AAAA-MM-DD`, una fecha real que no sea anterior a hoy (hora de Bogotá; hoy sí vale). Hoy solo se guarda: no hay avisos de vencimiento.

El orden es: autorización, deportista inexistente (`404`), validación (todos los errores juntos en un solo `400`), revisión rápida de si ya hay un documento activo del tipo (`409` antes de subir nada), subida a Storage **fuera de toda transacción** con la ruta `{idDeportista}/{uuid}.pdf|jpg|png` (nunca el nombre del cliente) y una transacción corta que bloquea al deportista (`select ... for no key update`), repite la revisión e inserta. El nombre original se guarda limpio (sin carpetas ni caracteres de control, hasta 255) solo como dato informativo.

- **Un solo documento activo por tipo.** No puede haber dos documentos `PENDIENTE` o `APROBADO` del mismo tipo para un deportista: el segundo da `409` con `campos: ["tipo"]`. Después de un `RECHAZADO` se puede volver a subir; el rechazado queda en el historial. Reemplazar uno `APROBADO` no entra en esta versión. Se permite subir a deportistas inactivos o con la inscripción completa (salvo esa regla).
- **Compensación.** Si algo falla después de subir (el `409` de la transacción, un error de la base) se borra el archivo de Storage y se relanza la excepción original; si borrar también falla, solo se registra el tipo de excepción. Si falla la subida a Storage no se inserta nada. Dos subidas simultáneas del mismo tipo dejan un documento y un solo archivo.
- Responde `201` con el documento.

### El documento

```json
{"id": "6f1c2a9e-…", "idDeportista": "0b3d…", "tipo": "PAZ_Y_SALVO", "nombreOriginal": "paz y salvo.pdf",
 "tipoContenido": "application/pdf", "tamanoBytes": 183204, "estado": "RECHAZADO",
 "comentario": "La imagen está borrosa.", "fechaVencimiento": null, "cargadoPor": "Acudiente",
 "cargadoPorCorreo": "familia@correo.co", "cargadoEn": "2026-11-05T15:00:00Z",
 "revisadoPor": "admin@pradofc.co", "revisadoEn": "2026-11-05T16:00:00Z"}
```

- `estado`: `PENDIENTE`, `APROBADO` o `RECHAZADO`. `comentario` es el motivo del rechazo (`null` si no está rechazado). `revisadoPor` y `revisadoEn` son `null` mientras está `PENDIENTE`.
- `cargadoPor` es `Administrador` o `Acudiente`, según el rol de quien lo subió; es `null` si esa cuenta ya no existe (igual que `cargadoPorCorreo`).
- **Nunca trae la ruta del archivo**: se descarga con la URL firmada.

### Listar y `estadoInscripcion`

`GET /api/deportistas/{id}/documentos` responde `{ "documentos": [...], "estadoInscripcion": "PENDIENTE", "documentosFaltantes": ["CERTIFICADO_ESTUDIO", ...] }`. Los documentos van del más reciente al más antiguo, **incluidos los rechazados** (historial). `estadoInscripcion` (`COMPLETA` o `PENDIENTE`) y `documentosFaltantes` (los obligatorios sin un documento `APROBADO`, en el orden de la regla; vacía si y solo si es `COMPLETA`) salen de lo que ya calcula el DAO de deportistas: **no hay columna**, así que aprobar o rechazar un documento los actualiza solos. La columna heredada `deportista.estado_inscripcion` no se lee ni se escribe (la lee `PagoDAOImpl`, solo para filtrar cobros).

### Descargar

`GET /api/documentos/{id}/descarga` devuelve `{ "url": "https://…/storage/v1/object/sign/documentos/…?token=…", "vigenciaSegundos": 300 }` con `Cache-Control: no-store`. La URL se pide **solo al descargar**; vive `DOCUMENTOS_VIGENCIA_URL_FIRMADA` (300 s por defecto, de 10 s a 60 min) y quien la tenga puede descargar el archivo hasta que venza, así que no se guarda ni se registra en ningún log. Si el archivo ya no está en el bucket responde `404` ("No se encontró el archivo.").

### Aprobar y rechazar

- `POST /api/documentos/{id}/aprobar` (sin cuerpo): responde `{ "documento": {...}, "estadoInscripcion": "COMPLETA" }`.
- `POST /api/documentos/{id}/rechazar` con `{ "comentario": "..." }`: responde `{ "documento": {...}, "estadoInscripcion": "PENDIENTE", "avisoEnviado": true }`. El comentario es obligatorio y se guarda **recortado**, de 1 a 300 caracteres (la base no lo limita; lo impone el backend) y puede traer saltos de línea. Sin cuerpo, con `{}`, vacío o solo espacios: `400` con `campos: ["comentario"]` (no un `500`).
- El orden es: autorización (`403`), documento (`404`), comentario (`400`), revisión (`409`). La revisión es `update ... where estado = 'PENDIENTE'` y la relectura del documento y de `estadoInscripcion`, en una transacción corta. Si ya no está `PENDIENTE` (lo revisó otro administrador o antes) responde `409` con `campos: ["estado"]` y no cambia nada; rechazar uno `APROBADO` también es `409`. Con dos revisiones simultáneas del mismo documento gana una sola. Quedan puestos `revisado_por` (el `sub` del administrador) y `revisado_en`.

### Aviso al rechazar (`DOCUMENTO_RECHAZADO`)

Después de confirmar el rechazo (nunca dentro de la transacción) se avisa por n8n a **cada acudiente vinculado al deportista que tenga un correo válido**, sin correos repetidos: un `notificar` por acudiente (contrato en el [contrato del webhook](../docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md)). Los `datos` son planos: `correo`, `nombreAcudiente`, `nombreDeportista`, `documento` (la etiqueta legible, por ejemplo "Documento de identidad"), `comentario` y `enlaceDocumentos` (`{FRONTEND_URL}/acudiente/documentos`). Si un nombre llega vacío se manda `Acudiente` o `tu deportista`. Aprobar no envía ningún aviso.

`avisoEnviado` en la respuesta del rechazo (la clave va siempre en el JSON, con `null` si no aplica, igual que en el horario):

| Valor | Significa |
| :--- | :--- |
| `true` | n8n aceptó todas las llamadas (no garantiza que el correo llegue) |
| `false` | No se pudo avisar: n8n falló o no respondió en 5 s, no se pudieron leer los destinatarios, falta `FRONTEND_URL` o el enlace pasa de 2000 caracteres. El rechazo **sí** quedó hecho y la respuesta sigue siendo `200`. Si una llamada falla, no se envían las demás |
| `null` | El deportista no tiene acudientes con un correo válido; no se llamó a n8n |

> **El flujo de n8n Cloud ya tiene el evento `DOCUMENTO_RECHAZADO`** (publicado y probado el 5 de noviembre, ver `docs/PRESENTACION-TECNICA.md` §9.5). Si n8n falla o no responde, el rechazo devuelve `avisoEnviado: false`. Los logs solo llevan el evento, el tipo de excepción y conteos: nunca correos, nombres, comentarios, rutas ni URL.

### Errores

| Código | Cuándo |
| :--- | :--- |
| `400` | Un `{id}` que no es UUID; en subir, tipo, archivo (vacío, de más de 5 MB o con otra firma) o `fechaVencimiento` inválidos, todos juntos en `campos`; si falla solo el archivo, `campos: ["archivo"]`; en rechazar, el comentario ausente, vacío o de más de 300 caracteres (`campos: ["comentario"]`); falta la parte `archivo` |
| `401` | Sin token, token inválido o vencido, o un token sin el claim `session_id` |
| `403` | La cuenta no tiene un rol permitido en esa ruta (el entrenador y el tesorero en las cinco; el acudiente en aprobar y rechazar); **o** la sesión del token no completó el 2FA: en ese caso la respuesta es `403` con `codigo: "2FA_REQUERIDA"` (no `401`), igual que en el 2C |
| `404` | El deportista o el documento no existe o no es del acudiente (mismo mensaje), o el archivo ya no está en el bucket |
| `409` | Subir: ya hay un documento `PENDIENTE` o `APROBADO` del tipo (`campos: ["tipo"]`). Aprobar y rechazar: el documento ya no está `PENDIENTE` (`campos: ["estado"]`) |
| `503` | Supabase Storage no respondió o no está configurado |

Los errores usan el formato de [Formato de errores](#formato-de-errores-para-el-frontend).

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

Al desactivar un entrenador, sus franjas se conservan con él y salen con `entrenadorActivo: false` (decidido en el bloque 5): el administrador decide si las reasigna.

## Horario de la semana y bloque `entrenador` en `/yo` (bloques 4B-1 y 4B-2)

Las franjas fijas del club puestas en las fechas de una semana, con sus **excepciones por fecha** (cancelaciones y extras, tabla `excepcion_horario`, migración 010), y el dato que el frontend necesita para saber sobre qué categorías actúa un entrenador.

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET /api/horario-semana?fecha=AAAA-MM-DD` | La semana de lunes a sábado (sin `fecha`, la de hoy en Bogotá), con cancelaciones y extras | los cuatro roles | `200`, `400`, `401`, `403` |
| `POST /api/horario-semana/cancelaciones` | Cancela una franja en una fecha | `ADMINISTRADOR`, `ENTRENADOR` | `201`, `400`, `401`, `403`, `404`, `409` |
| `DELETE /api/horario-semana/cancelaciones/{idExcepcion}` | Deshace una cancelación | `ADMINISTRADOR`, `ENTRENADOR` | `200`, `400`, `401`, `403`, `404` |
| `POST /api/horario-semana/extras` | Crea un entrenamiento extra | `ADMINISTRADOR`, `ENTRENADOR` | `201`, `400`, `401`, `403`, `404`, `409` |
| `DELETE /api/horario-semana/extras/{idExcepcion}` | Elimina un extra | `ADMINISTRADOR`, `ENTRENADOR` | `200`, `400`, `401`, `403`, `404` |
| `GET /api/usuarios/yo` | El perfil de siempre, más el bloque `entrenador` | cualquier rol | `200`, `401`, `403`, `404` |

**La semana.** Cualquier día sirve para pedirla. El domingo no hay entrenamientos, así que `ReglasSemana.lunesDeLaSemana` devuelve el lunes **siguiente** (igual que `semanaEnCurso` del frontend); la usan la semana por defecto, la fecha pedida y el chequeo del acudiente. "Hoy" sale del `Clock` de Bogotá. `hasta` es el sábado (lunes + 5) y la fecha de cada franja es el lunes + `dia` - 1.

```json
{"desde": "2026-11-02", "hasta": "2026-11-07", "sesiones": [
  {"tipo": "FRANJA", "estado": "ACTIVO", "idFranja": "0b8e…", "idExcepcion": null, "fecha": "2026-11-03", "dia": 2,
   "horaInicio": "16:30", "horaFin": "18:00", "idCategoria": "6f1c…", "categoria": "Semillero", "sede": "UVA El Paraíso",
   "idEntrenador": null, "entrenador": null, "entrenadorActivo": null, "grupo": "tarde", "motivo": null,
   "puedeGestionar": false}]}
```

- Ordenadas por `fecha`, `horaInicio`, categoría oficial y grupo. Los campos de la franja se llaman igual que en `/api/horario-club` (se reutiliza `IFranjaHorarioDAO.listar` y `FranjaHorarioMapper`).
- `tipo` es `FRANJA` o `EXTRA`; `estado`, `ACTIVO` o `CANCELADO`. Una franja cancelada ese día sale con `estado` `CANCELADO`, su `idExcepcion` y su `motivo` (sigue siendo `FRANJA`). Un extra sale con `idFranja` `null`, su propio `idExcepcion` y su `dia` calculado de la fecha. Una cancelación cuyo día de la semana ya no coincide con el de su franja (el 4A la cambió de día) no se aplica.
- `puedeGestionar`: el administrador, en todo; el entrenador, en las franjas de sus categorías o que él dirige y, en un extra, solo si la categoría es suya (ser el asignado no basta); el acudiente y el tesorero, nunca. Se calcula en memoria con una sola lectura de las categorías del entrenador por petición. **En una fecha anterior a hoy es `false` para todos**, administrador incluido (hoy no cuenta como pasada).
- **El acudiente solo ve la semana en curso:** otra da `403` "Solo puedes consultar la semana en curso" (`SemanaNoPermitidaException`). El rol sale de las autoridades de `Authentication`. Los demás roles piden cualquier semana.
- `fecha` se recibe como texto y se valida en el servicio: si no es una fecha real `AAAA-MM-DD`, `400` con `campos: ["fecha"]`.
- Sin la sesión 2FA verificada: `403` con `codigo: "2FA_REQUERIDA"`; `401` solo sin token, con token inválido o sin `session_id`.

### Cancelar, deshacer y extras (bloque 4B-2)

Solo `ADMINISTRADOR` y `ENTRENADOR` (acudiente y tesorero reciben `403` por rol). Todas responden `{ "sesion": …, "avisoEnviado": … }`: `201` en los `POST` y `200` en los `DELETE`. La escritura corre en una transacción que se confirma **antes** de avisar (`TransactionOperations`, sin `@Transactional` en el método). `creado_por` es el `sub` del JWT.

**Cancelar una franja.** `POST /api/horario-semana/cancelaciones`

```json
{"fecha": "2026-11-10", "idFranja": "0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b", "motivo": "Lluvia"}
```

```json
{"sesion": {"tipo": "FRANJA", "estado": "CANCELADO", "idFranja": "0b8e…", "idExcepcion": "9d41…", "fecha": "2026-11-10", "dia": 2,
            "horaInicio": "16:30", "horaFin": "18:00", "idCategoria": "6f1c…", "categoria": "Sub-12", "sede": "Horizontes",
            "idEntrenador": "e000…", "entrenador": "Luis Pérez", "entrenadorActivo": true, "grupo": null, "motivo": "Lluvia",
            "puedeGestionar": true},
 "avisoEnviado": true}
```

**Crear un extra.** `POST /api/horario-semana/extras` (`idEntrenador` `null` = "por turnos"; `grupo` y `motivo` opcionales)

```json
{"idCategoria": "6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b", "fecha": "2026-11-11", "horaInicio": "18:00", "horaFin": "19:30",
 "sede": "UVA El Paraíso", "idEntrenador": "e0000000-0000-0000-0000-00000000000a", "grupo": null, "motivo": "Partido amistoso"}
```

```json
{"sesion": {"tipo": "EXTRA", "estado": "ACTIVO", "idFranja": null, "idExcepcion": "c3a9…", "fecha": "2026-11-11", "dia": 3,
            "horaInicio": "18:00", "horaFin": "19:30", "idCategoria": "6f1c…", "categoria": "Sub-12", "sede": "UVA El Paraíso",
            "idEntrenador": "e000…", "entrenador": "Luis Pérez", "entrenadorActivo": true, "grupo": null,
            "motivo": "Partido amistoso", "puedeGestionar": true},
 "avisoEnviado": null}
```

**Deshacer y eliminar.** `DELETE /api/horario-semana/cancelaciones/{idExcepcion}` responde la franja otra vez `ACTIVO` ese día (`idExcepcion` y `motivo` en `null`). `DELETE /api/horario-semana/extras/{idExcepcion}` responde `{ "sesion": null, "avisoEnviado": … }`.

**Orden de las validaciones y errores** (el primero que falla):

| Operación | Orden |
| :--- | :--- |
| `POST` cancelaciones | cuerpo `400` (todos los campos juntos: `fecha`, `idFranja`, `motivo` máx. 200) → franja inexistente `404` → fecha pasada `400` (`campos: ["fecha"]`) → día distinto al de la franja `400` (`campos: ["fecha"]`) → permiso `403` → ya cancelada ese día `409` "Esa franja ya está cancelada en esa fecha" (`campos: ["fecha"]`) |
| `DELETE` cancelaciones | no existe o es un extra `404` → fecha pasada `400` → permiso `403` (un id que no es UUID da `400`) |
| `POST` extras | cuerpo `400` (todos los campos juntos: UUID, fecha, horas `HH:mm`, `horaFin` > `horaInicio`, sede, `grupo` solo en Cachorros y Semillero, `motivo` máx. 200) → categoría inexistente `404` → fecha pasada `400` → domingo `400` → permiso `403` → entrenador inexistente o inactivo `400` (`campos: ["idEntrenador"]`) → cruce `409` "Ese entrenador ya tiene otra franja que se cruza con este horario" (`campos: ["idEntrenador"]`) |
| `DELETE` extras | no existe o es una cancelación `404` → fecha pasada `400` → permiso `403` |

- **Fecha pasada = antes de hoy;** hoy es válido. Un extra es de lunes a sábado.
- **Permiso del entrenador, decidido en SQL** (`403` "Solo puedes modificar el horario de tus categorías"): para cancelar o deshacer, ser el entrenador de la categoría de la franja o el de la franja (`esDuenoDeFranja` / `esDuenoDeExcepcion`); una franja "por turnos" solo la gestiona el dueño de la categoría. Para crear o eliminar un **extra**, solo el dueño de la categoría (`esDuenoDeCategoria`). El administrador se salta la consulta por su rol.
- **Cruce de un extra** (solo si tiene entrenador; "por turnos" no se compara): en **una** transacción se bloquea la fila del entrenador (`SELECT … FOR UPDATE`), se busca una franja suya, del mismo día de la semana, que se solape y **no esté cancelada** esa fecha, y se inserta. El cruce con otro extra lo decide la exclusión `excepcion_horario_cruce_entrenador_ex`. Un extra en el horario de una franja cancelada ese día se permite, y tocarse en un extremo (una termina cuando empieza la otra) no es cruzarse.
- Se puede cancelar una franja de un entrenador inactivo; asignar un entrenador inactivo a un extra, no.

**`avisoEnviado` y los avisos.** Después del commit se avisa a n8n con el evento `CAMBIO_ENTRENAMIENTO` (nunca dentro de la transacción):

| Acción | `tipoCambio` | `motivo` |
| :--- | :--- | :--- |
| Cancelar una franja | `CANCELADO` | el de la cancelación |
| Deshacer una cancelación | `CREADO` | no se manda (n8n lo ignora en `CREADO`) |
| Crear un extra | `CREADO` | no se manda |
| Eliminar un extra | `CANCELADO` | el del extra (se lee antes de borrar) |

- `entrenamiento`: `categoria` (con el grupo si lo hay: `Cachorros (mañana)`), `fecha`, `horaInicio`, `lugar` (la sede) y `entrenador` (nombre y apellido, o `Por turnos`). Sin `horaFin` ni `anterior`. Los textos se recortan a 120 caracteres, el límite que valida el flujo de n8n.
- **Destinatarios:** los acudientes con correo de los deportistas **activos** de la categoría (calculada por año de nacimiento con el `Clock` de Bogotá), sin correos repetidos y sin distinguir mayúsculas; no se filtra por grupo ni se avisa al entrenador. Los correos que n8n descartaría (formato o más de 254 caracteres) no se envían.
- `true`: n8n aceptó todas las llamadas (`202`; no garantiza que el correo llegue). `false`: alguna llamada lanzó cualquier excepción (o no se pudieron leer los destinatarios); **la operación ya quedó hecha** y la respuesta sigue siendo `201` o `200`. `null`: no había a quién avisar y no se llamó a n8n.
- Más de 200 destinatarios: una llamada por bloque de 200. Si un bloque falla, los siguientes no se envían. No se reintenta. El log es un `WARN` con el evento, el tipo de excepción y el número de destinatarios, sin correos ni nombres.

**Limitaciones conocidas.**

- Deshacer una cancelación no revisa cruces con extras: si el entrenador recibió un extra en ese horario mientras la franja estaba cancelada, quedan solapados.
- El `PUT /api/horario-club/{id}` (4A) no revisa cruces con extras ya creados ni la fecha de las cancelaciones existentes (una cancelación cuyo día ya no coincide simplemente no se aplica).
- Borrar una franja borra sus cancelaciones (`on delete cascade`).
- El aviso se hace en la misma petición: si n8n tarda, la respuesta puede demorar hasta 5 s por bloque.
- Si la sesión tiene grupo, el aviso va a **todos** los acudientes de la categoría.
- Editar un extra (`PUT`, con `tipoCambio` `MODIFICADO`) no está en esta versión.

**El bloque `entrenador`.** `GET /api/usuarios/yo` responde ahora `UsuarioYoResponseDTO` (los campos de `UsuarioResponseDTO` más `entrenador`); `/api/usuarios`, `/api/usuarios/{id}` y los demás no cambian.

```json
{"id": "…", "nombre": "Luis", "apellido": "Pérez", "correo": "luis@pradofc.co", "rol": "entrenador", "activo": true,
 "creadoEn": "…", "entrenador": {"idEntrenador": "e000…", "categorias": [{"idCategoria": "6f1c…", "nombre": "Semillero"}]}}
```

- La clave `entrenador` **siempre va** en el JSON. Es `null` para ADMINISTRADOR, TESORERO y ACUDIENTE, y para un ENTRENADOR sin fila en `entrenador` vinculada por `id_usuario`.
- `categorias` son las que tiene a cargo (`categoria.id_entrenador`) en el orden oficial (`edad_minima`); `[]` si no tiene ninguna. Un entrenador inactivo recibe el bloque con las categorías que conserve.
- Consultas nuevas: `IEntrenadorDAO.buscarPorIdUsuario` e `ICategoriaDAO.listarPorEntrenador`.
- `/api/auth/2fa/verificar` sigue devolviendo el perfil sin este bloque: el frontend lo obtiene con `GET /api/usuarios/yo`.

## Asistencia (bloque 4C, versión mínima)

Pasar lista de una sesión del horario: la lista de deportistas de una franja en una fecha (o de un extra) con su estado, y guardar las marcas. **Estado:** implementado en la rama `feature/bloque-asistencia`, pendiente de merge. **La migración 012 (`supabase/migrations/012_asistencia_entrenador_opcional.sql`) está pendiente de aplicar en la base real**; las pruebas la aplican solas en su PostgreSQL. Diseño y decisiones en `docs/PRESENTACION-TECNICA.md` (9.1.2).

### Endpoints

| Método y ruta | Descripción | Roles | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET /api/asistencia/sesion?fecha=AAAA-MM-DD&idFranja=UUID` (o `&idExcepcion=UUID` para un extra) | La sesión y su lista de deportistas con lo ya guardado. **Solo lee: no crea nada** | `ADMINISTRADOR`; `ENTRENADOR` de la categoría (o que dirige la franja) | `200`, `400`, `401`, `403`, `404`, `409` |
| `PUT /api/asistencia/sesion` | Guarda las asistencias (upsert por deportista y sesión), todo o nada | Los mismos | `200`, `400`, `401`, `403`, `404`, `409` |

El `TESORERO` y el `ACUDIENTE` reciben `403` por rol, y sin la sesión 2FA verificada el `403` trae `codigo` `2FA_REQUERIDA`, como en las demás rutas protegidas. Para el `ENTRENADOR` rige la misma regla de `puedeGestionar` del horario de la semana: las franjas de **sus categorías** o que **él dirige**, y los extras solo de **sus categorías** (dirigirlos no basta). Un entrenador de otra categoría recibe `403` con "Solo puedes pasar lista de tus categorías".

### Respuesta (la misma en el `GET` y en el `PUT`)

```json
{
  "sesion": {
    "fecha": "2026-11-03",
    "horaInicio": "16:00",
    "horaFin": "17:30",
    "idCategoria": "uuid",
    "categoria": "Sub-12",
    "sede": "Horizontes",
    "grupo": null,
    "idEntrenador": "uuid",
    "entrenador": "Luis Mora"
  },
  "puedeRegistrar": true,
  "deportistas": [
    { "idDeportista": "uuid", "nombre": "Ana", "apellido": "Alfa", "estado": "PRESENTE", "observaciones": null },
    { "idDeportista": "uuid", "nombre": "Beto", "apellido": "Beta", "estado": null, "observaciones": null }
  ]
}
```

Los campos salen siempre, con `null` cuando no aplican: `grupo` (la sesión no tiene), `idEntrenador` y `entrenador` ("por turnos"), `estado` y `observaciones` (sin registrar). `idEntrenador` y `entrenador` son los **asignados** a la sesión, no quien pasó lista. `puedeRegistrar` es `true` siempre que hay `200` (el `400` y el `403` ya cubren los demás casos). `estado`: `PRESENTE`, `AUSENTE` o `JUSTIFICADO`. Los deportistas salen por apellido y nombre.

### Cuerpo del `PUT`

```json
{
  "fecha": "2026-11-03",
  "idFranja": "uuid",
  "registros": [
    { "idDeportista": "uuid", "estado": "AUSENTE", "observaciones": "Sin aviso" },
    { "idDeportista": "uuid", "estado": "PRESENTE" }
  ]
}
```

- **Sesión:** exactamente uno de `idFranja` o `idExcepcion` (un extra). Con una franja, la fecha debe caer en su día de la semana; con un extra, ser la fecha del extra.
- **`registros`:** al menos uno; puede traer solo una parte de la lista. `observaciones` es opcional (máximo 300 caracteres; se limpian los espacios y vacías quedan `null`). El `estado` se acepta en cualquier combinación de mayúsculas y minúsculas y se guarda en mayúsculas. Un deportista no puede repetirse.
- **Upsert:** se puede volver a guardar para corregir. Al corregir se actualizan el estado, las observaciones, quien pasó lista y la hora del registro. Volver a mandar un deportista sin `observaciones` **borra** las que tenía.
- **Todo o nada:** el `PUT` completo (revisar la lista, crear el entrenamiento y guardar) corre en una sola transacción. Un registro inválido no deja ninguno, ni siquiera el entrenamiento recién creado.

### Cómo se resuelve la sesión

- **La sesión es una franja en una fecha, o un extra.** No existe una tabla de "sesiones": la franja y la excepción de tipo `EXTRA` ya traen categoría, horas, sede, entrenador y grupo.
- **El entrenamiento se crea al pasar lista.** La asistencia (`asistencia`) apunta a una fila de `entrenamiento`, que el `PUT` busca por `(categoría, fecha, hora de inicio, sede)` y crea si no existe (estado `PROGRAMADO`, con la `hora_fin` de la sesión). El `GET` solo la busca: si no existe, la lista sale sin marcas.
- **`entrenamiento.id_entrenador` es el entrenador asignado** (el de la franja o el del extra; `null` si es "por turnos"). **`asistencia.id_entrenador` es quien pasó lista** (la ficha del entrenador; `null` si fue el administrador). Por eso la migración 012 hace NULLABLE las dos columnas.
- **Lista de la sesión:** deportistas **activos** de la categoría de la sesión y, si la sesión tiene grupo (`mañana` o `tarde`), solo los de ese grupo. La categoría es la **calculada por año de nacimiento** (`año de hoy − año de nacimiento` entre `edad_minima` y `edad_maxima`), el mismo criterio de `DeportistaDAOImpl`; una prueba compara las dos listas. **Limitación:** para una fecha pasada la categoría se calcula con la regla vigente **hoy**, no con la de ese día; un deportista que cambió de categoría desde entonces no aparece en la lista de la fecha pasada.
- **Fechas:** con el reloj de la aplicación (`America/Bogota`). Hoy se puede; una fecha futura da `400` en el `GET` y en el `PUT`.
- **Cancelada:** una franja con cancelación en esa fecha da `409`. Un extra no se cancela, se elimina.

### Migración 012

`supabase/migrations/012_asistencia_entrenador_opcional.sql` (dentro de `begin/commit`, idempotente, no lee ni cambia datos) solo hace dos `drop not null`:

- `entrenamiento.id_entrenador`: `NULL` = "por turnos".
- `asistencia.id_entrenador`: `NULL` = pasó lista el administrador.

No amplía ningún `unique`: el de `entrenamiento (lugar, fecha, hora_inicio)` ya no existe (lo reemplazó la 004 y la 007 quitó la exclusión por lugar), así que dos categorías a la misma hora y sede caben. Siguen vigentes `entrenamiento_cruce_categoria_ex` y `entrenamiento_cruce_entrenador_ex` (con `estado = 'PROGRAMADO'`; `NULL` no choca) y `unique (id_deportista, id_entrenamiento)` de `asistencia`, la clave del upsert.

### Entrenamiento y solape

El `PUT` crea el entrenamiento con `INSERT ... ON CONFLICT DO NOTHING` (sin objetivo, que PostgreSQL permite también para restricciones de exclusión, SQLState `23P01`). Así un conflicto no lanza error y la transacción no queda abortada. Si no se inserta nada, se vuelve a leer la fila por su clave: si existe, fue una **carrera** (otra petición creó la misma sesión) y se sigue con ella; si no existe, **otro entrenamiento se cruza de verdad** (de la misma categoría o del mismo entrenador, a otra hora) y el servicio responde `409` sin `campos`: "Esta sesión se cruza con otro entrenamiento ya registrado (de la misma categoría o del mismo entrenador): no se puede pasar lista".

### Orden de las validaciones

Para no revelar nada a quien no tiene permiso: **rol** (`403`) → **forma de la entrada** (`400`, todos los campos juntos) → **sesión** (`404` si la franja o el extra no existe; `400` con `campos: ["fecha"]` si la fecha no cae en el día de la franja o no es la del extra) → **permiso sobre la categoría** (`403`) → **fecha futura** (`400`) → **cancelada** (`409`) → **deportistas de la lista** (`400` con `campos: ["registros"]`) → crear o buscar el entrenamiento (`409` si hay solape) → guardar.

### Errores

| Código | Cuándo |
| :--- | :--- |
| `400` | Fecha ausente, inválida (`AAAA-MM-DD`) o futura (`campos: ["fecha"]`); ni `idFranja` ni `idExcepcion`, o los dos (`campos: ["idExcepcion", "idFranja"]`); un id que no es UUID; la fecha no cae en el día de la franja o no es la del extra (`campos: ["fecha"]`); `registros` vacío, con un estado inválido, observaciones de más de 300 caracteres, un `idDeportista` mal formado, repetido o que **no está en la lista de la sesión** (`campos: ["registros"]`, el mensaje dice "registro N: ..."); cuerpo ausente o JSON mal formado |
| `401` | Sin token, token inválido o vencido |
| `403` | Rol sin permiso (tesorero, acudiente), sin 2FA (`codigo` `2FA_REQUERIDA`) o un entrenador de otra categoría |
| `404` | No existe la franja ("No existe una franja con ese id.") o el extra ("No existe un extra con ese id.", también si el id es de una cancelación) |
| `409` | La franja está cancelada ese día (`campos: ["fecha"]`: "Esa sesión está cancelada ese día: no se puede pasar lista") o la sesión se cruza con otro entrenamiento (sin `campos`) |

Los errores usan el formato de [Formato de errores](#formato-de-errores-para-el-frontend).

### Fuera de esta versión

Historial de asistencia por deportista, reportes, vista del acudiente y auditoría (el proyecto solo audita pagos; pasar lista no registra eventos en `auditoria`).

### Pruebas

`ReglasAsistenciaTest`, `MigracionAsistenciaTest` y `AsistenciaDAOImplTest` (PostgreSQL real: lista, upsert, todo o nada, carrera y solape), `AsistenciaServiceImplTest` (DAO simulados: roles, orden de validaciones, permisos, grupo) y `AsistenciaConPostgresTest` (servicio y DAO reales: entrenador asignado y quien pasó lista, upsert, todo o nada con transacción real), `AsistenciaControllerTest` (HTTP con la seguridad real: forma exacta del JSON, nulos, 401, 403, 2FA, 400, 404, 409) y `AsistenciaControllerConPostgresTest` (recorrido completo por HTTP). Las que usan PostgreSQL se omiten solas sin Docker.

## Usuarios y roles (HU-004)

La API administra las cuentas de acceso y sus roles (`administrador`, `tesorero`, `entrenador` y `acudiente`). Al crear un usuario, el backend crea la cuenta en Supabase Auth mediante la invitación por correo y luego registra su perfil en `public.usuario` y `public.usuario_rol`. La llave `SUPABASE_SERVICE_ROLE_KEY` solo se usa en el backend.

| Método y ruta | Descripción | Respuestas |
| :--- | :--- | :--- |
| `GET /api/usuarios` | Lista usuarios y su rol | `200` |
| `GET /api/usuarios/yo` | Consulta el perfil del usuario autenticado (exige el 2FA completado); para ENTRENADOR trae el bloque `entrenador` ([4B-1](#horario-de-la-semana-y-bloque-entrenador-en-yo-bloques-4b-1-y-4b-2)) | `200`, `403`, `404` |
| `GET /api/usuarios/{id}` | Consulta un usuario | `200`, `400`, `404` |
| `POST /api/usuarios` | Crea e invita un usuario | `201`, `400`, `409` |
| `PUT /api/usuarios/{id}/rol` | Cambia el rol | `200`, `400`, `404`, `409` |
| `POST /api/usuarios/{id}/revocar` | Revoca el acceso sin borrar la cuenta | `200`, `404`, `409` |
| `POST /api/usuarios/{id}/restablecer` | Restablece el acceso | `200`, `404`, `409` |

El cuerpo de creación contiene `nombre`, `apellido`, `correo` y `rol`. Los errores de validación y conflictos incluyen `campos`; un correo existente responde `409` con `campos: ["correo"]`. La API no permite dejar el sistema sin un administrador activo.

> **Corregido (bloque 5): revocar y restablecer estaban rotos.** `ISupabaseAuthAdmin.cambiarAcceso` llamaba a Supabase Auth con `PATCH /auth/v1/admin/users/{id}`, pero esa ruta solo acepta `GET`, `PUT` y `DELETE`: toda llamada fallaba y `POST /api/usuarios/{id}/revocar` y `/restablecer` nunca bloquearon ni restauraron una cuenta. Ahora usa `PUT` con `{"ban_duration": "876000h"}` (bloquear) o `{"ban_duration": "none"}` (restaurar), y traduce las respuestas: `404` → `CuentaAuthNoEncontradaException`; `401`, `403`, `429`, `5xx` y tiempo agotado → `AuthNoDisponibleException` (`503`, con el código HTTP en el log); `400` y `422` → `AuthPeticionInvalidaException` (`500`, error de contrato). Lo prueba `SupabaseAuthAdminAccesoTest` (servidor simulado) y la prueba manual `SupabaseAuthEnlaceContraSupabaseTest` (bloquear, comprobar que no puede entrar, restaurar).

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
| `POST /api/entrenadores` | Registra los datos personales (sin categorías) y su cuenta de acceso, y envía el enlace para crear la contraseña ([bloque 5](#cuenta-de-acceso-y-enlace-para-crear-la-contraseña-bloque-5)) | `201` con `Location` y `correoEnviado`, `400`, `409`, `503` |
| `POST /api/entrenadores/{id}/reenviar-enlace` | Reenvía el enlace (crea la cuenta si no la tiene). Uno por minuto | `204`, `404`, `409`, `429`, `503` |
| `PUT /api/entrenadores/{id}` | Edita los datos personales; **no** cambia las categorías. Con cuenta, el correo no se puede cambiar | `200`, `400`, `404`, `409` |
| `PUT /api/entrenadores/{id}/categorias` | Deja al entrenador con exactamente esas categorías | `200`, `400`, `404`, `409` |
| `PATCH /api/entrenadores/{id}/estado` | Activa o desactiva (`{"activo": false}`). Desactivar deja sin entrenador sus categorías y, si tiene cuenta, le impide entrar | `200`, `400`, `404`, `503` |
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
9. **Sesión en todas las llamadas:** todo endpoint (salvo `GET /api/health` y `POST /api/auth/crear-contrasena`) exige `Authorization: Bearer <access_token de Supabase>`. Conviene un interceptor de Axios que lo agregue desde `supabase.auth.getSession()`, y `withCredentials: true` para la cookie de "Recordar este equipo". Un `401` significa sesión vencida (volver al inicio de sesión); un `403`, que el rol no alcanza. Ver [Seguridad](#seguridad).
10. **2FA sin correo en el cuerpo:** `POST /api/auth/2fa/codigo` y `/reenviar` van **sin cuerpo**, y `POST /api/auth/2fa/verificar` lleva solo `{"codigo": "012345"}`. Las tres con el token del primer paso.

### Pendientes / diferencias con el frontend y el esquema

- **`fecha_nacimiento` es obligatoria en la tabla, pero la pantalla no la pide** (punto 2 de arriba).
- **No se borran entrenadores**, solo se desactivan, para conservar su historial (decisión D8).
- **Entrenamientos de un entrenador desactivado:** cuando exista el módulo de entrenamientos (HU-026), hay que decidir qué pasa con sus entrenamientos `PROGRAMADO` futuros. Está marcado con `// TODO HU-026` en `EntrenadorServiceImpl.cambiarEstado`.
- **Ids UUID** en la base y la API, **números** en el frontend (punto 3).
- **`tipoDocumento` va por código** (punto 4). La tabla permite `TI`, pero la API la rechaza para entrenadores: todos son mayores de edad.
- `documentos` (carpeta de documentos) no lo maneja esta historia. `id_usuario` (cuenta de acceso) lo maneja el bloque 5: ver [Cuenta de acceso](#cuenta-de-acceso-y-enlace-para-crear-la-contraseña-bloque-5).
- Fuera del alcance: validar cruces de `horario_trabajo` (es texto libre).

## Cuenta de acceso y enlace para crear la contraseña (bloque 5)

Regla 9: el entrenador no recibe una contraseña temporal; recibe un enlace para crear la suya. Clases: `CuentasDeAcceso` (crear la cuenta, enviar el enlace, bloquear y restaurar el acceso; la usará también el acudiente en 2B), `EntrenadorServiceImpl` (alta, reenvío, estado), `ContrasenaServiceImpl` y `ContrasenaController` (crear la contraseña), `ReglasContrasena`, `LimiteDeFrecuencia`, `IpCliente` y las operaciones nuevas de `ISupabaseAuthAdmin` (`crearUsuario`, `generarTokenRecuperacion`, `verificarTokenRecuperacion`, `fijarContrasena`, `cerrarSesion`).

| Método y ruta | Quién | Qué hace | Respuestas |
| :--- | :--- | :--- | :--- |
| `POST /api/entrenadores` | ADMINISTRADOR | Alta con cuenta. Responde `correoEnviado` | `201`, `400`, `409` (correo de otra cuenta), `503` (Supabase Auth no responde; no guarda nada) |
| `POST /api/entrenadores/{id}/reenviar-enlace` | ADMINISTRADOR | Enlace nuevo (invalida el anterior); si no tiene cuenta, la crea. Nunca devuelve el enlace | `204`, `404`, `409` (inactivo, sin correo o correo de otra cuenta), `429` con `Retry-After`, `503` |
| `POST /api/auth/crear-contrasena` | **Pública** (en `RutasSin2FA`) | `{ "token", "contrasena" }`: crea la contraseña sin iniciar sesión | `200` `{"mensaje"}`, `400`, `429` con `Retry-After`, `503` |

**Alta.** (1) Valida, revisa duplicados y que el correo no sea de otro `usuario`. (2) Crea la cuenta de Supabase Auth con el correo confirmado y sin contraseña. (3) En una transacción: el entrenador, `usuario`, `usuario_rol` (ENTRENADOR) y `entrenador.id_usuario`; si falla (incluido el commit), borra la cuenta de Auth. (4) **Después del commit**: genera el enlace con `generate_link` (`type=recovery`; se usa el `hashed_token`, nunca el `action_link`), lo arma con `FRONTEND_URL` y pide el correo a n8n (`ENLACE_CONTRASENA`, síncrono, 15 s). Cualquier falla en el paso 4 deja el alta en `201` con `correoEnviado: false`.

**Crear la contraseña.** Orden: token vacío → `400` genérico sin llamar a Supabase; contraseña fuera de la política → `400` con el mensaje de la política y `campos: ["contrasena"]` (antes de verificar, porque verificar **consume** el enlace); límites → `429`; `verify` (`token_hash`, `type=recovery`); fijar la contraseña con la API admin por el id del usuario; cerrar la sesión que abrió `verify` (si falla, solo se registra); `200` sin tokens. Enlace inválido, vencido o usado: `400` "El enlace no es válido o venció; pide al administrador que te lo reenvíe", sin `campos`. Falla de Supabase Auth: `503` "Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace."

**Política de contraseña** (`ReglasContrasena`, igual que `validaciones.js`): entre 8 y 72 caracteres, como mucho 72 bytes en UTF-8 (límite de bcrypt en Supabase Auth: una ñ ocupa 2), al menos una letra y un número.

**Desactivar y reactivar.** Con cuenta: primero Supabase Auth (`PUT` con `ban_duration`: bloqueo o restauración), después en una transacción el entrenador, `usuario.activo` y, al desactivar, sus sesiones verificadas; si la base falla, se revierte Auth. Sin cuenta: solo la fila. Al reactivar, entra con la misma contraseña.

Si Auth responde **`404`** (la cuenta no existe): al **desactivar** se sigue con la base (`usuario.activo=false` y se borran sus sesiones; sin cuenta nadie entra) y se registra un error; al **reactivar**, `409` "La cuenta de acceso de este entrenador no existe en Supabase Auth; revisa la configuración", sin cambiar nada. Como `usuario.id_usuario` apunta a `auth.users` con `ON DELETE RESTRICT`, Supabase no puede borrar una cuenta que tiene perfil: un `404` casi siempre significa que **`SUPABASE_URL` y `DB_URL` son de proyectos distintos**, y el log lo dice. Un `400` o `422` de Auth es un error de contrato (`500`), no se disfraza de `503`.

**Límites en memoria** (`LimiteDeFrecuencia`):

| Dónde | Límite |
| :--- | :--- |
| Reenvío del enlace | 1 por minuto por entrenador (el alta cuenta como un envío). El cupo solo se gasta si se generó el enlace |
| Crear la contraseña | 10 intentos por minuto por IP, 5 por enlace (por su SHA-256) y 60 en total. Los que fallan por la política o sin token no cuentan |

Limitación: **se pierden al reiniciar la aplicación y no se comparten entre instancias**. Hoy hay una sola instancia en Render; si se agregan más, cada una llevaría su cuenta y habría que pasarlos a la base o a un almacén compartido.

**IP del cliente** (`IpCliente`). Render agrega direcciones a `X-Forwarded-For` sin borrar las que manda el cliente, así que ese encabezado se puede falsificar y **no se usa** (`server.forward-headers-strategy` queda sin configurar). Se usa `CF-Connecting-IP`, que escribe Cloudflare (delante de Render) y reemplaza la que traiga el cliente; si no viene (en local), `getRemoteAddr()`. En los logs la IP va enmascarada (`190.24.x.x`). **Qué revisar en Render:** que las peticiones lleguen con `CF-Connecting-IP`. Si no llega, todos comparten la IP del proxy y el límite por IP se vuelve uno solo para todos; los límites por enlace y global siguen funcionando.

**Nunca** se registra ni se devuelve el enlace, el token, la contraseña ni el JWT de la sesión de verificación.

**Configuración de Supabase Auth necesaria:** ver [`docs/GUIA-SUPABASE-EQUIPO.md`](../docs/GUIA-SUPABASE-EQUIPO.md) (vencimiento del enlace de 1 hora y política de contraseña no más estricta que la nuestra). Prueba manual contra el proyecto de prueba: `SupabaseAuthEnlaceContraSupabaseTest` (omitida por defecto; con `FUTDATA_PRUEBA_SUPABASE_REAL=true`).

**Deudas:**

- El administrador y el tesorero se siguen creando con la invitación de Supabase (`POST /api/usuarios`, `ISupabaseAuthAdmin.invitar`), no con este camino.
- El correo de un entrenador con cuenta no se puede editar (`409`): cambiarlo no actualiza `usuario.correo` ni Supabase Auth. Se compara sin mayúsculas ni espacios, así que enviar el mismo correo sí pasa.

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
| `GET /api/pagos` | `ADMINISTRADOR`, `TESORERO` | Paginado: `page` (desde 0), `size` (1 a 100), `sort` (`vencimiento`, `monto`, `creadoEn` o `estado`, con `,asc` o `,desc`); filtros `estado`, `tipo`, `idDeportista`, `periodo` (`AAAA-MM`), `vencimientoDesde` y `vencimientoHasta` (`AAAA-MM-DD`, ambos inclusive; ver [abajo](#filtro-de-vencimiento-de-get-apipagos)). Respuesta `{ content, totalElements, number, size }`; cada cobro trae `vencido` |
| `GET /api/pagos/mis-pagos` | `ACUDIENTE` | Los cobros de sus deportistas, con estado y `tieneComprobante`; sin nota interna ni datos de Wompi |
| `POST /api/pagos/{id}/iniciar-wompi` | `ACUDIENTE` dueño | Solo `PENDIENTE`. Referencia `FD-<id del pago>`, **reutilizada** si el cobro ya la tiene |
| `POST /api/pagos/{id}/reintentar` | `ACUDIENTE` dueño | Solo `RECHAZADO`: crea un `PENDIENTE` nuevo con los mismos datos; idempotente |
| `POST /api/pagos/webhook/wompi` | Wompi (pública) | Ver [Webhook](#webhook) |
| `POST /api/pagos/{id}/conciliar` | `ADMINISTRADOR`, `TESORERO` | Consulta a Wompi las transacciones de la referencia y aplica **las mismas reglas** del webhook |
| `POST /api/pagos/{id}/aprobar` | `ADMINISTRADOR`, `TESORERO` | Pago manual: `fechaPago` y `metodo` (`EFECTIVO` o `TRANSFERENCIA`) obligatorios |
| `POST /api/pagos/{id}/rechazar` | `ADMINISTRADOR`, `TESORERO` | Rechaza un cobro `PENDIENTE` (nota opcional) |
| `GET /api/pagos/{id}/comprobante` | dueño, `ADMINISTRADOR`, `TESORERO` | PDF (`application/pdf`) solo de un cobro `APROBADO` |
| `POST /api/pagos/generar-ciclos` | `ADMINISTRADOR` (JWT) o tarea de n8n (`X-Cron-Token`) | Crea los cobros que tocan hoy por ciclo de ingreso. Ver [Cobros por ciclo de ingreso](#cobros-por-ciclo-de-ingreso) |
| `POST /api/pagos/generar-mensualidades` | `ADMINISTRADOR` | **Obsoleto** (usa `generar-ciclos`). `{ "periodo": "2026-11" }`: crea la mensualidad `PENDIENTE` de cada deportista que no la tenga |

```bash
# Crear una mensualidad (administrador)
curl -X POST http://localhost:8080/api/pagos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"idDeportista":"<uuid>","tipo":"MENSUALIDAD","monto":90000,"fechaVencimiento":"2026-11-05","periodo":"2026-11-01"}'

# Datos del checkout (acudiente)
curl -X POST http://localhost:8080/api/pagos/<id>/iniciar-wompi -H "Authorization: Bearer $TOKEN"
```

#### Filtro de vencimiento de `GET /api/pagos`

`vencimientoDesde` y `vencimientoHasta` (`AAAA-MM-DD`, **ambos inclusive**) filtran por `fecha_vencimiento` (columna `NOT NULL`). Son opcionales, se pueden usar solos o juntos, y se combinan con `estado`, `tipo`, `periodo` e `idDeportista`. Sin ellos, todo sigue igual.

```bash
# Inscripciones que vencen en noviembre de 2026 (el 1 y el 30 incluidos)
curl "http://localhost:8080/api/pagos?tipo=INSCRIPCION&vencimientoDesde=2026-11-01&vencimientoHasta=2026-11-30" \
  -H "Authorization: Bearer $TOKEN"
```

`400` (con `campos`): una fecha que no existe o con otro formato (`vencimientoDesde: debe ser una fecha válida con el formato AAAA-MM-DD`; los errores de `periodo` y de las dos fechas salen juntos) y `vencimientoDesde` posterior a `vencimientoHasta` (`campos: ["vencimientoDesde", "vencimientoHasta"]`).

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

### Cobros por ciclo de ingreso

Reemplaza a la generación manual por calendario: cada deportista se cobra en el **aniversario mensual de su registro**, como en un gimnasio. Lo hace `ICobrosCicloService` (`CobrosCicloServiceImpl`); las fechas las calcula la función pura `domain.rules.CiclosDeCobro`.

**Regla.**

1. **Ancla:** `deportista.fecha_registro`. La fecha de cobro *n* es la fecha de registro (n = 0) o, si n ≥ 1, el mismo día del mes *n* meses después, acotado al último día de ese mes (`YearMonth.atEndOfMonth`). Siempre se calcula desde la fecha de registro, nunca desde el cobro anterior: registro el 31 de octubre → 31 oct, 30 nov, 31 dic, 31 ene, 28 feb (29 en bisiesto).
2. **Mensualidad:** el periodo es el primer día del mes de la fecha de cobro (cumple `pago_periodo_ck` y el índice único por deportista y periodo). `fecha_vencimiento` es la fecha de cobro; si el generador corre tarde y ya pasó, vence hoy.
3. **Ciclo vigente:** la fecha de cobro más reciente menor o igual a *hoy + `FUTDATA_COBROS_DIAS_ANTICIPACION`* (5). Se crea la mensualidad de ese ciclo si el deportista no tiene **ninguna** de ese periodo, **en cualquier estado** (un `RECHAZADO` a propósito no se regenera). Nunca se crean ciclos anteriores.
4. **Ingreso:** si `fecha_registro` ≥ *hoy − `FUTDATA_COBROS_VENTANA_INGRESO_DIAS`* (7), se crean además una vez `INSCRIPCION` ("Inscripción AAAA") y `UNIFORME`, si no tiene ya un cobro de ese tipo (en cualquier estado), con vencimiento hoy. Un monto en `0` desactiva ese cobro.
5. **Alcance:** deportistas con `activo = true` y `estado_inscripcion` `PENDIENTE` o `ACTIVA`. `RETIRADA` se excluye (la migración 008 ya los dejó inactivos; `activo` es lo que cambia hoy la aplicación).
6. **Monto de la mensualidad:** `FUTDATA_MENSUALIDAD_MONTO`. En `0` el endpoint responde `409` y no crea nada.

**Endpoint.** `POST /api/pagos/generar-ciclos`, sin cuerpo. Usa "hoy" del reloj de Colombia (`Clock` en `America/Bogota`). Es idempotente: se puede llamar todos los días o dos veces seguidas.

```json
{ "creadas": 5, "yaExistentes": 2, "avisosEnviados": 4, "avisosFallidos": 1, "deportistasConError": 0 }
```

- `creadas`: cobros creados (mensualidades, inscripciones y uniformes). `yaExistentes`: cobros que tocaba crear y ya existían (incluye el choque con el índice único por una ejecución simultánea).
- Cada deportista se procesa en **su propia transacción** (con su fila bloqueada): el fallo de uno no aborta a los demás; los que fallan por un error inesperado se cuentan en `deportistasConError` y se registran en el log (solo el id).
- Autorización: el administrador con su JWT, **o** la cabecera `X-Cron-Token` igual a `N8N_CRON_TOKEN`. La comparación es en tiempo constante (se comparan los SHA-256). La vía por token funciona **solo** en esta ruta y método, no abre ningún otro endpoint, y si la variable no está definida queda deshabilitada (solo el administrador). `N8N_CRON_TOKEN` es opcional y **no** la exige `VerificadorVariablesProd`. Si la petición trae `Authorization`, manda el JWT.
- Si el proceso diario viejo está activo (`FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA=true`, calendario) conviene apagarlo cuando n8n llame a `generar-ciclos`: crean mensualidades del mismo periodo y el índice único evita duplicados, pero los criterios de fecha son distintos.

**Aviso por correo.** Por cada **mensualidad** creada con **anticipación** (fecha de cobro posterior a hoy), el backend pide a n8n un `RECORDATORIO_PAGO` por cada acudiente vinculado (`acudiente_deportista`) con correo (sin repetir correos). No se avisa de inscripción ni uniforme. Los avisos salen **después** del commit de cada deportista; si n8n falla, el cobro **no** se revierte: se cuenta en `avisosFallidos` y se registra en el log con el correo enmascarado (`m***@dominio`) y el tipo de falla.

Evento `RECORDATORIO_PAGO` (`INotificador.RECORDATORIO_PAGO`; asíncrono como `CODIGO_2FA`: `200` o `202`):

```json
{
  "evento": "RECORDATORIO_PAGO",
  "datos": {
    "correo": "maria@ejemplo.com",
    "nombreAcudiente": "María Torres",
    "nombreDeportista": "Pedro Gómez",
    "concepto": "Mensualidad noviembre 2026",
    "monto": 90000,
    "fechaVencimiento": "2026-11-14",
    "diasRestantes": 4,
    "enlacePagos": "https://futdata.example/acudiente/pagos"
  }
}
```

`monto` en pesos enteros; `fechaVencimiento` `AAAA-MM-DD`; `diasRestantes` entero ≥ 1; `enlacePagos` = `{FRONTEND_URL}/acudiente/pagos` (sin datos secretos). **Pendiente en n8n:** agregar el evento al flujo de notificaciones y su plantilla; hasta entonces n8n responde `400 Evento no soportado` y el aviso cuenta como fallido (el cobro se crea igual). Y una tarea diaria en n8n que llame a `POST /api/pagos/generar-ciclos` con `X-Cron-Token`.

**Pruebas.** `CiclosDeCobroTest` (fechas y ciclo vigente), `CobrosCicloServiceImplTest` (reglas con DAO simulados), `CobrosCicloConPostgresTest` (alcance por estado, idempotencia, `RECHAZADO`, índice único, avisos; requiere Docker) y `PagoCiclosControllerTest` / `PagoCiclosSinTokenTest` (JWT, token, roles y vía deshabilitada).

### Cobros mensuales automáticos (obsoleto)

> **Obsoleto:** lo reemplazan los [cobros por ciclo de ingreso](#cobros-por-ciclo-de-ingreso). `generar-mensualidades` y el proceso diario por calendario se mantienen sin cambios mientras el frontend migra.

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
| `GET /api/health`, `/error`, `OPTIONS` (CORS), `POST /api/pagos/webhook/wompi`, `POST /api/auth/crear-contrasena` | Públicas, sin token (el webhook se autentica con la firma del evento; crear la contraseña, con el token de un solo uso del enlace y límites de intentos) |
| Swagger (`/swagger-ui.html`, `/v3/api-docs`) | Pública **solo con el perfil `dev`**; en los demás perfiles está apagado |
| `POST /api/auth/2fa/codigo`, `/reenviar`, `/verificar` | Cualquier rol, con el JWT del primer paso y sin haber verificado el código |
| `POST /api/auth/logout`, `GET /api/usuarios/yo` | Cualquier rol, con la sesión verificada |
| `/api/usuarios/**` (salvo `/yo`) | `ADMINISTRADOR` |
| `/api/deportistas/**` (incluidos `PUT` y `/foto`) | `ADMINISTRADOR`; `GET /api/deportistas` (la lista) también `ENTRENADOR`, solo con los deportistas de sus categorías y sin documento, teléfono, dirección ni estado de la inscripción (un rol distinto de `ADMINISTRADOR` y `ENTRENADOR` nunca obtiene la lista completa) |
| `/api/entrenadores/**` | `ADMINISTRADOR` |
| `POST /api/pagos`, `POST /api/pagos/generar-mensualidades` | `ADMINISTRADOR` |
| `POST /api/pagos/generar-ciclos` | `ADMINISTRADOR` con JWT, o la cabecera `X-Cron-Token` (solo si `N8N_CRON_TOKEN` está definida; solo esta ruta) |
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

Las rutas sin 2FA están todas en `util.security.RutasSin2FA`, en una sola lista documentada: las tres del segundo paso, crear la contraseña con el enlace, el webhook de Wompi, `GET /api/health`, `/error` y `OPTIONS`; con el perfil `dev`, también la documentación. **Agregar una ruta ahí la abre:** hay que justificarla.

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
| Timeout | 5 s de conexión y 5 s de lectura (`futdata.n8n.timeout`); **15 s solo para `ENLACE_CONTRASENA`** (`futdata.n8n.timeout-enlace`, `N8N_TIMEOUT_ENLACE`), que es síncrono |
| Reintentos | Ninguno. Si n8n recibió la petición y solo se demoró, un reintento enviaría el correo dos veces |
| Eventos | `INotificador.CODIGO_2FA`, `INotificador.CAMBIO_ENTRENAMIENTO`, `INotificador.ENLACE_CONTRASENA`, `INotificador.RECORDATORIO_PAGO` e `INotificador.DOCUMENTO_RECHAZADO` (constantes; no escribir la cadena a mano). `DOCUMENTO_RECHAZADO` es asíncrono, un `POST` por acudiente, con el tiempo de espera normal de 5 s ([Documentos](#documentos-de-la-inscripción-bloque-3)) |

`N8nClient` **no arma los `datos`**: cada servicio construye los de su evento (HU-002 el código 2FA, HU-026 el aviso de entrenamiento) y depende solo de la interfaz `INotificador`, inyectada por constructor.

### Resultado y excepciones

`notificar()` no devuelve nada: si retorna, n8n aceptó la notificación. Si no, lanza una de estas excepciones (no comprobadas, todas hijas de `NotificacionException`, con `getEvento()` y `getCodigoHttp()`):

| Respuesta de n8n | Resultado | Qué significa |
| :--- | :--- | :--- |
| `202` (`CODIGO_2FA` y `CAMBIO_ENTRENAMIENTO`) | Retorna | n8n **recibió** la notificación y la envía en segundo plano. No significa "entregado": si el SMTP falla después, solo queda en el registro de fallas de n8n |
| `200` | Retorna | `ENLACE_CONTRASENA` (síncrono): el SMTP aceptó el correo. También lo respondía el flujo anterior de `CODIGO_2FA`; se sigue aceptando para que no importe el orden de despliegue. No se lee el campo `enviado` |
| `400` | `NotificacionRechazadaException` | Los `datos` no cumplen el contrato: error de programación. `getDetalle()` trae la explicación de n8n, o `null` si parecía tener datos sensibles (correos, 6 dígitos seguidos o un valor de `datos`; los valores de `evento` y `tipoCambio` no cuentan, porque son fijos) |
| `401` | `NotificadorMalConfiguradoException` | `N8N_WEBHOOK_TOKEN` no coincide con el de n8n |
| Falta `N8N_BASE_URL` o `N8N_WEBHOOK_TOKEN` (perfil local) | `NotificadorMalConfiguradoException` | La app arranca, pero no puede enviar. En `prod` la app ni siquiera arranca |
| `502` (eventos síncronos), otro 5xx, timeout, error de red u otro código | `NotificacionNoEnviadaException` | n8n no aceptó la notificación. `getCodigoHttp()` está vacío si no hubo respuesta |
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

Asíncrono: n8n responde `202` apenas valida los datos (~0,6 s con el túnel) y envía después. El inicio de sesión espera solo esa respuesta, no el SMTP (~3 s). Si el SMTP falla después del `202`, el backend ya respondió y el código quedó guardado: el usuario no recibe el correo y debe usar "Reenviar código" (disponible al minuto). La falla solo queda en el registro de fallas de n8n.

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
            // Timeout, red o 5xx de n8n: avisar al usuario (503); puede usar "Reenviar código" (INTERVALO_REENVIO = 1 min).
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
INFO  Notificación CODIGO_2FA aceptada por n8n: HTTP 202, 612 ms
WARN  Notificación CODIGO_2FA no enviada: sin respuesta de n8n (5003 ms)
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
| `ReglasDocumentoTest` | Los 7 tipos (y que `TIPOS` contiene los 4 obligatorios de `ReglasDeportista`); firma real de PDF, JPG y PNG sin importar la extensión ni el `Content-Type`; archivo vacío, de 5 MB exactos y de 5 MB + 1; fecha de vencimiento (real, no pasada, hora de Bogotá); comentario de rechazo (vacío, solo espacios, 300 y 301); nombre original limpio; ruta sin el nombre del cliente |
| `DocumentosPropertiesTest`, `FrontendPropertiesTest` | Valores por defecto y límites (10 s a 60 min) de `futdata.documentos`; el enlace `{FRONTEND_URL}/acudiente/documentos` |
| `DocumentoDAOImplTest` | Contra PostgreSQL: insertar y leer con rol y correo de quien subió, orden de la lista, ruta duplicada, un activo por tipo, aprobar y rechazar solo si está `PENDIENTE`, propiedad del acudiente y la carrera de dos subidas con el bloqueo de la fila del deportista |
| `DocumentoServiceImplTest` | Con dobles: matriz de autorización, subir (orden, compensación del archivo, la ruta sin el nombre del cliente), listar, descargar, aprobar y rechazar (`403`, `404`, `400`, `409`) y el aviso `DOCUMENTO_RECHAZADO` (después del commit, `true`, `false` y `null`, sin datos personales en los logs) |
| `DocumentoServiceConPostgresTest` | Punta a punta con PostgreSQL y un Storage en memoria: un activo por tipo, rechazar y volver a subir, `estadoInscripcion` hasta el cuarto obligatorio, dos revisiones simultáneas y los avisos con un notificador simulado |
| `DocumentoControllerTest` | Las cinco rutas por HTTP con la cadena de seguridad completa: roles, `401`, `403` con `2FA_REQUERIDA`, forma del JSON, `Cache-Control: no-store`, `avisoEnviado` en `true`, `false` y `null`, y el comentario ausente como `400` |
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
