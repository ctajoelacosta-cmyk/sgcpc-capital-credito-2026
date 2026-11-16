# Entorno del proyecto SGCPC para Git Bash.
# Uso: source ./entorno.sh && <comando>
# Solo afecta a la terminal actual; no cambia nada global en Windows.
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
export MAVEN_HOME="/c/Program Files/NetBeans-25/netbeans/java/maven"
export PG_BIN="/c/Program Files/PostgreSQL/18/bin"
export CATALINA_HOME="/c/tomcat9"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PG_BIN:$PATH"
export PGCLIENTENCODING=UTF8
for d in "$JAVA_HOME/bin" "$MAVEN_HOME/bin" "$PG_BIN" "$CATALINA_HOME/bin"; do
  [ -d "$d" ] || echo "AVISO: no existe $d" >&2
done
