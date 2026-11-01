import { useState } from 'react'

// Círculo con iniciales; el nombre ya está en el texto de al lado, por eso es decorativo (la imagen lleva alt vacío).
// foto: URL opcional; si viene se muestra la imagen redonda, del mismo tamaño. Si no carga, se vuelve a las iniciales.
// className: clases extra (por ejemplo otro tamaño).
export default function Avatar({ name, foto, className = '' }) {
  const [fallida, setFallida] = useState(null) // la URL que no cargó (si cambia la foto, se vuelve a intentar)
  if (foto && foto !== fallida) {
    return <img src={foto} alt="" aria-hidden="true" onError={() => setFallida(foto)} className={`h-10 w-10 shrink-0 rounded-full object-cover ${className}`} />
  }
  const initials = name.split(' ').map((word) => word[0]).slice(0, 2).join('')
  return (
    <span
      aria-hidden="true"
      className={`flex min-h-10 min-w-10 shrink-0 items-center justify-center rounded-full bg-brand-primary text-body-medium text-on-dark ${className}`}
    >
      {initials}
    </span>
  )
}
