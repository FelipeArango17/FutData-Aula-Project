import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './tokens.css'
import './index.css'
import App from './App.jsx'
import { rutaDeEnlaceDeCorreo } from './utils/recuperacion.js'

// Un enlace del correo de Supabase (invitación, recuperación, vencido) que cae fuera de /restablecer se normaliza ANTES de montar
// React, sin pasar por el router (una redirección dentro de la app perdía contra el <Navigate to="/login"> de Inicio). Los imports de
// arriba ya capturaron urlInicial (supabaseClient.js lo importa primero), así que el orden no cambia.
const rutaEnlace = rutaDeEnlaceDeCorreo(window.location)
if (rutaEnlace) window.history.replaceState(null, '', rutaEnlace)

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
