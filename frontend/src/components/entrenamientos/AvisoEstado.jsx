import { useEffect, useRef } from 'react'
import Icon from '../Icon.jsx'
import { useEntrenamientos } from '../../context/EntrenamientosContext.js'

// Mensaje de estado tras guardar o eliminar (simulado: no se envía ningún aviso real).
// El contenedor role="status" siempre está en la página; el mensaje aparece dentro.
// Cuando llega un aviso nuevo recibe el foco: después de eliminar el botón que abrió el diálogo ya no existe.
export default function AvisoEstado() {
  const { aviso, cerrarAviso } = useEntrenamientos()
  const ref = useRef(null)

  useEffect(() => {
    if (aviso) ref.current?.focus()
  }, [aviso])

  return (
    <div role="status" ref={ref} tabIndex={-1} className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
      {aviso && (
        <div className="flex flex-wrap items-center gap-2 rounded-lg bg-success p-4 text-success">
          <Icon name="check" className="h-5 w-5 shrink-0" />
          <p className="min-w-0 flex-1 basis-48 break-words text-body-medium">{aviso.texto}</p>
          <button
            type="button"
            onClick={cerrarAviso}
            className="flex min-h-11 items-center rounded-md px-3 text-body-medium underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          >
            Cerrar aviso
          </button>
        </div>
      )}
    </div>
  )
}
