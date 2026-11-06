import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Icon from '../../components/Icon.jsx'
import FilaAsistencia from '../../components/asistencia/FilaAsistencia.jsx'
import FilaLectura from '../../components/asistencia/FilaLectura.jsx'
import HistorialCambios from '../../components/asistencia/HistorialCambios.jsx'
import { ESTADOS_ASISTENCIA, ORDEN_ESTADOS } from '../../components/asistencia/estados.js'
import { useFotosDeportistas } from '../../hooks/useFotosDeportistas.js'
import { useRole } from '../../context/RoleContext.js'
import { useAuth } from '../../context/AuthContext.js'
import AsistenciaApi from './AsistenciaApi.jsx'
import { asistenciaPorApi, editarAsistencia, listarEntrenamientosParaAsistencia, obtenerAsistencia, registrarAsistencia } from '../../services/asistenciaService.js'
import { ENTRENADORES, ID_ENTRENADOR_EJEMPLO } from '../../services/entrenamientosService.js'
import { diaLargo, formatHora } from '../../utils/fechas.js'

// Control de asistencia (HU-021 registrar, HU-022 editar). Una sola pantalla para entrenador y administrador
// (rutas /entrenador/asistencia y /administrador/asistencia; Figma 07 · Control de asistencia y AD10).
// Modos: captura (sin registro) -> lectura (registrada) -> edición (corrige y deja historial).
//
// Decisiones:
// - "Marcar todos presentes" SOLO marca a quienes aún no tienen estado; no pisa lo ya marcado (ni en edición).
// - Los entrenamientos futuros aparecen en el selector (grupo "Próximos") con un aviso y sin lista: no se puede registrar.
// - Figma tiene tres campos de solo lectura (Fecha, Categoría, Entrenador) y "Exportar PDF"; aquí van juntos en un
//   selector de entrenamiento y el encabezado de la lista. "Exportar PDF" no se incluye (sin función todavía).
// - ?entrenamiento=ID abre ese entrenamiento; si el rol no puede gestionarlo, se avisa en vez de mostrarlo.
// PENDIENTE: permisos y responsable los valida el backend con el JWT (aquí, asistenciaService simula RF008).

const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`
const clave = (e) => `${e.fecha}T${e.hora}`
const vacio = { estado: '', observacion: '' }
const sesionVacia = { editando: false, valores: {}, abiertas: {}, faltan: [], sinCambios: false }

const mensajeDeError = (e) =>
  ({
    SIN_PERMISO: 'No tienes permiso para gestionar la asistencia de este grupo.',
    ENTRENAMIENTO_FUTURO: 'Este entrenamiento aún no ocurre, todavía no se puede registrar.',
    YA_REGISTRADA: 'Otra persona ya registró la asistencia de este entrenamiento. Recarga para verla.',
  })[e?.codigo]

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

// "el miércoles 21 de octubre a las 7:05 p. m."
const momento = (fechaHora) => {
  const [fecha, hora] = fechaHora.split('T')
  return `el ${diaLargo(fecha).toLowerCase()} a las ${formatHora(hora)}`
}
const etiquetaMomento = (fechaHora) => {
  const [fecha, hora] = fechaHora.split('T')
  return `${diaLargo(fecha)}, ${formatHora(hora)}`
}

// Con el módulo "asistencia" encendido (VITE_API_MODULOS) se usa la pantalla de la API; apagado, queda la de ejemplo de abajo.
export default function Asistencia() {
  return asistenciaPorApi() ? <AsistenciaApi /> : <AsistenciaEjemplo />
}

function AsistenciaEjemplo() {
  const { role } = useRole()
  const fotos = useFotosDeportistas()
  const { estado: estadoSesion, usuario } = useAuth()
  const [params] = useSearchParams()
  const tituloRef = useRef(null)
  const enVuelo = useRef(false)

  const [intento, setIntento] = useState(0)
  const [elegido, setElegido] = useState(() => (params.get('entrenamiento') ? Number(params.get('entrenamiento')) : null))
  // Resultados con la clave de la petición que los produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, lista: [], error: null })
  const [detalle, setDetalle] = useState({ clave: null, data: null, error: null })
  const [sesion, setSesion] = useState({ clave: null, ...sesionVacia })
  const [guardando, setGuardando] = useState(false)
  const [exito, setExito] = useState(null)
  const [errorGuardar, setErrorGuardar] = useState(null)
  const [confirmarDescartar, setConfirmarDescartar] = useState(false)
  const [anuncio, setAnuncio] = useState('')
  const [foco, setFoco] = useState(null)

  // Quién actúa: el responsable del registro es el nombre de la sesión verificada (si coincide con el rol activo)
  const nombreActor = estadoSesion === 'verificada' && usuario?.rol === role && usuario.nombre
    ? usuario.nombre
    : role === 'entrenador'
      ? ENTRENADORES.find((e) => e.id === ID_ENTRENADOR_EJEMPLO).nombre
      : 'Administrador'
  const actor = useMemo(
    () => ({ rol: role, idEntrenador: role === 'entrenador' ? ID_ENTRENADOR_EJEMPLO : null, nombre: nombreActor }),
    [role, nombreActor],
  )

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  // Entrenamientos que el rol puede gestionar (el entrenador solo ve los de sus categorías)
  useEffect(() => {
    let activo = true
    listarEntrenamientosParaAsistencia({ actor, incluirFuturos: true })
      .then((lista) => activo && setCarga({ intento, lista, error: null }))
      .catch((error) => activo && setCarga({ intento, lista: [], error }))
    return () => {
      activo = false
    }
  }, [actor, intento])

  const cargandoLista = carga.intento !== intento
  const lista = carga.lista
  const recientes = lista.filter((e) => !e.futuro).sort((a, b) => clave(b).localeCompare(clave(a)))
  const proximos = lista.filter((e) => e.futuro).sort((a, b) => clave(a).localeCompare(clave(b)))
  const porDefecto = recientes[0]?.id ?? proximos[0]?.id ?? null
  const noDisponible = !cargandoLista && !carga.error && elegido !== null && !lista.some((e) => e.id === elegido)
  const idSel = noDisponible ? null : (elegido ?? porDefecto)
  const claveDetalle = idSel === null ? null : `${intento}|${idSel}`

  // Detalle del entrenamiento elegido: deportistas del grupo y registro (si existe)
  useEffect(() => {
    if (idSel === null) return
    let activo = true
    const k = `${intento}|${idSel}`
    obtenerAsistencia(idSel, actor)
      .then((data) => activo && setDetalle({ clave: k, data, error: null }))
      .catch((error) => activo && setDetalle({ clave: k, data: null, error }))
    return () => {
      activo = false
    }
  }, [idSel, actor, intento])

  const detalleActual = detalle.clave === claveDetalle ? detalle : null
  const data = detalleActual?.data ?? null
  const registro = data?.registro ?? null
  const deportistas = useMemo(() => data?.deportistas ?? [], [data])
  const nombreDe = (d) => `${d.nombre} ${d.apellido}`
  const nombres = Object.fromEntries(deportistas.map((d) => [d.id, nombreDe(d)]))

  const sesionActual = sesion.clave === claveDetalle ? sesion : { clave: claveDetalle, ...sesionVacia }
  const modo = registro ? (sesionActual.editando ? 'edicion' : 'lectura') : 'captura'
  const editable = modo !== 'lectura'

  function actualizarSesion(parche) {
    setSesion((previa) => {
      const base = previa.clave === claveDetalle ? previa : { clave: claveDetalle, ...sesionVacia }
      return { ...base, ...(typeof parche === 'function' ? parche(base) : parche) }
    })
  }

  // Valor mostrado de un deportista: lo que se tocó en la sesión, o lo ya registrado, o vacío
  const itemRegistrado = (id) => registro?.items.find((i) => i.idDeportista === id)
  const valorDe = (id) => ({ ...(itemRegistrado(id) ?? vacio), ...sesionActual.valores[id] })

  // Cambios reales frente a lo registrado (solo en edición)
  const cambiosReales = () =>
    deportistas
      .filter((d) => itemRegistrado(d.id))
      .map((d) => ({ idDeportista: d.id, ...valorDe(d.id), observacion: valorDe(d.id).observacion.trim() }))
      .filter((v) => {
        const antes = itemRegistrado(v.idDeportista)
        return antes.estado !== v.estado || antes.observacion !== v.observacion
      })

  useEffect(() => {
    if (!foco) return
    document.getElementById(foco.id)?.focus()
  }, [foco])

  function cambiarEntrenamiento(e) {
    setElegido(Number(e.target.value))
    setExito(null)
    setErrorGuardar(null)
  }

  function elegirEstado(id, estado) {
    actualizarSesion((base) => ({
      valores: { ...base.valores, [id]: { ...valorDe(id), estado } },
      faltan: base.faltan.filter((f) => f !== id),
      sinCambios: false,
    }))
  }

  function escribirObservacion(id, observacion) {
    actualizarSesion((base) => ({ valores: { ...base.valores, [id]: { ...valorDe(id), observacion } }, sinCambios: false }))
  }

  const alternarObservacion = (id) =>
    actualizarSesion((base) => ({ abiertas: { ...base.abiertas, [id]: !(base.abiertas[id] ?? Boolean(valorDe(id).observacion)) } }))

  function marcarTodos() {
    const sinMarcar = deportistas.filter((d) => !valorDe(d.id).estado)
    actualizarSesion((base) => ({
      valores: { ...base.valores, ...Object.fromEntries(sinMarcar.map((d) => [d.id, { ...valorDe(d.id), estado: 'presente' }])) },
      faltan: [],
      sinCambios: false,
    }))
    setAnuncio(
      sinMarcar.length === 0
        ? 'Todos los deportistas ya tenían un estado.'
        : `${plural(sinMarcar.length, 'deportista marcado', 'deportistas marcados')} como presentes.`,
    )
  }

  function iniciarEdicion() {
    setExito(null)
    setErrorGuardar(null)
    actualizarSesion({ editando: true })
    setFoco({ id: 'titulo-lista-asistencia' })
  }

  function descartar() {
    actualizarSesion({ ...sesionVacia })
    setConfirmarDescartar(false)
    setErrorGuardar(null)
    setFoco({ id: 'boton-editar-asistencia' })
  }
  const cerrarConfirmacion = useCallback(() => setConfirmarDescartar(false), [])

  function cancelarEdicion() {
    if (cambiosReales().length > 0) setConfirmarDescartar(true)
    else descartar()
  }

  async function guardar() {
    if (enVuelo.current) return
    setExito(null)
    let operacion
    if (modo === 'captura') {
      const faltan = deportistas.filter((d) => !valorDe(d.id).estado).map((d) => d.id)
      if (faltan.length > 0) {
        actualizarSesion({ faltan })
        setErrorGuardar(null)
        setFoco({ id: `radio-${faltan[0]}-presente` })
        return
      }
      operacion = () => registrarAsistencia(idSel, deportistas.map((d) => ({ idDeportista: d.id, ...valorDe(d.id) })), actor)
    } else {
      const cambios = cambiosReales()
      if (cambios.length === 0) {
        actualizarSesion({ sinCambios: true })
        return
      }
      operacion = () => editarAsistencia(idSel, cambios, actor)
    }
    enVuelo.current = true
    setGuardando(true)
    setErrorGuardar(null)
    try {
      const nuevo = await operacion()
      setDetalle((previo) => ({ ...previo, data: { ...previo.data, registro: nuevo } }))
      actualizarSesion({ ...sesionVacia })
      setExito({ texto: modo === 'captura' ? 'Asistencia registrada.' : 'Asistencia actualizada.' })
    } catch (error) {
      if (error.codigo === 'SIN_CAMBIOS') actualizarSesion({ sinCambios: true })
      else if (error.codigo === 'YA_REGISTRADA') {
        setErrorGuardar({ texto: mensajeDeError(error), accion: { texto: 'Recargar', onClick: () => setIntento((n) => n + 1) } })
      } else {
        setErrorGuardar({

          texto: mensajeDeError(error) ?? 'No se pudo guardar la asistencia.',
          accion: { texto: 'Reintentar', onClick: guardar },
        })
      }
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  // Resumen: del registro (lectura) o de lo marcado (captura y edición)
  const conteo = (estado) => deportistas.filter((d) => (modo === 'lectura' ? itemRegistrado(d.id)?.estado : valorDe(d.id).estado) === estado).length
  const sinMarcar = editable ? deportistas.filter((d) => !valorDe(d.id).estado).length : 0

  const reintentar = { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) }
  const faltan = sesionActual.faltan
  let avisoError = null
  if (errorGuardar) avisoError = errorGuardar
  else if (detalleActual?.error) avisoError = { texto: mensajeDeError(detalleActual.error) ?? 'No se pudo cargar la asistencia de este entrenamiento.', accion: reintentar }
  else if (carga.error && !cargandoLista) avisoError = { texto: mensajeDeError(carga.error) ?? 'No se pudieron cargar los entrenamientos.', accion: reintentar }
  else if (faltan.length > 0) avisoError = { texto: `Falta marcar la asistencia de ${plural(faltan.length, 'deportista', 'deportistas')}. Revisa las filas señaladas.` }

  const etiquetaOpcion = (e) =>
    `${diaLargo(e.fecha)} · ${formatHora(e.hora)} · ${e.categoria}${e.grupo ? ` (${e.grupo})` : ''} · ${e.sede}${e.registrada ? ' · Registrada' : ''}`
  const entrenamiento = data?.entrenamiento

  let contenido
  if (cargandoLista) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando entrenamientos…</p>
      </Mensaje>
    )
  } else if (carga.error) {
    contenido = null
  } else if (noDisponible) {
    contenido = (
      <Mensaje>
        <p role="alert">Ese entrenamiento no está disponible para tu cuenta.</p>
      </Mensaje>
    )
  } else if (lista.length === 0) {
    contenido = (
      <Mensaje>
        <p>No hay entrenamientos disponibles para registrar asistencia.</p>
      </Mensaje>
    )
  } else if (!detalleActual) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando asistencia…</p>
      </Mensaje>
    )
  } else if (detalleActual.error) {
    contenido = null
  } else if (entrenamiento.futuro) {
    contenido = (
      <Mensaje icono="alert">
        <p role="alert">
          Este entrenamiento aún no ocurre ({diaLargo(entrenamiento.fecha)}, {formatHora(entrenamiento.hora)}). La asistencia se podrá registrar cuando llegue su fecha.
        </p>
      </Mensaje>
    )
  } else if (deportistas.length === 0) {
    contenido = (
      <Mensaje>
        <p>El grupo {entrenamiento.categoria} no tiene deportistas activos, así que no hay asistencia que registrar.</p>
      </Mensaje>
    )
  } else {
    contenido = (
      <div className="flex flex-wrap items-start gap-6">
        <section aria-labelledby="titulo-lista-asistencia" className="flex min-w-0 grow basis-[24rem] flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 id="titulo-lista-asistencia" tabIndex={-1} className="break-words text-h3 outline-none">
              {entrenamiento.categoria}{entrenamiento.grupo ? ` (${entrenamiento.grupo})` : ''} · {entrenamiento.sede} · {formatHora(entrenamiento.hora)}
              {modo === 'edicion' && ' · Editando'}
            </h2>
            <p className="break-words text-body text-secondary">
              {diaLargo(entrenamiento.fecha)} · Entrenador: {entrenamiento.entrenador}
            </p>
            {registro && (
              <p className="break-words text-body text-secondary">
                Registrada por {registro.registradoPor} {momento(registro.registradoEn)}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            {editable && (
              <Button variant="secondary" onClick={marcarTodos} disabled={guardando}>
                Marcar todos presentes
              </Button>
            )}
            {modo === 'lectura' && data.puedeEditar && (
              <Button id="boton-editar-asistencia" variant="secondary" onClick={iniciarEdicion}>
                Editar asistencia
              </Button>
            )}
          </div>
          {modo === 'lectura' ? (
            <ul className="grid grid-cols-1 gap-4" aria-label="Asistencia registrada">
              {deportistas.map((d) => (
                <FilaLectura key={d.id} nombre={nombreDe(d)} foto={fotos[d.id]} estado={itemRegistrado(d.id).estado} observacion={itemRegistrado(d.id).observacion} />
              ))}
            </ul>
          ) : (
            <ul className="grid grid-cols-1 gap-4" aria-label="Lista de deportistas">
              {deportistas.map((d) => (
                <FilaAsistencia
                  key={d.id}
                  deportista={d}
                  nombre={nombreDe(d)}
                  foto={fotos[d.id]}
                  valor={valorDe(d.id)}
                  error={faltan.includes(d.id) ? `Falta marcar la asistencia de ${nombreDe(d)}.` : undefined}
                  obsAbierta={sesionActual.abiertas[d.id] ?? Boolean(valorDe(d.id).observacion)}
                  deshabilitado={guardando}
                  onEstado={elegirEstado}
                  onObservacion={escribirObservacion}
                  onAlternarObs={alternarObservacion}
                />
              ))}
            </ul>
          )}
          <div role="status">
            {sesionActual.sinCambios && (
              <p className="flex items-center gap-2 text-body-medium">
                <Icon name="info" className="h-5 w-5 shrink-0" />
                No hiciste ningún cambio.
              </p>
            )}
          </div>
          {modo === 'captura' && (
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button onClick={guardar} disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
                {guardando ? 'Guardando…' : 'Guardar asistencia'}
              </Button>
            </div>
          )}
          {modo === 'edicion' && (
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button onClick={guardar} disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
                {guardando ? 'Guardando…' : 'Guardar cambios'}
              </Button>
              <Button variant="secondary" onClick={cancelarEdicion} disabled={guardando}>
                Cancelar
              </Button>
            </div>
          )}
          <p className="break-words text-body text-secondary">¿Un jugador no aparece en la lista? Repórtalo al administrador.</p>
          {registro && data.puedeEditar && <HistorialCambios historial={registro.historial} nombres={nombres} momento={etiquetaMomento} />}
        </section>

        <aside aria-labelledby="titulo-resumen" className="flex min-w-0 grow basis-[16rem] flex-col gap-3 rounded-lg border bg-surface p-4 lg:max-w-[18rem]">
          <h2 id="titulo-resumen" className="break-words text-h3">Resumen</h2>
          <ul className="flex flex-col gap-2">
            {ORDEN_ESTADOS.map((estado) => {
              const { plural: etiqueta, icono, clases } = ESTADOS_ASISTENCIA[estado]
              return (
                <li key={estado} className={`flex flex-wrap items-center justify-between gap-x-2 rounded-md border px-3 py-2 ${clases}`}>
                  <span className="flex min-w-0 items-center gap-2">
                    <Icon name={icono} className="h-4 w-4 shrink-0" />
                    <span className="min-w-0 break-words text-body-medium">{etiqueta}</span>
                  </span>
                  <span className="text-kpi">{conteo(estado)}</span>
                </li>
              )
            })}
            {editable && (
              <li className="flex flex-wrap items-center justify-between gap-x-2 rounded-md border bg-app px-3 py-2 text-secondary">
                <span className="flex min-w-0 items-center gap-2">
                  <Icon name="info" className="h-4 w-4 shrink-0" />
                  <span className="min-w-0 break-words text-body-medium">Sin marcar</span>
                </span>
                <span className="text-kpi">{sinMarcar}</span>
              </li>
            )}
          </ul>
          <p className="break-words text-small text-secondary">Al guardar se registra fecha, hora, responsable y observaciones.</p>
        </aside>
      </div>
    )
  }

  return (
    <Page title="Control de asistencia" subtitle="Registra la asistencia del entrenamiento de hoy" tituloRef={tituloRef}>
      <p aria-live="polite" className="sr-only">{anuncio}</p>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={avisoError} rol="alert" tono="danger" enfocar={false} />
      {!cargandoLista && lista.length > 0 && (
        <Field label="Entrenamiento" id="entrenamiento" as="select" value={idSel ?? porDefecto ?? ''} onChange={cambiarEntrenamiento} disabled={guardando}>
          <optgroup label="Recientes y de hoy">
            {recientes.map((e) => (
              <option key={e.id} value={e.id}>{etiquetaOpcion(e)}</option>
            ))}
          </optgroup>
          {proximos.length > 0 && (
            <optgroup label="Próximos">
              {proximos.map((e) => (
                <option key={e.id} value={e.id}>{etiquetaOpcion(e)}</option>
              ))}
            </optgroup>
          )}
        </Field>
      )}
      {contenido}
      <DialogoConfirmacion
        open={confirmarDescartar}
        titulo="¿Descartar los cambios?"
        descripcion="Los cambios que hiciste en esta asistencia no se guardarán."
        textoConfirmar="Sí, descartar"
        onConfirmar={descartar}
        onCancelar={cerrarConfirmacion}
      />
    </Page>
  )
}
