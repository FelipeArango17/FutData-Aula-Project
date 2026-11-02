import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import AccionPago from './AccionPago.jsx'
import ContenidoEstadoCuenta from './ContenidoEstadoCuenta.jsx'
import ListaPagos from './ListaPagos.jsx'
import TablaCobros from './TablaCobros.jsx'
import { accionesDeCobro, adaptarCobro, adaptarMisPagos } from '../../utils/pagosApi.js'

// Sin entorno de navegador: se comprueba lo que se dibuja en cada estado con renderToStaticMarkup.
const ID = '7f0c2a52-6a5d-4f0e-9d5e-0b8d2d9c1a11'
const DEPORTISTA = '11111111-2222-4333-8444-555555555555'
const dto = (extra = {}) => ({
  id: ID, idDeportista: DEPORTISTA, nombreDeportista: 'Pedro Gómez', tipo: 'MENSUALIDAD', concepto: 'Mensualidad noviembre 2026', monto: 90000,
  periodo: '2026-11-01', fechaVencimiento: '2026-11-05', estado: 'PENDIENTE', vencido: false, fechaPago: null, metodo: null, tieneComprobante: false,
  referenciaWompi: null, numeroComprobante: null, notaInterna: null, ...extra,
})
const html = (nodo) => renderToStaticMarkup(<MemoryRouter>{nodo}</MemoryRouter>)
const acciones = { onPagar: vi.fn(), onReintentar: vi.fn(), onDescargar: vi.fn() }

describe('AccionPago: un botón por estado', () => {
  const pago = (estado, extra) => adaptarMisPagos([dto({ estado, ...extra })]).deportistas[0].pagos[0]

  it('PENDIENTE -> "Pagar en línea"', () => {
    const h = html(<AccionPago pago={pago('PENDIENTE')} {...acciones} />)
    expect(h).toContain('Pagar en línea')
    expect(h).not.toContain('Reintentar')
    expect(h).not.toContain('comprobante')
  })

  it('vencido también paga en línea', () => {
    expect(html(<AccionPago pago={pago('PENDIENTE', { vencido: true })} {...acciones} />)).toContain('Pagar en línea')
  })

  it('RECHAZADO -> "Reintentar"', () => {
    const h = html(<AccionPago pago={pago('RECHAZADO')} {...acciones} />)
    expect(h).toContain('Reintentar')
    expect(h).not.toContain('Pagar en línea')
  })

  it('APROBADO con comprobante -> "Descargar comprobante"; sin comprobante no hay botón', () => {
    expect(html(<AccionPago pago={pago('APROBADO', { tieneComprobante: true })} {...acciones} />)).toContain('Descargar comprobante')
    expect(html(<AccionPago pago={pago('APROBADO', { tieneComprobante: false })} {...acciones} />)).not.toContain('<button')
  })

  it('con una acción en curso el botón queda deshabilitado y lo dice', () => {
    const h = html(<AccionPago pago={pago('PENDIENTE')} ocupado={ID} {...acciones} />)
    expect(h).toContain('disabled')
    expect(h).toContain('aria-busy="true"')
    expect(h).toContain('Abriendo el pago…')
  })

  it('el nombre accesible dice a qué cobro se refiere', () => {
    expect(html(<AccionPago pago={pago('PENDIENTE')} {...acciones} />)).toContain('aria-label="Pagar en línea: Mensualidad noviembre 2026"')
  })
})

describe('ContenidoEstadoCuenta: cada estado de la pantalla del acudiente', () => {
  const base = { filtro: 'Todos', onFiltro: () => {}, conTabla: false }
  const estado = (lista) => adaptarMisPagos(lista)
  const conAcciones = (pago) => <AccionPago pago={pago} {...acciones} />

  it('cargando', () => {
    const h = html(<ContenidoEstadoCuenta {...base} cargando />)
    expect(h).toContain('Cargando tu estado de cuenta')
    expect(h).toContain('role="status"')
  })

  it('error: mensaje, rol alert y Reintentar', () => {
    const h = html(<ContenidoEstadoCuenta {...base} mensajeError="No se pudo cargar tu estado de cuenta." onReintentar={() => {}} />)
    expect(h).toContain('role="alert"')
    expect(h).toContain('No se pudo cargar tu estado de cuenta.')
    expect(h).toContain('Reintentar')
  })

  it('vacío: sin deportistas', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([])} />)
    expect(h).toContain('Todavía no tienes deportistas asociados')
  })

  it('PENDIENTE: estado con texto, monto y "Pagar en línea"', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([dto()])} accionesDe={conAcciones} />)
    expect(h).toContain('Pedro Gómez')
    expect(h).toContain('Pendiente')
    expect(h).toContain('Pagar en línea')
    expect(h).not.toContain('estará disponible próximamente')
  })

  it('RECHAZADO: el texto "Rechazado" y el botón "Reintentar"; no suma a lo pendiente', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([dto({ estado: 'RECHAZADO' })])} accionesDe={conAcciones} />)
    expect(h).toContain('Rechazado')
    expect(h).toContain('Reintentar')
    expect(h).toContain('Estás al día con todos tus pagos')
  })

  it('APROBADO: "Pagado", el método y "Descargar comprobante"', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([dto({ estado: 'APROBADO', fechaPago: '2026-11-03', metodo: 'NEQUI', tieneComprobante: true })])} accionesDe={conAcciones} />)
    expect(h).toContain('Pagado')
    expect(h).toContain('Descargar comprobante')
  })

  it('en tabla (escritorio) también hay columna de acciones', () => {
    const h = html(<ContenidoEstadoCuenta {...base} conTabla estado={estado([dto()])} accionesDe={conAcciones} />)
    expect(h).toContain('<th scope="col" class="p-3 text-body-medium">Acciones</th>')
  })

  it('"Estamos confirmando tu pago" mientras se espera el webhook', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([dto()])} accionesDe={conAcciones} confirmando="Estamos confirmando tu pago…" />)
    expect(h).toContain('Estamos confirmando tu pago')
    expect(h).toContain('role="status"')
  })

  it('sin la API (datos de ejemplo) avisa que el pago en línea llegará después y no hay acciones', () => {
    const h = html(<ContenidoEstadoCuenta {...base} estado={estado([dto()])} />)
    expect(h).toContain('El pago en línea estará disponible próximamente.')
    expect(h).not.toContain('Pagar en línea')
  })
})

describe('ListaPagos sin acciones (el formato de siempre)', () => {
  it('no agrega la columna de acciones', () => {
    const pagos = adaptarMisPagos([dto()]).deportistas[0].pagos
    expect(html(<ListaPagos pagos={pagos} nombre="Pedro" conTabla fechaLarga={(f) => f} />)).not.toContain('Acciones')
  })
})

describe('TablaCobros: cada estado de la lista de gestión', () => {
  const cobro = (extra) => adaptarCobro(dto(extra))
  const acc = (c) => <span>{JSON.stringify(accionesDeCobro(c))}</span>

  it('cargando, error y vacío', () => {
    expect(html(<TablaCobros cargando cobros={[]} />)).toContain('Cargando los cobros')
    const error = html(<TablaCobros mensajeError="No pudimos conectar con el servidor. Intenta de nuevo." onReintentar={() => {}} cobros={[]} />)
    expect(error).toContain('role="alert"')
    expect(error).toContain('Reintentar')
    expect(html(<TablaCobros cobros={[]} />)).toContain('No hay cobros con estos filtros.')
  })

  it('PENDIENTE sin Wompi', () => {
    const h = html(<TablaCobros cobros={[cobro()]} accionesDe={acc} />)
    expect(h).toContain('Pedro Gómez')
    expect(h).toContain('Mensualidad · Mensualidad noviembre 2026')
    expect(h).toContain('Pendiente')
    expect(h).not.toContain('Iniciado en Wompi')
  })

  it('PENDIENTE iniciado en Wompi: lo avisa y sugiere conciliar', () => {
    const h = html(<TablaCobros cobros={[cobro({ referenciaWompi: 'FD-x' })]} accionesDe={acc} />)
    expect(h).toContain('Iniciado en Wompi')
    expect(h).toContain('Conciliar')
  })

  it('RECHAZADO con su nota, y APROBADO con fecha y método', () => {
    const h = html(<TablaCobros cobros={[cobro({ estado: 'RECHAZADO', notaInterna: 'Duplicado' }), cobro({ id: 'b', estado: 'APROBADO', fechaPago: '2026-11-03', metodo: 'EFECTIVO', numeroComprobante: 'FD-2026-000001' })]} />)
    expect(h).toContain('Rechazado')
    expect(h).toContain('Nota: Duplicado')
    expect(h).toContain('Pagado')
    expect(h).toContain('Efectivo')
  })
})
