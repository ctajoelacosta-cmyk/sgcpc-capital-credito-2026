package py.com.capitalcredito.sgcpc.dto;

/** Fila de totales al pie del reporte IVA del mes (§13, Bloque 4, E8). Gravada 5 %/IVA 5 % no
 * están acá -- este negocio nunca las usa (mismo motivo que ServicioCobranza las manda en 0 al
 * PDF de la factura); la pantalla las muestra fijas en 0, sin sumarlas de ningún lado. */
public class TotalesIva {

    private final int cantidadFacturas;
    private final String exenta;
    private final String gravada10;
    private final String iva10;
    private final String total;

    public TotalesIva(int cantidadFacturas, String exenta, String gravada10, String iva10, String total) {
        this.cantidadFacturas = cantidadFacturas;
        this.exenta = exenta;
        this.gravada10 = gravada10;
        this.iva10 = iva10;
        this.total = total;
    }

    public int getCantidadFacturas() {
        return cantidadFacturas;
    }

    public String getExenta() {
        return exenta;
    }

    public String getGravada10() {
        return gravada10;
    }

    public String getIva10() {
        return iva10;
    }

    public String getTotal() {
        return total;
    }
}
