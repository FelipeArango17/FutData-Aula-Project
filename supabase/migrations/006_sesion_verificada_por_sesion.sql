-- Migración 006 de FutData: la verificación 2FA queda ligada a la SESIÓN de Supabase Auth, no al usuario
-- (PPI 4.2: "la sesión queda marcada como verificada"). Ver docs/info-sprints/sprint-1/diccionario-datos.md, §2.21.
--
-- La 005 dejó una fila por usuario (id_usuario, verificada_hasta): verificar el código en un equipo habilitaba todas
-- las sesiones de ese usuario durante 12 horas, incluso las abiertas después desde otro equipo. Aquí cada fila es una
-- sesión, identificada por el claim session_id del JWT, y el backend la busca con ese claim en cada petición.
--
-- Segura sobre bases con la 005 aplicada (o sin ella): se descartan las filas actuales, lo que solo obliga a los
-- usuarios conectados a verificar el código otra vez. La vigencia (expira_en) la calcula el backend con
-- ReglasVerificacion.VIGENCIA_SESION.

begin;

drop table if exists public.sesion_verificada;

create table public.sesion_verificada (
    id_sesion uuid primary key,
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    verificada_en timestamptz not null default now(),
    expira_en timestamptz not null,
    constraint sesion_verificada_vigencia_ck check (expira_en > verificada_en)
);

-- Cerrar todas las sesiones de un usuario (revocar el acceso) y limpiar las vencidas.
create index sesion_verificada_usuario_idx on public.sesion_verificada (id_usuario);

-- Igual que el resto del modelo: RLS activo y sin políticas. Solo el backend lee y escribe.
alter table public.sesion_verificada enable row level security;

commit;
