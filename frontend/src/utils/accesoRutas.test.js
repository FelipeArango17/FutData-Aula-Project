import { describe, expect, it } from 'vitest'
import { cambioDeRolPermitido, decidirAcceso, destinoDeAcceso, rutaDeRetorno } from './accesoRutas.js'

const ROLES = ['acudiente', 'entrenador', 'tesorero', 'administrador']

describe('decidirAcceso', () => {
  it('sesión sin restaurar -> cargando, sea cual sea el rol', () => {
    for (const rolUsuario of [null, ...ROLES]) {
      expect(decidirAcceso({ estado: 'restaurando', rolUsuario, rolRuta: 'administrador' })).toBe('cargando')
    }
  })

  it('sin sesión -> login, aunque el rol (por ejemplo uno guardado) coincida', () => {
    for (const rolUsuario of [null, ...ROLES]) {
      for (const rolRuta of ROLES) expect(decidirAcceso({ estado: 'sin-sesion', rolUsuario, rolRuta })).toBe('login')
    }
  })

  it('sesión pendiente del 2FA -> verificar, con cualquier rol', () => {
    for (const rolUsuario of [null, ...ROLES]) {
      for (const rolRuta of ROLES) expect(decidirAcceso({ estado: 'pendiente', rolUsuario, rolRuta })).toBe('verificar')
    }
  })

  it('verificada: permitir si el rol coincide y otro-rol si no (las 16 combinaciones)', () => {
    for (const rolUsuario of ROLES) {
      for (const rolRuta of ROLES) {
        expect(decidirAcceso({ estado: 'verificada', rolUsuario, rolRuta })).toBe(rolUsuario === rolRuta ? 'permitir' : 'otro-rol')
      }
    }
  })

  it('verificada sin rol de usuario no entra a ningún panel', () => {
    expect(decidirAcceso({ estado: 'verificada', rolUsuario: null, rolRuta: 'administrador' })).toBe('otro-rol')
  })

  it('un estado desconocido o ausente se trata como sin sesión', () => {
    expect(decidirAcceso({ estado: 'otra-cosa', rolUsuario: 'administrador', rolRuta: 'administrador' })).toBe('login')
    expect(decidirAcceso({ rolUsuario: 'administrador', rolRuta: 'administrador' })).toBe('login')
  })
})

describe('destinoDeAcceso', () => {
  it('sin sesión va a /login guardando la ruta pedida', () => {
    expect(destinoDeAcceso('login', { rolUsuario: null, desde: '/administrador/entrenadores' })).toEqual({ to: '/login', state: { desde: '/administrador/entrenadores' } })
  })
  it('pendiente va a /verificar guardando la ruta pedida', () => {
    expect(destinoDeAcceso('verificar', { rolUsuario: 'acudiente', desde: '/perfil' })).toEqual({ to: '/verificar', state: { desde: '/perfil' } })
  })
  it('otro rol va al inicio del rol de la persona, no al de la URL', () => {
    expect(destinoDeAcceso('otro-rol', { rolUsuario: 'acudiente', desde: '/administrador/entrenadores' })).toEqual({ to: '/acudiente', state: undefined })
  })
  it('permitir y cargando no redirigen', () => {
    expect(destinoDeAcceso('permitir', { rolUsuario: 'acudiente', desde: '/x' })).toBe(null)
    expect(destinoDeAcceso('cargando', { rolUsuario: null, desde: '/x' })).toBe(null)
  })
})

describe('rutaDeRetorno', () => {
  it('acepta rutas del rol de la persona y /perfil, con búsqueda y ancla', () => {
    expect(rutaDeRetorno('/administrador/entrenadores', 'administrador')).toBe('/administrador/entrenadores')
    expect(rutaDeRetorno('/administrador/deportistas?x=1#a', 'administrador')).toBe('/administrador/deportistas?x=1#a')
    expect(rutaDeRetorno('/perfil', 'tesorero')).toBe('/perfil')
    expect(rutaDeRetorno('/acudiente', 'acudiente')).toBe('/acudiente')
  })
  it('descarta rutas de otro rol, externas o inválidas', () => {
    expect(rutaDeRetorno('/administrador/entrenadores', 'acudiente')).toBe(null)
    expect(rutaDeRetorno('/administradorX/a', 'administrador')).toBe(null)
    expect(rutaDeRetorno('//malo.com', 'administrador')).toBe(null)
    expect(rutaDeRetorno('/\\malo.com', 'administrador')).toBe(null)
    expect(rutaDeRetorno('https://malo.com/administrador/a', 'administrador')).toBe(null)
    expect(rutaDeRetorno(undefined, 'administrador')).toBe(null)
    expect(rutaDeRetorno('/administrador/a', null)).toBe(null)
  })
})

describe('cambioDeRolPermitido', () => {
  it('solo con DEV y VITE_PERMITIR_CAMBIO_ROL === "true"', () => {
    expect(cambioDeRolPermitido({ DEV: true, VITE_PERMITIR_CAMBIO_ROL: 'true' })).toBe(true)
    expect(cambioDeRolPermitido({ DEV: false, VITE_PERMITIR_CAMBIO_ROL: 'true' })).toBe(false)
    expect(cambioDeRolPermitido({ DEV: true })).toBe(false)
    expect(cambioDeRolPermitido({ DEV: true, VITE_PERMITIR_CAMBIO_ROL: 'TRUE' })).toBe(false)
    expect(cambioDeRolPermitido({ DEV: true, VITE_PERMITIR_CAMBIO_ROL: '1' })).toBe(false)
    expect(cambioDeRolPermitido({})).toBe(false)
  })
})
