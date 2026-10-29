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

describe('mesesParaPago', () => {
  it('los últimos 6 meses y el actual según HOY_EJEMPLO, del más reciente al más antiguo', () => {
    const meses = servicio.mesesParaPago()
    expect(meses.map((m) => m.valor)).toEqual(['2026-10', '2026-09', '2026-08', '2026-07', '2026-06', '2026-05'])
    expect(meses[0].texto).toBe('octubre 2026')
  })

  it('cruza el cambio de año', () => {
    expect(servicio.mesesParaPago('2026-02-10', 4)).toEqual([
      { valor: '2026-02', texto: 'febrero 2026' }, { valor: '2026-01', texto: 'enero 2026' },
      { valor: '2025-12', texto: 'diciembre 2025' }, { valor: '2025-11', texto: 'noviembre 2025' },
    ])
  })
})

describe('registrarPagoManual', () => {
  const tesorero = { rol: 'tesorero', correo: 'tesorero@pradofc.co' }
  const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
  const pago = (extra = {}) => ({ idDeportista: 1, mes: '2026-10', monto: '90000', fechaPago: '2026-10-22', metodo: 'Efectivo', referencia: '', nota: '', ...extra })
  const estadoAcudiente = async () => resolver(servicio.obtenerEstadoCuenta(demo))
  const santiago = async () => (await estadoAcudiente()).deportistas[0]

  it('pago correcto sobre un cobro existente: queda pagado con fecha, método y quién lo registró', async () => {
    const r = await resolver(servicio.registrarPagoManual(pago({ referencia: ' ', nota: ' Recibido en sede ' }), tesorero))
    expect(r.cobroCreado).toBe(false)
    expect(r.pago).toMatchObject({
      id: 5, idDeportista: 1, concepto: 'Mensualidad octubre', monto: 90000, fechaPago: '2026-10-22', metodo: 'Efectivo',
      estado: 'pagado', referencia: '', nota: 'Recibido en sede', registradoPor: 'tesorero@pradofc.co',
    })
    expect(Number.isNaN(Date.parse(r.pago.registradoEn))).toBe(false)
  })

  it('el monto puede venir como número y el administrador también registra', async () => {
    const r = await resolver(servicio.registrarPagoManual(pago({ monto: 90000, metodo: 'Transferencia', referencia: 'TRF-0001' }), admin))
    expect(r.pago).toMatchObject({ metodo: 'Transferencia', referencia: 'TRF-0001', registradoPor: 'administrador@pradofc.co' })
  })

  it('un cobro que no existía se CREA ya pagado (septiembre de Emilio; vence el 30)', async () => {
    const r = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 3, mes: '2026-09' }), tesorero))
    expect(r.cobroCreado).toBe(true)
    expect(r.pago).toMatchObject({ id: 11, idDeportista: 3, concepto: 'Mensualidad septiembre', monto: 90000, fechaVencimiento: '2026-09-30', estado: 'pagado' })
  })

  it('el vencimiento de un cobro nuevo es el último día del mes con tope en el 30 (febrero 28, enero 30)', async () => {
    const feb = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 3, mes: '2026-02', fechaPago: '2026-03-01' }), admin))
    const ene = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 3, mes: '2026-01', fechaPago: '2026-02-01' }), admin))
    expect(feb.pago.fechaVencimiento).toBe('2026-02-28')
    expect(ene.pago.fechaVencimiento).toBe('2026-01-30')
    expect(feb.pago.id).not.toBe(ene.pago.id)
  })

  it('cobro existente con monto distinto: MONTO_DISTINTO con el monto esperado y no cambia nada', async () => {
    const e = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 6, monto: '90000' }), tesorero)).catch((x) => x)
    expect(e.codigo).toBe('MONTO_DISTINTO')
    expect(e.esperado).toBe(95000)
    const ok = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 6, monto: '95000' }), tesorero))
    expect(ok.pago).toMatchObject({ id: 9, monto: 95000, estado: 'pagado' })
  })

  it('mensualidad ya pagada: YA_PAGADO, sin duplicar ni tocar nada (Valeria, octubre)', async () => {
    const antes = (await estadoAcudiente()).deportistas[1].pagos
    const e = await resolver(servicio.registrarPagoManual(pago({ idDeportista: 2, monto: '90000', metodo: 'Transferencia', referencia: 'OTRO-1' }), tesorero)).catch((x) => x)
    expect(e.codigo).toBe('YA_PAGADO')
    expect((await estadoAcudiente()).deportistas[1].pagos).toEqual(antes)
  })

  it('registrar dos veces lo mismo (doble envío): la segunda da YA_PAGADO y conserva el primer pago', async () => {
    await resolver(servicio.registrarPagoManual(pago({ metodo: 'Efectivo' }), tesorero))
    await expect(resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: 'X', fechaPago: '2026-10-20' }), admin))).rejects.toMatchObject({ codigo: 'YA_PAGADO' })
    const cobro = await resolver(servicio.consultarCobroMensualidad(1, '2026-10', admin))
    expect(cobro).toMatchObject({ estado: 'pagado', fechaPago: '2026-10-22' })
    expect((await santiago()).pagos.filter((p) => p.concepto === 'Mensualidad octubre')).toHaveLength(1)
  })

  it.each([
    ['idDeportista', { idDeportista: '' }, 'Selecciona un deportista.'],
    ['mes', { mes: '' }, 'Selecciona el mes que se paga.'],
    ['monto', { monto: '' }, 'Escribe el monto.'],
    ['fechaPago', { fechaPago: '' }, 'Indica la fecha en que se pagó.'],
    ['metodo', { metodo: '' }, 'Selecciona el método de pago.'],
  ])('falta %s: VALIDACION con su mensaje y no registra nada', async (campo, cambio, mensaje) => {
    await expect(resolver(servicio.registrarPagoManual(pago(cambio), tesorero))).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { [campo]: mensaje } })
    expect((await santiago()).totalPendiente).toBe(180000)
  })

  it('todo vacío: VALIDACION con los 5 campos obligatorios', async () => {
    const vacio = { idDeportista: '', mes: '', monto: '', fechaPago: '', metodo: '', referencia: '', nota: '' }
    const e = await resolver(servicio.registrarPagoManual(vacio, tesorero)).catch((x) => x)
    expect(Object.keys(e.errores).sort()).toEqual(['fechaPago', 'idDeportista', 'mes', 'metodo', 'monto'])
  })

  it('transferencia sin referencia: VALIDACION; efectivo sin referencia y transferencia con referencia, bien', async () => {
    const e = await resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: '  ' }), tesorero)).catch((x) => x)
    expect(e.errores).toEqual({ referencia: 'Escribe la referencia de la transferencia.' })
    await expect(resolver(servicio.registrarPagoManual(pago({ metodo: 'Efectivo', referencia: '' }), tesorero))).resolves.toBeTruthy()
    await expect(resolver(servicio.registrarPagoManual(pago({ idDeportista: 6, monto: 95000, metodo: 'Transferencia', referencia: 'REF-9' }), tesorero))).resolves.toBeTruthy()
  })

  it('referencia de más de 60 caracteres y nota de más de 200: VALIDACION (los límites exactos pasan)', async () => {
    const e = await resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: 'r'.repeat(61), nota: 'n'.repeat(201) }), tesorero)).catch((x) => x)
    expect(e.errores).toEqual({ referencia: 'La referencia no puede superar los 60 caracteres.', nota: 'La nota no puede superar los 200 caracteres.' })
    await expect(resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: 'r'.repeat(60), nota: 'n'.repeat(200) }), tesorero))).resolves.toBeTruthy()
  })

  it('monto con letras, puntos, símbolo, decimales, negativo o cero: VALIDACION', async () => {
    for (const malo of ['90.000', '$ 90000', '90mil', '90000.5', '-5', '0']) {
      const e = await resolver(servicio.registrarPagoManual(pago({ monto: malo }), tesorero)).catch((x) => x)
      expect(e.codigo, malo).toBe('VALIDACION')
      expect(Object.keys(e.errores), malo).toEqual(['monto'])
    }
  })

  it('fecha de pago futura respecto a HOY_EJEMPLO: 2026-10-23 falla y 2026-10-22 pasa; mes futuro falla', async () => {
    const e = await resolver(servicio.registrarPagoManual(pago({ fechaPago: '2026-10-23' }), tesorero)).catch((x) => x)
    expect(e.errores).toEqual({ fechaPago: 'La fecha de pago no puede ser futura.' })
    const mes = await resolver(servicio.registrarPagoManual(pago({ mes: '2026-11' }), tesorero)).catch((x) => x)
    expect(mes.errores).toEqual({ mes: 'No se puede registrar el pago de un mes futuro.' })
    await expect(resolver(servicio.registrarPagoManual(pago({ fechaPago: '2026-10-22' }), tesorero))).resolves.toBeTruthy()
  })

  it('deportista inexistente: DEPORTISTA_NO_EXISTE; inactivo: DEPORTISTA_INACTIVO', async () => {
    await expect(resolver(servicio.registrarPagoManual(pago({ idDeportista: 999 }), tesorero))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
    await expect(resolver(servicio.registrarPagoManual(pago({ idDeportista: 5 }), tesorero))).rejects.toMatchObject({ codigo: 'DEPORTISTA_INACTIVO' })
  })

  it('sin permiso: SIN_PERMISO para acudiente, entrenador y sin actor, antes de cualquier otra comprobación', async () => {
    for (const actor of [{ rol: 'acudiente', correo: 'acudiente@pradofc.co' }, { rol: 'entrenador', correo: 'e@x.co' }, undefined]) {
      await expect(resolver(servicio.registrarPagoManual(pago(), actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.registrarPagoManual({}, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.consultarCobroMensualidad(1, '2026-10', actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
  })

  it('el estado de cuenta del acudiente cambia: el cobro pasa a pagado y los totales bajan', async () => {
    const antes = await santiago()
    expect(antes.totalPendiente).toBe(180000)
    expect(antes.pagos.find((p) => p.concepto === 'Mensualidad octubre').estado).toBe('pendiente')
    await resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: 'TRF-77', nota: 'Llamar al padre' }), tesorero))
    const despues = await santiago()
    const octubre = despues.pagos.find((p) => p.concepto === 'Mensualidad octubre')
    expect(octubre).toMatchObject({ estado: 'pagado', fechaPago: '2026-10-22', metodo: 'Transferencia' })
    expect(despues.totalPendiente).toBe(90000) // queda el uniforme vencido
    expect(despues.totalVencido).toBe(90000)
    expect(despues.pendienteDesde).toBe('2026-10-10')
    expect((await estadoAcudiente()).totalPendiente).toBe(90000)
  })

  it('al acudiente no se le muestra quién registró el pago, la referencia ni la nota interna', async () => {
    await resolver(servicio.registrarPagoManual(pago({ metodo: 'Transferencia', referencia: 'TRF-77', nota: 'Llamar al padre' }), tesorero))
    const estado = await estadoAcudiente()
    const texto = JSON.stringify(estado)
    expect(texto).not.toMatch(/tesorero@|Llamar al padre|TRF-77|registradoPor|registradoEn|referencia|nota/)
    for (const p of estado.deportistas.flatMap((d) => d.pagos)) {
      expect(Object.keys(p).sort()).toEqual(['concepto', 'estado', 'fechaPago', 'fechaVencimiento', 'id', 'idDeportista', 'metodo', 'monto'])
    }
    expect(servicio.generarCsvEstadoCuenta(estado)).not.toMatch(/tesorero@|Llamar al padre|TRF-77/)
  })

  it('un pago de otro deportista no aparece en el estado de cuenta del acudiente demo', async () => {
    await resolver(servicio.registrarPagoManual(pago({ idDeportista: 6, monto: 95000 }), tesorero))
    const estado = await estadoAcudiente()
    expect(JSON.stringify(estado)).not.toContain('95000')
    expect(estado.deportistas.map((d) => d.deportista.id)).toEqual([1, 2])
  })

  it('un cobro creado también aparece en el estado de cuenta (septiembre de Santiago ya pagado no se duplica; agosto sí existe)', async () => {
    await expect(resolver(servicio.registrarPagoManual(pago({ mes: '2026-09' }), tesorero))).rejects.toMatchObject({ codigo: 'YA_PAGADO' })
    const r = await resolver(servicio.registrarPagoManual(pago({ mes: '2026-07', fechaPago: '2026-07-30' }), tesorero))
    expect(r.cobroCreado).toBe(true)
    const conceptos = (await santiago()).pagos.map((p) => p.concepto)
    expect(conceptos).toContain('Mensualidad julio')
    expect(conceptos).toHaveLength(6)
  })

  it('un fallo de red lanza ERROR_DE_RED y no registra nada', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.registrarPagoManual(pago(), tesorero))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    expect((await santiago()).totalPendiente).toBe(180000)
  })
})

describe('consultarCobroMensualidad', () => {
  const tesorero = { rol: 'tesorero', correo: 'tesorero@pradofc.co' }

  it('cobro pendiente, cobro pagado y cobro que no existe', async () => {
    expect(await resolver(servicio.consultarCobroMensualidad(1, '2026-10', tesorero))).toMatchObject({ existe: true, concepto: 'Mensualidad octubre', monto: 90000, estado: 'pendiente' })
    expect(await resolver(servicio.consultarCobroMensualidad('2', '2026-10', tesorero))).toMatchObject({ existe: true, estado: 'pagado', fechaPago: '2026-10-05' })
    expect(await resolver(servicio.consultarCobroMensualidad(3, '2026-09', tesorero))).toEqual({ existe: false })
  })

  it('un cobro vencido se ve vencido con HOY_EJEMPLO (Mateo, octubre, 95000)', async () => {
    expect(await resolver(servicio.consultarCobroMensualidad(6, '2026-10', tesorero))).toMatchObject({ monto: 95000, estado: 'vencido' })
  })
})
