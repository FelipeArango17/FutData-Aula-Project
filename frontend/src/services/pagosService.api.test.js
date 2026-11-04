import { beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API del servicio de pagos: el cliente HTTP está simulado (nunca hay red) y el interruptor del módulo "pagos" va encendido.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'pagos' }))

import * as servicio from './pagosService.js'

const ID = '7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11'
const DEPORTISTA = '11111111-2222-4333-8444-555555555555'
const dto = (extra = {}) => ({
  id: ID, idDeportista: DEPORTISTA, nombreDeportista: 'Pedro Gómez', tipo: 'MENSUALIDAD', concepto: 'Mensualidad noviembre 2026', monto: 90000,
  periodo: '2026-11-01', fechaVencimiento: '2026-11-05', estado: 'PENDIENTE', vencido: false, fechaPago: null, metodo: null,
  referenciaWompi: null, idTransaccionWompi: null, numeroComprobante: null, notaInterna: null, ...extra,
})

beforeEach(() => {
  api.apiRequest.mockReset()
  api.apiListaPaginada.mockReset()
})

describe('con el interruptor encendido', () => {
  it('pagosPorApi es true', () => {
    expect(servicio.pagosPorApi()).toBe(true)
  })
})

describe('acudiente', () => {
  it('obtenerEstadoCuenta usa GET /pagos/mis-pagos y adapta la respuesta (estado derivado de estado + vencido)', async () => {
    api.apiRequest.mockResolvedValue([
      { ...dto({ vencido: true }), tieneComprobante: false },
      { ...dto({ id: 'otro', estado: 'APROBADO', fechaPago: '2026-10-03', metodo: 'PSE' }), tieneComprobante: true },
    ])
    const r = await servicio.obtenerEstadoCuenta({ rol: 'acudiente', correo: 'x@y.co' })
    expect(api.apiRequest).toHaveBeenCalledWith('/pagos/mis-pagos')
    expect(r.deportistas).toHaveLength(1)
    expect(r.deportistas[0].deportista.id).toBe(DEPORTISTA)
    expect(r.deportistas[0].pagos.map((p) => p.estado).sort()).toEqual(['pagado', 'vencido'])
    expect(r.totalVencido).toBe(90000)
  })

  it('obtenerEstadoCuenta propaga el error de la API', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 403 }))
    await expect(servicio.obtenerEstadoCuenta({})).rejects.toMatchObject({ estado: 403 })
  })

  it('el CSV del estado de cuenta incluye los cobros rechazados', async () => {
    api.apiRequest.mockResolvedValue([{ ...dto({ estado: 'RECHAZADO' }), tieneComprobante: false }])
    const { contenido } = await servicio.prepararDescargaEstadoCuenta({})
    expect(contenido).toContain('Rechazado')
  })

  it('iniciarPagoEnLinea usa POST /pagos/{id}/iniciar-wompi sin enviar monto ni cuerpo', async () => {
    const respuesta = { llavePublica: 'pub_test_x', referencia: `FD-${ID}`, montoEnCentavos: 9000000, moneda: 'COP', firmaIntegridad: 'f', urlRedireccion: null }
    api.apiRequest.mockResolvedValue(respuesta)
    expect(await servicio.iniciarPagoEnLinea(ID)).toBe(respuesta)
    expect(api.apiRequest).toHaveBeenCalledWith(`/pagos/${ID}/iniciar-wompi`, { method: 'POST' })
  })

  it('el id se usa como texto: nunca se convierte a número', async () => {
    api.apiRequest.mockResolvedValue({})
    await servicio.iniciarPagoEnLinea('00000000-0000-0000-0000-000000000123')
    expect(api.apiRequest.mock.calls[0][0]).toBe('/pagos/00000000-0000-0000-0000-000000000123/iniciar-wompi')
  })

  it('reintentarPago usa POST /pagos/{id}/reintentar y devuelve el cobro nuevo adaptado', async () => {
    api.apiRequest.mockResolvedValue({ ...dto({ id: 'nuevo' }), tieneComprobante: false })
    const nuevo = await servicio.reintentarPago(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/pagos/${ID}/reintentar`, { method: 'POST' })
    expect(nuevo).toMatchObject({ id: 'nuevo', estado: 'pendiente' })
  })

  it('descargarComprobante pide GET /pagos/{id}/comprobante como binario', async () => {
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' })
    api.apiRequest.mockResolvedValue(pdf)
    const r = await servicio.descargarComprobante(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/pagos/${ID}/comprobante`, { respuesta: 'blob' })
    expect(r.blob).toBe(pdf)
    expect(r.nombreArchivo).toBe('comprobante-de-pago.pdf')
  })
})

describe('tesorero y administrador', () => {
  it('listarCobros pide GET /pagos paginado y devuelve { items, total, pagina, tamano } con los cobros adaptados', async () => {
    api.apiListaPaginada.mockResolvedValue({ items: [dto({ referenciaWompi: 'FD-x' })], total: 41, pagina: 2, tamano: 10 })
    const r = await servicio.listarCobros({ estado: 'PENDIENTE', tipo: 'UNIFORME', periodo: '2026-11', page: 2, size: 10, sort: 'monto,asc' })
    expect(api.apiListaPaginada).toHaveBeenCalledWith('/pagos', { page: 2, size: 10, sort: 'monto,asc', estado: 'PENDIENTE', tipo: 'UNIFORME', periodo: '2026-11', idDeportista: undefined })
    expect(r).toMatchObject({ total: 41, pagina: 2, tamano: 10 })
    expect(r.items[0]).toMatchObject({ id: ID, estado: 'pendiente', iniciadoEnWompi: true })
  })

  it('listarCobros usa valores por defecto razonables', async () => {
    api.apiListaPaginada.mockResolvedValue({ items: [], total: 0, pagina: 0, tamano: 20 })
    await servicio.listarCobros()
    expect(api.apiListaPaginada).toHaveBeenCalledWith('/pagos', expect.objectContaining({ page: 0, size: 20, sort: 'vencimiento,desc' }))
  })

  it('listarTodosLosCobros recorre las páginas de 100 hasta traerlos todos', async () => {
    api.apiListaPaginada
      .mockResolvedValueOnce({ items: [dto({ id: 'a' }), dto({ id: 'b' })], total: 3, pagina: 0, tamano: 100 })
      .mockResolvedValueOnce({ items: [dto({ id: 'c' })], total: 3, pagina: 1, tamano: 100 })
    const r = await servicio.listarTodosLosCobros()
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(2)
    expect(api.apiListaPaginada).toHaveBeenNthCalledWith(1, '/pagos', { page: 0, size: 100, sort: 'vencimiento,desc' })
    expect(api.apiListaPaginada).toHaveBeenNthCalledWith(2, '/pagos', { page: 1, size: 100, sort: 'vencimiento,desc' })
    expect(r.items.map((c) => c.id)).toEqual(['a', 'b', 'c'])
    expect(r).toMatchObject({ total: 3, truncado: false })
    expect(r.items[0].estado).toBe('pendiente') // vienen adaptados
  })

  it('listarTodosLosCobros se detiene en el tope de páginas y avisa con truncado', async () => {
    api.apiListaPaginada.mockResolvedValue({ items: [dto()], total: 1000, pagina: 0, tamano: 100 })
    const r = await servicio.listarTodosLosCobros({ maxPaginas: 3 })
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(3)
    expect(r).toMatchObject({ truncado: true, total: 1000 })
    expect(r.items).toHaveLength(3)
  })

  it('listarTodosLosCobros sin cobros devuelve lista vacía y no pide más páginas', async () => {
    api.apiListaPaginada.mockResolvedValue({ items: [], total: 0, pagina: 0, tamano: 100 })
    expect(await servicio.listarTodosLosCobros()).toEqual({ items: [], total: 0, truncado: false })
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(1)
  })

  it('listarTodosLosCobros no come el error de la API', async () => {
    api.apiListaPaginada.mockRejectedValue(Object.assign(new Error('x'), { estado: 503 }))
    await expect(servicio.listarTodosLosCobros()).rejects.toMatchObject({ estado: 503 })
  })

  it('aprobarPagoManual usa POST /aprobar con fecha, método y nota', async () => {
    api.apiRequest.mockResolvedValue(dto({ estado: 'APROBADO' }))
    const r = await servicio.aprobarPagoManual(ID, { fechaPago: '2026-11-02', metodo: 'EFECTIVO', nota: ' En sede ' })
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe(`/pagos/${ID}/aprobar`)
    expect(opciones.method).toBe('POST')
    expect(JSON.parse(opciones.body)).toEqual({ fechaPago: '2026-11-02', metodo: 'EFECTIVO', nota: 'En sede' })
    expect(r.estado).toBe('pagado')
  })

  it('aprobarPagoManual omite la nota vacía', async () => {
    api.apiRequest.mockResolvedValue(dto())
    await servicio.aprobarPagoManual(ID, { fechaPago: '2026-11-02', metodo: 'TRANSFERENCIA', nota: '  ' })
    expect(JSON.parse(api.apiRequest.mock.calls[0][1].body)).toEqual({ fechaPago: '2026-11-02', metodo: 'TRANSFERENCIA' })
  })

  it('aprobar un cobro iniciado en Wompi: el 409 del backend llega tal cual', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 409, campos: ['estado'], mensaje: 'estado: el cobro ya se inició en Wompi' }))
    await expect(servicio.aprobarPagoManual(ID, { fechaPago: '2026-11-02', metodo: 'EFECTIVO' })).rejects.toMatchObject({ estado: 409, campos: ['estado'] })
  })

  it('rechazarCobro usa POST /rechazar con la nota opcional', async () => {
    api.apiRequest.mockResolvedValue(dto({ estado: 'RECHAZADO' }))
    expect((await servicio.rechazarCobro(ID, 'Duplicado')).estado).toBe('rechazado')
    expect(api.apiRequest.mock.calls[0][0]).toBe(`/pagos/${ID}/rechazar`)
    expect(JSON.parse(api.apiRequest.mock.calls[0][1].body)).toEqual({ nota: 'Duplicado' })
    await servicio.rechazarCobro(ID, '')
    expect(JSON.parse(api.apiRequest.mock.calls[1][1].body)).toEqual({})
  })

  it('conciliarCobro usa POST /conciliar', async () => {
    api.apiRequest.mockResolvedValue(dto({ estado: 'APROBADO', numeroComprobante: 'FD-2026-000001' }))
    const r = await servicio.conciliarCobro(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/pagos/${ID}/conciliar`, { method: 'POST' })
    expect(r).toMatchObject({ estado: 'pagado', tieneComprobante: true })
  })

  it('crearCobro usa POST /pagos con el monto como número entero', async () => {
    api.apiRequest.mockResolvedValue(dto())
    await servicio.crearCobro({ idDeportista: DEPORTISTA, tipo: 'MENSUALIDAD', concepto: '', monto: '90000', fechaVencimiento: '2026-11-05', periodo: '2026-11-01' })
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe('/pagos')
    expect(JSON.parse(opciones.body)).toEqual({ idDeportista: DEPORTISTA, tipo: 'MENSUALIDAD', monto: 90000, fechaVencimiento: '2026-11-05', periodo: '2026-11-01' })
  })

  it('generarMensualidades usa POST /pagos/generar-mensualidades con el periodo AAAA-MM', async () => {
    api.apiRequest.mockResolvedValue({ periodo: '2026-12', creadas: 12, yaExistentes: 3 })
    expect(await servicio.generarMensualidades({ periodo: '2026-12' })).toEqual({ periodo: '2026-12', creadas: 12, yaExistentes: 3 })
    expect(api.apiRequest.mock.calls[0][0]).toBe('/pagos/generar-mensualidades')
    expect(JSON.parse(api.apiRequest.mock.calls[0][1].body)).toEqual({ periodo: '2026-12' })
  })
})
