-- Migración 004 de FutData: ajustes al modelo de datos que salieron de la revisión del backend contra el PPI.
-- Ver docs/info-sprints/sprint-1/diccionario-datos.md, sección 6 (decisiones D6 a D16).
--
-- Se ejecuta UNA vez, después de la 001, la 002 (modelo_datos) y la 003 (codigo_verificacion_2fa), con el SQL Editor
-- de Supabase y una cuenta administrativa. Todo va en una transacción: si un paso falla, no queda ningún cambio.
-- La 002 del equipo ya hizo parte de lo revisado (acudiente_deportista, asistencia con tres estados, categoría
-- opcional del deportista, fecha_pago opcional) y la 003 creó codigo_verificacion; aquí no se repite.
-- Conserva los datos que ya existan. Se detiene con un mensaje claro si encuentra datos que no puede convertir
-- sin perder información (montos con centavos, documentos en la columna vieja, correos repetidos sin distinguir
-- mayúsculas o entrenamientos que se cruzan).

begin;

create extension if not exists btree_gist;

-- ---------------------------------------------------------------------------------------------------------
-- ---------------------------------------------------------------------------------------------------------
-- D7. Las categorías existen sin entrenador y se crean aquí (antes no había forma de crearlas).
-- ---------------------------------------------------------------------------------------------------------

alter table public.categoria alter column id_entrenador drop not null;

insert into public.categoria (nombre, edad_minima, edad_maxima)
values
    ('Sub-8', 0, 7),
    ('Sub-10', 8, 9),
    ('Sub-12', 10, 11),
    ('Sub-14', 12, 13),
    ('Sub-16', 14, 15)
on conflict (nombre) do nothing;

create index categoria_entrenador_idx on public.categoria (id_entrenador);

-- ---------------------------------------------------------------------------------------------------------
-- D8. Entrenadores activos e inactivos (CU-10, camino alternativo 2): desactivar conserva el historial.
-- ---------------------------------------------------------------------------------------------------------

alter table public.entrenador add column activo boolean not null default true;

-- ---------------------------------------------------------------------------------------------------------
-- D9. Estado de la inscripción del deportista (CU-01, CU-04 y BPMN 3.1). La categoría se asigna al aprobar.
-- Los deportistas que ya existen quedan ACTIVA; los nuevos empiezan PENDIENTE.
-- ---------------------------------------------------------------------------------------------------------

alter table public.deportista add column estado_inscripcion varchar(15) not null default 'ACTIVA';
alter table public.deportista alter column estado_inscripcion set default 'PENDIENTE';
alter table public.deportista
    add constraint deportista_estado_inscripcion_ck check (
        estado_inscripcion in ('PENDIENTE', 'ACTIVA', 'RETIRADA')
    ),
    add constraint deportista_activa_con_categoria_ck check (
        estado_inscripcion <> 'ACTIVA' or id_categoria is not null
    );

-- ---------------------------------------------------------------------------------------------------------
-- D10. Documentos del deportista en su propia tabla, con revisión y vencimiento (RF009, CU-04).
-- Reemplaza la columna deportista.documentos.
-- ---------------------------------------------------------------------------------------------------------

do $$
begin
    if exists (select 1 from public.deportista where documentos is not null) then
        raise exception 'Hay deportistas con deportista.documentos: pásalos a la tabla documento antes de esta migración';
    end if;
end
$$;

alter table public.deportista drop column documentos;

create table public.documento (
    id_documento uuid primary key default gen_random_uuid(),
    id_deportista uuid not null references public.deportista (id_deportista) on delete restrict,
    tipo varchar(30) not null,
    ruta varchar(300) not null unique,
    nombre_original varchar(255) not null,
    tipo_contenido varchar(30) not null,
    tamano_bytes integer not null,
    estado varchar(15) not null default 'PENDIENTE',
    comentario text,
    fecha_vencimiento date,
    cargado_por uuid references public.usuario (id_usuario) on delete set null,
    cargado_en timestamptz not null default now(),
    revisado_por uuid references public.usuario (id_usuario) on delete set null,
    revisado_en timestamptz,
    constraint documento_tipo_ck check (tipo in (
        'DOCUMENTO_IDENTIDAD', 'FICHA_MEDICA', 'PERMISO', 'CONTRATO', 'OTRO'
    )),
    constraint documento_tipo_contenido_ck check (tipo_contenido in (
        'application/pdf', 'image/jpeg', 'image/png'
    )),
    constraint documento_tamano_ck check (tamano_bytes between 1 and 5242880),
    constraint documento_estado_ck check (estado in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    constraint documento_rechazo_con_comentario_ck check (
        estado <> 'RECHAZADO' or (comentario is not null and length(btrim(comentario)) > 0)
    ),
    constraint documento_revision_ck check (
        (estado = 'PENDIENTE') = (revisado_en is null)
    )
);

create index documento_deportista_idx on public.documento (id_deportista);
create index documento_vencimiento_idx on public.documento (fecha_vencimiento) where fecha_vencimiento is not null;

-- ---------------------------------------------------------------------------------------------------------
-- D11. Asistencia: la 002 del equipo ya la dejó con PRESENTE, AUSENTE o JUSTIFICADO. Aquí se agrega la hora del
-- registro y se quita la fecha repetida, que sale del entrenamiento (decisión D5).
-- ---------------------------------------------------------------------------------------------------------

alter table public.asistencia
    add column registrado_en timestamptz not null default now(),
    drop column fecha;

-- ---------------------------------------------------------------------------------------------------------
-- D12. Pesos colombianos sin centavos en todo el modelo (el backend y el frontend usan enteros).
-- Las columnas siguen siendo numeric(12, 2), pero un valor con centavos se RECHAZA. Con numeric(12, 0)
-- PostgreSQL lo redondearía sin avisar, y un monto no debe cambiar en silencio.
-- ---------------------------------------------------------------------------------------------------------

do $$
begin
    if exists (select 1 from public.pago where monto <> trunc(monto))
       or exists (select 1 from public.torneo where costo <> trunc(costo))
       or exists (select 1 from public.reporte_financiero
                   where ingresos_mensualidades <> trunc(ingresos_mensualidades)
                      or ingresos_inscripciones <> trunc(ingresos_inscripciones)
                      or ingresos_ventas <> trunc(ingresos_ventas)) then
        raise exception 'Hay montos con centavos: corrígelos antes de exigir pesos enteros';
    end if;
end
$$;

alter table public.pago add constraint pago_monto_entero_ck check (monto = trunc(monto));
alter table public.torneo add constraint torneo_costo_entero_ck check (costo = trunc(costo));
alter table public.reporte_financiero add constraint reporte_ingresos_enteros_ck check (
    ingresos_mensualidades = trunc(ingresos_mensualidades)
    and ingresos_inscripciones = trunc(ingresos_inscripciones)
    and ingresos_ventas = trunc(ingresos_ventas)
);

-- ---------------------------------------------------------------------------------------------------------
-- D13. Pago: cobro por periodo, vencimiento, método, Wompi y comprobante (RF002, RF006, CU-02).
-- Un cobro se crea PENDIENTE y sin fecha_pago; al aprobarse se completan fecha_pago, metodo y el comprobante.
-- "Vencido" no se guarda: es un pago PENDIENTE con fecha_vencimiento anterior a hoy (vista pago_con_estado).
-- ---------------------------------------------------------------------------------------------------------

alter table public.pago rename column recibo_url to recibo_ruta;

alter table public.pago
    add column tipo_pago varchar(15) not null default 'MENSUALIDAD',
    add column periodo date,
    add column fecha_vencimiento date,
    add column metodo varchar(15),
    add column referencia_wompi varchar(100) unique,
    add column referencia_manual varchar(100),
    add column numero_comprobante varchar(20) unique,
    add column nota_interna text,
    add column registrado_por uuid references public.usuario (id_usuario) on delete set null,
    add column creado_en timestamptz not null default now();

alter table public.pago alter column tipo_pago drop default;

-- Pagos que ya existen: el periodo y el vencimiento salen de la fecha de pago.
update public.pago
   set periodo = date_trunc('month', coalesce(fecha_pago, current_date))::date,
       fecha_vencimiento = coalesce(fecha_pago, current_date);

alter table public.pago
    alter column fecha_vencimiento set not null;

alter table public.pago
    add constraint pago_tipo_ck check (tipo_pago in ('MENSUALIDAD', 'INSCRIPCION')),
    add constraint pago_periodo_ck check (
        (tipo_pago = 'MENSUALIDAD') = (periodo is not null)
        and (periodo is null or extract(day from periodo) = 1)
    ),
    add constraint pago_metodo_ck check (
        metodo in ('EFECTIVO', 'TRANSFERENCIA', 'PSE', 'NEQUI', 'TARJETA')
    ),
    add constraint pago_aprobado_completo_ck check (
        estado_pago <> 'APROBADO' or (fecha_pago is not null and metodo is not null)
    ),
    add constraint pago_referencia_wompi_ck check (
        estado_pago <> 'APROBADO'
        or (metodo in ('PSE', 'NEQUI', 'TARJETA')) = (referencia_wompi is not null)
    ),
    add constraint pago_numero_comprobante_ck check (numero_comprobante ~ '^FD-[0-9]{4}-[0-9]{6}$');

-- Una sola mensualidad por deportista y mes (los cobros rechazados no cuentan).
create unique index pago_mensualidad_unica_idx
    on public.pago (id_deportista, periodo)
    where tipo_pago = 'MENSUALIDAD' and estado_pago <> 'RECHAZADO';

create index pago_vencimiento_pendiente_idx
    on public.pago (fecha_vencimiento)
    where estado_pago = 'PENDIENTE';

-- Consecutivo de los comprobantes: FD-AAAA-NNNNNN. El número no se reinicia cada año.
create sequence public.pago_comprobante_seq as integer minvalue 1 maxvalue 999999 no cycle;

create function public.siguiente_numero_comprobante()
returns varchar
language sql
volatile
set search_path = ''
as $$
    select 'FD-' || to_char(now() at time zone 'America/Bogota', 'YYYY') || '-'
           || lpad(nextval('public.pago_comprobante_seq')::text, 6, '0');
$$;

revoke all on function public.siguiente_numero_comprobante() from public;

create view public.pago_con_estado
with (security_invoker = true)
as
select p.*,
       (p.estado_pago = 'PENDIENTE'
        and p.fecha_vencimiento < (now() at time zone 'America/Bogota')::date) as vencido
  from public.pago p;

-- ---------------------------------------------------------------------------------------------------------
-- D14. Ventas de uniformes (RF003: ingresos por ventas del reporte financiero).
-- ---------------------------------------------------------------------------------------------------------

create table public.venta (
    id_venta uuid primary key default gen_random_uuid(),
    fecha date not null default current_date,
    concepto varchar(250) not null,
    cantidad integer not null,
    valor_unitario numeric(12, 2) not null,
    total numeric(12, 2) generated always as (cantidad * valor_unitario) stored,
    metodo varchar(15) not null,
    id_deportista uuid references public.deportista (id_deportista) on delete set null,
    registrado_por uuid references public.usuario (id_usuario) on delete set null,
    creado_en timestamptz not null default now(),
    constraint venta_cantidad_ck check (cantidad > 0),
    constraint venta_valor_ck check (valor_unitario >= 0 and valor_unitario = trunc(valor_unitario)),
    constraint venta_metodo_ck check (metodo in ('EFECTIVO', 'TRANSFERENCIA', 'PSE', 'NEQUI', 'TARJETA'))
);

create index venta_fecha_idx on public.venta (fecha);

-- ---------------------------------------------------------------------------------------------------------
-- D15. Entrenamientos con hora de fin y estado. Ninguna cancha, categoría ni entrenador puede tener dos
-- entrenamientos programados que se crucen (CU-05). Reemplaza unique (lugar, fecha, hora_inicio).
-- Los entrenamientos que ya existen quedan de 90 minutos.
-- ---------------------------------------------------------------------------------------------------------

alter table public.entrenamiento
    add column hora_fin time,
    add column estado varchar(15) not null default 'PROGRAMADO';

update public.entrenamiento set hora_fin = hora_inicio + interval '90 minutes';

alter table public.entrenamiento
    alter column hora_fin set not null,
    add constraint entrenamiento_horas_ck check (hora_fin > hora_inicio),
    add constraint entrenamiento_estado_ck check (estado in ('PROGRAMADO', 'CANCELADO')),
    drop constraint entrenamiento_lugar_fecha_hora_inicio_key;

alter table public.entrenamiento
    add constraint entrenamiento_cruce_lugar_ex exclude using gist (
        lugar with =,
        tsrange(fecha + hora_inicio, fecha + hora_fin) with &&
    ) where (estado = 'PROGRAMADO'),
    add constraint entrenamiento_cruce_categoria_ex exclude using gist (
        id_categoria with =,
        tsrange(fecha + hora_inicio, fecha + hora_fin) with &&
    ) where (estado = 'PROGRAMADO'),
    add constraint entrenamiento_cruce_entrenador_ex exclude using gist (
        id_entrenador with =,
        tsrange(fecha + hora_inicio, fecha + hora_fin) with &&
    ) where (estado = 'PROGRAMADO');

-- ---------------------------------------------------------------------------------------------------------
-- D16. Equipos de confianza, sesiones verificadas, auditoría y preferencias de accesibilidad (PPI 4.1 y 4.2,
-- CU-00, CU-08). Los códigos 2FA ya están en codigo_verificacion (003). Los identificadores de equipo se guardan
-- solo como hash.
-- ---------------------------------------------------------------------------------------------------------

alter table public.usuario
    add column alto_contraste boolean not null default false,
    add column tamano_texto smallint not null default 100,
    add column paleta_daltonismo boolean not null default false,
    add constraint usuario_tamano_texto_ck check (tamano_texto between 100 and 200);

create table public.equipo_confianza (
    id_equipo uuid primary key default gen_random_uuid(),
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    token_hash varchar(100) not null unique,
    descripcion varchar(200),
    creado_en timestamptz not null default now(),
    expira_en timestamptz not null,
    ultimo_uso_en timestamptz,
    revocado_en timestamptz,
    constraint equipo_confianza_vigencia_ck check (expira_en > creado_en)
);

create index equipo_confianza_usuario_idx on public.equipo_confianza (id_usuario);

-- Sesión de Supabase Auth (claim session_id del JWT) que ya completó el segundo paso.
create table public.sesion_verificada (
    id_sesion uuid primary key,
    id_usuario uuid not null references public.usuario (id_usuario) on delete cascade,
    verificada_en timestamptz not null default now(),
    expira_en timestamptz not null
);

create index sesion_verificada_usuario_idx on public.sesion_verificada (id_usuario);

create table public.auditoria (
    id_auditoria bigint generated always as identity primary key,
    fecha timestamptz not null default now(),
    id_usuario uuid references public.usuario (id_usuario) on delete set null,
    evento varchar(60) not null,
    detalle jsonb,
    ip inet
);

create index auditoria_fecha_idx on public.auditoria (fecha desc);
create index auditoria_usuario_idx on public.auditoria (id_usuario, fecha desc);

-- ---------------------------------------------------------------------------------------------------------
-- Correos únicos sin distinguir mayúsculas. Los índices conservan el nombre de la restricción que reemplazan
-- (<tabla>_correo_key), así el backend reconoce el duplicado igual que antes.
-- ---------------------------------------------------------------------------------------------------------

update public.usuario set correo = lower(correo) where correo <> lower(correo);
update public.entrenador set correo = lower(correo) where correo <> lower(correo);
update public.deportista set correo = lower(correo) where correo <> lower(correo);
update public.acudiente set correo = lower(correo) where correo <> lower(correo);

alter table public.usuario drop constraint usuario_correo_key;
alter table public.entrenador drop constraint entrenador_correo_key;
alter table public.deportista drop constraint deportista_correo_key;
alter table public.acudiente drop constraint acudiente_correo_key;

create unique index usuario_correo_key on public.usuario (lower(correo));
create unique index entrenador_correo_key on public.entrenador (lower(correo));
create unique index deportista_correo_key on public.deportista (lower(correo));
create unique index acudiente_correo_key on public.acudiente (lower(correo));

-- ---------------------------------------------------------------------------------------------------------
-- Seguridad: igual que en la 001, RLS activo y sin políticas. Solo el backend (rol postgres) lee y escribe.
-- ---------------------------------------------------------------------------------------------------------

alter table public.documento enable row level security;
alter table public.venta enable row level security;
alter table public.equipo_confianza enable row level security;
alter table public.sesion_verificada enable row level security;
alter table public.auditoria enable row level security;

-- Los roles anon y authenticated existen en Supabase; en un PostgreSQL de pruebas pueden no existir.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon')
       and exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on public.pago_con_estado from anon, authenticated;
        revoke all on sequence public.pago_comprobante_seq from anon, authenticated;
        revoke all on function public.siguiente_numero_comprobante() from anon, authenticated;
    end if;
end
$$;

commit;
