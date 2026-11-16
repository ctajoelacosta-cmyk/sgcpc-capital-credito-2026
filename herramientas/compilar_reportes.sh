#!/bin/bash
# B (reprueba Lote 1, pedido de Joel): compila TODOS los .jrxml de
# src/main/resources/reportes/ a su .jasper correspondiente, y guarda el hash de cada .jrxml
# usado en herramientas/reportes_jasper.sha256 -- ReportesCompiladosTest (corre con cualquier
# "mvn test") compara ese hash contra el .jrxml actual, así que un .jasper desactualizado hace
# fallar la build en vez de pasar desapercibido.
#
# PASO OBLIGATORIO después de editar cualquier .jrxml (docs/REGLAS_DESARROLLO.md) -- antes de
# comitear el cambio, correr este script.
#
# Hallazgo de Joel (13/10/2026, RF40/F3): recompilar los 41 reportes aunque se haya editado uno
# solo deja los otros 40 .jasper con bytes distintos a los commiteados (el compilador de Jasper
# no es reproducible bit a bit, aunque el diseño no cambie) -- ruido que hay que distinguir de un
# cambio real, y un riesgo innecesario para reportes de RF ya probados. Por eso, con un
# argumento, compila SOLO ese reporte (no toca ningún otro .jasper ni ninguna otra línea del
# manifiesto).
#
# Uso: herramientas/compilar_reportes.sh                     (compila los 41, como siempre)
#      herramientas/compilar_reportes.sh informe_auditoria   (compila solo ese, con o sin ".jrxml")

set -e
cd "$(dirname "$0")/.."
source ./entorno.sh
if [ -n "$1" ]; then
    mvn -Dtest=HerramientaCompilarReportes "-Dreporte=$1" test
else
    mvn -Dtest=HerramientaCompilarReportes test
fi
