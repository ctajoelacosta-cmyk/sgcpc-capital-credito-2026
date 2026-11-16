# Diseño de comprobantes – SGCPC (E8)

Aprobado por Joel Acosta el 28/09/2026. Este documento es la especificación de los 5 comprobantes Jasper. Los ejemplos usan datos reales de la base (CC-2026-90001, sesión de caja del 27/09/2026) y están verificados al guaraní: el reporte generado debe dar **exactamente** esos números.

Reglas generales para los 5:

- Encabezado con los datos de la empresa leídos de parámetros, no fijos en el .jrxml: Capital Crédito Paraguay S.R.L., RUC 80123456-7, Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción, Tel. (021) 941 890, WhatsApp (0972) 552 233.
- Montos en guaraníes, sin decimales, con separador de miles con punto (1.815.826).
- Fechas en formato dd/mm/aaaa. Los montos en letras van en español ("Ciento noventa y tres mil setecientos setenta y nueve guaraníes").
- Todos se pueden **reimprimir** desde la ficha del préstamo o la caja. La reimpresión es idéntica al original: no se recalcula nada, se lee de lo guardado.
- Formato A4 vertical.

---

## 1. Factura (modelo Financiera Paraguayo Japonesa, D-78)

Se emite solo si hay conceptos gravados (interés, moratorio, punitorio). Siempre CONTADO. El capital **no** va en la factura.

**Decisión de Joel:** una línea por concepto y por cuota.

Ejemplo: factura 001-001-0000003 (cobro REC-2026-000005 de CC-2026-90001).

```
CAPITAL CRÉDITO PARAGUAY S.R.L.          | TIMBRADO N° <fiscal.timbrado_numero>
Préstamos personales                     | Vigencia: <desde> al <hasta>
Tte. Rojas Silva 1245 c/ Sgto. Gauto     | RUC: 80123456-7
Barrio Obrero – Asunción                 | FACTURA
Tel. (021) 941 890 – WA (0972) 552 233   | N° 001-001-0000003

Fecha de emisión: 27/09/2026        Condición de venta: CONTADO [X]  CRÉDITO [ ]
Nombre o razón social: Cliente Prueba Mora E7 Dos
RUC / C.I.: 9990002                 Operación: CC-2026-90001
Dirección: <dirección del cliente>  Recibo asociado: REC-2026-000005

Cant | Descripción                      | P. Unit | Exentas | 5% |   10%
  1  | Interés compensatorio cuota 1/12 |  66.388 |         |    |  66.388
  1  | Interés moratorio cuota 1/12     |   6.360 |         |    |   6.360
  1  | Interés punitorio cuota 1/12     |   1.909 |         |    |   1.909
  1  | Interés compensatorio cuota 2/12 |  61.442 |         |    |  61.442
  1  | Interés moratorio cuota 2/12     |     988 |         |    |     988
  1  | Interés punitorio cuota 2/12     |     296 |         |    |     296
  1  | Interés compensatorio cuota 3/12 |  56.396 |         |    |  56.396
SUBTOTALES                                         0    0      193.779
TOTAL A PAGAR: Gs. 193.779
(Ciento noventa y tres mil setecientos setenta y nueve guaraníes)

LIQUIDACIÓN DEL IVA:  (5%) 0    (10%) 17.617    TOTAL IVA 17.617

Tipo de transacción: Prestación de servicios
ORIGINAL: Cliente      DUPLICADO: Archivo tributario
```

Reglas:

- Cada línea = neto del concepto + su IVA (IVA incluido). Ejemplo: 60.353 + 6.035 = 66.388.
- Una línea se omite si su monto es 0.
- La suma de las líneas = `total_gravada_10` de la factura guardada. La liquidación del IVA = `iva_10` guardado (D-85). No recalcular.
- El timbrado y su vigencia se leen de los parámetros fiscales **vigentes a la fecha de emisión**.

---

## 2. Recibo de dinero

Documento interno, no fiscal. Incluye **todo**, también el capital. Se emite en todo cobro, anticipo y cancelación.

Ejemplo: REC-2026-000005.

```
CAPITAL CRÉDITO PARAGUAY S.R.L. – RUC 80123456-7
Tte. Rojas Silva 1245 c/ Sgto. Gauto – Barrio Obrero – Asunción

RECIBO DE DINERO  N° REC-2026-000005

Fecha: 27/09/2026 22:53            Operación: CC-2026-90001
Cliente: Cliente Prueba Mora E7 Dos        C.I.: 9.990.002
Factura asociada: 001-001-0000003

Cuota | Capital  | Interés | Morat. | Punit. | IVA    | Total
1/12  | 223.531  | 60.353  | 5.782  | 1.735  | 6.787  | 298.188
2/12  | 228.028  | 55.856  |   898  |   269  | 5.703  | 290.754
3/12  | 232.615  | 51.269  |     0  |     0  | 5.127  | 289.011
TOTAL | 684.174  |167.478  | 6.680  | 2.004  |17.617  | 877.953

Efectivo recibido: 877.953      Aplicado: 877.953
Son: Ochocientos setenta y siete mil novecientos cincuenta y tres guaraníes.

Saldo de capital después del pago: 2.315.826 Gs.
Cuotas pendientes: 9     Próximo vencimiento: cuota 4/12 – 19/11/2026

Cajero/a: Sofía Martínez

____________________        ____________________
  Firma del cajero            Firma del cliente

ORIGINAL: Cliente                          COPIA: Capital Crédito
```

Controles: la suma de capital, interés, moratorio, punitorio e IVA = aplicado. Interés + moratorio + punitorio + IVA = total de la factura asociada.

Variantes:

- **Sin factura** (no hay conceptos gravados): "Factura asociada: no corresponde (sin conceptos gravados)".
- **Pago parcial con promesa:** agrega "Promesa de pago: dd/mm/aaaa por X Gs." y marca la cuota como "parcial".
- **Anticipo de capital:** en lugar de la tabla de cuotas: "Anticipo de capital: X – Interés del período en curso: Y – IVA: Z". Debajo, un resumen del plan regenerado: cuotas restantes, misma cuota y nuevo último vencimiento.
- **Cancelación total:** leyenda "PRÉSTAMO CANCELADO". Va junto con la constancia (3).
- **Con vuelto:** "Efectivo recibido: 1.900.000 – Vuelto: 84.174 – Aplicado: 1.815.826".

---

## 3. Constancia de cancelación total

Se emite en cada cancelación total. **Numeración propia CAN-aaaa-nnnnnn**: confirmar si existe un numerador o crearlo (si hace falta cambio de esquema, avisar antes). Se reimprime desde la ficha del préstamo FINALIZADO.

Ejemplo: cancelación de CC-2026-90001 del 27/09/2026.

```
CAPITAL CRÉDITO PARAGUAY S.R.L. – RUC 80123456-7
Tte. Rojas Silva 1245 c/ Sgto. Gauto – Barrio Obrero – Asunción
Tel. (021) 941 890 – WhatsApp (0972) 552 233

CONSTANCIA DE CANCELACIÓN TOTAL DE PRÉSTAMO
N° CAN-2026-000002

Asunción, 27 de septiembre de 2026

Por la presente, CAPITAL CRÉDITO PARAGUAY S.R.L. deja constancia de que
el/la Sr./Sra. CLIENTE PRUEBA MORA E7 DOS, con C.I. N° 9.990.002, ha
CANCELADO EN SU TOTALIDAD el préstamo que se detalla, no registrando
deuda pendiente por esta operación a la fecha.

DATOS DEL PRÉSTAMO
Operación:               CC-2026-90001
Monto desembolsado:      3.000.000 Gs.
Fecha de desembolso:     19/07/2026
Plazo original:          12 cuotas mensuales
Fecha de cancelación:    27/09/2026

LIQUIDACIÓN DE LA CANCELACIÓN
Cuotas vencidas impagas (con mora e IVA):          0 Gs.
Capital no vencido:                        1.815.826 Gs.
Interés del período en curso:                      0 Gs.
IVA sobre intereses:                               0 Gs.
TOTAL CANCELADO:                           1.815.826 Gs.

Conforme a la Ley N° 6624/2020, no se cobraron intereses futuros ni
penalidades por cancelación anticipada.

Comprobantes: Recibo REC-2026-000007 – Factura: no corresponde (sin
conceptos gravados).
Pagaré: se devuelve al cliente el pagaré original firmado.  [ ]

____________________________        ____________________________
Ramón Benítez                        Recibí conforme (cliente)
Representante legal                  Firma y aclaración
Capital Crédito Paraguay S.R.L.

Emitido por: Sofía Martínez (cajera) – 27/09/2026 22:55
```

Reglas:

- La liquidación se lee de lo guardado en la cancelación (`liquidaciones_anticipadas` o equivalente), no se recalcula.
- La casilla del pagaré se marca a mano.
- El representante legal sale de parámetros (Ramón Benítez, C.I. 2.845.117).

---

## 4. Comprobante de desembolso y cronograma de pagos

Dos partes en el mismo PDF: el comprobante de entrega (página 1) y el cronograma (página 2). **Numeración DES-aaaa-nnnnnn** — numerador `DESEMBOLSO` creado en D-93 (E8 Bloque 1).

Ejemplo: **CC-2026-00002** (Mirtha Lucia Lopez Rojas), no CC-2026-90001. CC-2026-90001 se creó por SQL directo (D-89) sin pasar por `/desembolso/{id}/confirmar` — no tiene fila en `desembolsos` (verificado en D-93: 0 filas), así que no hay un desembolso real que reimprimir para ese préstamo. CC-2026-00002 sí: es el único desembolso real de una prueba manual de Joel (E5/E6) que no es de humo — desembolso id 1, DES-2026-000001, cajera Sofía Martínez, 25/09/2026 23:45.

**Página 1: comprobante de entrega**

```
CAPITAL CRÉDITO PARAGUAY S.R.L. – RUC 80123456-7
Tte. Rojas Silva 1245 c/ Sgto. Gauto – Barrio Obrero – Asunción

COMPROBANTE DE DESEMBOLSO  N° DES-2026-000001

Fecha: 25/09/2026 23:45              Operación: CC-2026-00002
Cliente: Mirtha Lucia Lopez Rojas          C.I.: 4.150.164

CONDICIONES DEL PRÉSTAMO
Monto aprobado:                    2.000.000 Gs.
Gastos y comisiones:                       0 Gs.
MONTO ENTREGADO EN EFECTIVO:       2.000.000 Gs.

Tasa efectiva anual (TEA):   27,00 %
Tasa mensual equivalente:     2,01 %
Sistema de amortización:     Francés (cuota fija)
Plazo:                       12 cuotas mensuales
Cuota (capital + interés):   189.256 Gs. + IVA s/ interés
Primer vencimiento:          26/10/2026
Último vencimiento:          27/09/2027

Total de intereses:            271.072 Gs.
IVA s/ intereses (10%):         27.108 Gs.
TOTAL A PAGAR (sin mora):    2.298.180 Gs.

En caso de atraso se aplican interés moratorio (a la tasa pactada) e
interés punitorio (30% del moratorio), más IVA.

Recibí conforme la suma de DOS MILLONES DE GUARANÍES (2.000.000 Gs.) en
efectivo, en concepto del préstamo arriba detallado, y me comprometo a
pagarlo según el cronograma adjunto.

____________________________        ____________________________
Firma del cliente                    Cajero/a: Sofía Martínez
Aclaración: ................

ORIGINAL: Capital Crédito (legajo)           COPIA: Cliente
```

**Página 2: cronograma de pagos**

```
CRONOGRAMA DE PAGOS – CC-2026-00002 – Mirtha Lucia Lopez Rojas

Cuota|Vencimiento | Capital  | Interés | IVA    | A pagar  | Saldo cap.
  1  | 26/10/2026 | 149.020  | 40.236  | 4.024  | 193.280  | 1.850.980
  2  | 25/11/2026 | 152.018  | 37.238  | 3.724  | 192.980  | 1.698.962
  3  | 28/12/2026 | 155.077  | 34.179  | 3.418  | 192.674  | 1.543.885
  4  | 25/01/2027 | 158.196  | 31.060  | 3.106  | 192.362  | 1.385.689
  5  | 25/02/2027 | 161.379  | 27.877  | 2.788  | 192.044  | 1.224.310
  6  | 29/03/2027 | 164.626  | 24.630  | 2.463  | 191.719  | 1.059.684
  7  | 26/04/2027 | 167.938  | 21.318  | 2.132  | 191.388  |   891.746
  8  | 25/05/2027 | 171.316  | 17.940  | 1.794  | 191.050  |   720.430
  9  | 25/06/2027 | 174.763  | 14.493  | 1.449  | 190.705  |   545.667
 10  | 26/07/2027 | 178.278  | 10.978  | 1.098  | 190.354  |   367.389
 11  | 25/08/2027 | 181.865  |  7.391  |   739  | 189.995  |   185.524
 12  | 27/09/2027 | 185.524  |  3.732  |   373  | 189.629  |         0
TOTAL|            |2.000.000 |271.072  |27.108  |2.298.180 |

Los vencimientos que caen en sábado, domingo o feriado se corren al día
hábil siguiente. Puede cancelar anticipadamente en cualquier momento sin
penalidad (Ley 6624/2020).
```

Reglas:

- Las fechas son las **efectivas** del plan guardado (con feriados, D-88). En el ejemplo solo se corrieron los fines de semana; el sistema debe usar sus feriados reales.
- "A pagar" = capital + interés + IVA del interés. Los montos se leen del plan guardado, no se recalculan.
- Si el préstamo tuvo anticipos, el cronograma impreso desde la ficha muestra el **plan vigente** e indica "Plan versión N – regenerado el dd/mm/aaaa por anticipo de capital".
- **Préstamo sin fila en `desembolsos`** (como CC-2026-90001, cargado por SQL directo sin pasar por `/desembolso/{id}/confirmar`): la reimpresión muestra un mensaje claro, "Este préstamo no tiene comprobante de desembolso registrado", nunca un error (decisión de Joel, D-93).

---

## 5. Arqueo y cierre de caja

Se imprime al cerrar la caja y se puede reimprimir desde el historial de sesiones.

**Decisión de Joel:** sin conteo por billetes; solo el total contado.

Ejemplo: sesión del 27/09/2026 de Sofía Martínez.

```
CAPITAL CRÉDITO PARAGUAY S.R.L. – RUC 80123456-7

ARQUEO Y CIERRE DE CAJA  – Sesión N° <id>

Cajero/a: Sofía Martínez
Apertura: 27/09/2026 22:50         Cierre: 27/09/2026 22:56

RESUMEN                                    Cant.          Monto
Fondo inicial                                               0
(+) Cobros de cuotas                         1        877.953
(+) Anticipos de capital                     1        500.000
(+) Cancelaciones totales                    1      1.900.000
(−) Vueltos entregados                       1        −84.174
(−) Desembolsos                              0              0
SALDO TEÓRICO                                       3.193.779
EFECTIVO CONTADO                                    3.193.779
DIFERENCIA                                                  0
Motivo de la diferencia: —

COMPOSICIÓN DE LO COBRADO (aplicado 3.193.779)
Capital                                             3.000.000
Intereses compensatorios                              167.478
Intereses moratorios                                    6.680
Intereses punitorios                                    2.004
IVA                                                    17.617

COMPROBANTES EMITIDOS
Recibos:      REC-2026-000005 a REC-2026-000007  (3)
Facturas:     001-001-0000003                    (1)  Total 193.779
Constancias:  CAN-2026-000002                    (1)
Desembolsos:  —

DETALLE DE MOVIMIENTOS
22:53  Cobro           CC-2026-90001   REC-000005     877.953
22:54  Anticipo cap.   CC-2026-90001   REC-000006     500.000
22:55  Cancelación     CC-2026-90001   REC-000007   1.900.000
22:55  Vuelto          CC-2026-90001   REC-000007     −84.174

____________________________        ____________________________
Entregué: Sofía Martínez             Recibí: Ramón Benítez
Cajera                               Gerente

Impreso: 27/09/2026 22:56
```

Reglas:

- La clasificación Cobro, Anticipo y Cancelación sale de la operación asociada al movimiento (hoy el tipo es "Cobro" para las tres y las distingue la observación, D-89). Usar una fuente confiable, no parsear el texto de la observación.
- Composición: la suma de capital, intereses e IVA = cobros + anticipos + cancelaciones − vueltos.
- La composición de lo cobrado es la base del reporte "intereses cobrados" de E8.
