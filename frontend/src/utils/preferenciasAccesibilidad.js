// Preferencias de accesibilidad del EQUIPO (tema de color y tamaño del texto) en localStorage. Funciones puras, sin React,
// para poder probarlas. No dependen de la sesión: cerrar sesión no las borra (ver limpiarSesion en context/sesion.js).
// El script de index.html aplica lo mismo antes de que React pinte; si cambias claves o valores, cámbialo allá también.

export const CLAVE_ACCESIBILIDAD = 'futdata.accesibilidad'
export const TEMAS = ['normal', 'alto-contraste', 'oscuro']
export const ESCALAS = [100, 125, 150, 200]
export const POR_DEFECTO = { tema: 'normal', escala: 100 }

// Texto para pantalla de cada valor (lo usan el panel y su aviso de estado)
export const etiquetasTema = { normal: 'Normal', 'alto-contraste': 'Alto contraste', oscuro: 'Oscuro' }
export const etiquetasEscala = { 100: 'Normal', 125: 'Grande', 150: 'Muy grande', 200: 'Máximo' }

const esquemaDeColor = { normal: 'light', 'alto-contraste': 'light', oscuro: 'dark' }

// Valida un objeto cualquiera: cada campo inválido vuelve a su valor por defecto.
export function normalizarPreferencias(valor) {
  const tema = TEMAS.includes(valor?.tema) ? valor.tema : POR_DEFECTO.tema
  const escala = ESCALAS.includes(valor?.escala) ? valor.escala : POR_DEFECTO.escala
  return { tema, escala }
}

export function leerPreferencias() {
  try {
    return normalizarPreferencias(JSON.parse(localStorage.getItem(CLAVE_ACCESIBILIDAD)))
  } catch {
    // sin almacenamiento o dato dañado
    return { ...POR_DEFECTO }
  }
}

export function guardarPreferencias(prefs) {
  try {
    localStorage.setItem(CLAVE_ACCESIBILIDAD, JSON.stringify(normalizarPreferencias(prefs)))
  } catch {
    // sin almacenamiento: las preferencias valen mientras la página siga abierta
  }
}

// font-size en porcentaje (no px): respeta el tamaño base que la persona eligió en el navegador.
// raiz se puede cambiar para probar sin DOM.
export function aplicarPreferencias(prefs, raiz = document.documentElement) {
  const { tema, escala } = normalizarPreferencias(prefs)
  if (tema === 'normal') raiz.removeAttribute('data-theme')
  else raiz.setAttribute('data-theme', tema)
  raiz.style.colorScheme = esquemaDeColor[tema]
  raiz.style.fontSize = `${escala}%`
}
