// Encabezado de pantalla (h1 + subtítulo) y contenedor del contenido.
// ocultarEnMovil: esconde el encabezado en móvil (cuando la pantalla muestra su propio título)
// tituloRef: referencia al h1 para llevarle el foco al abrir la pantalla
export default function Page({ title, subtitle, ocultarEnMovil = false, tituloRef, children }) {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <header className={`${ocultarEnMovil ? 'hidden md:flex' : 'flex'} flex-col gap-1`}>
        <h1 ref={tituloRef} tabIndex={tituloRef ? -1 : undefined} className="break-words text-display outline-none">{title}</h1>
        {subtitle && <p className="text-body text-secondary">{subtitle}</p>}
      </header>
      {children}
    </div>
  )
}
