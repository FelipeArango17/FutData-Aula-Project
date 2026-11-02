import { Navigate } from 'react-router-dom'

// "Horario del club" se unió a "Horarios": una sola pantalla. La ruta vieja lleva ahí (reemplazando el historial)
// para no romper enlaces ni marcadores.
export default function RedirigirAHorarios() {
  return <Navigate to="/administrador/horarios" replace />
}
