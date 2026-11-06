import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
  vi.resetModules()
  servicio = await import('./documentosService.js')
})
afterEach(() => vi.useRealTimers())

const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const otroAdmin = { rol: 'administrador', correo: 'otro.admin@pradofc.co' }
const archivo = (extra = {}) => ({ name: 'ficha.pdf', type: 'application/pdf', size: 100 * 1024, ...extra })
const datos = (extra = {}) => ({ tipo: 'CERTIFICADO_ESTUDIO', archivo: archivo(), ...extra })

describe('listarDocumentos', () => {
  it('trae los de ejemplo de cada deportista y vacío para los demás', async () => {
    expect(((await resolver(servicio.listarDocumentos(1))).documentos).map((x) => x.estado).sort()).toEqual(['APROBADO', 'APROBADO', 'PENDIENTE'])
    const valeria = (await resolver(servicio.listarDocumentos('2'))).documentos
    expect(valeria).toHaveLength(2)
    expect(valeria.find((x) => x.estado === 'RECHAZADO')).toMatchObject({ tipo: 'DOCUMENTO_IDENTIDAD', comentario: expect.stringContaining('borrosa') })
    expect((await resolver(servicio.listarDocumentos(6))).documentos).toHaveLength(5)
    expect((await resolver(servicio.listarDocumentos(3))).documentos).toEqual([])
  })

  it('devuelve copias: cambiarlas no cambia el servicio', async () => {
    const rechazado = ((await resolver(servicio.listarDocumentos(2))).documentos).find((x) => x.estado === 'RECHAZADO')
    rechazado.estado = 'APROBADO'
    expect(((await resolver(servicio.listarDocumentos(2))).documentos).find((x) => x.id === rechazado.id).estado).toBe('RECHAZADO')
  })

  it('los datos de ejemplo cumplen los límites del backend', async () => {
    const todos = (await Promise.all([1, 2, 6].map((id) => resolver(servicio.listarDocumentos(id))))).flatMap((r) => r.documentos)
    for (const x of todos) {
      expect(servicio.TIPOS_DOCUMENTO_JUGADOR).toContain(x.tipo)
      expect(['application/pdf', 'image/jpeg', 'image/png']).toContain(x.tipoContenido)
      expect(x.tamanoBytes).toBeGreaterThan(0)
      expect(x.tamanoBytes).toBeLessThanOrEqual(5 * 1024 * 1024)
    }
  })
})

describe('subirDocumento', () => {
  it('sube uno válido: queda PENDIENTE, con quién lo cargó, y aparece primero en la lista', async () => {
    const r = await resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: '2027-01-15' }), admin))
    expect(r).toMatchObject({
      idDeportista: 3, tipo: 'CERTIFICADO_ESTUDIO', nombreOriginal: 'ficha.pdf', tipoContenido: 'application/pdf', tamanoBytes: 102400,
      estado: 'PENDIENTE', comentario: null, fechaVencimiento: '2027-01-15', cargadoPor: 'Administrador', cargadoPorCorreo: 'administrador@pradofc.co', revisadoPor: null, revisadoEn: null,
    })
    expect(Number.isNaN(Date.parse(r.cargadoEn))).toBe(false)
    expect(((await resolver(servicio.listarDocumentos(3))).documentos)[0].id).toBe(r.id)
  })

  it('el vencimiento es opcional y la fecha de hoy se permite', async () => {
    expect((await resolver(servicio.subirDocumento(3, datos(), admin))).fechaVencimiento).toBeNull()
    expect((await resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: '' }), admin))).fechaVencimiento).toBeNull()
    expect((await resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: '2026-10-22' }), admin))).fechaVencimiento).toBe('2026-10-22')
  })

  it('acepta JPG y PNG, no GIF', async () => {
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ name: 'a.jpg', type: 'image/jpeg' }) }), admin))).resolves.toBeTruthy()
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ name: 'a.png', type: 'image/png' }) }), admin))).resolves.toBeTruthy()
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ name: 'a.gif', type: 'image/gif' }) }), admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
  })

  it('tipo de documento inválido o vacío: VALIDACION por campo y no guarda nada', async () => {
    for (const tipo of ['', 'CEDULA', undefined]) {
      await expect(resolver(servicio.subirDocumento(3, datos({ tipo }), admin))).rejects.toMatchObject({
        codigo: 'VALIDACION', errores: { tipo: 'Selecciona qué tipo de documento es.' },
      })
    }
    expect((await resolver(servicio.listarDocumentos(3))).documentos).toEqual([])
  })

  it('archivo de más de 5 MB: ARCHIVO_INVALIDO con el motivo', async () => {
    const falla = await resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ size: 6 * 1024 * 1024 }) }), admin)).catch((e) => e)
    expect(falla).toMatchObject({ codigo: 'ARCHIVO_INVALIDO', message: 'El archivo pesa 6 MB; el máximo es 5 MB.', errores: { archivo: 'El archivo pesa 6 MB; el máximo es 5 MB.' } })
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ size: 5 * 1024 * 1024 }) }), admin))).resolves.toBeTruthy()
  })

  it('sin archivo o archivo vacío: ARCHIVO_INVALIDO', async () => {
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: null }), admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
    await expect(resolver(servicio.subirDocumento(3, datos({ archivo: archivo({ size: 0 }) }), admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
  })

  it('vencimiento pasado (ayer) o inexistente: VALIDACION en ese campo', async () => {
    await expect(resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: '2026-10-21' }), admin))).rejects.toMatchObject({
      codigo: 'VALIDACION', errores: { fechaVencimiento: 'La fecha de vencimiento no puede ser pasada.' },
    })
    await expect(resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: '2027-02-30' }), admin))).rejects.toMatchObject({
      errores: { fechaVencimiento: 'La fecha de vencimiento no es válida.' },
    })
    await expect(resolver(servicio.subirDocumento(3, datos({ fechaVencimiento: 'mañana' }), admin))).rejects.toMatchObject({ codigo: 'VALIDACION' })
  })

  it('varios campos mal: VALIDACION con todos los errores', async () => {
    const falla = await resolver(servicio.subirDocumento(3, { tipo: '', archivo: null, fechaVencimiento: '2020-01-01' }, admin)).catch((e) => e)
    expect(falla.codigo).toBe('VALIDACION')
    expect(Object.keys(falla.errores).sort()).toEqual(['archivo', 'fechaVencimiento', 'tipo'])
  })

  it('suben el administrador y el acudiente del deportista; nadie más', async () => {
    for (const actor of [{ rol: 'entrenador' }, { rol: 'tesorero' }, { rol: 'acudiente' }, { rol: 'acudiente', correo: 'otro@correo.co' }, null]) {
      await expect(resolver(servicio.subirDocumento(3, datos(), actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
    expect((await resolver(servicio.listarDocumentos(3))).documentos).toEqual([])
  })

  it('cargadoPor dice quién subió: "Administrador" o "Acudiente" (solo con sus deportistas), con cualquiera de los 7 tipos', async () => {
    const acudiente = { rol: 'acudiente', correo: 'acudiente@pradofc.co' } // Santiago (1) y Valeria (2)
    for (const tipo of servicio.TIPOS_DOCUMENTO_JUGADOR) {
      expect(await resolver(servicio.subirDocumento(1, datos({ tipo }), acudiente)), tipo).toMatchObject({ cargadoPor: 'Acudiente', cargadoPorCorreo: 'acudiente@pradofc.co', estado: 'PENDIENTE' })
      expect(await resolver(servicio.subirDocumento(1, datos({ tipo }), admin)), tipo).toMatchObject({ cargadoPor: 'Administrador', cargadoPorCorreo: 'administrador@pradofc.co' })
    }
    await expect(resolver(servicio.subirDocumento(3, datos(), acudiente))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' }) // Emilio no es suyo
  })

  it('el acudiente no puede aprobar ni rechazar', async () => {
    await expect(resolver(servicio.aprobarDocumento(2, { rol: 'acudiente', correo: 'acudiente@pradofc.co' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })

  it('los documentos de ejemplo traen cargadoPor, de las dos formas', async () => {
    const todos = (await Promise.all([1, 2, 6].map((id) => resolver(servicio.listarDocumentos(id))))).flatMap((r) => r.documentos)
    expect(new Set(todos.map((x) => x.cargadoPor))).toEqual(new Set(['Administrador', 'Acudiente']))
  })
})

describe('aprobarDocumento y rechazarDocumento', () => {
  it('aprobar cambia el estado, guarda quién y cuándo revisó y devuelve { documento, estadoInscripcion }', async () => {
    const r = await resolver(servicio.aprobarDocumento(2, otroAdmin))
    expect(r.documento).toMatchObject({ id: 2, estado: 'APROBADO', comentario: null, revisadoPor: 'otro.admin@pradofc.co' })
    expect(Number.isNaN(Date.parse(r.documento.revisadoEn))).toBe(false)
    expect(r.estadoInscripcion).toBe('PENDIENTE') // a Santiago aún le falta Habeas Data
    expect((await resolver(servicio.listarDocumentos(1))).documentos.find((x) => x.id === 2).estado).toBe('APROBADO')
  })

  it('rechazar con comentario lo guarda sin espacios en los bordes y devuelve avisoEnviado', async () => {
    const r = await resolver(servicio.rechazarDocumento(2, { comentario: '  Falta la firma del médico ' }, admin))
    expect(r.documento).toMatchObject({ estado: 'RECHAZADO', comentario: 'Falta la firma del médico', revisadoPor: 'administrador@pradofc.co' })
    expect(r).toHaveProperty('avisoEnviado')
    expect(r.estadoInscripcion).toBe('PENDIENTE')
  })

  it('rechazar sin comentario (o solo espacios): VALIDACION en el campo comentario y no cambia nada', async () => {
    for (const comentario of [undefined, '', '   ']) {
      await expect(resolver(servicio.rechazarDocumento(2, { comentario }, admin))).rejects.toMatchObject({
        codigo: 'VALIDACION', errores: { comentario: 'Escribe el motivo del rechazo.' },
      })
    }
    expect((await resolver(servicio.listarDocumentos(1))).documentos.find((x) => x.id === 2)).toMatchObject({ estado: 'PENDIENTE', revisadoPor: null })
  })

  it('el comentario tiene un máximo de 300 caracteres', async () => {
    await expect(resolver(servicio.rechazarDocumento(2, { comentario: 'a'.repeat(301) }, admin))).rejects.toMatchObject({ codigo: 'VALIDACION' })
    await expect(resolver(servicio.rechazarDocumento(2, { comentario: 'a'.repeat(300) }, admin))).resolves.toBeTruthy()
  })

  it('documento inexistente: DOCUMENTO_NO_EXISTE', async () => {
    await expect(resolver(servicio.aprobarDocumento(999, admin))).rejects.toMatchObject({ codigo: 'DOCUMENTO_NO_EXISTE' })
  })

  it('solo el administrador revisa (el permiso se comprueba antes que la existencia)', async () => {
    for (const actor of [{ rol: 'entrenador' }, { rol: 'acudiente', correo: 'acudiente@pradofc.co' }, null]) {
      await expect(resolver(servicio.aprobarDocumento(2, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.rechazarDocumento(2, { comentario: 'x' }, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
    await expect(resolver(servicio.aprobarDocumento(999, { rol: 'entrenador' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })

  it('aprobar el último obligatorio pendiente deja la inscripción COMPLETA', async () => {
    await resolver(servicio.aprobarDocumento(2, admin)) // certificado de Santiago
    await resolver(servicio.subirDocumento(1, datos({ tipo: 'AUTORIZACION_HABEAS_DATA' }), admin))
    const lista = (await resolver(servicio.listarDocumentos(1))).documentos
    const habeas = lista.find((x) => x.tipo === 'AUTORIZACION_HABEAS_DATA')
    expect((await resolver(servicio.aprobarDocumento(habeas.id, admin))).estadoInscripcion).toBe('COMPLETA')
    expect(await resolver(servicio.listarDocumentos(1))).toMatchObject({ estadoInscripcion: 'COMPLETA', documentosFaltantes: [] })
  })
})

describe('listarDocumentos: estadoInscripcion y documentosFaltantes', () => {
  it('solo cuentan los 4 obligatorios aprobados; un "si aplica" aprobado no cambia nada', async () => {
    expect(await resolver(servicio.listarDocumentos(1))).toMatchObject({ estadoInscripcion: 'PENDIENTE', documentosFaltantes: ['CERTIFICADO_ESTUDIO', 'AUTORIZACION_HABEAS_DATA'] })
    expect(await resolver(servicio.listarDocumentos(2))).toMatchObject({ estadoInscripcion: 'PENDIENTE', documentosFaltantes: ['DOCUMENTO_IDENTIDAD', 'CERTIFICADO_ESTUDIO', 'FORMULARIO_INSCRIPCION', 'AUTORIZACION_HABEAS_DATA'] })
    expect(await resolver(servicio.listarDocumentos(6))).toMatchObject({ estadoInscripcion: 'COMPLETA', documentosFaltantes: [] }) // con un paz y salvo pendiente
    await resolver(servicio.aprobarDocumento(10, admin)) // el paz y salvo de Mateo
    expect((await resolver(servicio.listarDocumentos(6))).estadoInscripcion).toBe('COMPLETA')
  })
  it('sin documentos: PENDIENTE y los 4 faltan, en el orden del club', async () => {
    expect(await resolver(servicio.listarDocumentos(3))).toEqual({
      documentos: [], estadoInscripcion: 'PENDIENTE',
      documentosFaltantes: ['DOCUMENTO_IDENTIDAD', 'CERTIFICADO_ESTUDIO', 'FORMULARIO_INSCRIPCION', 'AUTORIZACION_HABEAS_DATA'],
    })
  })
})

describe('tipos de documento del club', () => {
  it('son los 7 que se pueden subir, con los identificadores internos, sin Ficha médica y sin Foto tipo documento (es la foto de perfil)', () => {
    expect(servicio.TIPOS_DOCUMENTO_JUGADOR).toEqual([
      'DOCUMENTO_IDENTIDAD', 'CERTIFICADO_ESTUDIO', 'FORMULARIO_INSCRIPCION',
      'AUTORIZACION_HABEAS_DATA', 'PAZ_Y_SALVO', 'CARTA_RENUNCIA', 'TRASPASO_NACIONAL',
    ])
    expect(servicio.TIPOS_DOCUMENTO_JUGADOR).not.toContain('FICHA_MEDICA')
    expect(servicio.TIPOS_DOCUMENTO_JUGADOR).not.toContain('FOTO_DOCUMENTO')
  })

  it('al subir se acepta cualquiera de los 7 tipos y se rechaza uno viejo o la foto', async () => {
    for (const tipo of servicio.TIPOS_DOCUMENTO_JUGADOR) {
      await expect(resolver(servicio.subirDocumento(3, datos({ tipo }), admin)), tipo).resolves.toMatchObject({ tipo, estado: 'PENDIENTE' })
    }
    for (const tipo of ['FICHA_MEDICA', 'FOTO_DOCUMENTO']) {
      await expect(resolver(servicio.subirDocumento(3, datos({ tipo }), admin)), tipo).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { tipo: expect.any(String) } })
    }
  })
})
