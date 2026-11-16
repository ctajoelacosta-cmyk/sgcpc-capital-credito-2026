<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ page import="py.com.capitalcredito.sgcpc.infraestructura.FabricaConexionBD" %>
<%--
    Portabilidad (pedido de Joel): página dedicada para cuando el sistema no puede conectarse a
    PostgreSQL, típicamente al desplegar el .war en una PC o servidor nuevo (el docente), con
    otro usuario, contraseña o nombre de base. A propósito, sin incluir la cabecera ni el pie de
    la plantilla (ni sesión, ni parámetros, ni nada que dependa de la base): si lo que falla es
    justo la conexión, esta página tiene que poder mostrarse igual. Ver ErrorConexionBD (la
    excepción que llega acá, registrada en web.xml) y FabricaConexionBD (los 3 niveles de
    configuración: variables de entorno, el archivo externo en la carpeta "conf" del Tomcat, o
    bd.properties empaquetado).
--%>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>No se pudo conectar a la base de datos — SGCPC</title>
    <style>
        body { font-family: Arial, Helvetica, sans-serif; background: #F4F6F8; color: #1B2733; margin: 0; padding: 40px 20px; }
        .caja { max-width: 720px; margin: 0 auto; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 6px; padding: 32px; }
        h1 { color: #B91C1C; font-size: 1.4rem; margin-top: 0; }
        .dato { background: #F4F6F8; border: 1px solid #E2E8F0; border-radius: 4px; padding: 10px 14px; margin: 6px 0; font-family: "Courier New", monospace; font-size: 0.9rem; word-break: break-all; }
        ol { padding-left: 20px; }
        li { margin-bottom: 8px; }
    </style>
</head>
<body>
    <div class="caja">
        <h1>No se pudo conectar a la base de datos</h1>
        <p>El sistema no pudo abrir ninguna conexión a PostgreSQL. Esto pasa normalmente al
           instalar el sistema en una computadora nueva, con otro usuario, contraseña o nombre de
           base distintos a los que ya tenía configurados.</p>

        <p><strong>Configuración que está usando ahora mismo:</strong></p>
        <div class="dato"><%= FabricaConexionBD.descripcionFuenteConfiguracionActiva() %></div>
        <div class="dato"><%= FabricaConexionBD.descripcionDestinoConexion() %></div>

        <p><strong>Para corregirlo, revisar en este orden:</strong></p>
        <ol>
            <li>Variables de entorno <code>SGCPC_BD_URL</code>, <code>SGCPC_BD_USUARIO</code> y
                <code>SGCPC_BD_CONTRASENA</code> (si están definidas, se usan antes que cualquier
                archivo).</li>
            <li>El archivo <code><%= FabricaConexionBD.rutaArchivoExterno() %></code> — si existe,
                revisar que <code>bd.url</code>, <code>bd.usuario</code> y
                <code>bd.contrasena</code> sean correctos para esta PostgreSQL.</li>
            <li>Si no hay variables de entorno ni ese archivo, el sistema usa el
                <code>bd.properties</code> empaquetado dentro del <code>.war</code> — para
                cambiarlo sin recompilar, crear el archivo externo del punto anterior.</li>
            <li>Confirmar que el servicio de PostgreSQL esté corriendo y acepte conexiones en el
                host y puerto indicados arriba, y que la base y el usuario existan.</li>
        </ol>
    </div>
</body>
</html>
