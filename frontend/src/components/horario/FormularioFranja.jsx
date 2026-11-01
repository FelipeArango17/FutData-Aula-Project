import { useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import { ENTRENADORES_CLUB, NOMBRES_DIAS, SEDES, entrenadorDeCategoria, nombreDeCategoria } from '../../services/horarioClubData.js'
import { NOMBRES_CATEGORIAS } from '../../utils/categorias.js'
import { CATEGORIAS_CON_GRUPO, validarFranja } from '../../utils/horarioClub.js'

// Formulario de una franja de la plantilla semanal. No guarda por sí mismo: llama a onGuardar(valores) y, si el servicio
// rechaza con `errores`, los muestra en cada campo. Valida antes con validarFranja (las mismas reglas del servicio).
// props: inicial (valores en texto), franjas (las demás, para el choque de entrenador), ignorarId (al editar), onGuardar, onCancelar

export default function FormularioFranja({ inicial, franjas = [], ignorarId = null, onGuardar, onCancelar }) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const formRef = useRef(null)
  const enVuelo = useRef(false)

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    const nuevos = { ...valores, [campo]: valor }
    if (campo === 'idCategoria') {
      // Al elegir la categoría se proponen su entrenador responsable (o ninguno: por turnos) y la sede de sus otras franjas (editables)
      const nombre = nombreDeCategoria(valor)
      nuevos.idEntrenador = String(entrenadorDeCategoria(nombre) ?? '')
      nuevos.sede = franjas.find((f) => f.idCategoria === Number(valor))?.sede ?? valores.sede
      if (!CATEGORIAS_CON_GRUPO.includes(nombre)) nuevos.grupo = ''
    }
    setValores(nuevos)
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(null)
    const encontrados = validarFranja(valores, franjas, ignorarId)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setGuardando(true)
    try {
      await onGuardar(valores)
    } catch (error) {
      if (error.errores) {
        setErrores(error.errores)
        setIntentos((n) => n + 1)
      } else {
        setErrorGeneral('No se pudo guardar la franja. Intenta de nuevo.')
      }
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  const grupoAplica = CATEGORIAS_CON_GRUPO.includes(nombreDeCategoria(valores.idCategoria))
  const campo = (nombre) => ({ value: valores[nombre], error: errores[nombre], onChange: (e) => cambiar(nombre, e.target.value) })

  return (
    <form ref={formRef} noValidate onSubmit={enviar} aria-busy={guardando} className="flex flex-col gap-4">
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
        <Field label="Categoría" id="franja-categoria" as="select" requerido {...campo('idCategoria')}>
          <option value="">Selecciona la categoría</option>
          {NOMBRES_CATEGORIAS.map((c, i) => <option key={c} value={String(i + 1)}>{c}</option>)}
        </Field>
        <Field label="Día" id="franja-dia" as="select" requerido {...campo('dia')}>
          <option value="">Selecciona el día</option>
          {Object.entries(NOMBRES_DIAS).map(([n, nombre]) => <option key={n} value={n}>{nombre}</option>)}
        </Field>
        <Field label="Hora de inicio" id="franja-inicio" type="time" requerido {...campo('horaInicio')} />
        <Field label="Hora de fin" id="franja-fin" type="time" requerido {...campo('horaFin')} />
        <Field label="Sede" id="franja-sede" as="select" requerido {...campo('sede')}>
          <option value="">Selecciona la sede</option>
          {SEDES.map((s) => <option key={s} value={s}>{s}</option>)}
        </Field>
        <Field label="Entrenador" id="franja-entrenador" as="select" {...campo('idEntrenador')}>
          <option value="">Por turnos</option>
          {ENTRENADORES_CLUB.map((e) => <option key={e.id} value={String(e.id)}>{e.nombre}</option>)}
        </Field>
        {grupoAplica && (
          <Field label="Grupo (opcional)" id="franja-grupo" as="select" {...campo('grupo')}>
            <option value="">Sin grupo</option>
            <option value="mañana">Mañana</option>
            <option value="tarde">Tarde</option>
          </Field>
        )}
      </div>
      <div role="alert">{errorGeneral && <p className="break-words text-small text-danger">{errorGeneral}</p>}</div>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button type="submit" disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
          {guardando ? 'Guardando…' : 'Guardar franja'}
        </Button>
        <Button variant="secondary" onClick={onCancelar} disabled={guardando}>Cancelar</Button>
      </div>
    </form>
  )
}
