import { describe, expect, it } from 'vitest'
import { ENTRENADORES_CLUB, FRANJAS_EJEMPLO, entrenadorDeCategoria, idCategoriaDe, nombreDeCategoria, sedeDeCategoria } from '../services/horarioClubData.js'
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
    const cardona = franjasDeEntrenador(FRANJAS_EJEMPLO, 4) // Sub-13 y Sub-14
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

  it('una franja sin entrenador ("Todos los entrenadores") no genera choque de entrenador', () => {
    const existente = franja({ id: 1, idEntrenador: null })
    expect(validarFranja(franja({ idEntrenador: null }), [existente])).toEqual({})
    expect(validarFranja(franja({ idEntrenador: '' }), [existente])).toEqual({})
    expect(validarFranja(franja({ idEntrenador: 3 }), [existente])).toEqual({})
  })

  it('el grupo solo aplica a Sub-9 y Sub-10 y es mañana o tarde', () => {
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Sub-9'), grupo: 'mañana', idEntrenador: null }), [])).toEqual({})
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Sub-10'), grupo: 'tarde', idEntrenador: null }), [])).toEqual({})
    expect(validarFranja(franja({ grupo: 'mañana' }), [])).toEqual({ grupo: MENSAJES_FRANJA.grupo }) // Sub-11
    expect(validarFranja(franja({ idCategoria: idCategoriaDe('Sub-9'), grupo: 'noche' }), [])).toEqual({ grupo: MENSAJES_FRANJA.grupo })
  })
})

describe('plantilla de ejemplo del club', () => {
  it('ninguna franja tiene errores contra las demás: no hay choques de entrenador', () => {
    for (const f of FRANJAS_EJEMPLO) {
      const errores = validarFranja(f, FRANJAS_EJEMPLO, f.id)
      expect(errores, `${nombreDeCategoria(f.idCategoria)} día ${f.dia} ${f.horaInicio}`).toEqual({})
    }
  })

  it('todas las categorías vigentes tienen franjas, y solo esas categorías existen', () => {
    for (const nombre of NOMBRES_CATEGORIAS) expect(franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe(nombre)).length, nombre).toBeGreaterThan(0)
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.idCategoria)).size).toBe(NOMBRES_CATEGORIAS.length)
    expect(NOMBRES_CATEGORIAS).toHaveLength(10)
  })

  it('va de lunes a sábado, con ids únicos, horas válidas y las dos sedes', () => {
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.id)).size).toBe(FRANJAS_EJEMPLO.length)
    expect(FRANJAS_EJEMPLO.every((f) => f.dia >= 1 && f.dia <= 6 && f.horaFin > f.horaInicio)).toBe(true)
    expect(new Set(FRANJAS_EJEMPLO.map((f) => f.sede))).toEqual(new Set(['Horizontes', 'UVA El Paraíso']))
  })

  it('el grupo solo se usa en Sub-9 y Sub-10, con mañana y tarde, y sin entrenador fijo', () => {
    const conGrupo = FRANJAS_EJEMPLO.filter((f) => f.grupo)
    expect(conGrupo).toHaveLength(8)
    expect(new Set(conGrupo.map((f) => nombreDeCategoria(f.idCategoria)))).toEqual(new Set(['Sub-9', 'Sub-10']))
    expect(new Set(conGrupo.map((f) => f.grupo))).toEqual(new Set(['mañana', 'tarde']))
    expect(conGrupo.every((f) => f.idEntrenador === null)).toBe(true)
  })

  it('Párvulos y Cachorros no tienen entrenador fijo; las demás sí, y una categoría tiene un solo responsable', () => {
    for (const nombre of ['Párvulos', 'Cachorros']) expect(entrenadorDeCategoria(nombre)).toBeNull()
    for (const nombre of NOMBRES_CATEGORIAS.slice(2)) expect(entrenadorDeCategoria(nombre), nombre).not.toBeNull()
    const responsables = ENTRENADORES_CLUB.flatMap((e) => e.categorias)
    expect(new Set(responsables).size).toBe(responsables.length)
    for (const f of FRANJAS_EJEMPLO.filter((x) => !x.grupo)) expect(f.idEntrenador).toBe(entrenadorDeCategoria(nombreDeCategoria(f.idCategoria)))
  })

  it('las horas de ejemplo coinciden con la hoja del club', () => {
    const de = (cat) => franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe(cat)).filter((f) => !f.grupo).map((f) => `${f.dia} ${f.horaInicio}-${f.horaFin}`)
    expect(de('Párvulos')).toEqual(['2 08:30-09:30', '6 08:30-10:00'])
    expect(de('Cachorros')).toEqual(['2 07:00-08:00', '2 16:30-17:30', '4 07:00-08:00', '4 16:30-17:30', '6 08:30-10:00'])
    expect(de('Sub-9')).toEqual(['5 19:00-20:00', '6 08:30-10:00'])
    expect(de('Sub-10')).toEqual(['1 19:00-20:00', '2 19:00-20:00', '5 19:00-20:00', '6 08:30-10:00'])
    expect(de('Sub-11')).toEqual(['2 20:00-21:00', '3 19:00-20:00', '4 19:00-20:00'])
    expect(de('Sub-12')).toEqual(['1 19:00-20:00', '2 18:00-19:00', '4 19:00-20:00'])
    expect(de('Sub-13')).toEqual(['1 19:00-20:00', '3 19:00-20:00', '5 19:00-20:00'])
    expect(de('Sub-14')).toEqual(['1 18:00-19:00', '3 18:00-19:00', '5 18:00-19:00'])
    expect(de('Sub-15')).toEqual(['1 18:00-19:00', '2 20:00-21:00', '4 18:00-19:00'])
    expect(de('Sub-16')).toEqual(['2 16:30-18:00', '3 18:00-19:00', '4 16:30-18:00'])
    const semillero = (grupo) => franjasDeCategoria(FRANJAS_EJEMPLO, idCategoriaDe('Sub-9')).filter((f) => f.grupo === grupo).map((f) => `${f.dia} ${f.horaInicio}-${f.horaFin}`)
    expect(semillero('mañana')).toEqual(['2 07:00-08:00', '4 07:00-08:30'])
    expect(semillero('tarde')).toEqual(['2 16:30-17:30', '4 16:30-18:00'])
  })

  it('Párvulos a Sub-11 van en UVA El Paraíso y Sub-12 a Sub-16 en Horizontes', () => {
    for (const f of FRANJAS_EJEMPLO) expect(f.sede).toBe(sedeDeCategoria(nombreDeCategoria(f.idCategoria)))
    expect(sedeDeCategoria('Sub-11')).toBe('UVA El Paraíso')
    expect(sedeDeCategoria('Sub-12')).toBe('Horizontes')
  })
})

describe('entrenamientosDeSemana', () => {
  const semana = entrenamientosDeSemana('2026-10-19', FRANJAS_EJEMPLO)

  it('genera un entrenamiento por franja en las fechas de esa semana (lunes 19 a sábado 24)', () => {
    expect(semana).toHaveLength(FRANJAS_EJEMPLO.length)
    expect(semana.every((e) => e.fecha >= '2026-10-19' && e.fecha <= '2026-10-24')).toBe(true)
    const sub13 = semana.filter((e) => e.categoria === 'Sub-13')
    expect(sub13.map((e) => e.fecha)).toEqual(['2026-10-19', '2026-10-21', '2026-10-23'])
    expect(sub13[0]).toEqual({ id: expect.any(Number), categoria: 'Sub-13', fecha: '2026-10-19', hora: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: 4, grupo: null })
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
