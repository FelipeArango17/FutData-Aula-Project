import { useEffect, useRef, useState } from 'react'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import Button from '../ui/Button.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import Field from '../ui/Field.jsx'
import GrupoCasillas from '../ui/GrupoCasillas.jsx'
import { CATEGORIAS, ESPECIALIDADES, ETIQUETAS_TIPO_DOCUMENTO, TIPOS_DOCUMENTO } from '../../services/entrenadoresService.js'
import { erroresPorCampo } from '../../utils/erroresApi.js'
import {
  MENSAJES, enLista, requerido, validarCampos, validarCorreo, validarDocumento, validarFechaNacimientoEntrenador, validarTelefono,
} from '../../utils/validaciones.js'

function validar(v) {
  return validarCampos(v, {
    nombre: [requerido(MENSAJES.nombreObligatorio)],
    apellido: [requerido(MENSAJES.apellidoObligatorio)],
    tipoDocumento: [enLista(TIPOS_DOCUMENTO, MENSAJES.tipoDocumento)],
    documento: [(valor, valores) => validarDocumento(valor, valores.tipoDocumento)],
    fechaNacimiento: [(valor) => validarFechaNacimientoEntrenador(valor)],
    telefono: [validarTelefono],
    correo: [validarCorreo],
    especialidad: [requerido(MENSAJES.especialidadObligatoria)],
  })
}

const textoDuplicado = {
  documento: 'Ya existe un entrenador con ese documento.',
  correo: 'Ya existe un entrenador con ese correo.',
}

const mismas = (a, b) => a.length === b.length && a.every((c) => b.includes(c))

// Formulario para registrar y editar entrenadores. Guarda en dos pasos, como la API:
//   1. onGuardar(valores)  -> datos personales; devuelve el entrenador guardado ({ id, ... })
//   2. onAsignarCategorias(id, categorias, reasignar) -> categorías (solo si cambiaron y el entrenador está activo)
// Si el paso 2 falla, el entrenador ya quedó guardado: los datos personales se bloquean y el botón pasa a reintentar solo la
// asignación. Si una categoría tiene otro entrenador (409), pide confirmación y repite con reasignar = true. Al terminar
// llama a onTerminar().
// props: inicial, ignorarId (al editar), categorias (las de la API, para mostrar quién dirige cada una), detectarDuplicado,
// onGuardar, onAsignarCategorias, onTerminar, onCancelar
export default function FormularioEntrenador({
  inicial, ignorarId = null, categorias = [], detectarDuplicado, onGuardar, onAsignarCategorias, onTerminar, onCancelar,
}) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const [aviso, setAviso] = useState(null) // error general: { id, texto }
  const [pendiente, setPendiente] = useState(null) // id del entrenador ya guardado cuyas categorías faltan
  const [reasignar, setReasignar] = useState(null) // { mensaje } mientras se pide confirmar la reasignación
  const formRef = useRef(null)
  const inactivo = inicial.activo === false

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    const limpiar = campo === 'tipoDocumento' ? ['tipoDocumento', 'documento'] : [campo]
    setValores({ ...valores, [campo]: valor })
    if (limpiar.some((c) => errores[c])) setErrores(Object.fromEntries(Object.entries(errores).filter(([c]) => !limpiar.includes(c))))
  }

  const texto = (campo) => ({ value: valores[campo], error: errores[campo], onChange: (e) => cambiar(campo, e.target.value) })
  const mostrarAviso = (mensaje) => setAviso({ id: Date.now(), texto: mensaje })

  // Datos personales: los errores con `campos` marcan el campo; los demás (permisos, red, servidor) van en el aviso.
  function errorDeDatos(error) {
    const porCampo = error.duplicado
      ? { [error.duplicado.campo]: textoDuplicado[error.duplicado.campo] }
      : erroresPorCampo(error, { duplicados: textoDuplicado })
    if (Object.keys(porCampo).length > 0) {
      setErrores(porCampo)
      setIntentos((n) => n + 1)
    } else {
      mostrarAviso(error.mensaje ?? error.message)
    }
  }

  async function asignarYTerminar(id, confirmado) {
    const quedanCategorias = !inactivo && !mismas(valores.categorias, inicial.categorias ?? [])
    if (quedanCategorias) {
      try {
        await onAsignarCategorias(id, valores.categorias, confirmado)
      } catch (error) {
        setPendiente(id)
        if (!confirmado && error.estado === 409 && error.campos?.includes('categorias')) {
          setReasignar({ mensaje: error.mensaje })
        } else {
          mostrarAviso(`Los datos del entrenador ya quedaron guardados, pero no se pudieron asignar las categorías: ${error.mensaje ?? error.message} Puedes reintentar.`)
        }
        return
      }
    }
    onTerminar()
  }

  async function enviar(event) {
    event.preventDefault()
    if (guardando) return
    setAviso(null)
    if (pendiente) {
      setGuardando(true)
      try {
        await asignarYTerminar(pendiente, false)
      } finally {
        setGuardando(false)
      }
      return
    }
    const encontrados = validar(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    setGuardando(true)
    try {
      let guardado
      try {
        const duplicado = await detectarDuplicado(valores, ignorarId)
        if (duplicado) {
          setErrores({ [duplicado.campo]: textoDuplicado[duplicado.campo] })
          return setIntentos((n) => n + 1)
        }
        guardado = await onGuardar(valores)
      } catch (error) {
        return errorDeDatos(error)
      }
      await asignarYTerminar(guardado.id, false)
    } finally {
      setGuardando(false)
    }
  }

  async function confirmarReasignacion() {
    setReasignar(null)
    setGuardando(true)
    try {
      await asignarYTerminar(pendiente, true)
    } finally {
      setGuardando(false)
    }
  }

  function cancelarReasignacion() {
    setReasignar(null)
    mostrarAviso('Los datos del entrenador quedaron guardados, pero las categorías no se reasignaron. Cambia la selección o reintenta.')
  }

  // Quién dirige cada categoría (de la API), sin contar al propio entrenador
  const ajenas = categorias.filter((c) => c.entrenador && c.entrenador.id !== ignorarId && c.entrenador.id !== pendiente)
  const libres = categorias.filter((c) => !c.entrenador)

  return (
    <form ref={formRef} noValidate onSubmit={enviar} aria-busy={guardando} className="flex flex-col gap-4">
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" onCerrar={() => setAviso(null)} />
      <fieldset disabled={Boolean(pendiente)} className="flex min-w-0 flex-col gap-4">
        <legend className="sr-only">Datos personales</legend>
        <Field label="Nombre" id="nombre" autoComplete="off" {...texto('nombre')} />
        <Field label="Apellido" id="apellido" autoComplete="off" {...texto('apellido')} />
        <Field label="Tipo de documento" id="tipoDocumento" as="select" {...texto('tipoDocumento')}>
          <option value="">Selecciona el tipo de documento</option>
          {TIPOS_DOCUMENTO.map((t) => <option key={t} value={t}>{ETIQUETAS_TIPO_DOCUMENTO[t]}</option>)}
        </Field>
        <Field label="Número de documento" id="documento" inputMode={valores.tipoDocumento === 'PASAPORTE' ? 'text' : 'numeric'} autoComplete="off" {...texto('documento')} />
        <Field label="Fecha de nacimiento (18 años o más)" id="fechaNacimiento" type="date" {...texto('fechaNacimiento')} />
        <Field label="Teléfono" id="telefono" type="tel" inputMode="numeric" autoComplete="off" {...texto('telefono')} />
        <Field label="Correo electrónico" id="correo" type="email" autoComplete="off" {...texto('correo')} />
        <Field label="Especialidad" id="especialidad" list="especialidades" autoComplete="off" {...texto('especialidad')} />
        <Field label="Dirección (opcional)" id="direccion" autoComplete="street-address" {...texto('direccion')} />
        <datalist id="especialidades">
          {ESPECIALIDADES.map((e) => <option key={e} value={e} />)}
        </datalist>
        <Field label="Horario de trabajo (opcional)" id="horarioTrabajo" autoComplete="off" {...texto('horarioTrabajo')} />
      </fieldset>

      {inactivo ? (
        <p className="break-words text-body text-secondary">
          Este entrenador está inactivo y no dirige categorías. Actívalo desde la lista para asignarle categorías.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          <GrupoCasillas
            legend="Categorías a cargo (opcional)"
            id="categorias"
            opciones={CATEGORIAS}
            value={valores.categorias}
            error={errores.categorias}
            onChange={(lista) => cambiar('categorias', lista)}
          />
          <p className="break-words text-small text-secondary">
            Cada categoría tiene un solo entrenador. Si desmarcas una, queda sin entrenador.
            {libres.length > 0 && ` Sin entrenador: ${libres.map((c) => c.nombre).join(', ')}.`}
            {ajenas.length > 0 && ` Con otro entrenador: ${ajenas.map((c) => `${c.nombre} (${c.entrenador.nombre} ${c.entrenador.apellido})`).join(', ')}; pedirá confirmación.`}
          </p>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={guardando} className="grow">
          {guardando ? 'Guardando…' : pendiente ? 'Reintentar asignación de categorías' : 'Guardar'}
        </Button>
        <Button variant="secondary" onClick={pendiente ? onTerminar : onCancelar} className="grow">
          {pendiente ? 'Ir a la lista sin asignar' : 'Cancelar'}
        </Button>
      </div>

      <DialogoConfirmacion
        open={Boolean(reasignar)}
        titulo="¿Reasignar categorías?"
        descripcion={reasignar?.mensaje ?? ''}
        textoConfirmar="Sí, reasignar"
        onConfirmar={confirmarReasignacion}
        onCancelar={cancelarReasignacion}
      />
    </form>
  )
}
