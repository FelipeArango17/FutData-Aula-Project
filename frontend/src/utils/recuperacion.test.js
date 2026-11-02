import { describe, expect, it, vi } from 'vitest'
import {
  MENSAJE_CONTRASENA_IGUAL, MENSAJE_LIMITE, MENSAJE_RECUPERACION_ENVIADA, MENSAJE_RECUPERACION_FALLO, MENSAJE_RESTABLECER_ENLACE_INVALIDO,
  MENSAJE_RESTABLECER_FALLO, MENSAJE_SIN_RED, codigoDeErrorSupabase, decidirRecuperacion, enviarRestablecer, esRecuperacionEnCurso,
  solicitarEnlace, urlEsDeRecuperacion, urlTraeError, validarCorreoRecuperacion,
} from './recuperacion.js'
import { MENSAJES } from './validaciones.js'

const fallo = (codigo) => Object.assign(new Error(codigo), { codigo })

describe('validación del correo (la misma del inicio de sesión)', () => {
  it('obligatorio y con formato', () => {
    expect(validarCorreoRecuperacion('')).toBe(MENSAJES.loginCorreoObligatorio)
    expect(validarCorreoRecuperacion('no-es-correo')).toBe(MENSAJES.loginCorreoFormato)
    expect(validarCorreoRecuperacion(' ana@pradofc.co ')).toBeNull()
  })
})

describe('solicitarEnlace', () => {
  it('correo inválido: error del campo y no llama a Supabase', async () => {
    const solicitar = vi.fn()
    expect(await solicitarEnlace({ correo: 'x', solicitar })).toEqual({ tipo: 'errores', errores: { correo: MENSAJES.loginCorreoFormato } })
    expect(solicitar).not.toHaveBeenCalled()
  })
  it('correo registrado y no registrado dan exactamente el mismo resultado', async () => {
    const igual = await solicitarEnlace({ correo: 'existe@pradofc.co', solicitar: vi.fn().mockResolvedValue(undefined) })
    const otro = await solicitarEnlace({ correo: 'nadie@pradofc.co', solicitar: vi.fn().mockResolvedValue(undefined) })
    expect(otro).toEqual(igual)
    expect(igual).toEqual({ tipo: 'enviado', texto: MENSAJE_RECUPERACION_ENVIADA })
    expect(MENSAJE_RECUPERACION_ENVIADA).toBe('Si el correo está registrado, te enviamos un enlace para crear una contraseña nueva. Revisa también la carpeta de spam.')
  })
  it('límite (429): mensaje de espera y marca de límite', async () => {
    const r = await solicitarEnlace({ correo: 'a@b.co', solicitar: vi.fn().mockRejectedValue(fallo('LIMITE')) })
    expect(r).toEqual({ tipo: 'fallo', texto: MENSAJE_LIMITE, limite: true })
    expect(MENSAJE_LIMITE).toBe('Hiciste muchos intentos. Espera un minuto e inténtalo de nuevo.')
  })
  it('otros errores: mensaje genérico sin detalles técnicos', async () => {
    for (const codigo of ['RED', 'DESCONOCIDO']) {
      expect(await solicitarEnlace({ correo: 'a@b.co', solicitar: vi.fn().mockRejectedValue(fallo(codigo)) })).toEqual({ tipo: 'fallo', texto: MENSAJE_RECUPERACION_FALLO, limite: false })
    }
  })
})

describe('codigoDeErrorSupabase', () => {
  it.each([
    [{ status: 429 }, 'LIMITE'],
    [{ code: 'over_email_send_rate_limit', status: 429 }, 'LIMITE'],
    [{ code: 'over_email_send_rate_limit' }, 'LIMITE'],
    [{ code: 'weak_password', status: 422 }, 'POLITICA'],
    [{ code: 'same_password', status: 422 }, 'IGUAL_ANTERIOR'],
    [{ message: 'New password should be different from the old password.', status: 422 }, 'IGUAL_ANTERIOR'],
    [{ name: 'AuthSessionMissingError', status: 400 }, 'ENLACE_INVALIDO'],
    [{ code: 'session_not_found', status: 403 }, 'ENLACE_INVALIDO'],
    [{ status: 401 }, 'ENLACE_INVALIDO'],
    [{ name: 'AuthRetryableFetchError', status: 0 }, 'RED'],
    [{ status: 503 }, 'RED'],
    [{ status: 400, code: 'otro' }, 'DESCONOCIDO'],
    [new TypeError('x'), 'RED'],
  ])('%j -> %s', (error, esperado) => {
    expect(codigoDeErrorSupabase(error)).toBe(esperado)
  })
})

describe('qué hay en la URL del enlace', () => {
  it('detecta errores en el hash (otp_expired, access_denied) y en la query', () => {
    expect(urlTraeError({ hash: '#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid' })).toBe(true)
    expect(urlTraeError({ search: '?error_code=otp_expired' })).toBe(true)
    expect(urlTraeError({ hash: '#access_token=a&type=recovery' })).toBe(false)
    expect(urlTraeError({})).toBe(false)
  })
  it('reconoce type=recovery', () => {
    expect(urlEsDeRecuperacion({ hash: '#access_token=a&refresh_token=b&type=recovery' })).toBe(true)
    expect(urlEsDeRecuperacion({ hash: '#access_token=a&type=signup' })).toBe(false)
    expect(urlEsDeRecuperacion({ hash: '', search: '' })).toBe(false)
  })
})

describe('decidirRecuperacion', () => {
  const recuperacion = { hash: '#access_token=a&type=recovery' }
  it('enlace con sesión de recuperación: listo', () => {
    expect(decidirRecuperacion({ url: recuperacion, haySesion: true })).toBe('listo')
  })
  it('llegó el evento PASSWORD_RECOVERY (flujo con code): listo', () => {
    expect(decidirRecuperacion({ url: { search: '?code=x' }, evento: 'PASSWORD_RECOVERY', haySesion: true })).toBe('listo')
  })
  it('enlace vencido o denegado: inválido de inmediato, aunque haya sesión', () => {
    expect(decidirRecuperacion({ url: { hash: '#error=access_denied&error_code=otp_expired' }, haySesion: true })).toBe('invalido')
  })
  it('una sesión normal (sin enlace de recuperación) no sirve para cambiar la contraseña', () => {
    expect(decidirRecuperacion({ url: {}, haySesion: true })).toBe('verificando')
    expect(decidirRecuperacion({ url: {}, haySesion: true, agotado: true })).toBe('invalido')
  })
  it('sin sesión: verificando y, al agotarse el tiempo, inválido', () => {
    expect(decidirRecuperacion({ url: recuperacion, haySesion: false })).toBe('verificando')
    expect(decidirRecuperacion({ url: recuperacion, haySesion: false, agotado: true })).toBe('invalido')
  })
})

describe('esRecuperacionEnCurso (no restaurar la sesión como ingreso)', () => {
  it('en /restablecer, con enlace de recuperación o con la marca explícita', () => {
    expect(esRecuperacionEnCurso({ pathname: '/restablecer', url: {} })).toBe(true)
    expect(esRecuperacionEnCurso({ pathname: '/', url: { hash: '#type=recovery&access_token=a' } })).toBe(true)
    expect(esRecuperacionEnCurso({ pathname: '/login', url: {}, marcada: true })).toBe(true)
  })
  it('en cualquier otro caso es un ingreso normal', () => {
    expect(esRecuperacionEnCurso({ pathname: '/login', url: {} })).toBe(false)
    expect(esRecuperacionEnCurso({ pathname: undefined, url: {} })).toBe(false)
  })
})

describe('enviarRestablecer', () => {
  const buenos = { contrasena: 'Clave2026x', confirmar: 'Clave2026x' }
  it('política incumplida o no coinciden: errores y no llama a Supabase', async () => {
    const restablecer = vi.fn()
    expect((await enviarRestablecer({ valores: { contrasena: 'corta1', confirmar: 'corta1' }, restablecer })).errores.contrasena).toBe(MENSAJES.contrasenaPolitica)
    expect((await enviarRestablecer({ valores: { contrasena: 'Clave2026x', confirmar: 'Clave2026y' }, restablecer })).errores.confirmar).toBe(MENSAJES.contrasenaNoCoincide)
    expect(restablecer).not.toHaveBeenCalled()
  })
  it('éxito: envía la contraseña nueva', async () => {
    const restablecer = vi.fn().mockResolvedValue(undefined)
    expect(await enviarRestablecer({ valores: buenos, restablecer })).toEqual({ tipo: 'listo' })
    expect(restablecer).toHaveBeenCalledWith('Clave2026x')
  })
  it('Supabase rechaza por política o por igual a la anterior: error en el campo', async () => {
    expect(await enviarRestablecer({ valores: buenos, restablecer: vi.fn().mockRejectedValue(fallo('POLITICA')) })).toEqual({ tipo: 'errores', errores: { contrasena: MENSAJES.contrasenaPolitica } })
    expect(await enviarRestablecer({ valores: buenos, restablecer: vi.fn().mockRejectedValue(fallo('IGUAL_ANTERIOR')) })).toEqual({ tipo: 'errores', errores: { contrasena: MENSAJE_CONTRASENA_IGUAL } })
  })
  it('enlace vencido durante el envío, límite, red y otros', async () => {
    const con = (codigo) => enviarRestablecer({ valores: buenos, restablecer: vi.fn().mockRejectedValue(fallo(codigo)) })
    expect(await con('ENLACE_INVALIDO')).toMatchObject({ tipo: 'fallo', texto: MENSAJE_RESTABLECER_ENLACE_INVALIDO, enlaceInvalido: true })
    expect(await con('LIMITE')).toMatchObject({ texto: MENSAJE_LIMITE, enlaceInvalido: false })
    expect(await con('RED')).toMatchObject({ texto: MENSAJE_SIN_RED })
    expect(await con('DESCONOCIDO')).toMatchObject({ texto: MENSAJE_RESTABLECER_FALLO })
  })
})
