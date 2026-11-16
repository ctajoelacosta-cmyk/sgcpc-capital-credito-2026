package py.com.capitalcredito.sgcpc.dto;

import java.util.Map;

/** Totales del informe "Historial de parámetros" (C-4.3, pedido de Joel): cantidad total de
 * cambios en el período y cantidad por parámetro. */
public class TotalesHistorialParametros {

    private final int cantidadTotal;
    private final int cantidadParametrosDistintos;
    private final Map<String, Integer> cantidadPorParametro;

    public TotalesHistorialParametros(int cantidadTotal, int cantidadParametrosDistintos,
            Map<String, Integer> cantidadPorParametro) {
        this.cantidadTotal = cantidadTotal;
        this.cantidadParametrosDistintos = cantidadParametrosDistintos;
        this.cantidadPorParametro = cantidadPorParametro;
    }

    public int getCantidadTotal() {
        return cantidadTotal;
    }

    /** RF39 (pantalla rediseñada): cantidad de parámetros distintos (por CLAVE, no por nombre)
     * que tuvieron al menos un cambio en el período filtrado -- para el total corto "N cambios en
     * el período · M parámetros modificados". */
    public int getCantidadParametrosDistintos() {
        return cantidadParametrosDistintos;
    }

    /** Se mantiene para no romper el PDF actual (ServicioInformes.generarInformeHistorialParametrosPdf
     * la sigue usando en el pie de página) -- el rediseño del .jrxml queda como paso aparte, con
     * revisión de Joel. */
    public Map<String, Integer> getCantidadPorParametro() {
        return cantidadPorParametro;
    }
}
