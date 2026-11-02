# FutData — Presentación técnica

Sistema de gestión deportiva para la Asociación Deportiva Prado Fútbol Club (PPI, sexto semestre).

> **Esta es la fuente de verdad del proyecto.** El documento PPI ya no se modifica: lo que se decida desde el 1 de noviembre de 2026 se registra aquí. Se actualiza con cada cambio (ver el registro de cambios al final).
> Última actualización: 1 de noviembre de 2026 (versión 2: alcance de la entrega final).
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
- **Base de datos:** PostgreSQL en Supabase. Migraciones numeradas en `supabase/migrations/` (hoy 001 a 008).
- **Autenticación:** Supabase Auth, con 2FA por correo. El backend exige el rol en cada controlador con `@PreAuthorize`. El frontend solo muestra las rutas de un panel si hay sesión verificada **y** el rol de la sesión coincide con el del panel (no se usa el rol guardado en el navegador).
- **Pagos en línea:** Wompi (pasarela elegida por el equipo; el profesor la pidió expresamente). Se trabaja con el entorno de pruebas (sandbox). El webhook `POST /api/pagos/webhook/wompi` es público y debe **verificar la firma** del evento.
- **Archivos:** Supabase Storage con buckets privados (`fotos`, `documentos`, y el de comprobantes). Solo el backend entra con la llave `service_role` y entrega URL firmadas.
- **Correos y automatización:** n8n (código 2FA, enlace de contraseña, aviso de documento rechazado).
- **Frontend:** React.
- **Pruebas:** JUnit con Testcontainers (PostgreSQL 17 en Docker). Estado actual: **624 pruebas, 0 fallos, 6 omitidas** (las que necesitan n8n y Supabase reales).

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
9. **Alta de entrenador y de acudiente:** sin contraseña temporal. Se envía un enlace para que cada persona cree su propia contraseña.
10. **Inscripción pendiente:** el estado de la inscripción **se calcula** (no es una columna): está *pendiente* mientras alguno de los 4 documentos obligatorios no esté APROBADO. La API lo devuelve como `estadoInscripcion`. Mientras esté pendiente, el deportista queda **activo**, para que el administrador lo vea en la lista y valide sus documentos.
11. **Parentesco** (acudiente–deportista): `Madre`, `Padre`, `Abuelo`, `Abuela`, `Tia`, `Tio`, `Otro` (migración 008). El frontend lo muestra con tilde y lo envía sin tilde.
12. **Protección de pantallas:** sin sesión verificada, cualquier ruta de la aplicación lleva a `/login`; con la sesión de un rol, las rutas de otro rol llevan al inicio del propio.

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

**Alta de entrenador.** El administrador lo crea; se genera el usuario con rol ENTRENADOR sin contraseña y n8n envía el enlace. Si el enlace vence, el administrador puede reenviarlo.

**Revisión de documentos.** El acudiente o el administrador suben. Solo el administrador aprueba o rechaza. Al rechazar va un comentario y n8n avisa al acudiente.

## 8. API

Los endpoints existentes están en `backend/README.md` y en la colección Postman (HU-057, pendiente). Endpoints previstos:

- `GET/POST/PUT/DELETE /api/horario-club` (filtros `categoria`, `entrenador`, `sede`; 409 si el entrenador se cruza).
- Pagos: crear el cobro (mensualidad), iniciar el pago con Wompi, `POST /api/pagos/webhook/wompi` (firma verificada), consultar el estado, comprobante en PDF, estado de cuenta del acudiente y vista del tesorero.
- Grupos, asistencia, torneos, reportes y avisos: por definir en la sección 9.
- Deportistas (bloque 2A, en la rama `feat/deportistas-api`; detalle en `backend/README.md`):
  - `GET /api/deportistas`: paginado (`page`, `size` hasta 100, `sort`, filtros `activo` y `categoria`); responde `content`, `page`, `size`, `totalElements` y `totalPages`. ADMINISTRADOR y ENTRENADOR (este último sin documento, teléfono, dirección ni `estadoInscripcion`).
  - `GET /api/deportistas/{id}`, `POST /api/deportistas` y `PUT /api/deportistas/{id}`: solo ADMINISTRADOR; documento repetido da 409.
  - `POST` y `DELETE /api/deportistas/{id}/foto`: solo ADMINISTRADOR; JPG o PNG por su contenido, máximo 5 MB; la respuesta trae una URL firmada de 60 minutos.
- Documentos: subir, aprobar y rechazar.
- `POST` de inscripción única.
- Alta de entrenador con enlace y reenvío.

## 9. Estado y plan

### 9.1 Bloques de base de datos y API ya definidos

| Bloque | Qué incluye | Responsable | Estado |
|---|---|---|---|
| 1 | Migración 007, buckets, categorías, sedes, `franja_horario` | Omar | **Hecho** (en `develop` y en la base real) |
| 2A | API de deportistas: categoría calculada al leer, sin CC y con RC, paginación, foto, posición. Incluye la migración 008 (parentesco, grupo y activo) | Omar | Hecho en la rama `feat/deportistas-api` (pendiente de merge) |
| 4 | Horario del club y entrenamientos (HU-026) | Omar | Pendiente |
| 5 | Alta de entrenadores con enlace y reenvío | Omar | Pendiente |
| 3 | Documentos: subir, aprobar, rechazar, correo | Omar | Pendiente |
| 2B | Inscripción única (la migración 008 llegó con el 2A) | Omar | Pendiente |

Orden acordado: 2A, 4, 5, 3, 2B.

### 9.2 Módulos nuevos para la entrega final

Estos módulos están en el menú de los cuatro roles y hoy son maquetas. Ninguno estaba en el plan anterior.

| Bloque | Módulo | Quién lo usa | Responsable |
|---|---|---|---|
| 6 | **Pagos con Wompi** (cobro, pago en línea, webhook, comprobante PDF, estado de cuenta) | acudiente, tesorero, administrador | Omar (backend) y Felipe (pantallas). **Prioridad alta:** el profesor insistió en este punto; Omar decide si lo adelanta antes del bloque 3 |
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
