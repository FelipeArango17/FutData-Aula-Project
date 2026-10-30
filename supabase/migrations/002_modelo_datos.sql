-- Ajustes de compatibilidad para bases que ya ejecutaron el esquema inicial.

alter table public.usuario
    add column if not exists apellido varchar(100);

update public.usuario
set apellido = substring(nombre from position(' ' in nombre) + 1),
    nombre = split_part(nombre, ' ', 1)
where apellido is null
  and position(' ' in nombre) > 0;

alter table public.asistencia
    add column if not exists estado varchar(20);

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'asistencia'
          and column_name = 'presente'
    ) then
        update public.asistencia
        set estado = case when presente then 'PRESENTE' else 'AUSENTE' end
        where estado is null;

        alter table public.asistencia drop column presente;
    end if;
end
$$;

alter table public.asistencia
    alter column estado set not null;

alter table public.asistencia
    drop constraint if exists asistencia_estado_ck;

alter table public.asistencia
    add constraint asistencia_estado_ck check (
        estado in ('PRESENTE', 'AUSENTE', 'JUSTIFICADO')
    );

alter table public.pago
    alter column fecha_pago drop not null;

create table if not exists public.acudiente_deportista (
    id_acudiente uuid not null references public.acudiente (id_acudiente) on delete cascade,
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    primary key (id_acudiente, id_deportista)
);

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'acudiente'
          and column_name = 'id_deportista'
    ) then
        insert into public.acudiente_deportista (id_acudiente, id_deportista)
        select id_acudiente, id_deportista
        from public.acudiente
        on conflict do nothing;

        alter table public.acudiente
            drop constraint if exists acudiente_id_deportista_fkey,
            drop column id_deportista;
    end if;
end
$$;

create index if not exists acudiente_deportista_idx
    on public.acudiente_deportista (id_deportista);

alter table public.acudiente_deportista enable row level security;

alter table public.deportista
    alter column id_categoria drop not null;

alter table public.deportista
    drop constraint if exists deportista_tipo_documento_documento_identidad_key;

create unique index if not exists deportista_documento_identidad_uidx
    on public.deportista (documento_identidad);
