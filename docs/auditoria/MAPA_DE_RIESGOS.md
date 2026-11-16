# Mapa de riesgos — 7 peligros antes de tocar código (fase 2)

Construido **solo con lo que ya está** en `docs/recorrido/` y `docs/auditoria/AUDITORIA_TECNICA.md`
— sin investigación nueva, salvo 3 confirmaciones puntuales de portabilidad (peligro 7) que no
estaban escritas todavía en ningún documento previo. Cada peligro: dónde está, qué lo protege
hoy, qué verificar antes y después de tocarlo. Usado por `sgcpc-cambio-seguro` como el control
obligatorio de 7 preguntas antes de cualquier cambio.

## 1. Cambios que repercuten en varios lugares

**Dónde está**: el domicilio del cliente se muestra en contrato, comprobante de desembolso y
factura (B13, la misma coma duplicada aparece en los 3); el 10 % de IVA está en un solo punto
de lectura (`ServicioMotorFinanciero.TASA_IVA`, T6 del audit) pero lo usan recibo, factura y el
control de adelantos; el redondeo de efectivo vive en dos implementaciones separadas para dos
conceptos distintos (D-141 en `ServicioCobranza` vs. `ServicioMotorFinanciero.redondear`,
observación 5 de `06_cobro_mapa_completo.md` § 13); la fecha elegida vs. la fecha efectiva
(corrida a día hábil) aparece en el contrato, el pagaré y el comprobante de desembolso (C3/C12).

**Qué lo protege hoy**: donde el dato tiene una sola fuente real (IVA, vía
`ServicioMotorFinanciero.iva()`), todo el resto del código **delega** ahí, nunca recalcula por
su cuenta (confirmado en el Área 3 del audit) — así que corregirlo ahí alcanza. Donde NO hay una
sola fuente (el domicilio, repetido en cada `.jrxml`), no hay ninguna protección: cada lugar hay
que corregirlo aparte.

**Verificar antes**: `grep` de todos los lugares donde se usa/muestra el dato que se va a tocar,
no solo el primero que aparezca — y confirmar si hay una única función fuente o varias
independientes.

**Verificar después**: revisar los 2-3 documentos/pantallas donde ese mismo dato aparece,
no solo el que motivó el cambio.

## 2. Piezas compartidas (ej. `pie.jsp`)

**Dónde está**: `plantilla/pie.jsp` (el texto "Cancelar" de todos los diálogos de confirmación
del sistema — B3, repetido en Formalización/Aprobación/Solicitud/Cobranza), `plantilla/menu.jsp`,
`plantilla/cabecera.jsp`, `plantilla/csrf.jsp` (incluido en todo formulario POST).

**Qué lo protege hoy**: nada automático — es una convención de reutilización (`<%@ include %>`),
no algo que un test verifique. El primer lote de `FASE2_PLAN.md` deliberadamente evita tocar
`pie.jsp` en su versión acotada de B3, justo por este riesgo.

**Verificar antes**: `grep -rl` de todas las pantallas que incluyen esa pieza compartida, antes
de decidir si el cambio va ahí o en un lugar más acotado (como el primer lote de B3 eligió
hacer).

**Verificar después**: revisar 2-3 pantallas distintas que usan la pieza compartida (no solo la
que motivó el cambio), para confirmar que ninguna quedó rota ni con un texto que ya no tiene
sentido en ese contexto.

## 3. Cálculos de plata

**Dónde está**: `ServicioMotorFinanciero` (plan de pagos, interés, mora, IVA, TIR/CFT,
redondeo) y `ServicioCobranza` (redondeo de efectivo, reconstrucción de líneas de factura,
`confirmarCobro`/`confirmarAnticipoCapital`/`confirmarCancelacionTotal`).

**Qué lo protege hoy**: `BigDecimal` en todo lo que es dinero real (nunca `double`, salvo el TIR
informativo, aislado — T7 del audit); una sola fuente para el IVA; **776 pruebas JUnit**, varias
con ejemplos documentados número por número (ej. `CC90001`, cuadres verificados con `SELECT`
en `06_comprobantes_recibo_factura.md`); los triggers de inmutabilidad de la Área 2 del audit
evitan que un cálculo ya aplicado se borre después.

**Verificar antes**: leer las pruebas JUnit que ya existen para ese método (`ServicioMotorFinancieroTest`/
`ServicioCobranzaTest`) — son la especificación exacta de qué número debe salir para qué
entrada, no solo un ejemplo suelto.

**Verificar después**: `mvn test` en verde, y comparar contra los ejemplos reales ya
documentados y cuadrados en `06_comprobantes_recibo_factura.md` § 5 (capital + interés + IVA =
monto aplicado, en los 4 ángulos: recibo, caja, `aplicaciones_pago`, factura).

## 4. Documentos ya emitidos

**Dónde está**: recibo, factura, constancia de cancelación, constancia de devolución, nota de
anulación — todos con el patrón `obtenerOGenerarPdf` (`ServicioCobranza`/`ServicioCaja`): se
generan una vez, se guardan los bytes, se reimprimen siempre igual.

**Qué lo protege hoy**: **D-94** — un documento ya emitido para una operación real nunca se
regenera con el diseño nuevo, ni manualmente ni por accidente (el método busca primero el PDF
ya guardado antes de generar uno nuevo). El único bypass que existe es para el caso de reversión
(recibo/factura regenerados con la franja "ANULADO" — un caso ya previsto, no un hueco).

**Verificar antes**: confirmar si el cambio es de *diseño* (aplica solo a documentos nuevos,
nunca a los viejos) o de *dato/cálculo* (ahí la pregunta es distinta: qué hacer con los
documentos ya emitidos que tenían el dato viejo — eso es una decisión de Joel, no un efecto
automático).

**Verificar después**: reimprimir un documento **ya existente** de antes del cambio y confirmar
que sigue exactamente igual; generar uno **nuevo** y confirmar que refleja el cambio.

## 5. Estructura y protecciones de la base (triggers)

**Dónde está**: `bd/01_esquema.sql` (33 tablas, 79 FK, 26 `UNIQUE`, 41 `CHECK`),
`bd/02_indices.sql` (11 tablas financieras + `auditoria_operaciones` con trigger `BEFORE DELETE`
que **rechaza** el borrado a nivel de base — Área 2 del audit). `documentos_expediente`/
`archivos_binarios` **no** están en esa lista de 11 (T5 del audit, riesgo bajo, sin acción hoy).

**Qué lo protege hoy**: los triggers rechazan cualquier intento de `DELETE` sobre las tablas
financieras, sin importar qué código lo intente; `bd/recrear_base.sh` tiene una guarda que
aborta si no apunta a `sgcpc_db_pruebas`; `bd/comparar_esquemas.sh` (de Joel, no de esta sesión)
confirma que `sgcpc_db` nunca se desvió a mano de `01_esquema.sql`/`02_indices.sql`/
`03_parametros.sql`.

**Verificar antes**: cualquier cambio de esquema pasa primero por `bd/migraciones/` (un archivo
nuevo, nunca editar el historial) y se refleja también en `01_esquema.sql`/`02_indices.sql`,
para que ambos sigan siendo la misma fuente de verdad.

**Verificar después**: `bd/recrear_base.sh` sobre `sgcpc_db_pruebas` (nunca `sgcpc_db`) y
`mvn test` en verde contra la base recreada.

## 6. Partes no vistas en pantalla

**Dónde está**: todo lo marcado "No recorrido" en la tabla "Cobertura del recorrido" de
`00_LEEME.md` — tramo 7 completo (mora con fecha real, pago parcial de cuota vencida,
vencimiento anticipado), informes (RF31-35) y administración como pantalla propia (RF36-40,
aunque el código ya exista y esté verificado por la Área 9 del audit), `RF14` (anular préstamo
antes del desembolso), y cada fila "No recorrido" puntual de las tablas de Completitud de cada
tramo.

**Qué lo protege hoy**: el propio protocolo (`00_LEEME.md` § "Protocolo antes de cualquier
cambio", punto 2) — no proponer un cambio sobre una parte no recorrida sin avisar primero.
Nada más la protege: es, por definición, la parte menos observada del sistema.

**Verificar antes**: revisar la tabla "Cobertura del recorrido" de `00_LEEME.md` — si la parte
que se va a tocar dice "No recorrido", avisarle a Joel antes de proponer nada, igual que si no
se entendiera el comportamiento real (regla de "no suponer, pedir prueba en pantalla").

**Verificar después**: si el cambio obligó a recorrer esa parte por primera vez, documentarlo en
el recorrido (mismo patrón que el cierre documental de RF29, 25/10/2026).

## 7. Portabilidad a otro servidor sin internet

**Dónde está**: el `.war` completo + la base de datos, para instalarse en el servidor de la
institución — una máquina distinta a esta, sin conexión a internet garantizada.

**Qué lo protege hoy** (Área 6 del audit + 3 confirmaciones nuevas de esta ronda, sin
investigación adicional más allá de esto):

- **Cero llamadas a internet** en todo el código del servidor (Área 6: sin `HttpClient`/
  `URLConnection`, consulta al buró manual, frontend vendorizado).
- **Cero rutas absolutas** de esta computadora en `src/main` (confirmado ahora con `grep`,
  sin resultados).
- **Fuentes de los PDF ya embebidas**: los 43 `.jrxml` usan `DejaVu Sans`, y la dependencia
  Maven `jasperreports-fonts` (`pom.xml`, junto a `jasperreports`) la trae empaquetada como
  extensión de fuentes, registrada sola con estar en el classpath — nunca depende de una fuente
  instalada en el servidor de destino (decisión ya tomada, D-17/D-68: "nunca Arial ni fuentes
  de Windows").
- **Configuración editable sin recompilar**: `bd.properties` (archivo externo, fuera del
  `.war`) para la conexión a la base; `parametros_sistema` (pantalla `/parametros`) para los
  parámetros del negocio.
- **`.claude/` nunca viaja en el `.war`**: confirmado en `pom.xml` — `maven-war-plugin` no tiene
  un `warSourceDirectory` propio, así que empaqueta el default (`src/main/webapp`) y nada más;
  `.claude/` vive en la raíz del proyecto, fuera de esa carpeta — no hace falta excluirlo a
  mano, estructuralmente no puede entrar.

**Verificar antes**: si el cambio agrega una fuente, una librería o un recurso nuevo, confirmar
que viene empaquetado (dependencia Maven o archivo vendorizado), nunca instalado a mano en esta
computadora.

**Verificar después**: `grep` de rutas absolutas y de `http://`/`https://` nuevos en lo que se
tocó; confirmar que `.claude/` sigue sin aparecer en la lista de archivos que
`maven-war-plugin` empaquetaría (misma lógica de "fuera de `src/main/webapp`", no hace falta
generar el `.war` para confirmarlo).
