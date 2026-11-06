import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API: el módulo "horarioclub" sale de vi.stubEnv (no depende de .env.local) y el cliente HTTP está simulado (nunca hay red).
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)

let servicio

const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const ENT = 'e0a1e0a1-0000-4000-8000-000000000001'
const dto = (id, extra = {}) => ({
  idFranja: id, idCategoria: CAT, categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes',
  idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null, ...extra,
})
const fallo = (promesa) => promesa.then(() => { throw new Error('Debía rechazar') }, (e) => ({ ...e, message: e.message }))

beforeEach(async () => {
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', 'horarioclub')
  api.apiRequest.mockReset()
  vi.resetModules()
  servicio = await import('./semanaService.js')
})

afterEach(() => {
  vi.unstubAllEnvs()
})

const sesion = (extra = {}) => ({
  tipo: 'FRANJA', estado: 'ACTIVO', idFranja: 'a', idExcepcion: null, fecha: '2026-11-02', dia: 1, horaInicio: '19:00', horaFin: '20:00',
  idCategoria: CAT, categoria: 'Sub-12', sede: 'Horizontes', idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true,
  grupo: null, motivo: null, puedeGestionar: true, ...extra,
})
const EXC = 'c0ffee00-0000-4000-8000-000000000009'

describe('capacidades y permisos en modo API', () => {
  it('lee, cancela y crea extras; editar un extra no existe en el backend y queda oculto', () => {
    expect(servicio.semanaPorApi()).toBe(true)
    expect(servicio.capacidades()).toEqual({ leer: true, cancelar: true, extras: true, editarExtra: false, rotarEntrenador: true })
  })

  it('el entrenador con sesión se identifica por su perfil (la pantalla), no por este servicio', () => {
    expect(servicio.identidadEntrenador()).toBeNull()
  })

  it('editar un extra no llama a la API: avisa que no está disponible', async () => {
    expect(await fallo(servicio.editarExtra('x', {}))).toMatchObject({ codigo: 'NO_DISPONIBLE' })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
})

describe('listarSemana en modo API', () => {
  it('pide GET /horario-semana?fecha=<lunes> y arma la semana tal cual: franjas, canceladas y extras', async () => {
    api.apiRequest.mockResolvedValue({
      desde: '2026-11-02', hasta: '2026-11-07',
      sesiones: [
        sesion(),
        sesion({ idFranja: 'b', fecha: '2026-11-03', dia: 2, estado: 'CANCELADO', idExcepcion: EXC, motivo: 'Lluvia', idEntrenador: null, entrenador: null, entrenadorActivo: null, puedeGestionar: false }),
        sesion({ tipo: 'EXTRA', idFranja: null, idExcepcion: 'x1', fecha: '2026-11-04', dia: 3, grupo: 'tarde' }),
      ],
    })
    const sesiones = await servicio.listarSemana('2026-11-02')
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-semana?fecha=2026-11-02')
    expect(sesiones.map((s) => [s.clave, s.tipo, s.estado, s.motivo, s.hora, s.puedeGestionar])).toEqual([
      ['f-a-2026-11-02', 'FRANJA', 'PROGRAMADO', null, '19:00', true],
      ['f-b-2026-11-03', 'FRANJA', 'CANCELADO', 'Lluvia', '19:00', false],
      ['x-x1', 'EXTRA', 'PROGRAMADO', null, '19:00', true],
    ])
    expect(sesiones[1]).toMatchObject({ idExcepcion: EXC, idEntrenador: null, entrenador: null })
    expect(sesiones[2]).toMatchObject({ idFranja: null, idExcepcion: 'x1', grupo: 'tarde', idCategoria: CAT })
  })

  it('una semana sin sesiones es una lista vacía', async () => {
    api.apiRequest.mockResolvedValue({ desde: '2026-11-02', hasta: '2026-11-07', sesiones: [] })
    expect(await servicio.listarSemana('2026-11-02')).toEqual([])
  })

  it('propaga el error de la API', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 403, mensaje: 'No tienes permiso.' }))
    expect(await fallo(servicio.listarSemana('2026-11-02'))).toMatchObject({ estado: 403 })
  })
})

describe('cancelar y reactivar en modo API', () => {
  it('POST /cancelaciones con { fecha, idFranja, motivo } y devuelve la sesión cancelada y el aviso', async () => {
    api.apiRequest.mockResolvedValue({ sesion: sesion({ estado: 'CANCELADO', idExcepcion: EXC, motivo: 'Lluvia' }), avisoEnviado: false })
    const resultado = await servicio.cancelar({ fecha: '2026-11-02', idFranja: 'a', motivo: ' Lluvia ' })
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect([ruta, opciones.method]).toEqual(['/horario-semana/cancelaciones', 'POST'])
    expect(JSON.parse(opciones.body)).toEqual({ fecha: '2026-11-02', idFranja: 'a', motivo: 'Lluvia' })
    expect(resultado).toMatchObject({ avisoEnviado: false, sesion: { estado: 'CANCELADO', idExcepcion: EXC } })
  })

  it('sin motivo manda null', async () => {
    api.apiRequest.mockResolvedValue({ sesion: sesion({ estado: 'CANCELADO' }), avisoEnviado: null })
    await servicio.cancelar({ fecha: '2026-11-02', idFranja: 'a', motivo: '  ' })
    expect(JSON.parse(api.apiRequest.mock.calls[0][1].body).motivo).toBeNull()
  })

  it('el 409 (ya cancelada) y el 400 llegan con el mensaje del backend', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 409, mensaje: 'Esa franja ya está cancelada en esa fecha' }))
    expect(await fallo(servicio.cancelar({ fecha: '2026-11-02', idFranja: 'a' }))).toMatchObject({ estado: 409, mensaje: 'Esa franja ya está cancelada en esa fecha' })
  })

  it('DELETE /cancelaciones/{id} deshace la cancelación y devuelve la sesión activa', async () => {
    api.apiRequest.mockResolvedValue({ sesion: sesion(), avisoEnviado: true })
    const resultado = await servicio.reactivar(EXC)
    expect(api.apiRequest).toHaveBeenCalledWith(`/horario-semana/cancelaciones/${EXC}`, { method: 'DELETE' })
    expect(resultado).toMatchObject({ avisoEnviado: true, sesion: { estado: 'PROGRAMADO', idExcepcion: null } })
  })
})

describe('extras en modo API', () => {
  const datos = { categoria: 'Sub-12', fecha: '2026-11-10', hora: '18:00:00', horaFin: '19:30', sede: 'Horizontes', idEntrenador: '', grupo: '', motivo: 'Amistoso' }
  const responder = (extra) => api.apiRequest.mockImplementation(async (ruta, opciones) => {
    if (ruta === '/horario-club') return [dto('a')]
    return extra(ruta, opciones)
  })

  it('POST /extras traduce la categoría a su id, manda HH:mm y null por turnos', async () => {
    responder(async () => ({ sesion: sesion({ tipo: 'EXTRA', idFranja: null, idExcepcion: 'x1', fecha: '2026-11-10' }), avisoEnviado: true }))
    const resultado = await servicio.crearExtra(datos)
    const [ruta, opciones] = api.apiRequest.mock.calls.find(([r]) => r === '/horario-semana/extras')
    expect(opciones.method).toBe('POST')
    expect(JSON.parse(opciones.body)).toEqual({ idCategoria: CAT, fecha: '2026-11-10', horaInicio: '18:00', horaFin: '19:30', sede: 'Horizontes', idEntrenador: null, grupo: null, motivo: 'Amistoso' })
    expect(ruta).toBe('/horario-semana/extras')
    expect(resultado).toMatchObject({ avisoEnviado: true, sesion: { tipo: 'EXTRA', idExcepcion: 'x1' } })
  })

  it('el 409 de cruce del entrenador se marca en el campo Entrenador con el mensaje del backend', async () => {
    const mensaje = 'Ese entrenador ya tiene otra franja que se cruza con este horario'
    responder(async () => { throw Object.assign(new Error(mensaje), { estado: 409, mensaje, campos: [] }) })
    expect(await fallo(servicio.crearExtra({ ...datos, idEntrenador: ENT }))).toMatchObject({ estado: 409, errores: { idEntrenador: mensaje } })
  })

  it('el 400 por campos usa los nombres del formulario (idCategoria -> categoria, horaInicio -> hora)', async () => {
    responder(async () => { throw Object.assign(new Error('x'), { estado: 400, mensaje: 'horaInicio: hora inválida; sede: sede inválida', campos: ['horaInicio', 'sede'] }) })
    expect(await fallo(servicio.crearExtra(datos))).toMatchObject({ errores: { hora: 'Hora inválida.', sede: 'Sede inválida.' } })
  })

  it('un campo que el formulario no tiene deja solo el mensaje del backend', async () => {
    responder(async () => { throw Object.assign(new Error('x'), { estado: 400, mensaje: 'otro: raro', campos: ['otro'] }) })
    expect((await fallo(servicio.crearExtra(datos))).errores).toBeUndefined()
  })

  it('una categoría que no está en el club responde NO_EXISTE sin hacer POST', async () => {
    responder(async () => ({}))
    expect(await fallo(servicio.crearExtra({ ...datos, categoria: 'Sub-99' }))).toMatchObject({ codigo: 'NO_EXISTE', estado: 404 })
    expect(api.apiRequest.mock.calls.some(([r]) => r === '/horario-semana/extras')).toBe(false)
  })

  it('DELETE /extras/{id} elimina el extra (sesion null) y devuelve el aviso', async () => {
    api.apiRequest.mockResolvedValue({ sesion: null, avisoEnviado: null })
    const resultado = await servicio.eliminarExtra('x1')
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-semana/extras/x1', { method: 'DELETE' })
    expect(resultado).toEqual({ sesion: null, avisoEnviado: null })
  })

  it('el cruce lo decide el servidor: detectarConflicto no consulta nada', async () => {
    expect(await servicio.detectarConflicto(datos)).toEqual({ hayConflicto: false })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('categoriasDelClub sale de las franjas, sin repetir', async () => {
    api.apiRequest.mockResolvedValue([dto('a'), dto('b', { dia: 2 })])
    expect(await servicio.categoriasDelClub()).toEqual([{ id: CAT, nombre: 'Sub-12' }])
  })
})

describe('cambiarEntrenadorDeFranja en modo API', () => {
  const NUEVO = 'e0a1e0a1-0000-4000-8000-000000000002'

  it('hace PUT con los siete campos tal como están y cambia solo idEntrenador', async () => {
    api.apiRequest.mockImplementation(async (ruta, opciones) => (opciones?.method === 'PUT' ? dto('a', { idEntrenador: NUEVO }) : [dto('a', { grupo: 'tarde' })]))
    await servicio.cambiarEntrenadorDeFranja('a', NUEVO)
    const [ruta, opciones] = api.apiRequest.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(ruta).toBe('/horario-club/a')
    expect(JSON.parse(opciones.body)).toEqual({ idCategoria: CAT, dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: NUEVO, grupo: 'tarde' })
  })

  it('"por turnos" manda idEntrenador null', async () => {
    api.apiRequest.mockImplementation(async (ruta, opciones) => (opciones?.method === 'PUT' ? dto('a', { idEntrenador: null }) : [dto('a')]))
    await servicio.cambiarEntrenadorDeFranja('a', null)
    const [, opciones] = api.apiRequest.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(JSON.parse(opciones.body).idEntrenador).toBeNull()
  })

  it('el 409 de cruce llega con el mensaje del backend', async () => {
    const mensaje = 'Ese entrenador ya tiene otra franja que se cruza con este horario'
    api.apiRequest.mockImplementation(async (ruta, opciones) => {
      if (opciones?.method === 'PUT') throw Object.assign(new Error(mensaje), { estado: 409, mensaje, campos: ['idEntrenador'] })
      return [dto('a')]
    })
    expect(await fallo(servicio.cambiarEntrenadorDeFranja('a', NUEVO))).toMatchObject({ estado: 409, mensaje, errores: { idEntrenador: mensaje } })
  })

  it('una franja que ya no existe responde NO_EXISTE sin hacer PUT', async () => {
    api.apiRequest.mockResolvedValue([dto('a')])
    expect(await fallo(servicio.cambiarEntrenadorDeFranja('zzz', NUEVO))).toMatchObject({ codigo: 'NO_EXISTE', estado: 404 })
    expect(api.apiRequest.mock.calls.every(([, o]) => o?.method !== 'PUT')).toBe(true)
  })
})
