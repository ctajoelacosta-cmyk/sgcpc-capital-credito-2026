package py.com.capitalcredito.sgcpc.excepciones;

/**
 * Consolidación (A.8, pedido de Joel): subtipo de NegocioException para cuando la caja sigue
 * abierta de un día anterior -- bloquea entregar efectivo o desembolsar hasta cerrarla y
 * rendirla, pero no es un error del servidor ni un dato mal escrito, es una regla de tiempo
 * (mismo tipo de bloqueo que RF11 "fuera de plazo", ver {@code FechaFueraDePlazoException}):
 * advertencia, no error.
 */
public class CajaDeOtroDiaException extends NegocioException {

    public CajaDeOtroDiaException(String mensaje) {
        super(mensaje);
    }
}
