import { createContext, useContext } from 'react'

// Rol activo de la sesión. Por ahora lo fija el selector de desarrollo;
// cuando exista login, solo cambia RoleProvider.
export const RoleContext = createContext(null)

export const useRole = () => useContext(RoleContext)
