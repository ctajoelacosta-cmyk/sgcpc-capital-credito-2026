# Revisión de los Requisitos No Funcionales (RNF01-18) contra el anteproyecto

**Estado: análisis del 13/10/2026, con pruebas en pantalla de Joel el mismo día para RNF03/04/
05/14 (ver cada fila).** Mismo criterio que la revisión de los RF (RF01-RF40): el texto de cada
RNF se copia exacto de `docs/RF_ANTEPROYECTO.md` (transcripción ya usada en todo el proyecto
como "el texto firmado" de `ANTE_PROYECTO_FINAL_JOEL_ACOSTA_v7.docx`, CLAUDE.md §2), y se
compara contra el código y la documentación real, no contra lo que se planeó.

A diferencia de los RF, ningún RNF tiene pantalla propia para "probarlo" de punta a punta — cada
uno vive dentro de uno o varios procesos ya probados, o es un requisito de infraestructura sin
código que lo pruebe en pantalla. Por eso esta tabla no tiene una columna de "tag" propia: cita
el tag del RF/proceso donde ya quedó probado, cuando corresponde.

## Tabla RNF01-18

| RNF | Texto (anteproyecto) | Cómo lo cumple hoy el sistema | Estado | Qué falta |
|---|---|---|---|---|
| RNF01 | Hardware del servidor: computadora del local con procesador Intel Core i5 o equivalente, 8 GB de RAM y disco SSD de 256 GB como mínimo. | Requisito de infraestructura física, sin ningún acoplamiento de código — el sistema no verifica ni depende de hardware específico más allá de lo que exige RNF03 (Java 8+, Tomcat 9, PostgreSQL). | No verificable desde el código. **Corrección (13/10/2026, Joel)**: el servidor real de la institución es **Windows Server** — Doc. 00 §14.6 asumía "Servidor Ubuntu Server del tutor", un supuesto de planificación que no se cumplió; el anteproyecto firmado permite los dos ("en Windows o Linux"), así que no hay conflicto con el texto firmado. | Confirmar las specs de hardware (i5/8 GB/SSD 256 GB) de ese Windows Server real; no es responsabilidad del software. |
| RNF02 | Hardware de los usuarios: PC del gerente, oficial y cajera, con 4 GB de RAM como mínimo, sin instalar ningún programa adicional. | Cumple por diseño: aplicación 100 % web (Servlet/JSP), sin applet, sin plugin, sin instalador de cliente — todo corre dentro de un navegador moderno (AdminLTE/Bootstrap/jQuery servidos desde el propio `.war`). | Cumple por diseño; no probado formalmente en una PC de 4 GB de RAM exactos. | Prueba puntual en una PC con 4 GB de RAM (prioridad baja — el único requisito real es el navegador). |
| RNF03 | Software del servidor: Java 8 o superior, Apache Tomcat 9, PostgreSQL 17, en Windows o Linux. | `pom.xml` (`maven.compiler.release=8`, `javax.servlet-api 4.0.1` para Tomcat 9), `web.xml` (`version="4.0"`), driver `org.postgresql:postgresql:42.7.1`; `EscuchaContextoAplicacion` fija `java.awt.headless=true` al arrancar (evita que se intente cargar un entorno gráfico en un servidor que no lo tiene, sea Linux o Windows Server); todos los scripts `bd/*.sh` usan rutas con `/` y son independientes del sistema operativo. | **Confirmado (13/10/2026, Joel)**: el servidor real de la institución es **Windows Server**, con **PostgreSQL 17** y **pgAdmin 17** usados por el profesor — coincide exacto con el anteproyecto ("en Windows o Linux", PostgreSQL 17). La duda de la sesión anterior (Doc. 00 §14.6 mencionaba "Ubuntu Server del tutor") queda resuelta: era un supuesto de planificación, no el entorno real; sin conflicto con el texto firmado. El entorno de desarrollo de esta laptop sigue en PostgreSQL 18 (un escalón por encima), pero no es el que se entrega — no afecta la conformidad. | Desplegar y probar, al menos una vez, en ese Windows Server real (Tomcat 9 + PostgreSQL 17) antes de la entrega — ya no es una duda de sistema operativo ni de versión, solo falta la prueba de despliegue en sí (relacionado con RNF15, ver análisis del 13/10/2026). |
| RNF04 | Software de los usuarios: accesible desde Google Chrome, Microsoft Edge o Mozilla Firefox. | Sin JavaScript específico de un navegador (sin polyfills a medida, SweetAlert2/Bootstrap/jQuery ya multi-navegador). | **APROBADO (13/10/2026, Joel, en su laptop)**: probado en Brave, Chrome, Firefox y Edge (4 navegadores, uno más que los 3 que pide el texto). | Nada. |
| RNF05 | Red: debe funcionar en la red local del negocio, sin depender de una conexión a internet para operar. | Cumple por diseño: regla de desarrollo activa desde el inicio ("AdminLTE 3, Bootstrap 4, jQuery, SweetAlert2, Font Awesome — archivos locales dentro del `.war`, sin CDN", `REGLAS_DESARROLLO.md`; Doc. 00 §14.4 "sin CDN: el servidor puede no tener internet") — nunca se agregó un `<script src="https://...">` externo en todo el proyecto, confirmado por un barrido completo de JSP/CSS/JS/`web.xml` el 13/10/2026 (sin hallazgos). | **APROBADO (13/10/2026, Joel, en su laptop)**: las 4 pruebas de navegador de RNF04 se hicieron con el wifi desconectado — panel, informes, estado de cuenta, y recibo/factura en PDF con logo, todo sin internet. Confirma en pantalla el análisis de código del mismo día (sin recursos externos, sin llamadas salientes del servidor). | Nada. |
| RNF06 | Periféricos: una impresora para contratos/pagarés/recibos/facturas/comprobantes, y un escáner para cargar los documentos firmados. | El rol del sistema es generar el PDF (JasperReports) y recibir el archivo ya escaneado — nunca controla la impresora ni el escáner directamente: el PDF se abre en el navegador para que el usuario lo imprima (Ctrl+P/diálogo del navegador); el escaneo se sube por un formulario `multipart/form-data` (`ServicioDocumentos`/`archivos_binarios`), probado en cada RF que carga un documento (RF03, RF13, RF29, etc.). | Cumple por diseño; probado indirectamente en decenas de RF (cada vez que se genera o se carga un PDF). | Nada de software — la impresora/el escáner son hardware del local, fuera del alcance del sistema. |
| RNF07 | Energía: el servidor debe estar conectado a una UPS para evitar la pérdida de información ante cortes de energía. | No aplica al código — es un requisito de infraestructura eléctrica del local, sin ningún mecanismo de software que lo sustituya (no hay guardado automático parcial ni nada equivalente, cada operación ya es una transacción SQL atómica, que es la única protección que el software sí puede dar ante un corte a mitad de una operación). | No verificable/no aplica al sistema. | Nada de software; confirmar que el local tenga la UPS antes de la entrega (decisión de Joel, no del sistema). |
| RNF08 | Seguridad: contraseñas cifradas, que nadie pueda leer, ni siquiera desde la base de datos. | `BCrypt.hashpw`/`BCrypt.checkpw` (jBCrypt), nunca se guarda ni se loguea una contraseña en texto plano (`ServicioAutenticacion`) — analizado completo en `procesos/login.md` §1/§4. | **Probado en pantalla** (`login-consolidado`, 09-10/10/2026) — ver `procesos/login.md` §9. | Nada. |
| RNF09 | Seguridad: bloquear la cuenta después de 3 intentos fallidos y mostrar el mismo mensaje sin revelar si el usuario existe. | `ServicioAutenticacion` (`seguridad.intentos_login`, mensaje único `mensajeAccesoRechazado`, tiempo de respuesta igualado con un `BCrypt.checkpw` dummy para un usuario inexistente) — analizado completo en `procesos/login.md` §2.2/§6. | **Probado en pantalla** (tag `rnf09-bloqueo-probado`, 09/10/2026). | Nada. |
| RNF10 | Seguridad: cerrar la sesión después de 30 minutos de inactividad, avisando 2 minutos antes. | `web.xml` (`session-timeout=30`), `seguridad.minutos_inactividad`, `recursos/js/aviso-sesion.js` (SweetAlert con cuenta regresiva 2 minutos antes, "Seguir conectado"/`/sesion/renovar` o "Salir"). | **Probado en pantalla** (`login-consolidado`) — ver `procesos/login.md` §9. | Nada. |
| RNF11 | Seguridad: controlar los permisos de cada perfil en el servidor (aunque se escriba la URL a mano) y rechazar formularios enviados desde fuera del sistema. | `FiltroAutorizacion` (permisos por ruta, en el servidor, no solo oculto en el menú) + `FiltroCsrf` (rechaza cualquier `POST` sin el token de la sesión) — analizado completo en `procesos/login.md` §4, también confirmado RF por RF (ej. RF38 D1/D2). | **Probado en pantalla** (`login-consolidado`, RF38 `rf38-probado`). | Nada. |
| RNF12 | Integridad: no permitir borrar operaciones registradas; las correcciones son por anulación o reversión, con motivo, autorización del gerente y registro en auditoría. | Triggers `fn_bloquear_eliminacion`/`fn_bloquear_modificacion_auditoria` (`bd/02_indices.sql:108-147`) sobre 11 tablas financieras + `auditoria_operaciones`; cero `DELETE` en el código Java (confirmado por RF40); anulación/reversión con motivo + autorización en RF14/RF25. | **Probado en pantalla** (RF40 `rf40-probado`, RF25 `rf25-probado`). | Nada. |
| RNF13 | Usabilidad: interfaz en español, montos con separador de miles, confirmación antes de cada operación irreversible, mensajes claros. | Interfaz 100 % en español; `FormateadorNumeros.comoGuaranies` (separador de miles en guaraníes, corregido ya en D-29 para que nunca confunda coma decimal con punto de miles); `plantilla/pie.jsp` con el mecanismo genérico `data-confirmar-boton`/SweetAlert2 para toda operación que no se puede deshacer (abrir/cerrar caja, anular, rechazar, revertir — uno por RF); `MensajesFlash` con 3 niveles (éxito/advertencia/error), nunca un stacktrace visible. | **Probado de forma transversal** — no tiene una pantalla propia, pero cada corrección de texto/confirmación de este tipo quedó probada dentro de su propio RF (RF09, RF10, RF11, RF14, RF15, RF19, RF25, RF39, etc.). | Nada puntual; es el único RNF sin una "prueba de cierre" propia porque se verifica junto con cada RF. |
| RNF14 | Rendimiento: responder en menos de 3 segundos en las operaciones habituales. | Doc. 00 §14.2 lo detalla por tipo de operación: búsquedas ≤ 2 s, cobro ≤ 3 s, documento Jasper ≤ 4 s. Las consultas usan índices (`bd/02_indices.sql`); no hay ninguna medición automatizada de tiempos en el código ni un test de performance. | **APROBADO (13/10/2026, Joel, en su laptop, medido con F12 → Red, sin internet)**: ingreso 0,78 s (carga completa de la página 2,03 s); panel 0,74-1,00 s (carga 2,02 s; 3,13 s solo en la primera carga del día, con la caché del navegador desactivada — ver el hallazgo de `ServicioMora.recalcularMorosidadDiaria` en Tanda 2); clientes 0,30-0,50 s (carga 1,48-1,68 s); recibo y factura en PDF abren enseguida. Todo dentro de los 3 umbrales de Doc. 00 §14.2 (búsquedas ≤ 2 s, cobro ≤ 3 s, Jasper ≤ 4 s). | Nada. |
| RNF15 | Disponibilidad: el sistema debe estar disponible durante todo el horario de atención del local. | No depende del código — depende de que Tomcat y PostgreSQL estén corriendo. Procedimiento completo para el Windows Server real, listo en `herramientas/windows/RESPALDO_WINDOWS.md` §1: Tomcat 9 como servicio con `service.bat install`, inicio Automático; PostgreSQL 17 ya viene como servicio Automático de fábrica; `sc config Tomcat9 depend= postgresql-x64-17` para que Windows arranque Postgres primero; verificación con `Get-Service`. | **Procedimiento listo (14/10/2026), prueba real pendiente**: falta hacer la prueba de reiniciar el Windows Server de verdad y confirmar que el sistema queda arriba solo (§1.5 de `RESPALDO_WINDOWS.md`) — no se puede probar desde esta laptop, necesita el servidor real. | Reiniciar el Windows Server una vez (fuera del horario de atención) y confirmar `http://<IP>:8081/sgcpc/acceso` sin tocar nada a mano. |
| RNF16 | Respaldo: copia de seguridad diaria de la base de datos, guardada fuera del servidor. | **`herramientas/windows/respaldo_diario.bat`** (nuevo, 14/10/2026): mismo `pg_dump` en 2 formatos que `bd/respaldar.sh` (custom + SQL plano, `--no-owner --no-privileges`, con fecha y hora en el nombre), con 2 agregados para cumplir el texto completo: copia los 2 archivos a una carpeta de destino configurable al inicio del `.bat` (pendrive o carpeta de red — "fuera del servidor") y borra, solo dentro de esa carpeta y de la local, los respaldos `sgcpc_*` de más de 30 días. **`herramientas/windows/restaurar_prueba.bat`** (nuevo): restaura un `.backup`/`.sql` en la base descartable `sgcpc_db_restaurada`, compara fila por fila contra la base real (solo lectura) y la vuelve a vaciar — pensado para la prueba de restauración mensual que pide el texto. Las 2 reglas de seguridad de siempre: `respaldo_diario.bat` solo acepta `sgcpc_db`/`sgcpc_db_pruebas` como origen; `restaurar_prueba.bat` nunca restaura sobre `sgcpc_db` ni `sgcpc_db_pruebas` (solo sobre `sgcpc_db_restaurada`) — ambos abortan con cualquier otro nombre. | **APROBADO en la laptop de Joel (14/10/2026)**: `respaldo_diario.bat sgcpc_db_pruebas` generó los 2 archivos y los copió a una carpeta de destino de prueba; `restaurar_prueba.bat` con ese `.backup` recién hecho restauró `sgcpc_db_restaurada`, comparó las 30 tablas contra `sgcpc_db_pruebas` (las 30 "OK", mismo conteo de filas) y la vació de nuevo al terminar. Queda pendiente repetir la misma prueba, una vez, en el Windows Server real contra `sgcpc_db` (ver `RESPALDO_WINDOWS.md` §2.5), y dejar armada la tarea programada diaria (§2.3) — eso todavía no se hizo, es el único paso que falta para RNF16 completo. | Repetir la prueba de `restaurar_prueba.bat` una vez en el servidor real; crear la tarea programada de las 22:00 (`schtasks`, `RESPALDO_WINDOWS.md` §2.3); configurar `CARPETA_DESTINO` con la unidad/ruta real de ese servidor. |
| RNF17 | Mantenibilidad: permitir cambiar los parámetros del negocio (tasa, montos, plazos, límites) desde sus propias pantallas, sin modificar el programa. | `/parametros` (general, Feriados, Límites de tasa), `ServicioParametros`, tabla `parametros_sistema` — tasa, montos, plazos y límites, todos editables desde pantalla por administrador/gerente, sin recompilar ni tocar código. | **Probado en pantalla, completo** (tag `rf39-probado`, 13/10/2026). | Nada. |
| RNF18 | Normativa: respetar el tope legal de la tasa de interés, calcular la cancelación anticipada sin intereses futuros (Ley 6624/2020) y facturar los intereses con su IVA (Ley 6380/2019). | Tope legal: `limites_tasa_usura` + `ServicioMotorFinanciero.validarLimiteTasa` (una sola fuente desde RF39, 13/10/2026); cancelación anticipada: `ServicioMotorFinanciero.calcularCancelacionTotal` (Ley 6624/2020, citada en RF22/RF26/RF27); IVA sobre intereses: `factura.jrxml`/`ServicioCobranza` (Ley 6380/2019, citada en RF12/RF23/RF33). | **Probado en pantalla** (RF26 `rf26-probado`, RF33 `rf33-probado`, RF39 `rf39-probado`) para el cálculo. La revisión LEGAL de forma (que un contador confirme la redacción exacta de la factura y un abogado la del contrato/pagaré) sigue pendiente — decisión de Joel del 13/10/2026: la factura ya se considera legal y aprobada (se le agrega logo/estilo en el Lote 2), contrato/pagaré siguen esperando esa revisión (`AVANCE.md`, "Revisión legal de contrato y pagaré"). | Nada de cálculo; solo la revisión de forma, ya decidida y pendiente de agendar con el abogado/contador. |

## Guion de prueba corto para los RNF que faltan

**Actualizado 14/10/2026**: RNF04, RNF05, RNF14 y RNF16 (en la laptop, contra
`sgcpc_db_pruebas`) ya se probaron — ver la tabla arriba. Quedan 2 pasos, los dos solo posibles
en el servidor real:

1. **RNF03/RNF15 (despliegue + arranque como servicio, en el Windows Server real)**: desplegar
   el `.war` + restaurar un backup en ese servidor (Tomcat 9 + PostgreSQL 17) y recorrer login,
   cotizador en PDF, un informe en PDF y la carga de un documento; después reiniciar la máquina
   y confirmar que Tomcat y PostgreSQL arrancan solos (sin que nadie los inicie a mano) antes del
   horario de atención — procedimiento completo y comandos exactos en
   `herramientas/windows/RESPALDO_WINDOWS.md` §1.
2. **RNF16 (en el servidor real)**: configurar `CARPETA_DESTINO` en `respaldo_diario.bat` con la
   unidad/ruta real de ese servidor, crear la tarea programada diaria de las 22:00 (`schtasks`,
   `RESPALDO_WINDOWS.md` §2.3) y repetir, una vez, la prueba de `restaurar_prueba.bat` contra
   `sgcpc_db` (§2.5) — el mecanismo ya está probado en la laptop, solo falta repetirlo ahí.
3. **RNF02 (hardware mínimo, prioridad baja)**: abrir el sistema en una PC con 4 GB de RAM y un
   navegador común, confirmar que anda fluido sin necesitar instalar nada adicional.

## 10. Pendientes (sin corregir)

*(Este documento no tenía todavía una sección de pendientes propia — se agrega acá, mismo
formato que el §10 de los RF individuales, porque estos hallazgos son transversales a varias
pantallas, no de un solo RF.)*

### Tanda de usabilidad (16/10/2026, hallazgos de Joel durante la prueba en pantalla del grupo
Domicilio — RNF13, ninguno específico de Domicilio en sí). Sin corregir todavía, ver el análisis
al final de esta sección.

a) ~~**Ficha del cliente sin acceso a sus préstamos y documentos**: la ficha (`clientes/ficha.jsp`)
   no muestra si el cliente tiene algún préstamo, en qué estado está, ni un enlace directo a su
   formalización/documentos/estado de cuenta — hay que ir a buscarlo aparte en Formalización o en
   el listado de Préstamos.~~ — **resuelto (19/10/2026, tanda de usabilidad, punto 4)**: pestaña
   nueva "Solicitudes y préstamos" (sin número, al final del nav-pills, visible para cualquiera
   que pueda ver la ficha). `SolicitudDAO.listarPorCliente` (nuevo, mismo estilo que
   `listarPorOficial`) lista todas las solicitudes del cliente, más reciente primero;
   `ServicioFormalizacion.obtenerPorSolicitud` (ya existía) resuelve el préstamo de cada una, si
   ya se formalizó. El enlace de cada fila se decide por el PERMISO real del usuario en sesión
   (`ControladorClientesServlet.construirEnlaceHistorial`, mismo `nivelAccesoSesion` que ya usa el
   resto de la ficha — nunca por el nombre del rol): `/solicitudes/{id}` o
   `/formalizacion/nueva/{id}` sin préstamo; `/formalizacion/{id}` o `/desembolso/{id}` con
   `PENDIENTE_DESEMBOLSO`; `/reportes/estado-cuenta/{id}` o `/cobranza/{id}` con el préstamo ya
   resuelto — `null` (sin enlace) si no tiene permiso para ninguna de esas pantallas (ej. la
   cajera nunca ve un enlace a `/reportes`, que le daría 403). Sin solicitudes: "El cliente
   todavía no tiene solicitudes." Cubierto en `pruebas/humo_http.sh` (préstamo de E6): la pestaña
   aparece, la cajera no ve ningún enlace a `/reportes`, el oficial sí ve el enlace a
   `/reportes/estado-cuenta/{id}` del préstamo ya `ACTIVO`.

b) ~~**El mensaje "Siguiente paso" al guardar la ficha no sigue la etapa real del cliente**: hoy
   (`ControladorClientesServlet.siguientePasoParaEdicion`) dice "cargar sus documentos en
   Expediente" aunque el cliente ya tenga un préstamo en curso (p. ej. en formalización) — debería
   reconocer esa etapa y no repetir un paso ya superado.~~ — **resuelto (19/10/2026, tanda de
   usabilidad, Commit B)**: `siguientePasoParaEdicion` llama a `buscarCompromisoAbierto` una sola
   vez al principio del método (antes se llamaba otra vez al final) y reutiliza el resultado. En
   la rama de "falta el comprobante de ingreso" (la que dispara el Commit A al cambiar la
   situación laboral), si hay un compromiso abierto el texto ahora dice "Siguiente paso: cargar el
   nuevo comprobante de ingreso. El cliente tiene un préstamo en curso; su expediente debe quedar
   al día." (o "una solicitud abierta" según `CompromisoAbierto.isViaPrestamo()`); sin compromiso,
   el texto queda igual que antes. El resto de las ramas (cédula, buró, "iniciar una solicitud")
   no cambia.

c) **Falta un buscador único con AJAX en todas las listas** (Clientes, Solicitudes, Aprobaciones,
   Formalización, Desembolso, Cobranza, Mora, Cancelación): desde 2-3 caracteres, con espera de
   ~300 ms, por cédula, nombre y apellido en cualquier orden, y número de solicitud u operación;
   validado también en el servidor según permisos; combinable con los filtros existentes de cada
   lista; con lo más reciente o lo pendiente primero; un solo componente reutilizado en las 8
   pantallas (hoy Clientes ya tiene "listado vivo" con AJAX —
   `ControladorClientesServlet.mostrarLista` + `clientes/_resultados.jsp` +
   `recursos/js/listado-vivo.js` — pero las otras 7 listas no).

d) **Buscar "Miguel Sosa" en Clientes da 0 resultados** (con "MIGUE" o la cédula sí encuentra).
   **Confirmado, NO es un efecto del grupo Domicilio**: existe desde la implementación original de
   E3 (commit `153e612`, antes de cualquier normalización) — `PersonaDAO.construirWhere` compara
   el texto contra `nombres` y `apellidos` por separado (`nombres ILIKE '%texto%' OR apellidos
   ILIKE '%texto%'`), nunca concatenados; "Miguel Sosa" no es substring de "Miguel" ni de "Sosa"
   por separado, así que ninguna de las dos columnas lo contiene y la búsqueda da 0 a propósito
   (no es un bug introducido ahora, es una limitación del diseño original del filtro).

e) **Regla general pendiente: toda acción no obvia necesita un ⓘ con una explicación corta (en
   "usted"), y un botón deshabilitado necesita decir por qué** — hoy no hay ningún criterio
   consistente sobre cuándo una acción lleva ayuda y cuándo no. Caso concreto: en
   `formalizacion/ficha.jsp` conviven "Re-emitir los PDF (sin cambiar plan)" (línea 177, re-emite
   los 3 PDF con la plantilla/datos actuales SIN tocar el plan ni el número de operación — sirve
   para aplicar una corrección de diseño de reporte) y "Regenerar" (línea 346, bajo "Regenerar
   documentos" — rehace el plan de pagos con una fecha de primer vencimiento distinta, generando
   un número de operación nuevo) — dos botones visualmente parecidos (`btn-outline-primary`), en
   la misma pantalla, sin nada que explique la diferencia real entre uno y otro.

f) ~~**Cambiar la situación laboral invalida el comprobante de ingreso vigente sin avisar antes de
   guardar** (17/10/2026, hallazgo de Joel durante la prueba en pantalla del grupo Laborales). Hoy
   el cambio de situación laboral deja `REEMPLAZADO` el `COMPROBANTE_INGRESO` vigente (regla RF03
   preexistente desde `7946119`/`rf03-situacion-laboral`, 09/10/2026 — ver `RF03_expediente.md`
   §8) sin ningún aviso previo: el oficial guarda el cambio y recién después, si entra al
   Expediente, ve que el documento desapareció. Falta una confirmación ANTES de guardar
   ("Cambiar la situación laboral invalidará el comprobante de ingreso vigente del cliente; deberá
   cargar uno nuevo. ¿Continuar?"), mismo patrón ya usado para otras confirmaciones del sistema
   (`data-confirmar`). No implementado todavía.~~ — **resuelto (19/10/2026, tanda de usabilidad,
   Commit A)**: `clientes/ficha.jsp` agrega/quita dinámicamente `data-confirmar` en
   `#formularioFichaCliente` (mismo mecanismo que `caja/ficha.jsp`) solo cuando el `<select>` de
   situación laboral cambió respecto al valor guardado Y el comprobante de ingreso está vigente
   hoy (mismo criterio de búsqueda en `itemsExpediente` que ya usa `itemAutorizacionBuroFicha`).
   Texto: "Cambiar la situación laboral invalidará el comprobante de ingreso vigente. Deberá
   cargar uno nuevo." Botones "Sí, cambiar"/"Cancelar" (REGLAS §12.3: el cartel pide decidir, no
   solo informa). Probado "antes" por Joel con C.I. 90908192 (Comerciante → Asalariado): guardaba
   directo, sin aviso.

#### Análisis (16/10/2026, solo análisis, sin aplicar nada)

Hallazgo que cambia el panorama de (c): **ya existe un componente reutilizable** —
`recursos/js/listado-vivo.js` (debounce de 300 ms en campos de texto, inmediato en selects/
fechas, AJAX contra la misma ruta/filtro de siempre, mismo mecanismo `PeticionAjax.esAjax` del
servidor). Ya está en uso en: Clientes, Cobranza (búsqueda), Préstamos, Parámetros (historial) y
6 pantallas de Reportes. **No hace falta construir el mecanismo de nuevo** — falta adoptarlo
donde todavía no está.

| Pantalla pedida | Estado hoy | Qué falta |
|---|---|---|
| Clientes | ✅ ya tiene `listado-vivo` | Ajustar el filtro de texto en sí (puntos b/d de arriba) |
| Cobranza | ✅ ya tiene `listado-vivo` (`cobranza/busqueda.jsp`) | Nada de infraestructura; "Cancelación" vive en esta misma pantalla (`/cobranza/{id}/cancelar` desde la ficha), no es una pantalla aparte |
| Solicitudes | Formulario GET plano, sin AJAX (`solicitudes/lista.jsp:30`) | Agregar `data-listado-vivo` + fragmento `_resultados.jsp` (mismo patrón que Clientes) |
| Aprobaciones | Formulario GET plano, sin AJAX (`aprobaciones/lista.jsp:33`) | Igual que Solicitudes |
| Formalización | **Sin formulario de filtro/búsqueda hoy** (`formalizacion/lista.jsp`) | Hay que agregar el campo de texto además del mecanismo |
| Desembolso | **Sin formulario de filtro/búsqueda hoy** (`desembolso/lista.jsp`) | Igual que Formalización |
| Mora | **Sin formulario de filtro/búsqueda hoy** (`mora/lista.jsp`) | Igual que Formalización |

Archivos por pantalla nueva: 1 controlador (separar `mostrarLista` en método propio si no lo
tiene + servir el fragmento cuando `PeticionAjax.esAjax`), 1 DAO (agregar la condición de texto
al `WHERE` — cédula/nombre/apellido en cualquier orden necesita concatenar
`nombres || ' ' || apellidos` además de cada uno por separado, no solo "cualquier orden" sino
también "los dos juntos"), 1 JSP de lista + 1 fragmento `_resultados.jsp` nuevo, agregar
`data-listado-vivo` al `<form>`.

**Reutilizable además de `listado-vivo.js`**: `PrestamoDAO.obtenerUltimoPorCliente`,
`SolicitudDAO.buscarPorClienteConPrestamoAbierto`/`buscarAbiertaPorCliente`,
`ServicioClientes.contarPrestamosActivosDelCliente` — para (a), ya existen los JOIN por
`id_cliente`, falta solo un `listarPorCliente` que devuelva todas las filas (hoy solo hay "el
último" o "si hay uno abierto"). Las rutas a enlazar ya existen: `/formalizacion/{idPrestamo}`,
`/reportes/estado-cuenta/{idPrestamo}`.

**Riesgos**:
- (c) es la más grande: 3 pantallas sin ningún filtro hoy (Formalización/Desembolso/Mora) — no
  es "agregar AJAX a algo que ya busca", es diseñar el filtro de cero en esas 3.
- "Nombre y apellido en cualquier orden" + número de solicitud/operación en el MISMO campo de
  texto, combinado con los filtros existentes de cada pantalla, es la parte con más superficie de
  cambio en cada DAO — cambia el `WHERE` de 8 lugares distintos, cada uno con su propia tabla
  base.
- (b) necesita decidir primero una jerarquía de "etapas" (Expediente → Buró → Solicitud →
  Formalización → Desembolso → Activo) antes de tocar `siguientePasoParaEdicion` — hoy ese método
  no sabe nada de préstamos/formalización, solo de Expediente/Buró/solicitud abierta.
- (a) y (b) tocan la misma zona (`ControladorClientesServlet.mostrarFicha`/`clientes/ficha.jsp`)
  — conviene hacerlos juntos para no tocar ese archivo dos veces.
- (e) es la única sin un componente a reutilizar (el ⓘ de hoy es `title=""` nativo del navegador,
  sin ningún CSS/JS compartido) — si se quiere un tooltip más vistoso que el nativo, hay que
  elegir/agregar algo (o decidir que el `title` nativo alcanza).

**Orden propuesto**: (a)+(b) juntos (mismo archivo, bajo riesgo, alto valor) → (e) (acotado,
puntual, sin tocar lógica de negocio) → (c) (la más grande, de a una pantalla por vez:
Solicitudes/Aprobaciones primero por ya tener filtro, Formalización/Desembolso/Mora después).
(d) ya quedó resuelto, sin código (confirmado preexistente). **No aplicado todavía.**
