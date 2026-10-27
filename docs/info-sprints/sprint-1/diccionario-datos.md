# Diccionario de datos

**Sprint:** Sprint 1  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club  
**Origen:** Modelo Entidad-Relación definido para la HU de modelado de datos  
**Ampliación Sprint 2:** se agregan las tablas `Categoria` y `Entrenamiento` y los campos y relaciones que las HU-025 (entrenadores) y HU-026 (programar entrenamientos) necesitan. Todo lo nuevo está marcado como **Nuevo (Sprint 2)** y es una propuesta pendiente de confirmar por el responsable del modelo de datos (ver sección 5). Se alinea con el diagrama de clases del dominio (`docs/diagramas-clases/01-dominio-fig.png`), que ya incluía estas entidades.

## 1. Convenciones

- `PK` identifica de forma única cada registro.
- `FK` referencia la clave primaria de otra tabla.
- `NOT NULL` indica un campo obligatorio. Las restricciones de obligatoriedad no están expresadas en el código del diagrama y deberán definirse durante la implementación de la base de datos.
- `varchar` almacena texto corto; `text`, texto de longitud variable; `int`, números enteros; `long`, enteros de mayor capacidad; `decimal`, valores numéricos con decimales; `date`, fecha; `time`, hora del día; `datetime`, fecha y hora; y `boolean`, valores verdadero/falso.
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
| `id_categoria` | `int` | FK | **Nuevo (Sprint 2).** Categoría a la que pertenece el deportista. Referencia `Categoria.id_categoria`. Ver decisión D3. |

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
| `especialidad` | `varchar` | — | **Nuevo (Sprint 2).** Especialidad del entrenador (por ejemplo, porteros o preparación física). Ya existe en el diagrama de clases del dominio. |
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
| `id_entrenamiento` | `int` | FK | **Nuevo (Sprint 2).** Entrenamiento al que corresponde la asistencia. Referencia `Entrenamiento.id_entrenamiento`. Ver decisión D5. |
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

### 2.13 `Categoria`

**Nuevo (Sprint 2).** Catálogo de categorías por edad del club (Sub-8, Sub-10, Sub-12, Sub-14 y Sub-16 en las pantallas actuales).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_categoria` | `int` | PK | Identificador único de la categoría. |
| `nombre` | `varchar` | — | Nombre de la categoría, por ejemplo `Sub-12`. Único. |
| `edad_minima` | `int` | — | Edad mínima para pertenecer a la categoría. Ver decisión D3. |
| `edad_maxima` | `int` | — | Edad máxima para pertenecer a la categoría. Ver decisión D3. |
| `id_entrenador` | `int` | FK | Entrenador que dirige la categoría. Referencia `Entrenador.id_entrenador`. Ver decisión D1. |

### 2.14 `Entrenamiento`

**Nuevo (Sprint 2).** Sesión de entrenamiento programada para una categoría (HU-026).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_entrenamiento` | `int` | PK | Identificador único del entrenamiento. |
| `id_categoria` | `int` | FK | Categoría que entrena. Referencia `Categoria.id_categoria`. |
| `id_entrenador` | `int` | FK | Entrenador a cargo de la sesión. Referencia `Entrenador.id_entrenador`. Puede ser distinto del que dirige la categoría (por ejemplo, un reemplazo). |
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

1. `Usuario.rol` y la tabla `UsuarioRol` representan dos formas de asociar roles. Antes de implementar la base de datos se debe elegir una única fuente de verdad o documentar explícitamente el uso de cada una, para evitar inconsistencias.
2. Conviene agregar restricciones de unicidad para los documentos de identidad y los correos, según las reglas del negocio.
3. Para evitar duplicados, se recomienda una restricción compuesta sobre (`id_torneo`, `id_deportista`) en `InscripcionTorneo`.
4. Para evitar dos registros del mismo deportista en una fecha, se debe evaluar una restricción sobre (`id_deportista`, `fecha`) en `Asistencia`, si el negocio registra una asistencia diaria.
5. `long` es un tipo lógico del diagrama. En el motor de base de datos deberá mapearse a un tipo entero equivalente, como `BIGINT`.
6. Los valores de `estado_pago`, `tipo_documento`, `rol` y `resultado` deberían validarse mediante catálogos o restricciones, en lugar de aceptar cualquier texto.
7. Las contraseñas nunca deben almacenarse en texto plano; `Usuario.contraseña` debe contener únicamente un hash generado por un algoritmo seguro.
8. **Nuevo (Sprint 2).** Para que no se programen dos entrenamientos en el mismo lugar a la misma hora, se recomienda una restricción de unicidad sobre (`lugar`, `fecha`, `hora_inicio`) en `Entrenamiento`. La pantalla de programación ya detecta este conflicto y ofrece alternativas; la base de datos debe hacerlo cumplir también.
9. **Nuevo (Sprint 2).** El diccionario usa `int` como identificador, mientras que el diagrama de clases del dominio usa `UUID`. Hay que unificar el tipo antes de crear las tablas (Supabase Auth usa UUID para sus usuarios).
10. **Nuevo (Sprint 2).** `Torneo.categoria` es texto libre. Una vez exista `Categoria`, conviene reemplazarlo por una clave foránea `id_categoria` para que los torneos y los deportistas hablen de las mismas categorías.
11. **Nuevo (Sprint 2).** El diagrama de clases indica que `Usuario` es la cuenta de un `Entrenador` y de un `Acudiente` (relación 1 a 0..1), pero ni `Entrenador` ni `Acudiente` tienen `id_usuario` en este diccionario. La HU-025 (registrar entrenadores) necesita esa unión para crear su acceso.

## 5. Decisiones pendientes de la ampliación del Sprint 2

Estas decisiones las debe confirmar el responsable del modelo de datos. El frontend avanza con datos en memoria detrás de una capa de servicio y se ajusta cuando se decidan.

| Id | Decisión | Propuesta |
| :--- | :--- | :--- |
| D1 | ¿Un entrenador dirige varias categorías y una categoría tiene un solo entrenador, o una categoría puede tener varios entrenadores (por ejemplo, un auxiliar)? | Seguir el diagrama de clases: `Categoria.id_entrenador` (1 entrenador, varias categorías). Si se necesitan varios entrenadores por categoría, crear la tabla intermedia `EntrenadorCategoria` (`id_entrenador`, `id_categoria`). |
| D2 | ¿`lugar` es texto libre o un catálogo? | Empezar con texto y la restricción de la observación 8. Si hay errores de escritura (`Cancha 1` y `cancha 1`), crear la tabla `Lugar`. |
| D3 | ¿La categoría de un deportista se guarda o se calcula a partir de la edad? | Guardar `Deportista.id_categoria`, asignada al inscribir según `edad_minima` y `edad_maxima`. Falta definir esas edades y cómo se trata el cambio de año. |
| D4 | ¿Cómo se guarda el aviso a deportistas y acudientes cuando se crea, cambia o elimina un entrenamiento (tarea 338)? | `NotificacionPago` solo sirve para pagos. Ampliarla a una tabla `Notificacion` con tipo, o enviar el aviso sin guardarlo. |
| D5 | ¿La asistencia se toma por entrenamiento? | Sí: agregar `Asistencia.id_entrenamiento` y cambiar la restricción de la observación 4 a (`id_deportista`, `id_entrenamiento`). `Asistencia.fecha` quedaría derivable de `Entrenamiento.fecha`. |

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
