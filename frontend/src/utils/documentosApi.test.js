import { describe, expect, it } from 'vitest'
import { adaptarListaDocumentos, avisoDeRechazo, errorDeDocumentos, mensajeDeError } from './documentosApi.js'
import { calcularInscripcion, estadoDelTipo, textoFaltantes, tiposParaSubir } from './documentosClub.js'
import { abrirEnPestanaNueva } from './abrirDocumento.js'
import { vi } from 'vitest'

const doc = (tipo, estado, id, cargadoEn = '2026-11-01T10:00:00Z') => ({ id, tipo, estado, cargadoEn })

describe('avisoDeRechazo: un mensaje distinto por cada caso de avisoEnviado', () => {
  it('true, false y null', () => {
    const textos = [true, false, null].map((v) => avisoDeRechazo(v))
    expect(new Set(textos.map((t) => t.texto)).size).toBe(3)
    expect(textos[0]).toMatchObject({ tono: 'success', texto: expect.stringContaining('Se avisó al acudiente') })
    expect(textos[1]).toMatchObject({ tono: 'warning', texto: expect.stringContaining('no se pudo enviar') })
    expect(textos[2]).toMatchObject({ tono: 'warning', texto: expect.stringContaining('no tiene acudientes con correo') })
  })
})

describe('adaptarListaDocumentos', () => {
  it('acepta el objeto del contrato y tolera claves ausentes', () => {
    expect(adaptarListaDocumentos({})).toEqual({ documentos: [], estadoInscripcion: 'PENDIENTE', documentosFaltantes: [] })
  })
})

describe('errorDeDocumentos / mensajeDeError', () => {
  it('lo que no es de la API queda igual y sin codigo se muestra el texto general', () => {
    const e = new Error('boom')
    expect(errorDeDocumentos(e)).toBe(e)
    expect(mensajeDeError(e, 'general')).toBe('general')
  })
})

describe('reglas de tipos con UUID', () => {
  it('estadoDelTipo desempata por texto y "Subir" se oculta en los activos y vuelve si el último fue rechazado', () => {
    const docs = [doc('CERTIFICADO_ESTUDIO', 'RECHAZADO', 'a', '2026-10-01T10:00:00Z'), doc('DOCUMENTO_IDENTIDAD', 'PENDIENTE', 'b'), doc('PAZ_Y_SALVO', 'APROBADO', 'c')]
    expect(estadoDelTipo(docs, 'CERTIFICADO_ESTUDIO')).toBe('RECHAZADO')
    const ids = tiposParaSubir(docs).map((t) => t.id)
    expect(ids).toContain('CERTIFICADO_ESTUDIO')
    expect(ids).not.toContain('DOCUMENTO_IDENTIDAD')
    expect(ids).not.toContain('PAZ_Y_SALVO')
    const nuevo = [...docs, doc('CERTIFICADO_ESTUDIO', 'PENDIENTE', 'd', '2026-11-02T10:00:00Z')]
    expect(tiposParaSubir(nuevo).map((t) => t.id)).not.toContain('CERTIFICADO_ESTUDIO')
  })
  it('los "si aplica" aprobados no cuentan para COMPLETA ni salen en los faltantes', () => {
    const r = calcularInscripcion([doc('PAZ_Y_SALVO', 'APROBADO', 'x'), doc('CARTA_RENUNCIA', 'APROBADO', 'y'), doc('TRASPASO_NACIONAL', 'APROBADO', 'z')])
    expect(r.estadoInscripcion).toBe('PENDIENTE')
    expect(r.documentosFaltantes).toEqual(['DOCUMENTO_IDENTIDAD', 'CERTIFICADO_ESTUDIO', 'FORMULARIO_INSCRIPCION', 'AUTORIZACION_HABEAS_DATA'])
    expect(textoFaltantes(['CERTIFICADO_ESTUDIO'])).toBe('Faltan: Certificado de estudio')
    expect(textoFaltantes([])).toBeNull()
  })
})

describe('abrirEnPestanaNueva', () => {
  it('abre una pestaña en blanco dentro del clic, le quita el opener y le pone la URL cuando llega', async () => {
    const ventana = { opener: 'x', location: { replace: vi.fn() }, close: vi.fn() }
    vi.stubGlobal('window', { open: vi.fn(() => ventana) })
    await abrirEnPestanaNueva(async () => 'https://x/firmada?token=1')
    expect(window.open).toHaveBeenCalledWith('', '_blank')
    expect(ventana.opener).toBeNull()
    expect(ventana.location.replace).toHaveBeenCalledWith('https://x/firmada?token=1')
    vi.unstubAllGlobals()
  })
  it('si falla la petición o la URL no es https, cierra la pestaña y repite el error', async () => {
    const ventana = { opener: 'x', location: { replace: vi.fn() }, close: vi.fn() }
    vi.stubGlobal('window', { open: vi.fn(() => ventana) })
    await expect(abrirEnPestanaNueva(async () => { throw new Error('red') })).rejects.toThrow('red')
    await expect(abrirEnPestanaNueva(async () => 'javascript:alert(1)')).rejects.toMatchObject({ codigo: 'URL_INVALIDA' })
    expect(ventana.close).toHaveBeenCalledTimes(2)
    expect(ventana.location.replace).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
  it('si el navegador bloquea la pestaña, abre la URL con noopener', async () => {
    vi.stubGlobal('window', { open: vi.fn(() => null) })
    await abrirEnPestanaNueva(async () => 'https://x/a')
    expect(window.open).toHaveBeenLastCalledWith('https://x/a', '_blank', 'noopener,noreferrer')
    vi.unstubAllGlobals()
  })
})
