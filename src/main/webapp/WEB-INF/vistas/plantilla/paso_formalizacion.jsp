<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.PasoGuiado" %>
<%--
  B104 (01/11/2026, hallazgo de Joel): el cartel de éxito al aprobar promete "Ahora se formaliza
  en Formalización", pero ninguna de las dos fichas de una solicitud aprobada ofrecía un camino
  hasta ahí.

  Las DOS pantallas muestran el mismo cuadro verde de "Aprobada por ...": /aprobaciones/{id}
  (aprobaciones/ficha.jsp, donde el gerente aprueba) y /solicitudes/{id} (solicitudes/ficha.jsp,
  la del oficial). Este fragmento existe para que el botón viva en UN solo lugar: el texto, el
  destino y la etiqueta salen del paso guiado (ServicioFormalizacion.calcularPasoDeLaSolicitudAprobada,
  mismo DTO PasoGuiado que B88), y el markup, de acá. Si mañana cambia el texto, cambia en el
  servicio y las dos pantallas lo siguen.

  No muestra nada si el atributo no está (solicitud que no es APROBADA) o si el paso no tiene
  destino. El botón NO agrega ningún permiso: /formalizacion sigue protegido por FiltroAutorizacion.
--%>
<% PasoGuiado pasoFormalizacionCompartido = (PasoGuiado) request.getAttribute("pasoFormalizacion"); %>
<% if (pasoFormalizacionCompartido != null && pasoFormalizacionCompartido.getRuta() != null) { %>
<div class="mt-2">
    <%= pasoFormalizacionCompartido.getTexto() %>
    <a href="${pageContext.request.contextPath}<%= pasoFormalizacionCompartido.getRuta() %>"
       class="btn btn-sm btn-success ms-2"><%= pasoFormalizacionCompartido.getEtiquetaBoton() %>
        <i class="fas fa-arrow-right" aria-hidden="true"></i></a>
</div>
<% } %>
