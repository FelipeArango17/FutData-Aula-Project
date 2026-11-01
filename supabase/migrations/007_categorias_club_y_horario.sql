-- Migración 007 de FutData: categorías reales del club, registro civil, sedes y horario fijo.
-- Ver docs/info-sprints/sprint-1/diccionario-datos.md y el PPI (RF001, RF004, RF009, CU-01, CU-05).
--
-- Se ejecuta UNA vez, después de la 006, con el SQL Editor de Supabase y una cuenta administrativa. Todo va en una
-- transacción: si un paso falla, no queda ningún cambio. Antes de tocar nada revisa tres cosas y se detiene con un
-- mensaje que dice qué dato corregir (igual que la 004):
--   * deportistas con tipo_documento 'CC' (para deportistas ya no existe; entrenador y acudiente la conservan),
--   * deportistas cuyo año de nacimiento queda fuera de 4 a 16 años,
--   * entrenamientos con un lugar que no sea una de las dos sedes.
-- Haz una copia de seguridad antes de correrla en la base del equipo.
--
-- Categorías: por AÑO de nacimiento, no por edad cumplida. edad por año = año(hoy) - año de nacimiento.
-- categoria.edad_minima y edad_maxima guardan ese número. Las filas existentes conservan su id (Sub-8 pasa a
-- Párvulos), así que los entrenamientos que ya apuntaban a una categoría siguen apuntando a ella.

begin;

-- ---------------------------------------------------------------------------------------------------------
-- 1. Revisión previa: datos que esta migración no puede convertir sin perder información.
-- ---------------------------------------------------------------------------------------------------------

do $$
declare
    n integer;
    detalle text;
begin
    select count(*) into n from public.deportista where tipo_documento = 'CC';
    if n > 0 then
        raise exception 'Hay % deportista(s) con tipo_documento CC. Cámbialos a TI, RC, CE o PASAPORTE antes de esta migración: select id_deportista, nombre, apellido from public.deportista where tipo_documento = ''CC'';', n;
    end if;

    select count(*), string_agg(id_deportista::text || ' (' || fecha_nacimiento || ')', ', ')
      into n, detalle
      from public.deportista
     where extract(year from current_date)::int - extract(year from fecha_nacimiento)::int not between 4 and 16;
    if n > 0 then
        raise exception 'Hay % deportista(s) fuera del rango de 4 a 16 años por año de nacimiento: %. Corrige su fecha de nacimiento o retíralo antes de esta migración.', n, detalle;
    end if;

    select count(*), string_agg(distinct lugar, ', ')
      into n, detalle
      from public.entrenamiento
     where lugar not in ('Horizontes', 'UVA El Paraíso');
    if n > 0 then
        raise exception 'Hay % entrenamiento(s) con un lugar que no es una sede (%). Cámbialos a Horizontes o UVA El Paraíso antes de esta migración: update public.entrenamiento set lugar = ''Horizontes'' where lugar not in (''Horizontes'', ''UVA El Paraíso'');', n, detalle;
    end if;
end
$$;

-- ---------------------------------------------------------------------------------------------------------
-- 2. Categorías: Párvulos (4-6), Cachorros (7-8) y de Sub-9 a Sub-16, una por cada año.
-- ---------------------------------------------------------------------------------------------------------

alter table public.categoria drop constraint categoria_nombre_rango_ck;

-- Reutiliza las filas de la 004 para conservar los ids.
update public.categoria set nombre = 'Párvulos', edad_minima = 4, edad_maxima = 6 where nombre = 'Sub-8';
update public.categoria set edad_minima = 10, edad_maxima = 10 where nombre = 'Sub-10';
update public.categoria set edad_minima = 12, edad_maxima = 12 where nombre = 'Sub-12';
update public.categoria set edad_minima = 14, edad_maxima = 14 where nombre = 'Sub-14';
update public.categoria set edad_minima = 16, edad_maxima = 16 where nombre = 'Sub-16';

insert into public.categoria (nombre, edad_minima, edad_maxima)
values
    ('Párvulos', 4, 6), ('Cachorros', 7, 8),
    ('Sub-9', 9, 9), ('Sub-10', 10, 10), ('Sub-11', 11, 11), ('Sub-12', 12, 12), ('Sub-13', 13, 13),
    ('Sub-14', 14, 14), ('Sub-15', 15, 15), ('Sub-16', 16, 16)
on conflict (nombre) do nothing;

alter table public.categoria
    add constraint categoria_nombre_rango_ck check (
        (nombre = 'Párvulos' and edad_minima = 4 and edad_maxima = 6)
        or (nombre = 'Cachorros' and edad_minima = 7 and edad_maxima = 8)
        or (nombre ~ '^Sub-(9|1[0-6])$' and edad_minima = substring(nombre from 5)::int and edad_maxima = edad_minima)
    );

-- Cada deportista queda en la categoría que le toca por su año de nacimiento (CU-01: la categoría ya no se
-- asigna a mano). Los rangos no se solapan, así que cada uno cae en una sola.
update public.deportista d
   set id_categoria = c.id_categoria
  from public.categoria c
 where extract(year from current_date)::int - extract(year from d.fecha_nacimiento)::int
       between c.edad_minima and c.edad_maxima
   and d.id_categoria is distinct from c.id_categoria;

-- ---------------------------------------------------------------------------------------------------------
-- 3. Tipo de documento del deportista: se agrega el registro civil (RC) y sale la cédula (CC).
-- ---------------------------------------------------------------------------------------------------------

alter table public.deportista drop constraint deportista_tipo_documento_ck;
alter table public.deportista
    add constraint deportista_tipo_documento_ck check (tipo_documento in ('TI', 'RC', 'CE', 'PASAPORTE'));

-- ---------------------------------------------------------------------------------------------------------
-- 4. Sedes. entrenamiento.lugar pasa a ser una de las dos sedes. Se elimina la restricción de cruce por lugar
-- de la 004: en el club varias categorías entrenan a la misma hora y en la misma sede. Se conservan las de
-- categoría y entrenador.
-- ---------------------------------------------------------------------------------------------------------

alter table public.entrenamiento drop constraint entrenamiento_cruce_lugar_ex;
alter table public.entrenamiento
    add constraint entrenamiento_sede_ck check (lugar in ('Horizontes', 'UVA El Paraíso'));

-- ---------------------------------------------------------------------------------------------------------
-- 5. Horario fijo del club (lunes a sábado). id_entrenador NULL = "todos los entrenadores" (Párvulos, Cachorros
-- y los grupos de mañana y tarde de Sub-9 y Sub-10); NULL no choca con la restricción de cruce. grupo solo se
-- usa en Sub-9 y Sub-10. Un mismo entrenador no puede tener dos franjas que se solapen el mismo día.
-- ---------------------------------------------------------------------------------------------------------

create table public.franja_horario (
    id_franja uuid primary key default gen_random_uuid(),
    id_categoria uuid not null references public.categoria (id_categoria) on delete cascade,
    dia smallint not null,
    hora_inicio time not null,
    hora_fin time not null,
    sede varchar(30) not null,
    id_entrenador uuid references public.entrenador (id_entrenador) on delete restrict,
    grupo varchar(10),
    constraint franja_dia_ck check (dia between 1 and 6),
    constraint franja_horas_ck check (hora_fin > hora_inicio),
    constraint franja_sede_ck check (sede in ('Horizontes', 'UVA El Paraíso')),
    constraint franja_grupo_ck check (grupo is null or grupo in ('mañana', 'tarde')),
    constraint franja_cruce_entrenador_ex exclude using gist (
        id_entrenador with =,
        dia with =,
        tsrange(date '2000-01-01' + hora_inicio, date '2000-01-01' + hora_fin) with &&
    )
);

create index franja_categoria_idx on public.franja_horario (id_categoria);

alter table public.franja_horario enable row level security;

-- ---------------------------------------------------------------------------------------------------------
-- 6. Storage: fotos y documentos de los deportistas (privados, máximo 5 MB). Como el bucket de comprobantes, no
-- llevan políticas: solo el backend entra con la llave service_role y entrega URL firmadas. Se crean solo si
-- existe el esquema storage (en Supabase sí; en el PostgreSQL de las pruebas no).
-- ---------------------------------------------------------------------------------------------------------

do $$
begin
    if to_regclass('storage.buckets') is not null then
        insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
        values
            ('fotos', 'fotos', false, 5242880, array['image/jpeg', 'image/png']),
            ('documentos', 'documentos', false, 5242880, array['application/pdf', 'image/jpeg', 'image/png'])
        on conflict (id) do update
            set public = false,
                file_size_limit = excluded.file_size_limit,
                allowed_mime_types = excluded.allowed_mime_types;
    end if;
end
$$;

commit;
