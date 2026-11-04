import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Sin red y sin depender de .env.local: el módulo "horarioclub" sale de vi.stubEnv y el cliente HTTP está simulado.
vi.mock('../services/apiClient.js', () => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))

import SesionesSemana from '../components/horario/SesionesSemana.jsx'
import { entrenadorDeSesion } from '../components/horario/formatoSesion.js'
import { adaptarFranja } from './franjasApi.js'
import { estadoPerfil, esDeMisCategorias, opcionesDeCategoria } from './horarioEntrenador.js'
import { sesionesDeSemana } from './semana.js'
import { diasDeSemana } from './fechas.js'
import { modulosConApi, usarApiDe } from '../services/usarApi.js'
import { RoleContext } from '../context/RoleContext.js'

afterEach(() => vi.unstubAllEnvs())

// Formato real del backend: CategoriaYoDTO y FranjaHorarioResponseDTO
const perfil = { idEntrenador: 'e1', categorias: [{ idCategoria: 'c11', nombre: 'Sub-11' }, { idCategoria: 'c12', nombre: 'Sub-12' }] }
const dto = (extra) => ({
  idFranja: 'f1', idCategoria: 'c11', categoria: 'Sub-11', dia: 1, horaInicio: '16:00', horaFin: '17:30', sede: 'Horizontes',
  idEntrenador: 'e1', entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null, ...extra,
})
const LUNES = '2026-10-19'
const franjas = [
  dto(),
  dto({ idFranja: 'f2', idCategoria: 'c13', categoria: 'Sub-13', dia: 2, idEntrenador: 'e2', entrenador: 'Ana Gómez' }),
  dto({ idFranja: 'f3', idCategoria: 'c12', categoria: 'Sub-12', dia: 3, idEntrenador: null, entrenador: null, entrenadorActivo: null }),
].map(adaptarFranja)
const sesiones = sesionesDeSemana(franjas, [], LUNES)

describe('Horarios del entrenador (API)', () => {
  it('el módulo horarioclub se enciende con vi.stubEnv, no con .env.local', () => {
    vi.stubEnv('VITE_API_MODULOS', 'horarioclub')
    expect(modulosConApi()).toContain('horarioclub')
    expect(usarApiDe('horarioclub', { modo: 'production' })).toBe(true)
  })

  it('línea del entrenador: "Tú", el nombre de otro, o "Por turnos"', () => {
    const [propia, otro, turnos] = sesiones
    expect(entrenadorDeSesion(propia, 'e1')).toBe('Tú')
    expect(entrenadorDeSesion(otro, 'e1')).toBe('Ana Gómez')
    expect(entrenadorDeSesion(turnos, 'e1')).toBe('Por turnos')
    expect(entrenadorDeSesion(propia)).toBe('Luis Pérez') // sin perfil no se dice "Tú"
  })

  it('estado del perfil: cargando, error, sin entrenador, sin categorías y ok', () => {
    expect(estadoPerfil({ cargando: true })).toBe('cargando')
    expect(estadoPerfil({ error: true })).toBe('error')
    expect(estadoPerfil({ perfil: null })).toBe('sin-categorias')
    expect(estadoPerfil({ perfil: { idEntrenador: 'e1', categorias: [] } })).toBe('sin-categorias')
    expect(estadoPerfil({ perfil })).toBe('ok')
  })

  it('"Mis entrenamientos" ofrece solo sus categorías; "Todo el club" todas', () => {
    expect(opcionesDeCategoria({ soloMias: true, perfil })).toEqual(['Todas', 'Sub-11', 'Sub-12'])
    const club = opcionesDeCategoria({ soloMias: false })
    expect(club).toContain('Sub-13')
    expect(club[0]).toBe('Todas')
  })

  it('reconoce sus franjas por idCategoria', () => {
    expect(sesiones.map((s) => esDeMisCategorias(s, perfil))).toEqual([true, false, true])
  })

  const dibujar = (modo, extra) => renderToStaticMarkup(
    <MemoryRouter><RoleContext.Provider value={{ role: 'entrenador' }}>
      <SesionesSemana dias={diasDeSemana(LUNES)} sesiones={sesiones} hoy="2026-10-21" modo={modo} {...extra} />
    </RoleContext.Provider></MemoryRouter>,
  )

  it.each(['cuadricula', 'lista'])('en %s resalta "Tu categoría" con texto y dice Tú / nombre / Por turnos', (modo) => {
    const html = dibujar(modo, { miId: 'e1', esPropia: (s) => esDeMisCategorias(s, perfil) })
    expect(html.match(/Tu categoría/g)).toHaveLength(2) // las dos franjas de sus categorías
    if (modo === 'cuadricula') expect(html).toContain(', tu categoría') // también en el nombre accesible del grupo
    expect(html).toContain('Entrenador: Tú')
    expect(html).toContain('Entrenador: Ana Gómez')
    expect(html).toContain('Entrenador: Por turnos')
  })

  it('sin contexto (otros roles) no hay etiqueta ni "Tú"', () => {
    const html = dibujar('lista')
    expect(html).not.toContain('Tu categoría')
    expect(html).not.toContain('Entrenador: Tú')
    expect(html).toContain('Entrenador: Luis Pérez')
  })
})
