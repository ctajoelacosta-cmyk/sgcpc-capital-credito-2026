package py.com.capitalcredito.sgcpc.controladores;

import java.io.IOException;
import java.math.BigDecimal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros;
import py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras;

/**
 * Utilidades livianas de solo lectura, reutilizables desde cualquier
 * pantalla con un campo "campo-guaranies" (recursos/js/campo-guaranies.js).
 * Protegido solo por FiltroAutenticacion (exige sesión iniciada); no tiene
 * entrada en FiltroAutorizacion porque no expone ni modifica ningún dato
 * del negocio, solo convierte un número a texto.
 */
public class ControladorUtilidadesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (!"/letras".equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("text/plain; charset=UTF-8");
        String texto = "";
        try {
            BigDecimal monto = ConversorNumeros.aBigDecimalGuaranies(request.getParameter("monto"));
            texto = ConversorNumerosALetras.comoLetrasConGuaranies(monto);
        } catch (RuntimeException error) {
            // Se llama mientras el usuario todavia esta escribiendo (ej. "3." a medio
            // borrar): un monto invalido o fuera de rango en este punto no es un error,
            // simplemente no hay nada que mostrar todavia.
            texto = "";
        }
        response.getWriter().write(texto);
    }
}
