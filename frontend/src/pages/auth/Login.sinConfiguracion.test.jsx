import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Producción sin variables de Supabase: se simula con un módulo auth que dice "sin configuración".
vi.mock('../../services/auth.js', async (importar) => ({ ...(await importar()), sinConfiguracion: () => true }))

import Login from './Login.jsx'
import { AuthContext } from '../../context/AuthContext.js'

describe('Login sin configuración (producción)', () => {
  it('muestra el mensaje de configuración y ni el formulario ni las cuentas demo', () => {
    const auth = { estado: 'sin-sesion', usuario: null, iniciarSesion: () => {}, retomarCodigo: false, restaurada: true }
    const html = renderToStaticMarkup(
      <MemoryRouter><AuthContext.Provider value={auth}><Login /></AuthContext.Provider></MemoryRouter>,
    )
    expect(html).toContain('La aplicación no está configurada. Contacta al administrador.')
    expect(html).not.toContain('<form')
    expect(html).not.toContain('Correo electrónico')
    expect(html).not.toContain('pradofc.co')
    expect(html).not.toContain('Demo2026')
  })
})
