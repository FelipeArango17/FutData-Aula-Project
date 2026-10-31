import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Login from './Login.jsx'
import { AuthContext } from '../../context/AuthContext.js'

// Cuando el backend rechaza la sesión en /verificar (401, 403 o cuenta inactiva), Verificar vuelve a /login con el
// motivo en el estado de la ruta y el login lo muestra como error.
const dibujar = (estadoRuta) => renderToStaticMarkup(
  <MemoryRouter initialEntries={[{ pathname: '/login', state: estadoRuta }]}>
    <AuthContext.Provider value={{ estado: 'sin-sesion', usuario: null, iniciarSesion: vi.fn(), retomarCodigo: false }}>
      <Login />
    </AuthContext.Provider>
  </MemoryRouter>,
)

describe('login con el motivo de cierre', () => {
  it('401 del backend (token sin session_id): pide iniciar sesión de nuevo', () => {
    expect(dibujar({ motivo: 'SESION_INVALIDA' })).toContain('No pudimos validar tu sesión. Inicia sesión de nuevo.')
  })

  it('403 del backend (cuenta sin roles): explica que no tiene acceso', () => {
    expect(dibujar({ motivo: 'SIN_ROL' })).toContain('Tu cuenta no tiene acceso a FutData')
  })

  it('cuenta inactiva', () => {
    expect(dibujar({ motivo: 'INACTIVA' })).toContain('Tu cuenta no está activa. Contacta al administrador.')
  })

  it('un motivo desconocido o sin motivo no muestra ningún error (la región de alertas queda vacía)', () => {
    for (const estado of [{ motivo: 'OTRA_COSA' }, null]) {
      const html = dibujar(estado)
      for (const texto of ['No pudimos validar', 'no tiene acceso a FutData', 'no está activa', 'OTRA_COSA']) {
        expect(html).not.toContain(texto)
      }
      expect(html).toContain('Iniciar sesión')
    }
  })
})
