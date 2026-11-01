# Diccionario de datos

**Sprint:** Sprint 1  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club  
**Origen:** Modelo Entidad-Relación definido para la HU de modelado de datos  
**Ampliación Sprint 2:** se agregan las tablas `Categoria`, `Entrenamiento` y `Notificacion`, además de los campos y relaciones que las HU-025 (entrenadores) y HU-026 (programar entrenamientos) necesitan. Todo lo nuevo está marcado como **Nuevo (Sprint 2)** y se alinea con el diagrama de clases del dominio (`docs/diagramas-clases/01-dominio-fig.png`).  
**Migraciones 002 y 003 (equipo):** la tabla `AcudienteDeportista`, la asistencia con tres estados, `Usuario.apellido`, la categoría del deportista y la fecha de pago opcionales, y la tabla `CodigoVerificacion` del 2FA. Están marcadas como **Migración 002** o **Migración 003**.  
**Migración 004:** se agregan las tablas `Documento`, `Venta`, `EquipoConfianza`, `SesionVerificada` y `Auditoria`, y se ajustan `Deportista`, `Entrenador`, `Usuario`, `Pago`, `Asistencia`, `Categoria` y `Entrenamiento`. Lo que cambia está marcado como **Migración 004** y explicado en la sección 6 (decisiones D6 a D16).  
**Migración 007:** categorías reales del club por año de nacimiento (9: Párvulos, Cachorros, Semillero y de Sub-11 a Sub-16), registro civil como tipo de documento del deportista, los 7 tipos de documento que pide el club, sedes y horario fijo de lunes a sábado (nueva tabla `FranjaHorario`) y buckets de Storage para fotos y documentos. Marcado como **Migración 007** y explicado en la sección 6 (decisiones D18 a D21).  
El esquema vigente son las migraciones de `supabase/migrations/` aplicadas en orden: `001_schema_inicial.sql`, `002_modelo_datos.sql`, `003_codigo_verificacion_2fa.sql`, `004_ajustes_modelo.sql`, `005_seguridad_sesion_2fa.sql`, `006_sesion_verificada_por_sesion.sql` y `007_categorias_club_y_horario.sql`.

## 1. Convenciones

- `PK` identifica de forma única cada registro.
- `FK` referencia la clave primaria de otra tabla.
- `NOT NULL` indica un campo obligatorio. Las restricciones de obligatoriedad no están expresadas en el código del diagrama y deberán definirse durante la implementación de la base de datos.
- `UUID` identifica registros y relaciones entre tablas; `varchar` almacena texto corto; `text`, texto de longitud variable; `int`, números enteros; `decimal`, valores numéricos con decimales; `date`, fecha; `time`, hora del día; `datetime`, fecha y hora; y `boolean`, valores verdadero/falso.
- En PostgreSQL/Supabase, `UUID` se implementa como `uuid`, `decimal` como `numeric`, `datetime` como `timestamp with time zone` y `boolean` como `boolean`.
- Los campos `edad` son calculados a partir de `fecha_nacimiento`; no deben mantenerse como un valor independiente sin una regla de actualización.
- Los campos `foto`, `documentos`, `Documento.ruta` y `Pago.recibo_ruta` almacenan rutas al almacenamiento de archivos (Supabase Storage), no el contenido binario ni URLs firmadas, que vencen a los pocos minutos.
- **Migración 004.** Los valores en dinero son pesos colombianos **enteros**: las columnas son `numeric(12,2)`, pero una regla `CHECK` rechaza los centavos (con `numeric(12,0)` PostgreSQL los redondearía sin avisar).
- **Migración 004.** Los correos se guardan en minúsculas y son únicos sin distinguir mayúsculas (índice único sobre `lower(correo)`).
- **Migración 004.** Los códigos de verificación y los identificadores de equipos de confianza se guardan solo como hash; nunca el valor original.
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
| `tipo_documento` | `varchar` | — | Tipo de documento de identidad: `TI`, `RC` (registro civil), `CE` o `PASAPORTE`. **Migración 007:** se agrega `RC` y sale `CC` (D18). |
| `documento_identidad` | `varchar` | — | Número del documento de identidad como texto, para conservar ceros iniciales y soportar distintos formatos. |
| `fecha_nacimiento` | `date` | — | Fecha de nacimiento. |
| `edad` | `int` | Calculado | Edad calculada a partir de `fecha_nacimiento`. |
| `posicion` | `varchar` | — | Posición habitual de juego. |
| `eps` | `varchar` | — | Entidad promotora de salud. |
| `telefono` | `varchar` | — | Teléfono de contacto. |
| `correo` | `varchar` | — | Correo electrónico. |
| `direccion` | `varchar` | — | Dirección de residencia. |
| `foto` | `varchar` | — | Ruta de la fotografía en el bucket privado `fotos` de Supabase Storage (JPG o PNG, máximo 5 MB). El backend entrega URL firmadas (D20). |
| `fecha_registro` | `date` | — | Fecha de registro en el sistema. |
| `id_categoria` | `UUID` | FK, nullable | **Nuevo (Sprint 2).** Categoría a la que pertenece el deportista. Referencia `Categoria.id_categoria`. Ver decisión D3. **Migración 002:** nullable mientras se asigna el grupo. **Migración 004:** obligatoria cuando la inscripción está `ACTIVA` (D9). **Migración 007:** se asigna automáticamente según el año de nacimiento (D18). |
| `estado_inscripcion` | `varchar` | — | **Migración 004.** `PENDIENTE` (recién inscrito, documentos en revisión), `ACTIVA` (aprobado, con categoría) o `RETIRADA`. Por defecto `PENDIENTE`. Ver decisión D9. |

**Migración 004:** se quita `documentos`; los documentos del deportista están en la tabla `Documento` (D10). Los acudientes se asocian por `AcudienteDeportista` (migración 002).

### 2.2 `Acudiente`

Personas responsables de uno o varios deportistas. **Migración 002:** un acudiente puede tener varios deportistas (por ejemplo, hermanos) y un deportista varios acudientes; la relación está en `AcudienteDeportista` (D6).

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
| `id_usuario` | `UUID` | FK | Cuenta de acceso asociada. Referencia `Usuario.id_usuario`. Nullable mientras se registra el acudiente. |

**Migración 002:** se quita `id_deportista` (pasa a `AcudienteDeportista`).

### 2.3 `AcudienteDeportista`

**Migración 002.** Tabla puente para asociar acudientes y deportistas. Permite que un acudiente tenga varios hijos y que un deportista tenga varios acudientes (D6).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_acudiente` | `UUID` | PK/FK | Acudiente asociado. Referencia `Acudiente.id_acudiente`. |
| `id_deportista` | `UUID` | PK/FK | Deportista asociado. Referencia `Deportista.id_deportista`. |

### 2.4 `Entrenador`

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
| `activo` | `boolean` | — | **Migración 004.** `false` si el entrenador fue desactivado: conserva su historial, pero no recibe categorías ni entrenamientos nuevos. Por defecto `true`. Ver decisión D8. |

### 2.5 `Usuario`

Perfil y estado de una cuenta administrada por Supabase Auth.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario` | `UUID` | PK | Identificador único del usuario. |
| `nombre` | `varchar` | — | Nombre visible del usuario. |
| `apellido` | `varchar` | — | **Migración 002.** Apellidos del usuario. |
| `correo` | `varchar` | — | Correo utilizado para el acceso. |
| `activo` | `boolean` | — | Indica si el acceso de la cuenta está vigente. Revocar el acceso no elimina la cuenta de Supabase Auth. |
| `creado_en` | `datetime` | — | Fecha y hora de creación del perfil. |
| `alto_contraste` | `boolean` | — | **Migración 004.** Preferencia de accesibilidad: modo de alto contraste. Por defecto `false` (CU-08). |
| `tamano_texto` | `int` | — | **Migración 004.** Preferencia de accesibilidad: tamaño del texto en porcentaje, de 100 a 200. Por defecto 100 (CU-08). |
| `paleta_daltonismo` | `boolean` | — | **Migración 004.** Preferencia de accesibilidad: paleta apta para daltonismo. Por defecto `false` (CU-08). |

### 2.6 `Pago`

Cobros y pagos de un deportista. **Migración 004:** un cobro se crea `PENDIENTE` y sin `fecha_pago` en la fecha de corte; cuando se paga queda `APROBADO` con la fecha, el método y el comprobante. Ver decisión D13.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_pago` | `UUID` | PK | Identificador único del pago. |
| `id_deportista` | `UUID` | FK | Deportista que realiza o recibe el cobro. Referencia `Deportista.id_deportista`. |
| `tipo_pago` | `varchar` | — | **Migración 004.** `MENSUALIDAD` o `INSCRIPCION`. Separa los ingresos del reporte financiero. |
| `periodo` | `date` | — | **Migración 004.** Mes que cubre una mensualidad, guardado como el día 1 de ese mes (`2026-10-01`). Obligatorio en mensualidades y nulo en inscripciones. |
| `fecha_vencimiento` | `date` | — | **Migración 004.** Fecha límite de pago. Con ella se calcula si el pago está vencido y se envían los recordatorios (RF006). |
| `fecha_pago` | `date` | — | Fecha en que se hizo el pago. **Migración 002:** `NULL` mientras el cobro está pendiente. **Migración 004:** obligatoria si está `APROBADO`. |
| `monto` | `decimal` | — | Valor del cobro en pesos enteros. |
| `concepto` | `varchar` | — | Concepto del pago, por ejemplo `Mensualidad octubre`. |
| `metodo` | `varchar` | — | **Migración 004.** Medio de pago: `EFECTIVO`, `TRANSFERENCIA`, `PSE`, `NEQUI` o `TARJETA`. Obligatorio si está `APROBADO`. |
| `referencia_wompi` | `varchar` | — | **Migración 004.** Referencia de la transacción en Wompi. Única: un webhook repetido no registra el pago dos veces. Obligatoria en pagos aprobados por `PSE`, `NEQUI` o `TARJETA`, y prohibida en los manuales. |
| `referencia_manual` | `varchar` | — | **Migración 004.** Soporte de un pago manual (por ejemplo, el número de la transferencia). No sale en el comprobante. |
| `numero_comprobante` | `varchar` | — | **Migración 004.** Número del comprobante, `FD-AAAA-NNNNNN`. Único. Lo genera la función `siguiente_numero_comprobante()`. |
| `recibo_ruta` | `varchar` | — | Ruta del comprobante en PDF dentro del bucket privado, `AAAA/FD-AAAA-NNNNNN.pdf`. **Migración 004:** antes `recibo_url`; guarda la ruta, no una URL. |
| `estado_pago` | `varchar` | — | `PENDIENTE`, `APROBADO` o `RECHAZADO`. "Vencido" no es un estado: se calcula (D13). |
| `nota_interna` | `text` | — | **Migración 004.** Observación interna del tesorero. No sale en el comprobante. |
| `registrado_por` | `UUID` | FK | **Migración 004.** Usuario que registró un pago manual. Referencia `Usuario.id_usuario`. Nulo en los pagos en línea. |
| `creado_en` | `datetime` | — | **Migración 004.** Fecha y hora en que se creó el cobro. |

La vista `pago_con_estado` agrega la columna calculada `vencido`: verdadera si el pago está `PENDIENTE` y su `fecha_vencimiento` ya pasó (hora de Colombia).

### 2.7 `Torneo`

Información de los torneos en los que puede participar el club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_torneo` | `UUID` | PK | Identificador único del torneo. |
| `nombre_torneo` | `varchar` | — | Nombre del torneo. |
| `id_categoria` | `UUID` | FK | Categoría participante. Referencia `Categoria.id_categoria`. |
| `lugar` | `varchar` | — | Lugar donde se realiza el torneo. |
| `fecha_inicio` | `date` | — | Fecha de inicio. |
| `fecha_fin` | `date` | — | Fecha de finalización. |
| `costo` | `decimal` | — | Costo de inscripción o participación, en pesos enteros. |

### 2.8 `NotificacionPago`

Mensajes de notificación de pagos dirigidos a acudientes.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_notificacion_pago` | `UUID` | PK | Identificador único de la notificación. |
| `id_acudiente` | `UUID` | FK | Acudiente destinatario. Referencia `Acudiente.id_acudiente`. |
| `mensaje` | `text` | — | Contenido de la notificación. |
| `fecha_envio` | `datetime` | — | Fecha y hora del envío. |
| `leido` | `boolean` | — | Indica si el acudiente leyó la notificación. |

### 2.9 `Notificacion`

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

### 2.10 `Asistencia`

Registro de asistencia de un deportista tomado por un entrenador.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_asistencia` | `UUID` | PK | Identificador único del registro de asistencia. |
| `id_deportista` | `UUID` | FK | Deportista registrado. Referencia `Deportista.id_deportista`. |
| `id_entrenador` | `UUID` | FK | Entrenador que registra la asistencia (el responsable que pide RF005). Referencia `Entrenador.id_entrenador`. |
| `id_entrenamiento` | `UUID` | FK | **Nuevo (Sprint 2).** Entrenamiento al que corresponde la asistencia. Referencia `Entrenamiento.id_entrenamiento`. Ver decisión D5. |
| `estado` | `varchar(20)` | — | **Migración 002.** `PRESENTE`, `AUSENTE` o `JUSTIFICADO`. Reemplaza `presente` (D11). |
| `registrado_en` | `datetime` | — | **Migración 004.** Fecha y hora en que se tomó la asistencia. |
| `observaciones` | `text` | — | Comentarios adicionales sobre la asistencia. |

**Migración 002:** se quita `presente` (pasa a `estado`). **Migración 004:** se quita `fecha`, que se toma de `Entrenamiento.fecha` como preveía la decisión D5.

### 2.11 `InscripcionTorneo`

Tabla intermedia que registra la participación de deportistas en torneos.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_inscripcion` | `UUID` | PK | Identificador único de la inscripción. |
| `id_torneo` | `UUID` | FK | Torneo de la inscripción. Referencia `Torneo.id_torneo`. |
| `id_deportista` | `UUID` | FK | Deportista inscrito. Referencia `Deportista.id_deportista`. |
| `resultado` | `varchar` | — | Resultado o clasificación obtenida. |

### 2.12 `Rol`

Catálogo de roles disponibles en el sistema.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_rol` | `UUID` | PK | Identificador único del rol. |
| `nombre` | `varchar` | — | Nombre del rol. Único; valores: `ADMINISTRADOR`, `TESORERO`, `ENTRENADOR` y `ACUDIENTE`. |

### 2.13 `ReporteFinanciero`

Resumen mensual de los ingresos del club.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_reporte` | `UUID` | PK | Identificador único del reporte. |
| `mes` | `int` | — | Mes del periodo reportado. |
| `anio` | `int` | — | Año del periodo reportado (sin ñ, como en la tabla). |
| `ingresos_mensualidades` | `decimal` | — | Total de ingresos por mensualidades. |
| `ingresos_inscripciones` | `decimal` | — | Total de ingresos por inscripciones. |
| `ingresos_ventas` | `decimal` | — | Total de ingresos por ventas de uniformes (tabla `Venta`, D14). |
| `fecha_generacion` | `datetime` | — | Fecha y hora de generación del reporte. |

### 2.14 `UsuarioRol`

Tabla intermedia para asociar usuarios con roles.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario_rol` | `UUID` | PK | Identificador único de la asociación. |
| `id_usuario` | `UUID` | FK | Usuario asociado. Referencia `Usuario.id_usuario`. |
| `id_rol` | `UUID` | FK | Rol asignado. Referencia `Rol.id_rol`. |

### 2.15 `Categoria`

**Nuevo (Sprint 2).** Catálogo de categorías del club. **Migración 007:** son 9 y se asignan por **año de nacimiento** (edad por año = año actual − año de nacimiento), no por edad cumplida (D18).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_categoria` | `UUID` | PK | Identificador único de la categoría. |
| `nombre` | `varchar` | — | Nombre de la categoría: `Párvulos`, `Cachorros`, `Semillero` o de `Sub-11` a `Sub-16`. Único. |
| `edad_minima` | `int` | — | Edad por año de nacimiento mínima: 4 en Párvulos, 7 en Cachorros, 9 en Semillero y el número de la categoría en las `Sub-N` (de 11 a 16). |
| `edad_maxima` | `int` | — | Edad por año de nacimiento máxima: 6 en Párvulos, 8 en Cachorros, 10 en Semillero y el mismo número que la mínima en las `Sub-N`. |
| `id_entrenador` | `UUID` | FK | Entrenador que dirige la categoría. Referencia `Entrenador.id_entrenador`. Ver decisión D1. **Migración 004:** nullable; una categoría puede no tener entrenador asignado todavía (D7). |

| Categoría | Edad por año | Nacidos en (2026) |
| :--- | :--- | :--- |
| Párvulos | 4 a 6 | 2020, 2021 y 2022 |
| Cachorros | 7 y 8 | 2018 y 2019 |
| Semillero | 9 y 10 | 2016 y 2017 |
| Sub-11 | 11 | 2015 |
| Sub-12 | 12 | 2014 |
| Sub-13 | 13 | 2013 |
| Sub-14 | 14 | 2012 |
| Sub-15 | 15 | 2011 |
| Sub-16 | 16 | 2010 |

**Migración 004:** las cinco categorías (Sub-8 a Sub-16) se crean en la migración, sin entrenador. **Migración 007:** pasan a ser las 9 del club. `Sub-8` se convierte en `Párvulos` y `Sub-10` en `Semillero`, conservando su id, y no se admiten deportistas fuera de 4 a 16 años (D18).

### 2.16 `Entrenamiento`

**Nuevo (Sprint 2).** Sesión de entrenamiento programada para una categoría (HU-026).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_entrenamiento` | `UUID` | PK | Identificador único del entrenamiento. |
| `id_categoria` | `UUID` | FK | Categoría que entrena. Referencia `Categoria.id_categoria`. |
| `id_entrenador` | `UUID` | FK | Entrenador a cargo de la sesión. Referencia `Entrenador.id_entrenador`. Puede ser distinto del que dirige la categoría (por ejemplo, un reemplazo). |
| `fecha` | `date` | — | Fecha del entrenamiento. |
| `hora_inicio` | `time` | — | Hora de inicio. |
| `hora_fin` | `time` | — | **Migración 004.** Hora de fin, posterior a `hora_inicio`. Necesaria para detectar cruces de horario (CU-05). Los entrenamientos que ya existían quedan de 90 minutos. |
| `lugar` | `varchar` | — | Sede del entrenamiento: `Horizontes` o `UVA El Paraíso`. **Migración 007:** restricción `entrenamiento_sede_ck`; antes era texto libre (D2, D19). |
| `estado` | `varchar` | — | **Migración 004.** `PROGRAMADO` o `CANCELADO`. Cancelar no borra el entrenamiento, así sus avisos y asistencias conservan la referencia. Por defecto `PROGRAMADO`. |

### 2.17 `Documento`

**Migración 004.** Documentos requeridos del deportista, con su revisión y vencimiento (RF009, CU-04, D10).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_documento` | `UUID` | PK | Identificador único del documento. |
| `id_deportista` | `UUID` | FK | Deportista dueño del documento. Referencia `Deportista.id_deportista`. |
| `tipo` | `varchar` | — | **Migración 007:** uno de los 7 tipos que pide el club (D21). Obligatorios: `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION` y `AUTORIZACION_HABEAS_DATA`. Si aplican: `PAZ_Y_SALVO`, `CARTA_RENUNCIA` y `TRASPASO_NACIONAL`. Antes (004): `DOCUMENTO_IDENTIDAD`, `FICHA_MEDICA`, `PERMISO`, `CONTRATO` u `OTRO`. |
| `ruta` | `varchar` | — | Ruta del archivo en el bucket privado de documentos. Única. |
| `nombre_original` | `varchar` | — | Nombre del archivo como lo cargó el usuario, para mostrarlo. |
| `tipo_contenido` | `varchar` | — | `application/pdf`, `image/jpeg` o `image/png`. |
| `tamano_bytes` | `int` | — | Tamaño del archivo, de 1 byte a 5 MB (5.242.880 bytes). |
| `estado` | `varchar` | — | `PENDIENTE`, `APROBADO` o `RECHAZADO`. Por defecto `PENDIENTE`. |
| `comentario` | `text` | — | Observación del administrador. Obligatoria si el documento está `RECHAZADO`. |
| `fecha_vencimiento` | `date` | — | Fecha en que el documento deja de ser válido (por ejemplo, el certificado de estudio). Opcional; se usa para las alertas de vencimiento. |
| `cargado_por` | `UUID` | FK | Usuario que cargó el archivo. Referencia `Usuario.id_usuario`. |
| `cargado_en` | `datetime` | — | Fecha y hora de la carga. |
| `revisado_por` | `UUID` | FK | Administrador que aprobó o rechazó. Referencia `Usuario.id_usuario`. |
| `revisado_en` | `datetime` | — | Fecha y hora de la revisión. Nulo mientras está `PENDIENTE` y obligatorio después. |

### 2.18 `Venta`

**Migración 004.** Ventas de uniformes, para los ingresos por ventas del reporte financiero (RF003, D14).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_venta` | `UUID` | PK | Identificador único de la venta. |
| `fecha` | `date` | — | Fecha de la venta. Por defecto, hoy. |
| `concepto` | `varchar` | — | Qué se vendió, por ejemplo `Uniforme de juego talla 12`. |
| `cantidad` | `int` | — | Unidades vendidas; mayor que 0. |
| `valor_unitario` | `decimal` | — | Precio de una unidad, en pesos enteros. |
| `total` | `decimal` | Calculado | `cantidad × valor_unitario`, calculado por la base de datos. |
| `metodo` | `varchar` | — | Medio de pago, con los mismos valores de `Pago.metodo`. |
| `id_deportista` | `UUID` | FK | Deportista que compró, si aplica. Referencia `Deportista.id_deportista`. |
| `registrado_por` | `UUID` | FK | Usuario que registró la venta. Referencia `Usuario.id_usuario`. |
| `creado_en` | `datetime` | — | Fecha y hora del registro. |

### 2.19 `CodigoVerificacion`

**Migración 003.** Códigos de 6 dígitos del segundo paso del inicio de sesión (CU-00, D16).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_codigo` | `UUID` | PK | Identificador único del código. |
| `id_usuario` | `UUID` | FK | Usuario al que se envió. Referencia `Usuario.id_usuario`. |
| `codigo_hash` | `varchar` | — | Hash del código. El código en claro nunca se guarda. |
| `expira_en` | `datetime` | — | Vencimiento: 10 minutos después de enviado. |
| `intentos` | `int` | — | Intentos fallidos con este código, de 0 a 5. |
| `bloqueado_hasta` | `datetime` | — | Hasta cuándo está bloqueada la verificación tras 5 intentos fallidos (15 minutos). Nulo si no está bloqueada. |
| `ultimo_envio` | `datetime` | — | Último envío del código; limita los reenvíos a uno por minuto. |
| `usado` | `boolean` | — | `true` cuando el código ya se validó; un código usado no vuelve a servir. |
| `creado_en` | `datetime` | — | Fecha y hora de creación. |

### 2.20 `EquipoConfianza`

**Migración 004.** Navegadores marcados con "Recordar este equipo" (D16).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_equipo` | `UUID` | PK | Identificador único del equipo. |
| `id_usuario` | `UUID` | FK | Dueño del equipo. Referencia `Usuario.id_usuario`. |
| `token_hash` | `varchar` | — | Hash del identificador aleatorio que va en la cookie HttpOnly. Único. |
| `descripcion` | `varchar` | — | Navegador y sistema operativo, para que el usuario reconozca el equipo. |
| `creado_en` | `datetime` | — | Fecha y hora de registro. |
| `expira_en` | `datetime` | — | Vencimiento: 30 días después de creado. |
| `ultimo_uso_en` | `datetime` | — | Último inicio de sesión desde este equipo. |
| `revocado_en` | `datetime` | — | Momento en que se revocó (cierre de sesión en todos los equipos o cambio de contraseña). |

### 2.21 `SesionVerificada`

Sesiones de Supabase Auth que ya completaron el segundo paso (D16, D17). Una fila por **sesión**, no por usuario: verificar el código en un equipo no habilita las demás sesiones del mismo usuario (PPI 4.2: "la sesión queda marcada como verificada").

Historia de la tabla:
- **Migración 004:** la crea por sesión (`id_sesion`, `id_usuario`, `verificada_en`, `expira_en`).
- **Migración 005:** la cambió a una fila por usuario (`id_usuario` como PK y `verificada_hasta`). Con ese diseño, verificar en un equipo habilitaba todas las sesiones del usuario durante 12 horas.
- **Migración 006:** vuelve a una fila por sesión, identificada por el claim `session_id` del JWT. Descarta las filas de la 005, lo que solo obliga a verificar de nuevo. Es la estructura vigente.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_sesion` | `UUID` | PK | El claim `session_id` del JWT de Supabase. Un token sin ese claim se rechaza con `401`. |
| `id_usuario` | `UUID` | FK | Dueño de la sesión. Referencia `Usuario.id_usuario` (borrado en cascada). Índice `sesion_verificada_usuario_idx`, para cerrar todas sus sesiones. |
| `verificada_en` | `datetime` | — | Momento en que se completó el segundo paso. |
| `expira_en` | `datetime` | — | Hasta cuándo vale la verificación: `verificada_en` + `ReglasVerificacion.VIGENCIA_SESION` (12 horas). Debe ser posterior a `verificada_en`. |

Se borra una fila al cerrar sesión (`POST /api/auth/logout`, solo la de ese token) y todas las del usuario cuando se le revoca el acceso. Las vencidas se limpian al verificar un código nuevo.

### 2.22 `Auditoria`

**Migración 004.** Registro de eventos de seguridad y de notificaciones fallidas (D16).

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_auditoria` | `bigint` | PK | Consecutivo del evento. |
| `fecha` | `datetime` | — | Fecha y hora del evento. |
| `id_usuario` | `UUID` | FK | Usuario relacionado, si lo hay. Referencia `Usuario.id_usuario`. |
| `evento` | `varchar` | — | Nombre del evento, por ejemplo `INICIO_SESION`, `CODIGO_FALLIDO`, `BLOQUEO_2FA`, `EQUIPO_REGISTRADO` o `NOTIFICACION_NO_ENVIADA`. |
| `detalle` | `jsonb` | — | Datos adicionales del evento. Nunca códigos, tokens, contraseñas ni datos de menores. |
| `ip` | `inet` | — | Dirección IP de la petición, si aplica. |

### 2.23 `FranjaHorario`

**Migración 007.** Horario fijo semanal del club, de lunes a sábado (RF004, CU-05, D19). Los entrenamientos concretos (`Entrenamiento`) se programan dentro de este horario.

| Atributo | Tipo | Clave | Descripción |
| :--- | :--- | :--- | :--- |
| `id_franja` | `UUID` | PK | Identificador único de la franja. |
| `id_categoria` | `UUID` | FK | Categoría que entrena. Referencia `Categoria.id_categoria`. Se borra junto con la categoría. |
| `dia` | `smallint` | — | Día de la semana: de 1 (lunes) a 6 (sábado). No hay domingo. |
| `hora_inicio` | `time` | — | Hora de inicio. |
| `hora_fin` | `time` | — | Hora de fin, posterior a `hora_inicio`. |
| `sede` | `varchar` | — | `Horizontes` o `UVA El Paraíso`. |
| `id_entrenador` | `UUID` | FK, nullable | Entrenador de la franja. Referencia `Entrenador.id_entrenador`. NULL significa "por turnos": no hay un entrenador fijo y el club rota quién dirige (Párvulos, Cachorros y Semillero). |
| `grupo` | `varchar` | nullable | `mañana` o `tarde`. Describe la franja, no al deportista: solo se usa en las franjas de Cachorros y Semillero de martes y jueves, que tienen dos sesiones alternativas el mismo día porque unos niños estudian en la mañana y otros en la tarde. El grupo de cada deportista no se guarda en la base de datos. |

Un mismo entrenador no puede tener dos franjas que se solapen el mismo día (restricción `franja_cruce_entrenador_ex`). Categorías distintas sí pueden coincidir en hora y sede. La plantilla del club (30 franjas, tomadas del Excel oficial del club) está en `supabase/seeds/franjas_club.sql`; no es una migración y se carga a mano.

## 3. Relaciones

Las siguientes relaciones se derivan de las claves foráneas incluidas en el modelo:

| Tabla origen | Cardinalidad | Tabla destino | Descripción |
| :--- | :--- | :--- | :--- |
| `AcudienteDeportista` | N:1 | `Acudiente` | **Migración 002.** Cada vínculo pertenece a un acudiente; un acudiente puede tener varios deportistas. |
| `AcudienteDeportista` | N:1 | `Deportista` | **Migración 002.** Cada vínculo pertenece a un deportista; un deportista puede tener varios acudientes. |
| `Documento` | N:1 | `Deportista` | **Migración 004.** Cada documento es de un deportista; un deportista tiene varios documentos. |
| `Venta` | N:1 | `Deportista` | **Migración 004.** Una venta puede asociarse al deportista que compró (opcional). |
| `CodigoVerificacion` | N:1 | `Usuario` | **Migración 003.** Un usuario recibe muchos códigos a lo largo del tiempo. |
| `EquipoConfianza` | N:1 | `Usuario` | **Migración 004.** Un usuario puede tener varios equipos de confianza. |
| `SesionVerificada` | N:1 | `Usuario` | **Migración 004.** Un usuario puede tener varias sesiones verificadas. |
| `Auditoria` | N:1 | `Usuario` | **Migración 004.** Un usuario puede tener muchos eventos de auditoría. |
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
| `Categoria` | N:1 | `Entrenador` | **Nuevo.** Cada categoría la dirige como máximo un entrenador; un entrenador puede dirigir varias categorías (decisiones D1 y D7). |
| `FranjaHorario` | N:1 | `Categoria` | **Migración 007.** Cada franja es de una categoría; una categoría tiene varias franjas a la semana. |
| `FranjaHorario` | N:1 | `Entrenador` | **Migración 007.** Una franja puede tener un entrenador fijo (opcional); un entrenador puede tener varias franjas. |
| `Entrenamiento` | N:1 | `Categoria` | **Nuevo.** Cada entrenamiento es de una categoría; una categoría tiene muchos entrenamientos. |
| `Entrenamiento` | N:1 | `Entrenador` | **Nuevo.** Cada entrenamiento tiene un entrenador a cargo; un entrenador puede tener muchos entrenamientos. |
| `Asistencia` | N:1 | `Entrenamiento` | **Nuevo.** Cada registro de asistencia pertenece a un entrenamiento; un entrenamiento tiene muchas asistencias (decisión D5). |

Por medio de `InscripcionTorneo`, la relación entre `Deportista` y `Torneo` es **N:M**. Por medio de `UsuarioRol`, la relación entre `Usuario` y `Rol` también es **N:M**. **Migración 002:** por medio de `AcudienteDeportista`, la relación entre `Deportista` y `Acudiente` también es **N:M**.

## 4. Observaciones para la implementación

1. `UsuarioRol` es la única fuente de verdad para los roles; `Usuario` no almacena un campo `rol`.
2. Se debe aplicar `UNIQUE` a `correo` en `Usuario`, a (`tipo_documento`, `documento_identidad`) en `Deportista`, `Acudiente` y `Entrenador`, y a `correo` en cada perfil que lo almacene.
3. Para evitar duplicados, se debe aplicar `UNIQUE (id_torneo, id_deportista)` en `InscripcionTorneo`.
4. Para evitar duplicados, se debe aplicar `UNIQUE (id_deportista, id_entrenamiento)` en `Asistencia`.
5. `documento_identidad` se almacena como `varchar`, no como entero, para conservar ceros iniciales y soportar documentos alfanuméricos.
6. Los valores permitidos son `tipo_documento`: `CC`, `TI`, `CE`, `PASAPORTE`; `estado_pago`: `PENDIENTE`, `APROBADO`, `RECHAZADO`; `resultado`: `PENDIENTE`, `CAMPEON`, `SUBCAMPEON`, `TERCER_LUGAR`, `OTRO`. Deben validarse con `CHECK` o catálogos equivalentes. **Migración 004:** también `estado_inscripcion`, `Documento.tipo`, `Documento.estado`, `Asistencia.estado`, `Pago.tipo_pago`, `Pago.metodo`, `Venta.metodo` y `Entrenamiento.estado`, con los valores de cada tabla.
7. Las contraseñas no se almacenan en el esquema de la aplicación: Supabase Auth las gestiona en `auth.users`. `Usuario.id_usuario` es una FK/PK hacia `auth.users.id`.
8. **Nuevo (Sprint 2), reemplazada en la migración 004.** Antes era `UNIQUE (lugar, fecha, hora_inicio)`, que solo impedía dos entrenamientos que empezaran exactamente a la misma hora. Ahora tres restricciones de exclusión impiden que se **crucen** los entrenamientos `PROGRAMADO` de una misma cancha, de una misma categoría o de un mismo entrenador (D15). Los cancelados no cuentan. La pantalla de programación detecta el conflicto y ofrece alternativas; la base de datos lo hace cumplir también.
9. **Nuevo (Sprint 2).** Todas las PK y FK usan `UUID`, compatible con Supabase Auth y con el diagrama de clases del dominio.
10. **Nuevo (Sprint 2).** `Torneo.id_categoria` referencia `Categoria.id_categoria`, por lo que torneos y deportistas usan el mismo catálogo.
11. **Nuevo (Sprint 2).** `Entrenador.id_usuario` y `Acudiente.id_usuario` permiten asociar cada perfil con su cuenta de acceso; ambos son nullable durante el registro.
12. `Entrenador.id_usuario` y `Acudiente.id_usuario` deben ser `UNIQUE` para que una cuenta no se vincule a dos perfiles del mismo tipo.
13. **Migración 004.** Un deportista tiene como máximo una mensualidad por mes (índice único sobre `id_deportista` y `periodo`; los cobros `RECHAZADO` no cuentan).
14. **Migración 004.** Todas las tablas tienen RLS activo y sin políticas: solo el backend (rol `postgres`) lee y escribe. El frontend nunca consulta las tablas directamente.

## 5. Decisiones confirmadas de la ampliación del Sprint 2

Estas decisiones fueron confirmadas para cerrar el modelo antes de crear tablas. El frontend avanza con datos en memoria detrás de una capa de servicio y se ajusta cuando se implementen los endpoints.

| Id | Decisión | Propuesta |
| :--- | :--- | :--- |
| D1 | ¿Un entrenador dirige varias categorías y una categoría tiene un solo entrenador, o una categoría puede tener varios entrenadores (por ejemplo, un auxiliar)? | Confirmado: seguir el diagrama de clases con `Categoria.id_entrenador` (1 entrenador, varias categorías). No se crea `EntrenadorCategoria`. |
| D2 | ¿`lugar` es texto libre o un catálogo? | Confirmado: texto, con restricción de unicidad sobre (`lugar`, `fecha`, `hora_inicio`). **Migración 007:** `lugar` pasa a ser una de las dos sedes del club (D19). |
| D3 | ¿La categoría de un deportista se guarda o se calcula a partir de la edad? | Confirmado: guardar `Deportista.id_categoria` y asignarla según la edad cumplida: `Sub-8` (0–7), `Sub-10` (8–9), `Sub-12` (10–11), `Sub-14` (12–13) y `Sub-16` (14–15). Se recalcula al registrar y se actualiza explícitamente cuando cambie la edad; no se cambia silenciosamente durante una consulta. **Reemplazada por D18 (migración 007):** las categorías son las 9 del club y se asignan por año de nacimiento, no por edad cumplida. |
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

## 6. Decisiones de la revisión del modelo (migraciones 002 a 007)

Ajustes que salieron de revisar el backend contra el PPI. D6, D11 (los tres estados) y la tabla de códigos de D16 los aplicaron las migraciones 002 y 003 del equipo; el resto está en `supabase/migrations/004_ajustes_modelo.sql`.

| Id | Problema | Decisión |
| :--- | :--- | :--- |
| D6 | `Acudiente.id_deportista` era obligatorio y el documento y el correo del acudiente son únicos: una madre con dos hijos en el club no se podía registrar. | **Migración 002.** Tabla intermedia `AcudienteDeportista` (N:M). Se quita `Acudiente.id_deportista`; las asociaciones existentes se copian a la tabla nueva. |
| D7 | `Categoria.id_entrenador` era obligatorio y la migración no creaba categorías: sin un entrenador no existía ninguna categoría, y sin categorías no se podía inscribir a nadie. | `id_entrenador` pasa a ser opcional y la migración crea las categorías sin entrenador (hoy son 9, ver D18). Se mantiene D1: como máximo un entrenador por categoría. |
| D8 | No había forma de desactivar un entrenador (CU-10, camino alternativo 2). | `Entrenador.activo`. Desactivar no borra: conserva el historial y lo retira de las asignaciones futuras. |
| D9 | El PPI dice que la inscripción queda pendiente hasta que el administrador aprueba la documentación y asigna la categoría (BPMN 3.1, CU-01 y CU-04), pero el deportista no tenía estado y la categoría era obligatoria desde el inicio. | `Deportista.estado_inscripcion` (`PENDIENTE`, `ACTIVA`, `RETIRADA`). La categoría es opcional mientras está pendiente y obligatoria cuando está activa. Un rechazo de documentos no cambia el estado: el acudiente los corrige y la inscripción sigue pendiente. |
| D10 | `Deportista.documentos` era un solo texto: no permitía aprobar o rechazar cada documento, dejar comentarios ni alertar vencimientos (RF009). | Tabla `Documento`, un registro por archivo, con estado, comentario obligatorio al rechazar, vencimiento y quién lo cargó y revisó. Acepta PDF, JPG y PNG de hasta 5 MB. Se quita `Deportista.documentos`. |
| D11 | `Asistencia.presente` era booleano y el PPI pide presente, ausente o justificado (N005, RF005, CU-03). | **Migración 002:** `Asistencia.estado` con los tres valores. **Migración 004:** `registrado_en` con la hora y se quita `Asistencia.fecha`, que sale de `Entrenamiento.fecha` (D5). |
| D12 | El backend y el frontend manejan pesos enteros, y la base de datos aceptaba centavos. | Los montos siguen como `numeric(12,2)`, pero un `CHECK` rechaza los centavos. Se descartó `numeric(12,0)` porque redondea sin avisar. |
| D13 | `Pago` no tenía periodo, vencimiento, método, referencia de Wompi ni número de comprobante, aunque el comprobante en PDF ya los usa. Un cobro pendiente no podía existir porque `fecha_pago` era obligatoria. | Se agregan `tipo_pago`, `periodo`, `fecha_vencimiento`, `metodo`, `referencia_wompi` (única), `referencia_manual`, `numero_comprobante` (con la función `siguiente_numero_comprobante()`), `nota_interna`, `registrado_por` y `creado_en`. `fecha_pago` es opcional mientras el cobro está pendiente. `recibo_url` se renombra `recibo_ruta`. **Vencido se calcula, no se guarda:** un pago `PENDIENTE` con `fecha_vencimiento` pasada (vista `pago_con_estado`). Así no hace falta una tarea que actualice estados y nunca queda desactualizado. |
| D14 | El reporte financiero tenía `ingresos_ventas`, pero no existía de dónde sacar las ventas. | El club solo vende uniformes: tabla `Venta` con concepto, cantidad, valor unitario y total calculado. |
| D15 | `Entrenamiento` solo tenía hora de inicio, así que no se podían detectar cruces de horario (CU-05); y eliminar un entrenamiento dejaba sus avisos sin referencia. | `hora_fin` y `estado` (`PROGRAMADO`, `CANCELADO`). Tres restricciones de exclusión impiden cruces de cancha, de categoría y de entrenador entre entrenamientos programados. Cancelar reemplaza a borrar. |
| D16 | El PPI define la verificación en dos pasos, los equipos de confianza, la auditoría y las preferencias de accesibilidad (4.1, 4.2, CU-00 y CU-08), pero no había dónde guardarlos. | **Migración 003:** `CodigoVerificacion`. **Migración 004:** `EquipoConfianza`, `SesionVerificada` y `Auditoria`, y en `Usuario` las columnas `alto_contraste`, `tamano_texto` y `paleta_daltonismo`. "Recordar este equipo" usa una cookie HttpOnly, como dice el PPI. |
| D17 | La migración 005 dejó `SesionVerificada` con una fila por usuario: verificar el código en un equipo habilitaba todas las sesiones de ese usuario durante 12 horas, incluso las abiertas después en otro equipo. El PPI marca como verificada la sesión. | **Migración 006:** `SesionVerificada` por `session_id` del JWT (§2.21). El backend rechaza con `401` los tokens sin ese claim, borra la sesión al cerrar sesión y todas las del usuario al revocar su acceso. |
| D18 | El club tiene 9 categorías (Párvulos, Cachorros, Semillero y de Sub-11 a Sub-16) que se asignan por año de nacimiento, pero el modelo tenía cinco por edad cumplida (Sub-8 a Sub-16). Además, el tipo de documento del deportista no incluía el registro civil, que es el que tienen los niños pequeños. | **Migración 007.** `Categoria` con las 9 categorías y rangos por año: edad por año = año actual − año de nacimiento. Párvulos 4 a 6, Cachorros 7 y 8, Semillero 9 y 10, y de Sub-11 a Sub-16 una por año; ya no existen Sub-9 ni Sub-10. Cada deportista se asigna automáticamente al registrarse (CU-01) y no se admite a nadie fuera de 4 a 16 años. `Sub-8` pasa a `Párvulos` y `Sub-10` a `Semillero`, conservando su id. `Deportista.tipo_documento` queda en `TI`, `RC`, `CE` o `PASAPORTE` (sale `CC`; entrenador y acudiente la conservan). Reemplaza a D3. |
| D19 | `lugar` era texto libre y la restricción de cruce por lugar impedía que varias categorías entrenaran a la misma hora en la misma sede, algo normal en el club. Tampoco había dónde guardar el horario fijo semanal. | **Migración 007.** `Entrenamiento.lugar` pasa a ser `Horizontes` o `UVA El Paraíso`. Se elimina `entrenamiento_cruce_lugar_ex` y se conservan las de categoría y entrenador. Tabla `FranjaHorario` con el horario fijo de lunes a sábado y una restricción de cruce por entrenador. Reemplaza a D2 en lo del lugar. |
| D20 | Las fotos y los documentos de los deportistas no tenían dónde guardarse. | **Migración 007.** Buckets privados de Supabase Storage: `fotos` (JPG o PNG) y `documentos` (PDF, JPG o PNG), de máximo 5 MB. Sin políticas: solo el backend entra con la llave `service_role` y entrega URL firmadas de corta duración, como el bucket de comprobantes. `Deportista.foto` guarda la ruta. |
| D21 | Los tipos de `Documento` de la 004 (`FICHA_MEDICA`, `PERMISO`, `CONTRATO`, `OTRO`) no son los que pide el club para inscribir a un deportista. | **Migración 007.** `documento_tipo_ck` con los 7 tipos del club. Obligatorios: `DOCUMENTO_IDENTIDAD` (cédula, tarjeta de identidad o registro civil, según la edad), `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION` y `AUTORIZACION_HABEAS_DATA`. Si aplican: `PAZ_Y_SALVO` (si viene de otro club), `CARTA_RENUNCIA` (si jugaba en otra liga o región) y `TRASPASO_NACIONAL`. La ficha médica sale de la lista. La foto tipo documento no es un tipo: es `Deportista.foto` (bucket `fotos`). La migración se detiene si ya hay documentos con un tipo viejo y dice cuántos hay de cada uno, para migrarlos o borrarlos antes. |

Además, los correos de `Usuario`, `Entrenador`, `Deportista` y `Acudiente` pasan a ser únicos sin distinguir mayúsculas.

### 6.1 Cómo aplicarla

1. Hacer una copia de seguridad del proyecto en Supabase (**Database → Backups**) o exportar los datos.
2. Comprobar que ya se aplicaron la 002 y la 003 (por ejemplo, que existan `acudiente_deportista` y `codigo_verificacion`). Si no, aplicarlas primero, en orden.
3. Abrir **SQL Editor**, pegar todo `004_ajustes_modelo.sql` y ejecutarlo una sola vez.
4. Si termina con error, no se aplicó **ningún** cambio (va en una transacción). El mensaje dice qué dato hay que corregir antes: montos con centavos, valores en `deportista.documentos`, correos repetidos sin distinguir mayúsculas o entrenamientos que se cruzan.
5. Comprobar: `select nombre from categoria order by edad_minima;` debe devolver las cinco categorías.

### 6.2 Migración 007 (categorías del club, sedes y horario)

1. Hacer una copia de seguridad del proyecto en Supabase (**Database → Backups**) o exportar los datos.
2. Comprobar que ya se aplicaron la 001 a la 006.
3. Abrir **SQL Editor**, pegar todo `007_categorias_club_y_horario.sql` y ejecutarlo una sola vez.
4. Si termina con error, no se aplicó **ningún** cambio (va en una transacción). El mensaje dice qué dato hay que corregir antes: deportistas con tipo de documento `CC`, deportistas fuera de 4 a 16 años por año de nacimiento, entrenamientos con un lugar que no sea una de las dos sedes, o documentos con un tipo que el club ya no pide (`FICHA_MEDICA`, `PERMISO`, `CONTRATO` u `OTRO`; el mensaje da el conteo por tipo).
5. Comprobar: `select nombre, edad_minima, edad_maxima from categoria order by edad_minima;` debe devolver 9 filas (Párvulos 4-6, Cachorros 7-8, Semillero 9-10 y de Sub-11 a Sub-16), y `select count(*) from franja_horario;` debe devolver 0.
6. Opcional, solo en una base nueva o de pruebas: cargar el horario del club con `supabase/seeds/franjas_club.sql`. Deja 30 franjas (`select count(*) from franja_horario;` debe devolver 30), todas sin entrenador, y 8 con grupo (Cachorros y Semillero, martes y jueves). Se detiene si `franja_horario` ya tiene datos.
