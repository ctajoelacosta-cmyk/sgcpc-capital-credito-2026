-- =====================================================================
-- E10_01_tanda_a_datos_cliente.sql
-- Migración sobre la base EXISTENTE (sgcpc_db) para el Bloque 7 (E10),
-- Tanda A: datos del cliente (D-126, AVANCE.md -- decisiones de Joel tras
-- el inventario de campos aprobado, D-124).
--
-- Agrega: vencimiento de cédula, datos del cónyuge (obligatorios solo si
-- estado civil es "Casado/a" o "Unión de hecho"), domicilio por partes
-- (departamento/ciudad de catálogos nuevos, barrio y referencia en texto
-- libre), tipo de vivienda y antigüedad en el domicilio, una segunda
-- referencia personal, y una lista referencial de motivos de rechazo de
-- solicitud (antes texto libre).
--
-- Sin backfill de personas/solicitudes_credito: las columnas nuevas quedan
-- NULL en los registros existentes -- por diseño (REGLAS_DESARROLLO.md, D-126): los
-- campos nuevos son obligatorios solo en altas nuevas, y los registros
-- existentes se completan recién cuando se editan.
--
-- NO se corre en una instalación nueva: ahí alcanza con 01_esquema.sql,
-- 02_indices.sql y 03_parametros.sql, que ya incluyen estos mismos cambios
-- desde el origen.
--
-- Se corre UNA vez, a mano, conectado a sgcpc_db, recién después del OK
-- de Joel sobre este archivo:
--
--   source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
--     src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
--     psql -h localhost -U sgcpc_user -d sgcpc_db -v ON_ERROR_STOP=1 \
--     -f bd/migraciones/E10_01_tanda_a_datos_cliente.sql
--
-- Todo en una sola transacción: o se aplica entero, o no se aplica nada.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Catálogos nuevos: departamentos y ciudades de Paraguay. Sembrado
-- inicial (D-126): los 17 departamentos + Asunción (capital); ciudades
-- con los 19 distritos de Central completos (donde va a vivir la mayoría
-- de los clientes, y donde la Tanda B va a limitar la política de
-- crédito) más la ciudad capital de cada uno de los demás departamentos,
-- ampliable después sin tocar código.
-- ---------------------------------------------------------------------
CREATE TABLE departamentos (
    id_departamento  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(60) NOT NULL UNIQUE,
    activo           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE ciudades (
    id_ciudad         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_departamento   BIGINT NOT NULL,
    nombre            VARCHAR(80) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (id_departamento, nombre)
);

-- ---------------------------------------------------------------------
-- Catálogo nuevo: motivos de rechazo de solicitud (D-126). Reemplaza al
-- <select> de opciones fijas que tenía aprobaciones/ficha.jsp -- el texto
-- libre en solicitudes_credito.motivo_rechazo queda como el "detalle"
-- (obligatorio solo para el motivo "Otro").
-- ---------------------------------------------------------------------
CREATE TABLE motivos_rechazo_solicitud (
    id_motivo_rechazo  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR(30)  NOT NULL UNIQUE,
    nombre              VARCHAR(100) NOT NULL,
    requiere_detalle    BOOLEAN NOT NULL DEFAULT FALSE,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    orden               SMALLINT NOT NULL
);

-- ---------------------------------------------------------------------
-- personas: vencimiento de cédula, cónyuge, domicilio por partes, tipo de
-- vivienda, antigüedad y segunda referencia (D-126). "domicilio" (texto
-- libre, ya existente) NO se quita: ServicioClientes lo sigue armando
-- automáticamente a partir de las partes nuevas, para no tener que tocar
-- todos los lugares que ya lo leen (contrato, pagaré, reportes).
-- ---------------------------------------------------------------------
-- 600 (no 250): desde ahora "domicilio" se arma solo a partir de barrio + ciudad + departamento +
-- referencia libre (hasta 300) -- ver ServicioClientes.armarDomicilioDesdePartes.
ALTER TABLE personas ALTER COLUMN domicilio TYPE VARCHAR(600);

ALTER TABLE personas ADD COLUMN fecha_vencimiento_cedula   DATE NULL;
ALTER TABLE personas ADD COLUMN conyuge_nombre             VARCHAR(150) NULL;
ALTER TABLE personas ADD COLUMN conyuge_cedula              VARCHAR(20) NULL;
ALTER TABLE personas ADD COLUMN conyuge_telefono            VARCHAR(30) NULL;
ALTER TABLE personas ADD COLUMN id_departamento             BIGINT NULL;
ALTER TABLE personas ADD COLUMN id_ciudad                   BIGINT NULL;
ALTER TABLE personas ADD COLUMN barrio                      VARCHAR(150) NULL;
ALTER TABLE personas ADD COLUMN referencia_domicilio        VARCHAR(300) NULL;
ALTER TABLE personas ADD COLUMN tipo_vivienda               VARCHAR(20) NULL
    CHECK (tipo_vivienda IN ('PROPIA', 'ALQUILADA', 'FAMILIAR'));
ALTER TABLE personas ADD COLUMN antiguedad_domicilio_anios  SMALLINT NULL;
ALTER TABLE personas ADD COLUMN referencia2_nombre          VARCHAR(150) NULL;
ALTER TABLE personas ADD COLUMN referencia2_telefono        VARCHAR(30) NULL;

-- ---------------------------------------------------------------------
-- solicitudes_credito: motivo de rechazo referencial (D-126). motivo_
-- rechazo (TEXT, ya existente) sigue guardando el texto combinado
-- "nombre del motivo [- detalle]", para no romper las pantallas/reportes
-- que ya lo muestran tal cual.
-- ---------------------------------------------------------------------
ALTER TABLE solicitudes_credito ADD COLUMN id_motivo_rechazo BIGINT NULL;

-- ---------------------------------------------------------------------
-- Restricciones de clave foránea
-- ---------------------------------------------------------------------
ALTER TABLE ciudades ADD CONSTRAINT fk_ciudades_departamento
    FOREIGN KEY (id_departamento) REFERENCES departamentos (id_departamento);

ALTER TABLE personas ADD CONSTRAINT fk_personas_departamento
    FOREIGN KEY (id_departamento) REFERENCES departamentos (id_departamento);
ALTER TABLE personas ADD CONSTRAINT fk_personas_ciudad
    FOREIGN KEY (id_ciudad) REFERENCES ciudades (id_ciudad);

ALTER TABLE solicitudes_credito ADD CONSTRAINT fk_solicitudes_motivo_rechazo
    FOREIGN KEY (id_motivo_rechazo) REFERENCES motivos_rechazo_solicitud (id_motivo_rechazo);

-- ---------------------------------------------------------------------
-- Semilla de departamentos y ciudades (mismos datos que bd/03_parametros.sql)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Semilla de motivos de rechazo (mismos datos que bd/03_parametros.sql)
-- ---------------------------------------------------------------------
INSERT INTO motivos_rechazo_solicitud (codigo, nombre, requiere_detalle, activo, orden) VALUES
('POLITICA_LABORAL', 'Política laboral (situación laboral no admitida)', FALSE, TRUE, 1),
('ZONA', 'Zona (fuera de la cobertura)', FALSE, TRUE, 2),
('EDAD', 'Edad (fuera del rango permitido)', FALSE, TRUE, 3),
('INFORMCONF', 'Informconf: figura con deudas', FALSE, TRUE, 4),
('CAPACIDAD_PAGO', 'Capacidad de pago insuficiente', FALSE, TRUE, 5),
('DOCUMENTACION_INCOMPLETA', 'Documentación incompleta o inconsistente', FALSE, TRUE, 6),
('OTRO', 'Otro', TRUE, TRUE, 7);

COMMIT;
