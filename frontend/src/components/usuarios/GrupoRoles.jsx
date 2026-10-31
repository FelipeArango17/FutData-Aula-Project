import Icon from '../Icon.jsx'
import { DESCRIPCION_ROL, ETIQUETAS_ROL, ROLES } from '../../services/usuariosService.js'

// Roles como grupo de opciones (radio) con una línea que explica qué puede hacer cada uno.
// Con texto grande (menos de 22 rem de ancho) el botón de opción va arriba del texto.
// Sin borde ni relleno propio por opción: así no se anida relleno dentro de la tarjeta que lo contiene.
// requerido: asterisco junto a la leyenda.
// error: mensaje de validación (se asocia al grupo y a cada opción con aria-describedby).
export default function GrupoRoles({ legend = 'Rol', id, value, onChange, error, requerido = false, disabled = false }) {
  const errorId = error ? `${id}-error` : undefined
  return (
    <fieldset aria-describedby={errorId} disabled={disabled} className="flex min-w-0 flex-col gap-1">
      <legend className="mb-1 break-words text-body-medium">
        {legend}
        {requerido && <span aria-hidden="true" className="text-danger"> *</span>}
      </legend>
      <div className="flex flex-col divide-y">
        {ROLES.map((rol) => (
          <label key={rol} className="flex min-h-11 cursor-pointer flex-col items-start gap-1 py-2 min-[22rem]:flex-row min-[22rem]:gap-3">
            <input
              type="radio"
              name={id}
              id={`${id}-${rol}`}
              value={rol}
              checked={value === rol}
              onChange={() => onChange(rol)}
              aria-invalid={error ? true : undefined}
              aria-describedby={[errorId, `${id}-${rol}-ayuda`].filter(Boolean).join(' ')}
              className="h-5 w-5 shrink-0 min-[22rem]:mt-0.5 accent-brand-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
            />
            <span className="flex min-w-0 flex-col">
              <span className="break-words text-body-medium">{ETIQUETAS_ROL[rol]}</span>
              <span id={`${id}-${rol}-ayuda`} className="break-words text-small text-secondary">{DESCRIPCION_ROL[rol]}</span>
            </span>
          </label>
        ))}
      </div>
      {error && (
        <p id={errorId} className="flex flex-col items-start gap-1 text-small text-danger">
          <Icon name="alert" className="h-4 w-4 shrink-0" />
          <span className="min-w-0 break-words">{error}</span>
        </p>
      )}
    </fieldset>
  )
}
