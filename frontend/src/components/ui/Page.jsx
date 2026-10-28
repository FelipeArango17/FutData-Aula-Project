// Encabezado de pantalla (h1 + subtítulo) y contenedor del contenido.
// ocultarEnMovil: esconde el encabezado en móvil (cuando la pantalla muestra su propio título)
export default function Page({ title, subtitle, ocultarEnMovil = false, children }) {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header className={`${ocultarEnMovil ? 'hidden md:flex' : 'flex'} flex-col gap-1`}>
        <h1 className="break-words text-display">{title}</h1>
        {subtitle && <p className="text-body text-secondary">{subtitle}</p>}
      </header>
      {children}
    </div>
  )
}
