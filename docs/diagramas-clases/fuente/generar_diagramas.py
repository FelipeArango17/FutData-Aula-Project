#!/usr/bin/env python3
"""Genera los diagramas de clases de FutData (HU-066 / tarea 295) con Graphviz.
Uso: python3 gen_uml.py <carpeta_salida>
"""
import html, os, re, subprocess, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(OUT, exist_ok=True)
FONT = "Inter"

KIND = {  # relleno, borde, etiqueta de leyenda
    "controller": ("#E3F2FD", "#1565C0", "Controlador (api.controllers)"),
    "dto":        ("#FFF8E1", "#F9A825", "DTO / Mapper (api.dto)"),
    "facade":     ("#E8F5E9", "#2E7D32", "Fachada (services.facade / impl)"),
    "rules":      ("#FFEBEE", "#C62828", "Reglas de dominio (domain.rules)"),
    "entity":     ("#ECEFF1", "#37474F", "Entidad (domain.entities)"),
    "dao":        ("#F3E5F5", "#6A1B9A", "DAO (persistence.dao / impl)"),
    "ext":        ("#E0F2F1", "#00695C", "Fuente de datos, servicio externo o util"),
    "seg":        ("#FFF8E1", "#F9A825", "Seguridad y acceso"),
    "nuevo":      ("#FFFFFF", "#C62828", "Entidad nueva (no esta en el PPI 4.2)"),
}


def esc(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"~([^~]*)~", r"&lt;\1&gt;", s)
    return s.replace("\n", "<BR/>")


def C(name, kind, stereo=None, attrs=(), methods=(), rank=0, note=None, dashed=False):
    return dict(name=name, kind=kind, stereo=stereo, attrs=list(attrs),
                methods=list(methods), rank=rank, note=note, dashed=dashed)


def label(c):
    fill, border, _ = KIND[c["kind"]]
    rows = []
    head = ""
    if c["stereo"]:
        head += f'<FONT POINT-SIZE="10" COLOR="#4B5563">&#171;{esc(c["stereo"])}&#187;</FONT><BR/>'
    head += f'<B>{esc(c["name"])}</B>'
    rows.append(f'<TR><TD BGCOLOR="{border}" ALIGN="CENTER"><FONT COLOR="white" POINT-SIZE="1"> </FONT></TD></TR>')
    rows.append(f'<TR><TD ALIGN="CENTER" CELLPADDING="5">{head}</TD></TR>')
    if c["attrs"]:
        a = "".join(f'<TR><TD ALIGN="LEFT" BALIGN="LEFT" CELLPADDING="1"><FONT POINT-SIZE="10">{esc(x)}</FONT></TD></TR>' for x in c["attrs"])
        rows.append(f'<TR><TD ALIGN="LEFT" BORDER="0" CELLPADDING="3"><TABLE BORDER="0" CELLSPACING="0" CELLPADDING="1">{a}</TABLE></TD></TR>')
    if c["note"]:
        rows.append(f'<TR><TD ALIGN="LEFT" CELLPADDING="4"><FONT POINT-SIZE="10" COLOR="#4B5563"><I>{esc(c["note"])}</I></FONT></TD></TR>')
    if c["methods"]:
        m = "".join(f'<TR><TD ALIGN="LEFT" BALIGN="LEFT" CELLPADDING="1"><FONT POINT-SIZE="10">{esc(x)}</FONT></TD></TR>' for x in c["methods"])
        rows.append(f'<TR><TD ALIGN="LEFT" BORDER="0" CELLPADDING="3"><TABLE BORDER="0" CELLSPACING="0" CELLPADDING="1">{m}</TABLE></TD></TR>')
    style = ' STYLE="DASHED"' if c["dashed"] else ""
    return (f'<<TABLE BORDER="2" COLOR="{border}" CELLBORDER="0" CELLSPACING="0" '
            f'CELLPADDING="0" BGCOLOR="{fill}"{style}>' + "".join(rows) + "</TABLE>>")


def legend(kinds):
    cells = "".join(
        f'<TR><TD BGCOLOR="{KIND[k][0]}" BORDER="1" COLOR="{KIND[k][1]}" WIDTH="18" HEIGHT="14"> </TD>'
        f'<TD ALIGN="LEFT"><FONT POINT-SIZE="10">{esc(KIND[k][2])}</FONT></TD></TR>' for k in kinds)
    extra = (
        '<TR><TD ALIGN="LEFT" COLSPAN="2"><FONT POINT-SIZE="10">'
        '- - -&#9655; implementa &#160;&#160; - - -&gt; depende de (usa) &#160;&#160; &#9671;&#8212; agregacion &#160;&#160; &#9670;&#8212; composicion</FONT></TD></TR>')
    return ('<<TABLE BORDER="1" COLOR="#9CA3AF" CELLBORDER="0" CELLSPACING="2" CELLPADDING="3" BGCOLOR="white">'
            '<TR><TD COLSPAN="2" ALIGN="LEFT"><B><FONT POINT-SIZE="10">Leyenda</FONT></B></TD></TR>'
            + cells + extra + "</TABLE>>")


def render(fname, title, classes, edges, rank_labels=None, notes=(), rankdir="TB",
           legend_kinds=None, layered=True, ranksep=0.95, nodesep=0.45):
    by = {c["name"]: c for c in classes}
    L = ["digraph G {",
         f'  rankdir={rankdir}; splines=true; overlap=false; compound=true; newrank=true;',
         f'  nodesep={nodesep}; ranksep={ranksep}; pad=0.35; bgcolor="white";',
         f'  labelloc=t; labeljust=l; fontname="{FONT}"; fontsize=20; fontcolor="#1A2332";',
         f'  label=<<B>{esc(title)}</B>>;',
         f'  node [shape=plain, fontname="{FONT}"]; edge [fontname="{FONT}", fontsize=10, color="#4B5563", fontcolor="#374151"];']
    ranks = sorted({c["rank"] for c in classes})
    if layered:
        for r in ranks:
            members = [c for c in classes if c["rank"] == r]
            lab = (rank_labels or {}).get(r, "")
            L.append("  { rank=same;")
            if lab:
                L.append(f'    "L{r}" [shape=plaintext, label=<<FONT POINT-SIZE="11" COLOR="#6B7280"><B>{esc(lab)}</B></FONT>>, fontname="{FONT}"];')
            prev = f'"L{r}"' if lab else None
            for c in members:
                L.append(f'    "{c["name"]}" [label={label(c)}];')
                if prev:
                    L.append(f'    {prev} -> "{c["name"]}" [style=invis, weight=50];')
                prev = f'"{c["name"]}"'
            L.append("  }")
        labs = [r for r in ranks if (rank_labels or {}).get(r)]
        for a, b in zip(labs, labs[1:]):
            L.append(f'  "L{a}" -> "L{b}" [style=invis, weight=100];')
    else:
        for c in classes:
            L.append(f'  "{c["name"]}" [label={label(c)}];')
    for n_id, text, attach, rk in notes:
        t = esc(text)
        L.append(f'  "{n_id}" [shape=note, style=filled, fillcolor="#FFFDE7", color="#F9A825", fontname="{FONT}", fontsize=10, label=<{t}>, width=2.6];')
        L.append(f'  "{n_id}" -> "{attach}" [style=dotted, arrowhead=none, constraint=false, color="#F9A825"];')
        if rk is not None and layered:
            L.append(f'  {{ rank=same; "{n_id}"; "{attach}"; }}')
    if legend_kinds:
        L.append(f'  "LEG" [shape=plain, label={legend(legend_kinds)}];')
        last = [c["name"] for c in classes if c["rank"] == ranks[-1]][-1]
        L.append(f'  "{last}" -> "LEG" [style=invis, weight=1];')
    for e in edges:
        a, b, kind = e[0], e[1], e[2]
        lab = e[3] if len(e) > 3 else ""
        tm = e[4] if len(e) > 4 else ""
        hm = e[5] if len(e) > 5 else ""
        ra, rb = by[a]["rank"], by[b]["rank"]
        attrs = []
        if lab:
            attrs.append(f'label="{lab}"')
        if tm:
            attrs.append(f'taillabel="{tm}"')
        if hm:
            attrs.append(f'headlabel="{hm}"')
        if kind == "use":
            attrs += ['style=dashed', 'arrowhead=vee', 'arrowsize=0.8']
            attrs.append("constraint=true" if (layered and ra < rb) else "constraint=false")
            L.append(f'  "{a}" -> "{b}" [{", ".join(attrs)}];')
        elif kind == "impl":  # a implementa b (b = interfaz, arriba)
            attrs += ['style=dashed', 'dir=back', 'arrowtail=empty', 'arrowsize=1.0']
            attrs.append("constraint=true" if (layered and rb < ra) else "constraint=false")
            L.append(f'  "{b}" -> "{a}" [{", ".join(attrs)}];')
        elif kind == "assoc":
            attrs += ['arrowhead=vee', 'arrowsize=0.8', "constraint=false"]
            L.append(f'  "{a}" -> "{b}" [{", ".join(attrs)}, labeldistance=1.6];')
        elif kind == "line":
            attrs += ['arrowhead=none', "constraint=false"]
            L.append(f'  "{a}" -> "{b}" [{", ".join(attrs)}, labeldistance=1.6];')
        elif kind in ("comp", "aggr"):
            tail = "diamond" if kind == "comp" else "odiamond"
            attrs += [f'dir=both', f'arrowtail={tail}', 'arrowhead=none', 'arrowsize=1.1', "constraint=false"]
            L.append(f'  "{a}" -> "{b}" [{", ".join(attrs)}, labeldistance=1.6];')
    L.append("}")
    dot = "\n".join(L)
    path = os.path.join(OUT, fname + ".dot")
    open(path, "w").write(dot)
    for fmt, extra in (("svg", []), ("png", ["-Gdpi=170"])):
        subprocess.run(["dot", f"-T{fmt}", *extra, path, "-o", os.path.join(OUT, f"{fname}.{fmt}")], check=True)
    print("ok", fname)


LEG_FLOW = ["controller", "dto", "facade", "rules", "dao", "entity", "ext"]
RL = {0: "PRESENTACION\n(api.controllers)"}  # se redefine por diagrama

# --------------------------------------------------------------- 00 PATRONES
cl = [
    C("RecursoController", "controller", "Controller", methods=["+crear(RecursoRequestDTO) RecursoResponseDTO", "+consultar(id UUID) RecursoResponseDTO"], rank=0),
    C("RecursoRequestDTO", "dto", "DTO", attrs=["+datosDeEntrada"], rank=1),
    C("RecursoResponseDTO", "dto", "DTO", attrs=["+datosDeSalida"], rank=1),
    C("RecursoMapper", "dto", "Mapper", methods=["+toEntity(RecursoRequestDTO) Recurso", "+toDTO(Recurso) RecursoResponseDTO"], rank=3),
    C("IRecursoService", "facade", "Fachada (interfaz)", methods=["+operacionDeNegocioA() Resultado", "+operacionDeNegocioB() Resultado"], rank=2),
    C("RecursoServiceImpl", "facade", None, note="implementa IRecursoService\ncoordina reglas, DAO y servicios", rank=3),
    C("ReglasRecurso", "rules", "Reglas de dominio", methods=["+validar(Recurso) void"], rank=4),
    C("IRecursoDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Recurso", "+guardar(Recurso) Recurso"], rank=4),
    C("RecursoDAOImpl", "dao", None, note="implementa IRecursoDAO\nSQL con JDBC", rank=5),
    C("Recurso", "entity", "Entidad", attrs=["-id UUID"], rank=6),
    C("ConexionPool", "ext", "Fuente de datos", methods=["+getConnection() Connection"], rank=6),
]
ed = [
    ("RecursoController", "IRecursoService", "use", "usa solo la interfaz"),
    ("RecursoController", "RecursoRequestDTO", "use", "recibe"),
    ("RecursoController", "RecursoResponseDTO", "use", "responde"),
    ("RecursoMapper", "RecursoRequestDTO", "use"), ("RecursoMapper", "RecursoResponseDTO", "use"),
    ("RecursoMapper", "Recurso", "use"),
    ("RecursoServiceImpl", "IRecursoService", "impl"),
    ("RecursoServiceImpl", "RecursoMapper", "use"),
    ("RecursoServiceImpl", "ReglasRecurso", "use"),
    ("RecursoServiceImpl", "IRecursoDAO", "use", "usa solo la interfaz"),
    ("RecursoDAOImpl", "IRecursoDAO", "impl"),
    ("RecursoDAOImpl", "ConexionPool", "use", "JDBC"),
    ("RecursoDAOImpl", "Recurso", "use", "construye"),
    ("ReglasRecurso", "Recurso", "use"),
]
nt = [
    ("N1", "<B>FACHADA</B><BR ALIGN=\"LEFT\"/>Una interfaz simple que oculta la coordinacion<BR ALIGN=\"LEFT\"/>de reglas, DAO y servicios externos.<BR ALIGN=\"LEFT\"/>El controlador no conoce lo que hay detras.<BR ALIGN=\"LEFT\"/>", "IRecursoService", 1),
    ("N2", "<B>DAO</B><BR ALIGN=\"LEFT\"/>Aisla el SQL. Cambiar la base de datos<BR ALIGN=\"LEFT\"/>solo afecta a RecursoDAOImpl.<BR ALIGN=\"LEFT\"/>", "IRecursoDAO", 1),
    ("N3", "<B>DTO</B><BR ALIGN=\"LEFT\"/>Lo que viaja por la API.<BR ALIGN=\"LEFT\"/>La entidad nunca se expone.<BR ALIGN=\"LEFT\"/>", "RecursoRequestDTO", None),
]
# las notas ya vienen en HTML: se pasan sin escapar
_esc = esc
def esc_notes(s):
    return s
RANKS = {0: "PRESENTACION", 1: "DTO / MAPPER", 2: "SERVICIOS · FACHADA", 3: "SERVICIOS · IMPLEMENTACION\n+ Mapper",
         4: "DOMINIO · PERSISTENCIA (interfaz)", 5: "PERSISTENCIA (impl)", 6: "ENTIDAD · FUENTE DE DATOS"}

# notas con HTML propio: usar render con parche
def render_notes_patch():
    global esc
    orig = esc
    def raw(s):
        return s if s.startswith("<B>") else orig(s)
    esc = raw
render_notes_patch()
render("00-patrones", "Fig. 1 · Estructura base: patrones Fachada, DAO y DTO en una capa de FutData", cl, ed, RANKS, nt, legend_kinds=LEG_FLOW)
esc = _esc

# --------------------------------------------------------------- 02 PAGOS
cl = [
    C("PagoController", "controller", "Controller", methods=[
        "+consultarEstadoCuenta(deportistaId UUID) EstadoCuentaDTO",
        "+iniciarPago(PagoRequestDTO) PagoResponseDTO",
        "+registrarPagoManual(PagoManualDTO) PagoResponseDTO"], rank=0),
    C("WompiWebhookController", "controller", "Controller", methods=["+recibirEvento(cuerpo String, firma String) void"], rank=0),
    C("PagoRequestDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+periodos List~YearMonth~", "+metodo String"], rank=1),
    C("PagoManualDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+periodo YearMonth", "+valor BigDecimal", "+observacion String"], rank=1),
    C("PagoResponseDTO", "dto", "DTO", attrs=["+pagoId UUID", "+referencia String", "+valor BigDecimal", "+estado String", "+urlCheckout String", "+urlComprobante String"], rank=1),
    C("EstadoCuentaDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+saldoPendiente BigDecimal", "+pagos List~PagoResponseDTO~"], rank=1),
    C("PagoMapper", "dto", "Mapper", methods=["+toDTO(Pago) PagoResponseDTO", "+toEstadoCuentaDTO(List~Pago~) EstadoCuentaDTO"], rank=3),
    C("IPagoService", "facade", "Fachada (interfaz)", methods=[
        "+consultarEstadoCuenta(deportistaId UUID) EstadoCuentaDTO",
        "+iniciarPago(PagoRequestDTO) PagoResponseDTO",
        "+confirmarPagoWompi(cuerpo String, firma String) void",
        "+registrarPagoManual(PagoManualDTO) PagoResponseDTO"], rank=2),
    C("PagoServiceImpl", "facade", None, note="implementa IPagoService (CU-02, RF002)", rank=3),
    C("CalculadoraMensualidad", "rules", "Reglas de dominio", methods=["+calcularValor(Deportista, YearMonth) BigDecimal", "+calcularEstado(Pago, LocalDate) EstadoPago"], rank=4),
    C("IPagoDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Pago", "+buscarPorReferencia(ref String) Pago", "+buscarPorDeportista(id UUID) List~Pago~", "+guardar(Pago) Pago", "+actualizarEstado(id UUID, EstadoPago) void"], rank=4),
    C("IDeportistaDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Deportista"], rank=4),
    C("IPasarelaPago", "ext", "Servicio externo", methods=["+crearTransaccion(ref String, valor BigDecimal) String", "+verificarFirma(cuerpo String, firma String) boolean"], rank=4),
    C("IGeneradorComprobante", "ext", "Reportes", methods=["+generarPdf(Pago) byte[]"], rank=4),
    C("IAlmacenamientoArchivos", "ext", "Servicio externo", methods=["+guardar(ruta String, contenido byte[]) String"], rank=4),
    C("INotificador", "ext", "Servicio externo", methods=["+notificar(evento String, datos Map) void"], rank=4),
    C("PagoDAOImpl", "dao", None, note="implementa IPagoDAO (JDBC)", rank=5),
    C("DeportistaDAOImpl", "dao", None, note="implementa IDeportistaDAO", rank=5),
    C("WompiClient", "ext", None, note="implementa IPasarelaPago", rank=5),
    C("ComprobantePdfGenerator", "ext", None, note="implementa IGeneradorComprobante", rank=5),
    C("SupabaseStorageClient", "ext", None, note="implementa IAlmacenamientoArchivos", rank=5),
    C("N8nClient", "ext", None, note="implementa INotificador", rank=5),
    C("Pago", "entity", "Entidad", attrs=["-periodo YearMonth", "-valor BigDecimal", "-estado EstadoPago"], rank=6),
    C("Deportista", "entity", "Entidad", attrs=["-id UUID"], rank=6),
    C("ConexionPool", "ext", "Fuente de datos", methods=["+getConnection() Connection"], rank=6),
]
ed = [
    ("PagoController", "IPagoService", "use"), ("WompiWebhookController", "IPagoService", "use"),
    ("PagoController", "PagoRequestDTO", "use"), ("PagoController", "PagoManualDTO", "use"),
    ("PagoController", "PagoResponseDTO", "use"), ("PagoController", "EstadoCuentaDTO", "use"),
    ("EstadoCuentaDTO", "PagoResponseDTO", "aggr"),
    ("PagoMapper", "Pago", "use"),
    ("PagoServiceImpl", "IPagoService", "impl"),
    ("PagoServiceImpl", "PagoMapper", "use"),
    ("PagoServiceImpl", "CalculadoraMensualidad", "use"),
    ("PagoServiceImpl", "IPagoDAO", "use"), ("PagoServiceImpl", "IDeportistaDAO", "use"),
    ("PagoServiceImpl", "IPasarelaPago", "use"), ("PagoServiceImpl", "IGeneradorComprobante", "use"),
    ("PagoServiceImpl", "IAlmacenamientoArchivos", "use"), ("PagoServiceImpl", "INotificador", "use"),
    ("PagoDAOImpl", "IPagoDAO", "impl"), ("DeportistaDAOImpl", "IDeportistaDAO", "impl"),
    ("WompiClient", "IPasarelaPago", "impl"), ("ComprobantePdfGenerator", "IGeneradorComprobante", "impl"),
    ("SupabaseStorageClient", "IAlmacenamientoArchivos", "impl"), ("N8nClient", "INotificador", "impl"),
    ("PagoDAOImpl", "ConexionPool", "use"), ("DeportistaDAOImpl", "ConexionPool", "use"),
    ("PagoDAOImpl", "Pago", "use"), ("DeportistaDAOImpl", "Deportista", "use"),
    ("CalculadoraMensualidad", "Pago", "use"), ("CalculadoraMensualidad", "Deportista", "use"),
    ("Pago", "Deportista", "assoc", "", "*", "1"),
]
RANKS = {0: "PRESENTACION\napi.controllers", 1: "api.dto", 2: "SERVICIOS\nservices.facade", 3: "services.impl\n+ Mapper",
         4: "interfaces\ndomain.rules · persistence.dao\ndatasource.external · util", 5: "implementaciones", 6: "domain.entities\ndatasource.db"}
render("02-pagos", "Fig. 3 · Flujo de pagos y facturacion (CU-02, RF002)", cl, ed, RANKS, legend_kinds=LEG_FLOW)

# --------------------------------------------------------------- 03 AUTENTICACION
cl = [
    C("AuthController", "controller", "Controller", methods=[
        "+iniciarVerificacion(jwt String, equipoId String) VerificacionDTO",
        "+verificarCodigo(CodigoRequestDTO) SesionVerificadaDTO",
        "+reenviarCodigo() void",
        "+cerrarSesionEnTodosLosEquipos() void"], rank=0),
    C("CodigoRequestDTO", "dto", "DTO", attrs=["+codigo String", "+recordarEquipo boolean"], rank=1),
    C("VerificacionDTO", "dto", "DTO", attrs=["+requiereCodigo boolean", "+correoEnmascarado String"], rank=1),
    C("SesionVerificadaDTO", "dto", "DTO", attrs=["+verificada boolean", "+rol String", "+equipoId String"], rank=1),
    C("IAutenticacionService", "facade", "Fachada (interfaz)", methods=[
        "+iniciarVerificacion(jwt String, equipoId String) VerificacionDTO",
        "+verificarCodigo(usuarioId UUID, CodigoRequestDTO) SesionVerificadaDTO",
        "+reenviarCodigo(usuarioId UUID) void",
        "+cerrarSesionEnTodosLosEquipos(usuarioId UUID) void"], rank=2),
    C("AutenticacionServiceImpl", "facade", None, note="implementa IAutenticacionService (2FA por correo, RNF001, RF008)", rank=3),
    C("ReglasVerificacion", "rules", "Reglas de dominio", attrs=["VIGENCIA_CODIGO = 10 min", "MAX_INTENTOS = 5", "BLOQUEO = 15 min", "INTERVALO_REENVIO = 1 min", "DIAS_EQUIPO_CONFIANZA = 30"], methods=["+puedeIntentar(CodigoVerificacion) boolean", "+puedeReenviar(CodigoVerificacion) boolean"], rank=4),
    C("JwtValidator", "ext", "util.security", methods=["+validar(jwt String) Usuario", "+rolDe(jwt String) NombreRol"], rank=4),
    C("GeneradorCodigo", "ext", "util.security", methods=["+generarSeisDigitos() String", "+hash(valor String) String"], rank=4),
    C("AuditoriaLogger", "ext", "util.security", methods=["+registrar(evento String, usuarioId UUID) void"], rank=4),
    C("IUsuarioDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Usuario", "+buscarPorCorreo(correo String) Usuario"], rank=5),
    C("ICodigoVerificacionDAO", "dao", "DAO (interfaz)", methods=["+guardar(CodigoVerificacion) void", "+buscarVigente(usuarioId UUID) CodigoVerificacion", "+incrementarIntentos(id UUID) void"], rank=5),
    C("IEquipoConfianzaDAO", "dao", "DAO (interfaz)", methods=["+guardar(EquipoConfianza) void", "+existeVigente(usuarioId UUID, hash String) boolean", "+eliminarTodos(usuarioId UUID) void"], rank=5),
    C("INotificador", "ext", "Servicio externo", methods=["+notificar(evento String, datos Map) void"], rank=5),
    C("UsuarioDAOImpl", "dao", None, note="implementa IUsuarioDAO", rank=6),
    C("CodigoVerificacionDAOImpl", "dao", None, note="implementa ICodigoVerificacionDAO", rank=6),
    C("EquipoConfianzaDAOImpl", "dao", None, note="implementa IEquipoConfianzaDAO", rank=6),
    C("N8nClient", "ext", None, note="implementa INotificador (envia el correo)", rank=6),
    C("Usuario", "entity", "Entidad", attrs=["-id UUID", "-correo String"], rank=7),
    C("CodigoVerificacion", "entity", "Entidad", attrs=["-codigoHash String", "-expiraEn Instant", "-intentos int"], rank=7),
    C("EquipoConfianza", "entity", "Entidad", attrs=["-identificadorHash String", "-vigenteHasta Instant"], rank=7),
    C("ConexionPool", "ext", "Fuente de datos", methods=["+getConnection() Connection"], rank=7),
]
ed = [
    ("AuthController", "IAutenticacionService", "use"),
    ("AuthController", "CodigoRequestDTO", "use"), ("AuthController", "VerificacionDTO", "use"), ("AuthController", "SesionVerificadaDTO", "use"),
    ("AutenticacionServiceImpl", "IAutenticacionService", "impl"),
    ("AutenticacionServiceImpl", "ReglasVerificacion", "use"), ("AutenticacionServiceImpl", "JwtValidator", "use"),
    ("AutenticacionServiceImpl", "GeneradorCodigo", "use"), ("AutenticacionServiceImpl", "AuditoriaLogger", "use"),
    ("AutenticacionServiceImpl", "IUsuarioDAO", "use"), ("AutenticacionServiceImpl", "ICodigoVerificacionDAO", "use"),
    ("AutenticacionServiceImpl", "IEquipoConfianzaDAO", "use"), ("AutenticacionServiceImpl", "INotificador", "use"),
    ("UsuarioDAOImpl", "IUsuarioDAO", "impl"), ("CodigoVerificacionDAOImpl", "ICodigoVerificacionDAO", "impl"),
    ("EquipoConfianzaDAOImpl", "IEquipoConfianzaDAO", "impl"), ("N8nClient", "INotificador", "impl"),
    ("UsuarioDAOImpl", "ConexionPool", "use"), ("CodigoVerificacionDAOImpl", "ConexionPool", "use"), ("EquipoConfianzaDAOImpl", "ConexionPool", "use"),
    ("UsuarioDAOImpl", "Usuario", "use"), ("CodigoVerificacionDAOImpl", "CodigoVerificacion", "use"), ("EquipoConfianzaDAOImpl", "EquipoConfianza", "use"),
    ("ReglasVerificacion", "CodigoVerificacion", "use"),
    ("Usuario", "CodigoVerificacion", "comp", "", "1", "*"),
    ("Usuario", "EquipoConfianza", "comp", "", "1", "*"),
]
RANKS = {0: "PRESENTACION\napi.controllers", 1: "api.dto", 2: "SERVICIOS\nservices.facade", 3: "services.impl\n+ Mapper",
         4: "domain.rules · util.security", 5: "interfaces\npersistence.dao\ndatasource.external", 6: "implementaciones", 7: "domain.entities\ndatasource.db"}
render("03-autenticacion", "Fig. 4 · Inicio de sesion con segundo factor por correo (RNF001, RF008)", cl, ed, RANKS, legend_kinds=LEG_FLOW)

# --------------------------------------------------------------- 04 INSCRIPCION
cl = [
    C("InscripcionController", "controller", "Controller", methods=[
        "+inscribir(InscripcionRequestDTO) InscripcionResponseDTO",
        "+consultar(deportistaId UUID) InscripcionResponseDTO",
        "+subirDocumento(deportistaId UUID, DocumentoRequestDTO) DocumentoResponseDTO"], rank=0),
    C("InscripcionRequestDTO", "dto", "DTO", attrs=["+nombreCompleto String", "+fechaNacimiento LocalDate", "+documentoIdentidad String", "+telefono String", "+direccion String", "+acudienteId UUID"], rank=1),
    C("InscripcionResponseDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+categoria String", "+estado String", "+documentosPendientes List~String~"], rank=1),
    C("DocumentoRequestDTO", "dto", "DTO", attrs=["+tipo String", "+nombreArchivo String", "+contenido byte[]"], rank=1),
    C("DocumentoResponseDTO", "dto", "DTO", attrs=["+documentoId UUID", "+estado String"], rank=1),
    C("InscripcionMapper", "dto", "Mapper", methods=["+toEntity(InscripcionRequestDTO) Deportista", "+toDTO(Deportista) InscripcionResponseDTO"], rank=3),
    C("IInscripcionService", "facade", "Fachada (interfaz)", methods=[
        "+inscribir(InscripcionRequestDTO) InscripcionResponseDTO",
        "+consultar(deportistaId UUID) InscripcionResponseDTO",
        "+adjuntarDocumento(deportistaId UUID, DocumentoRequestDTO) DocumentoResponseDTO"], rank=2),
    C("InscripcionServiceImpl", "facade", None, note="implementa IInscripcionService (CU-01, CU-04, RF001)", rank=3),
    C("ReglasInscripcion", "rules", "Reglas de dominio", methods=["+validarDatosObligatorios(Deportista) void", "+categoriaPara(fechaNacimiento LocalDate) Categoria", "+validarArchivo(tipo String, bytes long) void"], rank=4),
    C("IDeportistaDAO", "dao", "DAO (interfaz)", methods=["+guardar(Deportista) Deportista", "+buscarPorId(id UUID) Deportista", "+existePorDocumento(doc String) boolean"], rank=5),
    C("IAcudienteDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Acudiente"], rank=5),
    C("ICategoriaDAO", "dao", "DAO (interfaz)", methods=["+listarTodas() List~Categoria~"], rank=5),
    C("IDocumentoDAO", "dao", "DAO (interfaz)", methods=["+guardar(Documento) Documento", "+buscarPorDeportista(id UUID) List~Documento~"], rank=5),
    C("IAlmacenamientoArchivos", "ext", "Servicio externo", methods=["+guardar(ruta String, contenido byte[]) String"], rank=5),
    C("INotificador", "ext", "Servicio externo", methods=["+notificar(evento String, datos Map) void"], rank=5),
    C("DeportistaDAOImpl", "dao", None, note="implementa IDeportistaDAO", rank=6),
    C("AcudienteDAOImpl", "dao", None, note="implementa IAcudienteDAO", rank=6),
    C("CategoriaDAOImpl", "dao", None, note="implementa ICategoriaDAO", rank=6),
    C("DocumentoDAOImpl", "dao", None, note="implementa IDocumentoDAO", rank=6),
    C("SupabaseStorageClient", "ext", None, note="implementa IAlmacenamientoArchivos", rank=6),
    C("N8nClient", "ext", None, note="implementa INotificador", rank=6),
    C("Deportista", "entity", "Entidad", attrs=["-id UUID"], rank=7),
    C("Acudiente", "entity", "Entidad", attrs=["-id UUID"], rank=7),
    C("Categoria", "entity", "Entidad", attrs=["-id UUID"], rank=7),
    C("Documento", "entity", "Entidad (nueva)", attrs=["-tipo String", "-rutaStorage String", "-estado EstadoDocumento"], rank=7, dashed=True),
    C("ConexionPool", "ext", "Fuente de datos", methods=["+getConnection() Connection"], rank=7),
]
ed = [
    ("InscripcionController", "IInscripcionService", "use"),
    ("InscripcionController", "InscripcionRequestDTO", "use"), ("InscripcionController", "InscripcionResponseDTO", "use"),
    ("InscripcionController", "DocumentoRequestDTO", "use"), ("InscripcionController", "DocumentoResponseDTO", "use"),
    ("InscripcionMapper", "Deportista", "use"),
    ("InscripcionServiceImpl", "IInscripcionService", "impl"),
    ("InscripcionServiceImpl", "InscripcionMapper", "use"), ("InscripcionServiceImpl", "ReglasInscripcion", "use"),
    ("InscripcionServiceImpl", "IDeportistaDAO", "use"), ("InscripcionServiceImpl", "IAcudienteDAO", "use"),
    ("InscripcionServiceImpl", "ICategoriaDAO", "use"), ("InscripcionServiceImpl", "IDocumentoDAO", "use"),
    ("InscripcionServiceImpl", "IAlmacenamientoArchivos", "use"), ("InscripcionServiceImpl", "INotificador", "use"),
    ("DeportistaDAOImpl", "IDeportistaDAO", "impl"), ("AcudienteDAOImpl", "IAcudienteDAO", "impl"),
    ("CategoriaDAOImpl", "ICategoriaDAO", "impl"), ("DocumentoDAOImpl", "IDocumentoDAO", "impl"),
    ("SupabaseStorageClient", "IAlmacenamientoArchivos", "impl"), ("N8nClient", "INotificador", "impl"),
    ("DeportistaDAOImpl", "ConexionPool", "use"), ("AcudienteDAOImpl", "ConexionPool", "use"),
    ("CategoriaDAOImpl", "ConexionPool", "use"), ("DocumentoDAOImpl", "ConexionPool", "use"),
    ("DeportistaDAOImpl", "Deportista", "use"), ("AcudienteDAOImpl", "Acudiente", "use"),
    ("CategoriaDAOImpl", "Categoria", "use"), ("DocumentoDAOImpl", "Documento", "use"),
    ("ReglasInscripcion", "Categoria", "use"),
    ("Acudiente", "Deportista", "line", "", "1", "1..*"),
    ("Deportista", "Categoria", "assoc", "", "*", "1"),
    ("Deportista", "Documento", "comp", "", "1", "*"),
]
RANKS = {0: "PRESENTACION\napi.controllers", 1: "api.dto", 2: "SERVICIOS\nservices.facade", 3: "services.impl\n+ Mapper",
         4: "domain.rules", 5: "interfaces\npersistence.dao\ndatasource.external", 6: "implementaciones", 7: "domain.entities\ndatasource.db"}
render("04-inscripcion", "Fig. 5 · Inscripcion de deportistas y documentacion (CU-01, CU-04, RF001)", cl, ed, RANKS, legend_kinds=LEG_FLOW)

# --------------------------------------------------------------- 05 ASISTENCIA
cl = [
    C("AsistenciaController", "controller", "Controller", methods=[
        "+listarGrupo(categoriaId UUID, fecha LocalDate) GrupoAsistenciaDTO",
        "+registrar(RegistroAsistenciaDTO) void",
        "+corregir(asistenciaId UUID, AsistenciaItemDTO) void",
        "+reporteSemanal(categoriaId UUID, semana LocalDate) ReporteAsistenciaDTO"], rank=0),
    C("GrupoAsistenciaDTO", "dto", "DTO", attrs=["+entrenamientoId UUID", "+jugadores List~JugadorAsistenciaDTO~"], rank=1),
    C("JugadorAsistenciaDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+nombre String"], rank=1),
    C("RegistroAsistenciaDTO", "dto", "DTO", attrs=["+entrenamientoId UUID", "+items List~AsistenciaItemDTO~"], rank=1),
    C("AsistenciaItemDTO", "dto", "DTO", attrs=["+deportistaId UUID", "+estado String", "+observaciones String"], rank=1),
    C("ReporteAsistenciaDTO", "dto", "DTO", attrs=["+categoria String", "+porcentajeAsistencia double", "+detalle List~AsistenciaItemDTO~"], rank=1),
    C("AsistenciaMapper", "dto", "Mapper", methods=["+toEntity(AsistenciaItemDTO) Asistencia", "+toDTO(Asistencia) AsistenciaItemDTO"], rank=3),
    C("IAsistenciaService", "facade", "Fachada (interfaz)", methods=[
        "+listarGrupo(categoriaId UUID, fecha LocalDate) GrupoAsistenciaDTO",
        "+registrar(RegistroAsistenciaDTO) void",
        "+corregir(asistenciaId UUID, AsistenciaItemDTO) void",
        "+reporteSemanal(categoriaId UUID, semana LocalDate) ReporteAsistenciaDTO"], rank=2),
    C("AsistenciaServiceImpl", "facade", None, note="implementa IAsistenciaService (CU-03, RF005)", rank=3),
    C("ReglasAsistencia", "rules", "Reglas de dominio", methods=["+validarEstado(String) EstadoAsistencia", "+entrenadorAsignado(Entrenador, Categoria) boolean", "+porcentaje(List~Asistencia~) double"], rank=4),
    C("ControlAcceso", "ext", "util.security", methods=["+usuarioActual() Usuario", "+exigirRol(NombreRol) void"], rank=4),
    C("IAsistenciaDAO", "dao", "DAO (interfaz)", methods=["+guardarTodas(List~Asistencia~) void", "+buscarPorId(id UUID) Asistencia", "+actualizar(Asistencia) void", "+buscarPorCategoriaYSemana(id UUID, semana LocalDate) List~Asistencia~"], rank=5),
    C("IEntrenamientoDAO", "dao", "DAO (interfaz)", methods=["+buscarPorId(id UUID) Entrenamiento"], rank=5),
    C("IDeportistaDAO", "dao", "DAO (interfaz)", methods=["+listarPorCategoria(categoriaId UUID) List~Deportista~"], rank=5),
    C("IGeneradorReporte", "ext", "util.reports", methods=["+generarPdf(ReporteAsistenciaDTO) byte[]", "+generarExcel(ReporteAsistenciaDTO) byte[]"], rank=5),
    C("INotificador", "ext", "Servicio externo", methods=["+notificar(evento String, datos Map) void"], rank=5),
    C("AsistenciaDAOImpl", "dao", None, note="implementa IAsistenciaDAO", rank=6),
    C("EntrenamientoDAOImpl", "dao", None, note="implementa IEntrenamientoDAO", rank=6),
    C("DeportistaDAOImpl", "dao", None, note="implementa IDeportistaDAO", rank=6),
    C("ReporteAsistenciaGenerator", "ext", None, note="implementa IGeneradorReporte", rank=6),
    C("N8nClient", "ext", None, note="implementa INotificador (resumen semanal)", rank=6),
    C("Asistencia", "entity", "Entidad", attrs=["-estado EstadoAsistencia", "-observaciones String"], rank=7),
    C("Entrenamiento", "entity", "Entidad (nueva)", attrs=["-fecha LocalDate", "-lugar String"], rank=7, dashed=True),
    C("Deportista", "entity", "Entidad", attrs=["-id UUID"], rank=7),
    C("Entrenador", "entity", "Entidad", attrs=["-id UUID"], rank=7),
    C("ConexionPool", "ext", "Fuente de datos", methods=["+getConnection() Connection"], rank=7),
]
ed = [
    ("AsistenciaController", "IAsistenciaService", "use"),
    ("AsistenciaController", "GrupoAsistenciaDTO", "use"), ("AsistenciaController", "RegistroAsistenciaDTO", "use"),
    ("AsistenciaController", "AsistenciaItemDTO", "use"), ("AsistenciaController", "ReporteAsistenciaDTO", "use"),
    ("GrupoAsistenciaDTO", "JugadorAsistenciaDTO", "aggr"), ("RegistroAsistenciaDTO", "AsistenciaItemDTO", "aggr"),
    ("ReporteAsistenciaDTO", "AsistenciaItemDTO", "aggr"),
    ("AsistenciaMapper", "Asistencia", "use"),
    ("AsistenciaServiceImpl", "IAsistenciaService", "impl"),
    ("AsistenciaServiceImpl", "AsistenciaMapper", "use"), ("AsistenciaServiceImpl", "ReglasAsistencia", "use"),
    ("AsistenciaServiceImpl", "ControlAcceso", "use"),
    ("AsistenciaServiceImpl", "IAsistenciaDAO", "use"), ("AsistenciaServiceImpl", "IEntrenamientoDAO", "use"),
    ("AsistenciaServiceImpl", "IDeportistaDAO", "use"), ("AsistenciaServiceImpl", "IGeneradorReporte", "use"),
    ("AsistenciaServiceImpl", "INotificador", "use"),
    ("AsistenciaDAOImpl", "IAsistenciaDAO", "impl"), ("EntrenamientoDAOImpl", "IEntrenamientoDAO", "impl"),
    ("DeportistaDAOImpl", "IDeportistaDAO", "impl"), ("ReporteAsistenciaGenerator", "IGeneradorReporte", "impl"),
    ("N8nClient", "INotificador", "impl"),
    ("AsistenciaDAOImpl", "ConexionPool", "use"), ("EntrenamientoDAOImpl", "ConexionPool", "use"), ("DeportistaDAOImpl", "ConexionPool", "use"),
    ("AsistenciaDAOImpl", "Asistencia", "use"), ("EntrenamientoDAOImpl", "Entrenamiento", "use"), ("DeportistaDAOImpl", "Deportista", "use"),
    ("ReglasAsistencia", "Asistencia", "use"), ("ReglasAsistencia", "Entrenador", "use"),
    ("Entrenamiento", "Asistencia", "comp", "", "1", "*"),
    ("Deportista", "Asistencia", "line", "", "1", "*"),
]
RANKS = {0: "PRESENTACION\napi.controllers", 1: "api.dto", 2: "SERVICIOS\nservices.facade", 3: "services.impl\n+ Mapper",
         4: "domain.rules · util.security", 5: "interfaces\npersistence.dao\nutil.reports · external", 6: "implementaciones", 7: "domain.entities\ndatasource.db"}
render("05-asistencia", "Fig. 6 · Registro de asistencia y reportes (CU-03, RF005)", cl, ed, RANKS, legend_kinds=LEG_FLOW)

# --------------------------------------------------------------- 01 DOMINIO
D = []
DR = {"Usuario":0,"Rol":0,"NombreRol":0,"CodigoVerificacion":1,"EquipoConfianza":1,"Acudiente":1,"Entrenador":1,"Deportista":2,"Categoria":2,"Entrenamiento":2,"Pago":3,"Asistencia":3,"Documento":3,"Torneo":3}
def E(name, kind, attrs, stereo=None, dashed=False):
    D.append(C(name, kind, stereo, attrs=attrs, rank=DR[name], dashed=dashed))
E("Usuario", "seg", ["-id UUID", "-correo String", "-nombreCompleto String", "-activo boolean"])
E("Rol", "seg", ["-id int", "-nombre NombreRol"])
E("NombreRol", "seg", ["ADMINISTRADOR", "TESORERO", "ENTRENADOR", "PADRE"], stereo="enumeracion")
E("CodigoVerificacion", "seg", ["-codigoHash String", "-expiraEn Instant", "-intentos int", "-usado boolean"])
E("EquipoConfianza", "seg", ["-identificadorHash String", "-vigenteHasta Instant"])
E("Acudiente", "entity", ["-id UUID", "-nombreCompleto String", "-documentoIdentidad String", "-telefono String"])
E("Deportista", "entity", ["-id UUID", "-nombreCompleto String", "-fechaNacimiento LocalDate", "-documentoIdentidad String", "-telefono String", "-direccion String", "-estado EstadoDeportista", "+edad() int"])
E("Categoria", "entity", ["-id UUID", "-nombre String", "-edadMinima int", "-edadMaxima int"])
E("Entrenador", "entity", ["-id UUID", "-nombreCompleto String", "-telefono String", "-especialidad String"])
E("Entrenamiento", "nuevo", ["-fecha LocalDate", "-horaInicio LocalTime", "-lugar String"], stereo="nueva", dashed=True)
E("Asistencia", "entity", ["-estado EstadoAsistencia", "-observaciones String", "-registradaEn Instant"])
E("Pago", "entity", ["-periodo YearMonth", "-valor BigDecimal", "-estado EstadoPago", "-metodo String", "-referencia String", "-fechaPago Instant", "-comprobanteRuta String"])
E("Torneo", "entity", ["-nombre String", "-fechaInicio LocalDate", "-fechaFin LocalDate", "-costoInscripcion BigDecimal"])
E("Documento", "nuevo", ["-tipo String", "-rutaStorage String", "-estado EstadoDocumento", "-venceEn LocalDate"], stereo="nueva", dashed=True)
ed = [
    ("Usuario", "Rol", "assoc", "", "*", "1"),
    ("Usuario", "CodigoVerificacion", "comp", "", "1", "*"),
    ("Usuario", "EquipoConfianza", "comp", "", "1", "*"),
    ("Usuario", "Acudiente", "line", "cuenta de", "1", "0..1"),
    ("Usuario", "Entrenador", "line", "cuenta de", "1", "0..1"),
    ("Acudiente", "Deportista", "line", "responsable de", "1", "1..*"),
    ("Deportista", "Categoria", "assoc", "pertenece a", "*", "1"),
    ("Entrenador", "Categoria", "line", "dirige", "1", "*"),
    ("Categoria", "Entrenamiento", "line", "programa", "1", "*"),
    ("Entrenador", "Entrenamiento", "line", "a cargo de", "1", "*"),
    ("Entrenamiento", "Asistencia", "comp", "", "1", "*"),
    ("Deportista", "Asistencia", "line", "", "1", "*"),
    ("Deportista", "Pago", "comp", "", "1", "*"),
    ("Deportista", "Torneo", "line", "participa", "*", "*"),
    ("Deportista", "Documento", "comp", "", "1", "*"),
    ("Rol", "NombreRol", "use"),
]
render("01-dominio", "Fig. 2 · Modelo de dominio (domain.entities)", D, ed, {0: "SEGURIDAD\ny acceso", 1: "RESPONSABLES\nY CUENTAS", 2: "DEPORTISTAS\nY ENTRENAMIENTOS", 3: "ACTIVIDAD\ny SEGUIMIENTO"}, layered=True, rankdir="TB",
       legend_kinds=["seg", "entity", "nuevo"], ranksep=1.0, nodesep=0.6)
