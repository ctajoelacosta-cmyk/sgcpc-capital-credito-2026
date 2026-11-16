<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Cuota" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%-- D-145 (P-5, pedido de Joel): fragmento AJAX (GET, sin grabar nada) -- arma la misma vista
     previa que ya se calcula al cargar la ficha (calcularPreviewAnticipoParcial, sin tocar el
     servicio), pero con el monto real que se va a aplicar. Lo sirve
     ControladorCobranzaServlet.mostrarVistaPreviaAnticipo, que deja "previewAnticipo" en el
     request antes de reenviar acá; nunca se visita esta ruta directamente. Los <div> con id son
     los que cobranza-ficha.js separa para reemplazar el contenido de la ficha, sin volver a
     crear el <p>/<details> que ya existen en cobranza/ficha.jsp.

     Corrección de diseño del 27/10/2026 (opción B, tras la prueba en pantalla de Joel):
     - "fragMonto" (en guaraníes enteros, sin formato): el monto que el SERVIDOR encontró para la
       cantidad de cuotas elegida -- el mínimo que deja exactamente esas cuotas en el plan nuevo,
       ya múltiplo de credito.redondeo_efectivo. Así el navegador nunca arma un importe
       (REGLAS_DESARROLLO.md §5): solo muestra y reenvía lo que el servidor ya decidió. Es un solo
       monto, sin el par "exacto / en efectivo" (aprobado por Joel): acá no es un precio con
       descuento, es el mínimo que logra sacar esas cuotas.
     - "fragAhorro": el interés que el cliente deja de pagar (RN-11), informativo. Vacío cuando el
       pedido fue por monto libre, donde no hay una cantidad de cuotas con la que comparar.
     - "fragMensajeBloqueo": el motivo exacto por el que esa cantidad/ese monto no se puede
       adelantar (mínimo de interés del período, D-88, D-90, un parámetro inválido, o que no haya
       ningún monto que deje justo esa cantidad de cuotas). Antes el servlet respondía vacío y la
       cajera se quedaba sin saber por qué "Cobrar" no andaba. --%>
<%
    ResultadoLiquidacion previewAnticipoFragmento = (ResultadoLiquidacion) request.getAttribute("previewAnticipo");
    String mensajeBloqueoFragmento = (String) request.getAttribute("mensajeBloqueoAnticipo");
    if (previewAnticipoFragmento == null) {
%><div id="fragMensajeBloqueo"><%= mensajeBloqueoFragmento == null
        ? "No se pudo calcular el adelanto; intente nuevamente." : mensajeBloqueoFragmento %></div><%
    } else {
        Cuota primeraCuotaNuevoPlanFragmento = previewAnticipoFragmento.getNuevoPlan().get(0);
        int cuotasRestantesFragmento = previewAnticipoFragmento.getNuevoPlan().size();
        BigDecimal montoFragmento = (BigDecimal) request.getAttribute("montoAnticipoPrevia");
        BigDecimal ahorroFragmento = (BigDecimal) request.getAttribute("ahorroAnticipoPrevia");
%><div id="fragTextoProximaCuota">Su próxima cuota será el
    <%-- C39 (29/10/2026): la fecha de COBRO, del mismo dueño que usan la ficha y el recibo. --%>
    <strong><%= py.com.capitalcredito.sgcpc.servicios.ServicioCobranza
            .fechaProximaCuotaNuevoPlan(primeraCuotaNuevoPlanFragmento) %></strong>
    por <strong><%= FormateadorNumeros.comoGuaranies(primeraCuotaNuevoPlanFragmento.getTotalProgramado()) %></strong>;
    <%-- D-145 (P-14, pedido de Joel): "queda 1 cuota" en singular, "quedan N cuotas" en plural. --%>
    le <%= cuotasRestantesFragmento == 1 ? "queda" : "quedan" %>
    <strong><%= cuotasRestantesFragmento %> <%= cuotasRestantesFragmento == 1 ? "cuota" : "cuotas" %></strong>.</div><div id="fragContenidoDetalle">Interés devengado hoy: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFragmento.getInteresDevengado()) %>
    + IVA <%= FormateadorNumeros.comoGuaranies(previewAnticipoFragmento.getIvaInteresDevengado()) %>
    Capital aplicado: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFragmento.getMontoAnticipoCapital()) %>
    Saldo que queda: <%= FormateadorNumeros.comoGuaranies(previewAnticipoFragmento.getNuevoSaldoCapital()) %></div><div id="fragMonto"><%= montoFragmento == null ? "" : montoFragmento.toBigInteger() %></div><div id="fragAhorro"><%= ahorroFragmento == null || ahorroFragmento.compareTo(BigDecimal.ZERO) <= 0 ? ""
        : "El cliente se ahorra " + FormateadorNumeros.comoGuaranies(ahorroFragmento)
                + " de intereses por pagar por adelantado." %></div><%
    }
%>
