-- Plantilla semanal del club (30 franjas). NO es una migración: se carga a mano, una sola vez, en una base nueva
-- o de pruebas, después de la 007:  psql ... -f supabase/seeds/franjas_club.sql
--
-- Fuente: el Excel oficial del club (Horario_de_Entrenamientos_Prado_FC.xlsx), transcrito por Felipe en la
-- versión 4 de la entrega de backend (tabla de la sección 2.1). Días: 1 = lunes ... 6 = sábado. La sede va por
-- franja (en el Excel, azul = Horizontes y verde = UVA El Paraíso).
--
-- Todas las franjas quedan SIN entrenador (id_entrenador NULL):
--   * Párvulos, Cachorros y Semillero son "por turnos" (en el Excel, "CUALQUIERA"): los entrenadores se rotan.
--   * Los responsables de Sub-11 a Sub-16 (David Mesa, Nicolás, Jhon Cardona, Giovanny, Jhon Ramírez) todavía no
--     existen como entrenadores en la base; el administrador los asigna en la aplicación cuando se creen.
-- grupo (mañana/tarde) solo se llena en las 8 franjas de Cachorros y Semillero de martes y jueves: son dos
-- sesiones alternativas el mismo día y cada niño va a una sola.
--
-- Se detiene si franja_horario ya tiene datos, para no duplicar ni pisar lo que el administrador haya editado.

begin;

do $$
begin
    if exists (select 1 from public.franja_horario) then
        raise exception 'franja_horario ya tiene datos: no se carga la plantilla del club';
    end if;
end
$$;

insert into public.franja_horario (id_categoria, dia, hora_inicio, hora_fin, sede, grupo)
select c.id_categoria, p.dia, p.hora_inicio::time, p.hora_fin::time, p.sede, p.grupo
  from (values
    ('Párvulos', 2, '08:30', '10:00', 'UVA El Paraíso', null),
    ('Párvulos', 6, '09:00', '10:00', 'Horizontes', null),
    ('Cachorros', 2, '07:30', '09:00', 'UVA El Paraíso', 'mañana'),
    ('Cachorros', 4, '07:30', '09:00', 'UVA El Paraíso', 'mañana'),
    ('Cachorros', 2, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Cachorros', 4, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Cachorros', 6, '08:30', '10:00', 'Horizontes', null),
    ('Semillero', 2, '07:30', '09:00', 'UVA El Paraíso', 'mañana'),
    ('Semillero', 4, '07:30', '09:00', 'UVA El Paraíso', 'mañana'),
    ('Semillero', 2, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Semillero', 4, '16:30', '18:00', 'UVA El Paraíso', 'tarde'),
    ('Semillero', 6, '08:30', '10:00', 'Horizontes', null),
    ('Sub-11', 2, '20:00', '21:00', 'Horizontes', null),
    ('Sub-11', 3, '19:00', '20:00', 'Horizontes', null),
    ('Sub-11', 4, '19:00', '20:00', 'Horizontes', null),
    ('Sub-12', 1, '19:00', '20:00', 'Horizontes', null),
    ('Sub-12', 4, '19:00', '20:00', 'Horizontes', null),
    ('Sub-12', 2, '18:00', '19:00', 'Horizontes', null),
    ('Sub-13', 1, '19:00', '20:00', 'Horizontes', null),
    ('Sub-13', 3, '19:00', '20:00', 'Horizontes', null),
    ('Sub-13', 5, '19:00', '20:00', 'Horizontes', null),
    ('Sub-14', 1, '18:00', '19:00', 'Horizontes', null),
    ('Sub-14', 3, '18:00', '19:00', 'Horizontes', null),
    ('Sub-14', 5, '18:00', '19:00', 'Horizontes', null),
    ('Sub-15', 1, '18:00', '19:00', 'Horizontes', null),
    ('Sub-15', 4, '18:00', '19:00', 'Horizontes', null),
    ('Sub-15', 2, '20:00', '21:00', 'Horizontes', null),
    ('Sub-16', 2, '16:30', '18:00', 'Horizontes', null),
    ('Sub-16', 4, '16:30', '18:00', 'Horizontes', null),
    ('Sub-16', 3, '18:00', '19:00', 'Horizontes', null)
  ) as p (categoria, dia, hora_inicio, hora_fin, sede, grupo)
  join public.categoria c on c.nombre = p.categoria;

do $$
declare n integer;
begin
    select count(*) into n from public.franja_horario;
    if n <> 30 then
        raise exception 'Se esperaban 30 franjas y quedaron %: faltan categorías (¿se corrió la 007?)', n;
    end if;
end
$$;

commit;
