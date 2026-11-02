import { describe, expect, it, vi } from 'vitest'
import { MENSAJE_ENLACE_INVALIDO, enviarContrasena, validarContrasenas } from './crearContrasena.js'
import { MENSAJES, cumplePoliticaContrasena } from './validaciones.js'

const buenos = { contrasena: 'Clave2026x', confirmar: 'Clave2026x' }

describe('política de contraseña', () => {
  const de = (n, base = 'a1') => base + 'x'.repeat(n - base.length)
  it('entre 8 y 72 caracteres: 7 no, 8 sí, 72 sí, 73 no', () => {
    expect(cumplePoliticaContrasena(de(7))).toBe(false)
    expect(cumplePoliticaContrasena(de(8))).toBe(true)
    expect(cumplePoliticaContrasena(de(72))).toBe(true)
    expect(cumplePoliticaContrasena(de(73))).toBe(false)
  })
  it('exige al menos una letra y un número', () => {
    expect(cumplePoliticaContrasena('sololetrasaqui')).toBe(false)
    expect(cumplePoliticaContrasena('123456789')).toBe(false)
    expect(cumplePoliticaContrasena('Clave2026x')).toBe(true)
    expect(cumplePoliticaContrasena('')).toBe(false)
    expect(cumplePoliticaContrasena(null)).toBe(false)
  })
  it('el mensaje es el acordado con el backend', () => {
    expect(MENSAJES.contrasenaPolitica).toBe('La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.')
  })
})

describe('validarContrasenas', () => {
  it('sin errores si coinciden y cumplen la política', () => {
    expect(validarContrasenas(buenos)).toEqual({})
  })
  it('campos distintos: error en confirmar', () => {
    expect(validarContrasenas({ contrasena: 'Clave2026x', confirmar: 'Clave2026y' })).toEqual({ confirmar: 'Las contraseñas no coinciden.' })
  })
  it('política incumplida y confirmación vacía', () => {
    expect(validarContrasenas({ contrasena: 'abc', confirmar: '' })).toEqual({
      contrasena: MENSAJES.contrasenaPolitica,
      confirmar: 'Repite la contraseña.',
    })
  })
})

describe('enviarContrasena', () => {
  it('con campos distintos no llama al servicio', async () => {
    const crear = vi.fn()
    const r = await enviarContrasena({ token: 't', valores: { contrasena: 'Clave2026x', confirmar: 'otra' }, crear })
    expect(r.tipo).toBe('errores')
    expect(crear).not.toHaveBeenCalled()
  })
  it('sin token: enlace inválido y no llama a la API', async () => {
    const crear = vi.fn()
    expect(await enviarContrasena({ token: null, valores: buenos, crear })).toEqual({ tipo: 'fallo', texto: MENSAJE_ENLACE_INVALIDO })
    expect(crear).not.toHaveBeenCalled()
  })
  it('éxito: envía token y contraseña', async () => {
    const crear = vi.fn().mockResolvedValue(undefined)
    expect(await enviarContrasena({ token: 'tok', valores: buenos, crear })).toEqual({ tipo: 'listo' })
    expect(crear).toHaveBeenCalledWith('tok', 'Clave2026x')
  })
  it('400: el mensaje exacto del enlace', async () => {
    const crear = vi.fn().mockRejectedValue(Object.assign(new Error('x'), { estado: 400, mensaje: 'x' }))
    const r = await enviarContrasena({ token: 'tok', valores: buenos, crear })
    expect(r.texto).toBe('El enlace no es válido o venció; pide al administrador que te lo reenvíe')
  })
  it('400 con el texto de la política: error del campo contraseña, no enlace inválido', async () => {
    const e = Object.assign(new Error('x'), { estado: 400, mensaje: MENSAJES.contrasenaPolitica })
    expect(await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(e) })).toEqual({
      tipo: 'errores', errores: { contrasena: MENSAJES.contrasenaPolitica },
    })
  })
  it('400 con campos "contrasena": error del campo aunque el texto sea otro', async () => {
    const e = Object.assign(new Error('x'), { estado: 400, mensaje: 'contrasena: no cumple', campos: ['contrasena'] })
    expect(await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(e) })).toEqual({
      tipo: 'errores', errores: { contrasena: 'contrasena: no cumple' },
    })
  })
  it('400 de token (cualquier otro mensaje): enlace inválido', async () => {
    const e = Object.assign(new Error('x'), { estado: 400, mensaje: 'El enlace venció.', campos: [] })
    expect((await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(e) })).texto).toBe(MENSAJE_ENLACE_INVALIDO)
  })
  it('503: pide reintentar o que reenvíen el enlace', async () => {
    const e = Object.assign(new Error('x'), { estado: 503, mensaje: 'Servicio no disponible.' })
    expect(await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(e) })).toEqual({
      tipo: 'fallo', texto: 'Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace.',
    })
  })
  it('red y otros errores usan el mensaje del cliente', async () => {
    const red = Object.assign(new Error('r'), { estado: 0, mensaje: 'No pudimos conectar con el servidor. Intenta de nuevo.' })
    expect((await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(red) })).texto).toBe(red.mensaje)
    const e500 = Object.assign(new Error('s'), { estado: 500, mensaje: 'No se pudo completar la operación.' })
    expect((await enviarContrasena({ token: 't', valores: buenos, crear: vi.fn().mockRejectedValue(e500) })).texto).toBe(e500.mensaje)
  })
})
