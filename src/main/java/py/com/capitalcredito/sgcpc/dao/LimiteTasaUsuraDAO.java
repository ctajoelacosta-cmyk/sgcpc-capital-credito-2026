package py.com.capitalcredito.sgcpc.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import py.com.capitalcredito.sgcpc.infraestructura.Reloj;
import py.com.capitalcredito.sgcpc.modelos.LimiteTasaUsura;

/** Acceso a limites_tasa_usura (bd/01_esquema.sql / Doc. 08 Tabla 03). */
public class LimiteTasaUsuraDAO {

    /** Solo para mostrar el historial completo en /parametros (pestaña "Límites de tasa") -- el
     * tope de tasa que de verdad se usa al cotizar/aprobar/formalizar sale de obtenerVigente(),
     * nunca de este método. Desempate (encontrado probando RF39, D-151): si dos límites se
     * cargaran el mismo día, empatarían en vigente_desde (DATE, sin hora) -- fecha_registro
     * (TIMESTAMP real de esta tabla) e id_limite_tasa desempatan, mismo criterio que el LAG de
     * ParametroDAO.listarHistorial. */
    public List<LimiteTasaUsura> listar(Connection conexion) throws SQLException {
        String sql = "SELECT id_limite_tasa, vigente_desde, vigente_hasta, tasa_maxima_tea, fuente_referencia, "
                + "registrado_por, fecha_registro FROM limites_tasa_usura "
                + "ORDER BY vigente_desde DESC, fecha_registro DESC, id_limite_tasa DESC";
        List<LimiteTasaUsura> limites = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                limites.add(mapear(resultado));
            }
        }
        return limites;
    }

    /**
     * El limite en vigor HOY. Los siete lectores del tope legal llaman este metodo (el cotizador,
     * el alta de solicitud, la aprobacion, la formalizacion, ServicioCotizacion,
     * ServicioAprobacionCredito y el informe), asi que su respuesta es la del circuito entero.
     */
    public LimiteTasaUsura obtenerVigente(Connection conexion) throws SQLException {
        return obtenerVigenteEn(conexion, Reloj.hoy());
    }

    /**
     * B224: el limite en vigor en una fecha dada. La decision NO va en el SQL sino en
     * {@link #elVigenteEn(List, java.time.LocalDate)}, que es pura y por lo tanto se puede probar
     * con JUnit sin base -- y el tope legal es justo el dato donde una regla sin prueba cuesta mas
     * caro. Traer todas las filas no cuesta nada: esta tabla tiene UNA fila hoy y crece una vez por
     * cambio del tope del BCP (el criterio de volumen medido de B205/B210).
     */
    public LimiteTasaUsura obtenerVigenteEn(Connection conexion, java.time.LocalDate fecha) throws SQLException {
        return elVigenteEn(listar(conexion), fecha);
    }

    /**
     * B224 (15/11/2026): el limite en vigor en una fecha. <b>Pura</b>, para que la regla del tope
     * legal se pueda probar con JUnit sin base -- es el dato que leen los siete lectores del
     * circuito de credito.
     *
     * <p>Vigente en una fecha significa las dos mitades: <b>ya empezo</b>
     * ({@code vigente_desde <= fecha}) y <b>todavia no termino</b> ({@code vigente_hasta} nulo o
     * {@code >= fecha}). La consulta anterior miraba solo {@code vigente_hasta IS NULL} y se quedaba
     * con el de {@code vigente_desde} mas alto, asi que un limite cargado con fecha FUTURA pasaba a
     * ser el tope de hoy.</p>
     *
     * <p><b>La segunda mitad no es un adorno</b>: al cargar un tope futuro, el alta cierra el actual
     * el dia anterior a esa fecha, asi que el que rige hoy es un limite <b>cerrado</b> que todavia
     * cubre hoy. Sin ese {@code vigente_hasta >= fecha} no habria ningun vigente y el circuito
     * entero se bloquearia con "No hay un limite de tasa vigente cargado".</p>
     *
     * <p><b>Un rango vacio nunca rige</b> ({@code vigente_hasta < vigente_desde}): es el limite que
     * se cargo mal y se reemplazo el mismo dia (B223, opcion C). Lo descarta la misma condicion, sin
     * un caso aparte.</p>
     *
     * <p>Y el desempate es por el <b>ultimo cargado</b> (id mas alto) para que dos limites que
     * empiezan el mismo dia no alternen entre dos consultas -- la leccion de B205 con
     * {@code fecha_hora}.</p>
     */
    public static LimiteTasaUsura elVigenteEn(List<LimiteTasaUsura> todos, java.time.LocalDate fecha) {
        LimiteTasaUsura elegido = null;
        for (LimiteTasaUsura candidato : todos) {
            if (candidato.getVigenteDesde().isAfter(fecha)) {
                continue;
            }
            if (candidato.getVigenteHasta() != null && candidato.getVigenteHasta().isBefore(fecha)) {
                continue;
            }
            if (elegido == null
                    || candidato.getVigenteDesde().isAfter(elegido.getVigenteDesde())
                    || (candidato.getVigenteDesde().equals(elegido.getVigenteDesde())
                            && candidato.getIdLimiteTasa() > elegido.getIdLimiteTasa())) {
                elegido = candidato;
            }
        }
        return elegido;
    }

    public void cerrarVigencia(Connection conexion, long idLimiteTasa, java.time.LocalDate vigenteHasta) throws SQLException {
        String sql = "UPDATE limites_tasa_usura SET vigente_hasta = ? WHERE id_limite_tasa = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setDate(1, Date.valueOf(vigenteHasta));
            sentencia.setLong(2, idLimiteTasa);
            sentencia.executeUpdate();
        }
    }

    public long insertar(Connection conexion, LimiteTasaUsura limite) throws SQLException {
        String sql = "INSERT INTO limites_tasa_usura "
                + "(vigente_desde, vigente_hasta, tasa_maxima_tea, fuente_referencia, registrado_por, fecha_registro) "
                + "VALUES (?, ?, ?, ?, ?, now())";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setDate(1, Date.valueOf(limite.getVigenteDesde()));
            if (limite.getVigenteHasta() == null) {
                sentencia.setNull(2, java.sql.Types.DATE);
            } else {
                sentencia.setDate(2, Date.valueOf(limite.getVigenteHasta()));
            }
            sentencia.setBigDecimal(3, limite.getTasaMaximaTea());
            sentencia.setString(4, limite.getFuenteReferencia());
            sentencia.setLong(5, limite.getRegistradoPor());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    private LimiteTasaUsura mapear(ResultSet resultado) throws SQLException {
        LimiteTasaUsura limite = new LimiteTasaUsura();
        limite.setIdLimiteTasa(resultado.getLong("id_limite_tasa"));
        limite.setVigenteDesde(resultado.getDate("vigente_desde").toLocalDate());
        Date vigenteHasta = resultado.getDate("vigente_hasta");
        limite.setVigenteHasta(vigenteHasta == null ? null : vigenteHasta.toLocalDate());
        limite.setTasaMaximaTea(resultado.getBigDecimal("tasa_maxima_tea"));
        limite.setFuenteReferencia(resultado.getString("fuente_referencia"));
        limite.setRegistradoPor(resultado.getLong("registrado_por"));
        limite.setFechaRegistro(resultado.getTimestamp("fecha_registro").toLocalDateTime());
        return limite;
    }
}
