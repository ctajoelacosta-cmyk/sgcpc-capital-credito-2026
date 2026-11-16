package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.dao.CiudadDAO;
import py.com.capitalcredito.sgcpc.dao.DepartamentoDAO;
import py.com.capitalcredito.sgcpc.dao.DocumentoExpedienteDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaBajaDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaConyugeDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaDomicilioDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaLaboralDAO;
import py.com.capitalcredito.sgcpc.dao.PersonaReferenciaDAO;
import py.com.capitalcredito.sgcpc.dao.PrestamoDAO;
import py.com.capitalcredito.sgcpc.dao.SolicitudDAO;
import py.com.capitalcredito.sgcpc.dao.TipoDocumentoDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.GeneradorInformesPdf;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Ciudad;
import py.com.capitalcredito.sgcpc.modelos.Departamento;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PersonaBaja;
import py.com.capitalcredito.sgcpc.modelos.PersonaConyuge;
import py.com.capitalcredito.sgcpc.modelos.PersonaDomicilio;
import py.com.capitalcredito.sgcpc.modelos.PersonaLaboral;
import py.com.capitalcredito.sgcpc.modelos.PersonaReferencia;

/**
 * P03, parte ficha del cliente (Doc. 10 §10.19: ControladorClientesServlet /
 * ServicioClientes). En E3 solo se registra la Persona (ajuste A, D-37): el
 * vínculo cliente con una solicitud es de E4. Los métodos de
 * validación son puros (sin DAO); los que persisten usan PersonaDAO y
 * ServicioAuditoria en una sola transacción, mismo patrón que ServicioUsuarios.
 */
public class ServicioClientes {

    private final PersonaDAO personaDAO = new PersonaDAO();
    // Normalización de personas, grupo Referencias (15/10/2026): personas_referencias reemplaza a
    // referencia_nombre/referencia_telefono/referencia2_nombre/referencia2_telefono.
    private final PersonaReferenciaDAO personaReferenciaDAO = new PersonaReferenciaDAO();
    // Normalización de personas, grupo Cónyuge (16/10/2026): personas_conyuge reemplaza a
    // conyuge_nombre/conyuge_cedula/conyuge_telefono.
    private final PersonaConyugeDAO personaConyugeDAO = new PersonaConyugeDAO();
    // Normalización de personas, grupo Baja (16/10/2026): personas_baja reemplaza a
    // motivo_baja/detalle_baja/fecha_baja/dado_de_baja_por.
    private final PersonaBajaDAO personaBajaDAO = new PersonaBajaDAO();
    // Normalización de personas, grupo Domicilio (16/10/2026): personas_domicilio reemplaza a
    // domicilio/direccion_domicilio/barrio/id_ciudad/id_departamento/referencia_domicilio/
    // tipo_vivienda/antiguedad_domicilio_anios.
    private final PersonaDomicilioDAO personaDomicilioDAO = new PersonaDomicilioDAO();
    // Normalización de personas, grupo Laborales (16/10/2026): personas_laborales reemplaza a
    // situacion_laboral/empleador_*/ingreso_neto_comprobado/presenta_comprobante_ingreso/
    // otros_ingresos_mensuales/gastos_fijos_mensuales/cuota_otras_deudas_mensual.
    private final PersonaLaboralDAO personaLaboralDAO = new PersonaLaboralDAO();
    private final PrestamoDAO prestamoDAO = new PrestamoDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();
    private final ServicioParametros servicioParametros = new ServicioParametros();
    // RF03 (pedido de Joel), Parte 1 -- reprueba: para marcar REEMPLAZADO el escaneo de la cédula
    // cuando cambia su fecha de vencimiento (actualizar()). No genera dependencia circular:
    // ServicioDocumentos no depende de ServicioClientes.
    private final DocumentoExpedienteDAO documentoExpedienteDAO = new DocumentoExpedienteDAO();
    private final TipoDocumentoDAO tipoDocumentoDAO = new TipoDocumentoDAO();
    /** App móvil, ETAPA 2 (13/11/2026): solo para la lectura en bloque de "Mis pendientes"
     * (quiénes tienen consentimiento). Es el DAO y no ServicioConsultaCrediticia a propósito:
     * un campo Servicio* dentro de otro servicio puede cerrar un ciclo de construcción (la
     * trampa de B88), y acá hace falta una sola lectura. */
    private final py.com.capitalcredito.sgcpc.dao.ConsentimientoCrediticioDAO consentimientoCrediticioDAO =
            new py.com.capitalcredito.sgcpc.dao.ConsentimientoCrediticioDAO();
    // RF06 (pedido de Joel, 10/10/2026): para rechazar la baja si hay una solicitud abierta --
    // DAO directo, no ServicioSolicitudes (que sí depende de ServicioClientes; instanciar los dos
    // de vuelta en el otro sentido recién en el constructor de campos sería una recursión
    // infinita). SolicitudDAO no depende de ServicioClientes, sin riesgo de ciclo.
    private final SolicitudDAO solicitudDAO = new SolicitudDAO();

    /** Bloque 7/E10 (D-129, Obs. 31): motivos válidos de baja de un cliente. */
    public static final java.util.Set<String> MOTIVOS_BAJA = new java.util.HashSet<>(
            java.util.Arrays.asList("FALLECIMIENTO", "PEDIDO_CLIENTE", "DATOS_DUPLICADOS", "OTRO"));
    private final DepartamentoDAO departamentoDAO = new DepartamentoDAO();
    private final CiudadDAO ciudadDAO = new CiudadDAO();

    /** Estado civil que hace obligatorios los datos del cónyuge (Bloque 7/E10, Tanda A, D-126). */
    private static final java.util.Set<String> ESTADOS_CIVILES_CON_CONYUGE =
            new java.util.HashSet<>(java.util.Arrays.asList("Casado/a", "Unión de hecho"));

    /** Los 8 códigos válidos de situación laboral (Bloque 7/E10, Tanda B, D-127). */
    public static final java.util.Set<String> SITUACIONES_LABORALES = new java.util.HashSet<>(java.util.Arrays.asList(
            "ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO", "JUBILADO_PENSIONADO", "INDEPENDIENTE",
            "COMERCIANTE", "EMPLEADO_DOMESTICO", "DESEMPLEADO", "OTRO"));

    /**
     * Bloque 7/E10 (D-130, pedido de Joel): única fuente de la etiqueta legible de cada código de
     * situación laboral -- antes el listado de Clientes y el resumen de Aprobaciones mostraban el
     * código interno tal cual. Mismo orden y mismo texto que el <select> de clientes/ficha.jsp
     * (Tanda B, D-127); solo presentación, no cambia datos ni el cargador.
     */
    private static final String[][] ETIQUETAS_SITUACION_LABORAL = {
        {"ASALARIADO_SECTOR_PRIVADO", "Asalariado (sector privado)"},
        {"FUNCIONARIO_PUBLICO", "Funcionario público"},
        {"JUBILADO_PENSIONADO", "Jubilado/pensionado"},
        {"INDEPENDIENTE", "Independiente / cuenta propia"},
        {"COMERCIANTE", "Comerciante / negocio propio"},
        {"EMPLEADO_DOMESTICO", "Empleado/a doméstico/a"},
        {"DESEMPLEADO", "Desempleado/a"},
        {"OTRO", "Otro"}
    };

    public static String etiquetaSituacionLaboral(String codigo) {
        if (codigo == null) {
            return "-";
        }
        for (String[] par : ETIQUETAS_SITUACION_LABORAL) {
            if (par[0].equals(codigo)) {
                return par[1];
            }
        }
        return codigo;
    }

    /**
     * RF02 (pedido de Joel, tu opción 1): el <select> del formulario ofrece SOLO las situaciones
     * admitidas por credito.situaciones_laborales_admitidas (mismo orden de ETIQUETAS_SITUACION_
     * LABORAL) -- ya no las 8 fijas de SITUACIONES_LABORALES (esas siguen siendo el chequeo de
     * integridad de que el código exista, no de que la política vigente lo admita hoy). Si la
     * persona que se está editando tiene una situación que NO está admitida, se agrega al final
     * igual, marcada "(no admitida actualmente)" -- para no perder su dato real ni obligar a
     * cambiarlo solo por estar editando otra cosa (ej. el teléfono).
     */
    public List<String[]> listarSituacionesLaboralesParaFormulario(Connection conexion, String situacionActual)
            throws SQLException {
        List<String> admitidas = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        List<String[]> opciones = new java.util.ArrayList<>();
        for (String[] par : ETIQUETAS_SITUACION_LABORAL) {
            if (admitidas.contains(par[0])) {
                opciones.add(par);
            }
        }
        if (situacionActual != null && !admitidas.contains(situacionActual)) {
            opciones.add(new String[]{situacionActual, etiquetaSituacionLaboral(situacionActual) + " (no admitida actualmente)"});
        }
        return opciones;
    }

    /**
     * RF02 (pedido de Joel): el filtro "Situación laboral" del listado de Clientes no es un
     * formulario de alta/edición -- tiene que poder encontrar a cualquier cliente que ya exista,
     * admitido hoy o no. Devuelve las admitidas por credito.situaciones_laborales_admitidas MÁS
     * cualquier otra que tenga al menos un cliente cargado (ej. INDEPENDIENTE/OTRO de antes de
     * esta política, o clientes de prueba), sin la marca "(no admitida actualmente)" -- acá no es
     * un aviso de política, es simplemente una opción más para buscar.
     */
    public List<String[]> listarSituacionesLaboralesParaFiltro(Connection conexion) throws SQLException {
        List<String> admitidas = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        List<String> enUso = personaLaboralDAO.listarSituacionesLaboralesDistintas(conexion);
        List<String[]> opciones = new java.util.ArrayList<>();
        for (String[] par : ETIQUETAS_SITUACION_LABORAL) {
            if (admitidas.contains(par[0]) || enUso.contains(par[0])) {
                opciones.add(par);
            }
        }
        return opciones;
    }

    /** Situación laboral que hace obligatorios los datos del empleador (D-127: "tiene un empleador"). */
    private static final java.util.Set<String> SITUACIONES_LABORALES_CON_EMPLEADOR =
            new java.util.HashSet<>(java.util.Arrays.asList("ASALARIADO_SECTOR_PRIVADO", "FUNCIONARIO_PUBLICO"));

    // ------------------------------------------------------------------
    // RF02 (pedido de Joel): política de elegibilidad de la Persona (edad, zona, situación
    // laboral) -- movida acá desde ServicioSolicitudes porque ServicioSolicitudes ya depende de
    // ServicioClientes (campo servicioClientes más abajo en esa clase); si ServicioClientes
    // dependiera de ServicioSolicitudes se armaría una dependencia circular al instanciar
    // cualquiera de las dos (StackOverflowError). ServicioSolicitudes.calcularEdad/
    // evaluarPoliticaCredito ahora delegan acá, misma firma pública de siempre -- no cambia nada
    // para Solicitudes, ServicioSolicitudesTest sigue pasando sin tocar ni un assert.
    // ------------------------------------------------------------------

    /** Edad en años cumplidos a una fecha dada. Pura. */
    public int calcularEdad(LocalDate fechaNacimiento, LocalDate hoy) {
        return java.time.Period.between(fechaNacimiento, hoy).getYears();
    }

    /**
     * Duplicaciones y parches (Commit 4): único lugar que lee credito.edad_minima/edad_maxima --
     * antes se repetía en 4 lugares de esta clase y 2 de ServicioSolicitudes (que ya cruza acá
     * para valoresSeparadosPorComa, mismo patrón).
     */
    public int obtenerEdadMinima(Connection conexion) throws SQLException {
        return servicioParametros.obtenerValorEntero(conexion, "credito.edad_minima");
    }

    public int obtenerEdadMaxima(Connection conexion) throws SQLException {
        return servicioParametros.obtenerValorEntero(conexion, "credito.edad_maxima");
    }

    /** RF03 (pedido de Joel), Parte 1: la cédula de identidad de la persona ya venció -- la usan
     * validarPoliticaCreditoAlta (bloquea el alta), ControladorClientesServlet (aviso amarillo de
     * la edición) y construirItemDocumento (fila 2 del Expediente en rojo), y
     * ServicioSolicitudes (bloquea "Enviar a aprobación"). Pura. fechaVencimientoCedula es
     * obligatoria en la ficha (validarDatosTandaA) pero null nunca cuenta como vencida acá --
     * cada llamador decide qué hacer si falta el dato. */
    public boolean cedulaVencida(LocalDate fechaVencimientoCedula, LocalDate hoy) {
        return fechaVencimientoCedula != null && fechaVencimientoCedula.isBefore(hoy);
    }

    /**
     * Política de crédito parametrizable (edad, departamento, situación laboral) -- "si no
     * cumple, aviso claro con el motivo" (pedido de Joel). Revisa las tres en orden y rechaza con
     * el motivo PUNTUAL de la primera que no se cumple (nunca un mensaje genérico). Pura, sin DAO.
     */
    public void evaluarPoliticaCredito(int edad, int edadMinima, int edadMaxima, String nombreDepartamento,
            List<String> departamentosHabilitados, String situacionLaboral, List<String> situacionesAdmitidas) {
        if (edad < edadMinima || edad > edadMaxima) {
            throw new NegocioException("La edad permitida es de " + edadMinima + " a " + edadMaxima
                    + " años; esta persona tiene " + edad + ".");
        }
        if (nombreDepartamento == null || !departamentosHabilitados.contains(nombreDepartamento)) {
            throw new NegocioException("El departamento del cliente ("
                    + (nombreDepartamento == null ? "sin completar en la ficha" : nombreDepartamento)
                    + ") no está habilitado por la política de crédito vigente.");
        }
        if (situacionLaboral == null || !situacionesAdmitidas.contains(situacionLaboral)) {
            throw new NegocioException("La situación laboral del cliente "
                    + (situacionLaboral == null ? "no está completa en la ficha" : "no está admitida")
                    + " por la política de crédito vigente.");
        }
    }

    /** Lista de valores separados por coma de un parámetro (ej. "Asunción,Central"), recortados y
     * sin vacíos. Pura. */
    public List<String> valoresSeparadosPorComa(String valor) {
        List<String> valores = new java.util.ArrayList<>();
        if (valor == null) {
            return valores;
        }
        for (String token : valor.split(",")) {
            String limpio = token.trim();
            if (!limpio.isEmpty()) {
                valores.add(limpio);
            }
        }
        return valores;
    }

    public List<Departamento> listarDepartamentos(Connection conexion) throws SQLException {
        return departamentoDAO.listarActivos(conexion);
    }

    /**
     * RF02 (pedido de Joel): el <select> del formulario de alta/edición ofrece SOLO los
     * departamentos habilitados por credito.departamentos_habilitados (hoy Asunción y Central),
     * no los 18 del país -- "activo" en la tabla departamentos significa "existe como
     * departamento real", no "habilitado por la política de crédito". Si la persona que se está
     * editando vive en uno no habilitado, se agrega al final igual, marcado "(no admitido
     * actualmente)" -- mismo criterio que listarSituacionesLaboralesParaFormulario. No se toca
     * listarDepartamentos: lo sigue usando el filtro de la lista de Clientes, que sí debe poder
     * buscar por cualquier departamento (dato administrativo, no de política de alta).
     */
    public List<Departamento> listarDepartamentosParaFormulario(Connection conexion, Long idDepartamentoActual)
            throws SQLException {
        List<String> habilitados = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        List<Departamento> todos = departamentoDAO.listarActivos(conexion);
        List<Departamento> opciones = new java.util.ArrayList<>();
        boolean actualYaIncluido = false;
        for (Departamento departamento : todos) {
            if (habilitados.contains(departamento.getNombre())) {
                opciones.add(departamento);
                if (departamento.getIdDepartamento().equals(idDepartamentoActual)) {
                    actualYaIncluido = true;
                }
            }
        }
        if (idDepartamentoActual != null && !actualYaIncluido) {
            Departamento actual = departamentoDAO.buscarPorId(conexion, idDepartamentoActual);
            if (actual != null) {
                Departamento marcado = new Departamento();
                marcado.setIdDepartamento(actual.getIdDepartamento());
                marcado.setNombre(actual.getNombre() + " (no admitido actualmente)");
                opciones.add(marcado);
            }
        }
        return opciones;
    }

    public List<Ciudad> listarCiudades(Connection conexion) throws SQLException {
        return ciudadDAO.listarActivas(conexion);
    }

    /** Bloque 7/E10 (D-129, Obs. 26): nombre de departamento/ciudad del cliente, para el resumen de Aprobaciones. */
    public Departamento buscarDepartamentoPorId(Connection conexion, Long idDepartamento) throws SQLException {
        return idDepartamento == null ? null : departamentoDAO.buscarPorId(conexion, idDepartamento);
    }

    public Ciudad buscarCiudadPorId(Connection conexion, Long idCiudad) throws SQLException {
        return idCiudad == null ? null : ciudadDAO.buscarPorId(conexion, idCiudad);
    }

    /**
     * Bloque 7/E10, Tanda B (D-127): guía informativa (no bloquea el envío de nada) de qué
     * documentos suele pedir la financiera según la situación laboral -- a criterio del oficial,
     * quien decide qué carga en el expediente. Pura.
     */
    public List<String> documentosSugeridosPorSituacionLaboral(String situacionLaboral) {
        List<String> documentos = new java.util.ArrayList<>();
        documentos.add("Cédula de identidad (ambos lados)");
        documentos.add("Factura de ANDE/ESSAP/telefonía (comprobante de domicilio)");
        documentos.add(descripcionComprobanteIngresoSegunSituacionLaboral(situacionLaboral));
        return documentos;
    }

    /**
     * Mejora de pantalla (Precalificación, pedido de Joel): botón "Imprimir requisitos" de la
     * caja de Precalificación -- un PDF informativo con lo que la persona necesita presentar
     * para pedir un préstamo, antes de que exista ningún registro (no persiste nada, ni siquiera
     * si nombre/cédula están vacíos). Reutiliza documentosSugeridosPorSituacionLaboral (ya trae
     * cédula + comprobante de domicilio + el comprobante de ingreso específico de la situación) y
     * el mismo mecanismo de "línea para completar" que la cotización
     * (ServicioInformes.construirLineaInteresado, mismo texto acá para no depender de ese
     * servicio). Mismo estilo V9 (logo, colores de marca, encabezado de empresa) que cotizacion.jrxml.
     */
    public byte[] generarInformeRequisitosPdf(Connection conexion, String situacionLaboral, String nombreCliente,
            String cedulaCliente, String nombreUsuarioEmisor) throws SQLException {
        Map<String, Object> parametros = GeneradorInformesPdf.parametrosBase(conexion, servicioParametros, nombreUsuarioEmisor);
        parametros.put("EMPRESA_TELEFONO", servicioParametros.obtenerValor(conexion, "empresa.telefono"));
        parametros.put("EMPRESA_WHATSAPP", servicioParametros.obtenerValor(conexion, "empresa.whatsapp"));
        parametros.put("SITUACION_LABORAL", etiquetaSituacionLaboral(situacionLaboral));
        parametros.put("NOMBRE_INTERESADO", construirLineaInteresado("Nombre", nombreCliente));
        parametros.put("CEDULA_INTERESADO", construirLineaInteresado("C.I.", cedulaCliente));

        StringBuilder documentos = new StringBuilder();
        for (String documento : documentosSugeridosPorSituacionLaboral(situacionLaboral)) {
            if (documentos.length() > 0) {
                documentos.append('\n');
            }
            documentos.append("• ").append(documento);
        }
        parametros.put("DOCUMENTOS", documentos.toString());

        int edadMinima = obtenerEdadMinima(conexion);
        int edadMaxima = obtenerEdadMaxima(conexion);
        List<String> departamentosHabilitados = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        String zonaHabilitada = departamentosHabilitados.size() <= 1
                ? String.join("", departamentosHabilitados)
                : String.join(", ", departamentosHabilitados.subList(0, departamentosHabilitados.size() - 1))
                        + " y " + departamentosHabilitados.get(departamentosHabilitados.size() - 1);
        java.math.BigDecimal montoMinimo = java.math.BigDecimal.valueOf(
                servicioParametros.obtenerValorEntero(conexion, "credito.monto_minimo"));
        java.math.BigDecimal montoMaximo = java.math.BigDecimal.valueOf(
                servicioParametros.obtenerValorEntero(conexion, "credito.monto_maximo"));
        int plazoMinimo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_minimo");
        int plazoMaximo = servicioParametros.obtenerValorEntero(conexion, "credito.plazo_maximo");
        parametros.put("CONDICIONES", "• Edad: de " + edadMinima + " a " + edadMaxima + " años.\n"
                + "• Zona habilitada: " + zonaHabilitada + ".\n"
                + "• Monto: de " + FormateadorNumeros.comoGuaranies(montoMinimo) + " a "
                + FormateadorNumeros.comoGuaranies(montoMaximo) + "\n"
                + "• Plazo: de " + plazoMinimo + " a " + plazoMaximo + " meses.\n"
                + "• Comprobante de ingreso: obligatorio.");

        return GeneradorInformesPdf.llenarYExportar("requisitos_prestamo.jasper", parametros,
                new net.sf.jasperreports.engine.JREmptyDataSource());
    }

    /** Mismo criterio que ServicioInformes.construirLineaInteresado (duplicado a propósito, una
     * sola línea, para no depender de ServicioInformes desde ServicioClientes): "Nombre: valor" o
     * "Nombre: ______________" para completar a mano si todavía no se escribió. */
    private String construirLineaInteresado(String etiqueta, String valor) {
        return valor == null || valor.trim().isEmpty() ? etiqueta + ": ______________" : etiqueta + ": " + valor.trim();
    }

    /**
     * Bloque 7/E10 (D-132, pedido de Joel): único lugar donde se decide qué comprobante de
     * ingreso corresponde según la situación laboral -- antes vivía inline dentro de
     * documentosSugeridosPorSituacionLaboral; ahora también la usa construirItemsExpediente, para
     * que la pestaña de Expediente muestre exactamente la misma guía que ya mostraba el
     * formulario de datos ("Documentos sugeridos para esta situación laboral"). Pura.
     */
    public String descripcionComprobanteIngresoSegunSituacionLaboral(String situacionLaboral) {
        if (SITUACIONES_LABORALES_CON_EMPLEADOR.contains(situacionLaboral)) {
            return "Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses).";
        } else if ("JUBILADO_PENSIONADO".equals(situacionLaboral)) {
            // Mejora de pantalla (punto 7, pedido de Joel): una constancia de jubilación solo
            // prueba que está jubilado, no cuánto cobra -- el recibo de haberes sí sirve como
            // comprobante de ingreso, igual que una liquidación de sueldo.
            return "Recibo de haberes jubilatorios (últimos 3 a 6 meses).";
        } else if ("COMERCIANTE".equals(situacionLaboral)) {
            return "Declaraciones de IVA o facturas de los últimos 3 a 6 meses, y patente comercial.";
        } else if ("EMPLEADO_DOMESTICO".equals(situacionLaboral)) {
            return "Extracto de aportes IPS (últimos 3 a 6 meses).";
        }
        return "Comprobante de ingreso disponible (a criterio del oficial).";
    }

    /**
     * Bloque 7/E10 (D-132/D-133, pedido de Joel): orden real del trámite -- antes el desplegable
     * de carga mostraba los tipos de documento de contexto PERSONA en orden alfabético (la
     * autorización de consulta al buró, que es de los últimos pasos, aparecía primera). D-133:
     * el informe de Informconf (RESULTADO_CONSULTA_BURO) sale de esta lista -- ahora se carga
     * desde el Paso B de "③ Autorización y buró" (ver construirItemInformeBuro), no desde acá; el
     * expediente queda en 4 documentos. MISMOS códigos internos de siempre (CEDULA_IDENTIDAD,
     * COMPROBANTE_INGRESO, COMPROBANTE_DOMICILIO, AUTORIZACION_CONSULTA_BURO,
     * RESULTADO_CONSULTA_BURO) -- nada de esto cambia el chequeo de "Enviar a aprobación"
     * (ServicioSolicitudes.agregarFaltantesExpediente), la pestaña de autorización y buró, ni el
     * cargador, todos siguen resolviendo por código, no por orden ni por en qué pestaña se carga.
     *
     * <p><b>B90 (decisión de Joel, 29/10/2026): la autorización del buró es la FILA 1.</b> El
     * motivo es el mismo que el manual ya aconsejaba ("empiece por la autorización"): el buró es el
     * filtro que ahorra tiempo -- si el cliente no pasa, no tiene sentido juntar el resto de los
     * papeles. Va junto con B88 (el flujo guiado pide los pasos en este mismo orden). El número de
     * cada fila sale de la POSICIÓN en este arreglo (clientes/ficha.jsp lo cuenta al dibujarlas), y
     * el contador "N de 4" sale de CODIGOS_EXPEDIENTE_OBLIGATORIOS, que no tiene orden: cambiar
     * este orden no toca ninguna lógica, solo la presentación.</p>
     */
    public static final String[] CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN = {
        "AUTORIZACION_CONSULTA_BURO", "CEDULA_IDENTIDAD", "COMPROBANTE_INGRESO", "COMPROBANTE_DOMICILIO"
    };

    // Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): nombrados acá para
    // que agregarFaltantesExpediente (ServicioSolicitudes) referencie estos mismos, en vez de
    // repetir el literal -- la autorización no tiene un literal propio que reemplazar ahí
    // (agregarFaltantesBuro llama a ServicioConsultaCrediticia, que no recibe un código de tipo).
    // 28/10/2026 (decisión de Joel del 21/10): se suma CODIGO_COMPROBANTE_DOMICILIO, usado por
    // agregarFaltantesExpediente igual que los otros dos.
    public static final String CODIGO_CEDULA_IDENTIDAD = "CEDULA_IDENTIDAD";
    public static final String CODIGO_COMPROBANTE_INGRESO = "COMPROBANTE_INGRESO";
    public static final String CODIGO_AUTORIZACION_CONSULTA_BURO = "AUTORIZACION_CONSULTA_BURO";
    /** Decisión de Joel del 21/10/2026 (ver CODIGOS_EXPEDIENTE_OBLIGATORIOS): pasó a obligatorio. */
    public static final String CODIGO_COMPROBANTE_DOMICILIO = "COMPROBANTE_DOMICILIO";

    /**
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): la ÚNICA lista de
     * qué documentos del expediente son obligatorios -- los mismos que de verdad bloquean
     * "Enviar a aprobación" (RF06, ServicioSolicitudes.agregarFaltantesExpediente para cédula/
     * comprobante de ingreso/comprobante de domicilio, agregarFaltantesBuro para la
     * autorización). Usada por construirItemDocumento para marcar cada
     * ItemExpediente.isObligatorio() -- la pestaña de Documentos y el "N de N"
     * (ControladorClientesServlet) leen de ahí, nunca redefinen la lista por su cuenta.
     *
     * <p><b>Decisión de Joel del 21/10/2026, implementada el 28/10/2026</b>: COMPROBANTE_DOMICILIO
     * entra a la lista y pasa de 3 a 4 obligatorios ("Expediente: N de 4", antes "de 3").
     * Reemplaza la "opción b" del 19/10/2026, que lo había dejado como complementario. Cierra
     * además C14 (el modal de "Documento cargado" ya lo listaba entre los faltantes, porque
     * ControladorDocumentosServlet.itemsFaltantesExpediente no filtra por isObligatorio(): los
     * dos criterios quedan ahora de acuerdo sin tocar ese método). El anteproyecto no fija la
     * lista de documentos obligatorios (RF03 solo pide "indicar qué documentos faltan"), así que
     * esto es una decisión interna de diseño y RF_ANTEPROYECTO.md no se toca -- ver
     * RF03_expediente.md §7.</p>
     */
    public static final java.util.Set<String> CODIGOS_EXPEDIENTE_OBLIGATORIOS = new java.util.HashSet<>(
            java.util.Arrays.asList(CODIGO_CEDULA_IDENTIDAD, CODIGO_COMPROBANTE_INGRESO,
                    CODIGO_COMPROBANTE_DOMICILIO, CODIGO_AUTORIZACION_CONSULTA_BURO));

    /**
     * Bloque 7/E10 (D-132/D-133): arma la lista de los documentos requeridos del expediente, en
     * el orden del trámite, con la descripción (la del comprobante de ingreso cambia según la
     * situación laboral) y el documento VIGENTE de cada uno si ya se cargó -- Pura, recibe ya
     * resueltos los tipos disponibles y los documentos de la persona (el llamador ya los tenía,
     * no hace falta una Connection acá). RF03 (pedido de Joel), Parte 1: fechaVencimientoCedula
     * se usa SOLO para la fila CEDULA_IDENTIDAD (ver construirItemDocumento) -- deja de contar
     * como cumplida, y en rojo, si ya venció.
     */
    public List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> construirItemsExpediente(String situacionLaboral,
            List<py.com.capitalcredito.sgcpc.modelos.TipoDocumento> tiposDisponibles,
            List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente> documentosDeLaPersona,
            LocalDate fechaVencimientoCedula, boolean consentimientoBuroRegistrado) {
        List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> items = new java.util.ArrayList<>();
        for (String codigo : CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN) {
            py.com.capitalcredito.sgcpc.dto.ItemExpediente item = construirItemDocumento(codigo, situacionLaboral,
                    tiposDisponibles, documentosDeLaPersona, fechaVencimientoCedula, consentimientoBuroRegistrado);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * B88: qué acción acaba de hacer el oficial. Es un dato de la REGLA, no del servlet: lo que
     * cambia según la acción es a dónde conviene llevarlo (decisión de Joel del 29/10/2026, tras
     * ver el paso 3 de la prueba -- ver {@link #calcularSiguientePaso}).
     */
    public enum AccionPrevia {
        /** Alta o edición de la ficha, registro del resultado del buró, o simplemente mirar la ficha. */
        NINGUNA,
        /** Cargó un documento del expediente que no es la autorización (fila 2, 3 o 4, u "Otros documentos"). */
        CARGA_DOCUMENTO_EXPEDIENTE,
        /**
         * Cargó la autorización firmada (fila 1). Sigue el orden B sin excepción: lo que viene es el
         * buró. Está nombrada aparte, en vez de usar NINGUNA, para que el llamador diga la verdad de
         * qué pasó y no haya que adivinarlo leyendo el servlet.
         */
        CARGA_AUTORIZACION
    }

    /**
     * B88 (pedido explícito de Joel, 28/10/2026; orden "opción B" decidido el 29/10): <b>el único
     * dueño</b> de "cuál es el PRIMER paso pendiente del cliente, qué se le dice y a dónde se lo
     * lleva". Lo usan el alta y la edición de la ficha, el registro del resultado del buró, las dos
     * cargas de documentos del expediente y los botones "Siguiente: …" de la ficha -- antes cada
     * uno tenía su destino y su texto fijos en el código, sin mirar el estado real (de ahí los dos
     * casos que vio Joel el 28/10: cargar la autorización con el domicilio pendiente saltaba a ③,
     * y cargar el domicilio con el buró ya registrado seguía diciendo "registrar el buró").
     *
     * <p><b>Orden (opción B, decisión de Joel del 29/10/2026)</b>: el filtro del buró va ANTES de
     * pedir el resto de los papeles -- si el cliente no pasa el buró, no tiene sentido juntarlos:</p>
     * <ol>
     *   <li>falta la autorización firmada (o venció) → ② </li>
     *   <li>autorización OK, buró sin registrar → ③ </li>
     *   <li>buró registrado CON MORA (RN-18) → no lleva a ningún lado: no hay nada que completar
     *       (mismo texto del bloqueo real, {@link ServicioSolicitudes#MENSAJE_MORA_INFORMCONF})</li>
     *   <li>buró OK, falta cédula / comprobante de ingreso / comprobante de domicilio → ② </li>
     *   <li>expediente completo y sin solicitud → ④ </li>
     *   <li>solicitud en BORRADOR → ficha de la solicitud ("enviar SOL-… a aprobación")</li>
     *   <li>solicitud PENDIENTE_APROBACION → no lleva a ningún lado (decide el gerente)</li>
     * </ol>
     *
     * <p><b>Excepción por la acción previa (decisión de Joel del 29/10/2026, sobre el paso 3 del
     * plan de prueba)</b>: si acaba de <b>cargar un documento del expediente</b> y todavía falta
     * otro, <b>se queda en ②</b> y se lo dice ("Faltan: …", más "Pendiente también: registrar el
     * resultado del buró." si el buró sigue sin registrar) -- con el orden B puro, cada papel que
     * cargaba lo sacaba del Expediente y lo mandaba a ③. Dos cosas que esta excepción NO cambia:
     * cargar la <b>autorización</b> sigue llevando a ③ (es el filtro), y con <b>mora</b> registrada
     * no se lo invita a seguir juntando papeles (RN-18 manda, igual que en el orden B).</p>
     *
     * <p><b>Nunca cambia lo que se permite hacer</b>: el oficial puede cargar cualquier documento
     * o crear la solicitud cuando quiera, y la consulta al buró no se bloquea por el expediente
     * (aclaración de Joel del 28/10). Lo único que cambia es el destino y el texto.</p>
     *
     * <p>Pura (sin Connection ni DAO): recibe el estado ya leído, igual que
     * {@link #construirItemsExpediente}, así que se puede probar sin base --
     * {@code ServicioClientesTest}, un caso por cada uno de los 7 más los de la excepción. Si el
     * llamador no pudo leer el expediente (lista null o vacía), devuelve un paso sin texto ni
     * destino: nunca rompe ni bloquea el aviso de éxito de la acción que lo invocó.</p>
     *
     * @param itemsExpediente los ítems del expediente en el orden de
     *        {@link #CODIGOS_EXPEDIENTE_PERSONA_EN_ORDEN} (B90: la autorización es la fila 1)
     * @param situacionLaboral solo para el texto del comprobante de ingreso pendiente de renovación
     * @param autorizacionVigente consentimiento cargado y todavía dentro de su vigencia
     * @param consultaMasRecienteConEvidencia null = buró sin registrar; mismo criterio que el
     *        checklist de envío (ServicioConsultaCrediticia.obtenerMasRecienteConEvidencia)
     * @param solicitudAbierta solicitud del compromiso abierto (RN-01/RN-02), o null si no hay
     * @param compromisoViaPrestamo true si ese compromiso es por un préstamo no cerrado
     * @param accionPrevia qué acaba de hacer el oficial (ver {@link AccionPrevia})
     */
    public py.com.capitalcredito.sgcpc.dto.PasoGuiado calcularSiguientePaso(
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> itemsExpediente, String situacionLaboral,
            boolean autorizacionVigente,
            py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia consultaMasRecienteConEvidencia,
            py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitudAbierta, boolean compromisoViaPrestamo,
            AccionPrevia accionPrevia) {
        if (itemsExpediente == null || itemsExpediente.isEmpty()) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(null,
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.NINGUNO, null);
        }
        boolean buroConMora = consultaMasRecienteConEvidencia != null
                && Boolean.TRUE.equals(consultaMasRecienteConEvidencia.getIndicadorMora());
        List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> faltantesExpediente =
                faltantesObligatorios(itemsExpediente);
        // Excepción de la acción previa (decisión de Joel del 29/10/2026): acaba de cargar un papel
        // y todavía falta otro -> no se lo mueve del Expediente. Con mora registrada no aplica: no
        // tiene sentido invitarlo a seguir juntando papeles de un crédito que no se va a dar.
        if (accionPrevia == AccionPrevia.CARGA_DOCUMENTO_EXPEDIENTE && !faltantesExpediente.isEmpty()
                && !buroConMora) {
            String texto = textoFaltantesExpediente(faltantesExpediente, situacionLaboral, solicitudAbierta,
                    compromisoViaPrestamo);
            if (consultaMasRecienteConEvidencia == null) {
                texto = texto + " Pendiente también: registrar el resultado del buró.";
            }
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(texto,
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.EXPEDIENTE, "② Expediente");
        }
        // 1) La autorización firmada, primero (opción B): es el filtro que ahorra tiempo. "Cumplida"
        // ya exige el consentimiento registrado (construirItemDocumento); autorizacionVigente suma
        // que no esté vencida -- con cualquiera de las dos cosas en falta, el paso es el mismo.
        py.com.capitalcredito.sgcpc.dto.ItemExpediente autorizacion =
                buscarItemExpediente(itemsExpediente, CODIGO_AUTORIZACION_CONSULTA_BURO);
        if (autorizacion != null && (!autorizacion.isCumplido() || !autorizacionVigente)) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(
                    "Siguiente paso: generar la autorización, hacerla firmar y cargarla.",
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.EXPEDIENTE, "② Expediente");
        }
        // 2) Con la autorización en mano, lo que sigue es el buró -- aunque falten papeles.
        if (consultaMasRecienteConEvidencia == null) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(
                    "Siguiente paso: registrar el resultado del buró.",
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.BURO, "③ Consulta al buró");
        }
        // 3) RN-18: con mora no hay "siguiente paso" que ofrecer -- no hay nada que completar para
        // subsanarlo. Mismo texto que el bloqueo real de ServicioSolicitudes.enviarAAprobacion, para
        // que el oficial lea lo mismo acá y al intentar enviar.
        if (buroConMora) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(ServicioSolicitudes.MENSAJE_MORA_INFORMCONF,
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.NINGUNO, null);
        }
        // 4) Recién ahora el resto de los papeles.
        if (!faltantesExpediente.isEmpty()) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(
                    textoFaltantesExpediente(faltantesExpediente, situacionLaboral, solicitudAbierta,
                            compromisoViaPrestamo),
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.EXPEDIENTE, "② Expediente");
        }
        // 5/6/7) Expediente y buró al día: lo que sigue depende de la solicitud.
        if (solicitudAbierta == null) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(
                    "Siguiente paso: iniciar una solicitud de crédito.",
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.SOLICITUD_NUEVA, "④ Solicitud");
        }
        if (!compromisoViaPrestamo
                && solicitudAbierta.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.BORRADOR) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(
                    "Siguiente paso: enviar " + solicitudAbierta.getCodigoSolicitud() + " a aprobación.",
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.SOLICITUD_FICHA,
                    "④ " + solicitudAbierta.getCodigoSolicitud(), solicitudAbierta.getIdSolicitud());
        }
        if (!compromisoViaPrestamo
                && solicitudAbierta.getEstadoSolicitud() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.PENDIENTE_APROBACION) {
            return new py.com.capitalcredito.sgcpc.dto.PasoGuiado("Espera la decisión del gerente.",
                    py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.NINGUNO, null);
        }
        // Resto de los casos (préstamo en curso, solicitud aprobada sin formalizar): el circuito del
        // oficial ya no tiene un paso siguiente propio -- lo que sigue lo hace otra pantalla u otra
        // persona, y la ficha ya enlaza a eso (textoCompromisoAbierto).
        return new py.com.capitalcredito.sgcpc.dto.PasoGuiado(null,
                py.com.capitalcredito.sgcpc.dto.PasoGuiado.Destino.NINGUNO, null);
    }

    /**
     * B88: misma regla con {@link AccionPrevia#NINGUNA} -- para los llamadores que no vienen de
     * cargar un documento (alta y edición de la ficha, registro del resultado del buró, y la propia
     * ficha al dibujar los botones "Siguiente: …").
     */
    public py.com.capitalcredito.sgcpc.dto.PasoGuiado calcularSiguientePaso(
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> itemsExpediente, String situacionLaboral,
            boolean autorizacionVigente,
            py.com.capitalcredito.sgcpc.modelos.ConsultaCrediticia consultaMasRecienteConEvidencia,
            py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitudAbierta, boolean compromisoViaPrestamo) {
        return calcularSiguientePaso(itemsExpediente, situacionLaboral, autorizacionVigente,
                consultaMasRecienteConEvidencia, solicitudAbierta, compromisoViaPrestamo, AccionPrevia.NINGUNA);
    }

    /**
     * Los documentos obligatorios del expediente que todavía faltan, en el orden de las filas. Solo
     * los obligatorios: son los que de verdad frenan "Enviar a aprobación"
     * ({@link #CODIGOS_EXPEDIENTE_OBLIGATORIOS}, la única lista). Puro.
     */
    private List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> faltantesObligatorios(
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> itemsExpediente) {
        List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> faltantes = new java.util.ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.ItemExpediente item : itemsExpediente) {
            if (item.isObligatorio() && !item.isCumplido()) {
                faltantes.add(item);
            }
        }
        return faltantes;
    }

    /** Ítem del expediente por código de tipo, o null si ese tipo no está activo. Puro. */
    private py.com.capitalcredito.sgcpc.dto.ItemExpediente buscarItemExpediente(
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> itemsExpediente, String codigoTipo) {
        for (py.com.capitalcredito.sgcpc.dto.ItemExpediente item : itemsExpediente) {
            if (codigoTipo.equals(item.getTipo().getCodigoTipo())) {
                return item;
            }
        }
        return null;
    }

    /**
     * B88: "Falta: …" / "Faltan: …" con los nombres de los documentos (los de la base,
     * nombre_tipo, los mismos que muestra cada fila del Expediente -- un solo dueño del nombre).
     *
     * <p>Mantiene los dos textos específicos de renovación que ya existían en
     * ControladorClientesServlet.siguientePasoParaEdicion (RF03 Parte 1 -- reprueba, y la tanda de
     * usabilidad del 19/10/2026): cuando lo que falta es volver a cargar la cédula o el comprobante
     * de ingreso porque un cambio en Datos invalidó el anterior, el aviso lo dice así en vez de
     * sonar como si nunca se hubiera cargado nada. Puro.</p>
     */
    private String textoFaltantesExpediente(List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> faltantes,
            String situacionLaboral, py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitudAbierta,
            boolean compromisoViaPrestamo) {
        py.com.capitalcredito.sgcpc.dto.ItemExpediente primero = faltantes.get(0);
        if (primero.isPendienteRenovacion() && CODIGO_CEDULA_IDENTIDAD.equals(primero.getTipo().getCodigoTipo())) {
            return "Siguiente paso: cargar el escaneo de la cédula renovada en el Expediente.";
        }
        if (primero.isPendienteRenovacion() && CODIGO_COMPROBANTE_INGRESO.equals(primero.getTipo().getCodigoTipo())) {
            // Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10b): con un
            // préstamo o una solicitud en curso, el paso no es el de un cliente nuevo que recién
            // empieza -- el texto lo dice, en vez de sonar igual en los dos casos.
            if (solicitudAbierta != null) {
                return "Siguiente paso: cargar el nuevo comprobante de ingreso. El cliente tiene "
                        + (compromisoViaPrestamo ? "un préstamo en curso" : "una solicitud abierta")
                        + "; su expediente debe quedar al día.";
            }
            return "Siguiente paso: cargar el comprobante de ingreso que corresponde a "
                    + etiquetaSituacionLaboral(situacionLaboral) + " en el Expediente.";
        }
        StringBuilder texto = new StringBuilder(faltantes.size() == 1 ? "Falta: " : "Faltan: ");
        for (int indice = 0; indice < faltantes.size(); indice++) {
            if (indice > 0) {
                texto.append(", ");
            }
            texto.append(faltantes.get(indice).getEtiqueta());
        }
        return texto.append(".").toString();
    }

    /**
     * Bloque 7/E10 (D-133, pedido de Joel): el informe de Informconf, para el Paso B de
     * "③ Autorización y buró" -- mismo tipo de documento interno de siempre
     * (RESULTADO_CONSULTA_BURO), fuera de la lista del expediente pero con el mismo criterio
     * (VIGENTE o no). Pura, mismo motivo que construirItemsExpediente. No tiene noción de
     * "vencida" (eso es solo de la fila de cédula): fechaVencimientoCedula=null.
     */
    public py.com.capitalcredito.sgcpc.dto.ItemExpediente construirItemInformeBuro(
            List<py.com.capitalcredito.sgcpc.modelos.TipoDocumento> tiposDisponibles,
            List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente> documentosDeLaPersona) {
        return construirItemDocumento("RESULTADO_CONSULTA_BURO", null, tiposDisponibles, documentosDeLaPersona, null,
                false);
    }

    private py.com.capitalcredito.sgcpc.dto.ItemExpediente construirItemDocumento(String codigo, String situacionLaboral,
            List<py.com.capitalcredito.sgcpc.modelos.TipoDocumento> tiposDisponibles,
            List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente> documentosDeLaPersona,
            LocalDate fechaVencimientoCedula, boolean consentimientoBuroRegistrado) {
        py.com.capitalcredito.sgcpc.modelos.TipoDocumento tipo = null;
        if (tiposDisponibles != null) {
            for (py.com.capitalcredito.sgcpc.modelos.TipoDocumento candidato : tiposDisponibles) {
                if (codigo.equals(candidato.getCodigoTipo())) {
                    tipo = candidato;
                    break;
                }
            }
        }
        if (tipo == null) {
            return null;
        }
        boolean obligatorio = CODIGOS_EXPEDIENTE_OBLIGATORIOS.contains(codigo);
        py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente vigente = null;
        if (documentosDeLaPersona != null) {
            for (py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente documento : documentosDeLaPersona) {
                if (codigo.equals(documento.getTipoDocumento().getCodigoTipo())
                        && documento.getEstadoDocumento() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento.VIGENTE) {
                    vigente = documento;
                    break;
                }
            }
        }
        // Hallazgo de Joel (RF03, punto 2: PDF falso cargado como autorización en "Otros
        // documentos", ya quitado): un documento VIGENTE de este tipo no alcanza por sí solo --
        // tiene que existir además un consentimiento registrado (lo crea
        // ServicioConsultaCrediticia.cargarAutorizacionFirmada, atómico con la carga, por su ruta
        // dedicada). Sin eso, es como si nunca se hubiera cargado nada: nunca cuenta como cumplida,
        // y tampoco deja un "Ver" apuntando a un documento que no respalda ninguna autorización real.
        if ("AUTORIZACION_CONSULTA_BURO".equals(codigo) && vigente != null && !consentimientoBuroRegistrado) {
            vigente = null;
        }
        // RF03 (pedido de Joel), Parte 1: solo la fila de cédula puede quedar "vencida" -- un
        // documento VIGENTE en la tabla (el escaneo no fue reemplazado) no dice nada sobre si la
        // cédula real de la persona ya venció.
        boolean vencida = "CEDULA_IDENTIDAD".equals(codigo) && cedulaVencida(fechaVencimientoCedula, Reloj.hoy());
        if (vencida) {
            return new py.com.capitalcredito.sgcpc.dto.ItemExpediente(tipo, etiquetaDocumentoExpediente(codigo, tipo),
                    descripcionDocumentoExpediente(codigo, situacionLaboral), vigente, true, fechaVencimientoCedula,
                    obligatorio);
        }
        // RF03 (pedido de Joel), Parte 1 -- reprueba: actualizar() marca REEMPLAZADO el escaneo
        // anterior en cuanto cambia la fecha de vencimiento, sin subir ninguno nuevo -- vigente
        // queda null, igual que "nunca se cargó nada". La señal para distinguir un caso del otro
        // (sin columna ni tabla nueva) es si ya existe un REEMPLAZADO de este tipo en el
        // historial: "nunca cargó nada" no tiene ninguno; "le invalidamos el escaneo" sí.
        // RF03 (pedido de Joel): mismo criterio para el renglón 2 -- un cambio de situación
        // laboral marca REEMPLAZADO el comprobante de ingreso vigente (ver actualizar()), sin
        // subir ninguno nuevo todavía. fechaVencimiento no aplica acá (eso es solo de la cédula):
        // null para que la JSP no muestre una fecha que no corresponde a este renglón.
        boolean pendienteRenovacion = ("CEDULA_IDENTIDAD".equals(codigo) || "COMPROBANTE_INGRESO".equals(codigo))
                && vigente == null && existeReemplazado(codigo, documentosDeLaPersona);
        if (pendienteRenovacion) {
            LocalDate fechaVencimientoParaItem = "CEDULA_IDENTIDAD".equals(codigo) ? fechaVencimientoCedula : null;
            return new py.com.capitalcredito.sgcpc.dto.ItemExpediente(tipo, etiquetaDocumentoExpediente(codigo, tipo),
                    descripcionDocumentoExpediente(codigo, situacionLaboral), vigente, false, fechaVencimientoParaItem,
                    true, obligatorio);
        }
        return new py.com.capitalcredito.sgcpc.dto.ItemExpediente(tipo,
                etiquetaDocumentoExpediente(codigo, tipo), descripcionDocumentoExpediente(codigo, situacionLaboral),
                vigente, obligatorio);
    }

    /** RF03 (pedido de Joel), Parte 1 -- reprueba: ¿hay algún documento de este tipo en estado
     * REEMPLAZADO en el historial de la persona? Pura. */
    private boolean existeReemplazado(String codigo,
            List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente> documentosDeLaPersona) {
        if (documentosDeLaPersona == null) {
            return false;
        }
        for (py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente documento : documentosDeLaPersona) {
            if (codigo.equals(documento.getTipoDocumento().getCodigoTipo())
                    && documento.getEstadoDocumento() == py.com.capitalcredito.sgcpc.enumeraciones.EstadoDocumento.REEMPLAZADO) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bloque 7/E10 (D-132, pedido de Joel): "Respuesta de Informconf" es más claro que el nombre
     * del tipo de documento en la base ("Resultado de la consulta al buró", pensado para la
     * tabla, no para esta fila) -- solo presentación, el código y el nombre_tipo de la base no
     * cambian (nada se rompe en el chequeo de "Enviar a aprobación" ni en el resto del sistema).
     * RF04 (pedido de Joel, 10/10/2026): mismo texto que la pestaña ③, Paso B.
     */
    private String etiquetaDocumentoExpediente(String codigoTipo, py.com.capitalcredito.sgcpc.modelos.TipoDocumento tipo) {
        if ("RESULTADO_CONSULTA_BURO".equals(codigoTipo)) {
            return "Respuesta de Informconf";
        }
        return tipo.getNombreTipo();
    }

    private String descripcionDocumentoExpediente(String codigoTipo, String situacionLaboral) {
        switch (codigoTipo) {
            case "CEDULA_IDENTIDAD":
                return "Ambos lados, vigente.";
            case "COMPROBANTE_INGRESO":
                return descripcionComprobanteIngresoSegunSituacionLaboral(situacionLaboral);
            case "COMPROBANTE_DOMICILIO":
                return "Factura de ANDE, ESSAP o telefonía (últimos 2 meses).";
            case "AUTORIZACION_CONSULTA_BURO":
                return "Firmada por el cliente. Sin ella no se puede consultar Informconf.";
            case "RESULTADO_CONSULTA_BURO":
                return "Resultado de la consulta, después de la autorización.";
            default:
                return "";
        }
    }

    /** Ajuste B: quita puntos/espacios/guiones, deja solo dígitos ("1.234.567" -> "1234567"). Pura. */
    public String normalizarCedula(String cedula) {
        if (cedula == null) {
            return "";
        }
        return cedula.replaceAll("[.\\-\\s]", "");
    }

    /**
     * Corrección de Joel (revisión de reportes E5): recorta espacios y colapsa dobles en los
     * campos de texto libre de la ficha antes de guardar, para que no lleguen con espacios de
     * más a los imprimibles (contrato, pagaré, etc.). Pura, muta la Persona recibida.
     */
    public void normalizarDatosPersona(Persona persona) {
        // RF02 (pedido de Joel): mayúscula inicial por palabra al guardar -- antes se guardaban
        // tal cual se escribían (el cliente 142 quedó "edad menor prueba" en minúsculas). Solo
        // nombres y apellidos de la persona (no referencias, empleador, etc.), como se aprobó.
        persona.setNombres(NormalizadorTexto.normalizarNombrePropio(persona.getNombres()));
        persona.setApellidos(NormalizadorTexto.normalizarNombrePropio(persona.getApellidos()));
        persona.setTelefonoWhatsapp(NormalizadorTexto.normalizarTextoLibre(persona.getTelefonoWhatsapp()));
        persona.setTelefonoAlternativo(NormalizadorTexto.normalizarTextoLibre(persona.getTelefonoAlternativo()));
        persona.setCorreoElectronico(NormalizadorTexto.normalizarTextoLibre(persona.getCorreoElectronico()));
    }

    /** Valida los datos de la ficha (cédula ya normalizada). Pura, sin DAO. */
    public void validarDatosPersona(Persona persona) {
        normalizarDatosPersona(persona);
        String cedula = persona.getCedulaIdentidad();
        if (cedula == null || cedula.isEmpty() || !cedula.matches("\\d+")) {
            throw new NegocioException("La cédula de identidad debe tener solo números.");
        }
        if (vacio(persona.getNombres())) {
            throw new NegocioException("Ingrese los nombres.");
        }
        if (vacio(persona.getApellidos())) {
            throw new NegocioException("Ingrese los apellidos.");
        }
        if (persona.getFechaNacimiento() == null || persona.getFechaNacimiento().isAfter(Reloj.hoy())) {
            throw new NegocioException("La fecha de nacimiento no puede ser futura.");
        }
        if (vacio(persona.getTelefonoWhatsapp())) {
            throw new NegocioException("Ingrese el teléfono/WhatsApp.");
        }
        // Bloque 7/E10, Tanda A (D-126): el resto de la Tanda A (departamento/ciudad/barrio/
        // dirección/tipo de vivienda/antigüedad) eran todos campos de domicilio -- se validan
        // ahora en validarDomicilio (normalización de personas, grupo Domicilio, 16/10/2026).
        if (persona.getFechaVencimientoCedula() == null) {
            throw new NegocioException("Ingrese la fecha de vencimiento de la cédula.");
        }
        // Bloque 7/E10, Tanda B (D-127): situación laboral, empleador, ingreso e ingresos/deudas
        // eran todos campos laborales -- se validan ahora en validarLaboral (normalización de
        // personas, grupo Laborales, 16/10/2026).
    }

    /**
     * Normalización de personas, grupo Referencias (15/10/2026): reemplaza la validación que antes
     * vivía en validarDatosTandaA sobre persona.referenciaNombre/referencia2Nombre -- mismo
     * criterio de siempre (dos referencias obligatorias, en alta Y edición, pedido de Joel), ahora
     * sobre la lista de personas_referencias. Pura, sin DAO -- visibilidad de paquete, mismo
     * criterio que camposModificados/construirTextoDomicilio, para poder probarla sin la base.
     */
    void validarReferencias(List<PersonaReferencia> referencias) {
        for (PersonaReferencia referencia : referencias) {
            referencia.setNombre(NormalizadorTexto.normalizarTextoLibre(referencia.getNombre()));
            referencia.setTelefono(NormalizadorTexto.normalizarTextoLibre(referencia.getTelefono()));
        }
        PersonaReferencia primera = referenciaNumero(referencias, 1);
        if (primera == null || vacio(primera.getNombre()) || vacio(primera.getTelefono())) {
            throw new NegocioException("Complete la primera referencia personal (nombre y teléfono).");
        }
        PersonaReferencia segunda = referenciaNumero(referencias, 2);
        if (segunda == null || vacio(segunda.getNombre()) || vacio(segunda.getTelefono())) {
            throw new NegocioException("Complete la segunda referencia personal (nombre y teléfono).");
        }
    }

    private PersonaReferencia referenciaNumero(List<PersonaReferencia> referencias, int numero) {
        for (PersonaReferencia referencia : referencias) {
            if (referencia.getNumeroReferencia() == numero) {
                return referencia;
            }
        }
        return null;
    }

    /**
     * Normalización de personas, grupo Cónyuge (16/10/2026): reemplaza la validación que antes
     * vivía en validarDatosTandaA sobre persona.conyugeNombre/conyugeCedula/conyugeTelefono --
     * ahora sobre personas_conyuge. A diferencia de validarReferencias (siempre obligatorias), el
     * cónyuge solo es obligatorio si el estado civil lo exige (ESTADOS_CIVILES_CON_CONYUGE); con
     * otro estado civil la fila no debe existir (bd/01_esquema.sql), así que se descarta cualquier
     * dato recibido devolviendo null -- "solo existe si corresponde". Pura, sin DAO -- visibilidad
     * de paquete, mismo criterio que validarReferencias, para poder probarla sin la base.
     */
    PersonaConyuge validarConyuge(String estadoCivil, PersonaConyuge conyuge) {
        if (!ESTADOS_CIVILES_CON_CONYUGE.contains(estadoCivil)) {
            return null;
        }
        if (conyuge == null || vacio(conyuge.getNombre()) || vacio(conyuge.getCedula()) || vacio(conyuge.getTelefono())) {
            throw new NegocioException("Con estado civil \"" + estadoCivil
                    + "\", complete el nombre, la cédula y el teléfono del cónyuge.");
        }
        conyuge.setNombre(NormalizadorTexto.normalizarTextoLibre(conyuge.getNombre()));
        conyuge.setTelefono(NormalizadorTexto.normalizarTextoLibre(conyuge.getTelefono()));
        conyuge.setCedula(normalizarCedula(conyuge.getCedula()));
        return conyuge;
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): reemplaza la validación que antes
     * vivía en validarDatosTandaA sobre persona.idCiudad/barrio/direccionDomicilio/tipoVivienda/
     * antiguedadDomicilioAnios -- ahora sobre personas_domicilio. Siempre obligatorio (a
     * diferencia de validarConyuge, nunca condicional) -- toda persona tiene domicilio. No valida
     * el departamento acá (eso es validarCiudadPerteneceADepartamento, que sí necesita la
     * Connection): esto es solo lo que no necesita la base. Pura, sin DAO -- visibilidad de
     * paquete, mismo criterio que validarReferencias/validarConyuge.
     */
    void validarDomicilio(PersonaDomicilio domicilio) {
        if (domicilio.getIdCiudad() == null) {
            throw new NegocioException("Seleccione la ciudad del domicilio.");
        }
        if (vacio(domicilio.getDireccionDomicilio())) {
            throw new NegocioException("Ingrese la dirección (calle y número) del domicilio.");
        }
        if (vacio(domicilio.getBarrio())) {
            throw new NegocioException("Ingrese el barrio del domicilio.");
        }
        if (vacio(domicilio.getTipoVivienda())) {
            throw new NegocioException("Seleccione el tipo de vivienda.");
        }
        if (domicilio.getAntiguedadDomicilioAnios() == null || domicilio.getAntiguedadDomicilioAnios() < 0) {
            throw new NegocioException("Ingrese la antigüedad en el domicilio (en años).");
        }
        // B13 (01/11/2026, decisión de Joel): la dirección y el barrio se guardan capitalizados,
        // con el MISMO normalizador que los nombres y apellidos (RF02) -- antes la dirección no
        // pasaba por ningún normalizador y el barrio solo por el de espacios, así que un dato
        // cargado en minúsculas ("mburicao 444") salía tal cual en el contrato, el comprobante de
        // desembolso y la factura. Se normaliza AL GUARDAR, no al imprimir, para que la ficha y
        // los PDF digan siempre lo mismo. La coma duplicada ya estaba resuelta aparte, al armar el
        // texto compuesto (limpiarParte). La referencia coloquial NO se capitaliza: no va en
        // ningún documento legal (B.15) y es texto de uso interno.
        domicilio.setDireccionDomicilio(
                NormalizadorTexto.normalizarNombrePropio(domicilio.getDireccionDomicilio()));
        domicilio.setBarrio(NormalizadorTexto.normalizarNombrePropio(domicilio.getBarrio()));
        domicilio.setReferenciaDomicilio(NormalizadorTexto.normalizarTextoLibre(domicilio.getReferenciaDomicilio()));
    }

    /**
     * Normalización de personas, grupo Laborales (16/10/2026): reemplaza la validación que antes
     * vivía en validarDatosTandaB sobre persona.situacionLaboral/empleadorNombre (y el resto de
     * los campos del empleador)/ingresoNetoComprobado/presentaComprobanteIngreso/
     * otrosIngresosMensuales/gastosFijosMensuales/
     * cuotaOtrasDeudasMensual -- ahora sobre personas_laborales. Situación laboral (lista
     * cerrada), datos del empleador (obligatorios solo si la situación laboral es "tiene un
     * empleador": asalariado del sector privado o funcionario público), ingreso neto comprobado
     * y la cuota mensual de otras deudas -- la única de las dos cifras que entra en la fórmula de
     * capacidad de pago (otros ingresos y gastos fijos son solo informativos). Siempre
     * obligatorio (a diferencia de validarConyuge, nunca condicional) -- toda persona tiene datos
     * laborales. Pura, sin DAO -- visibilidad de paquete, mismo criterio que
     * validarReferencias/validarConyuge/validarDomicilio.
     */
    void validarLaboral(PersonaLaboral laboral) {
        if (vacio(laboral.getSituacionLaboral()) || !SITUACIONES_LABORALES.contains(laboral.getSituacionLaboral())) {
            throw new NegocioException("Seleccione la situación laboral.");
        }
        if (SITUACIONES_LABORALES_CON_EMPLEADOR.contains(laboral.getSituacionLaboral())) {
            if (vacio(laboral.getEmpleadorNombre()) || vacio(laboral.getEmpleadorDireccion())
                    || vacio(laboral.getEmpleadorTelefono()) || vacio(laboral.getCargo())
                    || laboral.getAntiguedadLaboralAnios() == null) {
                throw new NegocioException("Complete los datos del empleador (nombre, dirección, teléfono, "
                        + "cargo y antigüedad).");
            }
            if (laboral.getAntiguedadLaboralAnios() < 0) {
                throw new NegocioException("La antigüedad laboral no puede ser negativa.");
            }
        }
        if (laboral.getIngresoNetoComprobado() == null
                || laboral.getIngresoNetoComprobado().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El ingreso neto comprobado no puede ser negativo.");
        }
        if (laboral.getOtrosIngresosMensuales() != null
                && laboral.getOtrosIngresosMensuales().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("Los otros ingresos mensuales no pueden ser negativos.");
        }
        if (laboral.getGastosFijosMensuales() != null
                && laboral.getGastosFijosMensuales().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("Los gastos fijos mensuales no pueden ser negativos.");
        }
        if (laboral.getCuotaOtrasDeudasMensual() == null) {
            throw new NegocioException("Ingrese la cuota mensual de otras deudas (0 si no tiene).");
        }
        if (laboral.getCuotaOtrasDeudasMensual().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("La cuota mensual de otras deudas no puede ser negativa.");
        }
        // Bloque 7/E10 (D-129, Obs. 18): formato del RUC del empleador, solo si se cargó uno
        // (el campo es opcional) -- validación de forma (número-dígito verificador), no del
        // dígito verificador en sí (Módulo 11), para no bloquear datos reales por un cálculo que
        // el sistema todavía no hace en ningún otro lado.
        if (!vacio(laboral.getEmpleadorRuc()) && !laboral.getEmpleadorRuc().matches("\\d{6,9}-\\d")) {
            throw new NegocioException("El RUC del empleador debe tener el formato número-dígito verificador (ej.: 80012345-6).");
        }
        laboral.setEmpleadorNombre(NormalizadorTexto.normalizarTextoLibre(laboral.getEmpleadorNombre()));
        laboral.setEmpleadorDireccion(NormalizadorTexto.normalizarTextoLibre(laboral.getEmpleadorDireccion()));
        laboral.setEmpleadorTelefono(NormalizadorTexto.normalizarTextoLibre(laboral.getEmpleadorTelefono()));
        laboral.setCargo(NormalizadorTexto.normalizarTextoLibre(laboral.getCargo()));
    }

    /**
     * 2c (pedido de Joel, 17/10/2026): una solicitud PENDIENTE_APROBACION, APROBADA sin
     * formalizar, o un préstamo PENDIENTE_DESEMBOLSO de la persona -- en cualquiera de los tres
     * casos los datos de evaluación quedan congelados (ver bloquearSiCambianDatosDeEvaluacion).
     * BORRADOR no cuenta (el oficial todavía puede volver a simular). Devuelve null si no hay
     * ninguna.
     */
    public static final class EvaluacionEnCurso {
        private final String codigo;
        private final String etapa;
        private final String verboAccionGerente;
        // F2 (21/10/2026, pedido de Joel, punto 7): para la versión (b) -- qué puede hacer QUIEN
        // SÍ tiene el permiso, y cuál es ese permiso (nunca por nombre de rol).
        private final String verboAccionPropia;
        private final String pantallaAccionPropia;
        private final py.com.capitalcredito.sgcpc.enumeraciones.Permiso permisoParaResolver;

        EvaluacionEnCurso(String codigo, String etapa, String verboAccionGerente, String verboAccionPropia,
                String pantallaAccionPropia, py.com.capitalcredito.sgcpc.enumeraciones.Permiso permisoParaResolver) {
            this.codigo = codigo;
            this.etapa = etapa;
            this.verboAccionGerente = verboAccionGerente;
            this.verboAccionPropia = verboAccionPropia;
            this.pantallaAccionPropia = pantallaAccionPropia;
            this.permisoParaResolver = permisoParaResolver;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getEtapa() {
            return etapa;
        }

        public String getVerboAccionGerente() {
            return verboAccionGerente;
        }

        public String getVerboAccionPropia() {
            return verboAccionPropia;
        }

        public String getPantallaAccionPropia() {
            return pantallaAccionPropia;
        }

        public py.com.capitalcredito.sgcpc.enumeraciones.Permiso getPermisoParaResolver() {
            return permisoParaResolver;
        }
    }

    public EvaluacionEnCurso buscarEvaluacionEnCursoPorCliente(Connection conexion, long idPersona) throws SQLException {
        py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitudEnEvaluacion =
                solicitudDAO.buscarEnEvaluacionPorCliente(conexion, idPersona);
        if (solicitudEnEvaluacion != null) {
            boolean pendiente = solicitudEnEvaluacion.getEstadoSolicitud()
                    == py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud.PENDIENTE_APROBACION;
            return new EvaluacionEnCurso(solicitudEnEvaluacion.getCodigoSolicitud(),
                    pendiente ? "está pendiente de aprobación" : "ya está aprobada",
                    pendiente ? "la rechace" : "la cancele",
                    pendiente ? "rechazarla" : "cancelarla", "Aprobación",
                    py.com.capitalcredito.sgcpc.enumeraciones.Permiso.APROBAR_RECHAZAR_SOLICITUD);
        }
        py.com.capitalcredito.sgcpc.modelos.PrestamoCredito prestamoPendiente =
                prestamoDAO.buscarPendienteDesembolsoPorCliente(conexion, idPersona);
        if (prestamoPendiente != null) {
            return new EvaluacionEnCurso(prestamoPendiente.getIdentificadorOperacion(),
                    "está pendiente de desembolso", "lo anule", "anularlo", "Formalización",
                    py.com.capitalcredito.sgcpc.enumeraciones.Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO);
        }
        return null;
    }

    /**
     * B91 (hallazgo de Joel del 29/10/2026): dónde se va a leer el aviso -- lo único que cambia
     * es cómo empieza la frase.
     *
     * <p>Hace falta porque el banner de la ficha convive con campos que **sí** se pueden editar
     * (nombre, cédula, referencias, otros ingresos y gastos): ahí "Estos datos" no decía cuáles,
     * y los congelados se distinguen porque se ven en gris. En el rechazo del servidor, en cambio,
     * no hay nada gris que señalar -- es un modal después de intentar guardar.</p>
     */
    public enum ContextoAvisoEvaluacion {
        /** Banner amarillo de la ficha, al lado de los campos congelados (en gris). */
        FICHA_CON_CAMPOS_EN_GRIS,
        /** Rechazo del servidor al intentar guardar (modal): no hay campos a la vista. */
        RECHAZO_DEL_SERVIDOR
    }

    /** Pura: el texto exacto que pidió Joel, armado con los datos de EvaluacionEnCurso.
     * F2 (21/10/2026, pedido de Joel, punto 7): puedeResolver decide la versión (a) -- igual a
     * siempre, "informe al gerente" -- o (b) -- la acción directa, por pantalla propia
     * (Aprobación/Formalización) --, calculado en el controlador con
     * nivelAccesoSesion(evaluacion.getPermisoParaResolver()) != NivelAcceso.NINGUNO, nunca por
     * nombre de rol.
     *
     * <p>B91 (decisión de Joel, 29/10/2026): el arranque de la frase depende de dónde se lee
     * (ver {@link ContextoAvisoEvaluacion}) -- "Los datos en gris" en la ficha, "Los datos de la
     * evaluación" en el rechazo del servidor. Una sola redacción, dos arranques: el resto del
     * texto (el sujeto y el "qué puede hacer") es exactamente el mismo de siempre, y no se lista
     * cuáles son los campos. */
    public String mensajeEvaluacionBloqueada(EvaluacionEnCurso evaluacion, boolean puedeResolver,
            ContextoAvisoEvaluacion contexto) {
        String sujeto = (evaluacion.getVerboAccionGerente().startsWith("lo") ? "el préstamo " : "la solicitud ")
                + evaluacion.getCodigo() + " " + evaluacion.getEtapa();
        String arranque = contexto == ContextoAvisoEvaluacion.FICHA_CON_CAMPOS_EN_GRIS
                ? "Los datos en gris no se pueden modificar porque "
                : "Los datos de la evaluación no se pueden modificar porque ";
        if (puedeResolver) {
            return arranque + sujeto + ". Puede "
                    + evaluacion.getVerboAccionPropia() + " desde " + evaluacion.getPantallaAccionPropia()
                    + ", registrando el motivo, si los datos del cliente cambiaron.";
        }
        return arranque + sujeto
                + ". Si los datos del cliente cambiaron, informe al gerente para que "
                + evaluacion.getVerboAccionGerente() + ", registrando el motivo.";
    }

    /**
     * 2c (pedido de Joel, 17/10/2026): bloquea el guardado si, habiendo una evaluación en curso,
     * alguno de los 7 datos de evaluación (los 5 laborales + fecha de nacimiento + ciudad del
     * domicilio, que definen edad y zona evaluadas) cambió -- el resto de la ficha (nombre,
     * teléfono, domicilio salvo la ciudad, cónyuge, referencias) se sigue pudiendo editar igual
     * que siempre (D-129: "la edición siempre guarda"). Solo se consulta si hay una evaluación en
     * curso cuando de verdad cambió algo de esto, para no pagar el costo de la consulta en el
     * caso común (nada de esto cambió).
     */
    private void bloquearSiCambianDatosDeEvaluacion(Connection conexion, Persona anterior, Persona nueva,
            PersonaLaboral laboralAnterior, PersonaLaboral laboralNuevo, Long idCiudadAnterior, Long idCiudadNueva,
            boolean puedeAprobarRechazarSolicitud, boolean puedeAnularPrestamo) throws SQLException {
        if (!cambiaronDatosDeEvaluacion(anterior, nueva, laboralAnterior, laboralNuevo, idCiudadAnterior, idCiudadNueva)) {
            return;
        }
        EvaluacionEnCurso evaluacion = buscarEvaluacionEnCursoPorCliente(conexion, anterior.getIdPersona());
        if (evaluacion != null) {
            // F2 (21/10/2026, pedido de Joel, punto 7): el controlador pasa los 2 permisos ya
            // resueltos (nivelAccesoSesion, nunca por rol) porque todavía no sabe cuál de los 2
            // casos de EvaluacionEnCurso va a aplicar -- recién se sabe acá, una vez resuelta.
            boolean puedeResolver = evaluacion.getPermisoParaResolver()
                    == py.com.capitalcredito.sgcpc.enumeraciones.Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO
                    ? puedeAnularPrestamo : puedeAprobarRechazarSolicitud;
            // B91: acá no hay campos a la vista (es el modal del rechazo), así que el aviso
            // habla de "los datos de la evaluación", no de "los datos en gris".
            throw new NegocioException(mensajeEvaluacionBloqueada(evaluacion, puedeResolver,
                    ContextoAvisoEvaluacion.RECHAZO_DEL_SERVIDOR));
        }
    }

    /**
     * La parte PURA de bloquearSiCambianDatosDeEvaluacion -- separada para poder probarla con
     * JUnit sin conexión (mismo criterio que camposModificados). Visibilidad de paquete.
     */
    boolean cambiaronDatosDeEvaluacion(Persona anterior, Persona nueva, PersonaLaboral laboralAnterior,
            PersonaLaboral laboralNuevo, Long idCiudadAnterior, Long idCiudadNueva) {
        return !java.util.Objects.equals(anterior.getFechaNacimiento(), nueva.getFechaNacimiento())
                || !java.util.Objects.equals(idCiudadAnterior, idCiudadNueva)
                || !java.util.Objects.equals(laboralAnterior.getSituacionLaboral(), laboralNuevo.getSituacionLaboral())
                || laboralAnterior.isPresentaComprobanteIngreso() != laboralNuevo.isPresentaComprobanteIngreso()
                || !java.util.Objects.equals(laboralAnterior.getIngresoNetoComprobado(), laboralNuevo.getIngresoNetoComprobado())
                || !java.util.Objects.equals(laboralAnterior.getCuotaOtrasDeudasMensual(), laboralNuevo.getCuotaOtrasDeudasMensual())
                || !java.util.Objects.equals(laboralAnterior.getEmpleadorNombre(), laboralNuevo.getEmpleadorNombre())
                || !java.util.Objects.equals(laboralAnterior.getEmpleadorRuc(), laboralNuevo.getEmpleadorRuc())
                || !java.util.Objects.equals(laboralAnterior.getEmpleadorDireccion(), laboralNuevo.getEmpleadorDireccion())
                || !java.util.Objects.equals(laboralAnterior.getEmpleadorTelefono(), laboralNuevo.getEmpleadorTelefono())
                || !java.util.Objects.equals(laboralAnterior.getCargo(), laboralNuevo.getCargo())
                || !java.util.Objects.equals(laboralAnterior.getAntiguedadLaboralAnios(), laboralNuevo.getAntiguedadLaboralAnios());
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): el departamento no se persiste
     * (se obtiene siempre por JOIN desde la ciudad) -- pero el `<select>` de Departamento de la
     * ficha sigue viajando en el `POST` (filtra el `<select>` de ciudad en el navegador y
     * alimenta la Precalificación) y esto confirma, igual que antes (D-126), que la ciudad
     * elegida pertenezca al departamento que se envió. Reemplaza a armarDomicilioDesdePartes, que
     * además armaba y persistía el texto -- eso ahora es obtenerTextoDomicilio, aparte, porque ya
     * no se guarda en cada alta/edición, solo se lee al vuelo cuando un PDF lo necesita.
     */
    private void validarCiudadPerteneceADepartamento(Connection conexion, Long idDepartamento, Long idCiudad)
            throws SQLException {
        Ciudad ciudad = ciudadDAO.buscarPorId(conexion, idCiudad);
        if (ciudad == null) {
            throw new NegocioException("La ciudad elegida no existe.");
        }
        // 2c (pedido de Joel, 17/10/2026): el <select> de Departamento queda deshabilitado junto
        // con el resto de los datos de evaluación bloqueados (clientes/ficha.jsp) -- sin valor
        // enviado, el servidor confía en el departamento real de la ciudad ya guardada, en vez
        // de exigir uno (nunca se persiste, solo se usaba para validar esta pareja).
        if (idDepartamento == null) {
            return;
        }
        Departamento departamento = departamentoDAO.buscarPorId(conexion, idDepartamento);
        if (departamento == null) {
            throw new NegocioException("El departamento seleccionado no existe.");
        }
        if (!ciudad.getIdDepartamento().equals(departamento.getIdDepartamento())) {
            throw new NegocioException("La ciudad elegida no pertenece al departamento seleccionado.");
        }
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): única fuente del texto legal del
     * domicilio -- antes se armaba una sola vez (armarDomicilioDesdePartes) y se guardaba en
     * personas.domicilio; ahora personas_domicilio no guarda ningún texto, así que se arma al
     * vuelo, con un JOIN a ciudades/departamentos, cada vez que algún PDF lo necesita
     * (ServicioCobranza/ServicioDesembolso/ServicioFormalizacion/ServicioConsultaCrediticia, que
     * antes leían persona.getDomicilio()). Mismo construirTextoDomicilio de siempre, sin cambiar
     * su lógica/orden/puntuación -- un PDF nuevo con los mismos datos de ciudad/barrio/dirección/
     * referencia da exactamente el mismo texto que antes (D-94: los PDF ya emitidos quedan
     * guardados como bytes y nunca vuelven a leer esto). Null si la persona no existe o todavía
     * no tiene domicilio guardado (no debería pasar en la práctica, toda persona lo tiene).
     */
    public String obtenerTextoDomicilio(Connection conexion, long idPersona) throws SQLException {
        PersonaDomicilio domicilio = personaDomicilioDAO.obtenerPorPersona(conexion, idPersona);
        if (domicilio == null) {
            return null;
        }
        Ciudad ciudad = ciudadDAO.buscarPorId(conexion, domicilio.getIdCiudad());
        Departamento departamento = departamentoDAO.buscarPorId(conexion, ciudad.getIdDepartamento());
        return construirTextoDomicilio(domicilio.getDireccionDomicilio(), domicilio.getBarrio(),
                ciudad.getNombre(), departamento.getNombre(), domicilio.getReferenciaDomicilio());
    }

    /**
     * Normalización de personas, grupo Domicilio (16/10/2026): el departamento de una persona ya
     * no se guarda -- se obtiene siempre por JOIN desde su ciudad. Única fuente para política de
     * crédito (validarPoliticaCreditoAlta/listarMotivosNoCalifica), solicitudes/aprobaciones e
     * informes. Null si la persona no tiene ciudad guardada.
     */
    public String nombreDepartamentoDesdeCiudad(Connection conexion, Long idCiudad) throws SQLException {
        if (idCiudad == null) {
            return null;
        }
        Ciudad ciudad = ciudadDAO.buscarPorId(conexion, idCiudad);
        if (ciudad == null) {
            return null;
        }
        Departamento departamento = departamentoDAO.buscarPorId(conexion, ciudad.getIdDepartamento());
        return departamento == null ? null : departamento.getNombre();
    }

    /**
     * RF03 Parte 3 (pedido de Joel): calle y número primero (direccionDomicilio, obligatoria en
     * la ficha desde D-129 pero hasta ahora nunca usada acá), barrio, ciudad y departamento --
     * este último una sola vez si el nombre coincide con el de la ciudad (ej. "Asunción,
     * Asunción" quedaba así porque Asunción es ciudad Y departamento). Pura -- visibilidad de
     * paquete (no private), mismo criterio que camposModificados, para poder probarla sin la
     * base y para que la migración de recálculo masivo (bd/migraciones/) pueda documentar la
     * misma regla sin tener que adivinarla del SQL.
     */
    String construirTextoDomicilio(String direccionDomicilio, String barrio, String nombreCiudad,
            String nombreDepartamento, String referenciaDomicilio) {
        List<String> partes = new java.util.ArrayList<>();
        String direccionLimpia = limpiarParte(direccionDomicilio);
        String barrioLimpio = limpiarParte(barrio);
        if (!vacio(direccionLimpia)) {
            partes.add(direccionLimpia);
        }
        if (!vacio(barrioLimpio)) {
            partes.add(barrioLimpio);
        }
        partes.add(nombreCiudad);
        if (!nombreCiudad.equals(nombreDepartamento)) {
            partes.add(nombreDepartamento);
        }
        String domicilio = String.join(", ", partes);
        if (!vacio(referenciaDomicilio)) {
            domicilio += " (" + referenciaDomicilio + ")";
        }
        return domicilio;
    }

    /**
     * B13 (fase 2, pedido de Joel): direccionDomicilio y barrio pueden traer una coma, un punto
     * y coma o espacios pegados al principio o al final (dato cargado asi en el campo) --
     * String.join(", ", partes) duplicaba ese separador ("Barcequillo,,"). Pura, no cambia el
     * orden ni la puntuacion entre partes, solo el contenido de cada una.
     */
    private String limpiarParte(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.trim().replaceAll("^[ ,;]+", "").replaceAll("[ ,;]+$", "");
    }

    private boolean vacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }

    /**
     * B.15 (consolidación, pedido de Joel): el domicilio compuesto ({@link #construirTextoDomicilio})
     * guarda la referencia coloquial al final, entre paréntesis -- útil para uso operativo (la
     * ficha del cliente), pero no corresponde en un documento legal (contrato, pagaré,
     * comprobante de desembolso). Pura: solo quita ese paréntesis final si existe, sin tocar el
     * resto del domicilio ni volver a armarlo desde las partes.
     */
    public static String domicilioSinReferencia(String domicilioCompuesto) {
        if (domicilioCompuesto == null) {
            return null;
        }
        return domicilioCompuesto.replaceAll("\\s*\\([^()]*\\)\\s*$", "");
    }

    public Persona buscarPorCedula(Connection conexion, String cedula) throws SQLException {
        return personaDAO.buscarPorCedula(conexion, normalizarCedula(cedula));
    }

    public Persona buscarPorId(Connection conexion, long idPersona) throws SQLException {
        return personaDAO.buscarPorId(conexion, idPersona);
    }

    /** Normalización de personas, grupo Referencias (15/10/2026): para la ficha (ver) y para
     * poder volver a mostrarlas si un alta/edición se rechaza. Ordenadas por número de referencia. */
    public List<PersonaReferencia> listarReferencias(Connection conexion, long idPersona) throws SQLException {
        return personaReferenciaDAO.listarPorPersona(conexion, idPersona);
    }

    /** Normalización de personas, grupo Cónyuge (16/10/2026): para la ficha (ver) y para poder
     * volver a mostrarlo si un alta/edición se rechaza. Null si la persona no tiene cónyuge
     * registrado (estado civil que no lo exige, o nunca lo tuvo). */
    public PersonaConyuge obtenerConyuge(Connection conexion, long idPersona) throws SQLException {
        return personaConyugeDAO.obtenerPorPersona(conexion, idPersona);
    }

    /** Normalización de personas, grupo Baja (16/10/2026): para la ficha, solo cuando
     * estado_persona = INACTIVA (ver ControladorClientesServlet.mostrarFicha). Null si el
     * cliente nunca fue dado de baja. */
    public PersonaBaja obtenerBaja(Connection conexion, long idPersona) throws SQLException {
        return personaBajaDAO.obtenerPorPersona(conexion, idPersona);
    }

    /** Normalización de personas, grupo Domicilio (16/10/2026): para la ficha (ver) y para poder
     * volver a mostrarlo si un alta/edición se rechaza. */
    public PersonaDomicilio obtenerDomicilio(Connection conexion, long idPersona) throws SQLException {
        return personaDomicilioDAO.obtenerPorPersona(conexion, idPersona);
    }

    /** Normalización de personas, grupo Domicilio (16/10/2026): id_ciudad de un lote de personas
     * en una sola consulta -- para el listado/informe/CSV de Clientes y ServicioInformes, que
     * antes leían persona.getIdCiudad() directo. */
    public Map<Long, Long> mapaIdCiudadPorPersona(Connection conexion, List<Long> idsPersona) throws SQLException {
        return personaDomicilioDAO.mapaIdCiudadPorPersona(conexion, idsPersona);
    }

    /** Normalización de personas, grupo Laborales (16/10/2026): para la ficha (ver), para poder
     * volver a mostrarlo si un alta/edición se rechaza, y única fuente de los datos de capacidad
     * de pago (ingreso, cuota de otras deudas) para ServicioSolicitudes (crear/simular/
     * enviarAAprobacion) y ServicioFormalizacion (revalidación RF11). */
    public PersonaLaboral obtenerLaboral(Connection conexion, long idPersona) throws SQLException {
        return personaLaboralDAO.obtenerPorPersona(conexion, idPersona);
    }

    /** Normalización de personas, grupo Laborales (16/10/2026): situación laboral de un lote de
     * personas en una sola consulta -- para el listado/informe/CSV de Clientes y ServicioInformes,
     * que antes leían persona.getSituacionLaboral() directo. */
    public Map<Long, String> mapaSituacionLaboralPorPersona(Connection conexion, List<Long> idsPersona)
            throws SQLException {
        return personaLaboralDAO.mapaSituacionLaboralPorPersona(conexion, idsPersona);
    }

    /**
     * RF02 (pedido de Joel): "si no califica, ni siquiera se puede cargar" -- al REGISTRAR, edad/
     * zona/situación laboral fuera de la política vigente, o sin comprobante de ingresos,
     * rechazan el alta (NegocioException, antes de guardar nada). Reutiliza evaluarPoliticaCredito (Grupo 1),
     * sin fórmula nueva. Nunca se llama desde actualizar(): la edición de un cliente existente
     * NUNCA se bloquea por esto (pedido explícito de Joel -- hay que poder corregir el teléfono
     * de alguien que ya no califica, por ejemplo).
     */
    private void validarPoliticaCreditoAlta(Connection conexion, Persona persona, Long idCiudad, PersonaLaboral laboral)
            throws SQLException {
        // RF03 (pedido de Joel), Parte 1: mismo criterio que la edad -- no se registra con la
        // cédula ya vencida (validarDatosTandaA ya exige que exista la fecha; esto exige que no
        // esté en el pasado).
        if (cedulaVencida(persona.getFechaVencimientoCedula(), Reloj.hoy())) {
            throw new NegocioException("La cédula de identidad está vencida (venció el "
                    + FormateadorFechas.comoDdMmAaaa(persona.getFechaVencimientoCedula())
                    + "); no se puede registrar al cliente hasta que la renueve.");
        }
        int edadMinima = obtenerEdadMinima(conexion);
        int edadMaxima = obtenerEdadMaxima(conexion);
        List<String> departamentosHabilitados = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        List<String> situacionesAdmitidas = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        // Normalización de personas, grupo Domicilio (16/10/2026): el departamento ya no viene de
        // persona.getIdDepartamento() (no existe más) -- se obtiene siempre por JOIN desde la
        // ciudad ya validada.
        String nombreDepartamento = nombreDepartamentoDesdeCiudad(conexion, idCiudad);
        int edad = calcularEdad(persona.getFechaNacimiento(), Reloj.hoy());
        // Normalización de personas, grupo Laborales (16/10/2026): situación laboral y
        // presenta comprobante de ingreso ya no vienen de persona.getSituacionLaboral()/
        // isPresentaComprobanteIngreso() (no existen más) -- vienen del PersonaLaboral recibido.
        evaluarPoliticaCredito(edad, edadMinima, edadMaxima, nombreDepartamento, departamentosHabilitados,
                laboral.getSituacionLaboral(), situacionesAdmitidas);
        String motivoComprobante = motivoSinComprobanteSiAplica(laboral.isPresentaComprobanteIngreso());
        if (motivoComprobante != null) {
            throw new NegocioException(motivoComprobante);
        }
    }

    /** Corrección (pedido de Joel, incidente real: evaluarPrecalificacion y validarPoliticaCreditoAlta
     * tenían cada una su PROPIA copia literal de este texto -- al actualizar una se desactualizó la
     * otra, y el indicador en vivo mostró el mensaje viejo). Única fuente del texto (constante) Y de
     * la condición -- las dos llaman a este método, nunca pueden volver a divergir. Pura. null si
     * no aplica (tiene comprobante). */
    static final String MOTIVO_SIN_COMPROBANTE =
            "Sin comprobante de ingresos no puede solicitar un crédito (política vigente).";

    String motivoSinComprobanteSiAplica(boolean presentaComprobanteIngreso) {
        if (presentaComprobanteIngreso) {
            return null;
        }
        return MOTIVO_SIN_COMPROBANTE;
    }

    /** RF05/RF06/RF11 (pedido de Joel, 18/10/2026): hueco preexistente destapado al quitar el
     * codeudor -- antes, sin comprobante quedaba bloqueado indirectamente porque RN-04a exigía
     * un codeudor que la política vigente del negocio nunca dejaba asignar; al sacar esa
     * regla, el bloqueo se perdió. Se corrige con un bloqueo directo en los tres momentos de la
     * solicitud (crear, enviar a aprobación, formalizar), mismo texto en los tres -- ÚNICA fuente
     * (constante y condición), mismo criterio que MOTIVO_SIN_COMPROBANTE de arriba (ese es el del
     * alta del cliente, con su propio texto, no éste). Pura, visibilidad de paquete: cada
     * llamador (ServicioSolicitudes, ServicioFormalizacion) le pasa el booleano ya resuelto. */
    static final String MOTIVO_SIN_COMPROBANTE_SOLICITUD =
            "El cliente no presenta comprobante de ingresos. Solo se otorgan créditos a personas con "
            + "ingreso fijo comprobable.";

    String motivoSinComprobanteSolicitudSiAplica(boolean presentaComprobanteIngreso) {
        if (presentaComprobanteIngreso) {
            return null;
        }
        return MOTIVO_SIN_COMPROBANTE_SOLICITUD;
    }

    /** La persona no presenta comprobante de ingresos: sin comprobante, no hay forma de que
     * califique. Recibe el booleano ya resuelto (no la Persona completa) para poder reutilizarla
     * desde listarMotivosNoCalifica (que usa un texto distinto, de lista, no este). */
    private boolean sinComprobanteNoCalifica(boolean presentaComprobanteIngreso) {
        return !presentaComprobanteIngreso;
    }

    /**
     * RF02 (pedido de Joel): motivos por los que esta Persona, TAL COMO QUEDÓ guardada, hoy no
     * calificaría para una solicitud nueva -- nunca bloquea ni se llama antes de guardar (a
     * diferencia de validarPoliticaCreditoAlta): se usa recién después de un alta o una edición
     * exitosa, para el aviso amarillo "se guardó, pero atención". Junta TODOS los motivos que
     * apliquen (no se detiene en el primero, a diferencia del rechazo de alta), para avisar todo
     * de una vez. Pura en el sentido de no escribir nada; necesita Connection para leer los
     * parámetros vigentes y el nombre del departamento.
     */
    public List<String> listarMotivosNoCalifica(Connection conexion, Persona persona, Long idCiudad,
            PersonaLaboral laboral) throws SQLException {
        List<String> motivos = new java.util.ArrayList<>();
        int edadMinima = obtenerEdadMinima(conexion);
        int edadMaxima = obtenerEdadMaxima(conexion);
        int edad = calcularEdad(persona.getFechaNacimiento(), Reloj.hoy());
        if (edad < edadMinima || edad > edadMaxima) {
            motivos.add("la edad permitida es de " + edadMinima + " a " + edadMaxima + " años; esta persona tiene "
                    + edad + ".");
        }
        List<String> departamentosHabilitados = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
        // Normalización de personas, grupo Domicilio (16/10/2026): departamento por JOIN desde la
        // ciudad (ya no existe persona.getIdDepartamento()).
        String nombreDepartamento = nombreDepartamentoDesdeCiudad(conexion, idCiudad);
        if (nombreDepartamento == null || !departamentosHabilitados.contains(nombreDepartamento)) {
            motivos.add("la zona (" + (nombreDepartamento == null ? "sin completar" : nombreDepartamento)
                    + ") no está habilitada por la política vigente.");
        }
        List<String> situacionesAdmitidas = valoresSeparadosPorComa(
                servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
        // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que
        // validarPoliticaCreditoAlta -- situación laboral y comprobante de ingreso vienen del
        // PersonaLaboral recibido, ya no de persona.
        if (laboral.getSituacionLaboral() == null || !situacionesAdmitidas.contains(laboral.getSituacionLaboral())) {
            motivos.add("la situación laboral no está admitida por la política vigente.");
        }
        if (sinComprobanteNoCalifica(laboral.isPresentaComprobanteIngreso())) {
            motivos.add("sin comprobante de ingresos no califica para un crédito.");
        }
        return motivos;
    }

    private String nombreDepartamento(Connection conexion, Long idDepartamento) throws SQLException {
        if (idDepartamento == null) {
            return null;
        }
        Departamento departamento = departamentoDAO.buscarPorId(conexion, idDepartamento);
        return departamento == null ? null : departamento.getNombre();
    }

    /**
     * Mejora de pantalla (Precalificación, pedido de Joel): evalúa SOLO los 4 campos de la caja
     * de Precalificación -- edad, zona, situación laboral y comprobante -- antes de que exista
     * una Persona armada (todavía no se cargaron ni nombre ni referencias). A diferencia de
     * validarPoliticaCreditoAlta, nunca lanza: devuelve null si califica, o el motivo si no. Edad/
     * zona/situación reutilizan evaluarPoliticaCredito (Grupo 1), compartido de verdad con
     * validarPoliticaCreditoAlta; comprobante reutiliza motivoSinComprobanteSiAplica -- las dos
     * SIEMPRE dicen lo mismo porque llaman al mismo código, no por tener el mismo texto copiado en
     * cada lugar (eso pasó una vez: esta y validarPoliticaCreditoAlta tenían cada una su propia
     * copia literal del texto de comprobante, se actualizó una y el indicador quedó mostrando el
     * mensaje viejo). La usa GET /clientes/precalificar, en el alta (donde además deshabilita el
     * resto del formulario) y en la edición (ahí solo informativo, nunca bloquea).
     */
    public String evaluarPrecalificacion(Connection conexion, LocalDate fechaNacimiento, Long idDepartamento,
            String situacionLaboral, boolean presentaComprobanteIngreso) throws SQLException {
        try {
            int edadMinima = obtenerEdadMinima(conexion);
            int edadMaxima = obtenerEdadMaxima(conexion);
            List<String> departamentosHabilitados = valoresSeparadosPorComa(
                    servicioParametros.obtenerValor(conexion, "credito.departamentos_habilitados"));
            List<String> situacionesAdmitidas = valoresSeparadosPorComa(
                    servicioParametros.obtenerValor(conexion, "credito.situaciones_laborales_admitidas"));
            String nombreDepartamento = nombreDepartamento(conexion, idDepartamento);
            int edad = calcularEdad(fechaNacimiento, Reloj.hoy());
            evaluarPoliticaCredito(edad, edadMinima, edadMaxima, nombreDepartamento, departamentosHabilitados,
                    situacionLaboral, situacionesAdmitidas);
            String motivoComprobante = motivoSinComprobanteSiAplica(presentaComprobanteIngreso);
            if (motivoComprobante != null) {
                throw new NegocioException(motivoComprobante);
            }
            return null;
        } catch (NegocioException motivo) {
            return motivo.getMessage();
        }
    }

    /** Alta de la ficha (PO-02 paso 1-3). */
    public long alta(Persona nuevaPersona, List<PersonaReferencia> referencias, PersonaConyuge conyuge,
            PersonaDomicilio domicilio, Long idDepartamentoDomicilio, PersonaLaboral laboral,
            long idUsuarioQueRegistra) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                nuevaPersona.setCedulaIdentidad(normalizarCedula(nuevaPersona.getCedulaIdentidad()));
                validarDatosPersona(nuevaPersona);
                validarReferencias(referencias);
                PersonaConyuge conyugeValidado = validarConyuge(nuevaPersona.getEstadoCivil(), conyuge);
                validarDomicilio(domicilio);
                validarCiudadPerteneceADepartamento(conexion, idDepartamentoDomicilio, domicilio.getIdCiudad());
                validarLaboral(laboral);
                validarPoliticaCreditoAlta(conexion, nuevaPersona, domicilio.getIdCiudad(), laboral);
                if (personaDAO.buscarPorCedula(conexion, nuevaPersona.getCedulaIdentidad()) != null) {
                    throw new NegocioException("Ya existe una persona registrada con la cédula \""
                            + FormateadorTexto.comoCedulaConPuntos(nuevaPersona.getCedulaIdentidad()) + "\".");
                }
                nuevaPersona.setRegistradoPor(idUsuarioQueRegistra);
                long idNuevo = personaDAO.insertar(conexion, nuevaPersona);
                personaReferenciaDAO.reemplazarTodas(conexion, idNuevo, referencias);
                personaConyugeDAO.guardar(conexion, idNuevo, conyugeValidado);
                personaDomicilioDAO.guardar(conexion, idNuevo, domicilio);
                personaLaboralDAO.guardar(conexion, idNuevo, laboral);
                servicioAuditoria.registrar(conexion, idUsuarioQueRegistra, "ALTA_CLIENTE", "PERSONA", idNuevo,
                        "Registró la ficha de \"" + nuevaPersona.getNombres() + " " + nuevaPersona.getApellidos()
                                + "\" (C.I. " + nuevaPersona.getCedulaIdentidad() + ").");
                conexion.commit();
                return idNuevo;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Edición de la ficha ya existente -- SIEMPRE guarda (pedido explícito de Joel: la política de
     * crédito nunca bloquea la edición de un cliente existente, solo el alta). Devuelve los
     * motivos por los que el cliente, tal como quedó, no calificaría hoy para una solicitud nueva
     * (vacío si califica) -- el controlador los usa para el aviso amarillo, nunca para impedir
     * nada: el guardado ya ocurrió.
     */
    public List<String> actualizar(Persona persona, List<PersonaReferencia> referencias, PersonaConyuge conyuge,
            PersonaDomicilio domicilio, Long idDepartamentoDomicilio, PersonaLaboral laboral,
            long idUsuarioQueModifica, boolean puedeAprobarRechazarSolicitud, boolean puedeAnularPrestamo)
            throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                persona.setCedulaIdentidad(normalizarCedula(persona.getCedulaIdentidad()));
                validarDatosPersona(persona);
                validarReferencias(referencias);
                PersonaConyuge conyugeValidado = validarConyuge(persona.getEstadoCivil(), conyuge);
                validarDomicilio(domicilio);
                validarCiudadPerteneceADepartamento(conexion, idDepartamentoDomicilio, domicilio.getIdCiudad());
                validarLaboral(laboral);
                Persona conMismaCedula = personaDAO.buscarPorCedula(conexion, persona.getCedulaIdentidad());
                if (conMismaCedula != null && !conMismaCedula.getIdPersona().equals(persona.getIdPersona())) {
                    throw new NegocioException("Ya existe otra persona registrada con la cédula \""
                            + FormateadorTexto.comoCedulaConPuntos(persona.getCedulaIdentidad()) + "\".");
                }
                // Bloque 7/E10 (D-129, Obs. 31): "qué campos" cambiaron, para la auditoría --
                // quién/cuándo ya los guarda auditoria_operaciones automáticamente.
                Persona anterior = personaDAO.buscarPorId(conexion, persona.getIdPersona());
                // Normalización de personas, grupo Domicilio (16/10/2026): el texto "antes" se lee
                // ANTES de guardar (todavía es el domicilio viejo); el "después" se lee DESPUÉS de
                // guardar, releyendo dentro de la misma transacción (siempre da exactamente lo
                // mismo que leería un PDF generado justo ahora).
                String domicilioAntes = obtenerTextoDomicilio(conexion, persona.getIdPersona());
                // Normalización de personas, grupo Laborales (16/10/2026): mismo criterio que
                // domicilioAntes -- se lee ANTES de guardar, para comparar contra la situación
                // laboral nueva (reemplazo del comprobante de ingreso) y para camposModificados.
                PersonaLaboral laboralAnterior = personaLaboralDAO.obtenerPorPersona(conexion, persona.getIdPersona());
                // 2c (pedido de Joel, 17/10/2026): la ciudad ANTERIOR, para saber si la zona
                // evaluada cambió -- mismo criterio que laboralAnterior/domicilioAntes.
                PersonaDomicilio domicilioAnterior = personaDomicilioDAO.obtenerPorPersona(conexion, persona.getIdPersona());
                // 2c (pedido de Joel, 17/10/2026): si hay una evaluación en curso (solicitud
                // PENDIENTE_APROBACION/APROBADA sin formalizar, o préstamo PENDIENTE_DESEMBOLSO)
                // y alguno de los 7 datos de evaluación cambió, se bloquea ANTES de escribir nada
                // -- el resto de la ficha (nombre, teléfono, domicilio salvo la ciudad, cónyuge,
                // referencias) sigue guardando siempre, sin excepción (D-129).
                bloquearSiCambianDatosDeEvaluacion(conexion, anterior, persona, laboralAnterior, laboral,
                        domicilioAnterior == null ? null : domicilioAnterior.getIdCiudad(), domicilio.getIdCiudad(),
                        puedeAprobarRechazarSolicitud, puedeAnularPrestamo);
                // RF03 (pedido de Joel), Parte 1 -- reprueba: cualquier cambio en la fecha de
                // vencimiento de la cédula (incluida una corrección hacia atrás) invalida el
                // escaneo VIGENTE actual -- lo marca REEMPLAZADO en la misma transacción, sin
                // subir ningún archivo nuevo (mismo método que ya usa ServicioDocumentos.
                // cargarDocumento al reemplazar). La fila 2 del Expediente vuelve a "Falta" hasta
                // que se cargue el escaneo renovado.
                if (!java.util.Objects.equals(anterior.getFechaVencimientoCedula(), persona.getFechaVencimientoCedula())) {
                    py.com.capitalcredito.sgcpc.modelos.TipoDocumento tipoCedula =
                            tipoDocumentoDAO.obtenerPorCodigo(conexion, "CEDULA_IDENTIDAD");
                    py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente cedulaVigente =
                            documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, persona.getIdPersona(),
                                    tipoCedula.getIdTipoDocumento());
                    if (cedulaVigente != null) {
                        documentoExpedienteDAO.marcarReemplazado(conexion, cedulaVigente.getIdDocumento());
                    }
                }
                // RF03 (pedido de Joel): mismo agujero que la cédula, ahora con la situación
                // laboral -- el comprobante de ingreso viejo (de la situación anterior) ya no
                // sirve de evidencia para la nueva (ej. liquidación de sueldo de Asalariado no
                // prueba nada de un Comerciante). Mismo patrón: queda REEMPLAZADO, sin subir
                // ningún archivo nuevo.
                if (!java.util.Objects.equals(laboralAnterior.getSituacionLaboral(), laboral.getSituacionLaboral())) {
                    py.com.capitalcredito.sgcpc.modelos.TipoDocumento tipoComprobanteIngreso =
                            tipoDocumentoDAO.obtenerPorCodigo(conexion, "COMPROBANTE_INGRESO");
                    py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente comprobanteVigente =
                            documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, persona.getIdPersona(),
                                    tipoComprobanteIngreso.getIdTipoDocumento());
                    if (comprobanteVigente != null) {
                        documentoExpedienteDAO.marcarReemplazado(conexion, comprobanteVigente.getIdDocumento());
                    }
                }
                personaDAO.actualizar(conexion, persona);
                personaReferenciaDAO.reemplazarTodas(conexion, persona.getIdPersona(), referencias);
                personaConyugeDAO.guardar(conexion, persona.getIdPersona(), conyugeValidado);
                personaDomicilioDAO.guardar(conexion, persona.getIdPersona(), domicilio);
                personaLaboralDAO.guardar(conexion, persona.getIdPersona(), laboral);
                String domicilioDespues = obtenerTextoDomicilio(conexion, persona.getIdPersona());
                String camposModificados = camposModificados(anterior, persona, domicilioAntes, domicilioDespues,
                        laboralAnterior, laboral);
                servicioAuditoria.registrar(conexion, idUsuarioQueModifica, "ACTUALIZAR_CLIENTE", "PERSONA",
                        persona.getIdPersona(), "Actualizó la ficha de \"" + persona.getNombres() + " "
                                + persona.getApellidos() + "\"."
                                + (camposModificados.isEmpty() ? "" : " Campos modificados: " + camposModificados + "."));
                List<String> motivosNoCalifica = listarMotivosNoCalifica(conexion, persona, domicilio.getIdCiudad(), laboral);
                conexion.commit();
                return motivosNoCalifica;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * App móvil, ETAPA 2 (13/11/2026): los clientes activos a los que les falta algún documento
     * obligatorio del expediente, el que más le falta primero. <b>Solo lectura: no escribe nada.</b>
     *
     * <p><b>No hay ninguna regla nueva acá.</b> Quién está completo y quién no lo sigue decidiendo
     * {@link #construirItemsExpediente} -- la misma llamada que arma la pestaña Documentos de la
     * ficha web-- y el contador sale de {@link #CODIGOS_EXPEDIENTE_OBLIGATORIOS}, la única lista.
     * Este método solo repite esa evaluación para muchos clientes y los ordena.</p>
     *
     * <p><b>Por qué carga todo de golpe</b>: la regla necesita, por cliente, su situación laboral,
     * sus documentos y si tiene consentimiento. Pidiéndolos de a uno serían cuatro consultas por
     * cliente (944 al 12/11/2026, con 236 clientes activos). Acá son <b>cinco en total</b>: los
     * clientes, los tipos de documento, y las tres lecturas en bloque. La evaluación en sí se hace
     * en memoria, con la función pura.</p>
     *
     * <p>El orden es: primero los que más documentos deben, y entre los que empatan se conserva el
     * orden del listado de clientes (apellidos y nombres), porque {@code List.sort} de Java es
     * <b>estable</b> y la lista llega ya ordenada así desde la base. Nunca se devuelve a alguien
     * con el expediente completo.</p>
     */
    public List<py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente> listarPendientesDeExpediente(
            Connection conexion) throws SQLException {
        py.com.capitalcredito.sgcpc.dto.FiltroClientes filtro =
                new py.com.capitalcredito.sgcpc.dto.FiltroClientes();
        filtro.setEstado("ACTIVA");
        // Sin paginar: hay que evaluarlos a todos para poder ordenar por "cuántos le faltan".
        // Recortar antes de ordenar daría una lista que parece la peor y no lo es.
        List<Persona> personas = listarConFiltros(conexion, filtro, null, null);
        List<Long> ids = new java.util.ArrayList<>();
        for (Persona persona : personas) {
            ids.add(persona.getIdPersona());
        }

        Map<Long, String> situacionPorPersona = mapaSituacionLaboralPorPersona(conexion, ids);
        Map<Long, List<py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente>> documentosPorPersona =
                documentoExpedienteDAO.listarPorPersonas(conexion, ids);
        java.util.Set<Long> conConsentimiento =
                consentimientoCrediticioDAO.idsPersonaConConsentimiento(conexion, ids);
        List<py.com.capitalcredito.sgcpc.modelos.TipoDocumento> tipos =
                tipoDocumentoDAO.listarActivosPorContexto(conexion, "PERSONA");

        List<py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente> pendientes =
                new java.util.ArrayList<>();
        for (Persona persona : personas) {
            long idPersona = persona.getIdPersona();
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> items = construirItemsExpediente(
                    situacionPorPersona.get(idPersona),
                    tipos,
                    documentosPorPersona.getOrDefault(idPersona, java.util.Collections.emptyList()),
                    persona.getFechaVencimientoCedula(),
                    conConsentimiento.contains(idPersona));
            py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente pendiente =
                    armarPendiente(persona, items);
            if (pendiente != null) {
                pendientes.add(pendiente);
            }
        }
        ordenarPorFaltantes(pendientes);
        return pendientes;
    }

    /**
     * Ordena "el que más le falta, primero". Pura y de visibilidad de paquete para poder probar
     * con JUnit, sin base, las dos cosas que promete.
     *
     * <p>{@code List.sort} de Java es <b>estable</b>: los que empatan en cuántos faltan conservan
     * el orden en que venían, que es el del listado de clientes (apellidos y nombres). Eso importa
     * -- sin estabilidad, la lista se reordenaría sola entre una consulta y la siguiente, y el
     * oficial perdería de vista dónde estaba.</p>
     */
    static void ordenarPorFaltantes(List<py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente> pendientes) {
        pendientes.sort((uno, otro) -> Integer.compare(otro.cuantosFaltan(), uno.cuantosFaltan()));
    }

    /**
     * La parte pura: de los ítems de un cliente, su fila de "pendientes" -- o null si tiene el
     * expediente completo. Visibilidad de paquete para poder probarla con JUnit sin base.
     *
     * <p>Cuenta y lista <b>solo los obligatorios</b>, los mismos que pesan en "Enviar a
     * aprobación": un documento complementario sin cargar no es un pendiente.</p>
     */
    py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente armarPendiente(Persona persona,
            List<py.com.capitalcredito.sgcpc.dto.ItemExpediente> items) {
        int cumplidos = 0;
        int obligatorios = 0;
        List<String> faltantes = new java.util.ArrayList<>();
        for (py.com.capitalcredito.sgcpc.dto.ItemExpediente item : items) {
            if (!item.isObligatorio()) {
                continue;
            }
            obligatorios++;
            if (item.isCumplido()) {
                cumplidos++;
            } else {
                faltantes.add(item.getEtiqueta());
            }
        }
        if (faltantes.isEmpty()) {
            return null;
        }
        return new py.com.capitalcredito.sgcpc.dto.ClientePendienteExpediente(persona, cumplidos,
                obligatorios, faltantes);
    }

    /**
     * App móvil (FASE 1, 12/11/2026, decisión de Joel -- opción B): el vencimiento de la cédula,
     * y SOLO eso.
     *
     * <p><b>Por qué no se reusa {@link #actualizar}</b>: ese método es la ficha ENTERA (exige
     * referencias, cónyuge, domicilio y datos laborales, revalida la cédula duplicada y bloquea
     * si hay una evaluación en curso). Llamarlo desde la aplicación móvil, que solo tiene la
     * fecha que el oficial leyó de la cédula que acaba de fotografiar, obligaría a inventar el
     * resto de la ficha. Esto hace las tres cosas que corresponden, en una sola transacción, y
     * ninguna más.</p>
     *
     * <p><b>Mantiene la regla de RF03 Parte 1</b>: cambiar la fecha de vencimiento invalida el
     * escaneo VIGENTE de la cédula (queda REEMPLAZADO), igual que en {@link #actualizar} y con el
     * mismo método del DAO. En el circuito de la visita eso es lo que se busca: el oficial cambia
     * la fecha y sube la foto nueva, que entra como la versión siguiente.</p>
     *
     * <p><b>Lo que a propósito NO hace</b>: no pasa por
     * {@code bloquearSiCambianDatosDeEvaluacion}. El vencimiento de la cédula no es uno de los
     * datos de evaluación ({@link #cambiaronDatosDeEvaluacion}: fecha de nacimiento, ciudad y los
     * nueve datos laborales), así que en la web tampoco lo bloquea una evaluación en curso. No es
     * una excepción que se tome acá: es la misma regla.</p>
     *
     * <p>Si la fecha es la que ya estaba, no escribe nada y no audita nada -- un "cambio" que no
     * cambia nada no es un evento (mismo criterio que B125 con el regenerado de documentos).</p>
     */
    public void actualizarVencimientoCedula(long idPersona, LocalDate fechaVencimiento, long idUsuarioQueModifica)
            throws SQLException {
        if (fechaVencimiento == null) {
            throw new NegocioException("Ingrese la fecha de vencimiento de la cédula.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Persona persona = personaDAO.buscarPorId(conexion, idPersona);
                if (persona == null) {
                    throw new NegocioException("No se encontró ese cliente.");
                }
                LocalDate anterior = persona.getFechaVencimientoCedula();
                if (java.util.Objects.equals(anterior, fechaVencimiento)) {
                    conexion.rollback();
                    return;
                }
                persona.setFechaVencimientoCedula(fechaVencimiento);
                personaDAO.actualizar(conexion, persona);
                // La MISMA regla de actualizar(): el escaneo vigente deja de valer en cuanto
                // cambia la fecha, sin importar si después se sube uno nuevo o no.
                py.com.capitalcredito.sgcpc.modelos.TipoDocumento tipoCedula =
                        tipoDocumentoDAO.obtenerPorCodigo(conexion, CODIGO_CEDULA_IDENTIDAD);
                py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente cedulaVigente =
                        documentoExpedienteDAO.obtenerVigentePorPersonaYTipo(conexion, idPersona,
                                tipoCedula.getIdTipoDocumento());
                if (cedulaVigente != null) {
                    documentoExpedienteDAO.marcarReemplazado(conexion, cedulaVigente.getIdDocumento());
                }
                // Misma operación que la edición de la ficha (ACTUALIZAR_CLIENTE): es una edición
                // del cliente, no un evento nuevo, así que no hace falta ninguna etiqueta nueva en
                // EtiquetasAuditoria y el informe de auditoría la muestra junto con las demás.
                // registrarCambioDeValor guarda además el valor anterior y el nuevo.
                servicioAuditoria.registrarCambioDeValor(conexion, idUsuarioQueModifica, "ACTUALIZAR_CLIENTE",
                        "PERSONA", idPersona, anterior == null ? null : anterior.toString(),
                        fechaVencimiento.toString(),
                        "Actualizó el vencimiento de la cédula de \"" + persona.getNombres() + " "
                                + persona.getApellidos() + "\" desde la aplicación móvil.");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 31): campos editables que cambiaron, para el detalle de
     * auditoría. Pura -- visibilidad de paquete (no private) para poder probarla directo desde
     * ServicioClientesTest sin pasar por actualizar() (que necesita Connection).
     */
    String camposModificados(Persona anterior, Persona nueva, String domicilioAnterior, String domicilioNuevo,
            PersonaLaboral laboralAnterior, PersonaLaboral laboralNuevo) {
        java.util.List<String> cambios = new java.util.ArrayList<>();
        agregarSiCambio(cambios, "cédula", anterior.getCedulaIdentidad(), nueva.getCedulaIdentidad());
        agregarSiCambio(cambios, "RUC", anterior.getRuc(), nueva.getRuc());
        agregarSiCambio(cambios, "nombres", anterior.getNombres(), nueva.getNombres());
        agregarSiCambio(cambios, "apellidos", anterior.getApellidos(), nueva.getApellidos());
        agregarSiCambio(cambios, "fecha de nacimiento", anterior.getFechaNacimiento(), nueva.getFechaNacimiento());
        // RF03 (pedido de Joel), Parte 1: queda en el detalle de auditoría -- antes ningún cambio
        // de esta fecha se mencionaba, aunque invalide el escaneo (ver actualizar()).
        agregarSiCambio(cambios, "vencimiento de cédula", anterior.getFechaVencimientoCedula(), nueva.getFechaVencimientoCedula());
        agregarSiCambio(cambios, "estado civil", anterior.getEstadoCivil(), nueva.getEstadoCivil());
        agregarSiCambio(cambios, "teléfono", anterior.getTelefonoWhatsapp(), nueva.getTelefonoWhatsapp());
        agregarSiCambio(cambios, "correo electrónico", anterior.getCorreoElectronico(), nueva.getCorreoElectronico());
        agregarSiCambio(cambios, "domicilio", domicilioAnterior, domicilioNuevo);
        // Normalización de personas, grupo Laborales (16/10/2026): ingreso/comprobante/situación
        // laboral/empleador/cuota de otras deudas ya no están en Persona -- se comparan sobre el
        // PersonaLaboral antes/después (mismo criterio que domicilioAnterior/domicilioNuevo).
        agregarSiCambio(cambios, "ingreso neto comprobado", laboralAnterior.getIngresoNetoComprobado(),
                laboralNuevo.getIngresoNetoComprobado());
        agregarSiCambio(cambios, "presenta comprobante de ingresos", laboralAnterior.isPresentaComprobanteIngreso(),
                laboralNuevo.isPresentaComprobanteIngreso());
        agregarSiCambio(cambios, "situación laboral", laboralAnterior.getSituacionLaboral(), laboralNuevo.getSituacionLaboral());
        agregarSiCambio(cambios, "empleador", laboralAnterior.getEmpleadorNombre(), laboralNuevo.getEmpleadorNombre());
        agregarSiCambio(cambios, "cuota de otras deudas", laboralAnterior.getCuotaOtrasDeudasMensual(),
                laboralNuevo.getCuotaOtrasDeudasMensual());
        return String.join(", ", cambios);
    }

    private void agregarSiCambio(java.util.List<String> cambios, String etiqueta, Object valorAnterior, Object valorNuevo) {
        if (!java.util.Objects.equals(valorAnterior, valorNuevo)) {
            cambios.add(etiqueta);
        }
    }

    /**
     * Bloque 7/E10 (D-135, pedido de Joel): listado único para el ABM de Clientes, el informe PDF
     * y el CSV -- misma consulta, mismos filtros. pagina/porPagina null = todas las filas (para
     * el informe/CSV, que no paginan).
     */
    public List<Persona> listarConFiltros(Connection conexion, py.com.capitalcredito.sgcpc.dto.FiltroClientes filtro,
            Integer pagina, Integer porPagina) throws SQLException {
        prepararFiltro(filtro);
        return personaDAO.listarConFiltros(conexion, filtro, pagina, porPagina);
    }

    public int contarConFiltros(Connection conexion, py.com.capitalcredito.sgcpc.dto.FiltroClientes filtro)
            throws SQLException {
        prepararFiltro(filtro);
        return personaDAO.contarConFiltros(conexion, filtro);
    }

    private void prepararFiltro(py.com.capitalcredito.sgcpc.dto.FiltroClientes filtro) {
        if (filtro.getTexto() != null && !filtro.getTexto().trim().isEmpty()) {
            filtro.setCedulaNormalizada(normalizarCedula(filtro.getTexto()));
        }
    }

    public int contarPrestamosActivosDelCliente(Connection conexion, long idPersona) throws SQLException {
        return prestamoDAO.contarActivosOEnMoraPorCliente(conexion, idPersona);
    }

    /**
     * Bloque 7/E10 (D-129, Obs. 31): baja lógica -- nunca borra nada (REGLAS_DESARROLLO.md §5). Solo el
     * gerente (mismo patrón que ServicioCaja.registrarEntregaGerente: chequeo de perfil en el
     * Servicio, no solo en la ruta). Bloqueada si el cliente tiene préstamos ACTIVO/EN_MORA.
     */
    public void darDeBaja(long idPersona, String motivo, String detalle, PerfilUsuario perfilSolicitante,
            long idUsuarioGerente) throws SQLException {
        // D7 (pedido de Joel): el administrador también puede dar de baja a un cliente.
        if (perfilSolicitante != PerfilUsuario.GERENTE_ADMINISTRADOR && perfilSolicitante != PerfilUsuario.ADMINISTRADOR) {
            throw new NegocioException("Solo el gerente puede dar de baja a un cliente.");
        }
        if (motivo == null || !MOTIVOS_BAJA.contains(motivo)) {
            throw new NegocioException("Seleccione el motivo de la baja.");
        }
        detalle = NormalizadorTexto.normalizarTextoLibre(detalle);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Persona persona = personaDAO.buscarPorId(conexion, idPersona);
                if (persona == null) {
                    throw new NegocioException("El cliente no existe.");
                }
                int prestamosActivos = prestamoDAO.contarActivosOEnMoraPorCliente(conexion, idPersona);
                if (prestamosActivos > 0) {
                    throw new NegocioException("No se puede dar de baja: el cliente tiene " + prestamosActivos
                            + " préstamo(s) activo(s) o en mora. Regularice o finalice esos préstamos primero.");
                }
                // RF06 (pedido de Joel, 10/10/2026): tampoco si tiene una solicitud abierta
                // (borrador, pendiente, o aprobada sin formalizar) -- red de seguridad del
                // servidor; el enlace a la solicitud lo arma el controlador (mismo criterio que
                // RF05 con "Ver solicitud...", ver ControladorClientesServlet.procesarBajaOReactivar).
                py.com.capitalcredito.sgcpc.modelos.SolicitudCredito solicitudAbierta =
                        solicitudDAO.buscarAbiertaPorCliente(conexion, idPersona);
                if (solicitudAbierta != null) {
                    throw new NegocioException("No se puede dar de baja: el cliente tiene una solicitud abierta. "
                            + "Cancélela primero: Ver solicitud " + solicitudAbierta.getCodigoSolicitud() + ".");
                }
                personaDAO.marcarBaja(conexion, idPersona);
                personaBajaDAO.marcarBaja(conexion, idPersona, motivo, detalle, idUsuarioGerente);
                servicioAuditoria.registrar(conexion, idUsuarioGerente, "DAR_DE_BAJA_CLIENTE", "PERSONA", idPersona,
                        "Dio de baja a \"" + persona.getNombres() + " " + persona.getApellidos() + "\". Motivo: "
                                + motivo + (detalle == null || detalle.isEmpty() ? "" : " - " + detalle) + ".");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Bloque 7/E10 (D-129, Obs. 31): reactivar -- solo el gerente. */
    public void reactivar(long idPersona, PerfilUsuario perfilSolicitante, long idUsuarioGerente) throws SQLException {
        // D7 (pedido de Joel): el administrador también puede reactivar a un cliente.
        if (perfilSolicitante != PerfilUsuario.GERENTE_ADMINISTRADOR && perfilSolicitante != PerfilUsuario.ADMINISTRADOR) {
            throw new NegocioException("Solo el gerente puede reactivar a un cliente.");
        }
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                Persona persona = personaDAO.buscarPorId(conexion, idPersona);
                if (persona == null) {
                    throw new NegocioException("El cliente no existe.");
                }
                personaDAO.marcarReactivada(conexion, idPersona);
                servicioAuditoria.registrar(conexion, idUsuarioGerente, "REACTIVAR_CLIENTE", "PERSONA", idPersona,
                        "Reactivó a \"" + persona.getNombres() + " " + persona.getApellidos() + "\".");
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }
}
