// Descarga un texto como archivo desde el navegador (Blob + enlace temporal). Sin dependencias.
// conBom: antepone el BOM UTF-8 para que Excel abra bien las tildes en un CSV.
export function descargarTexto(nombreArchivo, contenido, tipo = 'text/plain;charset=utf-8', { conBom = false } = {}) {
  const blob = new Blob(conBom ? ['﻿', contenido] : [contenido], { type: tipo })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.hidden = true
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  // Se libera la URL un poco después del clic: algunos navegadores aún la leen para iniciar la descarga
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// Descarga un Blob que ya se tiene (por ejemplo un PDF que devolvió la API).
export function descargarBlob(nombreArchivo, blob) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  enlace.hidden = true
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
