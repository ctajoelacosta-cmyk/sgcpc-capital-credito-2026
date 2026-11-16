---
name: sgcpc-cambio-seguro
description: Usar SIEMPRE antes de modificar cualquier código de SGCPC (Java, JSP, SQL, .jrxml, JS/CSS). Define el protocolo obligatorio de la fase 2 — identificar el ítem, leer el contexto, el control de 7 preguntas de riesgo, pedir aprobación del diff, aplicar, probar y dejar registro — y la lista de lo que nunca se hace sin pedido explícito de Joel.
---

# Cambio seguro en SGCPC (fase 2)

Protocolo obligatorio para cualquier cambio de código a partir de la fase 2. No es un
checklist opcional: cada paso existe porque ya causó un problema real en este proyecto si se
salteó antes.

## Los tres dogmas — por encima de todo lo de abajo

Están completos en `docs/REGLAS_DESARROLLO.md` §0. Resumen operativo:

- **DOGMA 1 — se prueba hoy, punta a punta, o no está hecho.** Ningún cambio se da por
  terminado sin una prueba del circuito real que Joel pueda ver **el mismo día**. Prohibido
  "esperar a mañana" o "cuando venza una cuota". **"Verificado por código" no es "probado", y
  "vista previa" tampoco.** Todo análisis de un cambio dice **cómo se va a probar hoy**; si no
  hay forma, la primera tarea es crearla, antes del cambio.
- **DOGMA 2 — una sola base: `sgcpc_db_pruebas`** (la de `bd.properties`). Prohibido crear,
  restaurar, copiar o apuntar a otra, y prohibido `SGCPC_BD_URL` u otra variable de entorno
  para desviarla. Si hay que limpiarla, se vacían los datos **dentro de esa misma base**.
- **DOGMA 3 (28/10/2026) — las skills se actualizan con cada ítem.** Al cerrar **cada** ítem,
  en el mismo commit o el inmediato. Al empezar una sesión, si la fecha de una skill es
  anterior al último ítem de `AVANCE.md`, **se actualiza antes de seguir**. Una skill vieja no
  es documentación incompleta: es documentación que miente, y es lo primero que se lee. Si un
  ítem no cambia ninguna skill, se dice explícitamente en el resumen. Ver el paso 16 de "Al
  cerrar".

## Antes de tocar nada

1. **Identificar el ítem exacto** en `docs/recorrido/FASE2_PLAN.md` o en
   `docs/auditoria/AUDITORIA_TECNICA.md` (el código `Bxx`/`Cxx`/`Txx`). Si el pedido de Joel no
   coincide con ningún ítem ya catalogado, decírselo antes de inventar uno nuevo sobre la
   marcha.
2. **Leer el contexto completo de ese ítem**: la escena del recorrido donde se originó
   (`docs/recorrido/0x_*.md`), su anexo "qué pasa por detrás" si existe, el `RFxx_*.md` del
   módulo afectado (`docs/funcionamiento/`), y la fila de la matriz RF → escena en
   `docs/recorrido/00_LEEME.md`. No alcanza con leer solo la fila de la tabla del plan.
3. **Listar las dependencias** citadas en ese ítem (qué otras pantallas, tablas, estados o
   documentos tocaría el cambio) — si el ítem no las menciona todas, revisar los anexos antes
   de seguir.
4. **Si no está seguro de cómo funciona algo en la práctica**, o la documentación y el código
   no coinciden: parar y pedirle a Joel una prueba en pantalla concreta (qué pantalla, qué
   datos, qué botón, qué captura) — nunca suponer. Ver
   `docs/recorrido/00_LEEME.md` § "Protocolo antes de cualquier cambio", punto 7.
5. **Antes de proponerle a Joel una prueba en pantalla, verificar en el código que ese camino
   existe de verdad** (28/10/2026, B85): se le propuso volver atrás en el navegador y reenviar
   el formulario del paso 3 para volver a ver un aviso — pero con el préstamo ya cerrado la
   pantalla **no renderiza ningún `<form>`**, así que no hay botón ni token CSRF (el token solo
   llega al HTML por `plantilla/csrf.jsp`, dentro de un formulario; no hay meta tag ni variable
   JS). Antes de decir "probalo así": mirar las condiciones del JSP en ese estado, de dónde sale
   cada dato que el camino necesita, y qué filtro valida el pedido. **Si el camino no existe, se
   dice y el ítem queda pendiente** — nunca se inventa un rodeo.

   **Segunda vez, con otra causa (30/10/2026, al cerrar B83+B58)**: se le dijo a Joel que
   reimprimiera el comprobante de desembolso desde *Reportes → Estado de cuenta*, y entrando
   como gerente **ese botón no existe en la pantalla**. El camino sí existía… para la cajera:
   `reportes/estado_cuenta_ficha.jsp:34` lo condiciona a
   `getPerfilUsuario() == PerfilUsuario.CAJERO`, y `/desembolso` pide `DESEMBOLSAR`
   (`FiltroAutorizacion:62`), que el gerente **no tiene** (`ServicioAutorizacion:47`). Un camino
   de pantalla no se verifica solo mirando que el control esté escrito en el JSP: hay que
   verificar **las tres capas**, y decirle a Joel **con qué usuario** entrar.

   | Qué verificar | Dónde se mira |
   |---|---|
   | La condición que **dibuja** el control | el `if`/`hidden` del JSP, con el estado real del registro |
   | El **permiso de la ruta** que ese control invoca | `FiltroAutorizacion.RUTAS_PROTEGIDAS` (y sus excepciones por ruta) |
   | El **nivel de ese permiso para ese perfil** | `ServicioAutorizacion` (NINGUNO / CONSULTA / TOTAL) |

   **Y los números del plan de prueba salen del dato, no de una estimación** (30/10/2026, al
   probar B79): se le propuso escribir *10.000* como monto "que no llega al interés del período" —
   y sí llegaba. El mínimo real era **1.880 Gs.** (interés de 1 día 1.709 + IVA 171), un valor que
   la pantalla ya traía servido en `data-monto-anticipo-minimo` y que el análisis no leyó. Antes de
   decirle a Joel "escribí X y vas a ver el bloqueo": leer el umbral de donde lo lee el código, o
   decir que el valor hay que mirarlo en pantalla. Un caso de prueba con un número inventado le
   hace perder la pasada.

   Un permiso de ruta sin botón en ninguna pantalla **no es un camino de prueba**: se anota como
   ítem nuevo y se le pregunta a Joel (fue el caso del comprobante de desembolso para el gerente,
   que tiene el permiso de la ruta pero ninguna pantalla que le ofrezca el botón). Y al indicar
   la prueba se nombra el **perfil y el usuario concreto** ("entrá como Sofía, cajera"), nunca
   solo la pantalla.
6. **Antes de asignar un `Bxx`/`Cxx` nuevo, buscar el último usado** con
   `grep -rhoE "\bB[0-9]{1,3}\b" docs/ --include=*.md | sort -u -V | tail` sobre **todos** los
   documentos — nunca confiar en la nota "Huecos de numeración" de `FASE2_PLAN.md`, que ya quedó
   vieja una vez y provocó un **B76 duplicado**. Después de asignar, actualizar esa nota.

   **Y buscar el TEMA, no solo el número** (01/11/2026): el 30/10 se registró **B101** para el
   *"Son:"* del recibo, que ya existía como **C37** desde el recorrido — mismo archivo, mismo
   parámetro, mismo dueño de la decisión. El grep del número libre encuentra **números**; un
   duplicado de contenido solo lo encuentra un grep de **las palabras del hallazgo** (ahí habría
   bastado `grep -rn "Son:" docs/`). Antes de asignar un `Bxx`/`Cxx` nuevo: grepear 2-3 términos
   del hallazgo sobre `docs/` **y mirar las dos familias de numeración**, porque un hallazgo "B"
   puede estar registrado como "C" y al revés.

   **Y el grep por tema también encuentra el caso inverso: un hallazgo escrito pero nunca
   numerado** (05/11/2026, al asignar **B112**). El grep del número dio B111 y el del tema
   —`constancia de devolución`, `sin confirmación`— encontró la observación en
   `recorrido/04_formalizacion.md:1067`, del **25/10/2026**: estaba registrada al pasar, dentro
   del relato de una pasada, **sin número y sin fila en `FASE2_PLAN.md`**, así que llevaba 11 días
   invisible para cualquier planificación. No era un duplicado: era lo contrario. Cuando el grep
   por tema devuelve algo, **leerlo antes de decidir** — puede ser un duplicado (no asignar número
   nuevo) o un hallazgo huérfano (asignarlo, y citar de dónde salió y desde cuándo).

## Control obligatorio de 7 preguntas (una por peligro de `docs/auditoria/MAPA_DE_RIESGOS.md`)

Responder las 7 antes de mostrar el diff. **Cualquier "sí" exige la verificación extra que
`MAPA_DE_RIESGOS.md` detalla para ese peligro**, antes de aplicar:

1. ¿Este cambio toca algo que se calcula, se usa o se muestra en **más de un lugar**?
2. ¿Toca una **pieza compartida** por varias pantallas (ej. `pie.jsp`, `menu.jsp`,
   `cabecera.jsp`, `csrf.jsp`) **o algo que `pruebas/humo_http.sh` ejercita**?
3. ¿Toca un **cálculo de dinero** (interés, IVA, mora, redondeo, plan de pagos, TIR/CFT)?
4. ¿Puede afectar un **documento ya emitido** (recibo/factura/constancia/contrato ya
   generados para una operación real)?
5. ¿Toca el **esquema de la base** o algo protegido por un trigger/`UNIQUE`/`CHECK` (ver
   `bd/01_esquema.sql`/`02_indices.sql`)?
6. ¿Toca una **parte todavía no recorrida en pantalla** (tramo 7, informes, administración —
   ver "Cobertura del recorrido" en `00_LEEME.md`)?
7. ¿Puede introducir una **ruta absoluta de esta computadora, un recurso de internet, o algo
   que no viaje dentro del `.war`**?

## Al aplicar

8. **Mostrar el diff propuesto y esperar el OK de Joel** antes de escribir nada — archivo por
   archivo si el cambio toca varios.
9. **Aplicar solo en los archivos que Joel aprobó** — ni uno más, aunque de paso se note otra
   mejora posible (eso se anota como un ítem nuevo, no se mezcla en el mismo cambio).
10. **Correr `mvn test`** (`source ./entorno.sh && mvn test`, en Bash) — nunca
    `mvn clean package` (eso es siempre de Joel). Si falla, no seguir: corregir o avisar.
    **`mvn test` también verifica las JSP**: `jetty-jspc-maven-plugin` corre en `process-classes`
    (antes de `test`, ver `pom.xml`), así que un scriptlet mal formado o un import faltante
    **rompe el build** en vez de aparecer recién en el navegador. Si se tocó una `.jsp`, decirlo
    en el resumen: el BUILD SUCCESS ya cubre su compilación, pero **no** su aspecto ni su
    comportamiento (eso es DOGMA 1, lo prueba Joel).
11. **Pedirle a Joel una captura de la prueba en pantalla** del resultado — un test en verde
    no reemplaza que él lo vea funcionar.
11b. **Si la tanda tocó Java Y una JSP que usa lo nuevo, la pantalla queda en 500 hasta el Clean
    and Build — y la rompe `mvn test`, no la edición.** Verificado el 05/11/2026 (B106): Tomcat
    **no** lee `src/main/webapp`, sirve **`target/sgcpc/`**; `mvn test` copia la JSP editada ahí
    (fase `process-resources`) pero **no** actualiza `target/sgcpc/WEB-INF/classes`. Medido ese
    día: la JSP quedó con la hora de `mvn test` (18:05) y el `.class` con la del último despliegue
    (16:58, sin la constante nueva), así que la JSP recompilada no resolvía el símbolo. En pantalla
    sale *"Ocurrió un error inesperado"* (la página de error del sistema), **sin** el detalle del
    compilador en el HTML. Qué hacer:
    - **Avisarle a Joel en el mismo mensaje del resumen**, no después: "la pantalla X está en 500
      hasta tu Clean and Build, no hay nada roto en el código".
    - **No sacar una captura del "después"** y presentarla como el resultado: es el 500. Se declara
      "no visto" y se dice qué falta (ver `sgcpc-diseno-ui`, DOGMA 1).
    - Para confirmar la causa sin leer logs:
      `strings target/sgcpc/WEB-INF/classes/.../X.class | grep <lo nuevo>` contra el mismo `.class`
      de `target/classes` — uno lo tiene y el otro no.
    - **CORRECCIÓN (16/11/2026): `mvn test` NO copia `src/main/webapp` a `target/sgcpc`.** Lo de
      arriba decía que sí, en la fase `process-resources`, y es falso: **no hay ningún plugin en el
      `pom.xml` que lo haga** (el único que mira `src/main/webapp` es `jetty-jspc-maven-plugin`, y
      escribe en `target/jsp-precompiladas`). Se verificó corriendo `mvn test` con una JSP editada y
      viendo que la copia de `target/sgcpc` seguía con la fecha del día anterior. **Lo que copia es
      el vigilante de archivos de NetBeans**, en segundo plano, a los pocos segundos de cada
      escritura — por eso parecía que lo hacía Maven.
    - **Y ese vigilante se saltea archivos cuando cambian varios en el mismo instante.** Medido el
      16/11/2026: de 8 JSP escritas juntas dejó una sin copiar, y de 5 escritas juntas dejó dos. O
      sea que **"ya está desplegado" no se supone nunca**: después de editar JSP o CSS se compara
      `src` contra `target/sgcpc` archivo por archivo
      (`grep -c <lo nuevo>` en los dos, más `stat -c '%y'`).
    - **Cómo destaparlo sin desplegar nada**: reescribir el archivo con los **mismos bytes**
      (`d = open(p,'rb').read(); open(p,'wb').write(d)`) — el vigilante lo toma como un cambio y lo
      copia, el contenido no se altera y **el árbol de git queda limpio**. `touch` NO alcanza: se
      probó y el vigilante lo ignora. Y de a **un archivo por vez**, que es cuando no se saltea
      ninguno.

## Pruebas automáticas contra la base (clases `Prueba*`)

Cuando el DOGMA 1 pide probar algo hoy y el circuito **no es alcanzable por pantalla** (defensa
en profundidad, una rama que necesita datos que no existen), se escribe una clase `Prueba*` en
`src/test/.../servicios/`. Modelo ya probado: `PruebaRN01AprobarFormalizar`,
`PruebaPanelTarjetasNuevas`, `PruebaEmisionFacturaPdf`.

- **No corren con `mvn test`**: el nombre no matchea `*Test`/`Test*` de Surefire. Se invocan a
  propósito con `mvn -Dtest=PruebaLoQueSea test`.
- **Guarda dura de DOGMA 2**, leída de **la conexión abierta** y no de un archivo que alguien
  pudo mover: `conexion.getMetaData().getURL()` tiene que terminar en `/sgcpc_db_pruebas` y
  `conexion.getCatalog()` tiene que ser exactamente eso; si no, `fail(...)` y no corre nada.
- **Una sola conexión, `autoCommit=false` y `rollback()` en un `finally`** — revierte también
  si una verificación falla. Al final, **abrir una conexión nueva** y confirmar contra la base
  ya comiteada que todo quedó como estaba.
- **Solo `UPDATE` sobre filas que ya existen**: ningún `INSERT` ni `DELETE`, para no tocar los
  13 triggers `BEFORE DELETE` (`REGLAS_DESARROLLO.md` §14) ni consumir numeración.
- **El límite, que se dice de frente en el javadoc de la clase**: un método público que abre
  **su propia conexión** (`FabricaConexionBD.obtenerConexion()`) **no se puede envolver** en la
  transacción de la prueba — un escenario armado sin commit le es invisible. En ese caso la
  prueba ejercita **el método de guarda que ese servicio llama** (mismo código que corre en
  producción, pero no la llamada completa), y se escribe así en el javadoc y en el informe. La
  llamada completa la prueba Joel en pantalla: **esto no reemplaza al DOGMA 1**.

## Probar algo que necesita una cuota vencida (o que venza hoy) — corrimiento de fechas

**No se simula el reloj: se envejece el préstamo por SQL** (camino B de D-81; Joel ya descartó
tocar `Reloj` y 5 DAO financieros). El método completo, con los números de la última vez, está en
`AVANCE.md` (30/10/2026, B81) — **ese es el dueño, acá va solo lo que no es obvio**:

- **Días múltiplos de 7**, para conservar el día de la semana, y se eligen para que quede
  **exactamente** lo que se quiere probar (una vencida, no dos): se calcula contra las fechas
  reales de las cuotas antes de decidir.
- **`planes_pago.fecha_generacion` se mueve siempre**: si el plan vigente es `ANTICIPO_PARCIAL`,
  `inicioPeriodoTeorico` (D-87) la usa como inicio del período de la cuota 1 y, sin moverla, el
  período queda negativo. D-81 decía que esa columna "no la usa ningún cálculo": era cierto solo
  para un plan `ORIGINAL`.
- **`psql -v ON_ERROR_STOP=1 -1`** y **cada condición en un `DO $$ … RAISE EXCEPTION … $$`** —
  ninguna verificación en un comentario "a revisar a ojo". Guarda de DOGMA 2 leída de
  `current_database()`, no de un archivo.
- **Antes de escribir la vuelta, verificar en el código qué escribe la aplicación** por ver ese
  estado, en vez de suponerlo. En este caso: solo `prestamos.estado_prestamo` → `EN_MORA`; **nada
  en `cuotas`** (los importes de mora los calcula el motor en el momento) y `estado_pago` no puede
  volverse "VENCIDA" (el `CHECK` solo admite PENDIENTE/PARCIAL/PAGADA). Por eso la vuelta **verifica
  y aborta** en vez de poner importes en cero: borrar lo que nadie escribió es peor que no volver.
- **La vuelta devuelve el estado a `ACTIVO`** (si queda `EN_MORA`, RN-18 le bloquea al cliente
  cualquier crédito nuevo) y se le pasa la cantidad de pagos que había al hacer la ida, para abortar
  si alguien cobró con las fechas corridas.
- **La auditoría no se deshace** (es de solo inserción, con trigger): si el recálculo diario dejó
  una fila, se anota en el registro de la prueba. Y ojo con B80: si el recálculo del día ya corrió,
  la bandeja puede seguir diciendo "Al día" hasta que se abra la ficha.
- Los dos scripts quedan **fuera del repo**, en `..\pruebas\<item>_corrimiento\`, como el resto de
  `..\pruebas\`.

## Un dato, un dueño (28/10/2026, decisión de Joel) — para TODO lo que se escriba de ahora en adelante

Cada dato se escribe **en un solo documento**; los demás ponen **un enlace y una frase**, nunca
una copia. Si para corregir un dato hay que editar más de un archivo, uno de los dos no era el
dueño.

| Dato | Dueño | Los demás |
|---|---|---|
| **Estado de un ítem** (abierto/resuelto/probado, prioridad, riesgo, decisión pendiente) | `FASE2_PLAN.md` | enlazan |
| **Numeración B/C** (último usado, huecos) | `FASE2_PLAN.md` § Huecos de numeración | — |
| **Cómo funciona hoy y por qué** (reglas, decisiones de diseño, dónde está el código) | `funcionamiento/RFxx.md` | citan el RF |
| **Camino del usuario y qué se vio en pantalla** | `recorrido/0x_*.md` | — |
| **Diario con fecha** (qué se hizo, cuándo, con qué commit) | `AVANCE.md` | — |
| **Lo que ve y hace el usuario final** | `manual_usuario/0x_*.md` | — |
| **Esquema y cambios de la base** | `base_de_datos/04_esquema_actual.md` + `03_bitacora_cambios.md` | — |
| **Dónde está cada cosa / qué manda** | skill `sgcpc-mapa` | índice, nunca contenido |
| **Requisitos** | `RF_ANTEPROYECTO.md` (firmado) | nadie lo reescribe |

**El recorrido es un log con fecha, no un tablero de estado.** Se escribe *"el DD/MM vi esto"* y,
si el hallazgo tiene estado, **un enlace a `FASE2_PLAN.md`** — nunca "el estado de esto es X".
Un registro con fecha no se reescribe después: se le agrega una línea nueva.

**Por qué**: cerrar C14 obligó a editar a mano **6 lugares** que decían lo mismo
(`FASE2_PLAN.md`, `00_LEEME.md` ×2, `01_llega_el_cliente.md`, `02_solicitud.md` ×2), y el manual
de usuario quedó **7 días mintiendo** ("N de 3", "complementario") porque la lista de documentos
obligatorios estaba copiada en 5 documentos. El código no tuvo ese problema: tiene una sola
lista (`CODIGOS_EXPEDIENTE_OBLIGATORIOS`), y por eso el cambio fue de una línea.

**También en el código** (B88, 29/10/2026). Si una regla de negocio o de pantalla está decidida
en dos lugares, el segundo va a mentir: "qué paso sigue y a dónde se lleva al usuario" estaba fijo
en cada servlet y en cada botón, y por eso una carga de documento mandaba a "registrar el buró"
con el buró ya registrado. La forma que quedó probada:

- **Un método puro con el dueño de la regla** (`ServicioClientes.calcularSiguientePaso`): recibe
  el estado ya leído, no abre conexión, y devuelve qué decir y a dónde ir. Se prueba **sin base**,
  un test por caso.
- **Un servicio aparte que junta el estado** (`ServicioPasoGuiado`) y un **DTO** que lleva el
  resultado y la traducción "destino → pantalla" (`PasoGuiado.rutaRelativa`/`anclaPestanaFicha`).
- **Las pantallas preguntan, no deciden**: la JSP consulta (`ofreceBotonEn`) y dibuja.
- **Si la acción del usuario cambia la respuesta, es un parámetro de la regla** (`AccionPrevia`),
  nunca un `if` en el servlet.
- **Cuidado con el ciclo de construcción**: `ServicioConsultaCrediticia` ya tiene un
  `new ServicioClientes()` como campo, así que agregarle a `ServicioClientes` la dependencia
  inversa haría que construir cualquiera de los dos se llamara a sí mismo sin fin
  (`StackOverflowError` al primer pedido). Antes de agregar un campo `Servicio*` a un servicio,
  revisar los campos del otro.
- **Al borrar una copia, revisar qué textos especiales tenía** (los avisos de "cédula renovada" y
  "comprobante de ingreso con compromiso abierto" vivían en la copia que se eliminó): se mueven al
  dueño con su propio test, no se pierden.
- **Una prueba no debe depender del orden de una lista** si ese orden es presentación: buscar el
  ítem por su código, no por `get(0)`. Un cambio de orden aprobado por Joel rompió 8 tests que
  miraban posiciones; el orden se verifica en **un** test dedicado.

**Alcance: solo lo nuevo.** Lo ya escrito **no se reorganiza** (decisión de Joel del 28/10/2026);
si al tocar un documento viejo se cruza una duplicación, se corrige **esa**, sin abrir una pasada
de limpieza.

## Un mensaje que promete una regla tiene que verificarla (C20, 29/10/2026)

El bug de C20 estuvo escrito a la vista durante semanas: el error de
`ServicioAprobacionCredito.cancelar` decía *"…aprobada **(todavía no formalizada)**"* y el código
comparaba **solo el estado**, nunca la columna `formalizada`. Una solicitud ya formalizada se
queda en `APROBADA` (no existe un estado "FORMALIZADA"), así que pasaba el filtro y quedaba
`CANCELADA` con su préstamo vivo.

De ahí, dos hábitos:

- **Al leer una validación, leer su mensaje como si fuera la especificación** y confirmar, línea
  por línea, que la condición dice lo mismo. Si el texto nombra una condición que no está en el
  `if`, eso **es** el hallazgo — no hace falta reproducirlo para saber que falta.
- **Desconfiar de "el estado ya lo dice"**: cuando una entidad tiene un estado y además banderas
  (`formalizada`, `anulada`, `revisada`), el estado **no** alcanza para decidir. Antes de escribir
  una condición sobre un estado, mirar las columnas booleanas de esa tabla
  (`base_de_datos/04_esquema_actual.md`) y preguntarse cuál de las dos cosas define de verdad la
  regla.
- **El gate real va en el servicio, y la pantalla solo evita ofrecer lo que el servicio va a
  rechazar.** Arreglar únicamente la JSP habría dejado el agujero abierto por URL.

## Un comentario que afirma cómo se comporta el framework es una suposición, no un hecho (B109, 05→10/11/2026)

Extensión de C20 a la capa de presentación, encontrada al probar B87. En el alta de cliente, el
botón **"Volver" está muerto**: es un `<a class="btn">` que vive dentro de un
`<fieldset disabled>`, y tanto Bootstrap como AdminLTE traen
`fieldset:disabled a.btn{pointer-events:none}`. El comentario del propio JSP
(`clientes/ficha.jsp:746`) dice exactamente lo contrario y **por eso el enlace está ahí**:
*"el `<a>` queda DENTRO del fieldset a propósito (un enlace nunca se deshabilita por fieldset
disabled, así que en el alta sin calificar se puede seguir saliendo igual)"*. Lo que quedó roto es
**la salida de la pantalla**, justo en el estado en que más se necesita.

- **Un comentario que justifica una decisión citando el comportamiento de una librería se lee como
  una afirmación a verificar**, igual que el mensaje de una validación (C20). La verificación es
  barata: `grep` del selector en el `.min.css` vendorizado, o `getComputedStyle` en pantalla.
- **"Se ve en la pantalla" no es "funciona".** El `<a>` se dibujaba perfecto; lo que no hacía era
  responder al clic. Para un control, lo que hay que mirar es `pointerEvents`, `disabled` y si
  **hay algo encima**, no si se ve.
- **Vale al revés también**: antes de escribir un comentario que diga "esto funciona así porque la
  librería hace X", confirmar X en la librería **vendorizada de este proyecto** (no en la
  documentación de la versión que uno recuerda).

**Cerrado el 10/11/2026** con la excepción `fieldset:disabled a.btn{pointer-events:auto}` en §2b de
`sgcpc-tema.css` (no se movió el marcado). Lo que agregó el arreglo:

- **El comentario equivocado casi nunca viene solo.** Al corregir el de la JSP apareció que el de
  **§2b** también afirmaba algo falso: decía que `fieldset:disabled .btn` iba aparte *"porque el
  botón puede no llevar el atributo propio"*, y en pantalla ese `<button>` **sí** matchea
  `:disabled` aunque herede el estado del fieldset (`el.matches(':disabled') === true`), así que
  `.btn:disabled` ya lo cubría. Un `<a>`, en cambio, **nunca** matchea `:disabled`. Al tocar una
  regla por un comentario falso, **releer los comentarios vecinos de esa misma regla**.
- **Devolverle la vida a un control es la mitad: también tiene que VERSE vivo** (pedido de Joel al
  aprobar el arreglo). Y el valor correcto no se elige a ojo: se **mide el mismo elemento fuera del
  contexto roto** —clonarlo con `cloneNode(true)`, colgarlo del `<body>`, leer `getComputedStyle` y
  sacarlo— y después se comprueba que el elemento real dé **exactamente** esos valores. Acá dio
  `#F8F9FA` / `#444444` / `#DDDDDD` en los dos lados: así se demuestra que recuperó su aspecto real
  y no uno inventado.
- **Una regla vendorizada se desactiva acotando la nuestra, no con `!important`.** Si los selectores
  empatan en especificidad, alcanza con el orden de carga (`cabecera.jsp` carga el tema último).
  Antes de escribir un `!important`, contar la especificidad de las dos.
- **Y el alcance se cuenta antes de prometerlo**: un `grep` de `<fieldset` sobre todo `src/main`
  (sin lo vendorizado) mostró que `clientes/ficha.jsp:424` es el **único** del sistema, así que
  ninguna otra pantalla tenía el defecto. "¿Dónde más pasa esto?" se contesta grepeando **el
  elemento que lo causa**, no el síntoma.

## Escribir una ruta de Windows con un script: `\b` es un backspace (10/11/2026)

Al cerrar B109 se escribió `..\pruebas\b86_capturas` dentro de un literal de Python y en el
documento quedó `..\pruebas` + **un carácter de control `\x08`** + `86_capturas`: invisible al leer,
pero el nombre de la carpeta ya no es el que dice. Buscándolo apareció **el mismo defecto,
preexistente, en la fila de B107** de `FASE2_PLAN.md`.

- Las rutas de `..\pruebas\...`, `..\evidencias\...` y cualquier otra con `\b`, `\t`, `\n`, `\f`,
  `\r` o `\x` van en un literal **crudo** (`r"..."`). **La barra duplicada NO siempre
  alcanza** (11/11/2026, cerrando la tanda 4 de la revisión final): el literal viajaba dentro de
  un heredoc del shell, que se comió una de las dos barras, así que Python recibió `\r` y
  `..\pruebas\revision_final\` quedó escrito `..\pruebasevision_final\`,
  con un **retorno de carro invisible** en el medio. Dos salidas, las dos baratas: armar la ruta
  con `chr(92)` (ninguna secuencia de escape posible) o **escribir el script con la herramienta
  de escritura en vez de con un heredoc**. Y la verificación de después es la misma de siempre,
  ampliada: buscar en el archivo editado **cualquier** carácter de control, no solo `\b`
  (`[i+1 for i, l in enumerate(open(p, encoding="utf-8")) if any(c in l for c in (chr(8), chr(13)))]`).
- **Y se verifica después de escribir**, que cuesta una línea:
  `[i+1 for i, l in enumerate(open(p, encoding="utf-8")) if chr(8) in l]` — vacío, o se corrige.
- Un documento no se da por escrito porque el script no haya fallado: **se vuelve a leer la línea
  editada**. Pasó **tres veces el mismo día** (B109 y B112) por escribir el literal con la barra
  duplicada en vez de usar `r"..."`: si el texto que se va a insertar lleva una ruta, el literal
  **nace** crudo, no se arregla después.

## Verificar en la base lo que una confirmación acaba de emitir (B112, 10/11/2026)

Probando el cartel nuevo, Joel confirmó sin querer y se emitió una constancia real (en
`sgcpc_db_pruebas`). Las cinco cosas que hay que mirar, y en qué orden, para decir "quedó
coherente" sin suponer nada:

1. **La numeración, completa y no solo la última**: listar todos los números de esa familia y ver
   que sean **correlativos sin huecos**, y que el contador (`numeradores.ultimo_valor` para esa
   clave y ese año) coincida con el último emitido.
2. **El documento**: tipo, versión, `estado_documento = VIGENTE` y el origen (`JASPER` para los
   que genera el sistema).
3. **La integridad del binario, recalculada**: que empiece en `%PDF-`, que
   `length(contenido) = tamano_bytes` y que `encode(sha256(contenido),'hex')` dé **exactamente**
   `archivos_binarios.hash_sha256`. Es la misma comprobación del protocolo de `..\evidencias\`, y
   cuesta una consulta.
4. **Lo que NO tenía que cambiar**: acá `estado_cierre_documental` y `fecha_cierre_documental`
   seguían como estaban, porque eso lo mueve otro paso. Un cambio de más es tan hallazgo como uno
   de menos.
5. **La auditoría — y ojo con por dónde se la busca**: la fila de un documento cuelga del
   **documento**, no de la entidad de negocio. Buscarla con `id_entidad = <id del préstamo>`
   devuelve **cero filas** y parece que no se auditó; está con
   `tipo_entidad = 'DOCUMENTO_EXPEDIENTE'` e `id_entidad = <id del documento>`. Antes de declarar
   que algo "no se audita", buscar también por el texto del detalle.

**Y antes de la prueba que emite, está la que no emite**: abrir el cartel y cancelar no escribe
nada (C30), así que el cartel se mira primero así — y se **comprueba en la base** que efectivamente
no quedó nada, que es lo que después permite atribuirle la emisión a la confirmación.

## La pantalla promete lo que el servidor va a hacer, o no promete nada (B122, 10/11/2026)

La ficha de una solicitud en BORRADOR mostraba **dos afectaciones del mismo cliente** —154,6 % en
el aviso en vivo, **2,6 %** en la tabla— y encima el cartel verde *"Cumple validaciones
preliminares"* con el botón activo, mientras `enviarAAprobacion` rechazaba el envío. El servidor
nunca dejó pasar nada: lo que fallaba era la promesa de la pantalla.

- **Si el servidor tiene un gate, el checklist de la pantalla lo tiene que tener.** Acá el gate de
  la capacidad de pago estaba en el envío (desde RF06, 10/10/2026) y **no** entre los 9 ítems del
  checklist, así que el cartel no podía verlo. Al revisar una pantalla que dice "cumple / no
  cumple", **listar los rechazos del método que ejecuta** y comprobar que cada uno tenga su ítem.
  Es C20 al revés: no un texto que promete una regla inexistente, sino una regla que el texto no
  mira.
- **Y los dos salen del MISMO método**, no de dos cuentas equivalentes: el cálculo se extrae
  (`calcularAfectacionVigente`) y lo llaman el rechazo, el ítem del checklist y la pantalla. Dos
  cuentas "iguales" se separan en el primer cambio.
- **Un snapshot es correcto para lo ya decidido y mentira para lo que todavía se decide.** La
  misma columna (`porcentaje_afectacion`) está bien en la ficha del gerente —es la base sobre la
  que se aprobó— y mal en el borrador del oficial, donde la decisión todavía no se tomó. Antes de
  mostrar un dato guardado, preguntarse si esa pantalla **decide** con él o solo **registra** lo
  que se decidió.
- **Mirar los vecinos del dato desactualizado.** La fila de arriba de esa misma tabla ya hacía lo
  correcto (`esBorrador ? ingreso de hoy : foto`): el criterio existía tres líneas más arriba y no
  se había aplicado al segundo número. Cuando aparezca un dato viejo en pantalla, revisar **todo
  el cuadro**, no solo la fila del reclamo.
- **Al mover un chequeo al dueño, borrar la copia que la pantalla tenía.** La JSP agregaba su
  propio faltante *"La cuota supera la capacidad de pago del cliente"*; con el ítem nuevo, el
  cartel lo habría listado **dos veces**. Agregar y borrar van en el mismo cambio (mismo criterio
  que B111 para mover una pieza de lugar).
- **Y hay un rastro barato para medir el alcance**: una consulta que compara el dato guardado
  contra el recalculado con los datos de hoy dijo que eran **10 de 40** borradores, y **0** de las
  pendientes. Un alcance medido vale más que un "pasa a veces".

## Un texto partido entre dos dueños concuerda mal justo en el caso raro

01/11/2026 (**B103**). La cláusula del contrato escribía `"a los "` fijo y el conversor devolvía
el día; para 20 de los 31 días el resultado era correcto, así que el defecto vivió meses. Solo
aparece el día 1 (*"a los primer día del mes de..."*), que en ese momento no había ocurrido nunca
en los datos de prueba.

- Cuando un texto se arma **concatenando** algo fijo con algo que varía, el pedazo fijo es una
  **suposición sobre todos los valores posibles** del que varía. Si esa suposición puede fallar
  (concordancia de género/número, un artículo, una preposición), el pedazo fijo tiene que mudarse
  al dueño del dato: pedir *la frase*, no *el dato* (`comoFechaConArticulo`, no
  `"a los " + comoDiaMesAnioEnLetras`).
- El JUnit va sobre **la frase completa armada**, no sobre las dos mitades: ninguna de las dos
  estaba mal por separado. Si el armador es de visibilidad de paquete, el test vive en **su**
  paquete (mover el test, nunca ampliar la visibilidad del método para que un test lo alcance).
- Para elegir los casos del JUnit, buscar **el valor que nunca apareció en los datos**: acá el
  día 1 y los terminados en uno. "Funciona con los datos que tengo" no es cobertura.

**Segunda vez, con otra forma de falla (B117, 07/11/2026)**: el aviso *"Lo recibido (…) no alcanza
para **1 cuota** (…). **Elija menos cuotas** o use «Por monto»."* El pedazo fijo no era
**agramatical** —como en B103— sino un **consejo imposible de seguir**: menos de 1 no existe. Mismo
defecto de forma, otra cara.

- Al revisar un texto armado por concatenación, no alcanza con preguntarse si **suena bien** para
  todos los valores: también si lo que **afirma o aconseja** sigue siendo cierto. Un imperativo
  ("elija menos", "pruebe con otra", "cargue el anterior") es una afirmación sobre lo que el
  usuario **puede** hacer, y en el valor del borde suele dejar de ser posible.
- **El arreglo suele no necesitar un parámetro nuevo**: la información del borde ya estaba en la
  firma (acá, el `plural` que el llamador pasaba para el sustantivo). Antes de agregar un argumento,
  mirar si alguno de los que ya están distingue el caso.
- **Y cuidado con el test que compara dos variantes de un mismo texto**: el de B115 cruzaba las dos
  frases **completas** (una con `replace` sobre la otra) y eso funcionaba mientras difirieran en un
  solo fragmento. Al aparecer un **segundo eje de variación**, ese test deja de controlar en
  silencio — hay que pasar a comparar **por partes** (acá, título y detalle por separado, con el
  mismo corte que hace `pie.jsp`). Cuando un ítem le agrega una variante a un texto que ya tenía un
  test de equivalencia, **ese test entra en el cambio**.

## Un control que compara contra lo que devuelve un DAO: verificar primero qué trae ese DAO

01/11/2026 (**B42**). El control del archivo repetido comparaba el hash del archivo que se sube
contra `documento.getArchivoBinario().getHashSha256()` del documento vigente que devuelven
`DocumentoExpedienteDAO.obtenerVigentePorPersonaYTipo`/`...PorPrestamoYTipo`. **Esos dos no hacen
JOIN con `archivos_binarios`**: traen `id_archivo` y nada más, así que `getArchivoBinario()` venía
en **null**, el chequeo se salía por el `if (… == null) return;` y **no bloqueaba nada**. Pasó
todos los tests (eran sobre las funciones puras del texto) y lo encontró Joel en pantalla en dos
minutos, reemplazando dos veces el mismo `ejemplo.pdf`: versión 2 y versión 3, las tres con el
mismo hash.

- Antes de escribir una comparación contra un objeto que vino de un DAO, **leer el SQL de ese
  DAO** y confirmar que la columna esté en el `SELECT` y que el `mapear…` la cargue. Un modelo con
  getters no promete que estén poblados: el mismo modelo se llena distinto según la consulta
  (`mapear` vs. `mapearConDetalle`).
- Un `if (algo == null) return;` dentro de un control **convierte el dato faltante en "no hay
  problema"**: el control desaparece en silencio. Si el null no debería poder pasar, que falle
  ruidosamente o que el dato se pida con una consulta que lo garantice (acá, una consulta propia
  con JOIN, `listarVigentesConMismoHash`).
- Un control de **bloqueo** no queda probado por los JUnit de sus textos. Lo que hay que ejercitar
  es *"intenté la acción prohibida y no se guardó nada"* — con base (clase `Prueba*`) o en
  pantalla. DOGMA 1: "verificado por código" no alcanza.

## El humo es una pieza compartida: entra en la pregunta 2 del control

03/11/2026. La corrida de `pruebas/humo_http.sh` posterior a las tandas 2 y 3 dio **10 de 1015
fallas**, y **dos las causó B42**: el humo "reemplaza" la autorización del buró **subiendo el
mismo PDF de prueba con otro nombre**, y desde B42 eso se rechaza. Ningún defecto del sistema —
el escenario del humo era irreal— pero el control de 7 preguntas había dado "no toca pieza
compartida", y el humo **es** una pieza compartida.

- **`$ARCHIVO_PRUEBA` se sube decenas de veces**, en varios tipos de documento y varias personas:
  cualquier regla nueva sobre **archivos, hashes, duplicados o versiones** lo cruza. Hoy existe
  además `$ARCHIVO_PRUEBA_B`, con otro contenido, justo para los reemplazos de verdad.
- **Antes de dar por cerrado un ítem que cambia una regla de carga, de texto visible o de
  numeración, grepear el humo** (`grep -n "<lo que cambió>" pruebas/humo_http.sh`). Es más
  barato que descubrirlo en una corrida de 1015 verificaciones.
- **Las otras 8 fallas fueron textos viejos del propio humo**, de cambios ya probados en pantalla
  semanas antes (B85 renombró "Cierre documental" → "Devolución del pagaré"; B36 le dio
  singular/plural a la tarjeta del panel; B90 puso la autorización como fila 1 y cambió cuál es
  el "primer faltante"). **El humo no se corría desde el 18/10**: una red de seguridad que no se
  corre se desactualiza en silencio y, cuando hace falta, no sirve. Correrlo **al cerrar cada
  tanda**, no solo antes de una pasada grande.
- **Dos corridas del mismo día van con sufijo** (`humo_2026-11-16b.txt`): el 16/11/2026 la
  corrida final —la posterior a B160/B240/B241/B158— se guardó con el **mismo nombre** que la de
  la mañana y la **sobreescribió**. Las dos habían dado 1295/1295 y `exit 0`, así que no se
  perdió ninguna medición, pero el archivo es la **evidencia** de que una corrida se hizo con el
  código de ese momento: si la segunda hubiera fallado, el verde anterior ya no estaría para
  comparar.
- **Una precondición que no se cumple no es una falla funcional.** El bloque E9 no pudo hacer
  vencer una cuota (feriado activo), siguió igual y reportó 2 fallas de reglas de negocio que en
  realidad nunca se ejercitaron. Si el escenario no se puede armar, el script tiene que decirlo
  con una fila `PRECONDICIÓN ...` y saltar los casos que dependen de ella.
- **El texto de un aviso se lee siguiendo el camino DESDE EL CONTROLADOR, no del primer método
  que parece armarlo.** Arreglando esas mismas verificaciones se falló **dos veces seguidas**:
  primero se supuso el origen por el nombre del método, después por el más parecido
  (`textoFaltantesExpediente`), cuando el flash de la edición lo arma el **paso guiado**
  (`ControladorClientesServlet` → `ServicioPasoGuiado` → `ServicioClientes.calcularSiguientePaso`),
  que ofrece la autorización **antes** que cualquier otro faltante. Cuesta un minuto abrir el
  controlador y seguir la llamada; cada suposición costó una corrida entera del humo.
- **Y un corrimiento de fechas tiene que mantener el escenario limpio**: con
  `credito.dias_gracia_mora = 0`, hacer vencer una cuota **ayer** en vez de **hoy** le agrega
  moratorio y punitorio, el cobro de `total_programado` pasa a ser parcial, pide fecha de
  promesa y lo rechaza el servidor. El único corrimiento sano es el que deja la cuota venciendo
  **hoy** — verificar el parámetro antes de proponer "correlo un día más".
- **Al eliminar marcado, grepear el humo por los nombres GENÉRICOS del framework, no solo por las
  clases propias** (B111, 05/11/2026). Se buscó `navbar-usuario`, `sidebar-tarjeta`,
  `cambiar-contrasena` y `/salir`: todo limpio. Lo que sí dependía del bloque eliminado era un
  `grep -A1 'dropdown-toggle'` con el que el **diagnóstico** del arqueo E8 saca quién estaba
  logueado — `dropdown-toggle` es de Bootstrap, no una clase del proyecto, y por eso ningún grep
  "por lo nuestro" lo encontraba. Antes de borrar un bloque de HTML, grepear el humo por **cada
  clase y atributo que ese bloque lleva puestos**, incluidas las de Bootstrap/AdminLTE
  (`dropdown-toggle`, `nav-link`, `card-header`, `data-widget`): ahí es donde se engancha un
  scraping escrito para otra cosa.
- **Renombrar un literal que el humo verifica AUSENTE no rompe nada: lo deja mirando un fantasma**
  (B43, 05/11/2026). `verificar_texto_ausente "… ya no pide '¿Aprobar igualmente?'"` (línea 2082)
  comprueba que el refuerzo de riesgo alto **no** aparezca en una solicitud sin riesgo. Al
  reescribir ese cartel, cambiarle la redacción habría dejado la verificación **siempre en verde**
  —el texto nuevo nunca coincidiría con el viejo— sin una sola falla que avisara que ya no
  controla nada. Por eso el literal se **conservó** como título del cartel. Regla: antes de
  reescribir un texto, grepearlo en el humo y mirar **de qué lado** está — una comprobación de
  presencia se rompe ruidosamente (y se actualiza), una de **ausencia** se rompe en silencio.
- **Un `registrar` de un POST que espera 302 no distingue un cobro exitoso de uno rechazado**
  (B113, 07/11/2026). El humo tenía un cobro *"para N+1"* que corría **antes** del `UPDATE` que
  atrasa las fechas, así que el préstamo no tenía nada vencido y `confirmarCobro` lo rechazaba por
  D-144 — pero el controlador **redirige igual**, así que la fila daba 302 y pasaba en verde. Sin
  cobro no había factura, y la verificación que dependía de esa factura (la correlatividad fiscal
  de D-89) se salteaba. Dos hábitos: cuando una fila del humo espera **302**, agregarle una
  comprobación de **efecto** (un `verificar_sql` del pago, de la factura o del estado) si de ese
  POST depende algo más abajo; y al leer un bloque del humo, **mirar el orden contra los `UPDATE`
  de fechas** — el escenario se arma varias líneas después de donde uno supone.
- **Una verificación cuyo `else` solo hace `echo` desaparece del conteo.** El arreglo que quedó
  probado: la comparación va en una **función** (así se pueden ejercitar las dos ramas con `awk` +
  `source`, sin una corrida completa de 1019) y la rama sin datos **llama a `registrar`** con una
  fila `PRECONDICIÓN no dada`. Y el control nuevo se verifica **contra una violación** antes de
  darlo por bueno: con el valor equivocado tiene que salir **FALLO**.
- **Un scraping del humo puede estar en un camino que casi nunca corre.** Ese `grep` vive dentro
  de un `if [ "$STATUS" != "200" ]` y **nunca llama a `registrar`**, así que romperlo no habría
  hecho fallar ninguna de las 1015 verificaciones: habría impreso `usuario=[]` el día que la
  falla intermitente volviera a aparecer, que es justo cuando el dato hace falta. Que una
  dependencia no pueda poner el humo en rojo **no** la convierte en inofensiva.

## Un hallazgo de seguridad se mide contra el producto, no contra el manual (C19, 09/11/2026)

La fila decía *"`GET /documentos/{id}` sin control de pertenencia ni auditoría"*, y la mitad del
ítem se cayó al mirar el código: **el sistema no tiene la noción de "mis clientes"** —ni oficial
asignado en `personas` ni filtro por oficial en el listado—, así que cambiar el id en la URL **no
da acceso a nada que el usuario no pueda abrir navegando la ficha**. No era escalación de
privilegios, era enumeración del mismo acceso.

- **Antes de proponer un control de acceso, preguntarse si la frontera que promete existe en el
  producto.** Implementar "pertenencia" habría inventado una regla que el anteproyecto no pide y
  que cambiaría el listado de clientes entero, no esa ruta (regla de Joel del 18/10/2026). La
  pregunta se contesta con dos greps: ¿hay una columna de dueño en la tabla?, ¿el listado filtra
  por el usuario?
- **Y la otra mitad del hallazgo puede ser la de verdad**: lo que faltaba era el **registro de la
  visualización**. Son datos personales, y el sistema ya auditaba cosas menos sensibles. Un ítem
  con dos partes se parte: una se descarta con argumento, la otra se hace.
- **La fila que exige "prueba en pantalla antes de cambiar" suele tener razón**, y el valor está
  en lo que la prueba descarta. Acá el plan de prueba (cajera bloqueada / oficial abre el de otro
  cliente / el informe no registra nada) servía para decidir **si había que restringir**, no para
  confirmar un arreglo ya escrito.

## Un registro de auditoría lo lee alguien que no estuvo ahí (C19, 09/11/2026)

El detalle decía *"Abrió …(versión 1) de **persona 3**."* y Joel lo rechazó: el gerente que abre
la auditoría meses después no sabe quién es "persona 3".

- **En un detalle de auditoría, los ids internos no sirven**: va el nombre y la C.I., el N.º de
  operación, el código de la solicitud — lo que esa persona ve en pantalla. Si para eso hay que
  leer otras tablas, se leen: es una operación por apertura, no un bucle.
- **El armado va en el servicio dueño de la entidad, no en el controlador.** Mientras solo copiaba
  campos del propio objeto podía vivir en el servlet; apenas hubo que resolver un nombre, el dueño
  pasó a ser el servicio que tiene los DAO. **Si el texto se mueve de paquete, el test se mueve con
  él** — nunca se amplía la visibilidad para que el test viejo alcance.
- **Para esas lecturas, usar los DAO y no los servicios**: un campo `Servicio*` dentro de otro
  servicio puede cerrar un ciclo de construcción (B88). Y el formato de cada dato se le pide a su
  dueño (acá `FormateadorTexto.comoCedulaConPuntos`: en la base la cédula se guarda normalizada).
- **Si el registro referenciado no existe, cae al id** en vez de tirar una excepción: un detalle
  incompleto es mejor que impedir ver el documento por un problema del log.

## El ORDEN de dos controles es una regla de negocio (B114, 05/11/2026)

La pantalla ofrecía el redondeo de D-141 (*"Cancela la deuda de 540.316 Gs., redondeo a favor del
cliente: 16 Gs."*) y el servidor lo rechazaba: *"El pago no cubre lo exigible"*. Ninguna de las dos
condiciones estaba mal escrita — estaban en el **orden** equivocado. `confirmarCobro` decidía
`esParcial = montoRecibido < exigible` y **después** calculaba el redondeo, pero solo
`esParcial ? null : …`. Circular: el monto redondeado está por definición 1..49 Gs. por debajo del
exacto, así que se clasificaba como pago parcial y el redondeo, que existe justamente para
permitirlo, nunca llegaba a correr.

- **Si un control A decide y un control B solo corre cuando A dijo "no", y el dato que B produce
  cambiaría la respuesta de A, la dependencia es circular.** Al leer dos controles encadenados,
  preguntarse *"¿el segundo sabe algo que el primero necesitaba?"* — no alcanza con verificar que
  cada condición, por separado, diga lo que promete.
- **Si hay un método hermano que hace lo mismo, compararlos línea por línea antes de proponer
  nada.** `confirmarCancelacionTotal` ya lo hacía bien (calcula el redondeo primero y después
  pregunta `if (!aplicaRedondeo && recibido < exacto)`), así que el arreglo fue **copiar la forma
  del hermano**, no inventar una. Y su comentario decía *"mismo criterio que confirmarCobro"*:
  **falso**. Es C20 aplicado a la paridad entre hermanos — un comentario que afirma que dos
  métodos hacen lo mismo se verifica contra el otro método, no se cree.
- **Preguntarse para qué valor del dato el control no controla nada.** Acá el control es **vacío
  cuando lo exigible es 0** —una cuota que vence hoy, D-144/B98— y ese era justo el único caso de
  cobro normal con monto redondeado que se había probado en pantalla (30/10/2026). Por eso el
  defecto vivió 35 días. Al revisar un control, buscar el valor que lo vuelve trivial y probar
  **ahí**.
- **Una variable booleana que decide cinco cosas se arregla en su definición, no en el primer
  uso.** `esParcial` decidía la fecha de promesa, el redondeo, el `TipoPago`, la
  `GestionCobranza` y el texto de auditoría. Tocar solo el mensaje de error habría dejado el pago
  tipado `PARCIAL`, el redondeo sin correr y una promesa de cobranza por los 16 Gs. que la empresa
  iba a perdonar — corregir el síntoma, prohibido por §13.
- **Si la regla vive dentro de un método que abre su propia conexión, se extrae a un método puro**
  (acá `esCobroParcial(montoRecibido, totalExigible, aplicaRedondeo)`), que es la única forma de
  cubrirla con JUnit sin base. Y el JUnit incluye **el borde**: 540.299, un guaraní por debajo del
  redondeado, tiene que volver a ser parcial. Sin ese caso, el test no demuestra que la ventana
  nueva sea de 49 Gs. y no más ancha.
- **El humo puede estar cubriendo el camino OPUESTO al de la pantalla.** 1015/1015 en verde con el
  cobro real bloqueado: el humo siempre posteaba `data-monto-pagar-cuota` (el **exacto**) y nunca
  `data-monto-pagar-cuota-redondeado`, que es con el que la ficha autocompleta *"Recibido"*. Regla:
  si la pantalla **autocompleta** un campo con un valor derivado, grepear el humo por **ese**
  `data-` y, si no lo usa, ahí hay un camino sin cubrir. No alcanza con que el humo ejercite el
  endpoint: tiene que mandar **lo que manda la pantalla**.

## Antes de unificar dos textos divergentes, averiguar si la diferencia era deliberada (B115, 05/11/2026)

El aviso *"lo recibido no alcanza para N cuotas"* estaba escrito en el servidor y en el navegador, y
las dos redacciones habían quedado distintas: el JS nombraba la cantidad (*"para 3 cuotas"*), el
servidor no (*"para la cantidad de cuotas elegida"*). Parecía la duplicación de siempre. **No lo
era del todo**: el javadoc de Java explicaba que *a propósito* no la nombra, porque *"esa cantidad
solo existe en el navegador"* — y al verificarlo resultó cierto: el formulario de confirmación manda
`montoSugerido` y `modoAnticipo`, **nunca la cantidad de cuotas**.

- **Una divergencia entre dos copias puede ser una decisión, no un descuido.** Antes de unificar,
  leer el javadoc/comentario de cada copia y buscar si una de las dos **no puede** decir lo que dice
  la otra. Si la hay, el ítem no es "elegir una redacción": es "una redacción con un parámetro".
- **La salida es "una redacción, dos arranques"** (el patrón que B91 ya había dejado): **un solo
  armador**, dos entradas, y un **JUnit que cruza las dos** y falla si difieren en algo más que ese
  fragmento. Así la unificación no obliga a que una de las dos mienta.
- **Y cuidado con "lo unifico agregando el dato que falta"**: acá habría sido un campo oculto con la
  cantidad, que el servidor **no puede cruzar contra el monto**; manipulado, el mensaje diría una
  cantidad falsa — peor que una genérica. Antes de traer un dato del formulario para completar un
  texto, preguntarse si el servidor puede **verificarlo**; si no puede, el texto genérico es la
  opción correcta y se dice por qué.
- **La decisión de qué queda es de Joel, y se le lleva con el límite ya medido**, no después de
  implementar media cosa: acá eligió *"la pantalla nombra las cuotas; el respaldo del servidor queda
  genérico, sin campo oculto"*.

## Dos préstamos con los mismos montos no son sospechosos: el humo crea un gemelo por corrida

05/11/2026 (al verificar B114). `/cobranza/13` y `/cobranza/24` mostraban **exactamente** los
mismos tres montos (224.411 / 315.905 / 848.276) y hacía falta descartar que fuera un dato
cruzado. No lo era: los dos son del humo —cliente *"Cliente Prueba Humo E7 D95-Promesas"*, cédulas
distintas, `monto_aprobado` 900.000, `plazo_meses` 3, **la misma `fecha_desembolso`**— con planes
idénticos (313.962 / 313.370 / 312.766) y los **mismos dos pagos parciales de 50.000**. Hay **18**
préstamos con ese plan: uno por corrida del bloque D-95.

- Antes de sospechar de un número repetido, comparar **el plan y los pagos**, no el total de la
  pantalla: con el mismo monto, plazo y fecha de desembolso, el motor da el mismo plan, y con los
  mismos pagos da el mismo exigible. Coincidir es lo correcto.
- Y sirve al revés: **para probar algo que consume el escenario, buscar un gemelo del humo en vez
  de armar un préstamo nuevo** (fue lo que permitió probar el caso parcial en `/cobranza/24` sin
  tocar el que ya se había cobrado).

## Un aviso que no bloquea: mismo dueño que el control, y se prueba primero el caso SIN aviso

03/11/2026 (**C30**). Con una reversión esperando autorización, los tres caminos de cobro seguían
confirmando sin decir nada, y recién al autorizar el gerente se enteraba de que ya no podía
(`autorizarReversion` revalida "es la última operación"). Joel eligió **avisar, no bloquear**:
frenar el cobro dejaría al cliente que vino con la plata esperando una decisión ajena, y sería
una regla que el anteproyecto no pide.

- **El aviso sale del MISMO método que después rechaza**, nunca de una consulta paralela que
  "parezca" equivalente: acá `hayOperacionPosterior` llama al mismo `esLaUltimaOperacion` que usa
  la autorización. Si el aviso y el control divergen, el aviso miente — y es peor que no tenerlo.
- **Antes de implementar, verificar el alcance real del control**: el análisis hablaba de
  "adelantar o cancelar", pero `esLaUltimaOperacion` mira el último pago **CONFIRMADO sin
  distinguir tipo**, así que un **cobro normal de cuota** rompe la autorización igual. El aviso
  tenía que ir en los **tres** caminos, no en dos.
- **La primera prueba en pantalla es el caso SIN aviso.** Un cartel que aparece siempre no avisa
  nada, y es el error más fácil de no ver (el ojo confirma lo que espera encontrar). En C30 se
  abrió el cartel con el préstamo limpio **antes** de solicitar la reversión, se confirmó que el
  recuadro no estaba, y recién después se armó el escenario.
- **Un aviso se puede probar sin gastar numeración**: abrir el cartel y cancelar no emite nada.
  Conviene diseñar la prueba para quedarse ahí y dejar lo caro (confirmar la operación) cubierto
  por JUnit, anotando qué quedó sin ver en pantalla.
- **El texto va en un `data-` y el JavaScript solo lo muestra** (mismo patrón que B79/B92), y se
  escapa antes de meterlo en el HTML del cartel aunque venga del servidor.

## Para armar un plan de prueba, preguntarle al motor en vez de calcular a mano

01/11/2026. Para decidir qué cobro probar hoy hacía falta el monto exacto de una cancelación
total. En vez de replicar la fórmula (ya hubo una predicción errada así: el mínimo del adelanto),
se escribió una clase `Prueba*Temporal` de **solo lectura** que llama a la misma vista previa que
pinta la ficha (`calcularPreviewCancelacionTotal`, que por definición no persiste nada), con la
guarda dura de DOGMA 2. Devolvió 844.004 y 2.554.040 — los dos coincidieron con la pantalla.
Cuesta cinco minutos y evita decirle a Joel un número que después no sale.

**Antes de proponer una operación para la prueba del día, verificar en el código que esa rama
hace lo que se quiere mostrar**: el adelanto de capital **no** aplica redondeo
(`ServicioCobranza:1553`, *"Nunca hay redondeo acá"*) — proponerlo para ver un recibo con
redondeo habría quemado una prueba entera.

## Un comentario XML no puede contener `--`, en NINGUN archivo XML (12/11/2026)

Ya estaba escrito en `sgcpc-reportes-jasper`, pero acotado a los `.jrxml`. **Vale para todo XML
del proyecto**, y el 11 y el 12 de noviembre de 2026 costó tres veces el mismo día:

| Archivo | Qué pasó | Quién lo agarró |
|---|---|---|
| `pom.xml` (11/11) | Maven ni arrancó: el POM dejó de parsearse | `mvn`, al instante |
| `web.xml` (12/11) | `jetty-jspc-maven-plugin`: *"Failing pre-compilation due to errors in web.xml"* | `mvn test-compile` |
| `android/app/src/main/AndroidManifest.xml` (12/11) | `processReleaseMainManifest`: *"Error parsing AndroidManifest.xml"* | **solo el empaquetado del APK, en GitHub Actions, 5 minutos después** |

El tercero es el que enseña: **cuanto más lejos está el validador, más caro sale el guion doble.**
El manifiesto no lo mira ni `flutter analyze` ni `flutter test` ni `flutter build web` — solo
`flutter build apk`, que en este proyecto corre en el CI. Un `--` ahí cuesta una ejecución entera.

**La regla, entonces**: en un comentario XML va coma, punto, o la palabra "o sea". Nunca el guion
doble que se usa en los javadoc de este proyecto. Y conviene pasar el control después de editar
cualquier XML, que es más rápido que esperar al validador que esté más lejos:

```bash
python -c "
import io, re, sys, xml.dom.minidom as m
for ruta in sys.argv[1:]:
    try:
        m.parse(ruta)
    except Exception as e:
        print('NO PARSEA', ruta, '->', e); continue
    malos = [c for c in re.findall(r'<!--(.*?)-->', io.open(ruta, encoding='utf-8').read(), re.S) if '--' in c]
    print(('OK   ' if not malos else 'TIENE -- EN UN COMENTARIO '), ruta)
" src/main/webapp/WEB-INF/web.xml pom.xml
```

## Si una regla hay que copiarla, la prueba lee el original (13/11/2026)

Pasa cuando una regla de negocio vive dentro de una JSP y la necesita algo que no puede llamar a
una JSP (un servlet, la API de la app móvil). Las dos salidas son:

1. **Mover la regla a una clase** y que la JSP la use. Es lo correcto… y toca el sistema web.
2. **Copiarla.** Es lo que se hace cuando tocar el web no está sobre la mesa (etapa de
   consolidación, víspera de entrega, trabajo de otra materia sobre la misma base).

Si se copia, **la deuda no es la copia: es que nadie se entere cuando una de las dos cambie.** Un
comentario diciendo "ojo, está copiado de X" no frena nada (ver la lección del `--` en los
comentarios XML: el comentario ya estaba escrito y el error se repitió tres veces).

**Lo que sí frena**: que el JUnit **lea el archivo original** y compruebe contra él. En tres
niveles, porque cada uno agarra un error distinto:

| Nivel | Qué compara | Qué error agarra |
|---|---|---|
| La tabla | el resultado de la copia, caso por caso, escrito a mano leyendo el original | que la copia esté mal hecha desde el día uno |
| El original, leído del archivo | que las condiciones del original sigan siendo las que se copiaron (las expresiones exactas, y **cuántas** hay de cada tipo) | **que alguien cambie el original y no la copia** |
| Los textos, leídos del archivo | que cada pedazo de texto que la copia produce exista, carácter por carácter, en el original | que se corrija una palabra en una pantalla y no en la otra |

El del medio es el que justifica todo el ejercicio, y el que no se le ocurre a nadie escribir:
los otros dos prueban la copia, ese prueba **la relación entre las dos**.

Ejemplo vivo: `TarjetasPanelApi` / `TarjetasPanelApiTest` (ETAPA 5 de la app móvil) contra
`src/main/webapp/WEB-INF/vistas/panel.jsp`. La copia son los `if (esOficialPanel)`,
`if (esGerentePanel)`… que deciden qué tarjeta del Panel ve cada perfil. El nivel 2 comprueba,
entre otras cosas, que `esGerentePanel` siga incluyendo al ADMINISTRADOR (D7): si alguien lo saca
de la JSP, el panel del web cambia, el de la app no, y **el test lo dice ese mismo día**.

Un detalle operativo: el texto de una JSP viene **partido** por sus expresiones
(`Cobro<%= n == 1 ? "" : "s" %> de hoy`), así que el nivel 3 no puede comparar la frase entera —
compara los pedazos fijos, que es donde está el riesgo real.

## Una verificación que usa un pedido para comparar tiene que registrar ESE pedido (12/11/2026)

El humo del 12/11/2026 dio **1 fallo de 1153**, y decía esto:

```
La cuota del simulador es IDENTICA a la del Cotizador web (483.199 Gs.)   presente   ausente   FALLO
```

Leído así, eso significa **una fórmula duplicada en el sistema**: la API calculando distinto que
la pantalla. Es de las cosas más graves que puede decir el humo.

No era eso. Las dos cuotas eran idénticas (verificado a mano: `483.199 Gs.` por las dos vías). Lo
que había pasado es que la verificación hacía el `POST /cotizador` **reusando `$COOKIE_JAR` y
`$TOKEN_CSRF` tomados al principio del script**, y cuando la corrida llega a ese bloque —5.000
líneas después— esa sesión ya fue cerrada por las pasadas intermedias (los `GET /salir` del
oficial, del administrador y del gerente). El POST daba **403** y devolvía una página sin ningún
monto; `verificar_texto` leía "la cuota no está".

**Las dos reglas que salen de acá:**

1. **Sesión propia para cada comparación.** Si una verificación necesita entrar a la web, abre su
   jar y lee su `tokenCsrf` de la pantalla, como ya hacen `COOKIE_JAR_GERENTE_E6`,
   `COOKIE_JAR_OFICIAL_HIST` y las demás. Y la cierra al terminar. Un `$COOKIE_JAR` global en un
   script de 5.000 líneas no es una sesión: es la última sesión que alguien dejó ahí.

2. **El pedido que se usa para comparar se registra.** Sin el `registrar` del código HTTP, *"el
   pedido falló"* y *"los dos valores difieren"* **se ven exactamente iguales en la tabla** — y
   significan cosas opuestas: la primera es un problema del humo, la segunda sería del sistema. Un
   caso que no distingue esas dos cosas no mide lo que dice medir, y encima manda a buscar el
   error al lado equivocado.

La segunda es la que más cuesta: el fallo era legible, reproducible y estaba en verde el producto
—y aun así hubo que salir a reproducir a mano las dos vías para saber de qué lado estaba el
problema. Con el `registrar` puesto, la tabla lo habría dicho sola.

**Al escribir una verificación nueva**, entonces: preguntarse qué pasa si falla **el andamio**
(el login, el CSRF, la precondición) en vez de lo que se quiere probar. Si las dos cosas se
reportan igual, falta un `registrar`.

## Al paginar un listado, el riesgo no son solo los `verificar_texto`: están los SCRAPES que alimentan un POST (B197, 15/11/2026)

Antes de aplicar B197 se barrió el humo y se avisó: *"se rompe una sola verificación, la 4466"*.
La corrida dio **4 de 1295 fallas**, todas del bloque 4. **La predicción estaba incompleta**, y el
motivo vale más que las cuatro líneas: se auditaron los `verificar_texto` que piden el listado
pelado, y **no los `extraer_*` que leen marcado de una fila para armar el POST siguiente**.

Lo que pasó: `extraer_token_gestion "$CUERPO_TMP" "$idPrestamo"` lee el `<form>` de **esa fila**
para sacar su `tokenGestion` (TokenUnaVez, uno por fila desde la tanda 2 del 14/10). El guion
refresca a propósito la página completa sin filtro justo antes (y lo dice en un comentario). Con el
listado paginado, ese préstamo cayó en la **página 3** —medido: puesto 47 de 71—, así que su
formulario **no estaba en el HTML**, el token salió **vacío** y el POST fue rechazado. De ahí las
cuatro: el flash que no apareció, el `SELECT` del delta en 0, la prueba del `PAGO_PARCIAL` que usaba
el mismo token vacío, y el estado de cuenta sin la gestión que nunca se guardó.

- **Antes de paginar un listado, grepear el humo por los `extraer_`/`grep -o` que leen de ese
  listado**, no solo por los `verificar_texto`. La pregunta es: *¿alguien saca de esta página un
  token, un id o un `action` de una fila concreta?* Si sí, esa fila **tiene que estar en la página
  que el guion pide**.
- **Y el barrido se hace por tipo de scrape, mirando de qué PÁGINA lee cada uno.** Acá eran tres:
  `extraer_token_gestion` (lee `/mora`, **el único afectado**), `extraer_identificador_confirmacion`
  (10 usos, todos sobre la **ficha** de cobranza, que no pagina) y el `grep -o` de
  `/caja/revisar-diferencia/[0-9]*` (lee el bloque de diferencias, que **no** se paginó). Tres
  minutos de grep y el diagnóstico queda cerrado.
- **El síntoma engaña, y mucho**: un token vacío se comporta **igual que uno inventado**, así que la
  prueba del *"token inválido"* de la línea siguiente **pasó en verde** por el mismo motivo por el
  que la del token válido falló. Dos verificaciones con sentidos opuestos, la misma causa — si uno
  lee solo la que falla, va a buscar el defecto al servidor.
- **Lo que confirma que el producto está bien** (y conviene verificarlo antes de tocar nada): una
  persona ve la página 1 y toca *"Registrar"* en una fila **dibujada**, cuyo token existe; las filas
  de la página 3 se dibujan con su token cuando se va a esa página. El escenario del guion era el
  irreal, como en B42 y B113.
- **Y no quedó dato malo**: el `SELECT` del delta da 0 porque no se escribió nada. Las 24 filas con
  el texto de la prueba son de corridas anteriores — el humo no limpia, y para eso el guion captura
  el conteo **antes** (la regla del `count(*)` acotado).

**Y el arreglo tuvo DOS sitios, no uno** (la corrida siguiente bajó de 4 fallas a 1): el segundo POST
del bloque usa un token **re-scrapeado** unas líneas más abajo, y lo lee de la página que dejó el
POST anterior — porque **`postear_y_verificar_en` reescribe `$CUERPO_TMP`** con la página que sigue
al POST (su tercer argumento). Arreglar solo el pedido que uno encontró deja la mitad.

- **Al arreglar un scrape, seguir la cadena completa de `$CUERPO_TMP`**: cada `verificar`,
  `verificar_texto` con archivo propio y cada `postear_y_verificar_en` puede reescribirlo. La
  pregunta es, para **cada** `extraer_*` del bloque: *¿cuál fue el último pedido que escribió ese
  archivo?* — y se contesta leyendo hacia arriba, no suponiendo.
- **El síntoma del segundo sitio era especialmente engañoso**: la verificación esperaba el mensaje de
  una **regla de negocio** (*"Ese tipo de gestión se genera solo al cobrar."*) y el POST se estaba
  rechazando **por el token**, así que el texto nunca aparecía. Un rechazo por el andamio disfrazado
  de rechazo por la regla — la misma trampa que la del `registrar` que falta, con otra cara.

## Una verificación puede MOVER la fila que va a buscar (B197, 15/11/2026)

Al paginar `/mora` había que arreglar dos verificaciones del humo que piden el listado pelado. La
primera fue el caso conocido (la fila cae en la página 3, se pide un orden que la traiga a la 1).
**La segunda no era lo mismo, y el motivo vale más que el arreglo**: esa verificación mira la fila
del préstamo al que la app **acaba de registrarle una gestión con promesa**, y esa promesa es de
**fecha futura**, así que el préstamo deja de tener *"promesa vencida"* y **sale del grupo que el
listado pone primero**. O sea: **la operación que se está verificando cambia la posición de lo que
se busca**.

- **Antes de arreglar una verificación con "pedí este orden", preguntarse si el POST de arriba
  cambia alguno de los campos por los que ese orden ordena.** Si los toca, el orden no es
  determinista para esa fila por más que se fije la clave.
- **La salida robusta es entrar con un FILTRO que deje un solo resultado** (el N.º de operación, el
  código): no depende de ningún orden ni de ninguna página, y sigue verificando lo mismo. El id ya
  suele estar en una variable del bloque; el código legible sale con un `SELECT` de solo lectura,
  como ya hace el resto del guion.
- Es la contracara de la regla de las verificaciones por ausencia: ahí el peligro era una cadena
  que **nunca** podía estar; acá es una fila que **se mueve sola** entre el POST y el GET.

## Un servlet con dos rutas que escriben: el permiso es de la RUTA, no del servlet (12/11/2026)

Cuando un servlet tiene **una sola** acción que escribe, lo natural es pedir el permiso al entrar:

```java
protected void doPost(...) {
    SesionApi sesion = exigirSesionConPermiso(request, response, false);  // el permiso de "este" servlet
    ...
}
```

Eso funciona mientras haya una sola ruta. **En cuanto se agrega la segunda, miente**: la ruta nueva
hereda el permiso de la vieja. Y miente para los dos lados — abre la acción nueva a quien tenía la
vieja, y la cierra a quien debería poder usarla.

Pasó al agregar el alta de solicitud a `ClientesApiServlet` (ETAPA 7 de la app móvil), que ya tenía
`POST /clientes/{id}/documentos`. El expediente y el alta de solicitud son **dos pantallas
distintas del sistema web, con dos permisos distintos** (`CARGAR_DOCUMENTOS_CONSULTA_BURO` y
`GESTIONAR_SOLICITUD`): la cajera tiene el primero en NINGUNO, el gerente el segundo en NINGUNO.
Compartir uno habría dado la respuesta equivocada a los dos perfiles.

**La forma**: una función **pura** que traduce la ruta al permiso, llamada **antes** de validar la
sesión.

```java
static Permiso permisoDelPost(String[] segmentos) { ... null si la ruta no existe ... }
```

Tres cosas que se ganan, y las tres se prueban sin base ni contenedor:

1. **Cada ruta con el permiso de su pantalla.** Un JUnit lo afirma ruta por ruta, y otro afirma que
   los dos permisos **no son el mismo** — esa es la afirmación que se rompe si alguien unifica.
2. **Una ruta desconocida no hereda nada** (null → 404). Sin eso, un `else` final la dejaría con el
   permiso de la última rama.
3. **El 404 se contesta igual con token.** Si la ruta inexistente contestara 404 sin pedir sesión,
   cualquiera podría averiguar qué rutas tiene la API sin haber entrado nunca.

Y la consecuencia por perfil se prueba contra la matriz **real** (`ServicioAutorizacion` +
`ServletApiBase.autorizaAcceso`, las dos en memoria): quién puede y quién no, escrito como se lo
diría a una persona. Si mañana cambia la matriz, lo dice un test y no un 403 en la cara del
oficial.

## Al abrir una acción en un canal nuevo, probar la SEPARACIÓN de funciones (12/11/2026)

Cuando una acción que ya existe en el sistema web se abre en otro canal —una API, una aplicación
móvil—, lo que hay que probar no es solo que **funcione**. Es que **siga sin funcionar para quien
no le corresponde**.

Suena obvio escrito así. No lo es en el momento: el que escribe el endpoint está pensando en el
perfil que lo va a usar, y el que prueba en pantalla entra con ese mismo perfil. El que no
corresponde no aparece en ninguna de las dos cabezas.

El caso concreto (ETAPA 7 y 8 de la app móvil): el oficial **carga** la solicitud y la **envía**;
el gerente la **aprueba**. Son dos permisos distintos a propósito —`GESTIONAR_SOLICITUD` y
`APROBAR_RECHAZAR_SOLICITUD`—, y el sistema web los mantiene separados sin esfuerzo porque son dos
pantallas con dos entradas en `FiltroAutorizacion`. En una API es más fácil que se junten: un
servlet, dos rutas, un `exigirSesionConPermiso` al entrar... y de golpe el mismo oficial, con el
mismo teléfono, carga y aprueba su propia solicitud.

**Lo que se prueba, entonces, para cada acción nueva:**

| Prueba | Qué agarra |
|---|---|
| El perfil que SÍ puede, puede | que la ruta exista y el permiso sea el correcto |
| **El perfil que NO puede, recibe 403** | que no se haya heredado el permiso de otra ruta |
| **Y ese 403 no escribió nada** (conteo antes/después en la base) | un 403 que ya hizo el INSERT es peor que no tener permisos |
| Los dos permisos **no son el mismo** | que nadie los unifique "para simplificar" |

Las dos del medio son las que faltan siempre. La tercera es barata —dos `SELECT count(*)` alrededor
del pedido— y es la única que distingue "te lo negué" de "te lo negué después de hacerlo".

Y conviene escribir la afirmación como la diría una persona, no como una tabla de permisos: *"el
oficial no decide su propia solicitud"*. Si esa prueba se pone verde al revés algún día, lo que se
lee en el informe de fallos es la frase que importa, no un `assertFalse` sobre un enum.

**El otro lado de lo mismo**: una acción que el servicio sabe hacer y que el canal nuevo **no**
expone (cancelar una solicitud, en este caso) también merece su prueba. Sin ella, "no está" y "nos
olvidamos" se parecen demasiado, y el que venga después la agrega sin preguntar.

## Una verificación por AUSENCIA no se escribe sola (12/11/2026)

Las dos mitades de `verificar_texto` / `verificar_texto_ausente` no son simétricas, y la
diferencia es toda:

- Una verificación **por presencia** mal escrita **falla**. Se nota el mismo día.
- Una verificación **por ausencia** mal escrita **pasa**. No se nota nunca.

El caso: en la bandeja del gerente (ETAPA 8 de la app móvil) se buscaba la solicitud por la
**cédula cruda** del cliente. Pero el servidor la manda **formateada con puntos** —
`construirFilaBandeja` usa `FormateadorTexto.comoCedulaConPuntos` — así que esa cadena no estaba
nunca en el JSON. La verificación por presencia falló y se arregló en el momento. Pero al lado
había esta otra:

```bash
verificar_texto_ausente "la ya aprobada salio de la bandeja" "$CEDULA_ETAPA7"
```

y **esa pasó**. No porque la solicitud hubiera salido de la bandeja: porque la cédula cruda no
podía estar ahí ni antes ni después. Estaba midiendo nada, y en verde.

**La regla, entonces**: cada verificación por ausencia necesita **su par por presencia, con la
misma cadena exacta, en un estado donde esa cadena SÍ tenga que estar.** En este caso:

```bash
# antes de aprobar: tiene que estar
verificar_texto      "esta en la bandeja"            "\"codigo\":\"$CODIGO\""
# despues de aprobar: no tiene que estar
verificar_texto_ausente "salio de la bandeja"        "\"codigo\":\"$CODIGO\""
```

El par es lo que le da sentido al segundo: si la cadena está mal escrita, **el primero avisa**.

Y si no se puede escribir ese par —si no hay ningún estado en el que la cadena deba aparecer—,
entonces la verificación por ausencia no prueba lo que dice: hay que buscar otra cadena. Sirve de
guía elegir algo que el servidor mande **tal cual**, como un código (`SOL-2026-0312`), en vez de
un dato que pase por un formateador (una cédula, un monto, una fecha): esos viajan distintos de
como están en la base, y es fácil asumir la forma equivocada.

Van dos veces el mismo día con esto: también se asumió que el mensaje de RN-02 nombraba el código
de la solicitud abierta, y no lo nombra. **Leer el valor antes de afirmarlo** es más rápido que la
corrida del humo que lo descubre.

## Un `count(*)` del humo se acota a lo que escribió ESTA corrida (12/11/2026)

El humo **no limpia lo que crea**. Entonces cualquier verificación del tipo

```bash
verificar_sql "la gestion quedo guardada" \
    "SELECT count(*) FROM gestiones_cobranza WHERE id_prestamo = $ID AND resultado LIKE '%...%'" "1"
```

pasa la **primera** vez y falla la **segunda**: la corrida anterior dejó una fila idéntica —mismo
préstamo, mismo texto, misma promesa— y el conteo devuelve 2. Pasó el 12/11/2026 con cuatro
verificaciones de una sola vez, en la segunda corrida del día.

**La forma**: capturar el último id ANTES del pedido y acotar el conteo con él.

```bash
ID_ANTES="$(PGPASSWORD="$BD_CONTRASENA" psql -w -h localhost -U "$BD_USUARIO" -d "$BD_NOMBRE" -tAc \
    "SELECT coalesce(max(id_gestion_cobranza), 0) FROM gestiones_cobranza" 2>/dev/null | tr -d '[:space:]')"
# ... el pedido ...
verificar_sql "..." "SELECT count(*) FROM gestiones_cobranza WHERE id_gestion_cobranza > ${ID_ANTES:-0} AND ..." "1"
```

**Lo que NO sirve para acotar:**

- **Una ventana de tiempo** (`fecha_hora > now() - interval '10 minutes'`). Ya costó una vez con la
  auditoría: contaba también los accesos web de la misma pasada, y daba 5 y 91 en vez de 1.
- **El texto del dato** (`resultado LIKE '%hable con la hermana%'`). Es justo lo que la corrida
  anterior también escribió. Y encima se lee como si acotara.

El id es lo único que distingue "la fila que acabo de escribir" de "una fila igual de ayer".

**Por qué se repitió**: la lección existía, pero **solo como comentario en el caso que la
descubrió** (`AUDITORIA_ANTES_CAJERA` en el bloque de la cajera). Al escribir un bloque nuevo se
copió el patrón para la auditoría —que estaba a la vista— y no para las cuatro verificaciones de
la tabla propia. Un comentario local enseña a quien lee esa línea; una regla acá enseña a quien
escribe la próxima.

**Al escribir cualquier `verificar_sql` con `count(*)` y esperado `"1"`**, entonces, la pregunta
es: *¿qué pasa si el humo corre dos veces hoy?* Si la respuesta es "da 2", falta la marca del id.

## Separar un `.war` no se lleva los filtros: mirar qué lee cada servicio DEL HILO (12/11/2026)

Al mover la API a su propio `.war`, los servicios viajan (vienen en el jar de clases) pero **el
contexto en el que esos servicios confiaban se queda del otro lado**.

`ServicioAuditoria.registrar` **no recibe** la IP ni el navegador por parámetro: los lee de
`ContextoPeticion`, un `ThreadLocal` que en el sistema web llena `FiltroAutenticacion`. Con la API
en otro `.war`, ese filtro ya no la ve: **todas** las filas de auditoría de la aplicación móvil
habrían quedado con `ip = null` y `navegador = null`, con la API funcionando perfecto y sin que
nada avise. En un sistema cuya defensa es la trazabilidad, eso es grave.

**Cómo se encuentra, antes de que pase:** por cada servicio que el `.war` nuevo va a usar,
preguntar *¿qué lee que NO le llegue por parámetro?* — `ThreadLocal`, `System.getProperty`,
`ServletContext`, la sesión HTTP. **Lo que no llega por parámetro no se muda solo.** En esta
separación aparecieron tres, y los tres había que reponer en el `web.xml` nuevo:

| Lo que leía | Quién lo ponía | Qué se rompía sin él |
|---|---|---|
| `ContextoPeticion` (ThreadLocal) | `FiltroAutenticacion` | auditoría sin IP ni navegador |
| codificación del pedido | `FiltroCodificacionUtf8` | `request.getParameter` lee el POST como ISO-8859-1: "hablé con la señora" se guarda roto |
| `java.awt.headless` | `EscuchaContextoAplicacion` | JasperReports sin pantalla (hoy la API no emite PDF, pero el `.war` tiene que poder arrancar solo) |

**Y la prueba se escribe en dos mitades, porque agarran cosas distintas.** El JUnit del filtro
prueba su contrato (pone los dos valores, los limpia en un `finally`) y **seguiría en verde si
alguien saca el filtro del `web.xml`** — justo el caso en que la auditoría se vacía. La mitad que
muerde ahí es la del humo: después de un POST real de la API, un `SELECT` que exige
`direccion_ip IS NOT NULL AND navegador LIKE 'curl/%'` sobre la fila de **esta** corrida. Las dos,
siempre; una sola da falsa tranquilidad.

## Un `.war` que contesta JSON necesita sus propias `error-page`, y una por código (12/11/2026)

La raíz del `.war` nuevo (`/sgcpc-api/`) respondía `{"error":"Ocurrió un error inesperado. Ya
quedó registrado para revisarlo."}`. El código HTTP era **404** y era correcto —la raíz no tiene
nada mapeado, a propósito—, pero el texto mandaba a buscar un problema del servidor que no
existía. Lo vio **Joel abriendo la URL en el navegador**, no una prueba.

Y el **405** (un POST a una ruta que solo atiende GET, que resuelve `HttpServlet`) ni llegaba a la
página de error: caía en la pantalla técnica de Tomcat, que publica la versión del servidor en un
`<h3>` — exactamente lo que prohíbe Doc. 10 §10.18.

- **Un `.war` sin JSP no hereda las `error-page` del otro**: las del sistema web apuntan a
  `error-500.jsp` y ahí no hay JSP ninguna. Dos archivos JSON estáticos en la raíz del `.war`
  (`/error-404.json`, `/error-405.json`, …) alcanzan: el `forward` conserva el código HTTP, el
  detalle real queda solo en el log y el cliente recibe JSON incluso cuando la excepción no la
  atrapó el servlet.
- **Cada código con su texto.** Un 404 dice que la dirección no existe; "error inesperado" es del
  500. Mismo mensaje para todo = el primer día que la app apunte a una ruta vieja, se busca el
  problema en el lugar equivocado.
- **Probar los códigos que genera el CONTENEDOR, no solo los que tira el código**: la raíz sin
  `welcome-file`, una ruta inventada, un método no permitido. Son 30 segundos:

```bash
for c in "GET /sgcpc-api/" "GET /sgcpc-api/loquesea" "POST /sgcpc-api/api/panel"; do
  curl -s -o /dev/null -w "$c -> %{http_code} %{content_type}\n" -X "${c%% *}" "http://localhost:8081${c#* }"
done
```

## Un jar publicado desde `target/classes` lleva lo que el `.war` excluye (12/11/2026)

El `<packagingExcludes>` de `maven-war-plugin` saca `bd.properties.real` y `bd.properties.pruebas`
del `.war` (causa raíz del hallazgo de seguridad del 26/10), **pero no de `target/classes`**. Un
jar de clases armado desde ahí —el `<attachClasses>` del war-plugin lo hace así— se los lleva
adentro, a `WEB-INF/lib`, que es **justo donde el control de la entrega no miraba**: el mismo
hallazgo otra vez, en un lugar nuevo. Por eso el jar se publica con `maven-jar-plugin` y
`<exclude>configuracion/**</exclude>` (la carpeta entera, no los tres archivos conocidos).

- **El control de secretos se hace sobre el artefacto que se entrega Y dentro de cada jar de
  `WEB-INF/lib`**, no solo en `WEB-INF/classes`.
- **Y se prueba con un jar sucio armado a mano** (`configuracion/bd.properties.real` adentro):
  tiene que abortar, nombrar el jar y no copiar nada a la carpeta de entrega. Un control de
  ausencia que nunca vio el caso malo no es un control — ver "Una verificación por AUSENCIA no se
  escribe sola".

## `mvn package` sin `clean` deja lo que se BORRÓ (12/11/2026)

Después de `git rm` del paquete `api` del SGCPC, `target/sgcpc.war` y la carpeta explotada
`target/sgcpc` **seguían teniendo las 20 clases viejas**: el war-plugin agrega y reemplaza en
`webappDirectory`, no borra lo que desapareció. Y Tomcat sirve justamente `target/sgcpc` (así lo
deja NetBeans: `conf/Catalina/localhost/sgcpc.xml` con `docBase` apuntando ahí).

Entonces: **cuando el cambio BORRA archivos, el build que vale es Clean and Build**, y hay que
pedírselo a Joel diciendo por qué — "Build" solo deja los restos adentro y desplegados.

## `git add -A -- <carpeta>` barre lo que está sin versionar a propósito (12/11/2026)

Al comitear la separación, `git add -A -- src` metió al commit las **14 clases `*Temporal`** de
`src/test`, que por convención del proyecto nunca se comitean (están en disco, sin versionar, y el
propio `preparar_war_entrega.sh` lo explica).

- **Agregar por lista de archivos**, no por carpeta, cuando en esa carpeta vive algo que no se
  versiona; o `git add -u` (solo los ya seguidos) y los nuevos uno por uno.
- **Leer el `git show --name-only --format="" HEAD` del commit recién hecho**, antes de seguir. El
  conteo de archivos tiene que coincidir con lo que uno cree que cambió.
- Si ya está hecho y no se pushó: `git rm --cached` de esos archivos y `commit --amend`. Quedan en
  disco, sin versionar, como estaban.

## Lo que se abre ENCIMA lleva flecha; el menú es de los destinos (13/11/2026)

En la aplicación móvil, la ficha del cliente mostraba **☰ en vez de ←**. No era que faltara poner
la flecha: **un `Scaffold` con `drawer:` dibuja el botón del menú EN LUGAR del de volver**, aunque
la pantalla esté apilada encima de otra. Y como el menú de entonces solo tenía "servidor" y
"cerrar sesión", desde la ficha la única salida visible era *salir*. Lo encontró Joel probando en
la tablet, no una prueba.

- **La regla**: el menú lateral es de los **destinos** (lo que en Material son las pantallas de
  primer nivel); **lo que se abre encima no lleva menú, lleva flecha**. Al escribir una pantalla
  nueva, la pregunta es "¿esta se abre encima de otra?" — si sí, nada de `drawer:`.
- **Un menú que no lleva a ninguna parte es peor que no tenerlo.** Si el menú es lo único a mano
  en una pantalla sin salida, tiene que ofrecer a dónde ir, no solo cómo irse.
- **Dos listas de destinos (el menú y la barra) se desincronizan solas.** Son la misma función y
  los mismos permisos, pasados desde el armazón con un `InheritedWidget`; no dos listas que
  "casualmente" coinciden hoy.
- **El "atrás" de Android se diseña, no se hereda.** En una pantalla raíz cierra la aplicación sin
  avisar: si la sesión vive en memoria, eso es perderla. `PopScope(canPop: false)` + preguntar, y
  desde un destino interno, volver al destino inicial.

**Para probarlo** (Flutter, sin librerías de mock):

- la flecha existe solo si la pantalla está **apilada**: hay que abrirla desde otra (un botón que
  hace `Navigator.push`), porque una pantalla bombeada sola es la primera de la pila y nunca
  dibuja flecha — la prueba pasaría por el motivo equivocado;
- `find.byType(BackButton)` / `find.byType(DrawerButton)` dicen cuál de los dos quedó;
- `probador.binding.handlePopRoute()` es el "atrás" del sistema, el que dispara el `PopScope`;
- con un armazón hay **dos `Scaffold`** vivos a la vez: `probador.state<ScaffoldState>(...)` falla
  con "Too many elements"; se recorre `stateList` y se abre el que tenga `hasDrawer`;
- un `pumpWidget` nuevo **no vacía la pila del Navigator**: dos pantallas apiladas en la misma
  prueba se tapan entre sí. Una prueba por pantalla.

## Una prueba que no usa el valor POR DEFECTO no prueba el camino de todos los días (13/11/2026)

`preparar_war_entrega.sh` se probó el 12/11 de punta a punta y dio las 8 verificaciones en verde
en los dos `.war`... pasándole `SGCPC_CARPETA_ENTREGA` con una ruta **absoluta** al scratchpad,
para no tocar la entrega de verdad. Al correrlo en serio, con su valor por defecto
(`entrega_final`, **relativo**), murió en la verificación final:

```
herramientas/preparar_war_entrega.sh: line 97: cd: entrega_final: No such file or directory
```

La causa es una trampa de bash que vale anotar: en

```bash
( cd "$verifica" && unzip -o -q "$(absoluta "$destino")" ... )
```

la sustitución `$(absoluta "$destino")` se evalúa **adentro del subshell, después del `cd`**, así
que una ruta relativa se resuelve contra la carpeta temporal. Con una ruta absoluta funciona y el
defecto queda invisible. La forma correcta es calcularla **antes** del paréntesis.

Dos reglas:

- **Una variable que existe "solo para poder probar" tiene que usarse además en su valor normal
  al menos una vez.** Si la prueba solo recorre el camino alternativo, lo que queda probado es el
  camino que nadie usa.
- **Y el script tiene que correrse de verdad antes de decir que está listo** — DOGMA 1. Acá el
  costo fue barato (se vio en la primera corrida real, y el `.war` anterior se repuso a mano
  antes de repetirla), pero el `.war` de la entrega ya estaba copiado cuando el script abortó:
  un script que se interrumpe a la mitad deja la carpeta de salida en un estado intermedio.


## Un desglose no se arma sumando los getters de un DTO: hay que ver de dónde sale el total (B203, 15/11/2026)

El ítem parecía de pura presentación: *"la cancelación muestra el total y nada del desglose; poné
los sumandos como en el adelanto"*. Y `ResultadoLiquidacion` parecía traerlos todos servidos —
`getSaldoCapitalFecha`, `getInteresDevengado`, `getIvaInteresDevengado`, `getMoratorioPendiente`,
`getPunitorioPendiente` y sus IVA—, así que un desglose "obvio" habría sido imprimirlos en fila.

**No dan el total.** En `calcularCancelacionTotal` el monto sale de:

```
montoTotal = exigible.getTotalGeneral() + capital no vencido + interesDevengado + su IVA
```

y `getInteresDevengado()` trae **solo** el interés del período en curso más el de las cuotas con
fecha teórica pasada (D-88): **el interés de las cuotas vencidas viaja adentro del total del
exigible**, que no es un getter de la liquidación. Medido el 15/11/2026 en `CC-2026-00035`:
faltarían **12.190** de interés y **1.219** de IVA (es su cuota 2, vencida, con el interés
todavía sin pagar) — y el hueco crece con cada cuota vencida que tenga el préstamo. El desglose habría quedado por debajo del total que la pantalla cobra, en la
operación más sensible del sistema y justo donde el cliente lo está leyendo.

Lo que queda como hábito:

- **Antes de desglosar un total, leer la línea que lo calcula** y seguir cada sumando hasta su
  origen. Un getter con el nombre correcto puede estar midiendo un subconjunto (acá
  "interés devengado" no incluía el devengado de lo vencido, aunque los dos se cobren).
- **El dueño del desglose es el servicio, no la vista**: un método puro que devuelve los renglones
  en orden (rótulo + importe) y la vista solo formatea. Así la JSP no suma importes
  (`REGLAS_DESARROLLO.md` §5) y los rótulos no quedan repartidos entre el servicio y el HTML.
- **El JUnit que vale no es el de los rótulos: es el que comprueba que los sumandos DEN el total**,
  y corrido contra la salida **real del motor** (no contra números a mano), en los dos escenarios:
  sin cuotas vencidas y con cuotas vencidas, mora y punitorios. Un desglose que no suma el total
  miente.
- **Y una prueba que fija por qué el método recibe lo que recibe**: acá
  `testDesgloseCancelacion_SinElExigibleQuedariaCortoConVencidas` pasa `null` como exigible y
  verifica que la suma quede **por debajo** del total. Es la que impide que alguien
  "simplifique" el segundo parámetro dentro de seis meses.
- **Antes de sumar dos resultados del motor, verificar que midan lo mismo**: acá cerró exacto
  porque los dos salen del mismo `hoy` y los mismos `credito.dias_gracia_mora`, y el criterio de
  "vencida" (`estadoPago != PAGADA && fechaVencimiento.isBefore(fecha)`) es **idéntico** en
  `calcularDeudaExigible` y en `calcularCancelacionTotal` — `diasGracia` solo cambia los importes
  de mora, no quién entra. Si uno de los dos filtrara distinto, la suma no cerraría y el desglose
  mentiría sin que ningún test de rótulos lo note.


## Cuando un ítem agrega una CUENTA, las opciones se le llevan medidas, no descritas (B204, 15/11/2026)

De los ítems de la revisión de UX, B204 era el único que agregaba un cálculo nuevo, y Joel pidió
—con razón— *"traeme primero las opciones de cómo calcular el ahorro, antes de escribir código"*.
Había dos candidatas, y **descritas en palabras sonaban equivalentes**:

- **A**: lo que faltaba del plan menos el total a cancelar (la fórmula que ya usa el adelanto).
- **B**: el interés futuro no devengado, con su IVA.

**Medidas, no eran equivalentes.** Sobre un plan de 3.000.000 a 12 cuotas al 27%: sin cuotas
vencidas **dan el mismo número** (436.897 y 407.851, así que la elección parecía indiferente); con
**2** cuotas vencidas 261.362 contra 277.598; y con **5** vencidas **44.559 contra 135.647**. La
diferencia es **exactamente la mora con sus punitorios e IVA** (`A + mora = B` en los cuatro
casos): A le descuenta al cliente un **castigo** como si fuera menos ahorro, y con suficiente mora
lo arrastra a cero. Y el caso "con vencidas" no era el raro: **75 de 190 préstamos activos (39%)**
tenían cuotas vencidas ese día.

Cómo se hizo, y es el método a repetir:

- **Medir con el motor, no estimar.** Una clase `Generar…Temporal` (la convención de este
  proyecto para medir y tirar) que corre el motor real en 4 fechas e imprime cada opción. Cuesta
  diez minutos y convierte una discusión de criterio en una tabla.
- **Incluir el caso donde las opciones DIVERGEN, y contar cuántos préstamos caen ahí.** "Con
  cuotas vencidas da distinto" no decide nada; "39% de la cartera, y la diferencia es la mora" sí.
- **Buscar la identidad entre las dos opciones** (acá `A + mora = B`). Si se encuentra, la decisión
  deja de ser "qué fórmula" y pasa a ser una pregunta de negocio en una línea: *¿la mora le
  descuenta el ahorro al cliente?* — que es una pregunta que Joel puede contestar, a diferencia de
  dos fórmulas.
- **Decir dónde va a vivir el número antes de que lo pida**, porque condiciona el cambio: acá la
  ficha **no** tenía la lista de cuotas, así que cualquier lugar distinto del motor costaba una
  consulta extra por carga de pantalla. Y el parámetro nuevo del constructor se puso **último**,
  detrás de los dos `List`, para que un orden equivocado **no compile** (entre tres `BigDecimal`
  seguidos, un intercambio compila y miente).
- **La decisión se escribe como test**, no solo en el documento: `con mora, el ahorro tiene que ser
  mayor que la opción A, y la diferencia exactamente la mora`. Si alguien "simplifica" la fórmula
  a la opción A dentro de seis meses, eso se pone en rojo y dice por qué.
- **Y antes de cerrar, grepear si el número ya existía con otro nombre.** Acá apareció *"Interés
  futuro programado"* en el reporte de Cartera (`CuotaDAO.sumarInteresFuturoPendiente`): es
  `SUM(interes_programado)` sin restar lo pagado, sin IVA y sin descontar lo exigible hoy — otra
  pregunta (cuánto interés tiene la cartera por delante) y por lo tanto otro número. **Que sean
  distintos a propósito hay que escribirlo** en el javadoc del nuevo, o el próximo que los cruce va
  a "corregir" uno de los dos.


## Un parámetro que no es un campo del formulario no sobrevive al submit (B201, 15/11/2026)

B201 hizo viajar el `origen=cancelacion-anticipada` por los **cuatro** enlaces a una ficha, con un
solo sufijo que junta `q` y `origen`, y Joel lo probó y funcionaba. Al día siguiente, probando otra
tanda, encontró el agujero: **entrando por el menú y BUSCANDO** un préstamo, la ficha abría como
`/cobranza/8?q=CC-2026-00008` **sin `origen`** — el menú volvía a marcar *"Cobranza"* y el aviso
desaparecía. Desde la lista sin buscar sí funcionaba.

**Los cuatro enlaces estaban bien.** La causa estaba un paso antes: el formulario de búsqueda es un
`method="get"` a `/cobranza` que llevaba **solo el campo `q`**, así que al enviarlo la URL se
reescribía con los campos del formulario y nada más. El `origen` vivía únicamente en el query
string de la página, y un submit **no lo conserva**. De ahí se caían de una vez las tres cosas que
dependían de él: el título, el aviso de entrada y el sufijo de los enlaces (que lo lee de
`request.getParameter("origen")`).

Qué queda como hábito:

- **Al hacer que un parámetro de pantalla "viaje", listar por dónde sale la página, no solo los
  enlaces**: cada `<form method="get">` que apunte a la misma ruta **reescribe la URL** y se lleva
  puesto todo lo que no sea uno de sus campos. Lo mismo un `<a>` a la ruta pelada (acá, el
  *"Limpiar"*).
- **El arreglo es un `<input type="hidden">` en el formulario**, no un `if` más en cada enlace: así
  el parámetro vuelve a la URL y **todos** los lectores lo encuentran, incluido el que se escriba
  mañana.
- **Verificar que el hidden también sirva al camino AJAX**, que es el que de verdad corre: acá
  `listado-vivo.js` arma el pedido con `new URLSearchParams(new FormData(form))` —un `hidden`
  entra—, solo le ata listeners a `text`/`search`/`select`/`date` (así que no dispara búsquedas
  solo) y `limpiarFormulario` no lo vacía. Las tres cosas se leyeron en el script antes de dar el
  arreglo por bueno; si cualquiera fuera distinta, el campo oculto habría arreglado el submit y
  dejado roto el camino real.
- **Y acordarse del respaldo sin JavaScript.** El `href` del *"Limpiar"* apuntaba a la ruta pelada;
  con JS el click lo intercepta el script y no se nota, así que es un defecto que **solo aparece
  sin JavaScript** — el peor tipo para encontrar probando.
- **Una prueba en pantalla que pasa no cubre las otras entradas a la misma pantalla.** B201 se
  probó entrando por el menú y tocando una fila de la lista; el camino "buscar primero" es otra
  entrada y tenía otro resultado. Al pedir la prueba de algo que se conserva entre pantallas,
  nombrar **cada** forma de llegar (lista, búsqueda, paginación, volver) — no "entrá y probalo".


## Editar un `.jrxml` obliga a recompilar su `.jasper`, aunque el cambio sea un comentario (B205, 15/11/2026)

En B205 se corrigió un comentario de `informe_auditoria.jrxml` que había quedado viejo (decía que
el tope de 500 filas valía "en pantalla y CSV", y ya no). `mvn test` falló:
`ReportesCompiladosTest.todosLosJasperCorrespondenASuJrxmlActual` compara el **hash del `.jrxml`**
contra el guardado en `herramientas/reportes_jasper.sha256`, así que **cualquier** byte distinto lo
pone en rojo — incluido un comentario.

- **El arreglo es el que el script ya tiene escrito**: `herramientas/compilar_reportes.sh
  informe_auditoria`, con el **nombre del reporte**. Sin argumento recompila los 41 y deja los otros
  40 `.jasper` con bytes distintos a los commiteados (el compilador de Jasper no es reproducible bit
  a bit), que es ruido imposible de distinguir de un cambio real.
- **Y el `.jasper` puede salir idéntico**: acá cambió solo un comentario XML, que no se compila, así
  que el `.jasper` quedó igual y lo único que se movió fue su línea del manifiesto. Eso es correcto,
  no un error del script — pero conviene mirarlo con `git status` para saber qué se está comiteando.
- **No es excusa para dejar el comentario viejo.** Un comentario que describe un tope que ya no
  existe manda a buscar un comportamiento que no está; el costo de recompilar es un comando.

## Identificar un control por su CLASE agarra al vecino (B209, 15/11/2026)

B209 tenía que apagar los botones *"Informe PDF"* y *"Excel/CSV"* de veinte reportes cuando no hay
filas. Para encontrarlos se usó la clase `btn-outline-secondary`, descartando los que llevan
`data-buscar-en-tabla-limpiar` (el *"Limpiar"* de V15). La regla se verificó sobre las 42 líneas…
y aun así falló en uno: en `auditoria.jsp` el *"Limpiar"* del formulario de filtros es un `<a>`
**sin** ese atributo y con la **misma** clase, así que quedó apagado junto con los de exportar y el
ⓘ aterrizó dentro de la tarjeta de filtros.

- **La clase dice cómo se ve algo, no qué hace.** Para encontrar un control por lo que hace, el
  criterio es su **destino** (el `href`/`action`) o un atributo propio, no su apariencia.
- **Cuando el criterio es necesariamente la clase** (porque el marcado no da otra cosa), hay que
  **contar cuántos matchea por archivo y revisar los que se salen del promedio**: acá los veinte
  reportes daban 2 y auditoría dio **3**. Ese "3" era el defecto, visible sin abrir nada.
- **Un cambio masivo sobre N archivos necesita su propio control de coherencia**, aunque sea
  imprimir cuántas coincidencias tocó en cada uno. Sin ese conteo el error se descubre en pantalla,
  multiplicado por veinte.

## Apagar un `<a>` no es pintarlo gris (B209, 15/11/2026)

Bootstrap 4 pinta `.btn.disabled` en gris, pero un `<a href>` con esa clase **sigue siendo un
enlace**: se puede tabular hasta él y activarlo con Enter, y un lector de pantalla lo anuncia como
disponible. Para un botón que no debe funcionar hacen falta las tres cosas: la clase `disabled`,
`tabindex="-1"` y `aria-disabled="true"`. (En un `<button>` alcanza el atributo `disabled`, que no
existe para `<a>`.)

Y **el gris sin explicación obliga a adivinar**: al lado del botón apagado va el porqué en texto
(*"No hay filas para exportar con este filtro."*), con su dueño en el servicio.

## Un fragmento compartido que lee PARÁMETROS sirve donde el que lee atributos no (B207, 15/11/2026)

B207 tenía que avisar el rango de fechas invertido en trece reportes. El camino obvio era un
fragmento que leyera los atributos `desde`/`hasta` del request… y habría funcionado en **ocho**:
los otros cinco (Caja, Solicitudes, Aprobaciones, Desembolsos, Cobros) guardan el rango **dentro de
un DTO de filtro**, con otro nombre de atributo y otra forma.

Leer los **parámetros del pedido** resolvió los trece de una vez, y además es lo correcto por dos
razones: son iguales en todas las pantallas que tienen ese filtro, y son **exactamente lo que la
persona escribió** — que es de lo que habla el aviso. Resultado: **cero controladores tocados**, una
línea por reporte.

- **Antes de diseñar un fragmento compartido, verificar que el dato que va a leer exista igual en
  TODAS las pantallas donde se va a incluir.** Si hay dos formas de guardarlo, hay que elegir la
  capa donde el dato es uno solo — y el pedido HTTP suele ser esa capa.
- **Y el fragmento se dibuja en todas las cargas, también la primera sin parámetros**: su método
  tiene que devolver "nada que decir" ante null, vacío y basura, con un test por caso. Si no, la
  pantalla se ensucia (o falla) en la carga inicial, que es la que todos ven.

## Un tope de filas no se quita: se mueve al camino donde sirve (B205, 15/11/2026)

El ítem pedía *"sin tope de 500"* en Auditoría. Quitarlo de los tres caminos a la vez habría
cambiado un problema por otro: un **PDF de 18.381 filas** no lo quiere nadie y es el que puede
tumbar la memoria de Jasper. Lo que resolvió el problema real fue repartirlo según para qué sirve
cada camino:

| Camino | Qué quiere el que lo usa | Qué quedó |
|---|---|---|
| **Pantalla** | buscar y llegar a cualquier fila | **pagina** (20 por página, en SQL), sin tope |
| **CSV** | llevarse los datos completos | **sin tope** |
| **PDF** | un documento para leer o archivar | **tope**, y el aviso dice que el CSV trae todo |

- **Cuando un ítem pide quitar un límite, preguntarse qué problema resolvía ese límite.** Si el
  problema sigue existiendo en alguno de los caminos, el límite se queda **ahí** y el ítem se cierra
  diciéndolo — no se quita en silencio ni se deja en los tres "porque así estaba".
- **Un aviso de tope tiene que ofrecer una salida alcanzable.** El viejo decía *"acote el filtro"* y
  el filtro por defecto ya era **un solo día**: pedía algo imposible. Ahora nombra el camino que sí
  trae todo.
- **Paginar en Java (`recortarPagina`) o en SQL no es una preferencia de estilo**: con 190-292 filas
  el recorte en Java está perfecto (y es lo que usan las otras cinco pantallas); con 18.381 obliga a
  traerlas todas a memoria, que era justo lo que el tope evitaba. **El criterio es el volumen real,
  medido** — no el que se supone.
- **Y al paginar en SQL, el `ORDER BY` tiene que ser determinista.** `ORDER BY fecha_hora DESC` con
  registros del mismo instante deja que dos filas alternen entre páginas: se desempata por una
  columna única y estable (acá `id_auditoria`, de una tabla de solo inserción).

## Un informe de UX también puede equivocarse al contar, y se corrige al cerrar el ítem (B209, 15/11/2026)

El informe de Informes decía *"los botones de exportar no avisan qué va a pasar en 13 de los 20
reportes (solo 7 tienen el ⓘ)"*. El 7 salía de contar `fa-info-circle`… y esos 7 iconos son **otra
cosa**: el aviso de V15 de que la búsqueda dentro de la tabla no afecta la exportación. El ⓘ del
glosario (`data-glosario-titulo`) **no lo usaba ninguno de los veinte**.

- **Contar una clase CSS o un icono no es contar una funcionalidad.** Si el conteo va a entrar en un
  informe que decide el alcance de una tanda, hay que grepear **el mecanismo** (acá
  `data-glosario-titulo`), no su decoración.
- **El número equivocado se corrige en la fila del ítem al cerrarlo**, nombrando el conteo viejo y
  el nuevo. Un informe con fecha no se reescribe (es un registro), pero el ítem que sale de él sí
  tiene que llevar el número correcto, o la próxima lectura hereda el error.


## Una clase de Bootstrap que el tema NO redefine se ve distinta a todas sus hermanas (B206, 15/11/2026)

B206 cambió siete estados vacíos de `alert-success` a `alert-secondary`, y Joel vio el resultado
*"muy oscuro, parece un bg-secondary"*. Era exactamente eso: `sgcpc-tema.css` define el estilo suave
del sistema (fondo claro + franja izquierda de 4px) para `.alert-success`, `.alert-danger`,
`.alert-warning`, `.alert-info` y `.alert-riesgo-alto` — y **`.alert-secondary` quedó afuera**, así
que caía en la regla de **AdminLTE**, que pinta las alertas sólidas
(`color:#fff; background-color:#6c757d`) y le gana a la de Bootstrap por orden de carga.

- **Antes de usar una clase de Bootstrap que la pantalla todavía no usaba, buscarla en los tres
  CSS**: `bootstrap.min.css`, `adminlte.min.css` y `sgcpc-tema.css`. AdminLTE redefine buena parte
  de lo que uno cree conocer de Bootstrap, y el tema redefine parte de AdminLTE. La que manda es la
  última: una clase que el tema no nombra **no sigue el estilo del sistema**.
- **El arreglo va en el dueño de la paleta, no en las vistas.** Cambiar la clase en siete JSP habría
  tapado el síntoma y dejado las otras once igual de oscuras; agregar la regla al tema corrigió los
  **18** `.alert-secondary` del sistema de una vez — incluido uno que ya estaba mal **antes** de la
  tanda.
- **Un cambio en el tema entra en la pregunta 2 del control de 7** (pieza compartida): hay que
  contar cuántas pantallas toca y decírselo a Joel, aunque el cambio sea para mejor. "Corrige 18
  lugares" es un dato que él necesita antes de probar, no después.

## Un defecto latente aparece cuando el dato crece, no cuando se escribe el código (B205, 15/11/2026)

Los contadores de los títulos de la fase 2 imprimían el `int` tal cual: `<%= totalSesiones %>`.
Pasaron seis pantallas y seis revisiones en pantalla sin que nadie lo notara, porque **ninguno había
pasado de 999**. El de Auditoría llegó a 2.230 y el defecto salió solo: *"Auditoría (2230
registros)"*.

- **Al cerrar un ítem que muestra un número, preguntarse qué pasa con tres cifras más.** Un
  separador de miles, un ancho de columna, un `colspan` — todos se ven bien con los datos de hoy.
- **Si el defecto está en N lugares, el arreglo es un dueño nuevo y un barrido**, no el lugar donde
  apareció: acá `FormateadorNumeros.comoCantidad` + los otros 12 contadores, que tenían el mismo
  defecto esperando. Joel lo pidió así de entrada (*"revisá todos los contadores nuevos"*), y es el
  criterio correcto.
- **Y ojo con los fragmentos que se sirven por AJAX**: tres de esos doce (`_resultados.jsp` de
  Clientes, Aprobaciones y Mora) no pasan por `cabecera.jsp`, así que **no heredan ningún import**;
  el `<%@ page import %>` va en el fragmento mismo (B169), y buscar el ancla de la cabecera para
  insertarlo no encuentra nada.

## Dos avisos que dicen lo mismo: el que sobra es el genérico (B207, 15/11/2026)

Con el rango invertido, la pantalla mostraba el aviso ámbar nuevo (*"Revise el rango…, así que el
listado sale vacío"*) **y** debajo el cartel de siempre (*"No hay operaciones registradas con esos
filtros"*). Los dos eran ciertos y uno sobraba: el genérico no explica nada que el específico no
haya dicho mejor.

- **Al agregar un aviso que explica un caso particular, buscar el aviso genérico que ya cubría ese
  caso** y apagarlo para ese caso — si no, el usuario lee dos cosas y tiene que decidir cuál manda.
- **La condición se evalúa una vez y se comparte por el request**: el fragmento que ya calculaba el
  caso deja la marca (`request.setAttribute`) y los doce carteles la consultan. Calcularlo dos veces
  es dos lugares donde la regla puede divergir.
- **El orden importa**: el fragmento que deja la marca tiene que incluirse **antes** en la página que
  el que la lee. Con `<%@ include %>` (traducción estática) eso es literalmente el orden del
  archivo.

## Un helper de paginación no debe recibir ni la ruta ni los filtros (B210, 15/11/2026)

Paginar dieciséis reportes podía ser dieciséis veces "armá la URL base con tus filtros", que es
exactamente el olvido que B147/B163/B170/B175 fueron corrigiendo pantalla por pantalla. Lo que
quedó es un helper que **no sabe nada del reporte**: la ruta sale de `request.getRequestURI()` y los
filtros, de `request.getParameterNames()` menos `pagina`.

- **Si un helper necesita saber en qué pantalla está, casi siempre el pedido ya lo dice.** Pasarle la
  ruta como texto es una copia más que puede quedar vieja.
- **Copiar TODOS los parámetros menos el propio** hace que un filtro nuevo viaje solo: nadie tiene
  que acordarse de agregarlo a la paginación. Es la diferencia entre una lista blanca (que se olvida)
  y una lista negra de uno (que no).
- **Y el recorte en Java o en SQL es una decisión de volumen medido**, no de estilo: 190-292 filas se
  cortan en Java sin pensarlo; 18.381 (Auditoría, B205) obligan a SQL. Los dos conviven en el mismo
  controlador y cada uno dice por qué en su comentario.

## Dos fragmentos parecidos pueden necesitar fuentes distintas: parámetro vs. atributo (B211, 15/11/2026)

`aviso_rango_reporte.jsp` (B207) lee los **parámetros** del pedido, y está bien: habla de lo que la
persona escribió. `rango_aplicado_reporte.jsp` (B211) parece su gemelo y tuvo que leer un
**atributo**, porque necesita el rango que el reporte está usando **de verdad** — y en la primera
carga no hay ningún parámetro: el valor por defecto lo resolvió el controlador, que es justo cuando
más falta saber cuál es.

- **Antes de copiar el molde de un fragmento, preguntarse de dónde sale su dato en el caso
  inicial.** "Lo que se pidió" y "lo que se está mostrando" coinciden casi siempre y se separan
  exactamente en la carga sin parámetros, que es la que todos ven primero.
- **Cuando el dato efectivo lo resuelve el controlador, el texto lo deja el controlador**: la vista
  no vuelve a resolver un valor por defecto, porque sería la segunda copia de esa regla.

## Un total va en la columna que totaliza, o no va en una celda de datos (B212, 15/11/2026)

Tres reportes apilaban dos o cuatro importes con `<br>` en una sola celda del `tfoot`. Al
desarmarlos aparecieron tres casos distintos, y el tercero es el interesante:

| Caso | Qué hacer |
|---|---|
| El número **es** el total de esa columna (*Vencimientos*: el monto) | va en esa celda, solo |
| Son **partes** del total (capital, interés, IVA) | un renglón propio cada uno, un número por celda |
| **Ninguno** es el total de la columna bajo la que estaba (*Morosidad*: capital vencido y saldo, debajo de "Monto exigible") | se rotula cada uno y **no se inventa un total que el DTO no trae** |

- **Antes de "poner cada total en su columna", verificar que la columna exista en el cuerpo.** Si el
  cuerpo muestra un solo número y el pie muestra cuatro, tres de esos cuatro no tienen columna: son
  un desglose y necesitan renglones, no celdas.
- **Y hay totales que NO se suman a propósito**: en *Caja*, un sobrante y un faltante no se cancelan
  —cada uno es un problema de caja distinto (RF18/B52)—, así que van en renglones separados y no en
  un "total de diferencias" que no significaría nada. Al desarmar una celda conviene preguntarse si
  la suma que falta es un olvido o una decisión.


## Ordenar por un dato FORMATEADO es ordenar por otra cosa (B210, 15/11/2026)

Joel probó la paginación de *Vencimientos* con el rango 15/11 a 31/12 y la página 1 arrancaba en
**01/12**: las cuotas del 15/11 —las que vencen **hoy**— no estaban. La causa, en una línea:

```java
filas.sort((a, b) -> a.getFechaVencimiento().compareTo(b.getFechaVencimiento()));
```

`getFechaVencimiento()` devuelve el **texto** `"dd/MM/yyyy"` (se formatea nueve líneas antes, al
armar el DTO). Comparado como String, eso ordena por **día, después mes, después año**: `"01/12/2026"`
va antes que `"15/11/2026"`, y `"01/01/2027"` antes que `"31/12/2026"`.

- **El dato formateado es para mostrar: nunca para calcular, comparar ni ordenar.** Si un DTO lleva
  un valor ya formateado y alguien necesita ordenar por él, lo que falta es el valor **crudo** al
  lado — no un comparador más astuto. Este mismo DTO ya tenía el precedente: **B99** le había
  agregado `getMontoExacto()` (el `BigDecimal`) junto al monto formateado, por exactamente la misma
  razón.
- **Los síntomas de este defecto con fechas `dd/MM/yyyy` son reconocibles**: el listado arranca en
  los días bajos de cualquier mes, y los meses se intercalan. Con `yyyy-MM-dd` el texto **sí** ordena
  bien, y por eso el defecto sobrevive donde se usa ISO y aparece recién donde se formatea a la
  paraguaya. Lo mismo con los importes: `"9.000 Gs."` va antes que `"80.000 Gs."`.
- **El comparador va extraído, `public static`, con nombre propio.** Así su JUnit lo prueba sin
  base, con dos filas armadas a mano cuyo orden por texto es el **inverso** del orden por fecha
  (`ServicioMora.COMPARADOR_PRIORIDAD`, de B197, es el precedente). Y la prueba hay que verla en
  **rojo contra el código viejo** antes de darla por buena: acá falló con
  `expected:<15/11/2026> but was:<01/12/2026>`, el síntoma exacto que vio Joel.
- **Al barrer N listados por este defecto, el criterio es de dónde sale el orden**: si viene de un
  `ORDER BY` de SQL sobre una columna real, está bien; el riesgo está en los `sort` de Java sobre
  DTO ya formateados. De los 16 listados de B210, catorce ordenaban en SQL y solo uno tenía el
  defecto. (El de IVA ordena por un `numero` que es texto, pero está rellenado a ancho fijo, así que
  hoy ordena bien: eso se mide, se anota como frágil y no se toca.)

## Paginar no crea defectos de orden: los vuelve consecuentes (B210, 15/11/2026)

El orden de *Vencimientos* estaba mal desde que el reporte existe, y pasó por su revisión en
pantalla sin que nadie lo notara: con las 205 filas en **una sola** pantalla, el desorden era una
molestia enterrada en una lista larga que igual se recorría entera. Al paginar, las primeras 20
filas dejaron de ser "el principio de la lista" y pasaron a ser **la respuesta**: lo que no está en
la página 1, para el que la mira, no existe.

- **Al agregar paginación a un listado, revisar su ORDEN antes que nada**, incluso si el listado
  venía "funcionando": la paginación no cambia el orden, cambia **quién lo nota**.
- Es el espejo de la otra lección de paginar (B175, B197, B210): paginar cambia **qué filas se
  dibujan**, así que hay que mirar el humo. Acá cambia **qué filas se dibujan primero**, así que hay
  que mirar el orden.
- **Y vale para el próximo**: cuando un defecto latente aparece justo después de un cambio, la
  pregunta no es solo "¿qué rompí?" sino "¿qué volví visible?". Decirlo con precisión importa — este
  no lo trajo B210, lo **destapó**.

## Una cantidad no totaliza ninguna columna: va en el rótulo, con su unidad (B212, 15/11/2026)

En la fila de totales de *Vencimientos*, la cantidad de cuotas (`205`) caía en la celda de la
columna **"Cuota"**, que en el cuerpo tiene números de cuota: se leía como *"cuota 205"*. Lo mismo
en *Morosidad*, donde la cantidad es de **préstamos** del tramo y caía bajo *"Cuotas venc."*.

- **Un total va en la columna que totaliza; un recuento no totaliza ninguna** — va en la celda del
  rótulo, con su unidad (*"Total (205 cuotas)"*, *"Subtotal tramo 31-60 (12 préstamos)"*). Si el
  número tiene que ir en una celda de datos, la pregunta es qué columna está sumando.
- **El peor caso no es el número sin rótulo: es el número que cae bajo un rótulo ajeno**, porque
  entonces no se lee como "falta un dato" sino como **otro dato**.


## Un defecto de texto se barre por CAPA, y en algunas capas no se corrige (B210, 15/11/2026)

Joel encontró *"autorizada **--** ver la Nota"* en la descripción de un reporte: dos guiones donde
va la raya (—). Barrer el defecto dio cuatro lugares más, en tres capas distintas, y la decisión
correcta fue distinta en cada una:

| Dónde | Qué se hizo |
|---|---|
| **Texto de pantalla** (la JSP que vio Joel) | corregido \u2014 era el único en los 21 reportes; todos los otros `--` estaban dentro de comentarios JSP |
| **Etiquetas de un CSV** (`"Cartera -- capital pendiente"`) | **anotado, NO corregido** |
| **Detalle de auditoría** (una cadena que se guarda en la base) | **anotado, NO corregido** |

- **El CSV se abre en Excel**: una raya puede salir mal según la codificación con que Excel lo lea,
  y cambiar la etiqueta altera el **contenido** de un archivo que alguien puede estar procesando.
  Eso no es un arreglo de texto: es un cambio de formato de salida, y lo decide Joel.
- **Un detalle de auditoría ya guardado no se reescribe** (la tabla es de solo inserción, 18.381
  filas): cambiar el literal solo afecta a los registros **futuros**, así que el reporte quedaría
  con dos redacciones del mismo evento. Si se cambia, se cambia sabiendo eso.
- **La regla general**: al barrer un defecto de redacción, separar lo que se **muestra** (se
  corrige) de lo que se **guarda** o se **exporta** (se decide aparte, porque tiene lectores y
  versiones viejas). Y decirle a Joel los tres grupos, no solo el que se arregló.

## Un importe que se parte en dos renglones es la celda, no el texto (B210, 15/11/2026)

*"17.924.976 Gs."* salía en dos líneas en el pie de *Facturas anuladas*. No es un problema del
formato del número: son **diez columnas en una `table-sm`**, así que esa celda queda angosta y el
navegador parte por el espacio antes de *"Gs."*.

- **La clase es `text-nowrap` en la celda**, y ya estaba en uso en el sistema
  (`parametros/administracion.jsp`, `parametros/historial.jsp`) para exactamente esto: buscarla
  antes de inventar un `<span>` o un `&nbsp;`.
- **El rótulo sí puede partirse**; lo que no puede es el número. Poner `nowrap` en toda la fila
  ensancha la tabla y empuja el scroll horizontal.
- **Y si el defecto apareció en la tabla más angosta, revisar las vecinas con el mismo riesgo**:
  acá se aplicó también a las otras 9 celdas de importe de los `tfoot` reestructurados, porque Caja
  tiene nueve columnas y era la siguiente en aparecer.

## Un caso especial de un mecanismo compartido se resuelve con una MARCA, no cambiando el mecanismo (B213, 15/11/2026)

La contraseña temporal de un usuario se muestra **una sola vez** y en el modal de éxito que
`plantilla/pie.jsp` abre para **todo** el sistema. Hacía falta que **ese** modal no se cerrara con
un clic afuera ni con Esc y que tuviera *"Copiar"*. Poner `allowOutsideClick:false` en el
`Swal.fire` de `pie.jsp` habría cambiado **todos** los avisos de éxito de todas las pantallas ya
probadas: un cambio de una línea con el alcance de la pregunta 2 del control entero.

La salida, que el propio sistema ya tenía escrita como precedente (`mensajeExitoImprimibles`, D-131):
**el llamador marca el caso y el mecanismo reacciona a la marca**.

- `MensajesFlash.guardarExitoSensible(sesion, mensaje, valorACopiar)` → `cabecera.jsp` dibuja un
  `div` oculto → `pie.jsp` cambia de comportamiento **solo si ese `div` existe**. Sin la marca,
  `Swal.fire` recibe exactamente las mismas opciones de antes, y eso se puede **afirmar leyendo el
  código**, sin volver a probar las otras pantallas.
- **La pregunta que decide la forma** es: *¿este comportamiento lo quiero para todos los casos que
  pasan por acá, o para uno?* Si es para uno, el `if` va en el mecanismo y la decisión en el
  llamador — nunca al revés (el llamador no debe saber cómo se dibuja el modal).
- **Y el dato que viaja es el dato, no el mensaje**: lo que se copia es la contraseña sola, no la
  oración entera. Si la marca llevara el texto completo, el botón "Copiar" copiaría *"Usuario
  creado. Contraseña temporal: …"* y habría que recortarlo en el navegador, que es donde no vive
  la regla.

**Y un detalle de portabilidad que vale para cualquier cosa que copie al portapapeles**: no se
confía en `navigator.clipboard`, que **no está disponible en todo contexto** (depende del navegador
y de cómo se sirva la aplicación), y este sistema se instala en el servidor de la institución, no
solo en esta máquina. Se intenta esa API y, si no está o si falla, se copia con un campo temporal y
`document.execCommand('copy')`. Las dos son del navegador: no agregan ninguna dependencia ni
ningún recurso de internet (pregunta 7 del control). Y si las dos fallan, **el aviso lo dice**
(*"No se pudo copiar: anótela antes de cerrar"*) en vez de quedarse mudo — un botón que no avisa su
fracaso es peor que no tener el botón, porque el dato se muestra una sola vez.

## Una ayuda de pantalla que describe una validación va en el dueño de la validación (B215, 15/11/2026)

`/parametros` no decía qué valores acepta cada parámetro: el límite se descubría cuando el servidor
rechazaba el cambio. La tentación es escribir las frases en la JSP, que es donde se ven. **Eso es
C20 esperando**: el día que cambie una validación, la pantalla sigue prometiendo la vieja.

- Las frases viven en `ServicioParametros.reglaDeClave`, al lado de los `switch (clave)` que
  validan, y devuelven **null** para las claves sin regla cruzada — **una pantalla que no sabe no
  promete nada** es mejor que una que generaliza (de los 52 parámetros vigentes, solo 17 tienen
  regla).
- **La red que lo sostiene cruza las dos listas leyendo el fuente**, y en las **dos** direcciones,
  porque agarran errores distintos: una regla sin frase deja la pantalla muda (el defecto que el
  ítem vino a cerrar), y una frase sin regla es la pantalla prometiendo algo que el servidor no
  verifica. Es la misma idea del § *"Si una regla hay que copiarla, la prueba lee el original"*,
  aplicada a una **descripción** de la regla en vez de a una copia de la regla.
- **Un tercer test fija cuántas reglas hay hoy** (17 = 15 `case` + 2 `equals(clave)`), para que un
  `switch` nuevo en otro método no entre como si fuera una validación de parámetros sin que nadie
  lo mire.
- **Y antes de mostrar "el valor actual" hay que preguntarse si lo que se muestra es lo que hay que
  escribir.** Acá no siempre: dos parámetros de texto son listas que se **muestran con espacios**
  (`comoListaConEspacios`) y se **guardan sin ellos**, así que poner *"Actual: A, B, C"* al lado del
  campo invitaba a retipearlas con espacios y a cambiarles el formato guardado. Se mostró el valor
  actual **solo** donde el formato importa y no engaña (DECIMAL, ENTERO y FECHA), usando el **mismo**
  valor que ya calcula la columna de al lado — nunca un segundo formateo propio.

## La QUINTA forma del punto duplicado: una variable local con el monto ya formateado (B215, 15/11/2026)

Joel probó la ayuda nueva de `/parametros` y ahí estaba otra vez: **"Actual: 500.000.000 Gs.."**. La
tabla de las formas de este defecto queda así, y vale leerla completa antes de escribir cualquier
texto con un monto:

| # | Forma | En el fuente | La atrapó |
|---|---|---|---|
| 1 | Concatenación en Java/JS | `comoGuaranies(x) + "."` | B79 |
| 2 | Literal a mano | `"… Gs.."` | B79 |
| 3 | Expresión de JSP + marcado | `<%= comoGuaranies(x) %>.` | B178 |
| 4 | Getter de un DTO ya formateado | `<%= fila.getMontoAplicado() %>.` | B192 |
| 5 | **Variable local con el monto formateado** | `valorMostrado = comoGuaranies(x)` … `<%= valorMostrado %>.` | **B215** |

Las cuatro redes anteriores **no podían** verla, y por motivos distintos: la de B178 exige que la
expresión **termine** en el formateador (acá la llamada está veinte líneas más arriba), la de B192
mira el nombre de un **getter** (acá es una variable), y la de B79 busca una **concatenación**.

- **Cuando el nombre no sirve, se sigue el RASTRO.** El nombre de la variable no dice nada de
  dinero —la misma `valorMostrado` lleva a veces un porcentaje y a veces una fecha, y **por eso** el
  defecto aparece en una sola de sus ramas—, así que la red junta, por archivo, las variables que
  ese archivo **asigna** desde `comoGuaranies`/`formatearGs` y después las busca impresas con el
  punto pegado, en las dos capas (`<%= var %>.` en una JSP, `var + "."` en Java o JS). Es más
  preciso que cualquier heurística de nombres: 72 JSP, **45** expresiones seguidas de punto, **15**
  variables de monto — la red mira esas 15 y marcaba **1**.
- **Y acá el arreglo NO es sacar el punto**, a diferencia de las otras cuatro veces: la frase
  *tiene* que cerrar, porque al lado se imprime otra oración. Se arma con
  `FormateadorTexto.conPuntoFinal`, que lo agrega **solo si falta** — el dueño que ya existía desde
  **B188**. Antes de "sacar el punto" por reflejo, mirar si la oración necesita cerrar: si necesita,
  el que decide es ese método y no la vista.
- **La pregunta que generaliza las cinco formas**: *¿en qué capa se formateó este valor, y cuántos
  pasos hay entre el formateador y el punto?* Si hay más de cero (un DTO, una variable, un
  atributo), ninguna red que mire **la línea del punto** lo va a encontrar.

## Si el build falla, el informe de surefire que quedó en `target/` es el de la corrida ANTERIOR (15/11/2026)

Al escribir la red de la quinta forma, el patrón se escribió dentro de un heredoc y **el shell se
comió una barra**: quedó `"(\w+)…"`, que en Java **no compila**. Eso dio `BUILD FAILURE`… y al
mirar el resultado se leyó `target/surefire-reports/…TextosDePantallaTest.txt`, que decía **8 test,
0 fallas**. Conclusión equivocada, y durante unos minutos: *"la red no agarra el defecto"*. Ese
archivo era de la corrida anterior — con el build en rojo, **nadie lo reescribe**.

- **El exit code se mira primero**, siempre: `mvn … > salida.txt 2>&1; echo "exit=$?"`. Un informe
  de surefire sin su corrida es un dato viejo con cara de dato nuevo, exactamente como una skill
  desactualizada (DOGMA 3) o una JSP vieja en `target/` (B129).
- Y la causa de raíz es la que esta skill ya tiene escrita dos veces: **un literal con barras
  invertidas no viaja por un heredoc**. Va con la herramienta de escritura a un archivo y de ahí se
  inserta. El síntoma a reconocer en el acto es el `BUILD FAILURE` en un cambio que "solo agrega un
  test": antes de dudar del test, **mirar el literal que se acaba de escribir**.

## Paginar una pantalla nueva: la lógica del servidor ya existe (B219, 15/11/2026)

Al paginar el historial de parámetros apareció que la cuenta de páginas, la página pedida y la URL
base **ya estaban escritas**, dentro de `ControladorReportesServlet`, para los dieciséis reportes de
B210. Se extrajeron a **`infraestructura/PaginacionPantalla`** (con 6 JUnit: el redondeo, el
`?pagina=0`/`-3`/`hola` y la URL que lleva los filtros y nunca la página vieja).

- **Para paginar la próxima pantalla** hacen falta tres cosas y ninguna se escribe de nuevo:
  `PaginacionPantalla.paginar(request, filasCompletas)` en el controlador,
  `<%@ include … plantilla/paginacion.jsp %>` en la vista (B167), y **calcular los totales ANTES de
  recortar** — contar lo que quedó dibujado diría "20" en cada página (B191).
- **Reportes todavía tiene su copia**: pasarlo a delegar es **B225**, anotado y sin aplicar, porque
  es el controlador de dieciséis pantallas ya probadas y estamos en víspera de entrega — el mismo
  criterio con que B183 decidió no tocar `listado-vivo.js`. Mientras eso siga abierto, la pregunta
  *"¿de dónde saco la paginación?"* tiene una respuesta: `PaginacionPantalla`, que es el dueño
  declarado. **Nunca de la copia de Reportes.**
- **Y el criterio de dónde recortar sigue siendo el volumen medido** (B205/B210): en Java hasta unos
  cientos de filas; en SQL cuando son miles. 164 filas se recortan en Java sin pensarlo.

## Enseñarle algo a una pieza compartida: aditivo y condicionado a una marca que hoy tiene un solo usuario (B220, 15/11/2026)

Segunda vez en el día (la primera fue B213 con el modal de éxito). Para agrupar los 52 parámetros
hacía falta que `recursos/js/buscar-en-tabla.js` —que usan **8 pantallas**— entendiera que una fila
de encabezado de grupo **no es un dato**: no se filtra, no se cuenta en *"Mostrando X de N"* (si se
contara, la pantalla diría "de 60" donde hay 52 parámetros) y se oculta sola si su grupo queda sin
filas visibles.

- **La forma**: una clase nueva (`buscar-en-tabla-grupo`) que el script excluye del selector y
  atiende aparte. En las 8 pantallas que no la usan, el bloque nuevo **no hace nada** — y eso se
  puede **afirmar leyendo**, sin reprobar las ocho.
- **El patrón, ya con tres casos**: `data-confirma-a` (B216), `datosMensajeExitoSensible` (B213) y
  `buscar-en-tabla-grupo` (B220). Cuando un caso particular necesita algo del mecanismo compartido,
  el mecanismo gana un `if` **condicionado a una marca**, y el caso particular la pone. Nunca al
  revés, y nunca cambiando el comportamiento por defecto.
- **Y hay que preguntarse qué más cuenta ese mecanismo**: acá el error más fácil no era el filtro
  sino el **contador** — una fila de encabezado habría entrado en el total sin que nadie lo note
  hasta leer "de 60".

## Para ver fallar una regla que vive en el SQL o enredada en un `if`, primero se extrae (B223/B224, 15/11/2026)

Joel pidió el arreglo del tope legal "con JUnit que lo vea fallar primero". Las dos reglas no se
podían ver fallar: una vivía en una consulta (`WHERE vigente_hasta IS NULL ORDER BY vigente_desde
DESC LIMIT 1`) y la otra dentro de un `if` anidado del alta. El camino que funcionó, y es el que
conviene repetir:

1. **Extraer la regla a un método puro, escrito con la lógica VIEJA**, tal cual: `elVigenteEn` que
   mira solo `vigente_hasta IS NULL`, `cierreParaLimiteReemplazado` que devuelve null cuando hoy no
   cierra. No es trabajo perdido: es el paso que permite el siguiente.
2. **Escribir los JUnit del comportamiento que se quiere** y correrlos: de 14, **5 en rojo**,
   nombrando los dos defectos (el tope futuro aplicándose hoy, el de hoy desapareciendo, el
   desempate no determinista, el reemplazo imposible el mismo día y a futuro).
3. **Corregir la regla** y volver a correr. El diff del arreglo queda chico y, sobre todo, **cada
   caso del test tiene un defecto real detrás** en vez de ser una tabla escrita a gusto.

- **Sacar una decisión del SQL a Java se justifica por volumen medido** (B205/B210): `limites_tasa_usura`
  tiene **una** fila y crece una vez por cambio del tope del BCP. Con 18.000 filas la respuesta
  habría sido la contraria.
- **Y la firma pública no se toca**: `obtenerVigente(conexion)` siguió igual y se le agregó
  `obtenerVigenteEn(conexion, fecha)` al lado, así los **siete** lectores del tope no cambian ni una
  línea. Cuando un dato lo leen N lugares, el arreglo que no les toca la firma es el que se puede
  revisar leyendo.

## "Vigente" son DOS mitades, y la que falta es la que bloquea todo (B224, 15/11/2026)

El defecto era que `obtenerVigente` no comparaba `vigente_desde` con hoy. El arreglo obvio
—agregar `vigente_desde <= hoy`— **habría dejado el sistema peor**: con un tope futuro programado,
el alta ya cerró el anterior el día previo a esa fecha, así que no hay ningún límite **abierto** que
cubra hoy, y la consulta habría devuelto null → *"No hay un límite de tasa vigente cargado"* en el
cotizador, la aprobación y la formalización. O sea: el circuito de crédito entero caído por
programar un tope.

- **Vigente en una fecha es `desde <= fecha` Y (`hasta` nulo O `hasta >= fecha`).** Si un sistema
  cierra vigencias (y este las cierra solo, en el alta), **el registro que rige hoy puede estar
  cerrado**: filtrar por "sin fecha de cierre" es una suposición, no la definición.
- **El caso que lo demuestra es el que hay que escribir primero**: *"con un tope futuro programado,
  ¿cuál rige hoy?"*. Es el que Joel pidió explícitamente (*"el cierre automático sigue
  funcionando"*), y el que separa el arreglo correcto del arreglo a medias.
- **Un rango vacío (`hasta < desde`) no rige ni se superpone con nada.** Al permitir que un registro
  se cierre antes de su propio inicio —que es lo que significa "nunca rigió"— hay que revisar **todas**
  las condiciones que miran esa vigencia: acá eran dos (la que elige el vigente y la que valida
  superposición), y la segunda no se notaba hasta probar el caso del límite programado.
- **Y la pantalla tiene que decirlo**: una fila con la vigencia al revés no se muestra como fechas,
  se muestra como *"No rigió (reemplazado)"*; y un registro futuro no se llama *"(vigente)"* sino
  *"Rige desde el DD/MM"*. Si la pantalla dejara el rótulo viejo, el arreglo del dato sería
  invisible y el usuario seguiría leyendo lo contrario de lo que pasa.

## Un formulario que se "pliega" se oculta por CSS, nunca se saca del HTML (B220-bis, 15/11/2026)

Al compactar `/parametros` —cada fila con un botón *"Cambiar"* que despliega su formulario— la
tentación es dibujar el formulario **solo cuando está abierto**. Eso habría roto el humo sin tocar
una sola regla de negocio: sus cuatro POST a `/parametros/actualizar-valor` sacan el `tokenCsrf` del
`GET /parametros` (`actualizar_parametro_como_admin` → `extraer_token_csrf`), y sin formulario en el
HTML no hay token. El fallo habría aparecido como *"el POST da 403"*, o sea **el andamio
disfrazado de producto**, que es la trampa de B197/B113.

- **Antes de plegar, esconder o cargar por AJAX un bloque que contiene un `<form>`, grepear el humo
  por lo que ese bloque aporta al HTML** — empezando por `tokenCsrf`, que vive **dentro** de cada
  formulario (`plantilla/csrf.jsp`) y no en un meta tag.
- **Lo que hace que esto sea seguro acá es que el token es POR SESIÓN** (`Csrf.obtenerOCrearToken`
  guarda uno en la sesión y `csrf.jsp` imprime `${sessionScope.tokenCsrf}`), así que da igual de qué
  formulario lo saque el `grep`. **Eso se verifica leyendo, no se supone**: si fuera un token por
  formulario —como el `tokenGestion` de B197, que es `TokenUnaVez` por fila— la respuesta sería la
  contraria y habría que cuidar cuál queda en el HTML.
- **Y la decisión se escribe en el CSS y en la JSP**, no solo en el resumen: las dos tienen el
  comentario de por qué el `display:none` no puede volverse un `if` de servidor. Un día alguien va a
  querer "optimizar" eso.

## Dos clases, dos dueños: el filtro pone `d-none` y el despliegue pone la suya (B220-bis, 15/11/2026)

La fila de detalle tiene dos motivos independientes para estar oculta: el filtro la escondió, o
simplemente no está desplegada. Con **una sola** clase para las dos cosas, el último que escribe
gana y el bloque aparece o desaparece cuando no corresponde (es la familia de B187: dos escritores
sobre el mismo atributo).

- **Una clase por dueño**: el script del buscador pone `d-none` (visibilidad por filtro) y
  `…-detalle-abierta` (despliegue). No se cruzan porque el `d-none` de Bootstrap es
  `display:none !important` y **gana siempre** — así que "filtrado" pisa a "abierto" sin que haya que
  coordinar nada.
- **Y cualquier cambio de filtro cierra los desplegados.** No es solo prolijidad: un formulario
  abierto que el filtro acaba de esconder es un dato a medio escribir que el usuario no ve.
- **El comportamiento va donde ya vive la decisión de "qué se ve en esta tabla"** —el script del
  buscador—, no en un script propio de la pantalla: con dos dueños, el conteo *"Mostrando X de N"*
  es lo primero que empieza a mentir.

## Reusar un estilo de otra pantalla: cargar SU archivo, no copiar las reglas (B220-bis, 15/11/2026)

Joel pidió las píldoras de grupo "con el mismo patrón y estilo que el índice de /reportes". Las
reglas viven en `recursos/css/indice-reportes.css`, que hasta ahora cargaba **solo** esa pantalla.
Copiarlas a `sgcpc-tema.css` o a un archivo nuevo habría dejado **dos definiciones del mismo
estilo**, que divergen en el primer cambio; renombrar las clases a algo genérico habría tocado la
pantalla del índice, ya probada en pantalla, en víspera de entrega (el criterio de B183/B225).

- **Se carga el archivo de la otra pantalla y se reusan sus clases.** El nombre queda un poco fuera
  de lugar (`indice-reportes-categoria-boton` en `/parametros`), y eso se dice en el comentario; lo
  que no se negocia es que la definición sea **una**.
- **El comentario de cabecera de ese archivo decía "Cargado solo desde reportes/indice.jsp"**: al
  cargarlo desde otra pantalla, esa frase pasa a ser falsa. **Se corrige en el mismo cambio** (B109:
  un comentario que afirma algo sobre el sistema se lee como una afirmación a verificar).
- **El resto del archivo queda inerte**, y conviene decirlo: las clases del buscador grande y de las
  tarjetas no aparecen en la pantalla nueva. Si alguna vez aparecen por casualidad, el estilo del
  índice se le aplicaría sin que nadie lo haya pedido.

## El espacio que sobra en un texto de JSP no está en el texto: está ENTRE dos expresiones (B222, 16/11/2026)

Joel vio *"Administrador: 1 **,** Cajera: 1 **,**"* en el resumen de Usuarios. Buscar el espacio en
los literales no sirve de nada: no hay ninguno. El separador se imprimía con una expresión por
vuelta del bucle, así:

```jsp
<%  for (…) { %>
<%= primero ? "" : ", " %><%= entrada.getKey() %>: <%= entrada.getValue() %><% primero = false; %>
<%  } %>
```

y el **salto de línea más la sangría** que quedan entre el `%>` de una vuelta y el `<%=` de la
siguiente **son texto de plantilla**: la JSP los escribe en el HTML y el navegador los colapsa en un
espacio, justo antes de la coma.

- **La regla**: si un texto se arma **dentro de un bucle de JSP** con varias expresiones, se arma en
  el **scriptlet** (un `StringBuilder`) y la pantalla imprime **un solo valor**. Así no queda
  plantilla en el medio donde se pueda meter un espacio.
- **Si hay que dejarlo en el marcado**, el truco del proyecto es pegar las expresiones con un
  `<%` … `%>` vacío en el medio (`…<%\n%><% if … %>`), que se come el salto de línea. Sirve para dos
  o tres pedazos; para un bucle, no.
- **Esto NO lo ve `TextosDePantallaTest`**: sus redes leen `src/main` y el espacio **no existe en el
  fuente**, aparece al renderizar. Es la misma familia que el punto duplicado de B188 (el texto
  venía de la base) — **cuando el defecto nace al armar el HTML, la verificación es el HTML
  servido**: `curl` a la pantalla y `grep` del fragmento, que es como se confirmó este arreglo.
- **Y se mira con los ojos de la coma**: cualquier signo de puntuación pegado a una expresión
  (`, . ; :` y el cierre de un paréntesis) es un lugar donde el salto de línea de la plantilla puede
  meterse. Si en la pantalla hay un espacio raro antes de un signo, el sospechoso número uno es el
  marcado entre expresiones, no el literal.

## `panel.jsp` lo LEE el proyecto hermano, carácter por carácter (B228-B233, 16/11/2026)

Antes de tocar una línea de `src/main/webapp/WEB-INF/vistas/panel.jsp` hay que saber que
`../sgcpc-api` (el `.war` de la API, proyecto aparte) tiene la **copia** de "qué tarjeta ve cada
perfil" en `TarjetasPanelApi`, para la aplicación móvil, y que su `TarjetasPanelApiTest` **abre este
archivo** y le exige:

| Qué le exige | Qué se rompe si se cambia |
|---|---|
| Los **3 booleanos** de perfil, tal cual (`esGerentePanel` = `GERENTE_ADMINISTRADOR \|\| ADMINISTRADOR`, etc.) | que la app muestre tarjetas a un perfil equivocado |
| **4 conteos de guardas**: `esOficialPanel` ×2, `esOficialPanel \|\| esGerentePanel` ×1, `esCajeroPanel \|\| esGerentePanel` ×2, `esGerentePanel` ×4 | agregar, sacar o mover una condición de visibilidad |
| Que **no exista** una guarda `es<Algo>Panel` que el Java no conozca | un perfil nuevo en la pantalla y no en la app |
| Los **dos cortes del saludo** (12 y 19) | que la tablet salude distinto que la web |
| **32 pedazos de texto** de las 15 tarjetas, carácter por carácter | corregir una palabra en la pantalla y no en la app |

- **Se corre desde este proyecto, sin moverse de carpeta**:
  `source ./entorno.sh && mvn -f ../sgcpc-api/pom.xml test` — el `mvn` sale del `entorno.sh` de
  `sistema`, así que en la carpeta del hermano, sola, `mvn: command not found`.
- **Mover bloques enteros es seguro; cambiar una guarda o un texto no.** Reordenar los tres bloques
  del panel (B228) no movió ningún conteo porque las guardas viajan **dentro** del bloque que se
  mueve. En cambio, poner una mayúscula en una descripción (B233) rompe el test: el pedazo
  `"préstamo"` en minúscula aparece **una sola vez** en el archivo y es exactamente el que se
  querría cambiar.
- **Y antes de "corregir" un texto de esa pantalla, leer el porqué en el hermano.** Esa minúscula
  está decidida a propósito y escrito en `TarjetasPanelApi:292`: el número va arriba y grande y la
  descripción es su **continuación** (*"22" / "préstamos pendientes de…"*), así que no es un rótulo
  como las otras diez. Un informe de UX puede marcar como inconsistencia algo que en el otro
  proyecto tiene su razón: **se verifica antes de aplicar, y si cruza los dos proyectos se frena y
  se le pregunta a Joel** (fue su propio pedido al abrir la tanda).

## Un emparejamiento heurístico no prueba que no se tocó una condición (B228, 16/11/2026)

Para demostrar que reordenar los bloques del panel no cambiaba la visibilidad de ninguna tarjeta,
saqué una foto que emparejaba cada tarjeta con *"la última guarda abierta arriba de ella"*. El diff
marcó una diferencia en *"Préstamos en mora"*… que **no tiene guarda**: se ve siempre, para los
cuatro perfiles. El emparejamiento era una heurística que no sigue los cierres de los `if`, así que
inventó una relación que no existe.

- **Lo que sí prueba**, y es lo que hay que usar: el **conteo** de cada guarda (idéntico antes y
  después) más la **medición en vivo de los cuatro perfiles** — entrar con cada uno y comparar el
  conjunto de tarjetas y de enlaces. Eso no depende de ninguna interpretación del marcado.
- **Y la medición en vivo sirve para las dos puntas**: la de "antes" es la que convierte el "no
  cambió nada" en un dato. Hay que sacarla **antes** de la primera edición.
- Es la misma familia que la lección de B129 (*un regex sobre marcado se rompe con los
  scriptlets*): cuando la verificación de un cambio depende de parsear la JSP, la verificación es
  más frágil que el cambio. Mejor medir el resultado que leer la causa.

## Un enlace nuevo: no se copia la condición del menú, se le pregunta al dueño del permiso (B234, 16/11/2026)

La regla del proyecto para los enlaces de una pantalla era **copiar la condición de `menu.jsp`**
(V5 punto 4: nunca un enlace a algo que el servidor va a bloquear). Al agregar los accesos rápidos
del panel, copiar habría metido un defecto: `menu.jsp` ofrece `/clientes` a los **cuatro** perfiles
—porque la **lista** la puede ver la cajera— y el acceso que hacía falta era *"Nuevo cliente"*, que
es otra cosa: la cajera tiene `REGISTRAR_EDITAR_CLIENTE` en **CONSULTA**, y `/clientes` deja pasar
el GET pero exige **TOTAL** para el resto (`FiltroAutorizacion:42`). Le habríamos puesto en el panel
un formulario que no puede guardar.

- **Cuando existe un servicio que decide el permiso, la pantalla le pregunta**:
  `ServicioAutorizacion.tieneAccesoTotal(perfil, permiso)` es el mismo que usa `FiltroAutorizacion`
  para bloquear, así que un enlace dibujado **no puede** terminar en 403. Copiar `menu.jsp` sigue
  estando bien para una ruta que el menú ya ofrece **con la misma granularidad**; para una acción
  más fina que la del menú, copiar **pierde** la distinción.
- **La pregunta que lo detecta**: *¿el permiso de esta acción tiene NIVELES?* Si alguno de los
  perfiles lo tiene en CONSULTA, "tiene la ruta" y "puede hacer la acción" no son lo mismo, y el
  menú —que es de rutas— no alcanza.
- **Y el nombre de un booleano nuevo importa**: en `panel.jsp` no puede llamarse `es…Panel`, porque
  el test del proyecto hermano cuenta esas guardas como condiciones de visibilidad de tarjeta. Se
  llamó `verAcceso*`, igual que los `verEnlace*` que ya estaban.

## El control de caracteres de control estaba ciego al `\r` suelto (16/11/2026)

El control que esta skill viene recomendando desde el 14/11 lee el archivo **en modo texto**:

```python
malas = [i+1 for i, l in enumerate(io.open(p, encoding="utf-8")) if any(c in l for c in (chr(8), chr(9), chr(13)))]
```

y en modo texto Python traduce los saltos de línea (*universal newlines*): **un `\r` suelto se
convierte en `\n`**, parte la línea en dos y el `chr(13) in l` **nunca lo encuentra**. O sea que
justo el carácter que más veces se coló en este proyecto es el que el control no veía. Dio
*"limpio"* sobre una ruta que había quedado escrita `..^Mespaldos_git` (el `\r` se comió la **r** de
`respaldos_git`) y, en el mismo literal, `\2026` interpretado como **octal** (`\202` = 0x82) se
había comido la fecha.

**El control corregido va en binario**, que además distingue el `\r` de un CRLF legítimo:

```python
datos = open(p, "rb").read()
cr_sueltos = [i for i, b in enumerate(datos) if b == 13 and (i + 1 >= len(datos) or datos[i + 1] != 10)]
otros = sorted(b for b in set(datos) if b < 32 and b not in (9, 10, 13))
print(ruta, "CR sueltos:", len(cr_sueltos), "| backspace:", datos.count(8), "| otros:", otros)
```

- **Y si hay que leer en modo texto, va con `newline=""`**, que desactiva la traducción.
- **Lo que delata el problema antes que cualquier control es el `SyntaxWarning`** de Python
  (`invalid escape sequence '\r'`, `'\s'`, `'\2'`…). Esta vez el warning estuvo, se leyó, y por eso
  se encontró el daño: **ese aviso nunca es cosmético**, y conviene mirarlo incluso cuando el
  control de después dice "limpio" — porque el control puede estar ciego, como estaba este.
- **Al corregir una ruta, se arma con `chr(92)`** en vez de barras escapadas: es la única forma que
  no depende de cuántas capas de escape atravesó el literal.
- **Y se barre el resto**: al encontrar uno, el mismo control en binario sobre todos los `.md` de
  `docs/` más `AVANCE.md` dice si hay más (el 16/11/2026: ninguno).

## Al cerrar

12. **Marcar el ítem como Resuelto** en `FASE2_PLAN.md`/`AUDITORIA_TECNICA.md` (fecha + hash
    del commit) y agregar una línea en el `RFxx_*.md` correspondiente
    (`docs/funcionamiento/`, REGLAS_DESARROLLO.md: actualizar el RF en el mismo commit que el
    cambio). **El estado vive solo ahí** (ver "Un dato, un dueño" arriba): en el recorrido va la
    observación con su fecha y un enlace, no una segunda copia del estado.
13. **Commit chico: un commit por ítem**, solo sus archivos (código + su documentación), con
    `git status --short` antes de comitear para confirmarlo — se estadean **por nombre**, nunca
    `git add -A`/`git add .`, para no arrastrar archivos sin seguimiento de otra tanda.
14. **Nunca `git commit --amend`** (reescribe algo que Joel ya pudo ver) y **nunca `git push`**
    sin que Joel lo pida. Si un commit salió mal, se arregla con un commit nuevo.
    **El mensaje de varias líneas se escribe con un heredoc de Bash** (29/10/2026, error real):

    ```bash
    git commit -F - <<'EOF'
    Asunto corto
    
    Cuerpo…
    EOF
    ```

    **Nunca `git commit -m @'…'@`**: esa es sintaxis de here-string de **PowerShell** y Bash no la
    entiende — el mensaje entra con un `@` suelto como primera línea, así que el **asunto del
    commit queda en `@`** y el título real se va a la segunda línea (le pasó al commit de B89,
    `7e38d9a`; Joel eligió dejarlo y explicarlo en un commit de nota en vez de reescribirlo).
    **Después de cada commit, verificar el asunto** con `git log -1 --format=%s` antes de seguir —
    y nunca apilar commits encima de uno que haya que corregir.
15. **Nombres visibles vs. nombres internos**: si el cambio renombra algo que el usuario lee,
    ver la regla en `sgcpc-diseno-ui` § "Lenguaje del usuario" — **ids, anclas, URL, estados y
    operaciones de auditoría no se renombran**.
15b. **Si un defecto de redacción ya se corrigió antes en otra pantalla, no alcanza con otro
    comentario: va un test** (30/10/2026, B79). El punto duplicado de un monto (*"1.880 Gs.."*;
    los dos formateadores, `formatearGs` y `comoGuaranies`, **ya terminan en "Gs."**) estaba
    anotado en un comentario **del mismo archivo** donde se repitió, y aparecieron **tres**
    comentarios más de pantallas donde ya se había corregido. Un comentario no frena un error.
    Ahora lo cubre `TextosDePantallaTest`, que **lee `src/main`** (`.java`/`.jsp`/`.js`) y falla si
    alguien le pega un `"."` al monto formateado o deja un `"Gs.."`. Es el modelo a copiar para
    cualquier regla de redacción que no se pueda probar con un JUnit normal: saca comentarios antes
    de buscar (si no, falla por las propias explicaciones) y deja escrita la excepción deliberada
    que exista (en `ServicioCotizacion` el `". Ajuste…"` es a propósito, porque `pie.jsp` parte
    título y detalle en el primer `". "`).
16. **DOGMA 3: actualizar las skills con este mismo ítem.** Preguntarse qué aprendió este ítem
    que cambiaría cómo se encara el próximo, y escribirlo en la skill que corresponda
    (`sgcpc-mapa` dónde está cada cosa, `sgcpc-cambio-seguro` el protocolo,
    `sgcpc-diseno-ui` pantallas y textos, `sgcpc-reportes-jasper` PDF). **Editar las dos
    copias** —`.claude/skills/<nombre>/SKILL.md` y `docs/skills_respaldo/<nombre>_SKILL.md`— y
    confirmar con `diff` que quedaron idénticas; solo la segunda se versiona. Si no hay nada
    que agregar, decirlo en el resumen en vez de omitirlo.

## Portabilidad (el sistema se entrega como .war + base, para instalarse en el servidor de la institución, en otra máquina, sin internet)

- Nunca una ruta absoluta de esta computadora (`C:\Users\...` ni ninguna otra) — todo camino de
  archivo relativo al proyecto, o leído de configuración (`bd.properties`,
  `parametros_sistema`).
- Nunca un recurso de internet (CDN, Google Fonts en vivo, cualquier fuente externa) — todo ya
  vendorizado en `src/main/webapp/recursos/` o empaquetado como dependencia Maven.
- La configuración que cambia entre instalaciones (conexión a la base, parámetros del negocio)
  se edita sin recompilar: `bd.properties` (fuera del `.war`) y `parametros_sistema`
  (`/parametros`).
- **Nada en `src/main` nombra la herramienta de asistencia** (28/10/2026, al preparar la copia
  para el docente — commit `56df05a`): ni en un javadoc o comentario (citar "reglas internas del
  proyecto", nunca el nombre del archivo interno de reglas), **ni en los metadatos de un binario**
  — seis PNG del sistema traían un chunk C2PA `caBX` con `com.anthropic.claude.provided`,
  invisible en pantalla pero extraíble con cualquier visor, y uno era el logo que se imprime en
  **todos** los PDF. Al agregar o reemplazar una imagen en `src/main`, revisar sus metadatos antes
  de commitear; si hay que limpiarlos, filtrar chunks copiando `IHDR`/`IDAT`/`IEND` byte a byte
  con su CRC original (nunca recodificar ni recomprimir, eso cambiaría el píxel).
- `.claude/` (skills, settings) nunca viaja en el `.war` — está fuera de `src/main/webapp`, la
  única carpeta que `maven-war-plugin` empaqueta (confirmado en `pom.xml`: sin
  `warSourceDirectory` propio, usa ese default). Si alguna vez se agrega un
  `warSourceDirectory`/`webResources` distinto, volver a confirmar esto.

## Nunca, sin pedido explícito de Joel

- Editar `docs/RF_ANTEPROYECTO.md`.
- Tocar `sgcpc_db` (solo `SELECT` en `sgcpc_db_pruebas`, como `sgcpc_user`, contraseña siempre
  leída de `bd.properties`, nunca escrita en un comando).
- Correr `bd/recrear_base.sh` o `pruebas/humo_http.sh`.
- `git push`.
- Lanzar agentes/subagentes en paralelo (ni para investigación de solo lectura) — trabajar
  secuencial, o preguntar primero.
- Parches o soluciones temporales — cambio mínimo que corrige la causa raíz, citando el
  `RF`/decisión y el hallazgo que resuelve (`docs/REGLAS_DESARROLLO.md` § 13).

## Al corregir una rama, mirar a sus vecinas (B127, 11/11/2026)

La apócope de `comoLetrasConGuaranies` tenía **un segundo sitio** en la misma clase:
`comoLetras` la aplicaba en la rama de **mil millones** y en la de **miles**, y **no** en la
de **millones** — así que 21.000 daba *"veintiún mil"* y 21.000.000 daba *"veintiuno
millones"*. Las tres ramas estaban a la vista, una debajo de la otra.

- **Cuando un método resuelve lo mismo para varios tramos (miles, millones, páginas,
  tipos de documento), el arreglo de uno se verifica contra los otros antes de darlo por
  cerrado.** La pregunta es "¿por qué este tramo sí y aquel no?", y casi nunca hay una
  razón: hay un olvido.
- **Lo encontró el test, no una pantalla**, y solo porque el test incluyó el caso del borde
  que nunca había aparecido en los datos (B103 ya lo había dejado escrito: buscar el valor
  que nunca ocurrió). Un test de un defecto de redacción conviene escribirlo **por familia**
  —todos los terminados en 1, en unidades, decenas, miles y millones— y no solo con el valor
  del reclamo.
- **Y el javadoc de la clase también mentía**: decía que ese método era *"para la vista
  previa del cotizador/ficha"* cuando lo usan además los siete puntos de documentos. Al
  tocar un método, releer la frase que lo presenta (C20 aplicado al javadoc).

## Un atributo de JSP puede llevar comillas adentro de un `<%= %>` (B129, 11/11/2026)

Al barrer los 23 `data-confirmar` con un script, `data-confirmar="([^"]*)"` **cortó el valor a
la mitad** en `reversiones/bandeja.jsp`, donde el valor incluye
`<%= pagoFila == null ? "-" : ... %>`: el atributo quedó partido y **rompió la compilación de la
JSP**. Lo atajó `mvn test` (el `jetty-jspc` corre en `process-classes`), que es exactamente para
lo que está.

- **Tapar los `<%…%>` y `${…}` ANTES de buscar el atributo**, no después, y mapear las
  posiciones al texto original. Taparlos solo para decidir dónde parte el texto no alcanza: el
  problema está en encontrar **dónde termina el valor**.
- **Un script que edita varias JSP se prueba primero en la que tiene el caso raro**, no en las
  diez fáciles; y si rompe una, se restaura con `git checkout --` de **ese** archivo y se rehace,
  en vez de parchear el resultado a mano.
- **La copia desplegada de una JSP puede quedar vieja aunque las demás se actualicen.**
  `target/sgcpc/.../formalizacion/ficha.jsp` seguía siendo la de seis días antes mientras las
  otras diez ya tenían el cambio, así que el cartel salía con el título viejo y parecía que el
  cambio no había funcionado. Antes de dudar del cambio: `curl` a la pantalla y `grep` del
  atributo, y comparar `src` contra `target` con `date -r` y el tamaño.
- **"Hoy no se puede ver" se verifica contra la base, no contra las últimas filas.** Se declaró
  que ninguna solicitud en BORRADOR cumplía el checklist mirando solo las **últimas 25**; el
  listado completo (`SELECT id_solicitud ... WHERE estado_solicitud='BORRADOR'`) tenía 39, y
  **cinco** cumplían. Un "no visto" mal declarado le saca a Joel una prueba que sí podía hacer.

## Un bloque entero del humo en rojo con el MISMO síntoma es una aplicación caída (B154, 14/11/2026)

El humo dio **38 fallas de 507** y las 38 decían lo mismo: `404` donde se esperaba `401` o `405`,
todas bajo `/api/`. Más **182 verificaciones que no aparecieron** en la corrida (sin token, los
casos autenticados no se ejercitan). Ningún defecto del producto: el `.war` de la API **no estaba
desplegado**.

- **Antes de buscar la causa en el cambio propio, leer la FORMA del conjunto de fallas.** 38 fallas
  repartidas por toda la tabla son 38 problemas; 38 fallas seguidas, del mismo bloque y con el mismo
  síntoma, son **una** causa — y casi siempre de entorno. El segundo dato que lo confirma es el
  **total**: si la corrida tiene cientos de verificaciones menos que la anterior, algo no se
  ejercitó.
- **Un cambio de CSS/JSP de un `.war` no puede hacer que OTRO `.war` conteste 404.** Eso descarta el
  cambio en curso en diez segundos, antes de tocar nada.
- **La causa se busca en los logs del contenedor, no en el código**: `logs/manager.*.log` registra
  cada `deploy`/`undeploy`/`start` con hora, y `conf/Catalina/localhost/` dice qué contextos quedan
  vivos. Acá la secuencia fue `11:52:47 undeploy /sgcpc` → `11:52:52 undeploy /sgcpc-api` →
  `11:54:51 install + start /sgcpc`: **el IDE repliega todo lo que tiene en ese servidor y vuelve a
  desplegar solo el proyecto que se corre**.
- **Y el paso culpable puede ser el primero del procedimiento, no el último.** Los dos repliegues
  pasaron **antes** del build y a 5 segundos uno del otro: el que se lleva la segunda aplicación es
  el *Undeploy* inicial (`REGLAS_DESARROLLO.md:201`), no el redespliegue. Con las horas del log eso
  se decide; sin ellas, se discute.
- **Cuando el sistema son dos `.war`, cualquier redespliegue de uno exige confirmar el otro**:
  `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/sgcpc-api/api/panel` tiene que dar
  **401** (no 404). Diez segundos, y evita leer una corrida entera al revés.

## El control de caracteres de control tiene que incluir el TABULADOR (14/11/2026)

Cuarta vez con la misma trampa, y esta vez la pasó por alto el propio control. Escribiendo una fila
de `FASE2_PLAN.md` con un script de Python **dentro de un heredoc**, el shell se comió una de las
dos barras y Python interpretó `\t`: en el documento quedó `C:` + **un tabulador** + `omcat9\webapps\`.
Se veía casi igual en pantalla, y el chequeo que el protocolo venía usando —`chr(8)` y `chr(13)`—
**no lo detecta**, porque un tabulador no es ninguno de los dos.

- **El control, corregido**, y conviene correrlo sobre cada archivo editado:

  ```bash
  python -c "
  import io, sys
  for p in sys.argv[1:]:
      malas = [i+1 for i, l in enumerate(io.open(p, encoding='utf-8'))
               if any(c in l for c in (chr(8), chr(9), chr(13)))]
      print(('OK    ' if not malas else 'CONTROL EN ' + str(malas) + ' '), p)
  " docs/recorrido/FASE2_PLAN.md AVANCE.md
  ```

  Si el archivo usa tabulaciones a propósito (no es el caso de los `.md` de este proyecto), el
  control se acota a las líneas que uno tocó.
- **Y la causa de raíz, otra vez**: un literal con una ruta de Windows **no se escribe con un
  heredoc**. Va con la herramienta de escritura/edición (que no tiene capa de escapes) o con
  `chr(92)`. La barra duplicada no alcanza cuando el texto cruza shell **y** lenguaje.
- **Lo que delata el problema en el momento**: Python avisa `SyntaxWarning: invalid escape
  sequence '\w'` (o `'\t'`, `'\s'`…). Ese warning **no es cosmético** — significa que el literal que
  llegó no es el que se escribió. Si aparece, se revisa el archivo antes de seguir.

**Tercera variante, y con otro culpable: los BACKTICKS dentro de `python -c "…"`** (15/11/2026,
escribiendo la nota de numeración de B198-B204). El texto llevaba `` `RF26 §10` `` —comillas
invertidas, que en Markdown son código— y bash, al estar el script entre **comillas dobles**, las
tomó como **sustitución de comando**: ejecutó `RF26 §10`, puso su salida vacía en el texto y dejó
el documento diciendo *"los pendientes falsos de )"* y *"y en  estaban anotados"*. Lo delató el
`RF26: command not found` en la salida.

- **Un texto en Markdown casi siempre lleva backticks**, así que **no viaja dentro de
  `python -c "…"`**: va con la herramienta de escritura a un archivo del scratchpad y de ahí se
  inserta, o el script se pasa con comillas **simples**.
- **Y el aviso está en la salida, no en el archivo**: un `command not found` en medio de un script
  que "funcionó" significa que **parte del texto se evaluó**. Si aparece, hay que mirar lo escrito,
  no el código de salida.
- La regla general de las tres variantes es la misma: **cuando el texto cruza shell y lenguaje,
  cada capa se queda con algo**. El camino sin capas —escribir el archivo y leerlo— no tiene este
  problema, y ya es el que esta skill recomienda para las rutas de Windows.

## Un `.war` en `webapps` pierde contra un descriptor en `conf/Catalina/localhost` (B154, 14/11/2026)

Para sacar la API de la administración del IDE se copió `sgcpc-api.war` a `C:\tomcat9\webapps\`
(con `autoDeploy="true"` y `unpackWARs="true"` en el `<Host>`). **Tomcat no lo desplegó**: el
descriptor `conf/Catalina/localhost/sgcpc-api.xml` que había instalado NetBeans ya era dueño de
`/sgcpc-api`, y el descriptor **gana** — el `.war` queda ahí, sin explotar y sin una línea de error
que lo explique.

- **Migrar un despliegue del IDE a `webapps` son DOS pasos**: copiar el `.war` **y** quitar el
  descriptor. Con uno solo, el estado es exactamente el de antes y parece que la copia no sirvió.
- **Cómo se confirma cuál manda**: `ls conf/Catalina/localhost/` (qué contextos hay),
  `ls webapps/` (si el `.war` se explotó en una carpeta del mismo nombre) y `logs/catalina.*.log`
  (`deployDescriptor` vs. `deployWAR`). Tres comandos y no hay nada que suponer.
- **Borrar archivos fuera del proyecto puede estar denegado**, y entonces el paso es de Joel: se le
  dice **exactamente** qué archivo o qué acción del IDE (*Undeploy*), se deja **respaldo** del
  descriptor en el scratchpad, y el ítem queda **"aplicado a medias"** en `FASE2_PLAN.md` — nunca
  "resuelto".
- **Y el descriptor puede volver**: un *Run* del proyecto desde el IDE lo reinstala y vuelve a tapar
  el despliegue de `webapps`. Por eso el procedimiento escrito dice *Clean and Build*, **nunca
  *Run***, y agrega el control de 10 segundos (`curl` que tiene que dar **401**, no 404).

## Ordenar un listado paginado: el orden es del SQL, y la clave nunca es la columna (B157, 14/11/2026)

Al hacer ordenables los encabezados del listado de clientes aparecieron cuatro cosas que vale no
volver a pensar desde cero:

- **Si la paginación es `LIMIT/OFFSET`, ordenar en el navegador es MENTIR.** Ordenaría las 20 filas
  de la página, no las 469 del filtro: el usuario ve "la primera por C.I." y es la primera de esa
  página. El orden va en la consulta, siempre.
- **Lo que viaja del navegador es una CLAVE, no un nombre de columna**, y la traducción vive con el
  SQL (una lista blanca `Map<String,String>` en el DAO). El controlador valida **antes** de guardar
  la clave, y lo desconocido cae en el orden de siempre. Así no hay ninguna ruta por la que texto
  del pedido llegue a la consulta — y eso se prueba contra el caso malo
  (`"cedula_identidad; DROP TABLE personas"`), no solo con las claves buenas.
- **Todo orden que no sea el de defecto necesita desempate** (`, apellidos ASC, nombres ASC`). Dos
  filas con el mismo valor quedan en un orden que PostgreSQL puede cambiar entre dos consultas, y
  con `LIMIT/OFFSET` eso se ve como una fila **repetida o faltante** al pasar de página — un
  defecto que aparece en producción y no en una prueba de una sola página.
- **Si el orden entra en el DTO del filtro, los demás consumidores lo heredan gratis.** Acá
  `construirFiltroDesdeRequest` es el único lugar que arma `FiltroClientes` y lo usan el listado, el
  informe PDF y el CSV: poner `orden`/`sentido` ahí hizo que los tres salieran igual **sin tocar los
  otros dos caminos**. Antes de agregar un parámetro a la firma de un DAO, mirar si el DTO que ya
  viaja es el lugar correcto.
- **Y lo que la pantalla ofrece tiene que poder hacerse**: tres columnas del listado no salen de esa
  consulta (las resuelve el controlador con mapas aparte), así que **no llevan control de orden**.
  Un encabezado que parece clickeable y no ordena es peor que uno que no lo parece; si alguna vez se
  las quiere ordenar, son JOINs en una consulta compartida y es otra decisión.

## Un *Run* del IDE sobre una app que vive en `webapps` se lleva el `.war` (14/11/2026)

Ampliación de B154, y el detalle que no se había anticipado. Al hacer *Clean and Build + Run* de
**los dos** proyectos, el *Run* de la API no solo reinstaló su descriptor: **antes borró de
`webapps` el `.war` y la carpeta explotada**. Medido en `catalina.*.log`:

```
15:04:45  HostConfig.undeploy /sgcpc-api      <- el Run del IDE, sobre la app de webapps
15:07:46  deployDescriptor sgcpc-api.xml      <- y se queda con el contexto
```

y `C:\tomcat9\webapps\` quedó con fecha 15:04 y **sin** `sgcpc-api*`. Es el comportamiento normal de
Tomcat: un *undeploy* de una aplicación desplegada desde `appBase` borra su `docBase` **y** su
`.war`.

- **Entonces recuperar ese despliegue son DOS pasos, y en orden**: primero **Joel** borra el
  descriptor (a Claude le está denegado borrar fuera del proyecto), y después **Claude vuelve a
  copiar el `.war`**. Borrar solo el descriptor deja la aplicación caída — y es el error natural,
  porque la vez anterior alcanzó con borrarlo.
- **El diagnóstico son dos `ls`**: `conf/Catalina/localhost/` (¿volvió el descriptor?) y `webapps/`
  (¿está el `.war`?). Dicen en un segundo si falta una cosa, la otra, o las dos — y evitan suponer
  que el síntoma (`404`) tiene siempre la misma causa que la última vez.
- **Un `404` que "se arregla solo" en minutos no se arregló: cambió de dueño.** Acá el 401 volvió a
  los 3 minutos porque para entonces la servía el descriptor del IDE, no `webapps`. Si el síntoma se
  va sin que nadie haga nada, hay que preguntarse **quién** la está sirviendo ahora, no dar por
  cerrado el problema.

## Un `catch` que devuelve vacío convierte "lo escribiste mal" en "todavía no escribiste" (B161, 14/11/2026)

En *"Nueva solicitud"*, el aviso en vivo no decía **para quién** era la solicitud, y con una cédula
inexistente se quedaba **mudo** hasta el POST. La causa no estaba en el texto sino en el **orden**:
el endpoint parseaba monto y plazo **antes** de resolver la persona, así que con el plazo todavía a
medio escribir el `RuntimeException` salía por el `catch` general y la respuesta era cadena vacía —
indistinguible de *"faltan datos"*.

- **Es la familia de B42** (`if (algo == null) return;` dentro de un control): un `catch` ancho que
  devuelve el valor neutro **borra la diferencia** entre tres situaciones distintas (no escribió
  nada / escribió algo que no existe / escribió todo y está mal). Al leer un endpoint que alimenta
  un aviso en vivo, preguntarse **cuántos estados distintos tiene que poder decir** y si el código
  puede distinguirlos.
- **El orden es la mitad del arreglo**: primero se resuelve **lo que identifica** (la persona),
  después se parsean los números, y cada parseo con su propia red. Así el peor caso es "sé quién es
  pero todavía no puedo calcular", que es justo lo que el usuario necesita ver.
- **Un endpoint que alimentan dos pantallas se amplía POR RAMA, no en el texto común**: acá la rama
  de la cédula (el alta) agrega el nombre del cliente y dos estados nuevos, y la rama del id (la
  ficha) devuelve **exactamente** lo de antes — en la ficha el cliente ya tiene su tarjeta y
  repetirlo sería decirlo dos veces (B123). Así no hay que tocar el segundo consumidor ni arriesgar
  su texto ya probado.
- **Y los estados nuevos viajan en el protocolo que ya existe** (un primer segmento que el JS lee
  para elegir la clase), nunca como frases que el navegador redacte (B92). El JS gana un `if`, no
  un vocabulario.

## Una regla partida entre el servicio y un ternario del controlador miente (B167, 14/11/2026)

El orden del listado de solicitudes tenía la regla del **sentido** en dos lugares: el servicio
("desc" si lo piden, ascendente en cualquier otro caso) y un ternario del controlador (sin columna
pedida, "desc"). Cada mitad, leída sola, parecía correcta. Juntas, llamar al servicio sin
parámetros —como lo hace cualquier test, y como lo haría otro llamador mañana— ordenaba **al
revés** del orden de siempre.

- **Lo agarró el JUnit en la primera corrida**, y por el motivo que vale: el test ordenaba *como la
  pantalla*, pasando los parámetros **crudos** del pedido por las reglas del servicio, no valores
  ya resueltos. Un test que le pasa al método el resultado de la regla no prueba la regla.
- **El arreglo es mover la regla entera al servicio** (`ordenAplicado` / `sentidoAplicado`, puras y
  públicas) y dejar al controlador pasando `request.getParameter(...)` sin decidir nada. La JSP lee
  lo aplicado de dos atributos, así que los tres —servicio, controlador y pantalla— dicen lo mismo
  porque **sale del mismo lugar**.
- **Y esto cuenta como "verificado contra una violación"** sin romper nada a propósito: el control
  nació fallando sobre un defecto real. Cuando eso pasa, se anota así en el ítem — es mejor
  evidencia que una rotura inventada.

## Usar un fragmento compartido N veces en una pantalla: `<jsp:include>` y atributos opcionales (B183, 15/11/2026)

Formalización necesitó **tres** paginaciones en una sola pantalla (sus 3 secciones). Dos cosas que
no se ven venir y que valen para cualquier fragmento que haya que repetir:

- **Con `<%@ include %>` (estático) la segunda copia NO COMPILA.** El fragmento se traduce dentro
  del mismo `_jspService`, así que sus variables se redeclaran. Va con **`<jsp:include>`**
  (dinámico), que compila cada copia en su propio servlet. Los datos se le pasan igual, por
  `request.setAttribute` **justo antes** de cada include.
- **Lo que el fragmento necesita saber se agrega como atributo OPCIONAL, con el comportamiento de
  hoy por defecto.** Acá fueron dos (`paginacionParamPagina` y `paginacionConListadoVivo`): las
  otras cuatro pantallas que lo usan no cambiaron ni una línea, y eso se puede afirmar leyendo el
  default, sin probarlas de nuevo. Un fragmento compartido se amplía **hacia atrás compatible** o
  se convierte en el próximo problema de otro.

**Y la decisión de fondo, que es la que importa**: hacer que `listado-vivo.js` entendiera tres
paginaciones era posible (manda `pagina=N` fijo, línea 82) pero significaba tocar un script del que
dependen **Clientes, Solicitudes, Aprobaciones y Mora**, las cuatro ya probadas en pantalla, en
víspera de entrega. Se eligió que los enlaces de página de **esta** pantalla naveguen de verdad
—igual que ya hacen los encabezados que ordenan en todo el sistema— y dejarlo **escrito en la fila
y en el comentario del fragmento**.

- **Cuando la forma "consistente" exige cambiar una pieza compartida ya probada, la pregunta no es
  "¿se puede?" sino "¿cuántas pantallas vuelvo a poner en riesgo?"**. Acotar el cambio a la
  pantalla nueva y **decir por qué** vale más que una consistencia que obliga a reprobar cuatro.
- **Pero se dice, no se esconde**: la inconsistencia queda anotada con su motivo, así el día que
  aparezca una quinta pantalla con el mismo problema la decisión se puede revisar con los datos a
  la vista, en vez de descubrirse como una rareza.

## Una verificación del humo sobre un literal GENÉRICO sobrevive a todo y no prueba nada (B183, 15/11/2026)

Barriendo el humo para ver qué rompía la paginación apareció esto, que no era de B183:

```bash
verificar_texto "GET /formalizacion (R3 en la sección de cierre documental pendiente)" "CC-2026"
```

Dice que verifica que **R3** está en la sección 3, y lo que busca es `"CC-2026"` — el prefijo que
comparten **todas** las operaciones del año. De las 20 filas de la página 1, las 20 lo tienen: esa
verificación **no se rompe ni aunque R3 desaparezca**. Es la familia de B113 y B154 (pasar por el
motivo equivocado), con una cara nueva: no es un `registrar` que falta ni un escenario mal armado,
es **la cadena elegida**.

- **Al grepear el humo por una pantalla que se va a cambiar, mirar QUÉ CADENA usa cada
  verificación, no solo si va a romperse.** Una que no se rompe puede ser una que nunca midió nada.
- **La prueba rápida**: preguntarse *"¿esta cadena estaría igual si el registro de esta corrida no
  existiera?"*. Si la respuesta es sí, la verificación mide "hay alguna fila", no "está la mía".
- **Lo que sirve es un identificador de esta corrida** (`$IDENTIFICADOR_OPERACION_R3_E9`,
  `SOL-2026-0312`), no un prefijo, un año ni un nombre que el humo repite en cada pasada — es la
  misma elección que pide la regla de las verificaciones por ausencia.
- **Y no se arregla de paso**: el guion es de Joel. Se anota con la línea exacta y el reemplazo
  propuesto, y lo decide él. (El `deny` de `settings.local.json` es `Bash(pruebas/humo_http.sh*)`:
  bloquea **correrlo**, no editarlo. Con su aprobación se edita; correrlo sigue siendo de él.)

**Y al aplicar el reemplazo, verificar DÓNDE se asigna la variable** (15/11/2026). El arreglo
obvio era cambiar `"CC-2026"` por `"$IDENTIFICADOR_OPERACION_R3_E9"`… y esa variable se asignaba
**seis líneas más abajo** que la verificación. Hecho a ciegas, el texto buscado habría sido **la
cadena vacía**, que `verificar_texto` encuentra siempre: una verificación que **no puede fallar
nunca**, peor que la que se venía a arreglar.

- En un guion de 5.000 líneas, `$VARIABLE` no garantiza nada: se busca su **asignación** y se
  confirma que está **antes**. Un `grep -n` del nombre y mirar el número de línea cuesta diez
  segundos.
- Si hay que moverla, se mueve **la asignación hacia arriba** (si es de solo lectura y sus
  precondiciones ya se cumplen), no la verificación hacia abajo: abajo suele haber un POST que
  cambia el estado que la verificación quería ver.
- Y se deja escrito en el guion por qué está ahí arriba, para que el próximo que la mueva sepa qué
  rompe.

## Un test de redacción tiene un agujero por cada FORMA de escribir el defecto (B178, 14/11/2026)

El cartel nuevo de anular decía **"2.000.000 Gs.."**, el punto duplicado de D-145 punto 8 — el
mismo defecto para el que existe `TextosDePantallaTest` desde B79. **Y el test estaba en verde**:
997/997, y lo encontró Joel en pantalla.

La causa es que el defecto tiene **tres formas de escribirse** y el test cubría dos:

| Forma | Cómo se ve en el fuente | ¿La veía el test? |
|---|---|---|
| Concatenación en Java/JS | `comoGuaranies(x) + "."` | Sí (`PUNTO_PEGADO_AL_MONTO`) |
| Literal escrito a mano | `"… Gs.."` | Sí (busca `Gs..`) |
| **Expresión de JSP + marcado** | `<%= comoGuaranies(x) %>.` | **No** |

La tercera es invisible para las otras dos redes y por un motivo que conviene tener presente:
**los dos puntos no están juntos en el fuente**, se juntan al **renderizar**. Un test que lee el
código fuente solo puede encontrar lo que está escrito, no lo que se arma.

- **Al cerrar un ítem con un test de redacción, preguntarse "¿de cuántas maneras se puede escribir
  este defecto?"** y cubrir cada una con su red. La pregunta no es "¿está cubierto?", es "¿está
  cubierto **en la capa donde yo lo escribí**?". Las capas de este proyecto son tres: Java, el JS
  del navegador y el **marcado** de la JSP.
- **Y revisar el patrón, no solo su existencia**: el de B79 era `\([^()]*\)`, que **no** tolera un
  paréntesis adentro, así que `comoGuaranies(prestamo.getMontoAprobado())` —la forma más común en
  una pantalla— nunca matcheaba. Un patrón que solo ve el caso simple da la misma falsa
  tranquilidad que no tenerlo. Se endureció a un nivel de anidado, con **0 casos preexistentes
  antes y después** (se mide antes de tocarlo: si aparecen casos viejos, eso es un hallazgo para
  Joel, no un arreglo de paso).
- **La red nueva se verifica contra el defecto vivo**: se escribe el test **antes** de corregir el
  texto, se corre y tiene que **fallar nombrando el archivo y el fragmento**. Es la mejor evidencia
  que hay y no cuesta nada, porque el defecto real ya está ahí (igual que B167 y B177).
- **Un barrido del patrón nuevo sobre todo `src/main` antes de cerrar** dice si el defecto estaba
  en un solo lugar o en varios. Acá dio **1**: el mío.

**La red se cobró la primera pieza al día siguiente** (15/11/2026, B188): escribiendo el cuerpo de
una ventana nueva salió *"faltante de 50.000 Gs.."*, el mismo punto duplicado, y lo frenó `mvn test`
antes de que llegara a ninguna pantalla. Vale anotarlo porque es la prueba de que la regla general
—cubrir **cada capa** donde el defecto se puede escribir— es lo que sirve: el comentario de B79
llevaba meses escrito en los archivos vecinos y no frenó nada.

**Y al día siguiente apareció la CUARTA forma** (15/11/2026, B192) — y esa **tampoco** cerró el patrón: la **quinta** salió esa misma noche con B215 (ver su sección más abajo, con la tabla completa de las cinco). Dar un patrón por cerrado es lo que deja la red con un agujero:

| # | Forma | En el fuente | La atrapó |
|---|---|---|---|
| 1 | Concatenación en Java/JS | `comoGuaranies(x) + "."` | B79 |
| 2 | Literal a mano | `"… Gs.."` | B79 |
| 3 | Expresión de JSP + marcado | `<%= comoGuaranies(x) %>.` | B178 |
| 4 | **Getter de un DTO ya formateado** | `<%= fila.getMontoAplicado() %>.` | B192 |

La cuarta se escapa porque **el formateo ocurre en otra capa**: un servicio le pasa
`comoGuaranies(...)` al DTO, que guarda el `String`, y la JSP solo imprime el getter — el nombre del
formateador **no aparece** en la JSP. La lección general, que vale para cualquier regla de
redacción:

- **Preguntarse dónde OCURRE la operación que el test busca, no dónde se ve el síntoma.** Si el
  valor puede llegar ya formateado desde un servicio, un DTO o la base, el test que lee la pantalla
  nunca lo va a ver.
- Cuando ya no se puede reconocer la operación, se reconoce **el dato**: acá la red nueva mira el
  **nombre del getter** (que contenga Monto, Importe, Saldo, Cuota, Vuelto, Efectivo, Interés,
  Capital, Mora o Deuda) y exige que la expresión **termine** en esa llamada, para no marcar un
  `getCantidadTotal() == 1 ? "" : "s"`, que es un contador. **Eso es una heurística y se dice en el
  javadoc**, junto con qué hacer si marca un falso positivo.
- **Y se mide antes de darla por buena**: barrido de las **46** expresiones de JSP seguidas de punto
  que hay en `src/main` → marca **1** (el defecto) y ninguna de las 45 legítimas. Un patrón nuevo
  sin ese número es una promesa, no un control.

## Mover un formulario a una ventana es BORRAR el viejo, no encerrarlo en un `if (false)` (B192, 15/11/2026)

Al pasar la solicitud de reversión a una ventana, el primer intento dejó el formulario original
dentro de un `<% if (false) { %>` para "no perderlo". Eso es **un parche**, justo lo que prohíbe
`REGLAS_DESARROLLO.md` §13: marcado muerto que compila, que el próximo lector no sabe si importa, y
que un día alguien reactiva. Se rehízo sacando el bloque entero.

- **La versión vieja ya está guardada: la guarda git.** Un `if (false)`, un bloque comentado o un
  `.jsp.bak` no conservan nada que `git show HEAD~1 -- <archivo>` no dé mejor.
- Es la misma regla que B111 (*"mover una pieza es borrar la vieja, no dejarla"*) y la que B122
  aplicó al borrar el faltante duplicado de la JSP: **agregar y borrar van en el mismo cambio.**
- **Cómo se nota al revisar el diff propio antes de pedir el OK**: si el diff AGREGA un bloque y no
  QUITA ninguno, en un ítem que dice "mover", algo quedó de más.

## Un texto que viene de la BASE no lo puede cubrir un test que lee `src/main` (B188, 15/11/2026)

Al día siguiente de cerrar el agujero del `"Gs.."`, el mismo punto duplicado volvió **por otra
puerta**: el cuerpo de una ventana nueva pegaba un `"."` después del **motivo que había escrito la
cajera**, y ese motivo ya terminaba en punto (*"…a propósito.."*). Lo encontró Joel en pantalla, con
`mvn test` en verde.

- **Las tres redes de `TextosDePantallaTest` leen `src/main`**: pueden ver un literal mal escrito y
  una concatenación, nunca lo que llega de la base en tiempo de ejecución. **Al pegar algo a un
  dato que escribió una persona, el test no es la red: el dueño del texto lo es.**
- La forma que quedó: una función pura con su JUnit —`FormateadorTexto.conPuntoFinal`— que agrega el
  punto **solo si falta** (y respeta `.`, `!`, `?`, `…`, `:`; ignora los espacios del final; con null
  o vacío devuelve vacío, porque un texto que no existe no se cierra con un punto suelto). El borde
  que hay que probar es **el texto ya cerrado**, que es justo el que nadie escribe en los datos de
  prueba.
- **El humo tampoco lo habría encontrado**, y vale medirlo antes de confiar en él: sus motivos son
  *"Faltante detectado en la prueba de humo"*, sin punto final. Un `SELECT` de la columna
  (`right(motivo, 1)`) dice en diez segundos si los datos de prueba ejercitan el borde o no.
- **Y la pregunta general, para cualquier texto armado**: *¿de dónde viene cada pedazo?* Si alguno
  viene de la base o del usuario, la regla de redacción va en una función con test, no en la JSP; si
  los dos son fijos, alcanza el test de textos. Es la misma familia de B103/B117 (el pedazo fijo es
  una suposición sobre todos los valores posibles del que varía), con el valor viniendo de afuera.

## El título del cartel es el lugar seguro cuando un script reescribe el cuerpo (B187, 15/11/2026)

El cartel de cerrar caja tenía que decir **de qué día** es la caja. El cuerpo (`data-confirmar`) era
el lugar natural… y es justo el que **`caja/ficha.jsp` reescribe en cada tecla** con
`setAttribute('data-confirmar', …)` para mostrar el efectivo contado y la diferencia: cualquier dato
fijo puesto ahí se habría borrado al primer carácter. Fue al **título**
(`data-confirmar-titulo`), que no lo toca nadie.

- **Antes de agregar un dato a un cartel, buscar quién más escribe ese atributo**
  (`grep -n "data-confirmar" sobre la JSP y su script`). Si hay un script que lo arma, el dato fijo
  va en el título, o se lo pasa al script por un `data-` propio para que lo intercale (B92).
- Es la misma familia que "un dato, un dueño", aplicada a un atributo: **dos escritores sobre el
  mismo atributo y gana el último**, que casi nunca es el que uno está mirando.

## Dentro de un scriptlet, `<%-- --%>` no es un comentario: es un error de compilación (B181, 14/11/2026)

Agregando una declaración al bloque `<% … %>` de arriba de `formalizacion/_resultados.jsp` se
escribió el comentario que explica de dónde sale el dato con la sintaxis de comentario **de JSP**.
Adentro de un scriptlet eso es **Java**, y `<%--` no compila. Es la misma familia que el `--` de los
comentarios XML: un detalle de sintaxis que se paga con un build.

- **La regla**: en un `<% … %>` (declaraciones, lógica) el comentario va con `//` o `/* */`; el
  `<%-- --%>` va **afuera**, en el marcado. Si el comentario explica una **variable**, vive adentro
  con `//`; si explica un **bloque de marcado**, vive afuera con `<%-- --%>`.
- **Lo atrapa `mvn test`** (el `jspc` corre en `process-classes`), igual que el import faltante de
  B169 — pero releer la línea recién editada lo agarra **antes**, y cuesta diez segundos. La regla
  de las rutas de Windows vale igual acá: *un documento (o una JSP) no se da por escrito porque la
  herramienta no haya fallado*.

## Satisfacer un ítem de UX AGREGANDO deja el humo en verde; reemplazando, lo rompe (B181, 14/11/2026)

El ítem pedía que *"Incompleto"* dijera **qué** falta. Había dos formas: cambiar la etiqueta por el
detalle, o **dejar la etiqueta y poner el detalle debajo**. La segunda cumple lo mismo y además
conserva `verificar_texto … "Incompleto"` (`pruebas/humo_http.sh:2273`) en verde, sin tocar el
guion de Joel ni gastar una corrida.

- **Antes de elegir cómo redactar un ítem de pantalla, grepear el humo por el literal que está hoy**
  y preguntarse si el cambio puede ser **aditivo**. Es la cara constructiva de la regla de B43 (una
  verificación por presencia se rompe ruidosamente): si se puede no romperla, mejor.
- **Y no es solo por el humo**: la etiqueta da el estado de un vistazo y el detalle responde "qué
  falta" — las dos cosas se leen distinto, así que agregar es también la mejor pantalla. Cuando las
  dos razones apuntan al mismo lado, no hay nada que decidir.
- **El límite**: si el texto viejo **miente** (no es el caso acá), se reemplaza y se avisa la falla
  predicha con la línea exacta, como manda B175. "Aditivo" nunca es excusa para dejar un texto falso
  al lado del bueno.

## Una JSP que empieza a usar una clase necesita su propio `import` (B169, 14/11/2026)

`cabecera.jsp` trae algunos de los ayudantes comunes y otros no: al agregarle a
`solicitudes/nueva.jsp` las ayudas de los límites, `FormateadorFechas` compiló (viene de la
cabecera) y `FormateadorNumeros` y `ServicioFormalizacion` **no**. No hay forma de saberlo de
memoria, y tampoco hace falta:

- **Lo atrapa `mvn test`** con el número de línea exacto (`An error occurred at line: [48] in the
  jsp file: [/WEB-INF/vistas/solicitudes/nueva.jsp]`), porque el `jetty-jspc-maven-plugin` corre en
  `process-classes`. Es exactamente para lo que está en el build: sin él, eso aparecía como un 500
  en la cara de Joel.
- **El `BUILD FAILURE` del `jspc` no dice "falta un import" en la línea del `[ERROR]`**: hay que
  mirar unas líneas más arriba, en el `[WARNING] Compilation error`, donde están los números de
  línea. Conviene grepear `"An error occurred at line"` directamente.
- **Y los imports se copian de la pantalla de la que se copió el marcado** (acá el Cotizador), con
  los mismos nombres y en el mismo estilo: si dos JSP hacen lo mismo, que no difieran ni en eso.

## El sentido descendente también invierte los nulos (B175, 14/11/2026)

El comparador de un listado se escribe comparando y, al final, invirtiendo el resultado si el
sentido es descendente (`resultado = -resultado`). Con eso, **el criterio de "los nulos al final"
se invierte junto con todo lo demás**: en descendente, la fila a la que le falta ese dato pasa a
encabezar la lista. El javadoc del helper decía *"nulos al final, en los dos sentidos"* y el código
cumplía solo en ascendente — **es la trampa de C20**, un texto que promete una regla que la
condición no verifica, esta vez en un comentario propio escrito el mismo día.

- **El sentido se aplica ADENTRO de la comparación, con los nulos afuera de la inversión**: primero
  se resuelve "¿alguno es nulo?" (y ese va al final, sin mirar el sentido), y recién con los dos
  valores presentes se compara y se invierte.
- **Sale más limpio con un extractor**: un método que devuelve el **valor** por el que se ordena
  (`Comparable`, o null si esa fila no lo tiene) y un solo lugar que compara. Con un `switch` que
  devuelve `int` hacen falta dos `switch` —uno para el valor y otro para la nulidad— y el día que se
  agrega una columna se actualiza uno solo.
- **El JUnit que lo encuentra es el que prueba el nulo en los DOS sentidos.** El de B167 tenía 19
  casos y ninguno así, y por eso el mismo defecto estaba vivo en Solicitudes sin que nadie lo
  viera: un listado con 19 pruebas en verde puede tener la mitad de un criterio sin probar. Al
  ordenar, los casos que valen son: nulo en ascendente, nulo en descendente, y el desempate entre
  dos filas iguales. (Se corrigió el mismo día como **B177**, con la misma forma, y las dos pruebas
  nuevas se verificaron **contra el código de `HEAD`** —fallan ahí, pasan con el arreglo— en vez de
  contra una rotura inventada.)
- **Y si el defecto está en código ya cerrado de OTRA pantalla, no se mezcla en la tanda**: se
  registra con su número y se le pregunta a Joel, aunque el arreglo sea de tres líneas. Acá dijo que
  sí enseguida, pero la decisión era suya: tocar un servicio ya probado en pantalla es ampliar el
  alcance de la tanda por cuenta propia.

## Paginar un listado cambia QUÉ filas se dibujan: hay que mirar el humo (B175, 14/11/2026)

Agregar paginación no es un cambio visual: la página 1 deja de tener todas las filas. Si el humo
verifica que **una fila concreta aparece** en un listado completo, esa verificación se rompe
cuando esa fila cae en la página 2 — y el resultado no es "la paginación está mal", es que la
verificación ya no describe la pantalla.

- **Antes de cerrar la tanda, grepear el humo por la ruta** (`grep -n "/aprobaciones" pruebas/humo_http.sh`)
  y separar las verificaciones en dos: las que entran con un **filtro** (`?texto=…`, un resultado,
  siempre en la página 1 — esas siguen bien) y las que piden el **listado pelado** esperando un
  texto de una fila. Esas últimas son las que hay que revisar una por una.
- **Y el orden decide si se rompe o no**: en Solicitudes el orden por defecto es la fecha
  descendente, así que la solicitud que el humo acaba de crear queda primera y la verificación
  sigue pasando; en la bandeja de Aprobaciones el orden es la fecha de envío **ascendente** (la que
  más espera, primero), así que la del humo queda **última** y con 40 pendientes cae en la página 2.
  El mismo cambio, el mismo número de filas, y un resultado distinto por el `ORDER BY`.
- **La corrección es del guion, pero la decide Joel** (`pruebas/humo_http.sh` es suyo, y además está
  en el `deny` de `settings.local.json`): se le dice **antes** de que corra el humo cuál
  verificación va a fallar y por qué, con la línea exacta y el arreglo propuesto (acá: pedir la
  misma pantalla con `?orden=enviada&sentido=desc`, que de paso ejercita el orden nuevo). Anunciar
  una falla predicha es muy distinto de explicarla después.
