import { useCallback, useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import Icon from '../Icon.jsx'
import CamposDeportista from '../deportistas/CamposDeportista.jsx'
import { aplicarCambio } from '../../services/deportistasService.js'
import { INSCRIPCION_VACIA, validarDatosInscripcion } from '../../services/inscripcionService.js'

// Formulario público de inscripción en línea (HU-009): deportista, acudiente y autorización. No guarda por sí mismo:
// llama a onGuardar(valores). Reutiliza CamposDeportista (sin categoría) y las validaciones compartidas.
// Si el servicio falla por red, muestra una alerta con "Reintentar" y no se pierde nada de lo escrito.
// PENDIENTE (Ley 1581 de 2012, datos de menores): el texto de la autorización y la "Política de tratamiento de datos" los
// define el club; todavía no hay una página con esa política, por eso no se muestra como enlace.
const columnas = 'grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4'
const fieldset = 'flex min-w-0 flex-col gap-4 rounded-lg border bg-surface p-3 sm:p-4'
const leyenda = 'break-words px-1 text-h3'

export default function FormularioInscripcion({ onGuardar, onCancelar }) {
  const [valores, setValores] = useState(INSCRIPCION_VACIA)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const [confirmarDescartar, setConfirmarDescartar] = useState(false)
  const formRef = useRef(null)
  const enVuelo = useRef(false)

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores(aplicarCambio(valores, campo, valor))
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  const texto = (campo) => ({ value: valores[campo], error: errores[campo], onChange: (e) => cambiar(campo, e.target.value) })

  async function enviar(event) {
    event?.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(false)
    const encontrados = validarDatosInscripcion(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setGuardando(true)
    try {
      await onGuardar(valores)
    } catch (error) {
      if (error.codigo === 'VALIDACION') {
        setErrores(error.errores)
        setIntentos((n) => n + 1)
      } else {
        setErrorGeneral(true)
      }
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  const hayCambios = JSON.stringify(valores) !== JSON.stringify(INSCRIPCION_VACIA)
  const cerrarConfirmacion = useCallback(() => setConfirmarDescartar(false), [])
  const cancelar = () => (hayCambios ? setConfirmarDescartar(true) : onCancelar())

  const cantidad = Object.values(errores).filter(Boolean).length
  const aviso = cantidad > 0
    ? { texto: `Revisa los campos marcados: ${cantidad} ${cantidad === 1 ? 'campo necesita' : 'campos necesitan'} corrección.` }
    : errorGeneral
      ? { texto: 'No se pudo enviar la inscripción. Revisa tu conexión; lo que escribiste no se perdió.', accion: { texto: 'Reintentar', onClick: () => enviar() } }
      : null

  const errorConsentimiento = errores.consentimiento

  return (
    <form ref={formRef} noValidate onSubmit={enviar} aria-busy={guardando} className="flex flex-col gap-6">
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />

      <fieldset className={fieldset}>
        <legend className={leyenda}>Datos del deportista</legend>
        <CamposDeportista texto={texto} fechaNacimiento={valores.fechaNacimiento} mostrarCategoria={false} />
      </fieldset>

      <fieldset className={fieldset}>
        <legend className={leyenda}>Datos del acudiente</legend>
        <div className={columnas}>
          <Field label="Nombres del acudiente" id="acudienteNombre" autoComplete="off" {...texto('acudienteNombre')} />
          <Field label="Apellidos del acudiente" id="acudienteApellido" autoComplete="off" {...texto('acudienteApellido')} />
        </div>
        <div className={columnas}>
          <Field label="Correo electrónico del acudiente" id="acudienteCorreo" type="email" inputMode="email" autoComplete="off" {...texto('acudienteCorreo')} />
          <Field label="Teléfono del acudiente" id="acudienteTelefono" type="tel" inputMode="numeric" autoComplete="off" {...texto('acudienteTelefono')} />
        </div>
      </fieldset>

      <fieldset className={fieldset} aria-describedby={errorConsentimiento ? 'consentimiento-error' : undefined}>
        <legend className={leyenda}>Autorización</legend>
        <label className={`flex min-h-11 cursor-pointer flex-wrap items-start gap-x-3 gap-y-2 rounded-md border bg-surface p-2 text-body sm:p-3 ${errorConsentimiento ? 'border-danger' : ''}`}>
          <input
            type="checkbox"
            id="consentimiento"
            checked={valores.consentimiento}
            onChange={(e) => cambiar('consentimiento', e.target.checked)}
            aria-invalid={errorConsentimiento ? true : undefined}
            aria-describedby={errorConsentimiento ? 'consentimiento-error' : 'consentimiento-ayuda'}
            className="mt-0.5 h-5 w-5 shrink-0 accent-brand-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
          />
          {/* El texto baja debajo de la casilla cuando no cabe a su lado (texto grande, 320 px): así no se parte a mitad de palabra */}
          <span className="min-w-0 grow basis-[12rem] break-words">
            Autorizo al club el tratamiento de los datos personales del deportista y los míos para gestionar la inscripción.
          </span>
        </label>
        <p id="consentimiento-ayuda" className="break-words text-small text-secondary">
          Los datos del deportista, que es menor de edad, se tratan según la Ley 1581 de 2012. Consulta la{' '}
          <strong className="font-semibold">Política de tratamiento de datos</strong> del club.
        </p>
        {errorConsentimiento && (
          <p id="consentimiento-error" className="flex items-start gap-1 text-small text-danger">
            <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
            <span className="min-w-0 break-words">{errorConsentimiento}</span>
          </p>
        )}
      </fieldset>

      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button type="submit" disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
          {guardando ? 'Enviando…' : 'Enviar inscripción'}
        </Button>
        <Button variant="secondary" onClick={cancelar} disabled={guardando}>Cancelar</Button>
      </div>

      <DialogoConfirmacion
        open={confirmarDescartar}
        titulo="¿Salir sin enviar la inscripción?"
        descripcion="Los datos que escribiste no se guardarán."
        textoConfirmar="Sí, salir"
        onConfirmar={() => {
          setConfirmarDescartar(false)
          onCancelar()
        }}
        onCancelar={cerrarConfirmacion}
      />
    </form>
  )
}
