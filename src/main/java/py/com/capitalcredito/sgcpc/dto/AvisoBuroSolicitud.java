package py.com.capitalcredito.sgcpc.dto;

import java.util.Collections;
import java.util.List;

/**
 * RF08 (pedido de Joel, 11/10/2026): resumen YA FORMATEADO de lo que el buró indica sobre una
 * solicitud -- franja arriba de todo en `aprobaciones/ficha.jsp`,
 * antes de la tarjeta "Cliente". Armado por
 * `ServicioAprobacionCredito.construirAvisoBuro`, nunca en la JSP (mismo criterio que
 * `FilaBandejaAprobacion` de RF07).
 *
 * Si hay mora Y clasificación de riesgo alto (D/E) a la vez, se muestra SOLO la franja roja
 * (`hayMora`), con la clasificación incluida en sus líneas -- la franja naranja
 * (`hayRiesgoAlto`) es exclusiva de cuando no hay ninguna mora.
 */
public class AvisoBuroSolicitud {

    private final boolean hayMora;
    private final boolean hayRiesgoAlto;
    private final List<String> lineas;

    public AvisoBuroSolicitud(boolean hayMora, boolean hayRiesgoAlto, List<String> lineas) {
        this.hayMora = hayMora;
        this.hayRiesgoAlto = hayRiesgoAlto;
        this.lineas = lineas == null ? Collections.emptyList() : lineas;
    }

    public boolean isHayMora() {
        return hayMora;
    }

    public boolean isHayRiesgoAlto() {
        return hayRiesgoAlto;
    }

    public List<String> getLineas() {
        return lineas;
    }
}
