package py.com.capitalcredito.sgcpc.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import py.com.capitalcredito.sgcpc.dto.DetalleAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.DetalleCuotaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoAplicacionPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCapacidadPago;
import py.com.capitalcredito.sgcpc.dto.ResultadoCotizacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoDeudaExigible;
import py.com.capitalcredito.sgcpc.dto.ResultadoLimiteTasa;
import py.com.capitalcredito.sgcpc.dto.ResultadoLiquidacion;
import py.com.capitalcredito.sgcpc.dto.ResultadoMora;
import py.com.capitalcredito.sgcpc.dto.ResultadoTasasMora;
import py.com.capitalcredito.sgcpc.enumeraciones.EstadoPago;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoAplicacion;
import py.com.capitalcredito.sgcpc.enumeraciones.TipoLiquidacion;
import py.com.capitalcredito.sgcpc.excepciones.NegocioException;
import py.com.capitalcredito.sgcpc.infraestructura.FormateadorFechas;
import py.com.capitalcredito.sgcpc.modelos.Cuota;
import py.com.capitalcredito.sgcpc.modelos.Prestamo;

/**
 * Motor financiero unico (Doc. 00 Sec. 6): todas las formulas de creditos,
 * IVA, mora, aplicacion de pagos, cancelacion y anticipo viven aca. Metodos
 * puros: reciben datos en memoria y devuelven resultados, sin tocar JDBC ni
 * mutar los objetos que reciben (una Cuota "resultante" siempre es una copia
 * nueva). Dinero y tasas en BigDecimal; tasas como fraccion (0.27 = 27%,
 * D-12). Redondeo HALF_UP a entero en cada importe final; escala 10 en
 * calculos intermedios (potencias). Java no tiene potencia de exponente
 * fraccionario para BigDecimal: se resuelve con Math.pow sobre double y el
 * resultado se vuelve a convertir a BigDecimal escala 10 (ver potencia()).
 */
public class ServicioMotorFinanciero {

    private static final BigDecimal TASA_IVA = new BigDecimal("0.10");
    private static final BigDecimal UNO_MAS_IVA = new BigDecimal("1.10");
    private static final int ESCALA_INTERMEDIA = 10;
    private static final int MESES_ANIO = 12;
    private static final int DIAS_ANIO = 365;

    // ----------------------------------------------------------------
    // Utilidades numericas
    // ----------------------------------------------------------------

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Paquete (no private) para que ServicioCobranza pueda derivar, al reimprimir una factura
     * (D-93/E8), el IVA de cada concepto a partir del neto ya guardado -- la MISMA fórmula fija
     * que ya se usó al emitir, sin duplicarla (motor financiero único, REGLAS_DESARROLLO.md §5).
     */
    static BigDecimal iva(BigDecimal base) {
        return redondear(base.multiply(TASA_IVA));
    }

    /** (base)^exponente, con exponente fraccionario o negativo; escala 10. */
    private static BigDecimal potencia(BigDecimal base, BigDecimal exponente) {
        double resultado = Math.pow(base.doubleValue(), exponente.doubleValue());
        return BigDecimal.valueOf(resultado).setScale(ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
    }

    private static BigDecimal dividir(BigDecimal numerador, BigDecimal denominador) {
        return numerador.divide(denominador, ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
    }

    /** i = (1+TEA)^(1/12) - 1 (Doc. 00 Sec. 6.1). */
    public static BigDecimal calcularTasaMensual(BigDecimal tea) {
        BigDecimal base = BigDecimal.ONE.add(tea);
        BigDecimal exponente = dividir(BigDecimal.ONE, BigDecimal.valueOf(MESES_ANIO));
        return potencia(base, exponente).subtract(BigDecimal.ONE);
    }

    // ----------------------------------------------------------------
    // fechaHabil (RN-19)
    // ----------------------------------------------------------------

    public LocalDate fechaHabil(LocalDate fecha, Collection<LocalDate> feriados) {
        LocalDate resultado = fecha;
        while (resultado.getDayOfWeek() == DayOfWeek.SATURDAY
                || resultado.getDayOfWeek() == DayOfWeek.SUNDAY
                || (feriados != null && feriados.contains(resultado))) {
            resultado = resultado.plusDays(1);
        }
        return resultado;
    }

    /**
     * RF11 (pedido de Joel, 11/10/2026): el día hábil más cercano, hacia atrás, a `fecha` --
     * usado para sugerir una alternativa cuando `fechaHabil` (hacia adelante) empuja la fecha del
     * primer vencimiento fuera del rango permitido.
     */
    public LocalDate fechaHabilAnterior(LocalDate fecha, Collection<LocalDate> feriados) {
        LocalDate resultado = fecha;
        while (resultado.getDayOfWeek() == DayOfWeek.SATURDAY
                || resultado.getDayOfWeek() == DayOfWeek.SUNDAY
                || (feriados != null && feriados.contains(resultado))) {
            resultado = resultado.minusDays(1);
        }
        return resultado;
    }

    // ----------------------------------------------------------------
    // generarPlanPagos / cotizar (Sec. 6.2, 6.1)
    // ----------------------------------------------------------------

    public List<Cuota> generarPlanPagos(BigDecimal monto, int plazoMeses, BigDecimal tea,
            LocalDate fechaPrimerVencimiento, Collection<LocalDate> feriados) {
        BigDecimal i = calcularTasaMensual(tea);
        BigDecimal unoMasI = BigDecimal.ONE.add(i);
        BigDecimal factorDescuento = BigDecimal.ONE.subtract(potencia(unoMasI, BigDecimal.valueOf(-plazoMeses)));
        BigDecimal cuotaConstante = redondear(dividir(monto.multiply(i), factorDescuento));

        List<Cuota> plan = new ArrayList<>();
        BigDecimal saldo = monto;
        for (int k = 1; k <= plazoMeses; k++) {
            LocalDate fechaTeorica = fechaPrimerVencimiento.plusMonths(k - 1);
            LocalDate fechaReal = fechaHabil(fechaTeorica, feriados);

            BigDecimal interes = redondear(saldo.multiply(i));
            BigDecimal capital = (k == plazoMeses) ? saldo : cuotaConstante.subtract(interes);
            BigDecimal ivaCuota = iva(interes);
            BigDecimal total = interes.add(capital).add(ivaCuota);
            BigDecimal saldoPosterior = saldo.subtract(capital);

            Cuota cuota = new Cuota();
            cuota.setNumeroCuota(k);
            cuota.setFechaVencimiento(fechaReal);
            cuota.setFechaVencimientoTeorica(fechaTeorica);
            cuota.setCapitalProgramado(capital);
            cuota.setInteresProgramado(interes);
            cuota.setTributosProgramados(ivaCuota);
            cuota.setTotalProgramado(total);
            cuota.setSaldoCapitalPosterior(saldoPosterior);
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            plan.add(cuota);

            saldo = saldoPosterior;
        }
        return plan;
    }

    public ResultadoCotizacion cotizar(BigDecimal monto, int plazoMeses, LocalDate fechaPrimerVencimiento,
            BigDecimal tea, Collection<LocalDate> feriados) {
        List<Cuota> plan = generarPlanPagos(monto, plazoMeses, tea, fechaPrimerVencimiento, feriados);

        Cuota primera = plan.get(0);
        BigDecimal cuotaSinIva = primera.getInteresProgramado().add(primera.getCapitalProgramado());
        BigDecimal montoTotalConIva = BigDecimal.ZERO;
        for (Cuota cuota : plan) {
            montoTotalConIva = montoTotalConIva.add(cuota.getTotalProgramado());
        }

        BigDecimal i = calcularTasaMensual(tea);
        BigDecimal tna = i.multiply(BigDecimal.valueOf(MESES_ANIO)).setScale(6, RoundingMode.HALF_UP);
        BigDecimal cft = calcularCft(monto, plan);

        return new ResultadoCotizacion(cuotaSinIva, montoTotalConIva, tea, tna, cft, plan);
    }

    /** RN-03: "cuota más alta con IVA" del plan (RF11, movido de ServicioSolicitudes para que
     * RF05/RF06 y RF11 compartan la misma fórmula -- motor financiero único). Pura. */
    public BigDecimal cuotaMasAlta(ResultadoCotizacion cotizacion) {
        return cotizacion.getPlan().stream()
                .map(Cuota::getTotalProgramado)
                .max(BigDecimal::compareTo)
                .orElse(cotizacion.getMontoTotalConIva());
    }

    /** Bloque 7/E10, Tanda B (D-127): suma tratando null como cero (persona sin el dato todavía).
     * RF11 (11/10/2026): movido de ServicioSolicitudes a este motor -- ServicioFormalizacion
     * también lo necesita y, como servicio, ya depende de ServicioCotizacion/ServicioSolicitudes
     * en el otro sentido (ServicioCotizacion -> ServicioFormalizacion), así que inyectar
     * ServicioSolicitudes ahí formaba un ciclo de construcción (StackOverflowError). Pura. */
    public BigDecimal sumarCuotasOtrasDeudas(BigDecimal cuotaCliente) {
        return cuotaCliente == null ? BigDecimal.ZERO : cuotaCliente;
    }

    /** CFT = TIR mensual de los flujos del cliente, anualizada (Sec. 6.1). Resuelto por biseccion. */
    private BigDecimal calcularCft(BigDecimal monto, List<Cuota> plan) {
        double montoDouble = monto.doubleValue();
        double bajo = 0.0;
        double alto = 10.0;
        for (int iteracion = 0; iteracion < 100; iteracion++) {
            double medio = (bajo + alto) / 2.0;
            double valorPresente = valorPresenteFlujos(plan, medio);
            if (valorPresente > montoDouble) {
                bajo = medio;
            } else {
                alto = medio;
            }
        }
        double tirMensual = (bajo + alto) / 2.0;
        BigDecimal unoMasTir = BigDecimal.valueOf(1 + tirMensual);
        return potencia(unoMasTir, BigDecimal.valueOf(MESES_ANIO)).subtract(BigDecimal.ONE)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private double valorPresenteFlujos(List<Cuota> plan, double tasaMensual) {
        double valorPresente = 0.0;
        int periodo = 1;
        for (Cuota cuota : plan) {
            valorPresente += cuota.getTotalProgramado().doubleValue() / Math.pow(1 + tasaMensual, periodo);
            periodo++;
        }
        return valorPresente;
    }

    // ----------------------------------------------------------------
    // calcularCapacidadPago (Sec. 6.3 / RN-03 / RN-04)
    // ----------------------------------------------------------------

    /**
     * D-127 (Bloque 7/E10, Tanda B, Opción A elegida por Joel): la cuota de otras deudas del
     * cliente se SUMA a la cuota nueva antes de medir la afectación -- "cuánto de lo que el
     * cliente ya paga por mes, más lo nuevo, representa sobre su ingreso total". Nunca un
     * denominador cero/negativo (a diferencia de restarla del ingreso), así que no hace falta
     * ningún caso especial. cuotaOtrasDeudas puede ser null (una persona todavía sin ese dato,
     * D-126/D-127: "se completa al editar") -- se trata como cero.
     */
    public ResultadoCapacidadPago calcularCapacidadPago(BigDecimal cuotaMaxima, BigDecimal ingresoCliente,
            BigDecimal cuotaOtrasDeudas) {
        BigDecimal cuotaTotal = cuotaMaxima;
        if (cuotaOtrasDeudas != null) {
            cuotaTotal = cuotaTotal.add(cuotaOtrasDeudas);
        }
        BigDecimal afectacion = dividir(cuotaTotal, ingresoCliente);
        return new ResultadoCapacidadPago(afectacion);
    }

    // ----------------------------------------------------------------
    // validarLimiteTasa (RN-15)
    // ----------------------------------------------------------------

    public ResultadoLimiteTasa validarLimiteTasa(BigDecimal tea, BigDecimal tasaMaximaVigente) {
        boolean ok = tea.compareTo(tasaMaximaVigente) <= 0;
        return new ResultadoLimiteTasa(ok, tasaMaximaVigente);
    }

    // ----------------------------------------------------------------
    // calcularTasasMora (RN-16/D-20, snapshot al formalizar)
    // ----------------------------------------------------------------

    /**
     * D-82: tasas de mora que se "congelan" al formalizar el préstamo (no cambian
     * retroactivamente si el parámetro del sistema cambia después). tasaMoratoria = TEA
     * aprobada × factor_moratorio. tasaPunitoria es el porcentaje_punitorio TAL CUAL -- una
     * fracción del moratorio ya calculado (ej. 0,30 = 30 %), NO una tasa anual: nunca se
     * multiplica por tasaMoratoria (bug corregido en D-82; antes se guardaba
     * tasaMoratoria × porcentajePunitorio, dejando el punitorio real en ~8,1 % en vez de 30 %).
     */
    public static ResultadoTasasMora calcularTasasMora(BigDecimal tasaTeaAprobada, BigDecimal factorMoratorio,
            BigDecimal porcentajePunitorio) {
        BigDecimal tasaMoratoria = tasaTeaAprobada.multiply(factorMoratorio).setScale(6, RoundingMode.HALF_UP);
        BigDecimal tasaPunitoria = porcentajePunitorio.setScale(6, RoundingMode.HALF_UP);
        return new ResultadoTasasMora(tasaMoratoria, tasaPunitoria);
    }

    // ----------------------------------------------------------------
    // calcularMora (Sec. 6.4)
    // ----------------------------------------------------------------

    public ResultadoMora calcularMora(Cuota cuota, LocalDate fecha, BigDecimal tasaMoratoria,
            BigDecimal tasaPunitoria, int diasGracia) {
        LocalDate inicioTramo = cuota.getFechaVencimiento();
        if (cuota.getFechaCorteMora() != null && cuota.getFechaCorteMora().isAfter(inicioTramo)) {
            inicioTramo = cuota.getFechaCorteMora();
        }
        long dias = Math.max(0, ChronoUnit.DAYS.between(inicioTramo, fecha));

        // Dias de gracia (D-77, ajuste de Joel): tolerancia, no descuento -- si el pago cae
        // dentro de los dias de gracia contados desde el vencimiento, la mora de este tramo es
        // cero; si cae despues, se cuenta completa desde la fecha de vencimiento (no se restan
        // los dias de gracia de "dias").
        BigDecimal moratorioTramo;
        BigDecimal ivaMoratorioTramo;
        BigDecimal punitorioTramo;
        BigDecimal ivaPunitorioTramo;
        if (dias <= diasGracia) {
            moratorioTramo = BigDecimal.ZERO;
            ivaMoratorioTramo = BigDecimal.ZERO;
            punitorioTramo = BigDecimal.ZERO;
            ivaPunitorioTramo = BigDecimal.ZERO;
        } else {
            BigDecimal base = cuota.capitalPendiente();
            BigDecimal exponente = dividir(BigDecimal.valueOf(dias), BigDecimal.valueOf(DIAS_ANIO));
            BigDecimal factor = potencia(BigDecimal.ONE.add(tasaMoratoria), exponente).subtract(BigDecimal.ONE);

            moratorioTramo = redondear(base.multiply(factor));
            ivaMoratorioTramo = iva(moratorioTramo);
            punitorioTramo = redondear(moratorioTramo.multiply(tasaPunitoria));
            ivaPunitorioTramo = iva(punitorioTramo);
        }

        BigDecimal moratorioTotal = cuota.getMoratorioPendiente().add(moratorioTramo);
        BigDecimal ivaMoratorioTotal = cuota.getIvaMoratorioPendiente().add(ivaMoratorioTramo);
        BigDecimal punitorioTotal = cuota.getPunitorioPendiente().add(punitorioTramo);
        BigDecimal ivaPunitorioTotal = cuota.getIvaPunitorioPendiente().add(ivaPunitorioTramo);

        return new ResultadoMora(dias, moratorioTramo, ivaMoratorioTramo, punitorioTramo, ivaPunitorioTramo,
                moratorioTotal, ivaMoratorioTotal, punitorioTotal, ivaPunitorioTotal);
    }

    // ----------------------------------------------------------------
    // distribuirPago (Sec. 6.5 / RN-10)
    // ----------------------------------------------------------------

    public ResultadoAplicacionPago distribuirPago(Prestamo prestamo, BigDecimal monto, LocalDate fecha,
            int diasGracia) {
        List<Cuota> vencidas = new ArrayList<>();
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA && cuota.getFechaVencimiento().isBefore(fecha)) {
                vencidas.add(cuota);
            }
        }
        vencidas.sort((a, b) -> Integer.compare(a.getNumeroCuota(), b.getNumeroCuota()));

        List<DetalleAplicacionPago> detalles = new ArrayList<>();
        BigDecimal restante = monto;

        for (Cuota cuota : vencidas) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            DetalleAplicacionPago detalle = aplicarPagoACuota(prestamo, cuota, restante, fecha, TipoAplicacion.CUOTA,
                    diasGracia);
            detalles.add(detalle);
            restante = restante.subtract(detalle.getSubtotalAplicado());
        }

        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            // D-144 (Ley 6624/2020, corrección de Joel sobre D-140/D-143): un cobro normal NUNCA
            // cierra una cuota que todavía no venció -- la única excepción es la cuota que vence
            // JUSTO hoy (fechaVencimiento no es posterior a "fecha"), que sigue siendo el cobro
            // contractual de siempre, sin prorrateo ni reducción. Marcar una cuota futura como
            // PAGADA con interés reducido dejaba un hueco de días sin generar interés sobre el
            // saldo que seguía siendo deuda vigente (bug real encontrado por Joel, CC-2026-00007,
            // ~75.763 Gs. de interés perdido). Cualquier excedente que alcance una cuota futura
            // queda como vuelto -- el camino correcto para adelantar esa cuota es "Adelantar
            // capital" (calcularAnticipoParcial), que sí recalcula el plan con interés continuo.
            Cuota cuotaActual = proximaCuotaNoProcesada(prestamo, vencidas);
            if (cuotaActual != null && !cuotaActual.getFechaVencimiento().isAfter(fecha)) {
                DetalleAplicacionPago detalle = aplicarPagoACuota(prestamo, cuotaActual, restante, fecha,
                        TipoAplicacion.CUOTA, diasGracia);
                detalles.add(detalle);
                restante = restante.subtract(detalle.getSubtotalAplicado());
            }
        }

        return new ResultadoAplicacionPago(detalles, restante);
    }

    // ----------------------------------------------------------------
    // revertirAplicacionEnCuota (E9/Bloque 5, PO-13) -- inversa exacta de aplicarPagoACuota.
    // ----------------------------------------------------------------

    /**
     * E9/Bloque 5: dado lo que un pago aplicó a una cuota (los montos NETOS que guardó
     * aplicaciones_pago -- capital/interés/moratorio/punitorio, sin su IVA por separado, que
     * nunca se guarda por concepto), la fecha del touque anterior a este pago sobre la misma
     * cuota (el MAX(fecha_hora_pago) de los demás pagos CONFIRMADO que la tocaron, o null si
     * ninguno la había tocado nunca) y la fecha del propio pago que se revierte, devuelve la
     * cuota tal como habría quedado si ese pago nunca hubiera existido -- invariante de Joel,
     * "como si el cobro nunca hubiera existido" (PO-13).
     * <p>
     * El moratorio/punitorio pendiente NO se reconstruye con una resta directa (la mora es
     * incremental por tramos, {@link #calcularMora} sigue sumando sobre la base que YA tenía la
     * cuota -- una resta simple deja el tramo que se acumuló entre el touque anterior y este pago
     * pisado por error). Se aísla ese tramo con una llamada a {@link #calcularMora} sobre una
     * cuota "en blanco" (pendientes en cero, con la fecha de corte y el capital restaurado, para
     * que la base de cálculo sea la correcta) y se lo resta del total actual antes de sumar de
     * vuelta lo que este pago había aplicado -- así el resultado es exacto tanto si es el primer
     * touque de la cuota como si hay uno anterior todavía vigente.
     * El IVA de mora se recalcula con el mismo {@link #iva} de siempre (10 % del neto
     * restaurado: aplicaciones_pago.monto_tributos junta el IVA de interés+moratorio+punitorio en
     * una sola columna, así que no hay nada que leer ahí por concepto).
     */
    public Cuota revertirAplicacionEnCuota(Cuota cuotaActual, BigDecimal capitalNetoAplicado,
            BigDecimal interesNetoAplicado, BigDecimal moratorioNetoAplicado, BigDecimal punitorioNetoAplicado,
            LocalDate fechaCorteMoraAnterior, BigDecimal tasaMoratoria, BigDecimal tasaPunitoria, int diasGracia,
            LocalDate fechaPagoRevertido) {
        BigDecimal capitalPagadoRestaurado = cuotaActual.getCapitalPagado().subtract(capitalNetoAplicado);
        BigDecimal interesPagadoRestaurado = cuotaActual.getInteresPagado().subtract(interesNetoAplicado);

        Cuota cuotaEnBlanco = new Cuota(cuotaActual);
        cuotaEnBlanco.setCapitalPagado(capitalPagadoRestaurado);
        cuotaEnBlanco.setMoratorioPendiente(BigDecimal.ZERO);
        cuotaEnBlanco.setPunitorioPendiente(BigDecimal.ZERO);
        cuotaEnBlanco.setFechaCorteMora(fechaCorteMoraAnterior);
        ResultadoMora tramoAislado = calcularMora(cuotaEnBlanco, fechaPagoRevertido, tasaMoratoria, tasaPunitoria,
                diasGracia);

        BigDecimal moratorioPendiente = cuotaActual.getMoratorioPendiente()
                .subtract(tramoAislado.getMoratorioTotal()).add(moratorioNetoAplicado);
        BigDecimal punitorioPendiente = cuotaActual.getPunitorioPendiente()
                .subtract(tramoAislado.getPunitorioTotal()).add(punitorioNetoAplicado);

        Cuota restaurada = new Cuota(cuotaActual);
        restaurada.setCapitalPagado(capitalPagadoRestaurado);
        restaurada.setInteresPagado(interesPagadoRestaurado);
        restaurada.setMoratorioPendiente(moratorioPendiente);
        restaurada.setPunitorioPendiente(punitorioPendiente);
        restaurada.setIvaMoratorioPendiente(iva(moratorioPendiente));
        restaurada.setIvaPunitorioPendiente(iva(punitorioPendiente));
        restaurada.setFechaCorteMora(fechaCorteMoraAnterior);
        restaurada.setEstadoPago(determinarEstadoPago(restaurada));
        return restaurada;
    }

    private Cuota proximaCuotaNoProcesada(Prestamo prestamo, List<Cuota> yaProcesadas) {
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA && !yaProcesadas.contains(cuota)) {
                return cuota;
            }
        }
        return null;
    }

    private DetalleAplicacionPago aplicarPagoACuota(Prestamo prestamo, Cuota cuota, BigDecimal disponible,
            LocalDate fecha, TipoAplicacion tipo, int diasGracia) {
        ResultadoMora mora = calcularMora(cuota, fecha, prestamo.getTasaMoratoria(), prestamo.getTasaPunitoria(),
                diasGracia);

        BigDecimal[] rMoratorio = aplicarConcepto(disponible, mora.getMoratorioTotal(), mora.getIvaMoratorioTotal());
        BigDecimal restante = rMoratorio[2];

        BigDecimal[] rPunitorio = aplicarConcepto(restante, mora.getPunitorioTotal(), mora.getIvaPunitorioTotal());
        restante = rPunitorio[2];

        BigDecimal interesPendiente = cuota.interesPendiente();
        BigDecimal ivaInteresPendiente = iva(interesPendiente);
        BigDecimal[] rInteres = aplicarConcepto(restante, interesPendiente, ivaInteresPendiente);
        restante = rInteres[2];

        BigDecimal capitalPendiente = cuota.capitalPendiente();
        BigDecimal capitalAplicado = restante.min(capitalPendiente);
        restante = restante.subtract(capitalAplicado);

        BigDecimal subtotal = disponible.subtract(restante);

        Cuota resultante = new Cuota(cuota);
        resultante.setCapitalPagado(cuota.getCapitalPagado().add(capitalAplicado));
        resultante.setInteresPagado(cuota.getInteresPagado().add(rInteres[0]));
        resultante.setMoratorioPendiente(mora.getMoratorioTotal().subtract(rMoratorio[0]));
        resultante.setIvaMoratorioPendiente(mora.getIvaMoratorioTotal().subtract(rMoratorio[1]));
        resultante.setPunitorioPendiente(mora.getPunitorioTotal().subtract(rPunitorio[0]));
        resultante.setIvaPunitorioPendiente(mora.getIvaPunitorioTotal().subtract(rPunitorio[1]));
        resultante.setFechaCorteMora(fecha);
        resultante.setEstadoPago(determinarEstadoPago(resultante));

        return new DetalleAplicacionPago(tipo, cuota.getNumeroCuota(), rMoratorio[0], rMoratorio[1], rPunitorio[0],
                rPunitorio[1], rInteres[0], rInteres[1], capitalAplicado, subtotal, resultante);
    }

    // D-144: el pago anticipado de una cuota NO vencida dentro de un cobro normal (D-140/D-143)
    // se retiró -- dejaba un hueco de días sin interés sobre el saldo (ver el comentario en
    // distribuirPago). Todo pago anticipado de capital pasa ahora solo por "Adelantar capital"
    // (calcularAnticipoParcial) o "Cancelación total" (calcularCancelacionTotal), que sí
    // recalculan el plan con interés continuo. corresponderPagoAnticipado,
    // calcularInteresDevengadoCuotaAnticipada y aplicarPagoACuotaAnticipada se borraron enteras
    // (sin dejar código muerto): no les queda ningún llamador en todo el proyecto.

    /**
     * Aplica un concepto (moratorio, punitorio o interes) + su IVA contra el
     * disponible. Si no alcanza, reparte: neto = redondear(monto / 1,10),
     * IVA = monto - neto (Sec. 6.5 punto 3). Devuelve {neto, iva, restante}.
     */
    /**
     * D-85: si el disponible no alcanza para el concepto completo, se elige el neto MAS GRANDE
     * tal que `neto + iva(neto) <= disponible` -- así el IVA aplicado es SIEMPRE exactamente el
     * 10 % del neto aplicado (nunca un "resto" suelto que no reconstruya bien en la factura,
     * ej.: con 16 Gs disponibles, antes daba neto=15/iva=1 -- round(15×10%)=2≠1, no cuadraba).
     * El guaraní que sobra (nunca más de 1 o 2, según dónde caiga el redondeo) NO se pierde:
     * vuelve en la posición [2] ("restante"), igual que antes, para que el llamador lo pase al
     * siguiente concepto del orden RN-10 (moratorio → punitorio → interés → capital).
     */
    BigDecimal[] aplicarConcepto(BigDecimal disponible, BigDecimal montoConcepto, BigDecimal ivaConcepto) {
        BigDecimal totalConcepto = montoConcepto.add(ivaConcepto);
        if (totalConcepto.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, disponible};
        }
        if (disponible.compareTo(totalConcepto) >= 0) {
            return new BigDecimal[] {montoConcepto, ivaConcepto, disponible.subtract(totalConcepto)};
        }
        BigDecimal neto = redondear(dividir(disponible, UNO_MAS_IVA));
        while (neto.add(iva(neto)).compareTo(disponible) > 0) {
            neto = neto.subtract(BigDecimal.ONE);
        }
        while (neto.add(BigDecimal.ONE).add(iva(neto.add(BigDecimal.ONE))).compareTo(disponible) <= 0) {
            neto = neto.add(BigDecimal.ONE);
        }
        BigDecimal ivaAplicado = iva(neto);
        BigDecimal restante = disponible.subtract(neto).subtract(ivaAplicado);
        return new BigDecimal[] {neto, ivaAplicado, restante};
    }

    // E9/Bloque 5: visibilidad de paquete (no private) -- ServicioReversiones la reusa tal cual
    // para que el estado de una cuota restaurada se calcule con la MISMA regla de siempre, nunca
    // una nueva (REGLAS_DESARROLLO.md §5, "toda fórmula vive solo en ServicioMotorFinanciero").
    EstadoPago determinarEstadoPago(Cuota cuota) {
        boolean capitalCompleto = cuota.getCapitalPagado().compareTo(cuota.getCapitalProgramado()) >= 0;
        boolean interesCompleto = cuota.getInteresPagado().compareTo(cuota.getInteresProgramado()) >= 0;
        if (capitalCompleto && interesCompleto) {
            return EstadoPago.PAGADA;
        }
        if (cuota.getCapitalPagado().compareTo(BigDecimal.ZERO) > 0 || cuota.getInteresPagado().compareTo(BigDecimal.ZERO) > 0) {
            return EstadoPago.PARCIAL;
        }
        return EstadoPago.PENDIENTE;
    }

    // ----------------------------------------------------------------
    // calcularDeudaExigible
    // ----------------------------------------------------------------

    public ResultadoDeudaExigible calcularDeudaExigible(Prestamo prestamo, LocalDate fecha, int diasGracia) {
        List<DetalleCuotaExigible> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Cuota cuota : prestamo.getCuotas()) {
            boolean vencida = cuota.getEstadoPago() != EstadoPago.PAGADA && cuota.getFechaVencimiento().isBefore(fecha);
            if (!vencida) {
                continue;
            }
            ResultadoMora mora = calcularMora(cuota, fecha, prestamo.getTasaMoratoria(), prestamo.getTasaPunitoria(),
                    diasGracia);
            BigDecimal capitalPendiente = cuota.capitalPendiente();
            BigDecimal interesPendiente = cuota.interesPendiente();
            BigDecimal ivaInteresPendiente = iva(interesPendiente);

            BigDecimal totalCuota = capitalPendiente.add(interesPendiente).add(ivaInteresPendiente)
                    .add(mora.getMoratorioTotal()).add(mora.getIvaMoratorioTotal())
                    .add(mora.getPunitorioTotal()).add(mora.getIvaPunitorioTotal());

            detalles.add(new DetalleCuotaExigible(cuota.getNumeroCuota(), capitalPendiente, interesPendiente,
                    ivaInteresPendiente, mora.getMoratorioTotal(), mora.getIvaMoratorioTotal(),
                    mora.getPunitorioTotal(), mora.getIvaPunitorioTotal(), totalCuota));
            total = total.add(totalCuota);
        }

        return new ResultadoDeudaExigible(detalles, total);
    }

    // ----------------------------------------------------------------
    // Periodo actual (D-23): dias entre fechas TEORICAS, para 6.6 y 6.7
    // ----------------------------------------------------------------

    /** Visibilidad de paquete (D-88): ServicioMotorFinancieroTest la usa directo en el barrido de fechas. */
    static final class PeriodoActual {
        final Cuota cuotaActual;
        final long diasTranscurridos;
        final long diasDelPeriodo;

        PeriodoActual(Cuota cuotaActual, long diasTranscurridos, long diasDelPeriodo) {
            this.cuotaActual = cuotaActual;
            this.diasTranscurridos = diasTranscurridos;
            this.diasDelPeriodo = diasDelPeriodo;
        }
    }

    /**
     * D-87: para la cuota 1 de un plan regenerado por anticipo (origenPlanVigente =
     * "ANTICIPO_PARCIAL"), fechaDesembolso ya no representa el inicio de su período -- ese plan
     * no arranca en el desembolso original del préstamo. El inicio real es el MAYOR entre la
     * fecha del anticipo que generó el plan (planes_pago.fecha_generacion) y "vencimiento
     * teórico de la cuota 1 menos un mes": si la cuota de referencia ya estaba PAGADA cuando se
     * hizo el anticipo (D-86: período cerrado, la cuota 1 nueva arranca un período entero
     * después), ese "menos un mes" cae DESPUÉS de la fecha del anticipo y gana; si la cuota de
     * referencia todavía estaba a mitad de período (D-80: anticipo dentro del período en curso),
     * ese "menos un mes" cae en el inicio del período ORIGINAL (anterior al anticipo) y pierde
     * contra la fecha del anticipo -- evitando cobrar de nuevo los días que el cliente ya pagó
     * con ese anticipo si vuelve a cancelar/anticipar dentro del mismo período.
     * <p>
     * D-145 (E-3, pedido de Joel, investigando el caso real de María/CC-2026-00007, AVANCE.md):
     * la rama "cuota de referencia ya PAGADA" de arriba (unMesAntes gana) solo abre un hueco real
     * de varias semanas sin interés cuando esa cuota quedó PAGADA con su vencimiento TODAVÍA
     * FUTURO respecto a la fecha del anticipo ("pagada por adelantado" -- el escenario de
     * BF-15/BF-16, más abajo). Bajo las reglas actuales (D-144: "Cobrar" nunca marca PAGADA una
     * cuota antes de su propio vencimiento, ver distribuirPago), eso ya no puede pasar con datos
     * nuevos: una cuota solo llega a PAGADA con fecha_del_pago >= su vencimiento, así que para
     * cualquier fecha de cálculo posterior a esa cuota, periodoActual ya encuentra la cuota
     * SIGUIENTE (todavía pendiente) como referencia -- nunca vuelve a entrar acá con una cuota
     * pagada. El único caso límite accesible es fecha == vencimiento teórico exacto (pagada el
     * mismo día que vence): ahí "unMesAntes" y "fechaAnticipo" empatan (interés 0 correcto, sin
     * hueco). Confirmado con ServicioMotorFinancieroTest#testE3a-d (sin base de datos): un hueco
     * solo es posible con una cuota PAGADA heredada de antes de D-144 (dato ya existente en la
     * base, como la cuota 3 de María) -- nunca con un pago nuevo.
     */
    private LocalDate inicioPeriodoTeorico(Prestamo prestamo, Cuota cuotaActual) {
        if (cuotaActual.getNumeroCuota() <= 1) {
            if ("ANTICIPO_PARCIAL".equals(prestamo.getOrigenPlanVigente())) {
                LocalDate unMesAntes = cuotaActual.getFechaVencimientoTeorica().minusMonths(1);
                LocalDate fechaAnticipo = prestamo.getFechaGeneracionPlanVigente();
                return unMesAntes.isAfter(fechaAnticipo) ? unMesAntes : fechaAnticipo;
            }
            return prestamo.getFechaDesembolso();
        }
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getNumeroCuota() == cuotaActual.getNumeroCuota() - 1) {
                return cuota.getFechaVencimientoTeorica();
            }
        }
        return prestamo.getFechaDesembolso();
    }

    /**
     * D-86: el período en curso es el que CONTIENE la fecha de cálculo -- el de la cuota cuyo
     * tramo teórico (vencimiento teórico anterior, exclusive] .. [su propio vencimiento
     * teórico, inclusive] cubre esa fecha -- sea esa cuota PAGADA o no. Reemplaza a la vieja
     * cuotaEnCurso (D-83), que buscaba "la primera no pagada y no vencida": eso fallaba cuando
     * una cuota se paga por adelantado (ej. cuota 3 pagada antes de su vencimiento) -- la
     * primera no pagada pasaba a ser una cuota FUTURA (ej. cuota 4) cuyo período todavía no
     * empezó, dando días transcurridos NEGATIVOS y un interés del período negativo (bug real,
     * D-86). Con esta búsqueda, si la cuota que contiene la fecha ya está PAGADA, el llamador
     * debe tratar el interés del período como cero (D-86: ya se cobró como parte de esa cuota
     * contractual, no se vuelve a cobrar ni se devuelve). Si ninguna cuota contiene la fecha
     * (ej. todas las pendientes ya vencieron, D-83), devuelve null.
     */
    /** Visibilidad de paquete (D-88): ServicioMotorFinancieroTest la usa directo en el barrido de fechas. */
    PeriodoActual periodoActual(Prestamo prestamo, LocalDate fecha) {
        for (Cuota cuota : prestamo.getCuotas()) {
            LocalDate inicio = inicioPeriodoTeorico(prestamo, cuota);
            LocalDate fin = cuota.getFechaVencimientoTeorica();
            if (fecha.isAfter(inicio) && !fecha.isAfter(fin)) {
                long diasDelPeriodo = ChronoUnit.DAYS.between(inicio, fin);
                long diasTranscurridos = ChronoUnit.DAYS.between(inicio, fecha);
                return new PeriodoActual(cuota, diasTranscurridos, diasDelPeriodo);
            }
        }
        // D-88: si la fecha es anterior (o igual) al inicio del primer período del plan vigente
        // (ej. un plan recién regenerado por anticipo, D-87, cuyo primer período todavía no
        // arrancó), ese tramo ya se cobró con el plan anterior -- cero días transcurridos, cero
        // interés, sin días negativos ni null. Distinto del caso D-83 (todas las cuotas
        // pendientes ya vencidas): ahí la fecha es POSTERIOR al último período, no anterior al
        // primero.
        if (!prestamo.getCuotas().isEmpty()) {
            Cuota primeraCuota = prestamo.getCuotas().get(0);
            LocalDate inicioPrimeraCuota = inicioPeriodoTeorico(prestamo, primeraCuota);
            if (!fecha.isAfter(inicioPrimeraCuota)) {
                long diasDelPeriodo = ChronoUnit.DAYS.between(inicioPrimeraCuota,
                        primeraCuota.getFechaVencimientoTeorica());
                return new PeriodoActual(primeraCuota, 0, diasDelPeriodo);
            }
        }
        return null;
    }

    /** D-86: el interés del período en curso se calcula solo si esa cuota todavía no se pagó. */
    private boolean corresponderProrratearInteresDelPeriodo(PeriodoActual periodo) {
        return periodo != null && periodo.diasDelPeriodo > 0
                && periodo.cuotaActual.getEstadoPago() != EstadoPago.PAGADA;
    }

    /**
     * D-86: protección -- con periodoActual buscando el tramo que CONTIENE la fecha, días
     * transcurridos siempre debería quedar entre 0 y diasDelPeriodo; si diera negativo es un
     * error de programación (no una situación de negocio), así que se corta acá en vez de
     * seguir con un importe sin sentido.
     */
    private BigDecimal interesDelPeriodoOFallar(BigDecimal interesDevengado) {
        if (interesDevengado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "El interés del período en curso no puede ser negativo (" + interesDevengado
                            + "); revisar periodoActual/corresponderProrratearInteresDelPeriodo.");
        }
        return interesDevengado;
    }

    /**
     * D-88 (regla de Joel, coherente con D-23): devengamiento de interés por fecha TEÓRICA --
     * toda cuota no pagada cuya teórica ya pasó tiene su interés de ese período devengado
     * COMPLETO, aunque todavía no esté vencida por la fecha EFECTIVA (RN-19 puede correr la
     * efectiva unos días después de la teórica, dejando un hueco de 1-3 días donde la cuota ni es
     * "el período en curso" -- ese ya pasó a la siguiente -- ni es "vencida" todavía). Devuelve
     * la primera cuota en ese estado (o null si no hay ninguna), para: (a) sumar su interés
     * completo en calcularCancelacionTotal sin esperar a que se vuelva vencida, y (b) rechazar un
     * anticipo de capital mientras exista una así ("primero hay que cobrar la cuota del DD/MM").
     */
    Cuota buscarCuotaConTeoricaPasada(Prestamo prestamo, LocalDate fecha) {
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA && cuota.getFechaVencimientoTeorica().isBefore(fecha)) {
                return cuota;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------
    // calcularCancelacionTotal (Sec. 6.6)
    // ----------------------------------------------------------------

    public ResultadoLiquidacion calcularCancelacionTotal(Prestamo prestamo, LocalDate fecha, int diasGracia) {
        ResultadoDeudaExigible exigibleVencidas = calcularDeudaExigible(prestamo, fecha, diasGracia);

        BigDecimal moratorioPendiente = BigDecimal.ZERO;
        BigDecimal ivaMoratorioPendiente = BigDecimal.ZERO;
        BigDecimal punitorioPendiente = BigDecimal.ZERO;
        BigDecimal ivaPunitorioPendiente = BigDecimal.ZERO;
        for (DetalleCuotaExigible detalle : exigibleVencidas.getDetalles()) {
            moratorioPendiente = moratorioPendiente.add(detalle.getMoratorioTotal());
            ivaMoratorioPendiente = ivaMoratorioPendiente.add(detalle.getIvaMoratorioTotal());
            punitorioPendiente = punitorioPendiente.add(detalle.getPunitorioTotal());
            ivaPunitorioPendiente = ivaPunitorioPendiente.add(detalle.getIvaPunitorioTotal());
        }

        BigDecimal capitalNoVencido = BigDecimal.ZERO;
        BigDecimal capitalVencido = BigDecimal.ZERO;
        BigDecimal interesTeoricaPasada = BigDecimal.ZERO;
        BigDecimal ivaInteresTeoricaPasada = BigDecimal.ZERO;
        // B204 (15/11/2026): el interes que el plan tenia programado, con su IVA, para las cuotas
        // que todavia NO estan vencidas -- la materia prima del "ahorro" de abajo. Las vencidas no
        // entran: su interes ya esta devengado y se cobra completo, asi que no hay nada que
        // ahorrar en ellas.
        BigDecimal interesProgramadoNoVencido = BigDecimal.ZERO;
        List<Cuota> pendientes = new ArrayList<>();
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() == EstadoPago.PAGADA) {
                continue;
            }
            pendientes.add(cuota);
            boolean vencida = cuota.getFechaVencimiento().isBefore(fecha);
            if (vencida) {
                capitalVencido = capitalVencido.add(cuota.capitalPendiente());
            } else {
                capitalNoVencido = capitalNoVencido.add(cuota.capitalPendiente());
                BigDecimal interesPendienteCuota = cuota.interesPendiente();
                // B204: lo que esta cuota iba a costar de interés si el cliente la pagaba en su
                // fecha. El IVA se calcula por cuota (iva(interesPendiente)), igual que en
                // calcularDeudaExigible: así la parte de lo ya pagado nunca se cuenta de nuevo y
                // una cuota PARCIAL no mete error (no existe una columna "tributos pagados").
                interesProgramadoNoVencido = interesProgramadoNoVencido
                        .add(interesPendienteCuota).add(iva(interesPendienteCuota));
                // D-88: todavía no está vencida (fecha efectiva), pero si su fecha TEÓRICA ya
                // pasó, su interés ya está devengado COMPLETO -- se cobra igual al cancelar, sin
                // esperar a que la mora la alcance (RN-19 solo corre la fecha de vencimiento, no
                // el devengamiento del interés, D-23). Evita el hueco de 1-3 días (D-88) donde
                // periodoActual ya movió el "período en curso" a la cuota siguiente y esta
                // todavía no es "vencida": sin este bucket, su interés desaparecía esos días.
                if (cuota.getFechaVencimientoTeorica().isBefore(fecha)) {
                    interesTeoricaPasada = interesTeoricaPasada.add(interesPendienteCuota);
                    ivaInteresTeoricaPasada = ivaInteresTeoricaPasada.add(iva(interesPendienteCuota));
                }
            }
        }

        PeriodoActual periodo = periodoActual(prestamo, fecha);
        BigDecimal interesDevengado = interesTeoricaPasada;
        BigDecimal ivaInteresDevengado = ivaInteresTeoricaPasada;
        if (corresponderProrratearInteresDelPeriodo(periodo)) {
            BigDecimal i = calcularTasaMensual(prestamo.getTasaTea());
            BigDecimal proporcion = dividir(BigDecimal.valueOf(periodo.diasTranscurridos),
                    BigDecimal.valueOf(periodo.diasDelPeriodo));
            BigDecimal interesPeriodoEnCurso = interesDelPeriodoOFallar(
                    redondear(capitalNoVencido.multiply(i).multiply(proporcion)));
            interesDevengado = interesDevengado.add(interesPeriodoEnCurso);
            ivaInteresDevengado = ivaInteresDevengado.add(iva(interesPeriodoEnCurso));
        }

        BigDecimal montoTotal = exigibleVencidas.getTotalGeneral().add(capitalNoVencido)
                .add(interesDevengado).add(ivaInteresDevengado);

        // B204 (15/11/2026, ítem 5 del informe de UX de Cancelación, decisión de Joel: "entra, no
        // se descarta"): el interés que el cliente NO va a pagar por cancelar hoy, que es lo que la
        // Ley 6624/2020 prohíbe cobrar hacia adelante (RF26 §6) puesto en un número. Lo que el plan
        // tenía programado para las cuotas no vencidas, con su IVA, menos el interés que esta
        // liquidación sí le cobra (el del período en curso, prorrateado por días, y el de las
        // cuotas con fecha teórica pasada de D-88 -- los dos salen de esas mismas cuotas).
        //
        // Las vencidas se cancelan solas de la cuenta: su interés entra COMPLETO en el total del
        // exigible, así que no aportan ahorro, y por eso no se suman ni se restan acá.
        //
        // Por qué no es "lo que faltaba del plan menos el total a cancelar" (la otra opción que se
        // midió el 15/11/2026): esa resta le descuenta la mora al ahorro -- con 5 cuotas vencidas
        // daba 44.559 donde el interés que de verdad no se cobra es 135.647, y con suficiente mora
        // habría llegado a cero. La mora es un castigo, no un ahorro menos. Sin cuotas vencidas las
        // dos fórmulas dan el MISMO número (medido: 436.897 y 407.851), así que esto no contradice
        // al ahorro del adelanto.
        //
        // Informativo: no interviene en ningún cobro. Acotado en cero por si un redondeo del
        // prorrateo lo empujara abajo (no debería: lo que se cobra es un subconjunto de lo
        // programado).
        BigDecimal interesFuturoNoCobrado = interesProgramadoNoVencido
                .subtract(interesDevengado).subtract(ivaInteresDevengado);
        if (interesFuturoNoCobrado.compareTo(BigDecimal.ZERO) < 0) {
            interesFuturoNoCobrado = BigDecimal.ZERO;
        }

        List<Cuota> cuotasResultantes = new ArrayList<>();
        for (Cuota cuota : pendientes) {
            Cuota resultante = new Cuota(cuota);
            resultante.setCapitalPagado(cuota.getCapitalProgramado());
            resultante.setInteresPagado(cuota.getInteresProgramado());
            resultante.setMoratorioPendiente(BigDecimal.ZERO);
            resultante.setPunitorioPendiente(BigDecimal.ZERO);
            resultante.setIvaMoratorioPendiente(BigDecimal.ZERO);
            resultante.setIvaPunitorioPendiente(BigDecimal.ZERO);
            resultante.setFechaCorteMora(fecha);
            resultante.setEstadoPago(EstadoPago.PAGADA);
            cuotasResultantes.add(resultante);
        }

        BigDecimal saldoCapitalFecha = capitalVencido.add(capitalNoVencido);

        return new ResultadoLiquidacion(TipoLiquidacion.TOTAL, saldoCapitalFecha, interesDevengado,
                ivaInteresDevengado, moratorioPendiente, ivaMoratorioPendiente, punitorioPendiente,
                ivaPunitorioPendiente, montoTotal, null, BigDecimal.ZERO, cuotasResultantes, null,
                interesFuturoNoCobrado);
    }

    // ----------------------------------------------------------------
    // calcularAnticipoParcial (Sec. 6.7)
    // ----------------------------------------------------------------

    /**
     * Precondicion (punto 1 de 6.7): no debe haber cuotas vencidas en
     * prestamo.getCuotas() (si las hay, se aplica antes distribuirPago). En
     * E1 esta precondicion la garantiza quien llama; no se valida aca.
     *
     * D-88 (regla de Joel, sí validada acá): tampoco debe haber una cuota no pagada con fecha
     * TEÓRICA ya pasada (aunque todavía no esté vencida por la fecha efectiva, RN-19) -- ese
     * período ya devengó su interés completo (ver calcularCancelacionTotal) y no se cobró
     * todavía; regenerar el plan por encima, sin cobrar antes esa cuota, perdería ese interés.
     * Se rechaza acá mismo (no en el llamador, a diferencia de la precondición de vencidas) para
     * que TODO camino que llegue a calcularAnticipoParcial quede protegido -- incluida la vista
     * previa de la ficha (monto 0), que no pasa por la validación de vencidas de ServicioCobranza.
     */
    public ResultadoLiquidacion calcularAnticipoParcial(Prestamo prestamo, BigDecimal monto, LocalDate fecha,
            Collection<LocalDate> feriados) {
        Cuota cuotaConTeoricaPasada = buscarCuotaConTeoricaPasada(prestamo, fecha);
        if (cuotaConTeoricaPasada != null) {
            throw new NegocioException("Primero hay que cobrar la cuota del "
                    + FormateadorFechas.comoDdMmAaaa(cuotaConTeoricaPasada.getFechaVencimientoTeorica())
                    + ".");
        }

        BigDecimal capitalPendienteTotal = BigDecimal.ZERO;
        for (Cuota cuota : prestamo.getCuotas()) {
            if (cuota.getEstadoPago() != EstadoPago.PAGADA) {
                capitalPendienteTotal = capitalPendienteTotal.add(cuota.capitalPendiente());
            }
        }

        BigDecimal i = calcularTasaMensual(prestamo.getTasaTea());
        PeriodoActual periodo = periodoActual(prestamo, fecha);

        BigDecimal interesDevengado = BigDecimal.ZERO;
        BigDecimal ivaInteresDevengado = BigDecimal.ZERO;
        if (corresponderProrratearInteresDelPeriodo(periodo)) {
            BigDecimal proporcion = dividir(BigDecimal.valueOf(periodo.diasTranscurridos),
                    BigDecimal.valueOf(periodo.diasDelPeriodo));
            interesDevengado = interesDelPeriodoOFallar(
                    redondear(capitalPendienteTotal.multiply(i).multiply(proporcion)));
            ivaInteresDevengado = iva(interesDevengado);
        }

        BigDecimal montoParaCapital = monto.subtract(interesDevengado).subtract(ivaInteresDevengado);

        // D-90: si lo que iría a capital cubre (o supera) todo el saldo pendiente, esto ya no es
        // un anticipo PARCIAL -- es una cancelación total disfrazada, que además dejaría un plan
        // degenerado (regenerarPlanAnticipo generaría una única cuota con saldo/capital/interés
        // en cero, D-88/D-89: el préstamo real que expuso este caso, CC-2026-00028, quedó ACTIVO
        // con saldo 0 para siempre porque confirmarAnticipoCapital nunca lo pasa a FINALIZADO).
        // Se rechaza acá, en el motor mismo (mismo criterio que D-88 con buscarCuotaConTeoricaPasada):
        // protege a cualquier llamador, incluida la vista previa de la ficha si algún día se la
        // llama con un monto real y no solo cero.
        if (montoParaCapital.compareTo(capitalPendienteTotal) >= 0) {
            throw new NegocioException("El monto cubre todo el saldo del préstamo. Use Cancelación total.");
        }

        BigDecimal nuevoSaldo = capitalPendienteTotal.subtract(montoParaCapital);

        Cuota cuotaBase = prestamo.getCuotas().get(0);
        BigDecimal cuotaConstante = cuotaBase.getInteresProgramado().add(cuotaBase.getCapitalProgramado());

        List<Cuota> nuevoPlan = regenerarPlanAnticipo(nuevoSaldo, cuotaConstante, i, periodo, feriados);

        BigDecimal montoTotal = interesDevengado.add(ivaInteresDevengado).add(montoParaCapital);

        return new ResultadoLiquidacion(TipoLiquidacion.PARCIAL, capitalPendienteTotal, interesDevengado,
                ivaInteresDevengado, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                montoTotal, montoParaCapital, nuevoSaldo, null, nuevoPlan,
                // B204: null a propósito, no cero. El "ahorro" de un adelanto es otra cuenta (se
                // compara contra el plan que QUEDA) y ya tiene su dueño en
                // ControladorCobranzaServlet.ahorroDeInteresesPorCuotas; un cero acá habría dejado
                // que alguna pantalla dijera "se ahorra 0" con la cuenta correcta en otro lado.
                null);
    }

    /**
     * Nuevo plan tras un anticipo (Sec. 6.7 punto 3): se mantiene la cuota
     * C constante; n' = techo(-ln(1 - S*i/C) / ln(1+i)). La primera cuota
     * conserva la fecha original del periodo en curso y su interes es solo
     * por los dias restantes de ese periodo (punto 4); las siguientes,
     * mes completo, igual que generarPlanPagos.
     */
    private List<Cuota> regenerarPlanAnticipo(BigDecimal saldo, BigDecimal cuotaConstante, BigDecimal i,
            PeriodoActual periodo, Collection<LocalDate> feriados) {
        double s = saldo.doubleValue();
        double c = cuotaConstante.doubleValue();
        double iDouble = i.doubleValue();
        double dentroDelLog = 1 - (s * iDouble / c);
        int nPrima = 1;
        if (dentroDelLog > 0 && dentroDelLog < 1) {
            nPrima = (int) Math.ceil(-Math.log(dentroDelLog) / Math.log(1 + iDouble));
        }
        if (nPrima < 1) {
            nPrima = 1;
        }

        List<Cuota> plan = new ArrayList<>();
        BigDecimal saldoActual = saldo;
        // D-86: si la cuota que contiene la fecha ya está PAGADA (pagada por adelantado), ese
        // período ya se cerró por completo -- la cuota nueva 1 no hereda ningún "resto" de días,
        // es un período entero normal (como cualquier k>1). D-87: en ese caso la cuota nueva 1
        // vence un mes DESPUÉS de la teórica de la cuota de referencia (el período que sigue),
        // no en la misma fecha -- esa misma fecha solo aplica cuando el anticipo cayó a mitad
        // del período de la cuota de referencia (D-80, todavía no pagada).
        boolean primeraCuotaEsPeriodoParcial = corresponderProrratearInteresDelPeriodo(periodo);
        LocalDate fechaTeoricaInicio = primeraCuotaEsPeriodoParcial
                ? periodo.cuotaActual.getFechaVencimientoTeorica()
                : periodo.cuotaActual.getFechaVencimientoTeorica().plusMonths(1);

        for (int k = 1; k <= nPrima; k++) {
            LocalDate fechaTeorica = (k == 1) ? fechaTeoricaInicio : fechaTeoricaInicio.plusMonths(k - 1);
            LocalDate fechaReal = fechaHabil(fechaTeorica, feriados);

            BigDecimal interes;
            if (k == 1 && primeraCuotaEsPeriodoParcial) {
                BigDecimal diasRestantes = BigDecimal.valueOf(periodo.diasDelPeriodo - periodo.diasTranscurridos);
                BigDecimal proporcion = dividir(diasRestantes, BigDecimal.valueOf(periodo.diasDelPeriodo));
                interes = redondear(saldoActual.multiply(i).multiply(proporcion));
            } else {
                interes = redondear(saldoActual.multiply(i));
            }

            BigDecimal capital = (k == nPrima) ? saldoActual : cuotaConstante.subtract(interes);
            BigDecimal ivaCuota = iva(interes);
            BigDecimal total = interes.add(capital).add(ivaCuota);
            BigDecimal saldoPosterior = saldoActual.subtract(capital);

            Cuota cuota = new Cuota();
            cuota.setNumeroCuota(k);
            cuota.setFechaVencimiento(fechaReal);
            cuota.setFechaVencimientoTeorica(fechaTeorica);
            cuota.setCapitalProgramado(capital);
            cuota.setInteresProgramado(interes);
            cuota.setTributosProgramados(ivaCuota);
            cuota.setTotalProgramado(total);
            cuota.setSaldoCapitalPosterior(saldoPosterior);
            cuota.setEstadoPago(EstadoPago.PENDIENTE);
            plan.add(cuota);

            saldoActual = saldoPosterior;
        }
        return plan;
    }
}
