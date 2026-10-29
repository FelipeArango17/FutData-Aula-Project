import Icon from '../Icon.jsx'

// Mensaje de estado vacío / cargando / sin acceso. El ícono va ARRIBA del texto (apilado) para que
// con texto al 200 % en 320 px el texto use todo el ancho.
export default function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex flex-col items-start gap-2 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-col items-start gap-3 break-words text-body">{children}</div>
    </div>
  )
}
