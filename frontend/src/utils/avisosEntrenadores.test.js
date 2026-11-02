import { describe, expect, it, vi } from 'vitest'
import { avisoDeAlta, reenviarConAviso } from './avisosEntrenadores.js'

describe('avisoDeAlta', () => {
  it('correoEnviado true: éxito con el correo', () => {
    expect(avisoDeAlta(true, 'luis@pradofc.co')).toEqual({ texto: 'Enviamos el enlace a luis@pradofc.co', tono: 'success' })
  })
  it('correoEnviado false: advertencia que manda a Reenviar enlace', () => {
    expect(avisoDeAlta(false, 'luis@pradofc.co')).toEqual({
      texto: 'El entrenador quedó registrado, pero no se pudo enviar el correo. Usa Reenviar enlace en la lista.', tono: 'warning',
    })
  })
  it('sin dato (sin API): el aviso de siempre', () => {
    expect(avisoDeAlta(undefined, 'a@b.co')).toEqual({ texto: 'Entrenador registrado.', tono: 'success' })
  })
})

describe('reenviarConAviso', () => {
  const luis = { id: 'u1', correo: 'luis@pradofc.co' }
  it('éxito: confirma con el correo', async () => {
    const reenviar = vi.fn().mockResolvedValue(null)
    expect(await reenviarConAviso(luis, reenviar)).toEqual({ ok: true, texto: 'Enviamos de nuevo el enlace a luis@pradofc.co' })
    expect(reenviar).toHaveBeenCalledWith('u1')
  })
  it('4xx: muestra el mensaje del backend', async () => {
    const reenviar = vi.fn().mockRejectedValue(Object.assign(new Error('x'), { estado: 409, mensaje: 'El entrenador ya creó su contraseña.' }))
    expect(await reenviarConAviso(luis, reenviar)).toEqual({ ok: false, texto: 'El entrenador ya creó su contraseña.' })
  })
})
