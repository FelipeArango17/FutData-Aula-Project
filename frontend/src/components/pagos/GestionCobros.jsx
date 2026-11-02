import { useCallback, useEffect, useRef, useState } from 'react'
import Page from '../ui/Page.jsx'
import Card from '../ui/Card.jsx'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import FilterChips from '../ui/FilterChips.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import Paginacion from '../ui/Paginacion.jsx'
import TablaCobros from './TablaCobros.jsx'
import { listarDeportistas } from '../../services/deportistasService.js'
import {
  aprobarPagoManual, conciliarCobro, crearCobro, descargarComprobante, generarMensualidades, listarCobros, rechazarCobro,
} from '../../services/pagosService.js'
import {
  ETIQUETAS_TIPO, METODOS_MANUALES, TIPOS_COBRO, accionesDeCobro, etiquetaMetodo, mensajeDeErrorPagos, validarAprobacionManual, validarNuevoCobro, validarPeriodo,
} from '../../utils/pagosApi.js'
import { descargarBlob } from '../../utils/descargas.js'
import { hoy } from '../../utils/hoy.js'
import { formatearCOP } from '../../utils/dinero.js'
import { mesEnTexto } from '../../utils/fechas.js'

// Gestión de cobros con la API de pagos (backend: "Pagos con Wompi"): lista paginada con filtros por estado, tipo y periodo y las
// acciones de cada fila. La usan el tesorero y el administrador (el administrador además crea cobros y genera mensualidades).
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
  const [panel, setPanel] = useState(null) // { tipo: 'manual' | 'rechazar' | 'crear' | 'generar', cobro? }
  const [confirmar, setConfirmar] = useState(false)
  const [errorPanel, setErrorPanel] = useState(null)
  const [erroresCampos, setErroresCampos] = useState({})
  const [valores, setValores] = useState({})
  const [ocupado, setOcupado] = useState(null)
  const [exito, setExito] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  const [deportistas, setDeportistas] = useState([])

  const clave = `${estado}|${tipo}|${periodo}|${pagina}|${recarga}`

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    listarCobros({ estado: ESTADOS[estado], tipo, periodo, page: pagina - 1, size: TAMANO })
      .then((datos) => activo && setCarga({ clave, datos, error: null }))
      .catch((error) => activo && setCarga({ clave, datos: null, error }))
    return () => {
      activo = false
    }
  }, [estado, tipo, periodo, pagina, clave])

  // El administrador elige a quién crearle un cobro
  useEffect(() => {
    if (!esAdministrador || panel?.tipo !== 'crear' || deportistas.length > 0) return
    listarDeportistas().then(setDeportistas).catch(() => setDeportistas([]))
  }, [esAdministrador, panel, deportistas.length])

  const cargando = carga.clave !== clave
  const datos = carga.datos
  const totalPaginas = Math.max(1, Math.ceil((datos?.total ?? 0) / TAMANO))
  const cobros = datos?.items ?? []
  const mensajeError = !cargando && carga.error ? mensajeDeErrorPagos(carga.error, 'No se pudieron cargar los cobros.') : null

  const cerrarConfirmacion = useCallback(() => setConfirmar(false), [])
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
      : tipoPanel === 'generar' ? { periodo: hoy().slice(0, 7) } : { nota: '' })
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
    if (panel.tipo === 'generar') {
      const motivo = validarPeriodo(valores.periodo)
      if (motivo) errores = { periodo: motivo }
    }
    setErroresCampos(errores)
    if (Object.keys(errores).length > 0) return
    // Aprobar, rechazar y generar mensualidades cambian cobros: piden confirmar. Crear un cobro es reversible (se rechaza).
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
      } else if (panel.tipo === 'crear') {
        await crearCobro({ ...valores, periodo: valores.tipo === 'MENSUALIDAD' ? `${valores.periodo}-01` : undefined })
        setExito({ texto: 'Cobro creado.' })
      } else {
        const r = await generarMensualidades({ periodo: valores.periodo })
        setExito({ texto: `Mensualidades de ${mesEnTexto(`${valores.periodo}-01`)}: ${r.creadas} nuevas y ${r.yaExistentes} que ya existían.` })
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
    generar: {
      titulo: 'Generar mensualidades',
      descripcion: `Se creará la mensualidad de ${panel.tipo === 'generar' && validarPeriodo(valores.periodo) === null ? mesEnTexto(`${valores.periodo}-01`) : ''} para cada deportista que todavía no la tenga. Los que ya la tienen no cambian.`,
      boton: 'Generar',
    },
  }[panel.tipo] ?? {}

  const campoMes = (id, label) => (
    <Field label={label} id={id} type="month" requerido value={valores[id] ?? ''} error={erroresCampos[id]} onChange={(e) => cambiar(id, e.target.value)} />
  )

  return (
    <Page title={titulo} subtitle={subtitulo} tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={errorAccion} rol="alert" tono="danger" enfocar={false} onCerrar={() => setErrorAccion(null)} />
      {esAdministrador && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button onClick={() => abrir('crear')}>Crear cobro</Button>
          <Button variant="secondary" onClick={() => abrir('generar')}>Generar mensualidades</Button>
        </div>
      )}

      {panel && (
        <Card as="section" aria-labelledby="titulo-panel" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="titulo-panel" className="break-words text-h3">
            {{ manual: 'Registrar pago manual', rechazar: 'Rechazar cobro', crear: 'Crear cobro', generar: 'Generar mensualidades' }[panel.tipo]}
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
            {panel.tipo === 'generar' && campoMes('periodo', 'Mes')}
            {panel.tipo === 'crear' && (
              <>
                <Field label="Deportista" id="idDeportista" as="select" requerido value={valores.idDeportista ?? ''} error={erroresCampos.idDeportista} onChange={(e) => cambiar('idDeportista', e.target.value)}>
                  <option value="">Selecciona el deportista</option>
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
                {ocupado === 'panel' ? 'Guardando…' : { manual: 'Registrar pago', rechazar: 'Rechazar cobro', crear: 'Crear cobro', generar: 'Generar' }[panel.tipo]}
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
        <Field label="Periodo (mes)" id="filtro-periodo" type="month" value={periodo} onChange={(e) => filtrar(setPeriodo)(e.target.value)} />
      </div>

      <TablaCobros cargando={cargando} mensajeError={mensajeError} onReintentar={() => setRecarga((n) => n + 1)} cobros={cobros} accionesDe={accionesDe} />
      {!cargando && !mensajeError && (
        <Paginacion
          pagina={pagina}
          totalPaginas={totalPaginas}
          desde={datos?.total ? (pagina - 1) * TAMANO + 1 : 0}
          hasta={(pagina - 1) * TAMANO + cobros.length}
          total={datos?.total ?? 0}
          onCambiar={setPagina}
        />
      )}

      <DialogoConfirmacion open={confirmar} titulo={textoConfirmacion.titulo ?? ''} descripcion={textoConfirmacion.descripcion ?? ''} textoConfirmar={textoConfirmacion.boton ?? 'Confirmar'} onConfirmar={ejecutar} onCancelar={cerrarConfirmacion} />
    </Page>
  )
}
