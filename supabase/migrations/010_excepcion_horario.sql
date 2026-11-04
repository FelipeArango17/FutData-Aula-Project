-- Migración 010 de FutData: excepciones por fecha del horario de la semana (bloque 4B-2).
-- Ver docs/PRESENTACION-TECNICA.md (secciones 8 y 9.1).
--
-- Se ejecuta UNA vez, después de la 009, con el SQL Editor de Supabase y una cuenta administrativa. Todo va en una
-- transacción: si un paso falla, no queda ningún cambio. Solo crea una tabla nueva: no modifica ni lee datos de las
-- demás, y la tabla entrenamiento NO se toca. Haz una copia de seguridad antes de correrla en la base del equipo.
--
-- Las franjas de franja_horario son fijas todo el año. Sobre ellas solo se registran excepciones de una fecha concreta:
--   * CANCELACION: la franja id_franja no se da ese día. La categoría, la hora, la sede y el entrenador son los de la
--     franja, así que la cancelación no los repite (id_categoria y los campos de horario van en NULL).
--   * EXTRA: un entrenamiento adicional que no está en las franjas. Lleva su propia categoría, hora, sede, entrenador
--     (NULL = "por turnos", como en franja_horario) y grupo (solo Cachorros y Semillero; lo valida el backend, como en
--     franja_horario).
--
-- Reglas que decide la base (el backend traduce cada rechazo por el nombre de la restricción):
--   * una franja no se cancela dos veces el mismo día (excepcion_horario_cancelacion_unica_idx),
--   * un entrenador no tiene dos extras que se solapen el mismo día (excepcion_horario_cruce_entrenador_ex; NULL no
--     choca). El cruce de un extra con las franjas no cabe aquí (depende de las cancelaciones y del día de la semana)
--     y lo comprueba el backend.
--
-- Al borrar una franja se borran sus cancelaciones (on delete cascade): la excepción no tiene sentido sin la franja. Al
-- borrar una categoría se borran sus extras. Un entrenador con extras no se puede borrar (restrict), igual que con franjas.
-- Requiere la extensión btree_gist, que ya crea la 004.

begin;

create table public.excepcion_horario (
    id_excepcion uuid primary key default gen_random_uuid(),
    tipo varchar(12) not null,
    fecha date not null,
    id_franja uuid references public.franja_horario (id_franja) on delete cascade,
    id_categoria uuid references public.categoria (id_categoria) on delete cascade,
    hora_inicio time,
    hora_fin time,
    sede varchar(30),
    id_entrenador uuid references public.entrenador (id_entrenador) on delete restrict,
    grupo varchar(10),
    motivo varchar(200),
    creado_por uuid references public.usuario (id_usuario) on delete set null,
    creado_en timestamptz not null default now(),
    constraint excepcion_horario_tipo_ck check (tipo in ('CANCELACION', 'EXTRA')),
    -- Qué campos lleva cada tipo: la cancelación solo apunta a su franja; el extra trae todo su horario.
    constraint excepcion_horario_campos_ck check (
        (tipo = 'CANCELACION'
            and id_franja is not null
            and id_categoria is null
            and hora_inicio is null
            and hora_fin is null
            and sede is null
            and id_entrenador is null
            and grupo is null)
        or (tipo = 'EXTRA'
            and id_franja is null
            and id_categoria is not null
            and hora_inicio is not null
            and hora_fin is not null
            and sede is not null)
    ),
    constraint excepcion_horario_horas_ck check (hora_fin > hora_inicio),
    constraint excepcion_horario_sede_ck check (sede in ('Horizontes', 'UVA El Paraíso')),
    constraint excepcion_horario_grupo_ck check (grupo in ('mañana', 'tarde'))
    -- hora_fin, sede y grupo pueden ser NULL: un check con NULL no se viola, y excepcion_horario_campos_ck obliga a
    -- que el extra los traiga (menos el grupo, que es opcional). El motivo (máximo 200 caracteres) es opcional.
);

-- No cancelar dos veces la misma franja en la misma fecha.
create unique index excepcion_horario_cancelacion_unica_idx
    on public.excepcion_horario (id_franja, fecha)
    where tipo = 'CANCELACION';

-- La semana se arma con las excepciones de un rango de fechas.
create index excepcion_horario_fecha_idx on public.excepcion_horario (fecha);

-- Un mismo entrenador no puede tener dos extras que se solapen el mismo día. NULL (por turnos) no choca.
alter table public.excepcion_horario
    add constraint excepcion_horario_cruce_entrenador_ex exclude using gist (
        id_entrenador with =,
        fecha with =,
        tsrange(fecha + hora_inicio, fecha + hora_fin) with &&
    ) where (tipo = 'EXTRA');

-- Como el resto de las tablas: sin políticas, solo el backend entra con la llave service_role.
alter table public.excepcion_horario enable row level security;

commit;
