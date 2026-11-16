package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dto.PasoGuiado;
import py.com.capitalcredito.sgcpc.dto.ResultadoCapacidadPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P04, solicitud y evaluación crediticia (Doc. 10 §10.7: ControladorSolicitudesServlet /
 * ServicioSolicitudes). Protegido por FiltroAutorizacion (GESTIONAR_SOLICITUD, solo
 * OFICIAL_CREDITO tiene TOTAL -- D-60).
 */
public class ControladorSolicitudesServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/solicitudes/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/solicitudes/_resultados.jsp";
    private static final String VISTA_NUEVA = "/WEB-INF/vistas/solicitudes/nueva.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/solicitudes/ficha.jsp";

    /**
     * B163 (14/11/2026): el punto de retorno de la lista de solicitudes, guardado en la sesión
     * para que el "Volver a la lista" de la ficha devuelva la búsqueda y el filtro de estado que
     * el oficial tenía, en vez de la lista sin filtrar. Mismo nombre y mismo criterio que
     * RETORNO_LISTA_EN_SESION de ControladorClientesServlet (B147), pero con su propia clave:
     * las dos listas se recuerdan por separado.
     */
    private static final String RETORNO_LISTA_EN_SESION = "retornoListaSolicitudes";

    /**
     * B167 (14/11/2026): cuántas solicitudes por página. El mismo 20 que Clientes
     * (CLIENTES_POR_PAGINA): si las dos pantallas pagina(ra)n distinto, la primera pregunta de
     * Joel sería por qué.
     */
    private static final int SOLICITUDES_POR_PAGINA = 20;

    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    // B104 (01/11/2026): para saber si la solicitud aprobada ya se formalizó y a dónde lleva el
    // paso siguiente. Solo lectura; no agrega ninguna acción a esta pantalla.
    private final ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();
    // B88 (29/10/2026): el enlace a la ficha del cliente abre la pestaña del primer paso pendiente.
    private final py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado servicioPasoGuiado =
            new py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado();
    private final py.com.capitalcredito.sgcpc.servicios.ServicioParametros servicioParametros =
            new py.com.capitalcredito.sgcpc.servicios.ServicioParametros();
    // RF06 (pedido de Joel, 10/10/2026): glosario de TEA -- mismo criterio que ControladorCotizadorServlet.
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response, null, null);
        } else if ("/nuevo".equals(pathInfo)) {
            mostrarNueva(request, response, null);
        } else if ("/previsualizar-afectacion".equals(pathInfo)) {
            previsualizarAfectacion(request, response);
        } else {
            Long idSolicitud = idDeSegmento(pathInfo, 1);
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

        if ("/nuevo".equals(pathInfo)) {
            procesarCrear(request, response, solicitante);
            return;
        }
        if (pathInfo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long idSolicitud = idDeSegmento(pathInfo, 1);
        if (idSolicitud == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            if (pathInfo.endsWith("/simular")) {
                BigDecimal monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
                int plazoMeses = ConversorNumeros.aEntero(request.getParameter("plazoMeses"));
                String destino = request.getParameter("destinoCredito");
                LocalDate fecha = parsearFecha(request.getParameter("primeraFechaVencimiento"));
                servicioSolicitudes.simular(idSolicitud, monto, plazoMeses, destino, fecha);
                MensajesFlash.guardarExito(sesion, "Simulación actualizada.");
            } else if (pathInfo.endsWith("/enviar")) {
                servicioSolicitudes.enviarAAprobacion(idSolicitud, solicitante.getIdUsuario());
                MensajesFlash.guardarExito(sesion, "Solicitud enviada a aprobación. "
                        + "Ahora la revisa el gerente en Aprobación.");
            } else if (pathInfo.endsWith("/cancelar")) {
                servicioSolicitudes.cancelar(idSolicitud, request.getParameter("codigoMotivoCancelacion"),
                        request.getParameter("detalleCancelacion"), solicitante.getIdUsuario());
                MensajesFlash.guardarExito(sesion, "Solicitud cancelada.");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/solicitudes/" + idSolicitud);
    }

    private void procesarCrear(HttpServletRequest request, HttpServletResponse response, Usuario solicitante)
            throws ServletException, IOException {
        try {
            String cedulaCliente = request.getParameter("cedulaCliente");
            Persona cliente;
            SolicitudCredito abierta;
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                cliente = servicioClientes.buscarPorCedula(conexion, cedulaCliente);
                if (cliente == null) {
                    throw new NegocioException("No se encontró ninguna persona registrada con esa cédula. "
                            + "Regístrela primero en Clientes.");
                }
                // Ajuste 4 (Joel): el aviso de RN-02 incluye un enlace a la solicitud abierta --
                // el controlador arma el enlace (conoce el contextPath), el servicio solo expone
                // el dato.
                abierta = servicioSolicitudes.buscarAbiertaPorCliente(conexion, cliente.getIdPersona());
            }
            if (abierta != null) {
                // Ajuste 4 (Joel) + corrección (pedido de Joel, 10/10/2026): el mensaje nunca
                // lleva HTML (REGLAS_DESARROLLO.md, mismo criterio que plantilla/cabecera.jsp) --
                // antes el <a> quedaba como texto plano al mostrarse (SweetAlert2 lee
                // .textContent, que descarta cualquier etiqueta). El enlace real se arma aparte,
                // igual que mensajeExitoImprimibles para los mensajes de éxito.
                mostrarNueva(request, response, "El cliente ya tiene una solicitud abierta. "
                                + "Ver solicitud " + abierta.getCodigoSolicitud() + ".",
                        java.util.Collections.singletonMap(abierta.getCodigoSolicitud(),
                                "/solicitudes/" + abierta.getIdSolicitud()));
                return;
            }
            BigDecimal monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
            int plazoMeses = ConversorNumeros.aEntero(request.getParameter("plazoMeses"));
            String destino = request.getParameter("destinoCredito");
            LocalDate fecha = parsearFecha(request.getParameter("primeraFechaVencimiento"));
            long idNuevo = servicioSolicitudes.crear(cliente.getIdPersona(), monto, plazoMeses, destino, fecha,
                    solicitante.getIdUsuario());
            response.sendRedirect(request.getContextPath() + "/solicitudes/" + idNuevo);
        } catch (NegocioException error) {
            mostrarNueva(request, response, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en /solicitudes/nuevo.", error);
            mostrarNueva(request, response, "No se pudo completar la operación. Intente nuevamente.");
        }
    }

    private LocalDate parsearFecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese la fecha estimada del primer vencimiento.");
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (RuntimeException error) {
            throw new NegocioException("\"" + texto + "\" no es una fecha válida.");
        }
    }

    /**
     * RF05 (pedido de Joel, 10/10/2026): indicador en vivo de afectación mientras el oficial
     * escribe el monto/plazo, antes de guardar nada -- mismo patrón liviano y de solo lectura que
     * ControladorClientesServlet.precalificar (texto plano, nunca un error 500, nunca bloquea
     * nada por sí solo). Acepta "idSolicitud" (re-simular una solicitud ya creada)
     * o "cedulaCliente" (antes de crear la primera vez). Responde
     * "<1 o 0>|<texto ya armado>" (1 = supera el límite, para pintarlo en rojo) o vacío si
     * todavía no hay datos suficientes para cotizar.
     */
    private void previsualizarAfectacion(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        String resultado = "";
        try {
            LocalDate fecha = parsearFechaOpcional(request.getParameter("primeraFechaVencimiento"));
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                // B161 (14/11/2026): el cliente se resuelve ANTES de parsear monto y plazo. Antes
                // se parseaba primero y, con el plazo todavía vacío, el RuntimeException salía por
                // el catch de abajo y la respuesta era cadena vacía: la pantalla de "Nueva
                // solicitud" no decía NI para quién era la solicitud NI que esa cédula no existe.
                Persona cliente = null;
                boolean porCedula = false;
                String idSolicitudTexto = request.getParameter("idSolicitud");
                if (idSolicitudTexto != null && !idSolicitudTexto.trim().isEmpty()) {
                    SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion,
                            Long.parseLong(idSolicitudTexto.trim()));
                    if (solicitud != null) {
                        cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
                    }
                } else {
                    String cedulaCliente = request.getParameter("cedulaCliente");
                    porCedula = cedulaCliente != null && !cedulaCliente.trim().isEmpty();
                    if (porCedula) {
                        cliente = servicioClientes.buscarPorCedula(conexion, cedulaCliente.trim());
                    }
                }
                // B161: con la cédula escrita y sin cliente detrás, se lo dice. Eso NO es un dato
                // que falta: es lo escrito que está mal, así que la pantalla lo pinta en rojo
                // (estado "X"), a diferencia del estado "C" de más abajo (B95).
                if (porCedula && cliente == null) {
                    response.getWriter().write("X|No hay ningún cliente registrado con esa cédula.");
                    return;
                }
                // B161: quién es, armado acá -- el JavaScript no redacta, solo pinta (B92).
                String textoCliente = cliente == null ? null
                        : "Cliente: " + cliente.getNombres() + " " + cliente.getApellidos()
                                + " — C.I. " + FormateadorTexto.comoCedulaConPuntos(cliente.getCedulaIdentidad());
                // B161: el monto y el plazo se parsean con su propia red. A medio escribir no son
                // un error que haya que mostrar: lo que corresponde es decir quién es el cliente y
                // esperar el resto.
                BigDecimal monto = null;
                int plazoMeses = 0;
                try {
                    monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
                    plazoMeses = ConversorNumeros.aEntero(request.getParameter("plazoMeses"));
                } catch (RuntimeException datosIncompletos) {
                    monto = null;
                }
                ServicioSolicitudes.PrevisualizacionAfectacion previsualizacion = servicioSolicitudes
                        .previsualizarAfectacion(conexion, cliente, monto, plazoMeses, fecha);
                if (previsualizacion == null) {
                    // B161: sin afectación para mostrar. Si al menos se sabe quién es, se dice
                    // (estado "C", en gris): es el dato que la pantalla nunca daba.
                    resultado = textoCliente == null ? "" : "C|" + textoCliente;
                } else {
                    ResultadoCapacidadPago capacidad = previsualizacion.getCapacidad();
                    BigDecimal limite = servicioParametros.obtenerValorDecimal(conexion, "credito.limite_afectacion");
                    boolean supera = capacidad.getPorcentajeAfectacion().compareTo(limite) > 0;
                    // RF05 (ajuste de Joel, 10/10/2026): cuota estimada + afectación con un
                    // decimal (comoPorcentaje da toda la precisión del cálculo, demasiado detalle
                    // para un indicador mientras se escribe) + el límite, este sí sin decimales de
                    // más (comoPorcentaje, igual que en el resto del sistema).
                    String texto = "Cuota estimada: " + FormateadorNumeros.comoGuaranies(previsualizacion.getCuotaEstimada())
                            + " · Afectación: " + FormateadorNumeros.comoPorcentajeUnDecimal(capacidad.getPorcentajeAfectacion())
                            + " (límite: " + FormateadorNumeros.comoPorcentaje(limite) + ")"
                            + (supera ? ". Baje el monto o alargue el plazo." : ".");
                    // B161: el cliente se nombra SOLO cuando se llegó por cédula, o sea en "Nueva
                    // solicitud", que es la pantalla que no lo decía en ninguna parte. En la ficha
                    // (rama de idSolicitud) el cliente ya tiene su propia tarjeta arriba: repetirlo
                    // ahí sería decir el mismo dato dos veces (B123), y por eso ese texto y esos
                    // estados quedan EXACTAMENTE como estaban.
                    if (porCedula && textoCliente != null) {
                        texto = textoCliente + " · " + texto;
                    }
                    resultado = (supera ? "1" : "0") + "|" + texto;
                }
            }
        } catch (RuntimeException | SQLException error) {
            resultado = "";
        }
        response.getWriter().write(resultado);
    }

    /**
     * B167: la página pedida, acotada a lo que existe. Fuera de rango (o basura) cae en 1 o en la
     * última: así un enlace viejo o un número tecleado a mano nunca deja la lista vacía sin
     * explicación.
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

    /**
     * B163: los filtros actuales de la lista como query string, para el "Volver a la lista" de la
     * ficha. **B167** lo reusa para la URL base de la paginación: son los mismos dos filtros. Son los dos que tiene esta pantalla (texto y estado); si no hay ninguno, devuelve
     * cadena vacía y el enlace queda igual que siempre.
     */
    private String construirRetornoLista(HttpServletRequest request) {
        StringBuilder retorno = new StringBuilder();
        for (String nombre : new String[] {"texto", "estado"}) {
            String valor = request.getParameter(nombre);
            if (valor == null || valor.trim().isEmpty()) {
                continue;
            }
            if (retorno.length() > 0) {
                retorno.append('&');
            }
            try {
                retorno.append(nombre).append('=').append(java.net.URLEncoder.encode(valor, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                // UTF-8 siempre está en la JVM; nunca debería pasar.
            }
        }
        return retorno.toString();
    }

    private LocalDate parsearFechaOpcional(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Long idDeSegmento(String pathInfo, int indiceSegmento) {
        String[] segmentos = pathInfo.substring(1).split("/");
        if (indiceSegmento > segmentos.length) {
            return null;
        }
        try {
            return Long.parseLong(segmentos[indiceSegmento - 1]);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void mostrarLista(HttpServletRequest request, HttpServletResponse response, String mensajeExito,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        // Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 3): buscador
        // con el mismo mecanismo que Clientes/Mora/Aprobaciones (listado-vivo.js + PeticionAjax.esAjax).
        String textoBusqueda = request.getParameter("texto");
        request.setAttribute("textoBusqueda", textoBusqueda);
        // B163 (14/11/2026): punto de retorno de la lista, en la sesión. Lo escribe SOLO este
        // método y lo sirve SOLO mostrarFicha (un dato, un dueño): la JSP no lo arma ni lo
        // recalcula. Mismo mecanismo que B147 en Clientes, y en la sesión por el mismo motivo --
        // así sobrevive a los redirect que vuelven a /solicitudes/{id} sin parámetros. El pedido
        // AJAX del "listado vivo" pasa por acá igual, así que buscar en vivo lo actualiza.
        request.getSession().setAttribute(RETORNO_LISTA_EN_SESION, construirRetornoLista(request));
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        // Ajuste 3 (Joel): filtro por estado en la bandeja.
        String estadoTexto = request.getParameter("estado");
        EstadoSolicitud estadoFiltro = null;
        if (estadoTexto != null && !estadoTexto.isEmpty()) {
            try {
                estadoFiltro = EstadoSolicitud.valueOf(estadoTexto);
            } catch (IllegalArgumentException error) {
                estadoFiltro = null;
            }
        }
        request.setAttribute("estadoFiltro", estadoFiltro == null ? "" : estadoFiltro.name());
        // B167 (14/11/2026): el controlador NO decide el orden, solo pasa los parámetros crudos.
        // La lista blanca y la regla del sentido (sin columna pedida, el de siempre; con columna y
        // sin sentido, ascendente) viven en ServicioSolicitudes, que es su único dueño -- la
        // primera versión tenía esa regla partida en dos y el JUnit la agarró ordenando al revés.
        String orden = ServicioSolicitudes.ordenAplicado(request.getParameter("orden"));
        String sentido = ServicioSolicitudes.sentidoAplicado(
                request.getParameter("orden"), request.getParameter("sentido"));
        request.setAttribute("ordenActual", orden);
        request.setAttribute("sentidoActual", sentido);
        request.setAttribute("queryStringFiltros", construirRetornoLista(request));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<SolicitudCredito> solicitudes = servicioSolicitudes.listarPorOficial(conexion,
                    solicitante.getIdUsuario(), estadoFiltro);
            Map<Long, Persona> clientesPorSolicitud = clientesPorSolicitud(conexion, solicitudes);
            // B167: filtrar, después ordenar la lista COMPLETA, y recién entonces cortar la
            // página. El orden va antes del corte a propósito: ordenar la página ya cortada
            // mostraría "la primera por monto" cuando es la primera de esa página (B157).
            // El filtro de texto sigue siendo en memoria, como desde el 20/10/2026, y por eso la
            // paginación también lo es: con LIMIT/OFFSET en SQL, ese filtro se aplicaría DESPUÉS
            // del corte y las páginas saldrían con menos filas de las que dicen.
            List<SolicitudCredito> filtradas = servicioSolicitudes.filtrarPorTexto(
                    solicitudes, clientesPorSolicitud, textoBusqueda);
            List<SolicitudCredito> ordenadas = servicioSolicitudes.ordenarParaListado(
                    filtradas, clientesPorSolicitud, orden, sentido);
            int total = ordenadas.size();
            int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) SOLICITUDES_POR_PAGINA));
            int pagina = paginaPedida(request.getParameter("pagina"), totalPaginas);
            int desde = (pagina - 1) * SOLICITUDES_POR_PAGINA;
            int hasta = Math.min(total, desde + SOLICITUDES_POR_PAGINA);
            request.setAttribute("solicitudes", ordenadas.subList(desde, hasta));
            request.setAttribute("pagina", pagina);
            request.setAttribute("totalPaginas", totalPaginas);
            request.setAttribute("totalResultados", total);
            request.setAttribute("clientesPorSolicitud", clientesPorSolicitud);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la lista de solicitudes.", error);
            request.setAttribute("mensajeError", "No se pudo leer la lista de solicitudes.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /** Ajuste 3 (Joel): columna Cliente en la bandeja -- una búsqueda por cada cliente distinto, no por fila. */
    private Map<Long, Persona> clientesPorSolicitud(Connection conexion, List<SolicitudCredito> solicitudes)
            throws SQLException {
        Map<Long, Persona> clientes = new HashMap<>();
        for (SolicitudCredito solicitud : solicitudes) {
            if (!clientes.containsKey(solicitud.getIdCliente())) {
                clientes.put(solicitud.getIdCliente(), servicioClientes.buscarPorId(conexion, solicitud.getIdCliente()));
            }
        }
        return clientes;
    }

    private void mostrarNueva(HttpServletRequest request, HttpServletResponse response, String mensajeError)
            throws ServletException, IOException {
        mostrarNueva(request, response, mensajeError, null);
    }

    /**
     * Corrección (pedido de Joel, 10/10/2026): overload con enlaces para el mensaje de error --
     * mismo patrón que MensajesFlash.guardarExito(sesion, mensaje, documentosImprimibles), pero
     * de solo lectura por forward (no hace falta sesión/flash: este controlador siempre llega
     * acá por forward, nunca por redirect). "enlaces" es texto visible -> ruta relativa (sin
     * contextPath: plantilla/cabecera.jsp se lo agrega); plantilla/pie.jsp reemplaza ese texto,
     * donde aparezca dentro del mensaje, por un enlace real.
     */
    private void mostrarNueva(HttpServletRequest request, HttpServletResponse response, String mensajeError,
            Map<String, String> enlaces) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        if (enlaces != null && !enlaces.isEmpty()) {
            request.setAttribute("mensajeErrorEnlaces", enlaces);
        }
        // Bloque 7/E10 (D-132, pedido de Joel): el paso ④ del expediente del cliente ("Solicitud")
        // enlaza acá con la cédula ya en la URL -- precarga el campo para no tener que volver a
        // escribirla/pegarla.
        request.setAttribute("cedulaClientePrellenada", request.getParameter("cedulaCliente"));
        // B169 (14/11/2026): los límites de la política, para que la pantalla los diga ANTES de que
        // el servidor rechace. Mismo servicio, mismas claves de parametros_sistema y mismo rango de
        // fechas que ya usa el Cotizador (ControladorCotizadorServlet:147-159): ningún parámetro
        // nuevo y ninguna cuenta nueva, para que las dos pantallas no puedan decir cosas distintas.
        // Si la lectura falla, la pantalla simplemente no muestra las ayudas: el formulario y la
        // validación del servidor siguen igual.
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("montoMinimo", servicioParametros.obtenerValorDecimal(conexion, "credito.monto_minimo"));
            request.setAttribute("montoMaximo", servicioParametros.obtenerValorDecimal(conexion, "credito.monto_maximo"));
            request.setAttribute("plazoMinimo", servicioParametros.obtenerValorEntero(conexion, "credito.plazo_minimo"));
            request.setAttribute("plazoMaximo", servicioParametros.obtenerValorEntero(conexion, "credito.plazo_maximo"));
            request.setAttribute("rangoFechas",
                    servicioFormalizacion.calcularRangoPrimerVencimiento(conexion, Reloj.hoy()));
        } catch (SQLException error) {
            getServletContext().log("No se pudieron leer los límites de la política para el alta de solicitud.", error);
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_NUEVA);
        dispatcher.forward(request, response);
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, long idSolicitud)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // B163: el "Volver a la lista" devuelve a la búsqueda que el oficial tenía. Vacío si
        // todavía no pasó por la lista en esta sesión: entonces va a /solicitudes, como siempre.
        Object retornoLista = request.getSession().getAttribute(RETORNO_LISTA_EN_SESION);
        request.setAttribute("urlVolverLista", retornoLista == null ? "" : retornoLista.toString());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            SolicitudCredito solicitud = servicioSolicitudes.obtenerPorId(conexion, idSolicitud);
            if (solicitud == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            Persona cliente = servicioClientes.buscarPorId(conexion, solicitud.getIdCliente());
            // Ajuste 1 (Joel): total a pagar EXACTO (suma del plan), no una aproximación.
            ResultadoCotizacion resultadoCotizacion = servicioSolicitudes.recalcularPlan(conexion, solicitud);
            // RF06 (pedido de Joel, 10/10/2026): ícono ⓘ de TEA en el glosario -- mismo criterio
            // que ControladorCotizadorServlet (el texto completo de TEA menciona el tope vigente).
            LimiteTasaUsura limiteVigente = limiteTasaUsuraDAO.obtenerVigente(conexion);
            if (limiteVigente != null) {
                request.setAttribute("topeTeaVigente", limiteVigente.getTasaMaximaTea());
            }
            request.setAttribute("solicitud", solicitud);
            request.setAttribute("cliente", cliente);
            // Normalización de personas, grupo Laborales (16/10/2026): ingreso/comprobante de
            // ingreso ya no están en Persona -- la ficha los necesita para mostrarlos, se
            // resuelven acá con el mismo método único de siempre.
            py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralCliente =
                    servicioClientes.obtenerLaboral(conexion, cliente.getIdPersona());
            request.setAttribute("laboralCliente", laboralCliente);
            request.setAttribute("resultadoCotizacion", resultadoCotizacion);
            boolean esBorrador = solicitud.getEstadoSolicitud() == EstadoSolicitud.BORRADOR;
            request.setAttribute("esBorrador", esBorrador);
            // B104 (01/11/2026, hallazgo de Joel): el cartel de éxito promete "Ahora se formaliza
            // en Formalización", pero la ficha de la solicitud aprobada no ofrecía ningún camino
            // hasta ahí. Mismo DTO del flujo guiado que B88 (PasoGuiado); el cálculo vive en
            // ServicioFormalizacion, que es el dueño de este tramo. El botón NO cambia ningún
            // permiso: /formalizacion sigue protegido por FiltroAutorizacion.
            if (solicitud.getEstadoSolicitud() == EstadoSolicitud.APROBADA) {
                PasoGuiado pasoFormalizacion = servicioFormalizacion.calcularPasoDeLaSolicitudAprobada(
                        solicitud.getIdSolicitud(),
                        servicioFormalizacion.obtenerPorSolicitud(conexion, solicitud.getIdSolicitud()));
                request.setAttribute("pasoFormalizacion",
                        pasoFormalizacion.conRuta(pasoFormalizacion.rutaRelativa(cliente.getIdPersona(),
                                cliente.getCedulaIdentidad())));
            }
            // B88 (hallazgo de Joel en la prueba del 29/10/2026): el enlace "Ver ficha completa /
            // documentos" abría siempre ① Datos, aunque lo que faltara fuera un documento. Ahora
            // abre la ficha en la pestaña del primer paso pendiente -- mismo dueño de la regla que
            // usa el resto del circuito (ServicioClientes.calcularSiguientePaso, vía
            // ServicioPasoGuiado), nunca un criterio propio de esta pantalla.
            request.setAttribute("anclaFichaCliente",
                    servicioPasoGuiado.calcular(conexion, cliente.getIdPersona()).anclaPestanaFicha());
            // Bloque 7/E10 (D-129, Obs. 19/22): checklist (✓ / falta) antes de "Enviar a
            // aprobación" -- solo tiene sentido mientras la solicitud sigue en BORRADOR.
            if (esBorrador) {
                request.setAttribute("checklistPrevioEnvio",
                        servicioSolicitudes.construirChecklistPrevioEnvio(conexion, cliente, solicitud));
                // B122 (10/11/2026): la afectación de HOY para la tabla de condiciones -- el
                // mismo objeto que acaba de usar el checklist, no un cálculo propio de la
                // pantalla. Con la solicitud ya enviada la ficha sigue mostrando la foto
                // guardada, que es la base sobre la que se decidió.
                if (solicitud.getCuotaSimulada() != null) {
                    request.setAttribute("afectacionVigente", servicioSolicitudes.calcularAfectacionVigente(
                            conexion, solicitud.getCuotaSimulada(), laboralCliente));
                }
                // RF06 (pedido de Joel, 10/10/2026): lista fija para el <select> de "Cancelar solicitud".
                request.setAttribute("motivosCancelacion", servicioSolicitudes.listarMotivosCancelacion());
            }
            // Bloque 7/E10, Tanda A (D-126): aviso (no bloquea) si la cédula del cliente ya venció.
            request.setAttribute("cedulaClienteVencida", cliente.getFechaVencimientoCedula() != null
                    && cliente.getFechaVencimientoCedula().isBefore(Reloj.hoy()));
            // Ajuste 6 (Joel): quién decidió/canceló y cuándo.
            if (solicitud.getDecididoPor() != null) {
                request.setAttribute("usuarioDecisor", servicioUsuarios.buscarPorId(conexion, solicitud.getDecididoPor()));
            }
            if (solicitud.getCanceladoPor() != null) {
                request.setAttribute("usuarioCancelador", servicioUsuarios.buscarPorId(conexion, solicitud.getCanceladoPor()));
            }
            // RF06 (pedido de Joel, 10/10/2026): franja "Enviada por... Espera la decisión del
            // gerente", igual criterio visual que la de cancelada/aprobada/rechazada.
            if (solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_APROBACION) {
                Long idUsuarioEnvio = servicioSolicitudes.obtenerIdUsuarioQueEnvio(conexion, idSolicitud);
                if (idUsuarioEnvio != null) {
                    request.setAttribute("usuarioEnvio", servicioUsuarios.buscarPorId(conexion, idUsuarioEnvio));
                }
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la solicitud.", error);
            request.setAttribute("mensajeError", "No se pudo leer la solicitud.");
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }
}
