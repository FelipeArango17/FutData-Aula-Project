import { describe, expect, it, vi } from 'vitest'
import { resolverEleccionDeFoto } from './eleccionFoto.js'

const foto = (extra = {}) => ({ name: 'foto.jpg', type: 'image/jpeg', size: 200 * 1024, ...extra })

describe('resolverEleccionDeFoto: en el asistente (guardarAlElegir)', () => {
  it('un archivo válido se adjunta de inmediato, sin pulsar nada más', async () => {
    const onGuardar = vi.fn(async () => {})
    const archivo = foto()
    expect(await resolverEleccionDeFoto(archivo, { guardarAlElegir: true, onGuardar })).toEqual({ error: null, adjuntada: true, pendiente: false })
    expect(onGuardar).toHaveBeenCalledTimes(1)
    expect(onGuardar).toHaveBeenCalledWith(archivo)
  })

  it('un archivo inválido (PDF, GIF, vacío, de más de 5 MB) muestra el error y no se adjunta', async () => {
    for (const malo of [foto({ type: 'application/pdf' }), foto({ type: 'image/gif' }), foto({ size: 0 }), foto({ size: 6 * 1024 * 1024 })]) {
      const onGuardar = vi.fn(async () => {})
      const r = await resolverEleccionDeFoto(malo, { guardarAlElegir: true, onGuardar })
      expect(r, `${malo.type} ${malo.size}`).toMatchObject({ adjuntada: false, pendiente: false })
      expect(r.error).toEqual(expect.any(String))
      expect(onGuardar).not.toHaveBeenCalled()
    }
  })

  it('el mensaje de un archivo inválido es el mismo con y sin guardarAlElegir', async () => {
    const malo = foto({ type: 'image/gif' })
    const con = await resolverEleccionDeFoto(malo, { guardarAlElegir: true, onGuardar: async () => {} })
    const sin = await resolverEleccionDeFoto(malo, { onGuardar: async () => {} })
    expect(con.error).toBe(sin.error)
    expect(con.error).toMatch(/JPG o PNG/)
  })

  it('si onGuardar falla, muestra el error y no queda adjunta', async () => {
    const r = await resolverEleccionDeFoto(foto(), { guardarAlElegir: true, onGuardar: async () => { throw new Error('No se pudo adjuntar.') } })
    expect(r).toEqual({ error: 'No se pudo adjuntar.', adjuntada: false, pendiente: false })
  })
})

describe('resolverEleccionDeFoto: en la ficha del deportista (sin guardarAlElegir)', () => {
  it('un archivo válido queda pendiente y NO se guarda hasta pulsar "Guardar foto"', async () => {
    const onGuardar = vi.fn(async () => {})
    expect(await resolverEleccionDeFoto(foto(), { onGuardar })).toEqual({ error: null, adjuntada: false, pendiente: true })
    expect(onGuardar).not.toHaveBeenCalled()
  })

  it('un archivo inválido no queda pendiente', async () => {
    expect(await resolverEleccionDeFoto(foto({ type: 'image/gif' }), { onGuardar: async () => {} })).toMatchObject({ adjuntada: false, pendiente: false, error: expect.any(String) })
  })
})
