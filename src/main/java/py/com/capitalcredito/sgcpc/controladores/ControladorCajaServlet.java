package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.excepciones.CajaDeOtroDiaException;
import py.com.capitalcredito.sgcpc.excepciones.CajaYaAbiertaException;
import py.com.capitalcredito.sgcpc.excepciones.CajaYaCerradaException;
import py.com.capitalcredito.sgcpc.excepciones.DiferenciaYaRevisadaException;
import py.com.capitalcredito.sgcpc.excepciones.MotivoDiferenciaInsuficienteException;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.dto.FilaMovimientoCaja;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.modelos.SesionCaja;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioCaja;
import py.com.capitalcredito.sgcpc.servicios.ServicioUsuarios;

/**
 * P07, caja (Doc. 10 §10.10: ControladorCajaServlet / ServicioCaja). Protegido por
 * FiltroAutorizacion: ABRIR_CERRAR_CAJA (TOTAL cajera, CONSULTA gerente) para /caja y sus
 * sub-acciones de apertura/cierre; ENTREGAR_RECIBIR_EFECTIVO_CAJA (TOTAL para los dos perfiles)
 * para /caja/entregar -- la restricción fina de que solo el gerente entrega se valida en
 * ServicioCaja.registrarEntregaGerente (decisión 2 del plan de E6), no acá; AUTORIZAR_REVERSION_O_DIFERENCIA
 * (TOTAL solo gerente) para /caja/revisar-diferencia.
 */
public class ControladorCajaServlet extends HttpServlet {

    private static final String VISTA_FICHA = "/WEB-INF/vistas/caja/ficha.jsp";

    private final ServicioCaja servicioCaja = new ServicioCaja();
    private final ServicioUsuarios servicioUsuarios = new ServicioUsuarios();
    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarFicha(request, response);
            return;
        }
        // E8 (Bloque 2, D-94): reimpresión del arqueo -- /caja/{idSesion}/arqueo.
        String[] segmentosGet = pathInfo.substring(1).split("/");
        if (segmentosGet.length == 2 && "arqueo".equals(segmentosGet[1])) {
            Long idSesionCaja = idDeTexto(segmentosGet[0]);
            if (idSesionCaja == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            descargarArqueo(request, response, idSesionCaja);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void descargarArqueo(HttpServletRequest request, HttpServletResponse response, long idSesionCaja)
            throws IOException {
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        try {
            byte[] pdf = servicioCaja.obtenerPdfArqueo(idSesionCaja, solicitante.getIdUsuario());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"arqueo_" + idSesionCaja + ".pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
            response.sendRedirect(request.getContextPath() + "/caja");
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el arqueo de la sesión " + idSesionCaja + ".",
                    error);
            MensajesFlash.guardarError(sesion, "No se pudo generar el arqueo. Intente nuevamente.");
            response.sendRedirect(request.getContextPath() + "/caja");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession sesion = request.getSession();
        Usuario solicitante = (Usuario) sesion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        String[] segmentos = pathInfo == null ? new String[0] : pathInfo.substring(1).split("/");

        if (segmentos.length == 1 && "abrir".equals(segmentos[0])) {
            procesarAbrir(request, response, sesion, solicitante);
            return;
        }
        if (segmentos.length == 1 && "entregar".equals(segmentos[0])) {
            procesarEntregar(request, response, sesion, solicitante);
            return;
        }
        if (segmentos.length == 1 && "cerrar".equals(segmentos[0])) {
            procesarCerrar(request, response, sesion, solicitante);
            return;
        }
        if (segmentos.length == 2 && "revisar-diferencia".equals(segmentos[0])) {
            procesarRevisarDiferencia(request, response, sesion, solicitante, segmentos[1]);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void procesarAbrir(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante) throws IOException {
        try {
            BigDecimal saldoInicial = parsearMonto(request.getParameter("saldoInicial"));
            servicioCaja.abrirCaja(solicitante.getIdUsuario(), saldoInicial);
            MensajesFlash.guardarExito(sesion, "Caja abierta.");
        } catch (CajaYaAbiertaException error) {
            // A.8 (consolidación, pedido de Joel): carrera de dos pestañas -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al abrir la caja.", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/caja");
    }

    private void procesarEntregar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante) throws IOException {
        try {
            BigDecimal monto = parsearMonto(request.getParameter("monto"));
            servicioCaja.registrarEntregaGerente(solicitante.getIdUsuario(), solicitante.getPerfilUsuario(), monto,
                    request.getParameter("observacion"));
            MensajesFlash.guardarExito(sesion, "Entrega registrada.");
        } catch (CajaDeOtroDiaException error) {
            // A.8 (consolidación, pedido de Joel): bloqueo de tiempo -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al registrar la entrega de efectivo.", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/caja");
    }

    private void procesarCerrar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
            Usuario solicitante) throws IOException {
        String saldoFisicoTexto = request.getParameter("saldoFisico");
        String motivoDiferenciaTexto = request.getParameter("motivoDiferencia");
        try {
            BigDecimal saldoFisico = parsearMonto(saldoFisicoTexto);
            ServicioCaja.ResultadoCierreCaja resultado = servicioCaja.cerrarCaja(solicitante.getIdUsuario(), saldoFisico,
                    motivoDiferenciaTexto);
            MensajesFlash.guardarExito(sesion, construirMensajeCierre(resultado));
        } catch (MotivoDiferenciaInsuficienteException error) {
            // RF19 (pedido de Joel, 11/10/2026): advertencia (ícono y botón "Entendido"), no
            // error -- no es que algo salió mal, es que falta completar el motivo. Mismo
            // patrón que SolicitudYaDecididaException (RF09/RF10).
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
            guardarValoresCierre(sesion, saldoFisicoTexto, motivoDiferenciaTexto);
        } catch (CajaYaCerradaException error) {
            // RF19 (pedido de Joel, 11/10/2026): carrera de dos pestañas -- advertencia, no
            // error; no hace falta devolver los valores tecleados, la página siguiente ya no
            // tiene ninguna sesión abierta para seguir cerrando.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
            // A3b (#1, pedido de Joel): si el cierre se rechaza (ej. falta el motivo), devolver lo
            // que la cajera tecleó -- mismo patrón que guardarValoresCobro en la ficha de
            // cobranza (D-89, PRG); no cambia la regla del servidor (ServicioCaja.cerrarCaja
            // sigue exigiendo motivo si hay diferencia), solo evita que tenga que volver a
            // escribir el efectivo contado.
            guardarValoresCierre(sesion, saldoFisicoTexto, motivoDiferenciaTexto);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al cerrar la caja.", error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
            guardarValoresCierre(sesion, saldoFisicoTexto, motivoDiferenciaTexto);
        }
        response.sendRedirect(request.getContextPath() + "/caja");
    }

    /** A3b (#1, pedido de Joel): ver procesarCerrar -- mismo criterio que
     * ControladorCobranzaServlet.guardarValoresCobro. */
    private void guardarValoresCierre(HttpSession sesion, String saldoFisicoTexto, String motivoDiferenciaTexto) {
        Map<String, String> valores = new HashMap<>();
        valores.put("saldoFisico", saldoFisicoTexto == null ? "" : saldoFisicoTexto);
        valores.put("motivoDiferencia", motivoDiferenciaTexto == null ? "" : motivoDiferenciaTexto);
        MensajesFlash.guardarValores(sesion, valores);
    }

    private void procesarRevisarDiferencia(HttpServletRequest request, HttpServletResponse response,
            HttpSession sesion, Usuario solicitante, String textoIdSesion) throws IOException {
        Long idSesionCaja = idDeTexto(textoIdSesion);
        if (idSesionCaja == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            servicioCaja.marcarDiferenciaRevisada(idSesionCaja, solicitante.getIdUsuario(),
                    request.getParameter("observacion"));
            MensajesFlash.guardarExito(sesion, "Diferencia revisada.");
        } catch (DiferenciaYaRevisadaException error) {
            // A.8 (consolidación, pedido de Joel): carrera de dos pestañas -- advertencia, no error.
            MensajesFlash.guardarAdvertencia(sesion, error.getMessage());
        } catch (NegocioException error) {
            MensajesFlash.guardarError(sesion, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al revisar la diferencia de la caja " + idSesionCaja + ".",
                    error);
            MensajesFlash.guardarError(sesion, "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/caja");
    }

    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // D-146 (E-5, pedido de Joel): el botón "Reimprimir" de Movimientos solo se muestra a
        // quien de verdad tiene permiso (defensa en profundidad -- FiltroAutorizacion ya lo
        // bloquea en el servidor aunque esto fallara u otro perfil llegara a ver esta pantalla).
        Usuario usuarioCaja = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        request.setAttribute("puedeVerComprobantes", usuarioCaja != null
                && servicioAutorizacion.tienePermiso(usuarioCaja.getPerfilUsuario(), Permiso.VER_COMPROBANTES_COBRANZA));
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            SesionCaja sesionAbierta = servicioCaja.obtenerSesionAbierta(conexion);
            request.setAttribute("sesionAbierta", sesionAbierta);
            if (sesionAbierta == null) {
                request.setAttribute("fondoSugerido", servicioCaja.calcularFondoSugerido(conexion));
            } else {
                boolean esDeHoyCaja = servicioCaja.esDeHoy(sesionAbierta);
                request.setAttribute("esDeHoy", esDeHoyCaja);
                // Tanda de usabilidad (20/10/2026, pedido de Joel): mismo texto que Cobranza/Desembolso.
                if (!esDeHoyCaja) {
                    request.setAttribute("mensajeCajaOtroDia", servicioCaja.mensajeCajaDeOtroDia(sesionAbierta));
                }
                request.setAttribute("saldoTeorico",
                        servicioCaja.calcularSaldoTeorico(conexion, sesionAbierta.getIdSesionCaja()));
                // B191 (15/11/2026): contador y paginacion. La lista completa se trae igual (es
                // de UNA sesion, no de la historia), se cuenta y se corta la pagina aca.
                List<FilaMovimientoCaja> movimientosDeLaSesion =
                        servicioCaja.listarMovimientosConComprobante(conexion, sesionAbierta.getIdSesionCaja());
                request.setAttribute("movimientos",
                        recortarPagina(request, movimientosDeLaSesion, "paginaMovimientos",
                                "totalMovimientos", "totalPaginasMovimientos"));
            }
            List<SesionCaja> sesionesConDiferencia = servicioCaja.listarConDiferenciaSinRevisar(conexion);
            request.setAttribute("sesionesConDiferenciaSinRevisar", sesionesConDiferencia);
            // Mejora de Joel: la lista muestra qué cajera abrió/cerró cada sesión con diferencia.
            // Corrección de Joel (Bloque 2, revisión): el nombre de la persona (Sofía Martínez),
            // no el usuario de acceso (getNombreUsuario() = "cajera").
            Map<Long, String> nombreCajeroPorSesion = new HashMap<>();
            for (SesionCaja sesionConDiferencia : sesionesConDiferencia) {
                Usuario cajeroDeLaSesion = servicioUsuarios.buscarPorId(conexion, sesionConDiferencia.getIdCajero());
                nombreCajeroPorSesion.put(sesionConDiferencia.getIdSesionCaja(),
                        cajeroDeLaSesion == null ? "" : cajeroDeLaSesion.getNombres() + " " + cajeroDeLaSesion.getApellidos());
            }
            // E8 (Bloque 2): sesiones cerradas para reimprimir su arqueo -- por defecto las últimas
            // 10; con ?desde=&hasta= (Bloque 2, corrección de Joel: antes no había forma de llegar
            // a una sesión vieja) filtra por fecha de cierre en su lugar, sin límite de cantidad.
            String desdeTexto = request.getParameter("desde");
            String hastaTexto = request.getParameter("hasta");
            List<SesionCaja> sesionesCerradasRecientes = null;
            if (desdeTexto != null && !desdeTexto.trim().isEmpty() && hastaTexto != null && !hastaTexto.trim().isEmpty()) {
                try {
                    java.time.LocalDate desde = java.time.LocalDate.parse(desdeTexto);
                    java.time.LocalDate hasta = java.time.LocalDate.parse(hastaTexto);
                    sesionesCerradasRecientes = servicioCaja.listarCerradasEntreFechas(conexion, desde, hasta);
                    request.setAttribute("filtroDesde", desdeTexto);
                    request.setAttribute("filtroHasta", hastaTexto);
                } catch (java.time.format.DateTimeParseException error) {
                    // Fecha inválida escrita a mano en la URL: se ignora el filtro, no se rompe la
                    // pantalla -- vuelve al comportamiento por defecto (últimas 10).
                }
            }
            if (sesionesCerradasRecientes == null) {
                // B191 (15/11/2026): antes eran "las ultimas 10" y el resto solo se alcanzaba con el
                // filtro de fechas -- hoy son 136 sesiones. Ahora se traen todas, ordenadas de la
                // mas nueva a la mas vieja (el mismo ORDER BY que tenia el metodo con limite, asi
                // que la pagina 1 trae exactamente las que se veian antes) y la pantalla pagina.
                sesionesCerradasRecientes = servicioCaja.listarCerradasTodas(conexion);
            }
            sesionesCerradasRecientes = recortarPagina(request, sesionesCerradasRecientes,
                    "paginaSesiones", "totalSesionesCerradas", "totalPaginasSesiones");
            request.setAttribute("sesionesCerradasRecientes", sesionesCerradasRecientes);
            for (SesionCaja sesionCerrada : sesionesCerradasRecientes) {
                if (!nombreCajeroPorSesion.containsKey(sesionCerrada.getIdSesionCaja())) {
                    Usuario cajeroDeLaSesion = servicioUsuarios.buscarPorId(conexion, sesionCerrada.getIdCajero());
                    nombreCajeroPorSesion.put(sesionCerrada.getIdSesionCaja(),
                            cajeroDeLaSesion == null ? "" : cajeroDeLaSesion.getNombres() + " " + cajeroDeLaSesion.getApellidos());
                }
            }
            request.setAttribute("nombreCajeroPorSesion", nombreCajeroPorSesion);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer la caja.", error);
            request.setAttribute("mensajeError", "No se pudo leer la caja.");
        }
        // B191: las URL de las dos paginaciones las arma el controlador (ahi viven los parametros).
        request.setAttribute("urlPaginacionSesiones", urlPaginacionCaja(request, "paginaSesiones"));
        request.setAttribute("urlPaginacionMovimientos", urlPaginacionCaja(request, "paginaMovimientos"));
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }

    private BigDecimal parsearMonto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new NegocioException("Ingrese un monto.");
        }
        return ConversorNumeros.aBigDecimalGuaranies(texto);
    }

    /** Ajuste de Joel: resumen del cierre, redactado acá (el servicio solo devuelve los datos). */
    private String construirMensajeCierre(ServicioCaja.ResultadoCierreCaja resultado) {
        BigDecimal diferencia = resultado.getDiferencia();
        String etiquetaDiferencia;
        if (diferencia.signum() > 0) {
            etiquetaDiferencia = "sobrante";
        } else if (diferencia.signum() < 0) {
            etiquetaDiferencia = "faltante";
        } else {
            etiquetaDiferencia = "sin diferencia";
        }
        String mensaje = "Caja cerrada. Saldo teórico " + FormateadorNumeros.comoGuaranies(resultado.getSaldoTeorico())
                + ", efectivo contado " + FormateadorNumeros.comoGuaranies(resultado.getSaldoFisico())
                + ", diferencia " + FormateadorNumeros.comoGuaranies(diferencia) + " (" + etiquetaDiferencia + ").";
        if (diferencia.signum() != 0) {
            mensaje += " Queda pendiente de revisión del gerente.";
        }
        return mensaje;
    }

    /**
     * B191 (15/11/2026): cuantas filas por pagina. El mismo 20 que el resto del sistema
     * (B148/B167/B175/B183).
     */
    private static final int FILAS_POR_PAGINA = 20;

    /**
     * B191: corta la pagina de uno de los dos listados de esta pantalla y deja en el request su
     * numero de pagina, su total de paginas y su <b>total de filas</b> (el del listado completo, no
     * el de la pagina: con 136 sesiones, contar las dibujadas diria "20 encontradas").
     *
     * <p>Cada listado tiene su propio parametro ({@code paginaSesiones}, {@code paginaMovimientos}):
     * son dos en una sola pantalla, asi que un solo {@code pagina} los moveria a los dos -- la misma
     * razon que en Formalizacion (B183).</p>
     */
    private <T> List<T> recortarPagina(HttpServletRequest request, List<T> filas,
            String nombreParametroPagina, String atributoTotal, String atributoTotalPaginas) {
        int total = filas.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) FILAS_POR_PAGINA));
        int pagina = paginaPedida(request.getParameter(nombreParametroPagina), totalPaginas);
        int desde = (pagina - 1) * FILAS_POR_PAGINA;
        int hasta = Math.min(total, desde + FILAS_POR_PAGINA);
        request.setAttribute(nombreParametroPagina, Integer.valueOf(pagina));
        request.setAttribute(atributoTotal, Integer.valueOf(total));
        request.setAttribute(atributoTotalPaginas, Integer.valueOf(totalPaginas));
        return filas.subList(desde, hasta);
    }

    /** B191: la pagina pedida, acotada a lo que existe -- misma forma que en B175/B183. */
    private int paginaPedida(String texto, int totalPaginas) {
        int pagina = 1;
        if (texto != null && !texto.trim().isEmpty()) {
            try {
                pagina = Integer.parseInt(texto.trim());
            } catch (NumberFormatException error) {
                pagina = 1;
            }
        }
        if (pagina < 1) {
            pagina = 1;
        }
        return Math.min(pagina, Math.max(1, totalPaginas));
    }

    /**
     * B191: la URL base de cada paginacion de esta pantalla -- conserva el filtro de fechas y la
     * pagina del OTRO listado, y siempre termina en "?" o "&" (el contrato de
     * plantilla/paginacion.jsp). Asi mover un listado no mueve al otro.
     */
    private String urlPaginacionCaja(HttpServletRequest request, String parametroPropio) {
        StringBuilder query = new StringBuilder();
        for (String nombre : new String[] {"desde", "hasta", "paginaSesiones", "paginaMovimientos"}) {
            if (nombre.equals(parametroPropio)) {
                continue;
            }
            String valor = request.getParameter(nombre);
            if (valor == null || valor.trim().isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            try {
                query.append(nombre).append('=').append(java.net.URLEncoder.encode(valor, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                // UTF-8 siempre esta en la JVM; nunca deberia pasar.
            }
        }
        return request.getContextPath() + "/caja?" + (query.length() == 0 ? "" : query + "&");
    }

    private Long idDeTexto(String texto) {
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
