package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/**
 * B1 (Bloque 7/E10, pedido de Joel): un único objeto de filtros para /prestamos, compartido por
 * la pantalla, el informe PDF (/prestamos/informe) y el CSV (/prestamos/exportar) -- mismo
 * criterio que FiltroClientes (D-135): "el listado en pantalla y el informe usan la MISMA
 * consulta y los MISMOS filtros". estado es el name() de EstadoPrestamo, o null = todos.
 */
public class FiltroPrestamos {

    private String texto;
    private String estado;
    private LocalDate desembolsoDesde;
    private LocalDate desembolsoHasta;

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getDesembolsoDesde() {
        return desembolsoDesde;
    }

    public void setDesembolsoDesde(LocalDate desembolsoDesde) {
        this.desembolsoDesde = desembolsoDesde;
    }

    public LocalDate getDesembolsoHasta() {
        return desembolsoHasta;
    }

    public void setDesembolsoHasta(LocalDate desembolsoHasta) {
        this.desembolsoHasta = desembolsoHasta;
    }
}
