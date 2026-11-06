// Abre un documento en una pestaña nueva sin dejar que la página abierta controle a la nuestra (noopener).
// La URL firmada dura solo 5 minutos y se pide al tocar "Ver documento", así que hay un `await` entre el clic y la apertura: para que el
// navegador no bloquee la ventana emergente, la pestaña se abre en blanco DENTRO del clic y se le asigna la URL cuando llega.
// `pedirUrl` devuelve la URL (https). Si falla, la pestaña en blanco se cierra y el error sigue su camino.
export async function abrirEnPestanaNueva(pedirUrl) {
  const ventana = window.open('', '_blank')
  if (ventana) ventana.opener = null // equivale a noopener
  try {
    const url = String(await pedirUrl())
    if (!/^https:\/\//i.test(url)) throw Object.assign(new Error('La dirección del documento no es válida.'), { codigo: 'URL_INVALIDA' })
    if (ventana) ventana.location.replace(url)
    else window.open(url, '_blank', 'noopener,noreferrer')
  } catch (error) {
    ventana?.close()
    throw error
  }
}
