package py.com.capitalcredito.sgcpc.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import py.com.capitalcredito.sgcpc.dto.FiltroClientes;

/**
 * B157 (14/11/2026): la lista blanca del orden del listado de clientes. Es la guarda que impide
 * que algo del navegador entre en el SQL, así que se prueba contra el caso malo y no solo contra
 * los cuatro buenos -- un control de ausencia que nunca vio el valor prohibido no es un control
 * (ver la skill sgcpc-cambio-seguro).
 *
 * <p>Vive en el paquete {@code dao} porque {@code construirOrderBy} es de visibilidad de paquete:
 * el test se mueve al paquete del método, nunca se amplía la visibilidad del método para que un
 * test de afuera lo alcance.</p>
 *
 * <p><b>Lo que este test NO prueba</b>: que la consulta corra. Eso lo cubren el humo y la prueba
 * en pantalla de Joel (DOGMA 1); acá se verifica el fragmento de SQL que se arma.</p>
 */
public class PersonaDAOOrdenTest {

    private FiltroClientes filtroCon(String orden, String sentido) {
        FiltroClientes filtro = new FiltroClientes();
        filtro.setOrden(orden);
        filtro.setSentido(sentido);
        return filtro;
    }

    // ---- Los cuatro órdenes permitidos, que son las columnas que viven en "personas" ----

    @Test
    public void testOrdenPorDefectoEsApellidosYNombresAscendente() {
        assertEquals("ORDER BY apellidos, nombres ASC", PersonaDAO.construirOrderBy(filtroCon(null, null)));
    }

    @Test
    public void testOrdenPorCedula() {
        assertEquals("ORDER BY cedula_identidad ASC, apellidos ASC, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("ci", "asc")));
    }

    @Test
    public void testOrdenPorTelefonoDescendente() {
        assertEquals("ORDER BY telefono_whatsapp DESC, apellidos ASC, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("telefono", "desc")));
    }

    @Test
    public void testOrdenPorEstado() {
        assertEquals("ORDER BY estado_persona ASC, apellidos ASC, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("estado", "asc")));
    }

    @Test
    public void testOrdenPorNombreDescendenteNoAgregaDesempate() {
        // Ordenando ya por apellidos/nombres, repetirlos como desempate sería ruido.
        assertEquals("ORDER BY apellidos, nombres DESC", PersonaDAO.construirOrderBy(filtroCon("nombre", "desc")));
    }

    // ---- El desempate: sin él, con LIMIT/OFFSET se ve una fila repetida al pasar de página ----

    @Test
    public void testTodoOrdenQueNoEsElDeDefectoDesempataPorApellidoYNombre() {
        for (String clave : new String[] {"ci", "telefono", "estado"}) {
            assertTrue("falta el desempate en " + clave,
                    PersonaDAO.construirOrderBy(filtroCon(clave, "asc")).endsWith(", apellidos ASC, nombres ASC"));
        }
    }

    // ---- La guarda: nada que venga del navegador puede llegar al SQL ----

    @Test
    public void testUnaClaveDesconocidaCaeEnElOrdenPorDefecto() {
        assertEquals("ORDER BY apellidos, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("fecha_registro", "asc")));
    }

    @Test
    public void testUnaClaveConSqlNoEntraEnLaConsulta() {
        String inyeccion = "cedula_identidad; DROP TABLE personas";
        String orderBy = PersonaDAO.construirOrderBy(filtroCon(inyeccion, "asc"));
        assertEquals("ORDER BY apellidos, nombres ASC", orderBy);
        assertFalse("el texto del pedido no puede aparecer en el SQL", orderBy.contains("DROP"));
        assertFalse(orderBy.contains(";"));
    }

    @Test
    public void testUnSentidoInventadoEsAscendente() {
        // Cualquier cosa que no sea exactamente "desc" (sin importar mayúsculas) es ascendente.
        assertEquals("ORDER BY cedula_identidad ASC, apellidos ASC, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("ci", "descendente; --")));
        assertEquals("ORDER BY cedula_identidad DESC, apellidos ASC, nombres ASC",
                PersonaDAO.construirOrderBy(filtroCon("ci", "DESC")));
    }

    @Test
    public void testFiltroNuloNoRompe() {
        assertEquals("ORDER BY apellidos, nombres ASC", PersonaDAO.construirOrderBy(null));
    }

    // ---- esOrdenPermitido, que es lo que usa el controlador antes de guardar la clave ----

    @Test
    public void testEsOrdenPermitidoSoloAceptaLasCuatroClaves() {
        for (String clave : new String[] {"ci", "nombre", "telefono", "estado"}) {
            assertTrue(clave + " tendría que estar permitida", PersonaDAO.esOrdenPermitido(clave));
        }
        assertFalse(PersonaDAO.esOrdenPermitido(null));
        assertFalse(PersonaDAO.esOrdenPermitido(""));
        assertFalse(PersonaDAO.esOrdenPermitido("ciudad"));
        assertFalse(PersonaDAO.esOrdenPermitido("situacionLaboral"));
        assertFalse(PersonaDAO.esOrdenPermitido("prestamosActivos"));
        assertFalse(PersonaDAO.esOrdenPermitido("CI"));
    }

    /**
     * Las tres columnas que la pantalla NO ofrece ordenar (decisión de Joel, 14/11/2026): no salen
     * de esta consulta, las resuelve el controlador con mapas aparte. Si alguna vez se las quiere
     * ordenar, hay que agregar JOINs y este test es el que avisa que la decisión cambió.
     */
    @Test
    public void testCiudadSituacionLaboralYPrestamosActivosNoSeOrdenan() {
        assertFalse(PersonaDAO.esOrdenPermitido("ciudad"));
        assertFalse(PersonaDAO.esOrdenPermitido("situacion"));
        assertFalse(PersonaDAO.esOrdenPermitido("prestamos"));
    }
}
