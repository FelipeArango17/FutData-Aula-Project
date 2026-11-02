import { useEffect, useRef } from 'react'
import Icon from '../Icon.jsx'

const botonClases =
  'flex min-h-11 items-center rounded-md px-3 text-body-medium underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

// Mensaje de estado. aviso = { id, texto, accion? } o null; accion = { texto, onClick } (por ejemplo "Reintentar").
// Lo usan programación de entrenamientos, gestión de entrenadores y el inicio de sesión.
// rol: "status" para avisos que informan (éxito) o "alert" para errores. tono: "success" | "warning" | "danger".
// onCerrar: si no se pasa, el aviso no tiene botón para cerrarlo.
// El contenedor con el rol siempre está en la página; el mensaje aparece dentro.
// enfocar: cuando llega un aviso nuevo recibe el foco (después de eliminar el botón que abrió el diálogo ya no existe).
export default function AvisoEstado({ aviso, onCerrar, rol = 'status', tono = 'success', enfocar = true }) {
  const ref = useRef(null)

  useEffect(() => {
    if (aviso && enfocar) ref.current?.focus()
  }, [aviso, enfocar])

  const colores = { danger: 'bg-danger text-danger', warning: 'bg-warning text-warning' }[tono] ?? 'bg-success text-success'

  return (
    <div role={rol} ref={ref} tabIndex={-1} className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
      {aviso && (
        <div className={`flex flex-wrap items-center gap-2 rounded-lg p-4 ${colores}`}>
          <Icon name={tono === 'danger' || tono === 'warning' ? 'alert' : 'check'} className="h-5 w-5 shrink-0" />
          <p className="min-w-0 flex-1 basis-48 break-words text-body-medium">{aviso.texto}</p>
          {aviso.accion && (
            <button type="button" onClick={aviso.accion.onClick} className={botonClases}>
              {aviso.accion.texto}
            </button>
          )}
          {onCerrar && (
            <button type="button" onClick={onCerrar} className={botonClases}>
              Cerrar aviso
            </button>
          )}
        </div>
      )}
    </div>
  )
}
