package py.com.capitalcredito.sgcpc.servicios;

import java.util.EnumMap;
import java.util.Map;
import py.com.capitalcredito.sgcpc.enumeraciones.NivelAcceso;
import py.com.capitalcredito.sgcpc.enumeraciones.Permiso;
import py.com.capitalcredito.sgcpc.enumeraciones.PerfilUsuario;

/**
 * Matriz de permisos del Doc. 00 §3.1, como datos en memoria. Pura: no
 * toca sesion ni base. RNF11: el permiso se valida siempre aca (servidor),
 * FiltroAutorizacion es el unico que la consulta antes de dejar pasar una
 * accion; el menu solo oculta lo que igual esta bloqueado aca.
 */
public class ServicioAutorizacion {

    private static final Map<PerfilUsuario, Map<Permiso, NivelAcceso>> MATRIZ = construirMatriz();

    public NivelAcceso nivelAcceso(PerfilUsuario perfil, Permiso permiso) {
        if (perfil == null || permiso == null) {
            return NivelAcceso.NINGUNO;
        }
        return MATRIZ.get(perfil).getOrDefault(permiso, NivelAcceso.NINGUNO);
    }

    public boolean tienePermiso(PerfilUsuario perfil, Permiso permiso) {
        return nivelAcceso(perfil, permiso) != NivelAcceso.NINGUNO;
    }

    public boolean tieneAccesoTotal(PerfilUsuario perfil, Permiso permiso) {
        return nivelAcceso(perfil, permiso) == NivelAcceso.TOTAL;
    }

    private static Map<PerfilUsuario, Map<Permiso, NivelAcceso>> construirMatriz() {
        Map<PerfilUsuario, Map<Permiso, NivelAcceso>> matriz = new EnumMap<>(PerfilUsuario.class);

        Map<Permiso, NivelAcceso> gerente = new EnumMap<>(Permiso.class);
        gerente.put(Permiso.COTIZAR, NivelAcceso.TOTAL);
        gerente.put(Permiso.REGISTRAR_EDITAR_CLIENTE, NivelAcceso.TOTAL);
        gerente.put(Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO, NivelAcceso.TOTAL);
        gerente.put(Permiso.GESTIONAR_SOLICITUD, NivelAcceso.NINGUNO);
        gerente.put(Permiso.APROBAR_RECHAZAR_SOLICITUD, NivelAcceso.TOTAL);
        gerente.put(Permiso.GENERAR_DOCUMENTOS_FORMALIZACION, NivelAcceso.TOTAL);
        gerente.put(Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO, NivelAcceso.TOTAL);
        gerente.put(Permiso.ABRIR_CERRAR_CAJA, NivelAcceso.CONSULTA);
        gerente.put(Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA, NivelAcceso.TOTAL);
        gerente.put(Permiso.DESEMBOLSAR, NivelAcceso.NINGUNO);
        gerente.put(Permiso.COBRAR, NivelAcceso.NINGUNO);
        // D-146 (E-5, pedido de Joel): el gerente controla la rendición -- puede ver y reimprimir
        // los PDF ya emitidos (recibo, factura, constancia, comprobante de desembolso), pero
        // CONSULTA alcanza: estas rutas son todas GET, nunca hay un POST que exija TOTAL.
        gerente.put(Permiso.VER_COMPROBANTES_COBRANZA, NivelAcceso.CONSULTA);
        gerente.put(Permiso.SOLICITAR_REVERSION, NivelAcceso.NINGUNO);
        gerente.put(Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA, NivelAcceso.TOTAL);
        gerente.put(Permiso.REGISTRAR_GESTION_COBRANZA, NivelAcceso.TOTAL);
        gerente.put(Permiso.VER_PANEL_REPORTES, NivelAcceso.TOTAL);
        gerente.put(Permiso.VER_REPORTES, NivelAcceso.TOTAL);
        // D-151 (pedido de Joel): esto deja sin efecto, para estos dos permisos, lo decidido en
        // D7 -- el administrador responde legalmente por el alta/baja/edición de usuarios y
        // actúa en presencia del dueño; el gerente pierde Usuarios por completo y Parámetros le
        // queda en CONSULTA (ve valores/historial, nunca los botones de modificar).
        gerente.put(Permiso.ADMINISTRAR_USUARIOS, NivelAcceso.NINGUNO);
        gerente.put(Permiso.ADMINISTRAR_PARAMETROS, NivelAcceso.CONSULTA);
        gerente.put(Permiso.CONSULTAR_AUDITORIA, NivelAcceso.TOTAL);
        matriz.put(PerfilUsuario.GERENTE_ADMINISTRADOR, gerente);

        // D7 (pedido de Joel, anteproyecto entregado): ADMINISTRADOR tiene acceso a TODO el
        // sistema -- TOTAL en todos los permisos, sin excepción. GERENTE_ADMINISTRADOR (arriba)
        // no se toca.
        Map<Permiso, NivelAcceso> administrador = new EnumMap<>(Permiso.class);
        for (Permiso permiso : Permiso.values()) {
            administrador.put(permiso, NivelAcceso.TOTAL);
        }
        matriz.put(PerfilUsuario.ADMINISTRADOR, administrador);

        Map<Permiso, NivelAcceso> oficial = new EnumMap<>(Permiso.class);
        oficial.put(Permiso.COTIZAR, NivelAcceso.TOTAL);
        oficial.put(Permiso.REGISTRAR_EDITAR_CLIENTE, NivelAcceso.TOTAL);
        oficial.put(Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO, NivelAcceso.TOTAL);
        oficial.put(Permiso.GESTIONAR_SOLICITUD, NivelAcceso.TOTAL);
        oficial.put(Permiso.APROBAR_RECHAZAR_SOLICITUD, NivelAcceso.NINGUNO);
        oficial.put(Permiso.GENERAR_DOCUMENTOS_FORMALIZACION, NivelAcceso.TOTAL);
        oficial.put(Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO, NivelAcceso.NINGUNO);
        oficial.put(Permiso.ABRIR_CERRAR_CAJA, NivelAcceso.NINGUNO);
        oficial.put(Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA, NivelAcceso.NINGUNO);
        oficial.put(Permiso.DESEMBOLSAR, NivelAcceso.NINGUNO);
        oficial.put(Permiso.COBRAR, NivelAcceso.NINGUNO);
        // D-146 (E-5): no pedido para el oficial -- sigue sin ver estos PDF, igual que hoy.
        oficial.put(Permiso.VER_COMPROBANTES_COBRANZA, NivelAcceso.NINGUNO);
        oficial.put(Permiso.SOLICITAR_REVERSION, NivelAcceso.NINGUNO);
        oficial.put(Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA, NivelAcceso.NINGUNO);
        oficial.put(Permiso.REGISTRAR_GESTION_COBRANZA, NivelAcceso.TOTAL);
        oficial.put(Permiso.VER_PANEL_REPORTES, NivelAcceso.CONSULTA);
        // Decisión 2 (Bloque 4, E8): CONSULTA, no NINGUNO -- el oficial entra a /reportes, pero
        // ControladorReportesServlet limita QUÉ sub-reportes ve (Vencimientos, Morosidad,
        // Gestiones de cobranza, Estado de cuenta); los demás dan 403 aunque este nivel alcance.
        oficial.put(Permiso.VER_REPORTES, NivelAcceso.CONSULTA);
        oficial.put(Permiso.ADMINISTRAR_USUARIOS, NivelAcceso.NINGUNO);
        oficial.put(Permiso.ADMINISTRAR_PARAMETROS, NivelAcceso.NINGUNO);
        oficial.put(Permiso.CONSULTAR_AUDITORIA, NivelAcceso.NINGUNO);
        matriz.put(PerfilUsuario.OFICIAL_CREDITO, oficial);

        Map<Permiso, NivelAcceso> cajero = new EnumMap<>(Permiso.class);
        cajero.put(Permiso.COTIZAR, NivelAcceso.TOTAL);
        cajero.put(Permiso.REGISTRAR_EDITAR_CLIENTE, NivelAcceso.CONSULTA);
        cajero.put(Permiso.CARGAR_DOCUMENTOS_CONSULTA_BURO, NivelAcceso.NINGUNO);
        cajero.put(Permiso.GESTIONAR_SOLICITUD, NivelAcceso.NINGUNO);
        cajero.put(Permiso.APROBAR_RECHAZAR_SOLICITUD, NivelAcceso.NINGUNO);
        cajero.put(Permiso.GENERAR_DOCUMENTOS_FORMALIZACION, NivelAcceso.NINGUNO);
        cajero.put(Permiso.ANULAR_PRESTAMO_PRE_DESEMBOLSO, NivelAcceso.NINGUNO);
        cajero.put(Permiso.ABRIR_CERRAR_CAJA, NivelAcceso.TOTAL);
        cajero.put(Permiso.ENTREGAR_RECIBIR_EFECTIVO_CAJA, NivelAcceso.TOTAL);
        cajero.put(Permiso.DESEMBOLSAR, NivelAcceso.TOTAL);
        cajero.put(Permiso.COBRAR, NivelAcceso.TOTAL);
        // D-146 (E-5): estas rutas (recibo/factura/constancia/comprobante de desembolso) dejan
        // de resolverse por COBRAR/DESEMBOLSAR (ver FiltroAutorizacion.permisoDeDocumento) -- sin
        // esta entrada la cajera perdería el acceso que ya tenía. CONSULTA alcanza (solo GET).
        cajero.put(Permiso.VER_COMPROBANTES_COBRANZA, NivelAcceso.CONSULTA);
        cajero.put(Permiso.SOLICITAR_REVERSION, NivelAcceso.TOTAL);
        cajero.put(Permiso.AUTORIZAR_REVERSION_O_DIFERENCIA, NivelAcceso.NINGUNO);
        // Decisión 4 (Bloque 4, E8): NINGUNO, no TOTAL -- la cajera nunca registra una gestión
        // manual (llamada/mensaje/visita) en /mora, que además no está en su menú. La gestión
        // PAGO_PARCIAL que genera confirmarCobro es una llamada interna al DAO dentro de una
        // acción ya autorizada por COBRAR: no pasa por este permiso, así que este cambio no la
        // afecta en nada.
        cajero.put(Permiso.REGISTRAR_GESTION_COBRANZA, NivelAcceso.NINGUNO);
        cajero.put(Permiso.VER_PANEL_REPORTES, NivelAcceso.CONSULTA);
        cajero.put(Permiso.VER_REPORTES, NivelAcceso.NINGUNO);
        cajero.put(Permiso.ADMINISTRAR_USUARIOS, NivelAcceso.NINGUNO);
        cajero.put(Permiso.ADMINISTRAR_PARAMETROS, NivelAcceso.NINGUNO);
        cajero.put(Permiso.CONSULTAR_AUDITORIA, NivelAcceso.NINGUNO);
        matriz.put(PerfilUsuario.CAJERO, cajero);

        return matriz;
    }
}
