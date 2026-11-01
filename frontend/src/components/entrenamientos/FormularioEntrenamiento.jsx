import { useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import Icon from '../Icon.jsx'
import { ENTRENADORES, SEDES } from '../../services/entrenamientosService.js'
import { entrenadorDeCategoria } from '../../services/horarioClubData.js'
import { CATEGORIAS_CON_GRUPO } from '../../utils/horarioClub.js'
import { diaLargo, formatHora, hoyLocal } from '../../utils/fechas.js'
import { MENSAJES, enLista, validarCampos, validarFechaEntrenamiento, validarHoraEntrenamiento, validarHoraFinEntrenamiento } from '../../utils/validaciones.js'

// fechaOriginal: la fecha con la que se abrió el formulario al editar (null al crear). Conservarla no da error aunque ya
// haya pasado; cambiarla a otra fecha pasada sí.
const ENTRENADORES_ACTIVOS = ENTRENADORES.filter((e) => e.activo)
const nombreEntrenador = (id) => ENTRENADORES.find((e) => String(e.id) === String(id))?.nombre ?? 'Por turnos'

function validar(v, categorias, fechaOriginal = null) {
  return validarCampos(v, {
    categoria: [enLista(categorias, MENSAJES.entrenamientoCategoria)],
    // '' = "Por turnos"; solo se ofrecen los activos
    idEntrenador: [enLista(['', ...ENTRENADORES_ACTIVOS.map((e) => String(e.id))], MENSAJES.entrenamientoEntrenador)],
    fecha: [(valor) => validarFechaEntrenamiento(valor, valor === fechaOriginal ? valor : hoyLocal())],
    hora: [validarHoraEntrenamiento],
    horaFin: [(valor, todos) => validarHoraFinEntrenamiento(valor, todos.hora)],
    sede: [enLista(SEDES, MENSAJES.entrenamientoSede)],
  })
}

// Para buscar conflicto solo hacen falta fecha, hora y hora de fin válidas
function tieneHorarioCompleto(v, fechaOriginal) {
  const errores = validar(v, [], fechaOriginal)
  return !errores.fecha && !errores.hora && !errores.horaFin
}

// Formulario para crear y editar. No guarda por sí mismo: llama a onGuardar(valores).
// props: inicial, categorias (las que puede elegir el rol), ignorarId (al editar), detectarConflicto,
//        onGuardar, onCancelar, onEliminar (solo al editar)
export default function FormularioEntrenamiento({ inicial, categorias, ignorarId = null, detectarConflicto, onGuardar, onCancelar, onEliminar }) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [conflicto, setConflicto] = useState(null) // { alternativas } o null
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const comprobacion = useRef(0) // descarta respuestas viejas si el usuario sigue cambiando campos
  const formRef = useRef(null)
  const fechaOriginal = ignorarId === null ? null : inicial.fecha
  const hoy = hoyLocal()
  // Al editar un entrenamiento que ya pasó no se pone mínimo: el navegador no debe rechazar su fecha original
  const fechaMinima = fechaOriginal && fechaOriginal < hoy ? undefined : hoy

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  async function comprobarConflicto(nuevos) {
    const numero = ++comprobacion.current
    if (!tieneHorarioCompleto(nuevos, fechaOriginal)) return setConflicto(null)
    const resultado = await detectarConflicto(nuevos, ignorarId)
    if (numero === comprobacion.current) setConflicto(resultado.hayConflicto ? resultado : null)
  }

  function cambiar(campo, valor) {
    const nuevos = { ...valores, [campo]: valor }
    if (campo === 'categoria') {
      // Al elegir categoría se propone su entrenador responsable (o "Por turnos") y se limpia un grupo que no aplica
      nuevos.idEntrenador = String(entrenadorDeCategoria(valor) ?? '')
      if (!CATEGORIAS_CON_GRUPO.includes(valor)) nuevos.grupo = ''
    }
    setValores(nuevos)
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
    comprobarConflicto(nuevos)
  }

  async function enviar(event) {
    event.preventDefault()
    if (guardando) return
    const encontrados = validar(valores, categorias, fechaOriginal)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    setGuardando(true)
    try {
      const resultado = await detectarConflicto(valores, ignorarId)
      if (resultado.hayConflicto) {
        setConflicto(resultado)
        return
      }
      await onGuardar(valores)
    } catch (error) {
      if (error.conflicto) setConflicto(error.conflicto)
      else throw error
    } finally {
      setGuardando(false)
    }
  }

  // Una alternativa cambia el inicio y el fin a la vez (misma duración)
  function usarAlternativa(alt) {
    const nuevos = { ...valores, hora: alt.hora, horaFin: alt.horaFin }
    setValores(nuevos)
    setErrores({ ...errores, hora: undefined, horaFin: undefined })
    comprobarConflicto(nuevos)
  }

  const describe = conflicto ? 'conflicto' : undefined

  return (
    <form ref={formRef} noValidate onSubmit={enviar} className="flex flex-col gap-4">
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <Field label="Categoría" id="categoria" requerido as="select" value={valores.categoria} error={errores.categoria} onChange={(e) => cambiar('categoria', e.target.value)}>
        <option value="">Selecciona una categoría</option>
        {categorias.map((c) => <option key={c} value={c}>{c}</option>)}
      </Field>
      <Field label="Entrenador" id="idEntrenador" as="select" value={valores.idEntrenador} error={errores.idEntrenador} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('idEntrenador', e.target.value)}>
        <option value="">Por turnos</option>
        {ENTRENADORES_ACTIVOS.map((e) => <option key={e.id} value={String(e.id)}>{e.nombre}</option>)}
      </Field>
      <Field label="Fecha" requerido id="fecha" type="date" min={fechaMinima} value={valores.fecha} error={errores.fecha} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('fecha', e.target.value)} />
      <Field label="Hora de inicio" requerido id="hora" type="time" value={valores.hora} error={errores.hora} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('hora', e.target.value)} />
      <Field label="Hora de fin" requerido id="horaFin" type="time" value={valores.horaFin} error={errores.horaFin} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('horaFin', e.target.value)} />
      <Field label="Sede" requerido id="sede" as="select" value={valores.sede} error={errores.sede} onChange={(e) => cambiar('sede', e.target.value)}>
        <option value="">Selecciona la sede</option>
        {SEDES.map((l) => <option key={l} value={l}>{l}</option>)}
      </Field>
      {CATEGORIAS_CON_GRUPO.includes(valores.categoria) && (
        <Field label="Grupo (opcional)" id="grupo" as="select" value={valores.grupo} onChange={(e) => cambiar('grupo', e.target.value)}>
          <option value="">Sin grupo</option>
          <option value="mañana">Mañana</option>
          <option value="tarde">Tarde</option>
        </Field>
      )}

      {conflicto && (
        <div id="conflicto" role="alert" className="flex flex-col gap-3 rounded-lg border border-warning bg-warning p-3 text-warning">
          <p className="flex flex-wrap items-start gap-2 text-body-medium">
            <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
            <span className="min-w-0 flex-1 basis-40 break-words">
              Conflicto: {nombreEntrenador(valores.idEntrenador)} ya tiene un entrenamiento el {diaLargo(valores.fecha).toLowerCase()} que se cruza con {formatHora(valores.hora)} – {formatHora(valores.horaFin)}.
              {conflicto.alternativas.length > 0 ? ' Horarios disponibles:' : ' No hay horarios alternativos ese día.'}
            </span>
          </p>
          {conflicto.alternativas.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {conflicto.alternativas.map((alt) => {
                const etiqueta = `${formatHora(alt.hora)} – ${formatHora(alt.horaFin)}`
                return (
                  <button
                    key={alt.hora}
                    type="button"
                    aria-label={`Cambiar el horario a ${etiqueta}`}
                    onClick={() => usarAlternativa(alt)}
                    className="min-h-11 rounded-full border bg-surface px-4 text-body-medium text-primary hover:bg-app focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                  >
                    {etiqueta}
                  </button>
                )
              })}
            </div>
          )}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {/* aria-disabled (y no disabled) para que el botón siga siendo enfocable y se pueda leer por qué no guarda */}
        <Button
          type="submit"
          aria-disabled={conflicto ? true : undefined}
          aria-describedby={describe}
          className={`grow ${conflicto ? 'cursor-not-allowed opacity-60' : ''}`}
        >
          {guardando ? 'Guardando…' : 'Guardar y notificar'}
        </Button>
        <Button variant="secondary" onClick={onCancelar} className="grow">Cancelar</Button>
      </div>
      {conflicto && <p className="text-small text-secondary">Resuelve el conflicto para poder guardar.</p>}
      {onEliminar && (
        <Button variant="danger" onClick={onEliminar}>
          <Icon name="trash" className="mr-2 h-5 w-5 shrink-0" />
          Eliminar entrenamiento
        </Button>
      )}
    </form>
  )
}
