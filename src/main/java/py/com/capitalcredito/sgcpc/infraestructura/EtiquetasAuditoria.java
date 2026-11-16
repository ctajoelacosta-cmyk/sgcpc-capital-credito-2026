package py.com.capitalcredito.sgcpc.infraestructura;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 3): nombre legible de cada
 * código de operación de auditoria_operaciones -- mismo criterio que GlosarioFinanciero (textos
 * que pasó Joel, agrupados por módulo en el pedido original, no se redactan por cuenta propia).
 * comoEtiquetaLegible(codigo) nunca rompe con un código nuevo que todavía no esté en el mapa --
 * devuelve el código tal cual (mismo criterio que el resto del sistema: nunca fallar por un dato
 * sin mapear). buscarCodigosPorTexto(texto) es la otra mitad del buscador "Operación contiene":
 * qué códigos tienen esa palabra en su nombre legible, normalizado sin mayúsculas ni acentos
 * (NormalizadorTexto.normalizarParaBuscar, mismo criterio que los buscadores del punto 6) -- el
 * código crudo lo sigue encontrando el ILIKE de siempre en AuditoriaDAO, estos 2 mecanismos se
 * combinan ahí, no acá.
 */
public final class EtiquetasAuditoria {

    private EtiquetasAuditoria() {
    }

    private static final Map<String, String> ETIQUETAS = construirEtiquetas();

    private static Map<String, String> construirEtiquetas() {
        Map<String, String> m = new LinkedHashMap<>();

        // --- Acceso y sesión ---
        m.put("ACCESO_CONCEDIDO", "Inicio de sesión");
        m.put("ACCESO_DENEGADO", "Acceso denegado por falta de permiso");
        m.put("ACCESO_FALLIDO", "Inicio de sesión fallido: contraseña incorrecta");
        m.put("ACCESO_CUENTA_BLOQUEADA", "Inicio de sesión rechazado: cuenta bloqueada");
        m.put("ACCESO_CUENTA_INACTIVA", "Inicio de sesión rechazado: cuenta inactiva");
        m.put("ACCESO_USUARIO_INEXISTENTE", "Inicio de sesión rechazado: usuario inexistente");
        m.put("CERRAR_SESION", "Cierre de sesión");
        m.put("CERRAR_SESION_POR_INTENTOS_FALLIDOS", "Cierre de sesión por intentos fallidos");
        m.put("SESION_EXPIRADA", "Sesión expirada por inactividad");
        m.put("CAMBIAR_CONTRASENA", "Cambio de contraseña");
        m.put("CAMBIAR_CONTRASENA_FALLIDO", "Cambio de contraseña fallido");
        m.put("RESTABLECER_CONTRASENA", "Restablecer contraseña");
        m.put("CUENTA_BLOQUEADA", "Bloqueo automático de cuenta");
        m.put("CUENTA_DESBLOQUEADA_AUTOMATICAMENTE", "Desbloqueo automático de cuenta");

        // --- Clientes ---
        m.put("ALTA_CLIENTE", "Alta de cliente");
        m.put("ACTUALIZAR_CLIENTE", "Editar cliente");
        m.put("DAR_DE_BAJA_CLIENTE", "Dar de baja a un cliente");
        m.put("REACTIVAR_CLIENTE", "Reactivar cliente");
        m.put("CARGAR_DOCUMENTO", "Cargar documento del cliente");
        m.put("NUEVA_VERSION_DOCUMENTO", "Reemplazar documento del cliente");
        // C19 (decisión de Joel, 07/11/2026): hasta ahora abrir un documento del expediente no
        // dejaba ningún rastro, y son datos personales (cédulas, comprobantes de ingreso, informes
        // de buró; la cláusula NOVENA del contrato cita la Ley 6534/2020). Se registra CADA
        // apertura. El informe de auditoría ya sabe filtrar por operación, así que se la puede
        // aislar -- o sacar del medio -- escribiendo "Ver documento" o "VER_DOCUMENTO" en el filtro.
        m.put("VER_DOCUMENTO_EXPEDIENTE", "Ver documento del cliente");
        m.put("GENERAR_AUTORIZACION_BURO", "Generar autorización de consulta al buró");
        m.put("REGISTRAR_CONSENTIMIENTO", "Registrar consentimiento crediticio");
        m.put("REGISTRAR_CONSULTA_BURO", "Registrar resultado de consulta al buró");

        // --- Solicitudes ---
        m.put("CREAR_SOLICITUD", "Crear solicitud");
        m.put("ENVIAR_SOLICITUD_APROBACION", "Enviar solicitud a aprobación");

        // --- Aprobación ---
        m.put("APROBAR_SOLICITUD", "Aprobar solicitud");
        m.put("RECHAZAR_SOLICITUD", "Rechazar solicitud");

        // --- Formalización ---
        m.put("GENERAR_DOCUMENTOS_FORMALIZACION", "Generar documentos de formalización");
        m.put("REGENERAR_DOCUMENTOS_FORMALIZACION", "Regenerar documentos de formalización");
        m.put("GENERAR_PDF_FORMALIZACION", "Re-emitir PDF de formalización");
        m.put("CARGAR_DOCUMENTO_FORMALIZACION", "Cargar documento firmado");
        m.put("NUEVA_VERSION_DOCUMENTO_FORMALIZACION", "Reemplazar documento firmado");
        m.put("COMPLETAR_CIERRE_DOCUMENTAL", "Completar cierre documental");

        // --- Desembolso y caja ---
        m.put("ABRIR_CAJA", "Abrir caja");
        m.put("CERRAR_CAJA", "Cerrar caja");
        m.put("ENTREGAR_EFECTIVO_CAJA", "Entregar efectivo a la caja");
        m.put("REVISAR_DIFERENCIA_CAJA", "Marcar como revisada una diferencia de arqueo");
        m.put("DESEMBOLSAR", "Desembolsar préstamo");
        m.put("IMPRIMIR_COMPROBANTE_DESEMBOLSO", "Imprimir comprobante de desembolso");
        m.put("REIMPRIMIR_COMPROBANTE_DESEMBOLSO", "Reimprimir comprobante de desembolso");

        // --- Cobranza y mora ---
        m.put("COBRAR", "Cobrar cuota");
        m.put("ANTICIPAR_CAPITAL", "Adelantar a capital");
        m.put("REGISTRAR_GESTION_COBRANZA", "Registrar gestión de cobranza");
        m.put("RECALCULAR_ESTADO_MORA", "Cambio automático de estado por mora");

        // --- Cancelación ---
        m.put("CANCELAR_SOLICITUD", "Cancelar solicitud");
        m.put("ANULAR_PRESTAMO_PRE_DESEMBOLSO", "Anular préstamo antes del desembolso");
        m.put("CANCELAR_TOTAL", "Cancelación total del préstamo");
        m.put("SOLICITAR_REVERSION", "Solicitar reversión de cobro");
        m.put("AUTORIZAR_REVERSION", "Autorizar reversión de cobro");
        m.put("RECHAZAR_REVERSION", "Rechazar reversión de cobro");

        // --- Parámetros y usuarios ---
        m.put("CAMBIAR_PARAMETRO", "Cambiar parámetro del sistema");
        m.put("ALTA_LIMITE_TASA", "Agregar límite de tasa");
        m.put("ALTA_FERIADO", "Alta de feriado");
        m.put("ACTIVAR_FERIADO", "Activar feriado");
        m.put("DESACTIVAR_FERIADO", "Desactivar feriado");
        m.put("ALTA_USUARIO", "Alta de usuario");
        m.put("ACTUALIZAR_USUARIO", "Editar usuario");
        m.put("INACTIVAR_USUARIO", "Inactivar usuario");
        m.put("REACTIVAR_USUARIO", "Reactivar usuario");
        m.put("BLOQUEAR_USUARIO", "Bloquear usuario");

        // --- Informes ---
        m.put("EMITIR_COTIZACION_PDF", "Emitir PDF de cotización");
        m.put("EMITIR_INFORME_PDF", "Emitir informe en PDF");
        m.put("EXPORTAR_CSV", "Exportar informe a CSV");

        // --- Sistema ---
        m.put("RECALCULO_MORA_DIARIO", "Recálculo diario de mora");

        return m;
    }

    /** Nunca rompe con un código nuevo sin mapear -- devuelve el código tal cual. */
    public static String comoEtiquetaLegible(String codigo) {
        return ETIQUETAS.getOrDefault(codigo, codigo);
    }

    /** Para el <datalist> del filtro "Operación contiene" -- solo nombres legibles (pedido de
     * Joel), ordenados para que sean fáciles de ubicar al escribir. */
    public static List<String> listarEtiquetasOrdenadas() {
        List<String> etiquetas = new ArrayList<>(ETIQUETAS.values());
        etiquetas.sort(String::compareTo);
        return etiquetas;
    }

    /** Tanda de usabilidad (21/10/2026, pedido de Joel, punto 8 paso 2): único lugar que decide
     * si una fila de auditoría es "del sistema" (Usuario "Sistema", sin IP/navegador) -- usado
     * tanto por AuditoriaDAO (columna Usuario) como por ServicioAuditoria (IP/navegador), para no
     * repetir esta misma regla en los dos lugares. RECALCULO_MORA_DIARIO ya tiene
     * tipo_entidad="SISTEMA"; RECALCULAR_ESTADO_MORA (por préstamo, dentro del mismo recálculo
     * diario) queda con tipo_entidad="PRESTAMO" (es sobre ESE préstamo), por eso se mira también
     * la operación, no solo la entidad. */
    public static boolean esOperacionDelSistema(String operacion, String tipoEntidad) {
        return "SISTEMA".equals(tipoEntidad) || "RECALCULAR_ESTADO_MORA".equals(operacion);
    }

    /** Códigos cuyo nombre legible contiene "texto" (normalizado, sin mayúsculas ni acentos) --
     * el código crudo lo sigue encontrando el ILIKE de siempre en AuditoriaDAO, combinado con
     * esto. Lista vacía si texto es null/vacío o no matchea ningún nombre legible (nunca null). */
    public static List<String> buscarCodigosPorTexto(String texto) {
        List<String> codigos = new ArrayList<>();
        String normalizado = NormalizadorTexto.normalizarParaBuscar(texto);
        if (normalizado.isEmpty()) {
            return codigos;
        }
        for (Map.Entry<String, String> entrada : ETIQUETAS.entrySet()) {
            if (NormalizadorTexto.normalizarParaBuscar(entrada.getValue()).contains(normalizado)) {
                codigos.add(entrada.getKey());
            }
        }
        return codigos;
    }
}
