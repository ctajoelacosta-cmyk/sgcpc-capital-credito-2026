package py.com.capitalcredito.sgcpc.dto;

/** Una fila del informe "Historial de parámetros" (C-4.3, pedido de Joel). Todo ya formateado --
 * mismo molde que el resto de los informes de este bloque. Solo lectura: refleja lo que ya estaba
 * guardado en parametros_sistema, nunca cambia nada. */
public class FilaHistorialParametro {

    private final String claveParametro;
    private final String parametro;
    private final String valorAnterior;
    private final String valorNuevo;
    private final String valorAnteriorMostrado;
    private final String valorNuevoMostrado;
    private final String vigenteDesde;
    private final String vigenteHasta;
    private final String modificadoPor;
    private final String fechaModificacion;
    private final String observacion;

    public FilaHistorialParametro(String parametro, String valorAnterior, String valorNuevo, String vigenteDesde,
            String vigenteHasta, String modificadoPor, String fechaModificacion, String observacion,
            String claveParametro, String valorAnteriorMostrado, String valorNuevoMostrado) {
        this.parametro = parametro;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
        this.modificadoPor = modificadoPor;
        this.fechaModificacion = fechaModificacion;
        this.observacion = observacion;
        this.claveParametro = claveParametro;
        this.valorAnteriorMostrado = valorAnteriorMostrado;
        this.valorNuevoMostrado = valorNuevoMostrado;
    }

    /** RF39 (pantalla rediseñada): la clave nunca cambia, a diferencia del nombre -- se usa para
     * agrupar/filtrar/exportar por identidad real del parámetro, y se muestra chica y gris debajo
     * del nombre (mismo patrón que Parámetros, V1). */
    public String getClaveParametro() {
        return claveParametro;
    }

    /** Nombre ACTUAL del parámetro (resuelto por clave en ParametroDAO.listarHistorial) -- nunca
     * el nombre congelado de la fila histórica, que puede haber cambiado desde entonces. */
    public String getParametro() {
        return parametro;
    }

    /** "—" si es la primera vigencia registrada de ese parámetro. */
    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    /** V1/RF39 (pedido de Joel): igual que getValorAnterior(), pero con el espacio después de
     * cada coma cuando el parámetro es TEXTO (FormateadorTexto.comoListaConEspacios) -- solo para
     * mostrar en la pantalla del historial y su PDF. El CSV sigue usando getValorAnterior() (el
     * valor original, sin tocar). */
    public String getValorAnteriorMostrado() {
        return valorAnteriorMostrado;
    }

    /** Igual que getValorAnteriorMostrado(), para el valor nuevo. */
    public String getValorNuevoMostrado() {
        return valorNuevoMostrado;
    }

    public String getVigenteDesde() {
        return vigenteDesde;
    }

    /** "Vigente" si todavía es el valor actual (vigente_hasta nulo). */
    public String getVigenteHasta() {
        return vigenteHasta;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public String getFechaModificacion() {
        return fechaModificacion;
    }

    /** "—" si no tiene observación cargada. */
    public String getObservacion() {
        return observacion;
    }
}
