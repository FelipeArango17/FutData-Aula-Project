import { describe, expect, it, vi } from 'vitest'

// Con el interruptor apagado (y siempre en las pruebas) nada llama a la API: los datos son de ejemplo, con ids numéricos.
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)

import * as servicio from './horarioClubService.js'

describe('con el interruptor apagado', () => {
  it('horarioClubPorApi es false y todo sale de los datos de ejemplo sin llamar a la API', async () => {
    vi.useFakeTimers()
    expect(servicio.horarioClubPorApi()).toBe(false)
    const lista = servicio.listar({ categoria: 'Sub-12' })
    await vi.runAllTimersAsync()
    const franjas = await lista
    expect(franjas.length).toBeGreaterThan(0)
    expect(franjas.every((f) => typeof f.id === 'number')).toBe(true)
    const creada = servicio.crear({ idCategoria: '2', dia: '3', horaInicio: '10:00', horaFin: '11:00', sede: 'Horizontes', idEntrenador: '', grupo: '' })
    await vi.runAllTimersAsync()
    expect((await creada).idEntrenador).toBeNull()
    expect(api.apiRequest).not.toHaveBeenCalled()
    vi.useRealTimers()
  })
})
