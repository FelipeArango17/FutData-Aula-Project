# Diccionario de datos

**Sprint:** Sprint 1  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club  
**Origen:** Modelo Entidad-Relación definido para la HU de modelado de datos  
**Ampliación Sprint 2:** se agregan las tablas `Categoria`, `Entrenamiento` y `Notificacion`, además de los campos y relaciones que las HU-025 (entrenadores) y HU-026 (programar entrenamientos) necesitan. Todo lo nuevo está marcado como **Nuevo (Sprint 2)** y se alinea con el diagrama de clases del dominio (`docs/diagramas-clases/01-dominio-fig.png`).

## 1. Convenciones

- `PK` identifica de forma única cada registro.
- `FK` referencia la clave primaria de otra tabla.
- `NOT NULL` indica un campo obligatorio. Las restricciones de obligatoriedad no están expresadas en el código del diagrama y deberán definirse durante la implementación de la base de datos.
- `UUID` identifica registros y relaciones entre tablas; `varchar` almacena texto corto; `text`, texto de longitud variable; `int`, números enteros; `decimal`, valores numéricos con decimales; `date`, fecha; `time`, hora del día; `datetime`, fecha y hora; y `boolean`, valores verdadero/falso.
- En PostgreSQL/Supabase, `UUID` se implementa como `uuid`, `decimal` como `numeric`, `datetime` como `timestamp with time zone` y `boolean` como `boolean`.
- Los campos `edad` son calculados a partir de `fecha_nacimiento`; no deben mantenerse como un valor independiente sin una regla de actualización.
- Los campos `foto`, `documentos` y `recibo_url` almacenan URLs o rutas al almacenamiento de archivos, no el contenido binario.
- La autenticación y las contraseñas pertenecen a Supabase Auth. `Usuario.id_usuario` usa el mismo UUID de `auth.users.id`; la tabla `Usuario` solo almacena el perfil y estado de la cuenta.
- Los nombres de rol se almacenan en la base de datos en mayúsculas: `ADMINISTRADOR`, `TESORERO`, `ENTRENADOR` y `ACUDIENTE`. La API puede exponerlos en minúsculas para coincidir con el frontend.

## 2. Tablas y atributos

### 2.1 `Deportista`

Registro principal de los jugadores del club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_deportista` | `UUID` | PK | Identificador único del deportista. |
| `nombre` | `varchar` | — | Nombres del deportista. |
| `apellido` | `varchar` | — | Apellidos del deportista. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `varchar` | — | Número del documento de identidad como texto, para conservar ceros iniciales y soportar distintos formatos. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `posicion` | `varchar` | — | Posición habitual de juego. |
| `eps` | `varchar` | — | Entidad promotora de salud. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `foto` | `varchar` | — | URL de la fotografía en almacenamiento en la nube. |
| `documentos` | `varchar` | — | URL a la carpeta de documentos del deportista. |
| `fecha_registro` | `date` | — | Fecha de registro en el sistema. |
| `id_categoria` | `UUID` | FK | **Nuevo (Sprint 2).** Categoría a la que pertenece el deportista. Referencia `Categoria.id_categoria`. Ver decisión D3. |

### 2.2 `Acudiente`

Personas responsables o acudientes asociados a un deportista.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_acudiente` | `UUID` | PK | Identificador único del acudiente. |
| `nombre` | `varchar` | — | Nombres del acudiente. |
| `apellido` | `varchar` | — | Apellidos del acudiente. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `varchar` | — | Número del documento de identidad como texto, para conservar ceros iniciales y soportar distintos formatos. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `id_deportista` | `UUID` | FK | Deportista al que está asociado. Referencia `Deportista.id_deportista`. |
| `id_usuario` | `UUID` | FK | Cuenta de acceso asociada. Referencia `Usuario.id_usuario`. Nullable mientras se registra el acudiente. |

### 2.3 `Entrenador`

Información personal y laboral de los entrenadores.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_entrenador` | `UUID` | PK | Identificador único del entrenador. |
| `nombre` | `varchar` | — | Nombres del entrenador. |
| `apellido` | `varchar` | — | Apellidos del entrenador. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `varchar` | — | Número del documento de identidad como texto, para conservar ceros iniciales y soportar distintos formatos. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `horario_trabajo` | `varchar` | — | Horario de trabajo del entrenador. |
| `especialidad` | `varchar` | — | **Nuevo (Sprint 2).** Especialidad del entrenador (por ejemplo, porteros o preparación física). Ya existe en el diagrama de clases del dominio. |
| `documentos` | `varchar` | — | URL a la carpeta de documentos del entrenador. |
| `id_usuario` | `UUID` | FK | Cuenta de acceso asociada. Referencia `Usuario.id_usuario`. Nullable mientras se registra el entrenador. |

### 2.4 `Usuario`

Perfil y estado de una cuenta administrada por Supabase Auth.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario` | `UUID` | PK | Identificador único del usuario. |
| `nombre` | `varchar` | — | Nombre visible del usuario. |
| `correo` | `varchar` | — | Correo utilizado para el acceso. |
| `activo` | `boolean` | — | Indica si el acceso de la cuenta está vigente. Revocar el acceso no elimina la cuenta de Supabase Auth. |

### 2.5 `Pago`

Pagos realizados por un deportista.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_pago` | `UUID` | PK | Identificador único del pago. |
| `id_deportista` | `UUID` | FK | Deportista que realiza o recibe el cobro. Referencia `Deportista.id_deportista`. |
| `fecha_pago` | `date` | — | Fecha en que se registra el pago. |
| `monto` | `decimal` | — | Valor monetario pagado. |
| `concepto` | `varchar` | — | Concepto del pago. |
| `recibo_url` | `varchar` | — | URL del recibo almacenado. |
| `estado_pago` | `varchar` | — | Estado del pago, por ejemplo pendiente, aprobado o rechazado. |

### 2.6 `Torneo`

Información de los torneos en los que puede participar el club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_torneo` | `UUID` | PK | Identificador único del torneo. |
| `nombre_torneo` | `varchar` | — | Nombre del torneo. |
| `id_categoria` | `UUID` | FK | Categoría participante. Referencia `Categoria.id_categoria`. |
| `lugar` | `varchar` | — | Lugar donde se realiza el torneo. |
| `fecha_inicio` | `date` | — | Fecha de inicio. |
| `fecha_fin` | `date` | — | Fecha de finalización. |
| `costo` | `decimal` | — | Costo de inscripción o participación. |

### 2.7 `NotificacionPago`

Mensajes de notificación de pagos dirigidos a acudientes.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_notificacion_pago` | `UUID` | PK | Identificador único de la notificación. |
| `id_acudiente` | `UUID` | FK | Acudiente destinatario. Referencia `Acudiente.id_acudiente`. |
| `mensaje` | `text` | — | Contenido de la notificación. |
| `fecha_envio` | `datetime` | — | Fecha y hora del envío. |
| `leido` | `boolean` | — | Indica si el acudiente leyó la notificación. |

### 2.8 `Notificacion`

Notificaciones generales dirigidas a las cuentas de usuarios, incluidos los avisos de entrenamientos.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_notificacion` | `UUID` | PK | Identificador único de la notificación. |
| `id_usuario` | `UUID` | FK | Usuario destinatario. Referencia `Usuario.id_usuario`. |
| `id_entrenamiento` | `UUID` | FK | Entrenamiento relacionado, si aplica. Referencia `Entrenamiento.id_entrenamiento`. Nullable para otros tipos de aviso. |
| `tipo` | `varchar` | — | Tipo de notificación, por ejemplo `ENTRENAMIENTO_CREADO`, `ENTRENAMIENTO_ACTUALIZADO` o `ENTRENAMIENTO_ELIMINADO`. |
| `mensaje` | `text` | — | Contenido de la notificación. |
| `fecha_envio` | `datetime` | — | Fecha y hora del envío. |
| `leido` | `boolean` | — | Indica si el usuario leyó la notificación. |

### 2.9 `Asistencia`

Registro de asistencia de un deportista tomado por un entrenador.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_asistencia` | `UUID` | PK | Identificador único del registro de asistencia. |
| `id_deportista` | `UUID` | FK | Deportista registrado. Referencia `Deportista.id_deportista`. |
| `id_entrenador` | `UUID` | FK | Entrenador que registra la asistencia. Referencia `Entrenador.id_entrenador`. |
| `id_entrenamiento` | `UUID` | FK | **Nuevo (Sprint 2).** Entrenamiento al que corresponde la asistencia. Referencia `Entrenamiento.id_entrenamiento`. Ver decisión D5. |
| `fecha` | `date` | — | Fecha de la asistencia. |
| `presente` | `boolean` | — | Indica si el deportista estuvo presente. |
| `observaciones` | `text` | — | Comentarios adicionales sobre la asistencia. |

### 2.10 `InscripcionTorneo`

Tabla intermedia que registra la participación de deportistas en torneos.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_inscripcion` | `UUID` | PK | Identificador único de la inscripción. |
| `id_torneo` | `UUID` | FK | Torneo de la inscripción. Referencia `Torneo.id_torneo`. |
| `id_deportista` | `UUID` | FK | Deportista inscrito. Referencia `Deportista.id_deportista`. |
| `resultado` | `varchar` | — | Resultado o clasificación obtenida. |

### 2.11 `Rol`

Catálogo de roles disponibles en el sistema.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_rol` | `UUID` | PK | Identificador único del rol. |
| `nombre` | `varchar` | — | Nombre del rol. Único; valores: `ADMINISTRADOR`, `TESORERO`, `ENTRENADOR` y `ACUDIENTE`. |

### 2.12 `ReporteFinanciero`

Resumen mensual de los ingresos del club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_reporte` | `UUID` | PK | Identificador único del reporte. |
| `mes` | `int` | — | Mes del periodo reportado. |
| `año` | `int` | — | Año del periodo reportado. |
| `ingresos_mensualidades` | `decimal` | — | Total de ingresos por mensualidades. |
| `ingresos_inscripciones` | `decimal` | — | Total de ingresos por inscripciones. |
| `ingresos_ventas` | `decimal` | — | Total de ingresos por ventas. |
| `fecha_generacion` | `datetime` | — | Fecha y hora de generación del reporte. |

### 2.13 `UsuarioRol`

Tabla intermedia para asociar usuarios con roles.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario_rol` | `UUID` | PK | Identificador único de la asociación. |
| `id_usuario` | `UUID` | FK | Usuario asociado. Referencia `Usuario.id_usuario`. |
| `id_rol` | `UUID` | FK | Rol asignado. Referencia `Rol.id_rol`. |

### 2.14 `Categoria`

**Nuevo (Sprint 2).** Catálogo de categorías por edad del club (Sub-8, Sub-10, Sub-12, Sub-14 y Sub-16 en las pantallas actuales).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_categoria` | `UUID` | PK | Identificador único de la categoría. |
| `nombre` | `varchar` | — | Nombre de la categoría: `Sub-8`, `Sub-10`, `Sub-12`, `Sub-14` o `Sub-16`. Único. |
| `edad_minima` | `int` | — | Edad mínima cumplida: 0, 8, 10, 12 o 14, respectivamente. |
| `edad_maxima` | `int` | — | Edad máxima cumplida: 7, 9, 11, 13 o 15, respectivamente. |
| `id_entrenador` | `UUID` | FK | Entrenador que dirige la categoría. Referencia `Entrenador.id_entrenador`. Ver decisión D1. |

### 2.15 `Entrenamiento`

**Nuevo (Sprint 2).** Sesión de entrenamiento programada para una categoría (HU-026).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_entrenamiento` | `UUID` | PK | Identificador único del entrenamiento. |
| `id_categoria` | `UUID` | FK | Categoría que entrena. Referencia `Categoria.id_categoria`. |
| `id_entrenador` | `UUID` | FK | Entrenador a cargo de la sesión. Referencia `Entrenador.id_entrenador`. Puede ser distinto del que dirige la categoría (por ejemplo, un reemplazo). |
| `fecha` | `date` | — | Fecha del entrenamiento. |
| `hora_inicio` | `time` | — | Hora de inicio. Las pantallas actuales solo manejan hora de inicio. |
| `lugar` | `varchar` | — | Cancha o lugar del entrenamiento, por ejemplo `Cancha 1`. Ver decisión D2. |

## 3. Relaciones

Las siguientes relaciones se derivan de las claves foráneas incluidas en el modelo:

| Tabla origen | Cardinalidad | Tabla destino | Descripción |
| :--- | :--- | :--- | :--- |
| `Acudiente` | N:1 | `Deportista` | Cada acudiente se asocia a un deportista; un deportista puede tener varios acudientes. |
| `Pago` | N:1 | `Deportista` | Cada pago pertenece a un deportista; un deportista puede tener varios pagos. |
| `NotificacionPago` | N:1 | `Acudiente` | Cada notificación pertenece a un acudiente; un acudiente puede recibir varias notificaciones. |
| `Notificacion` | N:1 | `Usuario` | Cada notificación general pertenece a un usuario destinatario; un usuario puede recibir varias notificaciones. |
| `Notificacion` | N:1 | `Entrenamiento` | Los avisos de entrenamiento pueden referenciar el entrenamiento relacionado. |
| `Asistencia` | N:1 | `Deportista` | Cada registro corresponde a un deportista; un deportista puede tener varios registros. |
| `Asistencia` | N:1 | `Entrenador` | Cada registro es tomado por un entrenador; un entrenador puede registrar muchas asistencias. |
| `InscripcionTorneo` | N:1 | `Torneo` | Cada inscripción pertenece a un torneo; un torneo puede tener muchos inscritos. |
| `InscripcionTorneo` | N:1 | `Deportista` | Cada inscripción pertenece a un deportista; un deportista puede participar en varios torneos. |
| `UsuarioRol` | N:1 | `Usuario` | Cada asociación asigna un rol a un usuario; un usuario puede tener varios roles. |
| `UsuarioRol` | N:1 | `Rol` | Cada asociación referencia un rol; un rol puede asignarse a varios usuarios. |
| `Deportista` | N:1 | `Categoria` | **Nuevo.** Cada deportista pertenece a una categoría; una categoría agrupa a varios deportistas. |
| `Categoria` | N:1 | `Entrenador` | **Nuevo.** Cada categoría la dirige un entrenador; un entrenador puede dirigir varias categorías (decisión D1). |
| `Entrenamiento` | N:1 | `Categoria` | **Nuevo.** Cada entrenamiento es de una categoría; una categoría tiene muchos entrenamientos. |
| `Entrenamiento` | N:1 | `Entrenador` | **Nuevo.** Cada entrenamiento tiene un entrenador a cargo; un entrenador puede tener muchos entrenamientos. |
| `Asistencia` | N:1 | `Entrenamiento` | **Nuevo.** Cada registro de asistencia pertenece a un entrenamiento; un entrenamiento tiene muchas asistencias (decisión D5). |

Por medio de `InscripcionTorneo`, la relación entre `Deportista` y `Torneo` es **N:M**. Por medio de `UsuarioRol`, la relación entre `Usuario` y `Rol` también es **N:M**.

## 4. Observaciones para la implementación

1. `UsuarioRol` es la única fuente de verdad para los roles; `Usuario` no almacena un campo `rol`.
2. Se debe aplicar `UNIQUE` a `correo` en `Usuario`, a (`tipo_documento`, `documento_identidad`) en `Deportista`, `Acudiente` y `Entrenador`, y a `correo` en cada perfil que lo almacene.
3. Para evitar duplicados, se debe aplicar `UNIQUE (id_torneo, id_deportista)` en `InscripcionTorneo`.
4. Para evitar duplicados, se debe aplicar `UNIQUE (id_deportista, id_entrenamiento)` en `Asistencia`.
5. `documento_identidad` se almacena como `varchar`, no como entero, para conservar ceros iniciales y soportar documentos alfanuméricos.
6. Los valores permitidos son `tipo_documento`: `CC`, `TI`, `CE`, `PASAPORTE`; `estado_pago`: `PENDIENTE`, `APROBADO`, `RECHAZADO`; `resultado`: `PENDIENTE`, `CAMPEON`, `SUBCAMPEON`, `TERCER_LUGAR`, `OTRO`. Deben validarse con `CHECK` o catálogos equivalentes.
7. Las contraseñas no se almacenan en el esquema de la aplicación: Supabase Auth las gestiona en `auth.users`. `Usuario.id_usuario` es una FK/PK hacia `auth.users.id`.
8. **Nuevo (Sprint 2).** Para que no se programen dos entrenamientos en el mismo lugar a la misma hora, se debe aplicar `UNIQUE (lugar, fecha, hora_inicio)` en `Entrenamiento`. La pantalla de programación ya detecta este conflicto y ofrece alternativas; la base de datos debe hacerlo cumplir también.
9. **Nuevo (Sprint 2).** Todas las PK y FK usan `UUID`, compatible con Supabase Auth y con el diagrama de clases del dominio.
10. **Nuevo (Sprint 2).** `Torneo.id_categoria` referencia `Categoria.id_categoria`, por lo que torneos y deportistas usan el mismo catálogo.
11. **Nuevo (Sprint 2).** `Entrenador.id_usuario` y `Acudiente.id_usuario` permiten asociar cada perfil con su cuenta de acceso; ambos son nullable durante el registro.
12. `Entrenador.id_usuario` y `Acudiente.id_usuario` deben ser `UNIQUE` para que una cuenta no se vincule a dos perfiles del mismo tipo.

## 5. Decisiones confirmadas de la ampliación del Sprint 2

Estas decisiones fueron confirmadas para cerrar el modelo antes de crear tablas. El frontend avanza con datos en memoria detrás de una capa de servicio y se ajusta cuando se implementen los endpoints.

| Id | Decisión | Propuesta |
| :--- | :--- | :--- |
| D1 | ¿Un entrenador dirige varias categorías y una categoría tiene un solo entrenador, o una categoría puede tener varios entrenadores (por ejemplo, un auxiliar)? | Confirmado: seguir el diagrama de clases con `Categoria.id_entrenador` (1 entrenador, varias categorías). No se crea `EntrenadorCategoria`. |
| D2 | ¿`lugar` es texto libre o un catálogo? | Confirmado: texto, con restricción de unicidad sobre (`lugar`, `fecha`, `hora_inicio`). |
| D3 | ¿La categoría de un deportista se guarda o se calcula a partir de la edad? | Confirmado: guardar `Deportista.id_categoria` y asignarla según la edad cumplida: `Sub-8` (0–7), `Sub-10` (8–9), `Sub-12` (10–11), `Sub-14` (12–13) y `Sub-16` (14–15). Se recalcula al registrar y se actualiza explícitamente cuando cambie la edad; no se cambia silenciosamente durante una consulta. |
| D4 | ¿Cómo se guarda el aviso a deportistas y acudientes cuando se crea, cambia o elimina un entrenamiento (tarea 338)? | Confirmado: crear una tabla `Notificacion` general con tipo; `NotificacionPago` queda reservada para pagos. |
| D5 | ¿La asistencia se toma por entrenamiento? | Confirmado: agregar `Asistencia.id_entrenamiento` y usar la restricción (`id_deportista`, `id_entrenamiento`). `Asistencia.fecha` queda derivable de `Entrenamiento.fecha`. |

### 5.1 Correspondencia con el frontend (tarea 339)

La capa `services/entrenamientosService.js` usa esta forma de datos, que debe cambiarse por la API cuando existan los endpoints (tarea 337).

| Campo en el frontend | Columna | Nota |
| :--- | :--- | :--- |
| `id` | `Entrenamiento.id_entrenamiento` | — |
| `categoria` (texto, por ejemplo `Sub-12`) | `Entrenamiento.id_categoria` | El frontend usa el nombre; la API deberá enviar el id o el nombre. |
| `fecha` (`AAAA-MM-DD`) | `Entrenamiento.fecha` | — |
| `hora` (`HH:MM`) | `Entrenamiento.hora_inicio` | — |
| `lugar` | `Entrenamiento.lugar` | — |
| `idEntrenador` | `Entrenamiento.id_entrenador` | — |
| Jugadores a notificar (por ejemplo, 17) | Conteo de `Deportista` con el mismo `id_categoria` | Hoy es un dato de ejemplo en el frontend. |
