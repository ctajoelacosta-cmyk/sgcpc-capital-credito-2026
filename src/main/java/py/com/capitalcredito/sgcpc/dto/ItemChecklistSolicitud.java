package py.com.capitalcredito.sgcpc.dto;

/**
 * Bloque 7/E10 (D-129, Obs. 19/22): un requisito del checklist que se muestra en la ficha de la
 * solicitud antes de "Enviar a aprobación" (✓ / falta), para que el oficial vea de una vez todo
 * lo que hace falta sin tener que apretar el botón y recibir el rechazo. No reemplaza la
 * validación real de ServicioSolicitudes.enviarAAprobacion (que sigue siendo la que manda): este
 * checklist es solo informativo.
 */
public class ItemChecklistSolicitud {

    private final String etiqueta;
    private final boolean cumple;

    public ItemChecklistSolicitud(String etiqueta, boolean cumple) {
        this.etiqueta = etiqueta;
        this.cumple = cumple;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isCumple() {
        return cumple;
    }
}
