import Icon from '../Icon.jsx'

// Campo de formulario con etiqueta visible. Sirve para input (por defecto) y select (as="select").
// error: mensaje de validación (se asocia al campo con aria-describedby).
// describedBy: id de otro texto que también describe el campo (por ejemplo un aviso de conflicto).
// invalid: marca el campo como inválido aunque no tenga `error` propio.
// requerido: muestra un asterisco junto a la etiqueta y marca el control con aria-required.
// accion: botón opcional al lado del campo (por ejemplo mostrar u ocultar la contraseña).
export default function Field({ label, id, as: Tag = 'input', error, describedBy, invalid, requerido, accion, children, ...rest }) {
  const errorId = error ? `${id}-error` : undefined
  const marcado = Boolean(error || invalid)
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-body-medium">
        {label}
        {requerido && <span aria-hidden="true" className="text-danger"> *</span>}
      </label>
      <div className={accion ? 'flex gap-2' : undefined}>
        <div className="min-w-0 flex-1">
          <Tag
            id={id}
            aria-invalid={marcado ? true : undefined}
            aria-required={requerido ? true : undefined}
            aria-describedby={[errorId, describedBy].filter(Boolean).join(' ') || undefined}
            className={`min-h-11 w-full rounded-md border bg-surface px-3 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
              marcado ? 'border-danger' : ''
            }`}
            {...rest}
          >
            {children}
          </Tag>
        </div>
        {accion}
      </div>
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-small text-danger">
          <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="min-w-0 break-words">{error}</span>
        </p>
      )}
    </div>
  )
}
