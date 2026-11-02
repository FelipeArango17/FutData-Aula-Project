import { describe, expect, it, vi } from 'vitest'

// Sin el módulo conectado a la API (y siempre en las pruebas) las funciones del alta por enlace no tocan la red.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('./apiClient.js', () => api)

import * as servicio from './entrenadoresService.js'

describe('enlace por correo sin API', () => {
  it('crearContrasena y reenviarEnlace lanzan NO_DISPONIBLE sin llamar a la red', async () => {
    await expect(servicio.crearContrasena('tok', 'Clave2026x')).rejects.toMatchObject({ codigo: 'NO_DISPONIBLE' })
    await expect(servicio.reenviarEnlace('id')).rejects.toMatchObject({ codigo: 'NO_DISPONIBLE' })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
})
