<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.modelos.Cuota" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.ConversorNumerosALetras" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.GlosarioFinanciero" %>
<%@ page import="java.util.List" %>
<%@ page import="py.com.capitalcredito.sgcpc.dto.FilaComparacionPlazo" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%!
    /* B143: mismo tratamiento que el Panel le da al sufijo (panel.jsp, V5-bis): separa el " Gs."
     * del numero para mostrarlo mas chico y gris, pegado al numero. No es cosmetica nada mas --
     * medido, el sufijo del mismo tamano obliga a bajar el numero para que el peor monto posible
     * entre en la tarjeta; con el sufijo mas chico el numero entra bastante mas grande. No toca
     * FormateadorNumeros ni ningun Java: es armado de HTML dentro de esta JSP. Si el texto no
     * termina en " Gs." lo devuelve tal cual. */
    private String conSufijoGuaranies(String monto) {
        if (monto == null) {
            return "";
        }
        if (!monto.endsWith(" Gs.")) {
            return monto;
        }
        return monto.substring(0, monto.length() - 4)
                + "<span class=\"cc-cotizador-cifra-sufijo\">Gs.</span>";
    }
%>
<%
    ResultadoCotizacion resultadoCotizacion = (ResultadoCotizacion) request.getAttribute("resultado");
    String fechaSugerida = (String) request.getAttribute("fechaSugerida");
    String montoIngresado = (String) request.getAttribute("montoIngresado");
    String plazoIngresado = (String) request.getAttribute("plazoIngresado");
    String fechaIngresada = (String) request.getAttribute("fechaIngresada");
    String nombreIngresado = (String) request.getAttribute("nombreIngresado");
    String cedulaIngresada = (String) request.getAttribute("cedulaIngresada");
    java.math.BigDecimal montoMinimo = (java.math.BigDecimal) request.getAttribute("montoMinimo");
    java.math.BigDecimal montoMaximo = (java.math.BigDecimal) request.getAttribute("montoMaximo");
    Integer plazoMinimo = (Integer) request.getAttribute("plazoMinimo");
    Integer plazoMaximo = (Integer) request.getAttribute("plazoMaximo");
    ServicioFormalizacion.RangoFechas rangoFechasCotizador =
            (ServicioFormalizacion.RangoFechas) request.getAttribute("rangoFechas");
    java.math.BigDecimal topeTeaVigente = (java.math.BigDecimal) request.getAttribute("topeTeaVigente");
    @SuppressWarnings("unchecked")
    List<FilaComparacionPlazo> comparacionPlazos = (List<FilaComparacionPlazo>) request.getAttribute("comparacionPlazos");
%>
<h1>Cotizador</h1>
<p class="text-muted">Calcule las cuotas para un interesado. No se guarda ningún registro.</p>

<%-- B143 (pedido de Joel, 11/11/2026): formulario a la izquierda y resultado a la derecha. En
     pantalla angosta el .row de Bootstrap las apila solo, el formulario arriba. Retoma la parte
     del ancho que B136 había dejado fuera de alcance. Solo presentación: ningún cálculo, ningún
     dato nuevo.
     Ajuste del 11/11/2026, probado por Joel en pantalla: el formulario NO lleva h-100. Con el
     plan de pagos desplegado la tarjeta se estiraba hasta el alto del resultado y dejaba debajo
     un rectángulo blanco enorme. Queda con su alto natural y, de 992 px para arriba, pegado
     arriba (position: sticky) mientras se recorre el resultado -- ver sgcpc-tema.css §9. --%>
<div class="row cc-cotizador">
    <div class="col-lg-4">
        <div class="card card-primary cc-cotizador-formulario">
            <div class="card-body">
                <form method="post" action="${pageContext.request.contextPath}/cotizador" id="formularioCotizador">
                    <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
                    <div class="form-group">
                        <label>Monto (Gs.)</label>
                        <input type="text" name="monto" id="campoMonto" class="form-control campo-guaranies"
                               value="<%= montoIngresado == null ? "" : montoIngresado %>" required>
                        <% if (montoMinimo != null && montoMaximo != null) { %>
                        <small class="form-text text-muted">Entre <%= FormateadorNumeros.comoGuaranies(montoMinimo) %>
                            y <%= FormateadorNumeros.comoGuaranies(montoMaximo) %></small>
                        <% } %>
                    </div>
                    <div class="form-group">
                        <label>Plazo (meses)</label>
                        <%-- B164 (14/11/2026): inputmode="numeric" -- teclado numérico en la
                             tablet, cero cambio visual. Los tres campos de plazo del sistema lo
                             llevan (este, solicitudes/nueva.jsp y solicitudes/ficha.jsp); no se
                             pasa a type="number" para no meter spinners en pantallas aprobadas. --%>
                        <input type="text" inputmode="numeric" name="plazoMeses" id="campoPlazo" class="form-control"
                               value="<%= plazoIngresado == null ? "" : plazoIngresado %>" required>
                        <% if (plazoMinimo != null && plazoMaximo != null) { %>
                        <small class="form-text text-muted">Entre <%= plazoMinimo %> y <%= plazoMaximo %> meses.</small>
                        <% } %>
                    </div>
                    <div class="form-group">
                        <label>Fecha estimada del primer vencimiento</label>
                        <input type="date" name="fechaPrimerVencimiento" class="form-control"
                               value="<%= fechaIngresada == null ? fechaSugerida : fechaIngresada %>"
                               min="<%= LocalDate.now() %>" required>
                        <% if (rangoFechasCotizador != null) { %>
                        <small class="form-text text-muted">Entre <%= FormateadorFechas.comoDdMmAaaa(rangoFechasCotizador.getMinima()) %>
                            y <%= FormateadorFechas.comoDdMmAaaa(rangoFechasCotizador.getMaxima()) %>.</small>
                        <% } %>
                    </div>
                    <div class="form-group">
                        <label>Nombre del interesado (opcional)</label>
                        <input type="text" name="nombreInteresado" class="form-control"
                               value="<%= nombreIngresado == null ? "" : nombreIngresado %>">
                    </div>
                    <div class="form-group">
                        <label>C.I. del interesado (opcional)</label>
                        <input type="text" name="cedulaInteresado" class="form-control"
                               value="<%= cedulaIngresada == null ? "" : cedulaIngresada %>">
                        <small class="form-text text-muted">El nombre y la C.I. solo se imprimen en el PDF.</small>
                    </div>
                    <a href="${pageContext.request.contextPath}/cotizador" class="btn btn-outline-secondary">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">Cotizar</button>
                </form>
            </div>
        </div>
    </div>

    <div class="col-lg-8">
<% if (resultadoCotizacion == null) { %>
        <%-- B143: sin cotización, la columna de la derecha dice qué va a aparecer ahí, en vez de
             quedar vacía (era el "todo a la izquierda con vacío a la derecha" que originó B86). --%>
        <div class="card h-100">
            <div class="card-body cc-cotizador-vacio">
                <i class="fas fa-calculator cc-cotizador-vacio-icono"></i>
                <p class="cc-cotizador-vacio-titulo">Todavía no hay una cotización</p>
                <p class="cc-cotizador-vacio-detalle">Complete el monto y el plazo, y use «Cotizar».</p>
            </div>
        </div>
<% } else { %>
        <div class="card card-success h-100" id="resultadoCotizacion">
            <div class="card-header"><h5 class="m-0">Resultado</h5></div>
            <div class="card-body">
                <%
                    Cuota primeraCuota = resultadoCotizacion.getPlan().get(0);
                    java.math.BigDecimal montoSolicitado = ConversorNumeros.aBigDecimalGuaranies(montoIngresado);
                %>
                <%-- B143: las tres cifras que el interesado pregunta primero, arriba de todo. Son
                     los mismos valores que ya mostraba la tabla (ningún cálculo nuevo); se sacaron
                     de la tabla para no decir dos veces el mismo número (criterio de B123). --%>
                <div class="row cc-cotizador-cifras">
                    <div class="col-xl-4">
                        <div class="cc-cotizador-cifra cc-cotizador-cifra-principal">
                            <span class="cc-cotizador-cifra-rotulo">Cuota (1.ª, con IVA)</span>
                            <span class="cc-cotizador-cifra-numero"><%= conSufijoGuaranies(FormateadorNumeros.comoGuaranies(primeraCuota.getTotalProgramado())) %></span>
                            <span class="cc-cotizador-cifra-pie"><%= ConversorNumerosALetras.comoLetrasConGuaranies(primeraCuota.getTotalProgramado()) %></span>
                            <span class="cc-cotizador-cifra-pie">El IVA sobre intereses baja en cada cuota.</span>
                        </div>
                    </div>
                    <div class="col-xl-4">
                        <div class="cc-cotizador-cifra">
                            <span class="cc-cotizador-cifra-rotulo">Total a pagar (con IVA)</span>
                            <span class="cc-cotizador-cifra-numero"><%= conSufijoGuaranies(FormateadorNumeros.comoGuaranies(resultadoCotizacion.getMontoTotalConIva())) %></span>
                            <span class="cc-cotizador-cifra-pie"><%= ConversorNumerosALetras.comoLetrasConGuaranies(resultadoCotizacion.getMontoTotalConIva()) %></span>
                        </div>
                    </div>
                    <div class="col-xl-4">
                        <div class="cc-cotizador-cifra">
                            <span class="cc-cotizador-cifra-rotulo">Plazo</span>
                            <span class="cc-cotizador-cifra-numero"><%= resultadoCotizacion.getPlan().size() %> meses</span>
                            <span class="cc-cotizador-cifra-pie">vencimientos mensuales</span>
                        </div>
                    </div>
                </div>

                <div class="alert alert-info">Cotización informativa, no constituye aprobación del crédito.</div>
                <%
                    String teaFormateada = FormateadorNumeros.comoPorcentaje(resultadoCotizacion.getTea());
                    String tnaFormateada = FormateadorNumeros.comoPorcentaje(resultadoCotizacion.getTna());
                    String cftFormateada = FormateadorNumeros.comoPorcentaje(resultadoCotizacion.getCft());
                    String tasaMensualFormateada = FormateadorNumeros.comoPorcentaje(
                            resultadoCotizacion.getTna().divide(new java.math.BigDecimal("12"), 6,
                                    java.math.RoundingMode.HALF_UP));
                    String topeTeaFormateado = topeTeaVigente == null ? "no disponible"
                            : FormateadorNumeros.comoPorcentaje(topeTeaVigente);
                %>
                <%-- B143: las tres tasas pasaron de tres filas de la tabla a una fila de tres
                     columnas. Cada una conserva su TEXTO CORTO debajo: RF01-E (decisión de Joel)
                     dice que el corto va siempre visible junto al dato y el completo recién al
                     hacer clic en el ⓘ. La primera versión de B143 los había sacado y, probándolo,
                     Joel pidió recuperarlos: sin ellos GlosarioFinanciero.getCorto() se quedaba
                     sin ningún uso en todo el sistema. Ni el corto ni el completo se redactan acá:
                     salen enteros de GlosarioFinanciero. --%>
                <div class="row cc-cotizador-tasas">
                    <div class="col-sm-4">
                        <div class="cc-cotizador-tasa">
                            <span class="cc-cotizador-tasa-dato"><span class="cc-cotizador-tasa-sigla">TEA</span> <%= teaFormateada %>
                                <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                        data-glosario-titulo="<%= GlosarioFinanciero.TEA.getTitulo() %>"
                                        data-glosario-texto="<%= GlosarioFinanciero.TEA.completo(topeTeaFormateado) %>"></i></span>
                            <span class="cc-cotizador-tasa-corto"><%= GlosarioFinanciero.TEA.getCorto() %></span>
                        </div>
                    </div>
                    <div class="col-sm-4">
                        <div class="cc-cotizador-tasa">
                            <span class="cc-cotizador-tasa-dato"><span class="cc-cotizador-tasa-sigla">TNA</span> <%= tnaFormateada %>
                                <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                        data-glosario-titulo="<%= GlosarioFinanciero.TNA.getTitulo() %>"
                                        data-glosario-texto="<%= GlosarioFinanciero.TNA.completo(tasaMensualFormateada, tnaFormateada) %>"></i></span>
                            <span class="cc-cotizador-tasa-corto"><%= GlosarioFinanciero.TNA.getCorto() %></span>
                        </div>
                    </div>
                    <div class="col-sm-4">
                        <div class="cc-cotizador-tasa">
                            <span class="cc-cotizador-tasa-dato"><span class="cc-cotizador-tasa-sigla">CFT</span> <%= cftFormateada %>
                                <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                        data-glosario-titulo="<%= GlosarioFinanciero.CFT.getTitulo() %>"
                                        data-glosario-texto="<%= GlosarioFinanciero.CFT.completo() %>"></i></span>
                            <span class="cc-cotizador-tasa-corto"><%= GlosarioFinanciero.CFT.getCorto() %></span>
                        </div>
                    </div>
                </div>

                <table class="table table-sm">
                    <tr>
                        <th>Monto solicitado</th>
                        <td>
                            <%= FormateadorNumeros.comoGuaranies(montoSolicitado) %>
                            <br><small class="text-muted"><%= ConversorNumerosALetras.comoLetrasConGuaranies(montoSolicitado) %></small>
                        </td>
                    </tr>
                    <tr><th>Cuota (sin IVA)</th><td><%= FormateadorNumeros.comoGuaranies(resultadoCotizacion.getCuotaSinIva()) %></td></tr>
                </table>
                <%
                    LocalDate fechaPedida = LocalDate.parse(fechaIngresada);
                    if (!primeraCuota.getFechaVencimiento().equals(fechaPedida)) {
                %>
                <p class="text-muted"><small>La fecha del primer vencimiento cae en feriado o fin de semana: se corre al siguiente día hábil, <%= FormateadorFechas.comoDdMmAaaa(primeraCuota.getFechaVencimiento()) %>.</small></p>
                <% } %>

                <% if (comparacionPlazos != null && !comparacionPlazos.isEmpty()) { %>
                <h5>Comparar plazos</h5>
                <div class="table-responsive">
                    <table class="table table-sm table-striped">
                        <thead><tr><th>Plazo</th><th class="cc-monto">Cuota (1.ª, con IVA)</th><th class="cc-monto">Total a pagar (con IVA)</th></tr></thead>
                        <tbody>
                        <% for (FilaComparacionPlazo filaPlazo : comparacionPlazos) { %>
                        <tr class="<%= filaPlazo.isActual() ? "table-success" : "" %>" style="cursor:pointer"
                                onclick="cotizarConPlazo(<%= filaPlazo.getPlazoMeses() %>)">
                            <td<%= filaPlazo.isActual() ? " style=\"font-weight:bold\"" : "" %>>
                                <%= filaPlazo.getPlazoMeses() %> meses<%= filaPlazo.isActual() ? " (actual)" : "" %>
                            </td>
                            <td class="cc-monto"<%= filaPlazo.isActual() ? " style=\"font-weight:bold\"" : "" %>>
                                <%= FormateadorNumeros.comoGuaranies(filaPlazo.getCuotaConIva()) %>
                            </td>
                            <td class="cc-monto"<%= filaPlazo.isActual() ? " style=\"font-weight:bold\"" : "" %>>
                                <%= FormateadorNumeros.comoGuaranies(filaPlazo.getTotalConIva()) %>
                            </td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>
                <p class="text-muted"><small>A más meses, la cuota es menor pero el total a pagar es mayor.</small></p>
                <% } %>

                <h5>Plan de pagos</h5>
                <%-- B143 (pedido de Joel, 11/11/2026, despues de probarlo en pantalla): el plan
                     arranca CERRADO; lo despliega este boton. "Comparar plazos" queda visible.
                     El PDF de "Imprimir" NO cambia: lo arma el servidor en POST /cotizador/informe
                     con ServicioInformes + cotizacion.jrxml, sin leer nada de esta pantalla.
                     Los dos rotulos del boton los escribe la JSP en sus data- y el JavaScript solo
                     ELIGE uno de los dos: nunca redacta (B92/B106). El singular/plural sale del
                     tamano del plan y no de un "cuotas" fijo: hoy el plazo minimo es 3, pero es un
                     parametro (credito.plazo_minimo) y un "1 cuotas" seria el mismo defecto de
                     B103/B117. --%>
                <%
                    int cantidadCuotasPlan = resultadoCotizacion.getPlan().size();
                    String rotuloVerPlan = "Ver plan de pagos (" + cantidadCuotasPlan
                            + (cantidadCuotasPlan == 1 ? " cuota)" : " cuotas)");
                %>
                <button type="button" class="btn btn-outline-secondary cc-cotizador-ver-plan"
                        id="botonVerPlan" aria-expanded="false" aria-controls="planDePagos"
                        data-texto-ver="<%= rotuloVerPlan %>"
                        data-texto-ocultar="Ocultar el plan de pagos">
                    <i class="fas fa-chevron-down"></i> <span id="textoVerPlan"><%= rotuloVerPlan %></span>
                </button>
                <%-- .tabla-informe-ancha (B128) porque esta tabla de 7 columnas ya no tiene el
                     ancho entero de la pantalla sino la columna derecha. Medido a 1280x800 con el
                     peor caso (15.000.000 Gs. a 24 meses, contenedor de 589 px): sin la clase la
                     tabla pide 597 px y 93 de 168 celdas parten el monto en dos renglones; con la
                     clase pide 516 y no parte ninguna. --%>
                <div class="table-responsive" id="planDePagos" hidden>
                    <table class="table table-sm table-striped tabla-informe-ancha">
                        <thead><tr><th>Cuota</th><th>Vencimiento</th>
                            <th class="cc-monto">Capital <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                    data-glosario-titulo="<%= GlosarioFinanciero.CAPITAL.getTitulo() %>"
                                    data-glosario-texto="<%= GlosarioFinanciero.CAPITAL.completo() %>"></i></th>
                            <th class="cc-monto">Interés <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                    data-glosario-titulo="<%= GlosarioFinanciero.INTERES.getTitulo() %>"
                                    data-glosario-texto="<%= GlosarioFinanciero.INTERES.completo() %>"></i></th>
                            <th class="cc-monto">IVA <i class="fas fa-info-circle text-muted" style="cursor:pointer"
                                    data-glosario-titulo="<%= GlosarioFinanciero.IVA.getTitulo() %>"
                                    data-glosario-texto="<%= GlosarioFinanciero.IVA.completo() %>"></i></th>
                            <th class="cc-monto">Total</th><th class="cc-monto">Saldo</th></tr></thead>
                        <tbody>
                        <% for (Cuota cuota : resultadoCotizacion.getPlan()) { %>
                        <tr>
                            <td><%= cuota.getNumeroCuota() %></td>
                            <td><%= FormateadorFechas.comoDdMmAaaa(cuota.getFechaVencimiento()) %></td>
                            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getCapitalProgramado()) %></td>
                            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getInteresProgramado()) %></td>
                            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getTributosProgramados()) %></td>
                            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getTotalProgramado()) %></td>
                            <td class="cc-monto"><%= FormateadorNumeros.comoGuaranies(cuota.getSaldoCapitalPosterior()) %></td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>
                <%-- B143: los dos botones de la cotizacion van en su propio renglon. Con el plan
                     plegado, "Ver plan de pagos" quedaba en la misma linea que estos dos y se leia
                     como un tercer boton del mismo grupo, cuando en realidad despliega la seccion
                     de arriba. --%>
                <div class="cc-cotizador-acciones">
                    <button type="button" class="btn btn-outline-secondary" id="botonNuevaCotizacion">
                        <i class="fas fa-plus"></i> Nueva cotización
                    </button>
                    <button type="submit" class="btn btn-primary" form="formularioCotizador"
                            formaction="${pageContext.request.contextPath}/cotizador/informe" formmethod="post" formtarget="_blank">
                        <i class="fas fa-print"></i> Imprimir
                    </button>
                </div>
            </div>
        </div>
<% } %>
    </div>
</div>

<% if (resultadoCotizacion != null) { %>
<script>
    // RF01-M (pedido de Joel): "Nueva cotización" no limpia los campos (para eso está
    // "Limpiar", que navega a un formulario vacío) -- sube hasta el campo Monto y lo enfoca,
    // para que el usuario sobreescriba y cotice de nuevo sin desplazarse a mano.
    document.getElementById('botonNuevaCotizacion').addEventListener('click', function () {
        var campoMonto = document.getElementById('campoMonto');
        if (campoMonto) {
            campoMonto.scrollIntoView({behavior: 'smooth', block: 'center'});
            campoMonto.focus();
        }
    });

    // Pedido de Joel: después de cotizar con éxito, bajar solo hasta "Resultado" -- este bloque
    // completo (y por lo tanto este script) solo se renderiza cuando resultadoCotizacion != null,
    // así que nunca corre si hubo error (la página se queda arriba, como antes).
    document.getElementById('resultadoCotizacion').scrollIntoView({behavior: 'smooth', block: 'start'});

    // B143 (pedido de Joel): el plan de pagos arranca cerrado y este botón lo despliega. El JS
    // no redacta: los dos rótulos los escribió la JSP en data-texto-ver/data-texto-ocultar y acá
    // solo se elige uno (B92/B106). Se leen con getAttribute y no con .data() de jQuery, que
    // cachea el valor en la primera lectura (B4/B43). El estado vive en el atributo hidden y en
    // aria-expanded, que es lo que además hace girar la flecha por CSS.
    (function () {
        var botonVerPlan = document.getElementById('botonVerPlan');
        var planDePagos = document.getElementById('planDePagos');
        var textoVerPlan = document.getElementById('textoVerPlan');
        botonVerPlan.addEventListener('click', function () {
            var seAbre = planDePagos.hidden;
            planDePagos.hidden = !seAbre;
            botonVerPlan.setAttribute('aria-expanded', seAbre ? 'true' : 'false');
            textoVerPlan.textContent = botonVerPlan.getAttribute(
                    seAbre ? 'data-texto-ocultar' : 'data-texto-ver');
        });
    })();

    // "Comparar plazos" (pedido de Joel): clic en una fila recotiza con ese plazo -- mismo monto,
    // fecha, nombre y C.I. ya están en el formulario principal; solo se cambia el plazo y se
    // reenvía (mismo POST /cotizador de siempre, mismo ServicioCotizacion.cotizar). Al volver a
    // renderizar con el nuevo resultado, el scroll de arriba baja de nuevo hasta "Resultado".
    function cotizarConPlazo(meses) {
        document.getElementById('campoPlazo').value = meses;
        document.getElementById('formularioCotizador').submit();
    }
</script>
<% } %>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
