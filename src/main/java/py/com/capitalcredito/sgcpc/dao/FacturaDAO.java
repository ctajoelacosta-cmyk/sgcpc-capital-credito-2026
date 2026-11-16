package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoFactura;
import py.com.capitalcredito.sgcpc.modelos.Factura;

/** Acceso a facturas (bd/01_esquema.sql / RN-25, P09, E7). */
public class FacturaDAO {

    private static final String COLUMNAS = "id_factura, id_pago, timbrado, establecimiento, punto_expedicion, "
            + "numero, fecha, cliente_nombre, cliente_documento, total_exenta, total_gravada_10, iva_10, total, "
            + "estado, id_archivo, id_reversion_pago, numero_nota_anulacion, id_archivo_nota_anulacion";

    public long insertar(Connection conexion, Factura factura) throws SQLException {
        String sql = "INSERT INTO facturas (id_pago, timbrado, establecimiento, punto_expedicion, numero, fecha, "
                + "cliente_nombre, cliente_documento, total_exenta, total_gravada_10, iva_10, total, estado, "
                + "id_archivo, id_reversion_pago) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, factura.getIdPago());
            sentencia.setString(2, factura.getTimbrado());
            sentencia.setString(3, factura.getEstablecimiento());
            sentencia.setString(4, factura.getPuntoExpedicion());
            sentencia.setString(5, factura.getNumero());
            sentencia.setDate(6, Date.valueOf(factura.getFecha()));
            sentencia.setString(7, factura.getClienteNombre());
            sentencia.setString(8, factura.getClienteDocumento());
            sentencia.setBigDecimal(9, factura.getTotalExenta());
            sentencia.setBigDecimal(10, factura.getTotalGravada10());
            sentencia.setBigDecimal(11, factura.getIva10());
            sentencia.setBigDecimal(12, factura.getTotal());
            sentencia.setString(13, EstadoFactura.EMITIDA.name());
            setLongNullable(sentencia, 14, factura.getIdArchivo());
            setLongNullable(sentencia, 15, factura.getIdReversionPago());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    public Factura obtenerPorId(Connection conexion, long idFactura) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM facturas WHERE id_factura = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idFactura);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    public Factura obtenerPorPago(Connection conexion, long idPago) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM facturas WHERE id_pago = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPago);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /** D-94 (E8): las facturas emitidas en pagos de una sesión de caja -- para el arqueo. */
    public java.util.List<Factura> listarPorSesion(Connection conexion, long idSesionCaja) throws SQLException {
        String sql = "SELECT f.id_factura, f.id_pago, f.timbrado, f.establecimiento, f.punto_expedicion, "
                + "f.numero, f.fecha, f.cliente_nombre, f.cliente_documento, f.total_exenta, f.total_gravada_10, "
                + "f.iva_10, f.total, f.estado, f.id_archivo, f.id_reversion_pago, f.numero_nota_anulacion, "
                + "f.id_archivo_nota_anulacion "
                + "FROM facturas f JOIN pagos p ON p.id_pago = f.id_pago WHERE p.id_sesion_caja = ? ORDER BY f.id_factura";
        java.util.List<Factura> facturas = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idSesionCaja);
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    facturas.add(mapear(resultado));
                }
            }
        }
        return facturas;
    }

    /** Bloque 4 (E8, reporte "IVA del mes"): facturas EMITIDAS con fecha dentro de un rango. */
    public java.util.List<Factura> listarPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM facturas WHERE estado = 'EMITIDA' AND fecha BETWEEN ? AND ? "
                + "ORDER BY numero";
        java.util.List<Factura> facturas = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    facturas.add(mapear(resultado));
                }
            }
        }
        return facturas;
    }

    /** E9/Bloque 5 (RF-41, DNIT RG 118/2017): anula la factura al autorizar la reversión de su
     * pago -- nunca se borra ni se reemplaza (RN-13), sigue siendo reimprimible con el cartel
     * ANULADA. numeroNotaAnulacion (NAN-aaaa-nnnnnn) se reserva acá, dentro de la transacción de
     * la reversión; id_archivo_nota_anulacion queda NULL y se completa después, la primera vez
     * que se pida el PDF (mismo patrón D-94 "generar al primer pedido", vía
     * ArchivoComprobanteDAO/obtenerOGenerarPdf -- la generación de un PDF con Jasper nunca va
     * dentro de la transacción de dinero). */
    public void marcarAnulada(Connection conexion, long idFactura, long idReversionPago, String numeroNotaAnulacion)
            throws SQLException {
        String sql = "UPDATE facturas SET estado = 'ANULADA', id_reversion_pago = ?, numero_nota_anulacion = ? "
                + "WHERE id_factura = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idReversionPago);
            sentencia.setString(2, numeroNotaAnulacion);
            sentencia.setLong(3, idFactura);
            sentencia.executeUpdate();
        }
    }

    /**
     * E9/Bloque 5: reporte "Facturas anuladas del mes" (solo gerente, para la baja en Marangatu).
     *
     * <p><b>B210, segunda vuelta (15/11/2026, decisión de Joel)</b>: ordenado de la más NUEVA a la
     * más vieja, igual que los otros listados del sistema que él revisó (Sesiones cerradas,
     * Historial de cobros, Auditoría). Antes era {@code ORDER BY numero} ascendente, y al paginar
     * el reporte en B210 eso dejó la anulación más reciente en la <b>última</b> página: con 81
     * anuladas en el mes (medido el 15/11/2026) la que acababa de emitirse caía en la página 5, así
     * que ni el gerente ni las cuatro verificaciones de fila del humo la encontraban. Se desempata
     * por {@code numero} para que el orden sea determinista dentro de un mismo día: dos facturas de
     * la misma fecha podrían alternar entre páginas (la lección de B205 con {@code fecha_hora}).</p>
     *
     * <p>No afecta al reporte de IVA, que es el otro que llama a este método:
     * {@code ServicioReportes.listarIva} reordena por su cuenta ({@code facturas.sort} por número)
     * después de mezclar las anuladas con el resto — verificado antes de cambiar el ORDER BY.</p>
     */
    public java.util.List<Factura> listarAnuladasPorRango(Connection conexion, java.time.LocalDate desde,
            java.time.LocalDate hasta) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM facturas WHERE estado = 'ANULADA' AND fecha BETWEEN ? AND ? "
                + "ORDER BY fecha DESC, numero DESC";
        java.util.List<Factura> facturas = new java.util.ArrayList<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(desde));
            sentencia.setDate(2, Date.valueOf(hasta));
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    facturas.add(mapear(resultado));
                }
            }
        }
        return facturas;
    }

    private void setLongNullable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }

    private Factura mapear(ResultSet resultado) throws SQLException {
        Factura factura = new Factura();
        factura.setIdFactura(resultado.getLong("id_factura"));
        factura.setIdPago(resultado.getLong("id_pago"));
        factura.setTimbrado(resultado.getString("timbrado"));
        factura.setEstablecimiento(resultado.getString("establecimiento"));
        factura.setPuntoExpedicion(resultado.getString("punto_expedicion"));
        factura.setNumero(resultado.getString("numero"));
        factura.setFecha(resultado.getDate("fecha").toLocalDate());
        factura.setClienteNombre(resultado.getString("cliente_nombre"));
        factura.setClienteDocumento(resultado.getString("cliente_documento"));
        factura.setTotalExenta(resultado.getBigDecimal("total_exenta"));
        factura.setTotalGravada10(resultado.getBigDecimal("total_gravada_10"));
        factura.setIva10(resultado.getBigDecimal("iva_10"));
        factura.setTotal(resultado.getBigDecimal("total"));
        factura.setEstado(EstadoFactura.valueOf(resultado.getString("estado")));
        long idArchivo = resultado.getLong("id_archivo");
        factura.setIdArchivo(resultado.wasNull() ? null : idArchivo);
        long idReversionPago = resultado.getLong("id_reversion_pago");
        factura.setIdReversionPago(resultado.wasNull() ? null : idReversionPago);
        factura.setNumeroNotaAnulacion(resultado.getString("numero_nota_anulacion"));
        long idArchivoNotaAnulacion = resultado.getLong("id_archivo_nota_anulacion");
        factura.setIdArchivoNotaAnulacion(resultado.wasNull() ? null : idArchivoNotaAnulacion);
        return factura;
    }
}
