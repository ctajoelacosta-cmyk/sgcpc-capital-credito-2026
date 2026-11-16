package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPrestamo;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoSolicitud;

/**
 * Tanda de usabilidad (19/10/2026, pedido de Joel, RNF_revision.md §10a): una fila de la pestaña
 * "Solicitudes y préstamos" de la ficha del cliente -- una solicitud, con su préstamo si ya se
 * formalizó (identificadorOperacion/estadoPrestamo null si no). El enlace ya viene resuelto por
 * ControladorClientesServlet.construirEnlaceHistorial según el permiso real del usuario en
 * sesión -- null si no tiene acceso a ninguna pantalla para esa fila (la JSP no decide nada).
 */
public class FilaHistorialCredito {

    private final String codigoSolicitud;
    private final LocalDateTime fechaSolicitud;
    private final EstadoSolicitud estadoSolicitud;
    private final String identificadorOperacion;
    private final EstadoPrestamo estadoPrestamo;
    private final String enlace;

    public FilaHistorialCredito(String codigoSolicitud, LocalDateTime fechaSolicitud, EstadoSolicitud estadoSolicitud,
            String identificadorOperacion, EstadoPrestamo estadoPrestamo, String enlace) {
        this.codigoSolicitud = codigoSolicitud;
        this.fechaSolicitud = fechaSolicitud;
        this.estadoSolicitud = estadoSolicitud;
        this.identificadorOperacion = identificadorOperacion;
        this.estadoPrestamo = estadoPrestamo;
        this.enlace = enlace;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public EstadoSolicitud getEstadoSolicitud() {
        return estadoSolicitud;
    }

    /** null si la solicitud todavía no se formalizó. */
    public String getIdentificadorOperacion() {
        return identificadorOperacion;
    }

    /** null si la solicitud todavía no se formalizó. */
    public EstadoPrestamo getEstadoPrestamo() {
        return estadoPrestamo;
    }

    /** null si el usuario en sesión no tiene permiso para ninguna pantalla de esta fila. */
    public String getEnlace() {
        return enlace;
    }
}
