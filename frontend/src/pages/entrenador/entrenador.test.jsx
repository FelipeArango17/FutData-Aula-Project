import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Sin red y sin depender de .env.local: el cliente HTTP y la sesión están simulados y los módulos salen de vi.stubEnv.
vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('../../services/supabaseClient.js', () => ({ autenticacionReal: true }))

import MisJugadores, { VistaMisJugadores } from './MisJugadores.jsx'
import Inicio, { VistaInicio } from './Inicio.jsx'
import { bottomNavs, menus } from '../../config/menus.js'
import { modulosConApi } from '../../services/usarApi.js'
import { bottomNavVisible, menusVisibles } from '../../utils/menusVisibles.js'

const dibujar = (nodo) => renderToStaticMarkup(<MemoryRouter>{nodo}</MemoryRouter>)
const jugador = (id, nombre, apellido, extra = {}) => ({
  id, nombre, apellido, edad: 11, posicion: 'Portero', foto: null, categoria: 'Sub-11', grupoEntrenamiento: null, ...extra,
})
const perfil = { idEntrenador: 'e1', categorias: [{ idCategoria: 'c1', nombre: 'Sub-11' }, { idCategoria: 'c2', nombre: 'Semillero' }] }
const datosJugadores = (porCategoria) => {
  const categorias = perfil.categorias.map((c) => ({ ...c, jugadores: porCategoria[c.nombre] ?? [] }))
  return { perfil, categorias, jugadores: categorias.flatMap((c) => c.jugadores) }
}
const vista = (props) => dibujar(<VistaMisJugadores conectado cargando={false} datos={null} error={null} onReintentar={() => {}} {...props} />)

afterEach(() => vi.unstubAllEnvs())

describe('Mis jugadores', () => {
  it('muestra foto-o-iniciales, nombre, edad, posición, categoría y grupo', () => {
    const html = vista({
      datos: datosJugadores({
        'Sub-11': [jugador('1', 'Ana', 'Rojas', { foto: 'https://firmada/a.jpg' })],
        Semillero: [jugador('2', 'Beto', 'Ríos', { edad: 6, posicion: null, categoria: 'Semillero', grupoEntrenamiento: 'mañana' })],
      }),
    })
    expect(html).toContain('Ana Rojas')
    expect(html).toContain('src="https://firmada/a.jpg"')
    expect(html).toContain('Beto Ríos')
    expect(html).toContain('>BR<') // sin foto: iniciales
    expect(html).toContain('11 años · Portero')
    expect(html).toContain('6 años · Sin posición definida')
    expect(html).toContain('Semillero · mañana')
    expect(html).toContain('2 jugadores en tus categorías')
  })

  it('no muestra datos que el API no devuelve al entrenador', () => {
    const html = vista({ datos: datosJugadores({ 'Sub-11': [jugador('1', 'Ana', 'Rojas')] }) })
    for (const prohibido of ['Documento', 'Acudiente', 'Teléfono', 'Dirección', 'Inscripción']) expect(html).not.toContain(prohibido)
  })

  it('tiene filtro por categoría (botones con estado) y orden con etiqueta', () => {
    const html = vista({ datos: datosJugadores({ 'Sub-11': [jugador('1', 'Ana', 'Rojas')] }) })
    expect(html).toContain('aria-label="Filtrar por categoría"')
    expect(html).toMatch(/aria-pressed="true"[^>]*>Todas</)
    expect(html).toMatch(/<label[^>]*for="orden-jugadores"[^>]*>Ordenar por<\/label>/)
    expect(html).toContain('<select id="orden-jugadores"')
    expect(html).toContain('role="status"')
  })

  it('con una sola categoría no hay filtro por categoría', () => {
    const uno = { perfil: { ...perfil, categorias: [perfil.categorias[0]] }, categorias: [{ ...perfil.categorias[0], jugadores: [jugador('1', 'Ana', 'Rojas')] }], jugadores: [jugador('1', 'Ana', 'Rojas')] }
    expect(vista({ datos: uno })).not.toContain('Filtrar por categoría')
  })

  it('cargando', () => {
    const html = vista({ cargando: true })
    expect(html).toContain('Cargando tus jugadores…')
    expect(html).toContain('role="status"')
  })

  it('error con reintento (role alert)', () => {
    const html = vista({ error: Object.assign(new Error('x'), { estado: 500 }) })
    expect(html).toContain('role="alert"')
    expect(html).toContain('No se pudieron cargar tus jugadores.')
    expect(html).toContain('Reintentar')
  })

  it('entrenador sin categorías: mensaje claro', () => {
    const html = vista({ datos: { perfil: { idEntrenador: 'e1', categorias: [] }, categorias: [], jugadores: [] } })
    expect(html).toContain('Todavía no tienes categorías asignadas')
    expect(html).not.toContain('Ordenar por')
  })

  it('cuenta sin entrenador vinculado: mensaje claro', () => {
    expect(vista({ datos: { perfil: null, categorias: [], jugadores: [] } })).toContain('no está vinculada a un registro de entrenador')
  })

  it('categorías sin jugadores', () => {
    expect(vista({ datos: datosJugadores({}) })).toContain('Tus categorías todavía no tienen jugadores activos.')
  })

  it('módulo apagado: avisa que no está conectado y no inventa jugadores', () => {
    const html = vista({ conectado: false })
    expect(html).toContain('todavía no está conectado a la API')
    expect(html).not.toContain('Ordenar por')
  })

  it('el contenedor, con el módulo apagado (pruebas), no llama a la API', () => {
    expect(dibujar(<MisJugadores />)).toContain('todavía no está conectado a la API')
  })
})

describe('Inicio del entrenador', () => {
  const datos = (extra = {}) => ({
    perfil,
    jugadores: { categorias: [{ ...perfil.categorias[0], jugadores: [jugador('1', 'A', 'A'), jugador('2', 'B', 'B')] }, { ...perfil.categorias[1], jugadores: [] }], jugadores: [jugador('1', 'A', 'A'), jugador('2', 'B', 'B')] },
    horario: {
      hoy: [{ clave: 'f-a-2026-10-20', fecha: '2026-10-20', hora: '17:00', horaFin: '18:30', categoria: 'Sub-11', grupo: null, sede: 'Horizontes' }],
      semana: [{ clave: 'f-b-2026-10-22', fecha: '2026-10-22', hora: '16:00', horaFin: '17:30', categoria: 'Semillero', grupo: 'tarde', sede: 'UVA El Paraíso' }],
    },
    ...extra,
  })
  const vistaInicio = (props) => dibujar(<VistaInicio usuario={{ nombre: 'Luis' }} conectado cargando={false} datos={null} error={null} onReintentar={() => {}} {...props} />)

  it('saluda con el nombre real y muestra categorías con su conteo y los entrenamientos', () => {
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador,misjugadores,horarioclub')
    const html = vistaInicio({ datos: datos() })
    expect(html).toContain('Hola, Luis')
    expect(html).toContain('Mis categorías')
    expect(html).toContain('Sub-11')
    expect(html).toContain('2 jugadores')
    expect(html).toContain('0 jugadores')
    expect(html).toContain('Entrenamientos de hoy')
    expect(html).toContain('5:00 p. m. – 6:30 p. m. · Horizontes')
    expect(html).toContain('Resto de la semana')
    expect(html).toContain('Semillero · grupo tarde')
    expect(html).toContain('UVA El Paraíso')
  })

  it('no deja constantes de ejemplo de la maqueta', () => {
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador,misjugadores,horarioclub')
    const html = vistaInicio({ datos: datos() })
    for (const ejemplo of ['Nicolás', 'Emmanuel', 'Tomás Giraldo', 'Copa Prado', '88 %', 'Sub-12']) expect(html).not.toContain(ejemplo)
    expect(html).not.toContain('Jugadores con más faltas')
  })

  it('el atajo a Mis jugadores y a Asistencia solo aparecen si su módulo está conectado', () => {
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador,misjugadores,horarioclub')
    const conMisJugadores = vistaInicio({ datos: datos() })
    expect(conMisJugadores).toContain('href="/entrenador/jugadores"')
    expect(conMisJugadores).not.toContain('Tomar asistencia')
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador')
    const sinNada = vistaInicio({ datos: datos() })
    expect(sinNada).not.toContain('Ver mis jugadores')
  })

  it('sin entrenamientos: mensajes de vacío', () => {
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador')
    const html = vistaInicio({ datos: datos({ horario: { hoy: [], semana: [] } }) })
    expect(html).toContain('No tienes entrenamientos hoy.')
    expect(html).toContain('No tienes más entrenamientos esta semana.')
  })

  it('si falla el horario, los jugadores siguen y hay reintento', () => {
    vi.stubEnv('VITE_API_MODULOS', 'inicioentrenador')
    const html = vistaInicio({ datos: datos({ horario: { error: Object.assign(new Error('x'), { estado: 500 }) } }) })
    expect(html).toContain('2 jugadores')
    expect(html).toContain('role="alert"')
    expect(html).toContain('No se pudo cargar tu horario.')
    expect(html).toContain('Reintentar')
  })

  it('cargando, error, sin vínculo, sin categorías y módulo apagado', () => {
    expect(vistaInicio({ cargando: true })).toContain('Cargando tu resumen…')
    expect(vistaInicio({ error: Object.assign(new Error('x'), { estado: 500 }) })).toContain('No se pudo cargar tu resumen.')
    expect(vistaInicio({ datos: { perfil: null } })).toContain('no está vinculada a un registro de entrenador')
    expect(vistaInicio({ datos: { perfil: { idEntrenador: 'e1', categorias: [] } } })).toContain('Todavía no tienes categorías asignadas')
    expect(vistaInicio({ conectado: false })).toContain('todavía no está conectado a la API')
  })

  it('el contenedor, con el módulo apagado (pruebas), no llama a la API', async () => {
    const { AuthContext } = await import('../../context/AuthContext.js')
    const html = dibujar(<AuthContext.Provider value={{ usuario: { nombre: 'Luis' } }}><Inicio /></AuthContext.Provider>)
    expect(html).toContain('Hola, Luis')
    expect(html).toContain('todavía no está conectado a la API')
  })
})

describe('menú del entrenador con VITE_API_MODULOS', () => {
  const opciones = (valor) => {
    vi.stubEnv('VITE_API_MODULOS', valor)
    const modulos = modulosConApi()
    const grupo = (menusVisibles({ menus, role: 'entrenador', autenticacionReal: true, modulos })).flatMap((g) => g.items.map((i) => i.label))
    const barra = bottomNavVisible({ bottomNavs, role: 'entrenador', autenticacionReal: true, modulos }).map((i) => i.label)
    return { grupo, barra }
  }

  it('con la configuración nueva solo ve Inicio, Mis jugadores y Horarios', () => {
    const r = opciones('entrenadores,pagos,horarioclub,usuarios,iniciotesorero,dashboard,inicioacudiente,reportes,inicioentrenador,misjugadores')
    expect(r.grupo).toEqual(['Inicio', 'Mis jugadores', 'Horarios'])
    expect(r.barra).toEqual(['Inicio', 'Jugadores', 'Horarios'])
  })

  it('con la configuración anterior (sin los módulos nuevos) solo ve Horarios', () => {
    expect(opciones('entrenadores,pagos,horarioclub,usuarios,iniciotesorero,dashboard,inicioacudiente,reportes').grupo).toEqual(['Horarios'])
  })

  it('encender "deportistas" no muestra Mis jugadores (es del administrador)', () => {
    expect(opciones('deportistas').grupo).toEqual([])
  })

  it('Asistencia y Torneos siguen ocultos hasta que se enciendan sus módulos', () => {
    expect(opciones('inicioentrenador,misjugadores,horarioclub').grupo).not.toContain('Asistencia')
    expect(opciones('inicioentrenador,misjugadores,horarioclub').grupo).not.toContain('Torneos')
    expect(opciones('asistencia,torneos').grupo).toEqual(['Asistencia', 'Torneos'])
  })
})
