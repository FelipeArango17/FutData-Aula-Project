import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import Drawer from './Drawer.jsx'
import { sampleUsers } from '../config/menus.js'
import { AuthContext } from '../context/AuthContext.js'

// El menú de navegación no es lo que se prueba aquí (necesita router y accesibilidad).
vi.mock('./NavMenu.jsx', () => ({ default: () => null }))

const pintar = (auth, role) =>
  renderToStaticMarkup(
    <AuthContext.Provider value={auth}>
      <Drawer open onClose={() => {}} role={role} />
    </AuthContext.Provider>,
  )

describe('Drawer: tarjeta de usuario', () => {
  it('con sesión verificada muestra el nombre, las iniciales y el rol de la sesión', () => {
    const html = pintar({ estado: 'verificada', usuario: { correo: 'p@x.co', rol: 'administrador', nombre: 'Pedro Gómez' } }, 'administrador')
    expect(html).toContain('Pedro Gómez')
    expect(html).toContain('>PG<')
    expect(html).toContain('Administrador')
    expect(html).not.toContain('Carlos Mendoza')
  })

  it('con un solo nombre muestra ese nombre', () => {
    const html = pintar({ estado: 'verificada', usuario: { rol: 'acudiente', nombre: 'Pedro' } }, 'acudiente')
    expect(html).toContain('Pedro')
    expect(html).not.toContain('María Torres')
    expect(html).toContain('Acudiente')
  })

  it('sesión verificada sin nombre no muestra ningún nombre de ejemplo: usa el correo, y si no, el rol', () => {
    const conCorreo = pintar({ estado: 'verificada', usuario: { correo: 'sin.nombre@x.co', rol: 'administrador', nombre: '  ' } }, 'administrador')
    expect(conCorreo).toContain('sin.nombre@x.co')
    const soloRol = pintar({ estado: 'verificada', usuario: { rol: 'tesorero' } }, 'tesorero')
    expect(soloRol).toContain('Tesorero')
    for (const html of [conCorreo, soloRol]) {
      for (const nombre of Object.values(sampleUsers)) expect(html).not.toContain(nombre)
    }
  })

  it('sin sesión real conserva el comportamiento anterior', () => {
    expect(pintar({ estado: 'sin-sesion' }, 'administrador')).toContain('Carlos Mendoza')
    expect(pintar(null, 'acudiente')).toContain('María Torres')
  })
})
