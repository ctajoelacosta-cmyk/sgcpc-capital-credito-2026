Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Expediente y consulta al buró

## Pestaña ② Expediente

Después de registrar al cliente, pase a esta pestaña. El título le muestra **"Expediente: 0 de
4 documentos obligatorios"**, con una barra de avance, y debajo, **"Documentos para: [situación
laboral]"** — la lista de documentos cambia según la situación laboral del cliente.

La lista tiene 4 filas, **todas obligatorias** (decisión del 21/10/2026, vigente desde el
28/10/2026 — antes el comprobante de domicilio era complementario), **en el orden en que conviene
resolverlas** (desde el 29/10/2026 — antes la autorización aparecía última):

1. **Autorización de consulta al buró** (obligatorio) — firmada por el cliente. Sin ella no se
   puede consultar Informconf.
2. **Cédula de identidad** (obligatorio) — ambos lados, vigente.
3. **Comprobante de ingreso** (obligatorio) — cambia según la situación laboral (por ejemplo,
   para un asalariado: liquidación de sueldo o extracto de aportes IPS de los últimos 3 a 6
   meses).
4. **Comprobante de domicilio** (obligatorio) — una factura de ANDE, ESSAP o telefonía de los
   últimos 2 meses.

**La autorización va primera porque es el filtro que le ahorra tiempo**: la consulta al buró es
lo que le dice si tiene sentido seguir, y nada de eso se puede hacer sin la autorización firmada.
Si el cliente no pasa el buró, no hace falta que le pida el resto de los papeles. El sistema
también se lo va pidiendo en ese orden (ver *"El sistema le dice a dónde ir"*, más abajo). La
autorización tiene 3 subpasos:

1. **Generar la autorización** — toque el botón del mismo nombre.
2. **El cliente firma la autorización impresa** (en papel, fuera del sistema).
3. **Cargar autorización firmada** — suba el escaneo de la hoja ya firmada.

### Generar la autorización

Al tocar "Generar autorización" aparece un cuadro: *"Autorización N.º AUT-[año]-[número]
generada — Siguiente paso: imprímala, que el cliente la firme, y cárguela escaneada."*, con
botones **"Abrir para imprimir"** y **"Aceptar"**. La fila pasa a mostrar **"✓ Generada el
[fecha y hora]"**, con dos botones: **"Reimprimir autorización"** y **"Cargar autorización
firmada"**.

El PDF que se abre para imprimir incluye:

- Encabezado con los datos de la empresa.
- Título: **"AUTORIZACIÓN PARA CONSULTA DE INFORMACIÓN CREDITICIA"**.
- El número de autorización, lugar y fecha/hora.
- **"Vigente hasta [fecha]"** — tiene 30 días de validez desde que se genera.
- Los datos del cliente (nombre, C.I., domicilio, teléfono).
- El texto legal: el cliente autoriza a la empresa a consultar y/o reportar sus datos
  crediticios, con la única finalidad de evaluar el crédito que está solicitando, y declara
  conocer sus derechos de acceso, rectificación y actualización de sus datos.
- Un espacio para la firma del cliente, con su nombre y C.I.
- Un recuadro de **"USO INTERNO"**, con el número, quién la generó y un espacio para que quede
  constancia de quién la recibió y verificó.

Imprímala, pídale al cliente que la firme, y escanéela.

### Cargar la autorización firmada

Al subir el escaneo, aparece un cuadro: *"Autorización cargada y consentimiento registrado."*,
seguido de qué le falta todavía al expediente (por ejemplo, cédula y comprobante de ingreso, si
no los cargó antes). **El sistema lo lleva directo a la pestaña ③ Consulta al buró** — aunque
el expediente todavía no esté completo. No es un error: solo significa que puede ir a trabajar
en la consulta al buró de inmediato, y volver después a terminar de cargar lo que falte en ②.

**Importante**: solo puede cargar la autorización firmada el **mismo día** en que la generó. Si
pasó un día, el sistema le va a pedir que genere una autorización nueva (para que la fecha de
emisión coincida con la fecha real de la firma).

### Cargar la cédula, el comprobante de ingreso o el de domicilio

Toque **"Cargar"** en la fila correspondiente y suba el archivo (PDF, JPG o PNG). Aparece un
cuadro: *"Documento cargado. Faltan: [lo que quede pendiente]."*, con **"Aceptar"**. La fila
pasa a **"✓ Cargado [fecha]"**, con "Ver" y "Reemplazar", y el contador y la barra de avance
suben (por ejemplo, de "1 de 4" a "2 de 4", de 25 % a 50 %).

**Estas cargas lo dejan en la misma pestaña ② Expediente** mientras falte algún otro documento,
y el cuadro le dice qué falta — y, si todavía no registró el resultado del buró, se lo recuerda:
*"Documento cargado. Falta: Comprobante de domicilio. Pendiente también: registrar el resultado
del buró."* Cuando carga el último que faltaba, el sistema sí lo lleva al paso que sigue (la
consulta al buró, o directamente su solicitud si el buró ya está registrado). La autorización es
la excepción: **siempre** lo lleva a ③ apenas se carga, complete o no el expediente, justamente
porque es el filtro.

**Si sube el mismo archivo dos veces, el sistema se da cuenta.** Compara el contenido, no el
nombre, así que renombrarlo no cambia nada:

- **Es el mismo archivo que ya está cargado en esa misma fila** → lo **rechaza**, en rojo: *"Ese
  archivo ya está cargado. Es idéntico al que figura como «Comprobante de domicilio»: no hace
  falta volver a subirlo."* No se guarda nada y no queda una versión nueva repetida. Si lo que
  quería era **actualizar** el documento, use el archivo nuevo: el sistema solo rechaza cuando es
  exactamente el mismo.
- **Es el mismo archivo que ya está en otra fila del mismo expediente** → lo **guarda** y solo le
  avisa, en amarillo: *"Este archivo ya está cargado como «Cédula de identidad». Si es el mismo
  papel, está bien; si se equivocó de archivo, vuelva a cargarlo."* Eso es a propósito: una sola
  hoja escaneada puede servir para dos documentos distintos. Léalo y decida usted.

El sistema **solo mira el expediente de ese cliente** (o, en Formalización, los papeles de ese
préstamo). No le va a avisar si el mismo archivo está en la carpeta de otro cliente.

**La vigencia de la cédula no se lee del archivo que sube** — viene del campo "Vencimiento de
la cédula" que usted completó en la pestaña ① Datos. Si ese campo ya venció, la fila de la
cédula va a seguir en rojo aunque el escaneo esté cargado — revise primero ese dato en ① Datos
si le pasa esto.

### El sistema le dice a dónde ir

Desde el 29/10/2026, después de **cada** acción el sistema le dice **qué falta** y lo lleva al
**primer paso pendiente** — nunca a un paso ya hecho, ni a uno posterior si falta algo antes.
El orden que sigue es este:

1. Falta la autorización firmada (o venció) → **② Expediente**: *"Siguiente paso: generar la
   autorización, hacerla firmar y cargarla."*
2. Autorización lista, falta registrar el buró → **③ Consulta al buró**: *"Siguiente paso:
   registrar el resultado del buró."*
3. El buró dio **deudas en mora** → no lo lleva a ningún lado: *"El cliente figura con deudas en
   Informconf. No se puede otorgar el crédito."* (no hay nada que completar para arreglarlo).
4. Buró listo, falta cédula, comprobante de ingreso o de domicilio → **② Expediente**:
   *"Falta: …"* / *"Faltan: …"*, con el nombre de cada documento.
5. Expediente completo y sin solicitud → **④ Solicitud**: *"Siguiente paso: iniciar una solicitud
   de crédito."*
6. Solicitud en borrador → su ficha: *"Siguiente paso: enviar SOL-… a aprobación."*
7. Solicitud ya enviada → *"Espera la decisión del gerente."*

Ese mismo paso es el que ofrece el botón **"Siguiente: … →"** al pie de cada pestaña. Si el paso
pendiente es la pestaña en la que ya está, el botón no aparece (no tiene sentido un botón que
lleva a donde está): las filas marcadas **"Falta"** ya le indican qué hacer ahí mismo.

**Esto no le prohíbe nada**: puede cargar cualquier documento, entrar a la consulta al buró o
crear la solicitud cuando quiera, en el orden que necesite. El sistema solo le sugiere el camino
más corto y le dice qué falta.

### Cuando carga el último obligatorio: la barra se pone verde

Al cargar el cuarto documento obligatorio (en cualquier orden — cédula, comprobante de ingreso,
comprobante de domicilio o autorización, el que le falte), tres cosas cambian juntas:

- El contador llega a **"Expediente: 4 de 4 documentos obligatorios"**.
- La barra de avance llega al **100 % y cambia de amarillo a verde**.
- Aparece el recuadro **"✓ Expediente completo"**.
- La pestaña **② Expediente** se marca con un tilde verde (**② ✓**), igual que ya pasa con las
  otras pestañas cuando terminan su parte.

Las cuatro cosas dependen de la misma condición (los 4 obligatorios cargados), así que siempre
van a coincidir entre sí — nunca va a ver, por ejemplo, la barra en verde pero la pestaña sin
tilde.

### El contador y la barra cuentan los 4 documentos

Desde el 28/10/2026 los cuatro documentos cuentan para el contador **"N de 4"** y para la barra
de avance, porque los cuatro son obligatorios. **Mientras falte alguno, incluido el comprobante
de domicilio, no va a poder enviar la solicitud a aprobación.**

> **Si atendió clientes antes del 28/10/2026**: un cliente al que le cargó los 3 documentos de
> antes y que veía "3 de 3 ✓" ahora aparece como **"3 de 4"**, con el comprobante de domicilio
> en **"Falta"**. No es un error ni se perdió nada: el documento pasó a ser obligatorio y hay
> que pedírselo al cliente. Los préstamos que ese cliente ya tenía **no se ven afectados** —
> siguen su curso normal, incluido el desembolso.

### Cuando carga el comprobante de domicilio

Si, con los otros 3 ya cargados, carga el comprobante de domicilio, el cuadro de éxito cambia —
**ahí deja de decir "Faltan: ..."**. En su lugar dice: **"Expediente completo. Siguiente paso:
registrar el resultado del buró."**, con **"Aceptar"**.

Al tocar "Aceptar", el sistema lo lleva **directo a la pestaña ③ Consulta al buró**, **aunque
usted ya haya registrado la consulta antes**. No es un error, pero tampoco es un aviso útil en
ese caso — el sistema siempre asume que, al completar el Expediente, lo que sigue es el Buró,
sin fijarse si ya lo hizo. Si ya tiene todo (Expediente, Buró y hasta la solicitud creada), no
se quede en esa pestaña: use la pestaña **④** (o el botón grande al pie de ③, que si ya creó la
solicitud le va a decir **"④ SOL-[número] (Borrador) →"**) para seguir con lo que realmente
corresponde.

### Si ya tiene una solicitud creada para este cliente

Una vez que creó la solicitud (aunque siga en Borrador), la pestaña **④** deja de decir
"Solicitud" y pasa a mostrar su número y estado — por ejemplo, **"SOL-2026-0001 (Borrador) →"**
— y lo lleva directo a ella. **El botón de abajo del Expediente, en cambio, puede seguir
diciendo "Siguiente: ③ Consulta al buró →" aunque usted ya haya registrado el buró** — no es un
error, pero no se deje guiar por ese botón en ese caso: use la pestaña ④ (o "Solicitudes y
préstamos") para volver a la solicitud que ya tiene en curso.

## Pestaña ③ Consulta al buró

Esta pestaña tiene dos partes:

**Autorización firmada por el cliente** — con la etiqueta **"Vigente"**, muestra quién la firmó,
el número, cuándo se firmó y quién la cargó, un botón **"Ver documento firmado"**, y **"Vigente
hasta [fecha] (quedan N días)"**. Si los 30 días ya pasaron, la etiqueta avisa que venció — en
ese caso, vuelva a ② Expediente y genere una autorización nueva.

**Paso B · Resultado de la consulta a Informconf** — con la etiqueta **"Pendiente"** hasta que lo
complete. Acá usted, fuera del sistema (por WhatsApp, con la cédula del cliente), consulta a
Informconf, y después carga el resultado:

1. **Respuesta de Informconf** (el informe en PDF) — botón "Cargar respuesta".
2. **Puntaje y Clasificación** — complete los dos.
3. **¿Tiene deudas en mora?** — Sí o No (obligatorio).
4. **Resumen** (opcional) — para cualquier observación suya.

### Cargar la respuesta de Informconf

Toque **"Cargar respuesta"** y suba el PDF del informe. Aparece un cuadro: *"Documento cargado.
Complete el puntaje, la clasificación y si tiene mora, y toque Registrar consulta."* **El
documento se guarda ahí mismo, apenas lo carga** — es un paso separado de "Registrar consulta"
(punto 2 más abajo), que recién viene después. Si necesita subir otro informe en su lugar, el
botón cambia a **"Reemplazar"**, con una confirmación (porque ya hay uno cargado).

**Atención**: si carga el informe pero se olvida de completar puntaje/clasificación/mora y
"Registrar consulta", el documento queda guardado y disponible (puede verlo, puede
reemplazarlo), pero la consulta en sí **no queda registrada** — no hay ningún aviso aparte que
le recuerde terminar el paso, más allá de que "Paso B" siga marcado como pendiente. Revise
siempre que el resumen (por ejemplo "850 · A · sin mora") haya quedado fijo antes de seguir.

El botón **"Registrar consulta"** permanece deshabilitado hasta que complete los 4 puntos.

### Registrar la consulta

Con puntaje, clasificación, mora y resumen completos, toque **"Registrar consulta"**. Aparece
un cuadro: *"Consulta registrada. [puntaje] · [clasificación] · sin mora."*, con **"Aceptar"**.
Al volver:

- La pestaña pasa a **"③ ✓ Consulta al buró"**.
- La etiqueta del Paso B pasa de **"🟠 Pendiente"** a **"✅ Registrada"**.
- Aparece el recuadro **"Consulta registrada: [puntaje] · [clasificación] · sin mora"**.
- Dos botones: **"Registrar una nueva consulta"** (por si hiciera falta registrar otra más
  adelante) y **"Siguiente: ④ Solicitud →"**.
- **"Historial de consultas"** suma una fila más.

**Atención**: el botón "Siguiente: ④ Solicitud →" aparece habilitado aunque el Expediente (②)
siga incompleto (por ejemplo, si todavía falta cargar la cédula o el comprobante de ingreso).
No es un error — el sistema le va a volver a avisar qué falta más adelante, al enviar la
solicitud a aprobación — pero conviene que usted mismo revise la pestaña ② antes de avanzar, en
vez de confiar en que el botón de acá se lo recuerde.

**Si registra una consulta nueva** (botón "Registrar una nueva consulta"): la consulta anterior
no se borra ni se modifica — queda igual en el Historial. Lo nuevo se agrega aparte. El informe
en PDF tampoco cambia solo por registrar de nuevo — sigue siendo el mismo que ya había cargado,
a menos que usted suba otro aparte (con "Reemplazar").

**Para avanzar a la Solicitud (④)** necesita tener completos tanto el Expediente (②) como esta
pestaña (③) — si todavía falta algo de cualquiera de las dos, el sistema se lo va a volver a
avisar más adelante, al enviar la solicitud a aprobación.
