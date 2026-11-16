package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.logging.Logger;
import py.com.capitalcredito.sgcpc.dao.FeriadoDAO;
import py.com.capitalcredito.sgcpc.dao.LimiteTasaUsuraDAO;
import py.com.capitalcredito.sgcpc.dao.ParametroDAO;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoValorParametro;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.NormalizadorTexto;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.Feriado;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;
import py.com.capitalcredito.sgcpc.modelos.ParametroSistema;

/**
 * P12, parte parametros/feriados/limites de tasa (Doc. 00 §12 / Doc. 10
 * §10.15). Las validaciones del punto 6 son puras (sin DAO); las lee el
 * metodo que persiste (con ParametroDAO/LimiteTasaUsuraDAO), antes del
 * INSERT de la nueva vigencia.
 */
public class ServicioParametros {

    private static final BigDecimal CERO = BigDecimal.ZERO;
    private static final BigDecimal UNO = BigDecimal.ONE;
    private static final BigDecimal TOPE_PUNITORIO = new BigDecimal("0.30");
    /** RF39/D1 (pedido de Joel, 13/10/2026): mismo criterio que MOTIVO_DIFERENCIA_LARGO_MINIMO
     * de ServicioCaja, para el motivo obligatorio al cambiar un parámetro. */
    static final int MOTIVO_PARAMETRO_LARGO_MINIMO = 10;
    private static final Logger LOGGER = Logger.getLogger(ServicioParametros.class.getName());

    /** Los únicos parámetros ENTERO que son montos en guaraníes (el resto de los ENTERO son días,
     * cuotas, intentos, etc.) -- mismo criterio que ya usaba administracion.jsp (duplicado acá,
     * C-4.3 pedido de Joel, para no tocar esa pantalla que ya funciona: ver
     * formatearValorParametro). */
    // RF39/E3 (pedido de Joel, 13/10/2026): caja.monto_maximo_arqueo agregada acá también, para
    // que el historial de cambios (pantalla, PDF y CSV) muestre "Gs. 500.000.000" en vez de
    // "500000000" -- mismo arreglo que ya se hizo en administracion.jsp (D3).
    // Tanda 2 (14/10/2026, pedido de Joel): credito.redondeo_efectivo agregada -- mismo hueco,
    // se mostraba "50" en vez de "50 Gs.".
    private static final java.util.Set<String> CLAVES_GUARANIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "credito.monto_minimo", "credito.monto_maximo", "caja.diferencia_tolerada", "caja.monto_maximo_arqueo",
            "credito.redondeo_efectivo"));

    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final FeriadoDAO feriadoDAO = new FeriadoDAO();
    private final LimiteTasaUsuraDAO limiteTasaUsuraDAO = new LimiteTasaUsuraDAO();
    private final ServicioAuditoria servicioAuditoria = new ServicioAuditoria();

    /** C-4.3 (pedido de Joel): mismo formato que ya usa administracion.jsp para mostrar el valor
     * de un parámetro según su tipo (porcentaje/guaraníes/fecha/texto) -- el historial de cambios
     * necesita formatear el mismo valor dos veces por fila (anterior y nuevo), así que esta regla
     * quedó acá en vez de seguir solo en el scriptlet de esa JSP (que no se tocó, sigue igual).
     * Pura. "—" si vino null (no hay valor anterior, la primera vigencia de ese parámetro). */
    public static String formatearValorParametro(String claveParametro, TipoValorParametro tipoValor,
            String valorCrudo) {
        if (valorCrudo == null) {
            return "—";
        }
        try {
            if (tipoValor == TipoValorParametro.DECIMAL) {
                return FormateadorNumeros.comoPorcentaje(new BigDecimal(valorCrudo));
            }
            if (CLAVES_GUARANIES.contains(claveParametro)) {
                return FormateadorNumeros.comoGuaranies(new BigDecimal(valorCrudo));
            }
            if (tipoValor == TipoValorParametro.FECHA) {
                return FormateadorFechas.comoDdMmAaaa(LocalDate.parse(valorCrudo));
            }
            return valorCrudo;
        } catch (RuntimeException errorFormato) {
            return valorCrudo + " (formato inválido)";
        }
    }

    /** Valor vigente de un parámetro (texto tal cual está guardado). */
    public String obtenerValor(Connection conexion, String clave) throws SQLException {
        ParametroSistema parametro = parametroDAO.obtenerVigentePorClave(conexion, clave);
        if (parametro == null) {
            throw new NegocioException("No se encontró el parámetro \"" + clave + "\".");
        }
        return parametro.getValorParametro();
    }

    /**
     * D-94 (E8): valor vigente en una fecha PASADA (nunca la de hoy) -- para que los 5
     * comprobantes reimpriman los datos de empresa/timbrado/representante legal tal como estaban
     * el día de la operación, aunque hoy ya hayan cambiado. Si no hay ningún parámetro vigente en
     * esa fecha (nunca debería pasar con datos reales, pero un parámetro cargado después de esa
     * fecha sí podría faltar), se rechaza igual que si el parámetro no existiera.
     */
    public String obtenerValorAFecha(Connection conexion, String clave, LocalDate fecha) throws SQLException {
        ParametroSistema parametro = parametroDAO.obtenerVigenteAFecha(conexion, clave, fecha);
        if (parametro == null) {
            throw new NegocioException("No se encontró el parámetro \"" + clave + "\" vigente el "
                    + fecha + ".");
        }
        return parametro.getValorParametro();
    }

    /**
     * Punto 16b (pedido de Joel): como obtenerValorAFecha, pero para datos que NO se graban en la
     * propia entidad (empresa.* -- a diferencia del timbrado, que la factura sí guarda) -- si no
     * hay ninguna versión vigente en esa fecha, usa la MÁS ANTIGUA disponible en vez de rechazar
     * la reimpresión de un documento viejo, y deja una línea de log (WARN, nunca auditoría --
     * decisión de Joel) para que quede registro de cuándo pasó esto.
     */
    public String obtenerValorAFechaOMasAntiguo(Connection conexion, String clave, LocalDate fecha) throws SQLException {
        ParametroSistema parametro = parametroDAO.obtenerVigenteAFecha(conexion, clave, fecha);
        if (parametro != null) {
            return parametro.getValorParametro();
        }
        ParametroSistema masAntiguo = parametroDAO.obtenerMasAntiguo(conexion, clave);
        if (masAntiguo == null) {
            throw new NegocioException("No se encontró el parámetro \"" + clave + "\".");
        }
        LOGGER.warning("Parámetro \"" + clave + "\" sin versión vigente el " + fecha
                + " -- se usó la versión más antigua disponible (vigente desde " + masAntiguo.getVigenteDesde() + ").");
        return masAntiguo.getValorParametro();
    }

    public int obtenerValorEntero(Connection conexion, String clave) throws SQLException {
        return parsearEntero(clave, obtenerValor(conexion, clave));
    }

    public BigDecimal obtenerValorDecimal(Connection conexion, String clave) throws SQLException {
        return parsearDecimal(clave, obtenerValor(conexion, clave));
    }

    /** Parámetros de tipo FECHA, siempre guardados en ISO yyyy-MM-dd (ej. fiscal.timbrado_vigencia_desde/hasta). */
    public LocalDate obtenerValorFecha(Connection conexion, String clave) throws SQLException {
        return parsearFecha(clave, obtenerValor(conexion, clave));
    }

    /**
     * D-84: si el valor guardado no es un entero válido -- nunca debería pasar, ya que
     * actualizarValor lo valida al guardar, pero un valor mal cargado por cualquier otra vía no
     * puede terminar en un 500 (incidente real, D-84: un parámetro de fecha mal cargado rompió
     * un cobro con un error genérico en vez de un mensaje entendible) -- se rechaza con
     * NegocioException, igual que si el parámetro no existiera. Pura y pública para poder
     * testearla sin base de datos.
     */
    public static int parsearEntero(String clave, String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException error) {
            throw new NegocioException("El parámetro \"" + clave + "\" tiene un valor inválido (\"" + valor
                    + "\"). Corríjalo en Parámetros.");
        }
    }

    /** D-84: mismo criterio que parsearEntero, para DECIMAL. */
    public static BigDecimal parsearDecimal(String clave, String valor) {
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException error) {
            throw new NegocioException("El parámetro \"" + clave + "\" tiene un valor inválido (\"" + valor
                    + "\"). Corríjalo en Parámetros.");
        }
    }

    /** D-84: mismo criterio que parsearEntero, para FECHA. */
    public static LocalDate parsearFecha(String clave, String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException error) {
            throw new NegocioException("El parámetro \"" + clave + "\" tiene un valor inválido (\"" + valor
                    + "\"). Corríjalo en Parámetros.");
        }
    }

    public List<ParametroSistema> listarVigentes(Connection conexion) throws SQLException {
        return parametroDAO.listarVigentes(conexion);
    }

    /** Punto 2: se lee en cada login para fijar el timeout de esa sesión. */
    public int obtenerMinutosInactividad() throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return obtenerValorEntero(conexion, "seguridad.minutos_inactividad");
        }
    }

    /** Para la marca de la plantilla (reemplaza el logo de AdminLTE, ver plan de E2). */
    public String obtenerNombreComercial() throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            return obtenerValor(conexion, "empresa.nombre_comercial");
        }
    }

    /** RF39/E1 (decisión de Joel, 13/10/2026): una sola fuente para el tope legal de TEA --
     * limites_tasa_usura. Duplicaciones y parches (Commit 2, 19/10/2026): ServicioCotizacion/
     * ServicioAprobacionCredito/ServicioFormalizacion/ServicioSolicitudes (las 4 revalidaciones
     * de RN-15) llaman a este método en vez de repetir el mismo mensaje cada uno. Pura (recibe
     * el límite ya leído, no la Connection) para poder testearla sin base de datos.
     * Tanda de usabilidad (21/10/2026, pedido de Joel, punto 7 paso F1): "Consulte al gerente" ->
     * "Consulte al administrador" -- ADMINISTRAR_PARAMETROS es CONSULTA (no TOTAL) para
     * GERENTE_ADMINISTRADOR, el gerente tampoco puede cargar un límite nuevo desde /parametros;
     * solo ADMINISTRADOR puede. */
    public BigDecimal resolverTopeLegalVigente(LimiteTasaUsura limiteVigente) {
        if (limiteVigente == null) {
            throw new NegocioException("No hay un límite de tasa vigente cargado. Consulte al administrador.");
        }
        return limiteVigente.getTasaMaximaTea();
    }

    public void validarTeaEmpresa(BigDecimal teaEmpresa, BigDecimal topeVigente) {
        // RF39/D2 (pedido de Joel, 13/10/2026): antes solo se comparaba contra el tope superior --
        // una TEA de 0 % quedaba aceptada (hallazgo del análisis de RF39).
        if (teaEmpresa.compareTo(CERO) <= 0) {
            throw new NegocioException("La TEA de la empresa debe ser mayor que 0 %.");
        }
        if (teaEmpresa.compareTo(topeVigente) > 0) {
            throw new NegocioException("La TEA de la empresa no puede superar el tope legal vigente ("
                    + FormateadorNumeros.comoPorcentaje(topeVigente) + ").");
        }
    }

    public void validarFactorMoratorio(BigDecimal factorMoratorio) {
        if (factorMoratorio.compareTo(CERO) <= 0 || factorMoratorio.compareTo(UNO) > 0) {
            throw new NegocioException("El factor moratorio debe ser mayor que 0 % y menor o igual a 100 %.");
        }
    }

    public void validarPorcentajePunitorio(BigDecimal porcentajePunitorio) {
        if (porcentajePunitorio.compareTo(CERO) <= 0 || porcentajePunitorio.compareTo(TOPE_PUNITORIO) > 0) {
            throw new NegocioException("El porcentaje punitorio debe ser mayor que 0 % y menor o igual a 30 %.");
        }
    }

    public void validarLimiteAfectacion(BigDecimal limiteAfectacion) {
        if (limiteAfectacion.compareTo(CERO) <= 0 || limiteAfectacion.compareTo(UNO) > 0) {
            throw new NegocioException("El límite de afectación debe ser mayor que 0 % y menor o igual a 100 %.");
        }
    }

    public void validarMontos(BigDecimal montoMinimo, BigDecimal montoMaximo) {
        // RF39/D2 (pedido de Joel, 13/10/2026): antes solo se comparaban entre si -- un monto
        // negativo quedaba aceptado (hallazgo del análisis de RF39; ConversorNumeros.
        // aBigDecimalGuaranies permite el signo "-", y no habia ningun tope por este lado).
        if (montoMinimo.compareTo(CERO) <= 0) {
            throw new NegocioException("El monto mínimo debe ser mayor que 0.");
        }
        if (montoMaximo.compareTo(CERO) <= 0) {
            throw new NegocioException("El monto máximo debe ser mayor que 0.");
        }
        if (montoMinimo.compareTo(montoMaximo) >= 0) {
            throw new NegocioException("El monto mínimo debe ser menor que el monto máximo.");
        }
    }

    public void validarPlazos(int plazoMinimo, int plazoMaximo) {
        if (plazoMinimo >= plazoMaximo) {
            throw new NegocioException("El plazo mínimo debe ser menor que el plazo máximo.");
        }
    }

    /** RF39/D2 (pedido de Joel, 13/10/2026): credito.edad_minima/edad_maxima no tenian ninguna
     * validacion cruzada al cambiarlos desde /parametros -- mismo criterio que validarPlazos. */
    public void validarEdades(int edadMinima, int edadMaxima) {
        if (edadMinima >= edadMaxima) {
            throw new NegocioException("La edad mínima debe ser menor que la edad máxima.");
        }
    }

    /** RF39/D1 (pedido de Joel, 13/10/2026): motivo obligatorio al cambiar un parametro -- se
     * guarda en "observacion" y se muestra en /parametros/historial. Pura, mismo criterio que
     * ServicioCaja.validarMotivoDiferencia. */
    public void validarMotivoParametro(String motivo) {
        if (motivo == null || motivo.length() < MOTIVO_PARAMETRO_LARGO_MINIMO) {
            throw new NegocioException("El motivo del cambio necesita al menos " + MOTIVO_PARAMETRO_LARGO_MINIMO
                    + " caracteres (tiene " + (motivo == null ? 0 : motivo.length()) + ").");
        }
    }

    /** E5 (D-68): mismo criterio que validarPlazos, para el rango de días del primer vencimiento al formalizar. */
    public void validarDiasPrimerVencimiento(int diasMinimo, int diasMaximo) {
        if (diasMinimo >= diasMaximo) {
            throw new NegocioException("Los días mínimos deben ser menos que los días máximos.");
        }
    }

    /** D-38 (E3): días de vigencia de la autorización de consulta al buró (RN-24). */
    public void validarDiasVigenciaConsentimiento(int dias) {
        if (dias < 1 || dias > 365) {
            throw new NegocioException("Los días de vigencia de la autorización deben estar entre 1 y 365.");
        }
    }

    /** D-84: la vigencia del timbrado no puede terminar antes de empezar. */
    public void validarVigenciaTimbrado(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new NegocioException("La vigencia hasta no puede ser anterior a la vigencia desde.");
        }
    }

    /** D-89: el rango de numeración autorizado del timbrado no puede terminar antes de empezar. */
    public void validarRangoNumeroFactura(int numeroDesde, int numeroHasta) {
        if (numeroHasta < numeroDesde) {
            throw new NegocioException("La numeración de factura \"hasta\" no puede ser menor que \"desde\".");
        }
    }

    /** Las vigencias de limites_tasa_usura no pueden superponerse. */
    public void validarSuperposicionLimitesTasa(LocalDate nuevaDesde, LocalDate nuevaHasta,
            List<LimiteTasaUsura> existentes) {
        for (LimiteTasaUsura existente : existentes) {
            // B223 opcion C: un limite con la vigencia VACIA (hasta anterior a desde) nunca rigio,
            // asi que no se superpone con nada. Sin esto, un limite programado para el futuro y
            // reemplazado hoy seguiria bloqueando el alta del corregido.
            if (existente.getVigenteHasta() != null
                    && existente.getVigenteHasta().isBefore(existente.getVigenteDesde())) {
                continue;
            }
            boolean empiezaAntesDelFinExistente = existente.getVigenteHasta() == null
                    || !nuevaDesde.isAfter(existente.getVigenteHasta());
            boolean terminaDespuesDelInicioExistente = nuevaHasta == null
                    || !nuevaHasta.isBefore(existente.getVigenteDesde());
            if (empiezaAntesDelFinExistente && terminaDespuesDelInicioExistente) {
                throw new NegocioException("La vigencia se superpone con un límite de tasa ya cargado.");
            }
        }
    }

    /**
     * Cambia el valor vigente de un parámetro: interpreta lo que escribió
     * el gerente según el tipo del parámetro (punto 1/2: acepta coma o
     * punto decimal; los DECIMAL se escriben como porcentaje y se guardan
     * como fracción), valida (punto 6), cierra la vigencia anterior,
     * inserta la nueva y audita con el valor anterior/nuevo (RF39 -- historial de cambios,
     * no es un RNF). Todo
     * en una transacción.
     *
     * RF39/D1 (pedido de Joel, 13/10/2026): "motivo" es ahora obligatorio (al menos
     * MOTIVO_PARAMETRO_LARGO_MINIMO caracteres) y se guarda en "observacion" -- antes
     * actualizarValor copiaba la observación de la fila anterior (siempre vacía desde la
     * siembra), por eso esa columna del historial nunca mostraba nada.
     */
    public void actualizarValor(String clave, String valorIngresado, String motivo, long idUsuarioQueModifica)
            throws SQLException {
        String motivoNormalizado = NormalizadorTexto.normalizarTextoLibre(motivo);
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                validarMotivoParametro(motivoNormalizado);
                ParametroSistema actual = parametroDAO.obtenerVigentePorClave(conexion, clave);
                if (actual == null) {
                    throw new NegocioException("No se encontró el parámetro \"" + clave + "\".");
                }
                String valorNuevo = normalizarValorNuevo(conexion, actual, valorIngresado);

                LocalDate hoy = Reloj.hoy();
                parametroDAO.cerrarVigencia(conexion, actual.getIdParametro(), hoy);

                ParametroSistema nuevo = new ParametroSistema();
                nuevo.setClaveParametro(actual.getClaveParametro());
                nuevo.setNombreParametro(actual.getNombreParametro());
                nuevo.setValorParametro(valorNuevo);
                nuevo.setTipoValor(actual.getTipoValor());
                nuevo.setVigenteDesde(hoy);
                nuevo.setModificadoPor(idUsuarioQueModifica);
                nuevo.setObservacion(motivoNormalizado);
                long idNuevo = parametroDAO.insertar(conexion, nuevo);

                servicioAuditoria.registrarCambioDeValor(conexion, idUsuarioQueModifica, "CAMBIAR_PARAMETRO",
                        "PARAMETRO_SISTEMA", idNuevo, actual.getValorParametro(), valorNuevo,
                        construirDetalleCambioParametro(actual.getNombreParametro(), actual.getTipoValor(), clave,
                                actual.getValorParametro(), valorNuevo, motivoNormalizado));

                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    /** Punto 1a/2: interpreta lo escrito segun el tipo del parametro y devuelve el texto a guardar. */
    private String normalizarValorNuevo(Connection conexion, ParametroSistema actual, String valorIngresado)
            throws SQLException {
        String clave = actual.getClaveParametro();
        if (actual.getTipoValor() == TipoValorParametro.DECIMAL) {
            // Los DECIMAL de este sistema son siempre tasas/porcentajes (D-12): se escriben
            // como porcentaje (27,39) y se guardan como fraccion (0.273900).
            BigDecimal porcentaje = ConversorNumeros.aBigDecimal(valorIngresado);
            BigDecimal fraccion = FormateadorNumeros.fraccionDesdePorcentaje(porcentaje, 6);
            validarFraccionSegunClave(conexion, clave, fraccion);
            return fraccion.toPlainString();
        }
        if (actual.getTipoValor() == TipoValorParametro.ENTERO) {
            // Formato de importes (D-44): los unicos ENTERO que son montos en guaranies se
            // escriben con puntos de miles ("500.000"); el resto (dias, cuotas, intentos, etc.)
            // sigue como numero simple.
            int valor = CLAVES_ENTERO_GUARANIES.contains(clave)
                    ? ConversorNumeros.aBigDecimalGuaranies(valorIngresado).intValueExact()
                    : ConversorNumeros.aEntero(valorIngresado);
            validarEnteroSegunClave(conexion, clave, valor);
            return String.valueOf(valor);
        }
        if (actual.getTipoValor() == TipoValorParametro.FECHA) {
            // D-84: la pantalla usa <input type="date"> (siempre manda ISO), pero el servidor
            // acepta además DD/MM/AAAA por si alguien lo escribe a mano -- y siempre guarda ISO.
            String fechaIso = normalizarFecha(valorIngresado);
            validarFechaSegunClave(conexion, clave, fechaIso);
            return fechaIso;
        }
        return valorIngresado.trim();
    }

    private static final DateTimeFormatter FORMATO_FECHA_DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * D-84: ISO (AAAA-MM-DD) o DD/MM/AAAA -- cualquier otra cosa (incluida una fecha inexistente
     * como 31/02/2026) se rechaza con un mensaje entendible, nunca con una excepción sin atrapar.
     * Pública para poder testearla sin base de datos.
     */
    public static String normalizarFecha(String valorIngresado) {
        String texto = valorIngresado == null ? "" : valorIngresado.trim();
        try {
            return LocalDate.parse(texto).toString();
        } catch (DateTimeParseException errorIso) {
            try {
                return LocalDate.parse(texto, FORMATO_FECHA_DIA_MES_ANIO).toString();
            } catch (DateTimeParseException errorDiaMesAnio) {
                throw new NegocioException("Fecha inválida. Use el calendario o el formato DD/MM/AAAA.");
            }
        }
    }

    private void validarFechaSegunClave(Connection conexion, String clave, String fechaIso) throws SQLException {
        LocalDate fecha = LocalDate.parse(fechaIso);
        if ("fiscal.timbrado_vigencia_desde".equals(clave)) {
            LocalDate hasta = obtenerValorFechaSiValida(conexion, "fiscal.timbrado_vigencia_hasta");
            if (hasta != null) {
                validarVigenciaTimbrado(fecha, hasta);
            }
        } else if ("fiscal.timbrado_vigencia_hasta".equals(clave)) {
            LocalDate desde = obtenerValorFechaSiValida(conexion, "fiscal.timbrado_vigencia_desde");
            if (desde != null) {
                validarVigenciaTimbrado(desde, fecha);
            }
        }
    }

    /**
     * D-84: variante de obtenerValorFecha que no interrumpe la validación cruzada si el OTRO
     * valor todavía está mal cargado (ej. al corregir "desde" mientras "hasta" sigue con el
     * valor viejo en DD/MM/AAAA) -- se salta la validación cruzada en vez de bloquear la
     * corrección del primero.
     */
    private LocalDate obtenerValorFechaSiValida(Connection conexion, String clave) throws SQLException {
        try {
            return obtenerValorFecha(conexion, clave);
        } catch (NegocioException error) {
            return null;
        }
    }

    // RF39/D3+E3 (pedido de Joel, 13/10/2026): caja.monto_maximo_arqueo agregada acá también --
    // sin esto, el "Cambiar" de administracion.jsp (que ahora pinta este campo con la clase
    // "campo-guaranies", puntos de miles incluidos en el valor que viaja al servidor) rompía al
    // guardar un valor nuevo: ConversorNumeros.aEntero("500.000.000") rechaza los puntos.
    // B218 (15/11/2026, el mismo hueco una segunda vez): faltaba credito.redondeo_efectivo, que
    // la pantalla sí cuenta entre los cinco parámetros en guaraníes y sí pinta con
    // "campo-guaranies" -- así que no se podía subir a 1.000 o más (el POST llegaba "1.000" y
    // aEntero lo rechazaba). Pasó inadvertido porque su valor vigente es 50, de dos cifras.
    // ParametrosEnGuaraniesTest cruza esta lista contra la de la JSP para que no vuelvan a
    // divergir.
    private static final java.util.Set<String> CLAVES_ENTERO_GUARANIES = new java.util.HashSet<>(
            java.util.Arrays.asList("credito.monto_minimo", "credito.monto_maximo", "caja.diferencia_tolerada",
                    "caja.monto_maximo_arqueo", "credito.redondeo_efectivo"));

    /** B218: las claves que se escriben con puntos de miles, para que el JUnit las cruce contra la
     * lista de {@code parametros/administracion.jsp}. Las dos tienen que decir lo mismo: la
     * pantalla decide con cuáles pinta el campo y el servicio decide cómo los interpreta. */
    public static java.util.Set<String> clavesEnteroEnGuaranies() {
        return java.util.Collections.unmodifiableSet(CLAVES_ENTERO_GUARANIES);
    }

    /**
     * B220 (pedido de Joel, 15/11/2026): el nombre legible del grupo al que pertenece un
     * parámetro, para que /parametros no sea una tabla plana de 52 filas. El grupo es el
     * <b>prefijo de la clave</b> —{@code credito.monto_minimo} es del grupo {@code credito}—, que
     * ya existe y ya ordena la consulta ({@code ORDER BY clave_parametro}): la pantalla no inventa
     * una agrupación, solo nombra y dibuja la que la clave tiene desde el principio.
     *
     * <p>Vive acá, y no en la JSP, por el mismo motivo que {@link #reglaDeClave(String)}: el
     * significado de una clave de parámetro es del servicio. {@code ParametrosGrupoTest} lee los
     * prefijos de {@code bd/03_parametros.sql} y comprueba que ninguno quede sin nombre.</p>
     *
     * <p>Si apareciera un prefijo sin etiqueta, devuelve el prefijo tal cual: una pantalla con un
     * título técnico se entiende; una que explota o que esconde el parámetro, no.</p>
     */
    public static String grupoDeClave(String clave) {
        if (clave == null || clave.indexOf('.') < 0) {
            return "";
        }
        String prefijo = clave.substring(0, clave.indexOf('.'));
        String etiqueta = ETIQUETAS_DE_GRUPO.get(prefijo);
        return etiqueta == null ? prefijo : etiqueta;
    }

    /** B220-bis: los grupos con su nombre, EN EL ORDEN en que la pantalla dibuja las pildoras
     * (prefijo -> etiqueta). La JSP recorre esto y no mantiene ninguna lista propia. */
    public static java.util.Map<String, String> gruposConEtiqueta() {
        return ETIQUETAS_DE_GRUPO;
    }

    /** B220-bis: el prefijo crudo de una clave ("credito.monto_minimo" -> "credito"), que es la
     * marca con la que la pantalla filtra por grupo. Cadena vacia si la clave no tiene punto. */
    public static String prefijoDeClave(String clave) {
        if (clave == null || clave.indexOf('.') < 0) {
            return "";
        }
        return clave.substring(0, clave.indexOf('.'));
    }

    /** B220: los prefijos con nombre, para que su JUnit los cruce contra bd/03_parametros.sql. */
    public static java.util.Set<String> prefijosConEtiqueta() {
        return ETIQUETAS_DE_GRUPO.keySet();
    }

    private static final java.util.Map<String, String> ETIQUETAS_DE_GRUPO = crearEtiquetasDeGrupo();

    private static java.util.Map<String, String> crearEtiquetasDeGrupo() {
        // El ORDEN de este mapa es el orden en que /parametros dibuja las pildoras de grupo
        // (B220-bis, pedido de Joel al probarlo): se decide aca, no en la JSP, para que la
        // pantalla no tenga una segunda lista de grupos que mantener.
        java.util.Map<String, String> etiquetas = new java.util.LinkedHashMap<>();
        etiquetas.put("credito", "Política de crédito");
        etiquetas.put("caja", "Caja");
        etiquetas.put("fiscal", "Facturación y timbrado");
        etiquetas.put("empresa", "Datos de la empresa");
        etiquetas.put("formalizacion", "Formalización");
        etiquetas.put("seguridad", "Seguridad y acceso");
        etiquetas.put("buro", "Buró de crédito");
        etiquetas.put("archivos", "Archivos");
        return java.util.Collections.unmodifiableMap(etiquetas);
    }

    /**
     * B215 (pedido de Joel, 15/11/2026): la frase que /parametros muestra debajo del campo
     * "Cambiar", para que el rango permitido no se descubra recien cuando el servidor rechaza el
     * valor. Vive aca, y no en la JSP, porque aca viven las reglas: cada frase describe
     * exactamente lo que validan validarFraccionSegunClave, validarEnteroSegunClave y
     * validarFechaSegunClave para esa clave -- si la frase estuviera en la pantalla, el dia que
     * cambie una validacion la pantalla mentiria (es el defecto de C20).
     *
     * Devuelve null para las claves SIN regla cruzada (el resto de los 52 parametros vigentes):
     * en ese caso la pantalla no promete nada, que es lo correcto -- solo las valida por tipo.
     *
     * ServicioParametrosReglaTest cruza las dos listas leyendo este mismo archivo: si alguien
     * agrega una regla nueva y se olvida de su frase (o escribe una frase para una clave que no
     * tiene regla), falla.
     */
    public static String reglaDeClave(String clave) {
        return REGLAS_POR_CLAVE.get(clave);
    }

    /** B215: las claves que tienen frase, para que ServicioParametrosReglaTest cruce las dos
     * listas contra las validaciones reales de este mismo archivo. */
    public static java.util.Set<String> clavesConRegla() {
        return REGLAS_POR_CLAVE.keySet();
    }

    private static final java.util.Map<String, String> REGLAS_POR_CLAVE = crearReglasPorClave();

    private static java.util.Map<String, String> crearReglasPorClave() {
        java.util.Map<String, String> reglas = new java.util.LinkedHashMap<>();
        // DECIMAL (validarFraccionSegunClave)
        reglas.put("credito.tea_empresa",
                "Mayor que 0 % y hasta el tope legal vigente (pestaña «Límites de tasa»).");
        reglas.put("credito.factor_moratorio", "Mayor que 0 % y hasta 100 %.");
        reglas.put("credito.porcentaje_punitorio", "Mayor que 0 % y hasta 30 %.");
        reglas.put("credito.limite_afectacion", "Mayor que 0 % y hasta 100 %.");
        // ENTERO (validarEnteroSegunClave)
        reglas.put("credito.monto_minimo", "Mayor que 0 y menor que el monto máximo.");
        reglas.put("credito.monto_maximo", "Mayor que 0 y mayor que el monto mínimo.");
        reglas.put("credito.plazo_minimo", "Menor que el plazo máximo.");
        reglas.put("credito.plazo_maximo", "Mayor que el plazo mínimo.");
        reglas.put("buro.dias_vigencia_consentimiento", "Entre 1 y 365 días.");
        reglas.put("formalizacion.dias_min_primer_vencimiento",
                "Menos que los días máximos para el primer vencimiento.");
        reglas.put("formalizacion.dias_max_primer_vencimiento",
                "Más que los días mínimos para el primer vencimiento.");
        reglas.put("credito.edad_minima", "Menor que la edad máxima.");
        reglas.put("credito.edad_maxima", "Mayor que la edad mínima.");
        reglas.put("fiscal.factura_numero_desde", "No puede ser mayor que la numeración «hasta».");
        reglas.put("fiscal.factura_numero_hasta", "No puede ser menor que la numeración «desde».");
        // FECHA (validarFechaSegunClave)
        reglas.put("fiscal.timbrado_vigencia_desde", "No puede ser posterior a la vigencia «hasta».");
        reglas.put("fiscal.timbrado_vigencia_hasta", "No puede ser anterior a la vigencia «desde».");
        return java.util.Collections.unmodifiableMap(reglas);
    }

    private void validarFraccionSegunClave(Connection conexion, String clave, BigDecimal fraccion) throws SQLException {
        switch (clave) {
            case "credito.tea_empresa":
                // RF39/E1 (decisión de Joel, 13/10/2026): una sola fuente para el tope legal --
                // limites_tasa_usura, la misma que ya usan el cotizador, la aprobación y la
                // formalización (las 3 revalidaciones de RN-15). credito.tea_tope_legal queda
                // retirado (ver bd/migraciones/E10_15_rf39_tope_legal_y_nombre_parametro.sql).
                validarTeaEmpresa(fraccion, resolverTopeLegalVigente(limiteTasaUsuraDAO.obtenerVigente(conexion)));
                break;
            case "credito.factor_moratorio":
                validarFactorMoratorio(fraccion);
                break;
            case "credito.porcentaje_punitorio":
                validarPorcentajePunitorio(fraccion);
                break;
            case "credito.limite_afectacion":
                validarLimiteAfectacion(fraccion);
                break;
            default:
                // resto de DECIMAL (fiscal.iva_intereses) sin regla cruzada en esta etapa.
        }
    }

    private void validarEnteroSegunClave(Connection conexion, String clave, int valor) throws SQLException {
        switch (clave) {
            case "credito.monto_minimo":
                validarMontos(BigDecimal.valueOf(valor), obtenerValorDecimal(conexion, "credito.monto_maximo"));
                break;
            case "credito.monto_maximo":
                validarMontos(obtenerValorDecimal(conexion, "credito.monto_minimo"), BigDecimal.valueOf(valor));
                break;
            case "credito.plazo_minimo":
                validarPlazos(valor, obtenerValorEntero(conexion, "credito.plazo_maximo"));
                break;
            case "credito.plazo_maximo":
                validarPlazos(obtenerValorEntero(conexion, "credito.plazo_minimo"), valor);
                break;
            case "buro.dias_vigencia_consentimiento":
                validarDiasVigenciaConsentimiento(valor);
                break;
            case "formalizacion.dias_min_primer_vencimiento":
                validarDiasPrimerVencimiento(valor, obtenerValorEntero(conexion, "formalizacion.dias_max_primer_vencimiento"));
                break;
            case "formalizacion.dias_max_primer_vencimiento":
                validarDiasPrimerVencimiento(obtenerValorEntero(conexion, "formalizacion.dias_min_primer_vencimiento"), valor);
                break;
            case "credito.edad_minima":
                validarEdades(valor, obtenerValorEntero(conexion, "credito.edad_maxima"));
                break;
            case "credito.edad_maxima":
                validarEdades(obtenerValorEntero(conexion, "credito.edad_minima"), valor);
                break;
            case "fiscal.factura_numero_desde":
                validarRangoNumeroFactura(valor, obtenerValorEntero(conexion, "fiscal.factura_numero_hasta"));
                break;
            case "fiscal.factura_numero_hasta":
                validarRangoNumeroFactura(obtenerValorEntero(conexion, "fiscal.factura_numero_desde"), valor);
                break;
            default:
                // resto de ENTERO (dias de gracia, cuotas de alerta, intentos, minutos, etc.) sin regla cruzada.
        }
    }

    /** RF39 (corrección 2b, pedido de Joel, 13/10/2026): detalle de auditoría de CAMBIAR_PARAMETRO
     * con valor anterior, valor nuevo y motivo -- antes solo decía 'Cambió "clave".'. Mismo patrón
     * que los construirDetalle* de E2 (feriados/límites de tasa); usa formatearValorParametro para
     * que el valor se vea igual que en el historial (porcentaje/guaraníes/fecha según el tipo). */
    String construirDetalleCambioParametro(String nombreParametro, TipoValorParametro tipoValor, String clave,
            String valorAnterior, String valorNuevo, String motivo) {
        // Los guaraníes ya terminan en "Gs." (abreviatura, con su propio punto) -- sin este
        // chequeo quedaba "600.000 Gs.. Motivo: ..." (punto doble).
        String valorNuevoFormateado = formatearValorParametro(clave, tipoValor, valorNuevo);
        String puntoFinal = valorNuevoFormateado.endsWith(".") ? "" : ".";
        return "Cambió \"" + nombreParametro + "\": " + formatearValorParametro(clave, tipoValor, valorAnterior)
                + " → " + valorNuevoFormateado + puntoFinal + " Motivo: " + motivo + ".";
    }

    /** RF39/E2 (pedido de Joel, 13/10/2026): detalle de auditoría con antes/después para un alta
     * de feriado -- pura, para poder testearla sin base de datos. */
    String construirDetalleAltaFeriado(LocalDate fecha, String descripcion) {
        return "Agregó el feriado " + FormateadorFechas.comoDdMmAaaa(fecha) + " (" + descripcion
                + "): no existía → activo.";
    }

    /** RF39/E2 (pedido de Joel, 13/10/2026): detalle de auditoría con antes/después para
     * activar/desactivar un feriado -- pura. */
    String construirDetalleCambioActivoFeriado(boolean activoNuevo, LocalDate fecha, String descripcion,
            boolean activoAnterior) {
        String estadoAnterior = activoAnterior ? "activo" : "inactivo";
        String estadoNuevo = activoNuevo ? "activo" : "inactivo";
        return (activoNuevo ? "Reactivó" : "Desactivó") + " el feriado " + FormateadorFechas.comoDdMmAaaa(fecha)
                + " (" + descripcion + "): " + estadoAnterior + " → " + estadoNuevo + ".";
    }

    /** RF39/E2 (pedido de Joel, 13/10/2026): detalle de auditoría con antes/después para un alta
     * de límite de tasa -- pura. "anterior"/"anteriorVigenteHasta" null cuando no había ningún
     * límite vigente antes de esta alta. */
    /**
     * B223, opcion C (decision de Joel, 15/11/2026): con que fecha se cierra un limite de tasa que
     * esta siendo reemplazado por uno nuevo abierto.
     *
     * <p><b>El problema que resuelve</b>: un limite con la tasa equivocada bloquea todo el circuito
     * de credito (es la unica fuente del tope legal, RF39/E1, y lo leen siete lugares), y hasta hoy
     * <b>no se podia corregir el mismo dia</b>. El alta calculaba el cierre como
     * {@code nuevaDesde - 1} y no cerraba nada si eso caia antes del inicio del existente; despues
     * la validacion de superposicion veia dos vigencias abiertas y rechazaba. El error de hoy se
     * tapaba recien manana.</p>
     *
     * <p><b>La regla</b>: si el existente <b>ya rigio</b> (empezo antes que el nuevo), se cierra el
     * dia anterior al nuevo, como siempre. Si empieza <b>el mismo dia o despues</b>, se cierra el
     * dia anterior a su propio inicio: queda con la vigencia vacia, que es exactamente lo que pasó
     * -- <b>nunca rigio</b>. Asi la fila no se borra (RNF12: nada se elimina) y la auditoria del
     * alta queda contando la historia.</p>
     *
     * <p>La pantalla muestra esa fila como <i>"No rigio (reemplazado)"</i> en vez de una vigencia al
     * reves, y {@code elVigenteEn} la descarta sola porque un rango vacio no cubre ninguna fecha.</p>
     */
    public static LocalDate cierreParaLimiteReemplazado(LocalDate desdeExistente, LocalDate desdeNuevo) {
        return desdeExistente.isBefore(desdeNuevo)
                ? desdeNuevo.minusDays(1)
                : desdeExistente.minusDays(1);
    }

    String construirDetalleAltaLimiteTasa(LimiteTasaUsura anterior, LocalDate anteriorVigenteHasta,
            LimiteTasaUsura nuevo) {
        String detalleAnterior = anterior == null ? "no había límite vigente"
                : "anterior " + FormateadorNumeros.comoPorcentaje(anterior.getTasaMaximaTea())
                        + (anteriorVigenteHasta == null ? ""
                                : " (hasta " + FormateadorFechas.comoDdMmAaaa(anteriorVigenteHasta) + ")");
        return "Nuevo límite de tasa: " + detalleAnterior + " → nuevo "
                + FormateadorNumeros.comoPorcentaje(nuevo.getTasaMaximaTea()) + " desde "
                + FormateadorFechas.comoDdMmAaaa(nuevo.getVigenteDesde()) + ".";
    }

    // ---- Feriados (P12) ----

    /**
     * B221 (pedido de Joel, 15/11/2026): el orden con el que /parametros muestra los feriados.
     * Antes salían de la fecha más vieja a la más nueva ({@code FeriadoDAO.listar}:
     * {@code ORDER BY fecha}), así que con 24 cargados —10 de ellos ya pasados— la pestaña
     * arrancaba por los que ya no sirven y los próximos quedaban al pie.
     *
     * <p>El criterio: primero los que <b>vienen</b> (incluido el de hoy, que todavía importa), del
     * más cercano al más lejano; después los <b>pasados</b>, del más reciente al más viejo. En las
     * dos mitades se desempata por descripción, para que el orden sea determinista si dos feriados
     * caen el mismo día.</p>
     *
     * <p>Recibe {@code hoy} en vez de leer el reloj adentro: así su JUnit prueba el borde —el
     * feriado de HOY va con los próximos, no con los pasados— sin depender del día en que se
     * corra. Es el mismo criterio con que B197/B210 extrajeron sus comparadores.</p>
     *
     * <p><b>Solo afecta a esta pantalla</b>: {@code FeriadoDAO.listar} tiene un único llamador
     * (este método), y el que usa Formalización para calcular días hábiles es
     * {@code listarActivos}, que no se toca.</p>
     */
    public static java.util.Comparator<Feriado> comparadorFeriados(final LocalDate hoy) {
        return new java.util.Comparator<Feriado>() {
            @Override
            public int compare(Feriado uno, Feriado otro) {
                boolean unoViene = !uno.getFecha().isBefore(hoy);
                boolean otroViene = !otro.getFecha().isBefore(hoy);
                if (unoViene != otroViene) {
                    return unoViene ? -1 : 1;
                }
                int porFecha = unoViene
                        ? uno.getFecha().compareTo(otro.getFecha())
                        : otro.getFecha().compareTo(uno.getFecha());
                if (porFecha != 0) {
                    return porFecha;
                }
                String descripcionUno = uno.getDescripcion() == null ? "" : uno.getDescripcion();
                String descripcionOtro = otro.getDescripcion() == null ? "" : otro.getDescripcion();
                return descripcionUno.compareToIgnoreCase(descripcionOtro);
            }
        };
    }

    /** B221: los feriados como los muestra /parametros -- los proximos primero (ver
     * comparadorFeriados). La lista del DAO viene de la fecha mas vieja a la mas nueva. */
    public List<Feriado> listarFeriados(Connection conexion) throws SQLException {
        List<Feriado> feriados = new java.util.ArrayList<>(feriadoDAO.listar(conexion));
        java.util.Collections.sort(feriados, comparadorFeriados(Reloj.hoy()));
        return feriados;
    }

    public void altaFeriado(Feriado feriado, long idUsuarioQueRegistra) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                long id = feriadoDAO.insertar(conexion, feriado);
                servicioAuditoria.registrar(conexion, idUsuarioQueRegistra, "ALTA_FERIADO", "FERIADO", id,
                        construirDetalleAltaFeriado(feriado.getFecha(), feriado.getDescripcion()));
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    public void cambiarActivoFeriado(long idFeriado, boolean activo, long idUsuarioQueModifica) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // RF39/E2 (pedido de Joel, 13/10/2026): se lee ANTES de cambiar, para que el
                // detalle de la auditoría diga el estado anterior (no solo "Desactivó el
                // feriado.", sin decir cuál ni desde qué estado).
                Feriado feriado = feriadoDAO.obtenerPorId(conexion, idFeriado);
                if (feriado == null) {
                    throw new NegocioException("No se encontró el feriado.");
                }
                feriadoDAO.actualizarActivo(conexion, idFeriado, activo);
                servicioAuditoria.registrar(conexion, idUsuarioQueModifica,
                        activo ? "ACTIVAR_FERIADO" : "DESACTIVAR_FERIADO", "FERIADO", idFeriado,
                        construirDetalleCambioActivoFeriado(activo, feriado.getFecha(), feriado.getDescripcion(),
                                feriado.isActivo()));
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }

    // ---- Límites de tasa (P12) ----

    public List<LimiteTasaUsura> listarLimitesTasa(Connection conexion) throws SQLException {
        return limiteTasaUsuraDAO.listar(conexion);
    }

    public void altaLimiteTasa(LimiteTasaUsura limite, long idUsuarioQueRegistra) throws SQLException {
        try (Connection conexion = FabricaConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                // Si se carga un limite abierto (vigente_hasta null) y ya hay uno vigente
                // desde antes, se cierra automaticamente el dia anterior: asi el gerente
                // solo carga "el nuevo tope" sin tener que cerrar el viejo a mano.
                LimiteTasaUsura vigenteActual = limiteTasaUsuraDAO.obtenerVigente(conexion);
                // RF39/E2 (pedido de Joel, 13/10/2026): para el "anterior" del detalle de
                // auditoría -- si se cierra más abajo, se actualiza a la fecha real de cierre.
                LocalDate anteriorVigenteHasta = vigenteActual == null ? null : vigenteActual.getVigenteHasta();
                // B223 opción C (decisión de Joel, 15/11/2026): un alta ABIERTA cierra todos los
                // límites abiertos que haya, con cierreParaLimiteReemplazado -- el que ya regía, el
                // día anterior al nuevo (lo de siempre); el que empieza hoy o más adelante, el día
                // anterior a su propio inicio, o sea sin vigencia: nunca rigió. Así un límite
                // cargado con la tasa equivocada se puede corregir EL MISMO DÍA, que es lo que
                // antes no se podía. Nada se borra (RNF12): la fila queda con su auditoría.
                if (limite.getVigenteHasta() == null) {
                    for (LimiteTasaUsura abierto : limiteTasaUsuraDAO.listar(conexion)) {
                        if (abierto.getVigenteHasta() != null) {
                            continue;
                        }
                        LocalDate cierre = cierreParaLimiteReemplazado(abierto.getVigenteDesde(),
                                limite.getVigenteDesde());
                        limiteTasaUsuraDAO.cerrarVigencia(conexion, abierto.getIdLimiteTasa(), cierre);
                        if (vigenteActual != null
                                && abierto.getIdLimiteTasa() == vigenteActual.getIdLimiteTasa()) {
                            anteriorVigenteHasta = cierre;
                        }
                    }
                }

                List<LimiteTasaUsura> existentes = limiteTasaUsuraDAO.listar(conexion);
                validarSuperposicionLimitesTasa(limite.getVigenteDesde(), limite.getVigenteHasta(), existentes);

                limite.setRegistradoPor(idUsuarioQueRegistra);
                long id = limiteTasaUsuraDAO.insertar(conexion, limite);
                servicioAuditoria.registrar(conexion, idUsuarioQueRegistra, "ALTA_LIMITE_TASA", "LIMITE_TASA_USURA",
                        id, construirDetalleAltaLimiteTasa(vigenteActual, anteriorVigenteHasta, limite));
                conexion.commit();
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            }
        }
    }
}
