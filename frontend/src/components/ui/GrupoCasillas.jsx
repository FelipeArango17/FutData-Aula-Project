import Icon from '../Icon.jsx'

// Grupo de casillas (checkbox) con título. opciones: lista de textos; value: lista de las marcadas.
// error: mensaje de validación (se asocia al grupo y a cada casilla con aria-describedby).
export default function GrupoCasillas({ legend, id, opciones, value, onChange, error }) {
  const errorId = error ? `${id}-error` : undefined

  function alternar(opcion) {
    onChange(value.includes(opcion) ? value.filter((v) => v !== opcion) : [...value, opcion])
  }

  return (
    <fieldset aria-describedby={errorId} className="flex min-w-0 flex-col gap-1">
      <legend className="mb-1 break-words text-body-medium">{legend}</legend>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {opciones.map((opcion) => (
          <label
            key={opcion}
            className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border bg-surface px-3 text-body ${error ? 'border-danger' : ''}`}
          >
            <input
              type="checkbox"
              checked={value.includes(opcion)}
              onChange={() => alternar(opcion)}
              aria-invalid={error ? true : undefined}
              aria-describedby={errorId}
              className="h-5 w-5 shrink-0 accent-brand-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
            />
            <span className="min-w-0 break-words">{opcion}</span>
          </label>
        ))}
      </div>
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-small text-danger">
          <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="min-w-0 break-words">{error}</span>
        </p>
      )}
    </fieldset>
  )
}
