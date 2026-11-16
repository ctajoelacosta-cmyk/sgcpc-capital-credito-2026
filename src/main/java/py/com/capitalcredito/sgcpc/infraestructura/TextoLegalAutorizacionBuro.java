package py.com.capitalcredito.sgcpc.infraestructura;

/**
 * RF03/RF04 (pedido de Joel): única fuente del texto legal de la "Autorización para consulta de
 * información crediticia" (Ley N.º 6534/2020) -- mismo criterio que GlosarioFinanciero: el .jrxml
 * no redacta nada, solo muestra este texto tal cual. RF03 Parte 3: completo(razonSocial) nombra a
 * la empresa real (antes decía "la entidad arriba mencionada", sin ningún dato concreto) -- mismo
 * patrón que GlosarioFinanciero.TEA.completo(...), interpolar en vez de duplicar el texto.
 *
 * ADVERTENCIA (a confirmar con Joel/asesoría legal antes de usar con un cliente real): el texto de
 * abajo es un borrador razonable, no una cita literal de la Ley N.º 6534/2020 ni de su
 * reglamentación -- no se inventa una redacción legal definitiva sin que Joel la confirme
 * (REGLAS_DESARROLLO.md §7.5). Reemplazar por el texto exacto que corresponda antes de la defensa.
 */
public final class TextoLegalAutorizacionBuro {

    private TextoLegalAutorizacionBuro() {
    }

    public static String completo(String razonSocial) {
        return "Por medio del presente documento, autorizo en forma expresa a " + razonSocial
                + " a consultar y/o reportar mis datos personales y mis antecedentes crediticios "
                + "ante las empresas de información crediticia habilitadas, de conformidad con la "
                + "Ley N.º 6534/2020 \"De Protección de Datos Personales Crediticios\" y su "
                + "reglamentación, con la única finalidad de evaluar mi capacidad y comportamiento "
                + "de pago para el otorgamiento del crédito solicitado. Declaro haber sido "
                + "informado/a de mi derecho de acceso, rectificación y actualización de mis datos "
                + "conforme a la citada ley.";
    }
}
