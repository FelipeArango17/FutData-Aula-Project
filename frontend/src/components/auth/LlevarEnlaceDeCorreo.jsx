import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { decidirRedireccionEnlace } from './redireccionEnlace.js'

// Los enlaces del correo de Supabase pueden aterrizar en la dirección del sitio y no en /restablecer: se llevan allí UNA vez
// (replace). Después la persona navega con libertad (por ejemplo a /login con el aviso de contraseña creada). El ref sobrevive
// al doble efecto de StrictMode, así que tampoco se redirige dos veces. Va después de <Routes>: su efecto corre al final y
// gana a cualquier redirección de la ruta de entrada.
export default function LlevarEnlaceDeCorreo({ activo }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const consumida = useRef(false)

  useEffect(() => {
    const decision = decidirRedireccionEnlace({ activo, consumida: consumida.current, pathname })
    consumida.current = decision.consumida
    if (decision.destino) navigate(decision.destino, { replace: true })
  }, [activo, pathname, navigate])

  return null
}
