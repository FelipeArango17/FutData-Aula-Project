// Contenedor básico de contenido. `as` permite usar section, li, article...
export default function Card({ as: Tag = 'section', className = '', children, ...rest }) {
  return (
    <Tag className={`rounded-lg border bg-surface p-4 ${className}`} {...rest}>
      {children}
    </Tag>
  )
}
