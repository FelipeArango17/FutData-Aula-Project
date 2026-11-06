import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Módulo "documentos" con la API activa. Sin red y sin depender de .env.local: el cliente HTTP está simulado y el interruptor sale de
// vi.stubEnv (MODE distinto de "test", porque en "test" la API siempre está apagada). El servicio se importa DESPUÉS de fijar el entorno.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const DOC = '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d'
const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const acudiente = { rol: 'acudiente', correo: 'acudiente@pradofc.co' }
const archivo = { name: 'cert.pdf', type: 'application/pdf', size: 1000 }
// Forma real del contrato (Omar)
const documento = (extra = {}) => ({
  id: DOC, idDeportista: ID, tipo: 'CERTIFICADO_ESTUDIO', nombreOriginal: 'cert.pdf', tipoContenido: 'application/pdf', tamanoBytes: 1000, estado: 'PENDIENTE',
  comentario: null, fechaVencimiento: null, cargadoPor: 'Acudiente', cargadoPorCorreo: 'acudiente@pradofc.co', cargadoEn: '2026-11-01T15:00:00Z', revisadoPor: null, revisadoEn: null, ...extra,
})
const error = (estado, mensaje = 'x', campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })

let servicio
async function cargar(modulos = 'documentos') {
  vi.resetModules()
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', modulos)
  servicio = await import('./documentosService.js')
}

beforeEach(() => {
  for (const f of Object.values(api)) f.mockReset()
})
afterEach(() => vi.unstubAllEnvs())

describe('documentos con la API', () => {
  it('sin "documentos" en VITE_API_MODULOS sigue en memoria (y no llama a la red)', async () => {
    await cargar('deportistas')
    expect(servicio.documentosPorApi()).toBe(false)
    vi.useFakeTimers()
    const p = servicio.listarDocumentos(1)
    await vi.runAllTimersAsync()
    expect(Array.isArray((await p).documentos)).toBe(true)
    vi.useRealTimers()
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('listarDocumentos: GET /deportistas/{id}/documentos devuelve un OBJETO { documentos, estadoInscripcion, documentosFaltantes }, el más reciente primero', async () => {
    await cargar()
    api.apiRequest.mockResolvedValue({
      documentos: [documento({ id: 'a', cargadoEn: '2026-10-01T10:00:00Z' }), documento({ id: 'b', cargadoEn: '2026-11-01T10:00:00Z' })],
      estadoInscripcion: 'PENDIENTE', documentosFaltantes: ['CERTIFICADO_ESTUDIO'],
    })
    const r = await servicio.listarDocumentos(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/deportistas/${ID}/documentos`)
    expect(r.documentos.map((d) => d.id)).toEqual(['b', 'a'])
    expect(r).toMatchObject({ estadoInscripcion: 'PENDIENTE', documentosFaltantes: ['CERTIFICADO_ESTUDIO'] })
  })

  it('subirDocumento: POST multipart con tipo, archivo y fechaVencimiento (opcional)', async () => {
    await cargar()
    api.subirArchivo.mockResolvedValue(documento())
    expect(await servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo, fechaVencimiento: '' }, admin)).toMatchObject({ id: DOC })
    expect(api.subirArchivo).toHaveBeenCalledWith(`/deportistas/${ID}/documentos`, archivo, { tipo: 'PAZ_Y_SALVO', fechaVencimiento: null })
    await servicio.subirDocumento(ID, { tipo: 'TRASPASO_NACIONAL', archivo, fechaVencimiento: '2099-01-31' }, admin)
    expect(api.subirArchivo).toHaveBeenLastCalledWith(`/deportistas/${ID}/documentos`, archivo, { tipo: 'TRASPASO_NACIONAL', fechaVencimiento: '2099-01-31' })
  })

  it('subirDocumento: los 7 tipos se aceptan; un archivo o tipo inválido no llega a la red', async () => {
    await cargar()
    api.subirArchivo.mockResolvedValue(documento())
    for (const tipo of servicio.TIPOS_DOCUMENTO_JUGADOR) await servicio.subirDocumento(ID, { tipo, archivo }, admin)
    expect(api.subirArchivo).toHaveBeenCalledTimes(7)
    api.subirArchivo.mockClear()
    await expect(servicio.subirDocumento(ID, { tipo: 'FICHA_MEDICA', archivo }, admin)).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { tipo: expect.any(String) } })
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo: { ...archivo, size: 6 * 1024 * 1024 } }, admin)).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo: { ...archivo, type: 'image/gif' } }, admin)).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
    expect(api.subirArchivo).not.toHaveBeenCalled()
  })

  it('verDocumento: pide la URL firmada en cada llamada (no la guarda)', async () => {
    await cargar()
    api.apiRequest.mockResolvedValueOnce({ url: 'https://x/a?token=1', vigenciaSegundos: 300 }).mockResolvedValueOnce({ url: 'https://x/a?token=2', vigenciaSegundos: 300 })
    expect(await servicio.verDocumento(DOC, admin)).toEqual({ url: 'https://x/a?token=1', vigenciaSegundos: 300 })
    expect((await servicio.verDocumento(DOC, admin)).url).toBe('https://x/a?token=2')
    expect(api.apiRequest).toHaveBeenCalledWith(`/documentos/${DOC}/descarga`)
    expect(api.apiRequest).toHaveBeenCalledTimes(2)
  })

  it('aprobarDocumento: POST sin cuerpo; devuelve { documento, estadoInscripcion }', async () => {
    await cargar()
    api.apiRequest.mockResolvedValue({ documento: documento({ estado: 'APROBADO' }), estadoInscripcion: 'COMPLETA' })
    const r = await servicio.aprobarDocumento(DOC, admin)
    expect(api.apiRequest).toHaveBeenCalledWith(`/documentos/${DOC}/aprobar`, { method: 'POST' })
    expect(r).toMatchObject({ documento: { estado: 'APROBADO' }, estadoInscripcion: 'COMPLETA' })
  })

  it('rechazarDocumento: POST { comentario } recortado y devuelve avisoEnviado tal cual (true, false o null)', async () => {
    await cargar()
    for (const avisoEnviado of [true, false, null]) {
      api.apiRequest.mockResolvedValueOnce({ documento: documento({ estado: 'RECHAZADO', comentario: 'Borroso' }), estadoInscripcion: 'PENDIENTE', avisoEnviado })
      expect((await servicio.rechazarDocumento(DOC, { comentario: '  Borroso ' }, admin)).avisoEnviado).toBe(avisoEnviado)
    }
    expect(api.apiRequest).toHaveBeenCalledWith(`/documentos/${DOC}/rechazar`, { method: 'POST', body: JSON.stringify({ comentario: 'Borroso' }) })
  })

  it('rechazarDocumento: comentario vacío o de más de 300 caracteres no llega a la red', async () => {
    await cargar()
    for (const comentario of [undefined, '   ']) await expect(servicio.rechazarDocumento(DOC, { comentario }, admin)).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { comentario: 'Escribe el motivo del rechazo.' } })
    await expect(servicio.rechazarDocumento(DOC, { comentario: 'a'.repeat(301) }, admin)).rejects.toMatchObject({ errores: { comentario: expect.stringContaining('300') } })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('errores en español: 400, 403, 404 (distinto para el acudiente), 409 y fallo de red', async () => {
    await cargar()
    api.subirArchivo.mockRejectedValueOnce(error(400, 'No se pudo completar la operación.', ['archivo']))
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo }, admin)).rejects.toMatchObject({ codigo: 'VALIDACION', message: expect.stringContaining('PDF, JPG o PNG') })
    api.subirArchivo.mockRejectedValueOnce(error(400, 'tipo: no es un tipo válido', ['tipo']))
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo }, admin)).rejects.toMatchObject({ errores: { tipo: expect.any(String) } })
    api.apiRequest.mockRejectedValueOnce(error(403))
    await expect(servicio.listarDocumentos(ID)).rejects.toMatchObject({ codigo: 'SIN_PERMISO', message: 'No tienes permiso para esta acción con los documentos.' })
    api.subirArchivo.mockRejectedValueOnce(error(404))
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo }, acudiente)).rejects.toMatchObject({ codigo: 'NO_ENCONTRADO', message: 'No encontramos ese jugador entre los tuyos.' })
    api.apiRequest.mockRejectedValueOnce(error(404))
    await expect(servicio.aprobarDocumento(DOC, admin)).rejects.toMatchObject({ codigo: 'NO_ENCONTRADO', message: 'No encontramos ese documento o ese deportista.' })
    api.subirArchivo.mockRejectedValueOnce(error(409))
    await expect(servicio.subirDocumento(ID, { tipo: 'PAZ_Y_SALVO', archivo }, admin)).rejects.toMatchObject({ codigo: 'DOCUMENTO_ACTIVO', message: 'Ya hay un documento activo de este tipo.' })
    api.apiRequest.mockRejectedValueOnce(Object.assign(new Error('x'), { estado: 0, mensaje: 'x', campos: [], codigo: 'RED' }))
    await expect(servicio.listarDocumentos(ID)).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
  })

  it('listarJugadoresDelAcudiente: GET /acudientes/me/deportistas (solo los suyos), con la foto ya incluida', async () => {
    await cargar()
    api.apiRequest.mockResolvedValue([{ id: ID, nombre: 'Luis', apellido: 'Gómez', foto: 'https://x/f' }, { id: 'otro', nombre: 'Ana', apellido: 'Ruiz', foto: null }])
    expect(await servicio.listarJugadoresDelAcudiente(acudiente)).toEqual([{ id: ID, nombre: 'Luis Gómez', tieneFoto: true }, { id: 'otro', nombre: 'Ana Ruiz', tieneFoto: false }])
    expect(api.apiRequest).toHaveBeenCalledWith('/acudientes/me/deportistas')
  })
})
