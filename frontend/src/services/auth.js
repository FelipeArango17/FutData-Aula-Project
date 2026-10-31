// Elige el servicio de autenticación: el real (Supabase Auth + backend) si hay VITE_SUPABASE_URL y
// VITE_SUPABASE_PUBLISHABLE_KEY y no se está en pruebas; si no, el simulado (authService.js). Los dos tienen el mismo
// contrato. `restaurarSesion` y `alCerrarSesionExterna` solo existen en el real.
import * as simulado from './authService.js'
import * as real from './authServiceReal.js'
import { autenticacionReal } from './supabaseClient.js'

export const servicio = autenticacionReal ? real : simulado
export { autenticacionReal }
