import { describe, expect, it } from 'vitest'
import { listarUsuarios, usuariosPorApi, configurarSimulacion } from './usuariosService.js'

// Sin el interruptor (y siempre en las pruebas) el servicio usa los datos de ejemplo, con ids numéricos.
describe('sin el interruptor', () => {
  it('usuariosPorApi es false y la lista es la de ejemplo', async () => {
    configurarSimulacion({ retardo: 0 })
    expect(usuariosPorApi()).toBe(false)
    const lista = await listarUsuarios({}, { rol: 'administrador', correo: 'administrador@pradofc.co' })
    expect(lista[0].id).toBe(1)
  })
})
