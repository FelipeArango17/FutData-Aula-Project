// react-router no desplaza la página al hash (#id) por sí solo. Si el elemento existe, lo enfoca (para lectores de pantalla y teclado) y
// lo trae a la vista; devuelve true si lo encontró. El elemento debe tener tabIndex={-1} para poder recibir el foco.
export function enfocarAncla(hash, doc = document) {
  const id = decodeURIComponent(String(hash ?? '').replace(/^#/, ''))
  const elemento = id ? doc.getElementById(id) : null
  if (!elemento) return false
  elemento.focus()
  elemento.scrollIntoView?.({ block: 'start' })
  return true
}
