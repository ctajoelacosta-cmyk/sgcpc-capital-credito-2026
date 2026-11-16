---
name: sgcpc-mapa
description: Usar SIEMPRE al iniciar una tarea de SGCPC o antes de buscar información del proyecto. Mapa de toda la documentación, qué documento manda, cuál está vigente, qué leer para cada pregunta y qué actualizar después de cada cambio. Leer solo la sección necesaria, nunca documentos enteros.
---

# SGCPC — Mapa de documentación

Objetivo: encontrar el dato correcto leyendo lo mínimo, y dejar cada cambio registrado en el
documento que corresponde. Ahorrar tokens es parte de la tarea.

## Reglas de lectura (obligatorias)

1. **Nunca leer un documento entero si alcanza con una sección.** Primero buscar (grep/Search)
   el término, después leer solo esas líneas. Varios documentos pesan 40.000+ tokens
   (`recorrido/06_primer_cobro.md`, `recorrido/04_formalizacion.md`, `00_especificacion_maestra.md`).
2. **Ante una contradicción, manda este orden:**
   0. `docs/REGLAS_DESARROLLO.md` **§0, los tres dogmas** — **por encima de todo**, incluso de
      lo que diga cualquier otro documento: **DOGMA 1**, se prueba el circuito real hoy o no
      está hecho ("verificado por código" y "vista previa" **no** son "probado"); **DOGMA 2**,
      una sola base, `sgcpc_db_pruebas`, la de `bd.properties`; **DOGMA 3** (28/10/2026), las
      skills se actualizan al cerrar **cada** ítem — ver el arranque de sesión acá abajo.
   1. `docs/RF_ANTEPROYECTO.md` (firmado; ley del proyecto; nunca se modifica)
   2. Decisiones de Joel registradas (CLAUDE.md, `AVANCE.md`, `recorrido/00_LEEME.md`)
   3. `docs/REGLAS_DESARROLLO.md`
   4. El código y la base reales (verificar ahí ante cualquier duda)
   5. `docs/funcionamiento/RF*.md` y `docs/recorrido/*`
   6. Todo lo demás
3. **Datos que cambian** (contraseñas, versiones, servidor, estado de pendientes): usar el valor
   del documento vivo con la **fecha más reciente** y, si no tiene fecha o es viejo, verificar
   en el sistema antes de usarlo. Nunca tomar estos datos de documentos históricos.
4. **No crear documentos nuevos** si el dato tiene lugar en uno existente (ver tabla de
   actualización). Menos documentos = menos contradicciones.

## 0. Al empezar la sesión: ¿las skills están al día? (DOGMA 3)

**Antes de trabajar**, comparar la fecha de las 4 skills propias con la fecha del último ítem de
`AVANCE.md`. Si alguna skill es **anterior**, se actualiza **antes de seguir** — una skill vieja
dirige mal la sesión entera.

```bash
ls -la .claude/skills/sgcpc-*/SKILL.md     # fecha de las skills vivas
head -5 AVANCE.md                          # fecha del último ítem
for s in sgcpc-mapa sgcpc-cambio-seguro sgcpc-diseno-ui sgcpc-reportes-jasper; do \
  diff -q --strip-trailing-cr ".claude/skills/$s/SKILL.md" "docs/skills_respaldo/${s}_SKILL.md"; done
```

`--strip-trailing-cr` es obligatorio: el repo está con `core.autocrlf=true`, así que la copia de
`docs/skills_respaldo/` sale del checkout con CRLF y la de `.claude/` (escrita en la sesión) con
LF. Sin esa opción, `diff` marca **todas** las líneas distintas y parece una skill desactualizada
cuando el contenido es idéntico (pasó el 29/10/2026 con `sgcpc-mapa` y `sgcpc-diseno-ui`).

Las dos copias (`.claude/skills/` y `docs/skills_respaldo/`) tienen que estar **idénticas**:
`.claude/` está en `.gitignore`, así que **solo la de `docs/` se versiona**. Si difieren, la que
manda es la de `.claude/` (la que se cargó en la sesión) — salvo que el `git log` muestre lo
contrario.

## 1. Información estable (cómo es el sistema; casi no cambia)

| Pregunta | Dónde mirar |
|---|---|
| ¿Qué tiene que hacer el sistema? | `docs/RF_ANTEPROYECTO.md` (RF01–RF40, RNF01–RNF18) |
| Reglas de negocio que nunca se rompen, convenciones | `docs/REGLAS_DESARROLLO.md` (§5 negocio, §13 consolidación) |
| Cómo funciona un RF por dentro | `docs/funcionamiento/LEEME.md` (índice) → `funcionamiento/RFxx_*.md` |
| RNF, login, estilo de PDF | `funcionamiento/RNF_revision.md`, `funcionamiento/procesos/` |
| Modelo de datos y campos | `docs/08_modelo_datos.md`, `docs/inventario_campos.md` |
| Esquema de la base HOY | `docs/base_de_datos/04_esquema_actual.md` |
| Pantallas e interfaces | `docs/10_interfaces.md` |
| Colores, tipografía, estilo visual | `docs/GUIA_VISUAL.md` + skill `sgcpc-diseno-ui` |
| Diseño de reportes y comprobantes | `docs/diseno_reportes_e5.md`, `diseno_comprobantes_E8.md`, `diseno_bloque4_E8.md`, `diseno_bloque5_E9.md` + skill `sgcpc-reportes-jasper` |
| Cómo se usa de verdad (paso a paso, lo que pasa por detrás) | `docs/recorrido/01…07_*.md` y `*_que_pasa_por_detras.md` |
| Cobro, recibo, factura, constancias, por qué existe cada uno | `recorrido/06_comprobantes_recibo_factura.md`, `06_cobro_mapa_completo.md` |
| Manual de usuario por módulo | `docs/recorrido/manual_usuario/01…08` |

## 2. Información viva (estado y cambios; se actualiza siempre)

| Documento | Qué guarda |
|---|---|
| `CLAUDE.md` (raíz, no versionado) | Reglas de Joel y orden de lectura al iniciar |
| `AVANCE.md` (raíz) | Registro con fecha de todo lo hecho; entrada más nueva arriba |
| `docs/recorrido/00_LEEME.md` | **PUNTO DE RETOMADA**: dónde quedamos, qué sigue, alcance vigente |
| `docs/recorrido/FASE2_PLAN.md` | Mejoras de fase 2: hechas (con commit), pendientes, fuera de alcance |
| `docs/auditoria/AUDITORIA_TECNICA.md` | Hallazgos técnicos T1–T10 y su estado |
| `docs/auditoria/MAPA_DE_RIESGOS.md` | Los 7 peligros y el control de 7 preguntas |
| `docs/base_de_datos/03_bitacora_cambios.md` | Cada cambio a la base, con fecha |
| `docs/entregas/ENTREGA_SERVIDOR.md` | Entregas a la institución, servidor de destino, **contraseñas vigentes** |
| Git (`git log`, tags) | Qué cambió y por qué; tags `antes-fase2`, `entrega-final` |

### Carpetas fuera del repo (al lado de `sistema/`, no versionadas)

| Carpeta | Qué guarda |
|---|---|
| `evidencias/AAAA-MM-DD/` | Los documentos que el sistema **emitió de verdad** ese día, sacados con `SELECT` de solo lectura de `archivos_binarios` — **nunca regenerados** (D-94), con el **SHA-256 verificado contra `archivos_binarios.hash_sha256`**. Subcarpetas por tipo (`recibos/`, `facturas/`, `constancias/`, `caja/`) y `capturas/` para las de Joel. Cada una con su **`LEEME.md`**: qué se probó, los números clave calculados **a mano y sellados antes** de abrir la pantalla, y cómo se obtuvo cada archivo. Existe porque la limpieza previa a la entrega va a borrar esos datos de `sgcpc_db_pruebas` |
| `pruebas/<item>_corrimiento/` | Los dos `.sql` de un **corrimiento controlado de fechas** (camino B de D-81) para probar algo que necesita una cuota vencida: la IDA y la VUELTA, cada condición en un `DO $$ … RAISE EXCEPTION $$`. Modelo: `b81_corrimiento/` (30/10/2026). El método y sus números están en `AVANCE.md`; el protocolo, en `sgcpc-cambio-seguro` |
| `pruebas/<reporte>_muestra/` | PDF de muestra de un `.jrxml` en revisión (ver `sgcpc-reportes-jasper`). Se borran antes de la entrega |
| `pruebas/b86_capturas/` | Capturas **antes/después** de cada pantalla tocada en la pasada visual de **B86**, sacadas con **Playwright** a **1280x800** (habilitado por Joel el 05/11/2026; ver "Lista de control B86" en `sgcpc-diseno-ui`, que trae lo operativo: viewport y no `fullPage`, cómo entrar sin escribir la contraseña, Ctrl+F5). Son para que Joel compare el aspecto: **registran, no prueban** (DOGMA 1). Nacen en `sistema/.playwright-mcp/` —Playwright solo escribe dentro del proyecto; esa carpeta está en `.gitignore` desde el 05/11/2026— y de ahí se **copian** acá. Se borran antes de la entrega |

## 3. Histórico / no vinculante (consultar, nunca obedecer)

- `docs/00_especificacion_maestra.md`: documento de trabajo anterior; sus RN/PO no son requisitos.
- `docs/base_de_datos/01_auditoria_inicial_v1.0.md`, `02_analisis_y_problemas.md`: diagnóstico viejo.
- `docs/entregas/ENTREGA_GIOVANNI.md`: **DESCARTADO (28/10/2026, decisión de Joel) — no usar.**
  El método de copia de la base y de envío del WAR que indicó Giovanni no se usa. **Los pasos de
  entrega se rehacen cuando Joel lo indique**; hasta entonces no hay procedimiento vigente para
  ese destino. No borrar el archivo ni armar pasos nuevos a partir de él.
- `entrega_v1.0-estable/INSTALACION.md`: **DESACTUALIZADO** (contraseñas y servidor viejos).
- `docs/funcionamiento/FLUJO_OPERATIVO.md`, `docs/recorrido/08_dias_siguientes.md`: esbozos casi vacíos.

## 4. Qué actualizar después de cada cambio

| Tipo de cambio | Actualizar |
|---|---|
| Arreglo o mejora visual / texto | `FASE2_PLAN.md` (marcar hecho + commit) y `AVANCE.md` |
| Cambio de lógica en un RF (solo con pedido de Joel) | `funcionamiento/RFxx_*.md`, `AVANCE.md` |
| Cambio en la base (solo con pedido de Joel) | `base_de_datos/03_bitacora_cambios.md` y `04_esquema_actual.md` |
| Hallazgo técnico nuevo o resuelto | `auditoria/AUDITORIA_TECNICA.md` |
| Comportamiento descubierto en pantalla | El tramo de `docs/recorrido/` que corresponda |
| Entrega nueva, contraseña o servidor | `entregas/ENTREGA_SERVIDOR.md` (con fecha de verificación) |
| **Cierre de CUALQUIER ítem (DOGMA 3)** | La skill que corresponda, **en el mismo commit o el inmediato** — y su copia en `docs/skills_respaldo/`, idénticas (`diff`). Si no hay nada que agregar, decirlo en el resumen |
| Fin de sesión o antes de `/clear` | `recorrido/00_LEEME.md` (PUNTO DE RETOMADA) y `AVANCE.md` |

Si se descubre que un documento quedó viejo: marcarlo arriba con **DESACTUALIZADO (fecha)** y
remitir al vigente, en vez de dejarlo como si fuera válido.

## 5. Herramientas

- Skills del proyecto: `sgcpc-cambio-seguro` (antes de tocar código), `sgcpc-diseno-ui` (pantallas),
  `sgcpc-reportes-jasper` (PDF), `ui-ux-pro-max` (solo `search.py` y `design_system.py`),
  `impeccable` (solo texto, sin scripts). Respaldo en `docs/skills_respaldo/`.
- Base de trabajo: `sgcpc_db_pruebas`. **Nunca** `sgcpc_db`.
  - **Cómo consultarla de solo lectura** (verificado el 05/11/2026, B114). `psql` **no está en el
    `PATH`**: vive en `/c/Program Files/PostgreSQL/18/bin` (hay también un 17 instalado). Y
    `bd.properties` trae `bd.usuario=postgres` —es el usuario de la aplicación—, pero **las
    consultas de verificación se hacen como `sgcpc_user`**, que funciona con esa misma contraseña.
    La contraseña **nunca** se escribe en el comando:

    ```bash
    export PATH="/c/Program Files/PostgreSQL/18/bin:$PATH"
    export PGPASSWORD="$(grep '^bd.contrasena=' src/main/resources/configuracion/bd.properties \
        | cut -d= -f2- | tr -d '\r')"          # el tr -d '\r' hace falta: el archivo está en CRLF
    psql -h localhost -U sgcpc_user -d sgcpc_db_pruebas -c "SELECT …"
    ```
  - **Los nombres de columna no se adivinan**: en una sola verificación fallaron
    `parametros_sistema.value` (es `valor_parametro`/`clave_parametro`),
    `prestamos.tasa_interes_anual`, `solicitudes_credito.plazo_meses` (está en `prestamos`) y
    `personas.documento_identidad`/`.cedula` (es `cedula_identidad`). Antes de escribir una
    consulta nueva, mirar `docs/base_de_datos/04_esquema_actual.md` o pedir las columnas a
    `information_schema.columns` — sale más barato que tres consultas con error.
- **La marca `entorno` de `src/main/resources/configuracion/bd.properties` está en
  `produccion` desde el 05/11/2026** (decisión de Joel: este sistema es el que se usa y el que
  se defiende, así que el cartel "BASE DE PRUEBAS" no va más). La base **no** cambió: sigue
  siendo `sgcpc_db_pruebas` — la marca es explícita y nunca se infiere del nombre de la base.
  - **Qué dejó de funcionar**: `CargadorDatosPrueba` y `RestablecerContrasenasPruebas`
    (`src/test/.../cargador/`) **abortan sin tocar nada** si la marca no dice `pruebas`. Es su
    guarda a propósito, no una falla.
  - **Cómo volver a activarlo** si hace falta una de esas dos: poner `entorno=pruebas` en la
    última línea de `bd.properties`, pedirle a Joel un **Clean and Build**, correr la
    herramienta, y **dejarlo de nuevo en `produccion`** con otro Clean and Build. El archivo
    está en `.gitignore`: se edita en la PC de Joel y **nunca se commitea**.
  - **Lo que NO depende de la marca**: `pruebas/humo_http.sh` (su guarda compara el **nombre**
    de la base contra `sgcpc_db_pruebas`, línea 140) ni `mvn test` (ninguna clase `*Test` la
    usa). Lo único visible que saca son las dos píldoras: la de la barra superior
    (`plantilla/cabecera.jsp`) y la del login (`acceso.jsp`).

## 6. Ahorro de tokens

- Una tarea por sesión; al terminar, documentar (tabla 4) y hacer `/clear`.
- Buscar antes de leer; leer secciones, no archivos completos.
- Sin agentes en paralelo salvo autorización escrita de Joel.
- No repetir investigaciones ya documentadas: primero mirar `AVANCE.md` y `FASE2_PLAN.md`.
