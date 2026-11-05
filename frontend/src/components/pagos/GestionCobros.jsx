import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import Page from '../ui/Page.jsx'
import Card from '../ui/Card.jsx'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import SelectorMes from '../ui/SelectorMes.jsx'
import FilterChips from '../ui/FilterChips.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import Paginacion from '../ui/Paginacion.jsx'
import TablaCobros from './TablaCobros.jsx'
import { listarDeportistas } from '../../services/deportistasService.js'
import {
  aprobarPagoManual, conciliarCobro, crearCobro, descargarComprobante, generarCiclos, listarCobros, listarTodosLosCobros, MAX_PAGINAS_RESUMEN, rechazarCobro,
} from '../../services/pagosService.js'
import {
  ETIQUETAS_TIPO, METODOS_MANUALES, TIPOS_COBRO, accionesDeCobro, etiquetaMetodo, mensajeDeErrorPagos, validarAprobacionManual, validarNuevoCobro,
  CONFIRMACION_GENERAR_CICLOS, MENSAJE_MONTO_NO_CONFIGURADO, NOTA_FILTRO_MES, filtroMesEsLocal, resumenGeneracionCiclos,
} from '../../utils/pagosApi.js'
import { descargarBlob } from '../../utils/descargas.js'
import { hoy } from '../../utils/hoy.js'
import { claveDeLectura, leerCobros } from '../../utils/lecturaCobros.js'
import { formatearCOP } from '../../utils/dinero.js'

// Gestión de cobros con la API de pagos (backend: "Pagos con Wompi"): lista paginada con filtros por estado, tipo y periodo y las
// acciones de cada fila. La usan el tesorero y el administrador (el administrador además crea cobros y genera los cobros del ciclo).
//   Registrar pago manual (fecha y método) y Rechazar piden confirmar. Conciliar solo si el cobro ya se inició en Wompi.
//   Un cobro iniciado en Wompi no se aprueba a mano: el backend responde 409 y se muestra su mensaje.
// El tesorero registra pagos sobre cobros existentes; no crea cobros (eso es solo del administrador).
const TAMANO = 10
const ESTADOS = { Todos: '', Pendientes: 'PENDIENTE', Aprobados: 'APROBADO', Rechazados: 'RECHAZADO' }

export default function GestionCobros({ titulo, subtitulo, esAdministrador = false, estadoInicial = 'Todos' }) {
  const tituloRef = useRef(null)
  const enVuelo = useRef(false)
  const [estado, setEstado] = useState(estadoInicial)
  const [tipo, setTipo] = useState('')
  const [periodo, setPeriodo] = useState('')
  const [pagina, setPagina] = useState(1)
  const [recarga, setRecarga] = useState(0)
  const [carga, setCarga] = useState({ clave: null, datos: null, error: null })
  const [panel, setPanel] = useState(null) // { tipo: 'manual' | 'rechazar' | 'crear', cobro? }
  const [confirmar, setConfirmar] = useState(false)
  const [confirmarCiclos, setConfirmarCiclos] = useState(false)
  const [errorPanel, setErrorPanel] = useState(null)
  const [erroresCampos, setErroresCampos] = useState({})
  const [valores, setValores] = useState({})
  const [ocupado, setOcupado] = useState(null)
  const [exito, setExito] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  // Deportistas ACTIVOS para «Crear cobro». `clave` es el intento con el que se leyeron (distinto del actual = cargando).
  const [reintentoDeportistas, setReintentoDeportistas] = useState(0)
  const [cargaDeportistas, setCargaDeportistas] = useState({ clave: null, lista: [], error: null })

  // Con el filtro de mes por vencimiento se leen todos los cobros una vez por estado + tipo + mes y se pagina aquí (ver lecturaCobros.js):
  // cambiar de página no vuelve a pedirlos. `filtros` no incluye la página en ese modo, así el efecto no se dispara.
  const local = filtroMesEsLocal(tipo, periodo)
  const paginaDeLectura = local ? 0 : pagina
  const clave = claveDeLectura({ estado, tipo, periodo, pagina, recarga })
  const filtros = useMemo(
    () => ({ estado: ESTADOS[estado], tipo, periodo, pagina: paginaDeLectura, tamano: TAMANO }),
    [estado, tipo, periodo, paginaDeLectura],
  )

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    leerCobros(filtros, { listarCobros, listarTodosLosCobros })
      .then((datos) => activo && setCarga({ clave, datos, error: null }))
      .catch((error) => activo && setCarga({ clave, datos: null, error }))
    return () => {
      activo = false
    }
  }, [filtros, clave])

  // El administrador elige a quién crearle un cobro
  // Con la API se recorren todas las páginas (de 100 en 100); se vuelve a leer cada vez que se abre el panel
  const pideDeportistas = esAdministrador && panel?.tipo === 'crear'
  useEffect(() => {
    if (!pideDeportistas) return undefined
    let activo = true
    listarDeportistas({ estado: 'activos' })
      .then((lista) => activo && setCargaDeportistas({ clave: reintentoDeportistas, lista, error: null }))
      .catch((error) => activo && setCargaDeportistas({ clave: reintentoDeportistas, lista: [], error }))
    return () => {
      activo = false
    }
  }, [pideDeportistas, reintentoDeportistas])
  const cargandoDeportistas = cargaDeportistas.clave !== reintentoDeportistas
  const deportistas = cargaDeportistas.lista
  const errorDeportistas = !cargandoDeportistas && cargaDeportistas.error
    ? { texto: cargaDeportistas.error.estado ? cargaDeportistas.error.mensaje : 'No se pudieron cargar los deportistas.', accion: { texto: 'Reintentar', onClick: () => setReintentoDeportistas((n) => n + 1) } }
    : null

  const cargando = carga.clave !== clave
  const datos = carga.datos
  const total = local ? (datos?.items.length ?? 0) : (datos?.total ?? 0)
  const totalPaginas = Math.max(1, Math.ceil(total / TAMANO))
  const cobros = local ? (datos?.items ?? []).slice((pagina - 1) * TAMANO, pagina * TAMANO) : (datos?.items ?? [])
  const mensajeError = !cargando && carga.error ? mensajeDeErrorPagos(carga.error, 'No se pudieron cargar los cobros.') : null

  const cerrarConfirmacion = useCallback(() => setConfirmar(false), [])
  const cerrarConfirmacionCiclos = useCallback(() => setConfirmarCiclos(false), [])
  const cambiar = (campo, valor) => {
    setValores((v) => ({ ...v, [campo]: valor }))
    setErroresCampos((e) => ({ ...e, [campo]: undefined }))
  }
  const filtrar = (poner) => (valor) => {
    poner(valor)
    setPagina(1)
  }

  function abrir(tipoPanel, cobro = null) {
    setExito(null)
    setErrorAccion(null)
    setErrorPanel(null)
    setErroresCampos({})
    setPanel({ tipo: tipoPanel, cobro })
    setValores(tipoPanel === 'manual' ? { fechaPago: hoy(), metodo: '', nota: '' }
      : tipoPanel === 'crear' ? { idDeportista: '', tipo: 'MENSUALIDAD', concepto: '', monto: '', fechaVencimiento: '', periodo: hoy().slice(0, 7) }
      : { nota: '' })
  }
  const cerrarPanel = () => {
    setPanel(null)
    setConfirmar(false)
  }

  function revisar(event) {
    event.preventDefault()
    setErrorPanel(null)
    let errores = {}
    if (panel.tipo === 'manual') errores = validarAprobacionManual(valores, hoy())
    if (panel.tipo === 'crear') errores = validarNuevoCobro(valores)
    setErroresCampos(errores)
    if (Object.keys(errores).length > 0) return
    // Aprobar y rechazar cambian cobros: piden confirmar. Crear un cobro es reversible (se rechaza).
    if (panel.tipo === 'crear') return ejecutar()
    setConfirmar(true)
  }

  async function ejecutar() {
    setConfirmar(false)
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado('panel')
    setErrorPanel(null)
    try {
      if (panel.tipo === 'manual') {
        await aprobarPagoManual(panel.cobro.id, { fechaPago: valores.fechaPago, metodo: valores.metodo, nota: valores.nota })
        setExito({ texto: 'Pago registrado.' })
      } else if (panel.tipo === 'rechazar') {
        await rechazarCobro(panel.cobro.id, valores.nota)
        setExito({ texto: 'Cobro rechazado.' })
      } else {
        await crearCobro({ ...valores, periodo: valores.tipo === 'MENSUALIDAD' ? `${valores.periodo}-01` : undefined })
        setExito({ texto: 'Cobro creado.' })
      }
      setPanel(null)
      setRecarga((n) => n + 1)
    } catch (error) {
      // 409 (por ejemplo "el cobro ya se inició en Wompi") y los demás errores, con su mensaje, dentro del formulario
      setErrorPanel({ texto: mensajeDeErrorPagos(error, 'No se pudo completar la operación. Intenta de nuevo.') })
      if (error.estado === 409 || error.estado === 404) setRecarga((n) => n + 1) // el estado del cobro cambió: se vuelve a leer
    } finally {
      enVuelo.current = false
      setOcupado(null)
    }
  }

  // Generar los cobros del ciclo (solo administrador). Idempotente; el backend puede tardar más de un minuto en arranque en frío.
  async function generarCobrosDelCiclo() {
    setConfirmarCiclos(false)
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado('ciclos')
    setExito(null)
    setErrorAccion(null)
    try {
      const r = await generarCiclos()
      const { texto, conProblemas } = resumenGeneracionCiclos(r)
      if (conProblemas) setErrorAccion({ texto })
      else setExito({ texto })
    } catch (error) {
      setErrorAccion({ texto: error.estado === 409 ? MENSAJE_MONTO_NO_CONFIGURADO : mensajeDeErrorPagos(error, 'No se pudieron generar los cobros. Intenta de nuevo.') })
    } finally {
      enVuelo.current = false
      setOcupado(null)
      setRecarga((n) => n + 1)
    }
  }

  async function sobreFila(cobro, accion, textoExito) {
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado(cobro.id)
    setExito(null)
    setErrorAccion(null)
    try {
      await accion()
      if (textoExito) setExito({ texto: textoExito })
    } catch (error) {
      setErrorAccion({ texto: mensajeDeErrorPagos(error) })
    } finally {
      enVuelo.current = false
      setOcupado(null)
    }
  }

  const conciliar = (cobro) => sobreFila(cobro, async () => {
    const r = await conciliarCobro(cobro.id)
    setRecarga((n) => n + 1)
    return r
  }, 'Cobro conciliado con Wompi.')
  const descargarPdf = (cobro) => sobreFila(cobro, async () => {
    const { nombreArchivo, blob } = await descargarComprobante(cobro.id)
    descargarBlob(nombreArchivo, blob)
  }, 'Descarga del comprobante iniciada.')

  const accionesDe = (cobro) => {
    const a = accionesDeCobro(cobro)
    const enCurso = ocupado === cobro.id
    const nombre = `${cobro.concepto} de ${cobro.nombreDeportista}`
    return (
      <>
        {a.manual && <Button onClick={() => abrir('manual', cobro)} disabled={enCurso} aria-label={`Registrar pago manual: ${nombre}`}>Registrar pago manual</Button>}
        {a.conciliar && <Button variant="secondary" onClick={() => conciliar(cobro)} disabled={enCurso} aria-busy={enCurso} aria-label={`Conciliar con Wompi: ${nombre}`}>{enCurso ? 'Conciliando…' : 'Conciliar'}</Button>}
        {a.rechazar && <Button variant="secondary" onClick={() => abrir('rechazar', cobro)} disabled={enCurso} aria-label={`Rechazar cobro: ${nombre}`}>Rechazar</Button>}
        {a.comprobante && <Button variant="secondary" onClick={() => descargarPdf(cobro)} disabled={enCurso} aria-label={`Descargar comprobante: ${nombre}`}>Descargar comprobante</Button>}
      </>
    )
  }

  const textoConfirmacion = !panel ? {} : {
    manual: {
      titulo: 'Registrar el pago',
      descripcion: `Se registrará como pagado "${panel.cobro?.concepto}" de ${panel.cobro?.nombreDeportista} por ${formatearCOP(panel.cobro?.monto ?? 0)}, con ${etiquetaMetodo(valores.metodo)?.toLowerCase()}. Se genera el comprobante y no se puede deshacer.`,
      boton: 'Registrar pago',
    },
    rechazar: {
      titulo: 'Rechazar el cobro',
      descripcion: `El cobro "${panel.cobro?.concepto}" de ${panel.cobro?.nombreDeportista} quedará rechazado. El acudiente podrá reintentarlo.`,
      boton: 'Rechazar cobro',
    },
  }[panel.tipo] ?? {}

  const campoMes = (id, label) => (
    <SelectorMes label={label} id={id} requerido value={valores[id] ?? ''} error={erroresCampos[id]} onChange={(v) => cambiar(id, v)} />
  )

  return (
    <Page title={titulo} subtitle={subtitulo} tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={errorAccion} rol="alert" tono="danger" enfocar={false} onCerrar={() => setErrorAccion(null)} />
      {esAdministrador && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button onClick={() => abrir('crear')}>Crear cobro</Button>
          <Button variant="secondary" onClick={() => setConfirmarCiclos(true)} disabled={ocupado === 'ciclos'} aria-busy={ocupado === 'ciclos'}>
            {ocupado === 'ciclos' ? 'Generando cobros… puede tardar un minuto' : 'Generar cobros del ciclo'}
          </Button>
        </div>
      )}

      {panel && (
        <Card as="section" aria-labelledby="titulo-panel" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="titulo-panel" className="break-words text-h3">
            {{ manual: 'Registrar pago manual', rechazar: 'Rechazar cobro', crear: 'Crear cobro' }[panel.tipo]}
          </h2>
          {panel.cobro && (
            <p className="break-words text-body text-secondary">{panel.cobro.concepto} · {panel.cobro.nombreDeportista} · {formatearCOP(panel.cobro.monto)}</p>
          )}
          <AvisoEstado aviso={errorPanel} rol="alert" tono="danger" enfocar />
          <form noValidate onSubmit={revisar} className="flex flex-col gap-4">
            {panel.tipo === 'manual' && (
              <>
                <Field label="Fecha del pago" id="fechaPago" type="date" requerido max={hoy()} value={valores.fechaPago ?? ''} error={erroresCampos.fechaPago} onChange={(e) => cambiar('fechaPago', e.target.value)} />
                <Field label="Método" id="metodo" as="select" requerido value={valores.metodo ?? ''} error={erroresCampos.metodo} onChange={(e) => cambiar('metodo', e.target.value)}>
                  <option value="">Selecciona el método</option>
                  {METODOS_MANUALES.map((m) => <option key={m} value={m}>{etiquetaMetodo(m)}</option>)}
                </Field>
                <Field label="Nota interna (opcional)" id="nota" maxLength={200} value={valores.nota ?? ''} onChange={(e) => cambiar('nota', e.target.value)} />
              </>
            )}
            {panel.tipo === 'rechazar' && (
              <Field label="Nota (opcional)" id="nota" maxLength={200} value={valores.nota ?? ''} onChange={(e) => cambiar('nota', e.target.value)} />
            )}
            {panel.tipo === 'crear' && (
              <>
                <AvisoEstado aviso={errorDeportistas} rol="alert" tono="danger" enfocar={false} />
                <Field label="Deportista" id="idDeportista" as="select" requerido value={valores.idDeportista ?? ''} error={erroresCampos.idDeportista} onChange={(e) => cambiar('idDeportista', e.target.value)}>
                  <option value="">{cargandoDeportistas ? 'Cargando deportistas…' : !errorDeportistas && deportistas.length === 0 ? 'No hay deportistas activos' : 'Selecciona el deportista'}</option>
                  {deportistas.map((d) => <option key={d.id} value={d.id}>{d.nombre} {d.apellido}</option>)}
                </Field>
                <Field label="Tipo de cobro" id="tipo" as="select" requerido value={valores.tipo ?? ''} error={erroresCampos.tipo} onChange={(e) => cambiar('tipo', e.target.value)}>
                  {TIPOS_COBRO.map((t) => <option key={t} value={t}>{ETIQUETAS_TIPO[t]}</option>)}
                </Field>
                {valores.tipo === 'MENSUALIDAD' && campoMes('periodo', 'Mes de la mensualidad')}
                <Field label={valores.tipo === 'MENSUALIDAD' ? 'Concepto (opcional)' : 'Concepto'} id="concepto" requerido={valores.tipo !== 'MENSUALIDAD'} maxLength={250} value={valores.concepto ?? ''} error={erroresCampos.concepto} onChange={(e) => cambiar('concepto', e.target.value)} />
                <Field label="Monto en pesos" id="monto" requerido inputMode="numeric" autoComplete="off" value={valores.monto ?? ''} error={erroresCampos.monto} onChange={(e) => cambiar('monto', e.target.value)} />
                <Field label="Fecha de vencimiento" id="fechaVencimiento" type="date" requerido value={valores.fechaVencimiento ?? ''} error={erroresCampos.fechaVencimiento} onChange={(e) => cambiar('fechaVencimiento', e.target.value)} />
              </>
            )}
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button type="submit" disabled={ocupado === 'panel'} aria-busy={ocupado === 'panel'} variant={panel.tipo === 'rechazar' ? 'danger' : 'primary'}>
                {ocupado === 'panel' ? 'Guardando…' : { manual: 'Registrar pago', rechazar: 'Rechazar cobro', crear: 'Crear cobro' }[panel.tipo]}
              </Button>
              <Button variant="secondary" onClick={cerrarPanel}>Cancelar</Button>
            </div>
          </form>
        </Card>
      )}

      <FilterChips label="Estado del cobro" options={Object.keys(ESTADOS)} value={estado} onChange={filtrar(setEstado)} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Tipo de cobro" id="filtro-tipo" as="select" value={tipo} onChange={(e) => filtrar(setTipo)(e.target.value)}>
          <option value="">Todos los tipos</option>
          {TIPOS_COBRO.map((t) => <option key={t} value={t}>{ETIQUETAS_TIPO[t]}</option>)}
        </Field>
        <div className="flex flex-col gap-1">
          <SelectorMes label="Mes" id="filtro-periodo" value={periodo} describedBy="nota-periodo" onChange={filtrar(setPeriodo)} />
          <p id="nota-periodo" className="text-body text-secondary">{NOTA_FILTRO_MES}</p>
        </div>
      </div>

      <TablaCobros cargando={cargando} mensajeError={mensajeError} onReintentar={() => setRecarga((n) => n + 1)} cobros={cobros} accionesDe={accionesDe} />
      {!cargando && !mensajeError && local && datos?.truncado && (
        <p role="status" className="rounded-lg bg-warning p-4 text-body-medium text-warning">
          Hay más de {MAX_PAGINAS_RESUMEN * 100} cobros y solo se leyeron los más recientes: la lista del mes puede estar incompleta.
        </p>
      )}
      {!cargando && !mensajeError && (
        <Paginacion
          pagina={pagina}
          totalPaginas={totalPaginas}
          desde={total ? (pagina - 1) * TAMANO + 1 : 0}
          hasta={(pagina - 1) * TAMANO + cobros.length}
          total={total}
          onCambiar={setPagina}
        />
      )}

      <DialogoConfirmacion open={confirmar} titulo={textoConfirmacion.titulo ?? ''} descripcion={textoConfirmacion.descripcion ?? ''} textoConfirmar={textoConfirmacion.boton ?? 'Confirmar'} onConfirmar={ejecutar} onCancelar={cerrarConfirmacion} />
      <DialogoConfirmacion open={confirmarCiclos} titulo={CONFIRMACION_GENERAR_CICLOS.titulo} descripcion={CONFIRMACION_GENERAR_CICLOS.descripcion} textoConfirmar={CONFIRMACION_GENERAR_CICLOS.boton} onConfirmar={generarCobrosDelCiclo} onCancelar={cerrarConfirmacionCiclos} />
    </Page>
  )
}
