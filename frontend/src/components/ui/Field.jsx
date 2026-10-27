// Campo de formulario con etiqueta visible. Sirve para input (por defecto) y select (as="select").
export default function Field({ label, id, as: Tag = 'input', children, ...rest }) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-body-medium">{label}</label>
      <Tag
        id={id}
        className="min-h-11 w-full rounded-md border bg-surface px-3 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        {...rest}
      >
        {children}
      </Tag>
    </div>
  )
}
