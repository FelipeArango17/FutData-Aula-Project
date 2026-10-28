import { useEffect, useMemo, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import ListaPagos from '../../components/pagos/ListaPagos.jsx'
import { useRole } from '../../context/RoleContext.js'
import { useAuth } from '../../context/AuthContext.js'
import { obtenerEstadoCuenta, prepararDescargaEstadoCuenta } from '../../services/pagosService.js'
import { ACUDIENTE_DEMO } from '../../services/deportistasService.js'
import { diaLargo } from '../../utils/fechas.js'
import { formatearCOP } from '../../utils/dinero.js'
import { descargarTexto } from '../../utils/descargas.js'

// Estado de cuenta del acudiente (HU-017: tarea 326 pantalla y tarea 329 descarga). Figma 05 · Pagos (Acudiente) y 21 · Pagos (móvil).
// Decisiones:
// - Figma trae Pagar, Comprobante y el panel de Wompi: NO se incluyen (el pago en línea no está en esta tarea).
//   En su lugar va el texto "El pago en línea estará disponible próximamente".
// - Figma elige un jugador con chips; aquí cada deportista tiene su propia tarjeta con su resumen y su historial, porque la
//   historia pide el total pendiente POR deportista. Los chips de estado (Todos / Pendientes / Pagados) sí se respetan.
// - La descarga incluye TODO el estado de cuenta, no solo el filtro activo.
// PENDIENTE: la descarga es un CSV provisional; la versión final será un PDF generado por el backend
// (GET /pagos/estado-cuenta?formato=pdf). El acudiente solo ve a sus deportistas (lo valida el backend con el JWT).

const FILTROS = ['Todos', 'Pendientes y vencidos', 'Pagados']
const cumple = (filtro, pago) =>
  filtro === 'Todos' || (filtro === 'Pagados' ? pago.estado === 'pagado' : pago.estado !== 'pagado')

// "viernes 31 de octubre de 2026"
const fechaLarga = (fecha) => `${diaLargo(fecha).toLowerCase()} de ${fecha.slice(0, 4)}`
const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`

function Mensaje({ icono = 'info', children }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

// Estado general del deportista: vencido > pendiente > al día (texto + ícono)
function EstadoDeportista({ d }) {
  if (d.totalVencido > 0) return <Badge tone="danger">Vencido</Badge>
  if (d.totalPendiente > 0) return <Badge tone="warning">Pendiente</Badge>
  return <Badge tone="success">Al día</Badge>
}

export default function Pagos() {
  const { role } = useRole()
  const { estado: estadoSesion, usuario } = useAuth()
  const tituloRef = useRef(null)
  const zonaPagos = useRef(null)
  const enVuelo = useRef(false)
  const [intento, setIntento] = useState(0)
  // Resultado con el intento que lo produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, estado: null, error: null })
  const [filtro, setFiltro] = useState('Todos')
  const [anuncio, setAnuncio] = useState('')
  const [conTabla, setConTabla] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [exito, setExito] = useState(null)
  const [errorDescarga, setErrorDescarga] = useState(null)

  // Quién consulta: el correo de la sesión verificada (si coincide con el rol activo) o el acudiente de demostración
  const correo = estadoSesion === 'verificada' && usuario?.rol === role && usuario.correo ? usuario.correo : ACUDIENTE_DEMO
  const actor = useMemo(() => ({ rol: role, correo }), [role, correo])

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    obtenerEstadoCuenta(actor)
      .then((estado) => activo && setCarga({ intento, estado, error: null }))
      .catch((error) => activo && setCarga({ intento, estado: null, error }))
    return () => {
      activo = false
    }
  }, [actor, intento])

  // La tabla necesita unos 40 rem de ancho. Si la zona es más angosta (móvil, tablet vertical, texto grande)
  // se usan tarjetas. Se mide en rem, así que depende del tamaño de letra y no solo de los píxeles.
  useEffect(() => {
    const zona = zonaPagos.current
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setConTabla(entrada.contentRect.width / rem >= 40)
    })
    observador.observe(zona)
    return () => observador.disconnect()
  }, [])

  const cargando = carga.intento !== intento
  const estado = carga.estado
  const deportistas = estado?.deportistas ?? []

  function cambiarFiltro(nuevo) {
    setFiltro(nuevo)
    const total = deportistas.reduce((suma, d) => suma + d.pagos.filter((p) => cumple(nuevo, p)).length, 0)
    setAnuncio(plural(total, 'pago', 'pagos'))
  }

  async function descargar() {
    if (enVuelo.current) return
    enVuelo.current = true
    setDescargando(true)
    setExito(null)
    setErrorDescarga(null)
    try {
      const { nombreArchivo, tipo, contenido } = await prepararDescargaEstadoCuenta(actor)
      descargarTexto(nombreArchivo, contenido, tipo, { conBom: true })
      setExito({ texto: 'Descarga iniciada.' })
    } catch {
      setErrorDescarga({ texto: 'No se pudo preparar el estado de cuenta.', accion: { texto: 'Reintentar', onClick: descargar } })
    } finally {
      enVuelo.current = false
      setDescargando(false)
    }
  }

  const avisoError = errorDescarga
    ?? (carga.error && !cargando
      ? {
          texto: carga.error.codigo === 'SIN_PERMISO' ? 'No tienes permiso para ver este estado de cuenta.' : 'No se pudo cargar tu estado de cuenta.',
          accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) },
        }
      : null)

  let contenido
  if (cargando) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando tu estado de cuenta…</p>
      </Mensaje>
    )
  } else if (carga.error) {
    contenido = null
  } else if (deportistas.length === 0) {
    contenido = (
      <Mensaje>
        <p>Todavía no tienes deportistas asociados a tu cuenta, así que no hay pagos que mostrar.</p>
      </Mensaje>
    )
  } else {
    contenido = (
      <>
        <section aria-labelledby="titulo-resumen" className="flex flex-col gap-3 rounded-lg border bg-surface p-4">
          <h2 id="titulo-resumen" className="text-small font-normal text-secondary">Total pendiente</h2>
          <p className="break-words text-kpi">{formatearCOP(estado.totalPendiente)}</p>
          {estado.totalVencido > 0 ? (
            <p className="flex items-start gap-2 rounded-md bg-danger p-3 text-body-medium text-danger">
              <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
              <span className="min-w-0 break-words">Tienes {formatearCOP(estado.totalVencido)} vencidos.</span>
            </p>
          ) : estado.totalPendiente > 0 ? (
            <p className="flex items-start gap-2 rounded-md bg-warning p-3 text-body-medium text-warning">
              <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0" />
              <span className="min-w-0 break-words">Tienes pagos pendientes, ninguno vencido.</span>
            </p>
          ) : (
            <p className="flex items-start gap-2 rounded-md bg-success p-3 text-body-medium text-success">
              <Icon name="check" className="mt-0.5 h-5 w-5 shrink-0" />
              <span className="min-w-0 break-words">Estás al día con todos tus pagos.</span>
            </p>
          )}
          <p className="break-words text-small text-secondary">El pago en línea estará disponible próximamente.</p>
        </section>

        <FilterChips label="Estado del pago" options={FILTROS} value={filtro} onChange={cambiarFiltro} />

        <ul className="grid grid-cols-1 gap-6">
          {deportistas.map((d) => {
            const nombre = `${d.deportista.nombre} ${d.deportista.apellido}`
            const visibles = d.pagos.filter((p) => cumple(filtro, p))
            return (
              <li key={d.deportista.id}>
                <section aria-labelledby={`dep-${d.deportista.id}`} className="flex flex-col gap-4 rounded-lg border bg-surface p-4">
                  <div className="flex flex-wrap items-start gap-3">
                    <Avatar name={nombre} />
                    <div className="flex min-w-0 grow basis-[12rem] flex-col gap-1">
                      <h2 id={`dep-${d.deportista.id}`} className="break-words text-h3">{nombre}</h2>
                      <p className="break-words text-body-medium">Total pendiente: {formatearCOP(d.totalPendiente)}</p>
                      {d.pendienteDesde && <p className="break-words text-body text-secondary">Pendiente desde: {fechaLarga(d.pendienteDesde)}</p>}
                      {d.totalVencido > 0 && <p className="break-words text-body text-danger">Vencido: {formatearCOP(d.totalVencido)}</p>}
                      <div className="flex flex-wrap pt-1"><EstadoDeportista d={d} /></div>
                    </div>
                  </div>
                  {visibles.length === 0 ? (
                    <p className="text-body text-secondary">No hay pagos con este filtro.</p>
                  ) : (
                    <ListaPagos pagos={visibles} nombre={nombre} conTabla={conTabla} fechaLarga={fechaLarga} />
                  )}
                </section>
              </li>
            )
          })}
        </ul>
      </>
    )
  }

  const hayDatos = !cargando && !carga.error && deportistas.length > 0

  return (
    <Page title="Pagos" subtitle="Estado de cuenta de tus jugadores" tituloRef={tituloRef}>
      <p aria-live="polite" className="sr-only">{anuncio}</p>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={avisoError} rol="alert" tono="danger" enfocar={false} />
      <div className="flex flex-col gap-2">
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button variant="secondary" onClick={descargar} disabled={!hayDatos || descargando} aria-busy={descargando} aria-describedby="ayuda-descarga" className="gap-2 disabled:opacity-60">
            <Icon name="download" className="h-5 w-5 shrink-0" />
            {descargando ? 'Preparando…' : 'Descargar estado de cuenta'}
          </Button>
        </div>
        <p id="ayuda-descarga" className="break-words text-small text-secondary">
          {hayDatos || cargando || carga.error
            ? 'Descarga un archivo CSV con todos tus jugadores y todos sus pagos, sin importar el filtro que elijas.'
            : 'No hay deportistas asociados a tu cuenta, así que no hay nada que descargar.'}
        </p>
      </div>
      <div ref={zonaPagos} className="flex min-w-0 flex-col gap-4">{contenido}</div>
    </Page>
  )
}
