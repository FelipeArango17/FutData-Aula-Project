# Guía de Supabase para el equipo

Esta guía explica cómo configurar el proyecto FutData con Supabase, conectar el backend y permitir que otros integrantes trabajen con la misma base de datos.

## 1. Requisitos de seguridad

- No subir `backend/.env` al repositorio.
- No compartir por chat ni confirmar en Git:
  - La contraseña de la base de datos.
  - La `service_role` key.
  - Tokens de n8n.
  - JWT secrets.
- La `anon`/publishable key puede usarse en aplicaciones cliente, pero debe estar protegida por RLS y políticas correctas.
- El backend debe conectarse a PostgreSQL mediante variables de entorno.
- Si una contraseña fue expuesta, debe restablecerse en Supabase antes de continuar.

## 2. Acceso al proyecto Supabase

El propietario del proyecto debe invitar a Omar y Felipe desde:

**Supabase Dashboard → Project Settings → Access Control / Team**

El nombre exacto puede variar según la versión del dashboard. También se puede acceder desde el selector de organización:

1. Abrir la organización que contiene `futdata`.
2. Entrar a la configuración de la organización.
3. Abrir **Members**.
4. Invitar los correos o usuarios de GitHub de Omar y Felipe.
5. Asignar un rol adecuado.

Roles recomendados:

- **Developer:** trabajar con el proyecto, SQL Editor y configuración de desarrollo.
- **Read-only:** consultar estructura y datos sin modificarla.
- **Owner/Admin:** reservarlo para quienes administren facturación, miembros y configuración sensible.

Para este proyecto, Omar y Felipe deberían recibir acceso de desarrollo, no acceso de propietario, salvo que el equipo acuerde lo contrario.

El acceso al dashboard no sustituye el acceso al repositorio GitHub. Cada integrante necesita:

1. Acceso al repositorio `FelipeArango17/FutData-Aula-Project`.
2. Acceso al proyecto Supabase `futdata`.
3. Su propio archivo local `backend/.env`.

## 3. Obtener los datos del proyecto

En Supabase:

### Project URL

Abrir **Project Settings → API** y copiar la URL del proyecto:

```text
https://TU_PROJECT_REF.supabase.co
```

`TU_PROJECT_REF` es el identificador del proyecto, visible también en la URL del dashboard.

### JWKS URL

Para la futura validación de JWT de HU-001:

```text
https://TU_PROJECT_REF.supabase.co/auth/v1/.well-known/jwks.json
```

Estas variables están preparadas en `.env`, aunque el backend base todavía no valida JWT.

### Connection string

1. Hacer clic en **Connect** en el dashboard.
2. Seleccionar la conexión de base de datos.
3. Elegir **Session pooler**.
4. Usar el puerto `5432`.
5. Copiar el host y el usuario mostrados por Supabase.

El proyecto usa la conexión JDBC de esta forma:

```properties
DB_URL=jdbc:postgresql://HOST_DEL_SESSION_POOLER:5432/postgres?sslmode=require
DB_USER=postgres.TU_PROJECT_REF
DB_PASSWORD=CONTRASEÑA_LOCAL_NO_COMPARTIR
```

No se debe pegar la contraseña dentro de `DB_URL`; Spring recibe cada valor por separado.

La conexión directa puede usar IPv6. Para evitar problemas en redes IPv4, se recomienda el **Session pooler**. Si se usa el pooler transaccional del puerto `6543`, debe agregarse `&prepareThreshold=0` a la URL JDBC.

## 4. Configurar el archivo `.env`

Desde la carpeta `backend`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Cada integrante debe completar su copia local con los valores del proyecto:

```properties
DB_URL=jdbc:postgresql://HOST_DEL_SESSION_POOLER:5432/postgres?sslmode=require
DB_USER=postgres.TU_PROJECT_REF
DB_PASSWORD=SU_CONTRASEÑA_DE_BASE_DE_DATOS

# En local y producción, separados por coma si hay más de un frontend.
CORS_ORIGINS=http://localhost:5173,https://TU_DOMINIO.vercel.app

SUPABASE_URL=https://TU_PROJECT_REF.supabase.co

N8N_BASE_URL=http://localhost:5678
N8N_WEBHOOK_TOKEN=TOKEN_LOCAL_DE_N8N
```

Los valores de n8n deben coincidir con la configuración local de `n8n/.env`. Si todavía no se probarán notificaciones, pueden quedar vacíos en el perfil local.

El archivo `backend/.env` está ignorado por Git. Verificarlo con:

```powershell
git check-ignore -v backend/.env
```

## 5. Configurar Authentication

En Supabase:

1. Abrir **Authentication → Sign In / Providers**.
2. Activar **Email**.
3. Configurar la confirmación de correo según el entorno.
4. Abrir **Authentication → URL Configuration**.
5. Para desarrollo local usar:

```text
Site URL: http://localhost:5173
Redirect URL: http://localhost:5173/**
```

La contraseña de los usuarios se administra exclusivamente en Supabase Auth. No existe una columna de contraseña en `public.usuario`.

## 6. Ejecutar el esquema PostgreSQL

La migración reproducible está en:

[001_schema_inicial.sql](../supabase/migrations/001_schema_inicial.sql)

Para ejecutarla desde el dashboard:

1. Abrir **SQL Editor**.
2. Crear una consulta nueva.
3. Copiar todo el contenido de `001_schema_inicial.sql`.
4. Pegar el contenido en el editor.
5. Ejecutar una sola vez con **Run**.

La migración crea:

- `usuario`
- `rol`
- `usuario_rol`
- `entrenador`
- `categoria`
- `deportista`
- `acudiente`
- `torneo`
- `pago`
- `inscripcion_torneo`
- `entrenamiento`
- `asistencia`
- `notificacion`
- `notificacion_pago`
- `reporte_financiero`

También crea UUID automáticos, claves foráneas, índices, restricciones `CHECK`, restricciones `UNIQUE` y activa RLS.

No se debe ejecutar repetidamente sobre un proyecto que ya tiene las tablas. Para cambios posteriores, crear una migración nueva con numeración siguiente.

### 6.1 Migraciones siguientes

Después de la 001 se ejecutan, una sola vez cada una y en orden, las demás de `supabase/migrations/`:

1. [002_modelo_datos.sql](../supabase/migrations/002_modelo_datos.sql): `acudiente_deportista`, asistencia con tres estados y `usuario.apellido`.
2. [003_codigo_verificacion_2fa.sql](../supabase/migrations/003_codigo_verificacion_2fa.sql): códigos del 2FA.
3. [004_ajustes_modelo.sql](../supabase/migrations/004_ajustes_modelo.sql): categorías Sub-8 a Sub-16, `documento`, `venta`, pagos con periodo, vencimiento y comprobante, entrenamientos sin cruces, equipos de confianza, sesiones verificadas y auditoría. El detalle y los pasos (incluida la copia de seguridad previa) están en la sección 6 del [diccionario de datos](info-sprints/sprint-1/diccionario-datos.md).

La 004 va en una transacción: si falla, no queda ningún cambio y el mensaje dice qué dato corregir.

## 7. Verificar las tablas

En SQL Editor:

```sql
select table_name
from information_schema.tables
where table_schema = 'public'
order by table_name;
```

Verificar los roles:

```sql
select id_rol, nombre
from public.rol
order by nombre;
```

Deben existir:

```text
ACUDIENTE
ADMINISTRADOR
ENTRENADOR
TESORERO
```

## 8. Crear un usuario administrador

Primero, en **Authentication → Users → Add user**:

1. Crear el correo del administrador.
2. Definir una contraseña fuerte.
3. Activar **Auto Confirm User** para desarrollo, si el equipo lo necesita.
4. Copiar el UID generado por Supabase Auth.

Después, en SQL Editor, reemplazar los valores de ejemplo:

```sql
begin;

insert into public.usuario (id_usuario, nombre, correo)
values (
    'UUID_REAL_DE_AUTH_USERS',
    'Administrador Principal',
    'admin@pradofc.co'
)
on conflict (id_usuario) do nothing;

insert into public.usuario_rol (id_usuario, id_rol)
select
    'UUID_REAL_DE_AUTH_USERS',
    id_rol
from public.rol
where nombre = 'ADMINISTRADOR'
on conflict (id_usuario, id_rol) do nothing;

commit;
```

Verificar:

```sql
select
    u.id_usuario,
    u.nombre,
    u.correo,
    u.activo,
    r.nombre as rol
from public.usuario u
join public.usuario_rol ur on ur.id_usuario = u.id_usuario
join public.rol r on r.id_rol = ur.id_rol
where u.correo = 'admin@pradofc.co';
```

El UUID debe coincidir con `auth.users.id`. No inventar un UUID para este registro.

## 9. Levantar el backend

Desde `backend`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Probar el estado:

```text
http://localhost:8080/api/health
```

El backend base puede responder `503` si la base no está disponible. Con las variables correctas y Supabase accesible debe reportar la conexión de base de datos disponible.

## 10. Cómo trabaja el equipo con la misma base

Todos deben conectarse al mismo proyecto Supabase usando el mismo host y base de datos, pero cada integrante debe guardar sus credenciales en su propio `backend/.env`.

Flujo recomendado:

1. Una persona crea una migración SQL en `supabase/migrations/`.
2. La migración se revisa mediante Pull Request.
3. Después de aprobarla, se ejecuta en Supabase.
4. Los demás actualizan `develop`.
5. Los demás verifican la estructura con las consultas de la sección 7.

No crear tablas manualmente desde **New table** si la estructura debe quedar reproducible en Git.

## 11. Estado actual y pendientes

Ya está preparado:

- Proyecto Supabase.
- Conexión PostgreSQL por Session pooler.
- Configuración local del backend mediante `.env`.
- Esquema inicial PostgreSQL.
- Roles iniciales.
- Usuario administrador de desarrollo.
- RLS activado.

Todavía requiere trabajo de las siguientes historias:

- HU-001: validación JWT con Spring Security y JWKS.
- HU-002: tablas y reglas de 2FA.
- HU-005: autorización por roles.
- Políticas RLS específicas, cuando se defina si las consultas serán solo mediante backend o también desde el frontend.
