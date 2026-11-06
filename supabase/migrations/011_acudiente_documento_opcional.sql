-- Migración 011 de FutData: el documento y la fecha de nacimiento del acudiente pasan a ser opcionales (bloque 2B).
-- Ver docs/PRESENTACION-TECNICA.md (secciones 5 y 7) y docs/info-sprints/sprint-1/diccionario-datos.md.
--
-- Motivo: la inscripción única (POST /api/inscripciones) pide del acudiente solo nombre, apellido, correo, teléfono y
-- parentesco. Con tipo_documento, documento_identidad y fecha_nacimiento en NOT NULL (migración 001) habría que inventar
-- esos datos para poder guardarlo.
--
-- Qué hace: quita el NOT NULL de esas tres columnas. Nada más: no cambia datos, no agrega columnas y no toca las
-- restricciones. Concretamente:
--   * acudiente_tipo_documento_ck (tipo_documento in ('CC','TI','CE','PASAPORTE')) sigue igual: un CHECK no rechaza
--     NULL (la condición queda desconocida, no falsa), así que un tipo NULL pasa y uno distinto de la lista se rechaza;
--   * el único (tipo_documento, documento_identidad) sigue valiendo: si ambos vienen informados no se repiten, y las
--     filas con NULL no chocan entre sí (en PostgreSQL los NULL son distintos en un único);
--   * el único lower(correo) (migración 004) y el de id_usuario no cambian.
--
-- Se puede ejecutar más de una vez sin efecto (quitar un NOT NULL que ya no está no falla). Se ejecuta con el SQL
-- Editor de Supabase y una cuenta administrativa, DESPUÉS de probarla en el proyecto de prueba y con una copia de
-- seguridad de la base del equipo (convención de docs/PRESENTACION-TECNICA.md, sección 10). Todo va en una transacción.

begin;

alter table public.acudiente alter column tipo_documento drop not null;
alter table public.acudiente alter column documento_identidad drop not null;
alter table public.acudiente alter column fecha_nacimiento drop not null;

commit;
