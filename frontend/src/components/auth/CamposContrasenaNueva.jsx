import { useState } from 'react'
import Field from '../ui/Field.jsx'
import Icon from '../Icon.jsx'
import { MENSAJES } from '../../utils/validaciones.js'

function BotonVer({ visible, onClick, etiqueta }) {
  return (
    <button
      type="button"
      aria-pressed={visible}
      aria-label={etiqueta}
      onClick={onClick}
      className={`flex min-h-11 min-w-11 items-center justify-center rounded-md border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${visible ? 'bg-app' : 'bg-surface'}`}
    >
      <Icon name={visible ? 'eyeOff' : 'eye'} className="h-5 w-5 shrink-0" />
    </button>
  )
}

// Contraseña nueva y su confirmación, con mostrar/ocultar y la política a la vista. La usan /crear-contrasena y /restablecer.
// valores = { contrasena, confirmar }; errores = { contrasena?, confirmar? }; onCambiar(campo, valor).
export default function CamposContrasenaNueva({ valores, errores, onCambiar }) {
  const [ver, setVer] = useState(false)
  const [verConfirmar, setVerConfirmar] = useState(false)
  return (
    <>
      <Field
        label="Contraseña"
        id="contrasena"
        type={ver ? 'text' : 'password'}
        autoComplete="new-password"
        requerido
        value={valores.contrasena}
        error={errores.contrasena}
        describedBy="ayuda-contrasena"
        onChange={(e) => onCambiar('contrasena', e.target.value)}
        accion={<BotonVer visible={ver} etiqueta="Mostrar contraseña" onClick={() => setVer(!ver)} />}
      />
      <p id="ayuda-contrasena" className="-mt-2 text-small text-secondary">{MENSAJES.contrasenaPolitica}</p>
      <Field
        label="Confirmar contraseña"
        id="confirmar"
        type={verConfirmar ? 'text' : 'password'}
        autoComplete="new-password"
        requerido
        value={valores.confirmar}
        error={errores.confirmar}
        onChange={(e) => onCambiar('confirmar', e.target.value)}
        accion={<BotonVer visible={verConfirmar} etiqueta="Mostrar confirmación de contraseña" onClick={() => setVerConfirmar(!verConfirmar)} />}
      />
    </>
  )
}
