# SGCPC — Sistema de Gestión de Créditos de Capital Crédito

Aplicación web desarrollada a medida para **Capital Crédito**, una casa de créditos del Paraguay.
Cubre el circuito completo del préstamo personal: alta y ficha del cliente, solicitud de crédito,
evaluación y aprobación, formalización con sus documentos (contrato, pagaré, plan de pagos),
desembolso, cobro en el mostrador con emisión de recibo y factura, mora, caja y arqueo, y los
informes de gestión.

Este repositorio acompaña al trabajo final de grado que documenta el sistema.

## Tecnologías

| | |
|---|---|
| Lenguaje | Java 17 (JDK Temurin 17) |
| Empaquetado | Maven → `sgcpc.war` |
| Servidor de aplicaciones | Apache Tomcat 9 |
| Base de datos | PostgreSQL 16 (driver JDBC 42.7.1) |
| Vista | JSP + JSTL sobre AdminLTE / Bootstrap, servidos localmente (el sistema funciona sin internet) |
| Reportes y comprobantes | JasperReports 6.20 (43 informes `.jrxml`, con las fuentes embebidas) |
| Contraseñas | BCrypt (jBCrypt) |
| Pruebas | JUnit 4 |

El bytecode se genera para Java 8 (`maven.compiler.release=8`) por compatibilidad con el Tomcat 9
del servidor de destino; la compilación se hace con el JDK 17.

## Cómo compilarlo

Requisitos: JDK 17 y Maven 3.

```bash
mvn clean package
```

El resultado es `target/sgcpc.war`, listo para desplegar en Tomcat 9.

Para crear la base, los scripts de `bd/` se corren en este orden sobre una base PostgreSQL vacía:

```
bd/01_esquema.sql     → tablas y relaciones
bd/02_indices.sql     → índices
bd/03_parametros.sql  → parámetros del sistema y datos iniciales
```

Las migraciones posteriores están en `bd/migraciones/`, en orden.

## La conexión a la base se configura en el servidor

**El repositorio no contiene ninguna contraseña, ni datos de conexión reales.**

La conexión se define en el archivo `configuracion/bd.properties`, que **no forma parte del
repositorio**: se completa en cada instalación. En el proyecto solo están las plantillas, con el
campo de la contraseña vacío:

- `src/main/resources/configuracion/bd.properties.ejemplo`
- `src/main/resources/configuracion/sgcpc.properties.ejemplo`

Para no tener que recompilar el `.war` al cambiar de servidor, el sistema también lee la conexión
desde `sgcpc.properties` ubicado en la carpeta `conf` del Tomcat que lo despliega
(`${catalina.base}/conf/sgcpc.properties`), con el mismo formato:

```properties
bd.url=jdbc:postgresql://localhost:5432/NOMBRE_DE_LA_BASE
bd.usuario=USUARIO
bd.contrasena=CONTRASEÑA
```

## Estructura

```
src/main/java/     código de la aplicación (servlets, servicios, DAO, DTO)
src/main/webapp/   pantallas JSP, recursos estáticos y los reportes .jrxml
src/test/java/     pruebas JUnit
bd/                esquema, índices, datos iniciales y migraciones
docs/              documentación del sistema: requisitos, modelo de datos,
                   funcionamiento por requisito, guía visual y recorrido de uso
herramientas/      scripts de apoyo (compilar reportes, respaldos)
pruebas/           prueba de humo HTTP sobre el sistema desplegado
```

Algunos documentos hacen referencia a `AVANCE.md` y a `docs/entregas/`, que **no se publican**:
el primero es la bitácora interna del desarrollo y el segundo contiene las guías de instalación
del servidor de la institución. Nada de eso forma parte del sistema ni hace falta para
compilarlo o entenderlo.

## Licencia

Software a medida, de **uso exclusivo de Capital Crédito**. No es software libre ni de código
abierto. Ver [`LICENCIA.md`](LICENCIA.md).
