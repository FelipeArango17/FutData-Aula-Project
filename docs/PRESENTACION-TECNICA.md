# FutData — Presentación técnica

Sistema de gestión deportiva para la Asociación Deportiva Prado Fútbol Club (PPI, sexto semestre).

> **Esta es la fuente de verdad del proyecto.** El documento PPI ya no se modifica: lo que se decida desde el 1 de noviembre de 2026 se registra aquí. Se actualiza con cada cambio (ver el registro de cambios al final).
> Última actualización: 2 de noviembre de 2026 (versión 2: alcance de la entrega final; bloque 5).
> **Entrega final al jurado: sábado 7 de noviembre de 2026.** Para esa fecha toda la aplicación debe funcionar con datos reales, con los cuatro roles y sin maquetas.

## 1. Qué es FutData

Una aplicación para administrar un club de fútbol formativo: inscripción de deportistas y acudientes, documentos, categorías, entrenadores, horario de entrenamientos, mensualidades y comprobantes de pago.

Roles (son **cuatro**): **administrador**, **entrenador**, **tesorero** y **acudiente**. El ingreso usa verificación en dos pasos por correo (2FA, flujo en n8n).

Una aplicación **sin maquetas**: cada pantalla del menú de cada rol se conecta a la API real. Lo que no se alcance a conectar se oculta del menú; no se muestran datos de ejemplo al jurado.

## 2. Equipo y herramientas

| Quién | Responsabilidad |
|---|---|
| Omar Támara | Backend (Spring Boot, base de datos, n8n, Wompi) |
| Felipe Arango | Frontend y, por definir, módulos de backend (ver sección 9) |
| Juan Sebastián | Apoyo: colección Postman (HU-057) y revisión de PR. Ya no está en la ruta crítica |

Trabajo distribuido en Azure DevOps. Código en GitHub (`FelipeArango17/FutData-Aula-Project`), ramas desde `develop`, un PR por bloque.

## 3. Arquitectura y stack

- **Backend:** Spring Boot 3.5, Java 21, Maven wrapper (`mvnw`), capas controlador, servicio y DAO con JDBC.
- **Base de datos:** PostgreSQL en Supabase. Migraciones numeradas en `supabase/migrations/` (hoy 001 a 009).
- **Autenticación:** Supabase Auth, con 2FA por correo. El backend exige el rol en cada controlador con `@PreAuthorize`. El frontend solo muestra las rutas de un panel si hay sesión verificada **y** el rol de la sesión coincide con el del panel (no se usa el rol guardado en el navegador).
- **Pagos en línea:** Wompi (pasarela elegida por el equipo; el profesor la pidió expresamente). Se trabaja con el entorno de pruebas (sandbox). El webhook `POST /api/pagos/webhook/wompi` es público y debe **verificar la firma** del evento.
- **Archivos:** Supabase Storage con buckets privados (`fotos`, `documentos`, y el de comprobantes). Solo el backend entra con la llave `service_role` y entrega URL firmadas.
- **Correos y automatización:** n8n (código 2FA, enlace de contraseña, aviso de documento rechazado).
- **Frontend:** React.
- **Pruebas:** JUnit con Testcontainers (PostgreSQL 17 en Docker). Estado actual, contado con `./mvnw clean test`: **1030 pruebas, 0 fallos, 9 omitidas** (las 9 manuales, que necesitan n8n o Supabase reales: n8n local, Storage, fotos y el enlace de crear contraseña contra el proyecto de prueba). El "874" que figuraba antes era un error de conteo: al sumar los reportes `.txt` de surefire no se contaban las clases `@Nested`. El total real antes del bloque 5 era **928**.

## 4. Modelo de datos (resumen)

Detalle completo en `docs/info-sprints/sprint-1/diccionario-datos.md`.

- **categoria:** 9 categorías por **año de nacimiento** (año actual − año de nacimiento). **La categoría del deportista se calcula al leer, a partir de su fecha de nacimiento**: así cambia sola cada 1 de enero, sin procesos programados ni actualizaciones masivas. La API devuelve el nombre de la categoría ya calculado.

| Categoría | Edad por año | Nacidos en (2026) |
|---|---|---|
| Párvulos | 4–6 | 2020 a 2022 |
| Cachorros | 7–8 | 2018 y 2019 |
| Semillero | 9–10 | 2016 y 2017 |
| Sub-11 | 11 | 2015 |
| Sub-12 | 12 | 2014 |
| Sub-13 | 13 | 2013 |
| Sub-14 | 14 | 2012 |
| Sub-15 | 15 | 2011 |
| Sub-16 | 16 | 2010 |

  No se inscribe a nadie fuera de 4–16 años. No existen Sub-8, Sub-9, Sub-10, Formación, Femenino ni Arqueros.
- **deportista:** tipo de documento TI, RC, CE o PASAPORTE (sin CC). El número de documento es único. Tiene `posicion` (opcional), `foto` y `grupo` (`mañana` o `tarde`, opcional, **migración 008**; solo se permite en Cachorros y Semillero, y el backend lo valida; si el deportista cambia a otra categoría, el grupo se descarta).
- **acudiente** y **acudiente_deportista:** relación N:M (un acudiente puede tener varios jugadores). `acudiente_deportista.parentesco` ya existe (migración 008, opcional, sin tilde), igual que `deportista.grupo` y `deportista.activo`.
- **entrenador:** puede tener varias categorías; cada categoría con responsable tiene uno solo.
- **entrenamiento:** `lugar` es una de las dos sedes. Hay bloqueo de cruce por categoría y por entrenador (no por lugar).
- **franja_horario:** horario fijo del club, lunes a sábado, con sede, entrenador opcional y grupo opcional.
- **documento:** 7 tipos, estados PENDIENTE, APROBADO y RECHAZADO, con `cargado_por`, `revisado_por` y comentario al rechazar.

## 5. Reglas de negocio vigentes

1. **Categoría automática** desde la fecha de nacimiento (regla de año). Se rechaza a quien quede fuera de 4–16.
2. **Documentos del deportista:** TI, RC, CE y PASAPORTE.
3. **Sedes:** Horizontes y UVA El Paraíso. La sede va **por franja**, no por categoría.
4. **Horario:** lunes a sábado. Varias categorías pueden coincidir en hora y sede. Un mismo entrenador no puede tener dos franjas ni dos entrenamientos que se solapen. No hay entrenamientos en fechas pasadas.
5. **Grupo mañana/tarde** (Cachorros y Semillero, martes y jueves): la franja lo guarda y **el grupo de cada deportista también se guarda en la base** (columna `deportista.grupo`, migración 008). Lo elige el administrador al inscribir o editar; el backend solo lo acepta en Cachorros y Semillero. No cambia el horario ni las categorías.
6. **Por turnos:** Párvulos, Cachorros y Semillero no tienen entrenador fijo (el club rota quién dirige).
7. **Documentos de la inscripción (7 tipos):**
   - Obligatorios: `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION`, `AUTORIZACION_HABEAS_DATA`.
   - Si aplica: `PAZ_Y_SALVO`, `CARTA_RENUNCIA`, `TRASPASO_NACIONAL`.
   - La "foto tipo documento" es la **foto del deportista**, no un tipo de documento. La ficha médica ya no existe.
   - Archivos PDF, JPG o PNG, máximo 5 MB, validados por su contenido real (no por la extensión).
8. **Validación de documentos:** el administrador revisa. Mientras no los haya validado, **la inscripción queda pendiente**. Al rechazar un documento el comentario es obligatorio y se envía por correo al acudiente (n8n).
9. **Alta de entrenador y de acudiente:** sin contraseña temporal ni cambio forzado. Se envía un enlace para que cada persona cree su propia contraseña. Hecho para el entrenador (bloque 5); el acudiente llega con la inscripción (2B).
   - **Enlace:** vence en **1 hora** (Supabase Auth, `mailer_otp_exp = 3600`) y sirve una sola vez; un enlace nuevo invalida el anterior. Si vence, el administrador lo reenvía. Nunca se registra en logs ni sale en una respuesta de la API.
   - **Contraseña:** entre 8 y 72 caracteres, como mucho 72 bytes en UTF-8 (una ñ o una tilde ocupan 2), con al menos una letra y un número. Mismo mensaje en el frontend y el backend: "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número."
   - **Límites en memoria:** un envío del enlace por minuto por entrenador (el alta cuenta como uno) y, al crear la contraseña, 10 intentos por minuto por IP, 5 por enlace y 60 en total. Se pierden al reiniciar y no se comparten entre instancias (hoy hay una sola en Render).
   - **Desactivar un entrenador con cuenta le impide entrar:** se bloquea su cuenta en Supabase Auth, `usuario.activo` pasa a `false` y se borran sus sesiones verificadas; al reactivarlo se restaura. Sus franjas se conservan (con `entrenadorActivo: false`).
   - El correo de un entrenador con cuenta no se puede cambiar (`409`): es con el que inicia sesión.
10. **Inscripción pendiente:** el estado de la inscripción **se calcula** (no es una columna): está *pendiente* mientras alguno de los 4 documentos obligatorios no esté APROBADO. La API lo devuelve como `estadoInscripcion`. Mientras esté pendiente, el deportista queda **activo**, para que el administrador lo vea en la lista y valide sus documentos.
11. **Parentesco** (acudiente–deportista): `Madre`, `Padre`, `Abuelo`, `Abuela`, `Tia`, `Tio`, `Otro` (migración 008). El frontend lo muestra con tilde y lo envía sin tilde.
12. **Protección de pantallas:** sin sesión verificada, cualquier ruta de la aplicación lleva a `/login`; con la sesión de un rol, las rutas de otro rol llevan al inicio del propio.
13. **Cobros por ciclo de ingreso** (como un gimnasio; reemplaza la generación manual de mensualidades por calendario):
   - **Ancla:** `deportista.fecha_registro`. La fecha de cobro del ciclo *n* es la fecha de registro (n = 0) o, para n ≥ 1, el mismo día del mes *n* meses después, acotado al último día de ese mes. Siempre se calcula desde la fecha de registro, nunca desde el cobro anterior, así que no hay corrimiento: registro el 31 de octubre → 31 oct, 30 nov, 31 dic, 31 ene, 28 feb (29 en bisiesto).
   - **Mensualidad:** el periodo es el primer día del mes de la fecha de cobro; vence en esa fecha (si el generador corre tarde y ya pasó, vence hoy). El **ciclo vigente** es la fecha de cobro más reciente menor o igual a *hoy + 5 días*. Se crea si el deportista no tiene **ninguna** mensualidad de ese periodo, en cualquier estado (si el administrador rechazó una a propósito, no se regenera). Nunca se crean ciclos anteriores: no hay cobros retroactivos.
   - **Ingreso:** si `fecha_registro` ≥ *hoy − 7 días*, se crean además una vez la **inscripción** y el **uniforme** (si el deportista no tiene ya un cobro de ese tipo), con vencimiento hoy.
   - **Alcance:** deportistas activos (`activo = true`) con `estado_inscripcion` PENDIENTE o ACTIVA; RETIRADA se excluye.
   - **Montos:** mensualidad = `FUTDATA_MENSUALIDAD_MONTO`; inscripción 150000 y uniforme 90000 por defecto. Todo configurable por variable de entorno (ver `backend/README.md`).
   - **Aviso:** por cada mensualidad creada **con anticipación** (fecha de cobro posterior a hoy), n8n envía un correo `RECORDATORIO_PAGO` a cada acudiente vinculado que tenga correo. No se avisa de inscripción ni uniforme. Si n8n falla, el cobro queda creado y el fallo se cuenta.
   - **Endpoint:** `POST /api/pagos/generar-ciclos`, idempotente; lo llama el administrador (JWT) o la tarea diaria de n8n con la cabecera `X-Cron-Token` (solo si `N8N_CRON_TOKEN` está definida). `POST /api/pagos/generar-mensualidades` queda **obsoleto**, sin cambios de comportamiento.
   - **Contrato `RECORDATORIO_PAGO`** (campos de `datos`, una petición por acudiente): `correo`, `nombreAcudiente`, `nombreDeportista`, `concepto`, `monto` (pesos enteros), `fechaVencimiento` (`AAAA-MM-DD`), `diasRestantes` (entero) y `enlacePagos` (`{FRONTEND_URL}/acudiente/pagos`). Respuesta asíncrona (`200` o `202`). El flujo de n8n debe agregarlo a su lista de eventos soportados (hasta entonces responde `400 Evento no soportado` y el aviso cuenta como fallido).

## 6. Horario del club (30 franjas)

Fuente: Excel del club (`Horario_de_Entrenamientos_Prado_FC.xlsx`). Plantilla cargable en `supabase/seeds/franjas_club.sql`. Entrenador "por turnos" = sin entrenador fijo (en la base, `id_entrenador` NULL).

| Categoría | Día | Inicio | Fin | Sede | Entrenador | Grupo |
|---|---|---|---|---|---|---|
| Párvulos | Martes | 08:30 | 10:00 | UVA El Paraíso | Por turnos | |
| Párvulos | Sábado | 09:00 | 10:00 | Horizontes | Por turnos | |
| Cachorros | Martes | 07:30 | 09:00 | UVA El Paraíso | Por turnos | mañana |
| Cachorros | Jueves | 07:30 | 09:00 | UVA El Paraíso | Por turnos | mañana |
| Cachorros | Martes | 16:30 | 18:00 | UVA El Paraíso | Por turnos | tarde |
| Cachorros | Jueves | 16:30 | 18:00 | UVA El Paraíso | Por turnos | tarde |
| Cachorros | Sábado | 08:30 | 10:00 | Horizontes | Por turnos | |
| Semillero | Martes | 07:30 | 09:00 | UVA El Paraíso | Por turnos | mañana |
| Semillero | Jueves | 07:30 | 09:00 | UVA El Paraíso | Por turnos | mañana |
| Semillero | Martes | 16:30 | 18:00 | UVA El Paraíso | Por turnos | tarde |
| Semillero | Jueves | 16:30 | 18:00 | UVA El Paraíso | Por turnos | tarde |
| Semillero | Sábado | 08:30 | 10:00 | Horizontes | Por turnos | |
| Sub-11 | Martes | 20:00 | 21:00 | Horizontes | David Mesa | |
| Sub-11 | Miércoles | 19:00 | 20:00 | Horizontes | David Mesa | |
| Sub-11 | Jueves | 19:00 | 20:00 | Horizontes | David Mesa | |
| Sub-12 | Lunes | 19:00 | 20:00 | Horizontes | Nicolás | |
| Sub-12 | Martes | 18:00 | 19:00 | Horizontes | Nicolás | |
| Sub-12 | Jueves | 19:00 | 20:00 | Horizontes | Nicolás | |
| Sub-13 | Lunes | 19:00 | 20:00 | Horizontes | Jhon Cardona | |
| Sub-13 | Miércoles | 19:00 | 20:00 | Horizontes | Jhon Cardona | |
| Sub-13 | Viernes | 19:00 | 20:00 | Horizontes | Jhon Cardona | |
| Sub-14 | Lunes | 18:00 | 19:00 | Horizontes | Jhon Cardona | |
| Sub-14 | Miércoles | 18:00 | 19:00 | Horizontes | Jhon Cardona | |
| Sub-14 | Viernes | 18:00 | 19:00 | Horizontes | Jhon Cardona | |
| Sub-15 | Lunes | 18:00 | 19:00 | Horizontes | Giovanny | |
| Sub-15 | Martes | 20:00 | 21:00 | Horizontes | Giovanny | |
| Sub-15 | Jueves | 18:00 | 19:00 | Horizontes | Giovanny | |
| Sub-16 | Martes | 16:30 | 18:00 | Horizontes | Jhon Ramírez | |
| Sub-16 | Miércoles | 18:00 | 19:00 | Horizontes | Jhon Ramírez | |
| Sub-16 | Jueves | 16:30 | 18:00 | Horizontes | Jhon Ramírez | |

Los entrenadores nombrados todavía no existen en la base: al crearlos se les asigna su franja con `PUT`.

## 7. Flujos principales

**Inscripción (administrador, 4 pasos: Jugador, Acudiente, Documentos, Confirmar).** Un solo `POST` transaccional (la categoría no se guarda: se calcula al leer) que crea el deportista, crea o reutiliza al acudiente por correo (con su parentesco), sube la foto y los documentos a Storage, y envía al acudiente el enlace para crear su contraseña. La inscripción queda **pendiente** hasta que el administrador valide los documentos. Si algo falla a mitad, devuelve un error claro (`INSCRIPCION_PARCIAL`).

**Alta de entrenador.** El administrador lo registra (`POST /api/entrenadores`). El backend crea la cuenta de Supabase Auth sin contraseña y, en una sola transacción, el entrenador, su `usuario` con rol ENTRENADOR y el vínculo `entrenador.id_usuario`; si la base falla, borra la cuenta de Auth. **Después** del commit genera el enlace y n8n envía el correo de bienvenida (evento síncrono `ENLACE_CONTRASENA`). Si el envío falla, el alta queda hecha igual y responde `correoEnviado: false`, y el frontend sugiere "Reenviar enlace". Si el correo ya es de otra cuenta, responde `409` y no guarda nada.

**Reenvío del enlace.** El administrador pulsa "Reenviar enlace" (`POST /api/entrenadores/{id}/reenviar-enlace`). Si el entrenador no tiene cuenta, se crea; si la tiene, se genera un enlace nuevo, que invalida el anterior. Uno por minuto por entrenador (`429`). Responde `204` sin el enlace; `409` si el entrenador está inactivo o su correo es de otra cuenta, y `503` si Supabase o el correo fallan.

**Crear la contraseña.** El entrenador abre el enlace (`/crear-contrasena?token=...`), escribe su contraseña y el frontend llama a `POST /api/auth/crear-contrasena`, que es público. El backend revisa la política **antes** de verificar el enlace (verificar lo consume), lo verifica con Supabase, fija la contraseña, cierra la sesión que abrió la verificación y responde `200` sin iniciar sesión: la persona entra después por el login normal, con su 2FA. Enlace inválido, vencido o usado: `400` con un mensaje único, sin decir cuál de los tres.

**Revisión de documentos.** El acudiente o el administrador suben. Solo el administrador aprueba o rechaza. Al rechazar va un comentario y n8n avisa al acudiente.

**Código 2FA (asíncrono).** n8n valida la petición, responde `202` y después envía el correo, así el ingreso no espera al SMTP (~3 s). Si el envío falla después del `202`, el código queda guardado pero el correo no llega: el usuario usa "Reenviar código" (al minuto) y la falla queda solo en el registro enmascarado de n8n.

## 8. API

Los endpoints existentes están en `backend/README.md` y en la colección Postman (HU-057, pendiente).

**Ya implementados:**

- Deportistas (bloque 2A, en la rama `feat/deportistas-api`; detalle en `backend/README.md`):
  - `GET /api/deportistas`: paginado (`page`, `size` hasta 100, `sort`, filtros `activo` y `categoria`); responde `content`, `page`, `size`, `totalElements` y `totalPages`. ADMINISTRADOR y ENTRENADOR (este último sin documento, teléfono, dirección ni `estadoInscripcion`).
  - `GET /api/deportistas/{id}`, `POST /api/deportistas` y `PUT /api/deportistas/{id}`: solo ADMINISTRADOR; documento repetido da 409.
  - `POST` y `DELETE /api/deportistas/{id}/foto`: solo ADMINISTRADOR; JPG o PNG por su contenido, máximo 5 MB; la respuesta trae una URL firmada de 60 minutos.
- Horario del club (bloque 4A, en la rama `feat/horario-club`; detalle en `backend/README.md`):
  - `GET /api/horario-club` y `GET /api/horario-club/{id}`: ADMINISTRADOR, TESORERO, ENTRENADOR y ACUDIENTE. Filtros opcionales `categoria` (nombre exacto), `entrenador` (UUID) y `sede`; ordenado por día, hora de inicio y categoría.
  - `POST /api/horario-club`, `PUT /api/horario-club/{id}` (reemplaza todo) y `DELETE /api/horario-club/{id}` (`204`): solo ADMINISTRADOR.
  - Horas en `HH:mm`. `idEntrenador` null = "por turnos"; el entrenador inactivo solo se rechaza al asignarlo o cambiarlo. Grupo solo en Cachorros y Semillero.
  - El mismo entrenador con dos franjas que se solapan el mismo día: `409` "Ese entrenador ya tiene otra franja que se cruza con este horario". Las contiguas no se cruzan y categorías distintas pueden coincidir en hora y sede.
- Alta de entrenadores con enlace (bloque 5, en la rama `feat/alta-entrenador-enlace`; detalle en `backend/README.md`):
  - `POST /api/entrenadores` (solo ADMINISTRADOR) ahora también crea la cuenta de acceso y responde `201` con `correoEnviado` (`true` o `false`); `409` si el correo ya es de otra cuenta y `503` si Supabase Auth no responde (no se guarda nada). Las demás respuestas de entrenadores no traen `correoEnviado`.
  - `POST /api/entrenadores/{id}/reenviar-enlace` (solo ADMINISTRADOR): `204` sin cuerpo; `404`; `409` (inactivo, sin correo o correo de otra cuenta); `429` con `Retry-After` (un envío por minuto); `503` (Supabase o el correo fallan).
  - `POST /api/auth/crear-contrasena` (**público**, sin token ni sesión) con `{ token, contrasena }`: `200` con un mensaje y sin tokens; `400` de política con `campos: ["contrasena"]`; `400` de enlace inválido, vencido o usado (mensaje único, sin `campos`); `429` con `Retry-After`; `503` si Supabase Auth falla.
  - `PUT /api/entrenadores/{id}`: `409` si intenta cambiar el correo de un entrenador con cuenta.

**Previstos:**

- Documentos: subir, aprobar y rechazar.
- `POST` de inscripción única.
- Mis jugadores del acudiente (bloque 2C), solo rol ACUDIENTE; el acudiente sale del token, nunca de un id que envíe el frontend:
  - `GET /api/acudientes/me/deportistas`: sus jugadores vinculados, con foto firmada y categoría derivada.
  - `GET /api/acudientes/me/deportistas/{id}`: la ficha de uno de sus hijos; si no es suyo, `404` (no `403`).
- Horario de la semana (bloque 4B). **Borrador del contrato: se ajusta al implementar.**
  - `GET /api/usuarios/yo` (ya existe) agrega `"entrenador": { "idEntrenador": uuid, "categorias": [ { "idCategoria": uuid, "nombre": "Semillero" } ] }` para el rol ENTRENADOR (`categorias` = las categorías a su cargo, `categoria.id_entrenador`). Es `null` para los demás roles y también para un ENTRENADOR sin fila de entrenador vinculada (`entrenador.id_usuario`). (4B-1)
  - `GET /api/horario-semana?fecha=AAAA-MM-DD` (4 roles; sin `fecha` = hoy en Bogotá). El acudiente solo puede pedir la semana en curso (otra: `403` "Solo puedes consultar la semana en curso"); los demás roles, cualquiera. Respuesta: `{ "desde": "2026-11-02", "hasta": "2026-11-07", "sesiones": [...] }`, ordenadas por `fecha` y `horaInicio`. (4B-1, solo franjas)
  - Cada sesión: `tipo` (`FRANJA` o `EXTRA`), `estado` (`ACTIVO` o `CANCELADO`), `idFranja` (uuid; `null` en un extra), `idExcepcion` (uuid de la cancelación o el extra; `null` en una franja activa), `fecha`, `dia` (1..6), `horaInicio` y `horaFin` (`HH:mm`), `idCategoria`, `categoria` (nombre), `sede`, `idEntrenador`, `entrenador` (nombre; `null` = por turnos), `entrenadorActivo`, `grupo` (`null` salvo Cachorros y Semillero), `motivo` (texto o `null`) y `puedeGestionar` (si quien consulta puede cancelar o editar esa sesión). Mismos nombres que `/api/horario-club`.
  - Escritura (4B-2). ADMINISTRADOR en cualquier categoría; ENTRENADOR solo en sus categorías o en franjas que le tocan (si no, `403`); acudiente y tesorero solo leen:
    - `POST /api/horario-semana/cancelaciones` `{ fecha, idFranja, motivo? (máx. 200) }`: `201`.
    - `DELETE /api/horario-semana/cancelaciones/{idExcepcion}`: deshace la cancelación.
    - `POST /api/horario-semana/extras` `{ idCategoria, fecha, horaInicio, horaFin, sede, idEntrenador (null = por turnos), grupo?, motivo? }`: `201`.
    - `PUT /api/horario-semana/extras/{idExcepcion}` (mismo cuerpo).
    - `DELETE /api/horario-semana/extras/{idExcepcion}`.
  - Todas las de escritura responden `{ "sesion": {...la sesión resultante, o null si se borró...}, "avisoEnviado": true | false | null }`. `true`: n8n recibió el aviso a los acudientes de la categoría (no garantiza que el correo llegue; `CAMBIO_ENTRENAMIENTO` es asíncrono). `false`: no se pudo avisar, pero la operación **sí** quedó hecha. `null`: no había a quién avisar.
  - Validaciones: no cancelar ni crear en fechas pasadas (`400`); al cancelar, el día de la semana de la fecha debe coincidir con el de la franja (`400`); no cancelar dos veces la misma franja y fecha (`409`); extras de lunes a sábado, sede válida, `horaFin` > `horaInicio`, grupo solo para Cachorros y Semillero; cruce del mismo entrenador contra franjas no canceladas y otros extras (`409` "Ese entrenador ya tiene otra franja que se cruza con este horario"). Un extra puede quedar fuera de las franjas fijas.
- Pagos: crear el cobro (mensualidad), generar los cobros por ciclo de ingreso (`POST /api/pagos/generar-ciclos`, regla 13), iniciar el pago con Wompi, `POST /api/pagos/webhook/wompi` (firma verificada), consultar el estado, comprobante en PDF, estado de cuenta del acudiente y vista del tesorero.
- Grupos, asistencia, torneos, reportes y avisos: por definir en la sección 9.

## 9. Estado y plan

### 9.1 Bloques de base de datos y API ya definidos

| Bloque | Qué incluye | Responsable | Estado |
|---|---|---|---|
| 1 | Migración 007, buckets, categorías, sedes, `franja_horario` | Omar | **Hecho** (en `develop` y en la base real) |
| 2A | API de deportistas: categoría calculada al leer, sin CC y con RC, paginación, foto, posición. Incluye la migración 008 (parentesco, grupo y activo) | Omar | Hecho en la rama `feat/deportistas-api` (pendiente de merge) |
| 4A | API del horario fijo del club (`franja_horario`, `/api/horario-club`), sin migración nueva | Omar | Hecho en la rama `feat/horario-club` (pendiente de merge) |
| 5 | Alta de entrenadores con enlace para crear la contraseña, reenvío y desactivación con bloqueo de la cuenta, sin migración nueva | Omar | Hecho en la rama `feat/alta-entrenador-enlace` (pendiente de merge) |
| 2C | **Mis jugadores (acudiente):** `GET /api/acudientes/me/deportistas` y `GET /api/acudientes/me/deportistas/{id}` (ver abajo). No depende de nada nuevo; va antes del bloque 3 | Omar | Pendiente |
| 4B | Entrenamientos (HU-026), redefinido: las franjas son fijas todo el año y solo se registran **excepciones por fecha** (`CANCELADO` y `EXTRA`) en una **tabla nueva de excepciones** (migración 010), sin tocar la tabla `entrenamiento`. `GET /api/horario-semana?fecha=` arma la semana con las franjas y sus excepciones. El acudiente solo ve la semana en curso. Cancelar una sesión y crear una extra: el administrador y el entrenador de la categoría. Se divide en 4B-1 y 4B-2 | Omar | Pendiente |
| 4B-1 | Sin migración: `GET /api/usuarios/yo` con el bloque `entrenador` para ENTRENADOR y `GET /api/horario-semana` **solo con franjas** (`estado` siempre `ACTIVO`, sin excepciones ni extras) | Omar | Pendiente |
| 4B-2 | Migración 010 (tabla nueva de excepciones; `entrenamiento` no se toca): cancelaciones y extras con sus reglas, y los avisos por `CAMBIO_ENTRENAMIENTO`. Primera versión: cancelar, deshacer, crear y eliminar extra; editar un extra después, si da el tiempo | Omar | Pendiente |
| 3 | Documentos: subir, aprobar, rechazar, correo | Omar | Pendiente |
| 2B | Inscripción única (la migración 008 llegó con el 2A) | Omar | Pendiente |

Orden acordado: 2A y 4A (hechos), después **5 → 2C → 4B-1 → 3 → 2B → 4B-2** (4A es el horario del club; 4B, los entrenamientos, HU-026).

**Bloque 2C — Mis jugadores (acudiente).**

- **Motivo:** `GET /api/deportistas` es solo para ADMINISTRADOR y ENTRENADOR; no existe ningún endpoint para que un acudiente vea a sus propios hijos, así que las pantallas acudiente/Jugadores, Ficha e Inicio no se pueden conectar.
- **Alcance:** `GET /api/acudientes/me/deportistas` (lista de sus jugadores vinculados, con foto firmada y categoría derivada) y `GET /api/acudientes/me/deportistas/{id}` (ficha de uno de sus hijos; si no es suyo, `404`, no `403`). Solo rol ACUDIENTE; el acudiente se identifica por el token, nunca por un id enviado por el frontend. Reutiliza el DAO, la categoría derivada y las URL firmadas del 2A.
- **Pregunta abierta:** si el acudiente ve `estadoInscripcion` y qué documentos faltan de sus hijos (propuesta: sí, solo de los suyos; se cierra en el bloque 3).
- **Responsable:** Omar. No depende de nada nuevo; va antes del bloque 3.

### 9.2 Módulos nuevos para la entrega final

Estos módulos están en el menú de los cuatro roles y hoy son maquetas. Ninguno estaba en el plan anterior.

| Bloque | Módulo | Quién lo usa | Responsable |
|---|---|---|---|
| 6 | **Pagos con Wompi** (cobro, pago en línea, webhook, comprobante PDF, estado de cuenta) | acudiente, tesorero, administrador | Omar (backend) y Felipe (pantallas). **Backend en curso** (rama `feature/back-pagos-wompi`: migración 009, endpoints, webhook y conciliación). **Prioridad alta:** el profesor insistió en este punto; Omar decide si lo adelanta antes del bloque 3 |
| 7 | Grupos (de entrenamiento) y asistencia | administrador, entrenador | Por definir |
| 8 | Torneos | administrador, entrenador | Por definir |
| 9 | Reportes y avisos / notificaciones | tesorero, administrador, acudiente | Por definir |
| 10 | Dashboard, pantallas de inicio por rol, respaldo, usuarios y roles | todos | Por definir |

Cada módulo "por definir" se reparte entre Omar y Felipe; la tabla se completa en cuanto se acuerde. Migraciones nuevas desde la 009.

### 9.3 Frontend (Felipe)

| Bloque | Qué hace el frontend | Estado |
|---|---|---|
| F-seg | Protección de rutas: sin sesión verificada no se entra; el rol sale de la sesión | En curso |
| F0 | `apiClient` con subida de archivos y paginación; interruptor de API real por servicio (como `entrenadoresService`); parentescos alineados; quitar la fecha fija del 22 de octubre | Pendiente |
| F-2A, F-4, F-5, F-3, F-2B, F-6… | Conectar cada pantalla a su bloque cuando Omar entregue los endpoints | Pendiente |

### 9.4 Cuándo se considera terminado un módulo

1. Los datos se guardan en la base real y sobreviven a recargar la página.
2. Solo los roles correspondientes pueden verlo y usarlo (comprobado con una cuenta de cada rol).
3. El flujo completo se probó de principio a fin, con sus errores comunes (validaciones, 401, 403, 409).
4. Hay pruebas automáticas y la documentación de la API está actualizada.

### 9.5 Pendientes de información

- Llaves y cuenta de pruebas (sandbox) de Wompi.
- Confirmar el reparto de los bloques 7 a 10.

## 10. Convenciones

- Una rama por bloque desde `develop`; PR a `develop`, el otro lo revisa.
- Toda migración se prueba primero en un proyecto de prueba de Supabase y se aplica en la real con backup. Una migración ya aplicada no se edita: se crea una nueva.
- Las pruebas corren con `.\mvnw.cmd test` desde `backend\` (Docker abierto) y no tocan la base real.
- **El total de pruebas se cuenta siempre con `./mvnw clean test`** (`.\mvnw.cmd clean test` en Windows) y se toma de la línea final de Maven (`Tests run: N, Failures: 0, Errors: 0, Skipped: M`). Sin `clean` quedan reportes viejos en `target/`, y sumar los `.txt` de surefire no cuenta las clases `@Nested`.
- Cada bloque termina con este archivo actualizado.
- Ninguna pantalla muestra datos de ejemplo en la entrega final: lo que no esté conectado a la API se oculta del menú.

## 11. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-11-01 | Se crea el documento. Bloque 1 (migración 007) en `develop` y aplicada en Supabase. 9 categorías, 7 tipos de documento, 30 franjas. |
| 2026-11-01 | Se congela el PPI; este archivo pasa a ser la fuente de verdad. |
| 2026-11-01 | Decisiones: la inscripción queda pendiente hasta que el administrador valide los documentos; parentesco = Madre, Padre, Abuelo, Tia, Tio, Otro; el backend es de Omar y el frontend de Felipe. |
| 2026-11-01 | Versión 2. Entrega final: sábado 7 de noviembre. Son **cuatro roles** (se agrega tesorero). Categoría calculada al leer. `grupo` pasa a la base de datos (migración 008). Inscripción pendiente calculada, con deportista activo. Parentesco incluye Abuela. Se agrega Wompi y los bloques 6 a 10 (pagos, grupos y asistencia, torneos, reportes y avisos, dashboard y respaldo). Se agrega la protección de pantallas por sesión y rol. |
| 2026-11-01 | Bloque 2A (rama `feat/deportistas-api`, pendiente de merge). Migración 008: `parentesco`, `deportista.grupo` y `deportista.activo` (los RETIRADA quedan inactivos). API de deportistas: categoría calculada al leer con el reloj de Bogotá (`id_categoria` y `estado_inscripcion` quedan heredados), `estadoInscripcion` calculado, lista paginada con filtros, `PUT`, foto con URL firmada. El entrenador no ve el número de documento. **624 pruebas, 0 fallos, 6 omitidas.** |
| 2026-11-01 | Bloque 6 (pagos con Wompi): **backend en curso**. Migración 009 (más medios de pago, tipos TORNEO y UNIFORME, `id_transaccion_wompi`), `/api/pagos`, webhook con firma, reintento y conciliación. El monto de la mensualidad queda configurable y pendiente de que lo defina el club. |
| 2026-11-02 | Bloque 4A (rama `feat/horario-club`, pendiente de merge), sin migración nueva. API `/api/horario-club`: lectura para los cuatro roles y edición solo para el administrador, filtros `categoria`, `entrenador` y `sede`, horas `HH:mm`. `idEntrenador` null = "por turnos"; el entrenador inactivo solo se rechaza al asignarlo o cambiarlo, y la respuesta trae `entrenadorActivo`. El cruce del mismo entrenador lo decide `franja_cruce_entrenador_ex` (`409`); las contiguas no se cruzan. El bloque 4 se divide en 4A (horario) y 4B (entrenamientos). **875 pruebas, 0 fallos, 7 omitidas** (con el bloque 6 de `develop`). |
| 2026-11-02 | Latencia del 2FA (rama `fix/latencia-2fa`): `CODIGO_2FA` pasa a asíncrono en n8n (valida, responde `202` y después envía). `N8nClient` ya aceptaba `200` y `202`, así que el orden de despliegue no importa. Si el SMTP falla después del `202`, el código queda guardado, el correo no llega y el usuario usa "Reenviar código"; la falla solo queda en el registro enmascarado de n8n. **874 pruebas, 0 fallos, 7 omitidas** (`develop` tenía 873: el 875 anterior contaba reportes viejos de `target/`). |
| 2026-11-02 | **Corrección del conteo de pruebas.** El "874" estaba mal contado: se sumaban los `.txt` de surefire, que no incluyen las clases `@Nested` (`EntrenadorServiceImplTest` y `JwtValidatorTest`). Con `./mvnw clean test`, el total real antes del bloque 5 era **928 pruebas, 0 fallos, 7 omitidas**. Desde ahora se cuenta siempre así (sección 10). |
| 2026-11-02 | Bloque 5 (rama `feat/alta-entrenador-enlace`, pendiente de merge), sin migración nueva. Alta de entrenador con cuenta de Supabase Auth sin contraseña, `usuario`, `usuario_rol` e `id_usuario` en una transacción (con compensación en Auth) y, después del commit, enlace para crear la contraseña por n8n (`ENLACE_CONTRASENA`, síncrono, 15 s de espera) con `correoEnviado`. Reenvío (`POST /api/entrenadores/{id}/reenviar-enlace`, uno por minuto) y `POST /api/auth/crear-contrasena` público (política antes de verificar, `400` genérico, `429`, `503`). El enlace vence en 1 hora. Política de contraseña 8–72 caracteres y máximo 72 bytes, con letra y número. Límites en memoria (por IP con `CF-Connecting-IP`, por enlace y global). Desactivar un entrenador con cuenta bloquea su acceso. `FRONTEND_URL` obligatoria en prod. Deudas: administrador y tesorero siguen con la invitación de Supabase; el correo de un entrenador con cuenta no se puede editar. Nuevo orden: 5 → 2C → 4B-1 → 3 → 2B → 4B-2, con 4B redefinido (excepciones por fecha, migración 010). Corrección: bloquear y restaurar una cuenta en Supabase Auth usaba `PATCH`, que esa ruta no acepta (desactivar un entrenador con cuenta daba `503`, y revocar y restablecer de `/api/usuarios` nunca funcionaron); ahora usa `PUT`, traduce cada respuesta y un `404` (cuenta inexistente, probable mezcla de proyectos `SUPABASE_URL`/`DB_URL`) deja desactivar y responde `409` al reactivar. **1030 pruebas, 0 fallos, 9 omitidas.** |
| 2026-11-03 | Cobros por ciclo de ingreso (rama `feature/cobros-por-ciclo`, sin migración): regla 13, `POST /api/pagos/generar-ciclos` (administrador por JWT o `X-Cron-Token` con `N8N_CRON_TOKEN`, opcional), evento `RECORDATORIO_PAGO` para n8n y variables `FUTDATA_COBROS_*`. `generar-mensualidades` queda obsoleto. |
