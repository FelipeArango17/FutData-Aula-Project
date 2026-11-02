// Elige el servicio de autenticación: el real (Supabase Auth + backend) si hay VITE_SUPABASE_URL y
// VITE_SUPABASE_PUBLISHABLE_KEY y no se está en pruebas; si no, el simulado (authService.js). Los dos tienen el mismo
// contrato. `restaurarSesion`, `alCerrarSesionExterna` y `cerrarSesion` (avisa al backend) solo existen en el real.
import * as simulado from './authService.js'
import * as real from './authServiceReal.js'
import { autenticacionReal } from './supabaseClient.js'

export const servicio = autenticacionReal ? real : simulado
export { autenticacionReal }

// En un build de producción (PROD) sin VITE_SUPABASE_URL y VITE_SUPABASE_PUBLISHABLE_KEY NO se usa el ingreso simulado con cuentas
// demo: la app muestra un mensaje de configuración. En desarrollo y en pruebas el ingreso simulado sigue igual.
export const sinConfiguracion = (prod = import.meta.env.PROD, real = autenticacionReal) => Boolean(prod) && !real
export const MENSAJE_SIN_CONFIGURACION = 'La aplicación no está configurada. Contacta al administrador.'
