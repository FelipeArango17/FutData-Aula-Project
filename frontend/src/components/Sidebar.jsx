import NavMenu from './NavMenu.jsx'
import { roleLabels } from '../config/menus.js'

// Menú lateral fijo. Solo se ve desde md. min-h-app y max-h-app llevan vh de respaldo para Safari < 15.4.
// w-64 está en rem y crece con la letra (a 200 % son 512 px), pero el breakpoint md es fijo en px: sin tope, a 768 px con
// letra al 200 % el menú se comía dos tercios de la pantalla. md:max-w-[35%] lo limita a una parte del ancho.
export default function Sidebar({ role, accesibilidadRef }) {
  return (
    <aside className="hidden w-64 shrink-0 flex-col self-start overflow-y-auto bg-sidebar text-on-dark md:sticky md:top-0 md:max-w-[35%] md:flex md:min-h-app md:max-h-app">
      <div className="px-6 pt-6">
        <p className="text-h3">FutData</p>
        <p className="text-small texto-atenuado">{roleLabels[role]}</p>
      </div>
      <NavMenu role={role} accesibilidadRef={accesibilidadRef} />
    </aside>
  )
}
