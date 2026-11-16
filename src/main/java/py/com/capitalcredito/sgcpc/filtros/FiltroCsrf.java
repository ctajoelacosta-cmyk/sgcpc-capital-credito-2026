package py.com.capitalcredito.sgcpc.filtros;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.infraestructura.Csrf;

/**
 * Valida el token CSRF (infraestructura/Csrf.java) en todo POST, incluido
 * el propio login (protege tambien contra login-CSRF). Reutilizable para
 * cualquier formulario POST de las etapas siguientes.
 */
public class FiltroCsrf implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // Sin inicializacion adicional.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equalsIgnoreCase(httpRequest.getMethod()) && !Csrf.esValido(httpRequest)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "El formulario venció o no es válido. Vuelva a intentarlo.");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Sin recursos que liberar.
    }
}
