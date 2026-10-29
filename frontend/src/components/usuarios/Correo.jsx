// Correo con puntos de corte permitidos antes de "@" y tras ".": con texto muy grande en pantallas angostas se parte en esos
// lugares y no en medio de una palabra.
export default function Correo({ correo }) {
  const partes = correo.split(/(?=@)|(?<=\.)/)
  return (
    <>
      {partes.map((parte, i) => (
        <span key={i}>
          {i > 0 && <wbr />}
          {parte}
        </span>
      ))}
    </>
  )
}
