package py.com.capitalcredito.sgcpc.dto;

/** Fila de totales al pie del informe Caja (§13, Bloque 4, E8; B5 Bloque 7/E10, pedido de Joel):
 * sobrantes y faltantes sumados POR SEPARADO (nunca netos entre sí -- un sobrante de una sesión
 * no "tapa" el faltante de otra). */
public class TotalesCaja {

    private final int cantidadSesiones;
    private final String sumaSobrantes;
    private final String sumaFaltantes;

    public TotalesCaja(int cantidadSesiones, String sumaSobrantes, String sumaFaltantes) {
        this.cantidadSesiones = cantidadSesiones;
        this.sumaSobrantes = sumaSobrantes;
        this.sumaFaltantes = sumaFaltantes;
    }

    public int getCantidadSesiones() {
        return cantidadSesiones;
    }

    public String getSumaSobrantes() {
        return sumaSobrantes;
    }

    public String getSumaFaltantes() {
        return sumaFaltantes;
    }
}
