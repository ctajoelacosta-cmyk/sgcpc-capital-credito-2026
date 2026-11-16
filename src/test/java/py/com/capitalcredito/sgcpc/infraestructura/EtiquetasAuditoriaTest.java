package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class EtiquetasAuditoriaTest {

    @Test
    public void comoEtiquetaLegibleDevuelveElNombreMapeado() {
        assertEquals("Aprobar solicitud", EtiquetasAuditoria.comoEtiquetaLegible("APROBAR_SOLICITUD"));
        assertEquals("Recálculo diario de mora", EtiquetasAuditoria.comoEtiquetaLegible("RECALCULO_MORA_DIARIO"));
    }

    @Test
    public void comoEtiquetaLegibleDevuelveElNombreMapeadoParaElComprobanteDeDesembolso() {
        // B23 (fase 2): únicas 2 operaciones de todo el sistema que quedaban sin nombre legible.
        assertEquals("Imprimir comprobante de desembolso",
                EtiquetasAuditoria.comoEtiquetaLegible("IMPRIMIR_COMPROBANTE_DESEMBOLSO"));
        assertEquals("Reimprimir comprobante de desembolso",
                EtiquetasAuditoria.comoEtiquetaLegible("REIMPRIMIR_COMPROBANTE_DESEMBOLSO"));
    }

    /** Nunca rompe con un código nuevo que todavía no esté en el mapa. */
    @Test
    public void comoEtiquetaLegibleCaeAlCodigoSiNoEstaMapeado() {
        assertEquals("OPERACION_FUTURA_SIN_MAPEAR",
                EtiquetasAuditoria.comoEtiquetaLegible("OPERACION_FUTURA_SIN_MAPEAR"));
    }

    @Test
    public void buscarCodigosPorTextoEncuentraPorNombreLegibleSinAcentosNiMayusculas() {
        List<String> codigos = EtiquetasAuditoria.buscarCodigosPorTexto("MORA");
        assertTrue(codigos.contains("RECALCULO_MORA_DIARIO"));
        assertTrue(codigos.contains("RECALCULAR_ESTADO_MORA"));
    }

    @Test
    public void buscarCodigosPorTextoIgnoraAcentos() {
        // "Recálculo" lleva tilde -- "recalculo" (sin tilde) tiene que encontrarlo igual.
        assertTrue(EtiquetasAuditoria.buscarCodigosPorTexto("recalculo").contains("RECALCULO_MORA_DIARIO"));
    }

    @Test
    public void buscarCodigosPorTextoVacioONuloNoDevuelveNada() {
        assertTrue(EtiquetasAuditoria.buscarCodigosPorTexto(null).isEmpty());
        assertTrue(EtiquetasAuditoria.buscarCodigosPorTexto("").isEmpty());
        assertTrue(EtiquetasAuditoria.buscarCodigosPorTexto("   ").isEmpty());
    }

    @Test
    public void buscarCodigosPorTextoSinCoincidenciasDevuelveListaVacia() {
        assertTrue(EtiquetasAuditoria.buscarCodigosPorTexto("xyzxyzxyz").isEmpty());
    }

    @Test
    public void listarEtiquetasOrdenadasNoEstaVaciaYEstaOrdenada() {
        List<String> etiquetas = EtiquetasAuditoria.listarEtiquetasOrdenadas();
        assertFalse(etiquetas.isEmpty());
        for (int i = 1; i < etiquetas.size(); i++) {
            assertTrue(etiquetas.get(i - 1).compareTo(etiquetas.get(i)) <= 0);
        }
    }
}
