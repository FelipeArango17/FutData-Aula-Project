# Diccionario de datos

**Sprint:** Sprint 1  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club  
**Origen:** Modelo Entidad-Relación definido para la HU de modelado de datos

## 1. Convenciones

- `PK` identifica de forma única cada registro.
- `FK` referencia la clave primaria de otra tabla.
- `NOT NULL` indica un campo obligatorio. Las restricciones de obligatoriedad no están expresadas en el código del diagrama y deberán definirse durante la implementación de la base de datos.
- `varchar` almacena texto corto; `text`, texto de longitud variable; `int`, números enteros; `long`, enteros de mayor capacidad; `decimal`, valores numéricos con decimales; `date`, fecha; `datetime`, fecha y hora; y `boolean`, valores verdadero/falso.
- Los campos `edad` son calculados a partir de `fecha_nacimiento`; no deben mantenerse como un valor independiente sin una regla de actualización.
- Los campos `foto`, `documentos` y `recibo_url` almacenan URLs o rutas al almacenamiento de archivos, no el contenido binario.

## 2. Tablas y atributos

### 2.1 `Deportista`

Registro principal de los jugadores del club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_deportista` | `int` | PK | Identificador único del deportista. |
| `nombre` | `varchar` | — | Nombres del deportista. |
| `apellido` | `varchar` | — | Apellidos del deportista. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `long` | — | Número del documento de identidad. |
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

### 2.2 `Acudiente`

Personas responsables o acudientes asociados a un deportista.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_acudiente` | `int` | PK | Identificador único del acudiente. |
| `nombre` | `varchar` | — | Nombres del acudiente. |
| `apellido` | `varchar` | — | Apellidos del acudiente. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `long` | — | Número del documento de identidad. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `id_deportista` | `int` | FK | Deportista al que está asociado. Referencia `Deportista.id_deportista`. |

### 2.3 `Entrenador`

Información personal y laboral de los entrenadores.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_entrenador` | `int` | PK | Identificador único del entrenador. |
| `nombre` | `varchar` | — | Nombres del entrenador. |
| `apellido` | `varchar` | — | Apellidos del entrenador. |
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad. |
| `documento_identidad` | `long` | — | Número del documento de identidad. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `horario_trabajo` | `varchar` | — | Horario de trabajo del entrenador. |
| `documentos` | `varchar` | — | URL a la carpeta de documentos del entrenador. |

### 2.4 `Usuario`

Credenciales y datos básicos de acceso al sistema.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario` | `int` | PK | Identificador único del usuario. |
| `nombre` | `varchar` | — | Nombre visible del usuario. |
| `correo` | `varchar` | — | Correo utilizado para el acceso. |
| `contraseña` | `varchar` | — | Contraseña almacenada de forma segura mediante hash. |
| `rol` | `varchar` | — | Rol indicado en el registro del usuario. |

### 2.5 `Pago`

Pagos realizados por un deportista.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_pago` | `int` | PK | Identificador único del pago. |
| `id_deportista` | `int` | FK | Deportista que realiza o recibe el cobro. Referencia `Deportista.id_deportista`. |
| `fecha_pago` | `date` | — | Fecha en que se registra el pago. |
| `monto` | `decimal` | — | Valor monetario pagado. |
| `concepto` | `varchar` | — | Concepto del pago. |
| `recibo_url` | `varchar` | — | URL del recibo almacenado. |
| `estado_pago` | `varchar` | — | Estado del pago, por ejemplo pendiente, aprobado o rechazado. |

### 2.6 `Torneo`

Información de los torneos en los que puede participar el club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_torneo` | `int` | PK | Identificador único del torneo. |
| `nombre_torneo` | `varchar` | — | Nombre del torneo. |
| `categoria` | `varchar` | — | Categoría participante. |
| `lugar` | `varchar` | — | Lugar donde se realiza el torneo. |
| `fecha_inicio` | `date` | — | Fecha de inicio. |
| `fecha_fin` | `date` | — | Fecha de finalización. |
| `costo` | `decimal` | — | Costo de inscripción o participación. |

### 2.7 `NotificacionPago`

Mensajes de notificación de pagos dirigidos a acudientes.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_notificacion_pago` | `int` | PK | Identificador único de la notificación. |
| `id_acudiente` | `int` | FK | Acudiente destinatario. Referencia `Acudiente.id_acudiente`. |
| `mensaje` | `text` | — | Contenido de la notificación. |
| `fecha_envio` | `datetime` | — | Fecha y hora del envío. |
| `leido` | `boolean` | — | Indica si el acudiente leyó la notificación. |

### 2.8 `Asistencia`

Registro de asistencia de un deportista tomado por un entrenador.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_asistencia` | `int` | PK | Identificador único del registro de asistencia. |
| `id_deportista` | `int` | FK | Deportista registrado. Referencia `Deportista.id_deportista`. |
| `id_entrenador` | `int` | FK | Entrenador que registra la asistencia. Referencia `Entrenador.id_entrenador`. |
| `fecha` | `date` | — | Fecha de la asistencia. |
| `presente` | `boolean` | — | Indica si el deportista estuvo presente. |
| `observaciones` | `text` | — | Comentarios adicionales sobre la asistencia. |

### 2.9 `InscripcionTorneo`

Tabla intermedia que registra la participación de deportistas en torneos.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_inscripcion` | `int` | PK | Identificador único de la inscripción. |
| `id_torneo` | `int` | FK | Torneo de la inscripción. Referencia `Torneo.id_torneo`. |
| `id_deportista` | `int` | FK | Deportista inscrito. Referencia `Deportista.id_deportista`. |
| `resultado` | `varchar` | — | Resultado o clasificación obtenida. |

### 2.10 `Rol`

Catálogo de roles disponibles en el sistema.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_rol` | `int` | PK | Identificador único del rol. |
| `nombre` | `varchar` | — | Nombre del rol. |

### 2.11 `ReporteFinanciero`

Resumen mensual de los ingresos del club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_reporte` | `int` | PK | Identificador único del reporte. |
| `mes` | `int` | — | Mes del periodo reportado. |
| `año` | `int` | — | Año del periodo reportado. |
| `ingresos_mensualidades` | `decimal` | — | Total de ingresos por mensualidades. |
| `ingresos_inscripciones` | `decimal` | — | Total de ingresos por inscripciones. |
| `ingresos_ventas` | `decimal` | — | Total de ingresos por ventas. |
| `fecha_generacion` | `datetime` | — | Fecha y hora de generación del reporte. |

### 2.12 `UsuarioRol`

Tabla intermedia para asociar usuarios con roles.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario_rol` | `int` | PK | Identificador único de la asociación. |
| `id_usuario` | `int` | FK | Usuario asociado. Referencia `Usuario.id_usuario`. |
| `id_rol` | `int` | FK | Rol asignado. Referencia `Rol.id_rol`. |

## 3. Relaciones

Las siguientes relaciones se derivan de las claves foráneas incluidas en el modelo:

| Tabla origen | Cardinalidad | Tabla destino | Descripción |
| :--- | :--- | :--- | :--- |
| `Acudiente` | N:1 | `Deportista` | Cada acudiente se asocia a un deportista; un deportista puede tener varios acudientes. |
| `Pago` | N:1 | `Deportista` | Cada pago pertenece a un deportista; un deportista puede tener varios pagos. |
| `NotificacionPago` | N:1 | `Acudiente` | Cada notificación pertenece a un acudiente; un acudiente puede recibir varias notificaciones. |
| `Asistencia` | N:1 | `Deportista` | Cada registro corresponde a un deportista; un deportista puede tener varios registros. |
| `Asistencia` | N:1 | `Entrenador` | Cada registro es tomado por un entrenador; un entrenador puede registrar muchas asistencias. |
| `InscripcionTorneo` | N:1 | `Torneo` | Cada inscripción pertenece a un torneo; un torneo puede tener muchos inscritos. |
| `InscripcionTorneo` | N:1 | `Deportista` | Cada inscripción pertenece a un deportista; un deportista puede participar en varios torneos. |
| `UsuarioRol` | N:1 | `Usuario` | Cada asociación asigna un rol a un usuario; un usuario puede tener varios roles. |
| `UsuarioRol` | N:1 | `Rol` | Cada asociación referencia un rol; un rol puede asignarse a varios usuarios. |

Por medio de `InscripcionTorneo`, la relación entre `Deportista` y `Torneo` es **N:M**. Por medio de `UsuarioRol`, la relación entre `Usuario` y `Rol` también es **N:M**.

## 4. Observaciones para la implementación

1. `Usuario.rol` y la tabla `UsuarioRol` representan dos formas de asociar roles. Antes de implementar la base de datos se debe elegir una única fuente de verdad o documentar explícitamente el uso de cada una, para evitar inconsistencias.
2. Conviene agregar restricciones de unicidad para los documentos de identidad y los correos, según las reglas del negocio.
3. Para evitar duplicados, se recomienda una restricción compuesta sobre (`id_torneo`, `id_deportista`) en `InscripcionTorneo`.
4. Para evitar dos registros del mismo deportista en una fecha, se debe evaluar una restricción sobre (`id_deportista`, `fecha`) en `Asistencia`, si el negocio registra una asistencia diaria.
5. `long` es un tipo lógico del diagrama. En el motor de base de datos deberá mapearse a un tipo entero equivalente, como `BIGINT`.
6. Los valores de `estado_pago`, `tipo_documento`, `rol` y `resultado` deberían validarse mediante catálogos o restricciones, en lugar de aceptar cualquier texto.
7. Las contraseñas nunca deben almacenarse en texto plano; `Usuario.contraseña` debe contener únicamente un hash generado por un algoritmo seguro.

