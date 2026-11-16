package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.filtros.FiltroAutenticacion;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;
import py.com.capitalcredito.sgcpc.infraestructura.ExportadorCsv;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.MensajesFlash;
import py.com.capitalcredito.sgcpc.infraestructura.PeticionAjax;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.SolicitudCredito;
import py.com.capitalcredito.sgcpc.modelos.Usuario;
import py.com.capitalcredito.sgcpc.servicios.ServicioAutorizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioClientes;
import py.com.capitalcredito.sgcpc.servicios.ServicioConsultaCrediticia;
import py.com.capitalcredito.sgcpc.servicios.ServicioDocumentos;
import py.com.capitalcredito.sgcpc.servicios.ServicioInformes;
import py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes;

/**
 * P03, ficha del cliente y sub-acciones de consentimiento/consulta al buró
 * (Doc. 10 §10.19: ControladorClientesServlet / ServicioClientes /
 * ServicioConsultaCrediticia — Doc. 10 no nombra un controlador aparte para
 * consentimiento/buró). Protegido por FiltroAutorizacion: GET exige
 * REGISTRAR_EDITAR_CLIENTE con nivel CONSULTA o TOTAL (la cajera ve, no
 * edita); POST exige TOTAL.
 */
public class ControladorClientesServlet extends HttpServlet {

    private static final String VISTA_LISTA = "/WEB-INF/vistas/clientes/lista.jsp";
    private static final String VISTA_RESULTADOS_LISTA = "/WEB-INF/vistas/clientes/_resultados.jsp";
    private static final String VISTA_FICHA = "/WEB-INF/vistas/clientes/ficha.jsp";

    /**
     * B147 (14/11/2026, revisión UX de Clientes): punto de retorno de la lista, en la sesión.
     * Lo escribe SOLO mostrarLista y lo sirve SOLO mostrarFicha (un dato, un dueño): la JSP no
     * arma ni recalcula nada, solo dibuja el href. Va en la sesión y no en la URL de la ficha
     * porque así sobrevive al redirect de después de guardar (que vuelve a /clientes/{id} sin
     * parámetros), que es justo cuando más se nota perder la búsqueda.
     */
    private static final String RETORNO_LISTA_EN_SESION = "retornoListaClientes";

    /** Bloque 7/E10, Tanda B (D-127): mismo orden que el <select> de situación laboral en la ficha.jsp. */
    private static final String[] SITUACIONES_LABORALES_EN_ORDEN = {
        "ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO", "JUBILADO_PENSIONADO", "INDEPENDIENTE",
        "COMERCIANTE", "EMPLEADO_DOMESTICO", "DESEMPLEADO", "OTRO"
    };

    private final ServicioClientes servicioClientes = new ServicioClientes();
    private final ServicioDocumentos servicioDocumentos = new ServicioDocumentos();
    private final ServicioConsultaCrediticia servicioConsultaCrediticia = new ServicioConsultaCrediticia();
    private final py.com.capitalcredito.sgcpc.servicios.ServicioParametros servicioParametros =
            new py.com.capitalcredito.sgcpc.servicios.ServicioParametros();
    private final ServicioAutorizacion servicioAutorizacion = new ServicioAutorizacion();
    private final ServicioInformes servicioInformes = new ServicioInformes();
    private final ServicioSolicitudes servicioSolicitudes = new ServicioSolicitudes();
    // B88 (pedido de Joel, 28/10/2026): junta el estado del cliente para el único dueño de la
    // regla del siguiente paso (ServicioClientes.calcularSiguientePaso).
    private final py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado servicioPasoGuiado =
            new py.com.capitalcredito.sgcpc.servicios.ServicioPasoGuiado();
    // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): obtenerPorSolicitud
    // ya existe, reutilizado para el préstamo de cada fila de "Solicitudes y préstamos".
    private final py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion servicioFormalizacion =
            new py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion();
    // RF03 Parte 2 (pedido de Joel): resolver quién cargó la autorización firmada (Paso A de ③).
    private final py.com.capitalcredito.sgcpc.dao.UsuarioDAO usuarioDAO = new py.com.capitalcredito.sgcpc.dao.UsuarioDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            mostrarLista(request, response, null, null);
        } else if ("/nuevo".equals(pathInfo)) {
            mostrarFicha(request, response, null, null, null, null, null, null, null, null, null);
        } else if ("/informe".equals(pathInfo)) {
            generarInformePdf(request, response);
        } else if ("/exportar".equals(pathInfo)) {
            exportarCsv(request, response);
        } else if ("/verificar-cedula".equals(pathInfo)) {
            verificarCedula(request, response);
        } else if ("/precalificar".equals(pathInfo)) {
            precalificar(request, response);
        } else if (pathInfo.endsWith("/autorizacion-buro")) {
            // RF03 Parte 2 (pedido de Joel): GET, de solo lectura -- "Reimprimir autorización" y
            // el enlace "Abrir para imprimir" del modal de éxito. No toca numeradores ni
            // auditoria_operaciones (eso solo lo hace el POST, al generar).
            Usuario solicitanteReimpresion = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
            reimprimirAutorizacionBuroPdf(request, response, idDeSegmento(pathInfo, 1), solicitanteReimpresion);
        } else {
            Long idPersona = idDeSegmento(pathInfo, 1);
            if (idPersona == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            mostrarFicha(request, response, idPersona, null, null, null, null, null, null, null, null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);

        if ("/requisitos".equals(pathInfo)) {
            generarInformeRequisitos(request, response, solicitante);
            return;
        }
        // RF03/RF04 (pedido de Joel): POST, no GET -- cada llamada arma un número nuevo
        // (numeradores, nunca se repite), así que no puede ser una acción "de solo leer" que un
        // doble clic, un refresh o un prefetch del navegador puedan disparar de más.
        if (pathInfo != null && pathInfo.endsWith("/autorizacion-buro")) {
            procesarGenerarAutorizacionBuro(request, response, idDeSegmento(pathInfo, 1), solicitante);
            return;
        }
        // RF03/RF04 (pedido de Joel): se eliminó la ruta "/consentimiento" -- el único camino para
        // registrar el consentimiento es "Cargar autorización firmada"
        // (ServicioConsultaCrediticia.cargarAutorizacionFirmada, con la regla del mismo día).
        if (pathInfo != null && pathInfo.endsWith("/consulta-buro")) {
            procesarConsultaBuro(request, response, pathInfo, solicitante);
            return;
        }
        if (pathInfo != null && (pathInfo.endsWith("/baja") || pathInfo.endsWith("/reactivar"))) {
            procesarBajaOReactivar(request, response, pathInfo, solicitante);
            return;
        }

        // Encontrado en la prueba de E3: ante un rechazo (ej. cédula duplicada), el formulario
        // volvía vacío -- se perdía todo lo que la persona ya había escrito. Para "/nuevo" y
        // "/guardar" se arma la Persona con lo tipeado ANTES de llamar al servicio, así que si
        // falla, mostrarFicha puede volver a mostrar exactamente esos valores (nunca los vuelve
        // a leer de la base ni los deja en blanco). El éxito sí redirige (PRG, D-57): un F5
        // después de guardar ya no reenvía el POST.
        Persona intentada = null;
        // Normalización de personas, grupo Referencias (15/10/2026): mismo criterio que
        // "intentada" de arriba -- se arma ANTES de llamar al servicio, para poder volver a
        // mostrar exactamente lo tipeado si falla.
        List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> referenciasIntentadas = null;
        // Normalización de personas, grupo Cónyuge (16/10/2026): mismo criterio que
        // referenciasIntentadas de arriba.
        py.com.capitalcredito.sgcpc.modelos.PersonaConyuge conyugeIntentado = null;
        // Normalización de personas, grupo Domicilio (16/10/2026): mismo criterio que
        // conyugeIntentado de arriba. idDepartamentoDomicilioIntentado es el valor crudo del
        // <select> de Precalificación (nunca se guarda, solo valida que la ciudad le pertenezca).
        py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioIntentado = null;
        Long idDepartamentoDomicilioIntentado = null;
        // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que
        // domicilioIntentado de arriba.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralIntentado = null;
        try {
            if ("/nuevo".equals(pathInfo)) {
                intentada = armarPersonaDesdeRequest(request, null);
                referenciasIntentadas = armarReferenciasDesdeRequest(request);
                conyugeIntentado = armarConyugeDesdeRequest(request);
                domicilioIntentado = armarDomicilioDesdeRequest(request);
                idDepartamentoDomicilioIntentado = idLargoOpcional(request.getParameter("idDepartamento"));
                laboralIntentado = armarLaboralDesdeRequest(request);
                long idNuevo = servicioClientes.alta(intentada, referenciasIntentadas, conyugeIntentado,
                        domicilioIntentado, idDepartamentoDomicilioIntentado, laboralIntentado, solicitante.getIdUsuario());
                // Bloque 7/E10 (D-131, pedido de Joel): antes no avisaba nada al registrar un
                // cliente -- quedaba en silencio, redirigiendo directo a la ficha.
                // B88 (pedido de Joel, 28/10/2026): el siguiente paso ya no es un texto fijo, sale
                // del mismo dueño que el resto del circuito -- con el orden "opción B" (29/10), a un
                // cliente nuevo le pide primero la autorización del buró, no "sus documentos".
                String nombreCompletoNuevo = intentada.getNombres() + " " + intentada.getApellidos();
                py.com.capitalcredito.sgcpc.dto.PasoGuiado pasoAlta = pasoGuiadoDePersona(idNuevo);
                MensajesFlash.guardarExito(request.getSession(), nombreCompletoNuevo
                        + " (C.I. " + FormateadorTexto.comoCedulaConPuntos(intentada.getCedulaIdentidad())
                        + ") se registró correctamente." + (pasoAlta.tieneTexto() ? " " + pasoAlta.getTexto() : ""));
                response.sendRedirect(request.getContextPath() + "/clientes/" + idNuevo);
            } else if (pathInfo != null && pathInfo.endsWith("/guardar")) {
                long idPersona = idDeSegmento(pathInfo, 1);
                intentada = armarPersonaDesdeRequest(request, idPersona);
                referenciasIntentadas = armarReferenciasDesdeRequest(request);
                conyugeIntentado = armarConyugeDesdeRequest(request);
                domicilioIntentado = armarDomicilioDesdeRequest(request);
                idDepartamentoDomicilioIntentado = idLargoOpcional(request.getParameter("idDepartamento"));
                laboralIntentado = armarLaboralDesdeRequest(request);
                // F2 (21/10/2026, pedido de Joel, punto 7): los 2 permisos ya resueltos acá (nunca
                // por rol) -- el servicio todavía no sabe cuál de los 2 casos de EvaluacionEnCurso
                // va a aplicar si el guardado queda bloqueado.
                List<String> motivosNoCalifica = servicioClientes.actualizar(intentada, referenciasIntentadas,
                        conyugeIntentado, domicilioIntentado, idDepartamentoDomicilioIntentado, laboralIntentado,
                        solicitante.getIdUsuario(),
                        nivelAccesoSesion(request, Permiso.APROBAR_RECHAZAR_SOLICITUD) != NivelAcceso.NINGUNO,
                        nivelAccesoSesion(request, Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO) != NivelAcceso.NINGUNO);
                // RF02 (pedido de Joel): la edición SIEMPRE guarda -- la política de crédito nunca
                // la bloquea. Si el cliente, tal como quedó, ya no calificaría para una solicitud
                // nueva, un solo aviso AMARILLO que dice que se guardó Y por qué, nunca el modal
                // rojo de error (eso sería contradictorio: si guardó, no fue un error).
                if (!motivosNoCalifica.isEmpty()) {
                    MensajesFlash.guardarAdvertencia(request.getSession(), construirMensajeNoCalifica(motivosNoCalifica));
                } else {
                    // RF03 (pedido de Joel), Parte 1: aviso aparte (no entra en "no califica") --
                    // si hay varios, se juntan en un solo aviso amarillo (nunca más de un flash).
                    List<String> avisosAparteEdicion = new java.util.ArrayList<>();
                    String avisoCedulaVencidaEdicion = avisoCedulaVencidaSiAplica(intentada);
                    if (avisoCedulaVencidaEdicion != null) {
                        avisosAparteEdicion.add(avisoCedulaVencidaEdicion);
                    }
                    if (avisosAparteEdicion.isEmpty()) {
                        py.com.capitalcredito.sgcpc.dto.PasoGuiado pasoEdicion = pasoGuiadoDePersona(idPersona);
                        MensajesFlash.guardarExito(request.getSession(), pasoEdicion.tieneTexto()
                                ? "Ficha actualizada. " + pasoEdicion.getTexto() : "Ficha actualizada.");
                    } else {
                        MensajesFlash.guardarAdvertencia(request.getSession(), "Datos guardados. Atención: "
                                + String.join(" ", avisosAparteEdicion));
                    }
                }
                response.sendRedirect(request.getContextPath() + "/clientes/" + idPersona);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (NegocioException error) {
            Long idPersona = idParaVolverAMostrar(pathInfo);
            // RF03 Parte 4 (pedido de Joel), punto 5: título corto + el motivo como detalle --
            // pie.jsp ya separa "Frase corta. Detalle." en título/cuerpo (dividirEnTituloYDetalle),
            // pero el motivo de rechazo (ej. cédula vencida) suele ser una sola oración sin ese
            // patrón, así que sin este prefijo todo el mensaje se volvía el título grande. No se
            // toca el texto de la excepción (se reutiliza en otros lugares, ej. el indicador en
            // vivo de Precalificación): el prefijo se agrega solo acá, según de qué acción vino.
            String prefijoErrorAlta = "/nuevo".equals(pathInfo) ? "No se puede registrar. "
                    : (pathInfo != null && pathInfo.endsWith("/guardar")) ? "No se pudo guardar. " : "";
            // Tanda 2 (14/10/2026, pedido de Joel): dato mal cargado es advertencia ("!"), no
            // error del sistema (X roja) -- el prefijo de arriba ya deja el título corto, solo
            // cambia el canal.
            request.setAttribute("mensajeAdvertencia", prefijoErrorAlta + error.getMessage());
            mostrarFicha(request, response, idPersona, intentada, referenciasIntentadas, conyugeIntentado,
                    domicilioIntentado, idDepartamentoDomicilioIntentado, laboralIntentado, null, null);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            Long idPersona = idParaVolverAMostrar(pathInfo);
            mostrarFicha(request, response, idPersona, intentada, referenciasIntentadas, conyugeIntentado,
                    domicilioIntentado, idDepartamentoDomicilioIntentado, laboralIntentado, null,
                    "No se pudo completar la operación. Intente nuevamente.");
        }
    }

    /**
     * Consulta al buró (D-57): a diferencia de "/nuevo"/"/guardar", siempre redirige (éxito o
     * error) a la pestaña de Buró de la ficha -- no es un formulario largo con valores que
     * preservar como la ficha (D-53), así que además del PRG (un F5 ya no repite el POST ni
     * duplica la consulta), la respuesta siempre vuelve a la pestaña donde estaba el usuario en
     * vez de a la de Ficha (punto 3 del pedido de Joel). RF03/RF04 (pedido de Joel): ya no hay
     * una rama de "/consentimiento" acá -- se eliminó esa ruta, el consentimiento se registra
     * solo desde "Cargar autorización firmada" (ServicioConsultaCrediticia.cargarAutorizacionFirmada).
     */
    private void procesarConsultaBuro(HttpServletRequest request, HttpServletResponse response, String pathInfo,
            Usuario solicitante) throws IOException {
        long idPersona = idDeSegmento(pathInfo, 1);
        // B88 (pedido de Joel, 28/10/2026): registrar el resultado también lleva al primer paso
        // pendiente -- con mora (RN-18) no empuja a ningún lado y lo dice; si falta un papel, a ②;
        // si está todo, a la solicitud. Antes se quedaba siempre en ③, sin decir qué seguía.
        String destinoConsulta = "/clientes/" + idPersona + "#tabBuro";
        try {
            procesarConsulta(request, idPersona, solicitante.getIdUsuario());
            // Corrección de Joel (RF08, 11/10/2026): título corto ("Consulta registrada") y el
            // mismo resumen que ya muestra la tarjeta de la ficha ("410 · E · con mora") -- antes
            // decía solo "Resultado de la consulta registrado.", sin ningún dato.
            py.com.capitalcredito.sgcpc.dto.PasoGuiado pasoConsulta = pasoGuiadoDePersona(idPersona);
            MensajesFlash.guardarExito(request.getSession(), "Consulta registrada. "
                    + resumenConsultaParaCartel(request)
                    + (pasoConsulta.tieneTexto() ? ". " + pasoConsulta.getTexto() : ""));
            if (pasoConsulta.getRuta() != null) {
                destinoConsulta = pasoConsulta.getRuta();
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(request.getSession(), error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + destinoConsulta);
    }

    /**
     * RF02 (pedido de Joel), punto c: "Asunción y Central" (2 valores), "Asunción, Central y X"
     * (3+) -- para la ayuda bajo el campo Departamento. Nunca un texto fijo: arma la lista que
     * venga de credito.departamentos_habilitados, sean cuales sean sus valores hoy.
     */
    private String construirListaConY(List<String> valores) {
        if (valores.isEmpty()) {
            return "";
        }
        if (valores.size() == 1) {
            return valores.get(0);
        }
        return String.join(", ", valores.subList(0, valores.size() - 1)) + " y " + valores.get(valores.size() - 1);
    }

    /**
     * RF02 (pedido de Joel): arma el texto único del aviso amarillo tras una edición que deja al
     * cliente sin calificar para una solicitud nueva -- "Datos guardados" (nunca "Error") seguido
     * del motivo. Con un solo motivo, va en la misma oración; con varios, lista con viñetas
     * (mismo criterio de \n + "- " que ya usa ServicioSolicitudes.enviarAAprobacion para sus
     * mensajes de varios puntos, que pie.jsp ya sabe separar en título + lista).
     */
    private String construirMensajeNoCalifica(List<String> motivosNoCalifica) {
        String encabezado = "Datos guardados. Atención: este cliente no califica para nuevas solicitudes";
        if (motivosNoCalifica.size() == 1) {
            return encabezado + ": " + motivosNoCalifica.get(0);
        }
        StringBuilder texto = new StringBuilder(encabezado + ":");
        for (String motivo : motivosNoCalifica) {
            texto.append("\n- ").append(motivo);
        }
        return texto.toString();
    }

    /**
     * RF02 (pedido de Joel), punto g: verificación de C.I. al salir del campo, sin esperar a
     * completar todo el formulario -- mismo patrón liviano que GET /utilidades/letras (solo
     * lectura, sin exponer nada que un oficial con acceso a Clientes no pueda ya ver buscando por
     * cédula en la lista). Respuesta de texto plano, sin JSON (no hay ninguna librería de JSON en
     * el proyecto -- REGLAS_DESARROLLO.md §3: nada nuevo sin aprobación de Joel): vacía si la cédula no está
     * registrada (o es la de la propia persona que se está editando, vía idPersonaActual), o
     * "idPersona|Nombre Apellido" si ya pertenece a otra. El servidor sigue validando igual al
     * guardar (esto es solo para avisar antes, nunca reemplaza esa validación).
     */
    private void verificarCedula(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        String texto = "";
        String cedulaConsultada = request.getParameter("cedula");
        if (cedulaConsultada != null && !cedulaConsultada.trim().isEmpty()) {
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                Persona encontrada = servicioClientes.buscarPorCedula(conexion, cedulaConsultada);
                if (encontrada != null) {
                    String idPersonaActualTexto = request.getParameter("idPersonaActual");
                    boolean esLaMismaPersona = idPersonaActualTexto != null
                            && String.valueOf(encontrada.getIdPersona()).equals(idPersonaActualTexto.trim());
                    if (!esLaMismaPersona) {
                        texto = encontrada.getIdPersona() + "|" + encontrada.getNombres() + " " + encontrada.getApellidos();
                    }
                }
            } catch (SQLException error) {
                getServletContext().log("Error de base de datos al verificar la cédula.", error);
            }
        }
        response.getWriter().write(texto);
    }

    /**
     * Mejora de pantalla (Precalificación, pedido de Joel): el indicador en vivo de la caja de
     * Precalificación -- mismo patrón liviano y de solo lectura que verificarCedula, texto plano
     * sin JSON. Vacío si califica (o si todavía faltan datos por completar -- el JS del navegador
     * ya no llama a esto hasta tener los 4 campos, pero si llegara incompleta no hay forma de
     * evaluar, así que responde vacío, nunca un error 500 que confunda al indicador), o el motivo
     * si no califica. Puramente informativo: el bloqueo real sigue siendo
     * ServicioClientes.alta/validarPoliticaCreditoAlta -- esta ruta nunca decide nada por sí sola.
     */
    private void precalificar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        String motivo = "";
        try {
            String fechaTexto = request.getParameter("fechaNacimiento");
            String idDepartamentoTexto = request.getParameter("idDepartamento");
            String situacionLaboral = request.getParameter("situacionLaboral");
            String presentaComprobanteTexto = request.getParameter("presentaComprobante");
            if (fechaTexto != null && !fechaTexto.trim().isEmpty()
                    && idDepartamentoTexto != null && !idDepartamentoTexto.trim().isEmpty()
                    && situacionLaboral != null && !situacionLaboral.trim().isEmpty()
                    && presentaComprobanteTexto != null) {
                LocalDate fechaNacimiento = LocalDate.parse(fechaTexto.trim());
                long idDepartamento = Long.parseLong(idDepartamentoTexto.trim());
                boolean presentaComprobante = "true".equals(presentaComprobanteTexto);
                try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                    String motivoNoCalifica = servicioClientes.evaluarPrecalificacion(conexion, fechaNacimiento,
                            idDepartamento, situacionLaboral, presentaComprobante);
                    motivo = motivoNoCalifica == null ? "" : motivoNoCalifica;
                }
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al evaluar la precalificación.", error);
        } catch (RuntimeException error) {
            // Fecha o id de departamento todavía no son válidos (a medio escribir) -- no se puede
            // evaluar aún, se responde vacío igual que "sin datos", nunca un error.
        }
        response.getWriter().write(motivo);
    }

    /**
     * Mejora de pantalla (Precalificación, pedido de Joel): botón "Imprimir requisitos" -- PDF
     * informativo que no persiste nada, ni antes ni después de guardar el cliente (no hace falta
     * que exista todavía). Único dato obligatorio: la situación laboral (el botón del navegador
     * recién se habilita cuando se elige una); nombre y cédula son opcionales, con líneas para
     * completar a mano si todavía no se escribieron (mismo criterio que la cotización).
     */
    private void generarInformeRequisitos(HttpServletRequest request, HttpServletResponse response, Usuario solicitante)
            throws IOException {
        String situacionLaboral = request.getParameter("situacionLaboral");
        if (situacionLaboral == null || situacionLaboral.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta la situación laboral.");
            return;
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            byte[] pdf = servicioClientes.generarInformeRequisitosPdf(conexion, situacionLaboral,
                    request.getParameter("nombreCompleto"), request.getParameter("cedulaIdentidad"),
                    solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"requisitos.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el PDF de requisitos.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "No se pudo generar el PDF. Intente nuevamente.");
        }
    }

    /**
     * RF03 (pedido de Joel), Parte 1: aviso claro, SIN bloquear, de que la cédula ya venció --
     * la edición siempre guarda (igual que el resto de la política de crédito, D-57/RF02); el
     * alta en cambio SÍ se bloquea (validarPoliticaCreditoAlta), así que esta nunca aplica ahí.
     * Texto exacto pedido por Joel. Pura (ServicioClientes.cedulaVencida ya es pura): null si no
     * aplica.
     */
    private String avisoCedulaVencidaSiAplica(Persona persona) {
        if (!servicioClientes.cedulaVencida(persona.getFechaVencimientoCedula(), LocalDate.now())) {
            return null;
        }
        return "La cédula venció el " + FormateadorFechas.comoDdMmAaaa(persona.getFechaVencimientoCedula())
                + ". El cliente debe renovarla antes de solicitar un crédito.";
    }

    /**
     * B88 (pedido de Joel, 28/10/2026): el primer paso pendiente del cliente, leído con los datos
     * ya guardados. Lo decide {@code ServicioClientes.calcularSiguientePaso} (el único dueño de la
     * regla) sobre el estado que junta {@code ServicioPasoGuiado} -- antes esta clase tenía su
     * propia versión ("siguientePasoParaEdicion"), y la carga de documentos otra, cada una con su
     * orden y sus textos. Lo usan el alta, la edición de la ficha, el registro del resultado del
     * buró y la propia ficha (para los botones "Siguiente: …").
     *
     * <p>Si falla la lectura (poco probable, son solo SELECT), no bloquea el aviso de éxito de la
     * acción: devuelve un paso sin texto ni destino.</p>
     */
    private py.com.capitalcredito.sgcpc.dto.PasoGuiado pasoGuiadoDePersona(long idPersona) {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return servicioPasoGuiado.calcular(conexion, idPersona);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al calcular el siguiente paso del cliente.", error);
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(null,
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.NINGUNO, null);
        }
    }

    /** Bloque 7/E10 (D-129, Obs. 31): baja lógica / reactivación -- ambas exclusivas del gerente. */
    private void procesarBajaOReactivar(HttpServletRequest request, HttpServletResponse response, String pathInfo,
            Usuario solicitante) throws IOException {
        long idPersona = idDeSegmento(pathInfo, 1);
        try {
            if (pathInfo.endsWith("/baja")) {
                // RF06 (pedido de Joel, 10/10/2026): mismo criterio que RF05 con "Ver solicitud
                // ..." -- el controlador arma el enlace real aparte (el mensaje de error nunca
                // lleva HTML); ServicioClientes.darDeBaja repite el mismo chequeo como red de
                // seguridad del servidor (sin enlace, para cuando se fuerza el POST).
                SolicitudCredito solicitudAbiertaBaja;
                try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                    solicitudAbiertaBaja = servicioSolicitudes.buscarAbiertaPorCliente(conexion, idPersona);
                }
                if (solicitudAbiertaBaja != null) {
                    // Corrección (pedido de Joel, 10/10/2026): el código tiene que caer en el
                    // DETALLE del cartel, no en el título -- SweetAlert2 siempre muestra el
                    // título como texto plano (nunca HTML), así que un enlace puesto ahí nunca se
                    // ve clickeable. dividirEnTituloYDetalle corta en el primer ". Mayúscula", y
                    // "Cancélela primero" después del primer punto asegura que el código
                    // (dentro de la oración siguiente) quede del lado del detalle.
                    MensajesFlash.guardarError(request.getSession(),
                            "No se puede dar de baja: el cliente tiene una solicitud abierta. "
                                    + "Cancélela primero: Ver solicitud " + solicitudAbiertaBaja.getCodigoSolicitud() + ".",
                            Collections.singletonMap(solicitudAbiertaBaja.getCodigoSolicitud(),
                                    "/solicitudes/" + solicitudAbiertaBaja.getIdSolicitud()));
                    response.sendRedirect(request.getContextPath() + "/clientes/" + idPersona);
                    return;
                }
                servicioClientes.darDeBaja(idPersona, request.getParameter("motivoBaja"),
                        request.getParameter("detalleBaja"), solicitante.getPerfilUsuario(), solicitante.getIdUsuario());
                MensajesFlash.guardarExito(request.getSession(), "Cliente dado de baja.");
            } else {
                servicioClientes.reactivar(idPersona, solicitante.getPerfilUsuario(), solicitante.getIdUsuario());
                MensajesFlash.guardarExito(request.getSession(), "Cliente reactivado.");
            }
        } catch (NegocioException error) {
            MensajesFlash.guardarError(request.getSession(), error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos en " + pathInfo + ".", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo completar la operación. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/clientes/" + idPersona);
    }

    private void procesarConsulta(HttpServletRequest request, long idPersona, long idUsuario) throws SQLException {
        String proveedor = request.getParameter("proveedorBuro");
        String puntajeTexto = request.getParameter("puntajeObtenido");
        // Punto 6 del pedido de Joel: el puntaje del buró es un número entero ("850", nunca
        // "850,5") -- aEntero rechaza cualquier valor con parte decimal con un mensaje claro.
        BigDecimal puntaje = (puntajeTexto == null || puntajeTexto.trim().isEmpty())
                ? null : BigDecimal.valueOf(ConversorNumeros.aEntero(puntajeTexto));
        String clasificacion = request.getParameter("clasificacionObtenida");
        String indicadorTexto = request.getParameter("indicadorMora");
        Boolean indicadorMora = (indicadorTexto == null || indicadorTexto.isEmpty())
                ? null : Boolean.valueOf(indicadorTexto);
        String resumen = request.getParameter("resumenResultado");
        // Ajuste D del plan de E3 (D-58): evidencia obligatoria del resultado (el informe de
        // Informconf, cargado antes como documento del expediente).
        String idDocumentoResultadoTexto = request.getParameter("idDocumentoResultado");
        Long idDocumentoResultado = (idDocumentoResultadoTexto == null || idDocumentoResultadoTexto.isEmpty())
                ? null : Long.parseLong(idDocumentoResultadoTexto);
        servicioConsultaCrediticia.registrarConsulta(idPersona, proveedor, puntaje, clasificacion, indicadorMora,
                resumen, idDocumentoResultado, idUsuario);
    }

    /** RF08 (pedido de Joel, 11/10/2026): "410 · E · con mora" -- mismo formato y mismas tres
     * leyendas de reserva ("sin puntaje"/"sin clasificación") que ya usa la tarjeta de la ficha
     * (clientes/ficha.jsp, "Consulta registrada:"), para el cartel de éxito. Relee los mismos
     * tres parámetros que procesarConsulta, del mismo request -- ya se sabe que son válidos
     * porque procesarConsulta no lanzó. */
    private String resumenConsultaParaCartel(HttpServletRequest request) {
        String puntajeTexto = request.getParameter("puntajeObtenido");
        String puntaje = (puntajeTexto == null || puntajeTexto.trim().isEmpty()) ? "sin puntaje"
                : py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros.comoEntero(
                        BigDecimal.valueOf(ConversorNumeros.aEntero(puntajeTexto)));
        String clasificacion = request.getParameter("clasificacionObtenida");
        String clasificacionTexto = (clasificacion == null || clasificacion.isEmpty()) ? "sin clasificación" : clasificacion;
        String indicadorTexto = request.getParameter("indicadorMora");
        String moraTexto = Boolean.TRUE.equals(indicadorTexto == null || indicadorTexto.isEmpty()
                ? null : Boolean.valueOf(indicadorTexto)) ? "con mora" : "sin mora";
        return puntaje + " · " + clasificacionTexto + " · " + moraTexto;
    }

    private Persona armarPersonaDesdeRequest(HttpServletRequest request, Long idPersona) {
        Persona persona = new Persona();
        persona.setIdPersona(idPersona);
        persona.setCedulaIdentidad(request.getParameter("cedulaIdentidad"));
        persona.setRuc(vacioANulo(request.getParameter("ruc")));
        persona.setNombres(request.getParameter("nombres"));
        persona.setApellidos(request.getParameter("apellidos"));
        String fechaNacimiento = request.getParameter("fechaNacimiento");
        persona.setFechaNacimiento(fechaNacimiento == null || fechaNacimiento.isEmpty()
                ? null : LocalDate.parse(fechaNacimiento));
        persona.setEstadoCivil(request.getParameter("estadoCivil"));
        persona.setTelefonoWhatsapp(request.getParameter("telefonoWhatsapp"));
        persona.setCorreoElectronico(vacioANulo(request.getParameter("correoElectronico")));
        persona.setTelefonoAlternativo(vacioANulo(request.getParameter("telefonoAlternativo")));
        // Bloque 7/E10, Tanda A (D-126)
        String fechaVencimientoCedula = request.getParameter("fechaVencimientoCedula");
        persona.setFechaVencimientoCedula(fechaVencimientoCedula == null || fechaVencimientoCedula.isEmpty()
                ? null : LocalDate.parse(fechaVencimientoCedula));
        return persona;
    }

    /**
     * Normalización de personas, grupo Laborales (16/10/2026): los mismos 9 campos del
     * formulario (situacionLaboral/empleadorNombre/empleadorRuc/empleadorDireccion/
     * empleadorTelefono/cargo/antiguedadLaboralAnios/ingresoNetoComprobado/
     * presentaComprobanteIngreso/otrosIngresosMensuales/gastosFijosMensuales/
     * cuotaOtrasDeudasMensual, nombres sin cambios) arman ahora un PersonaLaboral en vez de 12
     * setters sobre Persona. "lugarTrabajo" no tiene reemplazo -- columna legado eliminada, el
     * formulario ya no la envía.
     */
    private py.com.capitalcredito.sgcpc.modelos.PersonaLaboral armarLaboralDesdeRequest(HttpServletRequest request) {
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboral = new py.com.capitalcredito.sgcpc.modelos.PersonaLaboral();
        laboral.setSituacionLaboral(vacioANulo(request.getParameter("situacionLaboral")));
        laboral.setEmpleadorNombre(vacioANulo(request.getParameter("empleadorNombre")));
        laboral.setEmpleadorRuc(vacioANulo(request.getParameter("empleadorRuc")));
        laboral.setEmpleadorDireccion(vacioANulo(request.getParameter("empleadorDireccion")));
        laboral.setEmpleadorTelefono(vacioANulo(request.getParameter("empleadorTelefono")));
        laboral.setCargo(vacioANulo(request.getParameter("cargo")));
        String antiguedadLaboral = request.getParameter("antiguedadLaboralAnios");
        laboral.setAntiguedadLaboralAnios(antiguedadLaboral == null || antiguedadLaboral.isEmpty()
                ? null : Integer.valueOf(antiguedadLaboral));
        String ingreso = request.getParameter("ingresoNetoComprobado");
        laboral.setIngresoNetoComprobado(ingreso == null || ingreso.isEmpty()
                ? BigDecimal.ZERO : ConversorNumeros.aBigDecimalGuaranies(ingreso));
        laboral.setPresentaComprobanteIngreso("on".equals(request.getParameter("presentaComprobanteIngreso")));
        String otrosIngresos = request.getParameter("otrosIngresosMensuales");
        laboral.setOtrosIngresosMensuales(otrosIngresos == null || otrosIngresos.isEmpty()
                ? null : ConversorNumeros.aBigDecimalGuaranies(otrosIngresos));
        String gastosFijos = request.getParameter("gastosFijosMensuales");
        laboral.setGastosFijosMensuales(gastosFijos == null || gastosFijos.isEmpty()
                ? null : ConversorNumeros.aBigDecimalGuaranies(gastosFijos));
        String cuotaOtrasDeudas = request.getParameter("cuotaOtrasDeudasMensual");
        laboral.setCuotaOtrasDeudasMensual(cuotaOtrasDeudas == null || cuotaOtrasDeudas.isEmpty()
                ? null : ConversorNumeros.aBigDecimalGuaranies(cuotaOtrasDeudas));
        return laboral;
    }

    /** Normalización de personas, grupo Referencias (15/10/2026): los mismos 4 campos del
     * formulario (referenciaNombre/referenciaTelefono/referencia2Nombre/referencia2Telefono,
     * nombres sin cambios) arman ahora una lista de PersonaReferencia en vez de 4 setters sobre
     * Persona. */
    private List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> armarReferenciasDesdeRequest(
            HttpServletRequest request) {
        List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> referencias = new java.util.ArrayList<>();
        referencias.add(armarReferencia(1, vacioANulo(request.getParameter("referenciaNombre")),
                vacioANulo(request.getParameter("referenciaTelefono"))));
        referencias.add(armarReferencia(2, vacioANulo(request.getParameter("referencia2Nombre")),
                vacioANulo(request.getParameter("referencia2Telefono"))));
        return referencias;
    }

    private py.com.capitalcredito.sgcpc.modelos.PersonaReferencia armarReferencia(int numero, String nombre,
            String telefono) {
        py.com.capitalcredito.sgcpc.modelos.PersonaReferencia referencia =
                new py.com.capitalcredito.sgcpc.modelos.PersonaReferencia();
        referencia.setNumeroReferencia(numero);
        referencia.setNombre(nombre);
        referencia.setTelefono(telefono);
        return referencia;
    }

    /**
     * Normalización de personas, grupo Cónyuge (16/10/2026): los mismos 3 campos del formulario
     * (conyugeNombre/conyugeCedula/conyugeTelefono, nombres sin cambios) arman ahora un
     * PersonaConyuge en vez de 3 setters sobre Persona. Nunca null (mismo criterio que
     * armarReferenciasDesdeRequest: "null" en mostrarFicha significa "no es un reintento, hay que
     * leer de la base", no "sin cónyuge") -- los 3 campos quedan en null si vinieron vacíos.
     * ServicioClientes.validarConyuge decide, según el estado civil, si eso es válido o no.
     */
    private py.com.capitalcredito.sgcpc.modelos.PersonaConyuge armarConyugeDesdeRequest(HttpServletRequest request) {
        py.com.capitalcredito.sgcpc.modelos.PersonaConyuge conyuge = new py.com.capitalcredito.sgcpc.modelos.PersonaConyuge();
        conyuge.setNombre(vacioANulo(request.getParameter("conyugeNombre")));
        conyuge.setCedula(vacioANulo(request.getParameter("conyugeCedula")));
        conyuge.setTelefono(vacioANulo(request.getParameter("conyugeTelefono")));
        return conyuge;
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): los mismos 6 campos del formulario
     * (idCiudad/barrio/direccionDomicilio/referenciaDomicilio/tipoVivienda/
     * antiguedadDomicilioAnios, nombres sin cambios) arman ahora un PersonaDomicilio en vez de 6
     * setters sobre Persona. El "idDepartamento" del formulario (Precalificación) no forma parte
     * de PersonaDomicilio -- se lee aparte (ver doPost), solo para validarCiudadPerteneceADepartamento.
     */
    private py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio armarDomicilioDesdeRequest(HttpServletRequest request) {
        py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilio = new py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio();
        domicilio.setIdCiudad(idLargoOpcional(request.getParameter("idCiudad")));
        domicilio.setBarrio(vacioANulo(request.getParameter("barrio")));
        domicilio.setDireccionDomicilio(vacioANulo(request.getParameter("direccionDomicilio")));
        domicilio.setReferenciaDomicilio(vacioANulo(request.getParameter("referenciaDomicilio")));
        domicilio.setTipoVivienda(vacioANulo(request.getParameter("tipoVivienda")));
        String antiguedad = request.getParameter("antiguedadDomicilioAnios");
        domicilio.setAntiguedadDomicilioAnios(antiguedad == null || antiguedad.isEmpty()
                ? null : Integer.valueOf(antiguedad));
        return domicilio;
    }

    private Long idLargoOpcional(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? null : Long.valueOf(texto);
    }

    private String vacioANulo(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? null : texto;
    }

    private Long idDeSegmento(String pathInfo, int indiceSegmento) {
        String[] segmentos = pathInfo.substring(1).split("/");
        // Bug encontrado en la prueba de E3: con ">=" un pathInfo de un solo segmento (ej.
        // "/1", GET a la ficha) ya se rechazaba como fuera de rango (indiceSegmento=1,
        // segmentos.length=1), aunque el segmento pedido (indice 0) existe. Daba 404 siempre
        // al ver una ficha ya guardada; el alta funcionaba porque redirige, pero la pagina de
        // destino nunca cargaba. Las rutas de 2 segmentos (ej. "/1/guardar") no lo sufrian.
        if (indiceSegmento > segmentos.length) {
            return null;
        }
        try {
            return Long.parseLong(segmentos[indiceSegmento - 1]);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Long idParaVolverAMostrar(String pathInfo) {
        if (pathInfo == null || "/nuevo".equals(pathInfo)) {
            return null;
        }
        return idDeSegmento(pathInfo, 1);
    }

    private static final int CLIENTES_POR_PAGINA = 20;

    /**
     * Bloque 7/E10 (D-135, pedido de Joel): ABM completo con filtros unificados (texto, estado,
     * fecha de alta, departamento/ciudad, situación laboral, préstamo activo) -- la MISMA
     * consulta (ServicioClientes.listarConFiltros) que usan el informe PDF y el CSV.
     */
    private void mostrarLista(HttpServletRequest request, HttpServletResponse response, String mensajeExito,
            String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        boolean puedeEditarLista = puedeEditar(request);
        request.setAttribute("puedeEditar", puedeEditarLista);
        request.setAttribute("puedeDarDeBaja", esGerente(request));
        request.setAttribute("puedeEmitirInformes", puedeEmitirInformes(request));
        String filtroEstadoTexto = request.getParameter("estado");
        request.setAttribute("filtroEstado", filtroEstadoTexto == null ? "ACTIVOS" : filtroEstadoTexto);
        FiltroClientes filtro = construirFiltroDesdeRequest(request);
        request.setAttribute("filtro", filtro);
        String queryStringFiltros = construirQueryStringFiltros(request);
        request.setAttribute("queryStringFiltros", queryStringFiltros);
        // B157: el orden vigente, para que los encabezados de la tabla muestren por cuál columna
        // está ordenada y en qué sentido. Sale del filtro ya validado, no del parámetro crudo.
        request.setAttribute("ordenActual", filtro.getOrden());
        request.setAttribute("sentidoActual", filtro.getSentido());
        int pagina = idADeTexto(request.getParameter("pagina"), 1);
        // B147: se guarda la búsqueda actual (filtros + página) para que el "Volver" de la ficha
        // devuelva a ESTA pantalla. El pedido AJAX del "listado vivo" también pasa por acá, así
        // que una búsqueda en vivo actualiza el punto de retorno igual que una carga completa.
        StringBuilder retornoLista = new StringBuilder(queryStringFiltros);
        if (pagina > 1) {
            if (retornoLista.length() > 0) {
                retornoLista.append('&');
            }
            retornoLista.append("pagina=").append(pagina);
        }
        request.getSession().setAttribute(RETORNO_LISTA_EN_SESION, retornoLista.toString());
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<Persona> resultados = servicioClientes.listarConFiltros(conexion, filtro, pagina, CLIENTES_POR_PAGINA);
            int total = servicioClientes.contarConFiltros(conexion, filtro);
            request.setAttribute("resultados", resultados);
            request.setAttribute("pagina", pagina);
            request.setAttribute("totalPaginas", Math.max(1, (int) Math.ceil(total / (double) CLIENTES_POR_PAGINA)));
            request.setAttribute("totalResultados", total);
            request.setAttribute("departamentos", servicioClientes.listarDepartamentos(conexion));
            List<py.com.capitalcredito.sgcpc.modelos.Ciudad> ciudades = servicioClientes.listarCiudades(conexion);
            request.setAttribute("ciudades", ciudades);
            // RF02 (pedido de Joel): el filtro de situación laboral del listado ofrece las
            // admitidas por la política vigente MÁS cualquier otra que ya tenga algún cliente.
            request.setAttribute("situacionesLaboralesFiltro",
                    servicioClientes.listarSituacionesLaboralesParaFiltro(conexion));
            Map<Long, String> ciudadPorId = new HashMap<>();
            for (py.com.capitalcredito.sgcpc.modelos.Ciudad ciudad : ciudades) {
                ciudadPorId.put(ciudad.getIdCiudad(), ciudad.getNombre());
            }
            request.setAttribute("ciudadPorId", ciudadPorId);
            Map<Long, Integer> prestamosActivosPorId = new HashMap<>();
            List<Long> idsPersonaLista = new java.util.ArrayList<>();
            for (Persona fila : resultados) {
                prestamosActivosPorId.put(fila.getIdPersona(),
                        servicioClientes.contarPrestamosActivosDelCliente(conexion, fila.getIdPersona()));
                idsPersonaLista.add(fila.getIdPersona());
            }
            request.setAttribute("prestamosActivosPorId", prestamosActivosPorId);
            // Normalización de personas, grupo Domicilio (16/10/2026): la columna "Ciudad" de
            // _resultados.jsp ya no puede leer fila.getIdCiudad() (no existe más en Persona) --
            // este mapa la resuelve de una sola consulta.
            request.setAttribute("idCiudadPorPersona", servicioClientes.mapaIdCiudadPorPersona(conexion, idsPersonaLista));
            // Normalización de personas, grupo Laborales (16/10/2026): la columna "Situación
            // laboral" de _resultados.jsp ya no puede leer fila.getSituacionLaboral() (no existe
            // más en Persona) -- este mapa la resuelve de una sola consulta.
            request.setAttribute("situacionLaboralPorPersona",
                    servicioClientes.mapaSituacionLaboralPorPersona(conexion, idsPersonaLista));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al listar/buscar clientes.", error);
            request.setAttribute("mensajeError", "No se pudo leer la lista de clientes.");
        }
        // Bloque 7/E10 (D-136, pedido de Joel): "listado vivo" -- un pedido AJAX (mismo criterio
        // de PeticionAjax que ya usaba FiltroAutenticacion) recibe solo el fragmento de
        // resultados (contador + tabla + paginación), no la página completa con el formulario;
        // así el JS puede reemplazar ese pedazo del DOM sin recargar nada más. Misma mostrarLista,
        // mismos filtros, misma consulta -- no hay una ruta ni una lógica de filtrado aparte.
        RequestDispatcher dispatcher = request.getRequestDispatcher(
                PeticionAjax.esAjax(request) ? VISTA_RESULTADOS_LISTA : VISTA_LISTA);
        dispatcher.forward(request, response);
    }

    /**
     * Query string con los filtros actuales (sin "pagina"), para los enlaces de paginación del
     * fragmento de resultados -- sirven de respaldo sin JavaScript (href real) y el JS del
     * "listado vivo" los intercepta para pedir la página por AJAX en vez de navegar.
     */
    private String construirQueryStringFiltros(HttpServletRequest request) {
        String[] nombresFiltro = {"texto", "estado", "altaDesde", "altaHasta", "idDepartamento",
            "idCiudad", "situacionLaboral", "prestamoActivo"};
        StringBuilder queryString = new StringBuilder();
        for (String nombre : nombresFiltro) {
            String valor = request.getParameter(nombre);
            if (valor == null || valor.trim().isEmpty()) {
                continue;
            }
            if (queryString.length() > 0) {
                queryString.append('&');
            }
            try {
                queryString.append(nombre).append('=').append(java.net.URLEncoder.encode(valor, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                // UTF-8 siempre está disponible en la JVM; nunca debería pasar.
            }
        }
        return queryString.toString();
    }

    /** Mismos parámetros del formulario de filtros de /clientes -- reusado por la lista, el informe PDF y el CSV. */
    private FiltroClientes construirFiltroDesdeRequest(HttpServletRequest request) {
        FiltroClientes filtro = new FiltroClientes();
        filtro.setTexto(vacioANulo(request.getParameter("texto")));
        String filtroEstadoTexto = request.getParameter("estado");
        filtro.setEstado("INACTIVOS".equals(filtroEstadoTexto) ? "INACTIVA"
                : "TODOS".equals(filtroEstadoTexto) ? null : "ACTIVA");
        filtro.setAltaDesde(parsearFechaOpcional(request.getParameter("altaDesde")));
        filtro.setAltaHasta(parsearFechaOpcional(request.getParameter("altaHasta")));
        filtro.setIdDepartamento(idLargoOpcional(request.getParameter("idDepartamento")));
        filtro.setIdCiudad(idLargoOpcional(request.getParameter("idCiudad")));
        filtro.setSituacionLaboral(vacioANulo(request.getParameter("situacionLaboral")));
        String prestamoActivo = request.getParameter("prestamoActivo");
        filtro.setPrestamoActivo("SI".equals(prestamoActivo) || "NO".equals(prestamoActivo) ? prestamoActivo : null);
        // B157 (14/11/2026): el orden pedido, validado contra la lista blanca del DAO ANTES de
        // guardarlo -- lo que no está en esa lista se descarta acá y el DAO usa el orden de
        // siempre. Como este método es el único que arma el FiltroClientes y lo usan el listado,
        // el informe PDF y el CSV, los tres salen en el mismo orden (decisión de Joel).
        String ordenPedido = request.getParameter("orden");
        filtro.setOrden(py.com.capitalcredito.sgcpc.dao.PersonaDAO.esOrdenPermitido(ordenPedido)
                ? ordenPedido : py.com.capitalcredito.sgcpc.dao.PersonaDAO.ORDEN_POR_DEFECTO);
        filtro.setSentido("desc".equalsIgnoreCase(request.getParameter("sentido")) ? "desc" : "asc");
        return filtro;
    }

    private LocalDate parsearFechaOpcional(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private String etiquetaFiltroEstado(String filtroEstadoTexto) {
        return "INACTIVOS".equals(filtroEstadoTexto) ? "Inactivos" : "TODOS".equals(filtroEstadoTexto) ? "Todos" : "Activos";
    }

    /** Solo oficial/gerente emiten informes (Joel); la cajera sigue viendo la lista (CONSULTA), sin estos botones. */
    private boolean puedeEmitirInformes(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        return usuario != null && usuario.getPerfilUsuario() != PerfilUsuario.CAJERO;
    }

    /**
     * RF03 Parte 2 (pedido de Joel): botón "Generar"/"Generar autorización nueva" de la fila 1
     * del Expediente. POST (no GET: cada llamada arma un número nuevo que nunca se repite, no es
     * una acción "de solo leer") -- pero ya NO entrega el PDF directo: solo registra el número y
     * la auditoría (ServicioConsultaCrediticia.generarAutorizacionBuro) y redirige a la ficha con
     * el modal de éxito y el enlace "Abrir para imprimir" (mismo patrón que Formalización:
     * MensajesFlash.guardarExito con documentosImprimibles). El PDF en sí se ve recién con ese
     * enlace, vía reimprimirAutorizacionBuroPdf (GET, más abajo) -- "generar" y "ver/imprimir"
     * quedan separados, para poder reimprimir sin consumir otro número.
     */
    private void procesarGenerarAutorizacionBuro(HttpServletRequest request, HttpServletResponse response,
            Long idPersona, Usuario solicitante) throws IOException {
        if (idPersona == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!puedeCargarDocumentos(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para generar este documento.");
            return;
        }
        try {
            Persona persona;
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                persona = servicioClientes.buscarPorId(conexion, idPersona);
            }
            if (persona == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String numero = servicioConsultaCrediticia.generarAutorizacionBuro(persona, solicitante.getIdUsuario());
            Map<String, String> imprimibles = new java.util.LinkedHashMap<>();
            imprimibles.put("Abrir para imprimir", "/clientes/" + idPersona + "/autorizacion-buro");
            MensajesFlash.guardarExito(request.getSession(), "Autorización N.º " + numero + " generada. Siguiente "
                    + "paso: imprímala, que el cliente la firme, y cárguela escaneada.", imprimibles);
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar la autorización de consulta al buró.", error);
            MensajesFlash.guardarError(request.getSession(), "No se pudo generar la autorización. Intente nuevamente.");
        }
        response.sendRedirect(request.getContextPath() + "/clientes/" + idPersona + "#tabDocumentos");
    }

    /**
     * RF03 Parte 2 (pedido de Joel): "Reimprimir autorización" (generada hoy, todavía sin cargar)
     * y el enlace "Abrir para imprimir" del modal de éxito -- GET, de solo lectura: re-renderiza
     * el PDF con el número y la fecha de la ÚLTIMA generación (ServicioConsultaCrediticia.
     * reimprimirAutorizacionBuroPdf), sin tocar numeradores ni auditoria_operaciones.
     */
    private void reimprimirAutorizacionBuroPdf(HttpServletRequest request, HttpServletResponse response,
            Long idPersona, Usuario solicitante) throws IOException {
        if (idPersona == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!puedeCargarDocumentos(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para ver este documento.");
            return;
        }
        try {
            Persona persona;
            py.com.capitalcredito.sgcpc.dto.UltimaOperacionAuditoria ultimaGeneracion;
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                persona = servicioClientes.buscarPorId(conexion, idPersona);
                ultimaGeneracion = persona == null ? null
                        : servicioConsultaCrediticia.obtenerUltimaGeneracionAutorizacion(conexion, idPersona);
            }
            if (persona == null || ultimaGeneracion == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            byte[] pdf = servicioConsultaCrediticia.reimprimirAutorizacionBuroPdf(persona, ultimaGeneracion,
                    solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"autorizacion_buro.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al reimprimir la autorización de consulta al buró.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Bloque 7/E10 (D-135): "Informe PDF" -- documento formal con los mismos filtros del listado
     * (ServicioInformes.generarInformeClientesPdf: cabecera de empresa, filtros aplicados,
     * detalle, subtotales y auditoría). Se abre en pestaña nueva ("inline", no "attachment").
     */
    private void generarInformePdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!puedeEmitirInformes(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para emitir este informe.");
            return;
        }
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        FiltroClientes filtro = construirFiltroDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<Persona> resultados = servicioClientes.listarConFiltros(conexion, filtro, null, null);
            Map<Long, String> ciudadPorId = new HashMap<>();
            Map<Long, String> departamentoPorId = new HashMap<>();
            for (py.com.capitalcredito.sgcpc.modelos.Ciudad ciudad : servicioClientes.listarCiudades(conexion)) {
                ciudadPorId.put(ciudad.getIdCiudad(), ciudad.getNombre());
            }
            for (py.com.capitalcredito.sgcpc.modelos.Departamento departamento : servicioClientes.listarDepartamentos(conexion)) {
                departamentoPorId.put(departamento.getIdDepartamento(), departamento.getNombre());
            }
            Map<Long, Integer> prestamosActivosPorId = new HashMap<>();
            List<Long> idsPersona = new java.util.ArrayList<>();
            for (Persona fila : resultados) {
                prestamosActivosPorId.put(fila.getIdPersona(),
                        servicioClientes.contarPrestamosActivosDelCliente(conexion, fila.getIdPersona()));
                idsPersona.add(fila.getIdPersona());
            }
            // Normalización de personas, grupo Domicilio (16/10/2026): la ciudad de cada persona
            // ya no está en Persona -- se resuelve de una sola consulta a personas_domicilio.
            Map<Long, Long> idCiudadPorPersona = servicioClientes.mapaIdCiudadPorPersona(conexion, idsPersona);
            // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que la
            // ciudad -- la situación laboral se resuelve de una sola consulta a personas_laborales.
            Map<Long, String> situacionLaboralPorPersona =
                    servicioClientes.mapaSituacionLaboralPorPersona(conexion, idsPersona);
            String textoFiltros = servicioInformes.construirTextoFiltrosClientes(filtro,
                    etiquetaFiltroEstado(request.getParameter("estado")),
                    filtro.getIdDepartamento() == null ? null : departamentoPorId.get(filtro.getIdDepartamento()),
                    filtro.getIdCiudad() == null ? null : ciudadPorId.get(filtro.getIdCiudad()));
            byte[] pdf = servicioInformes.generarInformeClientesPdf(conexion, resultados, idCiudadPorPersona,
                    ciudadPorId, situacionLaboralPorPersona, prestamosActivosPorId, textoFiltros,
                    solicitante.getIdUsuario(), solicitante.getNombres() + " " + solicitante.getApellidos());
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline; filename=\"informe_clientes.pdf\"");
            try (java.io.OutputStream salida = response.getOutputStream()) {
                salida.write(pdf);
            }
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al generar el informe de clientes.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo generar el informe.");
        }
    }

    /** Bloque 7/E10 (D-135): CSV con las mismas columnas/filtros que el informe PDF. */
    private void exportarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!puedeEmitirInformes(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tiene permiso para exportar este listado.");
            return;
        }
        Usuario solicitante = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        FiltroClientes filtro = construirFiltroDesdeRequest(request);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            List<Persona> resultados = servicioClientes.listarConFiltros(conexion, filtro, null, null);
            Map<Long, String> ciudadPorId = new HashMap<>();
            Map<Long, String> departamentoPorId = new HashMap<>();
            for (py.com.capitalcredito.sgcpc.modelos.Ciudad ciudad : servicioClientes.listarCiudades(conexion)) {
                ciudadPorId.put(ciudad.getIdCiudad(), ciudad.getNombre());
            }
            for (py.com.capitalcredito.sgcpc.modelos.Departamento departamento : servicioClientes.listarDepartamentos(conexion)) {
                departamentoPorId.put(departamento.getIdDepartamento(), departamento.getNombre());
            }
            Map<Long, Integer> prestamosActivosPorId = new HashMap<>();
            List<Long> idsPersona = new java.util.ArrayList<>();
            for (Persona fila : resultados) {
                prestamosActivosPorId.put(fila.getIdPersona(),
                        servicioClientes.contarPrestamosActivosDelCliente(conexion, fila.getIdPersona()));
                idsPersona.add(fila.getIdPersona());
            }
            // Normalización de personas, grupo Domicilio (16/10/2026): mismo criterio que
            // generarInformePdf -- la ciudad de cada persona se resuelve de una sola consulta.
            Map<Long, Long> idCiudadPorPersona = servicioClientes.mapaIdCiudadPorPersona(conexion, idsPersona);
            // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que la
            // ciudad -- la situación laboral se resuelve de una sola consulta a personas_laborales.
            Map<Long, String> situacionLaboralPorPersona =
                    servicioClientes.mapaSituacionLaboralPorPersona(conexion, idsPersona);
            try (PrintWriter escritor = ExportadorCsv.abrir(response, "clientes.csv")) {
                escritor.println("C.I.;Apellidos y Nombres;Teléfono;Ciudad;Situación laboral;Fecha de alta;Préstamos activos;Estado");
                for (Persona fila : resultados) {
                    Long idCiudadFila = idCiudadPorPersona.get(fila.getIdPersona());
                    String ciudad = idCiudadFila == null ? "" : ciudadPorId.getOrDefault(idCiudadFila, "");
                    Integer prestamosActivos = prestamosActivosPorId.get(fila.getIdPersona());
                    // E-7 (pedido de Joel): C.I. y Teléfono marcados como texto -- Excel en
                    // español convertía "0985678901" en número y le borraba el 0 inicial.
                    escritor.println(String.join(";",
                            ExportadorCsv.comoTexto(FormateadorTexto.comoCedulaConPuntos(fila.getCedulaIdentidad())),
                            ExportadorCsv.comilla(fila.getApellidos() + " " + fila.getNombres()),
                            ExportadorCsv.comoTexto(fila.getTelefonoWhatsapp()),
                            ExportadorCsv.comilla(ciudad),
                            ExportadorCsv.comilla(ServicioClientes.etiquetaSituacionLaboral(
                                    situacionLaboralPorPersona.get(fila.getIdPersona()))),
                            fila.getFechaRegistro() == null ? "" : FormateadorFechas.comoDdMmAaaa(fila.getFechaRegistro().toLocalDate()),
                            String.valueOf(prestamosActivos == null ? 0 : prestamosActivos),
                            fila.getEstadoPersona().getEtiqueta()));
                }
            }
            String textoFiltros = servicioInformes.construirTextoFiltrosClientes(filtro,
                    etiquetaFiltroEstado(request.getParameter("estado")),
                    filtro.getIdDepartamento() == null ? null : departamentoPorId.get(filtro.getIdDepartamento()),
                    filtro.getIdCiudad() == null ? null : ciudadPorId.get(filtro.getIdCiudad()));
            servicioInformes.auditarExportacionCsvClientes(conexion, solicitante.getIdUsuario(), textoFiltros, resultados.size());
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al exportar clientes a CSV.", error);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No se pudo exportar el listado.");
        }
    }

    private int idADeTexto(String texto, int porDefecto) {
        try {
            return (texto == null || texto.trim().isEmpty()) ? porDefecto : Math.max(1, Integer.parseInt(texto));
        } catch (NumberFormatException error) {
            return porDefecto;
        }
    }

    private boolean esGerente(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        // D7 (pedido de Joel): el administrador también puede dar de baja/reactivar un cliente.
        return usuario != null
                && (usuario.getPerfilUsuario() == py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario.GERENTE_ADMINISTRADOR
                        || usuario.getPerfilUsuario() == py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario.ADMINISTRADOR);
    }

    /**
     * personaIntentada: si no es null, son los valores que la persona acaba de escribir en el
     * formulario (tras un rechazo de alta/edición) — se muestran tal cual, sin volver a leer la
     * ficha de la base (para no perder lo tipeado). Si es null, se lee la ficha de la base según
     * idPersona (o queda un formulario en blanco si idPersona también es null: "Nuevo cliente").
     */
    private void mostrarFicha(HttpServletRequest request, HttpServletResponse response, Long idPersona,
            Persona personaIntentada, List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> referenciasIntentadas,
            py.com.capitalcredito.sgcpc.modelos.PersonaConyuge conyugeIntentado,
            py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilioIntentado, Long idDepartamentoDomicilioIntentado,
            py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboralIntentado,
            String mensajeExito, String mensajeError) throws ServletException, IOException {
        Csrf.obtenerOCrearToken(request.getSession());
        // RF03 Parte 4 (pedido de Joel), punto c: el aviso en vivo de cédula vencida compara
        // contra ESTA fecha (la misma fuente que usa ServicioClientes.cedulaVencida en el
        // servidor, Reloj.hoy()) -- nunca el reloj del navegador, para que pantalla y servidor
        // nunca se contradigan, incluso con una fecha simulada (Reloj.fijarParaCargador).
        request.setAttribute("fechaHoyServidor", Reloj.hoy().toString());
        // B147 (14/11/2026): el "Volver" de la ficha devuelve a la búsqueda que el usuario tenía
        // en la lista (filtros y página), no a la lista sin filtros. Vacío si todavía no pasó por
        // la lista en esta sesión: entonces "Volver" va a /clientes, como siempre.
        Object retornoLista = request.getSession().getAttribute(RETORNO_LISTA_EN_SESION);
        request.setAttribute("urlVolverLista", retornoLista == null ? "" : retornoLista.toString());
        if (mensajeExito != null) {
            request.setAttribute("mensajeExito", mensajeExito);
        }
        if (mensajeError != null) {
            request.setAttribute("mensajeError", mensajeError);
        }
        request.setAttribute("puedeEditar", puedeEditar(request));
        request.setAttribute("puedeCargarDocumentos", puedeCargarDocumentos(request));
        request.setAttribute("puedeDarDeBaja", esGerente(request));
        // A pedido de Joel: el resultado de la consulta al buró es informacion crediticia
        // sensible. Un perfil con nivel NINGUNO en CARGAR_DOCUMENTOS_CONSULTA_BURO (la cajera)
        // no debe ver las pestañas "Expediente / Documentos" ni "Autorización y consulta al
        // buró" -- ni siquiera ocultas en el HTML -- asi que ademas de no mostrarlas en la JSP,
        // esos datos ni se consultan a la base cuando este perfil pide la ficha.
        boolean puedeVerDocumentosBuro = puedeVerDocumentosBuro(request);
        request.setAttribute("puedeVerDocumentosBuro", puedeVerDocumentosBuro);
        request.setAttribute("puedeGestionarSolicitud", puedeGestionarSolicitud(request));
        request.setAttribute("esNueva", idPersona == null);

        Persona persona = personaIntentada;
        // Normalización de personas, grupo Referencias (15/10/2026): si se vuelve a mostrar tras
        // un rechazo, se usan las que el usuario tipeó (referenciasIntentadas); si no, se leen de
        // personas_referencias más abajo, una vez que se sepa el idPersona.
        List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> referencias = referenciasIntentadas;
        // Normalización de personas, grupo Cónyuge (16/10/2026): mismo criterio que "referencias"
        // de arriba.
        py.com.capitalcredito.sgcpc.modelos.PersonaConyuge conyuge = conyugeIntentado;
        // Normalización de personas, grupo Domicilio (16/10/2026): mismo criterio que "conyuge"
        // de arriba -- se lee de personas_domicilio más abajo, una vez que se sepa el idPersona.
        py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio domicilio = domicilioIntentado;
        // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que "domicilio"
        // de arriba -- se lee de personas_laborales más abajo, una vez que se sepa el idPersona.
        py.com.capitalcredito.sgcpc.modelos.PersonaLaboral laboral = laboralIntentado;
        try (Connection conexionCatalogos = FabricaConexionBD.obtenerConexion()) {
            // Bloque 7/E10, Tanda A (D-126): ciudades de la ficha (cascada en el navegador, se
            // filtran solas por departamento en el JS -- no hace falta acotarlas acá). El
            // departamento (RF02: solo los habilitados) se arma más abajo, una vez resuelta la
            // persona, porque necesita saber cuál es su departamento actual.
            request.setAttribute("ciudades", servicioClientes.listarCiudades(conexionCatalogos));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer las ciudades.", error);
            request.setAttribute("mensajeError", "No se pudo leer la lista de departamentos y ciudades.");
        }
        // Bloque 7/E10, Tanda B (D-127): guía de documentos sugeridos por situación laboral (una
        // lista por cada una de las 8 opciones del <select>, para que el navegador cambie el aviso
        // sin ida y vuelta al servidor cuando el oficial cambia la situación laboral elegida).
        java.util.Map<String, java.util.List<String>> documentosSugeridosPorSituacion = new java.util.LinkedHashMap<>();
        for (String codigo : SITUACIONES_LABORALES_EN_ORDEN) {
            documentosSugeridosPorSituacion.put(codigo, servicioClientes.documentosSugeridosPorSituacionLaboral(codigo));
        }
        request.setAttribute("documentosSugeridosPorSituacion", documentosSugeridosPorSituacion);
        if (idPersona != null) {
            try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
                if (persona == null) {
                    persona = servicioClientes.buscarPorId(conexion, idPersona);
                    if (persona == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                }
                if (referencias == null) {
                    referencias = servicioClientes.listarReferencias(conexion, idPersona);
                }
                if (conyuge == null) {
                    conyuge = servicioClientes.obtenerConyuge(conexion, idPersona);
                }
                if (domicilio == null) {
                    domicilio = servicioClientes.obtenerDomicilio(conexion, idPersona);
                }
                if (laboral == null) {
                    laboral = servicioClientes.obtenerLaboral(conexion, idPersona);
                }
                // 2c (pedido de Joel, 17/10/2026): si hay una evaluación en curso (solicitud
                // pendiente/aprobada sin formalizar, o préstamo pendiente de desembolso), la
                // ficha deshabilita los 7 campos de evaluación y muestra el aviso -- la
                // validación real vive en el servidor (ServicioClientes.actualizar), esto es
                // solo para que la pantalla no deje escribir algo que el servidor va a
                // rechazar igual.
                py.com.capitalcredito.sgcpc.servicios.ServicioClientes.EvaluacionEnCurso evaluacionEnCurso =
                        servicioClientes.buscarEvaluacionEnCursoPorCliente(conexion, idPersona);
                request.setAttribute("evaluacionEnCurso", evaluacionEnCurso);
                if (evaluacionEnCurso != null) {
                    // F2 (21/10/2026, pedido de Joel, punto 7): versión (a)/(b) por el permiso real
                    // en sesión (nunca por nombre de rol) -- APROBAR_RECHAZAR_SOLICITUD o
                    // ANULAR_PRESTAMO_PRE_DESEMBOLSO según el caso (ver getPermisoParaResolver()).
                    boolean puedeResolverEvaluacionEnCurso =
                            nivelAccesoSesion(request, evaluacionEnCurso.getPermisoParaResolver()) != NivelAcceso.NINGUNO;
                    request.setAttribute("mensajeEvaluacionBloqueada",
                            servicioClientes.mensajeEvaluacionBloqueada(evaluacionEnCurso, puedeResolverEvaluacionEnCurso,
                                    py.com.capitalcredito.sgcpc.servicios.ServicioClientes.ContextoAvisoEvaluacion
                                            .FICHA_CON_CAMPOS_EN_GRIS));
                }
                // Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 ítem 14): mismo
                // compromiso abierto que ya bloquea crear una 2.ª solicitud (RN-01/RN-02,
                // ServicioSolicitudes.buscarCompromisoAbierto) -- evaluacionEnCurso de arriba no
                // alcanza para esto (deja afuera BORRADOR y préstamo ACTIVO/EN_MORA a propósito,
                // para otro fin). La pestaña ④ y el botón "Siguiente" lo usan para no prometer
                // "Nueva solicitud" cuando ya hay una abierta o un préstamo sin cerrar -- mismo
                // enlace que ya arma construirEnlaceHistorial para la pestaña "Solicitudes y
                // préstamos" (permiso real de sesión, nunca por rol; null si no hay ninguna
                // pantalla de destino a la que el usuario tenga acceso).
                py.com.capitalcredito.sgcpc.servicios.ServicioSolicitudes.CompromisoAbierto compromisoAbierto =
                        servicioSolicitudes.buscarCompromisoAbierto(conexion, idPersona);
                if (compromisoAbierto != null) {
                    SolicitudCredito solicitudCompromiso = compromisoAbierto.getSolicitud();
                    py.com.capitalcredito.sgcpc.modelos.PrestamoCredito prestamoCompromiso = compromisoAbierto.isViaPrestamo()
                            ? servicioFormalizacion.obtenerPorSolicitud(conexion, solicitudCompromiso.getIdSolicitud())
                            : null;
                    String textoCompromisoAbierto = prestamoCompromiso != null
                            ? prestamoCompromiso.getIdentificadorOperacion() + " ("
                                    + prestamoCompromiso.getEstadoPrestamo().getEtiqueta() + ")"
                            : solicitudCompromiso.getCodigoSolicitud() + " ("
                                    + solicitudCompromiso.getEstadoSolicitud().getEtiqueta() + ")";
                    request.setAttribute("textoCompromisoAbierto", textoCompromisoAbierto);
                    request.setAttribute("enlaceCompromisoAbierto",
                            construirEnlaceHistorial(request, solicitudCompromiso, prestamoCompromiso));
                }
                // Normalización de personas, grupo Baja (16/10/2026): la ficha solo muestra los
                // datos de la baja cuando el cliente está INACTIVA, igual que antes -- no hace
                // falta leer personas_baja para un cliente ACTIVA.
                py.com.capitalcredito.sgcpc.modelos.PersonaBaja baja = null;
                if (persona.getEstadoPersona() != null
                        && persona.getEstadoPersona().name().equals("INACTIVA")) {
                    baja = servicioClientes.obtenerBaja(conexion, idPersona);
                }
                request.setAttribute("baja", baja);
                // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): visible
                // para cualquiera que pueda ver la ficha -- a diferencia de Expediente/Buró, no
                // depende de CARGAR_DOCUMENTOS_CONSULTA_BURO (la cajera también la ve).
                List<SolicitudCredito> solicitudesCliente = servicioSolicitudes.listarPorCliente(conexion, idPersona);
                List<py.com.capitalcredito.sgcpc.dto.FilaHistorialCredito> historialCredito = new java.util.ArrayList<>();
                for (SolicitudCredito solicitudFila : solicitudesCliente) {
                    py.com.capitalcredito.sgcpc.modelos.PrestamoCredito prestamoFila = solicitudFila.isFormalizada()
                            ? servicioFormalizacion.obtenerPorSolicitud(conexion, solicitudFila.getIdSolicitud()) : null;
                    historialCredito.add(new py.com.capitalcredito.sgcpc.dto.FilaHistorialCredito(
                            solicitudFila.getCodigoSolicitud(), solicitudFila.getFechaSolicitud(),
                            solicitudFila.getEstadoSolicitud(),
                            prestamoFila == null ? null : prestamoFila.getIdentificadorOperacion(),
                            prestamoFila == null ? null : prestamoFila.getEstadoPrestamo(),
                            construirEnlaceHistorial(request, solicitudFila, prestamoFila)));
                }
                request.setAttribute("historialCredito", historialCredito);
                if (puedeVerDocumentosBuro) {
                    List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente> documentosPersona =
                            servicioDocumentos.listarPorPersona(conexion, idPersona);
                    List<py.com.capitalcredito.sgcpc.modelos.TipoDocumento> tiposDocumentoActivos =
                            servicioDocumentos.listarTiposDocumentoActivos(conexion);
                    java.time.LocalDate fechaVencimientoConsentimientoFicha =
                            servicioConsultaCrediticia.obtenerFechaVencimientoConsentimiento(conexion, idPersona);
                    request.setAttribute("fechaVencimientoConsentimiento", fechaVencimientoConsentimientoFicha);
                    // RF03 Parte 2 (pedido de Joel): antes se calculaba recién dentro de la pestaña
                    // ③ (tabBuro) -- la fila 1 del Expediente (② ) también la necesita ahora, para
                    // elegir entre "Reimprimir"/"Generar nueva"/sin botón, así que sube acá.
                    boolean consentimientoVigenteFicha = fechaVencimientoConsentimientoFicha != null
                            && !fechaVencimientoConsentimientoFicha.isBefore(java.time.LocalDate.now());
                    request.setAttribute("consentimientoVigenteFicha", consentimientoVigenteFicha);
                    // RF03/RF04 (pedido de Joel): "quedan N días" / vencida en rojo -- y el ✓ de
                    // "Generar" (fila 1 del Expediente / Paso A), vía auditoría, sin tabla nueva.
                    if (fechaVencimientoConsentimientoFicha != null) {
                        request.setAttribute("diasRestantesVigenciaConsentimiento", servicioConsultaCrediticia
                                .diasRestantesVigencia(fechaVencimientoConsentimientoFicha, java.time.LocalDate.now()));
                    }
                    request.setAttribute("ultimaGeneracionAutorizacion",
                            servicioConsultaCrediticia.obtenerUltimaGeneracionAutorizacion(conexion, idPersona));
                    request.setAttribute("autorizacionGeneradaHoy",
                            servicioConsultaCrediticia.autorizacionGeneradaHoy(conexion, idPersona));
                    // RF03 Parte 2 (pedido de Joel): Paso A muestra quién cargó la autorización
                    // firmada y cuándo se firmó -- el consentimiento completo (no solo la fecha de
                    // vencimiento, que ya traía fechaVencimientoConsentimientoFicha).
                    py.com.capitalcredito.sgcpc.modelos.ConsentimientoCrediticio ultimoConsentimientoFicha =
                            servicioConsultaCrediticia.obtenerUltimoConsentimiento(conexion, idPersona);
                    request.setAttribute("ultimoConsentimiento", ultimoConsentimientoFicha);
                    if (ultimoConsentimientoFicha != null && ultimoConsentimientoFicha.getRegistradoPor() != null) {
                        py.com.capitalcredito.sgcpc.modelos.Usuario usuarioQueCargoConsentimiento =
                                usuarioDAO.buscarPorId(conexion, ultimoConsentimientoFicha.getRegistradoPor());
                        if (usuarioQueCargoConsentimiento != null) {
                            request.setAttribute("nombreUsuarioCargoConsentimiento",
                                    usuarioQueCargoConsentimiento.getNombres() + " " + usuarioQueCargoConsentimiento.getApellidos());
                        }
                    }
                    // RF03 Parte 2 (pedido de Joel): "la autorización vale N días desde la firma",
                    // en el ⓘ del título de ③ -- antes este parámetro no llegaba a la vista.
                    request.setAttribute("diasVigenciaConsentimiento",
                            servicioParametros.obtenerValorEntero(conexion, "buro.dias_vigencia_consentimiento"));
                    List<py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia> consultasFicha =
                            servicioConsultaCrediticia.listarConsultas(conexion, idPersona);
                    request.setAttribute("consultas", consultasFicha);

                    // Bloque 7/E10 (D-132, pedido de Joel): expediente en el orden del trámite, no
                    // alfabético -- y el indicador de pasos (① Datos ② Expediente ③ Autorización y
                    // buró ④ Solicitud) visible en las tres pestañas.
                    List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> itemsExpediente =
                            servicioClientes.construirItemsExpediente(laboral.getSituacionLaboral(),
                                    tiposDocumentoActivos, documentosPersona, persona.getFechaVencimientoCedula(),
                                    ultimoConsentimientoFicha != null);
                    request.setAttribute("itemsExpediente", itemsExpediente);
                    // Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): el
                    // contador y la barra solo cuentan los documentos obligatorios, los mismos que
                    // pesan en "Enviar a aprobación" (ServicioSolicitudes.agregarFaltantesExpediente).
                    // 28/10/2026 (decisión de Joel del 21/10): con COMPROBANTE_DOMICILIO ahora
                    // obligatorio, los 4 ítems del expediente cuentan -- "N de 4", antes "N de 3".
                    // Este bloque no cambió: lee isObligatorio(), que sale de la única lista.
                    int expedienteCumplidos = 0;
                    int expedienteObligatorios = 0;
                    for (py.com.capitalcredito.sgcpc.dto.ItemExpediente item : itemsExpediente) {
                        if (!item.isObligatorio()) {
                            continue;
                        }
                        expedienteObligatorios++;
                        if (item.isCumplido()) {
                            expedienteCumplidos++;
                        }
                    }
                    request.setAttribute("expedienteCumplidos", expedienteCumplidos);
                    request.setAttribute("expedienteTotal", expedienteObligatorios);
                    request.setAttribute("pasoExpedienteCompleto", expedienteCumplidos == expedienteObligatorios);
                    boolean pasoBuroCompleto = fechaVencimientoConsentimientoFicha != null
                            && !fechaVencimientoConsentimientoFicha.isBefore(java.time.LocalDate.now())
                            && consultasFicha != null && !consultasFicha.isEmpty();
                    request.setAttribute("pasoBuroCompleto", pasoBuroCompleto);

                    // Bloque 7/E10 (D-133, pedido de Joel): "③ Autorización y buró" en dos pasos --
                    // Paso A (autorización/consentimiento) y Paso B (informe de Informconf +
                    // consulta), en vez de una sola hoja mezclando las dos cosas.
                    request.setAttribute("itemInformeBuro",
                            servicioClientes.construirItemInformeBuro(tiposDocumentoActivos, documentosPersona));
                    // B88/B28/B34 (pedido de Joel, 28/10/2026): los botones "Siguiente: …" de las
                    // pestañas ①②③ salen de acá -- el MISMO paso pendiente que avisa cada acción, en
                    // vez de un destino fijo por pestaña (② ofrecía ③ incluso con una solicitud en
                    // curso, y ③ ofrecía ④ con el expediente incompleto). Se pide al mismo
                    // ServicioPasoGuiado que usan las acciones, no a un cálculo propio de la ficha:
                    // así la pantalla y los avisos nunca pueden decir cosas distintas.
                    request.setAttribute("pasoGuiado", servicioPasoGuiado.calcular(conexion, idPersona));
                }
            } catch (SQLException error) {
                getServletContext().log("Error de base de datos al leer la ficha del cliente.", error);
                request.setAttribute("mensajeError", "No se pudo leer la ficha del cliente.");
            }
        }
        // RF02 (pedido de Joel): los <select> de situación laboral y departamento ofrecen solo lo
        // admitido por la política vigente, más el valor actual de la persona si ya no está
        // admitido (para no perder el dato real al editar otra cosa de su ficha).
        try (Connection conexionPolitica = FabricaConexionBD.obtenerConexion()) {
            request.setAttribute("situacionesLaboralesParaFormulario", servicioClientes
                    .listarSituacionesLaboralesParaFormulario(conexionPolitica,
                            laboral == null ? null : laboral.getSituacionLaboral()));
            // Normalización de personas, grupo Domicilio (16/10/2026): el departamento ya no vive
            // en Persona -- si viene de un reintento (idDepartamentoDomicilioIntentado, lo que la
            // persona tipeó en Precalificación) se usa tal cual; si no, se deriva por JOIN desde
            // la ciudad ya guardada en personas_domicilio.
            Long idDepartamentoActualFicha = idDepartamentoDomicilioIntentado;
            if (idDepartamentoActualFicha == null && domicilio != null) {
                py.com.capitalcredito.sgcpc.modelos.Ciudad ciudadDomicilioActual =
                        servicioClientes.buscarCiudadPorId(conexionPolitica, domicilio.getIdCiudad());
                idDepartamentoActualFicha = ciudadDomicilioActual == null ? null : ciudadDomicilioActual.getIdDepartamento();
            }
            request.setAttribute("departamentos", servicioClientes.listarDepartamentosParaFormulario(
                    conexionPolitica, idDepartamentoActualFicha));
            request.setAttribute("idDepartamentoActualFicha", idDepartamentoActualFicha);
            // RF02 (pedido de Joel), punto c: ayudas de política bajo los campos, leídas de
            // parámetros -- nunca valores fijos en la JSP.
            request.setAttribute("edadMinimaFicha", servicioParametros.obtenerValorEntero(conexionPolitica, "credito.edad_minima"));
            request.setAttribute("edadMaximaFicha", servicioParametros.obtenerValorEntero(conexionPolitica, "credito.edad_maxima"));
            List<String> departamentosHabilitadosFicha = servicioClientes.valoresSeparadosPorComa(
                    servicioParametros.obtenerValor(conexionPolitica, "credito.departamentos_habilitados"));
            request.setAttribute("zonaHabilitadaTextoFicha", construirListaConY(departamentosHabilitadosFicha));
        } catch (SQLException error) {
            getServletContext().log("Error de base de datos al leer situaciones laborales/departamentos admitidos.", error);
        }
        request.setAttribute("persona", persona);
        // Normalización de personas, grupo Referencias (15/10/2026): la ficha sigue mostrando 2
        // campos fijos (primera/segunda referencia) -- se resuelven acá, nunca con un bucle en la
        // JSP, para no tener que buscar por numeroReferencia ahí.
        request.setAttribute("referencia1", referenciaPorNumero(referencias, 1));
        request.setAttribute("referencia2", referenciaPorNumero(referencias, 2));
        // Normalización de personas, grupo Cónyuge (16/10/2026): puede ser null (sin cónyuge
        // registrado, o ficha nueva) -- la JSP ya revisa null campo por campo, mismo criterio que
        // referencia1/referencia2.
        request.setAttribute("conyuge", conyuge);
        // Normalización de personas, grupo Domicilio (16/10/2026): puede ser null solo en el alta
        // (todavía no se guardó nada) -- la JSP ya revisa null campo por campo, mismo criterio
        // que conyuge.
        request.setAttribute("domicilio", domicilio);
        // Normalización de personas, grupo Laborales (16/10/2026): puede ser null solo en el
        // alta (todavía no se guardó nada) -- la JSP ya revisa null campo por campo, mismo
        // criterio que domicilio.
        request.setAttribute("laboral", laboral);
        RequestDispatcher dispatcher = request.getRequestDispatcher(VISTA_FICHA);
        dispatcher.forward(request, response);
    }

    /**
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): el enlace de cada
     * fila de "Solicitudes y préstamos" se decide por el PERMISO real del usuario en sesión
     * (mismo nivelAccesoSesion que ya usa el resto de esta ficha, misma verificación que
     * FiltroAutorizacion) -- nunca por el nombre de su perfil. null si no tiene acceso a ninguna
     * pantalla para esta fila (la cajera, por ejemplo, no tiene VER_REPORTES -- no se le ofrece
     * un enlace a estado de cuenta que le daría 403).
     */
    private String construirEnlaceHistorial(HttpServletRequest request, SolicitudCredito solicitud,
            py.com.capitalcredito.sgcpc.modelos.PrestamoCredito prestamo) {
        String contextPath = request.getContextPath();
        if (prestamo == null) {
            if (solicitud.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.APROBADA) {
                return nivelAccesoSesion(request, Permiso.GENERAR_DOCUMENTOS_FORMALIZACION) != NivelAcceso.NINGUNO
                        ? contextPath + "/formalizacion/nueva/" + solicitud.getIdSolicitud() : null;
            }
            return nivelAccesoSesion(request, Permiso.GESTIONAR_SOLICITUD) != NivelAcceso.NINGUNO
                    ? contextPath + "/solicitudes/" + solicitud.getIdSolicitud() : null;
        }
        if (prestamo.getEstadoPrestamo() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo.PENDIENTE_DESEMBOLSO) {
            if (nivelAccesoSesion(request, Permiso.GENERAR_DOCUMENTOS_FORMALIZACION) != NivelAcceso.NINGUNO) {
                return contextPath + "/formalizacion/" + prestamo.getIdPrestamo();
            }
            return nivelAccesoSesion(request, Permiso.DESEMBOLSAR) != NivelAcceso.NINGUNO
                    ? contextPath + "/desembolso/" + prestamo.getIdPrestamo() : null;
        }
        if (nivelAccesoSesion(request, Permiso.VER_REPORTES) != NivelAcceso.NINGUNO) {
            return contextPath + "/reportes/estado-cuenta/" + prestamo.getIdPrestamo();
        }
        boolean enCobranza = prestamo.getEstadoPrestamo() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo.ACTIVO
                || prestamo.getEstadoPrestamo() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo.EN_MORA;
        if (enCobranza && nivelAccesoSesion(request, Permiso.COBRAR) != NivelAcceso.NINGUNO) {
            return contextPath + "/cobranza/" + prestamo.getIdPrestamo();
        }
        return null;
    }

    private py.com.capitalcredito.sgcpc.modelos.PersonaReferencia referenciaPorNumero(
            List<py.com.capitalcredito.sgcpc.modelos.PersonaReferencia> referencias, int numero) {
        if (referencias == null) {
            return null;
        }
        for (py.com.capitalcredito.sgcpc.modelos.PersonaReferencia referencia : referencias) {
            if (referencia.getNumeroReferencia() == numero) {
                return referencia;
            }
        }
        return null;
    }

    private boolean puedeEditar(HttpServletRequest request) {
        return nivelAccesoSesion(request, Permiso.REGISTRAR_EDITAR_CLIENTE) == NivelAcceso.TOTAL;
    }

    private boolean puedeCargarDocumentos(HttpServletRequest request) {
        return nivelAccesoSesion(request, Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO) == NivelAcceso.TOTAL;
    }

    private boolean puedeVerDocumentosBuro(HttpServletRequest request) {
        return nivelAccesoSesion(request, Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO) != NivelAcceso.NINGUNO;
    }

    /** Bloque 7/E10 (D-132, pedido de Joel): el paso ④ del expediente ("Solicitud") es solo del oficial. */
    private boolean puedeGestionarSolicitud(HttpServletRequest request) {
        return nivelAccesoSesion(request, Permiso.GESTIONAR_SOLICITUD) == NivelAcceso.TOTAL;
    }

    private NivelAcceso nivelAccesoSesion(HttpServletRequest request, Permiso permiso) {
        Usuario usuario = (Usuario) request.getSession().getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO);
        return usuario == null ? NivelAcceso.NINGUNO
                : servicioAutorizacion.nivelAcceso(usuario.getPerfilUsuario(), permiso);
    }
}
