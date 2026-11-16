package py.com.capitalcredito.sgcpc.dto;

import java.util.List;
import py.com.capitalcredito.sgcpc.modelos.Persona;

/**
 * App móvil, ETAPA 2 (13/11/2026): un cliente al que le falta algún documento obligatorio del
 * expediente, con cuántos tiene y qué le falta.
 *
 * <p>Lo arma {@code ServicioClientes.listarPendientesDeExpediente} a partir de los
 * {@link ItemExpediente} que devuelve {@code construirItemsExpediente} -- <b>no recalcula nada</b>:
 * el estado de cada documento lo sigue decidiendo esa única regla, la misma que pinta la ficha del
 * sistema web.</p>
 *
 * <p>Inmutable. Es un dato de salida: nadie lo modifica después de armarlo.</p>
 */
public class ClientePendienteExpediente {

    private final Persona persona;
    private final int cumplidos;
    private final int obligatorios;
    private final List<String> faltantes;

    public ClientePendienteExpediente(Persona persona, int cumplidos, int obligatorios,
            List<String> faltantes) {
        this.persona = persona;
        this.cumplidos = cumplidos;
        this.obligatorios = obligatorios;
        this.faltantes = faltantes;
    }

    public Persona getPersona() {
        return persona;
    }

    /** Cuántos de los obligatorios ya están cumplidos (el "N" de "N de 4"). */
    public int getCumplidos() {
        return cumplidos;
    }

    /** Cuántos documentos obligatorios tiene el expediente (el "4" de "N de 4"). */
    public int getObligatorios() {
        return obligatorios;
    }

    /**
     * Las etiquetas de los que faltan, en el orden del trámite (B90) -- las mismas palabras que
     * muestra la ficha del sistema web, no códigos internos.
     */
    public List<String> getFaltantes() {
        return faltantes;
    }

    /**
     * Cuántos faltan. Es el criterio de orden de "Mis pendientes": primero el cliente al que más
     * le falta, que es el que más trabajo pendiente tiene.
     */
    public int cuantosFaltan() {
        return obligatorios - cumplidos;
    }
}
