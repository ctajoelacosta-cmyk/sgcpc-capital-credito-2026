<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/vistas/plantilla/cabecera.jsp" %>
<%
    String textoBusquedaMora = (String) request.getAttribute("textoBusqueda");
%>
<h1>Seguimiento de mora</h1>
<p class="text-muted">Préstamos en mora, con las promesas vencidas primero.</p>

<%-- Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): mismo
     mecanismo de "listado vivo" que Clientes/Cobranza (recursos/js/listado-vivo.js no se tocó
     en su contrato, solo se le agregó el evento "listado-vivo:actualizado" -- ver ese archivo). --%>
<div class="card">
    <div class="card-body">
        <form method="get" action="${pageContext.request.contextPath}/mora" id="formularioFiltrosMora"
              data-listado-vivo="resultadosMora">
            <div class="form-row justify-content-between align-items-end">
                <div class="form-group col-sm-8 col-md-6">
                    <label class="mb-0">N.º de operación, cédula o nombre del cliente</label>
                    <input type="text" name="texto" class="form-control"
                           value="<%= textoBusquedaMora != null ? textoBusquedaMora : "" %>">
                </div>
                <div class="col-auto">
                    <small class="text-muted listado-vivo-indicador d-none mr-2">Buscando…</small>
                    <a href="${pageContext.request.contextPath}/mora" class="btn btn-outline-secondary listado-vivo-limpiar"
                       data-listado-vivo-de="formularioFiltrosMora">
                        <i class="fas fa-eraser"></i> Limpiar</a>
                    <button type="submit" class="btn btn-primary">
                        <i class="fas fa-search"></i> Buscar</button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultadosMora">
    <%@ include file="/WEB-INF/vistas/mora/_resultados.jsp" %>
</div>
<%-- Punto 2 de la mejora chica de RF24 (pedido de Joel): aviso ANTES de enviar si hay fecha de
     promesa sin monto -- mismo criterio que ya valida el servidor (ServicioMora.registrarGestion),
     esto solo evita el viaje al servidor para verlo. Corrección de Joel (consolidación, ítem 9):
     nada de avisos nativos del navegador -- mismo cartel "Falta un dato" / botón "Entendido" que
     ya usa el resto del sistema (pie.jsp), con un texto propio en vez del genérico (que diría
     solo "Falta completar «Monto prometido».").
     Corrección de Joel (12/10/2026, reprueba en pantalla): la versión anterior delegaba el
     'submit' en document (fase de captura) -- no se disparó en la prueba real (el formulario
     llegó a enviarse igual). Cambiado al MISMO patrón que ya usa recursos/js/campo-guaranies.js
     para este tipo de control en esta misma página (clase .campo-guaranies): esperar
     DOMContentLoaded y atar el manejador DIRECTO a cada <form> de gestión (uno por fila), nunca
     por delegación -- más simple y ya probado en este archivo.
     Tanda de usabilidad (20/10/2026, pedido de Joel, RNF_revision.md §10c, paso 1): con el
     buscador AJAX, el fragmento de #resultadosMora (y sus formularios de gestión) se reemplaza
     en cada búsqueda -- la función de ató se puso aparte para poder llamarla de nuevo en el
     evento "listado-vivo:actualizado" que dispara listado-vivo.js después de cada reemplazo,
     NUNCA por delegación en document (la causa confirmada de que no se disparara en la prueba
     real de la corrección anterior, ver arriba). --%>
<script>
    function atarValidacionGestionMora() {
        var formularios = document.querySelectorAll('#resultadosMora .modal form');
        for (var i = 0; i < formularios.length; i++) {
            formularios[i].addEventListener('submit', function (evento) {
                var formulario = evento.target;
                var campoFecha = formulario.querySelector('input[name="fechaPromesa"]');
                var campoMonto = formulario.querySelector('input[name="montoPrometido"]');
                if (!campoFecha || !campoMonto) {
                    return;
                }
                if (campoFecha.value && !campoMonto.value.trim()) {
                    evento.preventDefault();
                    Swal.fire({
                        icon: 'warning',
                        title: 'Falta un dato',
                        text: 'Si indica una fecha de promesa, indique también el monto prometido.',
                        confirmButtonText: 'Entendido'
                    });
                }
            });
        }
    }
    document.addEventListener('DOMContentLoaded', atarValidacionGestionMora);
    document.getElementById('resultadosMora').addEventListener('listado-vivo:actualizado', atarValidacionGestionMora);
</script>
<%@ include file="/WEB-INF/vistas/plantilla/pie.jsp" %>
