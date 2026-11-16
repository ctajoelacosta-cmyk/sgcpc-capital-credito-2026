package py.com.capitalcredito.sgcpc.enumeraciones;

/**
 * Una fila de la matriz de permisos del Doc. 00 §3.1. No son pantallas: son
 * acciones de negocio, para que un mismo permiso sirva a varias etapas.
 */
public enum Permiso {
    COTIZAR,
    REGISTRAR_EDITAR_CLIENTE,
    CARGAR_DOCUMENTOS_CONSULTA_BURO,
    GESTIONAR_SOLICITUD,
    APROBAR_RECHAZAR_SOLICITUD,
    GENERAR_DOCUMENTOS_FORMALIZACION,
    ANULAR_PRESTAMO_PRE_DESEMBOLSO,
    ABRIR_CERRAR_CAJA,
    ENTREGAR_RECIBIR_EFECTIVO_CAJA,
    DESEMBOLSAR,
    COBRAR,
    // D-146 (E-5, pedido de Joel): ver/reimprimir los PDF ya emitidos de cobranza/desembolso
    // (recibo, factura, constancia de cancelación, nota de anulación, comprobante de
    // desembolso) -- distinto de COBRAR/DESEMBOLSAR (que exigen poder cobrar/desembolsar de
    // verdad), porque el gerente controla la rendición y necesita verlos sin poder operar.
    VER_COMPROBANTES_COBRANZA,
    SOLICITAR_REVERSION,
    AUTORIZAR_REVERSION_O_DIFERENCIA,
    REGISTRAR_GESTION_COBRANZA,
    VER_PANEL_REPORTES,
    VER_REPORTES,
    // D-151 (pedido de Joel): ADMINISTRAR_USUARIOS_PARAMETROS se parte en dos -- Usuarios pasa a
    // ser exclusivo del administrador (responde legalmente, actúa en presencia del dueño) y deja
    // de poder tener el mismo nivel que Parámetros (donde el gerente sigue en CONSULTA). Esto
    // deja sin efecto, para estos dos permisos, lo decidido en D7 para GERENTE_ADMINISTRADOR.
    ADMINISTRAR_USUARIOS,
    ADMINISTRAR_PARAMETROS,
    CONSULTAR_AUDITORIA
}
