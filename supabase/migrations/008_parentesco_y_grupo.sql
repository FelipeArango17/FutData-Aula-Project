-- Migración 008 de FutData: parentesco del acudiente, grupo (mañana/tarde) y deportista activo.
-- Ver docs/PRESENTACION-TECNICA.md (secciones 4 y 5) y docs/info-sprints/sprint-1/diccionario-datos.md.
--
-- Se ejecuta UNA vez, después de la 007, con el SQL Editor de Supabase y una cuenta administrativa. Todo va en una
-- transacción: si un paso falla, no queda ningún cambio. Agrega columnas opcionales o con valor por defecto y
-- convierte un solo dato: los deportistas con estado_inscripcion RETIRADA quedan con activo = false (paso 3). Haz
-- una copia de seguridad antes de correrla en la base del equipo.

begin;

-- ---------------------------------------------------------------------------------------------------------
-- 1. Parentesco del acudiente con el deportista (opcional). Sin tildes: el frontend las muestra.
-- ---------------------------------------------------------------------------------------------------------

alter table public.acudiente_deportista add column parentesco varchar(10);
alter table public.acudiente_deportista
    add constraint acudiente_deportista_parentesco_ck check (
        parentesco is null or parentesco in ('Madre', 'Padre', 'Abuelo', 'Abuela', 'Tia', 'Tio', 'Otro')
    );

-- ---------------------------------------------------------------------------------------------------------
-- 2. Grupo del deportista (opcional): mañana o tarde. Solo tiene sentido en Cachorros y Semillero; como la
-- categoría se calcula al leer (cambia sola cada 1 de enero), esa regla la valida el backend, no la base.
-- ---------------------------------------------------------------------------------------------------------

alter table public.deportista add column grupo varchar(10);
alter table public.deportista
    add constraint deportista_grupo_ck check (grupo is null or grupo in ('mañana', 'tarde'));

-- ---------------------------------------------------------------------------------------------------------
-- 3. Deportista activo o inactivo. Reemplaza a estado_inscripcion, que queda como valor heredado: ningún código
-- lo lee ni lo escribe (el estado de la inscripción se calcula desde los documentos). Los retirados quedan
-- inactivos; los demás, activos.
-- ---------------------------------------------------------------------------------------------------------

alter table public.deportista add column activo boolean not null default true;
update public.deportista set activo = false where estado_inscripcion = 'RETIRADA';

commit;
