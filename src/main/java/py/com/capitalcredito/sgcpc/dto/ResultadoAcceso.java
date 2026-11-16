package py.com.capitalcredito.sgcpc.dto;

import py.com.capitalcredito.sgcpc.enumeraciones.EstadoCuenta;

/**
 * Salida de ServicioAutenticacion.intentarAcceso: la decision y, si
 * corresponde, que hay que actualizar en el usuario (null = sin cambio).
 * Quien orquesta (usa UsuarioDAO) persiste esos campos y llama a
 * ServicioAuditoria en la misma transaccion.
 */
public class ResultadoAcceso {

    public enum Tipo {
        ACCESO_CONCEDIDO,
        CREDENCIALES_INVALIDAS,
        CUENTA_BLOQUEADA,
        DEBE_CAMBIAR_CONTRASENA
    }

    private final Tipo tipo;
    private final String mensaje;
    private final Integer intentosFallidosNuevos;
    private final EstadoCuenta estadoCuentaNuevo;

    public ResultadoAcceso(Tipo tipo, String mensaje, Integer intentosFallidosNuevos, EstadoCuenta estadoCuentaNuevo) {
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.intentosFallidosNuevos = intentosFallidosNuevos;
        this.estadoCuentaNuevo = estadoCuentaNuevo;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Integer getIntentosFallidosNuevos() {
        return intentosFallidosNuevos;
    }

    public EstadoCuenta getEstadoCuentaNuevo() {
        return estadoCuentaNuevo;
    }
}
