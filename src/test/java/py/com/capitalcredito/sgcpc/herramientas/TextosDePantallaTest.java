package py.com.capitalcredito.sgcpc.herramientas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import py.com.capitalcredito.sgcpc.servicios.ServicioDocumentos;
import py.com.capitalcredito.sgcpc.modelos.PrestamoCredito;
import py.com.capitalcredito.sgcpc.servicios.ServicioFormalizacion;
import py.com.capitalcredito.sgcpc.servicios.ServicioReversiones;

/**
 * Chequeos sobre los TEXTOS que ve el usuario, leidos del codigo fuente -- no hay forma de
 * probarlos con JUnit de otra manera: los avisos de cobranza-ficha.js los redacta el navegador.
 *
 * <p>Existe por un defecto real encontrado por Joel en pantalla el 30/10/2026: el aviso decia
 * "El minimo para adelantar hoy es 1.880 Gs.." con DOS puntos, porque el formateador de montos ya
 * termina en "Gs." y la frase le agregaba otro "." (es la misma D-145 punto 8, que ya habia pasado
 * en otro aviso el 27/10/2026 y quedo anotada en un comentario... que no impidio repetirla). Un
 * comentario no frena un error; un test si.</p>
 */
public class TextosDePantallaTest {

    private static final File CARPETA_MAIN = new File("src/main");

    /**
     * B178 (14/11/2026): los argumentos del formateador, tolerando UN nivel de parentesis
     * adentro. Antes era {@code \([^()]*\)}, que no matchea la forma mas comun en una pantalla
     * --{@code comoGuaranies(prestamo.getMontoAprobado())}, con una llamada adentro-- asi que la
     * red solo veia los casos con una variable pelada. Un nivel alcanza para todo lo que hay hoy
     * en {@code src/main}; verificado que con esto no aparece ningun caso preexistente (0 antes,
     * 0 despues), o sea que es endurecer la red, no destapar deuda.
     */
    private static final String ARGUMENTOS = "\\((?:[^()]|\\([^()]*\\))*\\)";

    /**
     * El monto formateado seguido de + "." (un punto Y NADA MAS): eso es lo que duplica el punto.
     * No se prohibe cualquier punto despues de un monto: ")." y "Gs.)" se usan y estan bien, y
     * ServicioCotizacion:42-44 pega a proposito ". Ajuste el monto..." porque pie.jsp parte titulo
     * y detalle en el primer ". " (D-86 revisado + RF05). Lo que nunca va es el punto solo.
     */
    private static final Pattern PUNTO_PEGADO_AL_MONTO = Pattern.compile(
            "(formatearGs|comoGuaranies)" + ARGUMENTOS + "\\s*\\+\\s*(['\"])\\.\\2");

    /**
     * B178 (14/11/2026): la TERCERA forma de escribir el mismo defecto, y la que se escapo. Una
     * expresion de JSP que termina en el formateador, con el punto puesto en el MARCADO:
     * {@code <%= FormateadorNumeros.comoGuaranies(x) %>.} -- en pantalla sale "2.000.000 Gs..".
     *
     * <p>Las otras dos redes de esta clase no pueden verlo: {@code PUNTO_PEGADO_AL_MONTO} busca
     * una concatenacion de Java/JS ({@code + "."}) y el punto no esta en ningun literal, y la
     * busqueda de {@code "Gs.."} tampoco, porque en el fuente nunca aparecen los dos puntos
     * juntos: se juntan recien al renderizar. Por eso el defecto llego a la pantalla de Joel con
     * {@code mvn test} en 997/997.</p>
     */
    private static final Pattern PUNTO_PEGADO_AL_MONTO_EN_JSP = Pattern.compile(
            "(formatearGs|comoGuaranies)" + ARGUMENTOS + "\\s*%>\\s*\\.");

    /**
     * B192 (15/11/2026): la CUARTA forma del mismo defecto, y la que se escapo de las otras tres.
     * El monto se formatea en JAVA --un servicio le pasa
     * {@code FormateadorNumeros.comoGuaranies(...)} a un DTO, que lo guarda como String-- y la JSP
     * solo imprime el getter: {@code <%= fila.getMontoAplicado() %>.} sale "50.000 Gs..".
     *
     * <p>Las tres redes anteriores son ciegas acá, cada una por su motivo: la de la concatenacion y
     * la de la expresion de JSP buscan el NOMBRE del formateador, que en este caso no aparece en la
     * JSP sino en {@code ServicioCobranza}; y la de {@code "Gs.."} no ve dos puntos juntos en el
     * fuente, porque se juntan al renderizar.</p>
     *
     * <p><b>Como lo reconoce, y cual es su limite</b>: no puede saber que guarda un DTO, asi que
     * mira el NOMBRE del getter. Exige que la expresion <b>termine</b> en una llamada
     * {@code .getXxx()} --asi un {@code getCantidadTotal() == 1 ? "" : "s"}, que es un contador y
     * no un monto, no entra-- y que ese nombre contenga una palabra de dinero. Medido sobre
     * {@code src/main} el 15/11/2026: de las <b>46</b> expresiones seguidas de punto que hay en las
     * JSP, esta red marca <b>1</b>, la de B192, y ninguna de las 45 legitimas (fechas, cedulas,
     * contadores, nombres y listas que no terminan en punto).</p>
     *
     * <p>Si alguna vez salta sobre un getter que devuelve un {@code BigDecimal} sin formatear, la
     * respuesta correcta casi siempre es <b>formatearlo</b>, no sacarlo de la lista: un monto crudo
     * en una pantalla ya es un defecto aparte.</p>
     */
    /**
     * B215: una asignacion cuyo lado derecho llama a uno de los dos formateadores de montos. El
     * {@code [^=;]*} no cruza ni un punto y coma ni otro {@code =}, asi que no confunde una
     * comparacion ({@code a == comoGuaranies(x)}) con una asignacion.
     */
    private static final Pattern ASIGNACION_DESDE_FORMATEADOR_DE_MONTO = Pattern.compile(
            "(\\w+)\\s*=[^=;]*(?:comoGuaranies|formatearGs)\\s*\\(");

    private static final Pattern PUNTO_PEGADO_A_UN_GETTER_DE_DINERO = Pattern.compile(
            "<%=[^%]*\\.get\\w*(Monto|Importe|Saldo|Cuota|Vuelto|Efectivo|Interes|Capital|Mora|Deuda)\\w*"
                    + "\\(\\)\\s*%>\\s*\\.");

    /**
     * B116: el boton de descartar de un cartel diciendo "Cancelar" o "No" a secas. Literales
     * exactos, con la comilla de cierre, para que "No, volver" NO matchee y para no tocar los dos
     * textos deliberados ("Cerrar" y "Salir") -- ver el test de abajo. Comprobado el 05/11/2026
     * que no hay falsos positivos en lo vendorizado de {@code recursos/}: SweetAlert2 declara sus
     * defaults en ingles.
     */
    private static final Pattern CANCELAR_A_SECAS = Pattern.compile(
            "cancelButtonText\\s*:\\s*(['\"])(Cancelar|No)\\1");

    /**
     * Los dos formateadores de montos del sistema, el del navegador (formatearGs) y el del
     * servidor (FormateadorNumeros.comoGuaranies), devuelven el monto terminado en "Gs.".
     * Pegarle otro "." al resultado duplica el punto, en cualquier capa.
     */
    @Test
    public void ningunTextoLeAgregaUnPuntoAlMontoYaFormateado() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".java", ".jsp", ".js")) {
            Matcher coincidencia = PUNTO_PEGADO_AL_MONTO.matcher(sinComentarios(leer(archivo)));
            while (coincidencia.find()) {
                hallazgos.add(archivo.getPath() + ": " + coincidencia.group());
            }
        }
        assertTrue("El monto formateado ya termina en \"Gs.\": agregarle otro \".\" duplica el punto"
                + " (D-145 punto 8). Corregirlo en el texto, nunca en el formateador:\n" + hallazgos,
                hallazgos.isEmpty());
    }

    /**
     * B178 (14/11/2026, defecto encontrado por Joel en pantalla): el mismo punto duplicado, pero
     * con el punto en el MARCADO de la JSP en vez de en una concatenacion. Nacio sobre un defecto
     * real --{@code formalizacion/ficha.jsp:432}, el monto del cartel de anular, que en pantalla
     * decia "2.000.000 Gs.."-- y se verifico que lo encuentra ANTES de corregirlo.
     *
     * <p>Solucion del texto, igual que siempre: se saca el punto, nunca se toca el formateador.
     * Si alguna vez hace falta cerrar la oracion despues de un monto, se cierra con otra cosa
     * (una coma, un ")", o se reordena la frase para que el monto no quede al final).</p>
     */
    @Test
    public void ningunaJspLePegaUnPuntoAlMontoYaFormateado() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".jsp")) {
            Matcher coincidencia = PUNTO_PEGADO_AL_MONTO_EN_JSP.matcher(sinComentarios(leer(archivo)));
            while (coincidencia.find()) {
                hallazgos.add(archivo.getPath() + ": " + coincidencia.group());
            }
        }
        assertTrue("Una expresion de JSP que termina en el formateador NO puede llevar un \".\""
                + " pegado despues del \"%>\": el monto ya termina en \"Gs.\" y en pantalla salen dos"
                + " puntos (B178/D-145 punto 8):\n" + hallazgos, hallazgos.isEmpty());
    }

    /**
     * B192 (15/11/2026, defecto que Joel vio en pantalla): el monto formateado <b>en Java</b> y
     * guardado en un DTO, impreso por la JSP con un punto pegado -- "50.000 Gs..". Nacio sobre un
     * defecto real ({@code cobranza/ficha.jsp:221}, el cuerpo de la ventana de reversion) y se
     * verifico que lo encuentra ANTES de corregirlo.
     *
     * <p>La salida es la de siempre: se saca el punto. Si la oracion necesita cerrar despues del
     * monto, se reordena la frase para que el monto no quede al final.</p>
     */
    @Test
    public void ningunaJspLePegaUnPuntoAUnGetterDeDinero() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".jsp")) {
            Matcher coincidencia = PUNTO_PEGADO_A_UN_GETTER_DE_DINERO.matcher(sinComentarios(leer(archivo)));
            while (coincidencia.find()) {
                hallazgos.add(archivo.getPath() + ": " + coincidencia.group());
            }
        }
        assertTrue("Un getter de dinero devuelve el monto YA formateado (termina en \"Gs.\"), asi que"
                + " no puede llevar un \".\" pegado despues del \"%>\" -- en pantalla salen dos puntos"
                + " (B192/D-145 punto 8). Saque el punto o reordene la frase:\n" + hallazgos,
                hallazgos.isEmpty());
    }

    /**
     * B215 (15/11/2026, defecto que Joel vio en pantalla al probar la tanda 1 de Seguridad): la
     * QUINTA forma del punto duplicado, y la que se escapo de las otras cuatro. El monto se
     * formatea y se guarda en una <b>variable local</b> --{@code valorMostrado =
     * FormateadorNumeros.comoGuaranies(...)} en {@code parametros/administracion.jsp}-- y mas
     * abajo se imprime con un punto pegado: en pantalla salia <b>"Actual: 500.000.000 Gs.."</b>.
     *
     * <p><b>Por que no la veian las otras cuatro</b>: la red de B178 pide que la expresion
     * <i>termine</i> en la llamada al formateador, y aca la llamada esta veinte lineas mas arriba;
     * la de B192 mira el nombre de un <i>getter</i>, y aca no hay getter sino una variable; y la de
     * B79 busca una concatenacion, que tampoco es el caso. El nombre de la variable, ademas, no
     * dice nada de dinero: la misma {@code valorMostrado} lleva a veces un porcentaje y a veces una
     * fecha, y <b>por eso</b> el defecto solo aparece en una de sus ramas.</p>
     *
     * <p><b>Como lo reconoce</b>: en vez del nombre, sigue el <b>rastro</b>. Primero junta, por
     * archivo, las variables que ese mismo archivo asigna desde {@code comoGuaranies}/
     * {@code formatearGs}; despues busca esas variables impresas con un punto pegado
     * ({@code <%= variable %>.} en una JSP, {@code variable + "."} en Java o JS). Medido sobre
     * {@code src/main} el 15/11/2026: marca <b>1</b>, el defecto de B215, y ningun otro de los
     * usos legitimos de esas variables.</p>
     *
     * <p><b>El arreglo correcto no es sacar el punto</b>, a diferencia de las otras cuatro: aca la
     * frase <i>tiene</i> que cerrar, porque al lado se imprime otra oracion (la regla del
     * parametro). Se arma con {@code FormateadorTexto.conPuntoFinal}, que pone el punto solo si
     * falta -- el mismo dueno que B188 creo para el motivo que escribe una persona.</p>
     */
    @Test
    public void ningunaVariableConMontoFormateadoSeImprimeConUnPuntoPegado() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".java", ".jsp", ".js")) {
            String contenido = sinComentarios(leer(archivo));
            for (String variable : variablesConMontoFormateado(contenido)) {
                for (Pattern forma : formasDePuntoPegado(variable)) {
                    Matcher coincidencia = forma.matcher(contenido);
                    while (coincidencia.find()) {
                        hallazgos.add(archivo.getPath() + ": " + coincidencia.group().trim());
                    }
                }
            }
        }
        assertTrue("Una variable que guarda un monto ya formateado termina en \"Gs.\": imprimirla con"
                + " un \".\" pegado duplica el punto (B215/D-145 punto 8). Si la frase tiene que cerrar,"
                + " armarla con FormateadorTexto.conPuntoFinal en vez de pegar el punto a mano:\n"
                + hallazgos, hallazgos.isEmpty());
    }

    /** Las variables que ESTE archivo asigna desde uno de los dos formateadores de montos. */
    private static List<String> variablesConMontoFormateado(String contenido) {
        List<String> variables = new ArrayList<>();
        Matcher coincidencia = ASIGNACION_DESDE_FORMATEADOR_DE_MONTO.matcher(contenido);
        while (coincidencia.find()) {
            String variable = coincidencia.group(1);
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
        return variables;
    }

    /** El punto pegado, en las dos capas donde se puede escribir: el marcado de una JSP y una
     * concatenacion de Java o JS. */
    private static List<Pattern> formasDePuntoPegado(String variable) {
        String nombre = Pattern.quote(variable);
        return java.util.Arrays.asList(
                Pattern.compile("<%=\\s*" + nombre + "\\s*%>\\s*\\."),
                Pattern.compile("\\b" + nombre + "\\s*\\+\\s*(['\"])\\.\\1"));
    }

    /**
     * El mismo defecto escrito a mano: un literal con "Gs.." adentro. Vale para .java, .jsp y .js
     * por igual -- el usuario lee el resultado, no la capa.
     */
    @Test
    public void ningunTextoDelSistemaContieneUnMontoConDosPuntos() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".java", ".jsp", ".js")) {
            if (sinComentarios(leer(archivo)).contains("Gs..")) {
                hallazgos.add(archivo.getPath());
            }
        }
        assertTrue("Hay textos con \"Gs..\" (punto duplicado) en: " + hallazgos, hallazgos.isEmpty());
    }

    /**
     * B96 (30/10/2026): el cartel de confirmacion del pago parcial redactaba por su cuenta
     * ("Pago parcial: X" / "Queda pendiente: Y"), una tercera version de lo que el resumen ya
     * dice con dueno unico en ServicioCobranza. El JS no vuelve a redactar ese hecho: recibe las
     * frases en un data- y solo reemplaza numeros (regla de B92).
     */
    @Test
    public void elJavaScriptDeCobranzaNoRedactaElPagoParcial() throws IOException {
        String contenido = sinComentarios(leer(new File("src/main/webapp/recursos/js/cobranza-ficha.js")));
        for (String prohibido : new String[] {"Pago parcial: ", "Queda pendiente: ",
                // B98 (30/10/2026): los dos textos de D-146 (P-17) tambien se mudaron al servicio,
                // asi que el JS no puede volver a armarlos -- menos todavia en la rama de la cuota
                // que vence hoy, donde decir "lo vencido" o "lo que sobra" es falso.
                "Se cobra lo vencido", "Lo que sobra",
                // B98 (segunda prueba de Joel, 30/10/2026): este literal en el JS pisaba el primer
                // render de la JSP y la linea grande volvia a decir "Pagar lo vencido" con una
                // cuota que vence HOY. El rotulo lo decide ServicioCobranza.etiquetaOpcionCobro y
                // viaja en un data-: el JS no puede volver a escribirlo.
                "Pagar lo vencido",
                // B106 (05/11/2026): los dos ultimos rotulos que este archivo redactaba por su
                // cuenta. "Todo el préstamo" estaba duplicado con el boton de su opcion en la JSP
                // (seis copias en total, contando los dos mensajes del servidor) y "Adelantar a
                // capital" no existia en ninguna parte del servidor. Ahora los dos son constantes
                // de ServicioCobranza y viajan en data-opcion-todo / data-opcion-anticipo.
                "Todo el préstamo", "Adelantar a capital",
                // B106 (ampliacion aprobada por Joel el 05/11/2026): y los TRES textos del boton
                // del cartel, que eran los ultimos que este archivo redactaba. Ahora viajan en
                // data-boton-vencido / data-boton-todo / data-boton-anticipo.
                // Lo unico que queda escrito en el JS de este cartel es el respaldo de B98
                // ("Si, cobrar la cuota de hoy", linea del data-boton-cuota-de-hoy): es un
                // fallback, su dueno ya existe (BOTON_COBRAR_CUOTA_DE_HOY), y por eso no entra.
                "Sí, cobrar lo vencido", "Sí, cobrar todo el préstamo", "Sí, adelantar a capital",
                // B115 (05/11/2026, redaccion elegida por Joel): el aviso de "no alcanza para N
                // cuotas" estaba escrito acá Y en el servidor, y las dos redacciones ya habian
                // divergido. Ahora lo arma ServicioCobranza y viaja en data-falta-cuotas-singular
                // / -plural. Se prohibe el final, que es lo unico exclusivo de esta frase:
                // "Lo recibido (" lo comparte con el aviso de la cancelacion total (otro texto).
                "Elija menos cuotas"}) {
            assertTrue("cobranza-ficha.js volvio a redactar un texto que tiene dueno en el"
                    + " servidor: \"" + prohibido
                    + "\" -- esas frases las arma ServicioCobranza y viajan en un data-",
                    !contenido.contains(prohibido));
        }
    }

    /**
     * B102 (01/11/2026): las tres etiquetas de concepto gravado de la factura viven en UN solo
     * lugar (ServicioCobranza.ConceptoGravado). Antes estaban escritas en los tres armadores de
     * lineas -- el de emision, el de impresion de una cuota y el de impresion de un anticipo -- y
     * el de emision ya decia algo distinto del que se imprime.
     */
    @Test
    public void cadaConceptoGravadoDeLaFacturaEstaEscritoUnaSolaVez() throws IOException {
        for (String etiqueta : new String[] {"Interés compensatorio", "Interés moratorio",
                "Interés punitorio"}) {
            int veces = 0;
            for (File archivo : archivosCon(CARPETA_MAIN, ".java", ".jsp", ".js")) {
                String contenido = sinComentarios(leer(archivo));
                int desde = 0;
                while (true) {
                    int i = contenido.indexOf("\"" + etiqueta, desde);
                    if (i < 0) {
                        break;
                    }
                    veces++;
                    desde = i + etiqueta.length();
                }
            }
            assertTrue("la etiqueta \"" + etiqueta + "\" aparece " + veces + " veces en src/main;"
                    + " tiene que estar una sola (ServicioCobranza.ConceptoGravado)", veces == 1);
        }
    }

    /**
     * B116 (05/11/2026, hallazgo de Joel al probar B114): ningun cartel de confirmacion vuelve a
     * decir "Cancelar" ni "No" a secas en el boton de descartar. El default de los 24
     * {@code data-confirmar} ya es "No, volver" desde B3/B31 (commit f585f28, en
     * {@code plantilla/pie.jsp}), pero los {@code Swal.fire} escritos a mano son la OTRA familia de
     * carteles y no pasan por ahi, asi que B3 no los alcanzo: quedaron cinco diciendo "Cancelar" o
     * "No" -- uno de ellos el de cobranza, donde "Cancelar" ya significa "cancelacion total del
     * prestamo" (familia B3 en su forma textual).
     *
     * <p><b>Dos textos se dejan a proposito y por eso la guarda es de literales exactos, no de
     * "cualquier cosa que no sea 'No, volver'"</b>: {@code clientes/ficha.jsp} dice <i>"Cerrar"</i>
     * en el aviso de cedula ya registrada, que no es una confirmacion sino informativo (la accion
     * es "Ir a su ficha", asi que "No, volver" prometeria una vuelta que no existe), y
     * {@code aviso-sesion.js} dice <i>"Salir"</i> porque ahi el boton de descartar HACE algo
     * (cierra la sesion) -- llamarlo "No, volver" mentiria sobre lo que hace.</p>
     */
    @Test
    public void ningunCartelDeConfirmacionDiceCancelarNiNoAsecas() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (File archivo : archivosCon(CARPETA_MAIN, ".jsp", ".js")) {
            Matcher coincidencia = CANCELAR_A_SECAS.matcher(sinComentarios(leer(archivo)));
            while (coincidencia.find()) {
                hallazgos.add(archivo.getPath() + ": " + coincidencia.group());
            }
        }
        assertTrue("El boton de descartar de un cartel de confirmacion dice \"No, volver\" en todo"
                + " el sistema (B3/B31 para los data-confirmar, B116 para los Swal.fire propios)."
                + " Encontrado:\n" + hallazgos, hallazgos.isEmpty());
    }

    // ---------------------------------------------------------------------

    /**
     * Saca comentarios de linea, de bloque y de JSP antes de buscar. Hace falta porque este mismo
     * defecto ya se corrigio en tres pantallas (ServicioDesembolso, ServicioParametros,
     * caja/ficha.jsp) y en cada una quedo un comentario que CITA el texto malo ("Gs..") para
     * explicar por que no va: sin esto, los chequeos fallarian por las propias explicaciones.
     * No es un analizador de Java: si un literal contuviera "//" se recortaria de mas y el chequeo
     * podria no ver algo. Preferimos ese riesgo a un test que llora en falso.
     */
    private static String sinComentarios(String contenido) {
        return contenido
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?s)<%--.*?--%>", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static List<File> archivosCon(File carpeta, String... extensiones) {
        List<File> encontrados = new ArrayList<>();
        File[] hijos = carpeta.listFiles();
        if (hijos == null) {
            return encontrados;
        }
        for (File hijo : hijos) {
            if (hijo.isDirectory()) {
                encontrados.addAll(archivosCon(hijo, extensiones));
                continue;
            }
            for (String extension : extensiones) {
                if (hijo.getName().endsWith(extension)) {
                    encontrados.add(hijo);
                    break;
                }
            }
        }
        return encontrados;
    }

    private static String leer(File archivo) throws IOException {
        return new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * B42/B100/B104 (01/11/2026) y C30 (03/11/2026): los textos nuevos de estas tandas, pedidos a
     * sus dueños, terminan con punto.
     *
     * <p>Nació de un caso real: el rechazo de B42 salía en pantalla sin punto final. El punto
     * estaba en el código -- lo sacaba el pintor de carteles, que usa como TÍTULO cualquier
     * mensaje de una sola oración y a los títulos les quita el punto
     * ({@code pie.jsp}, {@code dividirEnTituloYDetalle}). De ahí la segunda comprobación: un
     * mensaje que el usuario ve en un cartel tiene que tener <b>dos</b> oraciones (título y
     * detalle), si no se muestra sin su punto.</p>
     *
     * <p><b>Límite declarado</b>: comprueba los textos de esta tanda, no todos los del sistema --
     * se los pide a sus dueños, así que verifica lo que de verdad sale, no la fuente.</p>
     */
    @Test
    public void losTextosNuevosTerminanEnPuntoYSePartenEnTituloYDetalle() {
        PrestamoCredito prestamo = new PrestamoCredito();
        prestamo.setIdPrestamo(6L);
        prestamo.setIdentificadorOperacion("CC-2026-00006");
        ServicioFormalizacion servicioFormalizacion = new ServicioFormalizacion();

        for (String texto : new String[] {
            servicioFormalizacion.calcularPasoDeLaSolicitudAprobada(7L, null).getTexto(),
            servicioFormalizacion.calcularPasoDeLaSolicitudAprobada(7L, prestamo).getTexto(),
            ServicioDocumentos.mensajeDeArchivoYaCargado("Comprobante de domicilio"),
            // C30 (03/11/2026): los dos avisos de la reversión pendiente -- el de la cajera, que
            // viaja dentro del cartel, y la marca de la bandeja del gerente.
            ServicioReversiones.textoAvisoDeReversionPendiente("REC-2026-000014"),
            ServicioReversiones.textoAvisoOperacionPosterior(),
        }) {
            assertTrue("termina mal: " + texto, texto.endsWith("."));
            assertFalse("doble punto: " + texto, texto.endsWith(".."));
        }

        // El que se muestra en un cartel: tiene que partirse en título y detalle. (El aviso
        // amarillo de B42 se comprueba en ServicioDocumentosTest, que vive en su paquete.)
        assertTrue("se muestra entero como título y pierde el punto: " + ServicioDocumentos.mensajeDeArchivoYaCargado("Comprobante de domicilio"),
                seParteEnTituloYDetalle(ServicioDocumentos.mensajeDeArchivoYaCargado("Comprobante de domicilio")));
    }

    /**
     * El mismo criterio que usa el pintor de carteles para partir un mensaje: un punto, espacio y
     * mayúscula. Sin regex a propósito -- es una sola pasada y se lee igual de claro.
     */
    private static boolean seParteEnTituloYDetalle(String mensaje) {
        for (int i = 0; i + 2 < mensaje.length(); i++) {
            if (mensaje.charAt(i) == '.' && Character.isWhitespace(mensaje.charAt(i + 1))
                    && Character.isUpperCase(mensaje.charAt(i + 2))) {
                return true;
            }
        }
        return false;
    }

}
