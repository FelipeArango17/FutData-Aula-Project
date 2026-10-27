// Encabezado de pantalla (h1 + subtítulo) y contenedor del contenido.
export default function Page({ title, subtitle, children }) {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="break-words text-display">{title}</h1>
        {subtitle && <p className="text-body text-secondary">{subtitle}</p>}
      </header>
      {children}
    </div>
  )
}
