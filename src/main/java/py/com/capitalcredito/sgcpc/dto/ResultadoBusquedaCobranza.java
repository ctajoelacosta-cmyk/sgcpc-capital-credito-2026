package py.com.capitalcredito.sgcpc.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.modelos.Persona;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;

/**
 * D-146 (pantalla de búsqueda de Cobranza, pedido de Joel): resultado de
 * ServicioCobranza.buscarParaCobranza -- una lista de préstamos cobrables (ACTIVO/EN_MORA) que
 * coinciden con la búsqueda, o bien, cuando no hay ninguno, el motivo exacto (cliente inexistente,
 * sin préstamo activo, pendiente de desembolso, o A3b/#6: varios clientes ambiguos, ninguno
 * cobrable) con el dato para armar el/los enlaces correspondientes. Todo ya viene resuelto desde
 * el servicio -- la JSP no decide nada (REGLAS_DESARROLLO.md §5).
 */
public class ResultadoBusquedaCobranza {

    private final List<PrestamoCredito> prestamos;
    private final Map<Long, Persona> clientesPorPrestamo;
    private final String mensajeSinResultados;
    private final Long idPrestamoEnlace;
    private final boolean enlaceADesembolso;
    private final boolean enlaceAFormalizacion;
    private final List<CoincidenciaBusquedaCobranza> coincidencias;
    private final boolean hayMasCoincidencias;

    private ResultadoBusquedaCobranza(List<PrestamoCredito> prestamos, Map<Long, Persona> clientesPorPrestamo,
            String mensajeSinResultados, Long idPrestamoEnlace, boolean enlaceADesembolso,
            boolean enlaceAFormalizacion, List<CoincidenciaBusquedaCobranza> coincidencias,
            boolean hayMasCoincidencias) {
        this.prestamos = prestamos;
        this.clientesPorPrestamo = clientesPorPrestamo;
        this.mensajeSinResultados = mensajeSinResultados;
        this.idPrestamoEnlace = idPrestamoEnlace;
        this.enlaceADesembolso = enlaceADesembolso;
        this.enlaceAFormalizacion = enlaceAFormalizacion;
        this.coincidencias = coincidencias;
        this.hayMasCoincidencias = hayMasCoincidencias;
    }

    public static ResultadoBusquedaCobranza encontrados(List<PrestamoCredito> prestamos,
            Map<Long, Persona> clientesPorPrestamo) {
        return new ResultadoBusquedaCobranza(prestamos, clientesPorPrestamo, null, null, false, false,
                Collections.<CoincidenciaBusquedaCobranza>emptyList(), false);
    }

    /** idPrestamoEnlace: nullable -- préstamo al que apunta "Ver historial" (ficha de cobranza,
     * que reimprime la constancia), "Ver desembolso" (ficha de desembolso, enlaceADesembolso) o
     * "Ver ficha de formalización" (préstamo anulado antes del desembolso, enlaceAFormalizacion
     * -- RF20, solo si el perfil tiene permiso de ver Formalización; la cajera no lo tiene, así
     * que para ella queda en false y no se muestra ningún enlace). A lo sumo uno de los dos está
     * en true. */
    public static ResultadoBusquedaCobranza sinResultados(String mensaje, Long idPrestamoEnlace,
            boolean enlaceADesembolso, boolean enlaceAFormalizacion) {
        return new ResultadoBusquedaCobranza(Collections.<PrestamoCredito>emptyList(),
                Collections.<Long, Persona>emptyMap(), mensaje, idPrestamoEnlace, enlaceADesembolso,
                enlaceAFormalizacion, Collections.<CoincidenciaBusquedaCobranza>emptyList(), false);
    }

    /** A3b (#6, pedido de Joel): el texto coincide con 2+ clientes y ninguno tiene préstamo
     * cobrable -- en vez del mensaje genérico, la lista de coincidencias (máximo 10, ya recortada
     * por el servicio) con la situación de cada una. */
    public static ResultadoBusquedaCobranza variasCoincidencias(String mensaje,
            List<CoincidenciaBusquedaCobranza> coincidencias, boolean hayMasCoincidencias) {
        return new ResultadoBusquedaCobranza(Collections.<PrestamoCredito>emptyList(),
                Collections.<Long, Persona>emptyMap(), mensaje, null, false, false, coincidencias,
                hayMasCoincidencias);
    }

    public List<PrestamoCredito> getPrestamos() {
        return prestamos;
    }

    public Map<Long, Persona> getClientesPorPrestamo() {
        return clientesPorPrestamo;
    }

    public String getMensajeSinResultados() {
        return mensajeSinResultados;
    }

    public Long getIdPrestamoEnlace() {
        return idPrestamoEnlace;
    }

    public boolean isEnlaceADesembolso() {
        return enlaceADesembolso;
    }

    public boolean isEnlaceAFormalizacion() {
        return enlaceAFormalizacion;
    }

    /** A3b (#6): vacía salvo en el caso de varios clientes ambiguos sin ninguno cobrable. */
    public List<CoincidenciaBusquedaCobranza> getCoincidencias() {
        return coincidencias;
    }

    public boolean isHayMasCoincidencias() {
        return hayMasCoincidencias;
    }
}
