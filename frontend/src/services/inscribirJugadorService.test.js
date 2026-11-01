import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Los servicios guardan los datos en memoria a nivel de módulo: cada prueba carga copias nuevas.
let inscribir
let deportistas
let acudientes
let documentos
let fotos
beforeEach(async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
  vi.resetModules()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:foto-de-prueba', revokeObjectURL: () => {} }))
  inscribir = await import('./inscribirJugadorService.js')
  deportistas = await import('./deportistasService.js')
  acudientes = await import('./acudientesService.js')
  documentos = await import('./documentosService.js')
  fotos = await import('./fotosService.js')
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const archivo = (extra = {}) => ({ name: 'doc.pdf', type: 'application/pdf', size: 100 * 1024, ...extra })
const jugador = (extra = {}) => ({
  nombre: 'Lucas', apellido: 'Prueba Ejemplo', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: '2017-05-10',
  telefono: '3000000099', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: 'tarde', posicion: 'Portero', ...extra,
})
const acudiente = (extra = {}) => ({ nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com', telefono: '3001234567', parentesco: 'Madre', ...extra })
const datos = (extra = {}) => ({ deportista: jugador(), acudiente: acudiente(), documentos: [], foto: null, ...extra })

describe('inscribirJugador: lo que crea', () => {
  it('crea el deportista con la categoría que da su fecha de nacimiento, con grupo y posición', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos(), admin))
    expect(r.categoria).toBe('Semillero')
    expect(r.deportista).toMatchObject({ id: 22, nombre: 'Lucas', grupoEntrenamiento: 'tarde', posicion: 'Portero', activo: true, creadoPor: 'administrador@pradofc.co' })
    const categorias = await resolver(deportistas.listarCategorias())
    expect(categorias.find((c) => c.id === r.deportista.idCategoria).nombre).toBe('Semillero')
    expect((await resolver(deportistas.obtenerDeportista(22))).idCategoria).toBe(r.deportista.idCategoria)
  })

  it('en una categoría sin grupo (2012 = Sub-14) el grupo no se guarda', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos({ deportista: jugador({ fechaNacimiento: '2012-05-10', grupoEntrenamiento: '' }) }), admin))
    expect(r.categoria).toBe('Sub-14')
    expect(r.deportista.grupoEntrenamiento).toBeNull()
  })

  it('guarda al acudiente y lo vincula con el jugador (con el parentesco del vínculo)', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos(), admin))
    expect(r.acudiente).toMatchObject({ nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com' })
    expect(r.vinculo).toEqual({ idDeportista: 22, idAcudiente: r.acudiente.id, parentesco: 'Madre' })
    expect(await resolver(acudientes.obtenerAcudienteDe(22))).toMatchObject({ correo: 'claudia.ruiz@correo.com', parentesco: 'Madre' })
    expect((await resolver(deportistas.obtenerDeportista(22))).acudiente).toBe('claudia.ruiz@correo.com')
  })

  it('si el correo ya es de un acudiente, se reutiliza (un acudiente con varios jugadores)', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos({ acudiente: acudiente({ correo: 'ACUDIENTE@pradofc.co', nombre: 'Otro' }) }), admin))
    expect(r.acudiente).toMatchObject({ id: 1, nombre: 'María' })
    expect(await resolver(acudientes.listarAcudientes())).toHaveLength(1)
  })

  it('registra los documentos con cargadoPor "Administrador" y la foto con fotosService', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos({
      documentos: [{ tipo: 'DOCUMENTO_IDENTIDAD', archivo: archivo() }, { tipo: 'PAZ_Y_SALVO', archivo: archivo({ name: 'paz.jpg', type: 'image/jpeg' }) }],
      foto: archivo({ name: 'foto.png', type: 'image/png' }),
    }), admin))
    expect(r.documentos).toHaveLength(2)
    expect(r.foto).toBe(true)
    const guardados = await resolver(documentos.listarDocumentos(22))
    expect(guardados.map((d) => d.tipo).sort()).toEqual(['DOCUMENTO_IDENTIDAD', 'PAZ_Y_SALVO'])
    expect(guardados.every((d) => d.cargadoPor === 'Administrador' && d.estado === 'PENDIENTE')).toBe(true)
    expect(await resolver(fotos.obtenerFoto(22))).toBe('blob:foto-de-prueba')
  })

  it('sin documentos ni foto también se inscribe (el paso 3 es opcional)', async () => {
    const r = await resolver(inscribir.inscribirJugador(datos(), admin))
    expect(r).toMatchObject({ documentos: [], foto: false })
    expect(await resolver(documentos.listarDocumentos(22))).toEqual([])
  })
})

describe('inscribirJugador: errores (no deja nada guardado)', () => {
  const sinCambios = async () => {
    expect(await resolver(deportistas.listarDeportistas())).toHaveLength(21)
    expect(await resolver(acudientes.listarAcudientes())).toHaveLength(1)
  }

  it('documento duplicado: DOCUMENTO_DUPLICADO en el paso 1', async () => {
    const falla = await resolver(inscribir.inscribirJugador(datos({ deportista: jugador({ documento: '1100000001' }) }), admin)).catch((e) => e)
    expect(falla).toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO', paso: 1, errores: { documento: expect.stringContaining('Ya existe') } })
    await sinCambios()
  })

  it('categoría fuera de rango (2009 y 2023): VALIDACION en el paso 1', async () => {
    for (const fechaNacimiento of ['2009-12-31', '2023-01-01']) {
      const falla = await resolver(inscribir.inscribirJugador(datos({ deportista: jugador({ fechaNacimiento }) }), admin)).catch((e) => e)
      expect(falla, fechaNacimiento).toMatchObject({ codigo: 'VALIDACION', paso: 1, errores: { fechaNacimiento: expect.stringContaining('nacidos entre 2010 y 2022') } })
    }
    await sinCambios()
  })

  it('datos del acudiente inválidos: VALIDACION en el paso 2, y nada se crea', async () => {
    const falla = await resolver(inscribir.inscribirJugador(datos({ acudiente: acudiente({ correo: 'mal', parentesco: '' }) }), admin)).catch((e) => e)
    expect(falla).toMatchObject({ codigo: 'VALIDACION', paso: 2, errores: { correo: expect.any(String), parentesco: expect.any(String) } })
    await sinCambios()
  })

  it('archivo inválido (GIF, vacío, de más de 5 MB) o tipo que no existe: VALIDACION en el paso 3 y nada se crea', async () => {
    for (const adjunto of [{ tipo: 'PAZ_Y_SALVO', archivo: archivo({ type: 'image/gif' }) }, { tipo: 'PAZ_Y_SALVO', archivo: archivo({ size: 0 }) }, { tipo: 'PAZ_Y_SALVO', archivo: archivo({ size: 6 * 1024 * 1024 }) }, { tipo: 'FICHA_MEDICA', archivo: archivo() }]) {
      const falla = await resolver(inscribir.inscribirJugador(datos({ documentos: [adjunto] }), admin)).catch((e) => e)
      expect(falla, adjunto.tipo + adjunto.archivo.type).toMatchObject({ codigo: 'VALIDACION', paso: 3 })
      expect(Object.keys(falla.errores)).toEqual([`documento-${adjunto.tipo}`])
    }
    const falla = await resolver(inscribir.inscribirJugador(datos({ foto: archivo({ type: 'application/pdf' }) }), admin)).catch((e) => e)
    expect(falla).toMatchObject({ codigo: 'VALIDACION', paso: 3, errores: { foto: expect.stringContaining('JPG o PNG') } })
    await sinCambios()
  })

  it('solo el administrador inscribe', async () => {
    for (const actor of [{ rol: 'acudiente', correo: 'acudiente@pradofc.co' }, { rol: 'entrenador' }, null]) {
      await expect(resolver(inscribir.inscribirJugador(datos(), actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
    await sinCambios()
  })
})
