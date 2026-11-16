-- SGCPC - 01_esquema.sql
-- Esquema completo: 27 tablas (22 del Doc. 08 + 5 del Doc. 00 Sec. 10.1),
-- con las columnas agregadas de la Sec. 10.2. Compatible con PostgreSQL 12+.
--
-- Convenciones (Doc. 00 Sec. 10 / Doc. 08 Sec. 8.2):
--   * Identificadores: BIGINT GENERATED ALWAYS AS IDENTITY.
--   * Dinero (PYG):     NUMERIC(18,0), entero, sin decimales.
--   * Tasas/porcentajes: NUMERIC(9,6) como FRACCION (0.270000 = 27%). Esta es
--     la definicion del Doc. 00 Sec. 10 (prevalece sobre Doc. 08 Sec. 8.2, que
--     describe NUMERIC(11,6) como porcentaje entero: 24.000000 = 24%). Se deja
--     registrado en AVANCE.md (decision D-11): TODA tasa en la base es una
--     fraccion NUMERIC(9,6); el motor financiero (E1) hace la conversion a %
--     solo para mostrar en pantalla/reportes.
--   * Fechas de negocio: DATE. Eventos/auditoria: TIMESTAMP.
--
-- Las FOREIGN KEY se agregan todas al final (seccion "RESTRICCIONES DE CLAVE
-- FORANEA") para no depender del orden de creacion entre tablas que se
-- referencian mutuamente (p. ej. prestamos <-> solicitudes_credito,
-- planes_pago <-> liquidaciones_anticipadas).

-- D-110 (Bloque 7/E10, Sec. 14.4): zona horaria de la base, siempre America/Asuncion --
-- segunda capa de defensa (la primera es FabricaConexionBD.obtenerConexion, que ya fija
-- "SET TIME ZONE" en cada conexión de la aplicación); esta cubre cualquier otra conexión que
-- no pase por ahí (ej. pgAdmin, psql a mano). Requiere ser dueño de la base (sgcpc_user ya lo
-- es, D-12) -- no requiere permisos de superusuario ni tocar postgresql.conf.
-- D-113: current_database() en vez de "sgcpc_db" fijo -- este mismo script también se corre tal
-- cual contra sgcpc_db_pruebas (y, si hiciera falta, cualquier otra base con este esquema); con
-- el nombre fijo, correrlo ahí alteraba "sgcpc_db" de nuevo en vez de la base real de destino
-- (encontrado corriendo recrear_base.sh contra sgcpc_db_pruebas por primera vez).
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO ''America/Asuncion''', current_database());
END
$$;

-- =====================================================================
-- 1. usuarios
-- =====================================================================
CREATE TABLE usuarios (
    id_usuario               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_usuario            VARCHAR(60)  NOT NULL UNIQUE,
    contrasena_hash           VARCHAR(255) NOT NULL,
    nombres                   VARCHAR(100) NOT NULL,
    apellidos                 VARCHAR(100) NOT NULL,
    cedula_identidad          VARCHAR(20)  NOT NULL UNIQUE,
    perfil_usuario            VARCHAR(30)  NOT NULL
        CHECK (perfil_usuario IN ('OFICIAL_CREDITO', 'GERENTE_ADMINISTRADOR', 'ADMINISTRADOR', 'CAJERO')),
    estado_cuenta              VARCHAR(20)  NOT NULL
        CHECK (estado_cuenta IN ('ACTIVA', 'BLOQUEADA', 'INACTIVA')),
    intentos_fallidos         SMALLINT     NOT NULL DEFAULT 0,
    debe_cambiar_contrasena   BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_ultimo_acceso       TIMESTAMP    NULL,
    fecha_cambio_contrasena   TIMESTAMP    NULL,
    fecha_creacion             TIMESTAMP    NOT NULL,
    creado_por                 BIGINT       NULL,
    fecha_inactivacion        TIMESTAMP    NULL
);

-- =====================================================================
-- 2. parametros_sistema
-- =====================================================================
CREATE TABLE parametros_sistema (
    id_parametro       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_parametro    VARCHAR(80)  NOT NULL,
    nombre_parametro   VARCHAR(150) NOT NULL,
    valor_parametro    VARCHAR(500) NOT NULL,
    tipo_valor         VARCHAR(20)  NOT NULL
        CHECK (tipo_valor IN ('TEXTO', 'ENTERO', 'DECIMAL', 'BOOLEANO', 'FECHA')),
    vigente_desde      DATE      NOT NULL,
    vigente_hasta      DATE      NULL,
    modificado_por     BIGINT    NOT NULL,
    fecha_modificacion TIMESTAMP NOT NULL,
    observacion        VARCHAR(300) NULL
);

-- =====================================================================
-- 3. limites_tasa_usura
-- =====================================================================
CREATE TABLE limites_tasa_usura (
    id_limite_tasa     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vigente_desde      DATE NOT NULL,
    vigente_hasta      DATE NULL,
    tasa_maxima_tea    NUMERIC(9,6) NOT NULL,
    fuente_referencia  VARCHAR(300) NOT NULL,
    registrado_por     BIGINT NOT NULL,
    fecha_registro     TIMESTAMP NOT NULL
);

-- =====================================================================
-- 4. personas (+ columnas Doc. 00 Sec. 10.2)
-- =====================================================================
CREATE TABLE personas (
    id_persona                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cedula_identidad               VARCHAR(20)  NOT NULL UNIQUE,
    ruc                            VARCHAR(20)  NULL,
    nombres                        VARCHAR(100) NOT NULL,
    apellidos                      VARCHAR(100) NOT NULL,
    fecha_nacimiento               DATE         NOT NULL,
    estado_civil                   VARCHAR(30)  NOT NULL,
    telefono_whatsapp              VARCHAR(30)  NOT NULL,
    correo_electronico             VARCHAR(150) NULL,
    estado_persona                 VARCHAR(20) NOT NULL
        CHECK (estado_persona IN ('ACTIVA', 'INACTIVA')),
    registrado_por                 BIGINT NOT NULL,
    fecha_registro                 TIMESTAMP NOT NULL,
    fecha_actualizacion            TIMESTAMP NOT NULL,
    telefono_alternativo           VARCHAR(30) NULL,
    fecha_vencimiento_cedula       DATE NULL
);

-- =====================================================================
-- 4a. personas_referencias (Normalización de personas, grupo Referencias, 15/10/2026)
-- =====================================================================
-- Reemplaza a referencia_nombre/referencia_telefono/referencia2_nombre/referencia2_telefono de
-- personas (grupo repetido, violación de 1FN -- ver docs/base_de_datos/02_analisis_y_problemas.md
-- §1): antes, el límite de 2 referencias era un límite del esquema (2 pares fijos de columnas);
-- ahora es una fila por referencia, sin límite fijo. numero_referencia sigue siendo obligatorio en
-- la aplicación (ServicioClientes exige exactamente las referencias 1 y 2, mismo criterio que
-- antes), pero el esquema ya no lo impone con columnas numeradas -- solo con el UNIQUE de abajo
-- (como mucho una fila por cada número de referencia, por persona). personas (arriba) no tenía
-- ningún CHECK sobre estas 2 columnas, así que no hay ninguno que trasladar acá.
CREATE TABLE personas_referencias (
    id_referencia      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_persona         BIGINT NOT NULL,
    numero_referencia  SMALLINT NOT NULL,
    nombre             VARCHAR(150) NOT NULL,
    telefono           VARCHAR(30) NOT NULL,
    UNIQUE (id_persona, numero_referencia)
);

-- =====================================================================
-- 4b. personas_conyuge (Normalización de personas, grupo Cónyuge, 16/10/2026)
-- =====================================================================
-- Reemplaza a conyuge_nombre/conyuge_cedula/conyuge_telefono de personas. A diferencia de
-- personas_referencias (1-N, siempre 2 filas), esta es 1-1 y OPCIONAL -- a lo sumo una fila por
-- persona (clave compartida: id_persona es PK y FK a la vez, no tiene un id propio), y solo existe
-- si corresponde (ServicioClientes.validarConyuge exige completarla únicamente cuando el estado
-- civil es "Casado/a" o "Unión de hecho" -- ESTADOS_CIVILES_CON_CONYUGE; con otro estado civil, la
-- fila simplemente no existe). personas (arriba) no tenía ningún CHECK sobre estas 3 columnas, así
-- que no hay ninguno que trasladar acá.
CREATE TABLE personas_conyuge (
    id_persona  BIGINT PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    cedula      VARCHAR(20) NOT NULL,
    telefono    VARCHAR(30) NOT NULL
);

-- =====================================================================
-- 4c. personas_baja (Normalización de personas, grupo Baja, 16/10/2026)
-- =====================================================================
-- Reemplaza a motivo_baja/detalle_baja/fecha_baja/dado_de_baja_por de personas. 1-1 (clave
-- compartida, igual que personas_conyuge), pero a diferencia de esa tabla NUNCA se borra al
-- "revertir" (reactivar): conserva el motivo/detalle/fecha/usuario de la ÚLTIMA baja como
-- historial, aunque el cliente ya esté ACTIVA de nuevo (mismo comportamiento que tenía
-- personas.marcarReactivada, probado por pruebas/humo_http.sh desde antes de esta normalización).
-- ServicioClientes.darDeBaja hace upsert (INSERT ... ON CONFLICT (id_persona) DO UPDATE), nunca
-- DELETE; ServicioClientes.reactivar no toca esta tabla en absoluto. El CHECK de motivo_baja
-- viaja igual que estaba en personas.
CREATE TABLE personas_baja (
    id_persona        BIGINT PRIMARY KEY,
    motivo_baja       VARCHAR(30) NOT NULL
        CHECK (motivo_baja IN ('FALLECIMIENTO', 'PEDIDO_CLIENTE', 'DATOS_DUPLICADOS', 'OTRO')),
    detalle_baja      VARCHAR(300) NULL,
    fecha_baja        TIMESTAMP NOT NULL,
    dado_de_baja_por  BIGINT NOT NULL
);

-- =====================================================================
-- 4d. personas_domicilio (Normalización de personas, grupo Domicilio, 16/10/2026)
-- =====================================================================
-- Reemplaza a domicilio/direccion_domicilio/barrio/id_ciudad/id_departamento/
-- referencia_domicilio/tipo_vivienda/antiguedad_domicilio_anios de personas. 1-1 (clave
-- compartida, mismo patrón que personas_conyuge/personas_baja), pero a diferencia de esas dos
-- esta fila es SIEMPRE obligatoria (toda persona tiene un domicilio) -- nunca opcional, nunca
-- historial.
--
-- Sin id_departamento (decisión de Joel, 16/10/2026): se obtiene siempre por JOIN a través de
-- id_ciudad -> ciudades.id_departamento (dependencia transitiva, 3FN -- ver
-- docs/base_de_datos/02_analisis_y_problemas.md §1 y §6). El departamento que el formulario pide
-- (para precalificar y para filtrar el <select> de ciudad en el navegador) nunca se persiste acá
-- -- ServicioClientes valida, al guardar, que la ciudad elegida pertenezca al departamento
-- enviado (igual que hoy, D-126), pero el dato que queda grabado es solo id_ciudad.
--
-- El texto legal compuesto ("domicilio", antes una columna de personas) tampoco se persiste --
-- se arma al vuelo con ServicioClientes.construirTextoDomicilio + un JOIN a ciudades/
-- departamentos, cada vez que un reporte lo necesita (ver ServicioClientes.obtenerTextoDomicilio).
CREATE TABLE personas_domicilio (
    id_persona                   BIGINT PRIMARY KEY,
    id_ciudad                    BIGINT NOT NULL,
    barrio                       VARCHAR(150) NOT NULL,
    direccion_domicilio          VARCHAR(250) NOT NULL,
    referencia_domicilio         VARCHAR(300) NULL,
    tipo_vivienda                VARCHAR(20) NOT NULL
        CHECK (tipo_vivienda IN ('PROPIA', 'ALQUILADA', 'FAMILIAR')),
    antiguedad_domicilio_anios   SMALLINT NOT NULL
);

-- =====================================================================
-- 4e. personas_laborales (Normalización de personas, grupo Laborales, 16/10/2026)
-- =====================================================================
-- Reemplaza a situacion_laboral/empleador_nombre/empleador_ruc/empleador_direccion/
-- empleador_telefono/cargo/antiguedad_laboral_anios/ingreso_neto_comprobado/
-- presenta_comprobante_ingreso/otros_ingresos_mensuales/gastos_fijos_mensuales/
-- cuota_otras_deudas_mensual de personas (12 columnas, mas lugar_trabajo sin reemplazo) --
-- ultimo grupo de la normalizacion, cierra personas en 15 columnas (verificado por SELECT:
-- 28 - 12 - 1). 1-1 (clave compartida, mismo patrón que personas_domicilio):
-- SIEMPRE obligatoria, toda persona tiene datos laborales. lugar_trabajo (columna legado,
-- reemplazada hace tiempo por empleador_direccion) se elimina sin reemplazo -- ya no tenía
-- ningún uso real, solo alimentaba un fallback de pantalla.
--
-- empleador_* quedan NULL en el esquema -- la obligatoriedad condicional (solo si
-- situacion_laboral es ASALARIADO_SECTOR_PRIVADO o FUNCIONARIO_PUBLICO) se valida en
-- ServicioClientes.validarLaboral, igual que antes en personas. El CHECK de situacion_laboral
-- viaja igual que estaba en personas.
CREATE TABLE personas_laborales (
    id_persona                   BIGINT PRIMARY KEY,
    situacion_laboral            VARCHAR(30) NOT NULL
        CHECK (situacion_laboral IN ('ASALARIADO_SECTOR_PRIVADO', 'FUNCIONARIO_PUBLICO',
            'JUBILADO_PENSIONADO', 'INDEPENDIENTE', 'COMERCIANTE', 'EMPLEADO_DOMESTICO',
            'DESEMPLEADO', 'OTRO')),
    empleador_nombre             VARCHAR(150) NULL,
    empleador_ruc                VARCHAR(20) NULL,
    empleador_direccion          VARCHAR(250) NULL,
    empleador_telefono           VARCHAR(30) NULL,
    cargo                        VARCHAR(100) NULL,
    antiguedad_laboral_anios     SMALLINT NULL,
    ingreso_neto_comprobado      NUMERIC(18,0) NOT NULL,
    presenta_comprobante_ingreso BOOLEAN NOT NULL DEFAULT FALSE,
    otros_ingresos_mensuales     NUMERIC(18,0) NULL,
    gastos_fijos_mensuales       NUMERIC(18,0) NULL,
    cuota_otras_deudas_mensual   NUMERIC(18,0) NOT NULL
);

-- =====================================================================
-- 5. archivos_binarios
-- =====================================================================
CREATE TABLE archivos_binarios (
    id_archivo      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_archivo  VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    extension       VARCHAR(10)  NOT NULL,
    tamano_bytes    BIGINT NOT NULL,
    contenido       BYTEA NOT NULL,
    hash_sha256     CHAR(64) NOT NULL,
    origen_archivo  VARCHAR(20) NOT NULL
        CHECK (origen_archivo IN ('CARGADO', 'JASPER')),
    fecha_carga     TIMESTAMP NOT NULL,
    cargado_por     BIGINT NOT NULL
);

-- =====================================================================
-- 6. tipos_documento
-- =====================================================================
CREATE TABLE tipos_documento (
    id_tipo_documento        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_tipo               VARCHAR(50)  NOT NULL UNIQUE,
    nombre_tipo                VARCHAR(120) NOT NULL,
    contexto                   VARCHAR(20) NOT NULL
        CHECK (contexto IN ('PERSONA', 'SOLICITUD', 'PRESTAMO')),
    obligatorio_desembolso    BOOLEAN NOT NULL,
    activo                      BOOLEAN NOT NULL
);

-- =====================================================================
-- 7. documentos_expediente
-- =====================================================================
CREATE TABLE documentos_expediente (
    id_documento                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_archivo                   BIGINT NOT NULL,
    id_tipo_documento            BIGINT NOT NULL,
    id_persona                   BIGINT NULL,
    id_solicitud                 BIGINT NULL,
    id_prestamo                  BIGINT NULL,
    version_documento            SMALLINT NOT NULL DEFAULT 1,
    estado_documento              VARCHAR(20) NOT NULL
        CHECK (estado_documento IN ('VIGENTE', 'REEMPLAZADO', 'CERRADO', 'ANULADO')),
    id_documento_reemplazado     BIGINT NULL,
    fecha_documento               DATE NULL,
    fecha_cierre                  TIMESTAMP NULL,
    cerrado_por                   BIGINT NULL,
    observacion                   VARCHAR(500) NULL,
    CHECK (id_persona IS NOT NULL OR id_solicitud IS NOT NULL OR id_prestamo IS NOT NULL)
);

-- =====================================================================
-- 8. consentimientos_crediticios
-- =====================================================================
CREATE TABLE consentimientos_crediticios (
    id_consentimiento        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_persona                BIGINT NOT NULL,
    id_solicitud               BIGINT NULL,
    id_documento_evidencia     BIGINT NOT NULL,
    finalidad                   VARCHAR(250) NOT NULL,
    fecha_consentimiento       TIMESTAMP NOT NULL,
    registrado_por              BIGINT NOT NULL,
    fecha_registro               TIMESTAMP NOT NULL
);

-- =====================================================================
-- 9. consultas_crediticias
-- =====================================================================
-- D-39 (E3): id_solicitud pasa de NOT NULL a NULL. Doc. 08 la definia
-- obligatoria, pero RN-24/PO-02 registran el resultado de la consulta al
-- buro durante el registro del cliente (P03), antes de que exista ninguna
-- solicitud (eso es P04/E4). id_persona sigue NOT NULL: la consulta siempre
-- pertenece a una persona. Como se vincula esta consulta con la solicitud
-- que se cree despues queda pendiente para el plan de E4 (ver AVANCE.md).
CREATE TABLE consultas_crediticias (
    id_consulta_crediticia     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_persona                  BIGINT NOT NULL,
    id_solicitud                 BIGINT NULL,
    id_consentimiento            BIGINT NOT NULL,
    proveedor_buro               VARCHAR(80) NOT NULL,
    fecha_hora_consulta          TIMESTAMP NOT NULL,
    consultado_por                BIGINT NOT NULL,
    puntaje_obtenido              NUMERIC(10,2) NULL,
    clasificacion_obtenida       VARCHAR(100) NULL,
    indicador_mora                BOOLEAN NULL,
    resumen_resultado             TEXT NULL,
    id_documento_resultado        BIGINT NULL
);

-- =====================================================================
-- 10. solicitudes_credito
-- =====================================================================
CREATE TABLE solicitudes_credito (
    id_solicitud                            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_solicitud                         VARCHAR(40) NOT NULL UNIQUE,
    id_cliente                               BIGINT NOT NULL,
    creado_por                               BIGINT NOT NULL,
    fecha_solicitud                          TIMESTAMP NOT NULL,
    monto_solicitado                         NUMERIC(18,0) NOT NULL,
    plazo_solicitado_meses                   SMALLINT NOT NULL,
    destino_credito                          VARCHAR(250) NOT NULL,
    primera_fecha_vencimiento_solicitada     DATE NOT NULL,
    tasa_tna_propuesta                       NUMERIC(9,6) NOT NULL,
    tasa_tea_simulada                        NUMERIC(9,6) NOT NULL,
    cft_simulado                             NUMERIC(9,6) NULL,
    cuota_simulada                           NUMERIC(18,0) NOT NULL,
    ingreso_cliente_evaluado                 NUMERIC(18,0) NOT NULL,
    limite_afectacion_aplicado               NUMERIC(7,4) NOT NULL,
    porcentaje_afectacion                    NUMERIC(7,4) NOT NULL,
    estado_solicitud                          VARCHAR(30) NOT NULL
        CHECK (estado_solicitud IN ('BORRADOR', 'PENDIENTE_APROBACION', 'APROBADA', 'RECHAZADA', 'CANCELADA')),
    fecha_envio_aprobacion                    TIMESTAMP NULL,
    monto_aprobado                            NUMERIC(18,0) NULL,
    plazo_aprobado_meses                      SMALLINT NULL,
    tasa_tna_aprobada                         NUMERIC(9,6) NULL,
    tasa_tea_aprobada                         NUMERIC(9,6) NULL,
    cft_aprobado                              NUMERIC(9,6) NULL,
    primera_fecha_vencimiento_aprobada        DATE NULL,
    id_limite_tasa_aplicado                   BIGINT NULL,
    decidido_por                              BIGINT NULL,
    fecha_decision                            TIMESTAMP NULL,
    motivo_rechazo                            TEXT NULL,
    cancelado_por                             BIGINT NULL,
    fecha_cancelacion                         TIMESTAMP NULL,
    motivo_cancelacion                        TEXT NULL,
    -- E5/D-68: una solicitud APROBADA no cambia de estado al formalizarse (no
    -- hay un estado "FORMALIZADA"), asi que sin esta columna RN-02 (y el
    -- indice unico de mas abajo) la seguirian considerando "abierta" para
    -- siempre. ServicioFormalizacion la pone en TRUE en la misma transaccion
    -- que crea el prestamo.
    formalizada                               BOOLEAN NOT NULL DEFAULT FALSE,
    -- Bloque 7/E10, Tanda A (D-126): motivo_rechazo (arriba) pasa a ser el
    -- "detalle" (nombre del motivo elegido + detalle libre, combinados) -- el
    -- motivo en si es referencial, ver motivos_rechazo_solicitud mas abajo.
    -- Al final de la tabla (no junto a motivo_rechazo): la migracion la agrega
    -- con ALTER TABLE ... ADD COLUMN, que siempre la deja al final -- para que
    -- bd/comparar_esquemas.sh compare column por column en el mismo orden.
    id_motivo_rechazo                         BIGINT NULL
);

-- =====================================================================
-- 11. prestamos (+ columnas nuevas Doc. 00 Sec. 10.2: id_limite_tasa,
--     tasa_mensual, cuota_contractual. No se repiten numero_operacion/tea/
--     fecha_cierre_documental: ya existen como identificador_operacion,
--     tasa_tea y fecha_cierre_documental. Ver AVANCE.md decision D-11.)
-- =====================================================================
CREATE TABLE prestamos (
    id_prestamo                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador_operacion               VARCHAR(50) NOT NULL UNIQUE,
    id_solicitud                          BIGINT UNIQUE NULL,
    numero_contrato                       VARCHAR(50) NULL UNIQUE,
    numero_pagare                         VARCHAR(50) NOT NULL UNIQUE,
    monto_aprobado                        NUMERIC(18,0) NOT NULL,
    plazo_meses                           SMALLINT NOT NULL,
    tasa_tna                              NUMERIC(9,6) NOT NULL,
    tasa_tea                              NUMERIC(9,6) NOT NULL,
    cft                                   NUMERIC(9,6) NULL,
    tasa_moratoria                        NUMERIC(9,6) NOT NULL,
    tasa_punitoria                        NUMERIC(9,6) NOT NULL,
    primera_fecha_vencimiento             DATE NOT NULL,
    fecha_formalizacion                   TIMESTAMP NOT NULL,
    -- D-23 (ServicioMotorFinanciero, E1): inicio del periodo de la cuota 1
    -- para prorratear interes en cancelacion/anticipo. Null mientras el
    -- prestamo esta PENDIENTE_DESEMBOLSO; se completa con la fecha del
    -- desembolso CONFIRMADO (espejo de desembolsos.fecha_hora_desembolso,
    -- guardado aca para no tener que hacer join cada vez).
    fecha_desembolso                       DATE NULL,
    estado_prestamo                        VARCHAR(35) NOT NULL
        CHECK (estado_prestamo IN ('PENDIENTE_DESEMBOLSO', 'ACTIVO', 'EN_MORA', 'FINALIZADO', 'ANULADO_ANTES_DESEMBOLSO')),
    fecha_finalizacion_financiera         TIMESTAMP NULL,
    motivo_anulacion_sin_desembolso       TEXT NULL,
    fecha_anulacion_sin_desembolso        TIMESTAMP NULL,
    anulado_por                            BIGINT NULL,
    estado_cierre_documental               VARCHAR(20) NOT NULL
        CHECK (estado_cierre_documental IN ('PENDIENTE', 'COMPLETO', 'NO_APLICA')),
    fecha_cierre_documental                TIMESTAMP NULL,
    cierre_documental_por                  BIGINT NULL,
    formalizado_por                        BIGINT NOT NULL,
    -- Doc. 00 Sec. 10.2 (columnas genuinamente nuevas)
    id_limite_tasa                         BIGINT NULL,
    tasa_mensual                           NUMERIC(9,6) NOT NULL,
    cuota_contractual                      NUMERIC(18,0) NOT NULL,
    -- E9/Bloque 5 (P10, cierre documental): mismo patrón que numero_contrato/numero_pagare -- el
    -- PDF en sí viaja por documentos_expediente (DEVOLUCION_PAGARE_GENERADO/_FIRMADO).
    numero_constancia_devolucion            VARCHAR(30) NULL UNIQUE,
    -- Bloque 7/E10 (D-130, pedido de Joel): comprobante de desembolso impreso y firmado ANTES de
    -- entregar el efectivo -- el número se asigna al imprimir "para firma", con el préstamo
    -- todavía PENDIENTE_DESEMBOLSO (la fila de desembolsos, que también tiene numero_comprobante
    -- desde E8, todavía no existe en ese momento).
    numero_comprobante_desembolso           VARCHAR(20) NULL UNIQUE
);

-- =====================================================================
-- 13. planes_pago
-- =====================================================================
CREATE TABLE planes_pago (
    id_plan_pago            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prestamo               BIGINT NOT NULL,
    version_plan               SMALLINT NOT NULL,
    origen_plan                 VARCHAR(25) NOT NULL
        CHECK (origen_plan IN ('ORIGINAL', 'ANTICIPO_PARCIAL')),
    vigente                     BOOLEAN NOT NULL,
    id_liquidacion_origen       BIGINT NULL,
    fecha_generacion             TIMESTAMP NOT NULL,
    generado_por                 BIGINT NOT NULL
);

-- =====================================================================
-- 14. cuotas (+ columnas nuevas Doc. 00 Sec. 10.2)
-- =====================================================================
CREATE TABLE cuotas (
    id_cuota                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_plan_pago                 BIGINT NOT NULL,
    numero_cuota                  SMALLINT NOT NULL,
    fecha_vencimiento             DATE NOT NULL,
    -- D-23 (ServicioMotorFinanciero, E1): fecha de vencimiento SIN el
    -- corrimiento de RN-19 por feriado/fin de semana. fecha_vencimiento (de
    -- arriba) es la real (con el corrimiento), la que ve el cliente y la que
    -- usa la mora (Sec. 6.4). fecha_vencimiento_teorica es solo para
    -- prorratear interes en cancelacion total/anticipo parcial (Sec. 6.6/6.7),
    -- para que un feriado no distorsione el prorrateo.
    fecha_vencimiento_teorica     DATE NOT NULL,
    capital_programado            NUMERIC(18,0) NOT NULL,
    interes_programado            NUMERIC(18,0) NOT NULL,
    tributos_programados          NUMERIC(18,0) NOT NULL,
    total_programado              NUMERIC(18,0) NOT NULL,
    saldo_capital_posterior       NUMERIC(18,0) NOT NULL,
    estado_pago                    VARCHAR(20) NOT NULL
        CHECK (estado_pago IN ('PENDIENTE', 'PARCIAL', 'PAGADA')),
    -- Doc. 00 Sec. 10.2
    iva_interes                    NUMERIC(18,0) NOT NULL DEFAULT 0,
    moratorio_pendiente            NUMERIC(18,0) NOT NULL DEFAULT 0,
    punitorio_pendiente            NUMERIC(18,0) NOT NULL DEFAULT 0,
    -- D-24: reemplaza a iva_mora_pendiente (columna unica) para que la base
    -- coincida con el modelo Cuota de ServicioMotorFinanciero (E1), que ya
    -- separa el IVA de moratorio y de punitorio para aplicar el orden de
    -- RN-10 sin ambiguedad.
    iva_moratorio_pendiente        NUMERIC(18,0) NOT NULL DEFAULT 0,
    iva_punitorio_pendiente        NUMERIC(18,0) NOT NULL DEFAULT 0,
    fecha_corte_mora                DATE NULL,
    capital_pagado                  NUMERIC(18,0) NOT NULL DEFAULT 0,
    interes_pagado                  NUMERIC(18,0) NOT NULL DEFAULT 0,
    -- Integridad (17/10/2026, auditoría de toda la base, §7 grupo C): mismo criterio que
    -- pagos/aplicaciones_pago -- ServicioMotorFinanciero siempre arma total_programado como la
    -- suma directa de los otros 3 (nunca un cálculo aparte), verificado contra los 2 únicos
    -- lugares que crean una Cuota antes de agregar este CHECK.
    CHECK (total_programado = capital_programado + interes_programado + tributos_programados)
);

-- =====================================================================
-- 15. liquidaciones_anticipadas
-- =====================================================================
CREATE TABLE liquidaciones_anticipadas (
    id_liquidacion             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prestamo                  BIGINT NOT NULL,
    tipo_liquidacion              VARCHAR(20) NOT NULL
        CHECK (tipo_liquidacion IN ('PARCIAL', 'TOTAL')),
    fecha_calculo                  TIMESTAMP NOT NULL,
    saldo_capital_fecha            NUMERIC(18,0) NOT NULL,
    interes_devengado               NUMERIC(18,0) NOT NULL,
    tributos_aplicables              NUMERIC(18,0) NOT NULL,
    interes_moratorio                NUMERIC(18,0) NOT NULL,
    interes_punitorio                NUMERIC(18,0) NOT NULL,
    monto_total_liquidacion          NUMERIC(18,0) NOT NULL,
    monto_anticipo_capital           NUMERIC(18,0) NULL,
    nuevo_saldo_capital               NUMERIC(18,0) NULL,
    estado_liquidacion                 VARCHAR(20) NOT NULL
        -- E9/Bloque 5: REVERTIDA -- ni el anticipo ni la cancelación se borran al revertirse.
        CHECK (estado_liquidacion IN ('GENERADA', 'APLICADA', 'CANCELADA', 'REVERTIDA')),
    calculado_por                       BIGINT NOT NULL,
    fecha_aplicacion                     TIMESTAMP NULL,
    id_archivo_liquidacion               BIGINT NULL,
    -- E8 (bd/migraciones/E8_01_comprobantes.sql, D-93): constancia de cancelación total (CAN-
    -- aaaa-nnnnnn, solo para tipo_liquidacion='TOTAL', NULL en las PARCIAL) y el desglose de
    -- "cuotas vencidas impagas (con mora e IVA)" que calcularCancelacionTotal ya calcula pero
    -- antes se descartaba al persistir -- necesario para reimprimir la constancia sin recalcular
    -- cuando la cancelación tuvo cuotas vencidas.
    numero_constancia                    VARCHAR(30) NULL UNIQUE,
    monto_vencidas_impagas               NUMERIC(18,0) NOT NULL DEFAULT 0,
    iva_moratorio                        NUMERIC(18,0) NOT NULL DEFAULT 0,
    iva_punitorio                        NUMERIC(18,0) NOT NULL DEFAULT 0
);

-- =====================================================================
-- 16. sesiones_caja (+ columnas nuevas Doc. 00 Sec. 10.2)
-- =====================================================================
CREATE TABLE sesiones_caja (
    id_sesion_caja          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cajero                 BIGINT NOT NULL,
    fecha_hora_apertura        TIMESTAMP NOT NULL,
    saldo_inicial               NUMERIC(18,0) NOT NULL,
    fecha_hora_cierre           TIMESTAMP NULL,
    saldo_teorico_cierre        NUMERIC(18,0) NULL,
    saldo_fisico                 NUMERIC(18,0) NULL,
    diferencia_arqueo             NUMERIC(18,0) NULL,
    estado_sesion                  VARCHAR(20) NOT NULL
        CHECK (estado_sesion IN ('ABIERTA', 'CERRADA')),
    id_archivo_cierre              BIGINT NULL,
    -- Doc. 00 Sec. 10.2 (columnas genuinamente nuevas)
    motivo_diferencia              VARCHAR(300) NULL,
    id_gerente_revisor              BIGINT NULL
);

-- =====================================================================
-- 17. desembolsos
-- =====================================================================
CREATE TABLE desembolsos (
    id_desembolso                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador_confirmacion    VARCHAR(60) NOT NULL UNIQUE,
    id_prestamo                    BIGINT NOT NULL,
    id_sesion_caja                  BIGINT NOT NULL,
    monto_aprobado_snapshot          NUMERIC(18,0) NOT NULL,
    monto_neto_entregado               NUMERIC(18,0) NOT NULL,
    fecha_hora_desembolso               TIMESTAMP NOT NULL,
    desembolsado_por                    BIGINT NOT NULL,
    estado_desembolso                    VARCHAR(20) NOT NULL
        CHECK (estado_desembolso IN ('CONFIRMADO', 'REVERTIDO')),
    id_archivo_comprobante                BIGINT NULL,
    -- E8 (bd/migraciones/E8_01_comprobantes.sql, D-93): comprobante de desembolso (DES-aaaa-
    -- nnnnnn). NULLABLE (no NOT NULL): el código recién lo completa cuando se construye el
    -- comprobante (E8 Bloque 2); dejarlo obligatorio desde el esquema rompería cualquier
    -- desembolso real hecho antes de que ese código exista.
    numero_comprobante                    VARCHAR(30) NULL UNIQUE,
    -- Integridad (17/10/2026, auditoría de toda la base, §7 grupo C): mismo criterio que
    -- pagos/aplicaciones_pago -- ServicioDesembolso.confirmarDesembolso siempre arma
    -- monto_neto_entregado como monto_aprobado_snapshot (RN-17: sin comisiones ni gastos,
    -- monto_deducciones eliminada en la etapa de refactor del 18/10/2026, ver
    -- docs/base_de_datos/02_analisis_y_problemas.md).
    CHECK (monto_neto_entregado = monto_aprobado_snapshot)
);

-- =====================================================================
-- 19. pagos (+ columna nueva Doc. 00 Sec. 10.2: tipo. No se repiten
--     clave_idempotencia/efectivo_recibido/vuelto/monto_aplicado: ya
--     existen como identificador_confirmacion, monto_recibido,
--     monto_vuelto y monto_aplicado. Ver AVANCE.md decision D-11.)
-- =====================================================================
CREATE TABLE pagos (
    id_pago                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador_confirmacion      VARCHAR(60) NOT NULL UNIQUE,
    id_prestamo                      BIGINT NOT NULL,
    id_sesion_caja                    BIGINT NOT NULL,
    id_liquidacion_anticipada          BIGINT NULL,
    numero_recibo                       VARCHAR(50) NOT NULL UNIQUE,
    fecha_hora_pago                      TIMESTAMP NOT NULL,
    monto_recibido                        NUMERIC(18,0) NOT NULL,
    monto_vuelto                           NUMERIC(18,0) NOT NULL DEFAULT 0,
    monto_aplicado                          NUMERIC(18,0) NOT NULL,
    estado_pago                              VARCHAR(20) NOT NULL
        CHECK (estado_pago IN ('CONFIRMADO', 'REVERTIDO')),
    registrado_por                            BIGINT NOT NULL,
    id_archivo_recibo                          BIGINT NULL,
    -- Doc. 00 Sec. 10.2
    tipo                                        VARCHAR(20) NOT NULL
        CHECK (tipo IN ('NORMAL', 'PARCIAL', 'CANCELACION', 'ANTICIPO')),
    -- D-141 (Bloque 7/E10, SEDECO, pedido de Joel): descuento a favor del cliente cuando el
    -- efectivo se redondea hacia abajo (parámetro credito.redondeo_efectivo) -- la deuda queda
    -- saldada por monto_aplicado (el exacto) aunque lo realmente recibido sea menos.
    monto_redondeo                           NUMERIC(18,0) NOT NULL DEFAULT 0,
    CHECK (monto_recibido + monto_redondeo = monto_aplicado + monto_vuelto),
    CHECK (monto_recibido >= 0 AND monto_vuelto >= 0 AND monto_aplicado >= 0 AND monto_redondeo >= 0)
);

-- =====================================================================
-- 20. aplicaciones_pago
-- =====================================================================
CREATE TABLE aplicaciones_pago (
    id_aplicacion_pago     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pago                  BIGINT NOT NULL,
    tipo_aplicacion            VARCHAR(25) NOT NULL
        CHECK (tipo_aplicacion IN ('CUOTA', 'ANTICIPO_CAPITAL')),
    id_cuota                    BIGINT NULL,
    monto_moratorio              NUMERIC(18,0) NOT NULL DEFAULT 0,
    monto_punitorio               NUMERIC(18,0) NOT NULL DEFAULT 0,
    monto_interes                  NUMERIC(18,0) NOT NULL DEFAULT 0,
    monto_tributos                  NUMERIC(18,0) NOT NULL DEFAULT 0,
    monto_capital                    NUMERIC(18,0) NOT NULL DEFAULT 0,
    subtotal_aplicado                 NUMERIC(18,0) NOT NULL,
    CHECK (
        (tipo_aplicacion = 'CUOTA' AND id_cuota IS NOT NULL)
        OR (tipo_aplicacion = 'ANTICIPO_CAPITAL' AND id_cuota IS NULL)
    ),
    CHECK (subtotal_aplicado = monto_moratorio + monto_punitorio + monto_interes + monto_tributos + monto_capital)
);

-- =====================================================================
-- 21. reversiones_pago (+ columnas nuevas E9/Bloque 5: fase "pendiente de
--     autorizar", Doc. 00 §7 SOLICITADA/AUTORIZADA/RECHAZADA -- las
--     columnas de ejecución quedan NULL hasta que el gerente autoriza,
--     ver chk_reversion_pago_consistencia más abajo.
--     factura_en_poder_confirmado: DNIT RG 118/2017 / Decreto 6539/05 --
--     confirmación obligatoria SOLO si la operación revertida tenía
--     factura, antes de anularla.)
-- =====================================================================
CREATE TABLE reversiones_pago (
    id_reversion_pago            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador_confirmacion    VARCHAR(60) NOT NULL UNIQUE,
    id_pago                        BIGINT UNIQUE NOT NULL,
    motivo_reversion                 VARCHAR(500) NOT NULL,
    fecha_hora_reversion              TIMESTAMP NULL,
    autorizado_por                     BIGINT NULL,
    procesado_por                       BIGINT NULL,
    id_sesion_caja                       BIGINT NULL,
    monto_salida_caja                     NUMERIC(18,0) NULL,
    id_archivo_comprobante                 BIGINT NULL,
    -- E9/Bloque 5
    estado                                   VARCHAR(20) NOT NULL
        CHECK (estado IN ('SOLICITADA', 'AUTORIZADA', 'RECHAZADA')),
    solicitado_por                            BIGINT NOT NULL,
    fecha_hora_solicitud                       TIMESTAMP NOT NULL,
    motivo_rechazo                              VARCHAR(500) NULL,
    factura_en_poder_confirmado                  BOOLEAN NULL,
    -- D-113: nombrada igual que bd/migraciones/E9_01_reversiones.sql (chk_reversion_pago_
    -- consistencia) -- sin nombre explícito, Postgres le pone un nombre por defecto distinto
    -- según cómo se creó la tabla, y sgcpc_db (creada por la migración) terminaba con un nombre
    -- de constraint distinto al de una base nueva (creada por este archivo). Encontrado con
    -- bd/comparar_esquemas.sh, primera corrida.
    CONSTRAINT chk_reversion_pago_consistencia CHECK (
        (estado = 'SOLICITADA' AND autorizado_por IS NULL AND procesado_por IS NULL
            AND id_sesion_caja IS NULL AND monto_salida_caja IS NULL AND fecha_hora_reversion IS NULL)
        OR (estado = 'AUTORIZADA' AND autorizado_por IS NOT NULL AND procesado_por IS NOT NULL
            AND id_sesion_caja IS NOT NULL AND monto_salida_caja IS NOT NULL AND fecha_hora_reversion IS NOT NULL)
        OR (estado = 'RECHAZADA' AND motivo_rechazo IS NOT NULL)
    )
);

-- =====================================================================
-- 22. auditoria_operaciones
-- =====================================================================
-- Bloque de seguridad (hallazgo de Joel, decisión nueva -- cambia D-27): id_usuario pasa a ser
-- NULL-able para poder auditar un intento de acceso con un nombre de usuario que NO existe (antes
-- D-27 decía que no había ninguna cuenta real a la que atribuirle esa fila, así que no se
-- auditaba nada; ahora se audita con id_usuario NULL y usuario_escrito con el texto tal cual se
-- tipeó, recortado). navegador guarda el User-Agent, mismo criterio que direccion_ip.
CREATE TABLE auditoria_operaciones (
    id_auditoria     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario         BIGINT NULL,
    fecha_hora           TIMESTAMP NOT NULL,
    operacion              VARCHAR(80) NOT NULL,
    tipo_entidad             VARCHAR(60) NOT NULL,
    id_entidad                BIGINT NOT NULL,
    valor_anterior              JSONB NULL,
    valor_nuevo                   JSONB NULL,
    direccion_ip                   VARCHAR(64) NULL,
    navegador                        VARCHAR(255) NULL,
    usuario_escrito                   VARCHAR(60) NULL,
    detalle                          TEXT NULL
);

-- =====================================================================
-- 23. movimientos_caja (Doc. 00 Sec. 10.1)
-- Nombres de columna normalizados a la convencion "id_<entidad>" /
-- "<verbo>_por" usada en las 22 tablas del Doc. 08 (Doc. 00 escribia
-- "sesion_caja_id", "usuario_registra_id", etc. con sufijo "_id"; ver
-- AVANCE.md decision D-11).
-- =====================================================================
CREATE TABLE movimientos_caja (
    id_movimiento_caja   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_sesion_caja         BIGINT NOT NULL,
    tipo                     VARCHAR(20) NOT NULL
        CHECK (tipo IN ('ENTREGA_GERENTE', 'RENDICION_GERENTE', 'DESEMBOLSO', 'COBRO', 'VUELTO', 'COMPENSACION')),
    monto                     NUMERIC(18,0) NOT NULL,
    signo                       SMALLINT NOT NULL CHECK (signo IN (1, -1)),
    referencia_tipo               VARCHAR(30) NOT NULL,
    id_referencia                  BIGINT NOT NULL,
    registrado_por                   BIGINT NOT NULL,
    confirmado_por                    BIGINT NULL,
    fecha_hora                          TIMESTAMP NOT NULL,
    observacion                          VARCHAR(300) NULL
);

-- =====================================================================
-- 24. gestiones_cobranza (Doc. 00 Sec. 10.1)
-- =====================================================================
CREATE TABLE gestiones_cobranza (
    id_gestion_cobranza     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prestamo                BIGINT NOT NULL,
    id_persona_contactada        BIGINT NOT NULL,
    tipo                            VARCHAR(20) NOT NULL
        CHECK (tipo IN ('LLAMADA', 'MENSAJE', 'VISITA_LOCAL', 'PAGO_PARCIAL')),
    resultado                          VARCHAR(200) NULL,
    excusa                                VARCHAR(300) NULL,
    fecha_promesa                          DATE NULL,
    monto_prometido                          NUMERIC(18,0) NULL,
    cumplida                                   BOOLEAN NOT NULL DEFAULT FALSE,
    registrado_por                               BIGINT NOT NULL,
    fecha_hora                                     TIMESTAMP NOT NULL,
    -- E8 (bd/migraciones/E8_02_gestion_pago.sql, D-95): qué pago parcial generó esta gestión (solo
    -- las de tipo PAGO_PARCIAL la tienen) -- para que el recibo de ese pago pueda mostrar la
    -- promesa sin adivinar por cercanía de fecha.
    id_pago                                        BIGINT NULL
);

-- =====================================================================
-- 25. feriados (Doc. 00 Sec. 10.1)
-- =====================================================================
CREATE TABLE feriados (
    id_feriado     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha            DATE NOT NULL UNIQUE,
    descripcion        VARCHAR(150) NOT NULL,
    activo               BOOLEAN NOT NULL DEFAULT TRUE
);

-- =====================================================================
-- 26. facturas (Doc. 00 Sec. 10.1)
-- =====================================================================
CREATE TABLE facturas (
    id_factura           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_pago                 BIGINT NOT NULL,
    timbrado                  VARCHAR(20) NOT NULL,
    establecimiento             VARCHAR(5) NOT NULL,
    punto_expedicion              VARCHAR(5) NOT NULL,
    numero                          VARCHAR(10) NOT NULL,
    fecha                             DATE NOT NULL,
    cliente_nombre                     VARCHAR(200) NOT NULL,
    cliente_documento                    VARCHAR(20) NOT NULL,
    total_exenta                           NUMERIC(18,0) NOT NULL DEFAULT 0,
    total_gravada_10                         NUMERIC(18,0) NOT NULL DEFAULT 0,
    iva_10                                     NUMERIC(18,0) NOT NULL DEFAULT 0,
    total                                        NUMERIC(18,0) NOT NULL,
    estado                                        VARCHAR(20) NOT NULL DEFAULT 'EMITIDA'
        CHECK (estado IN ('EMITIDA', 'ANULADA')),
    id_archivo                                     BIGINT NULL,
    id_reversion_pago                                BIGINT NULL,
    -- E9/Bloque 5: la Nota de anulación es un comprobante propio, ligado a la factura anulada
    -- (mismo patrón que liquidaciones_anticipadas.numero_constancia/id_archivo_liquidacion).
    numero_nota_anulacion                             VARCHAR(30) NULL UNIQUE,
    id_archivo_nota_anulacion                          BIGINT NULL,
    UNIQUE (timbrado, establecimiento, punto_expedicion, numero)
);

-- =====================================================================
-- 27. numeradores (Doc. 00 Sec. 10.1) - clave natural (clave, anio)
-- =====================================================================
CREATE TABLE numeradores (
    clave         VARCHAR(20) NOT NULL
        -- E8 (bd/migraciones/E8_01_comprobantes.sql, D-93): CONSTANCIA (constancia de cancelación
        -- total) y DESEMBOLSO (comprobante de desembolso), mismo patrón (clave, año) que RECIBO.
        -- E9 (bd/migraciones/E9_01_reversiones.sql): ANULACION (nota de anulación de factura) y
        -- DEVOLUCION (constancia de devolución de pagaré) -- claves propias, no reusan CONSTANCIA,
        -- para no dejar saltos en la numeración CAN- de las constancias de cancelación.
        -- Bloque 7/E10 (bd/migraciones/E10_08_numerador_autorizacion.sql): AUTORIZACION (número de
        -- la autorización de consulta al buró, RF03/RF04).
        CHECK (clave IN ('FACTURA', 'OPERACION', 'RECIBO', 'SOLICITUD', 'CONSTANCIA', 'DESEMBOLSO', 'ANULACION', 'DEVOLUCION', 'AUTORIZACION')),
    anio           SMALLINT NOT NULL,
    ultimo_valor    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (clave, anio)
);

-- =====================================================================
-- 28. departamentos (Bloque 7/E10, Tanda A, D-126) - catalogo de
--     departamentos de Paraguay, semilla en bd/03_parametros.sql
-- =====================================================================
CREATE TABLE departamentos (
    id_departamento  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(60) NOT NULL UNIQUE,
    activo           BOOLEAN NOT NULL DEFAULT TRUE
);

-- =====================================================================
-- 29. ciudades (Bloque 7/E10, Tanda A, D-126) - catalogo de ciudades/
--     distritos, semilla en bd/03_parametros.sql
-- =====================================================================
CREATE TABLE ciudades (
    id_ciudad         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_departamento   BIGINT NOT NULL,
    nombre            VARCHAR(80) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (id_departamento, nombre)
);

-- =====================================================================
-- 30. motivos_rechazo_solicitud (Bloque 7/E10, Tanda A, D-126) - lista
--     referencial de motivos de rechazo (P05), semilla en
--     bd/03_parametros.sql
-- =====================================================================
CREATE TABLE motivos_rechazo_solicitud (
    id_motivo_rechazo  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR(30)  NOT NULL UNIQUE,
    nombre              VARCHAR(100) NOT NULL,
    requiere_detalle    BOOLEAN NOT NULL DEFAULT FALSE,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    orden               SMALLINT NOT NULL
);

-- =====================================================================
-- RESTRICCIONES DE CLAVE FORANEA
-- =====================================================================

ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_creado_por
    FOREIGN KEY (creado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE parametros_sistema ADD CONSTRAINT fk_parametros_modificado_por
    FOREIGN KEY (modificado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE limites_tasa_usura ADD CONSTRAINT fk_limites_tasa_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE personas ADD CONSTRAINT fk_personas_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE ciudades ADD CONSTRAINT fk_ciudades_departamento
    FOREIGN KEY (id_departamento) REFERENCES departamentos (id_departamento);

ALTER TABLE personas_referencias ADD CONSTRAINT fk_personas_referencias_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);

ALTER TABLE personas_conyuge ADD CONSTRAINT fk_personas_conyuge_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);

ALTER TABLE personas_baja ADD CONSTRAINT fk_personas_baja_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);
ALTER TABLE personas_baja ADD CONSTRAINT fk_personas_baja_usuario
    FOREIGN KEY (dado_de_baja_por) REFERENCES usuarios (id_usuario);

ALTER TABLE personas_domicilio ADD CONSTRAINT fk_personas_domicilio_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);
ALTER TABLE personas_domicilio ADD CONSTRAINT fk_personas_domicilio_ciudad
    FOREIGN KEY (id_ciudad) REFERENCES ciudades (id_ciudad);

ALTER TABLE personas_laborales ADD CONSTRAINT fk_personas_laborales_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);

ALTER TABLE archivos_binarios ADD CONSTRAINT fk_archivos_cargado_por
    FOREIGN KEY (cargado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_archivo
    FOREIGN KEY (id_archivo) REFERENCES archivos_binarios (id_archivo);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_tipo
    FOREIGN KEY (id_tipo_documento) REFERENCES tipos_documento (id_tipo_documento);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_solicitud
    FOREIGN KEY (id_solicitud) REFERENCES solicitudes_credito (id_solicitud);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_reemplazado
    FOREIGN KEY (id_documento_reemplazado) REFERENCES documentos_expediente (id_documento);
ALTER TABLE documentos_expediente ADD CONSTRAINT fk_documentos_cerrado_por
    FOREIGN KEY (cerrado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE consentimientos_crediticios ADD CONSTRAINT fk_consentimientos_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);
ALTER TABLE consentimientos_crediticios ADD CONSTRAINT fk_consentimientos_solicitud
    FOREIGN KEY (id_solicitud) REFERENCES solicitudes_credito (id_solicitud);
ALTER TABLE consentimientos_crediticios ADD CONSTRAINT fk_consentimientos_documento
    FOREIGN KEY (id_documento_evidencia) REFERENCES documentos_expediente (id_documento);
ALTER TABLE consentimientos_crediticios ADD CONSTRAINT fk_consentimientos_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE consultas_crediticias ADD CONSTRAINT fk_consultas_persona
    FOREIGN KEY (id_persona) REFERENCES personas (id_persona);
ALTER TABLE consultas_crediticias ADD CONSTRAINT fk_consultas_solicitud
    FOREIGN KEY (id_solicitud) REFERENCES solicitudes_credito (id_solicitud);
ALTER TABLE consultas_crediticias ADD CONSTRAINT fk_consultas_consentimiento
    FOREIGN KEY (id_consentimiento) REFERENCES consentimientos_crediticios (id_consentimiento);
ALTER TABLE consultas_crediticias ADD CONSTRAINT fk_consultas_consultado_por
    FOREIGN KEY (consultado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE consultas_crediticias ADD CONSTRAINT fk_consultas_documento_resultado
    FOREIGN KEY (id_documento_resultado) REFERENCES documentos_expediente (id_documento);

ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_cliente
    FOREIGN KEY (id_cliente) REFERENCES personas (id_persona);
ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_creado_por
    FOREIGN KEY (creado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_limite_tasa
    FOREIGN KEY (id_limite_tasa_aplicado) REFERENCES limites_tasa_usura (id_limite_tasa);
ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_decidido_por
    FOREIGN KEY (decidido_por) REFERENCES usuarios (id_usuario);
ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_cancelado_por
    FOREIGN KEY (cancelado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_motivo_rechazo
    FOREIGN KEY (id_motivo_rechazo) REFERENCES motivos_rechazo_solicitud (id_motivo_rechazo);

ALTER TABLE prestamos ADD CONSTRAINT fk_prestamos_solicitud
    FOREIGN KEY (id_solicitud) REFERENCES solicitudes_credito (id_solicitud);
ALTER TABLE prestamos ADD CONSTRAINT fk_prestamos_anulado_por
    FOREIGN KEY (anulado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE prestamos ADD CONSTRAINT fk_prestamos_cierre_documental_por
    FOREIGN KEY (cierre_documental_por) REFERENCES usuarios (id_usuario);
ALTER TABLE prestamos ADD CONSTRAINT fk_prestamos_formalizado_por
    FOREIGN KEY (formalizado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE prestamos ADD CONSTRAINT fk_prestamos_limite_tasa
    FOREIGN KEY (id_limite_tasa) REFERENCES limites_tasa_usura (id_limite_tasa);

ALTER TABLE planes_pago ADD CONSTRAINT fk_planes_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE planes_pago ADD CONSTRAINT fk_planes_liquidacion_origen
    FOREIGN KEY (id_liquidacion_origen) REFERENCES liquidaciones_anticipadas (id_liquidacion);
ALTER TABLE planes_pago ADD CONSTRAINT fk_planes_generado_por
    FOREIGN KEY (generado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE cuotas ADD CONSTRAINT fk_cuotas_plan
    FOREIGN KEY (id_plan_pago) REFERENCES planes_pago (id_plan_pago);

ALTER TABLE liquidaciones_anticipadas ADD CONSTRAINT fk_liquidaciones_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE liquidaciones_anticipadas ADD CONSTRAINT fk_liquidaciones_calculado_por
    FOREIGN KEY (calculado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE liquidaciones_anticipadas ADD CONSTRAINT fk_liquidaciones_archivo
    FOREIGN KEY (id_archivo_liquidacion) REFERENCES archivos_binarios (id_archivo);

ALTER TABLE sesiones_caja ADD CONSTRAINT fk_sesiones_cajero
    FOREIGN KEY (id_cajero) REFERENCES usuarios (id_usuario);
ALTER TABLE sesiones_caja ADD CONSTRAINT fk_sesiones_archivo_cierre
    FOREIGN KEY (id_archivo_cierre) REFERENCES archivos_binarios (id_archivo);
ALTER TABLE sesiones_caja ADD CONSTRAINT fk_sesiones_gerente_revisa
    FOREIGN KEY (id_gerente_revisor) REFERENCES usuarios (id_usuario);

ALTER TABLE desembolsos ADD CONSTRAINT fk_desembolsos_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE desembolsos ADD CONSTRAINT fk_desembolsos_sesion
    FOREIGN KEY (id_sesion_caja) REFERENCES sesiones_caja (id_sesion_caja);
ALTER TABLE desembolsos ADD CONSTRAINT fk_desembolsos_desembolsado_por
    FOREIGN KEY (desembolsado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE desembolsos ADD CONSTRAINT fk_desembolsos_archivo
    FOREIGN KEY (id_archivo_comprobante) REFERENCES archivos_binarios (id_archivo);


ALTER TABLE pagos ADD CONSTRAINT fk_pagos_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_sesion
    FOREIGN KEY (id_sesion_caja) REFERENCES sesiones_caja (id_sesion_caja);
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_liquidacion
    FOREIGN KEY (id_liquidacion_anticipada) REFERENCES liquidaciones_anticipadas (id_liquidacion);
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_archivo
    FOREIGN KEY (id_archivo_recibo) REFERENCES archivos_binarios (id_archivo);

ALTER TABLE aplicaciones_pago ADD CONSTRAINT fk_aplicaciones_pago
    FOREIGN KEY (id_pago) REFERENCES pagos (id_pago);
ALTER TABLE aplicaciones_pago ADD CONSTRAINT fk_aplicaciones_cuota
    FOREIGN KEY (id_cuota) REFERENCES cuotas (id_cuota);

ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_pago
    FOREIGN KEY (id_pago) REFERENCES pagos (id_pago);
ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_autorizado_por
    FOREIGN KEY (autorizado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_procesado_por
    FOREIGN KEY (procesado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_sesion
    FOREIGN KEY (id_sesion_caja) REFERENCES sesiones_caja (id_sesion_caja);
ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_archivo
    FOREIGN KEY (id_archivo_comprobante) REFERENCES archivos_binarios (id_archivo);
ALTER TABLE reversiones_pago ADD CONSTRAINT fk_rev_pago_solicitado_por
    FOREIGN KEY (solicitado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE auditoria_operaciones ADD CONSTRAINT fk_auditoria_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario);

ALTER TABLE movimientos_caja ADD CONSTRAINT fk_movimientos_sesion
    FOREIGN KEY (id_sesion_caja) REFERENCES sesiones_caja (id_sesion_caja);
ALTER TABLE movimientos_caja ADD CONSTRAINT fk_movimientos_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE movimientos_caja ADD CONSTRAINT fk_movimientos_confirmado_por
    FOREIGN KEY (confirmado_por) REFERENCES usuarios (id_usuario);

ALTER TABLE gestiones_cobranza ADD CONSTRAINT fk_gestiones_prestamo
    FOREIGN KEY (id_prestamo) REFERENCES prestamos (id_prestamo);
ALTER TABLE gestiones_cobranza ADD CONSTRAINT fk_gestiones_persona
    FOREIGN KEY (id_persona_contactada) REFERENCES personas (id_persona);
ALTER TABLE gestiones_cobranza ADD CONSTRAINT fk_gestiones_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios (id_usuario);
ALTER TABLE gestiones_cobranza ADD CONSTRAINT fk_gestiones_pago
    FOREIGN KEY (id_pago) REFERENCES pagos (id_pago);

ALTER TABLE facturas ADD CONSTRAINT fk_facturas_pago
    FOREIGN KEY (id_pago) REFERENCES pagos (id_pago);
ALTER TABLE facturas ADD CONSTRAINT fk_facturas_archivo
    FOREIGN KEY (id_archivo) REFERENCES archivos_binarios (id_archivo);
ALTER TABLE facturas ADD CONSTRAINT fk_facturas_reversion_pago
    FOREIGN KEY (id_reversion_pago) REFERENCES reversiones_pago (id_reversion_pago);
ALTER TABLE facturas ADD CONSTRAINT fk_facturas_archivo_nota_anulacion
    FOREIGN KEY (id_archivo_nota_anulacion) REFERENCES archivos_binarios (id_archivo);
