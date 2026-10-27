import NavMenu from './NavMenu.jsx'
import { roleLabels } from '../config/menus.js'

// Menú lateral fijo. Solo se ve desde md.
export default function Sidebar({ role }) {
  return (
    <aside className="hidden w-64 shrink-0 flex-col self-start overflow-y-auto bg-sidebar text-on-dark md:sticky md:top-0 md:flex md:min-h-dvh md:max-h-dvh">
      <div className="px-6 pt-6">
        <p className="text-h3">FutData</p>
        <p className="text-small opacity-70">{roleLabels[role]}</p>
      </div>
      <NavMenu role={role} />
    </aside>
  )
}
