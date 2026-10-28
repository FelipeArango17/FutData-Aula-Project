import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./pagosService.js')
})
afterEach(() => vi.useRealTimers())

// Espera la respuesta del servicio avanzando el retardo simulado
const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const demo = { rol: 'acudiente', correo: 'acudiente@pradofc.co' }

describe('estadoDelPago', () => {
  it('pagado si tiene fecha de pago, aunque haya vencido', () => {
    expect(servicio.estadoDelPago({ fechaVencimiento: '2026-01-01', fechaPago: '2026-01-05' }, '2026-10-22')).toBe('pagado')
  })

  it('pendiente si no se ha pagado y no ha vencido; vencido si el vencimiento es anterior a hoy', () => {
    expect(servicio.estadoDelPago({ fechaVencimiento: '2026-10-30', fechaPago: null }, '2026-10-22')).toBe('pendiente')
    expect(servicio.estadoDelPago({ fechaVencimiento: '2026-10-10', fechaPago: null }, '2026-10-22')).toBe('vencido')
  })

  it('un pago que vence hoy sigue pendiente', () => {
    expect(servicio.estadoDelPago({ fechaVencimiento: '2026-10-22', fechaPago: null }, '2026-10-22')).toBe('pendiente')
  })
})

describe('obtenerEstadoCuenta', () => {
  it('devuelve solo los dos deportistas del acudiente demo', async () => {
    const r = await resolver(servicio.obtenerEstadoCuenta(demo))
    expect(r.deportistas.map((d) => d.deportista.nombre)).toEqual(['Santiago', 'Valeria'])
  })

  it('el estado de cada pago se deriva de las fechas y no se guarda', async () => {
    const [santiago] = (await resolver(servicio.obtenerEstadoCuenta(demo))).deportistas
    const estados = Object.fromEntries(santiago.pagos.map((p) => [p.concepto, p.estado]))
    expect(estados).toEqual({
      'Inscripción 2026': 'pagado',
      'Mensualidad agosto': 'pagado',
      'Mensualidad septiembre': 'pagado',
      'Uniforme (2.º pago)': 'vencido',
      'Mensualidad octubre': 'pendiente',
    })
  })

  it('totales por deportista y general (pendientes + vencidos)', async () => {
    const r = await resolver(servicio.obtenerEstadoCuenta(demo))
    const [santiago, valeria] = r.deportistas
    expect(santiago.totalPendiente).toBe(180000)
    expect(santiago.totalVencido).toBe(90000)
    expect(valeria.totalPendiente).toBe(0)
    expect(valeria.totalVencido).toBe(0)
    expect(r.totalPendiente).toBe(180000)
    expect(r.totalVencido).toBe(90000)
  })

  it('"desde cuándo" es el vencimiento más antiguo sin pagar; null si está al día', async () => {
    const [santiago, valeria] = (await resolver(servicio.obtenerEstadoCuenta(demo))).deportistas
    expect(santiago.pendienteDesde).toBe('2026-10-10')
    expect(valeria.pendienteDesde).toBeNull()
  })

  it('los pagos van del más reciente al más antiguo', async () => {
    const [santiago] = (await resolver(servicio.obtenerEstadoCuenta(demo))).deportistas
    const fechas = santiago.pagos.map((p) => p.fechaVencimiento)
    expect(fechas).toEqual([...fechas].sort().reverse())
    expect(santiago.pagos).toHaveLength(5)
  })

  it('con otro "hoy" cambian los estados (el mismo pago pasa de pendiente a vencido)', async () => {
    servicio.configurarSimulacion({ hoy: '2026-11-15' })
    const [santiago] = (await resolver(servicio.obtenerEstadoCuenta(demo))).deportistas
    expect(santiago.totalVencido).toBe(180000)
    expect(santiago.pendienteDesde).toBe('2026-10-10')
  })

  it('no se filtran pagos ni deportistas ajenos', async () => {
    const r = await resolver(servicio.obtenerEstadoCuenta(demo))
    const ids = r.deportistas.flatMap((d) => d.pagos.map((p) => p.idDeportista))
    expect(new Set(ids)).toEqual(new Set([1, 2]))
    const texto = JSON.stringify(r)
    expect(texto).not.toContain('Mateo')
    expect(texto).not.toContain('95000')
  })

  it('el correo se compara sin distinguir mayúsculas; otro acudiente no ve deportistas', async () => {
    const mayus = await resolver(servicio.obtenerEstadoCuenta({ rol: 'acudiente', correo: ' ACUDIENTE@pradofc.co ' }))
    expect(mayus.deportistas).toHaveLength(2)
    const otro = await resolver(servicio.obtenerEstadoCuenta({ rol: 'acudiente', correo: 'otro@pradofc.co' }))
    expect(otro).toEqual({ deportistas: [], totalPendiente: 0, totalVencido: 0 })
  })

  it('un actor que no es acudiente o no tiene correo lanza SIN_PERMISO', async () => {
    await expect(resolver(servicio.obtenerEstadoCuenta({ rol: 'tesorero', correo: 'acudiente@pradofc.co' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.obtenerEstadoCuenta({ rol: 'acudiente', correo: '' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.obtenerEstadoCuenta(undefined))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })

  it('un fallo simulado lanza ERROR_DE_RED una sola vez', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.obtenerEstadoCuenta(demo))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    await expect(resolver(servicio.obtenerEstadoCuenta(demo))).resolves.toBeTruthy()
  })
})

describe('generarCsvEstadoCuenta', () => {
  const estadoDe = (pagos, nombre = 'Ana', apellido = 'Pérez') => ({
    deportistas: [{ deportista: { id: 1, nombre, apellido }, pagos }],
  })
  const pago = (extra = {}) => ({ id: 1, idDeportista: 1, concepto: 'Mensualidad', monto: 90000, fechaVencimiento: '2026-10-30', fechaPago: null, estado: 'pendiente', ...extra })

  it('tiene las cabeceras pedidas, separador ";" y líneas CRLF', () => {
    const csv = servicio.generarCsvEstadoCuenta(estadoDe([pago()]))
    const [cabecera, fila] = csv.split('\r\n')
    expect(cabecera).toBe('Deportista;Concepto;Monto (COP);Vencimiento;Fecha de pago;Estado')
    expect(fila).toBe('Ana Pérez;Mensualidad;90000;2026-10-30;;Pendiente')
    expect(csv.endsWith('\r\n')).toBe(true)
  })

  it('conserva las tildes y no agrega BOM (se agrega al descargar)', () => {
    const csv = servicio.generarCsvEstadoCuenta(estadoDe([pago({ concepto: 'Inscripción niño' })], 'José', 'Muñoz'))
    expect(csv).toContain('José Muñoz;Inscripción niño')
    expect(csv.charCodeAt(0)).not.toBe(0xfeff)
  })

  it('escapa comillas, separadores y saltos de línea', () => {
    const csv = servicio.generarCsvEstadoCuenta(estadoDe([pago({ concepto: 'Uniforme "talla M"; con balón\nextra' })]))
    expect(csv).toContain('"Uniforme ""talla M""; con balón\nextra"')
  })

  it('neutraliza la inyección de fórmulas con un apóstrofo previo', () => {
    for (const peligroso of ['=SUMA(A1:A2)', '+57300', '-2+3', '@cmd', '\t=1+1']) {
      const csv = servicio.generarCsvEstadoCuenta(estadoDe([pago({ concepto: peligroso })]))
      expect(csv).toContain(`;'${peligroso};`)
    }
    const nombre = servicio.generarCsvEstadoCuenta(estadoDe([pago()], '=HIPERVINCULO("x")', 'Ruiz'))
    expect(nombre).toContain(`"'=HIPERVINCULO(""x"") Ruiz"`)
  })

  it('un texto normal no lleva apóstrofo y el guion en medio no se toca', () => {
    const csv = servicio.generarCsvEstadoCuenta(estadoDe([pago({ concepto: 'Mensualidad sub-10' })]))
    expect(csv).toContain(';Mensualidad sub-10;')
  })

  it('incluye una fila por pago de todos los deportistas, con fecha de pago y estado', async () => {
    const estado = await resolver(servicio.obtenerEstadoCuenta(demo))
    const lineas = servicio.generarCsvEstadoCuenta(estado).trim().split('\r\n')
    expect(lineas).toHaveLength(1 + 5 + 3)
    expect(lineas).toContain('Santiago Ríos Demo;Mensualidad septiembre;90000;2026-09-30;2026-09-15;Pagado')
    expect(lineas).toContain('Santiago Ríos Demo;Uniforme (2.º pago);90000;2026-10-10;;Vencido')
    expect(lineas.join('\n')).not.toContain('Mateo')
  })
})

describe('prepararDescargaEstadoCuenta', () => {
  it('devuelve el nombre estado-de-cuenta-AAAA-MM-DD.csv y el CSV completo', async () => {
    const r = await resolver(servicio.prepararDescargaEstadoCuenta(demo))
    expect(r.nombreArchivo).toBe('estado-de-cuenta-2026-10-22.csv')
    expect(r.tipo).toContain('text/csv')
    expect(r.contenido.split('\r\n')[0]).toBe('Deportista;Concepto;Monto (COP);Vencimiento;Fecha de pago;Estado')
  })

  it('sin permiso lanza SIN_PERMISO y con fallo de red ERROR_DE_RED', async () => {
    await expect(resolver(servicio.prepararDescargaEstadoCuenta({ rol: 'administrador', correo: 'a@b.co' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.prepararDescargaEstadoCuenta(demo))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
  })
})
