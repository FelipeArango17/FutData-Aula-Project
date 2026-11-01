# FutData — Presentación técnica

Sistema de gestión deportiva para la Asociación Deportiva Prado Fútbol Club (PPI, sexto semestre).

> **Esta es la fuente de verdad del proyecto.** El documento PPI ya no se modifica: lo que se decida desde el 1 de noviembre de 2026 se registra aquí. Se actualiza con cada cambio (ver el registro de cambios al final).
> Última actualización: 1 de noviembre de 2026.

## 1. Qué es FutData

Una aplicación para administrar un club de fútbol formativo: inscripción de deportistas y acudientes, documentos, categorías, entrenadores, horario de entrenamientos, mensualidades y comprobantes de pago.

Roles: **administrador**, **entrenador** y **acudiente**. El ingreso usa verificación en dos pasos por correo (2FA, flujo en n8n).

## 2. Equipo y herramientas

| Quién | Responsabilidad |
|---|---|
| Omar Támara | Backend completo (Spring Boot, base de datos, n8n) |
| Felipe Arango | Frontend |
| Juan Sebastián | Supabase (apoyo) |

Trabajo distribuido en Azure DevOps. Código en GitHub (`FelipeArango17/FutData-Aula-Project`), ramas desde `develop`, un PR por bloque.

## 3. Arquitectura y stack

- **Backend:** Spring Boot 3.5, Java 21, Maven wrapper (`mvnw`), capas controlador, servicio y DAO con JDBC.
- **Base de datos:** PostgreSQL en Supabase. Migraciones numeradas en `supabase/migrations/` (hoy 001 a 007).
- **Autenticación:** Supabase Auth, con 2FA por correo.
- **Archivos:** Supabase Storage con buckets privados (`fotos`, `documentos`, y el de comprobantes). Solo el backend entra con la llave `service_role` y entrega URL firmadas.
- **Correos y automatización:** n8n (código 2FA, enlace de contraseña, aviso de documento rechazado).
- **Frontend:** React.
- **Pruebas:** JUnit con Testcontainers (PostgreSQL 17 en Docker). Estado actual: **473 pruebas, 0 fallos, 6 omitidas** (las que necesitan n8n y Supabase reales).

## 4. Modelo de datos (resumen)

Detalle completo en `docs/info-sprints/sprint-1/diccionario-datos.md`.

- **categoria:** 9 categorías por **año de nacimiento** (año actual − año de nacimiento).

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
- **deportista:** tipo de documento TI, RC, CE o PASAPORTE (sin CC). El número de documento es único. Tiene `posicion` (opcional) y `foto`.
- **acudiente** y **acudiente_deportista:** relación N:M (un acudiente puede tener varios jugadores). Pendiente: columna `parentesco` (migración 008).
- **entrenador:** puede tener varias categorías; cada categoría con responsable tiene uno solo.
- **entrenamiento:** `lugar` es una de las dos sedes. Hay bloqueo de cruce por categoría y por entrenador (no por lugar).
- **franja_horario:** horario fijo del club, lunes a sábado, con sede, entrenador opcional y grupo opcional.
- **documento:** 7 tipos, estados PENDIENTE, APROBADO y RECHAZADO, con `cargado_por`, `revisado_por` y comentario al rechazar.

## 5. Reglas de negocio vigentes

1. **Categoría automática** desde la fecha de nacimiento (regla de año). Se rechaza a quien quede fuera de 4–16.
2. **Documentos del deportista:** TI, RC, CE y PASAPORTE.
3. **Sedes:** Horizontes y UVA El Paraíso. La sede va **por franja**, no por categoría.
4. **Horario:** lunes a sábado. Varias categorías pueden coincidir en hora y sede. Un mismo entrenador no puede tener dos franjas ni dos entrenamientos que se solapen. No hay entrenamientos en fechas pasadas.
5. **Grupo mañana/tarde** (Cachorros y Semillero, martes y jueves): la franja lo guarda; **el grupo de cada deportista solo vive en el front**. El backend no asigna grupos.
6. **Por turnos:** Párvulos, Cachorros y Semillero no tienen entrenador fijo (el club rota quién dirige).
7. **Documentos de la inscripción (7 tipos):**
   - Obligatorios: `DOCUMENTO_IDENTIDAD`, `CERTIFICADO_ESTUDIO`, `FORMULARIO_INSCRIPCION`, `AUTORIZACION_HABEAS_DATA`.
   - Si aplica: `PAZ_Y_SALVO`, `CARTA_RENUNCIA`, `TRASPASO_NACIONAL`.
   - La "foto tipo documento" es la **foto del deportista**, no un tipo de documento. La ficha médica ya no existe.
   - Archivos PDF, JPG o PNG, máximo 5 MB, validados por su contenido real (no por la extensión).
8. **Validación de documentos:** el administrador revisa. Mientras no los haya validado, **la inscripción queda pendiente**. Al rechazar un documento el comentario es obligatorio y se envía por correo al acudiente (n8n).
9. **Alta de entrenador y de acudiente:** sin contraseña temporal. Se envía un enlace para que cada persona cree su propia contraseña.

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

**Inscripción (administrador, 4 pasos: Jugador, Acudiente, Documentos, Confirmar).** Un solo `POST` transaccional que calcula la categoría, crea el deportista, crea o reutiliza al acudiente por correo (con su parentesco), sube la foto y los documentos a Storage, y envía al acudiente el enlace para crear su contraseña. La inscripción queda **pendiente** hasta que el administrador valide los documentos. Si algo falla a mitad, devuelve un error claro (`INSCRIPCION_PARCIAL`).

**Alta de entrenador.** El administrador lo crea; se genera el usuario con rol ENTRENADOR sin contraseña y n8n envía el enlace. Si el enlace vence, el administrador puede reenviarlo.

**Revisión de documentos.** El acudiente o el administrador suben. Solo el administrador aprueba o rechaza. Al rechazar va un comentario y n8n avisa al acudiente.

## 8. API

Los endpoints existentes están en `backend/README.md` y en la colección Postman (HU-057, pendiente). Endpoints previstos:

- `GET/POST/PUT/DELETE /api/horario-club` (filtros `categoria`, `entrenador`, `sede`; 409 si el entrenador se cruza).
- `GET /api/deportistas` paginado (`page`, `size`, `sort`, total; filtro `activo`).
- `POST/DELETE /api/deportistas/{id}/foto`.
- Documentos: subir, aprobar y rechazar.
- `POST` de inscripción única.
- Alta de entrenador con enlace y reenvío.

## 9. Estado y plan

| Bloque | Qué incluye | Estado |
|---|---|---|
| 1 | Migración 007, buckets, categorías, sedes, `franja_horario` | **Hecho** (en `develop` y en la base real) |
| 2A | API de deportistas: categoría automática, sin CC, paginación, foto | Pendiente |
| 4 | Horario del club y entrenamientos (HU-026) | Pendiente |
| 5 | Alta de entrenadores con enlace | Pendiente |
| 3 | Documentos: subir, aprobar, rechazar, correo | Pendiente |
| 2B | Migración 008 (parentesco) e inscripción única | Pendiente |

Orden: 2A, 4, 5, 3, 2B (el 2B reutiliza lo que dejan el 3 y el 5). Backend: Omar. Frontend: Felipe.

Parentesco permitido (migración 008): `Madre`, `Padre`, `Abuelo`, `Tia`, `Tio`, `Otro`.

## 10. Convenciones

- Una rama por bloque desde `develop`; PR a `develop`, el otro lo revisa.
- Toda migración se prueba primero en un proyecto de prueba de Supabase y se aplica en la real con backup. Una migración ya aplicada no se edita: se crea una nueva.
- Las pruebas corren con `.\mvnw.cmd test` desde `backend\` (Docker abierto) y no tocan la base real.
- Cada bloque termina con este archivo actualizado.

## 11. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-11-01 | Se crea el documento. Bloque 1 (migración 007) en `develop` y aplicada en Supabase. 9 categorías, 7 tipos de documento, 30 franjas. |
| 2026-11-01 | Se congela el PPI; este archivo pasa a ser la fuente de verdad. |
| 2026-11-01 | Decisiones: la inscripción queda pendiente hasta que el administrador valide los documentos; parentesco = Madre, Padre, Abuelo, Tia, Tio, Otro; el backend es de Omar y el frontend de Felipe. |
