import { describe, expect, it } from 'vitest'
import {
  LIMITE_SONDEO_MS, MENSAJE_PAGOS_NO_CONFIGURADOS, accionesDeCobro, adaptarCobro, adaptarMisPagos, buscarPago, construirUrlCheckout, estadoParaPantalla,
  etiquetaMetodo, evaluarSondeo, mensajeDeErrorPagos, validarAprobacionManual, validarNuevoCobro, validarPeriodo,
} from './pagosApi.js'

const ID_PAGO = '7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11'
const ID_DEPORTISTA = '11111111-2222-4333-8444-555555555555'

describe('construirUrlCheckout', () => {
  const respuesta = {
    llavePublica: 'pub_test_abc123', referencia: `FD-${ID_PAGO}`, montoEnCentavos: 9000000, moneda: 'COP',
    firmaIntegridad: '5e6bc69aaf0d3815b0ab2abe7782df69d9d17b4a4ce689cc355832c9ab079ed3', urlRedireccion: 'https://club.test/acudiente/pagos',
  }

  it('arma la URL del Web Checkout con los cinco parámetros obligatorios y la redirección', () => {
    expect(construirUrlCheckout(respuesta)).toBe(
      'https://checkout.wompi.co/p/?public-key=pub_test_abc123&currency=COP&amount-in-cents=9000000'
      + `&reference=FD-${ID_PAGO}&signature:integrity=${respuesta.firmaIntegridad}&redirect-url=https%3A%2F%2Fclub.test%2Facudiente%2Fpagos`,
    )
  })

  it('la firma lleva ":" sin codificar en el nombre del parámetro', () => {
    expect(construirUrlCheckout(respuesta)).toContain('&signature:integrity=')
    expect(construirUrlCheckout(respuesta)).not.toContain('signature%3Aintegrity')
  })

  it('sin redirección no agrega redirect-url (ni null, ni vacío)', () => {
    for (const urlRedireccion of [null, undefined, '']) {
      expect(construirUrlCheckout({ ...respuesta, urlRedireccion })).not.toContain('redirect-url')
    }
  })

  it('codifica los valores que pueden traer caracteres especiales', () => {
    const url = construirUrlCheckout({ ...respuesta, referencia: 'FD-a b&c=d', urlRedireccion: 'https://x.co/p?a=1&b=2' })
    expect(url).toContain('reference=FD-a%20b%26c%3Dd')
    expect(url).toContain('redirect-url=https%3A%2F%2Fx.co%2Fp%3Fa%3D1%26b%3D2')
    expect(url.split('?')).toHaveLength(2)
  })
})

describe('estado y método', () => {
  it('el estado de pantalla sale de estado + vencido', () => {
    expect(estadoParaPantalla({ estado: 'APROBADO', vencido: false })).toBe('pagado')
    expect(estadoParaPantalla({ estado: 'RECHAZADO', vencido: false })).toBe('rechazado')
    expect(estadoParaPantalla({ estado: 'PENDIENTE', vencido: false })).toBe('pendiente')
    expect(estadoParaPantalla({ estado: 'PENDIENTE', vencido: true })).toBe('vencido')
  })

  it('las etiquetas de los 8 métodos y null', () => {
    expect(etiquetaMetodo('TARJETA')).toBe('Tarjeta')
    expect(etiquetaMetodo('QR')).toBe('QR Bancolombia')
    expect(etiquetaMetodo('BOTON_BANCOLOMBIA')).toBe('Botón Bancolombia')
    expect(etiquetaMetodo('WOMPI_OTRO')).toBe('Wompi (otro medio)')
    expect(etiquetaMetodo('EFECTIVO')).toBe('Efectivo')
    expect(etiquetaMetodo(null)).toBeNull()
  })
})

const mi = (extra) => ({
  id: ID_PAGO, idDeportista: ID_DEPORTISTA, nombreDeportista: 'Pedro Gómez', tipo: 'MENSUALIDAD', concepto: 'Mensualidad noviembre 2026', monto: 90000,
  periodo: '2026-11-01', fechaVencimiento: '2026-11-05', estado: 'PENDIENTE', vencido: false, fechaPago: null, metodo: null, tieneComprobante: false, ...extra,
})

describe('adaptarMisPagos', () => {
  it('agrupa por deportista, deriva el estado, conserva los ids UUID y calcula los totales', () => {
    const r = adaptarMisPagos([
      mi({ id: 'a', concepto: 'Octubre', fechaVencimiento: '2026-10-05', vencido: true }),
      mi({ id: 'b' }),
      mi({ id: 'c', estado: 'APROBADO', fechaPago: '2026-09-03', metodo: 'NEQUI', tieneComprobante: true, fechaVencimiento: '2026-09-05' }),
      mi({ id: 'd', estado: 'RECHAZADO', fechaVencimiento: '2026-08-05' }),
      mi({ id: 'e', idDeportista: 'otro', nombreDeportista: 'Luis Gómez', monto: 50000 }),
    ])
    expect(r.deportistas).toHaveLength(2)
    const pedro = r.deportistas[0]
    expect(pedro.deportista).toEqual({ id: ID_DEPORTISTA, nombre: 'Pedro Gómez', apellido: '' })
    expect(pedro.pagos.map((p) => p.id)).toEqual(['b', 'a', 'c', 'd']) // más reciente primero
    expect(pedro.pagos.map((p) => p.estado)).toEqual(['pendiente', 'vencido', 'pagado', 'rechazado'])
    expect(pedro.pagos[2]).toMatchObject({ metodo: 'Nequi', fechaPago: '2026-09-03', tieneComprobante: true })
    expect(pedro.totalPendiente).toBe(180000) // pendiente + vencido; el rechazado no suma
    expect(pedro.totalVencido).toBe(90000)
    expect(pedro.pendienteDesde).toBe('2026-10-05')
    expect(r.totalPendiente).toBe(230000)
    expect(r.totalVencido).toBe(90000)
  })

  it('sin cobros: lista vacía y totales en 0', () => {
    expect(adaptarMisPagos([])).toEqual({ deportistas: [], totalPendiente: 0, totalVencido: 0 })
    expect(adaptarMisPagos(null).deportistas).toEqual([])
  })

  it('buscarPago encuentra un cobro por su id de texto', () => {
    const r = adaptarMisPagos([mi({ id: 'x-1' }), mi({ id: 'x-2' })])
    expect(buscarPago(r, 'x-2').id).toBe('x-2')
    expect(buscarPago(r, 'no-existe')).toBeNull()
    expect(buscarPago(null, 'x')).toBeNull()
  })
})

describe('adaptarCobro y accionesDeCobro', () => {
  const dto = (extra) => ({ ...mi(), referenciaWompi: null, idTransaccionWompi: null, numeroComprobante: null, notaInterna: null, ...extra })

  it('adapta el cobro (ids de texto, iniciadoEnWompi por la referencia)', () => {
    const c = adaptarCobro(dto({ referenciaWompi: `FD-${ID_PAGO}` }))
    expect(c).toMatchObject({ id: ID_PAGO, estado: 'pendiente', estadoApi: 'PENDIENTE', iniciadoEnWompi: true, tieneComprobante: false, nombreDeportista: 'Pedro Gómez' })
    expect(adaptarCobro(dto({ estado: 'APROBADO', numeroComprobante: 'FD-2026-000001', metodo: 'TARJETA' }))).toMatchObject({ estado: 'pagado', tieneComprobante: true, metodo: 'Tarjeta' })
  })

  it('PENDIENTE sin Wompi: registrar y rechazar, sin conciliar', () => {
    expect(accionesDeCobro(adaptarCobro(dto()))).toEqual({ manual: true, rechazar: true, conciliar: false, comprobante: false })
  })

  it('PENDIENTE iniciado en Wompi: también conciliar', () => {
    expect(accionesDeCobro(adaptarCobro(dto({ referenciaWompi: 'FD-x' })))).toEqual({ manual: true, rechazar: true, conciliar: true, comprobante: false })
  })

  it('RECHAZADO: manual solo si nunca se inició en Wompi; conciliar si se inició', () => {
    expect(accionesDeCobro(adaptarCobro(dto({ estado: 'RECHAZADO' })))).toEqual({ manual: true, rechazar: false, conciliar: false, comprobante: false })
    expect(accionesDeCobro(adaptarCobro(dto({ estado: 'RECHAZADO', referenciaWompi: 'FD-x' })))).toEqual({ manual: false, rechazar: false, conciliar: true, comprobante: false })
  })

  it('APROBADO: solo el comprobante', () => {
    expect(accionesDeCobro(adaptarCobro(dto({ estado: 'APROBADO', numeroComprobante: 'FD-2026-000001', referenciaWompi: 'FD-x' }))))
      .toEqual({ manual: false, rechazar: false, conciliar: false, comprobante: true })
  })
})

describe('mensajeDeErrorPagos', () => {
  it('PAGOS_NO_CONFIGURADOS (503)', () => {
    expect(mensajeDeErrorPagos({ estado: 503, codigo: 'PAGOS_NO_CONFIGURADOS', mensaje: 'x' })).toBe('Los pagos en línea no están disponibles por ahora.')
    expect(MENSAJE_PAGOS_NO_CONFIGURADOS).toBe('Los pagos en línea no están disponibles por ahora.')
  })
  it('401, 403, 404, 503 genérico y red', () => {
    expect(mensajeDeErrorPagos({ estado: 401 })).toMatch(/sesión venció/)
    expect(mensajeDeErrorPagos({ estado: 403 })).toBe('No tienes permiso para hacer esta acción.')
    expect(mensajeDeErrorPagos({ estado: 404 })).toMatch(/No encontramos ese pago/)
    expect(mensajeDeErrorPagos({ estado: 503, codigo: null })).toMatch(/no está disponible/)
    expect(mensajeDeErrorPagos({ estado: 0, codigo: 'RED' })).toMatch(/No pudimos conectar/)
  })
  it('409 muestra el mensaje del backend sin el prefijo del campo y con mayúscula', () => {
    expect(mensajeDeErrorPagos({ estado: 409, campos: ['estado'], mensaje: 'estado: el cobro ya se inició en Wompi; concílialo o recházalo antes de registrarlo como pago manual' }))
      .toBe('El cobro ya se inició en Wompi; concílialo o recházalo antes de registrarlo como pago manual')
  })
  it('400 con mensaje, y un error de los datos de ejemplo usa su propio texto', () => {
    expect(mensajeDeErrorPagos({ estado: 400, mensaje: 'monto: debe ser mayor que 0' })).toBe('Debe ser mayor que 0')
    expect(mensajeDeErrorPagos(Object.assign(new Error('Sin permiso.'), { codigo: 'SIN_PERMISO' }))).toBe('Sin permiso.')
    expect(mensajeDeErrorPagos({ estado: 500 }, 'Falló.')).toBe('Falló.')
  })
})

describe('evaluarSondeo', () => {
  const pago = (estado) => ({ estado })
  it('termina cuando el cobro se aprueba o se rechaza', () => {
    expect(evaluarSondeo(pago('pagado'), 3000)).toBe('aprobado')
    expect(evaluarSondeo(pago('rechazado'), 3000)).toBe('rechazado')
  })
  it('sigue esperando mientras esté pendiente o vencido y no pasen 30 s; luego se agota', () => {
    expect(evaluarSondeo(pago('pendiente'), 0)).toBe('esperando')
    expect(evaluarSondeo(pago('vencido'), LIMITE_SONDEO_MS - 1)).toBe('esperando')
    expect(evaluarSondeo(pago('pendiente'), LIMITE_SONDEO_MS)).toBe('agotado')
  })
  it('si el cobro ya no aparece, termina', () => {
    expect(evaluarSondeo(null, 0)).toBe('terminado')
  })
})

describe('validaciones de los formularios', () => {
  it('pago manual: fecha obligatoria y no futura; método efectivo o transferencia', () => {
    expect(validarAprobacionManual({ fechaPago: '2026-11-02', metodo: 'EFECTIVO' }, '2026-11-02')).toEqual({})
    expect(validarAprobacionManual({ fechaPago: '', metodo: '' }, '2026-11-02')).toEqual({ fechaPago: 'Indica la fecha del pago.', metodo: 'Elige efectivo o transferencia.' })
    expect(validarAprobacionManual({ fechaPago: '2026-11-03', metodo: 'PSE' }, '2026-11-02')).toEqual({ fechaPago: 'La fecha del pago no puede ser posterior a hoy.', metodo: 'Elige efectivo o transferencia.' })
  })

  it('crear cobro: el deportista debe ser un UUID, el monto entero y la mensualidad lleva mes', () => {
    const bueno = { idDeportista: ID_DEPORTISTA, tipo: 'MENSUALIDAD', concepto: '', monto: '90000', fechaVencimiento: '2026-11-05', periodo: '2026-11' }
    expect(validarNuevoCobro(bueno)).toEqual({})
    expect(validarNuevoCobro({ ...bueno, idDeportista: '3' }).idDeportista).toBe('Elige un deportista.')
    expect(validarNuevoCobro({ ...bueno, monto: '90.5' }).monto).toBeDefined()
    expect(validarNuevoCobro({ ...bueno, monto: '0' }).monto).toBeDefined()
    expect(validarNuevoCobro({ ...bueno, periodo: '' }).periodo).toBeDefined()
    expect(validarNuevoCobro({ ...bueno, tipo: 'UNIFORME' }).concepto).toBeDefined() // fuera de la mensualidad el concepto es obligatorio
    expect(validarNuevoCobro({ ...bueno, tipo: 'UNIFORME', concepto: 'Uniforme', periodo: '' })).toEqual({})
    expect(validarNuevoCobro({ ...bueno, tipo: 'REGALO' }).tipo).toBeDefined()
  })

  it('periodo AAAA-MM', () => {
    expect(validarPeriodo('2026-11')).toBeNull()
    expect(validarPeriodo('2026-13')).not.toBeNull()
    expect(validarPeriodo('')).not.toBeNull()
  })
})
