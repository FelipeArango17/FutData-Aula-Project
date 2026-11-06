import { useCallback, useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import FilaAsistencia from '../../components/asistencia/FilaAsistencia.jsx'
import FilaLectura from '../../components/asistencia/FilaLectura.jsx'
import SesionesSemana from '../../components/horario/SesionesSemana.jsx'
import { ORDEN_ESTADOS, ESTADOS_ASISTENCIA } from '../../components/asistencia/estados.js'
import { categoriaDeSesion, rangoDeSesion } from '../../components/horario/formatoSesion.js'
import { guardarSesionApi, listarSemanaDeAsistencia, obtenerSesionApi } from '../../services/asistenciaService.js'
import { conObservacionSinEstado, MAX_OBSERVACION_API, describirFallo, puedePasarLista, registrosAEnviar, sesionesParaLista } from '../../utils/asistenciaApi.js'
import { diaLargo, diasDeSemana, rangoSemana, sumarDias } from '../../utils/fechas.js'
import { hoy } from '../../utils/hoy.js'
import { semanaEnCurso } from '../../utils/semana.js'
import { useRole } from '../../context/RoleContext.js'
import { perfilEntrenador } from '../../services/entrenadorService.js'

// Control de asistencia conectado a la API (módulo "asistencia"). Reutiliza la semana del club: se elige una sesión (hoy o pasada, no
// cancelada y con permiso; `puedeGestionar` lo calcula el backend) y se pasa lista. Sin historial ni reportes: el backend no los tiene.
// Dos pasos en la misma ruta: semana -> lista de la sesión elegida.
// "Guardar asistencia" envía solo lo que el usuario marcó en esta visita (y con estado); se puede volver a guardar para corregir.
// Los errores se dicen en palabras del usuario (utils/asistenciaApi.js, describirFallo): 403/404 vuelven a la semana; 400/409 dejan la
// lista como está para no perder lo marcado.

const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`
const nombreDe = (d) => `${d.nombre} ${d.apellido}`.trim()

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

// ---- Lista de una sesión (presentación) ----
// props: sesion (la de la lista), deportistas [{ id, nombre, apellido, estado, observacion }], puedeRegistrar, marcas, abiertas,
//        guardando, aviso (éxito) y error ({ texto, accion? }) para mostrar, handlers.
export function VistaLista({
  sesion, deportistas, puedeRegistrar, marcas, abiertas, guardando, anuncio = '', tituloRef, erroresFila = {},
  onEstado, onObservacion, onAlternarObs, onMarcarTodos, onGuardar, onVolver,
}) {
  const valorDe = (d) => ({ estado: d.estado, observacion: d.observacion, ...marcas[d.id] })
  const aEnviar = registrosAEnviar(marcas).length
  const conteo = (estado) => deportistas.filter((d) => valorDe(d).estado === estado).length
  const sinMarcar = deportistas.filter((d) => !valorDe(d).estado).length
  // Una observación sin estado no se puede enviar: se avisa junto a la fila y no se deja guardar hasta marcar el estado
  const obsSinEstado = new Set(conObservacionSinEstado(deportistas.map((d) => ({ id: d.id, ...valorDe(d) }))).map((v) => v.id))
  return (
    <section aria-labelledby="titulo-lista-asistencia" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 id="titulo-lista-asistencia" ref={tituloRef} tabIndex={-1} className="break-words text-h3 outline-none">{categoriaDeSesion(sesion)}</h2>
        <p className="break-words text-body text-secondary">{diaLargo(sesion.fecha)} · {rangoDeSesion(sesion)} · {sesion.sede}</p>
        {sesion.entrenador && <p className="break-words text-body text-secondary">Entrenador: {sesion.entrenador}</p>}
      </div>
      <p aria-live="polite" className="sr-only">{anuncio}</p>
      {deportistas.length === 0 ? (
        <Mensaje><p>Esta sesión no tiene deportistas activos.</p></Mensaje>
      ) : puedeRegistrar ? (
        <>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button variant="secondary" onClick={onMarcarTodos} disabled={guardando}>Marcar todos presentes</Button>
          </div>
          <ul className="grid grid-cols-1 gap-4" aria-label="Lista de deportistas">
            {deportistas.map((d) => (
              <FilaAsistencia
                key={d.id}
                deportista={d}
                nombre={nombreDe(d)}
                valor={valorDe(d)}
                error={obsSinEstado.has(d.id) ? 'Marca el estado para guardar la observación.' : erroresFila[d.id]}
                maxObservacion={MAX_OBSERVACION_API}
                obsAbierta={abiertas[d.id] ?? Boolean(valorDe(d).observacion)}
                deshabilitado={guardando}
                onEstado={onEstado}
                onObservacion={onObservacion}
                onAlternarObs={onAlternarObs}
              />
            ))}
          </ul>
          <p className="break-words text-body text-secondary">
            {ORDEN_ESTADOS.map((e) => `${ESTADOS_ASISTENCIA[e].plural}: ${conteo(e)}`).join(' · ')} · Sin marcar: {sinMarcar}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            <Button onClick={onGuardar} disabled={guardando || aEnviar === 0 || obsSinEstado.size > 0} aria-busy={guardando} className="disabled:opacity-60">
              {guardando ? 'Guardando…' : 'Guardar asistencia'}
            </Button>
            {!guardando && obsSinEstado.size > 0 && <p className="text-small text-secondary">Marca el estado de quienes tienen observación para guardar.</p>}
            {!guardando && obsSinEstado.size === 0 && aEnviar === 0 && <p className="text-small text-secondary">Marca la asistencia de al menos un deportista para guardar.</p>}
          </div>
        </>
      ) : (
        <>
          <Mensaje><p>No puedes registrar la asistencia de esta sesión. Aquí ves lo que ya quedó guardado.</p></Mensaje>
          <ul className="grid grid-cols-1 gap-4" aria-label="Asistencia guardada">
            {deportistas.map((d) => <FilaLectura key={d.id} nombre={nombreDe(d)} estado={d.estado} observacion={d.observacion} />)}
          </ul>
        </>
      )}
      <Button variant="secondary" onClick={onVolver} disabled={guardando} className="sm:self-start">Volver a la semana</Button>
    </section>
  )
}

// ---- Lista de una sesión (datos y acciones) ----
function ListaDeSesion({ sesion, onVolver, onNoDisponible }) {
  const tituloRef = useRef(null)
  const enVuelo = useRef(false)
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: -1, datos: null, error: null })
  const [marcas, setMarcas] = useState({})
  const [abiertas, setAbiertas] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [exito, setExito] = useState(null)
  const [errorGuardar, setErrorGuardar] = useState(null)
  const [erroresFila, setErroresFila] = useState({})
  const [anuncio, setAnuncio] = useState('')

  useEffect(() => {
    let activo = true
    obtenerSesionApi(sesion)
      .then((datos) => activo && setCarga({ intento, datos, error: null }))
      .catch((error) => {
        if (!activo) return
        const fallo = describirFallo(error)
        if (fallo.accion === 'volver') onNoDisponible(fallo.texto)
        else setCarga({ intento, datos: null, error: fallo })
      })
    return () => {
      activo = false
    }
  }, [sesion, intento, onNoDisponible])

  const cargando = carga.intento !== intento
  const datos = cargando ? null : carga.datos
  useEffect(() => {
    if (datos) tituloRef.current?.focus()
  }, [datos === null]) // eslint-disable-line react-hooks/exhaustive-deps -- solo al aparecer la lista, no en cada guardado

  const base = (id) => datos.deportistas.find((d) => d.id === id)
  const valorDe = (id) => ({ estado: base(id).estado, observacion: base(id).observacion, ...marcas[id] })
  const tocar = (id, parche) => {
    setExito(null)
    setErroresFila((previos) => Object.fromEntries(Object.entries(previos).filter(([clave]) => clave !== id)))
    setMarcas((previas) => ({ ...previas, [id]: { ...valorDe(id), ...parche } }))
  }

  function marcarTodos() {
    const faltan = datos.deportistas.filter((d) => !valorDe(d.id).estado)
    setExito(null)
    setMarcas((previas) => ({ ...previas, ...Object.fromEntries(faltan.map((d) => [d.id, { ...valorDe(d.id), estado: 'presente' }])) }))
    setAnuncio(faltan.length === 0 ? 'Todos los deportistas ya tenían un estado.' : `${plural(faltan.length, 'deportista marcado', 'deportistas marcados')} como presentes.`)
  }

  async function guardar() {
    const registros = registrosAEnviar(marcas)
    if (enVuelo.current || registros.length === 0) return
    enVuelo.current = true
    setGuardando(true)
    setExito(null)
    setErrorGuardar(null)
    setErroresFila({})
    try {
      const nuevos = await guardarSesionApi(sesion, registros)
      setCarga({ intento, datos: nuevos, error: null })
      setMarcas({})
      setExito({ texto: 'Asistencia guardada.' })
    } catch (error) {
      const fallo = describirFallo(error, { alGuardar: true })
      if (fallo.accion === 'volver') onNoDisponible(fallo.texto)
      else {
        const culpable = fallo.posicion ? registros[fallo.posicion - 1]?.idDeportista : null
        if (culpable) setErroresFila({ [culpable]: 'Revisa la asistencia de este deportista.' })
        setErrorGuardar({ texto: fallo.texto, accion: fallo.accion === 'reintentar' ? { texto: 'Reintentar', onClick: guardar } : undefined })
      }
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  let contenido
  if (cargando) contenido = <Mensaje icono="clock"><p role="status">Cargando la lista…</p></Mensaje>
  else if (carga.error) {
    contenido = (
      <>
        <AvisoEstado aviso={{ texto: carga.error.texto, accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }} rol="alert" tono="danger" enfocar={false} />
        <Button variant="secondary" onClick={onVolver} className="sm:self-start">Volver a la semana</Button>
      </>
    )
  } else {
    contenido = (
      <VistaLista
        sesion={{ ...sesion, ...datos.sesion }}
        deportistas={datos.deportistas}
        puedeRegistrar={datos.puedeRegistrar}
        marcas={marcas}
        abiertas={abiertas}
        guardando={guardando}
        anuncio={anuncio}
        tituloRef={tituloRef}
        erroresFila={erroresFila}
        onEstado={(id, estado) => tocar(id, { estado })}
        onObservacion={(id, observacion) => tocar(id, { observacion })}
        onAlternarObs={(id) => setAbiertas((previas) => ({ ...previas, [id]: !(previas[id] ?? Boolean(valorDe(id).observacion)) }))}
        onMarcarTodos={marcarTodos}
        onGuardar={guardar}
        onVolver={onVolver}
      />
    )
  }
  return (
    <>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={errorGuardar} rol="alert" tono="danger" enfocar={false} />
      {contenido}
    </>
  )
}

// ---- Semana ----
export default function AsistenciaApi() {
  const { role } = useRole()
  const zona = useRef(null)
  const hoyTexto = hoy()
  const lunesActual = semanaEnCurso(hoyTexto)
  const [lunes, setLunes] = useState(lunesActual)
  const [intento, setIntento] = useState(0)
  const clave = `${lunes}|${intento}`
  const [carga, setCarga] = useState({ clave: null, sesiones: [], error: false })
  const [elegida, setElegida] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [compacta, setCompacta] = useState(false)
  // El entrenador se identifica con su perfil (GET /usuarios/yo): sus categorías y las franjas que dirige
  const esEntrenador = role === 'entrenador'
  const [intentoPerfil, setIntentoPerfil] = useState(0)
  const [cargaPerfil, setCargaPerfil] = useState({ intento: null, perfil: null, error: false })

  useEffect(() => {
    if (!esEntrenador) return undefined
    let activo = true
    perfilEntrenador()
      .then((perfil) => activo && setCargaPerfil({ intento: intentoPerfil, perfil, error: false }))
      .catch(() => activo && setCargaPerfil({ intento: intentoPerfil, perfil: null, error: true }))
    return () => {
      activo = false
    }
  }, [esEntrenador, intentoPerfil])

  useEffect(() => {
    let activo = true
    listarSemanaDeAsistencia(lunes)
      .then((sesiones) => activo && setCarga({ clave, sesiones, error: false }))
      .catch(() => activo && setCarga({ clave, sesiones: [], error: true }))
    return () => {
      activo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `clave` ya resume lunes e intento
  }, [clave])

  useEffect(() => {
    if (!zona.current || typeof ResizeObserver === 'undefined') return undefined
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setCompacta(entrada.contentRect.width / rem < 48)
    })
    observador.observe(zona.current)
    return () => observador.disconnect()
  }, [elegida])

  const recargar = () => setIntento((n) => n + 1)
  const volver = () => setElegida(null)
  // 403/404: se vuelve a la semana con el mensaje y se recarga (la sesión pudo cambiar)
  const sesionNoDisponible = useCallback((texto) => {
    setElegida(null)
    setAviso({ texto })
    setIntento((n) => n + 1)
  }, [])

  const cargandoPerfil = esEntrenador && cargaPerfil.intento !== intentoPerfil
  const cargando = carga.clave !== clave || cargandoPerfil
  const perfil = esEntrenador && !cargandoPerfil ? cargaPerfil.perfil : null
  const errorPerfil = esEntrenador && !cargandoPerfil && cargaPerfil.error
  // Solo las sesiones de esta cuenta (todas para el administrador; las de sus categorías o franjas para el entrenador)
  const contexto = { rol: role, perfil }
  const visibles = sesionesParaLista(carga.sesiones, contexto)
  const acciones = (s) => ({ pasarLista: puedePasarLista(s, hoyTexto, contexto) })

  if (elegida) {
    return (
      <Page title="Control de asistencia" subtitle="Marca la asistencia de la sesión">
        <ListaDeSesion key={elegida.clave} sesion={elegida} onVolver={volver} onNoDisponible={sesionNoDisponible} />
      </Page>
    )
  }

  let contenido
  if (errorPerfil) {
    contenido = <AvisoEstado aviso={{ texto: 'No se pudo cargar tu perfil de entrenador.', accion: { texto: 'Reintentar', onClick: () => setIntentoPerfil((n) => n + 1) } }} rol="alert" tono="danger" enfocar={false} />
  } else if (esEntrenador && !cargando && !perfil) {
    contenido = <Mensaje><p>Tu cuenta no tiene un entrenador vinculado, por eso no hay entrenamientos para pasar lista.</p></Mensaje>
  } else if (carga.error && !cargando) {
    contenido = <AvisoEstado aviso={{ texto: 'No se pudo cargar la semana.', accion: { texto: 'Reintentar', onClick: recargar } }} rol="alert" tono="danger" enfocar={false} />
  } else if (cargando) {
    contenido = <Mensaje icono="clock"><p role="status">Cargando la semana…</p></Mensaje>
  } else if (visibles.length === 0) {
    contenido = <Mensaje><p>No hay entrenamientos esta semana.</p></Mensaje>
  } else {
    contenido = (
      <SesionesSemana
        dias={diasDeSemana(lunes)}
        sesiones={visibles}
        hoy={hoyTexto}
        modo={compacta ? 'lista' : 'cuadricula'}
        accionesDe={acciones}
        onPasarLista={(s) => { setAviso(null); setElegida(s) }}
      />
    )
  }

  return (
    <Page title="Control de asistencia" subtitle={`Elige un entrenamiento de la semana del ${rangoSemana(lunes)}`}>
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" onCerrar={() => setAviso(null)} />
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => setLunes(sumarDias(lunes, -7))} className="grow md:grow-0">Semana anterior</Button>
        <Button variant="secondary" onClick={() => setLunes(lunesActual)} className="grow md:grow-0">Hoy</Button>
        <Button variant="secondary" onClick={() => setLunes(sumarDias(lunes, 7))} className="grow md:grow-0">Semana siguiente</Button>
      </div>
      <div ref={zona} className="min-w-0">{contenido}</div>
    </Page>
  )
}
