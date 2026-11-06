# FutData — Presentación técnica

Sistema de gestión deportiva para la Asociación Deportiva Prado Fútbol Club (PPI, sexto semestre).

> **Esta es la fuente de verdad del proyecto.** El documento PPI ya no se modifica: lo que se decida desde el 1 de noviembre de 2026 se registra aquí. Se actualiza con cada cambio (ver el registro de cambios al final).
> Última actualización: 5 de noviembre de 2026 (versión 4: el 4B-2 y el bloque 7 ya están en `develop`; el bloque 3 (documentos) está implementado en su rama `feature/bloque-3-documentos`, pendiente de merge, ver 9.1.1).
> **Congelamiento: viernes 6 de noviembre de 2026. Entrega final al jurado: sábado 7 de noviembre de 2026.** Para esa fecha toda la aplicación debe funcionar con datos reales, con los cuatro roles y sin maquetas.

## 1. Qué es FutData

Una aplicación para administrar un club de fútbol formativo: inscripción de deportistas y acudientes, documentos, categorías, entrenadores, horario de entrenamientos, mensualidades y comprobantes de pago.

Roles (son **cuatro**): **administrador**, **entrenador**, **tesorero** y **acudiente**. El ingreso usa verificación en dos pasos por correo (2FA, flujo en n8n).

Una aplicación **sin maquetas**: cada pantalla del menú de cada rol se conecta a la API real. Lo que no se alcance a conectar se oculta del menú; no se muestran datos de ejemplo al jurado.

## 2. Equipo y herramientas

| Quién | Responsabilidad |
|---|---|
| Omar Támara | Backend (Spring Boot, base de datos, n8n) |
| Felipe Arango | Frontend y backend de pagos (Wompi) |
| Juan Sebastián | Apoyo: colección Postman (HU-057) y revisión de PR. Ya no está en la ruta crítica |

Trabajo distribuido en Azure DevOps. Código en GitHub (`FelipeArango17/FutData-Aula-Project`), ramas desde `develop`, un PR por bloque.

## 3. Arquitectura y stack

- **Backend:** Spring Boot 3.5, Java 21, Maven wrapper (`mvnw`), capas controlador, servicio y DAO con JDBC.
- **Base de datos:** PostgreSQL en Supabase. Migraciones numeradas en `supabase/migrations/` (hoy 001 a 010).
- **Autenticación:** Supabase Auth, con 2FA por correo. El backend exige el rol en cada controlador con `@PreAuthorize`. El frontend solo muestra las rutas de un panel si hay sesión verificada **y** el rol de la sesión coincide con el del panel (no se usa el rol guardado en el navegador).
- **Pagos en línea:** Wompi (pasarela elegida por el equipo; el profesor la pidió expresamente). Se trabaja con el entorno de pruebas (sandbox). El webhook `POST /api/pagos/webhook/wompi` es público y debe **verificar la firma** del evento.
- **Archivos:** Supabase Storage con buckets privados (`fotos`, `documentos`, y el de comprobantes). Solo el backend entra con la llave `service_role` y entrega URL firmadas.
- **Correos y automatización:** n8n (código 2FA, enlace de contraseña, aviso de documento rechazado).
- **Frontend:** React.
- **Despliegue (lo verificado en el repositorio):** el backend se empaqueta en un contenedor Docker (`backend/Dockerfile`, perfil `prod`) y está documentado como desplegado en Render, con Cloudflare por delante (por eso la IP del cliente se toma de `CF-Connecting-IP`; ver `backend/README.md`). El frontend se publica en Vercel (`frontend/vercel.json`). n8n corre en **n8n Cloud**: el workflow está publicado, el webhook se autentica con *Header Auth* (cabecera `X-FutData-Token`) y el remitente de los correos es fijo. Se probó con el backend en local contra Cloud; **falta apuntar el backend de Render a esa instancia** (ver 9.5). El `docker-compose` de `n8n/` sigue sirviendo para desarrollo local. El CI de GitHub Actions (`.github/workflows/ci.yml`) prueba y compila, pero no despliega.
- **Pruebas:** JUnit con Testcontainers (PostgreSQL 17 en Docker). Estado de la rama `feature/bloque-3-documentos` (sobre `develop`, con el 4B-2 y el bloque 7), contado con `./mvnw clean test`: **1664 pruebas, 0 fallos, 9 omitidas**. El total ya incluye las pruebas del backend de pagos con Wompi. Las 9 omitidas son las manuales, que necesitan n8n o Supabase reales: n8n local, Storage, fotos y el enlace de crear contraseña contra el proyecto de prueba. El "874" que figuraba antes era un error de conteo: al sumar los reportes `.txt` de surefire no se contaban las clases `@Nested`. El total real antes del bloque 5 era **928**.

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
- **excepcion_horario:** (**migración 010**, bloque 4B-2; ya aplicada en la base del equipo) excepciones por fecha sobre las franjas. Dos tipos: `CANCELACION` (apunta a su `id_franja`; la categoría, la hora, la sede y el entrenador son los de la franja, así que sus demás campos van en `NULL`) y `EXTRA` (un entrenamiento adicional con su propia `id_categoria`, `hora_inicio`, `hora_fin`, `sede`, `id_entrenador` —`NULL` = "por turnos"— y `grupo`). Ambos con `fecha`, `motivo` (máximo 200), `creado_por` (`usuario`, `on delete set null`) y `creado_en`. La base decide tres reglas: un `check` de coherencia por tipo (`excepcion_horario_campos_ck`), el índice único parcial `excepcion_horario_cancelacion_unica_idx` (una franja no se cancela dos veces el mismo día) y la exclusión `excepcion_horario_cruce_entrenador_ex` (un entrenador no tiene dos extras que se solapan el mismo día; "por turnos" no choca). El cruce de un extra con las franjas lo comprueba el backend. `on delete cascade` desde `franja_horario` (borrar una franja borra sus cancelaciones) y desde `categoria` (borra sus extras); `restrict` desde `entrenador`. La tabla `entrenamiento` no se toca.
- **documento:** 7 tipos, estados PENDIENTE, APROBADO y RECHAZADO, con `cargado_por`, `revisado_por` y comentario al rechazar.

## 5. Reglas de negocio vigentes

1. **Categoría automática** desde la fecha de nacimiento (regla de año). Se rechaza a quien quede fuera de 4–16.
2. **Documentos del deportista:** TI, RC, CE y PASAPORTE.
3. **Sedes:** Horizontes y UVA El Paraíso. La sede va **por franja**, no por categoría.
4. **Horario:** lunes a sábado. Varias categorías pueden coincidir en hora y sede. Un mismo entrenador no puede tener dos franjas ni dos entrenamientos que se solapen. No hay entrenamientos en fechas pasadas: esta parte empieza a aplicar con el bloque 4B-2 (cancelaciones y extras), porque hoy ninguna API usa la tabla `entrenamiento`.
5. **Grupo mañana/tarde** (Cachorros y Semillero, martes y jueves): la franja lo guarda y **el grupo de cada deportista también se guarda en la base** (columna `deportista.grupo`, migración 008). Lo elige el administrador al inscribir o editar; el backend solo lo acepta en Cachorros y Semillero. No cambia el horario ni las categorías.
6. **Por turnos:** Párvulos, Cachorros y Semillero no tienen entrenador fijo (el club rota quién dirige).
7. **Documentos de la inscripción (7 tipos):**
   - Obligatorios: `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION`, `AUTORIZACION_HABEAS_DATA`.
   - Si aplica: `PAZ_Y_SALVO`, `CARTA_RENUNCIA`, `TRASPASO_NACIONAL`.
   - La "foto tipo documento" es la **foto del deportista**, no un tipo de documento. La ficha médica ya no existe.
   - Archivos PDF, JPG o PNG, máximo 5 MB, validados por su contenido real (no por la extensión).
8. **Validación de documentos:** el administrador revisa. Mientras no los haya validado, **la inscripción queda pendiente**. Al rechazar un documento el comentario es obligatorio y se envía por correo al acudiente (n8n). El correo llega con el bloque 3 (evento `DOCUMENTO_RECHAZADO`, ver 9.1.1): hoy el flujo de n8n Cloud tiene los eventos `CODIGO_2FA`, `ENLACE_CONTRASENA`, `CAMBIO_ENTRENAMIENTO` y `RECORDATORIO_PAGO`. El backend ya envía `DOCUMENTO_RECHAZADO` (bloque 3 implementado) y el JSON de Cloud con ese evento ya está en el repositorio (`n8n/workflows/futdata-notificaciones_cloud.json`); **falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento` (Omar)** (ver 9.5): hasta entonces n8n responde `400` "Evento no soportado" y el rechazo devuelve `avisoEnviado: false` (el documento queda rechazado igual).
9. **Alta de entrenador y de acudiente:** sin contraseña temporal ni cambio forzado. Se envía un enlace para que cada persona cree su propia contraseña. Hecho para el entrenador (bloque 5); el acudiente llega con la inscripción (2B).
   - **Enlace:** vence en **1 hora** (Supabase Auth, `mailer_otp_exp = 3600`) y sirve una sola vez; un enlace nuevo invalida el anterior. Si vence, el administrador lo reenvía. Nunca se registra en logs ni sale en una respuesta de la API.
   - **Contraseña:** entre 8 y 72 caracteres, como mucho 72 bytes en UTF-8 (una ñ o una tilde ocupan 2), con al menos una letra y un número. Mismo mensaje en el frontend y el backend: "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número."
   - **Límites en memoria:** un envío del enlace por minuto por entrenador (el alta cuenta como uno) y, al crear la contraseña, 10 intentos por minuto por IP, 5 por enlace y 60 en total. Se pierden al reiniciar y no se comparten entre instancias (hoy hay una sola en Render).
   - **Desactivar un entrenador con cuenta le impide entrar:** se bloquea su cuenta en Supabase Auth, `usuario.activo` pasa a `false` y se borran sus sesiones verificadas; al reactivarlo se restaura. Sus franjas se conservan (con `entrenadorActivo: false`).
   - El correo de un entrenador con cuenta no se puede cambiar (`409`): es con el que inicia sesión.
10. **Inscripción pendiente:** el estado de la inscripción **se calcula** (no es una columna): está *pendiente* mientras alguno de los 4 documentos obligatorios no esté APROBADO. La API lo devuelve como `estadoInscripcion`. Mientras esté pendiente, el deportista queda **activo**, para que el administrador lo vea en la lista y valide sus documentos.
11. **Parentesco** (acudiente–deportista): `Madre`, `Padre`, `Abuelo`, `Abuela`, `Tia`, `Tio`, `Otro` (migración 008). El frontend lo muestra con tilde y lo envía sin tilde.
12. **Protección de pantallas:** sin sesión verificada, cualquier ruta de la aplicación lleva a `/login`; con la sesión de un rol, las rutas de otro rol llevan al inicio del propio.
13. **Pagos (bloque 6, Wompi).** Fuente: `ReglasPago`, `MensualidadProperties`, `GeneradorMensualidades`, `AplicadorTransaccionWompi`, `WompiFirmas` y los `@PreAuthorize` de `PagoController`.
   - **Moneda y montos:** pesos colombianos (`COP`); a Wompi se envían en centavos. Monto máximo de un cobro: 999.999.999. Concepto de hasta 250 caracteres. Los montos salen siempre de la base, nunca del frontend.
   - **Tipos de cobro:** `MENSUALIDAD`, `INSCRIPCION`, `TORNEO` y `UNIFORME`. El periodo (primer día del mes) solo es obligatorio en la mensualidad, y no puede haber dos mensualidades no rechazadas del mismo deportista y periodo (`409`).
   - **Referencia:** `FD-<id del cobro>`, única por cobro y solo con letras, números, guion y guion bajo (lo que Wompi admite).
   - **Estados guardados:** `PENDIENTE`, `APROBADO` y `RECHAZADO`. "Vencido" no se guarda: es un `PENDIENTE` con vencimiento anterior a hoy.
   - **Transiciones con Wompi** (las mismas en el webhook y en la conciliación): transacción `APPROVED` sobre un `PENDIENTE` con el mismo monto → `APROBADO` (con fecha, medio de pago, id de la transacción y aviso al acudiente); con monto distinto, no cambia y queda en auditoría. `DECLINED`, `ERROR` o `VOIDED` sobre un `PENDIENTE` → `RECHAZADO` con aviso (no crea un cobro nuevo: para eso está "Reintentar"). `VOIDED` sobre un `APROBADO` con el mismo id → `RECHAZADO` y auditoría. `APPROVED` sobre un `RECHAZADO` (el checkout aprobó después de un rechazo): si ya hay otro cobro equivalente aprobado, no cambia nada y queda en auditoría como posible pago duplicado; si no, los cobros pendientes equivalentes pasan a `RECHAZADO` ("Reemplazado") y este se aprueba. Un evento repetido (mismo id de transacción) se ignora. `PENDING` no cambia nada.
   - **Medios de pago de Wompi:** tarjeta (`CARD`), Nequi, PSE, QR Bancolombia y botón Bancolombia; cualquier otro queda como "Wompi (otro medio)". La aprobación manual es para efectivo o transferencia.
   - **Quién hace qué:** el administrador crea cobros y genera mensualidades; el administrador y el tesorero listan, concilian con Wompi, aprueban pagos manuales y rechazan cobros pendientes; el acudiente ve los cobros de sus deportistas, inicia el pago en Wompi y reintenta un cobro rechazado (de un deportista que no es suyo recibe `404`, no `403`); el comprobante en PDF (solo de cobros aprobados) lo descargan administrador, tesorero y acudiente.
   - **Mensualidades automáticas:** si está activa (`FUTDATA_MENSUALIDAD_GENERACION_AUTOMATICA`), cada día a las 6:00, hora de Bogotá (`America/Bogota`), se crea la mensualidad `PENDIENTE` del mes en curso de cada deportista que no la tenga. Es idempotente: correrla dos veces no duplica nada. El monto (`FUTDATA_MENSUALIDAD_MONTO`) lo define el club (**por confirmar**); mientras no esté configurado, no se generan mensualidades (la generación manual responde `409`). Vence el día del mes de `FUTDATA_MENSUALIDAD_DIA_VENCIMIENTO`, o el último día si el mes es más corto. Hoy alcanza a **todos** los deportistas de la tabla, sin filtrar por `activo` ni por `estadoInscripcion`. **Por confirmar** si debe excluir a los inactivos. Un deportista con la inscripción pendiente queda activo (regla 10), así que, aunque se excluyan los inactivos, seguiría recibiendo la mensualidad. Esta generación queda reemplazada por los cobros por ciclo de ingreso (regla 14): `POST /api/pagos/generar-mensualidades` queda obsoleto y la tarea diaria de n8n llama a `POST /api/pagos/generar-ciclos`.
   - **Firma del webhook:** `POST /api/pagos/webhook/wompi` es público. Se calcula el checksum (SHA-256) con las propiedades que indica el evento, su `timestamp` y el secreto de eventos, y solo se acepta si coincide con el encabezado `X-Event-Checksum` **y** con `signature.checksum` (comparación en tiempo constante). Firma inválida o ausente: `401`. Sin el secreto configurado: `503` `PAGOS_NO_CONFIGURADOS`. Con firma válida responde `200` aunque ignore el evento. El checkout se firma en el servidor con el secreto de integridad.
14. **Cobros por ciclo de ingreso** (como un gimnasio; reemplaza la generación manual de mensualidades por calendario):
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

**Ya implementados (todos en `develop`):**

- Deportistas (bloque 2A, en `develop`; detalle en `backend/README.md`):
  - `GET /api/deportistas`: paginado (`page`, `size` hasta 100, `sort`, filtros `activo` y `categoria`); responde `content`, `page`, `size`, `totalElements` y `totalPages`. ADMINISTRADOR y ENTRENADOR. Al entrenador no se le envían las claves `documento` ni `estadoInscripcion` (ausentes, no `null`), y `telefono` y `direccion` llegan con valor `null`. `estadoInscripcion` vale `COMPLETA` (los 4 documentos obligatorios con uno APROBADO) o `PENDIENTE`.
  - **Qué ve cada rol en `GET /api/deportistas` (bloque 7, ya en `develop`, PR #93):** el ADMINISTRADOR ve todos los deportistas (también a quien queda fuera de 4 a 16 años). El ENTRENADOR **solo ve a los de las categorías que tiene a cargo** (`categoria.id_entrenador` = su fila de entrenador, que se halla por `entrenador.id_usuario` = `sub` del token). La regla se resuelve en SQL, con un `EXISTS` sobre la categoría **calculada por edad** (no sobre `deportista.id_categoria`) y por nombre, y la usan igual la lista y el total, así que `totalElements` y `totalPages` reflejan el filtro. Reglas del entrenador:
    - Sin el parámetro `categoria`, solo las suyas.
    - `categoria` acepta valores repetidos (`categoria=Sub-11&categoria=Sub-12`) o separados por comas, y se **cruza** con las suyas: pedir una que existe pero no es suya da `200` con la lista vacía (no `403`).
    - Un nombre de categoría que no existe da `400` (`campos: ["categoria"]`), igual que para el administrador.
    - Un entrenador sin categorías asignadas (o sin fila de entrenador) recibe `200` con la lista vacía y `totalElements` 0.
    - No ve a los deportistas fuera de 4 a 16 años (no tienen categoría calculada).
    - El entrenador inactivo conserva sus categorías (no se filtra por `entrenador.activo`; de todos modos su cuenta queda bloqueada).
    - La forma de la respuesta no cambia: sin `documento` ni `estadoInscripcion`.
    - TESORERO y ACUDIENTE reciben `403`. El servicio **falla cerrado**: un rol que no sea ADMINISTRADOR ni ENTRENADOR (o un solicitante sin identificar) nunca obtiene la lista completa.
  - `GET /api/deportistas/{id}` sigue **solo para ADMINISTRADOR** (el entrenador recibe `403`); `POST /api/deportistas` y `PUT /api/deportistas/{id}`: solo ADMINISTRADOR; documento repetido da 409.
  - `POST` y `DELETE /api/deportistas/{id}/foto`: solo ADMINISTRADOR; JPG o PNG por su contenido, máximo 5 MB; la respuesta trae una URL firmada de 60 minutos.
- Horario del club (bloque 4A, en `develop`; detalle en `backend/README.md`):
  - `GET /api/horario-club` y `GET /api/horario-club/{id}`: ADMINISTRADOR, TESORERO, ENTRENADOR y ACUDIENTE. Filtros opcionales `categoria` (nombre exacto), `entrenador` (UUID) y `sede`; ordenado por día, hora de inicio y categoría.
  - `POST /api/horario-club`, `PUT /api/horario-club/{id}` (reemplaza todo) y `DELETE /api/horario-club/{id}` (`204`): solo ADMINISTRADOR.
  - Horas en `HH:mm`. `idEntrenador` null = "por turnos"; el entrenador inactivo solo se rechaza al asignarlo o cambiarlo. Grupo solo en Cachorros y Semillero.
  - El mismo entrenador con dos franjas que se solapan el mismo día: `409` "Ese entrenador ya tiene otra franja que se cruza con este horario". Las contiguas no se cruzan y categorías distintas pueden coincidir en hora y sede.
- Horario de la semana (bloques 4B-1 y 4B-2; el 4B-1 ya está en `develop`, PR #88; el 4B-2 también, PR #91; detalle en `backend/README.md`). Incluye el bloque `entrenador` de `/api/usuarios/yo` y la migración 010:
  - `GET /api/usuarios/yo` agrega `"entrenador": { "idEntrenador": uuid, "categorias": [ { "idCategoria": uuid, "nombre": "Semillero" } ] }`. La clave va siempre en el JSON y es `null` salvo para el rol ENTRENADOR con una fila de entrenador vinculada (`entrenador.id_usuario`); un ENTRENADOR sin esa fila recibe `null`. `categorias` son las que tiene a cargo (`categoria.id_entrenador`) en el orden oficial; un entrenador inactivo recibe el bloque con las categorías que conserve. Los demás endpoints de usuarios no cambian.
  - `GET /api/horario-semana?fecha=AAAA-MM-DD`: los cuatro roles. Cualquier día de la semana sirve; sin `fecha`, hoy en Bogotá. **El domingo cuenta la semana siguiente** (el club no entrena los domingos; igual que `semanaEnCurso` del frontend), tanto para la semana por defecto como para una fecha pedida y para el acudiente. Respuesta: `{ "desde": "2026-11-02", "hasta": "2026-11-07", "sesiones": [...] }` (lunes y sábado), con las franjas y los extras de la semana ordenados por `fecha`, `horaInicio`, categoría oficial y grupo. El acudiente solo puede pedir la semana en curso: otra da `403` "Solo puedes consultar la semana en curso". Una `fecha` que no es una fecha real `AAAA-MM-DD` da `400` con `campos: ["fecha"]`.
  - Cada sesión: `tipo` (`FRANJA` o `EXTRA`), `estado` (`ACTIVO` o `CANCELADO`), `idFranja` (`null` en un extra), `idExcepcion` (el id de la cancelación o del extra; `null` en una franja activa), `fecha`, `dia` (1..6; en un extra sale de su fecha), `horaInicio` y `horaFin` (`HH:mm`), `idCategoria`, `categoria`, `sede`, `idEntrenador`, `entrenador` (nombre; `null` = por turnos), `entrenadorActivo` (`false` si el entrenador se desactivó después), `grupo`, `motivo` y `puedeGestionar`. Los nombres coinciden con `/api/horario-club`. Una franja cancelada ese día sigue siendo `tipo` `FRANJA`, con `estado` `CANCELADO`, su `idExcepcion` y el `motivo`; una cancelación cuyo día de la semana ya no coincide con el de su franja (porque el 4A la cambió de día) no se aplica.
  - `puedeGestionar` (si quien consulta puede cancelar, deshacer o eliminar esa sesión): ADMINISTRADOR, en todo; ENTRENADOR, en las franjas de una categoría suya o que él dirige y, en un **extra**, solo si la categoría es suya (ser el entrenador asignado no basta); acudiente y tesorero, nunca. En una sesión de una fecha **anterior a hoy** es `false` para todos, administrador incluido (hoy no cuenta como pasada).
  - Escritura (ADMINISTRADOR y ENTRENADOR; acudiente y tesorero reciben `403` por rol). Todas responden `{ "sesion": {...}, "avisoEnviado": true | false | null }`: `201` en los `POST` y `200` en los `DELETE` (en el borrado de un extra, `sesion` es `null`). `creado_por` es el `sub` del token.
    - `POST /api/horario-semana/cancelaciones` `{ fecha, idFranja, motivo? (máx. 200) }`. Orden de las validaciones: cuerpo (`400` con todos los campos que fallan: `fecha` `AAAA-MM-DD`, `idFranja` UUID, `motivo` máx. 200 sin espacios de más) → franja inexistente (`404`) → fecha anterior a hoy (`400`, `campos: ["fecha"]`; hoy es válido) → día de la semana distinto al de la franja (`400`, `campos: ["fecha"]`) → permiso (`403`) → franja ya cancelada ese día (`409` "Esa franja ya está cancelada en esa fecha", `campos: ["fecha"]`). Responde la sesión `CANCELADO`. Se puede cancelar aunque el entrenador de la franja esté inactivo.
    - `DELETE /api/horario-semana/cancelaciones/{idExcepcion}`: deshace la cancelación y responde la franja otra vez `ACTIVO`. `404` si no existe o es un extra; `400` si su fecha es pasada; `403`; `400` si el id no es un UUID.
    - `POST /api/horario-semana/extras` `{ idCategoria, fecha, horaInicio, horaFin, sede, idEntrenador (null = por turnos), grupo?, motivo? }`. Orden: cuerpo (`400` con todos los campos que fallan: UUID, fecha, horas `HH:mm`, `horaFin` > `horaInicio`, `sede` `Horizontes` o `UVA El Paraíso`, `grupo` `mañana` o `tarde` y solo si la categoría es Cachorros o Semillero, `motivo` máx. 200) → categoría inexistente (`404`) → fecha pasada (`400`) → fecha en domingo (`400`, de lunes a sábado) → permiso (`403`) → entrenador asignado inexistente o inactivo (`400`, `campos: ["idEntrenador"]`, los mismos mensajes que las franjas) → cruce (`409` "Ese entrenador ya tiene otra franja que se cruza con este horario", `campos: ["idEntrenador"]`). Todo en una sola transacción: se bloquea la fila del entrenador, se comprueba el cruce con sus franjas **no canceladas** ese día y se inserta (el cruce con otro extra lo decide la base). Un extra "por turnos" no se compara con nadie; un extra que cae en el horario de una franja **cancelada ese día** se permite; un extra puede quedar fuera de las franjas fijas. Responde la sesión `EXTRA` / `ACTIVO`.
    - `DELETE /api/horario-semana/extras/{idExcepcion}`: `404` si no existe o es una cancelación; `400` si su fecha es pasada; `403`.
  - Permisos del ENTRENADOR (se deciden en SQL, no en memoria; un `403` dice "Solo puedes modificar el horario de tus categorías"): **cancelar o deshacer** una franja, si es el entrenador de su categoría o el entrenador de la franja (una franja "por turnos" solo la gestiona el dueño de la categoría); **crear o eliminar un extra**, solo si es el entrenador de su categoría. El administrador se salta la consulta por su rol.
  - Avisos: después de confirmar el cambio (nunca dentro de la transacción) se avisa por `CAMBIO_ENTRENAMIENTO` a **todos** los acudientes con correo de los deportistas activos de la categoría (la categoría de cada deportista se calcula por año de nacimiento; no se filtra por grupo ni se avisa al entrenador), sin correos repetidos. `tipoCambio`: `CANCELADO` al cancelar una franja (con el motivo de la cancelación) y al eliminar un extra (con el motivo del extra); `CREADO` al deshacer una cancelación y al crear un extra (sin motivo: n8n lo ignora en `CREADO`). `entrenamiento`: `categoria` (con el grupo si lo hay: `Cachorros (mañana)`), `fecha`, `horaInicio`, `lugar` (la sede) y `entrenador` (o `Por turnos`). `avisoEnviado`: `true`, n8n aceptó todas las llamadas (no garantiza que el correo llegue); `false`, no se pudo avisar (n8n falló o no respondió en 5 s), pero la operación **sí** quedó hecha y la respuesta sigue siendo `201` o `200`; `null`, la categoría no tiene acudientes con correo y no se llamó a n8n. Más de 200 destinatarios se envían en bloques de 200, una llamada por bloque; si un bloque falla, los siguientes no se envían y el resultado es `false`.
  - Limitaciones conocidas: (1) deshacer una cancelación no revisa si el entrenador ya tiene un extra en ese horario; (2) el `PUT /api/horario-club/{id}` (4A) no revisa cruces con extras ya creados ni la fecha de las cancelaciones existentes; (3) borrar una franja borra sus cancelaciones (`on delete cascade`); (4) el aviso se hace en la misma petición: puede tardar hasta 5 s por bloque si n8n se demora, y si un bloque falla los siguientes no se envían; (5) si la sesión tiene grupo, el aviso va a **todos** los acudientes de la categoría, no solo a los del grupo; (6) un cambio de `tipoCambio` `MODIFICADO` no existe todavía (llegará con el `PUT` de extras).
  - El frontend debe mapear `horaInicio` a `hora` y `ACTIVO` a `PROGRAMADO`, guardar el bloque `entrenador` en la sesión y leer `puedeGestionar` y `avisoEnviado` (pendiente, F-4B).
- Mis jugadores del acudiente (bloque 2C, en `develop`, PR #87; detalle en `backend/README.md`). Solo rol ACUDIENTE; el acudiente sale del token (`sub` del JWT), nunca de un id que envíe el frontend, y la propiedad se decide en el SQL (`acudiente_deportista` + `acudiente.id_usuario`):
  - `GET /api/acudientes/me/deportistas`: arreglo (sin paginar) con sus jugadores vinculados: `id`, `nombre`, `apellido`, `fechaNacimiento`, `edad`, `categoria`, `idCategoria` (`null` fuera de 4–16), `grupo` (solo Cachorros y Semillero), `posicion`, `foto` (URL firmada o `null`), `parentesco` (sin tilde), `activo` y `estadoInscripcion` (`COMPLETA` o `PENDIENTE`). No trae documento, teléfono ni dirección. Sin jugadores: `200` con `[]`.
  - `GET /api/acudientes/me/deportistas/{id}`: la ficha, con lo anterior más `tipoDocumento`, `documento`, `telefono`, `direccion` y `documentosFaltantes` (**solo en la ficha**: los tipos obligatorios sin documento APROBADO; vacía si y solo si `estadoInscripcion` es `COMPLETA`). Un id que no es UUID da `400`; uno ajeno o inexistente da `404` con el mismo mensaje (no `403`).
  - Sin la sesión 2FA verificada la respuesta es `403` con `codigo` `2FA_REQUERIDA`; `401` solo si falta el token o el `session_id`.
- Documentos de la inscripción (bloque 3, en la rama `feature/bloque-3-documentos`, pendiente de merge; diseño y decisiones en 9.1.1; detalle en `backend/README.md`). Son documentos de identidad de menores: solo el ADMINISTRADOR y el ACUDIENTE dueño del deportista; el ENTRENADOR y el TESORERO reciben `403` por rol. El usuario sale siempre del token y la propiedad del acudiente se decide en el SQL (`acudiente_deportista` + `acudiente.id_usuario`); un deportista o documento ajeno responde `404` con el mismo mensaje que uno inexistente ("No existe un deportista con ese id." o "No existe un documento con ese id."). Sin la sesión 2FA verificada: `403` con `codigo` `2FA_REQUERIDA`, igual que el 2C; sin token o sin `session_id`: `401`.

  | Ruta | Roles | Entrada | Salida | Códigos |
  |---|---|---|---|---|
  | `POST /api/deportistas/{id}/documentos` | ADMINISTRADOR, ACUDIENTE dueño | Multipart: `tipo` (uno de los 7), `archivo` (PDF, JPG o PNG, hasta 5 MB) y `fechaVencimiento` opcional (`AAAA-MM-DD`, no anterior a hoy) | `201` con el documento (queda `PENDIENTE`) | `400` (todos los errores juntos; si falla solo el archivo, `campos: ["archivo"]`), `403`, `404`, `409` (`campos: ["tipo"]`: ya hay uno PENDIENTE o APROBADO de ese tipo), `503` (Storage) |
  | `GET /api/deportistas/{id}/documentos` | ADMINISTRADOR, ACUDIENTE dueño | — | `200` con `{ documentos, estadoInscripcion, documentosFaltantes }` | `400` (id no UUID), `403`, `404` |
  | `GET /api/documentos/{id}/descarga` | ADMINISTRADOR, ACUDIENTE dueño | — | `200` con `{ url, vigenciaSegundos }` y `Cache-Control: no-store` | `400`, `403`, `404`, `503` |
  | `POST /api/documentos/{id}/aprobar` | ADMINISTRADOR | — (sin comentario) | `200` con `{ documento, estadoInscripcion }` | `400`, `403` (también el acudiente dueño), `404`, `409` (`campos: ["estado"]`: ya no está PENDIENTE) |
  | `POST /api/documentos/{id}/rechazar` | ADMINISTRADOR | JSON `{ comentario }` (1 a 300 caracteres, recortado) | `200` con `{ documento, estadoInscripcion, avisoEnviado }` | `400` (`campos: ["comentario"]`, también sin cuerpo o con el comentario vacío), `403`, `404`, `409` (`campos: ["estado"]`) |

  - **Forma del documento:** `id`, `idDeportista`, `tipo`, `nombreOriginal`, `tipoContenido`, `tamanoBytes`, `estado` (`PENDIENTE`, `APROBADO` o `RECHAZADO`), `comentario` (el motivo del rechazo; `null` si no está rechazado), `fechaVencimiento`, `cargadoPor` (`Administrador` o `Acudiente`, derivado del rol de quien lo subió; `null` si esa cuenta ya no existe), `cargadoPorCorreo`, `cargadoEn`, `revisadoPor` (correo del revisor) y `revisadoEn`. **Nunca trae la ruta del archivo**: se descarga con una URL firmada.
  - **Archivos:** PDF, JPG o PNG de hasta 5 MB. El tipo se decide **solo por la firma de los bytes** (`%PDF-`, `FF D8 FF`, firma PNG): el `Content-Type` del cliente y la extensión se ignoran. Archivo vacío, de más de 5 MB (el tope de multipart es 10 MB; entre 5 y 10 lo decide el servicio y más de 10 el manejo global, los dos con `400`) o con otra firma: `400` con `campos: ["archivo"]`.
  - **Listar:** del más reciente al más antiguo, incluidos los rechazados (historial). `estadoInscripcion` (`COMPLETA` o `PENDIENTE`) y `documentosFaltantes` (los obligatorios sin documento APROBADO; vacía si y solo si es `COMPLETA`) salen de lo que ya calcula `DeportistaDAOImpl`: aprobar o rechazar los actualiza solos, sin columna.
  - **Subir:** autoriza, valida, revisa sin bloqueo si ya hay un documento activo del tipo (`409` antes de subir nada), sube a Storage **fuera de toda transacción** (ruta `{idDeportista}/{uuid}.{ext}`, nunca el nombre del cliente) y abre una transacción corta que bloquea al deportista (`select ... for no key update`), repite la revisión e inserta. Si algo falla después de subir (el `409` de la transacción, un error de la base), borra el archivo y relanza la excepción original; si borrar también falla, solo se registra el tipo de excepción. Dos subidas simultáneas del mismo tipo dejan un documento y un solo archivo.
  - **Aprobar y rechazar:** `update ... where estado = 'PENDIENTE'` y relectura en una transacción corta. Si ya no estaba PENDIENTE (lo revisó otro administrador, o ya estaba aprobado o rechazado) responde `409`; rechazar un APROBADO también es `409` (reemplazar un aprobado no entra en esta versión). El orden de las validaciones es: autorización (`403`), documento (`404`), comentario (`400`), revisión (`409`).
  - **Aviso al rechazar (`DOCUMENTO_RECHAZADO`):** después de confirmar el rechazo se avisa a los acudientes vinculados al deportista que tengan un correo válido, un `POST` por acudiente. `avisoEnviado`: `true`, n8n aceptó todas las llamadas (no garantiza que el correo llegue); `false`, no se pudo avisar (n8n falló o no respondió en 5 s, no se pudieron leer los destinatarios, falta `FRONTEND_URL` o el enlace pasa de 2000 caracteres), pero el rechazo **sí** quedó hecho y la respuesta sigue siendo `200`; `null`, no hay acudientes con correo y no se llamó a n8n. La clave va siempre en el JSON, con `null` si no aplica (igual que en el horario). Si una llamada falla no se envían las demás. Aprobar no envía ningún aviso.
- Alta de entrenadores con enlace (bloque 5, en `develop`; detalle en `backend/README.md`):
  - `POST /api/entrenadores` (solo ADMINISTRADOR) ahora también crea la cuenta de acceso y responde `201` con `correoEnviado` (`true` o `false`); `409` si el correo ya es de otra cuenta y `503` si Supabase Auth no responde (no se guarda nada). Las demás respuestas de entrenadores no traen `correoEnviado`.
  - `POST /api/entrenadores/{id}/reenviar-enlace` (solo ADMINISTRADOR): `204` sin cuerpo; `404`; `409` (inactivo, sin correo o correo de otra cuenta); `429` con `Retry-After` (un envío por minuto); `503` (Supabase o el correo fallan).
  - `POST /api/auth/crear-contrasena` (**público**, sin token ni sesión) con `{ token, contrasena }`: `200` con un mensaje y sin tokens; `400` de política con `campos: ["contrasena"]`; `400` de enlace inválido, vencido o usado (mensaje único, sin `campos`); `429` con `Retry-After`; `503` si Supabase Auth falla.
  - `PUT /api/entrenadores/{id}`: `409` si intenta cambiar el correo de un entrenador con cuenta.
- Pagos con Wompi (bloque 6, Felipe, en `develop`; detalle en `backend/README.md`):

  | Ruta | Roles | Qué hace |
  |---|---|---|
  | `POST /api/pagos` | ADMINISTRADOR | Crea un cobro en estado PENDIENTE |
  | `GET /api/pagos` | ADMINISTRADOR, TESORERO | Lista los cobros (paginado) |
  | `GET /api/pagos/mis-pagos` | ACUDIENTE | Los cobros de sus deportistas |
  | `POST /api/pagos/{id}/iniciar-wompi` | ACUDIENTE | Devuelve los datos para abrir el checkout de Wompi |
  | `POST /api/pagos/{id}/reintentar` | ACUDIENTE | Reintenta un cobro rechazado |
  | `POST /api/pagos/{id}/conciliar` | ADMINISTRADOR, TESORERO | Concilia el cobro con Wompi |
  | `POST /api/pagos/{id}/aprobar` | ADMINISTRADOR, TESORERO | Aprueba un pago manual |
  | `POST /api/pagos/{id}/rechazar` | ADMINISTRADOR, TESORERO | Rechaza un cobro pendiente |
  | `GET /api/pagos/{id}/comprobante` | ADMINISTRADOR, TESORERO, ACUDIENTE | Comprobante en PDF |
  | `POST /api/pagos/generar-mensualidades` | ADMINISTRADOR | Genera las mensualidades de un periodo |
  | `POST /api/pagos/webhook/wompi` | Público (sin token) | Recibe el evento de Wompi (`transaction.updated`) y verifica su firma antes de usarlo |

  **Filtros de `GET /api/pagos`** (ADMINISTRADOR y TESORERO): `estado`, `tipo`, `idDeportista`, `periodo` (`AAAA-MM`) y, desde el bloque 7, `vencimientoDesde` y `vencimientoHasta`. Estos dos son `AAAA-MM-DD`, **ambos inclusive**, y filtran `fecha_vencimiento` (columna `NOT NULL`, así que todo cobro tiene fecha). Se combinan entre sí y con los demás filtros; se pueden usar uno solo o los dos, y sin ellos todo sigue igual. Se leen como texto para controlar el mensaje:
  - Una fecha que no es real (`2026-13-01`, `2026-02-30`, `01/11/2026`) da `400` con `campos: ["vencimientoDesde"]` o `["vencimientoHasta"]` y el mensaje "debe ser una fecha válida con el formato AAAA-MM-DD" (el mismo de `/api/horario-semana`).
  - Los errores de formato de `periodo` y de las dos fechas salen juntos en un solo `400`.
  - `vencimientoDesde` posterior a `vencimientoHasta` da `400` con `campos: ["vencimientoDesde", "vencimientoHasta"]`.
  - Un valor en blanco cuenta como si no viniera.

- Autenticación, usuarios, categorías, salud y el resto de rutas de entrenadores (detalle en `backend/README.md`):

  | Ruta | Roles | Qué hace |
  |---|---|---|
  | `POST /api/auth/2fa/codigo` | Cualquier rol, con el JWT de Supabase y sin 2FA todavía | Genera el código de verificación y lo envía al correo (`202`) |
  | `POST /api/auth/2fa/reenviar` | Cualquier rol, con el JWT de Supabase y sin 2FA todavía | Reenvía el código (uno por minuto) |
  | `POST /api/auth/2fa/verificar` | Cualquier rol, con el JWT de Supabase y sin 2FA todavía | Verifica el código de seis dígitos; solo la sesión del token queda verificada |
  | `POST /api/auth/logout` | Cualquier rol con sesión verificada | Cierra la sesión del token (`204`); las demás sesiones del usuario no cambian |
  | `GET /api/usuarios/yo` | Cualquier rol con sesión verificada | Perfil del usuario autenticado; para ENTRENADOR trae además el bloque `entrenador` (4B-1) |
  | `GET /api/horario-semana` | ADMINISTRADOR, TESORERO, ENTRENADOR, ACUDIENTE | Semana de lunes a sábado armada con las franjas fijas del club (4B-1; detalle abajo) |
  | `GET /api/usuarios` y `GET /api/usuarios/{id}` | ADMINISTRADOR | Lista los usuarios o consulta uno |
  | `POST /api/usuarios` | ADMINISTRADOR | Crea un usuario y lo invita por correo (invitación de Supabase) |
  | `PUT /api/usuarios/{id}/rol` | ADMINISTRADOR | Cambia el rol de un usuario |
  | `POST /api/usuarios/{id}/revocar` y `POST /api/usuarios/{id}/restablecer` | ADMINISTRADOR | Bloquea o restaura el acceso de un usuario |
  | `GET /api/categorias` | ADMINISTRADOR, ENTRENADOR | Lista las categorías en el orden oficial, con su entrenador |
  | `GET /api/health` | Público (sin token) | Estado del backend y de la base de datos (`503` si la base no responde) |
  | `GET /api/entrenadores` y `GET /api/entrenadores/{id}` | ADMINISTRADOR | Lista los entrenadores o consulta uno |
  | `PUT /api/entrenadores/{id}/categorias` | ADMINISTRADOR | Asigna las categorías de un entrenador |
  | `PATCH /api/entrenadores/{id}/estado` | ADMINISTRADOR | Activa o desactiva un entrenador (con cuenta: bloquea o restaura su acceso, regla 9) |

**Previstos:**

- `POST` de inscripción única.
- `PUT /api/horario-semana/extras/{idExcepcion}` (mismo cuerpo que el `POST` de extras; **fuera de la primera versión del 4B-2**). Avisaría con `tipoCambio` `MODIFICADO` y los valores de `anterior`.
- Grupos, asistencia, torneos, reportes y avisos: por definir en la sección 9.

## 9. Estado y plan

### 9.1 Bloques de base de datos y API ya definidos

| Bloque | Qué incluye | Responsable | Estado |
|---|---|---|---|
| 1 | Migración 007, buckets, categorías, sedes, `franja_horario` | Omar | **Hecho** (en `develop` y en la base real) |
| 2A | API de deportistas: categoría calculada al leer, sin CC y con RC, paginación, foto, posición. Incluye la migración 008 (parentesco, grupo y activo) | Omar | **Hecho** (en `develop`) |
| 4A | API del horario fijo del club (`franja_horario`, `/api/horario-club`), sin migración nueva | Omar | **Hecho** (en `develop`) |
| 5 | Alta de entrenadores con enlace para crear la contraseña, reenvío y desactivación con bloqueo de la cuenta, sin migración nueva | Omar | **Hecho** (en `develop`, PR #79) |
| 2C | **Mis jugadores (acudiente):** `GET /api/acudientes/me/deportistas` y `GET /api/acudientes/me/deportistas/{id}` (ver abajo). No depende de nada nuevo | Omar | **Hecho** (en `develop`, PR #87) |
| 4B | Entrenamientos (HU-026), redefinido: las franjas son fijas todo el año y solo se registran **excepciones por fecha** (`CANCELADO` y `EXTRA`) en una **tabla nueva de excepciones** (migración 010), sin tocar la tabla `entrenamiento`. `GET /api/horario-semana?fecha=` arma la semana con las franjas y sus excepciones. El acudiente solo ve la semana en curso. Cancelar una sesión y crear una extra: el administrador y el entrenador de la categoría. Se divide en 4B-1 y 4B-2. **El frontend ya espera este contrato** (`frontend/src/utils/semanaApi.js`): con la API real, la pantalla "Horarios" lee `/api/horario-club` y oculta cancelar y extras hasta que exista, y necesita el `idEntrenador` en la sesión (lo trae el 4B-1) para saber sobre qué categorías actúa el entrenador | Omar | Pendiente |
| 4B-1 | Sin migración: `GET /api/usuarios/yo` con el bloque `entrenador` para ENTRENADOR y `GET /api/horario-semana` **solo con franjas** (`estado` siempre `ACTIVO`, sin excepciones ni extras) | Omar | **Hecho** (en `develop`, PR #88) |
| 4B-2 | Migración 010 (tabla nueva de excepciones; `entrenamiento` no se toca): cancelaciones y extras con sus reglas, y los avisos por `CAMBIO_ENTRENAMIENTO`. Primera versión: cancelar, deshacer, crear y eliminar extra; editar un extra (`PUT`) queda para después | Omar | **Hecho** y en `develop` (PR #91) |
| 3 | Documentos: subir, listar, descargar, aprobar, rechazar y correo (`DOCUMENTO_RECHAZADO`). Sin migración. Diseño e implementación en 9.1.1 | Omar | **Hecho** en la rama `feature/bloque-3-documentos` (pendiente de merge). El JSON de n8n Cloud con `DOCUMENTO_RECHAZADO` ya está en el repositorio (`n8n/workflows/futdata-notificaciones_cloud.json`); falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento` (Omar), ver 9.5 |
| 2B | Inscripción única (la migración 008 llegó con el 2A) | Omar | Pendiente |
| 7 | **Ajustes de API pedidos por el frontend**, sin migración: (A) `GET /api/deportistas` limitado, para el ENTRENADOR, a las categorías que tiene asignadas (categoría calculada, resuelto en SQL); la ficha sigue solo para el administrador; (B) `GET /api/pagos` con `vencimientoDesde` y `vencimientoHasta`. Ver §8 | Omar | **Hecho** y en `develop` (PR #93) |

Hechos y en `develop`: 1, 2A, 2C, 4A, 4B-1, 4B-2, 5 y 7. Hecho en su rama, pendiente de merge: 3. Orden pendiente: **2B (inscripción única)**. 4A es el horario del club; 4B, los entrenamientos (HU-026).

**Fuera de alcance del bloque 7 (pendiente de revisar):** `PagoDAOImpl.categoriaDeDeportista` usa la columna heredada `deportista.id_categoria`, no la categoría calculada por edad.

**Si falta tiempo, el 2B es el primero que se recorta.** Congelamiento: viernes 6 de noviembre. Entrega: sábado 7 de noviembre.

**Bloque 2C — Mis jugadores (acudiente).**

- **Motivo:** `GET /api/deportistas` es solo para ADMINISTRADOR y ENTRENADOR; no existe ningún endpoint para que un acudiente vea a sus propios hijos, así que las pantallas acudiente/Jugadores, Ficha e Inicio no se pueden conectar.
- **Alcance:** `GET /api/acudientes/me/deportistas` (lista de sus jugadores vinculados, con foto firmada y categoría derivada) y `GET /api/acudientes/me/deportistas/{id}` (ficha de uno de sus hijos; si no es suyo, `404`, no `403`). Solo rol ACUDIENTE; el acudiente se identifica por el token, nunca por un id enviado por el frontend. Reutiliza el DAO, la categoría derivada y las URL firmadas del 2A.
- **Decisión (antes pregunta abierta):** el acudiente ve `estadoInscripcion` (en la lista y en la ficha) y `documentosFaltantes` (solo en la ficha) de **sus** hijos. El estado de cada documento y el comentario de rechazo llegan con el bloque 3.
- **Riesgo:** sin el bloque 2B no hay vínculos reales: ninguna cuenta real tiene filas en `acudiente` (con `id_usuario`) ni en `acudiente_deportista`, así que la lista sale vacía hasta que se vinculen a mano o con un script de siembra.
- **Responsable:** Omar. Hecho y en `develop` (PR #87); falta conectar el frontend (F-2C).

### 9.1.1 Bloque 3 (documentos): implementado (diseño acordado el 5 de noviembre)

**Estado:** implementado el 5 de noviembre en la rama `feature/bloque-3-documentos`, pendiente de merge. Sin migración (la tabla `documento` y el bucket `documentos` ya existen desde la 004 y la 007). La migración 011 queda solo como plan B. Piezas: `DocumentoController`, `IDocumentoService` y `DocumentoServiceImpl`, `IDocumentoDAO` y `DocumentoDAOImpl`, `ReglasDocumento`, `DocumentosProperties` y el método `acudientesDeDeportista` de `IDestinatariosDAO`. El JSON de n8n Cloud con el evento `DOCUMENTO_RECHAZADO` ya está en el repositorio (paso 6B, `n8n/workflows/futdata-notificaciones_cloud.json`); falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento` (Omar), ver 9.5. Las rutas, entradas, salidas y códigos están en §8.

**Endpoints (primera versión).** Ruta única con rol; el entrenador y el tesorero reciben `403` por rol.

| Ruta | Roles | Qué hace | Códigos |
|---|---|---|---|
| `POST /api/deportistas/{id}/documentos` | ADMINISTRADOR, ACUDIENTE dueño | Multipart con `tipo`, `archivo` y `fechaVencimiento` opcional (no pasada). Crea el documento PENDIENTE | `201`; `400` (tipo, archivo vacío, firma que no es PDF, JPG o PNG, más de 5 MB, fecha; si falla solo el archivo trae `campos: ["archivo"]`); `403`; `404` (inexistente o ajeno); `409` con `campos: ["tipo"]` (ya hay uno PENDIENTE o APROBADO de ese tipo); `503` (Storage) |
| `GET /api/deportistas/{id}/documentos` | ADMINISTRADOR, ACUDIENTE dueño | Lista con estado, comentario de rechazo, fechas, `estadoInscripcion` y `documentosFaltantes` | `200`; `404` si es ajeno |
| `GET /api/documentos/{id}/descarga` | ADMINISTRADOR, ACUDIENTE dueño | `{ url, vigenciaSegundos }` con URL firmada de 300 segundos, solo bajo demanda | `200`; `404` también si es ajeno |
| `POST /api/documentos/{id}/aprobar` | ADMINISTRADOR | Aprueba (sin comentario) y devuelve el documento y `estadoInscripcion` | `200`; `404`; `409` con `campos: ["estado"]` si no está PENDIENTE |
| `POST /api/documentos/{id}/rechazar` | ADMINISTRADOR | Body `{ comentario }` (1 a 300, recortado). Devuelve `{ documento, estadoInscripcion, avisoEnviado }` | `200`; `400` con `campos: ["comentario"]` (también sin cuerpo); `404`; `409` con `campos: ["estado"]` |

**Forma del documento en la API** (la misma que ya usa el frontend en `documentosService`): `id`, `idDeportista`, `tipo`, `nombreOriginal`, `tipoContenido`, `tamanoBytes`, `estado`, `comentario`, `fechaVencimiento`, `cargadoPor` (`Administrador` o `Acudiente`), `cargadoPorCorreo`, `cargadoEn`, `revisadoPor` (correo), `revisadoEn`. Códigos de error del frontend: `SIN_PERMISO`, `ARCHIVO_INVALIDO`, `VALIDACION`, `DOCUMENTO_NO_EXISTE`.

**Decisiones.**
1. Rama `feature/bloque-3-documentos` desde `develop`.
2. Una sola ruta con rol (no hay ruta aparte del acudiente bajo `/api/acudientes/me`).
3. Un solo documento activo (PENDIENTE o APROBADO) por tipo y deportista. Tras un RECHAZADO se puede volver a subir (queda el historial). Reemplazar un APROBADO no entra en esta versión.
4. Subida concurrente: se bloquea la fila del deportista en la transacción (`select ... for no key update`, que serializa a quienes suben sin impedir que otros flujos, como los cobros, inserten filas que referencian al deportista); sin migración.
5. Descarga en endpoint aparte, con URL firmada de 300 segundos (`DOCUMENTOS_VIGENCIA_URL_FIRMADA`) que no se registra.
6. Tamaño o tipo inválido da `400`, igual que la foto (no se usan `413` ni `415`); un `400` de solo archivo trae `campos: ["archivo"]`.
7. El correo va a todos los acudientes vinculados al deportista que tengan correo, sin duplicados.
8. Se permiten subidas sobre deportistas inactivos o con inscripción completa, salvo la regla 3.
9. El enlace del correo es `{FRONTEND_URL}/acudiente/documentos` (la pantalla ya existe en el frontend). Si `FRONTEND_URL` no está configurada, o el enlace pasa de 2000 caracteres (que n8n rechazaría), no se recorta —un enlace cortado llevaría a una página que no existe— y el aviso da `avisoEnviado: false`.
10. Aprobar no recibe comentario en esta versión.
11. El comentario de rechazo tiene máximo 300 caracteres, el mismo del frontend; el backend lo impone porque la base no lo hace. Puede traer saltos de línea.
12. Subir primero a Storage y después insertar; si el insert (o el `409` de la transacción) falla, se borra el archivo (como la foto) y se relanza la excepción original. Aprobar y rechazar con `update ... where estado = 'PENDIENTE'`; si no actualiza ninguna fila, `409` con `campos: ["estado"]`.
13. No se escribe la columna heredada `deportista.estado_inscripcion` (el cálculo sigue siendo por documentos). Ojo: `PagoDAOImpl` todavía filtra por ella (`PENDIENTE` o `ACTIVA`), así que no hay que tocarla.
14. Autorización en el servicio y falla cerrado: ADMINISTRADOR todo; ACUDIENTE solo si es dueño (si no, `404` con el mismo mensaje que un deportista inexistente); cualquier otro rol, rol vacío o usuario nulo, `403`.
15. El tipo de archivo se decide **solo por la firma de los bytes** (PDF, JPG o PNG). El `Content-Type` que declara el cliente se ignora: el navegador lo deduce de la extensión, y rechazar un JPG guardado como `.png` (algo común con fotos del celular) frustraría al acudiente sin ganar seguridad, porque Storage y la ruta usan el tipo detectado.
16. Aprobar y rechazar no envían nada más: aprobar no avisa a nadie, y rechazar solo envía el aviso `DOCUMENTO_RECHAZADO` a los acudientes (decisión 7), después del commit.

**Evento nuevo `DOCUMENTO_RECHAZADO` (asíncrono, un `POST` por acudiente, como `RECORDATORIO_PAGO`; el contrato completo está en `docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`).** Datos, planos dentro de `datos`: `correo` (máx. 254), `nombreAcudiente` y `nombreDeportista` (una línea, máx. 120), `documento` (etiqueta legible, máx. 120), `comentario` (máx. 300) y `enlaceDocumentos` (máx. 2000, sin datos secretos). Si un nombre llega vacío se manda un texto neutro en lugar de un campo vacío (n8n lo rechazaría con `400`): `Acudiente` para el acudiente y `tu deportista` para el deportista. Los nombres se mandan en una sola línea (los saltos de línea pasan a un espacio); **el comentario es el único texto que puede traer saltos de línea** (`\n`, `\r` y `\t`). Respuestas: `202` `{"aceptado":true}`, `400` sin datos personales, `401` con token inválido. El aviso se envía después del commit; `avisoEnviado` es `true`, `false` o `null` (sin destinatarios) y un aviso fallido nunca deshace el rechazo ni cambia el `200`. Si una llamada falla, no se envían las demás. No se registran correos, nombres, comentarios ni enlaces en los logs: solo el evento, el tipo de excepción y conteos. Hasta que el flujo esté publicado en n8n Cloud (6B), n8n responde `400` "Evento no soportado" y `avisoEnviado` sale `false`.

**Fuera de esta versión:** reemplazar, borrar, vencimientos con avisos, historial completo e índice único (migración 011).

**Limitaciones conocidas:** no se puede reemplazar un documento ya APROBADO (si el administrador aprueba uno por error, hoy no hay forma de corregirlo desde la API); un PDF se valida por su cabecera y no se revisa si es válido ni libre de virus; el aviso se hace en la misma petición, así que puede tardar hasta 5 s por acudiente si n8n se demora (y si falla uno no se envían los demás); si el borrado del archivo de compensación también falla, queda un archivo huérfano en el bucket (solo se registra el tipo de excepción).

**Cómo se construyó** (seis pasos, con diff y OK entre cada uno): 1) dominio y configuración (`ReglasDocumento`, `DocumentosProperties`, entidad y DTO); 2) DAO con pruebas sobre PostgreSQL; 3) servicio y controlador de subir, listar y descargar; 4) aprobar y rechazar; 5) aviso por `DOCUMENTO_RECHAZADO`; 6) documentación y contrato (6A, hecho) y flujo de n8n Cloud con su plantilla y script de prueba (6B, en el repositorio; falta importarlo, publicarlo y probarlo).

**Deuda de n8n que arregla el 6B:** el nodo `¿Responder 502?` del flujo de Cloud solo excluía `CAMBIO_ENTRENAMIENTO` y `CODIGO_2FA`; en el JSON del repositorio ya excluye también `RECORDATORIO_PAGO` (fallo latente) y `DOCUMENTO_RECHAZADO` (surte efecto cuando se publique en n8n Cloud). `futdata-notificaciones.json` (el local) no tiene `RECORDATORIO_PAGO` ni `DOCUMENTO_RECHAZADO` y no se toca; el contrato `contrato-webhook-notificaciones.md` ya los lista.

### 9.2 Módulos nuevos para la entrega final

Estos módulos están en el menú de los cuatro roles y hoy son maquetas. Ninguno estaba en el plan anterior.

| Bloque | Módulo | Quién lo usa | Responsable |
|---|---|---|---|
| 6 | **Pagos con Wompi** (cobro, pago en línea, webhook, comprobante PDF, estado de cuenta) | acudiente, tesorero, administrador | Felipe (backend y pantallas). **Backend fusionado** en `develop` (PR #72: migración 009, endpoints, webhook con firma y conciliación) y **frontend fusionado** (PR #74). Pendiente: llaves y cuenta de pruebas (sandbox) de Wompi, y la prueba de punta a punta. Prioridad alta: el profesor insistió en este punto |
| 7 | Grupos (de entrenamiento) y asistencia | administrador, entrenador | Por definir |
| 8 | Torneos | administrador, entrenador | Por definir |
| 9 | Reportes y avisos / notificaciones | tesorero, administrador, acudiente | Por definir |
| 10 | Dashboard, pantallas de inicio por rol, respaldo, usuarios y roles | todos | Por definir |

Cada módulo "por definir" se reparte entre Omar y Felipe; la tabla se completa en cuanto se acuerde. Migraciones nuevas desde la 011 (la 010, `excepcion_horario`, es del 4B-2 y ya está aplicada en la base del equipo).

### 9.3 Frontend (Felipe)

| Bloque | Qué hace el frontend | Estado |
|---|---|---|
| F-seg | Protección de rutas: sin sesión verificada no se entra; el rol sale de la sesión | Fusionado (PR #68) |
| F0 | `apiClient` con subida de archivos y paginación; interruptor de API real por servicio (como `entrenadoresService`); parentescos alineados; quitar la fecha fija del 22 de octubre | Fusionado (PR #70) |
| F-6 | Pagos conectados a la API | Fusionado (PR #74) |
| F-5 | Crear la contraseña desde el enlace (PR #75); usuarios conectados a la API y enlaces de invitación (PR #80); los enlaces del correo van a `/restablecer` (PR #81) | Fusionado |
| F-4 | Horarios unificados, conectados a `/api/horario-club` (cancelar y extras quedan ocultos con la API real hasta el 4B) | Fusionado (PR #77) |
| F-2A | Deportistas conectados a la API | Fusionado (PR #78) |
| F-2C, F-4B, F-3, F-2B | Conectar cada pantalla a su bloque cuando Omar entregue los endpoints | Pendiente |

### 9.4 Cuándo se considera terminado un módulo

1. Los datos se guardan en la base real y sobreviven a recargar la página.
2. Solo los roles correspondientes pueden verlo y usarlo (comprobado con una cuenta de cada rol).
3. El flujo completo se probó de principio a fin, con sus errores comunes (validaciones, 401, 403, 409).
4. Hay pruebas automáticas y la documentación de la API está actualizada.

### 9.5 Pendientes de información

- Llaves y cuenta de pruebas (sandbox) de Wompi.
- n8n corre en n8n Cloud (workflow publicado, *Header Auth* con la cabecera `X-FutData-Token`, remitente fijo), probado con el backend en local. **Pendiente:** actualizar en Render `N8N_BASE_URL`, `N8N_WEBHOOK_TOKEN` y `FRONTEND_URL` y repetir las pruebas de 2FA y alta de entrenador contra el backend desplegado.
- **Deuda (n8n Cloud):** el JSON para Cloud se agregará al repositorio junto al actual (sin `$env` y sin el nodo **Validar token**). Con *Header Auth*, una petición sin token recibe `403` y no `401`, y `N8nClient` solo reconoce el `401` como "token mal configurado" (un `403` lo trata como código inesperado).
- Confirmar el reparto de los bloques 7 a 10.
- **Bloque 3, flujo de n8n (Omar):** el JSON de Cloud con `DOCUMENTO_RECHAZADO` ya está en el repositorio (`n8n/workflows/futdata-notificaciones_cloud.json`, con su plantilla, el script `probar-documento` y el arreglo de `¿Responder 502?`); **falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento`**. Hasta entonces el rechazo de un documento funciona, pero `avisoEnviado` sale `false` y el acudiente no recibe el correo.
- `futdata-notificaciones.json` (el flujo local de n8n) no tiene `RECORDATORIO_PAGO` ni `DOCUMENTO_RECHAZADO`; decisión: no se toca (solo se mantiene el JSON de Cloud).
- La columna heredada `deportista.estado_inscripcion` todavía la lee `PagoDAOImpl` (solo para filtrar cobros, `PENDIENTE` o `ACTIVA`); el bloque 3 no la escribe, porque la inscripción se calcula por los documentos.
- Una prueba con PostgreSQL falla de forma intermitente en Windows por agotamiento de puertos locales (`BindException: Address already in use`): `PostgresDePrueba` usa un `DriverManagerDataSource` sin pool, que abre una conexión por consulta. Ya cayó 3 veces en corridas completas (`HorarioSemanaConPostgresTest` 1 vez y `DocumentoServiceConPostgresTest` 2) y cada clase pasa sola; repetir la corrida completa lo resuelve. Se intentó un pool Hikari en `PostgresDePrueba` y se descartó: destapa dos acoplamientos en pruebas existentes (`CodigoVerificacionServiceImplConPostgresTest` expone el `DataSource` como `@Bean` y Spring cierra el pool; `MigracionPagosWompiTest` deja una conexión con la transacción abortada). Retomar después del congelamiento arreglando esas dos pruebas.
- Pasos de despliegue del bloque 5, **pendientes de confirmar** que se hicieron:
  - Workflow publicado en n8n Cloud, con `ENLACE_CONTRASENA` y el `CODIGO_2FA` asíncrono.
  - Ajustes de Supabase Auth: vencimiento del enlace de 1 hora y una política de contraseña que no sea más estricta que la nuestra (ver `docs/GUIA-SUPABASE-EQUIPO.md`).
  - Revisar en los logs de Render que las peticiones lleguen con `CF-Connecting-IP`; si no llega, el límite por IP se vuelve uno solo para todos.

## 10. Convenciones

- Una rama por bloque desde `develop`; PR a `develop`, el otro lo revisa.
- Toda migración se prueba primero en un proyecto de prueba de Supabase y se aplica en la real con backup. Una migración ya aplicada no se edita: se crea una nueva. Hoy existen de la 001 a la 010; las nuevas van desde la 011.
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
| 2026-11-03 | Versión 3, tras auditar el documento contra `develop`. **Fusionados en `develop`:** 2A (`a751aa9`), 4A (`fce17f3`), 5 (PR #79) y 6, pagos con Wompi (PR #72). **El backend de Wompi lo hizo Felipe**, no Omar (se corrigen §2 y §9.2). Frontend fusionado: PR #68 (protección de rutas), #70 (base de la API), #74 (pagos), #75 (crear contraseña), #77 (horarios), #78 (deportistas), #80 (usuarios) y #81 (enlaces del correo). §8 lista todas las rutas reales, con pagos ya implementado. §5 agrega la regla 13 (pagos) y aclara las reglas 4 y 8. Nuevo orden pendiente: 2C → 4B-1 → 4B-2 → 3 → 2B; el 2B es el primero que se recorta. Congelamiento el viernes 6. **Corrección de las migraciones:** la 009 ya está usada (pagos); las nuevas van desde la 010, reservada para el 4B-2. Despliegue: solo se deja lo verificado en el repositorio; dónde corre n8n en producción queda por confirmar. |
| 2026-11-03 | Cobros por ciclo de ingreso (rama `feature/cobros-por-ciclo`, sin migración): regla 14, `POST /api/pagos/generar-ciclos` (administrador por JWT o `X-Cron-Token` con `N8N_CRON_TOKEN`, opcional), evento `RECORDATORIO_PAGO` para n8n y variables `FUTDATA_COBROS_*`. `generar-mensualidades` queda obsoleto. |
| 2026-11-03 | Bloque 2C (rama `feature/bloque-2c-mis-jugadores`; ya en `develop`, PR #87), sin migración nueva. `GET /api/acudientes/me/deportistas` y `GET /api/acudientes/me/deportistas/{id}`, solo rol ACUDIENTE: el acudiente sale del token y la propiedad se decide en el SQL (`acudiente_deportista` + `acudiente.id_usuario`); un deportista ajeno o inexistente da `404` con el mismo mensaje. Lista sin documento, teléfono ni dirección; ficha con ellos y `documentosFaltantes`. Se resuelve la pregunta abierta: el acudiente ve `estadoInscripcion` y `documentosFaltantes` de sus hijos. Sin la sesión 2FA verificada la respuesta es `403` con `2FA_REQUERIDA`. Riesgo: sin el 2B no hay vínculos reales. Pruebas: **1123, 0 fallos, 9 omitidas** con `./mvnw clean test`, ya con los cobros por ciclo (1092) y los +31 de este bloque (6 de servicio, 8 sobre PostgreSQL y 17 del controlador). |
| 2026-11-04 | Bloque 4B-1 (rama `feature/bloque-4b1-semana`; ya en `develop`, PR #88), sin migración nueva. `GET /api/usuarios/yo` con el bloque `entrenador` (`null` salvo ENTRENADOR con fila vinculada) y `GET /api/horario-semana?fecha=` solo con franjas, para los cuatro roles. Decisión: el domingo cuenta la semana siguiente (igual que el frontend), también para el chequeo del acudiente, que solo ve la semana en curso (`403` "Solo puedes consultar la semana en curso"). `puedeGestionar` siempre `false` hasta el 4B-2. Pruebas: **1173, 0 fallos, 9 omitidas** con `./mvnw clean test` (+50 sobre las 1123 del 2C). |
| 2026-11-04 | Bloque 4B-2 (rama `feature/bloque-4b2-excepciones`, pendiente de merge). **Migración 010** (`excepcion_horario`, ya aplicada en la base del equipo). `GET /api/horario-semana` ahora con franjas `CANCELADO` y sesiones `EXTRA`, y `puedeGestionar` real (`false` en fechas pasadas, también para el administrador). `POST` y `DELETE` de cancelaciones y de extras, para ADMINISTRADOR y ENTRENADOR dueño (cancelar: de la categoría o de la franja; extras: solo de la categoría): `{ sesion, avisoEnviado }` con `201` en los `POST` y `200` en los `DELETE`. Fecha pasada = antes de hoy; el cruce de un extra con las franjas se comprueba en la misma transacción que el insert, bloqueando la fila del entrenador, y con otros extras lo decide la base. Avisos `CAMBIO_ENTRENAMIENTO` a todos los acudientes de la categoría, después del commit, en bloques de 200 (`avisoEnviado` `true`, `false` o `null`; un fallo del aviso no deshace la operación). Fuera de esta versión: el `PUT` de extras. Limitaciones: ver §8. Pruebas: **1368, 0 fallos, 0 errores, 9 omitidas** con `./mvnw clean test` (+195 sobre las 1173 del 4B-1). |
| 2026-11-04 | Bloque 7 (rama `feature/bloque-7-ajustes-api`, pendiente de merge), sin migración. (A) `GET /api/deportistas` para el ENTRENADOR solo devuelve a los deportistas de las categorías que tiene asignadas: la categoría calculada por edad y el dueño se resuelven en un solo `EXISTS` en SQL, compartido por la lista y el total; `categoria` acepta valores repetidos o con comas y se cruza con las suyas (una ajena da `200` vacío, un nombre inexistente `400`); sin categorías, lista vacía; el servicio falla cerrado ante cualquier rol que no sea ADMINISTRADOR ni ENTRENADOR (`SinPermisoException`, `403`). La ficha sigue solo para el administrador. (B) `GET /api/pagos` con `vencimientoDesde` y `vencimientoHasta` (`AAAA-MM-DD`, inclusivos, sobre `fecha_vencimiento`, `NOT NULL`), con `400` de formato (todos los errores juntos) y de desde > hasta. Pruebas: **1393, 0 fallos, 0 errores, 9 omitidas** con `./mvnw clean test` (+25 sobre las 1368 del 4B-2). |
| 2026-11-05 | Estado real: el **4B-2** (PR #91) y el **bloque 7** (PR #93) ya están en `develop`, junto con el 2C y el 4B-1; se corrigen las menciones de "pendiente de merge". Probados en producción: el 4B-2 y, del bloque 7, lo del administrador y el acudiente; **falta probar** el filtro del entrenador (Sub-11 y Sub-12) con una cuenta de entrenador. El cron de cobros en n8n Cloud está activo (6:00 a. m.). **Bloque 3 (documentos): diseño acordado** (9.1.1): 5 endpoints, un documento activo por tipo, descarga con URL firmada de 5 minutos, aviso `DOCUMENTO_RECHAZADO` y arreglo del nodo `¿Responder 502?`. Sin migración. |
| 2026-11-05 | **Bloque 3 (documentos) implementado** en la rama `feature/bloque-3-documentos` (pendiente de merge), **sin migración** (9.1.1 y §8). Cinco endpoints: `POST` y `GET /api/deportistas/{id}/documentos`, `GET /api/documentos/{id}/descarga`, `POST /api/documentos/{id}/aprobar` y `POST /api/documentos/{id}/rechazar`. Solo ADMINISTRADOR y ACUDIENTE dueño ven y suben (un deportista o documento ajeno da `404` igual que uno inexistente; entrenador y tesorero, `403`); solo el ADMINISTRADOR aprueba o rechaza. **Un solo documento activo (PENDIENTE o APROBADO) por tipo y deportista**, garantizado bloqueando la fila del deportista (`select ... for no key update`) en una transacción corta, sin migración; tras un rechazo se puede volver a subir y queda el historial. El tipo de archivo (PDF, JPG o PNG, hasta 5 MB) se decide solo por la firma de los bytes. **Storage se sube fuera de la transacción y se compensa**: si falla lo que viene después, se borra el archivo y se relanza la excepción original. La descarga entrega una URL firmada de **300 segundos**, solo bajo demanda y sin registrarla. `estadoInscripcion` y `documentosFaltantes` se recalculan solos al aprobar o rechazar; la columna heredada `deportista.estado_inscripcion` no se escribe. Aprobar y rechazar usan `update ... where estado = 'PENDIENTE'`, con `409` (`campos: ["estado"]`) si ya fue revisado. **Aviso `DOCUMENTO_RECHAZADO` después del commit**, un correo por acudiente con correo válido, con `avisoEnviado` `true`, `false` o `null`: un aviso fallido nunca deshace el rechazo (por ahora `false` hasta publicar el flujo de n8n, 6B). Variables nuevas `DOCUMENTOS_BUCKET` y `DOCUMENTOS_VIGENCIA_URL_FIRMADA`. Pruebas: **1664, 0 fallos, 0 errores, 9 omitidas** con `./mvnw clean test`. **Paso 6B (n8n):** el JSON de n8n Cloud del repositorio (`n8n/workflows/futdata-notificaciones_cloud.json`) ya tiene el evento `DOCUMENTO_RECHAZADO` (regla del Switch `Evento`, validación con el comentario de hasta 300 caracteres que admite saltos de línea, respuestas `202` y `400` y envío del correo), la plantilla `n8n/plantillas/documento-rechazado.html` y `.txt`, el script `n8n/scripts/probar-documento.sh` y `.ps1`, y el arreglo del nodo `¿Responder 502?` (ahora también excluye `RECORDATORIO_PAGO` y `DOCUMENTO_RECHAZADO`); el flujo local `futdata-notificaciones.json` no se toca. **Falta importarlo y publicarlo en n8n Cloud y probarlo con `probar-documento` (Omar):** no está publicado ni probado, y mientras tanto `avisoEnviado` sale `false`. |
