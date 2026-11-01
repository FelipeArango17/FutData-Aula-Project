import Field from '../ui/Field.jsx'
import { PARENTESCOS } from '../../services/acudientesService.js'

// Paso 2: el acudiente del jugador. Las validaciones son las compartidas (validarDatosAcudiente).
// PENDIENTE (backend): al confirmar se creará su cuenta y recibirá un correo con un enlace para crear su contraseña.
const columnas = 'grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4'

export default function PasoAcudiente({ acudiente, errores, onCambio }) {
  const texto = (campo) => ({ value: acudiente[campo], error: errores[campo], onChange: (e) => onCambio(campo, e.target.value) })
  return (
    <>
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <div className={columnas}>
        <Field label="Nombres del acudiente" id="nombre" requerido autoComplete="off" {...texto('nombre')} />
        <Field label="Apellidos del acudiente" id="apellido" requerido autoComplete="off" {...texto('apellido')} />
      </div>
      <div className={columnas}>
        <Field label="Correo electrónico" id="correo" requerido type="email" inputMode="email" autoComplete="off" {...texto('correo')} />
        <Field label="Teléfono" id="telefono" requerido type="tel" inputMode="numeric" autoComplete="off" {...texto('telefono')} />
      </div>
      <Field label="Parentesco con el jugador" id="parentesco" requerido as="select" {...texto('parentesco')}>
        <option value="">Selecciona el parentesco</option>
        {PARENTESCOS.map((p) => <option key={p} value={p}>{p}</option>)}
      </Field>
    </>
  )
}
