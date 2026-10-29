import { useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import GrupoCasillas from '../ui/GrupoCasillas.jsx'
import { CATEGORIAS, ESPECIALIDADES, TIPOS_DOCUMENTO } from '../../services/entrenadoresService.js'
import {
  MENSAJES, enLista, noVacia, requerido, validarCampos, validarCorreo, validarDocumento, validarTelefono,
} from '../../utils/validaciones.js'

function validar(v) {
  return validarCampos(v, {
    nombre: [requerido(MENSAJES.nombreObligatorio)],
    apellido: [requerido(MENSAJES.apellidoObligatorio)],
    tipoDocumento: [enLista(TIPOS_DOCUMENTO, MENSAJES.tipoDocumento)],
    documento: [validarDocumento],
    telefono: [validarTelefono],
    correo: [validarCorreo],
    especialidad: [requerido(MENSAJES.especialidadObligatoria)],
    categorias: [noVacia(MENSAJES.categoriasMinimo)],
  })
}

const textoDuplicado = {
  documento: 'Ya existe un entrenador con ese documento.',
  correo: 'Ya existe un entrenador con ese correo.',
}

// Formulario para registrar y editar entrenadores. No guarda por sí mismo: llama a onGuardar(valores).
// props: inicial, ignorarId (al editar), detectarDuplicado, onGuardar, onCancelar
export default function FormularioEntrenador({ inicial, ignorarId = null, detectarDuplicado, onGuardar, onCancelar }) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const formRef = useRef(null)

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  const texto = (campo) => ({ value: valores[campo], error: errores[campo], onChange: (e) => cambiar(campo, e.target.value) })

  async function enviar(event) {
    event.preventDefault()
    if (guardando) return
    const encontrados = validar(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    setGuardando(true)
    try {
      const duplicado = await detectarDuplicado(valores, ignorarId)
      if (duplicado) {
        setErrores({ [duplicado.campo]: textoDuplicado[duplicado.campo] })
        return setIntentos((n) => n + 1)
      }
      await onGuardar(valores)
    } catch (error) {
      if (!error.duplicado) throw error
      setErrores({ [error.duplicado.campo]: textoDuplicado[error.duplicado.campo] })
      setIntentos((n) => n + 1)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form ref={formRef} noValidate onSubmit={enviar} className="flex flex-col gap-4">
      <Field label="Nombre" id="nombre" autoComplete="off" {...texto('nombre')} />
      <Field label="Apellido" id="apellido" autoComplete="off" {...texto('apellido')} />
      <Field label="Tipo de documento" id="tipoDocumento" as="select" {...texto('tipoDocumento')}>
        <option value="">Selecciona el tipo de documento</option>
        {TIPOS_DOCUMENTO.map((t) => <option key={t} value={t}>{t}</option>)}
      </Field>
      <Field label="Número de documento" id="documento" inputMode="numeric" autoComplete="off" {...texto('documento')} />
      <Field label="Teléfono" id="telefono" type="tel" inputMode="numeric" autoComplete="off" {...texto('telefono')} />
      <Field label="Correo electrónico" id="correo" type="email" autoComplete="off" {...texto('correo')} />
      <Field label="Especialidad" id="especialidad" list="especialidades" autoComplete="off" {...texto('especialidad')} />
      <datalist id="especialidades">
        {ESPECIALIDADES.map((e) => <option key={e} value={e} />)}
      </datalist>
      <GrupoCasillas
        legend="Categorías a cargo"
        id="categorias"
        opciones={CATEGORIAS}
        value={valores.categorias}
        error={errores.categorias}
        onChange={(lista) => cambiar('categorias', lista)}
      />
      <Field label="Horario de trabajo (opcional)" id="horarioTrabajo" autoComplete="off" {...texto('horarioTrabajo')} />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="grow">{guardando ? 'Guardando…' : 'Guardar'}</Button>
        <Button variant="secondary" onClick={onCancelar} className="grow">Cancelar</Button>
      </div>
    </form>
  )
}
