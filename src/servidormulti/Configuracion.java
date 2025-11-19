package servidormulti;
public final class Configuracion {
    private Configuracion() {}
    // Configuración del Servidor
    public static final int PUERTO_SERVIDOR = 8080;
    public static final String DRIVER_CLASE = "org.sqlite.JDBC";

    // Configuración de la Base de Datos (BD)
    public static final String URL_BD = "jdbc:sqlite:usuarios.db";

    // Configuración de Grupos y Logs
    public static final String CHAT_LOGS_DIR = "chat_logs";
    public static final int ID_GRUPO_TODOS = 1;
    public static final String NOMBRE_GRUPO_TODOS = "Todos";
    public static final int ES_BORRABLE_NO = 0;

    // Configuración del Cliente/Invitado
    public static final int LIMITE_MENSAJES_INVITADO = 3;

    // Configuración de Ranking
    public static final int PUNTAJE_VICTORIA = 2;
    public static final int PUNTAJE_EMPATE = 1;

    // SQL de Creación de Tablas
    public static final String SQL_CREATE_USUARIOS = "CREATE TABLE IF NOT EXISTS usuarios (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    username VARCHAR(15) NOT NULL UNIQUE," +
            "    password VARCHAR(15) NOT NULL" +
            ");";
    public static final String SQL_CREATE_BLOQUEADOS = "CREATE TABLE IF NOT EXISTS bloqueados (" +
            "    bloqueador_id INTEGER NOT NULL," +
            "    bloqueado_id INTEGER NOT NULL," +
            "    PRIMARY KEY (bloqueador_id, bloqueado_id)," +
            "    FOREIGN KEY (bloqueador_id) REFERENCES usuarios(id) ON DELETE CASCADE," +
            "    FOREIGN KEY (bloqueado_id) REFERENCES usuarios(id) ON DELETE CASCADE" +
            ");";
    public static final String SQL_CREATE_RANKING = "CREATE TABLE IF NOT EXISTS ranking (" +
            "    usuario_id INTEGER PRIMARY KEY," +
            "    victorias INTEGER NOT NULL DEFAULT 0," +
            "    derrotas INTEGER NOT NULL DEFAULT 0," +
            "    empates INTEGER NOT NULL DEFAULT 0," +
            "    puntaje INTEGER NOT NULL DEFAULT 0," +
            "    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE" +
            ");";
    public static final String SQL_CREATE_GRUPOS = "CREATE TABLE IF NOT EXISTS grupos (" +
            "    grupo_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    nombre_grupo VARCHAR(25) NOT NULL UNIQUE," +
            "    creador_id INTEGER," +
            "    es_borrable INTEGER NOT NULL DEFAULT 1," +
            "    FOREIGN KEY (creador_id) REFERENCES usuarios(id) ON DELETE SET NULL" +
            ");";

    public static final String SQL_CREATE_GRUPO_MIEMBROS = "CREATE TABLE IF NOT EXISTS grupo_miembros (" +
            "    grupo_id INTEGER NOT NULL," +
            "    usuario_id INTEGER NOT NULL," +
            "    PRIMARY KEY (grupo_id, usuario_id)," +
            "    FOREIGN KEY (grupo_id) REFERENCES grupos(id) ON DELETE CASCADE," +
            "    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE" +
            ");";
}