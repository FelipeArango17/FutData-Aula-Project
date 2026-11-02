-- 009_pagos_wompi.sql
-- Pagos en línea con Wompi: más medios de pago, el ID de la transacción aprobada y los tipos de cobro TORNEO y UNIFORME.
--
-- Se ejecuta UNA vez, después de la 008 (parentesco, grupo y deportista activo), con el SQL Editor de Supabase y una
-- cuenta administrativa. Haz una copia de seguridad de public.pago antes de correrla en la base del equipo.
--
-- Qué cambia:
--   * pago_metodo_ck y pago_referencia_wompi_ck: se agregan 'QR', 'BOTON_BANCOLOMBIA' y 'WOMPI_OTRO'. WOMPI_OTRO es el
--     respaldo para cualquier payment_method_type de Wompi que no esté en la tabla de mapeo del backend: nunca se
--     pierde un pago aprobado. Igual que PSE, NEQUI y TARJETA, estos tres exigen referencia Wompi cuando el pago está
--     aprobado; EFECTIVO y TRANSFERENCIA siguen siendo manuales (sin referencia Wompi).
--   * pago.metodo pasa de varchar(15) a varchar(20): 'BOTON_BANCOLOMBIA' tiene 17 caracteres. PostgreSQL no deja cambiar el
--     tipo de una columna que usa una vista, así que se borra y se vuelve a crear pago_con_estado (igual que en la 004;
--     además ahora incluye id_transaccion_wompi).
--   * Columna pago.id_transaccion_wompi (ID de la transacción aprobada) con índice único parcial: una misma
--     transacción no puede aprobar dos cobros.
--   * pago_tipo_ck: se agregan 'TORNEO' y 'UNIFORME'.
--   * pago_periodo_ck: el periodo (primer día del mes) es obligatorio solo en MENSUALIDAD; en los demás tipos es
--     opcional (antes estaba prohibido). Si hay periodo, sigue siendo el día 1.
-- Todo en una sola transacción. Si algo no es como se espera, se detiene con un mensaje que dice qué revisar.

begin;

do $$
declare
    restriccion text;
begin
    foreach restriccion in array array['pago_tipo_ck', 'pago_periodo_ck', 'pago_metodo_ck', 'pago_referencia_wompi_ck'] loop
        if not exists (
            select 1 from pg_constraint
             where conrelid = 'public.pago'::regclass and conname = restriccion
        ) then
            raise exception 'Migración 009: no existe la restricción public.pago.% (la crea la migración 004). Revisa que las migraciones anteriores estén aplicadas y que nadie la haya renombrado.', restriccion;
        end if;
    end loop;

    if exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'pago' and column_name = 'id_transaccion_wompi'
    ) then
        raise exception 'Migración 009: public.pago ya tiene la columna id_transaccion_wompi. Revisa si esta migración ya se aplicó.';
    end if;

    if exists (select 1 from public.pago where metodo is not null and metodo not in ('EFECTIVO', 'TRANSFERENCIA', 'PSE', 'NEQUI', 'TARJETA')) then
        raise exception 'Migración 009: hay pagos con un método fuera de la lista conocida. Corrígelos antes de continuar: select id_pago, metodo from public.pago where metodo not in (''EFECTIVO'', ''TRANSFERENCIA'', ''PSE'', ''NEQUI'', ''TARJETA'');';
    end if;
end
$$;

-- 1. Tipos de cobro
alter table public.pago drop constraint pago_tipo_ck;
alter table public.pago add constraint pago_tipo_ck
    check (tipo_pago in ('MENSUALIDAD', 'INSCRIPCION', 'TORNEO', 'UNIFORME'));

-- 2. Periodo: obligatorio solo en MENSUALIDAD
alter table public.pago drop constraint pago_periodo_ck;
alter table public.pago add constraint pago_periodo_ck check (
    (tipo_pago <> 'MENSUALIDAD' or periodo is not null)
    and (periodo is null or extract(day from periodo) = 1)
);

-- 3. Métodos de pago y referencia Wompi (la vista se recrea al final, con la columna nueva)
drop view public.pago_con_estado;
alter table public.pago alter column metodo type varchar(20);

alter table public.pago drop constraint pago_metodo_ck;
alter table public.pago add constraint pago_metodo_ck check (
    metodo in ('EFECTIVO', 'TRANSFERENCIA', 'PSE', 'NEQUI', 'TARJETA', 'QR', 'BOTON_BANCOLOMBIA', 'WOMPI_OTRO')
);

alter table public.pago drop constraint pago_referencia_wompi_ck;
alter table public.pago add constraint pago_referencia_wompi_ck check (
    estado_pago <> 'APROBADO'
    or (metodo in ('PSE', 'NEQUI', 'TARJETA', 'QR', 'BOTON_BANCOLOMBIA', 'WOMPI_OTRO')) = (referencia_wompi is not null)
);

-- 4. ID de la transacción aprobada de Wompi
alter table public.pago add column id_transaccion_wompi varchar(60);

create unique index pago_id_transaccion_wompi_idx
    on public.pago (id_transaccion_wompi)
    where id_transaccion_wompi is not null;

-- 5. La vista, igual que en la 004 (security_invoker, "vencido" derivado, sin acceso para anon ni authenticated)
create view public.pago_con_estado
with (security_invoker = true)
as
select p.*,
       (p.estado_pago = 'PENDIENTE'
        and p.fecha_vencimiento < (now() at time zone 'America/Bogota')::date) as vencido
  from public.pago p;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon')
       and exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on public.pago_con_estado from anon, authenticated;
    end if;
end
$$;

commit;
