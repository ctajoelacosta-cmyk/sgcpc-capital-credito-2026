package py.com.capitalcredito.sgcpc.infraestructura;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import org.junit.Test;

public class ConversorNumerosALetrasTest {

    private String letras(long valor) {
        return ConversorNumerosALetras.comoLetras(BigDecimal.valueOf(valor));
    }

    @Test
    public void testCero() {
        assertEquals("cero", letras(0));
    }

    @Test
    public void testUnidadesYDecenas() {
        assertEquals("uno", letras(1));
        assertEquals("quince", letras(15));
        assertEquals("dieciséis", letras(16));
        assertEquals("veinte", letras(20));
        assertEquals("veintiuno", letras(21));
    }

    @Test
    public void testCentenas() {
        assertEquals("cien", letras(100));
        assertEquals("ciento uno", letras(101));
        assertEquals("ciento quince", letras(115));
        assertEquals("quinientos", letras(500));
    }

    @Test
    public void testMiles() {
        assertEquals("mil uno", letras(1001));
        assertEquals("veintiún mil", letras(21000));
        assertEquals("ciento un mil", letras(101000));
        assertEquals("quinientos cuarenta y un mil ochocientos veinticinco", letras(541825));
        assertEquals("ciento noventa y tres mil setecientos setenta y nueve", letras(193779));
    }

    @Test
    public void testMillones() {
        assertEquals("un millón", letras(1000000));
        assertEquals("dos millones", letras(2000000));
        assertEquals("un millón quinientos mil", letras(1500000));
        assertEquals("tres millones", letras(3000000));
        assertEquals("tres millones doscientos treinta y seis mil doscientos quince", letras(3236215));
    }

    @Test
    public void testNueveCientosNoventaYNueveMillones() {
        assertEquals("novecientos noventa y nueve millones novecientos noventa y nueve mil novecientos noventa y nueve",
                letras(999999999));
    }

    @Test
    public void testComoLetrasConGuaranies() {
        assertEquals("tres millones de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(3000000)));
        assertEquals("un millón de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(1000000)));
        assertEquals("veintiún mil guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(21000)));
        assertEquals("tres millones doscientos treinta y seis mil doscientos quince guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(3236215)));
    }

    /**
     * B127 (11/11/2026): "guaraníes" es masculino y va DETRÁS del número, así que todo monto
     * terminado en 1 lleva la apócope. El valor que nunca había aparecido en los datos de prueba
     * (y que por eso vivió hasta la revisión final) es justamente este. Incluye el singular,
     * 1 Gs., y los dos casos que NO se apocopan: los que terminan en "millón"/"millones".
     */
    @Test
    public void testComoLetrasConGuaraniesApocopa() {
        assertEquals("novecientos diecinueve mil doscientos setenta y un guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(919271)));
        assertEquals("veintiún guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(21)));
        assertEquals("ciento un guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(101)));
        assertEquals("un millón un guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(1000001)));
        assertEquals("un guaraní",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.ONE));
        // Los que terminan en millón/millones no se tocan: el sustantivo va después de "de".
        assertEquals("un millón de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(1000000)));
        assertEquals("veintiún millones de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(21000000)));
        // Y los que no terminan en 1 quedan exactamente como estaban.
        assertEquals("cero guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.ZERO));
        assertEquals("ochocientos mil guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(800000)));
    }

    /** RF12 (consolidación, pedido de Joel): casos redondos en millones/miles que antes se
     * armaban a mano con " GUARANÍES)" pegado afuera y daban "CINCO MILLONES GUARANÍES" en vez
     * de "...DE GUARANÍES" -- quedan cubiertos acá para que no se repita el error. */
    @Test
    public void testComoLetrasConGuaraniesMontosRedondos() {
        assertEquals("quince millones de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(15000000)));
        assertEquals("dos millones quinientos mil guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(2500000)));
        assertEquals("mil millones de guaraníes",
                ConversorNumerosALetras.comoLetrasConGuaranies(BigDecimal.valueOf(1000000000L)));
    }
}
