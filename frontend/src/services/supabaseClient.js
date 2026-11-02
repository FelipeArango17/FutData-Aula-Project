// Cliente único de Supabase Auth. Solo se crea si están VITE_SUPABASE_URL y VITE_SUPABASE_PUBLISHABLE_KEY (la llave
// pública: NUNCA la secret ni la service_role). Sin ellas, o en las pruebas (MODE === 'test'), la app sigue con el
// servicio de autenticación simulado (authService.js) y `supabase` es null.
import './urlInicial.js' // primero: captura la URL antes de que createClient lea el hash de recuperación
import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL
const llavePublica = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY

export const autenticacionReal = import.meta.env.MODE !== 'test' && Boolean(url) && Boolean(llavePublica)

export const supabase = autenticacionReal
  ? createClient(url, llavePublica, { auth: { persistSession: true, autoRefreshToken: true } })
  : null
