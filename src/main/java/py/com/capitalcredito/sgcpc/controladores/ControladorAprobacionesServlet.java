package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.excepciones.CondicionesCambiaronException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAprobacionCredito;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P05, bandeja de aprobación (Doc. 10 §10.8: ControladorAprobacionesServlet /
 * ServicioAprobacionCredito). Protegido por FiltroAutorizacion (APROBAR_RECHAZAR_SOLICITUD, solo
 * GERENTE_ADMINISTRADOR tiene TOTAL -- RN-06/D-60).
 */
public class ControladorAprobacionesServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/aprobaciones/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/aprobaciones/_resultados.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/aprobaciones/ficha.jsp";

    /**
     * B170 (14/11/2026): el punto de retorno de la bandeja, en la sesión, para que el "Volver a la
     * bandeja" de la ficha devuelva la **pestaña** y los filtros que el gerente tenía. Clave propia:
     * cada listado del sistema recuerda el suyo por separado (B147, B163).
     */
    private static final String RETORNO_BANDEJA_EN_SESION = "retornoBandejaAprobaciones";

    /**
     * B175 (14/11/2026): cuántas filas por página. El mismo 20 que Clientes (B148) y Solicitudes
     * (B167): si una pantalla paginara distinto, la primera pregunta de Joel sería por qué.
     */
    private static final int FILAS_POR_PAGINA = 20;

    private final ServicioAprobacionCredito servicioAprobacionCredito = new ServicioAprobacionCredito();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    // B104 (01/11/2026): para saber si la solicitud aprobada ya se formalizó y a dónde lleva el
    // paso siguiente. Solo lectura; no agrega ninguna acción a esta pantalla.
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    // B88 (29/10/2026): el enlace a la ficha del cliente abre la pestaña del primer paso pendiente.
    private final py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado servicioPasoGuiado =
            new py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado();
    // RF06 (pedido de Joel, 10/10/2026): glosario de TEA -- mismo criterio que ControladorCotizadorServlet.
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response);
        } else {
            Long idSolicitud = idDeSegmento(pathInfo);
            if (idSolicitud == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarFicha(request, response, idSolicitud);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);

        Long idSolicitud = pathInfo == null ? null : idDeSegmento(pathInfo);
        if (idSolicitud == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            if (pathInfo.endsWith("/aprobar")) {
                servicioAprobacionCredito.aprobar(idSolicitud, solicitante.getIdUsuario());
                // Tanda de usabilidad (20/10/2026, pedido de Joel, punto 7): no decía qué sigue,
                // a diferencia del mensaje de "enviada a aprobación" ("Ahora la revisa el
                // gerente en Aprobación.").
                MensajesFlash.guardarExito(sesion, "Solicitud aprobada. Ahora se formaliza en Formalización.");
            } else if (pathInfo.endsWith("/rechazar")) {
                String idMotivoTexto = request.getParameter("idMotivoRechazo");
                Long idMotivoRechazo = (idMotivoTexto == null || idMotivoTexto.isEmpty())
                        ? null : Long.valueOf(idMotivoTexto);
                servicioAprobacionCredito.rechazar(idSolicitud, idMotivoRechazo,
                        request.getParameter("detalleMotivo"), solicitante.getIdUsuario());
                MensajesFlash.guardarExito(sesion, "Solicitud rechazada.");
            } else if (pathInfo.endsWith("/cancelar")) {
                servicioAprobacionCredito.cancelar(idSolicitud, request.getParameter("codigoMotivoCancelacion"),
                        request.getParameter("detalleCancelacion"), solicitante.getIdUsuario());
                MensajesFlash.guardarExito(sesion, "Solicitud cancelada.");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (py.com.capitalcredito.sgcpc.excepciones.SolicitudYaDecididaException error) {
            // RF09/RF10 (pedido de Joel, 11/10/2026): carrera de dos pestañas -- no es un error
            // del servidor, es una advertencia (ícono y botón "Entendido", no el de error).
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (CondicionesCambiaronException error) {
            // A.8 (consolidación, pedido de Joel): el tope de tasa o la capacidad de pago
            // cambiaron desde que se simuló/envió -- advertencia, no error, mismo criterio que
            // SolicitudYaDecididaException.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/aprobaciones/" + idSolicitud);
    }

    private Long idDeSegmento(String pathInfo) {
        String[] segmentos = pathInfo.substring(1).split("/");
        if (segmentos.length < 1) {
            return null;
        }
        try {
            return Long.parseLong(segmentos[0]);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 2): buscador
        // con el mismo mecanismo que Clientes/Mora (listado-vivo.js + PeticionAjax.esAjax).
        String textoBusqueda = request.getParameter("texto");
        request.setAttribute("textoBusqueda", textoBusqueda);
        // B170 (14/11/2026): punto de retorno de la bandeja, en la sesión. Lo escribe SOLO este
        // método y lo sirve SOLO mostrarFicha (un dato, un dueño). Mismo mecanismo que B147
        // (Clientes) y B163 (Solicitudes), con su propia clave -- pero acá guarda ADEMÁS la
        // pestaña: sin eso, volver desde una solicitud del Historial aterrizaba en Pendientes.
        request.getSession().setAttribute(RETORNO_BANDEJA_EN_SESION, construirRetornoBandeja(request));
        // B175 (14/11/2026): los encabezados que ordenan y la paginación necesitan los filtros
        // vigentes en sus enlaces, y SOLO los filtros: el fragmento de paginación le pega
        // "pagina=N" y cada encabezado su "orden=...", así que si acá viniera el orden o la página
        // el enlace los llevaría dos veces. Por eso son dos consultas distintas y no una.
        request.setAttribute("queryStringFiltros", construirConsulta(request, FILTROS_DE_LA_BANDEJA));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            // Corrección 4 (Joel): pestaña "Historial" (?vista=historial) para ver las ya decididas.
            boolean vistaHistorial = "historial".equals(request.getParameter("vista"));
            request.setAttribute("vistaHistorial", vistaHistorial);
            // B175: el controlador NO decide el orden, solo pasa los parámetros crudos. La lista
            // blanca, el orden por defecto de cada pestaña y la regla del sentido viven en
            // ServicioAprobacionCredito, que es su único dueño (la lección de B167).
            String orden = ServicioAprobacionCredito.ordenAplicado(request.getParameter("orden"), vistaHistorial);
            String sentido = ServicioAprobacionCredito.sentidoAplicado(
                    request.getParameter("orden"), request.getParameter("sentido"), vistaHistorial);
            request.setAttribute("ordenActual", orden);
            request.setAttribute("sentidoActual", sentido);
            List<SolicitudCredito> solicitudes;
            if (vistaHistorial) {
                // RF07 (pedido de Joel, 10/10/2026): filtro por estado y por rango de fechas en el
                // Historial, sobre la fecha de la decisión.
                String estadoFiltro = normalizarEstadoDecidido(request.getParameter("estado"));
                LocalDate desdeFiltro = parsearFechaOpcional(request.getParameter("desde"));
                LocalDate hastaFiltro = parsearFechaOpcional(request.getParameter("hasta"));
                request.setAttribute("estadoFiltro", estadoFiltro);
                request.setAttribute("desdeFiltro", desdeFiltro);
                request.setAttribute("hastaFiltro", hastaFiltro);
                // B176 (14/11/2026): el enlace "Exportar en Informes" se arma ACÁ, con los
                // filtros ya validados -- la JSP pregunta y dibuja, no decide (B88).
                request.setAttribute("consultaInformeAprobaciones",
                        construirConsultaInformeAprobaciones(desdeFiltro, hastaFiltro, textoBusqueda));
                solicitudes = servicioAprobacionCredito.listarDecididasConFiltro(conexion, estadoFiltro, desdeFiltro, hastaFiltro);
            } else {
                solicitudes = servicioAprobacionCredito.listarPendientes(conexion);
            }
            // B172 (14/11/2026): cuántas hay esperando, para que la pestaña lo diga. Es el número
            // que el gerente necesita ver DESDE el Historial (estando en Pendientes, el contador de
            // resultados de abajo ya lo dice). Se cuenta sin el filtro de texto -- son las que
            // esperan, no las que coinciden con una búsqueda-- y en Pendientes se reusa la lista ya
            // cargada en vez de repetir la consulta. Mismo criterio que la tarjeta del Panel, que
            // también cuenta con el .size() de esta misma lista (ServicioPanel:107).
            request.setAttribute("cantidadPendientes", vistaHistorial
                    ? servicioAprobacionCredito.listarPendientes(conexion).size() : solicitudes.size());
            // Ajuste 3 (Joel): columna Cliente en la bandeja.
            Map<Long, Persona> clientes = new HashMap<>();
            for (SolicitudCredito solicitud : solicitudes) {
                if (!clientes.containsKey(solicitud.getIdCliente())) {
                    clientes.put(solicitud.getIdCliente(), servicioClientes.buscarPorId(conexion, solicitud.getIdCliente()));
                }
            }
            // RF07 (pedido de Joel, 10/10/2026): columna "Decidida por" -- quién decidió
            // (aprobó/rechazó) o quién canceló, según corresponda a cada fila.
            Map<Long, Usuario> decisores = new HashMap<>();
            if (vistaHistorial) {
                for (SolicitudCredito solicitud : solicitudes) {
                    // B175: la regla de "quién decidió" tiene un solo dueño (idDecisorDe); estaba
                    // escrita acá y al armar cada fila, y ahora la necesita también el comparador.
                    Long idDecisor = ServicioAprobacionCredito.idDecisorDe(solicitud);
                    if (idDecisor != null && !decisores.containsKey(idDecisor)) {
                        decisores.put(idDecisor, servicioUsuarios.buscarPorId(conexion, idDecisor));
                    }
                }
            }
            // B174 (14/11/2026): el oficial que envió cada solicitud, para la columna "Enviada
            // por" de Pendientes. Mismo patrón que el mapa de decisores de arriba -- una consulta
            // por usuario distinto, no una por fila-- y solo en Pendientes: en el Historial esa
            // columna no se dibuja, así que no se consulta nada.
            Map<Long, Usuario> oficialesQueEnviaron = new HashMap<>();
            if (!vistaHistorial) {
                for (SolicitudCredito solicitud : solicitudes) {
                    if (!oficialesQueEnviaron.containsKey(solicitud.getCreadoPor())) {
                        oficialesQueEnviaron.put(solicitud.getCreadoPor(),
                                servicioUsuarios.buscarPorId(conexion, solicitud.getCreadoPor()));
                    }
                }
            }
            // RF07 (pedido de Joel, 11/10/2026): cada fila se arma y se formatea ACÁ, entera,
            // antes de llegar a la JSP -- para que un dato faltante (ej. una CANCELADA que nunca
            // se envió a aprobación, sin fecha_envio_aprobacion -- ver SOL-2026-0003) no tire una
            // excepción a mitad de la tabla y deje la página dibujada a medias. La JSP ahora solo
            // imprime getters de FilaBandejaAprobacion, ya todos con "-" en vez de null.
            // B175: ordenar la lista COMPLETA acá, ANTES de armar las filas. Dos motivos: la fila
            // ya viene formateada (ordenar por "15.000.000 Gs." ordenaría alfabéticamente un
            // número), y ordenar después del corte de la página daría un orden falso (B157).
            List<SolicitudCredito> ordenadas = servicioAprobacionCredito.ordenarParaBandeja(
                    solicitudes, clientes, decisores, oficialesQueEnviaron, orden, sentido, vistaHistorial);
            List<py.com.capitalcredito.sgcpc.dto.FilaBandejaAprobacion> filas = new java.util.ArrayList<>();
            for (SolicitudCredito solicitud : ordenadas) {
                filas.add(servicioAprobacionCredito.construirFilaBandeja(solicitud,
                        clientes.get(solicitud.getIdCliente()),
                        decisores.get(ServicioAprobacionCredito.idDecisorDe(solicitud)),
                        oficialesQueEnviaron.get(solicitud.getCreadoPor()), vistaHistorial));
            }
            // B175: el filtro de texto es en memoria desde el 20/10/2026, así que la paginación
            // también lo es: con LIMIT/OFFSET en SQL ese filtro se aplicaría DESPUÉS del corte y
            // las páginas saldrían con menos filas de las que dicen (mismo razonamiento que B167).
            List<py.com.capitalcredito.sgcpc.dto.FilaBandejaAprobacion> filtradas =
                    servicioAprobacionCredito.filtrarPorTexto(filas, textoBusqueda);
            int total = filtradas.size();
            int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) FILAS_POR_PAGINA));
            int pagina = paginaPedida(request.getParameter("pagina"), totalPaginas);
            int desde = (pagina - 1) * FILAS_POR_PAGINA;
            int hasta = Math.min(total, desde + FILAS_POR_PAGINA);
            request.setAttribute("filas", filtradas.subList(desde, hasta));
            request.setAttribute("pagina", pagina);
            request.setAttribute("totalPaginas", totalPaginas);
            // B175: el contador de abajo cuenta el TOTAL, no las filas de la página -- si no, con
            // 409 decididas la pantalla diría "20 solicitudes encontradas" (el mismo cuidado que
            // hubo que tener en B167).
            request.setAttribute("totalResultados", total);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la bandeja de aprobación.", error);
            request.setAttribute("mensajeError", "No se pudo leer la bandeja de aprobación.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /**
     * B175: la página pedida, acotada a lo que existe. Fuera de rango (o basura) cae en 1 o en la
     * última: así un enlace viejo o un número tecleado a mano nunca deja la lista vacía sin
     * explicación. Mismo criterio y mismo texto que B167 en Solicitudes.
     */
    private int paginaPedida(String texto, int totalPaginas) {
        int pagina = 1;
        if (texto != null && !texto.trim().isEmpty()) {
            try {
                pagina = Integer.parseInt(texto.trim());
            } catch (NumberFormatException noEsUnNumero) {
                pagina = 1;
            }
        }
        if (pagina < 1) {
            pagina = 1;
        }
        return Math.min(pagina, Math.max(1, totalPaginas));
    }

    /** RF07: solo las tres decididas son válidas como filtro -- cualquier otro valor (vacío,
     * inválido, BORRADOR/PENDIENTE_APROBACION) se ignora y se listan las tres. */
    private String normalizarEstadoDecidido(String estadoParametro) {
        if (estadoParametro == null || estadoParametro.isEmpty()) {
            return null;
        }
        try {
            EstadoSolicitud estado = EstadoSolicitud.valueOf(estadoParametro);
            return (estado == EstadoSolicitud.APROBADA || estado == EstadoSolicitud.RECHAZADA
                    || estado == EstadoSolicitud.CANCELADA) ? estadoParametro : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /**
     * B170: los filtros actuales de la bandeja como query string, para el "Volver a la bandeja" de
     * la ficha. Incluye <b>vista</b> además de los filtros: es la pestaña, y perderla era la mitad
     * del problema (desde el Historial se volvía a Pendientes).
     */
    private String construirRetornoBandeja(HttpServletRequest request) {
        return construirConsulta(request, RETORNO_DE_LA_BANDEJA);
    }

    /** Los parámetros que hacen a lo FILTRADO: lo que viaja en el enlace de cada encabezado que
     * ordena y en la URL base de la paginación (B175). */
    private static final String[] FILTROS_DE_LA_BANDEJA = {"vista", "texto", "estado", "desde", "hasta"};

    /**
     * B175: lo que hace al LUGAR donde estaba el gerente -- los filtros, más el orden y la página.
     * Es lo que guarda el retorno de B170: con la bandeja paginada, volver de una ficha a la página
     * 1 sería una forma nueva de perder el lugar, que es justo lo que B170 vino a resolver (y lo
     * que B147 ya había resuelto en Clientes, donde el "Volver" sí conserva la página).
     */
    private static final String[] RETORNO_DE_LA_BANDEJA =
            {"vista", "texto", "estado", "desde", "hasta", "orden", "sentido", "pagina"};

    private String construirConsulta(HttpServletRequest request, String[] nombres) {
        StringBuilder consulta = new StringBuilder();
        for (String nombre : nombres) {
            agregarParametro(consulta, nombre, request.getParameter(nombre));
        }
        return consulta.toString();
    }

    /**
     * B176 (14/11/2026): el enlace <b>"Exportar en Informes"</b> del Historial, con los filtros
     * puestos. Venía llevando solo las fechas desde el 20/10/2026 (quedó anotado ese día en
     * {@code AVANCE.md}, sin número), así que el gerente que buscaba un cliente y hacía clic
     * aterrizaba en el informe <b>sin su búsqueda</b> y tenía que volver a escribirla.
     *
     * <p>El texto del buscador viaja como <b>{@code clienteUOperacion}</b>, que es el nombre que
     * ese parámetro tiene en el informe: son el mismo criterio con dos nombres
     * ({@code ServicioReportes.coincideClienteUOperacion} busca por código, cédula y nombre, igual
     * que {@code filtrarPorTexto} acá).</p>
     *
     * <p><b>El filtro de estado NO viaja, porque el informe no tiene dónde recibirlo</b>: sus
     * únicos filtros son {@code desde}, {@code hasta} y {@code clienteUOperacion}
     * ({@code informe-aprobaciones.jsp}). Inventarle un parámetro que nadie lee habría dado un
     * enlace que miente. Agregarle ese filtro al informe es otra pantalla y otra decisión.</p>
     *
     * <p>Las fechas se pasan <b>ya parseadas</b> (no el parámetro crudo): si alguien escribió
     * basura en la URL, acá vale null y el informe usa su rango por defecto, que es lo que hacía
     * antes.</p>
     */
    private String construirConsultaInformeAprobaciones(LocalDate desde, LocalDate hasta, String texto) {
        StringBuilder consulta = new StringBuilder();
        agregarParametro(consulta, "desde", desde == null ? null : desde.toString());
        agregarParametro(consulta, "hasta", hasta == null ? null : hasta.toString());
        agregarParametro(consulta, "clienteUOperacion", texto);
        return consulta.length() == 0 ? "" : "?" + consulta;
    }

    /** Un parámetro a una query string, codificado, salteando lo vacío. Lo usan el retorno de la
     * bandeja (B170) y el enlace al informe (B176): la misma regla, un solo lugar. */
    private void agregarParametro(StringBuilder consulta, String nombre, String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return;
        }
        if (consulta.length() > 0) {
            consulta.append('&');
        }
        try {
            consulta.append(nombre).append('=').append(java.net.URLEncoder.encode(valor, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException error) {
            // UTF-8 siempre está en la JVM; nunca debería pasar.
        }
    }

    private LocalDate parsearFechaOpcional(String textoFecha) {
        if (textoFecha == null || textoFecha.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(textoFecha);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, long idSolicitud)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // B170: el "Volver a la bandeja" devuelve la pestaña y los filtros que el gerente tenía.
        // Vacío si todavía no pasó por la bandeja en esta sesión: entonces va a /aprobaciones
        // (Pendientes), como siempre.
        Object retornoBandeja = request.getSession().getAttribute(RETORNO_BANDEJA_EN_SESION);
        request.setAttribute("urlVolverBandeja", retornoBandeja == null ? "" : retornoBandeja.toString());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            SolicitudCredito solicitud = servicioAprobacionCredito.obtenerPorId(conexion, idSolicitud);
            if (solicitud == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // RF08 (pedido de Joel, 11/10/2026, hallazgo del 11/10/2026): la franja y el
            // refuerzo de "Aprobar" usan la consulta MÁS RECIENTE del cliente, no la vinculada al
            // enviar -- antes una consulta nueva, registrada después del envío, quedaba
            // invisible. La tabla "Resultado del buró" sigue mostrando también la vinculada
            // original (trazabilidad), marcando la nueva.
            List<ConsultaCrediticia> consultasMasRecientes =
                    servicioAprobacionCredito.listarConsultasMasRecientes(conexion, cliente);
            py.com.capitalcredito.sgcpc.dto.AvisoBuroSolicitud avisoBuro =
                    servicioAprobacionCredito.construirAvisoBuro(consultasMasRecientes, cliente);
            List<py.com.capitalcredito.sgcpc.dto.FilaBuroSolicitud> filasConsultaBuro =
                    servicioAprobacionCredito.construirFilasConsultaBuro(conexion, solicitud, cliente);
            // Ajuste 1 (Joel): total a pagar EXACTO (suma del plan), no una aproximación.
            ResultadoCotizacion resultadoCotizacion = servicioSolicitudes.recalcularPlan(conexion, solicitud);
            // B88 (decisión de Joel del 29/10/2026, tras probar el mismo arreglo en la ficha de la
            // solicitud): "Ver ficha / expediente" abre la ficha en la pestaña del primer paso
            // pendiente -- el nombre del enlace ya promete el expediente, y el gerente entra justo
            // a mirar eso. Mismo dueño de la regla que el resto del circuito
            // (ServicioClientes.calcularSiguientePaso, vía ServicioPasoGuiado), nunca un criterio
            // propio de esta pantalla. Si el perfil no tiene CARGAR_DOCUMENTOS_CONSULTA_BURO, las
            // pestañas ②③ no se dibujan y el ancla simplemente no engancha: queda ① Datos.
            request.setAttribute("anclaFichaCliente",
                    servicioPasoGuiado.calcular(conexion, cliente.getIdPersona()).anclaPestanaFicha());
            // RF06 (pedido de Joel, 10/10/2026): ícono ⓘ de TEA en el glosario -- mismo criterio
            // que ControladorCotizadorServlet (el texto completo de TEA menciona el tope vigente).
            LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
            if (limiteVigente != null) {
                request.setAttribute("topeTeaVigente", limiteVigente.getTasaMaximaTea());
            }
            request.setAttribute("solicitud", solicitud);
            request.setAttribute("cliente", cliente);
            // Normalización de personas, grupo Laborales (16/10/2026): ingreso/comprobante de
            // ingreso/situación laboral/empleador/cuota de otras deudas ya no están en Persona --
            // el resumen los necesita para mostrarlos, se resuelven acá con el mismo método único.
            request.setAttribute("laboralCliente", servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona()));
            request.setAttribute("filasConsultaBuro", filasConsultaBuro);
            request.setAttribute("avisoBuro", avisoBuro);
            request.setAttribute("resultadoCotizacion", resultadoCotizacion);
            // Bloque 7/E10 (D-129, Obs. 26): resumen del cliente para decidir sin entrar a la
            // ficha -- edad, departamento/ciudad, y el mismo checklist (✓ / falta) que ve el
            // oficial antes de enviar a aprobación (construirChecklistPrevioEnvio).
            request.setAttribute("edadCliente",
                    servicioSolicitudes.calcularEdad(cliente.getFechaNacimiento(),
                            py.com.capitalcredito.sgcpc.infraestructura.Reloj.hoy()));
            // Normalización de personas, grupo Domicilio (16/10/2026): ciudad/departamento ya no
            // están en Persona -- se leen de personas_domicilio, y el departamento se obtiene
            // siempre por JOIN desde la ciudad.
            py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioCliente =
                    servicioClientes.obtenerDomicilio(conexion, cliente.getIdPersona());
            py.com.capitalcredito.sgcpc.modelos.Ciudad ciudadCliente = domicilioCliente == null ? null
                    : servicioClientes.buscarCiudadPorId(conexion, domicilioCliente.getIdCiudad());
            request.setAttribute("ciudadCliente", ciudadCliente == null ? null : ciudadCliente.getNombre());
            py.com.capitalcredito.sgcpc.modelos.Departamento departamentoCliente = ciudadCliente == null ? null
                    : servicioClientes.buscarDepartamentoPorId(conexion, ciudadCliente.getIdDepartamento());
            request.setAttribute("departamentoCliente", departamentoCliente == null ? null : departamentoCliente.getNombre());
            // RF07 (pedido de Joel, 10/10/2026): el checklist es una validación EN VIVO (contra la
            // política y los datos actuales del cliente) -- mostrarlo en una solicitud ya decidida
            // puede confundir una política que cambió después con la decisión real que se tomó en
            // su momento. Mismo criterio que RF06 ya aplicó a solicitudes/ficha.jsp (esBorrador):
            // solo se calcula y se muestra mientras la solicitud sigue PENDIENTE_APROBACION.
            if (solicitud.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.PENDIENTE_APROBACION) {
                // B122 (10/11/2026): con la solicitud, el checklist incluye además la capacidad
                // de pago -- el mismo ítem que ve el oficial antes de enviar.
                request.setAttribute("checklistCliente",
                        servicioSolicitudes.construirChecklistPrevioEnvio(conexion, cliente, solicitud));
            }
            // Bloque 7/E10, Tanda A (D-126): lista referencial para el <select> de rechazo.
            request.setAttribute("motivosRechazo", servicioAprobacionCredito.listarMotivosRechazo(conexion));
            // RF06 (pedido de Joel, 10/10/2026): lista fija para el <select> de "Cancelar solicitud".
            request.setAttribute("motivosCancelacion", servicioSolicitudes.listarMotivosCancelacion());
            // Ajuste 6 (Joel): quién decidió/canceló y cuándo.
            if (solicitud.getDecididoPor() != null) {
                request.setAttribute("usuarioDecisor", servicioUsuarios.buscarPorId(conexion, solicitud.getDecididoPor()));
            }
            if (solicitud.getCanceladoPor() != null) {
                request.setAttribute("usuarioCancelador", servicioUsuarios.buscarPorId(conexion, solicitud.getCanceladoPor()));
            }
            // B104 (01/11/2026, hallazgo de Joel): ESTA es la pantalla donde el gerente aprueba y
            // donde aparece "Solicitud aprobada. Ahora se formaliza en Formalización." -- y no
            // ofrecía ningún camino hasta ahí. El paso lo calcula ServicioFormalizacion (el dueño
            // del tramo), igual que en la ficha del oficial; el botón lo pinta el fragmento
            // compartido plantilla/paso_formalizacion.jsp, así el texto no vive en dos lugares.
            if (solicitud.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.APROBADA) {
                PasoGuiado pasoFormalizacion = servicioFormalizacion.calcularPasoDeLaSolicitudAprobada(
                        solicitud.getIdSolicitud(),
                        servicioFormalizacion.obtenerPorSolicitud(conexion, solicitud.getIdSolicitud()));
                request.setAttribute("pasoFormalizacion", pasoFormalizacion.conRuta(
                        pasoFormalizacion.rutaRelativa(cliente.getIdPersona(), cliente.getCedulaIdentidad())));
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la solicitud.", error);
            request.setAttribute("mensajeError", "No se pudo leer la solicitud.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }
}
