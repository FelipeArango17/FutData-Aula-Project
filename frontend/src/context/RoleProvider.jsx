import { useEffect, useState } from 'react'
import { RoleContext } from './RoleContext.js'
import { useAuth } from './AuthContext.js'
import { roles } from '../config/menus.js'
import { cambioDeRolPermitido } from '../utils/accesoRutas.js'

export const STORAGE_KEY = 'futdata.rol'

// Rol activo = rol de la sesión verificada (usuario.rol). Sin sesión verificada no hay rol (null).
// Única excepción: el selector de desarrollo (RoleSwitcher), solo con import.meta.env.DEV y VITE_PERMITIR_CAMBIO_ROL='true';
// su elección se guarda en futdata.rol y se borra al cerrar sesión. En cualquier otro caso futdata.rol se ignora.
function leerRolDeDesarrollo() {
  if (!cambioDeRolPermitido()) return null
  try {
    const guardado = localStorage.getItem(STORAGE_KEY)
    return roles.includes(guardado) ? guardado : null
  } catch {
    return null
  }
}

export default function RoleProvider({ children }) {
  const { estado, usuario, restaurada } = useAuth()
  const [, repintar] = useState(0) // el rol de desarrollo vive en localStorage; setRole fuerza el repintado
  const verificada = estado === 'verificada' && roles.includes(usuario?.rol)

  // Sin sesión verificada (cierre de sesión, sesión vencida o nunca iniciada) se borra el rol guardado
  useEffect(() => {
    if (!restaurada || verificada) return
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // sin almacenamiento
    }
  }, [restaurada, verificada])

  const setRole = (nuevo) => {
    if (!cambioDeRolPermitido() || !roles.includes(nuevo)) return
    try {
      localStorage.setItem(STORAGE_KEY, nuevo)
    } catch {
      // sin almacenamiento: el cambio de rol no se conserva
    }
    repintar((n) => n + 1)
  }

  const role = verificada ? (leerRolDeDesarrollo() ?? usuario.rol) : null
  return <RoleContext.Provider value={{ role, setRole }}>{children}</RoleContext.Provider>
}
