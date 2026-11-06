import { describe, expect, it } from 'vitest'
import { adaptarSesionAsistencia, conObservacionSinEstado, consultaSesion, cuerpoGuardar, describirFallo, puedePasarLista, registrosAEnviar, sesionesParaLista } from './asistenciaApi.js'

const FRANJA = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const EXC = 'c0ffee00-0000-4000-8000-000000000009'
const D1 = 'd0000000-0000-4000-8000-000000000001'
const D2 = 'd0000000-0000-4000-8000-000000000002'

describe('consultaSesion', () => {
  it('manda la fecha y exactamente un identificador: la franja o el extra', () => {
    expect(consultaSesion({ fecha: '2026-11-03', idFranja: FRANJA, idExcepcion: null })).toBe(`/asistencia/sesion?fecha=2026-11-03&idFranja=${FRANJA}`)
    expect(consultaSesion({ fecha: '2026-11-03', idFranja: null, idExcepcion: EXC })).toBe(`/asistencia/sesion?fecha=2026-11-03&idExcepcion=${EXC}`)
  })
})

describe('cuerpoGuardar', () => {
  it('traduce estados a mayúsculas, recorta la observación y omite la vacía', () => {
    const cuerpo = cuerpoGuardar({ fecha: '2026-11-03', idFranja: FRANJA }, [
      { idDeportista: D1, estado: 'presente', observacion: '  ' },
      { idDeportista: D2, estado: 'justificado', observacion: ' Cita médica ' },
    ])
    expect(cuerpo).toEqual({
      fecha: '2026-11-03',
      idFranja: FRANJA,
      registros: [{ idDeportista: D1, estado: 'PRESENTE' }, { idDeportista: D2, estado: 'JUSTIFICADO', observaciones: 'Cita médica' }],
    })
  })

  it('para un extra manda idExcepcion y nunca los dos', () => {
    const cuerpo = cuerpoGuardar({ fecha: '2026-11-03', idFranja: null, idExcepcion: EXC }, [{ idDeportista: D1, estado: 'ausente' }])
    expect(cuerpo.idExcepcion).toBe(EXC)
    expect('idFranja' in cuerpo).toBe(false)
  })

  it('deja fuera lo que no tiene estado', () => {
    expect(cuerpoGuardar({ fecha: '2026-11-03', idFranja: FRANJA }, [{ idDeportista: D1, estado: '', observacion: 'x' }]).registros).toEqual([])
  })
})

describe('adaptarSesionAsistencia', () => {
  it('arma la sesión y los deportistas con estados en minúscula; sin estado queda vacío', () => {
    const r = adaptarSesionAsistencia({
      sesion: { fecha: '2026-11-03', horaInicio: '19:00', horaFin: '20:00', idCategoria: 'c', categoria: 'Sub-12', sede: 'Horizontes', grupo: null, idEntrenador: 'e', entrenador: 'Luis Pérez' },
      puedeRegistrar: true,
      deportistas: [
        { idDeportista: D1, nombre: 'Ana', apellido: 'Rojas', estado: 'AUSENTE', observaciones: 'Gripa' },
        { idDeportista: D2, nombre: 'Beto', apellido: 'Ríos', estado: null, observaciones: null },
      ],
    })
    expect(r.sesion).toMatchObject({ fecha: '2026-11-03', hora: '19:00', horaFin: '20:00', categoria: 'Sub-12', entrenador: 'Luis Pérez' })
    expect(r.puedeRegistrar).toBe(true)
    expect(r.deportistas).toEqual([
      { id: D1, nombre: 'Ana', apellido: 'Rojas', estado: 'ausente', observacion: 'Gripa' },
      { id: D2, nombre: 'Beto', apellido: 'Ríos', estado: '', observacion: '' },
    ])
  })

  it('sin deportistas devuelve lista vacía y puedeRegistrar solo si es true', () => {
    expect(adaptarSesionAsistencia({ sesion: {}, deportistas: [] })).toMatchObject({ puedeRegistrar: false, deportistas: [] })
  })
})

describe('quién ve "Pasar lista"', () => {
  const MIA = 'cat-mia'
  const perfil = { idEntrenador: 'yo', categorias: [{ idCategoria: MIA, nombre: 'Sub-12' }] }
  const hoy = '2026-10-22'
  // puedeGestionar viene en false en fechas pasadas (es el del horario): no cuenta
  const s = (extra) => ({ puedeGestionar: false, estado: 'PROGRAMADO', fecha: '2026-10-20', idCategoria: MIA, idEntrenador: 'otro', ...extra })
  const entrenador = { rol: 'entrenador', perfil }
  const admin = { rol: 'administrador' }

  it('el entrenador: pasada de su categoría sí, aunque puedeGestionar sea false (Semana anterior)', () => {
    expect(puedePasarLista(s(), hoy, entrenador)).toBe(true)
    expect(puedePasarLista(s({ fecha: hoy }), hoy, entrenador)).toBe(true)
  })
  it('el entrenador: una franja que él dirige, aunque sea de otra categoría', () => {
    expect(puedePasarLista(s({ idCategoria: 'x', idEntrenador: 'yo' }), hoy, entrenador)).toBe(true)
  })
  it('el entrenador: pasada de otra categoría y de otro entrenador, no', () => {
    expect(puedePasarLista(s({ idCategoria: 'x' }), hoy, entrenador)).toBe(false)
  })
  it('el entrenador sin perfil no ve nada', () => {
    expect(sesionesParaLista([s()], { rol: 'entrenador', perfil: null })).toEqual([])
  })
  it('futura o cancelada, no (ni para el administrador)', () => {
    for (const contexto of [entrenador, admin]) {
      expect(puedePasarLista(s({ fecha: '2026-10-23' }), hoy, contexto)).toBe(false)
      expect(puedePasarLista(s({ estado: 'CANCELADO' }), hoy, contexto)).toBe(false)
    }
  })
  it('el administrador: todas las de hoy o pasadas', () => {
    expect(puedePasarLista(s({ idCategoria: 'x' }), hoy, admin)).toBe(true)
    expect(sesionesParaLista([s(), s({ idCategoria: 'x' })], admin)).toHaveLength(2)
  })
  it('otros roles no ven nada', () => {
    expect(sesionesParaLista([s()], { rol: 'tesorero' })).toEqual([])
  })
  it('el entrenador solo ve sus sesiones (las otras no aparecen)', () => {
    expect(sesionesParaLista([s(), s({ idCategoria: 'x' })], entrenador)).toHaveLength(1)
  })
})

describe('conObservacionSinEstado', () => {
  it('detecta observaciones escritas sin estado; el espacio solo no cuenta', () => {
    expect(conObservacionSinEstado([
      { id: 'a', estado: '', observacion: 'Gripa' },
      { id: 'b', estado: '', observacion: '   ' },
      { id: 'c', estado: 'presente', observacion: 'ok' },
    ]).map((v) => v.id)).toEqual(['a'])
  })
})

describe('registrosAEnviar', () => {
  it('solo lo marcado con estado', () => {
    expect(registrosAEnviar({ [D1]: { estado: 'presente', observacion: '' }, [D2]: { estado: '', observacion: 'nota' } })).toEqual([
      { idDeportista: D1, estado: 'presente', observacion: '' },
    ])
    expect(registrosAEnviar({})).toEqual([])
  })
})

describe('describirFallo', () => {
  const fallo = (estado, extra = {}) => ({ estado, mensaje: 'Mensaje del backend', campos: [], ...extra })
  it('403 y 404 vuelven a la semana', () => {
    expect(describirFallo(fallo(403)).accion).toBe('volver')
    expect(describirFallo(fallo(404)).accion).toBe('volver')
  })
  it('400 de registros trae la posición (desde 1) de la fila con problema', () => {
    expect(describirFallo(fallo(400, { campos: ['registros'], mensaje: 'registro 2: deportista repetido' })).posicion).toBe(2)
    expect(describirFallo(fallo(400, { campos: ['registros'], mensaje: 'otro texto' })).posicion).toBeNull()
  })
  it('409 muestra el mensaje del backend y conserva lo marcado', () => {
    expect(describirFallo(fallo(409, { campos: ['fecha'], mensaje: 'Esa sesión está cancelada.' }))).toEqual({ accion: 'revisar', texto: 'Esa sesión está cancelada.' })
  })
  it('400 de registros no deja pasar mensajes técnicos', () => {
    const d = describirFallo(fallo(400, { campos: ['registros'], mensaje: 'registro 2: deportista repetido' }))
    expect(d.accion).toBe('revisar')
    expect(d.texto).not.toMatch(/registro|deportista repetido|\d/)
  })
  it('fallos de red o del servidor se pueden reintentar', () => {
    expect(describirFallo(fallo(0)).accion).toBe('reintentar')
    expect(describirFallo(fallo(500), { alGuardar: true }).texto).toBe('No se pudo guardar la asistencia. Intenta de nuevo.')
  })
})
