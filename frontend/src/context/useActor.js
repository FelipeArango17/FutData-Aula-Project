import { useMemo } from 'react'
import { useRole } from './RoleContext.js'
import { useAuth } from './AuthContext.js'

// Cuenta de demostración de cada rol (las mismas de authService) para cuando todavía no hay sesión verificada.
const CORREOS_DEMO = {
  acudiente: 'acudiente@pradofc.co',
  entrenador: 'entrenador@pradofc.co',
  tesorero: 'tesorero@pradofc.co',
  administrador: 'administrador@pradofc.co',
}

// Quién actúa: { rol, correo }. El correo es el de la sesión verificada si su rol coincide con el rol activo;
// si no, el de la cuenta de demostración del rol. PENDIENTE: en producción el backend lo saca del JWT.
export function useActor() {
  const { role } = useRole()
  const { estado, usuario } = useAuth()
  const correo = estado === 'verificada' && usuario?.rol === role && usuario.correo ? usuario.correo : CORREOS_DEMO[role] ?? null
  return useMemo(() => ({ rol: role, correo }), [role, correo])
}
