package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import py.com.capitalcredito.sgcpc.modelos.PersonaLaboral;

/**
 * Acceso a personas_laborales (normalización de personas, grupo Laborales, 16/10/2026 --
 * bd/01_esquema.sql). SQL parametrizado, sin ORM, mismo patrón que el resto de los DAO (Doc. 00
 * §15): cada método recibe la Connection, la abre y la transacciona quien llama (el Servicio).
 */
public class PersonaLaboralDAO {

    public PersonaLaboral obtenerPorPersona(Connection conexion, long idPersona) throws SQLException {
        String sql = "SELECT id_persona, situacion_laboral, empleador_nombre, empleador_ruc, "
                + "empleador_direccion, empleador_telefono, cargo, antiguedad_laboral_anios, "
                + "ingreso_neto_comprobado, presenta_comprobante_ingreso, otros_ingresos_mensuales, "
                + "gastos_fijos_mensuales, cuota_otras_deudas_mensual "
                + "FROM personas_laborales WHERE id_persona = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        }
    }

    /**
     * Upsert -- igual que personas_domicilio: SIEMPRE corresponde una fila (toda persona tiene
     * datos laborales), sin la semántica de "nunca se toca al reactivar" de personas_baja.
     */
    public void guardar(Connection conexion, long idPersona, PersonaLaboral laboral) throws SQLException {
        String sql = "INSERT INTO personas_laborales (id_persona, situacion_laboral, empleador_nombre, "
                + "empleador_ruc, empleador_direccion, empleador_telefono, cargo, antiguedad_laboral_anios, "
                + "ingreso_neto_comprobado, presenta_comprobante_ingreso, otros_ingresos_mensuales, "
                + "gastos_fijos_mensuales, cuota_otras_deudas_mensual) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id_persona) DO UPDATE SET situacion_laboral = EXCLUDED.situacion_laboral, "
                + "empleador_nombre = EXCLUDED.empleador_nombre, empleador_ruc = EXCLUDED.empleador_ruc, "
                + "empleador_direccion = EXCLUDED.empleador_direccion, empleador_telefono = EXCLUDED.empleador_telefono, "
                + "cargo = EXCLUDED.cargo, antiguedad_laboral_anios = EXCLUDED.antiguedad_laboral_anios, "
                + "ingreso_neto_comprobado = EXCLUDED.ingreso_neto_comprobado, "
                + "presenta_comprobante_ingreso = EXCLUDED.presenta_comprobante_ingreso, "
                + "otros_ingresos_mensuales = EXCLUDED.otros_ingresos_mensuales, "
                + "gastos_fijos_mensuales = EXCLUDED.gastos_fijos_mensuales, "
                + "cuota_otras_deudas_mensual = EXCLUDED.cuota_otras_deudas_mensual";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, idPersona);
            sentencia.setString(2, laboral.getSituacionLaboral());
            sentencia.setString(3, laboral.getEmpleadorNombre());
            sentencia.setString(4, laboral.getEmpleadorRuc());
            sentencia.setString(5, laboral.getEmpleadorDireccion());
            sentencia.setString(6, laboral.getEmpleadorTelefono());
            sentencia.setString(7, laboral.getCargo());
            setIntNullable(sentencia, 8, laboral.getAntiguedadLaboralAnios());
            sentencia.setBigDecimal(9, laboral.getIngresoNetoComprobado());
            sentencia.setBoolean(10, laboral.isPresentaComprobanteIngreso());
            setBigDecimalNullable(sentencia, 11, laboral.getOtrosIngresosMensuales());
            setBigDecimalNullable(sentencia, 12, laboral.getGastosFijosMensuales());
            sentencia.setBigDecimal(13, laboral.getCuotaOtrasDeudasMensual());
            sentencia.executeUpdate();
        }
    }

    /**
     * Normalización de personas, grupo Laborales (16/10/2026): situación laboral de un lote de
     * personas en una sola consulta -- para informes/listados que antes leían
     * `persona.getSituacionLaboral()` directo (`ServicioInformes`, `ControladorClientesServlet`).
     * Vacío (nunca null) si `idsPersona` está vacía.
     */
    public Map<Long, String> mapaSituacionLaboralPorPersona(Connection conexion, List<Long> idsPersona)
            throws SQLException {
        Map<Long, String> mapa = new HashMap<>();
        if (idsPersona.isEmpty()) {
            return mapa;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id_persona, situacion_laboral FROM personas_laborales WHERE id_persona IN (");
        for (int i = 0; i < idsPersona.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < idsPersona.size(); i++) {
                sentencia.setLong(i + 1, idsPersona.get(i));
            }
            try (ResultSet resultado = sentencia.executeQuery()) {
                while (resultado.next()) {
                    mapa.put(resultado.getLong("id_persona"), resultado.getString("situacion_laboral"));
                }
            }
        }
        return mapa;
    }

    /** RF02 (pedido de Joel): códigos de situación laboral que tiene AL MENOS un cliente cargado
     * -- lo usa el filtro del listado de Clientes para no dejar fuera de la búsqueda a clientes
     * con una situación ya no admitida. Movido de PersonaDAO (vivía sobre personas, grupo
     * Laborales, 16/10/2026). */
    public List<String> listarSituacionesLaboralesDistintas(Connection conexion) throws SQLException {
        List<String> situaciones = new ArrayList<>();
        String sql = "SELECT DISTINCT situacion_laboral FROM personas_laborales";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            while (resultado.next()) {
                situaciones.add(resultado.getString(1));
            }
        }
        return situaciones;
    }

    private void setBigDecimalNullable(PreparedStatement sentencia, int indice, java.math.BigDecimal valor)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, java.sql.Types.NUMERIC);
        } else {
            sentencia.setBigDecimal(indice, valor);
        }
    }

    private void setIntNullable(PreparedStatement sentencia, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, java.sql.Types.SMALLINT);
        } else {
            sentencia.setInt(indice, valor);
        }
    }

    private PersonaLaboral mapear(ResultSet resultado) throws SQLException {
        PersonaLaboral laboral = new PersonaLaboral();
        laboral.setIdPersona(resultado.getLong("id_persona"));
        laboral.setSituacionLaboral(resultado.getString("situacion_laboral"));
        laboral.setEmpleadorNombre(resultado.getString("empleador_nombre"));
        laboral.setEmpleadorRuc(resultado.getString("empleador_ruc"));
        laboral.setEmpleadorDireccion(resultado.getString("empleador_direccion"));
        laboral.setEmpleadorTelefono(resultado.getString("empleador_telefono"));
        laboral.setCargo(resultado.getString("cargo"));
        int antiguedadLaboral = resultado.getInt("antiguedad_laboral_anios");
        laboral.setAntiguedadLaboralAnios(resultado.wasNull() ? null : antiguedadLaboral);
        laboral.setIngresoNetoComprobado(resultado.getBigDecimal("ingreso_neto_comprobado"));
        laboral.setPresentaComprobanteIngreso(resultado.getBoolean("presenta_comprobante_ingreso"));
        laboral.setOtrosIngresosMensuales(resultado.getBigDecimal("otros_ingresos_mensuales"));
        laboral.setGastosFijosMensuales(resultado.getBigDecimal("gastos_fijos_mensuales"));
        laboral.setCuotaOtrasDeudasMensual(resultado.getBigDecimal("cuota_otras_deudas_mensual"));
        return laboral;
    }
}
