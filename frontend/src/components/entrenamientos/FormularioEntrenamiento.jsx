import { useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import Icon from '../Icon.jsx'
import { LUGARES, ENTRENADORES } from '../../services/entrenamientosService.js'
import { diaLargo, formatHora } from '../../utils/fechas.js'
import { MENSAJES, enLista, validarCampos, validarFechaEntrenamiento, validarHoraEntrenamiento } from '../../utils/validaciones.js'

function validar(v, categorias) {
  return validarCampos(v, {
    categoria: [enLista(categorias, MENSAJES.entrenamientoCategoria)],
    idEntrenador: [enLista(ENTRENADORES.map((e) => String(e.id)), MENSAJES.entrenamientoEntrenador)],
    fecha: [validarFechaEntrenamiento],
    hora: [validarHoraEntrenamiento],
    lugar: [enLista(LUGARES, MENSAJES.entrenamientoLugar)],
  })
}

// Para buscar conflicto solo hacen falta fecha, hora y lugar válidos
function tieneHorarioCompleto(v) {
  const errores = validar(v, [])
  return !errores.fecha && !errores.hora && !errores.lugar
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

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  async function comprobarConflicto(nuevos) {
    const numero = ++comprobacion.current
    if (!tieneHorarioCompleto(nuevos)) return setConflicto(null)
    const resultado = await detectarConflicto(nuevos, ignorarId)
    if (numero === comprobacion.current) setConflicto(resultado.hayConflicto ? resultado : null)
  }

  function cambiar(campo, valor) {
    const nuevos = { ...valores, [campo]: valor }
    setValores(nuevos)
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
    comprobarConflicto(nuevos)
  }

  async function enviar(event) {
    event.preventDefault()
    if (guardando) return
    const encontrados = validar(valores, categorias)
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

  const describe = conflicto ? 'conflicto' : undefined

  return (
    <form ref={formRef} noValidate onSubmit={enviar} className="flex flex-col gap-4">
      <Field label="Categoría" id="categoria" as="select" value={valores.categoria} error={errores.categoria} onChange={(e) => cambiar('categoria', e.target.value)}>
        <option value="">Selecciona una categoría</option>
        {categorias.map((c) => <option key={c} value={c}>{c}</option>)}
      </Field>
      <Field label="Entrenador" id="idEntrenador" as="select" value={valores.idEntrenador} error={errores.idEntrenador} onChange={(e) => cambiar('idEntrenador', e.target.value)}>
        <option value="">Selecciona un entrenador</option>
        {ENTRENADORES.map((e) => <option key={e.id} value={String(e.id)}>{e.nombre}</option>)}
      </Field>
      <Field label="Fecha" id="fecha" type="date" value={valores.fecha} error={errores.fecha} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('fecha', e.target.value)} />
      <Field label="Hora" id="hora" type="time" value={valores.hora} error={errores.hora} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('hora', e.target.value)} />
      <Field label="Lugar" id="lugar" as="select" value={valores.lugar} error={errores.lugar} describedBy={describe} invalid={Boolean(conflicto)} onChange={(e) => cambiar('lugar', e.target.value)}>
        <option value="">Selecciona el lugar</option>
        {LUGARES.map((l) => <option key={l} value={l}>{l}</option>)}
      </Field>

      {conflicto && (
        <div id="conflicto" role="alert" className="flex flex-col gap-3 rounded-lg border border-warning bg-warning p-3 text-warning">
          <p className="flex flex-wrap items-start gap-2 text-body-medium">
            <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
            <span className="min-w-0 flex-1 basis-40 break-words">
              Conflicto: {valores.lugar} ocupada el {diaLargo(valores.fecha).toLowerCase()} a las {formatHora(valores.hora)}
              {conflicto.alternativas.length > 0 ? ' Horarios disponibles:' : ' No hay horarios alternativos ese día.'}
            </span>
          </p>
          {conflicto.alternativas.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {conflicto.alternativas.map((alt) => {
                const etiqueta = alt.tipo === 'hora' ? formatHora(alt.hora) : `${alt.lugar} · ${formatHora(valores.hora)}`
                return (
                  <button
                    key={alt.tipo}
                    type="button"
                    aria-label={alt.tipo === 'hora' ? `Cambiar la hora a ${etiqueta}` : `Cambiar el lugar: ${etiqueta}`}
                    onClick={() => cambiar(alt.tipo === 'hora' ? 'hora' : 'lugar', alt.tipo === 'hora' ? alt.hora : alt.lugar)}
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
