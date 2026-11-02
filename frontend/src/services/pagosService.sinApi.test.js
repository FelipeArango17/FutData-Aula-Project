import { describe, expect, it, vi } from 'vitest'

// Con el interruptor apagado (y siempre en las pruebas) nada llama a la API: los datos son de ejemplo.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('./apiClient.js', () => api)

import * as servicio from './pagosService.js'

describe('con el interruptor apagado', () => {
  it('pagosPorApi es false y el estado de cuenta sale de los datos de ejemplo', async () => {
    vi.useFakeTimers()
    expect(servicio.pagosPorApi()).toBe(false)
    const promesa = servicio.obtenerEstadoCuenta({ rol: 'acudiente', correo: 'acudiente@pradofc.co' })
    await vi.runAllTimersAsync()
    expect((await promesa).deportistas.length).toBeGreaterThan(0)
    expect(api.apiRequest).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('las funciones nuevas no llaman a la API: avisan que el módulo no está conectado', async () => {
    for (const llamar of [
      () => servicio.iniciarPagoEnLinea('x'), () => servicio.reintentarPago('x'), () => servicio.descargarComprobante('x'), () => servicio.listarCobros(),
      () => servicio.aprobarPagoManual('x', {}), () => servicio.rechazarCobro('x'), () => servicio.conciliarCobro('x'), () => servicio.crearCobro({}),
      () => servicio.generarMensualidades({ periodo: '2026-11' }),
    ]) {
      await expect(llamar()).rejects.toMatchObject({ codigo: 'NO_DISPONIBLE' })
    }
    expect(api.apiRequest).not.toHaveBeenCalled()
    expect(api.apiListaPaginada).not.toHaveBeenCalled()
  })
})
