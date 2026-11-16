# Recorrido operativo — qué es esto

## PUNTO DE RETOMADA (actualizado 16/11/2026: **la fase 2 está CERRADA** y lo que corre es la **entrega del libro**; el manual de usuario queda en pausa)

**Leer esto primero.** Reemplaza a los puntos de retomada anteriores, que quedan más abajo como
historial.

### Dónde quedamos (16/11/2026): no queda nada abierto del plan

**Las once pantallas, cerradas y probadas en pantalla.** **Las tres transversales, resueltas**:

| Transversal | Estado |
|---|---|
| **B159** — las etiquetas de estado con color | **EN PAUSA** por decisión de Joel: usaba la paleta nueva, que él **descartó** el mismo día (**B238**). En su fila quedó medido todo para retomarla, incluidos los dos hallazgos que **no** dependen del color: las **tres copias de la regla** que ya no coinciden y el **badge fijo** de la columna *Decisión* de Aprobaciones |
| **B160** — los montos a la derecha | **CERRADA y PROBADA**, las 4 tandas: **27 pantallas y 205 usos** de `.cc-monto`, una sola clase, y **ninguna** celda de cifras con el `text-right` de Bootstrap |
| **B158** — retirar `/api_capital` | **HECHA y VERIFICADA**: Joel la retiró del Tomcat y se midió en vivo — `/api_capital/` **404**, `/sgcpc/acceso` **200**, `/sgcpc-api/api/panel` **401**, sin restos en `webapps` ni en el `work` de Tomcat |

**EL HUMO FINAL YA SE CORRIÓ Y DIO VERDE: LA FASE 2 ESTÁ CERRADA.**

**La corrida** (16/11/2026 12:48, la corrió Joel): **1295/1295**, `exit 0`, **0 `FALLO`** y
**ninguna línea de `PRECONDICIÓN`** — los tres controles de B154/B113, verificados sobre
`..\pruebas\humo_2026-11-16.txt`. Es la corrida **posterior** a las cuatro tandas de **B160**, a
**B240**, a **B241** y al retiro de **B158**, que era exactamente lo que faltaba medir.

- **El cambio de una línea del guión que autorizó B160 quedó bien**: la única verificación que
  podía caer —el fondo sugerido de `/caja` contra la suma de los montos de `/desembolso`— sigue en
  verde, **2.000.000 / 2.000.000**.
- **Nada más del guión toca lo que se cambió**: no raspa ningún `<th>`, **no pide `/prestamos` ni
  una vez** (así que la paginación de B241 le es invisible), el único lugar donde lee la tarjeta
  del usuario es un **diagnóstico** (línea 4251, `pie-usuario-nombre`) que B240 conservó, y
  `api_capital` aparece **0** veces.
- **El archivo de la corrida de la mañana se sobreescribió**: la anterior a B160 usaba el **mismo
  nombre** `humo_2026-11-16.txt`. Las dos dieron 1295/1295 y `exit 0`, así que no se perdió
  ninguna medición, pero **dos corridas del mismo día van con sufijo** (`humo_2026-11-16b.txt`).

El comando que muestra el exit code del humo y no el del `tail`:

```bash
pruebas/humo_http.sh > ../pruebas/humo_AAAA-MM-DD.txt 2>&1; CODIGO=$?;        tail -3 ../pruebas/humo_AAAA-MM-DD.txt; echo "== exit del humo: $CODIGO =="
```

**Qué mirar en cada corrida** (B154/B113, regla permanente): que el total sea **igual o mayor** a 1295, que haya **0
`FALLO`** y que **no aparezca ninguna línea de `PRECONDICIÓN`** — un total más bajo sin fallas
significa que alguna verificación se salteó en silencio, que es el defecto que esas dos reglas
existen para atrapar.

**Al retomar, en este orden:**

1. **La entrega del libro** (decisión de Joel del 16/11/2026, lo que corre hoy): va **solo**
   `sgcpc.war` y la base; `sgcpc-api.war` **no** se entrega todavía. Está armada en
   `entrega_libro_2026-11-16/` y el procedimiento completo, con lo medido y lo que quedó sin
   verificar, en `docs/entregas/ENTREGA_SERVIDOR.md` → *"ENTREGA DEL LIBRO (16/11/2026)"*.
   **El `.war` se rehízo el mismo día para GIOVANNI** (segunda decisión de Joel): lleva la
   conexión **lista adentro** —`sgcpc_db_pruebas`, usuario `postgres`, contraseña escrita,
   `entorno=produccion`— porque la vez pasada buscaba una base con otro nombre y no conectó. La
   deuda de la contraseña adentro del `.war` quedó en **B242**, para después de la defensa.
   **Lo que falta y es de Joel**: sacar la copia de la base con el comando de
   `FALTA_LA_COPIA_DE_LA_BASE.txt` (`pg_dump --create` + el `sed` ya probado; a Claude le está
   denegado escribir `.sql`) y, si quiere la prueba de punta a punta, restaurarla y levantar el
   `.war` contra ella — eso último lo prohíbe el DOGMA 2 para Claude.
2. **El manual de usuario queda EN PAUSA** por la misma decisión. El informe de qué quedó
   desactualizado, con el trabajo estimado, está en
   `docs/recorrido/manual_usuario/00_PENDIENTE_ACTUALIZACION.md` — lo único que hoy **miente** son
   5 textos (el orden de los bloques del Panel, B228, y 4 etiquetas de B107); el resto son
   faltantes (B234, B235, B240, y Préstamos/Informes/Seguridad, que el manual no cubre).
3. **Las tres redes nuevas de esta pasada**, por si algo las hace fallar y no se entiende por qué:
   - **`MontosAlineadosTest`** (3): `.cc-monto` definida **una sola vez** y con sus **dos**
     declaraciones, ninguna celda de cifras con `text-right`, y la **cuenta de cada una de las 27
     pantallas**. Si una tanda nueva agrega celdas, se actualiza el número ahí y se dice en el
     resumen.
   - **`EtiquetasHtmlDePantallaTest`** (2): reconstruye el HTML que cada JSP va a servir y falla si
     un atributo queda como **texto visible** o si una etiqueta tiene **otra adentro**. Nació del
     botón *PDF* de 5 informes que salía como texto (defecto de B209).
   - **`PaginacionPantallaTest`** (7): la última lee el fuente de los controladores y falla si un
     `calcularTotales` queda **después** de `PaginacionPantalla.paginar` **en el mismo método** —
     la trampa de B191 y B210.
4. **Ocho decisiones anotadas, ninguna urgente**: **B225** (que `ControladorReportesServlet`
   delegue en `PaginacionPantalla` en vez de tener su copia), **B226** (el buscador en tabla busca
   solo en la página visible, en 8 pantallas), **B227** (la opción A del tope legal), **B233**
   (decidido: **no se hace**), **B237** (dos avisos ámbar apilados arriba del panel), **B239** (el
   vacío de `/solicitudes` dice que no hay solicitudes cuando lo que pasa es que no son del
   usuario), **B159** (en pausa) y **B242** (cambiar la contraseña de `sgcpc_user`, que sigue
   escrita dentro del `api_capital.war` respaldado). **Fechadas para después de la defensa**:
   **B227**, **B236** (el ítem 2 del Panel) y **B242**.
5. **Lo que quedó "no visto" y por qué**, con el motivo leído del dato y no supuesto: de **B160**,
   *Informes → Incobrables* (hoy no hay préstamos incobrables, así que su tabla no se dibuja); de
   **Seguridad**, **B217** (los 24 feriados están activos: desplegar *"Ya pasados"*, desactivar uno
   de los 10 y volver a activarlo), **B218** (`credito.redondeo_efectivo` no tiene historial:
   ponerle **1.000**, confirmar y **volverlo a 50 enseguida**, sin cobrar nada en el medio) y los
   dos escenarios de **B223/B224** (`limites_tasa_usura` tiene una sola fila); y del **Panel**,
   **B229** (ningún contador llega a 4 cifras) y **B230** (exige hacer fallar la base). Todos
   cubiertos por JUnit vistos en rojo.
6. **Dos trampas operativas que costaron mediciones equivocadas en esta pasada**, y que conviene
   tener presentes:
   - **El vigilante de archivos de NetBeans se saltea archivos.** `mvn test` **no** copia
     `src/main/webapp` a `target/sgcpc` (no hay ningún plugin que lo haga), lo copia ese vigilante,
     y cuando cambian varios juntos **deja alguno sin copiar** — el peor caso medido fue **11 de
     14**. Después de editar JSP o CSS se compara `src` contra `target/sgcpc` archivo por archivo,
     y el que falte se destapa **reescribiéndolo con los mismos bytes**
     (`d = open(p,'rb').read(); open(p,'wb').write(d)`), **de a uno**.
   - **Después de tocar un `.css`, medir con la caché del navegador limpia**: el `?v=` solo cambia
     al redesplegar, así que una medición puede decir que una regla "no se aplica" cuando Tomcat ya
     la está sirviendo bien.
7. Si algo de lo probado falló, eso primero.

#### La caja del 13/11 — CERRADA el 15/11/2026 (queda como el procedimiento verificado para cerrar una caja)

**Ya no hay nada abierto**: Joel cerró la sesión **136** el 15/11/2026 con **3.019.700 Gs.** y diferencia **0** (verificado en la base). Lo de abajo queda porque es el **procedimiento**, verificado en las tres capas, para cerrar cualquier caja. Era: sesión **136**, abierta el **13/11/2026 17:44**, cajera **Sofía Martínez**
(`cajera`), fondo inicial **5.000.000 Gs.**, con **5 movimientos** (un desembolso de 3.000.000, dos
cobros de 500.000 y 550.000, y dos vueltos de 24.550 y 5.750).

**El saldo teórico es 3.019.700 Gs.** — `saldo_inicial + Σ(signo × monto)`, la misma cuenta que
hace `ServicioCaja.cerrarCaja`. La pantalla lo muestra en *"Saldo teórico actual (efectivo)"*.

**Quién la cierra: Sofía, la cajera.** Las tres capas dicen lo mismo:

| Capa | Qué dice |
|---|---|
| La JSP | `caja/ficha.jsp:247` envuelve el bloque *"Cierre y rendición"* en `if (!esGerenteCaja)` |
| El permiso de la ruta | `/caja` → `ABRIR_CERRAR_CAJA` (`FiltroAutorizacion:61`) |
| El nivel por perfil | cajera **TOTAL**, gerente **CONSULTA**, oficial **NINGUNO** — y un POST exige TOTAL, así que el gerente la ve pero recibe **403** si la postea |

**Los pasos, en pantalla**: entrar como **Sofía** a *Desembolso y caja → Caja* → bloque *"Cierre y
rendición"* → escribir el efectivo contado en *"Efectivo contado"* → el renglón de abajo muestra en
vivo *"Saldo teórico: 3.019.700 Gs. · Coincide"* (o la diferencia) → *"Cerrar caja"* → el cartel
pregunta *"¿Cerrar caja con 3.019.700 Gs. contados? Coincide con el saldo teórico."*, con el foco
en cancelar → *"Sí, cerrar caja"*.

- **Con 3.019.700 no hace falta motivo.** Con cualquier otro número sí: `caja.diferencia_tolerada`
  está en **0**, así que toda diferencia exige un motivo de **10 caracteres** reales, y la pantalla
  lo vuelve obligatorio en vivo.
- **El tope de tipeo es 500.000.000** (`caja.monto_maximo_arqueo`): por encima, el servidor rechaza
  con *"Revise si tipeó ceros de más."*.
- **Que la caja sea de otro día NO bloquea el cierre** (verificado: `cerrarCaja` no mira la fecha);
  lo que bloquea es **operar** — por eso hoy no se puede desembolsar ni cobrar hasta cerrarla.
- **Qué deja**: la sesión en CERRADA con su arqueo, un movimiento `RENDICION_GERENTE` por el
  efectivo rendido, y una fila de auditoría `CERRAR_CAJA` con teórico, físico y diferencia. Si
  queda diferencia, aparece en *"Diferencias de arqueo sin revisar"* para que el **gerente** la
  marque revisada.
- **Después hay que abrir la caja de hoy** para poder desembolsar o cobrar.

**Lo que queda para el final, con todas las pantallas juntas** (decisión de Joel, no se tocan dentro
de la tanda de una pantalla sola): **B159** (las píldoras de estado, con color y con el mismo
criterio en todo el sistema — el ítem 4 de Aprobaciones se sumó acá) y **B160** (los montos de los
listados, alineados a la derecha). Aparte: **B158** (retirar `/api_capital`, la API vieja del
parcial, que sigue desplegada) y la **caja ABIERTA del 13/11 17:44** (`cajera`, 5.000.000), que hace
que el humo aborte 4 bloques y se quede en ~740 verificaciones en vez de ~1153 — no se cierra una
caja que no abrió esta sesión.

#### Informe de UX de Formalización (14/11/2026, 10 líneas, nada tocado)

Pantallas miradas: la bandeja (`/formalizacion`, con sus **3** secciones), *"Formalizar"*
(`nueva.jsp`), la **ficha del préstamo** y la **devolución del pagaré**. Ordenado por impacto:

1. **Las 3 secciones traen todo y no se pueden ordenar ni paginar, y ninguna dice cuántas son.**
   Medido con un `SELECT` de solo lectura el 14/11: **96** para formalizar, **18** pendientes de
   desembolso, **48** con el pagaré pendiente. Es exactamente lo que ya se resolvió en Clientes
   (B148/B157), Solicitudes (B167) y Aprobaciones (B175), con el fragmento compartido
   `plantilla/paginacion.jsp` y las listas blancas en el servicio.
2. **"Anular préstamo" se despliega dentro de la pantalla y su confirmación dice solo *"No se puede
   deshacer."***: no nombra la operación, ni el cliente, ni el monto. Es una acción irreversible y de
   gerente: le corresponde **ventana** (como B171/B155/B168) y una confirmación con los datos
   (B4/B43).
3. **El *"Volver a la bandeja"* pierde la búsqueda**, en *"Formalizar"* y en la ficha: son enlaces
   pelados a `/formalizacion`. Es la familia B147/B163/B170, que ya tiene su mecanismo de sesión.
4. **En *"Pendientes de desembolso"* la columna *"Checklist"* dice *"Incompleto"* sin decir qué
   falta**, así que hay que abrir cada préstamo para saberlo — contra B88/B89 (un aviso nombra lo que
   falta) y es el mismo hueco que B100 dejó anotado para la tarjeta del panel.
5. **La sección principal no tiene título propio ni contador, y las otras dos sí tienen título**; y
   con una búsqueda que coincide en una sola sección, las otras dos muestran *"No se encontraron…"*
   al mismo tiempo: **tres vacíos** que se leen como "no hay nada".
6. **En *"Formalizar"*, el motivo del bloqueo está lejos del botón.** *"Supera el límite"* y *"sin
   comprobante de ingreso"* se muestran en rojo **dentro de la tabla de condiciones**, arriba, y
   *"Generar documentos"* no dice nada: falta **un solo renglón de motivos** junto al botón
   (B79/B95). No se trata de deshabilitarlo — eso ya lo decidiste así el 19/10.
7. **Tres acciones parecidas en la ficha** —*"Generar los PDF que faltan"*, *"Re-emitir los PDF (sin
   cambiar plan)"* y *"Regenerar documentos"*— y **solo la última** tiene su ⓘ que la explica.
8. **El paso 3 de la devolución del pagaré confirma con *"No se puede deshacer."***, sin nombrar la
   operación ni el cliente, a diferencia del **paso 1**, que sí los nombra. Dos confirmaciones de la
   misma pantalla con dos criterios.
9. **La ficha del préstamo es muy larga** (condiciones, documentos generados, checklist, plan de
   12-24 cuotas, regenerar, anular) **sin índice ni barra fija**: la acción que importa queda fuera
   de la pantalla. Es lo que B152 resolvió en la ficha del cliente.
10. **Los dos transversales también aplican acá**: los montos no están a la derecha en las 3 tablas
    ni en el plan de pagos (**B160**), y la píldora de estado de la ficha es **gris para todos los
    estados** (**B159**).

#### Informe de UX de Informes (15/11/2026, 10 líneas, nada tocado)

**Alcance**: la pantalla es `/reportes` — el **índice** (`indice.jsp`, con su buscador y sus
píldoras de categoría) y los **21 reportes** que cuelgan de él, incluido *Estado de cuenta del
cliente*, que tiene dos pantallas (búsqueda y ficha). **Medido**: el índice dibuja 21 tarjetas y el
oficial ve 5 — los dos números que la pantalla dice son correctos. No entra acá lo que ya se revisó
en otras pantallas aunque se parezca (la bandeja de `/mora`, el historial de la ficha de cobro), ni
los `.jrxml` de los PDF (eso es `sgcpc-reportes-jasper`). Ordenado por impacto:

1. **Auditoría abre truncada y el aviso pide algo imposible.** El rango por defecto es **un solo
   día** (`desde` cae en `hasta`, que es hoy), y hoy hay **2.226** registros contra un tope de
   **500** (`ServicioReportes.LIMITE_AUDITORIA`), así que la primera pantalla ya sale cortada con
   *"Se muestran las primeras 500 de 2.226 registros; acotá el filtro"* — y el filtro **ya está en
   un día**. Como no hay paginación, de la fila 501 en adelante **no se llega desde la pantalla**:
   quedan 1.726 registros de hoy sin forma de verse. Los totales sí se calculan sobre el período
   completo (E-15), así que lo que falta es **llegar** al detalle, no contarlo.
2. **Ninguno de los 21 reportes paginia.** Es lo único que quedó afuera de todo lo que se hizo en
   las otras siete pantallas (B157, B175, B183, B191, B197): acá *Cartera* dibuja **190** filas de
   una sola vez, *Vencimientos* con un mes de rango **292**, y *Auditoría* **500**. El mecanismo ya
   existe y es reusable (`plantilla/paginacion.jsp`, con los atributos opcionales que agregó B183).
3. **Y ninguno tiene el contador en el título, salvo Auditoría.** En las demás pantallas Joel lo
   pidió arriba y en todas quedó así (*"69 préstamos en mora"*, *"Sesiones cerradas (141)"*); acá
   los títulos son *"Vencimientos"*, *"Cartera"*, *"Cobros"* a secas, y la cantidad hay que ir a
   buscarla al **pie de la tabla**, en la fila de totales, después de bajar 190 o 292 filas.
   *Auditoría* es la excepción y ya tiene la forma correcta (*"N registros mostrados"*).
4. **El estado vacío sale en VERDE en 9 reportes, y en 7 de ellos el vacío es mala noticia.**
   *"No hay préstamos activos en este momento"* (Cartera: el negocio parado), *"No hay cuotas que
   venzan en ese rango"* (Vencimientos: no entra plata), *"No hay gestiones registradas en ese
   rango"* (cobranza sin trabajar), *"No hay clientes nuevos en ese rango"*, *"No hay consultas al
   buró…"*, *"No hay préstamos cancelados…"*, *"No hay promesas incumplidas…"* — todas en
   `alert-success`. El verde solo corresponde en **Morosidad** e **Incobrables**, donde cero sí es
   buena noticia.
5. **Ningún reporte valida el rango invertido.** Con *Desde* posterior a *Hasta* la consulta sale
   vacía y la pantalla responde con ese mismo cartel verde: *"no hay nada"* es indistinguible de
   *"el filtro no tiene sentido"*. Es la familia de **B161** (*un `catch` que devuelve vacío
   convierte "lo escribiste mal" en "todavía no escribiste"*), y acá el que lo lee es el dueño
   decidiendo sobre plata.
6. **El rango aplicado no se dice en ninguna parte del texto.** Los títulos no lo nombran y las
   fechas viven solo dentro de los campos del formulario, así que una pantalla que se mira de lejos
   —o una captura— no dice de qué período es. El PDF de *Auditoría* sí lo resuelve bien
   (`construirTextoFiltrosAuditoria` imprime los filtros aplicados); es el modelo a copiar, y lo que
   falta es que la **pantalla** haga lo mismo.
7. **Los botones de "Informe PDF" y "Excel/CSV" no avisan qué va a pasar, en 13 de los 20 reportes
   que los tienen** (solo 7 tienen el ⓘ). Es exactamente lo que se cerró en Formalización con
   **B184** y en la devolución del pagaré con **B112**. Y además **siguen habilitados con 0 filas**:
   se puede exportar un PDF vacío sin que nada lo advierta.
8. **La fila de totales mete cuatro importes en una sola celda, con `<br>`.** En *Vencimientos* la
   celda de la columna *"Monto"* dice `Capital X / Interés Y / IVA Z / Total W` apilados, debajo de
   una columna que en el cuerpo muestra un único número — así que el total no queda debajo de lo que
   totaliza. Pasa en **6** reportes (`caja`, `consultas-buro`, `gestiones`, `morosidad`,
   `proyeccion-fondos`, `vencimientos`). El modelo correcto es el `tfoot` de **B195**, donde cada
   total cae en su propia columna.
9. **Dos detalles chicos, uno de texto y uno de aire.** El aviso de Auditoría dice *"acotá el
   filtro"*, el **único** imperativo en voseo de todo `src/main` — el resto del sistema le habla de
   usted (*"Revise"*, *"Elija"*, *"Busque"*, *"Ingrese"*). Y en el índice, el `<p>` que explica
   *"Como oficial, ve 5 de los 21 reportes"* **se dibuja vacío para el gerente**, dejando un renglón
   en blanco bajo el título. De paso, el comentario de `menu.jsp:198` todavía dice *"4 de los 19
   sub-reportes"*: quedó viejo dos veces.
10. **Lo transversal, que se suma a lo de siempre**: los importes van **a la izquierda** en casi
    todas las tablas (*Vencimientos*, *Caja*, *Cobros* y *Auditoría* no tienen un solo
    `text-right`; *Cartera* y *Morosidad* apenas uno o dos) → **B160**; y los estados se imprimen
    como **texto pelado** (`getEstado()`), sin píldora, en todos menos *Cobros* → **B159**.

#### Informe de UX de Cancelación (15/11/2026, 10 líneas, nada tocado)

**Alcance, para que no quede nada entre dos informes**: acá va lo propio de *Cancelación* — el
punto de entrada del menú (`/cobranza?origen=cancelacion-anticipada`), la opción **«Todo el
préstamo»** (RF26), el **adelanto a capital** (RF27) con su vista previa, y la **constancia**
(RF28). El cobro de cuotas, la deuda exigible, el historial y `/mora` ya se revisaron en *Cobranza
y mora* (B192-B197). Medido con `SELECT` de solo lectura el 15/11: **169** préstamos cancelables,
**159** liquidaciones ya emitidas (**82** adelantos y **77** cancelaciones totales), **77**
constancias numeradas. Ordenado por impacto:

1. **El aviso de entrada manda a tocar un botón que ya no se llama así.** `cobranza/busqueda.jsp:18`
   dice *"Busque el préstamo y, en su ficha, toque «Cancelar todo»"*, y ese botón se llama
   **«Todo el préstamo»** desde **B106** (05/11/2026, `ServicioCobranza.OPCION_TODO_EL_PRESTAMO`).
   Es lo primero que lee quien entra por el menú *"Cancelación anticipada"*, y es la familia de
   C20: un renombre que dejó una copia atrás. **Ojo al tocarlo**: `RF26 §10` ya tiene anotado otro
   defecto **del mismo aviso** (que repite el título), así que el aviso tiene **dos** cosas, una ya
   registrada y esta nueva.
2. **El cartel que confirma la cancelación total no dice de quién ni de qué operación es.** Dice
   *"Confirmar: Todo el préstamo"* y los montos, y nada más: ni el cliente ni el N.º de operación
   (verificado en `cobranza-ficha.js:1498` y en el armado del cuerpo). Es la operación más grande
   de la pantalla —**77** ya emitidas— y la única de su tamaño que no los nombra, cuando anular
   (B178), la devolución del pagaré (B179), el arqueo (B188) y la reversión (B192) sí. Lo demás del
   cartel está muy trabajado (B96, B105, B106, B116): lo que falta es **para quién**.
3. **Al volver de la ficha se pierde el contexto de "Cancelación anticipada".** El *"Volver a la
   búsqueda"* conserva `q` pero **no** `origen`, así que el título vuelve a *"Cobranza"* y el aviso
   desaparece. Es la familia B147/B163/B170/B180/B189, con la vuelta de tuerca de que acá lo que se
   pierde no es un filtro sino **para qué entró el usuario**.
4. **Y la ficha nunca dice que se entró por "Cancelación anticipada".** El menú resalta
   *"Cancelación"*, pero la pantalla es idéntica a la de Cobranza: tres opciones y ninguna marca.
   Quien entró a cancelar tiene que acordarse solo de cuál de las tres era.
5. **El "ahorro" de la cancelación total no se muestra, y es justo donde más se ahorra.** El
   adelanto **sí** lo dice (*"El cliente se ahorra X de intereses por pagar por adelantado"*,
   `textoAhorroAnticipo`); la cancelación, que por la Ley 6624 no cobra **ningún** interés futuro,
   no muestra nada. **Ya está anotado en `RF26 §10`** como mejora opcional desde el 12/10/2026:
   conviene subirlo a ítem o descartarlo con argumento, pero no dejarlo en el limbo otro mes.
6. **La cancelación total no tiene el detalle que sí tiene el adelanto.** El adelanto abre un
   `<details>` con interés devengado, IVA, capital aplicado y saldo que queda
   (`fragmento_vista_previa_anticipo.jsp`); la cancelación muestra el total y nada del desglose —
   que es exactamente lo que la ley obliga a no cobrar hacia adelante, o sea lo que conviene poder
   mostrarle al cliente que pregunta.
7. **El cartel no avisa que la cancelación emite una constancia numerada.** El paso 1 de la
   devolución del pagaré sí lo dice desde B112 (*"Se emite la constancia en PDF y recibe su
   número"*); acá la constancia aparece recién después, como botón *"Constancia N.º …"* en el
   historial. Son **77** constancias emitidas sin que el cartel las mencione.
8. **`RF26 §10` pide algo que ya está hecho**: *"precargar el campo «Recibido» con el monto en
   efectivo (múltiplo de 50)"*, del 12/10/2026 — lo resolvieron D-141/B114 con
   `data-monto-cancelar-todo-redondeado` y `autocompletarRecibido` de `cobranza-ficha.js`. No es un
   hallazgo de pantalla: es un documento que quedó pidiendo algo que el sistema ya hace, y por eso
   entra en la lista (un pendiente falso cuesta el mismo tiempo que uno real).
9. **Para B159**: la píldora de estado de esta ficha es la misma de Cobranza, que ya quedó marcada
   como **el modelo a copiar**. Nada nuevo acá.
10. **Para B160**: los montos de la vista previa del adelanto y del resumen de la cancelación
    tampoco están alineados a la derecha.

**Lo que NO es un hallazgo, verificado antes de escribirlo**: que el adelanto no se ofrezca cuando
hay cuotas vencidas es **correcto** (RN-10/D-144, y `notaAntesDeAdelantar` lo explica en pantalla
desde B98); el cartel ya abre con el foco en *"No, volver"* (B105/B116); y los rótulos de las tres
opciones tienen **un solo dueño** en `ServicioCobranza` desde B106, así que el defecto del punto 1
está en el **aviso de otra pantalla**, no en el botón.

**Nada de esto está aplicado ni numerado**: los números se asignan cuando Joel elige las tandas.

#### Informe de UX de Cobranza y mora (15/11/2026, 10 líneas, nada tocado)

Pantallas miradas: `/cobranza` (la **lista de trabajo** con sus 3 bloques, y los resultados de
búsqueda), la **ficha del cobro** (historial, deuda exigible, la tarjeta de cobranza) y `/mora`
(bandeja + ventana de gestión). Todo medido con `SELECT` de solo lectura el 15/11. Ordenado por
impacto:

1. **La lista de trabajo de `/cobranza` no avisa que no hay caja abierta, y ofrece 69 botones
   *"Cobrar"* que no van a poder cobrar.** Es el **gemelo exacto de B186**: el aviso existe y está
   bien hecho, pero **solo en la ficha** (`cobranza/ficha.jsp:265-270`, con *"Ir a caja"*), porque
   `sesionAbierta`/`cajaLista` se leen en `mostrarFicha` y **no** en `mostrarBusqueda`. Hoy, sin
   caja abierta, la pantalla entera promete trabajo que se cae un clic después.
2. **"Solicitar reversión" es un formulario en línea DENTRO de una celda del historial**: el motivo
   va en un campo de **180 px** y, cuando el cobro tiene factura, ahí mismo está la casilla en la
   que la cajera **declara tener en su poder el original y todas las copias**. Dispara una
   autorización del gerente y no se deshace. Es la familia B155/B168/B171/B178/B188 y el caso más
   delicado de todos: le corresponde **ventana**. El cartel de confirmación, en cambio, ya está
   bien (nombra el recibo y el monto, y abre en cancelar).
3. **El bloque *"En mora"* de `/cobranza` lista las 69 filas de una sola vez, sin paginar ni
   ordenar.** El contador ya está (la píldora del encabezado). Es la lista con la que la cajera
   trabaja todo el día. Familia B183/B191.
4. **`/mora` no dice cuántos hay, no ordena y no pagina**: 69 filas por 9 columnas. Y el subtítulo
   promete un orden —*"con las promesas vencidas primero"*— que desde la pantalla no se puede
   cambiar ni comprobar.
5. **"Deuda exigible hoy" no tiene fila de total**, con **7 columnas de dinero** y un total por
   cuota. El número ya está calculado (`exigibleFicha.getTotalGeneral()`, que la propia tarjeta usa
   en un `data-`), así que es mostrarlo: el plan de pagos de Formalización ya tiene su fila *"Total"*.
6. **La ventana de *"Registrar gestión"* de `/mora` descarta con *"Cancelar"***, y es la única
   ventana del sistema que quedó con ese verbo — las de B155/B168/B171/B178/B188 dicen
   *"No, volver"*. B116 unificó los carteles de SweetAlert, no los modales.
7. **El historial de cobros no tiene contador ni paginación**, y es la tabla **con más acciones por
   fila de todo el sistema**: recibo, factura, nota de anulación, constancia, constancia de
   reversión y el formulario de reversión del punto 2. Hoy el máximo son **5** pagos en un préstamo;
   con 24 cuotas y reversiones crece sin techo.
8. **Un bloque vacío de la lista de trabajo igual dibuja su tarjeta**, con su encabezado de color y
   los títulos de columna sin ninguna fila debajo: hoy pasa con *"Próximos 7 días 0"*. Es el mismo
   ruido que el ítem 5 del informe de Formalización.
9. **Para B159, esta pantalla es el modelo a copiar, no un defecto**: la píldora de estado de la
   ficha es la **única** del sistema que ya usa color por estado (rojo En mora, verde Activo, gris
   Finalizado). Cuando se unifique el criterio, sale de acá.
10. **Y B160 tiene acá su caso más claro**: ninguna de las tablas alinea los montos a la derecha, y
    la de la deuda exigible tiene siete columnas de dinero una al lado de la otra.

**Lo que NO es un hallazgo, verificado antes de escribirlo**: `/cobranza` y `/mora` **no** son dos
listas duplicadas de los mismos 69 préstamos — son de **públicos distintos** (`/cobranza` es de la
cajera y el administrador; `/mora`, del oficial y el gerente, y la cajera la tiene en NINGUNO:
`ServicioAutorizacion` + `menu.jsp:178-179`), con columnas y acciones distintas. El *"Volver a la
búsqueda"* de la ficha **ya** conserva la búsqueda (A3a #11), y la ficha **ya** avisa de la caja.

**Nada de esto está aplicado ni numerado**: los números se asignan cuando Joel elige las tandas.

#### Informe de UX de Desembolso y caja (15/11/2026, 10 líneas, nada tocado)

Pantallas miradas: la bandeja `/desembolso`, la **ficha del desembolso**, y `/caja` con sus seis
bloques (apertura, sesión abierta, movimientos, entregar efectivo, cierre y rendición, diferencias
de arqueo sin revisar, sesiones cerradas). Todo medido con `SELECT` de solo lectura el 15/11.
Ordenado por impacto:

1. **La caja abierta del 13/11 bloquea el circuito de hoy, y la bandeja de desembolso no lo dice.**
   `/desembolso` lista el préstamo listo con su botón *"Desembolsar"* como si se pudiera, y el
   aviso —*"La caja del 13/11/2026 sigue abierta: ciérrela y rinda antes de operar hoy."*, con
   enlace a Caja— **solo aparece al entrar a la ficha**. El dato ya está en el controlador
   (`sesionAbierta` + `esDeHoy`, `ControladorDesembolsoServlet:265`): es el criterio de B88/B89/B100,
   que el aviso esté donde se decide.
2. **La bandeja no dice cuántos hay, no ordena ni pagina — y el número engaña.** Hoy muestra **1**
   fila: de los **18** préstamos PENDIENTE_DESEMBOLSO, **17** no tienen el checklist completo. Eso
   lo explica una nota al pie… que solo se dibuja **cuando la lista NO está vacía**. Es la familia
   de B148/B157/B167/B175/B183.
3. **"Sesiones cerradas" muestra 10 de 135**, sin contador ni paginación (sí tiene filtro por
   fechas). Misma familia que la anterior.
4. **"Marcar revisada" es un formulario en línea, sin confirmación y sin ventana** — y cierra un
   control de arqueo que **no se deshace** (`DiferenciaYaRevisadaException`). Es una acción de
   gerente sobre dinero: le corresponde ventana y confirmación con los datos, como B178/B171/B168.
   Hoy hay **1** diferencia sin revisar.
5. **La confirmación del cierre no dice de qué día es la caja que se cierra.** El cartel está bien
   armado —nombra el efectivo contado y la diferencia, con *"falta"*/*"sobra"*— pero con una caja
   de **otro día** abierta, que es justo el caso de hoy, no dice cuál. Es B179 otra vez: nombrar la
   operación que se está confirmando.
6. **"Movimientos" no tiene contador ni paginación.** Hoy son 5 filas; una caja de un día movido
   crece sin techo, y es la tabla que la cajera mira para cuadrar.
7. **El *"Volver a la bandeja"* de la ficha de desembolso pierde la búsqueda**: es un enlace pelado
   a `/desembolso`. Familia B147/B163/B170/B180, que ya tiene su mecanismo de sesión.
8. **La ficha de desembolso se titula solo con el N.º de operación**; para quién es el dinero está
   más abajo, en el resumen. En la pantalla donde se entrega efectivo, el nombre del cliente merece
   estar en el encabezado (mismo criterio que B161: decir para quién es).
9. **Las cuatro condiciones de la ficha usan cuatro etiquetas distintas para "no cumple"**
   (*Pendiente*, *Vencido*, *No disponible*, *Insuficiente*), todas en rojo. Los textos están bien
   —cada uno dice qué pasa— pero el color no distingue "falta un paso" de "no se puede": eso se
   suma a **B159**.
10. **Los dos transversales también acá**: los montos no están a la derecha en ninguna de las tres
    tablas (**B160**), y las píldoras de estado siguen el criterio de cada pantalla (**B159**).

**Lo que NO es un hallazgo, y conviene no "arreglar"**: el checkbox de *"El cliente firmó el
comprobante…"* deshabilitado hasta imprimir (D-130 + B87) y el aviso de caja de otro día **dentro**
de la ficha están bien resueltos; la nota del saldo teórico vs. el Panel (B107) también.

**Nada de esto está aplicado ni numerado**: los números se asignan cuando Joel elige las tandas.

**Numerado el 14/11/2026, cuando Joel eligió las tandas**: ítem 2 → **B178**, 8 → **B179**,
3 → **B180**, 4 → **B181**, 6 → **B182** (tanda 1, aplicada ese día); ítems 1+5 → **B183**,
7 → **B184**, 9 → **B185** (tanda 2, anotada); ítem 10 → se sumó a **B159**/**B160**. El estado de
cada uno vive en `FASE2_PLAN.md`.

### Antes que nada (12/11/2026, noche): en qué rama estás

El trabajo de la aplicación móvil y de la API **no está en `main` ni en `d149`**:

| Rama / proyecto | Qué tiene |
|---|---|
| `api-separada` (la viva) | la API en su **proyecto aparte** (`../sgcpc-api`, con su propio git). Es lo que se entrega |
| `app-movil` | el estado anterior, con la API **dentro** de `sgcpc.war`. **Plan B, no se toca** |
| `d149-compromisos-anteproyecto` | el trabajo de la tesis (C19/B86, lo de abajo de esta página) |

Para compilar: **primero `sistema`** (deja el jar de clases en `~/.m2`), **después `sgcpc-api`**.
El detalle de la separación está en `AVANCE.md` del 12/11 (noche) y en
`docs/entregas/ENTREGA_SERVIDOR.md`, sección "La entrega son DOS `.war`". Las funciones de la
aplicación están **congeladas** por decisión de Joel desde la ETAPA 9.

### Lo último: **C19 cerrado** — y con él, **B86 y el último riesgo de seguridad**

La mejora de UX/UI de todo el sistema está **completa y probada en pantalla por Joel**, ítem por
ítem. Cierre del día: humo en **1019/1019** sin una sola falla
(`..\pruebas\humo_2026-11-07c.txt`, árbol limpio) y `mvn test` en **920/920**; con C19
cerrado el 09/11, **925/925**.

| Tramo | Ítems |
|---|---|
| 1 — transversal | B87, B105, B4/B43 |
| 2 — cobranza | **B106** (los rótulos y los botones del cobro, con dueño único en el servidor) |
| 3 — caja y cierre | **B107** (aplicado a la deuda vs. efectivo), **B61** (los tres estados de revisión), **B52+B53** (el arqueo: cero sin signo, y día y hora) |
| 4 — documentos | **B12** y **B120** (membrete de continuación en contrato y plan), **B108** (el nombre de quien firma ya no se corta), **B59** (cerrado como decisión: el recibo se queda en A4) |

El detalle de cada uno está en `AVANCE.md` del 07/11; el estado, en `FASE2_PLAN.md`.

### C19, cerrado el 09/11/2026 (commit `21e4aff`)

Abrir un documento del expediente queda auditado (`VER_DOCUMENTO_EXPEDIENTE`, cada apertura), y el
registro **dice de quién es** el documento: nombre y C.I., N.º de operación o código SOL-, nunca
*"persona 3"*. **Decisión de Joel, escrita en la fila**: sin control de pertenencia — todo oficial
ve todos los clientes, es lo correcto para Capital Crédito; no es un olvido, no se "arregla"
después. El detalle está en `AVANCE.md` del 09/11.

### Lo último del 10/11: **B109, B112 y B122 cerrados**

El *"Volver"* del alta de cliente volvió a funcionar **y a verse activo**: la excepción
`fieldset:disabled a.btn{pointer-events:auto}` en §2b de `sgcpc-tema.css`, sin mover el marcado.
Probado en pantalla por Joel el 10/11; el detalle está en `AVANCE.md` de ese día y el estado, en
`FASE2_PLAN.md`.

**B112** también quedó cerrado ese día: el paso 1 de la devolución del pagaré ya confirma antes de
emitir la constancia, con el mecanismo de `pie.jsp` y sin tocar Java. Probándolo se emitió
`DEV-2026-000014` para `CC-2026-00004` (confirmación involuntaria, base de pruebas): se verificó en
la base que quedó todo coherente — numeración correlativa, PDF íntegro (SHA-256 recalculado),
paso 1 cerrado y la fila de auditoría. El detalle está en `AVANCE.md` del 10/11.

**B122** cerró ese mismo día, y salió de probar B112: la ficha de una solicitud en BORRADOR
mostraba **dos afectaciones del mismo cliente** (154,6 % en vivo y 2,6 % en la tabla) y la daba por
apta para enviar, mientras el servidor la rechazaba. Ahora la pantalla y el rechazo salen del mismo
método (`ServicioSolicitudes.calcularAfectacionVigente`) y el checklist tiene **10 ítems**. El
detalle está en `AVANCE.md` del 10/11 y en `funcionamiento/RF06_envio_aprobacion.md`.

### Lo último del 11/11: **B4 cerrado, la revisión final hecha, y 4 de sus 5 tandas aplicadas**

El cartel de *"Regenerar documentos"* —el último "no visto" de B4— **quedó VISTO**, así que ni B4
ni B43 tienen carteles pendientes. Después se hizo entera la **revisión final de pantallas**: 5
tandas por perfil, **70 capturas a 1280x800** en `..\pruebas\revision_final\` (con su `LEEME.md`),
**ni un POST**, los 22 informes incluidos. Dejó **17 hallazgos, B126–B142**, en la sección nueva
**"K. Revisión final de pantallas"** de `FASE2_PLAN.md`, más **B125**.

**Y ese mismo día se aplicaron y probaron en pantalla las cuatro primeras tandas**, cada una con su
commit:

| Tanda | Ítems | Commit |
|---|---|---|
| 1 | **B127** (el monto en letras no apocopaba, y salía así impreso en el **pagaré**), **B126** (el monto del Panel se salía de su tarjeta), **B128** (el código de la operación se partía en dos renglones en todos los listados) | `7155e07` |
| 2 | **B123** (el mismo problema avisado dos veces), **B131** (el botón verde que prometía lo que la pantalla negaba), **B125** (regenerar con la misma fecha decía "Documentos regenerados." y auditaba un no-evento) | `2f1a73a` |
| 3 | **B129** con **B124** adentro: la familia de los 23 carteles — 0 sin título propio, 0 con "Confirmar:", y el foco en cancelar en los irreversibles | `193c556` |
| 4 | **B130** (la columna "Reversión" muda), **B132** (la cuenta de resultados y el botón "Buscar"), **B133** (Usuarios: C.I. con puntos, "Restablecer contraseña", el ámbar) | `56ca7dd` |
| 5 | **B121** (la auditoría de la mora, que nombra el préstamo), **B134**, **B135**, **B136** (solo el orden del botón), **B137**, **B138** y **B139** | `cd971cb` |

El detalle de cada tanda está en `AVANCE.md` del 11/11; el estado, en `FASE2_PLAN.md`.

### Lo hecho después de la revisión final (11/11/2026)

| | Estado |
|---|---|
| **Humo completo** | **1019/1019, 0 fallos** (`..\pruebas\humo_2026-11-11.txt`). Las 28 commits del día no rompieron nada |
| **Base** | `caja.gerente_predeterminado` con su texto corregido en `bd/03_parametros.sql` y en `sgcpc_db_pruebas` (commit `c48908d`). Las migraciones a `sgcpc_db`: **decidido que no aplican** |
| **Control de contenido** | `src/main`, `pom.xml` y las 6 imágenes propias: **sin una sola mención** a la herramienta de asistencia y sin metadatos C2PA |
| **Causa raíz del hallazgo de seguridad** | **resuelta** en el `pom.xml` (commit `79e5471`) |
| **Script del `.war`** | `herramientas/preparar_war_entrega.sh`, probado de punta a punta (7/7) |
| **`proyecto_final.sql`** | **No hay que regenerarlo**. Joel eligió la **opción B**: agregarle el `UPDATE` de `caja.gerente_predeterminado`. **PENDIENTE** — ver abajo |

### Lo que sigue

1. **Otra mejora en el sistema**, que Joel va a indicar. **El `.war` NO se arma todavía: sale al
   final**, después de esa mejora, y se arma **con el script** (`bash
   herramientas/preparar_war_entrega.sh`, después de un Clean and Build suyo).
2. **El parche de `proyecto_final.sql` (opción B) quedó sin aplicar**: los archivos `.sql` están
   protegidos por una regla `deny` de `.claude/settings.local.json`
   (`Edit(**/*.sql)`/`Write(**/*.sql)`), así que el parche lo corre Joel con `!` — el comando
   exacto está en `AVANCE.md` del 11/11. Son dos `UPDATE` al final del dump; el texto sale de
   `bd/03_parametros.sql`, que ya los tiene.
3. **Al final de todo, la limpieza**: las **14 clases `*Temporal`**, las carpetas de
   `..\pruebas\` (16 carpetas, 48 MB, incluidas las 84 capturas de `revision_final\`) y los datos
   de prueba que dejó el humo en `sgcpc_db_pruebas`.
4. **B121 se mira en la auditoría** (el recálculo diario ya había corrido el 11/11 a las 07:45).

### Lo que esta tanda dejó como método, y conviene no volver a aprender

- **Tres ítems tenían el defecto mal atribuido en el plan** (B52 estaba en Java y por duplicado,
  B61 tenía tres estados y no dos, B59 no era del anticipo sino del formato A4). En los tres, leer
  el código **antes** de tocar cambió qué había que hacer. La fila del plan es un punto de
  partida, no un diagnóstico.
- **El `grep` por tema encuentra lo que el `grep` por número no**: así aparecieron B3 hecho y sin
  marcar desde el 25/10, y PA-09/PA-11 anotados en prosa del lote 2 **sin fila en ningún plan**.
- **En los PDF, el primer sospechoso de un `null` es la muestra**, no el reporte: cuatro defectos
  inexistentes en el papel real costaron una ronda cada uno. Hoy la herramienta cruza los
  `$P{...}` del `.jrxml` contra el mapa de parámetros.
- **Un ancho se verifica renderizando**, nunca multiplicando caracteres por puntos.

### Antes de la entrega

Borrar las **13 clases `*Temporal`** (`src/test/.../servicios/` y `.../herramientas/`) y las
carpetas de `..\pruebas\` (muestras, capturas, corrimientos). Están en la lista de `AVANCE.md`.

## PUNTO DE RETOMADA del 05/11/2026 (historial, superado por el de arriba)

**Leer esto primero.** Reemplaza a los puntos de retomada anteriores, que quedan más abajo como
historial.

### Lo último: el tramo visual del 05/11, cerrado y probado en pantalla

Cinco ítems, **todos probados en pantalla por Joel el mismo día**. `mvn test` quedó en
**907/907**. El detalle de cada uno está en `AVANCE.md` del 05/11 y el estado, en
`FASE2_PLAN.md`.

**El humo quedó en 1015/1015 sin una sola falla, con el tramo 1 YA COMPLETO**
(`..\pruebas\humo_2026-11-05c.txt`, corrido por Joel después de B4/B43): cero `FALLA`, ninguna
fila `PRECONDICIÓN` y ningún bloque omitido. **Esta es la foto del "antes" para el tramo 2** —
sirve mientras no se toque nada.

Hubo **dos** corridas en verde el mismo día y conviene no confundirlas: la de la mañana
(`humo_2026-11-05b.txt`, commit `67b45e9`) es **anterior a B105 y a B4/B43** y no los cubre. La
de la tarde, sí. **Mismo número en las dos, 1015/1015**: los dos ítems no movieron ni una
verificación, que era lo que el `grep` anticipaba.

| Ítem | Qué quedó | Commit |
|---|---|---|
| **B87** | Un **único estilo de botón deshabilitado** en todo el sistema: gris plano `#F1F5F9`/`#64748B`, `opacity:1`, sin hover, `cursor:not-allowed`. Primer ítem del tramo 1 de B86 | `9e1106d` |
| **B110** | **Renovación de la paleta**: fondo `#EEF3F8`, barra superior azul de marca con franja verde, recuadros con borde, tablas con encabezado azul marino y cebra, y el Panel con **una tarjeta de color pleno por indicador** (tonos joya, **rojo reservado a la mora**) más el efecto de elevación al pasar el mouse | `9c3355d` |
| **Cartel fuera** | `entorno=produccion` en `bd.properties`: la píldora **"BASE DE PRUEBAS"** no se muestra más, ni en el login ni en la barra. **La base NO cambió**, sigue siendo `sgcpc_db_pruebas` | `da61dd9` |
| **B111** | El usuario conectado y sus dos acciones (**"Contraseña"** y **"Salir"**) pasaron de la barra superior al **pie del menú lateral**, a la vista. Se eliminaron las dos piezas viejas, así que el nombre dejó de aparecer dos veces | `0840fdf`, `41f9d97` |
| **B105** | Los carteles que **mueven dinero o emiten documentos** abren con el foco en **"Cancelar"**, para que un Enter apurado no confirme. Segundo ítem del tramo 1 de B86. Quedaron **"no vistos"** cobrar y desembolsar (hace falta una caja abierta de verdad) y regenerar/re-emitir los PDF | `4fcf877` |
| **B4/B43** | **Toda confirmación resume qué va a pasar**: *"Generar documentos"*, *"Regenerar"*, *"Aprobar"* y *"Rechazar"* dicen cliente y C.I., monto, plazo, cuota y —según el caso— el primer vencimiento efectivo, los documentos que se emiten, la afectación o el motivo elegido. **Tercer ítem del tramo 1 de B86, el que lo cierra.** Quedaron **"no vistas"** *"Aprobar"* (las 5 solicitudes pendientes figuran con mora y el formulario ni se renderiza) y *"Regenerar"* (el único préstamo pendiente de desembolso tiene los firmados cargados) | `d8f5712` |

**Capturas antes/después** de todos, a 1280x800, en `..\pruebas\b86_capturas\`
(`B87_*`, `B110_*`, `B111_*`, `B105_*`, `B4_*`, `B43_*`). Registran, no prueban: la prueba fue
Joel en pantalla. Las de los carteles se sacaron **abriendo y cancelando, nunca confirmando**, y
dos pares son **controles**: un cartel que NO cambió (`B105_control_…`,
`B43_control_sin_cambio_…`), que es lo que distingue un cambio por cartel de uno global.

**Ojo con `bd.properties`** (está en `.gitignore`, vive solo en la PC de Joel): con
`entorno=produccion`, **`CargadorDatosPrueba` y `RestablecerContrasenasPruebas` abortan**. Para
usar una de las dos hay que poner `entorno=pruebas`, Clean and Build, usarla y **dejarlo de
nuevo en `produccion`** con otro Clean and Build. El humo **no** depende de esa marca (guarda
por nombre de base). Está escrito en el propio `bd.properties` y en `sgcpc-mapa` §5.

### LO QUE SIGUE: terminar B86 — **arranca el tramo 2 (cobranza), con B106**

**El tramo 1 (transversal) está CERRADO**: B87, B105 y B4/B43, los tres probados en pantalla por
Joel el 05/11/2026. El orden acordado el 03/11/2026 se mantiene, descontando lo ya cerrado:

| Tramo | Pendiente |
|---|---|
| ~~**1. Transversal**~~ | **CERRADO el 05/11/2026**: ~~B87~~, ~~B105~~, ~~B4/B43~~ |
| **2. Cobranza — EL QUE SIGUE** | **B106** (dos rótulos escritos a mano en el JavaScript, que pisan la pintura del servidor) |
| **3. Caja y cierre** | **B61**, **B52** (nunca "−0 Gs."), **B53** (fecha con día y hora en el detalle del arqueo), **B107** (cada monto dice si es *aplicado a la deuda* o *efectivo en caja*) |
| **4. Documentos (PDF, al final)** | **B12** (hueco del contrato), **B59** (espacios del recibo), **B108** (ancho del nombre de la cajera). Cada uno exige muestra, recompilar su `.jasper` y una línea del manifiesto |

Aparte del tramo, ~~**suelto y pendiente de decisión de Joel**~~ (**CERRADO el 10/11/2026**, ver
el punto de retomada de arriba): **B109** — el botón *"Volver"* del
alta de cliente está **muerto** (`<a class="btn">` dentro de un `<fieldset disabled>`, que
Bootstrap y AdminLTE neutralizan con `pointer-events:none`). Es preexistente, no lo causó B87, y
es la salida de la pantalla justo en el estado en que más se necesita. La propuesta está escrita
en `FASE2_PLAN.md`; **no se aplicó**.

Y lo que B86 es de fondo: **la revisión de UX de TODAS las pantallas**, no solo de las de la
lista. Los diez puntos de la "Lista de control B86" (skill `sgcpc-diseno-ui`) **se corren
completos en cada pantalla que se toque**, sea del tramo que sea.

**La foto del "antes" ya está**: `humo_2026-11-05c.txt`, 1015/1015, posterior a todo el tramo 1.
Sirve **mientras no se toque nada** — si entre medio se toca el sistema, se vuelve a correr
antes de empezar B106: una red de seguridad que no se corre se desactualiza en silencio.

**Fuera de B86**: **B97** (no es visual, toca la lógica de los cuatro listados en vivo —
descartado para esta versión) y **C21** (criterio de agrupamiento de un informe).

**Estado de los datos**: sin cambios por este tramo — B87, B110, B111, B105 y B4/B43 son **solo
pantalla**, y el cartel es una línea de configuración. **Ningún `.java` se tocó** en B105 ni en
B4/B43, así que esos dos no necesitaron Clean and Build (el `.css` y el `.js` sí piden
**Ctrl+F5**: el `?v=` de `VersionDespliegue` solo se recalcula al redesplegar). La corrida del
humo del 05/11 sí escribió sus propios datos de prueba, como siempre.

**Lo que dejaron escrito estos ítems**, ya en las skills: el color de un elemento se
asigna **por significado, no por posición**, y el contraste se mide **en el peor estado** (con el
brillo del hover aplicado), no en reposo — `sgcpc-diseno-ui`; mover una pieza de lugar es
**borrar la vieja**, incluido su CSS y el de lo que arrastraba — `sgcpc-diseno-ui`; al borrar
marcado hay que grepear el humo por los nombres **genéricos del framework**
(`dropdown-toggle`, `nav-link`…), no solo por las clases propias — `sgcpc-cambio-seguro`; y de
B4/B43, lo que más va a servir en el tramo 2 (que es justamente de textos armados en el
JavaScript): **un valor que solo se conoce al confirmar viaja en su propio atributo** y el JS
escribe el valor, nunca la frase; **se lee con `getAttribute`, nunca con `.data()` de jQuery**,
que cachea el atributo en la primera lectura; y por eso **la prueba en pantalla de un cartel con
valores vivos es la SEGUNDA apertura**, no la primera — `sgcpc-diseno-ui`. Y una del humo: una
verificación de **ausencia** se rompe **en silencio** al renombrar el literal que vigila, así que
antes de reescribir un texto hay que grepearlo en el humo y mirar de qué lado está —
`sgcpc-cambio-seguro`.

## Punto de retomada anterior (03/11/2026) — historial

**Leer esto primero.** Reemplaza a los puntos de retomada anteriores, que quedan más abajo como
historial.

### Lo último: tandas 1 y 2 de la fase 2, cerradas y probadas en pantalla

`mvn test` quedó en **899/899**. El detalle de cada ítem está en `AVANCE.md` (01/11 tanda 1,
03/11 tanda 2) y el estado, en `FASE2_PLAN.md`.

| Tanda | Ítems | Commits |
|---|---|---|
| **1** (01/11) | B11 + B103 (fecha en letras del contrato), B13 (domicilio capitalizado), C37 (el *"Son:"* del recibo), B102 (concepto de la factura); **B75 descartado** | `4ef5194`, `0d0b86d`, `d788572`, `e933d21` |
| **2** (01-03/11) | B100 + B104 (el tramo *"aprobada → formalizar → firmados"* avisa y lleva), B42 (el mismo archivo cargado dos veces), **B23 cerrado** (ya estaba resuelto desde el 25/10) | `74f3b2a`, `5c93936`, `c8f932a` |
| **3** (03/11) | C30 (con una reversión pendiente, los tres cobros **avisan y no bloquean**), C22 (el acta de arqueo deja de firmar una recepción que nadie hizo) | `2029d2c`, `bbcb126` |

**Lo que quedó sin probar en pantalla, anotado como tal** (no suponer que está probado): la rama
*"Formalizar esta solicitud →"* de B104 — hace falta **una solicitud aprobada sin formalizar**, y
el 03/11 no quedaba ninguna; `/solicitudes/{id}` con esa misma franja (usa el mismo fragmento);
el caso "checklist completo → el panel no muestra el aviso de B100"; la marca de C30 *"Ya hay una
operación posterior: solo se puede rechazar."* (necesita confirmar una operación posterior:
cuesta un recibo y una factura); y el aviso de C30 en *"Pagar la cuota"* (necesita una cuota
vencida o venciendo hoy). Todas cubiertas por JUnit.

### LO QUE SIGUE (decisión de Joel, 03/11/2026): la documentación para la mesa

**B86 queda EN PAUSA.** La pasada visual **no se empieza**: la etapa que sigue es la
**documentación para la mesa evaluadora y los tutores**.

Lo de B86 queda listo para retomarlo cuando Joel lo decida, sin volver a analizarlo:

- **Alcance acordado** (03/11/2026), en cuatro tramos y en este orden: **transversal** (B87
  estilo `disabled` único, **B105** foco de los carteles que mueven dinero, B4/B43
  confirmaciones genéricas) → **cobranza** (**B106**) → **caja y cierre** (B61, B52, B53,
  **B107**) → **documentos** (B12, B59, **B108**), con los PDF al final. **Fuera**: **B97**
  (descartado para esta versión) y **C21** (no es diseño).
- **El prerequisito está cumplido**: `pruebas/humo_http.sh` quedó en **1015/1015 el
  03/11/2026** — la foto del "antes", en verde. **Igual hay que volver a correrlo justo antes
  de empezar**: una foto vieja no sirve para comparar.
- El detalle de cada ítem vive en `FASE2_PLAN.md` (fila **B86** y las de cada número).

**Estado de los datos**: caja del 01/11 **cerrada** con 844.000. `CC-2026-00006` (Ana) con los 3
firmados cargados, **pendiente de desembolso**, documentos que **vencen el 06/11/2026**.
`CC-2026-00004` finalizado (cancelación total del 01/11) y con las fechas corridas para siempre.

**Las dos lecciones de la tanda 2**, ya en las skills: un control que compara contra lo que
devuelve un DAO tiene que verificar primero **qué trae ese DAO** (un `getArchivoBinario()` en
null apagó el bloqueo de B42 en silencio) — `sgcpc-cambio-seguro`; y el pintor de carteles
**quita el punto final del título**, así que todo mensaje de cartel va en **dos oraciones** —
`sgcpc-diseno-ui`.

### Lo que se hizo el 30/10/2026 (jornada completa, 11 commits)

Todos **probados en pantalla por Joel el mismo día**. `mvn test` terminó el día en **872/872**
(empezó en 847). El estado de cada ítem vive en `FASE2_PLAN.md` y el detalle en `AVANCE.md` del
30/10; acá va solo el mapa.

| Ítem | Qué quedó | Commit |
|---|---|---|
| **B79 + B81** | La línea grande del *"Resumen del cobro"* dice **el monto que se va a cobrar**: `—` si hay un motivo en rojo, y *"Cobra X (pago parcial)"* + *"de Y exigibles — queda pendiente Z"* en un pago parcial. Un solo pintor, en vez de cinco escritores | `acd53c6` |
| **B82 + B93** | **Un solo buscador por número de operación** para Cobranza y Estado de cuenta: exacto sin importar mayúsculas y, si no hay, por fragmento con tope | `9b66988` |
| **B94** | El botón *"Comprobante de desembolso"* se ofrece **por permiso, no por perfil** (el gerente tenía el permiso y ninguna pantalla se lo daba) | `b998e4d` |
| **B58** | Cerrado: el texto de ejemplar del comprobante, **visto en pantalla** | `8e25a1c` |
| **B95 + B96** | La fecha de promesa vacía ya no sale en rojo (pedido en gris) y el cartel del pago parcial **repite** las frases del resumen, con el monto en el botón | `39c284b` |
| **B98 + B99** | Una cuota que **vence hoy** no está vencida: los cuatro textos de la ficha dejaron de decir *"vencido"*, y el panel de la cajera estrenó la tarjeta **"Vencen hoy"** | `1f71147` |
| **Paso 15** | **Desembolso y cobro real de una cuota que vence hoy**, de punta a punta, con todos los números predichos y sellados antes de abrir la pantalla | `48501b6` |

Los commits `4677a96`, `e2e79ea`, `859b436` y `43576cd` cargan los hashes en `FASE2_PLAN.md` y en
cada RF (un hash no se puede escribir en el commit que lo genera).

**El hilo de la jornada**: ocho de los diez ítems eran **el mismo tipo de problema** — una frase o
un dato escrito en más de un lugar, o elegido con la pregunta equivocada (*"¿hay algo cobrable?"* en
vez de *"¿hay algo vencido?"*). La salida fue siempre la misma: **un dueño único y puro**, plantilla
en un `data-` para el JavaScript, y un test que fija **la frase que se lee**, no la pieza.

### SIGUIENTE: el orden vigente

Los puntos 1 a 6 del orden que fijó Joel el 29/10 **están hechos** (ver el historial de abajo).
Queda:

1. **Los B medios y bajos que elija Joel.** La lista completa de abiertos (B y C), con gravedad,
   esfuerzo y tipo, se armó el 01/11/2026 — el dueño del estado sigue siendo `FASE2_PLAN.md`.
   Se hicieron las **tandas 1, 2 y 3** (ver la tabla de arriba); sin más tandas decididas.
2. **B86** — la pasada de UX/UI de **todo** el sistema, **la última**. **EN PAUSA desde el
   03/11/2026** por decisión de Joel: primero va la documentación para la mesa. El alcance
   acordado, lo que quedó fuera (B97, C21) y el estado del prerequisito están arriba, en
   "LO QUE SIGUE" — esta línea no los repite.

La **limpieza de la base y la entrega** siguen pendientes, con los 4 pasos de la lista del 27/10
(más abajo). La **revisión del contador/abogado** también: `RF23` §10 y riesgo R-02 (el 10 % de IVA
sobre intereses, la firma del emisor, Nota de anulación vs Nota de Crédito), más **C37** y **C25**
por el redondeo.

### Qué cambió en la base el 30/10/2026 (afecta la limpieza)

- **`CC-2026-00004`** (Joel Jesús Acosta, C.I. 3.637.936) quedó **desembolsado** (1.000.000,
  `DES-2026-000005`) y con **la cuota 1/6 cobrada** (`REC-2026-000015`, factura
  `001-001-0000007`): **ACTIVO**, saldo 841.521, 5 cuotas pendientes. Y con **las fechas corridas
  −31 días para siempre**, igual que D-157/D-158: una reimpresión de su comprobante mostrará el
  primer vencimiento corrido (D-94, el comprobante se regenera).
- **`CC-2026-00005`** sigue como quedó el 29/10 (ACTIVO, 10 cuotas, próxima el 30/11/2026): los
  dos corrimientos del paso 15 (a) se **deshicieron** con su VUELTA, verificado.
- **Caja**: la sesión 13 (30/10) quedó con saldo teórico **180.600 Gs.**, pendiente de cierre.

## Punto de retomada anterior (29/10/2026) — historial

### Lo que se hizo el 29/10/2026 (jornada completa, 10 ítems cerrados)

Todos **probados en pantalla por Joel el mismo día**, cada uno con su commit. `mvn test` terminó
el día en **847/847**. El detalle de cada uno está en `AVANCE.md` del 29/10 y en `FASE2_PLAN.md`
(que es el dueño del estado); acá va solo el mapa.

| Ítem | Qué quedó | Commit |
|---|---|---|
| **B89** | El cartel *"No cumple validaciones preliminares"* nombra lo que falta | `7e38d9a` (su asunto salió con un `@` por un error de tipeo; lo explica el commit de nota `fdbf6d1`) |
| **B88 + B28 + B27/B34/B35** y **B90** | Un solo dueño del "siguiente paso" (`ServicioClientes.calcularSiguientePaso`), y la autorización del buró pasa a ser la **fila 1** del expediente | `926d58c` |
| **(registro)** | La prueba del gerente del ajuste (c) y este PUNTO DE RETOMADA | `d94931d` |
| **C20** | No se puede cancelar una solicitud **ya formalizada** (el mensaje prometía una regla que el código no verificaba) | `342d1e6` |
| **C3 + C12** | El contrato y el comprobante dicen la fecha que el plan **usa de verdad** | `4c15391` (+ `f94fc88`, corrección de documentación) |
| **B91** | El aviso de la ficha dice **cuáles** son los datos congelados ("Los datos en gris…") | `8d09dd6` |
| **C39** | La próxima cuota que se le muestra al cliente es **la que se cobra**, no el ancla teórica | `e5cccc6` |
| **B83 + B58** | El **recibo**, como formulario de talonario; ejemplares con el mismo formato en los tres documentos | `30dc413` |
| **B92** | *"Adelanta N cuotas (sin intereses futuros)"*, con un solo dueño del texto | `945fd28` |

**El hilo de la jornada**, por si sirve para la defensa: siete de los diez ítems eran **el mismo
tipo de problema** — un dato o un texto escrito en más de un lugar (el "siguiente paso" en cada
servlet, la fecha elegida contra la efectiva en tres documentos, el aviso de la ficha en dos
contextos, la etiqueta del adelanto en tres archivos). La salida fue siempre la misma: **un dueño
único, puro y probado con JUnit**, y las pantallas preguntando en vez de decidir.

### SIGUIENTE: el orden que fijó Joel el 29/10/2026

Lo hecho queda tachado; **el orden activo arranca en el punto 5**:

1. ~~**C20**~~ — hecho (`342d1e6`).
2. ~~**C3 / C12**~~ — hechos (`4c15391`).
3. ~~**C39**~~ — hecho (`e5cccc6`).
4. ~~**B83 + B58**~~ — hechos (`30dc413`). *(B91 y B92 se intercalaron: salieron de las pruebas de
   estos mismos ítems.)*
5. **B79 / B81 / B82** — los tres del 27/10, todavía **solo registrados**: la línea grande del
   "Resumen del cobro" que no refleja el monto bloqueado por el servidor (B79) ni el pago parcial
   (B81), y la búsqueda de Cobranza que **no acepta parte del número de préstamo** (B82, `00003`
   no encuentra `CC-2026-00003`). Ver `FASE2_PLAN.md` área H.
6. **Paso 15** — `RF22_cobro_efectivo.md` §10: cubrir lo vencido pero **no** la cuota que vence.
7. Los **B medios y bajos** que elija Joel.
8. **B86** — la pasada de UX/UI de **todo** el sistema, **la última**, con `pruebas/humo_http.sh`
   corrido **justo antes** (prerequisito decidido el 28/10/2026; incluye **B87** y los espacios
   vacíos del recibo anotados el 29/10).

La **limpieza de la base y la entrega** siguen pendientes, con los 4 pasos de la lista del 27/10
(más abajo) — ojo con los datos de prueba que se fueron agregando (cuadro de abajo).

### Lo único que quedó sin ver en pantalla el 29/10/2026

El **comprobante de desembolso con las palabras nuevas de los ejemplares** (`ORIGINAL — ARCHIVO
CAPITAL CRÉDITO` / `COPIA — CLIENTE`, B58): se vio en la muestra, no en pantalla. Se reimprime
desde **Reportes → Estado de cuenta → "Comprobante de desembolso"** (también desde la ficha de
Desembolso del préstamo). Ese documento **se regenera en cada impresión**, así que la reimpresión
de `DES-2026-000004` ya sale con las palabras nuevas. Anotado igual en `FASE2_PLAN.md` (B58),
`RF23` y `RF17`: **no dado por probado**.

### Qué cambió en la base el 29/10/2026 (afecta la limpieza)

- **Persona 6, `Prueba de Flujo Jueves 29`** (C.I. 1.222.333), creada en la prueba de B88/B90:
  `SOL-2026-0006` aprobada y formalizada como **`CC-2026-00005`**, desembolsada (3.000.000) y hoy
  **`ACTIVO`**, con dos adelantos a capital (`REC-2026-000013` y `REC-2026-000014`) y el plan
  regenerado dos veces.
- **`SOL-2026-0007`** (Ana María Prueba, persona 4): **`PENDIENTE_APROBACION`**, enviada en la
  prueba de B91.
- **`SOL-2026-0005`** (Ejemplo 03, persona 5): **`CANCELADA`** en la prueba de C20 (motivo "Otro -
  prueba C20"), que era el caso legítimo que había que confirmar que seguía funcionando.
- **Caja**: sesiones del 29/10 abiertas y cerradas por Sofía Martínez para el desembolso y los
  adelantos. **Revisar que no quede ninguna abierta** antes de la entrega.
- Todo esto se va con el vaciado previo a la entrega.

### Lo que se hizo el 28/10/2026: primer lote de código de la FASE 2

**B45, B63 y B76 — resueltos y PROBADOS EN PANTALLA por Joel el mismo día.** Es el primer cambio
de código de la fase 2, y cierra el **"principio de paneles"** (§ Hallazgos generales, más
abajo): tres tarjetas nuevas, cada una con enlace a la pantalla donde ese trabajo se resuelve.

| Hallazgo | Tarjeta | Perfil | Bloque |
|---|---|---|---|
| **B45** | "Solicitud(es) aprobada(s) para formalizar" | Oficial | **Hoy** |
| **B76** | "Préstamo(s) con cierre documental pendiente" | Oficial | Pendientes |
| **B63** | "Diferencia(s) de caja por revisar" | Gerente + administrador | Pendientes |

Ninguna agrega una consulta nueva: reusan los métodos que ya alimentan `/formalizacion` y
`/caja`. Detalle y decisiones de Joel en `AVANCE.md` del 28/10/2026, `FASE2_PLAN.md` (área A y
puntos 3-4 del primer lote) y `RF30_panel.md` §2/§8/§9. `mvn test`: **800 pruebas, 0 fallas**;
más `PruebaPanelTarjetasNuevas` (nueva, con `rollback`), 2 pruebas en verde.

### Lo segundo del 28/10/2026: B84 — RN-01 en los cuatro momentos

**B84 — puntos (1) y (2) resueltos y PROBADOS EN PANTALLA por Joel el mismo día.** Decisión de
negocio de Joel: *un cliente no puede tener dos préstamos a la vez*. La regla (**RN-01**,
D-61/D-68) ya estaba al **crear** y al **enviar a aprobación**; se agregó al **aprobar**
(`ServicioAprobacionCredito.validarSinPrestamoAbierto`) y al **formalizar**
(`ServicioFormalizacion.validarSinOtroPrestamoAbierto`), reusando el DAO que ya existía — **sin
consulta nueva, sin JSP, sin controladores**. Al formalizar no se autobloquea con el préstamo que
está creando (tres capas, la tercera explícita por decisión de Joel). `mvn test`: **804 pruebas,
0 fallas**; más `PruebaRN01AprobarFormalizar` (nueva, con `rollback` total), 2 pruebas en verde.
Detalle en `AVANCE.md` del 28/10/2026, `FASE2_PLAN.md` área E (B84), `RF09_aprobar.md` §4/§9/§11/§13
y `RF11_formalizacion.md` §6/§7/§9/§10/§11.

**Queda anotado sin resolver** (B84 punto 3): el 40 % de afectación (RF05) **no suma la cuota de
un préstamo propio** del cliente. Hoy no produce ningún error porque RN-01 impide que coexistan;
si alguna vez se relajara la regla, habría que sumarla. Sin acción.

### SIGUIENTE del 28/10/2026 — historial, ya cumplido (el orden activo es el de arriba)

1. ~~**B85 + B77 + B78 (el próximo cambio)**~~ — **hecho el 28/10/2026**: la tanda de la pantalla
   de cierre documental (título "Devolución del pagaré — CC-…", la explicación y los 3 pasos
   numerados). Ver `FASE2_PLAN.md` área F.
2. **La limpieza de la base y la entrega** siguen pendientes, con los 4 pasos de la lista del
   27/10 (más abajo) — **ojo que varios de esos datos cambiaron hoy**, ver el cuadro de abajo.

### Qué cambió en la base el 28/10/2026 (afecta la limpieza)

- **`CC-2026-00003`**: cierre documental **COMPLETO** (28/10 09:31, constancia
  **`DEV-2026-000002`**). El único que sigue con el cierre pendiente es **`CC-2026-00001`** — por
  eso la tarjeta B76 muestra **1**, no 2.
- **Sesión de caja 9** (28/10): cerrada con **sobrante de 10.000**, motivo "prueba 28-10-2026",
  **ya revisada** por el gerente. Por eso la tarjeta B63 volvió a 0.
- ~~**`SOL-2026-0004`** … APROBADA y sin formalizar a propósito, como dato de prueba de B45~~ —
  **se formalizó en la prueba de B84, más tarde ese mismo día**: hoy está `formalizada = t`, así
  que **la tarjeta B45 muestra 0** (no 1). Las 4 solicitudes de la base están `APROBADA` y
  formalizadas.
- **`CC-2026-00004` (nuevo, 28/10/2026 10:37)**: préstamo del cliente 1 (Joel Jesús Acosta,
  1.000.000 a 6 meses), **`PENDIENTE_DESEMBOLSO`**, `estado_cierre_documental = NO_APLICA`,
  `primera_fecha_vencimiento = 28/11/2026` (es la fecha **elegida**; cae sábado, así que las
  cuotas usan el lunes **30/11/2026** — ver `RF11_formalizacion.md` §2). Es el único préstamo
  abierto de la base: **mientras exista, el cliente 1 no puede pedir otro crédito** (RN-01), y
  sigue pendiente su desembolso en `/desembolso`.
- **`numeradores` al 28/10/2026, después de B84** (verificado con `SELECT`; reemplaza la lista
  anterior del mismo día): `RECIBO` **12**, `FACTURA` **6**, `SOLICITUD` **4**, `OPERACION` **4**
  (subió por `CC-2026-00004`), `AUTORIZACION`/`CONSTANCIA`/`DESEMBOLSO` **3**, `DEVOLUCION` **2**.
  Todos se dejan en **0** con la limpieza. Nota: el intento de solicitud nueva que RN-01 bloqueó
  **no** consumió número de `SOLICITUD` (el control corre antes de generar el código).

---

## PUNTO DE RETOMADA (27/10/2026, noche) — historial

> **HISTORIAL (superado el 28/10/2026).** Sigue siendo el mejor registro de la jornada de pruebas
> del 27/10 y de los pendientes que siguen abiertos, pero **dos cosas quedaron viejas**: el punto
> 6 de sus pendientes (*"el resto de `FASE2_PLAN.md` — primer lote: B45, B63, B76"*) ya está
> **hecho y probado en pantalla**, y su lista de `numeradores` cambió. Los valores vigentes están
> en el punto de retomada de arriba.

### Lo que quedó PROBADO EN PANTALLA el 27/10/2026

Fue la jornada de prueba más completa del proyecto. Todo esto pasó de "verificado por código" a
**probado en pantalla por Joel**:

| Qué | Dónde está registrado |
|---|---|
| **Cuotas vencidas** y la deuda exigible con mora (5 días de atraso, moratorio 542, punitorio 163, IVA) | `RF21_deuda_exigible.md` §9 |
| **Dos tramos de mora en el mismo cálculo** (36 y 8 días: moratorio 6.932, punitorio 2.079) | `RF21_deuda_exigible.md` §9 |
| **"Pagar lo vencido"** sobre una cuota realmente vencida (pasos 12, 13, 14 y 16) | `RF22_cobro_efectivo.md` §9 |
| **Pago parcial con promesa** y la promesa **cumplida sola** al pagar el resto — ciclo RN-20 completo | `RF24_seguimiento_mora.md` §9 |
| **Cancelación total por la vía nueva**, con redondeo y constancia CAN-2026-000002 | `RF26_cancelacion_anticipada.md` §9 |
| **Cancelación total CON MORA en la liquidación** (2.433.459, constancia CAN-2026-000003) — cierra el paso 16 | `RF26_cancelacion_anticipada.md` §9 |
| **Primera factura real con varios renglones** (7), con moratorio y punitorio gravados | `RF23_recibo_factura.md` §9 |
| **La pantalla nueva del cobro en sus dos estados** (al día y con vencidas) | `RF27_adelantar_capital.md` §9, 7.ª y 8.ª pasada |
| **La factura nueva** emitida por el circuito real, y la vieja reimpresa con su diseño original (D-94) | `RF23_recibo_factura.md` §9 |
| **Doble clic** en la confirmación del cobro (idempotencia) | `RF22_cobro_efectivo.md` §9 |
| **Cierre de caja** en sus dos formas: jornada larga de 8 movimientos (sesión 7) y caja de 5 minutos para una sola operación (sesión 8), las dos con diferencia **0** y mora dentro de la composición | `RF19_cierre_arqueo.md` §9 |
| **IVA del mes**: facturas 0000001-0000005 correlativas, 31.463 / 2.861 | `RF33_facturas_iva.md` §9 |

**El método, que conviene repetir**: el resultado se calculó **a mano y sellado ANTES** de abrir la
pantalla —Joel por su lado, Claude por el suyo, sin verse— y recién después se comparó con lo que
mostró el sistema. Coincidieron los tres. Una comparación contra el propio motor no prueba lo
mismo: si la fórmula estuviera mal implementada, motor y pantalla coincidirían igual.

Para conseguir la cuota vencida se usó el **camino B de D-81** (envejecer el préstamo por SQL),
no una fecha simulada: −35 días sobre `CC-2026-00003`, en una transacción con 8 verificaciones.
Ver `AVANCE.md` del 27/10/2026.

### Evidencia ya guardada (fuera del repositorio)

Los documentos que el sistema emitió ese día están en **`evidencias\2026-10-27\`** (al lado de
`pruebas\`, fuera del repo): **19 PDF, la evidencia COMPLETA** —las 6 facturas del mes, los 8
recibos, las constancias `CAN-2026-000002` y `CAN-2026-000003`, los arqueos de las sesiones 7 y 8,
y el PDF de la prueba automática— exportados desde `archivos_binarios` **sin regenerar ninguno**,
con el SHA-256 de los 18 que salieron de la base verificado contra ella. 7,0 MB. Su `LEEME.md`
dice qué es cada archivo, a qué prueba corresponde y en qué commit está registrada. `capturas\`
queda para las capturas de Joel.

> **Ya no falta nada. LA BASE SE PUEDE LIMPIAR.** Los dos documentos que no existían hasta que
> alguien los abriera —`REC-2026-000006` y el arqueo de la sesión 8, los dos por
> `obtenerOGenerarPdf`— Joel los abrió y quedaron exportados y verificados.

### SIGUIENTE: limpieza de la base y entrega

**Los tres préstamos de prueba quedaron `FINALIZADO`**, y dos con las fechas corridas:
`CC-2026-00003` −35 días (**D-157**) y `CC-2026-00001` −63 días (**D-158**). En los dos casos la
decisión de Joel fue **no volver las fechas atrás**: sus constancias ya emitidas
(`CAN-2026-000002` y `CAN-2026-000003`) citan las fechas desplazadas, y revertirlas descuadraría
la base con un documento emitido. Esos datos **se eliminan con la limpieza previa a la entrega**,
que es el próximo trabajo:

1. **Vaciar los datos de prueba dentro de la base única** (DOGMA 2: nunca crear ni recrear otra
   base). Ojo con los **13 triggers `BEFORE DELETE`** de `REGLAS_DESARROLLO.md` §14, que rechazan
   el borrado en las 11 tablas financieras y en `auditoria_operaciones`.
2. **Dejar la numeración en 1** (`numeradores`): hoy está en `FACTURA` **6**, `RECIBO` **12**,
   `CONSTANCIA` **3**, `SOLICITUD`/`OPERACION`/`DESEMBOLSO`/`AUTORIZACION` 3, `DEVOLUCION` 1 — ya
   anotado como pendiente en `RF23_recibo_factura.md` §10.
3. **Borrar lo temporal**: `GenerarMuestrasFacturaTemporal.java` y `pruebas/factura_muestra/`
   (lista "Antes de entregar" de `AVANCE.md`).
4. **Armar el `.war` y el respaldo** siguiendo `docs/entregas/ENTREGA_SERVIDOR.md` (leerlo antes:
   trae el servidor de destino y lo que quedó pendiente la vez anterior, para no repetir).

### Pendientes que siguen abiertos (no los cierra la jornada de hoy)

1. **N = K en pantalla** (llegar a la cancelación total por el control de cantidad, no por la
   opción "Todo el préstamo"). La cancelación por la vía nueva ya se probó; lo que falta es solo
   ese camino. Hace falta otro préstamo de prueba. **Decisión de Joel.**
2. **Paso 15 de `RF22_cobro_efectivo.md` §10** (cubrir lo vencido pero **no** la cuota que vence
   justo hoy): **no se cierra envejeciendo un préstamo.** Se analizó a fondo el 27/10: necesita
   una cuota venciendo exactamente hoy y otra ya vencida, y ningún desplazamiento múltiplo de 7
   pone una cuota en el día de hoy; el único que lo lograba (55 días) tiraba una cuota sobre un
   feriado y otra a un sábado, contra RN-19. **Hace falta un préstamo formalizado con una cuota
   que caiga justo hoy** — después de la limpieza.
3. **Tramos de mora 61-90, 91-180 e incobrable (> 180)** (`RF21` §10) y **promesa incumplida**
   (`RF24` §9). El tramo 31-60 ya estaba probado desde el 13/10/2026.
4. **C39** (la cuota programada vence el 23/11/2026 y el plan regenerado muestra la próxima el
   21/11/2026): solo registrado, sin tocar nada. Es el motor, no la pantalla.
5. **Hallazgos nuevos del 27/10**, solo registrados en `FASE2_PLAN.md`: **B80** (recálculo diario
   ya corrido — investigado, **no es defecto**), **B81** (la línea grande del resumen en un pago
   parcial), **B82** (la búsqueda de Cobranza no acepta parte del número de préstamo), **B83** (el
   recibo sigue con el diseño viejo, familia B58).
6. El resto de `FASE2_PLAN.md` (primer lote: B45, B63, B76).

## PUNTO DE RETOMADA (27/10/2026, tarde) — historial

**En curso: la ampliación de alcance pedida por el tutor el 27/10/2026** — la forma de cobrar en
la ficha de cobranza. Ver `FASE2_PLAN.md` § "AMPLIACIÓN EXPLÍCITA DE ALCANCE (27/10/2026)" y
`docs/funcionamiento/RF27_adelantar_capital.md` §2a/§7/§9.

**Estado: aplicado, probado en pantalla por Joel y COMMITEADO el 27/10/2026** (un solo commit,
código + documentación; sin push, sin tag).

- Una sola pregunta, "¿Cuánto quiere pagar el cliente?", con tres opciones: por cantidad de cuotas
  (N libre, de 1 hasta las que le quedan), por monto, y todo el préstamo (RF26 intacto). El monto
  de "N cuotas" es el **mínimo que deja exactamente K − N cuotas**, no "N × la cuota".
- Archivos tocados: `ControladorCobranzaServlet.java`, `ServicioCobranza.java` (método nuevo
  `calcularMontoMinimoParaDejarCuotas`), `cobranza/ficha.jsp`,
  `cobranza/fragmento_vista_previa_anticipo.jsp`, `recursos/js/cobranza-ficha.js`, + 2 de prueba.
  **`ServicioMotorFinanciero` no se tocó. Nada de base de datos.** `mvn test`: 795 pruebas, 0
  fallos.
- Probado en pantalla el 27/10/2026 (cajera Sofía Martínez, `CC-2026-00001`, cobro real
  `REC-2026-000005`) — Paso 18 de `06_primer_cobro.md`.
- **Resueltos**: B74, C28. **Atenuado**: C23. **Nuevo**: C39.

### PRÓXIMO PASO EXACTO (27/10/2026, tras la cuarta prueba en pantalla — tanda CERRADA)

**La ampliación del cobro quedó cerrada**: cuatro pasadas de prueba en pantalla esa noche, los dos
errores de coherencia que Joel encontró corregidos y verificados, y todo commiteado. Detalle en el
Paso 18 de `06_primer_cobro.md` y en `RF27_adelantar_capital.md` §9.

Lo que sigue, en este orden:

1. **N = K en pantalla** ("Todo el préstamo" por la vía del control de cantidad): no se probó para
   no dejar `CC-2026-00001` en `FINALIZADO`. **La lógica de RF26 en sí ya está probada** desde el
   25/10/2026 con `CC-2026-00002`; lo que falta es solo llegar a ese flujo por la vía nueva. Hace
   falta otro préstamo de prueba, o aceptar finalizar ese. **Decisión de Joel.**
2. **El caso con cuotas vencidas (pasos 12-16 de `RF22_cobro_efectivo.md` §10)**: verificado solo
   por código. Requiere una cuota realmente vencida — **decisión de Joel** sobre si se carga un
   préstamo de prueba con fecha hacia atrás o se entrega así.
3. **C39** (la cuota programada vence el 23/11/2026 y el plan regenerado muestra la próxima el
   21/11/2026, último vencimiento 21/09/2027): **solo registrado, sin tocar nada**. Verificar
   aparte contra RF27, RN-13 y el contrato — es el motor, no la pantalla.
4. Seguir con el resto de `FASE2_PLAN.md` (el primer lote: B45, B63, B76).

**Ya probado en pantalla el 27/10/2026 y cerrado**: el **doble clic en "Cobrar"** — doble clic
rápido sobre "Sí, adelantar a capital" → un solo cobro (`REC-2026-000006`), una sola fila en el
historial, y F5 sin reenvío (D-89). Primera vez en todo el proyecto que se prueba la idempotencia
en pantalla. Ver `RF27_adelantar_capital.md` §9 (quinta prueba) y `RF22_cobro_efectivo.md` §9.

## PUNTO DE RETOMADA (actualizado 26/10/2026) — historial

Leer esto primero al retomar el recorrido en una próxima sesión. Ver primero la actualización de
26/10/2026, más abajo — reemplaza en la práctica al "PRÓXIMO PASO EXACTO" de 25/10/2026 (ese
quedó desactualizado: B45/B63 del primer lote todavía NO se hicieron).

### Estado de los datos de prueba (verificado con `SELECT` en `sgcpc_db_pruebas`, 25/10/2026)

- **`CC-2026-00001`** (primer cliente): `ACTIVO`, monto aprobado 3.000.000 Gs., desembolsado
  22/10/2026, plan `ORIGINAL` vigente (12 cuotas, próxima 23/11/2026), **sin ningún pago
  todavía** — nunca se cobró nada de este préstamo.
- **`CC-2026-00002`** (segundo cliente de práctica): **`FINALIZADO`**, con
  **`estado_cierre_documental = 'COMPLETO'`** (`DEV-2026-000001`, 25/10/2026 11:37). Historial
  de pagos:
  - `REC-2026-000001` (anticipo de capital, 193.250 Gs.) — **vigente**, `CONFIRMADO`.
  - `REC-2026-000002` (anticipo de capital con vuelto, 192.850 Gs.) — **revertido**,
    `REVERTIDO`, con la reversión `AUTORIZADA` y su constancia `REV-2026-000001`.
  - Una **segunda solicitud de reversión**, sobre `REC-2026-000001`, quedó **`RECHAZADA`** por
    el gerente (el cobro sigue vigente, sin cambios).
  - `REC-2026-000003` (**cancelación total**, 25/10/2026, 1.809.330 Gs.) — `CONFIRMADO`, con
    factura `001-001-0000001` y constancia `CAN-2026-000001`. Las 11 cuotas del plan vigente
    quedan `PAGADA`.
- **Cajas**: de las 5 sesiones existentes, **4 están `CERRADA`** y **la N.º 5 sigue `ABIERTA`**
  (abierta el 25/10/2026 10:52, nunca cerrada después de la cancelación total — queda así al
  cierre de esta ronda, sin que sea un hallazgo: simplemente no se recorrió el cierre de hoy).
  La sesión N.º 3 cerró con una diferencia real (−50.000 Gs.), **ya revisada** por el gerente
  (`id_gerente_revisor` puesto). La sesión N.º 4 cerró **sin diferencia** (0 Gs.).

### Tramos del recorrido

| Tramo | Estado |
|---|---|
| 1 (Cotizador → solicitud) | **Consolidado** |
| 2 (Aprobación) | **Consolidado** |
| 3 (Formalización) | Consolidado, con **tres pasadas** (primer y segundo cliente; tercera
  pasada el 25/10/2026, cierre documental de RF29, primera vez en el proyecto) |
| 4 (Caja y desembolso) | Consolidado, con **dos pasadas** |
| 5 (Primer cobro) | **Consolidado el 25/10/2026** — adelanto a capital (con y sin vuelto),
  recibo, reversión **autorizada** y reversión **rechazada**, y **cancelación total** con sus
  tres comprobantes (recibo, factura y constancia), todo recorrido en pantalla sin cambiar la
  fecha. Solo queda pendiente lo que sí requiere fecha futura (descartado por decisión de Joel,
  ver abajo) |
| 6 (Cierre del día) | **Recorrido** |
| 7 (Días siguientes: mora, informes) | **Pendiente**, no recorrido |

### PRÓXIMO PASO EXACTO (actualizado 25/10/2026) — SUPERADA por "ACTUALIZACIÓN 26/10/2026" (ver abajo)

**DESACTUALIZADA (26/10/2026): el próximo paso no es el primer lote de `FASE2_PLAN.md` (punto 1
de esta sección) ni el tramo 7 — eso quedó reemplazado por el orden de la sección
"ACTUALIZACIÓN 26/10/2026" más abajo (auditoría visual, cotizador en dos columnas, botón
"Guardar" de documentos). Se deja el texto original como referencia histórica, no como guía.**

**La cancelación total y el cierre documental de `CC-2026-00002` ya se recorrieron por completo**
(25/10/2026, Pasos 16-17 de `06_primer_cobro.md` y "Tercera pasada de formalización: cierre
documental" de `04_formalizacion.md`) — el préstamo queda `FINALIZADO`, con
`estado_cierre_documental = COMPLETO`. El siguiente paso **no es seguir recorriendo pantallas**,
es la **fase 2**:

1. Revisar y aprobar (o ajustar) el primer lote de cambios de bajo riesgo propuesto en
   `FASE2_PLAN.md` (B36, B13, B45, B63, B3 acotado).
2. Lo que queda sin recorrer en pantalla, para más adelante, se divide en dos grupos: lo que
   **requiere fecha futura** (cobro de cuota realmente vencida, mora, pago parcial, vencimiento
   anticipado — descartado por decisión de Joel, ver abajo) y lo que **todavía no se tocó en
   absoluto**: **informes** (RF31-RF35, tramo 7) y **administración** (usuarios, perfiles,
   parámetros, auditoría como pantallas propias — RF36-RF40).

### ACTUALIZACIÓN 26/10/2026 — alcance de la fase 2 y estado de la entrega

**Alcance de la fase 2, vigente** (decisión de Joel, 25/10/2026 noche, ver `FASE2_PLAN.md`
§"Ajuste de alcance"): **solo UX/UI y presentación — texto y formato, sin tocar lógica de
negocio, permisos, cálculos ni esquema.** Cualquier hallazgo de este plan o de la auditoría
técnica que cambie comportamiento queda anotado, sin tocar código, hasta una ronda aparte.

**Hechos en esta ronda** (commiteados, rama `d149-compromisos-anteproyecto`): B13 (domicilio sin
normalizar), B3/B31 ("No, volver" por defecto en las confirmaciones), B36 (singular/plural en el
panel), B23 (nombre legible en auditoría del comprobante de desembolso). B45/B63/B45 del "primer
lote" de `FASE2_PLAN.md` **no se hicieron** — quedan pendientes, no descartados.

**Pendiente, en este orden** (interrumpido por una entrega urgente al servidor de la
institución, ver `docs/entregas/ENTREGA_SERVIDOR.md`):

1. **Auditoría visual**: propuesta de dirección de diseño para login, estilo general
   (`sgcpc-tema.css`), cotizador y carga de documentos — presentada a Joel, **sin aplicar
   todavía**, a la espera de su aprobación.
2. **Cotizador en dos columnas**: hoy el formulario y el resultado están apilados en columnas
   angostas (`max-width` fijo) que no usan el ancho de la pantalla — cambio de layout propuesto
   (grilla de Bootstrap, formulario a la izquierda, resultado+tablas a la derecha), sin tocar
   `ServicioCotizacion` ni ningún cálculo.
3. **Botón "Guardar" de carga de documentos, mal ubicado**: Joel lo reportó, pero ninguna de las
   4 pantallas que suben archivos (`clientes/ficha.jsp` pestaña Expediente, `formalizacion/ficha.jsp`,
   `desembolso/ficha.jsp`, `formalizacion/cierre-documental.jsp`) tiene hoy un botón que diga
   literalmente "Guardar" junto a una carga — **falta que Joel confirme la pantalla exacta** (ruta
   o captura) antes de proponer nada ahí.
4. **Script de entrega**: la entrega de hoy se armó a mano (ver "Pendiente" en
   `docs/entregas/ENTREGA_SERVIDOR.md`) — considerar un script que lo automatice si se repite.

### Pendiente de decisión de Joel

- **Cómo recorrer lo que requiere fecha futura** (descartado por ahora, decisión del
  23/10/2026): cobro de una cuota realmente vencida, mora (moratorio/punitorio/alerta de
  garante), pago parcial de una cuota vencida, factura con interés devengado real, y
  vencimiento anticipado (cláusula OCTAVA — este último, además, sin ningún mecanismo en el
  código, tenga o no fecha futura).

### Preguntas para el dueño de Capital Crédito, acumuladas en el recorrido

Sin clasificar A/B — cada una queda citada en su hallazgo de origen, no se decide acá:

1. ¿Qué hacer con un informe de Informconf cargado y nunca usado para registrar la consulta?
   (`01_llega_el_cliente.md`, cierre de C15).
2. ¿Cuántas horas de espera son razonables antes de que la bandeja de Aprobación avise en
   amarillo, y "al día siguiente" o "24 horas corridas" como criterio para el rojo? (B39,
   `03_aprobacion.md`).
3. ¿El panel debería avisar cuánto efectivo necesitaría la caja si se aprueba una solicitud
   pendiente? (B37, `03_aprobacion.md`).
4. ¿"Formaliza la oficial, el gerente solo como respaldo" es realmente la forma de trabajo del
   negocio? (`03_aprobacion.md` § "Forma de trabajo del negocio" — pendiente de confirmar, no
   cambia el sistema).
5. Adelantar a capital no "tacha" la cuota adelantada, solo recorta el final del plan — ¿es la
   expectativa correcta para comunicarle al cliente, o convendría otro diseño en la fase 2?
   (C23, `06_primer_cobro.md`).
6. El plan firmado que el cliente se lleva impreso queda desactualizado tras un adelanto —
   ¿conviene habilitar un camino más directo para reimprimirlo, o ampliar el acceso de la
   cajera al Informe de estado de cuenta? (C26, `06_primer_cobro.md`).
7. El monto aplicado en un adelanto queda limitado al selector "1/2/3 cuotas" — ¿el negocio
   querría permitir un monto libre, o un "saldo a favor" en vez de devolver vuelto en efectivo?
   (C28, `06_primer_cobro.md`).
8. Solo la operación más reciente de un préstamo puede revertirse — ¿alcanza esta regla, o
   convendría un camino más directo para revertir una operación anterior? (C29,
   `06_primer_cobro.md`).
9. ¿Conviene un aviso dentro del sistema a la cajera cuando el gerente autoriza una reversión
   que descuenta su caja? (C32, `06_primer_cobro.md`).
10. ¿El arqueo debería marcar de algún modo, dentro de sus categorías, una operación ya
    revertida (en vez de solo corregir el total aparte con la Compensación)? (C34,
    `06_primer_cobro.md`).
11. ¿Cómo se resuelve en la operación real en papel que el arqueo firme "Recibí: [gerente]" sin
    que ese gerente haya intervenido? ¿Y debería el arqueo regenerarse una vez que el gerente
    revisa una diferencia? (C22/C27, `07_cierre_del_dia.md`).

### Antes de tocar cobro o comprobantes

**Leer obligatoriamente `06_cobro_mapa_completo.md` y `06_comprobantes_recibo_factura.md`**
(ya en el "Protocolo antes de cualquier cambio" más abajo) — ambos ya tienen el análisis
exhaustivo de cómo y por qué el sistema decide hoy cada cosa; no repetirlo desde cero.

Documentar cómo funciona HOY el sistema, recorriéndolo como una "obra de teatro" en el orden
real de un día de la financiera, con datos realistas cargados por pantalla. **Durante el
recorrido no se corrige código**: los hallazgos se anotan en la ficha del paso correspondiente
y se resuelven después, en su propio RF (`docs/funcionamiento/RFxx_*.md`).

Este recorrido **retoma y reemplaza** a `docs/funcionamiento/FLUJO_OPERATIVO.md` (mudado acá el
21/10/2026 — ver el aviso en ese archivo y el historial de cambios más abajo).

**Esta carpeta no duplica la documentación por RF**: cada ficha de paso enlaza al documento
`RFxx_*.md` que ya tiene el texto del anteproyecto, el análisis completo y las decisiones. El
recorrido aporta la vista "de punta a punta, en el orden real del día" y la matriz RF → escena,
que hoy no existe en ningún otro documento.

## Protocolo antes de cualquier cambio

Antes de proponer un cambio, una mejora o un módulo nuevo (en código, no en este recorrido):

1. **Leer primero**: la escena del recorrido que corresponde, su anexo "qué pasa por detrás", el
   `RFxx_*.md` del módulo afectado, y la matriz RF → escena (más abajo en este archivo).
2. **Si la parte afectada figura como "no recorrido todavía" o no tiene escena asignada: NO
   proponer el cambio todavía.** Primero hacer un análisis de solo lectura (sin tocar nada) y
   avisar a Joel que esa parte debe recorrerse en pantalla antes de tocarla.
3. **Revisar en los anexos** qué otras pantallas, tablas, estados y documentos dependen de lo
   que se va a tocar, y listarlos explícitamente en la propuesta — no alcanza con listar el
   archivo que se va a editar.
4. **Cambio mínimo, sin parches ni soluciones temporales.** Cada cambio cita su RF o decisión
   (`D-xx`) y el hallazgo que resuelve (A/B/C) — nunca un cambio "porque parece mejor", sin una
   cita concreta detrás.
5. **Después de cada cambio aprobado y probado**: actualizar la escena y el anexo del recorrido
   correspondientes, para que la documentación siga reflejando el sistema real (nunca quedar
   desactualizada respecto del código).
6. **Antes de modificar cualquier parte del cobro o de los comprobantes, leer obligatoriamente
   `06_cobro_mapa_completo.md` y `06_comprobantes_recibo_factura.md`** — en particular, antes de
   cambiar la emisión de comprobantes, la sección **"Por qué recibo, factura y constancia de
   cancelación (caso real 25/10/2026)"** de este último, con la regla exacta y la cuenta
   verificada de por qué una sola operación puede emitir los tres documentos a la vez.
7. **Si no está seguro de cómo funciona algo en la práctica, o la documentación y el código no
   coinciden: no lo suponga.** Deténgase y pídale a Joel una prueba en pantalla concreta (qué
   pantalla, qué datos, qué botón, qué captura). Con las capturas se registra en
   `docs/recorrido` cómo funciona realmente, y recién después se sigue. En la fase 2, después de
   cada cambio Joel prueba en pantalla y esas capturas también se registran (regla de Joel,
   25/10/2026).

## Perfiles y usuarios de prueba (sin contraseñas)

| Perfil | Usuario de prueba | Qué hace en el recorrido |
|---|---|---|
| Oficial de crédito | `oficial` | Registra clientes, expediente, buró, crea y envía solicitudes, formaliza. |
| Gerente | `gerente` | Aprueba o rechaza solicitudes; da de baja y reactiva clientes; entrega efectivo a la caja; revisa diferencias de arqueo. |
| Cajera | `cajera` | Abre y cierra caja, desembolsa, cobra. |
| Administrador | `administrador` | Usuarios, perfiles, parámetros, auditoría. |

Los 4 quedan con `debe_cambiar_contrasena=TRUE` apenas se recrea la base (`bd/03_parametros.sql`)
— el primer paso real de la Escena 00 es ese cambio obligatorio (RF36), no un login directo.

## Escenas del recorrido

| Archivo | Qué cubre | Estado |
|---|---|---|
| [00_apertura.md](00_apertura.md) | Login, cambio de contraseña obligatorio, revisión de parámetros, panel, apertura de caja, entrega de efectivo (RF15, RF18, RF30, RF36, RF39) | Solo esqueleto |
| [01_llega_el_cliente.md](01_llega_el_cliente.md) | Alta de cliente, expediente, consulta al buró, baja/reactivación (RF02-RF04) | Con contenido migrado de `FLUJO_OPERATIVO.md` |
| [02_solicitud.md](02_solicitud.md) | Crear y enviar la solicitud a aprobación (RF05-RF06) | Con contenido migrado |
| [03_aprobacion.md](03_aprobacion.md) | Bandeja, resumen, aprobar/rechazar, reglas de decisión del crédito (RF07-RF10) | Con contenido migrado |
| [04_formalizacion.md](04_formalizacion.md) | Generar documentos, firmados, checklist (RF11-RF14) | Con contenido migrado |
| [05_desembolso.md](05_desembolso.md) | Entrega de efectivo, condiciones, comprobante, movimientos de caja (RF16-RF18) | Solo esqueleto |
| [06_primer_cobro.md](06_primer_cobro.md) | Lista de trabajo, deuda exigible, cobro, recibo y factura (RF20-RF23) | Solo esqueleto |
| [07_cierre_del_dia.md](07_cierre_del_dia.md) | Cierre de caja con arqueo, revisión de diferencia, auditoría del día, panel/informes (RF19, RF30, RF40) | Solo esqueleto |
| [08_dias_siguientes.md](08_dias_siguientes.md) | Mora, reversión, cancelación, informes mensuales — con desplazamiento de fecha (RF24-RF35) | Solo esqueleto |

## Cobertura del recorrido

Por tramo (el orden real en que se recorrieron en pantalla, no el número de archivo de la
escena — un tramo puede cubrir más de una escena, o solo una parte de una). **Consolidado** =
tiene su sección "Completitud del tramo" y los hallazgos pendientes quedaron explícitamente
clasificados o derivados a una decisión. **Recorrido parcial** = probado en pantalla, pero sin
ese cierre todavía (puede tener hallazgos C sin clasificar). **No recorrido** = ninguna pantalla
de ese tramo se probó todavía — ver el punto 2 del protocolo de arriba antes de proponer nada
sobre esa parte.

| Tramo | Qué cubre | Escena(s) | Rol | Estado | Fecha |
|---|---|---|---|---|---|
| 1 | Cotizador, alta de cliente, expediente, consulta al buró, solicitud | [01_llega_el_cliente.md](01_llega_el_cliente.md), [02_solicitud.md](02_solicitud.md) | Oficial | **Consolidado** (Completitud del tramo 1 en `02_solicitud.md`; C13/C14/C15/C17 cerrados — **C14 cerrado de verdad el 28/10/2026**, al implementarse la decisión del domicilio obligatorio (los dos criterios coinciden sin tocar `itemsFaltantesExpediente`); los hallazgos B siguen abiertos, pendientes de clasificación A/B y de la fase 2) | 17, 21 y 22/10/2026 |
| 2 | Bandeja de aprobación, resumen, aprobar/rechazar, reglas de decisión | [03_aprobacion.md](03_aprobacion.md) | Gerente | **Consolidado** (Completitud del tramo 2 y "Recorrido completo del gerente"; C18 cerrado — Por diseño, RN-07; C19 y C20 quedan documentados como riesgo parcial, a reconfirmar/resolver en la fase 2 — C20 específicamente pendiente de una base limpia) | 17, 21, 22 y 23/10/2026 |
| 3 | Generar documentos, firmados, checklist | [04_formalizacion.md](04_formalizacion.md) | Oficial | **Consolidado** (Completitud del tramo 3; C2 cerrado con la decisión D-156; segunda pasada completa con el segundo cliente de práctica — "Segunda pasada de formalización (23/10/2026)" — confirma B45 en pantalla y agrega B46-B48, con la aclaración de C3) | 21, 22 y 23/10/2026 |
| 4 | Apertura de caja y desembolso | [00_apertura.md](00_apertura.md) (parcial: login/panel/caja de la cajera), [05_desembolso.md](05_desembolso.md) | Cajera | **Consolidado** (Completitud del tramo 4; B23/B24, C10-C12; segunda pasada completa con el segundo cliente de práctica — "Segunda pasada de desembolso (23/10/2026)" en `05_desembolso.md` — confirma B49 en pantalla, agrega B50-B53, C21/C22 quedan como riesgo documentado) | 21, 22 y 23/10/2026 |
| 5 | Primer cobro de cuota por la cajera (decisión de Joel: sin cambiar la fecha) | [06_primer_cobro.md](06_primer_cobro.md) | Cajera/Gerente | **Recorrido parcial** (Completitud del tramo 5 en `06_primer_cobro.md`: bandeja, búsqueda, ficha sin cobro normal, adelanto a capital completo, pago de más/vuelto, recibo, caja, y el ciclo completo de reversión de un cobro — solicitud, autorización, caja, comprobantes, verificación final y también un rechazo ("Recorrido completo de la reversión" + Paso 15) —, todos recorridos sin cambiar la fecha; cobro de cuota vencida, mora, pago parcial de cuota vencida y factura con interés devengado quedan "no recorrido: requiere fecha futura, descartado por decisión de Joel" (igual que el vencimiento anticipado, que además no tiene ningún mecanismo en el código); solo la **cancelación total** queda pendiente — NO requiere fecha futura, puede recorrerse hoy mismo sobre el saldo vigente de `CC-2026-00002` (1.806.750 Gs.)) | 23/10/2026 |
| 6 | Cierre del día: arqueo y cierre de caja, revisión de diferencia, auditoría del día | [07_cierre_del_dia.md](07_cierre_del_dia.md) | Cajera/Gerente | **Recorrido** (Completitud del tramo 6 en `07_cierre_del_dia.md`: Paso 1 — cierre cuadrado y con diferencia, arqueo, apertura del día siguiente; Paso 2 — el gerente ve la diferencia en `/caja`, "Marcar revisada", arqueo congelado tras la revisión; Pasos 3-4 — panel/informes formales (RF30) y auditoría del día (RF40) siguen sin recorrer en pantalla, con B61-B66 y C22/C27) | 23/10/2026 |
| 7 | Días siguientes: mora, pago de más, cancelación, informes — con cambio de fecha planificado aparte | [08_dias_siguientes.md](08_dias_siguientes.md) | Oficial/Gerente/Cajera/Administrador | No recorrido | — |
| Administración | Login y cambio de contraseña (visto de paso al empezar cada tramo), usuarios, perfiles, parámetros, auditoría | [00_apertura.md](00_apertura.md) (resto), RF36-RF40 | Administrador | No recorrido como pantalla propia de administrador — el login de cada rol ya se vio al empezar los tramos 1-4; usuarios/parámetros/auditoría como pantallas dedicadas, pendientes | — |

**Definición de tramos 5-7 (Joel, 22/10/2026)**: tramo 5 = primer cobro de cuota por la cajera
(requiere adelantar la fecha al primer vencimiento); tramo 6 = cierre del día (arqueo y cierre
de caja); tramo 7 = días siguientes (mora, pago de más, cancelación, informes), con el cambio de
fecha planificado aparte. **Archivos de escena**: los 3 ya existen con el nombre correcto —
`06_primer_cobro.md` (tramo 5), `07_cierre_del_dia.md` (tramo 6) y `08_dias_siguientes.md`
(tramo 7) — **no hace falta renombrar ni crear ningún archivo nuevo** para esta numeración.

**Anexos** (qué pasa por detrás de una escena, verificado contra el código real):
[01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md) — tablas leídas/escritas,
parámetros y clases que validan, auditoría, archivos y numeraciones del tramo login → enviar a
aprobación (Escenas 01 y 02, rol oficial). [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md)
— mismo detalle del tramo login → revisión de la solicitud, antes de decidir (Escena 03, rol
gerente), con la descripción de qué haría cada botón (Aprobar/Rechazar/Cancelar) sin ejecutarlo.
[04_que_pasa_por_detras.md](04_que_pasa_por_detras.md) — mismo detalle del tramo panel →
formalizar → ver los 3 PDF → cargar los firmados (Escena 04, rol oficial), con el control de
completitud de todos los caminos de Formalización (recorridos y pendientes).
[05_que_pasa_por_detras.md](05_que_pasa_por_detras.md) — mismo detalle del tramo login → panel →
abrir caja → bandeja de desembolso → ficha → comprobante → confirmar → revisar la caja (Escenas
00/05, rol cajera), con el control de completitud de todos los caminos de Caja y Desembolso en
`05_desembolso.md` § Completitud del tramo 4.

**[manual_usuario/](manual_usuario/)**: la guía para el usuario final (un empleado nuevo de
Capital Crédito), escrita en lenguaje simple, un archivo por parte del sistema — se va agregando
tramo por tramo, a medida que cada parte queda consolidada en el recorrido (no antes). Las
capturas de pantalla las agrega Joel en el manual visual.

## Plantilla de la ficha por paso

Cada paso dentro de una escena sigue esta ficha. Mientras no se haya recorrido en pantalla, el
paso queda solo con el título y la lista de lo previsto (sin completar los campos).

```
### Paso N — <nombre del paso>

- **Usuario/rol**:
- **Pantalla y ruta**:
- **Qué hace el usuario**:
- **Datos que pide** (obligatorios y validaciones):
- **Qué responde el sistema** (mensajes y bloqueos):
- **Regla de negocio** (RF/RN + clase/servicio): ver `RFxx_....md`
- **Qué cambia en la base** (tablas y estados):
- **Qué registra la auditoría**:
- **Qué sigue**:
- **Hallazgos**:
- **Captura**: (pendiente)
```

## Matriz RF → escena → cumple/observación

Los 40 RF del anteproyecto, con la escena y el resultado **por completar** a medida que se
recorre cada uno en pantalla (hoy en blanco a propósito — no se inventa el resultado).

| RF | Qué cubre | Documento | Escena | Cumple/observación |
|---|---|---|---|---|
| RF01 | Cotizador (simular préstamo) | [RF01_cotizador.md](../funcionamiento/RF01_cotizador.md) | [01_llega_el_cliente.md](01_llega_el_cliente.md) | Cumple. Hallazgos B (sin acceso directo desde el Panel, sin botón "Siguiente: verificar si califica", resultado no al costado) |
| RF02 | Alta de clientes | [RF02_clientes.md](../funcionamiento/RF02_clientes.md) | [01_llega_el_cliente.md](01_llega_el_cliente.md) | Cumple. Hallazgos B (papel de requisitos incompleto, cédula duplicada detectada tarde) |
| RF03 | Expediente de documentos | [RF03_expediente.md](../funcionamiento/RF03_expediente.md) | [01_llega_el_cliente.md](01_llega_el_cliente.md) | Cumple. Comprobante de domicilio: **decisión tomada (21/10/2026): obligatorio — implementación pendiente (fase 2)** |
| RF04 | Autorización + consulta al buró | [RF04_buro.md](../funcionamiento/RF04_buro.md) | [01_llega_el_cliente.md](01_llega_el_cliente.md) | Cumple. Hallazgos B (puntaje sin rango indicado; PDF de autorización con el domicilio sin armar) |
| RF05 | Solicitud, cuota, afectación | [RF05_solicitud.md](../funcionamiento/RF05_solicitud.md) | [02_solicitud.md](02_solicitud.md) | Cumple. Hallazgos C clasificados B (fecha propuesta distinta de la del cotizador, por diseño; monto editable en Borrador, por diseño — nombre del botón "Simular" confuso) |
| RF06 | Enviar a aprobación / cancelar | [RF06_envio_aprobacion.md](../funcionamiento/RF06_envio_aprobacion.md) | [02_solicitud.md](02_solicitud.md) | Cumple. Comprobante de domicilio: **decisión tomada (21/10/2026): obligatorio — implementación pendiente (fase 2)** (mismo hallazgo de RF03) |
| RF07 | Bandeja de aprobación | [RF07_bandeja_aprobacion.md](../funcionamiento/RF07_bandeja_aprobacion.md) | [03_aprobacion.md](03_aprobacion.md) | Cumple. Hallazgos B (sin columna de clasificación/mora ni color por afectación o espera, fila entera no clickeable) |
| RF08 | Resumen de la solicitud | [RF08_resumen_solicitud.md](../funcionamiento/RF08_resumen_solicitud.md) | [03_aprobacion.md](03_aprobacion.md) | Cumple. Checklist de 8 requisitos se recalcula en vivo; condiciones solicitadas quedan congeladas al enviar. Hallazgos B ("Resumen para decidir" arriba, separar Aprobar/Rechazar de Cancelar, sin ⓘ en los botones, expediente sin "Ver" ahí mismo) |
| RF09 | Aprobar | [RF09_aprobar.md](../funcionamiento/RF09_aprobar.md) | — | — |
| RF10 | Rechazar | [RF10_rechazar.md](../funcionamiento/RF10_rechazar.md) | — | — |
| RF11 | Formalización (fecha del primer vencimiento) | [RF11_formalizacion.md](../funcionamiento/RF11_formalizacion.md) | [04_formalizacion.md](04_formalizacion.md) | Cumple. Rango 20-45 días y afectación actual recalculada, verificados en el código (no forzado a fallar en pantalla hoy). Hallazgos B1/B2/B8 (nombres de botones, texto de ayuda largo, "Regenerar" se confunde con "Re-emitir") |
| RF12 | Contrato/pagaré/plan en PDF | [RF12_documentos_pdf.md](../funcionamiento/RF12_documentos_pdf.md) | [04_formalizacion.md](04_formalizacion.md) | Pagaré y plan de pagos sin hallazgos. **Contrato con hallazgo C3 (probable A): la cláusula SEGUNDA describe mal a qué día quedan ancladas las cuotas siguientes a la primera** (contradice el plan real, confirmado en el Paso 9). Hallazgos B11 (apócope "veintiún"), B12 (espacio en blanco, ya documentado en el propio `.jrxml`), B13 (domicilio/destino sin normalizar mayúsculas). C5: cita "Ley 6624/2020" rastreada hasta RNF18 del propio anteproyecto; revisión legal de contrato/pagaré ya pendiente desde antes de este recorrido |
| RF13 | Firmados + checklist | [RF13_firmados_checklist.md](../funcionamiento/RF13_firmados_checklist.md) | [04_formalizacion.md](04_formalizacion.md) | Cumple — carga de a uno, mensajes "Faltan"/"Checklist completo", checklist completo habilita la bandeja de la cajera (mismo método, confirmado en el código). Hallazgos B6 (etiqueta "firmado por el cliente" repetida/incompleta para el contrato), B7 (sin un botón por renglón), B10 (propuesta integral de la ficha, fase 2). **C7, clasificación A/B pendiente**: existe un camino para reemplazar un firmado mientras el checklist sigue incompleto; no existe ningún camino desde la pantalla una vez completo |
| RF14 | Anular antes del desembolso | [RF14_anular_prestamo.md](../funcionamiento/RF14_anular_prestamo.md) | — | No recorrido todavía — solo gerente/administrador, ver "Completitud del tramo 3" en `04_formalizacion.md` |
| RF15 | Apertura de caja | [RF15_apertura_caja.md](../funcionamiento/RF15_apertura_caja.md) | [00_apertura.md](00_apertura.md) | Cumple. Fondo sugerido = suma de los préstamos listos con checklist completo; abrir con una sesión ya `ABIERTA` se rechaza antes de escribir nada (índice único parcial, no es posible abrir dos cajas a la vez). Hallazgos B16-B19 (panel sin aviso, mensaje de éxito sin "qué sigue", sin acceso directo a Desembolsos), C8 (menú/ficha de la cajera por perfil). **C9, clasificación pendiente**: el fondo inicial ya representa la entrega física del gerente; no tiene su propio registro de movimiento, riesgo de duplicarlo si el gerente entrega aparte por el mismo monto |
| RF16 | Condiciones del desembolso | [RF16_condiciones_desembolso.md](../funcionamiento/RF16_condiciones_desembolso.md) | [05_desembolso.md](05_desembolso.md) | Cumple — las 4 condiciones (checklist, documentos vigentes, caja del día, saldo suficiente) se revalidan en el servidor en las 3 pantallas que las necesitan (ficha, imprimir comprobante, confirmar), un solo método (`asegurarCondicionesDesembolso`). Ninguno de los 4 rechazos se forzó en pantalla este tramo (C10). **C11, clasificación pendiente**: con documentos vencidos y firmados ya cargados, no existe ningún camino de salida puntual (regenerar/re-emitir quedan bloqueados por el mismo checklist que habilita el desembolso) |
| RF17 | Comprobante antes de entregar | [RF17_comprobante_desembolso.md](../funcionamiento/RF17_comprobante_desembolso.md) | [05_desembolso.md](05_desembolso.md) | Cumple — el comprobante se imprime y firma antes de entregar el efectivo; la casilla de confirmación se revalida también en el servidor (no solo en el navegador). Hallazgo B21 (numerador `DES` de 6 dígitos contra `CC` de 5). **C12 (probable A)**: el PDF toma la fecha elegida del primer vencimiento, no la fecha hábil efectiva que de verdad usa el plan — misma familia que C3 (contrato, tramo 3) |
| RF18 | Movimientos, saldo teórico, reimpresión | [RF18_movimientos_caja.md](../funcionamiento/RF18_movimientos_caja.md) | [05_desembolso.md](05_desembolso.md) | Cumple — movimiento `DESEMBOLSO` (signo −1) por el monto neto, saldo teórico = `saldo_inicial + Σ(movimientos.monto × signo)` (mismo cálculo en toda la escena), reimpresión marcada y auditada, nunca se cachea un PDF viejo. Hallazgos B20 (sin efectivo en caja visible en la bandeja), B22 (mensaje de éxito sin "qué sigue"), B24 (tabla de movimientos sin columna de saldo acumulado) |
| RF19 | Cierre con arqueo | [RF19_cierre_arqueo.md](../funcionamiento/RF19_cierre_arqueo.md) | — | No recorrido en pantalla todavía — la sesión de caja N.º 1 se cerró hoy (21/10 → 22/10) pero el recorrido en pantalla de "Cierre y rendición" queda para el tramo 6 (ver la Nota en `05_desembolso.md` y "A confirmar" en `05_que_pasa_por_detras.md`) |
| RF20 | Lista de trabajo de cobranza | [RF20_lista_trabajo_cobranza.md](../funcionamiento/RF20_lista_trabajo_cobranza.md) | — | — |
| RF21 | Deuda exigible, mora, IVA | [RF21_deuda_exigible.md](../funcionamiento/RF21_deuda_exigible.md) | — | — |
| RF22 | Cobro en efectivo, vuelto | [RF22_cobro_efectivo.md](../funcionamiento/RF22_cobro_efectivo.md) | — | — |
| RF23 | Recibo y factura con IVA | [RF23_recibo_factura.md](../funcionamiento/RF23_recibo_factura.md) | — | — |
| RF24 | Gestiones, promesas de pago | [RF24_seguimiento_mora.md](../funcionamiento/RF24_seguimiento_mora.md) | — | — |
| RF25 | Reversión del cobro | [RF25_reversion_cobro.md](../funcionamiento/RF25_reversion_cobro.md) | — | — |
| RF26 | Monto para cancelar | [RF26_cancelacion_anticipada.md](../funcionamiento/RF26_cancelacion_anticipada.md) | — | — |
| RF27 | Adelantar capital | [RF27_adelantar_capital.md](../funcionamiento/RF27_adelantar_capital.md) | — | — |
| RF28 | Constancia + finalizado | [RF28_constancia_cancelacion.md](../funcionamiento/RF28_constancia_cancelacion.md) | — | — |
| RF29 | Cierre documental | [RF29_cierre_documental.md](../funcionamiento/RF29_cierre_documental.md) | [04_formalizacion.md](04_formalizacion.md) | Cumple. Primer recorrido en pantalla del proyecto (25/10/2026), sobre `CC-2026-00002`: constancia de devolución `DEV-2026-000001`, escaneo firmado, cierre completado. **Segundo cierre el 28/10/2026 sobre `CC-2026-00003` (`DEV-2026-000002`), llegando desde la tarjeta nueva del panel** (RF29 §9). Hallazgos: ~~B76~~ **resuelto el 28/10/2026**, B77 (los 3 pasos aparecen de a uno, sin indicar cuántos son), B78 (la constancia no identifica quién entregó físicamente el pagaré), **B85 (nuevo, 28/10/2026: el nombre "Cierre documental" no se entiende — se trata junto con B77 y B78)** |
| RF30 | Panel con indicadores | [RF30_panel.md](../funcionamiento/RF30_panel.md) | [03_aprobacion.md](03_aprobacion.md) (parcial: solo el panel del gerente) + **28/10/2026: panel del oficial y del gerente, probados en pantalla con el circuito real** | Cumple. **28/10/2026: tres tarjetas nuevas, probadas en pantalla — ~~B45~~, ~~B63~~ y ~~B76~~ resueltos** (RF30 §9), con lo que queda cerrado el "principio de paneles" de § Hallazgos generales. Falta todavía el barrido de los 4 perfiles de una sola pasada (cajera y administrador, RF30 §10). Hallazgos abiertos: B ("pendientes de aprobar" sin resaltar, sin número en el menú "Aprobación"), B36 (singular/plural en 6 tarjetas), B49 (caja abierta de otro día) |
| RF31 | Resumen financiero, mensual, cartera, proyección | [RF31_informes_dueno.md](../funcionamiento/RF31_informes_dueno.md) | — | — |
| RF32 | Informes de operación y cobranza | [RF32_informes_operacion.md](../funcionamiento/RF32_informes_operacion.md) | — | — |
| RF33 | Facturas del mes e IVA | [RF33_facturas_iva.md](../funcionamiento/RF33_facturas_iva.md) | — | — |
| RF34 | Estado de cuenta, reimprimir | [RF34_estado_cuenta.md](../funcionamiento/RF34_estado_cuenta.md) | — | — |
| RF35 | Filtrar por fecha, exportar PDF/Excel | [RF35_filtros_exportacion.md](../funcionamiento/RF35_filtros_exportacion.md) | — | — |
| RF36 | Ingreso, cambio obligatorio de contraseña | [procesos/login.md](../funcionamiento/procesos/login.md) | — | — |
| RF37 | Administrador: crear/bloquear/inactivar/reactivar/restablecer | [RF37_usuarios.md](../funcionamiento/RF37_usuarios.md) | — | — |
| RF38 | Opciones según el perfil | [RF38_perfiles.md](../funcionamiento/RF38_perfiles.md) | — | — |
| RF39 | Parámetros + historial de cambios | [RF39_parametros.md](../funcionamiento/RF39_parametros.md) | — | — |
| RF40 | Auditoría con filtros | [RF40_auditoria.md](../funcionamiento/RF40_auditoria.md) | — | No recorrido en pantalla este tramo (no se usó `/auditoria`). **Hallazgo encontrado desde el tramo 4 (B23, ver `05_desembolso.md`)**: revisados los 17 archivos que escriben en `auditoria_operaciones`, `IMPRIMIR_COMPROBANTE_DESEMBOLSO` y `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` son las ÚNICAS 2 operaciones de todo el sistema sin nombre legible en `EtiquetasAuditoria` — se verían con el código técnico en vez de un texto en español |

## Hallazgos generales (principios transversales, no de una sola escena)

**El Panel de cada rol muestra su trabajo pendiente, en el orden del circuito; cuando alguien
termina su parte, el siguiente se entera por su Panel.** Encontrado el 21/10/2026, al cerrar el
tramo 2 (gerente aprueba) y empezar el tramo 3 (oficial formaliza) — ver
[03_aprobacion.md](03_aprobacion.md) y [04_formalizacion.md](04_formalizacion.md).

> **CERRADO el 28/10/2026.** Los tres huecos que faltaban de este principio —**B45** (oficial,
> aprobadas para formalizar), **B63** (gerente, diferencias de caja sin revisar) y **B76**
> (oficial, cierre documental pendiente)— **se implementaron y se probaron en pantalla** en el
> primer lote de código de la fase 2. Las tres reusan la consulta que ya alimenta la pantalla
> donde el trabajo se resuelve, sin agregar ninguna cuenta nueva. Ver `AVANCE.md` del 28/10/2026
> y `RF30_panel.md` §9. Lo que sigue abierto de este mismo principio, sin confirmar en pantalla,
> está en la penúltima viñeta ("A revisar en cada tramo que siga"), ya sin el caso de las
> diferencias de arqueo. El detalle de abajo queda como registro de cómo se encontró cada uno.

- **Ya existe, para el gerente**: "1 solicitudes pendientes de aprobar" (con enlace a
  Aprobaciones) y "reversiones por autorizar" — el panel ya le avisa al gerente su propio
  trabajo pendiente.
- **Falta, confirmado en este tramo**: la oficial, después de que el gerente aprueba, no ve
  ningún aviso — "Mis solicitudes en curso" pasa a 0 y ahí termina, sin ningún "Aprobadas para
  formalizar (N)" que la empuje a seguir. Tampoco hay ningún aviso para una solicitud
  **rechazada** (ahí el siguiente paso no es del sistema: alguien tiene que avisarle al cliente).
  **Registrado como B45 (prioridad alta) en `03_aprobacion.md`** (22/10/2026, **confirmado en
  pantalla el 23/10/2026**): verificado en el código que el Panel del oficial no calcula ni
  muestra ninguna tarjeta de Formalización — ni en `IndicadoresPanel`, ni en `ServicioPanel`,
  ni en `panel.jsp`; se sugiere una tarjeta "Préstamos aprobados para formalizar (N)"
  reutilizando `SolicitudDAO.listarAprobadasSinFormalizar` (el mismo método que ya alimenta la
  bandeja de Formalización). **Implementado así el 28/10/2026, con un cambio de texto decidido
  por Joel: la tarjeta dice "Solicitud(es) aprobada(s) para formalizar"**, no "Préstamos" — en
  esta etapa todavía no hay préstamo (la fuente es `solicitudes_credito`; el préstamo nace al
  formalizar).
- **Confirmado en pantalla (23/10/2026)**: cajera "listos para desembolsar" — el panel sí
  avisa (sube de 0 a 1 solo con completar el checklist, sin que nadie le diga nada) — ver
  `00_apertura.md` § "Registro adicional: segunda pasada". Contraste directo con la oficial
  (B45): acá el principio de paneles ya está implementado.
- **Nuevo hueco encontrado del mismo principio, en sentido inverso (23/10/2026)**: el panel de
  la cajera muestra "Efectivo en caja" de una sesión abierta de un día anterior sin avisar que
  hay que cerrarla antes de operar — **B49** en `00_apertura.md`. No es que falte un indicador
  nuevo (como B45): acá el indicador existe, pero no distingue si el estado que muestra
  (caja abierta) ya es un bloqueo esperando a que la cajera actúe.
- **A revisar en cada tramo que siga** (mismo principio, sin confirmar todavía en pantalla):
  oficial "esperando firmados" (después de generar documentos, mientras el cliente firma) y
  gerente "entregar efectivo a la caja" (cuando la cajera necesita fondo). El tercero que estaba
  en esta lista, "diferencias de arqueo", se confirmó en pantalla el 23/10/2026 como **B63** y
  **quedó resuelto el 28/10/2026**.
- **Encaja con RF30** ("mostrar un panel con indicadores") — no cambia ninguna lógica de
  negocio, solo mostraría en el Panel lo que el sistema ya calcula en otro lado (listas que ya
  existen: pendientes de aprobar, pendientes de desembolso, reversiones por autorizar, etc.).

## Ciclo de vida completo del segundo cliente (22–25/10/2026)

Recorrido de punta a punta de un segundo caso real, encadenando los tramos 1-6 (incluida la
**cancelación total** y el **cierre documental**, los dos últimos pasos de la vida de un
préstamo), usado para confirmar en pantalla (y no solo en el código) buena parte de los
hallazgos del primer cliente, y para encontrar varios nuevos. Resumen por escena, con los
commits de referencia (orden cronológico, los más recientes al final de cada fila) para quien
quiera revisar el detalle completo de cada paso.

| Escena | Qué pasó | Commits de referencia |
|---|---|---|
| Cotizador (consolidación) | El cotizador queda **Consolidado**, usando la cotización del segundo cliente como ejemplo | `91ab013` |
| Alta y precalificación | Registro del segundo cliente, precalificación, papel de requisitos | `8c6a7ef`, `1dce3c1`, `50d4f4a` |
| Expediente y buró | Autorización, cédula, comprobante de ingreso/domicilio, consulta a Informconf | `30782e7`, `f7bea22`, `3912137`, `a9d8fdd` |
| Solicitud (tramo 1) | Nueva solicitud, intento rechazado, camino real de 5 pasos al Expediente, checklist completo, envío exitoso, cierre del tramo | `69a0d7d` … `56ba671` (11 commits) |
| Aprobación (tramo 2) | Panel del gerente, bandeja, revisión, verificación urgente del PDF del buró, aprobar, cierre del tramo | `b7feb96` … `6ed0050` (8 commits) |
| Formalización (tramo 3) | Bandeja, Formalizar (fecha recalculada), generar documentos, ficha completa, checklist de firmados, cierre del tramo | `395cf09` … `8c834cb` (6 commits) |
| Panel de la cajera (tramo 4, apertura) | Confirma que el panel SÍ avisa el trabajo pendiente, pero no una caja de otro día | `918c8a3` |
| Desembolso (tramo 4) | Intento bloqueado por la caja de otro día, desembolso con la caja del día, comprobante, cierre del tramo | `a3e72db`, `bbb74dd`, `21eba42` |
| Cierre del día (tramo 6, Paso 1) | Apertura/cierre del Paso 1, cierre real de la sesión, PDF de arqueo | `e45f1de`, `8715b42`, `ea1a88b` |
| Cierre → apertura del día siguiente | Cierra el ciclo completo: sesión del 22/10 cerrada, sesión del 23/10 abierta | `58f873f` |
| Primer cobro (tramo 5) | Plan sin cambiar fecha, bandeja y búsqueda, ficha sin botón de cobro normal, adelanto a capital (detalle, confirmación, recibo, caja), mapa completo del cobro, comprobantes recibo/factura | `d80c5a8`, `56fdada`, `df916eb`, `67acdd3`, `9321df2`, `a9d8adf`, `af9f481`, `a4a66f5`, `16ab37d` |
| Cierre del día, completo (tramo 6) | Cierre con diferencia (sesión N.º 3), arqueo con diferencia, panel del gerente sin aviso, el gerente revisa en `/caja`, "Marcar revisada" | `b16874c`, `1779f14`, `1f669b9`, `5e874a5`, `82e0c6b` |
| Cancelación total (tramo 5, 25/10/2026) | Ficha y confirmación de "Cancelar todo", factura, recibo con desglose por cuota, constancia de cancelación — cierre completo del cobro de este préstamo | `a3d9199`, `38c7860`, `cf88e15` |
| Cierre documental (tramo 3, 25/10/2026) | Primer recorrido en pantalla de RF29 en todo el proyecto: constancia de devolución, escaneo firmado, cierre completado | (este cierre) |

**Hallazgos nuevos de este ciclo, con su estado**:

| Hallazgo | Resumen | Estado |
|---|---|---|
| B25 | "Imprimir requisitos" sin auditoría | Abierto |
| B26 | Autorización de buró debería ir primera en la lista del Expediente | Abierto |
| B27 | Salto automático a "Consulta al buró" sin avisar qué falta | Abierto (ampliado) |
| B28 | Cuadro de éxito/"Siguiente: ④" no avisan Expediente incompleto | Abierto (confirmado en pantalla) |
| B29 | "Nueva solicitud" saca al oficial de la ficha del cliente | Abierto |
| B30 | "Enviar a aprobación" habilitado con ✗, sin confirmación en ese caso | Abierto (confirmado en pantalla) |
| B31 | "Cancelar solicitud" junto a "Enviar a aprobación" (familia B3) | Abierto (se repite varias veces) |
| B32 | Camino real de 5 pasos al Expediente desde el aviso de faltantes | Abierto (ampliado) |
| B33 | Enlace "Ver ficha completa / documentos" pasa desapercibido | Abierto |
| B34 | Botón de ② sigue ofreciendo "Siguiente: ③" con la solicitud ya en curso | Abierto (repetido) |
| B35 | "Expediente completo" no distingue si el buró ya está hecho | Abierto |
| B36 | Concordancia singular/plural en varias tarjetas del Panel | Abierto |
| B37 | Panel sin aviso de cuánto efectivo necesitaría la caja si se aprueba | Abierto (para el dueño) |
| B38 | Bandeja de Aprobación sin columna de buró | Abierto (menor) |
| B39 | "Esperando" de la bandeja de Aprobación nunca cambia de color/alerta | Abierto (para decidir con el dueño) |
| B40 | Enlace a la ficha del cliente con nombres distintos según la pantalla | Abierto |
| B41 | Confirmación de Aprobar/Rechazar | Verificado — ambos piden confirmación clara, sin cambio pendiente |
| B42 | Sin aviso al cargar un archivo con un hash ya existente | **RESUELTO el 01/11/2026, probado en pantalla el 01/11 y el 03/11/2026** — rechaza el mismo tipo con el mismo contenido y avisa en amarillo si el contenido ya está en otro tipo del mismo expediente; búsqueda acotada a la persona o al préstamo, nunca global. Detalle: `funcionamiento/RF03_expediente.md` §2 |
| B43 | Confirmación de "Aprobar" sin monto/plazo/cliente | Abierto |
| B44 | Mensaje de éxito al aprobar no dice quién formaliza | Abierto |
| B45 | Panel del oficial sin aviso de trabajo pendiente en Formalización | **Confirmado en pantalla**, prioridad alta |
| B46 | Bandeja de Formalización sin columna "Esperando" | Abierto |
| B47 | Menú "Formalización" con una sola opción del mismo nombre | Abierto (confirmado de nuevo) |
| B48 | "Nueva solicitud" puede proponer un día no hábil sin avisar | Abierto |
| B49 | Panel de la cajera no avisa una caja abierta de otro día | Abierto (confirmado en pantalla) |
| B50 | Aviso de caja de otro día sin acceso directo al formulario de cierre | Abierto (cita B19) |
| B51 | Mensaje "Caja cerrada" no dice qué sigue | Abierto (familia B18) |
| B52 | "−0 Gs." en el detalle de movimientos del arqueo | Abierto (menor) |
| B53 | Detalle del arqueo sin fecha, solo hora | Abierto (relacionado con C21) |
| C13 | Edición con evaluación en curso | **Cubierto** |
| C14 | Contradicción del comprobante de domicilio entre dos métodos | **Cerrado (28/10/2026)** — el domicilio pasó a obligatorio, los dos criterios coinciden |
| C15 | Informe de Informconf cargado sin registrar consulta | **Cubierto** |
| C17 | Oficial sin forma de retirar una solicitud ya enviada | **Cubierto** (separación de funciones) |
| C18 | Gerente sin forma de aprobar otro monto/plazo | **Por diseño** (RN-07) |
| C19 | `GET /documentos/{id}` sin control de pertenencia entre roles con acceso | Riesgo **parcial**, sin clasificar A/B |
| C20 | "Cancelar solicitud" sigue visible ya Aprobada/formalizada | Riesgo **parcial**, ampliado — candidato a A si se confirma en base limpia |
| C21 | Informe de Caja agrupa por fecha de cierre, no de apertura | Riesgo, sin clasificar A/B |
| C22 | Arqueo firma "Recibí" sin confirmación real del gerente | ~~Riesgo, sin clasificar A/B (**agravado** 23/10: igual con una diferencia real sin revisar)~~ **RESUELTO y PROBADO EN PANTALLA el 03/11/2026**: el acta nombra al gerente solo si quedó registrado en esa sesión; si no, *"Recibí (firma):"* en blanco. Detalle: `funcionamiento/RF19_cierre_arqueo.md` §2 |
| B54 | Bandeja de Cobranza sin lista de préstamos activos en general | Abierto (menor) |
| B55 | "Monto a cobrar"/"Cobrar" invitan a una acción que se rechaza por esa vía | Abierto (ampliado: el botón final de confirmación también dice solo "Cobrar") |
| C23 | Adelantar a capital no "tacha" la cuota 1, solo recorta el final del plan | Sin clasificar A/B, para el dueño |
| C24 | Cláusula SEXTA ("mantiene el monto de la cuota") confirmada con un número real | Relacionado con C4 (`04_formalizacion.md`), sin clasificar A/B |
| C25 | Redondeo del adelanto no se registra como descuento (distinto del cobro normal) | Sin clasificar A/B |
| B56 | Desglose del adelanto oculto en un desplegable cerrado por defecto | Abierto |
| C26 | Plan firmado del cliente queda desactualizado tras un adelanto | **Cerrado**: riesgo parcial (cubierto para lo esencial, falta el plan completo en papel) |
| B57 | "Solicitar reversión" — premisa corregida: solo la última operación vigente, no "cada fila" | Verificado — condición ya correcta, solo mejora de presentación posible |
| B58 | Criterio de original/copia distinto entre recibo, factura y comprobante de desembolso | Abierto |
| B59 | Correo solo en el encabezado del recibo, espacio en blanco en la variante de anticipo | Abierto (menor, familia B12) |
| B60 | Columna "Tipo" de `/caja` muestra "Cobro" para un anticipo (el arqueo sí distingue) | Abierto |
| B61 | "Sesiones cerradas" solo distingue "pendiente" por la ausencia de la etiqueta "(revisada)" | Abierto — confirmado en pantalla en los dos extremos (pendiente y ya revisada) |
| B62 | PDF de arqueo no indica si la diferencia está pendiente o revisada | Abierto |
| B63 | Panel del gerente sin aviso de diferencias de caja pendientes | **Prioridad alta** — confirmado en pantalla, mismo principio que B45 |
| B64 | Vista del gerente en `/caja` muestra "Fondo sugerido" aunque no puede abrir caja | Abierto (menor) |
| B65 | "Marcar revisada" no pide confirmación aunque es irreversible | Abierto |
| B66 | El éxito "Diferencia revisada" no muestra la resolución; ninguna pantalla la repite después | Abierto |
| C27 | El PDF de arqueo no refleja la revisión posterior del gerente (mismo documento, antes y después) | Riesgo, sin clasificar A/B — relacionado con C22 y B62 |
| B67 | "Fondo sugerido" de apertura no contempla el cambio necesario para dar vuelto | Abierto (menor) |
| C28 | El monto aplicado en un adelanto no es libre, limitado al selector 1/2/3 cuotas, sin alternativa de saldo a favor | Sin clasificar A/B, para el rediseño del cobro |
| B68 | El mismo recibo aparece con dos botones "Reimprimir" (uno por cada movimiento de caja que generó) | Abierto (menor) |
| C29 | Solo el último cobro vigente de un préstamo puede revertirse, sin límite de tiempo ni de sesión; para revertir uno anterior hay que revertir los posteriores en orden | **Completo**, confirmado en pantalla: la regla también está en la propia pantalla de Reversiones, y el encadenamiento (de la más reciente a la más antigua) se verificó de punta a punta |
| C30 | Con una reversión pendiente, la ficha sigue permitiendo adelantar/cancelar sobre un saldo con un cobro en reversión, sin aviso | ~~Riesgo parcial — la integridad se protege solo al autorizar, nunca antes~~ **RESUELTO y PROBADO EN PANTALLA el 03/11/2026**: avisa (cartel de la cajera en los tres caminos + marca en la bandeja del gerente) y **no** bloquea, por decisión de Joel. Detalle: `funcionamiento/RF25_reversion_cobro.md` §2 |
| B69 | El historial no muestra el motivo de la reversión solicitada | Abierto (menor) |
| B70 | "Autorizar" no exige comentario del gerente, a diferencia de "Rechazar" | Abierto (menor) — **confirmado en pantalla**: en el Historial, la autorizada queda con "Motivo de la decisión: -", la rechazada sí lo tiene |
| C31 | Al revertir un cobro con vuelto, la caja devuelve el monto aplicado, no el recibido | Verificado — matemáticamente correcto, sin clasificar A/B |
| B71 | La confirmación de "Autorizar reversión" no anticipa el efecto en caja ni en el plan | Abierto (menor) |
| C32 | El gerente autoriza y descuenta la caja de la cajera sin aviso dentro del sistema | **Reclasificado**: cubierto en papel (firma del cliente en la constancia de reversión); riesgo parcial solo dentro del sistema — confirmado en pantalla del lado del gerente y de la cajera |
| B72 | Encabezado de la Constancia de reversión en letra más chica que el resto de comprobantes | Abierto (menor) — sí tiene pie "Emitido por"/"Página" |
| C33 | El recibo anulado por reversión sigue ofreciendo "Reimprimir", pero nunca sale como válido | **Cubierto**, confirmado en pantalla |
| C34 | El arqueo clasifica el cobro/vuelto de un pago revertido en sus categorías normales ("Anticipos de capital"/"Vueltos entregados"), sin marcarlos — el saldo final es correcto, el desglose por categoría no distingue lo anulado | Riesgo parcial (informe de control), sin clasificar A/B — relacionado con C27 (arqueo como documento oficial) y en contraste con `/caja`, que sí marca "(revertido)" |
| B73 | El desglose por cuota de una cancelación total solo aparece en el recibo, después de confirmar — la confirmación previa no lo muestra | Abierto (matizar) |
| B74 | Con "Cancelar todo" seleccionado, el selector "1/2/3 cuotas" y "Si adelanta…" del adelanto siguen visibles, sin sentido para esa operación | **RESUELTO (27/10/2026)** — las tres formas de pagar pasaron a ser opciones excluyentes de una sola pregunta; al elegir "Todo el préstamo" esas piezas desaparecen. Probado en pantalla (Paso 18 de `06_primer_cobro.md`), commit pendiente |
| C28 | El monto aplicado en un adelanto no es libre (limitado al selector 1/2/3 cuotas); sin "saldo a favor" | **RESUELTO (27/10/2026)** — cantidad de cuotas libre (1 hasta las que le quedan) + opción "Por monto". "Saldo a favor" sigue sin existir y no se agregó (RN-21) |
| C23 | Adelantar a capital no "tacha" la cuota, solo recorta el final del plan — choque de expectativa | **Abierto, atenuado (27/10/2026)** — la pantalla ya habla de "cuántas cuotas saca del plan", dice cuántas quedan, por cuánto será la próxima y cuánto se ahorra de intereses; el motor no se tocó |
| **C39** | **(nuevo, 27/10/2026)** La cuota programada vence el 23/11/2026, pero el plan regenerado tras un adelanto muestra la próxima el **21/11/2026** y el último vencimiento el **21/09/2027**. **Ya pasaba antes** de la ampliación del 27/10/2026 | **Abierto — solo registrado, NO corregir.** A verificar aparte contra RF27, RN-13 y el contrato (`ServicioMotorFinanciero.regenerarPlanAnticipo`). Encontrado por Joel en la prueba del 27/10/2026 (`CC-2026-00001`, `REC-2026-000005`) |
| C35 | Un préstamo `FINALIZADO` con factura sigue ofreciendo "Solicitar reversión" mientras el cierre documental no esté `COMPLETO` | **Verificado en el código, por diseño** — bloqueado automáticamente al completar el cierre documental; pendiente que el contador confirme "Nota de anulación" vs. "Nota de Crédito" |
| B75 | La factura de una cancelación total describe el interés como "cuota N/M", sin indicar que es una cancelación anticipada ni el prorrateo por días | Abierto |
| C36 | Revisión legal/SET-SIFEN de la factura, y si el timbrado configurado es de prueba o real | Sin clasificar A/B, para el contador/abogado |
| C37 | El "Son:" del recibo expresa el monto aplicado, no el efectivo neto recibido, cuando hay redondeo perdonado | Sin clasificar A/B, para el contador — relacionado con C25 |
| B76 | El panel del oficial no avisa cuando hay un préstamo con el cierre documental pendiente | Abierto — mismo principio que B45/B63 |
| B77 | Los 3 pasos del cierre documental aparecen de a uno, sin indicar de entrada cuántos son | Abierto (menor) |
| B78 | La constancia de devolución no identifica a quién entregó físicamente el pagaré | Abierto (menor) |
| C38 | La devolución del pagaré figura como una casilla a marcar a mano en la constancia de cancelación, sin control real detrás de ese PDF | **Cubierto, confirmado en pantalla el 25/10/2026** — el control real es el flujo de 3 pasos de RF29 (`DEV-2026-000001`), recorrido de punta a punta |

## Historial de cambios del recorrido

| Fecha | Qué cambió |
|---|---|
| 25/10/2026 | Cierre completo del tramo 5 (cancelación total de `CC-2026-00002`: ficha, confirmación, factura, recibo y constancia — B73-B75, B13 repetido, C35-C37) y del tramo 3 (cierre documental RF29, primera vez en el proyecto — B76-B78, cierre de C38). Inicio de la fase 2: `FASE2_PLAN.md`. |
| 21/10/2026 | Creación de `docs/recorrido/`, migrando lo ya verificado de `FLUJO_OPERATIVO.md` (que queda retirado, con aviso de mudanza) a las escenas correspondientes. El cliente de ejemplo real (nombre y C.I.) se reemplazó por datos ficticios en toda la migración. |
| 17/10/2026 (heredado de `FLUJO_OPERATIVO.md`) | Probado en pantalla por Joel el recorrido 1 (alta, expediente en curso, buró, solicitud, reglas de decisión), tag `reglas-decision-probado`. Ver el detalle migrado en cada escena. |
| 16/10/2026 (heredado) | Verificados en pantalla: Formalización (grupo Domicilio) y Baja/reactivación de clientes. |
