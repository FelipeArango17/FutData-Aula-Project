import { useState } from 'react'
import { RoleContext } from './RoleContext.js'
import { roles } from '../config/menus.js'

const STORAGE_KEY = 'futdata.rol'

function readStoredRole() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return roles.includes(stored) ? stored : roles[0]
  } catch {
    return roles[0]
  }
}

export default function RoleProvider({ children }) {
  const [role, setRoleState] = useState(readStoredRole)

  const setRole = (newRole) => {
    setRoleState(newRole)
    try {
      localStorage.setItem(STORAGE_KEY, newRole)
    } catch {
      // sin almacenamiento: el rol vale solo mientras la página siga abierta
    }
  }

  return <RoleContext.Provider value={{ role, setRole }}>{children}</RoleContext.Provider>
}
