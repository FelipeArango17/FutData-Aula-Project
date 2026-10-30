-- Códigos de verificación 2FA: solo se conserva el hash, nunca el código.

create table if not exists public.codigo_verificacion (
    id_codigo uuid primary key default gen_random_uuid(),
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    codigo_hash varchar(255) not null,
    expira_en timestamptz not null,
    intentos integer not null default 0,
    bloqueado_hasta timestamptz,
    ultimo_envio timestamptz not null default now(),
    usado boolean not null default false,
    creado_en timestamptz not null default now(),
    constraint codigo_verificacion_intentos_ck check (intentos between 0 and 5)
);

create index if not exists codigo_verificacion_usuario_idx
    on public.codigo_verificacion (id_usuario, creado_en desc);

alter table public.codigo_verificacion enable row level security;
