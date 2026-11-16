-- =====================================================================================
-- Clientes de demostración para la entrega (12/11/2026, pedido de Joel)
--
-- PARA QUÉ: que la base entregada permita hacer la demostración completa de la aplicación
-- móvil sin cargar nada a mano antes de empezar. El dump de la entrega
-- (entrega_final/proyecto_final.sql) trae 2 personas y, de casualidad, una sola con un
-- documento faltante: con eso "Mis pendientes" muestra un cliente y el circuito en vivo no se
-- puede hacer entero.
--
-- DÓNDE SE CORRE: **solo sobre una base restaurada desde el dump de la entrega**, antes de
-- volver a generar el dump. NUNCA contra sgcpc_db_pruebas, que es la base de trabajo (DOGMA 2);
-- las dos guardas de abajo lo impiden, no es una recomendación.
--
-- IDEMPOTENTE: si los tres clientes ya están, no toca nada y lo dice.
--
-- LO QUE NO HACE: no inventa ninguna cifra que el sistema calcule. No siembra solicitudes, ni
-- cuotas, ni tasas, ni préstamos: esas las tiene que crear el propio sistema durante la
-- demostración (o corriendo el circuito una vez sobre esta misma base antes de armar el dump
-- final). Una cuota escrita a mano en un .sql sería un número que no salió del motor
-- financiero, y al aprobar la solicitud el servicio revalida la TEA contra el tope legal y la
-- afectación contra su límite: un número inventado haría fallar la aprobación en la
-- demostración, que es el peor momento para enterarse.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Guarda 1: nunca contra la base de trabajo.
-- -------------------------------------------------------------------------------------
DO $$
BEGIN
    IF current_database() = 'sgcpc_db_pruebas' THEN
        RAISE EXCEPTION 'ABORTADO: este script no se corre contra sgcpc_db_pruebas (la base de trabajo). Restaure el dump de la entrega en una base aparte y correlo ahi.';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- Guarda 2: la base tiene que ser la del dump, no una base con historia.
--
-- El dump de la entrega trae 2 personas. Si hay muchas más, esta base no es la del dump
-- (puede ser una copia de la de trabajo con otro nombre), y sembrar ahí no tendría sentido.
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    cuantas_personas bigint;
BEGIN
    SELECT count(*) INTO cuantas_personas FROM personas;
    IF cuantas_personas > 10 THEN
        RAISE EXCEPTION 'ABORTADO: esta base tiene % personas. El dump de la entrega trae 2, asi que esta no es una base restaurada desde el dump.', cuantas_personas;
    END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- La siembra.
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    -- El oficial de crédito del dump (id 2): queda como quien registró a los tres, que es lo
    -- que habría pasado de verdad.
    id_oficial       CONSTANT bigint := 2;
    -- Asunción (id_ciudad 1), uno de los dos departamentos habilitados por
    -- credito.departamentos_habilitados ("Asunción,Central"). Si la ciudad fuera de otro
    -- departamento, la política de crédito rechazaría la solicitud y la demostración se
    -- cortaría ahí.
    id_ciudad_demo   CONSTANT bigint := 1;
    -- Un PDF válido de una página en blanco, 207 bytes. Es el contenido de los documentos
    -- sembrados: no se usa la foto del documento de ninguna persona real.
    pdf_demo         CONSTANT bytea := decode('255044462d312e340a312030206f626a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e656e646f626a0a322030206f626a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e656e646f626a0a332030206f626a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020353935203834325d2f5265736f75726365733c3c3e3e3e3e656e646f626a0a747261696c65723c3c2f526f6f742031203020523e3e0a2525454f460a', 'hex');
    sha_demo         CONSTANT char(64) := '7c061830723c913c51d7287b2c2a50a9c960518a5338b1f4a7886b047b95e6c8';

    id_a bigint;
    id_b bigint;
    id_c bigint;
    id_archivo_nuevo bigint;
    id_documento_autorizacion bigint;
    id_documento_resultado bigint;
    id_consentimiento_nuevo bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM personas WHERE cedula_identidad IN ('5100001', '5100002', '5100003')) THEN
        RAISE NOTICE 'Los clientes de demostracion ya estan sembrados: no se toca nada.';
        RETURN;
    END IF;

    -- =================================================================================
    -- CLIENTE A -- "le faltan los 4": ni un documento cargado.
    --
    -- Para qué: es el que hace que "Mis pendientes" de la aplicación muestre algo que se
    -- entienda de un vistazo, con su chapita en rojo y "Falta: ..." con los cuatro nombres.
    -- =================================================================================
    INSERT INTO personas (cedula_identidad, nombres, apellidos, fecha_nacimiento, estado_civil,
                          telefono_whatsapp, estado_persona, registrado_por, fecha_registro,
                          fecha_actualizacion, fecha_vencimiento_cedula)
    VALUES ('5100001', 'Rosa Elena', 'Gimenez Paez', '1986-04-18', 'Soltero/a',
            '0981500001', 'ACTIVA', id_oficial, now(), now(), '2032-04-18')
    RETURNING id_persona INTO id_a;

    INSERT INTO personas_domicilio (id_persona, id_ciudad, barrio, direccion_domicilio,
                                    referencia_domicilio, tipo_vivienda, antiguedad_domicilio_anios)
    VALUES (id_a, id_ciudad_demo, 'Barrio San Vicente', 'Avda. Peru 1234',
            'Frente a la plaza', 'ALQUILADA', 4);

    INSERT INTO personas_laborales (id_persona, situacion_laboral, ingreso_neto_comprobado,
                                    presenta_comprobante_ingreso, cuota_otras_deudas_mensual,
                                    antiguedad_laboral_anios, cargo, empleador_nombre)
    VALUES (id_a, 'COMERCIANTE', 4500000, true, 0, 6, 'Duena del comercio', 'Despensa Rosa');

    INSERT INTO personas_referencias (id_persona, numero_referencia, nombre, telefono)
    VALUES (id_a, 1, 'Marta Gimenez', '0981500011'),
           (id_a, 2, 'Pedro Paez', '0981500012');

    -- =================================================================================
    -- CLIENTE B -- "le falta 1": EL del circuito en vivo.
    --
    -- Tiene todo menos el comprobante de domicilio. Por qué justo ese: es el único de los
    -- cuatro obligatorios que se puede cargar desde la tablet. Los otros dos pasos del buró
    -- (generar la autorización y registrar la consulta con su evidencia) NO existen en la
    -- aplicación -- la autorización es de solo lectura ahí por decisión de Joel en la FASE 1, y
    -- la consulta al buró es una pantalla del sistema web. Así que vienen sembrados, y el
    -- circuito de la demostración queda entero dentro de la aplicación:
    --
    --   el oficial carga el comprobante de domicilio desde la tablet
    --     -> crea la solicitud (pestaña Solicitudes)
    --     -> la envía a aprobación  [pasa: ya no falta nada]
    --     -> el gerente la aprueba desde la aplicación
    --     -> se ve en /solicitudes y en /aprobaciones del sistema web
    --
    -- Ingreso 6.000.000 con 0 de otras deudas: un crédito de 5.000.000 a 12 meses queda muy
    -- por debajo del 40 % de afectación, así que la política no lo frena.
    -- =================================================================================
    INSERT INTO personas (cedula_identidad, nombres, apellidos, fecha_nacimiento, estado_civil,
                          telefono_whatsapp, estado_persona, registrado_por, fecha_registro,
                          fecha_actualizacion, fecha_vencimiento_cedula)
    VALUES ('5100002', 'Hugo Ramon', 'Villalba Ortiz', '1984-09-07', 'Casado/a',
            '0981500002', 'ACTIVA', id_oficial, now(), now(), '2031-09-07')
    RETURNING id_persona INTO id_b;

    INSERT INTO personas_conyuge (id_persona, nombre, cedula, telefono)
    VALUES (id_b, 'Liz Carolina Ortiz', '5100022', '0981500022');

    INSERT INTO personas_domicilio (id_persona, id_ciudad, barrio, direccion_domicilio,
                                    referencia_domicilio, tipo_vivienda, antiguedad_domicilio_anios)
    VALUES (id_b, id_ciudad_demo, 'Barrio Jara', 'Dr. Facundo Machain 870',
            'Casi Avda. Artigas', 'PROPIA', 9);

    INSERT INTO personas_laborales (id_persona, situacion_laboral, ingreso_neto_comprobado,
                                    presenta_comprobante_ingreso, cuota_otras_deudas_mensual,
                                    antiguedad_laboral_anios, cargo, empleador_nombre)
    VALUES (id_b, 'ASALARIADO_SECTOR_PRIVADO', 6000000, true, 0, 7, 'Encargado de deposito',
            'Distribuidora del Este S.A.');

    INSERT INTO personas_referencias (id_persona, numero_referencia, nombre, telefono)
    VALUES (id_b, 1, 'Carlos Villalba', '0981500021'),
           (id_b, 2, 'Nilda Ortiz', '0981500023');

    -- Documento 1: la cédula.
    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('cedula_villalba.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo, 'CARGADO',
            now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 1, id_b, 1, 'VIGENTE', current_date);

    -- Documento 2: el comprobante de ingreso (lo declara en personas_laborales, así que el
    -- envío a aprobación lo exige).
    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('comprobante_ingreso_villalba.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo,
            'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 2, id_b, 1, 'VIGENTE', current_date);

    -- NADA de tipo 3 (comprobante de domicilio): es lo que se carga en vivo desde la tablet.

    -- Documento 4: la autorización de consulta al buró, firmada. No se puede cargar desde la
    -- aplicación (es de solo lectura ahí), así que viene sembrada.
    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('autorizacion_buro_villalba_firmada.pdf', 'application/pdf', 'pdf', 207, pdf_demo,
            sha_demo, 'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 4, id_b, 1, 'VIGENTE', current_date)
    RETURNING id_documento INTO id_documento_autorizacion;

    -- El consentimiento, que es lo que vuelve "vigente" a esa autorización.
    INSERT INTO consentimientos_crediticios (id_persona, id_documento_evidencia, finalidad,
                                             fecha_consentimiento, registrado_por, fecha_registro)
    VALUES (id_b, id_documento_autorizacion,
            'Consulta al buro de credito para evaluacion crediticia.', now(), id_oficial, now())
    RETURNING id_consentimiento INTO id_consentimiento_nuevo;

    -- Documento 5: el resultado de la consulta al buró, y la consulta registrada con esa
    -- evidencia. indicador_mora en false: con true, RN-18 rechazaría el envío a aprobación.
    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('resultado_buro_villalba.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo,
            'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 5, id_b, 1, 'VIGENTE', current_date)
    RETURNING id_documento INTO id_documento_resultado;

    INSERT INTO consultas_crediticias (id_persona, id_consentimiento, proveedor_buro,
                                       fecha_hora_consulta, consultado_por, indicador_mora,
                                       resumen_resultado, id_documento_resultado)
    VALUES (id_b, id_consentimiento_nuevo, 'Informconf', now(), id_oficial, false,
            'Sin deudas registradas al momento de la consulta.', id_documento_resultado);

    -- =================================================================================
    -- CLIENTE C -- expediente completo, sin ninguna solicitud.
    --
    -- Para qué: el camino corto. Si en la demostración no hay tiempo de cargar un documento, o
    -- si algo sale mal con el cliente B, con C se puede ir derecho a "Nueva solicitud" ->
    -- "Enviar a aprobación" -> aprobar, sin pasos previos. También sirve para mostrar que un
    -- cliente completo NO aparece en "Mis pendientes".
    -- =================================================================================
    INSERT INTO personas (cedula_identidad, nombres, apellidos, fecha_nacimiento, estado_civil,
                          telefono_whatsapp, estado_persona, registrado_por, fecha_registro,
                          fecha_actualizacion, fecha_vencimiento_cedula)
    VALUES ('5100003', 'Blanca Noemi', 'Caceres Duarte', '1988-01-26', 'Soltero/a',
            '0981500003', 'ACTIVA', id_oficial, now(), now(), '2033-01-26')
    RETURNING id_persona INTO id_c;

    INSERT INTO personas_domicilio (id_persona, id_ciudad, barrio, direccion_domicilio,
                                    referencia_domicilio, tipo_vivienda, antiguedad_domicilio_anios)
    VALUES (id_c, id_ciudad_demo, 'Barrio Las Mercedes', 'Teniente Rojas Silva 450',
            'Entre Brasil y EEUU', 'FAMILIAR', 12);

    INSERT INTO personas_laborales (id_persona, situacion_laboral, ingreso_neto_comprobado,
                                    presenta_comprobante_ingreso, cuota_otras_deudas_mensual,
                                    antiguedad_laboral_anios, cargo, empleador_nombre)
    VALUES (id_c, 'FUNCIONARIO_PUBLICO', 5200000, true, 0, 11, 'Asistente administrativa',
            'Ministerio de Educacion');

    INSERT INTO personas_referencias (id_persona, numero_referencia, nombre, telefono)
    VALUES (id_c, 1, 'Ramona Duarte', '0981500031'),
           (id_c, 2, 'Luis Caceres', '0981500032');

    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('cedula_caceres.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo, 'CARGADO',
            now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 1, id_c, 1, 'VIGENTE', current_date);

    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('comprobante_ingreso_caceres.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo,
            'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 2, id_c, 1, 'VIGENTE', current_date);

    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('comprobante_domicilio_caceres.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo,
            'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 3, id_c, 1, 'VIGENTE', current_date);

    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('autorizacion_buro_caceres_firmada.pdf', 'application/pdf', 'pdf', 207, pdf_demo,
            sha_demo, 'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 4, id_c, 1, 'VIGENTE', current_date)
    RETURNING id_documento INTO id_documento_autorizacion;

    INSERT INTO consentimientos_crediticios (id_persona, id_documento_evidencia, finalidad,
                                             fecha_consentimiento, registrado_por, fecha_registro)
    VALUES (id_c, id_documento_autorizacion,
            'Consulta al buro de credito para evaluacion crediticia.', now(), id_oficial, now())
    RETURNING id_consentimiento INTO id_consentimiento_nuevo;

    INSERT INTO archivos_binarios (nombre_archivo, mime_type, extension, tamano_bytes, contenido,
                                   hash_sha256, origen_archivo, fecha_carga, cargado_por)
    VALUES ('resultado_buro_caceres.pdf', 'application/pdf', 'pdf', 207, pdf_demo, sha_demo,
            'CARGADO', now(), id_oficial)
    RETURNING id_archivo INTO id_archivo_nuevo;
    INSERT INTO documentos_expediente (id_archivo, id_tipo_documento, id_persona, version_documento,
                                       estado_documento, fecha_documento)
    VALUES (id_archivo_nuevo, 5, id_c, 1, 'VIGENTE', current_date)
    RETURNING id_documento INTO id_documento_resultado;

    INSERT INTO consultas_crediticias (id_persona, id_consentimiento, proveedor_buro,
                                       fecha_hora_consulta, consultado_por, indicador_mora,
                                       resumen_resultado, id_documento_resultado)
    VALUES (id_c, id_consentimiento_nuevo, 'Informconf', now(), id_oficial, false,
            'Sin deudas registradas al momento de la consulta.', id_documento_resultado);

    RAISE NOTICE 'Sembrados: A=% (5100001, sin documentos), B=% (5100002, falta el comprobante de domicilio), C=% (5100003, expediente completo).',
        id_a, id_b, id_c;
END $$;

-- -------------------------------------------------------------------------------------
-- Control: que la siembra haya quedado como dice.
--
-- Si algo no cuadra, aborta -- así el script no puede "parecer" que anduvo. Mismo criterio que
-- los corrimientos controlados de fechas del humo (DO $$ ... RAISE EXCEPTION $$).
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    docs_a bigint;
    docs_b bigint;
    docs_c bigint;
    tiene_domicilio_b bigint;
    consultas_b bigint;
    solicitudes_demo bigint;
BEGIN
    SELECT count(*) INTO docs_a FROM documentos_expediente d
        JOIN personas p ON p.id_persona = d.id_persona
        WHERE p.cedula_identidad = '5100001' AND d.estado_documento = 'VIGENTE';
    SELECT count(*) INTO docs_b FROM documentos_expediente d
        JOIN personas p ON p.id_persona = d.id_persona
        WHERE p.cedula_identidad = '5100002' AND d.estado_documento = 'VIGENTE';
    SELECT count(*) INTO docs_c FROM documentos_expediente d
        JOIN personas p ON p.id_persona = d.id_persona
        WHERE p.cedula_identidad = '5100003' AND d.estado_documento = 'VIGENTE';
    SELECT count(*) INTO tiene_domicilio_b FROM documentos_expediente d
        JOIN personas p ON p.id_persona = d.id_persona
        WHERE p.cedula_identidad = '5100002' AND d.id_tipo_documento = 3
          AND d.estado_documento = 'VIGENTE';
    SELECT count(*) INTO consultas_b FROM consultas_crediticias c
        JOIN personas p ON p.id_persona = c.id_persona
        WHERE p.cedula_identidad = '5100002' AND c.indicador_mora = false
          AND c.id_documento_resultado IS NOT NULL;
    SELECT count(*) INTO solicitudes_demo FROM solicitudes_credito s
        JOIN personas p ON p.id_persona = s.id_cliente
        WHERE p.cedula_identidad IN ('5100001', '5100002', '5100003');

    IF docs_a <> 0 THEN
        RAISE EXCEPTION 'El cliente A tendria que no tener ningun documento, y tiene %.', docs_a;
    END IF;
    IF docs_b <> 4 THEN
        RAISE EXCEPTION 'El cliente B tendria que tener 4 documentos (todos menos el de domicilio), y tiene %.', docs_b;
    END IF;
    IF tiene_domicilio_b <> 0 THEN
        RAISE EXCEPTION 'El cliente B NO tendria que tener el comprobante de domicilio: es el que se carga en vivo desde la tablet.';
    END IF;
    IF consultas_b <> 1 THEN
        RAISE EXCEPTION 'El cliente B tendria que tener 1 consulta al buro sin mora y con evidencia, y tiene %.', consultas_b;
    END IF;
    IF docs_c <> 5 THEN
        RAISE EXCEPTION 'El cliente C tendria que tener los 5 documentos, y tiene %.', docs_c;
    END IF;
    IF solicitudes_demo <> 0 THEN
        RAISE EXCEPTION 'Los clientes de demostracion no tendrian que tener ninguna solicitud sembrada, y hay %.', solicitudes_demo;
    END IF;

    RAISE NOTICE 'Control OK: A sin documentos, B con 4 y sin el de domicilio, C con los 5, y ninguna solicitud sembrada.';
END $$;
