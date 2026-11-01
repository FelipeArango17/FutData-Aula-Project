import { Navigate } from 'react-router-dom'

export const RUTA_INSCRIBIR = '/administrador/deportistas/inscribir'

// "Nuevo deportista" ya no existe: hay un solo flujo, el asistente "Inscribir jugador". La ruta vieja lleva ahí (reemplazando el
// historial) para no romper enlaces ni marcadores.
export default function RedirigirAInscribir() {
  return <Navigate to={RUTA_INSCRIBIR} replace />
}
