// Validación de archivos que se suben (foto y documentos del deportista). Funciones puras: sirven con un File real o con
// { name, type, size }. Los límites salen de la tabla `documento` del backend.
export const TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024
export const TIPOS_FOTO = ['image/jpeg', 'image/png']
export const TIPOS_DOCUMENTO = ['application/pdf', 'image/jpeg', 'image/png']

const NOMBRES_TIPO = { 'application/pdf': 'PDF', 'image/jpeg': 'JPG', 'image/png': 'PNG' }

// "2,4 MB", "350 KB", "800 bytes" (coma decimal, y sin ",0" cuando es exacto)
export function formatearTamano(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',').replace(/,0$/, '')} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} ${bytes === 1 ? 'byte' : 'bytes'}`
}

// "JPG o PNG" / "PDF, JPG o PNG"
const listaDeFormatos = (tipos) => {
  const nombres = tipos.map((t) => NOMBRES_TIPO[t] ?? t)
  return nombres.length > 1 ? `${nombres.slice(0, -1).join(', ')} o ${nombres.at(-1)}` : nombres[0]
}

// Devuelve null si el archivo sirve, o el mensaje del primer motivo: sin archivo, tipo, vacío o demasiado grande.
export function validarArchivo(archivo, { tipos = TIPOS_DOCUMENTO, maxBytes = TAMANO_MAXIMO_BYTES } = {}) {
  if (!archivo) return 'Elige un archivo.'
  if (!tipos.includes(archivo.type)) return `Solo se aceptan archivos ${listaDeFormatos(tipos)}.`
  if (!archivo.size) return 'El archivo está vacío.'
  if (archivo.size > maxBytes) {
    // Si al redondear se vería igual que el máximo (por ejemplo 5 MB + 1 byte), se dice en bytes
    const pesa = formatearTamano(archivo.size) === formatearTamano(maxBytes) ? `${archivo.size} bytes` : formatearTamano(archivo.size)
    return `El archivo pesa ${pesa}; el máximo es ${formatearTamano(maxBytes)}.`
  }
  return null
}
