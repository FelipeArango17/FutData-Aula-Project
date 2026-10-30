import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_ACCESIBILIDAD,
  POR_DEFECTO,
  aplicarPreferencias,
  guardarPreferencias,
  leerPreferencias,
  normalizarPreferencias,
} from './preferenciasAccesibilidad.js'

// localStorage se reemplaza por uno falso (el de Node no sirve en Vitest); la raíz del documento también.
function almacenFalso(inicial = {}) {
  const datos = new Map(Object.entries(inicial))
  return {
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => void datos.set(k, String(v)),
    removeItem: (k) => void datos.delete(k),
  }
}

function raizFalsa() {
  const atributos = new Map()
  return {
    atributos,
    style: {},
    setAttribute: (k, v) => void atributos.set(k, v),
    removeAttribute: (k) => void atributos.delete(k),
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('leerPreferencias', () => {
  it('sin dato guardado devuelve los valores por defecto', () => {
    vi.stubGlobal('localStorage', almacenFalso())
    expect(leerPreferencias()).toEqual(POR_DEFECTO)
  })

  it('lee un dato válido', () => {
    vi.stubGlobal('localStorage', almacenFalso({ [CLAVE_ACCESIBILIDAD]: JSON.stringify({ tema: 'oscuro', escala: 150 }) }))
    expect(leerPreferencias()).toEqual({ tema: 'oscuro', escala: 150 })
  })

  it('JSON dañado vuelve a los valores por defecto', () => {
    vi.stubGlobal('localStorage', almacenFalso({ [CLAVE_ACCESIBILIDAD]: '{tema:' }))
    expect(leerPreferencias()).toEqual(POR_DEFECTO)
  })

  it('un campo inválido vuelve a su valor por defecto y conserva el otro', () => {
    vi.stubGlobal('localStorage', almacenFalso({ [CLAVE_ACCESIBILIDAD]: JSON.stringify({ tema: 'rosado', escala: 200 }) }))
    expect(leerPreferencias()).toEqual({ tema: 'normal', escala: 200 })
    vi.stubGlobal('localStorage', almacenFalso({ [CLAVE_ACCESIBILIDAD]: JSON.stringify({ tema: 'oscuro', escala: 175 }) }))
    expect(leerPreferencias()).toEqual({ tema: 'oscuro', escala: 100 })
  })

  it('valores que no son objeto (null, número, texto) vuelven a los valores por defecto', () => {
    for (const crudo of ['null', '7', '"hola"', '[]']) {
      vi.stubGlobal('localStorage', almacenFalso({ [CLAVE_ACCESIBILIDAD]: crudo }))
      expect(leerPreferencias()).toEqual(POR_DEFECTO)
    }
  })

  it('si localStorage lanza error, devuelve los valores por defecto', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('bloqueado') } })
    expect(leerPreferencias()).toEqual(POR_DEFECTO)
  })
})

describe('guardarPreferencias', () => {
  let almacen
  beforeEach(() => {
    almacen = almacenFalso()
    vi.stubGlobal('localStorage', almacen)
  })

  it('guarda y se puede volver a leer', () => {
    guardarPreferencias({ tema: 'alto-contraste', escala: 125 })
    expect(JSON.parse(almacen.getItem(CLAVE_ACCESIBILIDAD))).toEqual({ tema: 'alto-contraste', escala: 125 })
    expect(leerPreferencias()).toEqual({ tema: 'alto-contraste', escala: 125 })
  })

  it('no guarda valores inválidos', () => {
    guardarPreferencias({ tema: 'x', escala: 3 })
    expect(JSON.parse(almacen.getItem(CLAVE_ACCESIBILIDAD))).toEqual(POR_DEFECTO)
  })

  it('si localStorage lanza error, no rompe', () => {
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('lleno') } })
    expect(() => guardarPreferencias({ tema: 'oscuro', escala: 200 })).not.toThrow()
  })
})

describe('aplicarPreferencias', () => {
  it('normal: sin data-theme, 100 % y esquema claro', () => {
    const raiz = raizFalsa()
    raiz.setAttribute('data-theme', 'oscuro')
    aplicarPreferencias({ tema: 'normal', escala: 100 }, raiz)
    expect(raiz.atributos.has('data-theme')).toBe(false)
    expect(raiz.style.fontSize).toBe('100%')
    expect(raiz.style.colorScheme).toBe('light')
  })

  it('alto contraste: pone data-theme, esquema claro y el tamaño en porcentaje (no en px)', () => {
    const raiz = raizFalsa()
    aplicarPreferencias({ tema: 'alto-contraste', escala: 150 }, raiz)
    expect(raiz.atributos.get('data-theme')).toBe('alto-contraste')
    expect(raiz.style.fontSize).toBe('150%')
    expect(raiz.style.colorScheme).toBe('light')
  })

  it('oscuro: esquema oscuro', () => {
    const raiz = raizFalsa()
    aplicarPreferencias({ tema: 'oscuro', escala: 200 }, raiz)
    expect(raiz.atributos.get('data-theme')).toBe('oscuro')
    expect(raiz.style.colorScheme).toBe('dark')
    expect(raiz.style.fontSize).toBe('200%')
  })

  it('un valor inválido se aplica como el valor por defecto', () => {
    const raiz = raizFalsa()
    aplicarPreferencias({ tema: 'x', escala: 99 }, raiz)
    expect(raiz.atributos.has('data-theme')).toBe(false)
    expect(raiz.style.fontSize).toBe('100%')
  })
})

describe('normalizarPreferencias', () => {
  it('es idempotente', () => {
    const una = normalizarPreferencias({ tema: 'oscuro', escala: 125 })
    expect(normalizarPreferencias(una)).toEqual(una)
  })
})
