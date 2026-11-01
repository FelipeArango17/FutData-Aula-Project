import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { TablaDeportistas, TarjetasDeportistas } from './ListaDeportistas.jsx'
import DatosDeportista from './DatosDeportista.jsx'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

const base = { apellido: 'Ejemplo', documento: '1100000000', fechaNacimiento: '2016-05-09', activo: true, tipoDocumento: 'Tarjeta de identidad', telefono: '3000000000', direccion: 'Calle 1 # 2-3' }
const semillero = { ...base, id: 6, nombre: 'Mateo', idCategoria: 3, grupoEntrenamiento: 'mañana', posicion: 'Portero' }
const sub12 = { ...base, id: 11, nombre: 'Emmanuel', idCategoria: 5, grupoEntrenamiento: null, posicion: null }
const sinCategoria = { ...base, id: 30, nombre: 'Nuevo', idCategoria: null, grupoEntrenamiento: null, posicion: 'Defensor' }
const nombres = { 3: 'Semillero', 5: 'Sub-12' }
const dibujar = (Componente, deportistas) => renderToStaticMarkup(<MemoryRouter><Componente deportistas={deportistas} nombresCategoria={nombres} /></MemoryRouter>)

describe('tabla de deportistas', () => {
  const html = dibujar(TablaDeportistas, [semillero, sub12, sinCategoria])

  it('tiene la columna Posición y cada fila muestra su posición, o "Sin definir"', () => {
    expect(html).toMatch(/<th scope="col"[^>]*>Posición<\/th>/)
    expect(html).toContain('>Portero</td>')
    expect(html).toContain('>Defensor</td>')
    expect(html).toContain('>Sin definir</td>')
  })

  it('la categoría lleva el grupo en Cachorros y Semillero ("Semillero · mañana") y solo el nombre en las demás', () => {
    expect(html).toContain('>Semillero · mañana</td>')
    expect(html).toContain('>Sub-12</td>')
    expect(html).toContain('>Sin asignar</td>')
    expect(html).not.toMatch(/nacidos/)
  })
})

describe('tarjetas de deportistas (móvil)', () => {
  const html = dibujar(TarjetasDeportistas, [semillero, sub12])

  it('muestran la categoría con grupo y la posición dentro de la tarjeta', () => {
    expect(html).toContain('Categoría: Semillero · mañana')
    expect(html).toContain('Categoría: Sub-12')
    expect(html).toContain('Posición: Portero')
    expect(html).toContain('Posición: Sin definir')
  })
})

describe('ficha del deportista: datos personales', () => {
  const ficha = (deportista, categoria) => renderToStaticMarkup(<DatosDeportista deportista={deportista} categoria={categoria} />)

  it('muestra solo el nombre de la categoría, sin "(nacidos en …)"', () => {
    for (const [d, categoria] of [[semillero, 'Semillero'], [sub12, 'Sub-12'], [{ ...base, id: 1, grupoEntrenamiento: 'tarde', posicion: null }, 'Cachorros']]) {
      const html = ficha(d, categoria)
      expect(html).toContain(`<dd class="break-words text-body">${categoria}</dd>`)
      expect(html).not.toMatch(/nacidos|\(\d{4}/)
    }
  })

  it('el grupo de entrenamiento aparece solo en Cachorros y Semillero', () => {
    const conGrupo = ficha(semillero, 'Semillero')
    expect(conGrupo).toContain('Grupo de entrenamiento')
    expect(conGrupo).toContain('>mañana</dd>')
    expect(ficha(sub12, 'Sub-12')).not.toContain('Grupo de entrenamiento')
    expect(ficha(sinCategoria, null)).not.toContain('Grupo de entrenamiento')
  })

  it('la posición se muestra, o "Sin definir" si no hay', () => {
    expect(ficha(semillero, 'Semillero')).toContain('>Portero</dd>')
    expect(ficha(sub12, 'Sub-12')).toContain('<dt class="text-small text-secondary">Posición</dt><dd class="break-words text-body">Sin definir</dd>')
  })
})
