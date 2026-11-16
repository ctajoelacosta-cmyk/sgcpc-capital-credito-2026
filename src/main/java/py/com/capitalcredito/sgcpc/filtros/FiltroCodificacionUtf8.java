package py.com.capitalcredito.sgcpc.filtros;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Fuerza UTF-8 en cada pedido y respuesta. Se registra en web.xml, mapeado a /*.
 */
public class FiltroCodificacionUtf8 implements Filter {

    private static final String CODIFICACION = "UTF-8";

    @Override
    public void init(FilterConfig filterConfig) {
        // Sin inicializacion adicional.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(CODIFICACION);
        response.setCharacterEncoding(CODIFICACION);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Sin recursos que liberar.
    }
}
