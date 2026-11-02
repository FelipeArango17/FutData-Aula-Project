import { useState } from 'react'
import Button from '../ui/Button.jsx'
import Dialogo from '../ui/Dialogo.jsx'
import Field from '../ui/Field.jsx'
import { CATEGORIAS_CON_GRUPO } from '../../utils/horarioClub.js'
import { diaLargo } from '../../utils/fechas.js'
import { MAX_MOTIVO, SEDES_CLUB, textoAviso, validarExtra, validarMotivo } from '../../utils/semana.js'
import { categoriaDeSesion, rangoDeSesion } from './formatoSesion.js'

const CLASE_TEXTAREA = 'min-h-11 w-full rounded-md border bg-surface px-3 py-2 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'
// Diálogos de la pantalla "Horarios". Cada uno monta su formulario solo mientras está abierto, así empieza limpio cada vez.
// Los errores del backend (403, 404, 409…) se muestran tal cual (`mensaje`); los demás, con un texto general.
const textoError = (error, general) => (error?.estado !== undefined && error.mensaje ? error.mensaje : general)

function Botones({ enviando, textoEnviar, onCerrar }) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
      <Button type="submit" disabled={enviando} aria-busy={enviando} className="disabled:opacity-60">{enviando ? 'Guardando…' : textoEnviar}</Button>
      <Button variant="secondary" onClick={onCerrar} disabled={enviando}>Cancelar</Button>
    </div>
  )
}

function MensajeError({ texto }) {
  return <div role="alert">{texto && <p className="break-words text-small text-danger">{texto}</p>}</div>
}

// ---- Cancelar un entrenamiento de una fecha ----
function FormularioCancelar({ sesion, onConfirmar, onCerrar }) {
  const [motivo, setMotivo] = useState('')
  const [errorMotivo, setErrorMotivo] = useState(null)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(event) {
    event.preventDefault()
    if (enviando) return
    const invalido = validarMotivo(motivo)
    setErrorMotivo(invalido)
    if (invalido) return
    setEnviando(true)
    setError(null)
    try {
      await onConfirmar(motivo.trim())
    } catch (e) {
      setError(textoError(e, 'No se pudo cancelar el entrenamiento. Intenta de nuevo.'))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
      <p className="break-words text-body text-secondary">
        {categoriaDeSesion(sesion)} · {diaLargo(sesion.fecha)} · {rangoDeSesion(sesion)} · {sesion.sede}. {textoAviso(sesion.categoria)}
      </p>
      <Field
        label="Motivo (opcional)" id="cancelar-motivo" as="textarea" rows={3} maxLength={MAX_MOTIVO + 50} value={motivo} error={errorMotivo}
        describedBy="cancelar-motivo-ayuda" onChange={(e) => { setMotivo(e.target.value); setErrorMotivo(null) }}
        className={CLASE_TEXTAREA}
      />
      <p id="cancelar-motivo-ayuda" className="text-small text-secondary">{motivo.trim().length} de {MAX_MOTIVO} caracteres</p>
      <MensajeError texto={error} />
      <Botones enviando={enviando} textoEnviar="Cancelar entrenamiento" onCerrar={onCerrar} />
    </form>
  )
}

export function DialogoCancelar({ sesion, onConfirmar, onCerrar }) {
  return (
    <Dialogo open={Boolean(sesion)} titulo="¿Cancelar este entrenamiento?" onCerrar={onCerrar}>
      {sesion && <FormularioCancelar sesion={sesion} onConfirmar={onConfirmar} onCerrar={onCerrar} />}
    </Dialogo>
  )
}

// ---- Cambiar el entrenador de una franja (todas las semanas) ----
function FormularioRotar({ sesion, entrenadores, onConfirmar, onCerrar }) {
  const [valor, setValor] = useState(sesion.idEntrenador === null ? '' : String(sesion.idEntrenador))
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(event) {
    event.preventDefault()
    if (enviando) return
    setEnviando(true)
    setError(null)
    try {
      await onConfirmar(valor === '' ? null : valor)
    } catch (e) {
      setError(e?.errores?.idEntrenador ?? textoError(e, 'No se pudo cambiar el entrenador. Intenta de nuevo.'))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
      <p className="break-words text-body text-secondary">
        {categoriaDeSesion(sesion)} · {diaLargo(sesion.fecha).split(' ')[0]} · {rangoDeSesion(sesion)} · {sesion.sede}.
        {' '}<strong>Este cambio vale para todas las semanas</strong>, no solo para esta.
      </p>
      <Field label="Entrenador" id="rotar-entrenador" as="select" value={valor} error={error} onChange={(e) => { setValor(e.target.value); setError(null) }}>
        <option value="">Por turnos</option>
        {entrenadores
          .filter((e) => e.activo !== false || String(e.id) === valor)
          .map((e) => <option key={e.id} value={String(e.id)}>{e.activo === false ? `${e.nombre} (inactivo)` : e.nombre}</option>)}
      </Field>
      <Botones enviando={enviando} textoEnviar="Cambiar en todas las semanas" onCerrar={onCerrar} />
    </form>
  )
}

export function DialogoCambiarEntrenador({ sesion, entrenadores, onConfirmar, onCerrar }) {
  return (
    <Dialogo open={Boolean(sesion)} titulo="Cambiar entrenador (todas las semanas)" onCerrar={onCerrar}>
      {sesion && <FormularioRotar sesion={sesion} entrenadores={entrenadores} onConfirmar={onConfirmar} onCerrar={onCerrar} />}
    </Dialogo>
  )
}

// ---- Entrenamiento extra (crear y editar) ----
// props: inicial (valores en texto), categorias (las que puede elegir el rol), entrenadores [{ id, nombre, activo }],
//        entrenadorFijo (id del entrenador con sesión: solo puede asignarse a sí mismo; null = elige), hoy,
//        proponerEntrenador(categoria) -> id | null, detectarConflicto(valores) -> { hayConflicto }, onGuardar(valores), onCerrar
export function FormularioExtra({ inicial, categorias, entrenadores, entrenadorFijo, hoy, proponerEntrenador, detectarConflicto, onGuardar, onCerrar, edita }) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  function cambiar(campo, valor) {
    const nuevos = { ...valores, [campo]: valor }
    if (campo === 'categoria') {
      if (entrenadorFijo === null) nuevos.idEntrenador = String(proponerEntrenador(valor) ?? '')
      if (!CATEGORIAS_CON_GRUPO.includes(valor)) nuevos.grupo = ''
    }
    setValores(nuevos)
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event.preventDefault()
    if (enviando) return
    setError(null)
    const encontrados = validarExtra(valores, { categorias, entrenadores, hoy })
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return
    setEnviando(true)
    try {
      if ((await detectarConflicto(valores)).hayConflicto) {
        setErrores({ idEntrenador: 'Ese entrenador ya tiene otra sesión que se cruza con este horario.' })
        return
      }
      await onGuardar(valores)
    } catch (e) {
      if (e?.errores) setErrores(e.errores)
      else setError(textoError(e, 'No se pudo guardar el entrenamiento extra. Intenta de nuevo.'))
    } finally {
      setEnviando(false)
    }
  }

  const campo = (nombre) => ({ value: valores[nombre], error: errores[nombre], onChange: (e) => cambiar(nombre, e.target.value) })
  return (
    <form noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
      <p className="text-small text-secondary">* Campo obligatorio. Puede quedar fuera de las franjas fijas.</p>
      <Field label="Categoría" id="extra-categoria" as="select" requerido {...campo('categoria')}>
        <option value="">Selecciona una categoría</option>
        {categorias.map((c) => <option key={c} value={c}>{c}</option>)}
      </Field>
      <Field label="Fecha" id="extra-fecha" type="date" min={hoy} requerido {...campo('fecha')} />
      <Field label="Hora de inicio" id="extra-hora" type="time" requerido {...campo('hora')} />
      <Field label="Hora de fin" id="extra-horaFin" type="time" requerido {...campo('horaFin')} />
      <Field label="Sede" id="extra-sede" as="select" requerido {...campo('sede')}>
        <option value="">Selecciona la sede</option>
        {SEDES_CLUB.map((s) => <option key={s} value={s}>{s}</option>)}
      </Field>
      <Field label="Entrenador" id="extra-entrenador" as="select" disabled={entrenadorFijo !== null} {...campo('idEntrenador')} error={errores.idEntrenador}>
        {entrenadorFijo === null && <option value="">Por turnos</option>}
        {entrenadores
          .filter((e) => (entrenadorFijo === null ? e.activo !== false : String(e.id) === String(entrenadorFijo)))
          .map((e) => <option key={e.id} value={String(e.id)}>{e.nombre}</option>)}
      </Field>
      {CATEGORIAS_CON_GRUPO.includes(valores.categoria) && (
        <Field label="Grupo (opcional)" id="extra-grupo" as="select" {...campo('grupo')}>
          <option value="">Sin grupo</option>
          <option value="mañana">Mañana</option>
          <option value="tarde">Tarde</option>
        </Field>
      )}
      <Field label="Motivo (opcional)" id="extra-motivo" as="textarea" rows={2} className={CLASE_TEXTAREA} {...campo('motivo')} />
      {valores.categoria && <p className="break-words text-small text-secondary">{textoAviso(valores.categoria)}</p>}
      <MensajeError texto={error} />
      <Botones enviando={enviando} textoEnviar={edita ? 'Guardar cambios' : 'Guardar y avisar'} onCerrar={onCerrar} />
    </form>
  )
}

// `extra`: null (cerrado) | { sesion: null } (nuevo) | { sesion } (editar un extra)
export function DialogoExtra({ extra, ...props }) {
  const edita = Boolean(extra?.sesion)
  return (
    <Dialogo open={Boolean(extra)} titulo={edita ? 'Editar entrenamiento extra' : 'Agregar entrenamiento extra'} onCerrar={props.onCerrar}>
      {extra && <FormularioExtra key={extra.sesion?.clave ?? 'nuevo'} edita={edita} {...props} />}
    </Dialogo>
  )
}
