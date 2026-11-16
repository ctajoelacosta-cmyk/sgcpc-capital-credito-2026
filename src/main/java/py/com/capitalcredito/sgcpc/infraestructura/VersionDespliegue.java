package py.com.capitalcredito.sgcpc.infraestructura;

/**
 * D-146 (pedido de Joel): un solo valor de cache-busting ("?v=") para TODO el despliegue, no
 * uno por archivo. Antes (RF08, 11/10/2026) cada JSP que cargaba sus propios .css/.js propios
 * tenía que declarar su propia constante (solo `pie.jsp` lo hacía) -- `cabecera.jsp` y
 * `reportes/indice.jsp` quedaban sin ese valor y sus assets propios (`sgcpc-tema.css`,
 * `indice-reportes.css`/`.js`) podían quedar cacheados después de un despliegue sin que
 * Ctrl+Shift+R fuera obvio (hallazgo de Joel, RF08_resumen_solicitud.md).
 *
 * Un campo estático se inicializa una sola vez, cuando esta clase se carga por primera vez --
 * que vuelve a pasar en cada despliegue (nueva classloader de la webapp). No depende de ningún
 * build tool (REGLAS_DESARROLLO.md §3: nada nuevo fuera de lo ya aprobado).
 */
public final class VersionDespliegue {

    public static final String VALOR = String.valueOf(System.currentTimeMillis());

    private VersionDespliegue() {
    }
}
