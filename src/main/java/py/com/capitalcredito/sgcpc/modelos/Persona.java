package py.com.capitalcredito.sgcpc.modelos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPersona;

/**
 * Ficha única de persona (cliente, Doc. 08 Tabla 04). En E3 solo
 * se registra la Persona; el vínculo con una solicitud (cliente) es
 * de E4 (ajuste A del plan de E3, AVANCE.md D-37). cedulaIdentidad siempre
 * se guarda normalizada (solo dígitos, ServicioClientes.normalizarCedula).
 */
public class Persona {

    private Long idPersona;
    private String cedulaIdentidad;
    private String ruc;
    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private String estadoCivil;
    private String telefonoWhatsapp;
    private String correoElectronico;
    private EstadoPersona estadoPersona;
    private Long registradoPor;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;
    private String telefonoAlternativo;
    // Bloque 7/E10, Tanda A (D-126)
    private LocalDate fechaVencimientoCedula;

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public String getCedulaIdentidad() {
        return cedulaIdentidad;
    }

    public void setCedulaIdentidad(String cedulaIdentidad) {
        this.cedulaIdentidad = cedulaIdentidad;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getEstadoCivil() {
        return estadoCivil;
    }

    public void setEstadoCivil(String estadoCivil) {
        this.estadoCivil = estadoCivil;
    }

    public String getTelefonoWhatsapp() {
        return telefonoWhatsapp;
    }

    public void setTelefonoWhatsapp(String telefonoWhatsapp) {
        this.telefonoWhatsapp = telefonoWhatsapp;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = correoElectronico;
    }

    public EstadoPersona getEstadoPersona() {
        return estadoPersona;
    }

    public void setEstadoPersona(EstadoPersona estadoPersona) {
        this.estadoPersona = estadoPersona;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public String getTelefonoAlternativo() {
        return telefonoAlternativo;
    }

    public void setTelefonoAlternativo(String telefonoAlternativo) {
        this.telefonoAlternativo = telefonoAlternativo;
    }

    public LocalDate getFechaVencimientoCedula() {
        return fechaVencimientoCedula;
    }

    public void setFechaVencimientoCedula(LocalDate fechaVencimientoCedula) {
        this.fechaVencimientoCedula = fechaVencimientoCedula;
    }
}
