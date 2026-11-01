-- Plantilla semanal de EJEMPLO del club (39 franjas). NO es una migración: se carga a mano, una sola vez, en
-- una base nueva o de pruebas, después de la 007:  psql ... -f supabase/seeds/franjas_ejemplo.sql
--
-- Sale de la tabla de docs/entrega-backend-futdata.md (sección 2.1). Las horas finales que la hoja del club no
-- indicaba se asumieron de 1 hora, y las sedes son de ejemplo: el administrador las corrige en la aplicación o
-- el club las confirma. Todas las franjas quedan SIN entrenador (id_entrenador NULL): los nombres de ejemplo de
-- la hoja (Nicolás, Dairo, David Mesa, Jhon Cardona, Giovanny, Jhon Ramírez) no existen como entrenadores en la
-- base; se asignan cuando se creen.
--
-- Se detiene si franja_horario ya tiene datos, para no duplicar ni pisar lo que el administrador haya editado.

begin;

do $$
begin
    if exists (select 1 from public.franja_horario) then
        raise exception 'franja_horario ya tiene datos: no se carga la plantilla de ejemplo';
    end if;
end
$$;

insert into public.franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, grupo)
select c.id_categoria, p.dia, p.hora_inicio::time, p.hora_fin::time, p.sede, p.grupo
  from (values
    ('Párvulos', 2, '08:30', '09:30', 'UVA El Paraíso', null),
    ('Párvulos', 6, '08:30', '10:00', 'UVA El Paraíso', null),
    ('Cachorros', 2, '07:00', '08:00', 'UVA El Paraíso', null),
    ('Cachorros', 4, '07:00', '08:00', 'UVA El Paraíso', null),
    ('Cachorros', 2, '16:30', '17:30', 'UVA El Paraíso', null),
    ('Cachorros', 4, '16:30', '17:30', 'UVA El Paraíso', null),
    ('Cachorros', 6, '08:30', '10:00', 'UVA El Paraíso', null),
    ('Sub-9', 5, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-9', 6, '08:30', '10:00', 'UVA El Paraíso', null),
    ('Sub-9', 2, '07:00', '08:00', 'UVA El Paraíso', 'mañana'),
    ('Sub-9', 4, '07:00', '08:30', 'UVA El Paraíso', 'mañana'),
    ('Sub-9', 2, '16:30', '17:30', 'UVA El Paraíso', 'tarde'),
    ('Sub-9', 4, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Sub-10', 1, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-10', 2, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-10', 5, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-10', 6, '08:30', '10:00', 'UVA El Paraíso', null),
    ('Sub-10', 2, '07:00', '08:00', 'UVA El Paraíso', 'mañana'),
    ('Sub-10', 4, '07:00', '08:30', 'UVA El Paraíso', 'mañana'),
    ('Sub-10', 2, '16:30', '17:30', 'UVA El Paraíso', 'tarde'),
    ('Sub-10', 4, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Sub-11', 2, '20:00', '21:00', 'UVA El Paraíso', null),
    ('Sub-11', 3, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-11', 4, '19:00', '20:00', 'UVA El Paraíso', null),
    ('Sub-12', 1, '19:00', '20:00', 'Horizontes', null),
    ('Sub-12', 2, '18:00', '19:00', 'Horizontes', null),
    ('Sub-12', 4, '19:00', '20:00', 'Horizontes', null),
    ('Sub-13', 1, '19:00', '20:00', 'Horizontes', null),
    ('Sub-13', 3, '19:00', '20:00', 'Horizontes', null),
    ('Sub-13', 5, '19:00', '20:00', 'Horizontes', null),
    ('Sub-14', 1, '18:00', '19:00', 'Horizontes', null),
    ('Sub-14', 3, '18:00', '19:00', 'Horizontes', null),
    ('Sub-14', 5, '18:00', '19:00', 'Horizontes', null),
    ('Sub-15', 1, '18:00', '19:00', 'Horizontes', null),
    ('Sub-15', 2, '20:00', '21:00', 'Horizontes', null),
    ('Sub-15', 4, '18:00', '19:00', 'Horizontes', null),
    ('Sub-16', 2, '16:30', '18:00', 'Horizontes', null),
    ('Sub-16', 4, '16:30', '18:00', 'Horizontes', null),
    ('Sub-16', 3, '18:00', '19:00', 'Horizontes', null)
  ) as p (categoria, dia, hora_inicio, hora_fin, sede, grupo)
  join public.categoria c on c.nombre = p.categoria;

do $$
declare n integer;
begin
    select count(*) into n from public.franja_horario;
    if n <> 39 then
        raise exception 'Se esperaban 39 franjas y quedaron %: faltan categorías (¿se corrió la 007?)', n;
    end if;
end
$$;

commit;
