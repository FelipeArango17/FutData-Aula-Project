-- Esquema inicial de FutData para PostgreSQL/Supabase.
-- Ejecutar con el SQL Editor usando una cuenta administrativa del proyecto.

create extension if not exists pgcrypto;

create table public.usuario (
    id_usuario uuid primary key references auth.users (id) on delete restrict,
    nombre varchar(100) not null,
    correo varchar(320) not null unique,
    activo boolean not null default true,
    creado_en timestamptz not null default now()
);

create table public.rol (
    id_rol uuid primary key default gen_random_uuid(),
    nombre varchar(30) not null unique,
    constraint rol_nombre_ck check (nombre in (
        'ADMINISTRADOR', 'TESORERO', 'ENTRENADOR', 'ACUDIENTE'
    ))
);

create table public.usuario_rol (
    id_usuario_rol uuid primary key default gen_random_uuid(),
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    id_rol uuid not null references public.rol (id_rol) on delete restrict,
    unique (id_usuario, id_rol)
);

create table public.entrenador (
    id_entrenador uuid primary key default gen_random_uuid(),
    nombre varchar(100) not null,
    apellido varchar(100) not null,
    tipo_documento varchar(20) not null,
    documento_identidad varchar(40) not null,
    fecha_nacimiento date not null,
    telefono varchar(30),
    correo varchar(320),
    direccion varchar(250),
    horario_trabajo varchar(250),
    especialidad varchar(100),
    documentos varchar(500),
    id_usuario uuid unique references public.usuario (id_usuario) on delete set null,
    constraint entrenador_tipo_documento_ck check (
        tipo_documento in ('CC', 'TI', 'CE', 'PASAPORTE')
    ),
    unique (tipo_documento, documento_identidad),
    unique (correo)
);

create table public.categoria (
    id_categoria uuid primary key default gen_random_uuid(),
    nombre varchar(30) not null unique,
    edad_minima integer not null,
    edad_maxima integer not null,
    id_entrenador uuid not null references public.entrenador (id_entrenador) on delete restrict,
    constraint categoria_nombre_rango_ck check (
        (nombre = 'Sub-8' and edad_minima = 0 and edad_maxima = 7)
        or (nombre = 'Sub-10' and edad_minima = 8 and edad_maxima = 9)
        or (nombre = 'Sub-12' and edad_minima = 10 and edad_maxima = 11)
        or (nombre = 'Sub-14' and edad_minima = 12 and edad_maxima = 13)
        or (nombre = 'Sub-16' and edad_minima = 14 and edad_maxima = 15)
    ),
    constraint categoria_rango_ck check (edad_minima <= edad_maxima)
);

create table public.deportista (
    id_deportista uuid primary key default gen_random_uuid(),
    nombre varchar(100) not null,
    apellido varchar(100) not null,
    tipo_documento varchar(20) not null,
    documento_identidad varchar(40) not null,
    fecha_nacimiento date not null,
    posicion varchar(50),
    eps varchar(150),
    telefono varchar(30),
    correo varchar(320),
    direccion varchar(250),
    foto varchar(500),
    documentos varchar(500),
    fecha_registro date not null default current_date,
    id_categoria uuid references public.categoria (id_categoria) on delete restrict,
    constraint deportista_tipo_documento_ck check (
        tipo_documento in ('CC', 'TI', 'CE', 'PASAPORTE')
    ),
    unique (tipo_documento, documento_identidad),
    unique (correo)
);

create table public.acudiente (
    id_acudiente uuid primary key default gen_random_uuid(),
    nombre varchar(100) not null,
    apellido varchar(100) not null,
    tipo_documento varchar(20) not null,
    documento_identidad varchar(40) not null,
    fecha_nacimiento date not null,
    telefono varchar(30),
    correo varchar(320),
    direccion varchar(250),
    id_usuario uuid unique references public.usuario (id_usuario) on delete set null,
    constraint acudiente_tipo_documento_ck check (
        tipo_documento in ('CC', 'TI', 'CE', 'PASAPORTE')
    ),
    unique (tipo_documento, documento_identidad),
    unique (correo)
);

create table public.acudiente_deportista (
    id_acudiente uuid not null references public.acudiente (id_acudiente) on delete cascade,
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    primary key (id_acudiente, id_deportista)
);

create table public.torneo (
    id_torneo uuid primary key default gen_random_uuid(),
    nombre_torneo varchar(150) not null,
    id_categoria uuid not null references public.categoria (id_categoria) on delete restrict,
    lugar varchar(250) not null,
    fecha_inicio date not null,
    fecha_fin date not null,
    costo numeric(12, 2) not null,
    constraint torneo_fechas_ck check (fecha_inicio <= fecha_fin),
    constraint torneo_costo_ck check (costo >= 0)
);

create table public.pago (
    id_pago uuid primary key default gen_random_uuid(),
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    fecha_pago date,
    monto numeric(12, 2) not null,
    concepto varchar(250) not null,
    recibo_url varchar(500),
    estado_pago varchar(20) not null,
    constraint pago_monto_ck check (monto >= 0),
    constraint pago_estado_ck check (
        estado_pago in ('PENDIENTE', 'APROBADO', 'RECHAZADO')
    )
);

create table public.inscripcion_torneo (
    id_inscripcion uuid primary key default gen_random_uuid(),
    id_torneo uuid not null references public.torneo (id_torneo) on delete cascade,
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    resultado varchar(30) not null default 'PENDIENTE',
    constraint inscripcion_resultado_ck check (
        resultado in ('PENDIENTE', 'CAMPEON', 'SUBCAMPEON', 'TERCER_LUGAR', 'OTRO')
    ),
    unique (id_torneo, id_deportista)
);

create table public.entrenamiento (
    id_entrenamiento uuid primary key default gen_random_uuid(),
    id_categoria uuid not null references public.categoria (id_categoria) on delete restrict,
    id_entrenador uuid not null references public.entrenador (id_entrenador) on delete restrict,
    fecha date not null,
    hora_inicio time not null,
    lugar varchar(150) not null,
    unique (lugar, fecha, hora_inicio)
);

create table public.asistencia (
    id_asistencia uuid primary key default gen_random_uuid(),
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    id_entrenador uuid not null references public.entrenador (id_entrenador) on delete restrict,
    id_entrenamiento uuid not null references public.entrenamiento (id_entrenamiento) on delete cascade,
    fecha date not null,
    estado varchar(20) not null,
    observaciones text,
    constraint asistencia_estado_ck check (
        estado in ('PRESENTE', 'AUSENTE', 'JUSTIFICADO')
    ),
    unique (id_deportista, id_entrenamiento)
);

create table public.notificacion_pago (
    id_notificacion_pago uuid primary key default gen_random_uuid(),
    id_acudiente uuid not null references public.acudiente (id_acudiente) on delete cascade,
    mensaje text not null,
    fecha_envio timestamptz not null default now(),
    leido boolean not null default false
);

create table public.notificacion (
    id_notificacion uuid primary key default gen_random_uuid(),
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    id_entrenamiento uuid references public.entrenamiento (id_entrenamiento) on delete set null,
    tipo varchar(40) not null,
    mensaje text not null,
    fecha_envio timestamptz not null default now(),
    leido boolean not null default false,
    constraint notificacion_tipo_ck check (tipo in (
        'ENTRENAMIENTO_CREADO',
        'ENTRENAMIENTO_ACTUALIZADO',
        'ENTRENAMIENTO_ELIMINADO'
    ))
);

create table public.reporte_financiero (
    id_reporte uuid primary key default gen_random_uuid(),
    mes integer not null,
    anio integer not null,
    ingresos_mensualidades numeric(12, 2) not null default 0,
    ingresos_inscripciones numeric(12, 2) not null default 0,
    ingresos_ventas numeric(12, 2) not null default 0,
    fecha_generacion timestamptz not null default now(),
    constraint reporte_mes_ck check (mes between 1 and 12),
    constraint reporte_anio_ck check (anio >= 2000),
    constraint reporte_ingresos_ck check (
        ingresos_mensualidades >= 0
        and ingresos_inscripciones >= 0
        and ingresos_ventas >= 0
    ),
    unique (mes, anio)
);

create index deportista_categoria_idx on public.deportista (id_categoria);
create index acudiente_deportista_idx on public.acudiente_deportista (id_deportista);
create index pago_deportista_idx on public.pago (id_deportista);
create index torneo_categoria_idx on public.torneo (id_categoria);
create index entrenamiento_fecha_idx on public.entrenamiento (fecha);
create index asistencia_entrenamiento_idx on public.asistencia (id_entrenamiento);
create index notificacion_usuario_idx on public.notificacion (id_usuario, fecha_envio desc);
create index notificacion_pago_acudiente_idx on public.notificacion_pago (id_acudiente, fecha_envio desc);

insert into public.rol (nombre)
values
    ('ADMINISTRADOR'),
    ('TESORERO'),
    ('ENTRENADOR'),
    ('ACUDIENTE')
on conflict (nombre) do nothing;

alter table public.usuario enable row level security;
alter table public.rol enable row level security;
alter table public.usuario_rol enable row level security;
alter table public.entrenador enable row level security;
alter table public.categoria enable row level security;
alter table public.deportista enable row level security;
alter table public.acudiente enable row level security;
alter table public.acudiente_deportista enable row level security;
alter table public.torneo enable row level security;
alter table public.pago enable row level security;
alter table public.inscripcion_torneo enable row level security;
alter table public.entrenamiento enable row level security;
alter table public.asistencia enable row level security;
alter table public.notificacion_pago enable row level security;
alter table public.notificacion enable row level security;
alter table public.reporte_financiero enable row level security;
