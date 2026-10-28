import { useMemo, useState } from 'react'
import { AuthContext } from './AuthContext.js'
import { useRole } from './RoleContext.js'
import * as servicio from '../services/authService.js'

const CLAVE = 'futdata.sesion'
const ESTADOS = ['sin-sesion', 'pendiente', 'verificada']
const VACIA = { estado: 'sin-sesion', usuario: null }

// Persistencia mínima en sessionStorage (se borra al cerrar la pestaña).
// PENDIENTE: con Supabase Auth la sesión la gestiona su SDK; este estado local desaparece.
function leer() {
  try {
    const guardada = JSON.parse(sessionStorage.getItem(CLAVE))
    // Un estado "pendiente" solo vale si el servicio todavía tiene la solicitud de código
    if (guardada && ESTADOS.includes(guardada.estado) && guardada.usuario) {
      if (guardada.estado === 'pendiente' && !servicio.estadoCodigo()) return VACIA
      return guardada
    }
  } catch {
    // sin almacenamiento o dato dañado
  }
  return VACIA
}

function guardar(sesion) {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(sesion))
  } catch {
    // sin almacenamiento: la sesión dura mientras la página siga abierta
  }
}

export default function AuthProvider({ children }) {
  const { setRole } = useRole()
  const [sesion, setSesion] = useState(leer)

  const value = useMemo(() => {
    const fijar = (nueva) => {
      setSesion(nueva)
      guardar(nueva)
    }
    return {
      estado: sesion.estado,
      usuario: sesion.usuario,
      // Paso 1. Si sale bien queda pendiente del código.
      async iniciarSesion(correo, contrasena) {
        const usuario = await servicio.iniciarSesion(correo, contrasena)
        fijar({ estado: 'pendiente', usuario })
      },
      // Paso 2. Si sale bien la sesión queda verificada y se fija el rol con el mecanismo existente (futdata.rol).
      async verificarCodigo(codigo, recordar) {
        const usuario = await servicio.verificarCodigo(codigo, { recordar })
        setRole(usuario.rol)
        fijar({ estado: 'verificada', usuario })
        return usuario
      },
      estadoCodigo: servicio.estadoCodigo,
      reenviarCodigo: servicio.reenviarCodigo,
      volverAlLogin() {
        servicio.cancelarSolicitud()
        fijar(VACIA)
      },
    }
  }, [sesion, setRole])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
