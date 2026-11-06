import { useEffect, useId, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import { validarDatosDocumento } from '../../services/documentosService.js'
import { DESCRIPCIONES_TIPO_DOCUMENTO, TIPOS_DOCUMENTO_CLUB } from '../../utils/documentosClub.js'
import { hoyLocal } from '../../utils/fechas.js'
import { mensajeDeError } from '../../utils/documentosApi.js'

// Subir un documento del deportista (el administrador lo sube por el acudiente cuando este no puede). No guarda por sí mismo:
// llama a onSubir({ tipo, archivo, fechaVencimiento }) y, si el servicio rechaza con `errores`, los muestra en cada campo.
// `tipos`: los tipos que se pueden subir ahora (un solo documento activo por tipo: sin los que están PENDIENTE o APROBADO). Sin ninguno,
// no se dibuja el formulario.
const VACIO = { tipo: '', archivo: null, fechaVencimiento: '' }

export default function FormularioDocumento({ onSubir, tipos = TIPOS_DOCUMENTO_CLUB }) {
  const [valores, setValores] = useState(VACIO)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const base = useId() // ids únicos por formulario: la pantalla del acudiente dibuja uno por jugador
  const formRef = useRef(null)
  const enVuelo = useRef(false)

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(null)
    const encontrados = validarDatosDocumento(valores, undefined, tipos.map((t) => t.id))
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setEnviando(true)
    try {
      await onSubir(valores)
    } catch (error) {
      if (error.errores) {
        setErrores(error.errores)
        setIntentos((n) => n + 1)
      } else {
        setErrorGeneral(mensajeDeError(error, 'No se pudo subir el documento. Intenta de nuevo.'))
      }
    } finally {
      enVuelo.current = false
      setEnviando(false)
    }
  }

  if (tipos.length === 0) {
    return <p role="status" className="break-words text-body text-secondary">Todos los tipos ya tienen un documento activo (pendiente o aprobado). Si se rechaza uno, podrás subirlo de nuevo.</p>
  }

  return (
    <form ref={formRef} noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <div className="flex flex-col gap-1">
        <Field
          as="select" label="Tipo de documento" id={`${base}-tipo`} requerido describedBy={`${base}-tipo-ayuda`}
          value={valores.tipo} error={errores.tipo} onChange={(e) => cambiar('tipo', e.target.value)}
        >
          <option value="">Selecciona el tipo de documento</option>
          {tipos.map((t) => <option key={t.id} value={t.id}>{t.nombre} ({t.obligatorio ? 'obligatorio' : 'si aplica'})</option>)}
        </Field>
        <p id={`${base}-tipo-ayuda`} className="break-words text-small text-secondary">
          {valores.tipo ? `${DESCRIPCIONES_TIPO_DOCUMENTO[valores.tipo]}${tipos.find((t) => t.id === valores.tipo)?.obligatorio === false ? '. Si aplica: no cuenta para completar la inscripción.' : ''}` : 'Elige el tipo para ver qué documento se espera.'}
        </p>
      </div>
      <Field
        label="Archivo (PDF, JPG o PNG, máximo 5 MB)" id={`${base}-archivo`} type="file" requerido
        accept=".pdf,application/pdf,image/jpeg,image/png"
        error={errores.archivo} onChange={(e) => cambiar('archivo', e.target.files?.[0] ?? null)}
      />
      <Field
        label="Fecha de vencimiento (opcional)" id={`${base}-vencimiento`} type="date" min={hoyLocal()}
        value={valores.fechaVencimiento} error={errores.fechaVencimiento} onChange={(e) => cambiar('fechaVencimiento', e.target.value)}
      />
      <div role="alert">
        {errorGeneral && <p className="break-words text-small text-danger">{errorGeneral}</p>}
      </div>
      <Button type="submit" disabled={enviando} aria-busy={enviando} className="disabled:opacity-60 sm:self-start">
        {enviando ? 'Subiendo…' : 'Subir documento'}
      </Button>
    </form>
  )
}
