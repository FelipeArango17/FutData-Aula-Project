import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Guardia from './Guardia.jsx'
import RoleRoute from './RoleRoute.jsx'
import RoleSwitcher from './RoleSwitcher.jsx'
import { AuthContext } from '../context/AuthContext.js'
import { RoleContext } from '../context/RoleContext.js'

// Sin entorno de navegador no se pueden seguir las redirecciones (los <Navigate> navegan en un efecto): aquí se comprueba lo
// que se pinta. Que el panel NO aparezca es lo que evita que se entre sin sesión; a dónde se redirige lo cubre destinoDeAcceso.
function pintar(auth, role, ui = <Guardia><p>PANEL SECRETO</p></Guardia>) {
  return renderToStaticMarkup(
    <MemoryRouter initialEntries={['/administrador/entrenadores']}>
      <AuthContext.Provider value={auth}>
        <RoleContext.Provider value={{ role, setRole: () => {} }}>{ui}</RoleContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

describe('Guardia', () => {
  it('mientras la sesión no se restaura muestra una espera accesible y no el panel', () => {
    const html = pintar({ estado: 'verificada', restaurada: false }, 'administrador')
    expect(html).toContain('role="status"')
    expect(html).toContain('Verificando tu sesión')
    expect(html).not.toContain('PANEL SECRETO')
  })

  it('sin sesión o pendiente no pinta el panel', () => {
    expect(pintar({ estado: 'sin-sesion', restaurada: true }, null)).not.toContain('PANEL SECRETO')
    expect(pintar({ estado: 'pendiente', restaurada: true }, null)).not.toContain('PANEL SECRETO')
  })

  it('con sesión verificada pinta el panel', () => {
    expect(pintar({ estado: 'verificada', restaurada: true }, 'administrador')).toContain('PANEL SECRETO')
  })
})

describe('RoleRoute', () => {
  it('no deja pasar sin sesión aunque haya un rol "guardado"', () => {
    const html = pintar({ estado: 'sin-sesion', restaurada: true }, 'administrador', <RoleRoute role="administrador" />)
    expect(html).toBe('')
  })
  it('con otro rol no pinta el contenido de la ruta', () => {
    expect(pintar({ estado: 'verificada', restaurada: true }, 'acudiente', <RoleRoute role="administrador" />)).toBe('')
  })
})

describe('RoleSwitcher', () => {
  it('no se muestra si el cambio de rol no está permitido (VITE_PERMITIR_CAMBIO_ROL no es "true" en las pruebas)', () => {
    expect(pintar({ estado: 'verificada', restaurada: true }, 'administrador', <RoleSwitcher />)).toBe('')
  })
})
