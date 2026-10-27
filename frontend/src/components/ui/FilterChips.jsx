// Filtros tipo "chip". Es un grupo de botones con aria-pressed; se acomodan en varias filas si no caben.
export default function FilterChips({ label, options, value, onChange }) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((option) => {
        const active = option === value
        return (
          <button
            key={option}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option)}
            className={`min-h-11 rounded-full border px-4 text-body-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 ${
              active ? 'border-transparent bg-brand-primary text-on-dark' : 'bg-surface text-primary hover:bg-app'
            }`}
          >
            {option}
          </button>
        )
      })}
    </div>
  )
}
