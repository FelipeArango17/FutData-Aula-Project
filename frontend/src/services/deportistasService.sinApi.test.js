import { describe, expect, it, vi } from 'vitest'

// Sin el módulo "deportistas" en VITE_API_MODULOS (y siempre en las pruebas) nada llama a la API: ids numéricos y datos de ejemplo.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)

import * as servicio from './deportistasService.js'

describe('con el interruptor apagado', () => {
  it('deportistasPorApi es false y todo sale de los datos de ejemplo (ids numéricos), sin tocar la red', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
    expect(servicio.deportistasPorApi()).toBe(false)
    const pagina = servicio.listarPaginaDeportistas({ pagina: 1 })
    const cuenta = servicio.contarDeportistas()
    const categorias = servicio.listarCategorias()
    await vi.runAllTimersAsync()
    const [p, c, cats] = await Promise.all([pagina, cuenta, categorias])
    expect(p.items).toHaveLength(10)
    expect(p).toMatchObject({ pagina: 1, total: 21 })
    expect(typeof p.items[0].id).toBe('number')
    expect(c).toEqual({ total: 21, activos: 18 })
    expect(cats).toHaveLength(9)
    expect(api.apiRequest).not.toHaveBeenCalled()
    expect(api.apiListaPaginada).not.toHaveBeenCalled()
    vi.useRealTimers()
  })
  it('edadDe usa la edad de la API si viene y, si no, la calcula con la fecha de nacimiento', () => {
    expect(servicio.edadDe({ edad: 10, fechaNacimiento: '2000-01-01' })).toBe(10)
    expect(servicio.edadDe({ fechaNacimiento: '2016-05-09' }, '2026-10-22')).toBe(10)
  })
})
