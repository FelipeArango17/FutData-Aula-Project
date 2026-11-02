import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import AccionPago from '../../components/pagos/AccionPago.jsx'
import ContenidoEstadoCuenta from '../../components/pagos/ContenidoEstadoCuenta.jsx'
import { useFotosDeportistas } from '../../hooks/useFotosDeportistas.js'
import { useRole } from '../../context/RoleContext.js'
import { useAuth } from '../../context/AuthContext.js'
import {
  construirUrlCheckout, descargarComprobante, iniciarPagoEnLinea, obtenerEstadoCuenta, pagosPorApi, prepararDescargaEstadoCuenta, reintentarPago,
} from '../../services/pagosService.js'
import { ACUDIENTE_DEMO } from '../../services/deportistasService.js'
import { INTERVALO_SONDEO_MS, LIMITE_SONDEO_MS, buscarPago, cumpleFiltro, evaluarSondeo, mensajeDeErrorPagos } from '../../utils/pagosApi.js'
import { descargarBlob, descargarTexto } from '../../utils/descargas.js'

// Estado de cuenta del acudiente (HU-017: tarea 326 pantalla y tarea 329 descarga). Figma 05 · Pagos (Acudiente) y 21 · Pagos (móvil).
// Con la API de pagos conectada (VITE_API_MODULOS con "pagos"): cada cobro tiene su acción según el estado
//   pendiente o vencido -> "Pagar en línea" (pide los datos del checkout y redirige a Wompi),
//   rechazado -> "Reintentar" y pagado -> "Descargar comprobante".
// Al volver del checkout (Wompi agrega ?id=... a la URL de redirección) se reconsulta cada 3 s, hasta 30 s, mientras el cobro siga
// pendiente: el webhook confirma de forma asíncrona. El cobro que se mandó al checkout se recuerda en sessionStorage.
// Sin la API (datos de ejemplo) no hay acciones y se avisa que el pago en línea llegará después.
// Decisiones:
// - Figma elige un jugador con chips; aquí cada deportista tiene su propia tarjeta con su resumen y su historial, porque la
//   historia pide el total pendiente POR deportista. Los chips de estado (Todos / Pendientes / Pagados) sí se respetan.
// - La descarga del estado de cuenta incluye TODO, no solo el filtro activo, y es un CSV provisional (PENDIENTE: PDF del backend).
//   El acudiente solo ve a sus deportistas (lo valida el backend con el JWT).
const CLAVE_PAGO_EN_CURSO = 'futdata.pagoEnCurso'

const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`
const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms))

function leerPagoEnCurso() {
  try {
    return JSON.parse(sessionStorage.getItem(CLAVE_PAGO_EN_CURSO))?.id ?? null
  } catch {
    return null
  }
}
function guardarPagoEnCurso(id) {
  try {
    if (id) sessionStorage.setItem(CLAVE_PAGO_EN_CURSO, JSON.stringify({ id }))
    else sessionStorage.removeItem(CLAVE_PAGO_EN_CURSO)
  } catch {
    // sin almacenamiento: solo se pierde la reconsulta automática
  }
}

export default function Pagos() {
  const { role } = useRole()
  const fotos = useFotosDeportistas()
  const { estado: estadoSesion, usuario } = useAuth()
  const [parametros] = useSearchParams()
  const conAcciones = pagosPorApi()
  const tituloRef = useRef(null)
  const zonaPagos = useRef(null)
  const enVuelo = useRef(false)
  const [intento, setIntento] = useState(0)
  const [recarga, setRecarga] = useState(0) // vuelve a pedir los datos sin mostrar "Cargando…" otra vez
  // Resultado con el intento que lo produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, estado: null, error: null })
  const [filtro, setFiltro] = useState('Todos')
  const [anuncio, setAnuncio] = useState('')
  const [conTabla, setConTabla] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [exito, setExito] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  const [ocupado, setOcupado] = useState(null) // id del cobro con una acción en curso
  // Cobro que se mandó al checkout y se está esperando: solo si se vuelve de Wompi (?id=...)
  const [esperando, setEsperando] = useState(() => (conAcciones && parametros.has('id') ? { id: leerPagoEnCurso(), desde: Date.now() } : null))

  // Quién consulta (datos de ejemplo): el correo de la sesión verificada (si coincide con el rol activo) o el acudiente de demostración
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
  }, [actor, intento, recarga])

  // Tras volver del checkout: reconsulta cada 3 s hasta 30 s mientras el cobro siga pendiente
  useEffect(() => {
    if (!esperando?.id) return undefined
    let activo = true
    const terminar = (resultado) => {
      setEsperando(null)
      guardarPagoEnCurso(null)
      if (resultado === 'aprobado') setExito({ texto: 'Tu pago fue aprobado. Ya puedes descargar el comprobante.' })
      else if (resultado === 'rechazado') setErrorAccion({ texto: 'Tu pago fue rechazado. Puedes intentarlo de nuevo con "Reintentar".' })
      else if (resultado === 'agotado') setErrorAccion({ texto: 'Todavía no recibimos la confirmación de tu pago. Revisa de nuevo en unos minutos.' })
    }
    ;(async () => {
      while (activo) {
        await esperar(INTERVALO_SONDEO_MS)
        if (!activo) return
        let estado = null
        try {
          estado = await obtenerEstadoCuenta(actor)
        } catch {
          // un fallo de red no corta la espera: se vuelve a intentar hasta el límite
        }
        if (!activo) return
        if (estado) setCarga({ intento, estado, error: null })
        const transcurrido = Date.now() - esperando.desde
        const resultado = estado ? evaluarSondeo(buscarPago(estado, esperando.id), transcurrido) : (transcurrido >= LIMITE_SONDEO_MS ? 'agotado' : 'esperando')
        if (resultado !== 'esperando') return terminar(resultado)
      }
    })()
    return () => {
      activo = false
    }
  }, [esperando, actor, intento])

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
  const pagoEsperado = esperando?.id ? buscarPago(estado, esperando.id) : null
  const confirmando = pagoEsperado && (pagoEsperado.estado === 'pendiente' || pagoEsperado.estado === 'vencido') ? 'Estamos confirmando tu pago…' : null

  function cambiarFiltro(nuevo) {
    setFiltro(nuevo)
    const total = deportistas.reduce((suma, d) => suma + d.pagos.filter((p) => cumpleFiltro(nuevo, p)).length, 0)
    setAnuncio(plural(total, 'pago', 'pagos'))
  }

  async function descargar() {
    if (enVuelo.current) return
    enVuelo.current = true
    setDescargando(true)
    setExito(null)
    setErrorAccion(null)
    try {
      const { nombreArchivo, tipo, contenido } = await prepararDescargaEstadoCuenta(actor)
      descargarTexto(nombreArchivo, contenido, tipo, { conBom: true })
      setExito({ texto: 'Descarga iniciada.' })
    } catch {
      setErrorAccion({ texto: 'No se pudo preparar el estado de cuenta.', accion: { texto: 'Reintentar', onClick: descargar } })
    } finally {
      enVuelo.current = false
      setDescargando(false)
    }
  }

  // Corre una acción sobre un cobro: una a la vez, con aviso de éxito o de error
  const sobreCobro = useCallback(async (pago, accion, textoError) => {
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado(pago.id)
    setExito(null)
    setErrorAccion(null)
    try {
      await accion()
    } catch (error) {
      setOcupado(null)
      setErrorAccion({ texto: mensajeDeErrorPagos(error, textoError) })
    } finally {
      enVuelo.current = false
    }
  }, [])

  const pagar = (pago) => sobreCobro(pago, async () => {
    const datos = await iniciarPagoEnLinea(pago.id)
    guardarPagoEnCurso(pago.id)
    window.location.assign(construirUrlCheckout(datos)) // la página se va a Wompi: el cobro sigue "ocupado" hasta entonces
  }, 'No se pudo abrir el pago en línea. Intenta de nuevo.')

  const reintentar = (pago) => sobreCobro(pago, async () => {
    await reintentarPago(pago.id)
    setOcupado(null)
    setExito({ texto: 'Creamos un cobro nuevo para que puedas pagar.' })
    setRecarga((n) => n + 1)
  }, 'No se pudo preparar el reintento. Intenta de nuevo.')

  const descargarPdf = (pago) => sobreCobro(pago, async () => {
    const { nombreArchivo, blob } = await descargarComprobante(pago.id)
    descargarBlob(nombreArchivo, blob)
    setOcupado(null)
    setExito({ texto: 'Descarga del comprobante iniciada.' })
  }, 'No se pudo descargar el comprobante. Intenta de nuevo.')

  const accionesDe = conAcciones
    ? (pago) => <AccionPago pago={pago} ocupado={ocupado} onPagar={pagar} onReintentar={reintentar} onDescargar={descargarPdf} />
    : undefined

  let mensajeError = null
  if (carga.error && !cargando) {
    if (conAcciones) mensajeError = mensajeDeErrorPagos(carga.error, 'No se pudo cargar tu estado de cuenta.')
    else mensajeError = carga.error.codigo === 'SIN_PERMISO' ? 'No tienes permiso para ver este estado de cuenta.' : 'No se pudo cargar tu estado de cuenta.'
  }
  const hayDatos = !cargando && !carga.error && deportistas.length > 0

  return (
    <Page title="Pagos" subtitle="Estado de cuenta de tus jugadores" tituloRef={tituloRef}>
      <p aria-live="polite" className="sr-only">{anuncio}</p>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={errorAccion} rol="alert" tono="danger" enfocar={false} onCerrar={() => setErrorAccion(null)} />
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
      <div ref={zonaPagos} className="flex min-w-0 flex-col gap-4">
        <ContenidoEstadoCuenta
          cargando={cargando}
          mensajeError={mensajeError}
          onReintentar={() => setIntento((n) => n + 1)}
          estado={estado}
          filtro={filtro}
          onFiltro={cambiarFiltro}
          conTabla={conTabla}
          fotos={fotos}
          accionesDe={accionesDe}
          confirmando={confirmando}
        />
      </div>
    </Page>
  )
}
