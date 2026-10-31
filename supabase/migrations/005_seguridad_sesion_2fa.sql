-- Mantiene el segundo paso completado para el usuario mientras dure la sesión de Auth.
-- La migración 004 creó una versión anterior con id_sesion y expira_en.
drop table if exists public.sesion_verificada;

create table public.sesion_verificada (
    id_usuario uuid primary key references public.usuario (id_usuario) on delete cascade,
    verificada_hasta timestamptz not null
);

create index sesion_verificada_vigencia_idx
    on public.sesion_verificada (verificada_hasta);

alter table public.sesion_verificada enable row level security;
