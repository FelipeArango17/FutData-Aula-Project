import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import Button from './Button.jsx'

// Diálogo de confirmación (role="alertdialog"). Mientras está abierto:
// - el resto de la página queda inert (no recibe foco ni clics), así el foco no se sale;
// - Esc cancela;
// - al cerrar, el foco vuelve al botón que lo abrió.
// Va en un portal fuera de #root para poder volver inert todo #root.
export default function DialogoConfirmacion({ open, titulo, descripcion, textoConfirmar, onConfirmar, onCancelar }) {
  const tituloId = useId()
  const descripcionId = useId()
  const cancelarRef = useRef(null)

  useEffect(() => {
    if (!open) return
    const raiz = document.getElementById('root')
    const quienAbrio = document.activeElement
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onCancelar()
    }
    document.addEventListener('keydown', onKeyDown)
    raiz.inert = true
    document.body.style.overflow = 'hidden'
    cancelarRef.current?.focus() // la opción segura recibe el foco primero
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      raiz.inert = false // antes de devolver el foco: un elemento inert no puede recibirlo
      document.body.style.overflow = ''
      if (quienAbrio?.isConnected) quienAbrio.focus()
    }
  }, [open, onCancelar])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex max-h-app items-center justify-center overflow-y-auto p-4">
      <div className="absolute inset-0 bg-sidebar opacity-60" aria-hidden="true" />
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        aria-describedby={descripcionId}
        className="relative flex max-h-full w-full max-w-md flex-col gap-4 overflow-y-auto rounded-lg border bg-surface p-6"
      >
        <h2 id={tituloId} className="text-h3">{titulo}</h2>
        <p id={descripcionId} className="text-body text-secondary">{descripcion}</p>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button variant="secondary" ref={cancelarRef} onClick={onCancelar}>Cancelar</Button>
          <Button variant="danger" onClick={onConfirmar}>{textoConfirmar}</Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
