<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate" %>
<%-- B169 (14/11/2026): los dos que hacían falta para las ayudas de los límites, con los mismos
     nombres que los declara el Cotizador (cotizador/formulario.jsp:5 y :9). FormateadorFechas no
     se agrega porque ya viene de cabecera.jsp. --%>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FormateadorNumeros" %>
<%@ page import="py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    // Bloque 7/E10 (D-132, pedido de Joel): paso ④ del expediente del cliente ("Solicitud").
    String cedulaClientePrellenadaNueva = (String) request.getAttribute("cedulaClientePrellenada");
    // Corrección (pedido de Joel, 10/10/2026): si el servidor rechaza la creación (ej. supera el
    // 40 % de afectación), esto es un forward (no un redirect) -- los parámetros del POST siguen
    // en el request, así que se pueden volver a mostrar en vez de dejar el formulario en blanco.
    String cedulaReintento = request.getParameter("cedulaCliente");
    String montoReintento = request.getParameter("monto");
    String plazoReintento = request.getParameter("plazoMeses");
    String destinoReintento = request.getParameter("destinoCredito");
    String fechaReintento = request.getParameter("primeraFechaVencimiento");
    // B169 (14/11/2026): los límites de la política que el controlador deja en el request,
    // leídos de los mismos parámetros que usa el Cotizador. Pueden venir en null si la lectura
    // falló: en ese caso no se dibuja la ayuda y el formulario queda como antes.
    java.math.BigDecimal montoMinimoNueva = (java.math.BigDecimal) request.getAttribute("montoMinimo");
    java.math.BigDecimal montoMaximoNueva = (java.math.BigDecimal) request.getAttribute("montoMaximo");
    Integer plazoMinimoNueva = (Integer) request.getAttribute("plazoMinimo");
    Integer plazoMaximoNueva = (Integer) request.getAttribute("plazoMaximo");
    ServicioFormalizacion.RangoFechas rangoFechasNueva =
            (ServicioFormalizacion.RangoFechas) request.getAttribute("rangoFechas");
%>
<h1>Nueva solicitud</h1>

<div class="card card-primary" style="max-width: 520px;">
    <div class="card-body">
        <p class="text-muted"><small>Los campos marcados con <strong>*</strong> son obligatorios.</small></p>
        <form method="post" action="${pageContext.request.contextPath}/solicitudes/nuevo">
            <%@ include file="/WEB-INF/vistas/plantilla/csrf.jsp" %>
            <div class="form-group">
                <label>Cédula del cliente *</label>
                <input type="text" name="cedulaCliente" id="campoCedulaNueva" class="form-control campo-cedula" required autofocus
                       value="<%= cedulaReintento != null ? cedulaReintento
                               : (cedulaClientePrellenadaNueva == null ? "" : cedulaClientePrellenadaNueva) %>">
                <small class="text-muted">El cliente debe estar registrado en "Clientes" antes de crear la solicitud.</small>
            </div>
            <div class="form-group">
                <label>Monto (Gs.) *</label>
                <input type="text" name="monto" id="campoMontoNueva" class="form-control campo-guaranies" required
                       value="<%= montoReintento == null ? "" : montoReintento %>">
                <%-- B169 (14/11/2026): los límites de la política, dichos ANTES de que el servidor
                     rechace. Copiado literal del Cotizador (cotizador/formulario.jsp:71-74), que ya
                     los mostraba, y leídos de los MISMOS parámetros: así las dos pantallas no
                     pueden decir cosas distintas. --%>
                <% if (montoMinimoNueva != null && montoMaximoNueva != null) { %>
                <small class="form-text text-muted">Entre <%= FormateadorNumeros.comoGuaranies(montoMinimoNueva) %>
                    y <%= FormateadorNumeros.comoGuaranies(montoMaximoNueva) %></small>
                <% } %>
            </div>
            <div class="form-group">
                <label>Plazo (meses) *</label>
                <%-- B164 (14/11/2026): inputmode="numeric" en los TRES campos de plazo del sistema
                     (acá, cotizador/formulario.jsp y solicitudes/ficha.jsp). Abre el teclado
                     numérico en la tablet y NO cambia nada visual, así que no reabre la prueba de
                     esas pantallas. NO se pasa a type="number" a propósito: metería los spinners
                     del navegador en pantallas ya aprobadas. --%>
                <input type="text" inputmode="numeric" name="plazoMeses" id="campoPlazoNueva" class="form-control" required
                       value="<%= plazoReintento == null ? "" : plazoReintento %>">
                <%-- B169: igual que el Cotizador (formulario.jsp:80-82). --%>
                <% if (plazoMinimoNueva != null && plazoMaximoNueva != null) { %>
                <small class="form-text text-muted">Entre <%= plazoMinimoNueva %> y <%= plazoMaximoNueva %> meses.</small>
                <% } %>
            </div>
            <div class="form-group">
                <label>Destino del crédito *</label>
                <input type="text" name="destinoCredito" class="form-control" required
                       value="<%= destinoReintento == null ? "" : destinoReintento %>">
            </div>
            <div class="form-group">
                <label>Fecha estimada del primer vencimiento *</label>
                <input type="date" name="primeraFechaVencimiento" id="campoFechaNueva" class="form-control"
                       value="<%= fechaReintento != null ? fechaReintento : LocalDate.now().plusMonths(1) %>"
                       min="<%= LocalDate.now() %>" required>
                <%-- B169 (Joel pidió que la fecha también entre, con el mismo rango del Cotizador):
                     el rango sale de ServicioFormalizacion.calcularRangoPrimerVencimiento, el MISMO
                     que valida el servidor al crear -- no una cuenta propia de la pantalla. El
                     "min" del input se queda como estaba. --%>
                <% if (rangoFechasNueva != null) { %>
                <small class="form-text text-muted">Entre <%= FormateadorFechas.comoDdMmAaaa(rangoFechasNueva.getMinima()) %>
                    y <%= FormateadorFechas.comoDdMmAaaa(rangoFechasNueva.getMaxima()) %>.</small>
                <% } %>
            </div>
            <%-- RF05 (pedido de Joel, 10/10/2026): afectación en vivo mientras se escribe, antes
                 de crear nada -- GET /solicitudes/previsualizar-afectacion, mismo criterio liviano
                 que precalificar (nunca bloquea nada por sí sola; el servidor vuelve a validar al
                 crear de verdad). --%>
            <div id="indicadorAfectacionNueva" class="mb-3"></div>
            <%-- B136 (11/11/2026, revisión final, solo el orden): el principal va a la DERECHA,
                 como en el cotizador, en los filtros de los listados y en los informes ("Limpiar"
                 y después "Cotizar"/"Buscar"/"Aplicar filtros"). Era la única pantalla del sistema
                 donde el botón principal estaba del otro lado. El ancho de la columna, que es la
                 otra mitad de B136, NO se toca. --%>
            <%-- B162 (14/11/2026): decía "Cancelar", y en ESTA pantalla esa palabra es una
                 operación del negocio -- "Cancelar solicitud" (RF06) es una acción real e
                 irreversible que vive en la ficha. El botón que solo sale sin guardar dice
                 "Volver", como en el resto del sistema. Es el caso que la guía marca como
                 ambigüedad real, no como gusto: cuando una palabra genérica coincide con un
                 término del negocio de la pantalla, hay que cambiarla. --%>
            <a href="${pageContext.request.contextPath}/solicitudes" class="btn btn-default">Volver</a>
            <button type="submit" class="btn btn-primary">Crear solicitud</button>
        </form>
    </div>
</div>
<script>
    (function () {
        var campoCedula = document.getElementById('campoCedulaNueva');
        var campoMonto = document.getElementById('campoMontoNueva');
        var campoPlazo = document.getElementById('campoPlazoNueva');
        var campoFecha = document.getElementById('campoFechaNueva');
        var indicador = document.getElementById('indicadorAfectacionNueva');
        var temporizador = null;
        // B161, ajuste del 14/11/2026 (Joel, probándolo en pantalla): el aviso rojo "No hay ningún
        // cliente registrado con esa cédula" salía mientras todavía se escribía -- con "910"
        // tipeado, la cédula no existe *todavía*, y el rojo es para lo que está mal, no para lo que
        // falta (B95). Ahora ese estado ("X") se muestra solo cuando el oficial SALE del campo
        // (blur) o al volver de un rechazo del servidor, que son los dos momentos en que la cédula
        // ya está completa. El nombre del cliente, cuando existe, sigue apareciendo en vivo.
        // Se elige el blur y no un mínimo de dígitos para no inventar un largo de cédula: en la
        // base conviven cédulas de 6, 7 y 8 dígitos.
        function actualizarIndicador(mostrarCedulaInexistente) {
            if (temporizador) { clearTimeout(temporizador); }
            temporizador = setTimeout(function () {
                var cedula = campoCedula.value.trim();
                var monto = campoMonto.value.trim();
                var plazo = campoPlazo.value.trim();
                var fecha = campoFecha.value.trim();
                // B161 (14/11/2026): alcanza con la CÉDULA para preguntar. Antes se exigían los
                // cuatro campos, así que la pantalla no decía para quién era la solicitud hasta
                // tener todo escrito -- y con una cédula que no existe se quedaba muda hasta el
                // POST. El servidor contesta igual con lo que pueda: quién es, que no existe, o
                // quién es + la afectación.
                if (!cedula) {
                    indicador.innerHTML = '';
                    return;
                }
                var parametros = 'cedulaCliente=' + encodeURIComponent(cedula)
                        + '&monto=' + encodeURIComponent(monto)
                        + '&plazoMeses=' + encodeURIComponent(plazo)
                        + '&primeraFechaVencimiento=' + encodeURIComponent(fecha);
                fetch('${pageContext.request.contextPath}/solicitudes/previsualizar-afectacion?' + parametros)
                        .then(function (respuesta) { return respuesta.text(); })
                        .then(function (texto) {
                            if (!texto) { indicador.innerHTML = ''; return; }
                            // B161: el primer segmento es el ESTADO y el segundo la frase ya
                            // armada por el servidor; acá solo se elige la clase. En rojo lo que
                            // está mal escrito ('X', la cédula sin cliente) y lo que no se puede
                            // mandar ('1', supera el límite); en gris lo que todavía falta ('C',
                            // solo el cliente) -- un pedido no es un error (B95).
                            var partes = texto.split('|');
                            var estado = partes[0];
                            if (estado === 'X' && !mostrarCedulaInexistente) {
                                // Todavía escribiendo: una cédula incompleta "no existe" por ahora.
                                indicador.innerHTML = '';
                                return;
                            }
                            var enRojo = estado === '1' || estado === 'X';
                            indicador.innerHTML = '<small class="'
                                    + (enRojo ? 'text-danger font-weight-bold' : 'text-muted') + '">'
                                    + partes[1] + '</small>';
                        })
                        .catch(function () { indicador.innerHTML = ''; });
            }, 400);
        }
        // B161: los listeners van ENVUELTOS a propósito. addEventListener le pasa el Event como
        // primer argumento, así que registrar "actualizarIndicador" pelado dejaría
        // mostrarCedulaInexistente con un objeto adentro -- siempre verdadero, y el aviso volvería
        // a salir mientras se escribe.
        [campoCedula, campoMonto, campoPlazo, campoFecha].forEach(function (campo) {
            campo.addEventListener('input', function () { actualizarIndicador(false); });
            campo.addEventListener('change', function () { actualizarIndicador(false); });
        });
        // Al salir del campo de la cédula ya está completa: ahí sí se dice si no existe.
        campoCedula.addEventListener('blur', function () { actualizarIndicador(true); });
        // Corrección (pedido de Joel, 10/10/2026): si el formulario vuelve con los datos del
        // intento anterior (rechazo del 40 %), mostrar el indicador ya actualizado, sin esperar a
        // que el oficial toque algo.
        // B161: acá va "true" porque la cédula que se repuebla ya está completa -- viene de un
        // submit o del paso ④ de la ficha del cliente, no de alguien tipeando.
        actualizarIndicador(true);
    })();
</script>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
