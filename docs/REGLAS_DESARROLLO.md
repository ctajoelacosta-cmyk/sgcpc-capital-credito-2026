# Reglas de desarrollo — SGCPC (Sistema de Gestión de Créditos Personales y Cobranzas)

Reglas y convenciones de desarrollo del proyecto. Leer completo antes de empezar a trabajar.

## 0. Los dos dogmas (Joel, 27/10/2026) — por encima de todo lo demás

Estas dos reglas no se discuten, no se interpretan y no tienen excepción. Si algo de este
documento o de cualquier otro las contradice, mandan estas.

### DOGMA 1 — Estamos en producción: se prueba hoy, o no está hecho

**Ningún cambio se da por terminado, ni se dice que "funciona", sin una prueba del circuito real
de punta a punta que Joel pueda ver EL MISMO DÍA.**

- Está **prohibido** proponer "esperar a mañana", "cuando pasen unos días" o "cuando venza una
  cuota". Si el resultado depende de la fecha, se resuelve con una **prueba automática que no
  deje rastro** (una sola transacción con `rollback`) o con **fecha simulada** — siempre dentro
  de la base única del DOGMA 2.
- **"Verificado por código" no es "probado". "Vista previa" no es "probado".** Leer el código y
  concluir que debería andar no alcanza; una vista previa muestra el diseño, no prueba el
  circuito que lo produce.
- **Todo análisis de un cambio tiene que decir cómo se va a probar hoy.** Si no hay forma de
  probarlo hoy, **la primera tarea es crear esa forma**, antes del cambio.

Ejemplo de cómo se cumple: `PruebaEmisionFacturaPdf` (`src/test/java/.../servicios/`) emite una
factura con el código real y revisa el PDF, en una transacción que revierte — sin esperar a que
haya interés devengado por cobrar y sin consumir numeración (ver `funcionamiento/RF23_recibo_factura.md` §9).

### DOGMA 2 — UNA SOLA BASE DE DATOS

**La única base es la de `bd.properties`: `sgcpc_db_pruebas`.** Es la que viaja con el `.war` y
la que usan Joel, el cliente, el docente y la mesa de la defensa.

- Está **prohibido crear, restaurar, copiar o apuntar a otra base**. Incluye
  `sgcpc_db_restaurada` y usar `SGCPC_BD_URL` (u otra variable de entorno) para apuntar a otra
  base. Trabajar contra una base paralela fue lo que rompió la entrega del 26/10/2026.
- Si hay que dejarla limpia, **se vacían los datos dentro de esa misma base y se mantiene la
  estructura** — nunca se crea una base nueva ni se recrea desde cero.
- Toda prueba automática que escriba corre contra esa base y revierte lo que hizo, y **aborta si
  la base conectada no es `sgcpc_db_pruebas`** (verificándolo contra la conexión abierta, no
  contra un archivo que alguien pudo mover).

#### Qué queda en conflicto con el DOGMA 2 (no borrar: Joel decide)

Estas piezas son de D-112/D-113, anteriores al dogma, y **hoy contradicen el DOGMA 2**. Quedan
listadas acá, sin borrarse, hasta que Joel decida qué hacer con cada una:

| Pieza | Qué hace | Por qué choca |
|---|---|---|
| `bd/restaurar_prueba.sh` | Restaura un respaldo **en otra base** (`sgcpc_db_restaurada` por defecto) | Crea/llena una segunda base |
| `bd/vaciar_prueba.sh` | Vacía `sgcpc_db_restaurada` con `DROP SCHEMA`/`CREATE SCHEMA` | Opera sobre la segunda base, y además borra la estructura |
| `bd/respaldar_prueba.sh` | Genera el respaldo que usa `restaurar_prueba.sh` | Solo lee; **no choca por sí mismo** — el respaldo sigue siendo necesario |
| Base `sgcpc_db_restaurada` (D-112) | Base de destino de las restauraciones de prueba | Es la segunda base |
| `bd/recrear_base.sh` | Recrea la estructura desde cero | No crea otra base, pero **recrea** en vez de vaciar los datos |
| `bd/comparar_esquemas.sh` | Compara el esquema de dos bases | Solo lee, pero presupone que existe una segunda base |

Nota para el futuro vaciado de datos (DOGMA 2): la base tiene **13 triggers** `BEFORE DELETE`
(y `BEFORE UPDATE` en `auditoria_operaciones`) que **rechazan el borrado** — ver §14.

### DOGMA 3 — Las skills se actualizan con cada ítem, no "cuando se junte"

**Las skills se actualizan al cerrar CADA ítem, en el mismo commit o el inmediato. Al empezar
una sesión, si la fecha de una skill es anterior al último ítem de `AVANCE.md`, se actualiza
antes de seguir.**

- Las skills son lo primero que se lee en cada sesión: una skill vieja no es documentación
  incompleta, es documentación **que miente**, y dirige el trabajo de la sesión entera.
- Aplica a las 4 skills propias del proyecto (`sgcpc-mapa`, `sgcpc-cambio-seguro`,
  `sgcpc-diseno-ui`, `sgcpc-reportes-jasper`), no a las externas (`impeccable`,
  `ui-ux-pro-max`).
- `.claude/` está en `.gitignore`: lo que se versiona es la copia de `docs/skills_respaldo/`.
  **Las dos se editan juntas y tienen que quedar idénticas** (confirmar con `diff`).
- Si al cerrar un ítem no hay nada que agregarle a ninguna skill, se dice explícitamente en el
  resumen ("ninguna skill cambia por este ítem") — no se omite en silencio.

## 1. Qué es este proyecto

Sistema web para **Capital Crédito**, una casa de crédito pequeña (un local, 3 usuarios) cuyo dueño prestaba dinero de manera informal y ahora se formaliza. Es además un **proyecto de tesis**: se defiende en el servidor del tutor.

- Analista y responsable: **Joel Acosta**. Él decide todo lo que no esté en la documentación.
- Los datos institucionales (razón social, RUC, timbrado, registros) son **ficticios** y parametrizables; se reemplazan por los reales después de la defensa.

## 2. Documentos que mandan (orden de prioridad)

**Lo primero que se lee, antes que todo lo de abajo**: si la tarea es sobre un RF o un proceso
(login, usuarios, buró, caja, cobranza, informes, etc.), `docs/funcionamiento/LEEME.md` (índice)
y el documento de ese RF/proceso, si ya existe — con la regla completa (leer antes de tocar,
actualizar en el mismo commit, crear el documento al cerrar un RF) escrita ahí mismo.

1. `docs/00_especificacion_maestra.md` — **fuente de verdad**. Reglas (§4), parámetros (§5), fórmulas (§6), estados (§7), procesos (§8), requisitos (§9), datos (§10), imprimibles (§11), pantallas (§12), reportes (§13), arquitectura y portabilidad (§14), estructura (§15), datos de prueba (§16), plan (§17), pruebas (§18), decisiones (§19.1).
2. `docs/08_modelo_datos.md` — diccionario de datos columna por columna (referencia; §10 del Doc. 00 prevalece).
3. `docs/10_interfaces.md` — pantallas (referencia; §12 del Doc. 00 prevalece).
4. `AVANCE.md` — estado actual del trabajo. **Leerlo siempre antes de empezar y actualizarlo al terminar.**

Leer solo las secciones necesarias para la tarea en curso (no todo el documento cada vez).

Cuando Joel cite un RF (RF01 a RF40), usar SIEMPRE `docs/RF_ANTEPROYECTO.md`. Esos textos están firmados y entregados: no se modifican. Si algo del sistema no coincide con un RF, avisar a Joel.

## 3. Tecnología (no cambiar sin aprobación de Joel)

| Capa | Tecnología |
|---|---|
| IDE | NetBeans (proyecto Maven, tipo Java Web) |
| Lenguaje | Java 8 (`maven.compiler.source/target = 1.8`), Servlets/JSP con `javax.*` |
| Servidor | Apache Tomcat **9** (no 10: usa `jakarta.*` y es incompatible) |
| Base de datos | PostgreSQL, driver JDBC 42.7.1 |
| Arquitectura | MVC: JSP → Servlet (controlador) → Servicio → DAO (JDBC con SQL parametrizado) |
| Reportes | JasperReports **6.20.6** (+ Groovy 3.0.22 si hace falta en expresiones) |
| Contraseñas | jBCrypt 0.4 |
| Interfaz | AdminLTE 3, Bootstrap 4, jQuery, SweetAlert2, Font Awesome — **archivos locales dentro del .war, sin CDN** |
| Pruebas | JUnit 4.13.2 |

Prohibido agregar: Spring, Hibernate/JPA u otros ORM, frameworks JS (React, Angular, Vue), otros motores de PDF, microservicios.

Todo cambio visual debe seguir `docs/GUIA_VISUAL.md`.

Paquete base: `py.com.capitalcredito.sgcpc`. Nombres del dominio **en español** y descriptivos (el mismo concepto con el mismo nombre en tabla, clase, pantalla y reporte).

## 4. Regla de entrega (MUY IMPORTANTE)

La defensa se hace en un **Ubuntu Server con Tomcat 9 y PostgreSQL** del tutor. Solo se llevan **dos cosas**: `sgcpc.war` y la **copia de la base de datos**. Nada puede quedar fuera de ellas:

- Archivos subidos (cédulas, documentos firmados) y PDF emitidos → **dentro de PostgreSQL** (tabla `archivos_binarios`, columna BYTEA). Nunca en carpetas del disco ni dentro del .war.
- Plantillas de reportes `.jrxml` y `.jasper` → `src/main/resources/reportes/` (viajan en el .war).
- Todas las librerías → `WEB-INF/lib` del .war (scope compile). Nada en la carpeta `lib` del Tomcat.
- CSS, JS, íconos, fuentes → dentro del .war. Sin internet.
- Fuentes de los PDF → extensión de fuentes de JasperReports dentro del .war (DejaVu Sans). No usar Arial ni fuentes de Windows.
- Conexión a la base → `src/main/resources/configuracion/bd.properties` (url, usuario, contraseña). **No usar DataSource JNDI** del Tomcat.

Compatibilidad Windows (desarrollo) ↔ Ubuntu (defensa):

- Nombres de archivos en **minúscula**, sin espacios ni tildes. Linux distingue mayúsculas.
- Rutas con `/` y relativas al classpath (`getResourceAsStream`). **Nunca** rutas absolutas ni `C:\`.
- UTF-8 en todo (fuentes, JSP, base, respuestas HTTP, `pom.xml`).
- Fijar `java.awt.headless=true` al arrancar la aplicación (ServletContextListener).
- Scripts SQL compatibles con PostgreSQL 12 o superior. La base `sgcpc_db` y el usuario `sgcpc_user` se crean una vez a mano en pgAdmin (no hay script para eso: el usuario de la aplicación no tiene permiso para crear bases ni roles); todo lo de **adentro** (tablas, índices, protecciones, parámetros) se crea **siempre desde los scripts** `bd/01..04`, para poder recrearlo en cualquier PC o servidor. Un backup de pg_dump 18 en formato custom no se restaura en versiones anteriores: por eso se entrega también el SQL plano y los scripts.

## 5. Reglas de negocio que nunca se rompen

- **Motor financiero único:** toda fórmula (cuota, IVA, mora, aplicación de pagos, cancelación, anticipo, capacidad, tope de tasa) vive **solo** en `ServicioMotorFinanciero`. JSP, Servlets, DAO, SQL y reportes **no calculan importes**.
- Dinero con `BigDecimal` (nunca `double`/`float`); redondeo `HALF_UP` a guaraní entero en cada importe guardado o mostrado; columnas `NUMERIC(18,0)`.
- **Formato de importes en guaraníes (D-44, AVANCE.md — regla automática desde E3, no hace falta que Joel la repita):**
  - Todo campo de formulario donde se escribe un monto en guaraníes lleva la clase CSS `campo-guaranies` (input `type="text"`, nunca `type="number"`): `recursos/js/campo-guaranies.js` (un único script, ya cargado en `plantilla/pie.jsp`) lo formatea con puntos de miles mientras se escribe y muestra debajo, en gris, el monto en letras (pide el texto a `GET /utilidades/letras`, que requiere sesión). Del lado del servidor ese valor se interpreta con `ConversorNumeros.aBigDecimalGuaranies` — el punto es separador de miles, **nunca** decimal (los guaraníes no llevan decimales) — y nunca con `aBigDecimal`/`aEntero` (esos son para porcentajes y otros números, donde la coma sigue siendo el decimal).
  - Todo importe en guaraníes que se **muestra** en pantalla (tablas, fichas, resultados, panel, parámetros) se formatea con `FormateadorNumeros.comoGuaranies`, nunca como número plano. Los importes principales (monto del préstamo, cuota, total a pagar, saldo, monto cobrado, monto desembolsado) muestran además, debajo, el texto en letras.
  - `infraestructura/ConversorNumerosALetras` es la **única** fuente del monto en letras en todo el sistema (JSP, servlets y reportes la llaman a ella; nunca se duplica esa conversión, ni en Java ni en JavaScript). `comoLetras(monto)` da el texto solo ("tres millones"); `comoLetrasConGuaranies(monto)` le agrega el sufijo correcto ("tres millones de guaraníes" / "quinientos mil guaraníes").
  - Todo documento impreso (pagaré, contrato, factura, recibo, comprobantes, constancias) muestra sus importes con puntos de miles y el total en letras con el patrón "Son guaraníes: [ConversorNumerosALetras.comoLetras(monto)]".
- Nada financiero se borra ni se edita: se corrige con **reversión** autorizada. La aplicación no hace `DELETE` en tablas financieras; en `sgcpc_db` esto además está bloqueado con triggers `BEFORE DELETE` (ver `bd/02_indices.sql`), porque el usuario de conexión `sgcpc_user` es dueño de sus propias tablas y un `GRANT`/`REVOKE` no alcanza para quitarle privilegios a su propio dueño.
- Cobro, desembolso, reversión y cierre de caja: **una sola transacción** JDBC (con su auditoría) y **clave de idempotencia** (doble clic no duplica dinero).
- Una sola caja ABIERTA; una caja CERRADA no se modifica.
- Permisos validados en el **servidor** (filtros), no solo ocultando botones.
- Ningún valor configurable escrito en el código (tasas, topes, límites, datos de la empresa, timbrado): todo en `parametros_sistema` / `limites_tasa_usura` / `feriados`.
- **Política del anteproyecto: solo ingreso fijo comprobable; NO se exige garante ni codeudor**
  (retirado del sistema el 18/10/2026). Ninguna pantalla, PDF, prueba ni muestra debe mostrar
  codeudor con esa política. Independientes sin comprobante y desempleados: no califican.
- **Política de crédito (anteproyecto firmado), todo por parámetro, nunca hardcodeado**: edad
  22 a 71 (`credito.edad_minima`/`credito.edad_maxima`); zona Asunción y Central
  (`credito.departamentos_habilitados`); cuota hasta el 40 % del ingreso, sumando otras deudas,
  **sin excepción, en todos los pasos** (crear, simular, enviar a aprobación, formalizar —
  `credito.limite_afectacion`); monto 500.000 a 15.000.000 Gs. (`credito.monto_minimo`/
  `credito.monto_maximo`); plazo 3 a 24 meses (`credito.plazo_minimo`/`credito.plazo_maximo`);
  TEA 27 % (`credito.tea_empresa`), sistema francés, IVA 10 % sobre los intereses, sin
  comisiones ni gastos; primer vencimiento 20 a 45 días desde hoy
  (`formalizacion.dias_min_primer_vencimiento`/`formalizacion.dias_max_primer_vencimiento`),
  corrido al día hábil siguiente si cae en fin de semana o feriado, y bloqueado si ese
  corrimiento lo saca del rango; el interés se calcula por período mensual.
- **Circuito del mostrador (autorización del buró y formalización)**: la autorización de
  consulta al buró la **genera el sistema**, se imprime, el cliente la **firma en el momento** y
  se **carga en el momento** — si queda una sin cargar de un día anterior, se genera una nueva;
  "Reimprimir" usa el **mismo número** (para cuando el cliente firmó mal). En la formalización,
  sin los **3 firmados** (contrato, pagaré, plan de pagos) cargados **no hay desembolso**; las
  firmas incluyen la del cliente (y representante, en el contrato) y la de **quien atendió**.

## 6. Reportes e impresos (JasperReports)

- Todo lo que se imprime o se exporta a PDF se hace con JasperReports: factura, recibo, contrato, pagaré, plan de pagos, constancias, arqueo, cotización y reportes.
- Cada reporte = **dos archivos**: `nombre.jrxml` (diseño editable) + `nombre.jasper` (compilado con JasperReports 6.20.6). Ambos en `src/main/resources/reportes/`.
- **El sistema carga en tiempo de ejecución SOLO el `.jasper` compilado — nunca el `.jrxml`** (`GeneradorInformesPdf`/`ServicioFormalizacion.llenarYExportar` leen `/reportes/nombre.jasper` del classpath). El Maven del proyecto no compila `.jrxml` → `.jasper` en ningún paso (ni `mvn test` ni `mvn clean package`) — es un paso manual. **PASO OBLIGATORIO después de editar cualquier `.jrxml`, antes de comitear**: correr `herramientas/compilar_reportes.sh` (recompila los 41 reportes y actualiza `herramientas/reportes_jasper.sha256`, el hash de cada `.jrxml` al momento de compilar). Si se olvida, `mvn test` **falla** (`ReportesCompiladosTest`, que compara ese hash contra el `.jrxml` actual) — hallazgo de Joel en la reprueba del Lote 1 de la consolidación RF08-RF19 (11/10/2026): se editaron `pagare.jrxml`/`plan_pagos.jrxml` sin recompilar y el PDF servido seguía siendo el viejo, sin que nada lo avisara hasta entonces.
- **Al editar un solo `.jrxml`, compilar SOLO ese reporte** — `herramientas/compilar_reportes.sh <nombre>` (con o sin `.jrxml`, ej. `herramientas/compilar_reportes.sh informe_auditoria`), que no toca ningún otro `.jasper` ni ninguna otra línea del manifiesto. Hallazgo de Joel (13/10/2026, RF40/F3): el compilador de JasperReports no es reproducible bit a bit entre corridas (mismo `.jrxml`, bytes distintos en el `.jasper`), así que recompilar los 41 de una sola corrida deja los otros 40 con una diferencia que después hay que distinguir de un cambio real, y es un riesgo innecesario para reportes de RF ya probados (`contrato`, `pagaré`, `recibo`, etc. — ver §13). El modo "los 41" (sin argumento) sigue existiendo para cuando de verdad haga falta (ej. una actualización de la librería de JasperReports).
- **ANTES de crear o modificar cualquier reporte, preguntar a Joel** qué debe mostrar, cómo y de dónde salen los datos (consulta SQL dentro del .jrxml o datos enviados desde Java). No diseñar reportes por cuenta propia.
- Entregar el `.jrxml` completo (diseño, parámetros, campos, consulta/variables) para que Joel lo revise en Jaspersoft Studio (vistas Design, Source y Preview).
- El PDF emitido se guarda en PostgreSQL; las reimpresiones devuelven el guardado.

## 7. Forma de trabajo

1. Leer `AVANCE.md` y ubicar la etapa actual (plan de etapas E0–E10, Doc. 00 §17).
2. Trabajar **una etapa a la vez** y en pasos chicos. No adelantar etapas ni tocar módulos terminados sin necesidad.
3. Antes de modificar algo que ya funciona, explicar el cambio y pedir confirmación. Lo mismo si
   durante la implementación aparece algo que no estaba en lo aprobado (un caso nuevo, una
   validación distinta, un atajo): preguntar antes de decidir por cuenta propia, nunca ajustar el
   alcance "mientras tanto".
4. Después de cada paso: `mvn test` (nunca `mvn clean package`, ver §10) y que las pruebas pasen.
   Una etapa se cierra solo cuando cumple su criterio de "terminado" (Doc. 00 §17).
5. Si algo no está definido en la documentación: **no inventar la regla**. Detenerse, proponer opciones a Joel y registrar la decisión en `AVANCE.md` (y luego en Doc. 00 §19.1).
6. Al terminar la sesión: actualizar `AVANCE.md` (qué se hizo, qué se probó, qué falta, decisiones, problemas abiertos).
7. Explicar a Joel en español simple qué se hizo y cómo probarlo en NetBeans / navegador.
8. Commits de git en español, uno por paso terminado y probado.
9. **`bd/recrear_base.sh` (D-47, AVANCE.md): no correrlo salvo que haya cambios en `01_esquema.sql`, `02_indices.sql` o `03_parametros.sql`, y siempre avisar a Joel antes.** Borra los datos de prueba que él carga a mano y resetea usuarios/contraseñas. Para verificar algo sin tocar `sgcpc_db`: `mvn test` (compila, precompila las JSP y corre las pruebas JUnit, que no usan ninguna base) y, si hace falta mirar datos reales, consultas contra `sgcpc_db_pruebas` — **nunca contra `sgcpc_db`, ni de solo lectura (ver §10): esa base no se toca por ningún motivo.**
10. **Nunca escribir `<%`, `%>` ni `${` dentro de un comentario de un scriptlet JSP (D-49, AVANCE.md)**, ni siquiera para explicar un bug relacionado con esos caracteres: el compilador de JSP busca esas secuencias como texto plano, sin distinguir si están dentro de un comentario Java (`//` o `/* */`) — un `%>` ahí cierra el scriptlet a la mitad y rompe la página. `mvn test` ya falla si esto pasa (o si una JSP tiene cualquier otro error): el build precompila todas las JSP (`jetty-jspc-maven-plugin`, fase `process-classes`, que corre antes de `test` en el ciclo de Maven aunque no se llegue a `package`) — no hace falta `mvn clean package` para este chequeo.
11. **`pruebas/humo_http.sh` (D-52, AVANCE.md): paso obligatorio antes de avisarle a Joel que una etapa está lista para probar**, siempre que Tomcat esté corriendo (`http://localhost:8081/sgcpc`). Recorre por HTTP real las rutas de E0 en adelante (login, panel, cotizador, clientes/documentos/buró, usuarios, parámetros, utilidades, sesión) y compara el código HTTP obtenido contra el esperado; usa cédulas de prueba que empiezan con "9" para no mezclarse con los datos de Joel, y no crea usuarios del sistema ni cambia ningún parámetro real. Si algo da FALLO, corregirlo y volver a correr el script hasta que dé OK en todo — no alcanza con que compile y pasen las pruebas JUnit, que no tocan HTTP/JSP en tiempo de ejecución.
    - **Credenciales**: se leen de `~/.sgcpc_prueba` (`C:\Users\HP\.sgcpc_prueba`) — un archivo **local de Joel, fuera de la carpeta `sistema/` y del repositorio, que nunca se comitea ni se lee su contenido en voz alta/en pantalla**. Contiene dos líneas: `export SGCPC_USUARIO=...` y `export SGCPC_CONTRASENA=...`. El script lo carga con `source` solo si `SGCPC_USUARIO`/`SGCPC_CONTRASENA` no vienen ya exportadas en la sesión.
    - **Redespliegue antes de correr la prueba (procedimiento oficial de Joel)**: en NetBeans, **Undeploy → Clean and Build → Run**, en ese orden. El `docBase` de Tomcat para este proyecto (`C:\tomcat9\conf\Catalina\localhost\sgcpc.xml`) apunta directo a `target/sgcpc` — el mismo directorio que `mvn clean` borra y reconstruye; hacer *Undeploy* primero evita que el autodespliegue de Tomcat encuentre ese directorio a medio reconstruir (la causa de los `ClassNotFoundException`/404 intermitentes de D-48 y D-52).
    - **El `.war` de la API NO se despliega desde NetBeans (B154, 14/11/2026).** El *Undeploy* del
      paso anterior repliega **todo** lo que el IDE tiene en ese Tomcat, así que se llevaba también
      `/sgcpc-api` y después volvía solo el sistema: medido en
      `C:\tomcat9\logs\manager.2026-11-14.log` — `11:52:47 undeploy /sgcpc`, `11:52:52 undeploy
      /sgcpc-api`, `11:54:51 install + start /sgcpc`. Con la API caída, el humo da **38 fallas y
      182 verificaciones saltadas**, todas con el mismo síntoma (`404` donde se espera `401`), y
      ninguna es un defecto del producto. Desde ahora la API vive en `C:\tomcat9\webapps\`, donde
      la tiene el autodespliegue de Tomcat (`autoDeploy="true"`, `unpackWARs="true"`) y el IDE no
      la puede tocar — igual que `api_capital.war`, que ya estaba ahí, **y igual que se va a
      instalar en el servidor de la institución, que son dos `.war`**.
      - **Para actualizar la API**: Joel hace **Clean and Build** del proyecto `sgcpc-api` (nunca
        *Run*: eso reinstala el descriptor del IDE y vuelve a tapar el despliegue de `webapps`), y
        **el comando lo corre Claude**:
        ```bash
        cp "../sgcpc-api/target/sgcpc-api.war" /c/tomcat9/webapps/sgcpc-api.war
        # el autodespliegue tarda unos segundos; tiene que dar 401, no 404:
        curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/sgcpc-api/api/panel
        ```
      - **LA REGLA, en una línea (Joel, 14/11/2026): para actualizar el sistema web va SOLO
        *Clean and Build* + *Run* de SGCPC. NUNCA *Run* de SGCPC API — esa la actualiza Claude**
        con el `cp` de arriba. *Clean and Build* del proyecto de la API sí, cuando haga falta un
        `.war` nuevo: lo que no va nunca es el *Run*.
      - **Por qué, verificado el 14/11/2026 a las 15:04**: Joel hizo *Clean and Build + Run* de
        **los dos** proyectos y el *Run* de la API no solo volvió a instalar el descriptor
        (`15:07:46 deployDescriptor sgcpc-api.xml`) — antes, a las **15:04:45**, su *Undeploy*
        **borró de `webapps` el `.war` y la carpeta explotada** (`HostConfig.undeploy /sgcpc-api`;
        la carpeta `webapps/` quedó con fecha 15:04 y sin `sgcpc-api*`). O sea que un *Run* de la
        API **deshace B154 en los dos sentidos**: se queda con el contexto y se lleva el archivo.
        Eso explica el 404 que Joel vio entre las 15:04 y las 15:07, y por qué después volvió el
        401: para entonces ya la servía el descriptor del IDE, no `webapps`.
      - **Si vuelve a aparecer `C:\tomcat9\conf\Catalina\localhost\sgcpc-api.xml`**, es que alguien
        le dio *Run* a la API desde NetBeans: ese descriptor **gana** sobre `webapps`. Para volver
        al despliegue bueno hacen falta **las dos cosas, y en este orden**: (1) **Joel** borra ese
        archivo (o hace *Undeploy* del proyecto en NetBeans) — a Claude le está denegado borrar
        fuera del proyecto; (2) **Claude vuelve a copiar el `.war`**, porque el *Undeploy* se lo
        llevó. Borrar solo el descriptor deja la API caída.
      - **Después de cualquier Clean and Build del sistema**, el control de 10 segundos es el mismo
        `curl` de arriba: **401** significa que la API sigue arriba. Y si da **404**, mirar primero
        `ls C:\tomcat9\conf\Catalina\localhost\` y `ls C:\tomcat9\webapps\`: dicen en un segundo si
        el problema es el descriptor de vuelta, el `.war` borrado, o las dos cosas.
    - **Nunca correr `pruebas/humo_http.sh` mientras Joel está haciendo una prueba manual.** Solo cuando él lo pida explícitamente, y avisarle cuando termina. Los usuarios de prueba (gerente/oficial/cajera) son las **mismas cuentas reales** que usa Joel a mano — no hay forma de distinguir por los datos "esto lo hizo el script" de "esto lo hizo Joel", así que una corrida en paralelo con su prueba puede pisarle una acción en curso (D-75, incidente real: una corrida de humo abrió una caja de prueba mientras Joel probaba a mano en simultáneo, y una limpieza manual terminó cerrando esa sesión sin saber que Joel la estaba usando).
12. **Nunca cerrar, anular ni modificar por cuenta propia sesiones de caja, préstamos, solicitudes u otros datos que no se esté 100% seguro de haber creado en esa misma acción (D-75, AVANCE.md).** Si algo en la base parece huérfano o a medio terminar (p. ej. una caja abierta sin movimientos, un préstamo `PENDIENTE_DESEMBOLSO` colgado), **avisar a Joel y que decida él** — nunca darlo de baja ni cerrarlo por iniciativa propia, ni siquiera usando el flujo normal de la aplicación (POST por HTTP): el dato puede parecer "de una corrida de prueba interrumpida" y en realidad ser una sesión que Joel está usando en ese momento, porque las cuentas de prueba son compartidas (ver punto 11).
13. **`mvn clean package` reconstruye `target/sgcpc`, que es lo que sirve el Tomcat de Joel, y puede disparar un redeploy automático (D-76, AVANCE.md).** Por eso nunca se corre durante el desarrollo (ver §10): el Clean and Build (Undeploy → Clean and Build → Run en NetBeans) es siempre tarea de Joel.
14. **Ningún cambio de estructura (tabla, columna, índice, restricción, trigger, función) se hace a mano en pgAdmin, en ninguna base (D-113, AVANCE.md).** Todo cambio de estructura:
    1. Se escribe primero como un archivo nuevo en `bd/migraciones/` (mismo estilo que `E9_01_reversiones.sql`: envuelto en `BEGIN;`/`COMMIT;`, con un encabezado que explica qué cambia y por qué), y se aplica con él a `sgcpc_db` (avisando a Joel antes, como cualquier cambio sobre la base real).
    2. El mismo cambio se incorpora también a `01_esquema.sql`, `02_indices.sql` o `03_parametros.sql` (el que corresponda), para que una base nueva creada desde cero con `recrear_base.sh` quede igual — nunca una base "recreada" y una "migrada a mano" con estructuras distintas.
    3. Se corre `bd/comparar_esquemas.sh` (compara solo estructura, nunca datos, entre `sgcpc_db` y otra base) para confirmar que las dos rutas (migración sobre la base real / script de creación sobre una base nueva) terminan en la misma estructura, incluidos los **nombres** de restricciones, índices, etc. (una restricción sin nombre explícito en `CREATE TABLE` recibe un nombre autogenerado por PostgreSQL que puede no coincidir con el nombre explícito de una migración — pasó una vez con `reversiones_pago_check`, ver AVANCE.md).

## 8. Entorno de desarrollo (Windows 11, laptop de Joel)

**Regla: no se cambia nada global en la PC de Joel.** Tiene otros proyectos que usan JDK 22 y Tomcat 7. Prohibido: modificar variables de entorno del sistema (PATH, JAVA_HOME), instalar o desinstalar programas, tocar Tomcat 7, otros proyectos u otras bases de datos (en especial `proyecto_final`). Todo se trabaja dentro de la carpeta `sistema/`; en el día a día, la única base que se toca es `sgcpc_db_pruebas` (nunca `sgcpc_db`, ver §10).

El entorno del proyecto se activa **solo en la ventana actual** con los archivos de entorno:

- Terminal con Git Bash: cada comando empieza con `source ./entorno.sh && ...` (las variables no se conservan entre comandos).
- Joel (CMD): ejecutar `entorno.bat` en la ventana antes de usar `mvn` o `psql`.

| Herramienta | Versión / ubicación | Uso |
|---|---|---|
| JDK del proyecto | **Temurin 17.0.20.1** — `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` | Compilar (release 8), Maven, Tomcat 9 y NetBeans para este proyecto |
| JDK por defecto de la PC | Oracle 22.0.1 (en el PATH) | **No se usa ni se toca** |
| Maven | El de NetBeans 25 — `C:\Program Files\NetBeans-25\netbeans\java\maven` | Vía entorno.sh / entorno.bat |
| NetBeans | 25 (también está la 22) | Usar 25 |
| Tomcat del proyecto | **9.0.x en `C:\tomcat9`**, puertos **8081** (HTTP) y **8006** (apagado) | URL: http://localhost:8081/sgcpc |
| Tomcat 7.0.41 | De otros proyectos | **No se usa ni se toca** |
| PostgreSQL | **18.0**, servicio `postgresql-x64-18`, puerto 5432 — bin: `C:\Program Files\PostgreSQL\18\bin` | Base `sgcpc_db`, usuario `sgcpc_user` (dueño de la base, creados a mano en pgAdmin). Scripts compatibles con PostgreSQL 12+ |
| pgAdmin | pgAdmin 4 (en la carpeta de PostgreSQL 17) | Consultas visuales de Joel |
| Jaspersoft Studio | Carpeta "Jaspersoft"; versión a confirmar | Debe ser 6.20.x |
| Git | 2.55 | Control de versiones dentro de `sistema/` |

Base de datos de desarrollo: nombre `sgcpc_db`, usuario `sgcpc_user` (creados a mano en pgAdmin, no por script); contraseña en `bd.properties` (no subir contraseñas reales a git: usar `bd.properties.ejemplo` en el repositorio).
Primera tarea de E0: ejecutar `source ./entorno.sh` y verificar Java 17, Maven, psql 18 y Tomcat 9.

**Logs de Tomcat (esta máquina): `C:\tomcat9\logs\`**

| Archivo | Contenido |
|---|---|
| `catalina.<fecha>.log` | Log principal del servidor: arranque/parada, excepciones no atrapadas, y todo lo que loggean `getServletContext().log(...)` desde el código |
| `localhost.<fecha>.log` | Errores del host "localhost" (despliegue, contexto de la aplicación) |
| `localhost_access_log.<fecha>.txt` | Accesos HTTP (método, ruta, código de respuesta, IP) |
| `manager.<fecha>.log` / `host-manager.<fecha>.log` | De las aplicaciones de gestión de Tomcat, no de sgcpc |

## 9. Comandos útiles (Git Bash, desde `sistema/`)

```bash
source ./entorno.sh && mvn test                                            # compila, precompila las JSP y corre las pruebas (nunca toca ninguna base) -- NUNCA "mvn clean package", ver §10
source ./entorno.sh && mvn -Dtest=ServicioMotorFinancieroTest test         # solo motor financiero
source ./entorno.sh && mvn -Dtest=PruebaConexionBD test                    # confirma la conexion a sgcpc_db_pruebas (bd.properties debe apuntar ahí)
source ./entorno.sh && bash bd/recrear_base.sh                             # recrea tablas/indices/parametros en sgcpc_db_pruebas (se niega a correr sobre cualquier otra base)
```
Las líneas con `psql`/`pg_dump` (copia para entregar, restaurar, consultas directas) las corre
Joel — ver §10. Tomcat 9: se arranca y se despliega desde NetBeans (servidor "Apache Tomcat 9"),
o con `"$CATALINA_HOME/bin/startup.bat"`.

## 10. Reglas no negociables

1. **Nunca tocar `sgcpc_db`, ni siquiera leer** (ni `SELECT`, ni `psql`, ni `pg_dump`, ni ningún
   script apuntando a ella). Toda verificación, prueba o dato de ejemplo va contra
   `sgcpc_db_pruebas`. Si hace falta aplicar algo a `sgcpc_db` de verdad (una migración ya
   aprobada, un respaldo), se lo pide a Joel con el comando exacto para que lo corra él.
2. **Nunca delegar trabajo de este proyecto a un proceso automático sin supervisión directa** —
   todo el análisis, el código, las pruebas y la documentación se hacen paso a paso, revisando
   cada uno.
3. **Nunca correr `mvn clean package`** (ni `mvn clean install`, ni nada que reconstruya
   `target/sgcpc`) — ver §7 puntos 4, 9 y 13. Para compilar y probar: `mvn test`. El Clean and
   Build es siempre tarea de Joel, desde NetBeans (Undeploy → Clean and Build → Run).
4. **Preguntar antes de desviarse de lo aprobado** — ver §7 punto 3.
5. **Ningún archivo entregable (código, comentarios, documentación, nombres de archivo) dice
   con qué método o herramienta se programó o se escribió** — el entregable describe el
   sistema, no cómo se construyó.
6. **Todo en español**: comentarios, nombres de dominio (§3), mensajes y documentación nueva.

## 11. Flujo de trabajo

Para cualquier pedido de código (no aplica a preguntas o análisis puros):

1. **Analizar**: leer primero `docs/funcionamiento/` (§2) del RF/proceso que corresponde, y el
   código real.
2. **Aprobación**: esperar el OK de Joel antes de programar (salvo que ya lo haya dado para ese
   punto puntual).
3. **Código + test + documento**: implementar, agregar o actualizar las pruebas JUnit, y
   actualizar el documento de `docs/funcionamiento/` correspondiente — los tres en el mismo paso.
4. `mvn test` en verde antes de avisar que está listo.
5. Avisar a Joel: el Clean and Build lo hace él (nunca se corre como parte de este flujo, ver
   §10 punto 3).
6. Joel prueba en pantalla.
7. Commit (y tag, si cierra algo) — solo después de que Joel confirma la prueba, nunca antes
   (salvo que él pida explícitamente lo contrario para ese paso).

## 12. Textos de pantalla

Todo texto visible para el usuario (etiquetas, carteles, mensajes de error/éxito, botones,
placeholders, ayudas) sigue estas reglas:

1. **Siempre en usted, nunca en voseo.** "Complete", "elija", "registre", "genere", "cargue",
   "pruebe", "intente" — nunca "completá", "elegí", "registrá", "generá", "cargá", "probá",
   "intentá". Esto es sobre texto que ve el usuario del sistema (gerente, oficial, cajera,
   administrador) — no tiene nada que ver con cómo se escriben los comentarios del código.
2. **Los carteles llevan título corto y detalle.** Una frase corta como título (qué pasó) y,
   si hace falta, el detalle debajo (por qué, o qué dato puntual) — nunca todo junto en una sola
   frase larga. El mecanismo genérico de mensajes (`plantilla/pie.jsp`) ya separa automáticamente
   un texto escrito como "Frase corta. Detalle." en título (negrita) y detalle (debajo) — alcanza
   con escribir el mensaje así, sin armar el cartel a mano en cada pantalla.
3. **Botón según el tipo de cartel**: "Aceptar" en los carteles de éxito (`icon: 'success'`);
   "Entendido" en los de error o aviso (`icon: 'error'`/`icon: 'warning'`) que solo informan, sin
   pedir una decisión. Un cartel que sí pide decidir algo (confirmar una acción, elegir entre dos
   caminos) usa los botones que correspondan a esa decisión puntual ("Sí"/"Cancelar", "Seguir
   conectado"/"Salir", etc.), nunca "Aceptar" ni "Entendido".
4. **Todo proceso termina diciendo qué sigue.** Un mensaje de éxito no se queda solo en "se
   guardó" — dice qué hacer después (qué pantalla, qué botón, qué dato completar), salvo que ya
   sea obviamente el final del proceso (ej. un préstamo que queda `FINALIZADO`).

## 13. Etapa de consolidación: cambio mínimo y sin parches

El sistema está terminado; la etapa actual es de consolidación contra el anteproyecto firmado
(objetivo general, objetivos específicos y RF01–RF40).

1. Cambio mínimo: solo lo necesario para que un RF cumpla su texto o para corregir algo que
   miente o confunde. Cada cambio cita el RF u objetivo al que responde; si no responde a
   ninguno, se anota en Pendientes y no se hace.
2. Prohibido refactorizar (mover, renombrar, reescribir código que funciona) sin pedido expreso
   de Joel.
3. Reutilizar antes de crear: antes de un método, clase, archivo, parámetro o estilo nuevo,
   buscar si ya existe (`MensajesFlash`, `data-confirmar-boton`, `FormateadorNumeros`/
   `FormateadorFechas`, `ConversorNumerosALetras`, `ServicioAuditoria`, `GeneradorInformesPdf`,
   etc.). Todo archivo nuevo se justifica.
4. Respetar las capas: controlador (pedido/respuesta), servicio (reglas y transacciones), DAO
   (solo SQL), JSP (solo presentación).
5. Nada de soluciones "temporales": si la solución correcta no entra en el pedido, se dice y se
   anota en Pendientes.
6. Lo consolidado (tags `*-probado`) no se toca sin explicar el riesgo y pedir aprobación;
   después de cada cambio, `mvn test` en verde y, tras el Clean and Build de Joel,
   `pruebas/humo_http.sh` cuando él lo pida.
7. Cada respuesta termina con la lista de archivos tocados, marcando los nuevos y los de otros
   RF.
8. Método de trabajo ante cualquier duda o cambio que pueda afectar algo ya probado (pedido de
   Joel, 13/10/2026, hallazgo de los 11 `.jasper` con ruido de recompilación de RF40/F3):
   1) auditar qué es, de dónde viene y cuándo cambió; 2) entender qué depende de eso (pantallas,
   documentos, RF con tag `*-probado`); 3) explicar el riesgo y esperar la decisión de Joel;
   4) modificar lo mínimo; 5) verificar con captura el cambio y lo que depende de él. **Nunca
   restaurar, borrar ni reemplazar algo sin haberlo auditado antes.**

## 14. Triggers de la base (bloquean borrados) — verificado el 27/10/2026

La base **tiene 13 triggers**, y no están en `bd/01_esquema.sql` ni en `bd/migraciones/`: se
crean en **`bd/02_indices.sql`** (líneas 123-161). Que no estén donde uno los busca ya costó una
afirmación equivocada ("no hay triggers") en el análisis del 27/10/2026, así que quedan
anotados acá.

Confirmado con `SELECT … FROM pg_trigger WHERE NOT tgisinternal` sobre `sgcpc_db_pruebas`:

| Función | Qué hace | Dónde |
|---|---|---|
| `fn_bloquear_eliminacion()` | Lanza excepción ante cualquier `DELETE` | 11 tablas financieras: `prestamos`, `cuotas`, `planes_pago`, `pagos`, `aplicaciones_pago`, `desembolsos`, `reversiones_pago`, `movimientos_caja`, `sesiones_caja`, `facturas`, `liquidaciones_anticipadas` |
| `fn_bloquear_modificacion_auditoria()` | Lanza excepción ante `DELETE` **y** ante `UPDATE` | `auditoria_operaciones` (de solo inserción, Doc. 08 §8.5) |

Son **11 + 2 = 13**. Existen porque `sgcpc_user` es el dueño de las tablas y un `REVOKE` no le
puede quitar privilegios a su propio dueño (D-12): la regla "ninguna tabla financiera se borra"
se aplica con triggers en vez de con permisos.

Dos consecuencias prácticas:

1. **No escriben nada** (solo rechazan), así que no dejan rastro ni interfieren con una prueba
   que revierte con `rollback`.
2. **Un vaciado de datos con `DELETE` va a fallar** en esas 12 tablas. Cuando se diseñe el
   vaciado del DOGMA 2 (vaciar los datos manteniendo la estructura), hay que resolver esto —
   `TRUNCATE` no dispara triggers `BEFORE DELETE … FOR EACH ROW`, pero eso hay que **probarlo**
   antes de darlo por bueno (DOGMA 1), y decidirlo con Joel.
