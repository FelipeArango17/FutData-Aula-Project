import { describe, expect, it } from 'vitest'
import { adaptarFranja, consultaFranjas, cuerpoFranja } from './franjasApi.js'

const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const ENT = 'e0a1e0a1-0000-4000-8000-000000000001'
const dto = (extra = {}) => ({
  idFranja: '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b', idCategoria: CAT, categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00',
  sede: 'Horizontes', idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null, ...extra,
})

describe('adaptarFranja', () => {
  it('pasa idFranja a id y conserva los nombres que trae el backend', () => {
    expect(adaptarFranja(dto())).toEqual({
      id: '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b', idCategoria: CAT, categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00',
      sede: 'Horizontes', idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null,
    })
  })

  it('"por turnos": idEntrenador, entrenador y entrenadorActivo quedan en null', () => {
    const f = adaptarFranja(dto({ idEntrenador: null, entrenador: null, entrenadorActivo: null, grupo: 'tarde' }))
    expect(f).toMatchObject({ idEntrenador: null, entrenador: null, entrenadorActivo: null, grupo: 'tarde' })
  })

  it('un entrenador desactivado sale con entrenadorActivo false', () => {
    expect(adaptarFranja(dto({ entrenadorActivo: false })).entrenadorActivo).toBe(false)
  })
})

describe('cuerpoFranja', () => {
  const datos = { idCategoria: CAT, dia: '2', horaInicio: '16:30', horaFin: '18:00', sede: 'UVA El Paraíso', idEntrenador: ENT, grupo: 'tarde' }

  it('manda los siete campos, el día como número', () => {
    expect(cuerpoFranja(datos)).toEqual({ idCategoria: CAT, dia: 2, horaInicio: '16:30', horaFin: '18:00', sede: 'UVA El Paraíso', idEntrenador: ENT, grupo: 'tarde' })
  })

  it('entrenador y grupo vacíos van como null (el PUT reemplaza todo)', () => {
    expect(cuerpoFranja({ ...datos, idEntrenador: '', grupo: '' })).toMatchObject({ idEntrenador: null, grupo: null })
  })

  it('recorta las horas a HH:mm (el campo de hora puede traer segundos)', () => {
    expect(cuerpoFranja({ ...datos, horaInicio: '07:30:00', horaFin: '09:00:00' })).toMatchObject({ horaInicio: '07:30', horaFin: '09:00' })
  })

  it('no inventa ids numéricos: el UUID viaja tal cual', () => {
    expect(cuerpoFranja(datos).idCategoria).toBe(CAT)
  })
})

describe('consultaFranjas', () => {
  it('sin filtros no agrega query', () => {
    expect(consultaFranjas()).toBe('')
    expect(consultaFranjas({ categoria: '', entrenador: undefined, sede: null })).toBe('')
  })

  it('codifica solo los filtros con valor', () => {
    expect(consultaFranjas({ categoria: 'Sub-12', sede: 'UVA El Paraíso' })).toBe('?categoria=Sub-12&sede=UVA+El+Para%C3%ADso')
    expect(consultaFranjas({ entrenador: ENT })).toBe(`?entrenador=${ENT}`)
  })
})
