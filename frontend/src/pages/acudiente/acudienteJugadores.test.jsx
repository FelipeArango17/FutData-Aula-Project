import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

// Sin red y sin depender de .env.local: el cliente HTTP y la sesión están simulados y el módulo sale de vi.stubEnv.
vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn(() => new Promise(() => {})), apiListaPaginada: vi.fn() }))
vi.mock('../../services/supabaseClient.js', () => ({ autenticacionReal: true }))

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const dibujar = (nodo, ruta = '/') => renderToStaticMarkup(<MemoryRouter initialEntries={[ruta]}>{nodo}</MemoryRouter>)
const error = (estado, mensaje = 'No se pudo completar la operación.') => Object.assign(new Error(mensaje), { estado, mensaje, codigo: null, campos: [] })
const jugador = (extra = {}) => ({
  id: ID, nombre: 'Santiago', apellido: 'Ríos', fechaNacimiento: '2018-03-14', edad: 8, categoria: 'Semillero', grupoEntrenamiento: 'mañana', posicion: null,
  foto: null, parentesco: 'Madre', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const ficha = (extra = {}) => ({
  ...jugador(), tipoDocumento: 'Tarjeta de identidad', documento: '1023456789', telefono: '3001234567', direccion: null,
  documentosFaltantes: [{ tipo: 'CERTIFICADO_ESTUDIO', nombre: 'Certificado de estudio' }, { tipo: 'AUTORIZACION_HABEAS_DATA', nombre: 'Habeas Data' }], ...extra,
})

afterEach(() => vi.unstubAllEnvs())

async function pantallas(modulos) {
  vi.resetModules()
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', modulos)
  const { default: Jugadores, VistaJugadores } = await import('./Jugadores.jsx')
  const { default: Ficha, VistaFicha } = await import('./Ficha.jsx')
  return { Jugadores, VistaJugadores, Ficha, VistaFicha }
}

const vistaLista = async (props) => {
  const { VistaJugadores } = await pantallas('acudientes')
  return dibujar(<VistaJugadores cargando={false} jugadores={null} error={null} onReintentar={() => {}} {...props} />)
}
const vistaFicha = async (props) => {
  const { VistaFicha } = await pantallas('acudientes')
  return dibujar(<VistaFicha cargando={false} jugador={null} error={null} onReintentar={() => {}} {...props} />)
}

describe('Mis jugadores (acudiente, API)', () => {
  it('con el módulo encendido y sin datos muestra "Cargando", no datos de ejemplo', async () => {
    const { Jugadores } = await pantallas('acudientes')
    const html = dibujar(<Jugadores />)
    expect(html).toContain('Cargando tus jugadores')
    for (const texto of ['Santiago Ríos', 'Valeria', 'Asistencia', 'Pagar ahora', 'Pago pendiente']) expect(html).not.toContain(texto)
  })

  it('lista vacía: mensaje claro de que el club debe vincular', async () => {
    const html = await vistaLista({ jugadores: [] })
    expect(html).toContain('Aún no tienes jugadores vinculados. Pide al club que los vincule.')
    expect(html).not.toContain('Ver ficha')
  })

  it('con jugadores: nombre, categoría con grupo, parentesco, estado y enlace a la ficha', async () => {
    const html = await vistaLista({ jugadores: [jugador(), jugador({ id: 'b', nombre: 'Valeria', categoria: null, grupoEntrenamiento: null, parentesco: null, estadoInscripcion: 'COMPLETA', foto: 'https://firmada/v.jpg' })] })
    expect(html).toContain('Santiago Ríos')
    expect(html).toContain('8 años · Semillero · mañana')
    expect(html).toContain('Parentesco: Madre')
    expect(html).toContain('Inscripción: Pendiente')
    expect(html).toContain(`href="/acudiente/jugadores/${ID}"`)
    expect(html).toContain('Valeria Ríos')
    expect(html).toContain('Inscripción: Completa')
    expect(html).toContain('src="https://firmada/v.jpg"')
    expect(html).toContain('2 jugadores vinculados')
    // sin categoría ni parentesco no se inventa nada
    expect(html).not.toContain('Sin asignar')
    expect(html.match(/Parentesco:/g)).toHaveLength(1)
  })

  it('no muestra lo que la API no entrega (asistencia, pagos, próximo entrenamiento)', async () => {
    const html = await vistaLista({ jugadores: [jugador()] })
    for (const texto of ['Asistencia', 'Pago', 'Al día', 'Pagar', 'Próximo', 'Horizontes']) expect(html).not.toContain(texto)
  })

  it('error: mensaje en español con Reintentar y sin textos técnicos', async () => {
    const html = await vistaLista({ error: error(500) })
    expect(html).toContain('role="alert"')
    expect(html).toContain('No se pudieron cargar tus jugadores.')
    expect(html).toContain('Reintentar')
    expect(html).not.toMatch(/\/api|acudientes\/me|UUID/)
  })

  it('con el módulo apagado todo sigue como hoy (datos de ejemplo)', async () => {
    const { Jugadores } = await pantallas('entrenadores')
    const html = dibujar(<Jugadores />)
    expect(html).toContain('Santiago Ríos')
    expect(html).toContain('Pagar ahora')
  })
})

describe('Ficha del jugador (acudiente, API)', () => {
  it('con el módulo encendido muestra "Cargando", no la ficha de ejemplo', async () => {
    const { Ficha } = await pantallas('acudientes')
    const html = dibujar(<Routes><Route path="/acudiente/jugadores/:id" element={<Ficha />} /></Routes>, `/acudiente/jugadores/${ID}`)
    expect(html).toContain('Cargando la ficha')
    for (const texto of ['Sura', 'Talla de uniforme', 'Santiago Ríos Zapata']) expect(html).not.toContain(texto)
  })

  it('datos de la API, inscripción y documentos que faltan con enlace a Documentos; oculta lo vacío', async () => {
    const html = await vistaFicha({ jugador: ficha() })
    expect(html).toContain('Santiago Ríos')
    expect(html).toContain('Semillero · mañana')
    expect(html).toContain('Inscripción: Pendiente')
    expect(html).toContain('14 de marzo de 2018')
    expect(html).toContain('1023456789')
    expect(html).toContain('3001234567')
    expect(html).toContain('Parentesco contigo')
    expect(html).not.toContain('Dirección') // vino null
    expect(html).not.toContain('Posición') // vino null
    expect(html).toMatch(/<a[^>]*href="\/acudiente\/documentos"[^>]*>Certificado de estudio<\/a>/)
    expect(html).toMatch(/<a[^>]*href="\/acudiente\/documentos"[^>]*>Habeas Data<\/a>/)
    for (const texto of ['EPS', 'Talla', 'Asistencia', 'Pago']) expect(html).not.toContain(texto)
  })

  it('es de solo lectura: sin formulario, campos ni botón de guardar', async () => {
    const html = await vistaFicha({ jugador: ficha() })
    for (const etiqueta of ['<form', '<input', '<select', '<textarea', 'Guardar', 'Editar']) expect(html).not.toContain(etiqueta)
  })

  it('inscripción completa: no falta ningún documento', async () => {
    const html = await vistaFicha({ jugador: ficha({ estadoInscripcion: 'COMPLETA', documentosFaltantes: [] }) })
    expect(html).toContain('Inscripción: Completa')
    expect(html).toContain('No falta ningún documento obligatorio')
  })

  it('un jugador que no es suyo o no existe: mensaje propio (sin "id") y enlace para volver, sin Reintentar', async () => {
    const html = await vistaFicha({ error: error(404, 'No existe un deportista con ese id.') })
    expect(html).toContain('role="alert"')
    expect(html).toContain('No encontramos ese jugador entre los tuyos.')
    expect(html).not.toContain('ese id')
    expect(html).toMatch(/<a[^>]*href="\/acudiente\/jugadores"[^>]*>Volver a Mis jugadores<\/a>/)
    expect(html).not.toContain('Reintentar')
  })

  it('403 usa el mensaje del servidor; un fallo pasajero ofrece Reintentar', async () => {
    expect(await vistaFicha({ error: error(403, 'Debes verificar tu identidad.') })).toContain('Debes verificar tu identidad.')
    const html = await vistaFicha({ error: error(503) })
    expect(html).toContain('Reintentar')
    expect(html).toContain('Volver a Mis jugadores')
  })

  it('con el módulo apagado la ficha de ejemplo sigue igual', async () => {
    const { Ficha } = await pantallas('entrenadores')
    const html = dibujar(<Routes><Route path="/acudiente/jugadores/:id" element={<Ficha />} /></Routes>, '/acudiente/jugadores/santiago')
    expect(html).toContain('Santiago Ríos')
    expect(html).toContain('Talla de uniforme')
  })
})
