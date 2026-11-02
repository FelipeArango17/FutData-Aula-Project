import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'

// Diálogo modal con contenido propio (formularios cortos). Mismo comportamiento que DialogoConfirmacion:
// - el resto de la página queda inert; Esc cierra; al cerrar el foco vuelve al botón que lo abrió;
// - al abrir, el foco va al título (el lector de pantalla lo anuncia) y de ahí el Tab recorre el formulario.
// props: open, titulo, onCerrar, children (el contenido, con sus propios botones)
export default function Dialogo({ open, titulo, onCerrar, children }) {
  const tituloId = useId()
  const tituloRef = useRef(null)
  const cerrarRef = useRef(onCerrar)
  useEffect(() => {
    cerrarRef.current = onCerrar
  })

  useEffect(() => {
    if (!open) return
    const raiz = document.getElementById('root')
    const quienAbrio = document.activeElement
    const onKeyDown = (event) => {
      if (event.key === 'Escape') cerrarRef.current()
    }
    document.addEventListener('keydown', onKeyDown)
    if (raiz) raiz.inert = true
    document.body.style.overflow = 'hidden'
    tituloRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      if (raiz) raiz.inert = false // antes de devolver el foco: un elemento inert no puede recibirlo
      document.body.style.overflow = ''
      if (quienAbrio?.isConnected) quienAbrio.focus()
    }
  }, [open])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex max-h-app items-start justify-center overflow-y-auto p-4 sm:items-center">
      <div className="absolute inset-0 bg-sidebar opacity-60" aria-hidden="true" />
      <div role="dialog" aria-modal="true" aria-labelledby={tituloId} className="relative flex w-full max-w-md flex-col gap-4 rounded-lg border bg-surface p-6">
        <h2 id={tituloId} ref={tituloRef} tabIndex={-1} className="break-words text-h3 outline-none">{titulo}</h2>
        {children}
      </div>
    </div>,
    document.body,
  )
}
