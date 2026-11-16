-- SGCPC - 02_indices.sql
-- Indices unicos parciales y restricciones cruzadas de Doc. 00 Sec. 10.4 /
-- Doc. 08 Sec. 8.8 que no se pueden expresar como CHECK/UNIQUE simple de
-- columna (por eso van aparte de 01_esquema.sql), mas indices de apoyo para
-- bandejas y reportes por estado/fecha.
--
-- No se crea un indice para "un solo prestamo no cerrado por cliente": Doc.
-- 08 Sec. 8.8 es explicito en que esa regla se valida transaccionalmente en
-- los Servicios antes de formalizar, no con un indice (duplicar id_cliente
-- en prestamos solo para eso rompería la normalizacion).

-- =====================================================================
-- Indices unicos parciales (una sola fila activa a la vez)
-- =====================================================================

-- Una sola sesion de caja ABIERTA en todo el sistema.
CREATE UNIQUE INDEX ux_sesiones_caja_una_abierta
    ON sesiones_caja (estado_sesion)
    WHERE estado_sesion = 'ABIERTA';

-- Un solo desembolso CONFIRMADO por prestamo (uno REVERTIDO no bloquea
-- un desembolso posterior valido).
CREATE UNIQUE INDEX ux_desembolsos_un_confirmado_por_prestamo
    ON desembolsos (id_prestamo)
    WHERE estado_desembolso = 'CONFIRMADO';

-- Una sola solicitud abierta (BORRADOR, PENDIENTE_APROBACION, o APROBADA
-- todavia sin formalizar) por cliente. E5/D-68: una APROBADA ya formalizada
-- (tiene prestamo) deja de contar como "abierta" -- si no, un cliente con un
-- prestamo ya cerrado (FINALIZADO/ANULADO_ANTES_DESEMBOLSO) nunca podria
-- volver a pedir, porque la solicitud vieja seguiria bloqueando para siempre.
CREATE UNIQUE INDEX ux_solicitudes_una_abierta_por_cliente
    ON solicitudes_credito (id_cliente)
    WHERE estado_solicitud IN ('BORRADOR', 'PENDIENTE_APROBACION')
       OR (estado_solicitud = 'APROBADA' AND NOT formalizada);

-- Una sola version de plan vigente por prestamo.
CREATE UNIQUE INDEX ux_planes_pago_una_vigente_por_prestamo
    ON planes_pago (id_prestamo)
    WHERE vigente = TRUE;

-- =====================================================================
-- Indices de apoyo para bandejas, seguimiento y reportes
-- =====================================================================

CREATE INDEX ix_solicitudes_estado ON solicitudes_credito (estado_solicitud);
CREATE INDEX ix_solicitudes_fecha ON solicitudes_credito (fecha_solicitud);

CREATE INDEX ix_prestamos_estado ON prestamos (estado_prestamo);
CREATE INDEX ix_prestamos_fecha_formalizacion ON prestamos (fecha_formalizacion);

CREATE INDEX ix_cuotas_fecha_vencimiento ON cuotas (fecha_vencimiento);
CREATE INDEX ix_cuotas_estado_pago ON cuotas (estado_pago);
CREATE INDEX ix_cuotas_plan ON cuotas (id_plan_pago);

CREATE INDEX ix_pagos_prestamo ON pagos (id_prestamo);
CREATE INDEX ix_pagos_fecha ON pagos (fecha_hora_pago);
CREATE INDEX ix_pagos_estado ON pagos (estado_pago);

CREATE INDEX ix_desembolsos_prestamo ON desembolsos (id_prestamo);

CREATE INDEX ix_movimientos_caja_sesion ON movimientos_caja (id_sesion_caja);
CREATE INDEX ix_movimientos_caja_fecha ON movimientos_caja (fecha_hora);

CREATE INDEX ix_gestiones_cobranza_prestamo ON gestiones_cobranza (id_prestamo);
CREATE INDEX ix_gestiones_cobranza_fecha_promesa ON gestiones_cobranza (fecha_promesa);

CREATE INDEX ix_documentos_persona ON documentos_expediente (id_persona);
CREATE INDEX ix_documentos_solicitud ON documentos_expediente (id_solicitud);
CREATE INDEX ix_documentos_prestamo ON documentos_expediente (id_prestamo);

CREATE INDEX ix_facturas_fecha ON facturas (fecha);
-- E9/Bloque 5: reporte "Facturas anuladas del mes" (filtra por estado + rango de fecha).
CREATE INDEX ix_facturas_estado ON facturas (estado);

-- E9/Bloque 5: bandeja de reversiones pendientes de autorizar (/reversiones, panel del gerente).
CREATE INDEX ix_reversiones_pago_estado ON reversiones_pago (estado);

CREATE INDEX ix_auditoria_fecha ON auditoria_operaciones (fecha_hora);
CREATE INDEX ix_auditoria_entidad ON auditoria_operaciones (tipo_entidad, id_entidad);
-- Bloque de eficiencia (hallazgo de Joel): filtro por usuario Y fecha a la vez (pantalla de
-- Auditoría, "Mostrar lo que hizo el usuario X en tal rango") -- ix_auditoria_fecha solo cubre
-- el filtro sin usuario.
CREATE INDEX ix_auditoria_usuario_fecha ON auditoria_operaciones (id_usuario, fecha_hora);

-- Bloque de eficiencia (hallazgo de Joel): único sobre el nombre de usuario EN MINÚSCULAS, de
-- más -- ya existe un UNIQUE simple sobre la columna (usuarios_nombre_usuario_key) y toda la
-- aplicación normaliza a minúsculas antes de guardar (ServicioUsuarios.crear/
-- ServicioAutenticacion.normalizarNombreUsuario), así que hoy ya no puede haber dos cuentas que
-- solo se diferencien por mayúsculas. Esto es una red de seguridad explícita para el día en que
-- algún código nuevo (o una carga directa por SQL) se olvide de normalizar.
CREATE UNIQUE INDEX ux_usuarios_nombre_usuario_minusculas ON usuarios (lower(nombre_usuario));

-- Integridad (17/10/2026, auditoría de toda la base, §7 grupo E): FK que se usan para filtrar o
-- unir sin tener su propio índice (más allá de la PK).
CREATE INDEX ix_archivos_binarios_cargado_por ON archivos_binarios (cargado_por);
CREATE INDEX ix_consentimientos_persona ON consentimientos_crediticios (id_persona);
CREATE INDEX ix_consentimientos_solicitud ON consentimientos_crediticios (id_solicitud);
CREATE INDEX ix_consultas_persona ON consultas_crediticias (id_persona);
CREATE INDEX ix_documentos_tipo_documento ON documentos_expediente (id_tipo_documento);
CREATE INDEX ix_documentos_reemplazado ON documentos_expediente (id_documento_reemplazado);
CREATE INDEX ix_facturas_pago ON facturas (id_pago);
CREATE INDEX ix_liquidaciones_prestamo ON liquidaciones_anticipadas (id_prestamo);
-- No es UNIQUE: clave_parametro se repite entre versiones históricas del mismo parámetro
-- (vigente_desde/vigente_hasta distinguen cuál está vigente).
CREATE INDEX ix_parametros_clave ON parametros_sistema (clave_parametro);
CREATE INDEX ix_personas_domicilio_ciudad ON personas_domicilio (id_ciudad);

-- =====================================================================
-- Proteccion contra DELETE en tablas financieras (Doc. 00 Sec. 10.4 /
-- Doc. 08 Sec. 8.1: "las operaciones financieras confirmadas no se
-- eliminan... se corrigen mediante reversion").
--
-- sgcpc_user es dueno de las tablas que el mismo crea al correr este
-- script (Doc. 00 E0, decision D-12: no se usa un rol de aplicacion
-- separado de "postgres" como en el diseño original, sino el usuario
-- sgcpc_user ya creado en pgAdmin). Un GRANT/REVOKE no le puede quitar
-- privilegios a su propio dueño, asi que la regla "ninguna tabla
-- financiera tiene DELETE desde la aplicacion" se aplica con triggers
-- BEFORE DELETE que rechazan la operacion, en vez de con permisos.
-- =====================================================================

CREATE OR REPLACE FUNCTION fn_bloquear_eliminacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'No se permite DELETE en %: las operaciones financieras se corrigen con una reversion autorizada, nunca se borran.', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_bloquear_modificacion_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'auditoria_operaciones es de solo insercion: no se permite %.', TG_OP;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tabla TEXT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY[
        'prestamos', 'cuotas', 'planes_pago', 'pagos', 'aplicaciones_pago',
        'desembolsos', 'reversiones_pago',
        'movimientos_caja', 'sesiones_caja', 'facturas', 'liquidaciones_anticipadas'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_bloquear_eliminacion BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION fn_bloquear_eliminacion();',
            tabla
        );
    END LOOP;
END
$$;

-- auditoria_operaciones: ademas de bloquear DELETE, bloquea UPDATE
-- (Doc. 08 Sec. 8.5: "los registros de auditoria son de solo insercion").
CREATE TRIGGER trg_bloquear_eliminacion
    BEFORE DELETE ON auditoria_operaciones
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion_auditoria();

CREATE TRIGGER trg_bloquear_actualizacion
    BEFORE UPDATE ON auditoria_operaciones
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion_auditoria();
