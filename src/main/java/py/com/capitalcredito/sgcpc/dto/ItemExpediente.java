package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;
import py.com.capitalcredito.sgcpc.modelos.DocumentoExpediente;
import py.com.capitalcredito.sgcpc.modelos.TipoDocumento;

/**
 * Bloque 7/E10 (D-132, pedido de Joel): un documento requerido del expediente del cliente, en el
 * orden real del trámite (cédula, comprobante de ingreso, comprobante de domicilio, autorización
 * de consulta al buró, informe de Informconf) -- antes la pestaña de Documentos solo tenía un
 * desplegable en orden alfabético, sin indicar qué faltaba ni en qué orden. La descripción es la
 * misma guía que ya mostraba "Documentos sugeridos para esta situación laboral" (Tanda B, D-127).
 */
public class ItemExpediente {

    private final TipoDocumento tipo;
    private final String etiqueta;
    private final String descripcion;
    private final DocumentoExpediente documentoVigente;
    private final boolean vencida;
    private final LocalDate fechaVencimiento;
    private final boolean pendienteRenovacion;
    private final boolean obligatorio;

    public ItemExpediente(TipoDocumento tipo, String etiqueta, String descripcion, DocumentoExpediente documentoVigente,
            boolean obligatorio) {
        this(tipo, etiqueta, descripcion, documentoVigente, false, null, obligatorio);
    }

    /**
     * RF03 (pedido de Joel), Parte 1: fila de la cédula de identidad -- un documento puede estar
     * cargado (documentoVigente != null) y aun así estar VENCIDO en la vida real (la cédula de la
     * persona venció, aunque el escaneo subido sigue siendo el VIGENTE de la tabla). vencida/
     * fechaVencimiento solo se usan hoy para esa fila (ver ServicioClientes.construirItemDocumento);
     * el resto de las filas siempre pasa vencida=false.
     */
    public ItemExpediente(TipoDocumento tipo, String etiqueta, String descripcion, DocumentoExpediente documentoVigente,
            boolean vencida, LocalDate fechaVencimiento, boolean obligatorio) {
        this(tipo, etiqueta, descripcion, documentoVigente, vencida, fechaVencimiento, false, obligatorio);
    }

    /**
     * RF03 (pedido de Joel), Parte 1 -- reprueba: al cambiar la fecha de vencimiento de la cédula
     * en Datos, ServicioClientes.actualizar marca REEMPLAZADO el escaneo VIGENTE anterior (sin
     * subir ninguno nuevo todavía) -- documentoVigente queda null, igual que "nunca se cargó
     * nada". pendienteRenovacion distingue ese caso ("había un escaneo, lo invalidó un cambio de
     * fecha") de "nunca se cargó la cédula", para mostrar "Falta: cargar la cédula renovada
     * (nuevo vencimiento dd/mm/aaaa)" en vez del "Falta" genérico. Mutuamente excluyente con
     * vencida (una exige documentoVigente != null, la otra == null).
     *
     * Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): obligatorio --
     * true para los que de verdad bloquean "Enviar a aprobación" (RF06,
     * ServicioSolicitudes.agregarFaltantesExpediente/agregarFaltantesBuro) -- ver
     * ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS, la única lista. Desde el 28/10/2026
     * (decisión de Joel del 21/10) son los 4 del expediente: cédula, comprobante de ingreso,
     * comprobante de domicilio y autorización de buró; ya no queda ninguno complementario.
     */
    public ItemExpediente(TipoDocumento tipo, String etiqueta, String descripcion, DocumentoExpediente documentoVigente,
            boolean vencida, LocalDate fechaVencimiento, boolean pendienteRenovacion, boolean obligatorio) {
        this.tipo = tipo;
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.documentoVigente = documentoVigente;
        this.vencida = vencida;
        this.fechaVencimiento = fechaVencimiento;
        this.pendienteRenovacion = pendienteRenovacion;
        this.obligatorio = obligatorio;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }

    /** Etiqueta para mostrar en la fila -- normalmente nombre_tipo, salvo alguna excepción (ver ServicioClientes). */
    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public DocumentoExpediente getDocumentoVigente() {
        return documentoVigente;
    }

    /** RF03 (pedido de Joel): true solo en la fila de cédula, cuando la cédula de la persona ya
     * venció -- deja de contar como cumplida aunque haya un documento VIGENTE cargado. */
    public boolean isVencida() {
        return vencida;
    }

    /** Fecha a mostrar en el aviso ("Cédula vencida (dd/mm/aaaa)..." o "nuevo vencimiento
     * dd/mm/aaaa"); null salvo en la fila vencida o pendiente de renovación. */
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    /** RF03 (pedido de Joel), Parte 1 -- reprueba: true solo en la fila de cédula, cuando un
     * cambio de fecha en Datos invalidó el escaneo anterior y todavía no se cargó uno nuevo. */
    public boolean isPendienteRenovacion() {
        return pendienteRenovacion;
    }

    public boolean isCumplido() {
        return documentoVigente != null && !vencida;
    }

    /** Tanda de usabilidad (19/10/2026, pedido de Joel, RF03_expediente.md §10): ver el
     * constructor de 8 parámetros. */
    public boolean isObligatorio() {
        return obligatorio;
    }
}
