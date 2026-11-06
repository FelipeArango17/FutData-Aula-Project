-- Migración 012 de FutData: asistencia de los entrenamientos (bloque 4, versión mínima). Ver docs/PRESENTACION-TECNICA.md.
--
-- Se ejecuta UNA vez, después de la 010 (la 011 es de otra rama), con el SQL Editor de Supabase y una cuenta
-- administrativa. Todo va en una transacción y es idempotente: correrla dos veces no cambia nada. No lee ni modifica
-- datos: solo quita dos NOT NULL. Haz una copia de seguridad antes de correrla en la base del equipo.
--
-- Qué cambia:
--   * entrenamiento.id_entrenador pasa a NULLABLE: es el entrenador ASIGNADO a la sesión; NULL = "por turnos" (Párvulos,
--     Cachorros y Semillero), igual que franja_horario.id_entrenador. La restricción de cruce por entrenador
--     (entrenamiento_cruce_entrenador_ex) no se toca: con NULL no choca.
--   * asistencia.id_entrenador pasa a NULLABLE: es QUIEN PASÓ LISTA; NULL si la pasó el administrador (que no es
--     entrenador). Sigue siendo una referencia a entrenador con on delete restrict.
--
-- Lo que NO hace falta cambiar (revisado contra las migraciones 001, 004 y 007): entrenamiento ya no tiene el unique
-- (lugar, fecha, hora_inicio) (la 004 lo reemplazó y la 007 quitó la exclusión por lugar), así que dos categorías a la
-- misma hora y sede caben. Siguen vigentes entrenamiento_cruce_categoria_ex (una categoría no tiene dos sesiones
-- PROGRAMADAS que se solapen) y entrenamiento_cruce_entrenador_ex. asistencia ya tiene unique (id_deportista,
-- id_entrenamiento), la clave del upsert, y registrado_en (migración 004).

begin;

alter table public.entrenamiento alter column id_entrenador drop not null;
alter table public.asistencia alter column id_entrenador drop not null;

commit;
