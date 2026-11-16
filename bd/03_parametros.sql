-- SGCPC - 03_parametros.sql
-- Usuarios de prueba (Doc. 00 Sec. 16.1), parametros institucionales/
-- fiscales/financieros (Doc. 00 Sec. 5) y limite de tasa vigente.
-- Todos los datos institucionales son ficticios (se reemplazan antes de
-- atender clientes reales, ver Doc. 00 Sec. 16.3).
--
-- Las tasas se guardan como FRACCION NUMERIC(9,6) (0.270000 = 27%), ver
-- decision D-11 en AVANCE.md y el encabezado de 01_esquema.sql.

-- =====================================================================
-- Usuarios (Doc. 00 Sec. 16.1) - contrasena "Cambiar@2026" para gerente/
-- oficial/cajera, hash real generado con jBCrypt 0.4 (costo 10), exige
-- cambio al primer ingreso.
--
-- RF36/RF37 (hallazgo de Joel, corrección): el administrador NO comparte
-- esa contraseña -- antes (commit 30c152a, D7) su hash había quedado
-- copiado tal cual del de "gerente" (mismo hash, mismo texto), así que
-- un recrear_base.sh nuevo le daría la misma contraseña que gerente sin
-- que nadie lo note. Ahora tiene su propia contraseña inicial
-- "CambiarAdmin@2026" (hash propio, generado igual con jBCrypt 0.4 costo
-- 10) -- distinta de la de los otros 3, y exige cambio al primer ingreso
-- igual que ellos (debe_cambiar_contrasena=TRUE, ya estaba así).
-- =====================================================================

INSERT INTO usuarios (
    nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad,
    perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena,
    fecha_creacion, creado_por
) VALUES (
    'gerente', '$2a$10$viR8.rhVb5mtYuXIpK9uFu36YaLkPhwnhzk5gcEYAI.N5GdBWSs3a',
    'Ramón', 'Benítez', '2845117',
    'GERENTE_ADMINISTRADOR', 'ACTIVA', 0, TRUE,
    CURRENT_TIMESTAMP, NULL
);

INSERT INTO usuarios (
    nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad,
    perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena,
    fecha_creacion, creado_por
) VALUES (
    'oficial', '$2a$10$AKCAgxYWtIv57eTla1/kB.iiPFLoKg1/XHGJn2vZ40wc0C.SVxJo6',
    'Lorenzo', 'Ramírez', '2345678',
    'OFICIAL_CREDITO', 'ACTIVA', 0, TRUE,
    CURRENT_TIMESTAMP, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente')
);

INSERT INTO usuarios (
    nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad,
    perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena,
    fecha_creacion, creado_por
) VALUES (
    'cajera', '$2a$10$ZmUZ8yE.3ZSAmIg9Td6okujfa0iZdDmX.w6DyUEdqIhzEsXZuK6F6',
    'Sofía', 'Martínez', '3456789',
    'CAJERO', 'ACTIVA', 0, TRUE,
    CURRENT_TIMESTAMP, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente')
);

-- D7 (pedido de Joel, anteproyecto entregado): perfil ADMINISTRADOR, acceso a TODO el sistema
-- (TOTAL en todos los permisos, ver ServicioAutorizacion). GERENTE_ADMINISTRADOR no se toca.
-- RF36/RF37: contraseña inicial propia "CambiarAdmin@2026" (no "Cambiar@2026", esa es la de
-- gerente/oficial/cajera) -- ver el encabezado de esta sección.
INSERT INTO usuarios (
    nombre_usuario, contrasena_hash, nombres, apellidos, cedula_identidad,
    perfil_usuario, estado_cuenta, intentos_fallidos, debe_cambiar_contrasena,
    fecha_creacion, creado_por
) VALUES (
    'administrador', '$2a$10$qQ8eztkP0tRJ37uN/qdqqeUJJPbUHJ6eZuxdrGLg5IYJG4BzYlKWa',
    'Joel Jesús', 'Acosta', '2456812',
    'ADMINISTRADOR', 'ACTIVA', 0, TRUE,
    CURRENT_TIMESTAMP, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente')
);

-- =====================================================================
-- Limite de tasa vigente (Doc. 00 Sec. 5.3) - a confirmar con el valor
-- oficial del BCP antes de atender clientes reales (Sec. 16.3).
-- =====================================================================

INSERT INTO limites_tasa_usura (
    vigente_desde, vigente_hasta, tasa_maxima_tea, fuente_referencia,
    registrado_por, fecha_registro
) VALUES (
    CURRENT_DATE, NULL, 0.273900,
    'Tope BCP vigente sept. 2026',
    (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP
);

-- =====================================================================
-- Parametros del sistema (Doc. 00 Sec. 5). vigente_desde = fecha de carga
-- para que la demostracion funcione en cualquier fecha.
-- =====================================================================

INSERT INTO parametros_sistema (clave_parametro, nombre_parametro, valor_parametro, tipo_valor, vigente_desde, modificado_por, fecha_modificacion) VALUES
-- 5.1 Datos institucionales (ficticios)
('empresa.razon_social', 'Razón social', 'Capital Crédito Paraguay S.R.L.', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.nombre_comercial', 'Nombre comercial', 'Capital Crédito', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.ruc', 'RUC', '80123456-5', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.actividad', 'Actividad', 'Otorgante de Crédito Dinerario (OCD) / Casa de Crédito', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.domicilio', 'Domicilio', 'Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.telefono', 'Teléfono', '(021) 941 890', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.whatsapp', 'WhatsApp', '(0972) 552 233', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.correo', 'Correo electrónico', 'atencion@capitalcredito.com.py', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.representante_legal', 'Representante legal', 'Ramón Benítez – C.I. 2.845.117', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- E8 (D-93): separado del combinado de arriba para los comprobantes (firma de la constancia).
-- El combinado NO se toca: ServicioFormalizacion todavía lo usa para contrato/pagaré (E5).
('empresa.representante_legal_nombre', 'Representante legal (nombre)', 'Ramón Benítez', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.representante_legal_cedula', 'Representante legal (C.I.)', '2.845.117', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.registro_bcp', 'Registro BCP', 'Res. SB N.º 0147/2026', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('empresa.registro_siro', 'Registro SIRO', 'SIRO-2026-00893', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- 5.2 Parametros fiscales
('fiscal.timbrado_numero', 'Número de timbrado', '16842795', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- D-113 (Bloque 7/E10): 01/09/2026-31/08/2027, no 01/10/2026 como el seed original -- con esa
-- fecha, cualquier cobro de HOY (o de cualquier día de septiembre 2026) se rechaza porque el
-- timbrado todavía no está vigente (encontrado corriendo el humo contra sgcpc_db_pruebas por
-- primera vez, 61 fallos en cascada por esta única causa). Ajuste de Joel.
-- D-115 (Bloque 7/E10, 7.4): el intento de extender vigencia_desde a 01/01/2025 (un solo timbrado
-- de 2,5 años) fue revertido -- Joel: no es realista (DNIT los otorga por un año) y deja facturas
-- fuera de la restricción fiscal acordada. Propuesta en revisión: dos timbrados reales (uno
-- anterior, ya vencido, y el actual) -- requiere además un cambio en ServicioCobranza (hoy resuelve
-- el timbrado "vigente hoy", no "vigente en la fecha del cobro"), ver AVANCE.md antes de tocar esto.
('fiscal.timbrado_vigencia_desde', 'Timbrado vigente desde', '2026-09-01', 'FECHA', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.timbrado_vigencia_hasta', 'Timbrado vigente hasta', '2027-08-31', 'FECHA', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.establecimiento', 'Establecimiento', '001', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.punto_expedicion', 'Punto de expedición', '001', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.factura_numero_desde', 'Numeración de factura desde', '1', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.factura_numero_hasta', 'Numeración de factura hasta', '9999999', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.factura_numero_aviso', 'Aviso cuando queden N números de factura o menos', '500', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.timbrado_dias_aviso', 'Aviso cuando falten N días o menos para el vencimiento del timbrado', '30', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.tipo_comprobante', 'Tipo de comprobante', 'AUTOIMPRESOR', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.iva_intereses', 'IVA sobre intereses', '0.100000', 'DECIMAL', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('fiscal.iva_capital', 'IVA sobre capital', 'EXENTO', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- 5.3 Parametros financieros y operativos
-- RF39/E1 (decision de Joel, 13/10/2026): "credito.tea_tope_legal" ya NO se siembra acá --
-- limites_tasa_usura (arriba, Doc. 00 Sec. 5.3) quedó como unica fuente del tope legal de TEA
-- (ServicioParametros.validarTeaEmpresa la lee de ahí); una base nueva ya nace con una fila
-- vigente en limites_tasa_usura, así que no hace falta ningun parametro aparte. Ver
-- bd/migraciones/E10_15_rf39_tope_legal_y_nombre_parametro.sql (aplicada en sgcpc_db_pruebas) y
-- docs/funcionamiento/RF39_parametros.md.
('credito.tea_empresa', 'TEA de la empresa', '0.270000', 'DECIMAL', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- D-20: reemplaza a "credito.tasa_moratoria_anual" (D-16/PA-02). Tasa moratoria
-- del prestamo = TEA del prestamo x factor_moratorio. Validacion (a aplicar
-- en el servicio que edite parametros, E2): 0 < factor_moratorio <= 1.
('credito.factor_moratorio', 'Factor de la tasa moratoria sobre la TEA', '1.000000', 'DECIMAL', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- Validacion (E2): porcentaje_punitorio <= 0.300000 (maximo legal, RN-16 Doc. 00 §6.4).
('credito.porcentaje_punitorio', 'Porcentaje punitorio (del moratorio)', '0.300000', 'DECIMAL', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.dias_gracia_mora', 'Días de gracia antes de mora', '0', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.monto_minimo', 'Monto mínimo de crédito (Gs.)', '500000', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.monto_maximo', 'Monto máximo de crédito (Gs.)', '15000000', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.plazo_minimo', 'Plazo mínimo (meses)', '3', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.plazo_maximo', 'Plazo máximo (meses)', '24', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.limite_afectacion', 'Límite de afectación al ingreso', '0.400000', 'DECIMAL', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- RF39/E4 (decisión de Joel, 13/10/2026): el nombre anterior ("Cuotas vencidas para alertar al
-- garante") era engañoso -- este parámetro también alimenta la cláusula de vencimiento
-- anticipado del contrato y del pagaré (ServicioFormalizacion, pagare.jrxml), sin relación con
-- el garante. Solo cambia el nombre visible; la clave y el valor siguen igual.
('credito.cuotas_alerta_garante', 'Cuotas vencidas para el vencimiento anticipado', '2', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.dias_incobrable', 'Días para considerar incobrable', '180', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('caja.diferencia_tolerada', 'Diferencia de arqueo tolerada (Gs.)', '0', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- RF19 (pedido de Joel, 11/10/2026): tope razonable del efectivo contado al cerrar caja, para
-- frenar un error de tipeo (ceros de más) antes de que quede guardado -- no es un límite de
-- negocio real, solo una red de seguridad contra el dedo en el teclado.
('caja.monto_maximo_arqueo', 'Efectivo contado máximo razonable al cerrar caja (Gs.)', '500000000', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- D-141 (Bloque 7/E10, SEDECO, pedido de Joel): redondeo del efectivo a favor del cliente en
-- cobros en efectivo (cobro normal, cancelación total, adelantar capital) -- 0 = sin redondeo.
('credito.redondeo_efectivo', 'Redondeo del efectivo a favor del cliente (Gs., 0 = sin redondeo)', '50', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- E8 (bd/migraciones/E8_04_gerente_predeterminado.sql, D-99): quién figuraba como "Recibí" en
-- el arqueo cuando la sesión cerraba sin diferencia y nadie quedaba registrado en
-- sesiones_caja.id_gerente_revisor.
-- SIN USO DESDE C22 (03/11/2026): el acta nombra al gerente SOLO si quedó registrado al revisar
-- una diferencia, y si no hay ninguno deja la firma en blanco. Ningún código lee esta clave --
-- el único lugar de src/main que la nombra es un comentario de ServicioCaja:597, que explica
-- justamente que antes la imprimía. NO se borra, por el dato histórico de quién figuraba en las
-- actas impresas antes del cambio; lo que se corrige es el texto, que decía "Gerente que recibe
-- la rendición" y ya no es cierto: alguien podía editarlo en /parametros esperando que cambiara
-- algo en el arqueo (decidido el 03/11/2026, aplicado el 11/11/2026). La nota larga va en el
-- UPDATE de más abajo, porque este INSERT no tiene la columna observacion.
('caja.gerente_predeterminado', 'Gerente que figuraba en el «Recibí» de las actas hasta C22 (ya no se usa)', 'Ramón Benítez', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('seguridad.intentos_login', 'Intentos de acceso antes de bloquear', '3', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('seguridad.minutos_inactividad', 'Minutos de inactividad antes de cerrar sesión', '30', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- RF36/RF37 (hallazgo de Joel): si el único administrador se bloquea por intentos fallidos,
-- nadie puede reactivarlo (D-151: Usuarios es exclusivo del administrador). Un bloqueo
-- AUTOMATICO (por intentos fallidos) se libera solo después de este tiempo; un bloqueo o
-- inactivacion MANUAL (hecho por un administrador) nunca se libera solo -- ver
-- ServicioAutenticacion.
('seguridad.minutos_bloqueo', 'Minutos para liberar un bloqueo automático por intentos fallidos', '15', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('archivos.tamano_maximo_mb', 'Tamaño máximo de archivo (MB)', '5', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- D-38 (E3): dias de vigencia de la autorizacion de consulta al buro (RN-24).
-- Validacion (ServicioParametros.validarDiasVigenciaConsentimiento): 1 a 365.
('buro.dias_vigencia_consentimiento', 'Días de vigencia de la autorización de consulta al buró', '30', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- E5 (D-68, ajuste de Joel): la fecha de primer vencimiento aprobada en la
-- solicitud puede quedar vieja si se formaliza dias despues -- al generar
-- documentos se propone fecha de formalizacion + 1 mes, editable por el
-- oficial dentro de este rango. dias_validez_documentos se siembra para
-- cuando exista E6 (desembolso): si pasan mas dias sin desembolsar, debera
-- exigir regenerar los documentos -- no se usa todavia.
('formalizacion.dias_min_primer_vencimiento', 'Días mínimos para el primer vencimiento al formalizar', '20', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('formalizacion.dias_max_primer_vencimiento', 'Días máximos para el primer vencimiento al formalizar', '45', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('formalizacion.dias_validez_documentos', 'Días de vigencia de los documentos generados antes de exigir regenerarlos', '5', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- D-127 (Bloque 7/E10, Tanda B, pedido de Joel): política de crédito parametrizable. Si el
-- cliente no cumple, ServicioSolicitudes.validarPoliticaCredito rechaza con un mensaje puntual
-- (cuál de las tres no se cumple) al crear la solicitud -- nunca silencioso.
('credito.edad_minima', 'Edad mínima para otorgar crédito', '22', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
('credito.edad_maxima', 'Edad máxima para otorgar crédito', '71', 'ENTERO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- Nombres de departamentos (tabla "departamentos", D-126), separados por coma -- texto libre a
-- propósito (se valida por nombre, no por id, para que Joel lo pueda editar desde la pantalla de
-- parámetros sin tener que saber los ids internos).
('credito.departamentos_habilitados', 'Departamentos habilitados para otorgar crédito (separados por coma)', 'Asunción,Central', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP),
-- Códigos de Persona.situacionLaboral (los mismos 8 del <select> de la ficha de cliente),
-- separados por coma. Por defecto, todas menos INDEPENDIENTE y DESEMPLEADO (pedido de Joel).
('credito.situaciones_laborales_admitidas', 'Situaciones laborales admitidas para otorgar crédito (códigos separados por coma)', 'ASALARIADO_SECTOR_PRIVADO,FUNCIONARIO_PUBLICO,JUBILADO_PENSIONADO,COMERCIANTE,EMPLEADO_DOMESTICO', 'TEXTO', CURRENT_DATE, (SELECT id_usuario FROM usuarios WHERE nombre_usuario = 'gerente'), CURRENT_TIMESTAMP);

-- Nota: "credito.tasa_moratoria_anual" (Doc. 00 Sec. 5.3) no se carga como tal
-- (D-16/PA-02, cerrada por D-20): se reemplazo por "credito.factor_moratorio"
-- de mas arriba. La tasa moratoria de cada prestamo se calcula en el motor
-- financiero (E1) como TEA del prestamo x factor_moratorio, y se guarda en
-- prestamos.tasa_moratoria al formalizar (columna ya existente en Doc. 08,
-- ver 01_esquema.sql) para que un cambio posterior del parametro no afecte
-- prestamos ya firmados. Ver AVANCE.md D-20.

-- =====================================================================
-- Tipos de documento (E3, D-37): catalogo de tipos_documento, derivado
-- literalmente de los pasos de PO-02 (Doc. 00 §8). Sin pantalla de
-- administracion en esta etapa; se ajusta a mano si hiciera falta.
-- =====================================================================

INSERT INTO tipos_documento (codigo_tipo, nombre_tipo, contexto, obligatorio_desembolso, activo) VALUES
('CEDULA_IDENTIDAD', 'Cédula de identidad', 'PERSONA', FALSE, TRUE),
('COMPROBANTE_INGRESO', 'Comprobante de ingreso', 'PERSONA', FALSE, TRUE),
('COMPROBANTE_DOMICILIO', 'Comprobante de domicilio', 'PERSONA', FALSE, TRUE),
('AUTORIZACION_CONSULTA_BURO', 'Autorización de consulta al buró', 'PERSONA', FALSE, TRUE),
('RESULTADO_CONSULTA_BURO', 'Resultado de la consulta al buró', 'PERSONA', FALSE, TRUE),
-- E5 (P06, formalización): contexto PRESTAMO. Las _GENERADO son el PDF que
-- arma el sistema (Jasper, todavía no diseñado); las _FIRMADO son el
-- escaneo que sube el oficial y son las que exige el checklist antes del
-- desembolso (obligatorio_desembolso = TRUE).
('CONTRATO_GENERADO', 'Contrato (generado por el sistema)', 'PRESTAMO', FALSE, TRUE),
('CONTRATO_FIRMADO', 'Contrato firmado', 'PRESTAMO', TRUE, TRUE),
('PAGARE_GENERADO', 'Pagaré (generado por el sistema)', 'PRESTAMO', FALSE, TRUE),
('PAGARE_FIRMADO', 'Pagaré firmado', 'PRESTAMO', TRUE, TRUE),
('PLAN_PAGOS_GENERADO', 'Plan de pagos (generado por el sistema)', 'PRESTAMO', FALSE, TRUE),
-- Reprueba Lote 1 (hallazgo de Joel, 11/10/2026, RF13): el anteproyecto pide contrato, pagaré
-- Y plan de cuotas firmados y controlados antes del desembolso -- faltaba este tercero. Mismo
-- patrón que CONTRATO_FIRMADO/PAGARE_FIRMADO (obligatorio_desembolso=TRUE).
('PLAN_PAGOS_FIRMADO', 'Plan de pagos firmado', 'PRESTAMO', TRUE, TRUE),
-- E9/Bloque 5 (P10, cierre documental): mismo patrón GENERADO/FIRMADO que CONTRATO/PAGARE -- no
-- es obligatoria para el desembolso (pasa después, cuando el préstamo ya está FINALIZADO).
('DEVOLUCION_PAGARE_GENERADO', 'Constancia de devolución de pagaré (generada por el sistema)', 'PRESTAMO', FALSE, TRUE),
('DEVOLUCION_PAGARE_FIRMADO', 'Constancia de devolución de pagaré firmada', 'PRESTAMO', FALSE, TRUE),
-- Bloque 7/E10 (D-130, pedido de Joel): comprobante de desembolso firmado (escaneo) -- el PDF
-- "generado por el sistema" no se guarda acá: se regenera al vuelo en cada impresión (para poder
-- timbrarlo con "REIMPRESIÓN" y la fecha/hora real de esa impresión), no es un documento fijo.
('COMPROBANTE_DESEMBOLSO_FIRMADO', 'Comprobante de desembolso firmado (escaneo)', 'PRESTAMO', FALSE, TRUE);

-- =====================================================================
-- Feriados de arranque 2026-2027 (E2, punto 7). Fecha fija segun el
-- calendario civico paraguayo; Jueves y Viernes Santo calculados con el
-- algoritmo de Pascua (Meeus/Jones/Butcher): Pascua 2026 = 05/04/2026,
-- Pascua 2027 = 28/03/2027. El gerente debe revisarlos cada año desde
-- P12: el Poder Ejecutivo a veces traslada feriados por decreto.
-- =====================================================================

INSERT INTO feriados (fecha, descripcion, activo) VALUES
('2026-01-01', 'Año Nuevo', TRUE),
('2026-03-01', 'Día de los Héroes de la Patria', TRUE),
('2026-04-02', 'Jueves Santo', TRUE),
('2026-04-03', 'Viernes Santo', TRUE),
('2026-05-01', 'Día del Trabajador', TRUE),
('2026-05-14', 'Día de la Independencia (víspera)', TRUE),
('2026-05-15', 'Día de la Independencia', TRUE),
('2026-06-12', 'Día de la Paz del Chaco', TRUE),
('2026-08-15', 'Fundación de Asunción', TRUE),
('2026-09-29', 'Día de la Victoria de Boquerón', TRUE),
('2026-12-08', 'Día de la Virgen de Caacupé', TRUE),
('2026-12-25', 'Navidad', TRUE),
('2027-01-01', 'Año Nuevo', TRUE),
('2027-03-01', 'Día de los Héroes de la Patria', TRUE),
('2027-03-25', 'Jueves Santo', TRUE),
('2027-03-26', 'Viernes Santo', TRUE),
('2027-05-01', 'Día del Trabajador', TRUE),
('2027-05-14', 'Día de la Independencia (víspera)', TRUE),
('2027-05-15', 'Día de la Independencia', TRUE),
('2027-06-12', 'Día de la Paz del Chaco', TRUE),
('2027-08-15', 'Fundación de Asunción', TRUE),
('2027-09-29', 'Día de la Victoria de Boquerón', TRUE),
('2027-12-08', 'Día de la Virgen de Caacupé', TRUE),
('2027-12-25', 'Navidad', TRUE);

-- =====================================================================
-- Departamentos y ciudades de Paraguay (Bloque 7/E10, Tanda A, D-126).
-- Sembrado inicial: los 17 departamentos + Asunción (capital); ciudades
-- con los 19 distritos de Central completos (donde va a vivir la mayoría
-- de los clientes, y donde la Tanda B va a limitar la política de
-- crédito) más la ciudad capital de cada uno de los demás departamentos,
-- ampliable después sin tocar código (mismos datos que
-- bd/migraciones/E10_01_tanda_a_datos_cliente.sql).
-- =====================================================================

INSERT INTO departamentos (nombre) VALUES
('Concepción'), ('San Pedro'), ('Cordillera'), ('Guairá'), ('Caaguazú'),
('Caazapá'), ('Itapúa'), ('Misiones'), ('Paraguarí'), ('Alto Paraná'),
('Central'), ('Ñeembucú'), ('Amambay'), ('Canindeyú'), ('Presidente Hayes'),
('Alto Paraguay'), ('Boquerón'), ('Asunción');

INSERT INTO ciudades (id_departamento, nombre) VALUES
((SELECT id_departamento FROM departamentos WHERE nombre = 'Asunción'), 'Asunción'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Areguá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Capiatá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Fernando de la Mora'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Guarambaré'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Itá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Itauguá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Julián Augusto Saldívar'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Lambaré'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Limpio'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Luque'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Mariano Roque Alonso'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Ñemby'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Nueva Italia'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'San Antonio'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'San Lorenzo'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Villa Elisa'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Villeta'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Ypacaraí'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Central'), 'Ypané'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Concepción'), 'Concepción'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'San Pedro'), 'San Pedro de Ycuamandiyú'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Cordillera'), 'Caacupé'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Guairá'), 'Villarrica'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Caaguazú'), 'Coronel Oviedo'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Caazapá'), 'Caazapá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Itapúa'), 'Encarnación'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Misiones'), 'San Juan Bautista'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Paraguarí'), 'Paraguarí'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Alto Paraná'), 'Ciudad del Este'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Ñeembucú'), 'Pilar'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Amambay'), 'Pedro Juan Caballero'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Canindeyú'), 'Salto del Guairá'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Presidente Hayes'), 'Villa Hayes'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Alto Paraguay'), 'Fuerte Olimpo'),
((SELECT id_departamento FROM departamentos WHERE nombre = 'Boquerón'), 'Filadelfia');

-- =====================================================================
-- Motivos de rechazo de solicitud (Bloque 7/E10, Tanda A, D-126).
-- Reemplaza al <select> de opciones fijas de aprobaciones/ficha.jsp.
--
-- RF10 (pedido de Joel, 11/10/2026): POLITICA_LABORAL/ZONA/EDAD nacen con
-- activo = FALSE -- corresponden exactamente a lo que
-- ServicioSolicitudes.validarPoliticaCredito ya bloquea al enviar la
-- solicitud a aprobación, así que el gerente nunca tiene un motivo real
-- para elegirlos al rechazar (ver docs/funcionamiento/RF10_rechazar.md
-- §6.2). Quedan las filas (no se borran, por si algún informe futuro
-- quiere listar "todos los motivos alguna vez definidos"), solo no
-- aparecen en el <select>. Mismo cambio que
-- bd/migraciones/E10_12_motivos_rechazo_desactivar.sql aplica a una base
-- que ya tenía estos 3 en activo = TRUE.
-- =====================================================================

INSERT INTO motivos_rechazo_solicitud (codigo, nombre, requiere_detalle, activo, orden) VALUES
('POLITICA_LABORAL', 'Política laboral (situación laboral no admitida)', FALSE, FALSE, 1),
('ZONA', 'Zona (fuera de la cobertura)', FALSE, FALSE, 2),
('EDAD', 'Edad (fuera del rango permitido)', FALSE, FALSE, 3),
('INFORMCONF', 'Informconf: figura con deudas', FALSE, TRUE, 4),
('CAPACIDAD_PAGO', 'Capacidad de pago insuficiente', FALSE, TRUE, 5),
('DOCUMENTACION_INCOMPLETA', 'Documentación incompleta o inconsistente', FALSE, TRUE, 6),
('OTRO', 'Otro', TRUE, TRUE, 7);

-- 11/11/2026 (limpieza previa a la entrega, decidido el 03/11/2026 al cerrar C22): la nota de
-- por qué caja.gerente_predeterminado sigue existiendo sin que nada lo lea. Va en un UPDATE y
-- no en el INSERT de arriba porque ese INSERT no incluye la columna observacion, y agregársela
-- obligaría a escribir un NULL en las otras ~60 filas. Se muestra en /parametros/historial.
UPDATE parametros_sistema
   SET observacion = 'Desde C22 (03/11/2026) el acta de arqueo nombra al gerente solo si quedó registrado al revisar una diferencia de caja; ningún código lee esta clave. Se conserva por el dato histórico de quién figuraba antes. Cambiar su valor no afecta el arqueo.'
 WHERE clave_parametro = 'caja.gerente_predeterminado'
   AND vigente_hasta IS NULL;
