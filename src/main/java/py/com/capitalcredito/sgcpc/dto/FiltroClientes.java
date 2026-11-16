package py.com.capitalcredito.sgcpc.dto;

import java.time.LocalDate;

/**
 * Bloque 7/E10 (D-135, pedido de Joel): un único objeto de filtros para el listado de Clientes,
 * compartido por la pantalla (/clientes), el informe PDF (/clientes/informe) y el CSV
 * (/clientes/exportar) -- "el listado en pantalla y el informe usan la MISMA consulta y los
 * MISMOS filtros". texto/cedulaNormalizada son el mismo criterio de siempre (ServicioClientes.
 * normalizarCedula); el resto son los filtros nuevos.
 */
public class FiltroClientes {

    private String texto;
    private String cedulaNormalizada;
    private String estado;
    private LocalDate altaDesde;
    private LocalDate altaHasta;
    private Long idDepartamento;
    private Long idCiudad;
    private String situacionLaboral;
    private String prestamoActivo;

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getCedulaNormalizada() {
        return cedulaNormalizada;
    }

    public void setCedulaNormalizada(String cedulaNormalizada) {
        this.cedulaNormalizada = cedulaNormalizada;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getAltaDesde() {
        return altaDesde;
    }

    public void setAltaDesde(LocalDate altaDesde) {
        this.altaDesde = altaDesde;
    }

    public LocalDate getAltaHasta() {
        return altaHasta;
    }

    public void setAltaHasta(LocalDate altaHasta) {
        this.altaHasta = altaHasta;
    }

    public Long getIdDepartamento() {
        return idDepartamento;
    }

    public void setIdDepartamento(Long idDepartamento) {
        this.idDepartamento = idDepartamento;
    }

    public Long getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(Long idCiudad) {
        this.idCiudad = idCiudad;
    }

    public String getSituacionLaboral() {
        return situacionLaboral;
    }

    public void setSituacionLaboral(String situacionLaboral) {
        this.situacionLaboral = situacionLaboral;
    }

    public String getPrestamoActivo() {
        return prestamoActivo;
    }

    public void setPrestamoActivo(String prestamoActivo) {
        this.prestamoActivo = prestamoActivo;
    }

    /**
     * B157 (14/11/2026): el orden pedido por el usuario, como CLAVE (no como nombre de columna).
     * Viaja acá, en el mismo DTO que los filtros, por una razón concreta:
     * {@code construirFiltroDesdeRequest} es el único lugar que arma este objeto y lo usan los
     * tres consumidores de la consulta -- el listado en pantalla, el informe PDF y el CSV -- así
     * que el PDF y el CSV salen en el mismo orden que la pantalla sin una línea extra
     * (decisión de Joel, 14/11/2026).
     *
     * <p>La traducción de clave a SQL, con su lista blanca, vive en {@code PersonaDAO}: es el
     * dueño de la consulta y el único que puede garantizar que acá nunca entre texto del
     * usuario. Si la clave es null o desconocida, el DAO usa su orden de siempre.</p>
     */
    private String orden;
    /** "asc" o "desc"; cualquier otra cosa la descarta el DAO. */
    private String sentido;

    public String getOrden() {
        return orden;
    }

    public void setOrden(String orden) {
        this.orden = orden;
    }

    public String getSentido() {
        return sentido;
    }

    public void setSentido(String sentido) {
        this.sentido = sentido;
    }
}
