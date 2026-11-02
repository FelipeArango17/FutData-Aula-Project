import { describe, expect, it } from 'vitest'
import { ENTRENADORES_CLUB, FRANJAS_EJEMPLO, SEDES, entrenadorDeCategoria, idCategoriaDe, nombreDeCategoria } from '../services/horarioClubData.js'
import { NOMBRES_CATEGORIAS } from './categorias.js'
import {
  MENSAJES_FRANJA, chocaEntrenador, entrenamientosDeSemana, franjasDeCategoria, franjasDeEntrenador, franjasPorDia, haySolapamiento, seSolapan, validarFranja,
} from './horarioClub.js'

const franja = (extra = {}) => ({ id: 100, idCategoria: idCategoriaDe('Sub-11'), dia: 3, horaInicio: '10:00', horaFin: '11:00', sede: 'UVA El Paraíso', idEntrenador: 3, grupo: null, ...extra })

describe('haySolapamiento', () => {
  it('se cruzan si el mismo día un horario empieza antes de que termine el otro', () => {
    expect(haySolapamiento(franja(), franja({ horaInicio: '10:30', horaFin: '11:30' }))).toBe(true)
    expect(haySolapamiento(franja(), franja({ horaInicio: '09:00', horaFin: '10:30' }))).toBe(true)
    expect(haySolapamiento(franja(), franja({ horaInicio: '10:15', horaFin: '10:45' }))).toBe(true) // contenida
  })

  it('terminar justo cuando empieza la otra no es solapamiento', () => {
    expect(haySolapamiento(franja(), franja({ horaInicio: '11:00', horaFin: '12:00' }))).toBe(false)
    expect(haySolapamiento(franja(), franja({ horaInicio: '09:00', horaFin: '10:00' }))).toBe(false)
  })

  it('otro día no se cruza', () => {
    expect(haySolapamiento(franja(), franja({ dia: 4 }))).toBe(false)
  })

  it('seSolapan trabaja con horas HH:MM', () => {
    expect(seSolapan('18:00', '19:00', '18:59', '20:00')).toBe(true)
    expect(seSolapan('18:00', '19:00', '19:00', '20:00')).toBe(false)
  })
})

describe('franjasPorDia, franjasDeCategoria y franjasDeEntrenador', () => {
  it('agrupa de lunes (1) a sábado (6), ordenadas por hora', () => {
    const porDia = franjasPorDia(FRANJAS_EJEMPLO)
    expect(Object.keys(porDia)).toEqual(['1', '2', '3', '4', '5', '6'])
    expect(porDia[1].every((f) => f.dia === 1)).toBe(true)
    for (const dia of Object.values(porDia)) expect(dia.map((f) => f.horaInicio)).toEqual(dia.map((f) => f.horaInicio).sort())
    expect(Object.values(porDia).flat()).toHaveLength(FRANJAS_EJEMPLO.length)
  })

  it('filtra por categoría (id como número o texto) y por entrenador', () => {
    const sub13 = franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe('Sub-13'))
    expect(sub13.map((f) => f.dia)).toEqual([1, 3, 5])
    expect(franjasDeCategoria(FRANJAS_EJEMPLO, String(idCategoriaDe('Sub-13')))).toEqual(sub13)
    const cardona = franjasDeEntrenador(FRANJAS_EJEMPLO, 3) // Jhon Cardona: Sub-13 y Sub-14
    expect(new Set(cardona.map((f) => nombreDeCategoria(f.idCategoria)))).toEqual(new Set(['Sub-13', 'Sub-14']))
    expect(cardona).toHaveLength(6)
    expect(franjasDeEntrenador(FRANJAS_EJEMPLO, 99)).toEqual([])
  })
})

describe('validarFranja', () => {
  it('una franja correcta no tiene errores', () => {
    expect(validarFranja(franja(), [])).toEqual({})
  })

  it('categoría, día, horas y sede son obligatorios', () => {
    const errores = validarFranja({ idCategoria: '', dia: '', horaInicio: '', horaFin: '', sede: '', idEntrenador: '' }, [])
    expect(errores).toEqual({
      idCategoria: MENSAJES_FRANJA.categoria,
      dia: MENSAJES_FRANJA.dia,
      horaInicio: MENSAJES_FRANJA.horaInicioObligatoria,
      horaFin: MENSAJES_FRANJA.horaFinObligatoria,
      sede: MENSAJES_FRANJA.sede,
    })
  })

  it('el día debe estar entre lunes y sábado', () => {
    expect(validarFranja(franja({ dia: 7 }), [])).toEqual({ dia: MENSAJES_FRANJA.diaRango })
    expect(validarFranja(franja({ dia: 0 }), [])).toEqual({ dia: MENSAJES_FRANJA.diaRango })
    expect(validarFranja(franja({ dia: 1.5 }), [])).toEqual({ dia: MENSAJES_FRANJA.diaRango })
    expect(validarFranja(franja({ dia: '6' }), [])).toEqual({})
  })

  it('la hora de fin debe ser posterior a la de inicio', () => {
    expect(validarFranja(franja({ horaFin: '10:00' }), [])).toEqual({ horaFin: MENSAJES_FRANJA.horaFinAnterior })
    expect(validarFranja(franja({ horaFin: '09:00' }), [])).toEqual({ horaFin: MENSAJES_FRANJA.horaFinAnterior })
  })

  it('las horas deben tener el formato HH:MM', () => {
    expect(validarFranja(franja({ horaInicio: '25:00' }), [])).toMatchObject({ horaInicio: MENSAJES_FRANJA.horaInvalida })
    expect(validarFranja(franja({ horaFin: '9' }), [])).toMatchObject({ horaFin: MENSAJES_FRANJA.horaInvalida })
  })

  it('la sede debe ser Horizontes o UVA El Paraíso', () => {
    expect(validarFranja(franja({ sede: 'Cancha 1' }), [])).toEqual({ sede: MENSAJES_FRANJA.sede })
    expect(validarFranja(franja({ sede: 'Horizontes' }), [])).toEqual({})
  })

  it('el mismo entrenador no puede tener dos franjas que se cruzan el mismo día', () => {
    const existente = franja({ id: 1, horaInicio: '10:30', horaFin: '11:30', idCategoria: idCategoriaDe('Sub-12') })
    expect(validarFranja(franja(), [existente])).toEqual({ idEntrenador: MENSAJES_FRANJA.entrenadorOcupado })
  })

  it('al editar, la franja no choca consigo misma', () => {
    const propia = franja({ id: 7 })
    expect(validarFranja(franja({ id: 7 }), [propia], 7)).toEqual({})
    expect(validarFranja(franja(), [propia], null)).toEqual({ idEntrenador: MENSAJES_FRANJA.entrenadorOcupado })
  })

  it('pegadas (una termina cuando empieza la otra), otro día u otro entrenador no chocan', () => {
    expect(validarFranja(franja(), [franja({ id: 1, horaInicio: '11:00', horaFin: '12:00' })])).toEqual({})
    expect(validarFranja(franja(), [franja({ id: 1, dia: 4 })])).toEqual({})
    expect(validarFranja(franja(), [franja({ id: 1, idEntrenador: 4 })])).toEqual({})
  })

  it('dos categorías distintas pueden coincidir en hora (con entrenadores distintos o sin entrenador)', () => {
    const otra = franja({ id: 1, idCategoria: idCategoriaDe('Sub-12'), idEntrenador: 1 })
    expect(validarFranja(franja(), [otra])).toEqual({})
  })

  it('una franja sin entrenador ("Por turnos") no genera choque de entrenador', () => {
    const existente = franja({ id: 1, idEntrenador: null })
    expect(validarFranja(franja({ idEntrenador: null }), [existente])).toEqual({})
    expect(validarFranja(franja({ idEntrenador: '' }), [existente])).toEqual({})
    expect(validarFranja(franja({ idEntrenador: 3 }), [existente])).toEqual({})
  })

  it('el grupo solo aplica a Cachorros y Semillero y es mañana o tarde', () => {
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Cachorros'), grupo: 'mañana', idEntrenador: null }), [])).toEqual({})
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Semillero'), grupo: 'tarde', idEntrenador: null }), [])).toEqual({})
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Párvulos'), grupo: 'tarde', idEntrenador: null }), [])).toEqual({ grupo: MENSAJES_FRANJA.grupo })
    expect(validarFranja(franja({ grupo: 'mañana' }), [])).toEqual({ grupo: MENSAJES_FRANJA.grupo }) // Sub-11
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Semillero'), grupo: 'noche' }), [])).toEqual({ grupo: MENSAJES_FRANJA.grupo })
  })
})

describe('plantilla de ejemplo del club', () => {
  const texto = (f) => `${f.dia} ${f.horaInicio}-${f.horaFin}`
  const de = (categoria, filtro = () => true) => franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe(categoria)).filter(filtro).map((f) => `${texto(f)} ${f.sede}${f.grupo ? ` ${f.grupo}` : ''}`)

  it('tiene exactamente 30 franjas', () => {
    expect(FRANJAS_EJEMPLO).toHaveLength(30)
  })

  it('ninguna franja tiene errores contra las demás: ningún entrenador con id se cruza', () => {
    for (const f of FRANJAS_EJEMPLO) {
      const errores = validarFranja(f, FRANJAS_EJEMPLO, f.id)
      expect(errores, `${nombreDeCategoria(f.idCategoria)} día ${f.dia} ${f.horaInicio}`).toEqual({})
    }
    const conEntrenador = FRANJAS_EJEMPLO.filter((f) => f.idEntrenador !== null)
    for (const f of conEntrenador) {
      expect(conEntrenador.some((g) => g.id !== f.id && g.idEntrenador === f.idEntrenador && haySolapamiento(f, g)), `${nombreDeCategoria(f.idCategoria)} día ${f.dia}`).toBe(false)
    }
  })

  it('todas las categorías vigentes tienen franjas, y solo esas categorías existen', () => {
    for (const nombre of NOMBRES_CATEGORIAS) expect(franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe(nombre)).length, nombre).toBeGreaterThan(0)
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.idCategoria)).size).toBe(NOMBRES_CATEGORIAS.length)
    expect(NOMBRES_CATEGORIAS).toHaveLength(9)
  })

  it('cada franja tiene una sede válida, de lunes a sábado, con ids únicos y horas válidas', () => {
    for (const f of FRANJAS_EJEMPLO) expect(SEDES, `${nombreDeCategoria(f.idCategoria)} día ${f.dia}`).toContain(f.sede)
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.id)).size).toBe(FRANJAS_EJEMPLO.length)
    expect(FRANJAS_EJEMPLO.every((f) => f.dia >= 1 && f.dia <= 6 && f.horaFin > f.horaInicio)).toBe(true)
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.sede))).toEqual(new Set(['Horizontes', 'UVA El Paraíso']))
  })

  it('el grupo solo se usa en Cachorros y Semillero, martes y jueves, con mañana y tarde, y sin entrenador fijo', () => {
    const conGrupo = FRANJAS_EJEMPLO.filter((f) => f.grupo)
    expect(conGrupo).toHaveLength(8)
    expect(new Set(conGrupo.map((f) => nombreDeCategoria(f.idCategoria)))).toEqual(new Set(['Cachorros', 'Semillero']))
    expect(new Set(conGrupo.map((f) => f.grupo))).toEqual(new Set(['mañana', 'tarde']))
    expect(new Set(conGrupo.map((f) => f.dia))).toEqual(new Set([2, 4]))
    expect(conGrupo.every((f) => f.idEntrenador === null)).toBe(true)
  })

  it('Párvulos, Cachorros y Semillero van por turnos; las demás tienen entrenador, y una categoría tiene un solo responsable', () => {
    for (const nombre of ['Párvulos', 'Cachorros', 'Semillero']) {
      expect(entrenadorDeCategoria(nombre), nombre).toBeNull()
      expect(franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe(nombre)).every((f) => f.idEntrenador === null), nombre).toBe(true)
    }
    for (const nombre of NOMBRES_CATEGORIAS.slice(3)) expect(entrenadorDeCategoria(nombre), nombre).not.toBeNull()
    const responsables = ENTRENADORES_CLUB.flatMap((e) => e.categorias)
    expect(new Set(responsables).size).toBe(responsables.length)
    for (const f of FRANJAS_EJEMPLO.filter((x) => x.idEntrenador !== null)) expect(f.idEntrenador).toBe(entrenadorDeCategoria(nombreDeCategoria(f.idCategoria)))
  })

  it('son 5 entrenadores; solo Jhon Cardona tiene dos categorías y Dairo ya no está', () => {
    expect(ENTRENADORES_CLUB.map((e) => e.nombre)).toEqual(['Nicolás', 'David Mesa', 'Jhon Cardona', 'Giovanny', 'Jhon Ramírez'])
    expect(ENTRENADORES_CLUB.filter((e) => e.categorias.length > 1).map((e) => e.nombre)).toEqual(['Jhon Cardona'])
    expect(ENTRENADORES_CLUB.find((e) => e.nombre === 'Jhon Cardona').categorias).toEqual(['Sub-13', 'Sub-14'])
    expect(ENTRENADORES_CLUB.find((e) => e.nombre === 'Dairo')).toBeUndefined()
  })

  it('horas y sedes de la plantilla (Excel del club)', () => {
    expect(de('Párvulos')).toEqual(['2 08:30-10:00 UVA El Paraíso', '6 09:00-10:00 Horizontes'])
    const turnos = ['2 07:30-09:00 UVA El Paraíso mañana', '2 16:30-18:00 UVA El Paraíso tarde', '4 07:30-09:00 UVA El Paraíso mañana', '4 16:30-18:00 UVA El Paraíso tarde', '6 08:30-10:00 Horizontes']
    expect(de('Cachorros')).toEqual(turnos)
    expect(de('Semillero')).toEqual(turnos)
    expect(de('Sub-11')).toEqual(['2 20:00-21:00 Horizontes', '3 19:00-20:00 Horizontes', '4 19:00-20:00 Horizontes'])
    expect(de('Sub-12')).toEqual(['1 19:00-20:00 Horizontes', '2 18:00-19:00 Horizontes', '4 19:00-20:00 Horizontes'])
    expect(de('Sub-13')).toEqual(['1 19:00-20:00 Horizontes', '3 19:00-20:00 Horizontes', '5 19:00-20:00 Horizontes'])
    expect(de('Sub-14')).toEqual(['1 18:00-19:00 Horizontes', '3 18:00-19:00 Horizontes', '5 18:00-19:00 Horizontes'])
    expect(de('Sub-15')).toEqual(['1 18:00-19:00 Horizontes', '2 20:00-21:00 Horizontes', '4 18:00-19:00 Horizontes'])
    expect(de('Sub-16')).toEqual(['2 16:30-18:00 Horizontes', '3 18:00-19:00 Horizontes', '4 16:30-18:00 Horizontes'])
  })

  it('Párvulos, Cachorros y Semillero: UVA El Paraíso entre semana y Horizontes el sábado; Sub-11 a Sub-16 solo en Horizontes', () => {
    for (const f of FRANJAS_EJEMPLO) {
      const nombre = nombreDeCategoria(f.idCategoria)
      const esperada = ['Párvulos', 'Cachorros', 'Semillero'].includes(nombre) && f.dia !== 6 ? 'UVA El Paraíso' : 'Horizontes'
      expect(f.sede, `${nombre} día ${f.dia}`).toBe(esperada)
    }
  })
})

describe('entrenamientosDeSemana', () => {
  const semana = entrenamientosDeSemana('2026-10-19', FRANJAS_EJEMPLO)

  it('genera un entrenamiento por franja en las fechas de esa semana (lunes 19 a sábado 24)', () => {
    expect(semana).toHaveLength(FRANJAS_EJEMPLO.length)
    expect(semana.every((e) => e.fecha >= '2026-10-19' && e.fecha <= '2026-10-24')).toBe(true)
    const sub13 = semana.filter((e) => e.categoria === 'Sub-13')
    expect(sub13.map((e) => e.fecha)).toEqual(['2026-10-19', '2026-10-21', '2026-10-23'])
    expect(sub13[0]).toEqual({ id: expect.any(Number), categoria: 'Sub-13', fecha: '2026-10-19', hora: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: 3, grupo: null })
  })

  it('otra semana cambia solo las fechas', () => {
    const siguiente = entrenamientosDeSemana('2026-10-26', FRANJAS_EJEMPLO)
    expect(siguiente.map((e) => e.hora)).toEqual(semana.map((e) => e.hora))
    expect(siguiente[0].fecha).toBe('2026-10-26')
  })

  it('los ids son 1, 2, 3… ordenados por fecha y hora', () => {
    expect(semana.map((e) => e.id)).toEqual(semana.map((_, i) => i + 1))
    const claves = semana.map((e) => `${e.fecha} ${e.hora}`)
    expect(claves).toEqual([...claves].sort())
  })

  it('un entrenador no tiene dos entrenamientos que se cruzan en la misma fecha', () => {
    for (const e of semana) expect(chocaEntrenador(e, semana, e.id), `${e.categoria} ${e.fecha} ${e.hora}`).toBe(false)
  })
})

describe('chocaEntrenador', () => {
  const existentes = [{ id: 1, fecha: '2026-10-19', hora: '19:00', horaFin: '20:00', idEntrenador: 2 }]
  const prueba = (extra) => ({ fecha: '2026-10-19', hora: '19:30', horaFin: '20:30', idEntrenador: 2, ...extra })

  it('choca el mismo entrenador, la misma fecha y horarios que se cruzan', () => {
    expect(chocaEntrenador(prueba(), existentes)).toBe(true)
    expect(chocaEntrenador(prueba({ idEntrenador: '2' }), existentes)).toBe(true)
  })

  it('no choca con otra fecha, otro entrenador, horarios pegados, ignorarId o sin entrenador', () => {
    expect(chocaEntrenador(prueba({ fecha: '2026-10-20' }), existentes)).toBe(false)
    expect(chocaEntrenador(prueba({ idEntrenador: 3 }), existentes)).toBe(false)
    expect(chocaEntrenador(prueba({ hora: '20:00', horaFin: '21:00' }), existentes)).toBe(false)
    expect(chocaEntrenador(prueba(), existentes, 1)).toBe(false)
    expect(chocaEntrenador(prueba({ idEntrenador: null }), existentes)).toBe(false)
    expect(chocaEntrenador(prueba({ idEntrenador: '' }), existentes)).toBe(false)
  })
})

describe('validarFranja con ids UUID (modo API)', () => {
  const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
  const SEMILLERO = '0b6c3f1e-9d2a-4c8b-a1e7-5f4d3c2b1a09'
  const ENT = 'e0a1e0a1-0000-4000-8000-000000000001'
  const categorias = [{ id: CAT, nombre: 'Sub-12' }, { id: SEMILLERO, nombre: 'Semillero' }]
  const base = { idCategoria: CAT, dia: '1', horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: ENT, grupo: '' }

  it('reconoce la categoría por su UUID', () => {
    expect(validarFranja(base, [], null, categorias)).toEqual({})
    expect(validarFranja({ ...base, idCategoria: 'otro' }, [], null, categorias).idCategoria).toBe(MENSAJES_FRANJA.categoria)
  })

  it('el grupo solo aplica a Cachorros y Semillero según el nombre de la categoría', () => {
    expect(validarFranja({ ...base, grupo: 'tarde' }, [], null, categorias).grupo).toBe(MENSAJES_FRANJA.grupo)
    expect(validarFranja({ ...base, idCategoria: SEMILLERO, grupo: 'tarde' }, [], null, categorias)).toEqual({})
  })

  it('detecta el cruce del mismo entrenador con UUID y no el de una franja contigua ni el de la propia', () => {
    const existente = { id: 'f1', idCategoria: SEMILLERO, dia: 1, horaInicio: '18:30', horaFin: '19:30', sede: 'Horizontes', idEntrenador: ENT }
    expect(validarFranja(base, [existente], null, categorias).idEntrenador).toBe(MENSAJES_FRANJA.entrenadorOcupado)
    expect(validarFranja(base, [existente], 'f1', categorias)).toEqual({})
    expect(validarFranja(base, [{ ...existente, horaInicio: '18:00', horaFin: '19:00' }], null, categorias)).toEqual({})
    expect(validarFranja(base, [{ ...existente, idEntrenador: null }], null, categorias)).toEqual({})
  })
})
