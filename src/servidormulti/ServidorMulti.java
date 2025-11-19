package servidormulti;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServidorMulti {

    private final List<UnCliente> clientesConectados = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
    private final List<JuegoGatito> juegosActivos = Collections.synchronizedList(new ArrayList<>());
    private int contadorInvitados = 0;

    private static Connection conexionBD() throws SQLException {
        return DriverManager.getConnection(Configuracion.URL_BD);
    }

    public synchronized int getSiguienteNumeroInvitado() {
        return ++contadorInvitados;
    }

    public List<UnCliente> getClientesConectados() {
        return clientesConectados;
    }

    private void cerrarConexion(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new ServidorMulti().iniciarServidor();
    }

    public void iniciarServidor() {
        inicializarBaseDeDatos();
        System.out.println("Servidor iniciado en el puerto " + Configuracion.PUERTO_SERVIDOR + " y conectado a SQLite.");

        try (ServerSocket serverSocket = new ServerSocket(Configuracion.PUERTO_SERVIDOR)) {
            aceptarConexiones(serverSocket);
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    private void aceptarConexiones(ServerSocket serverSocket) throws IOException {
        while (true) {
            Socket socket = serverSocket.accept();
            UnCliente nuevoCliente = new UnCliente(socket, this);
            clientesConectados.add(nuevoCliente);
            new Thread(nuevoCliente).start();
        }
    }

    private static void inicializarBaseDeDatos() {
        new File(Configuracion.CHAT_LOGS_DIR).mkdirs();
        try {
            Class.forName(Configuracion.DRIVER_CLASE);
            try (Connection conn = conexionBD();
                 Statement stmt = conn.createStatement()) {

                crearTablas(stmt);
                asegurarGrupoTodos(conn);
                System.out.println("Base de datos SQLite y tablas de grupos listas.");

            } catch (SQLException | IOException e) {
                System.err.println("No se pudo inicializar la base de datos o los logs: " + e.getMessage());
                System.exit(1);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Error CRÍTICO: No se encontró la clase del driver de SQLite.");
            System.exit(1);
        }
    }

    private static void crearTablas(Statement stmt) throws SQLException {
        stmt.execute(Configuracion.SQL_CREATE_USUARIOS);
        stmt.execute(Configuracion.SQL_CREATE_BLOQUEADOS);
        stmt.execute(Configuracion.SQL_CREATE_RANKING);
        stmt.execute(Configuracion.SQL_CREATE_GRUPOS);
        stmt.execute(Configuracion.SQL_CREATE_GRUPO_MIEMBROS);
    }

    private static void asegurarGrupoTodos(Connection conn) throws SQLException, IOException {
        String sqlInsertTodos = "INSERT OR IGNORE INTO grupos (grupo_id, nombre_grupo, creador_id, es_borrable) " +
                "VALUES (?, ?, NULL, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsertTodos)) {
            pstmt.setInt(1, Configuracion.ID_GRUPO_TODOS);
            pstmt.setString(2, Configuracion.NOMBRE_GRUPO_TODOS);
            pstmt.setInt(3, Configuracion.ES_BORRABLE_NO);
            pstmt.execute();
        }
        File logTodos = new File(Configuracion.CHAT_LOGS_DIR + "/" + Configuracion.ID_GRUPO_TODOS + ".txt");
        if (logTodos.createNewFile()) {
            System.out.println("Archivo de log para 'Todos' creado.");
        } else {
            System.out.println("Archivo de log para 'Todos' ya existía.");
        }
    }

    public boolean autenticarUsuario(String username, String password) {
        String sql = "SELECT password FROM usuarios WHERE username = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getString("password").equals(password);

        } catch (SQLException e) {
            System.err.println("Error al autenticar: " + e.getMessage());
            return false;
        }
    }

    public boolean registrarUsuario(String username, String password) {
        Connection conn = null;
        try {
            conn = conexionBD();
            conn.setAutoCommit(false);
            long idUsuario = ejecutarRegistroTransaccion(conn, username, password);
            if (idUsuario == -1) {
                conn.rollback();
                return false;
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            if (e.getErrorCode() == 19) return false;
            e.printStackTrace();
            return false;
        } finally {
            cerrarConexion(conn);
        }
    }

    private long ejecutarRegistroTransaccion(Connection conn, String username, String password) throws SQLException {
        long idUsuario = insertarUsuario(conn, username, password);
        if (idUsuario == -1) return -1;
        insertarRanking(conn, idUsuario);
        insertarMiembroTodos(conn, idUsuario);
        return idUsuario;
    }

    private long insertarUsuario(Connection conn, String username, String password) throws SQLException {
        String sql = "INSERT INTO usuarios(username, password) VALUES(?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private void insertarRanking(Connection conn, long idUsuario) throws SQLException {
        String sql = "INSERT INTO ranking(usuario_id, victorias, derrotas, empates, puntaje) VALUES (?, 0, 0, 0, 0)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, idUsuario);
            pstmt.executeUpdate();
        }
    }

    private void insertarMiembroTodos(Connection conn, long idUsuario) throws SQLException {
        String sql = "INSERT INTO grupo_miembros (grupo_id, usuario_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Configuracion.ID_GRUPO_TODOS);
            pstmt.setLong(2, idUsuario);
            pstmt.executeUpdate();
        }
    }

    public int obtenerIdUsuario(String username) {
        String sql = "SELECT id FROM usuarios WHERE username = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean existeUsuario(String username) {
        return obtenerIdUsuario(username) != -1;
    }

    public boolean estanBloqueados(int idUsuarioA, int idUsuarioB) {
        String sql = "SELECT 1 FROM bloqueados WHERE " +
                "(bloqueador_id = ? AND bloqueado_id = ?) OR " +
                "(bloqueador_id = ? AND bloqueado_id = ?)";

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idUsuarioA);
            pstmt.setInt(2, idUsuarioB);
            pstmt.setInt(3, idUsuarioB);
            pstmt.setInt(4, idUsuarioA);
            return pstmt.executeQuery().next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String bloquearUsuario(int idBloqueador, String usernameBloqueado) {
        if (!existeUsuario(usernameBloqueado)) return "Shavalon el usuario '" + usernameBloqueado + "' no existe.";
        int idBloqueado = obtenerIdUsuario(usernameBloqueado);
        if (idBloqueador == idBloqueado) return "Shavalon no puedes bloquearte a ti mismo.";

        try (Connection conn = conexionBD()) {
            if (yaEstaBloqueado(conn, idBloqueador, idBloqueado)) {
                return "Shavalon ya tenías bloqueado a '" + usernameBloqueado + "'.";
            }
            insertarBloqueo(conn, idBloqueador, idBloqueado);
            return "Has bloqueado de tu vida a '" + usernameBloqueado + "'.";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Shavalon no se pudo procesar el bloqueo.";
        }
    }

    private boolean yaEstaBloqueado(Connection conn, int bloqueadorId, int bloqueadoId) throws SQLException {
        String checkSql = "SELECT 1 FROM bloqueados WHERE bloqueador_id = ? AND bloqueado_id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, bloqueadorId);
            checkStmt.setInt(2, bloqueadoId);
            return checkStmt.executeQuery().next();
        }
    }

    private void insertarBloqueo(Connection conn, int bloqueadorId, int bloqueadoId) throws SQLException {
        String insertSql = "INSERT INTO bloqueados(bloqueador_id, bloqueado_id) VALUES(?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setInt(1, bloqueadorId);
            insertStmt.setInt(2, bloqueadoId);
            insertStmt.executeUpdate();
        }
    }

    public String desbloquearUsuario(int idBloqueador, String usernameDesbloqueado) {
        if (!existeUsuario(usernameDesbloqueado)) return "El usuario '" + usernameDesbloqueado + "' no existe.";
        int idBloqueado = obtenerIdUsuario(usernameDesbloqueado);
        String sql = "DELETE FROM bloqueados WHERE bloqueador_id = ? AND bloqueado_id = ?";

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idBloqueador);
            pstmt.setInt(2, idBloqueado);
            int filasAfectadas = pstmt.executeUpdate();

            if (filasAfectadas > 0) return "Has perdonado a '" + usernameDesbloqueado + "'.";
            else return "No estabas enojado con '" + usernameDesbloqueado + "'.";

        } catch (SQLException e) {
            e.printStackTrace();
            return "[Error] No se pudo procesar el desbloqueo.";
        }
    }

    public void difundirMensajeGrupo(UnCliente remitente, int grupoId, String mensajeFormateado) {
        List<Integer> idsMiembros = obtenerIdsMiembrosGrupo(grupoId);

        synchronized (clientesConectados) {
            for (UnCliente cliente : clientesConectados) {
                enviarMensajeACliente(cliente, remitente, grupoId, mensajeFormateado, idsMiembros);
            }
        }
    }

    private List<Integer> obtenerIdsMiembrosGrupo(int grupoId) {
        String sql = "SELECT usuario_id FROM grupo_miembros WHERE grupo_id = ?";
        List<Integer> idsMiembros = new ArrayList<>();
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) idsMiembros.add(rs.getInt("usuario_id"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return idsMiembros;
    }

    private void enviarMensajeACliente(UnCliente cliente, UnCliente remitente, int grupoId, String mensaje, List<Integer> idsMiembros) {
        if (cliente == remitente) return;

        boolean isMiembro = idsMiembros.contains(cliente.getIdUsuario());

        if (!cliente.isLogueado()) {
            if (grupoId == Configuracion.ID_GRUPO_TODOS) cliente.out.println(mensaje);
        } else if (isMiembro && cliente.getGrupoActualId() == grupoId) {
            if (!remitente.isLogueado() || !estanBloqueados(remitente.getIdUsuario(), cliente.getIdUsuario())) {
                cliente.out.println(mensaje);
            }
        }
    }

    public void difundirMensajeInvitado(String mensaje, UnCliente remitente) {
        String msgFormateado = remitente.getUsername() + ": " + mensaje;
        difundirMensajeGrupo(remitente, Configuracion.ID_GRUPO_TODOS, msgFormateado);
    }

    public void enviarMensajePrivado(String mensaje, UnCliente remitente, String usernameDestinatario) {
        if (!existeUsuario(usernameDestinatario)) {
            remitente.out.println("Shavalon el usuario '" + usernameDestinatario + "' no existe.");
            return;
        }
        int idDestinatario = obtenerIdUsuario(usernameDestinatario);
        if (estanBloqueados(remitente.getIdUsuario(), idDestinatario)) {
            remitente.out.println("Shavalon no puedes enviar mensajes a '" + usernameDestinatario + "' (Estan enojados).");
            return;
        }
        UnCliente clienteDestinatario = obtenerClientePorUsername(usernameDestinatario);
        if (clienteDestinatario != null) {
            clienteDestinatario.out.println("[Privado de " + remitente.getUsername() + "]: " + mensaje);
        } else {
            remitente.out.println("Shavalon el usuario '" + usernameDestinatario + "' no está conectado.");
        }
    }

    public void removerCliente(UnCliente cliente) {
        boolean removido = clientesConectados.remove(cliente);
        if (cliente.isLogueado()) {
            forzarFinDeJuego(cliente);
        }
        if (!removido) {
            System.err.println("Advertencia: Se intentó remover un cliente que no estaba en la lista.");
        }
        System.out.println("Cliente " + cliente.getUsername() + " desconectado. Clientes restantes: " + clientesConectados.size());
        if (cliente.isLogueado()) {
            notificarDesconexion(cliente);
        }
    }

    private void notificarDesconexion(UnCliente cliente) {
        String msgFormateado = "Servidor: " + cliente.getUsername() + " ha abandonado el chat.";
        String msgParaOtros = "[Servidor] " + cliente.getUsername() + " ha abandonado el chat.";
        registrarMensajeEnArchivo(Configuracion.ID_GRUPO_TODOS, msgFormateado);
        difundirMensajeGrupo(cliente, Configuracion.ID_GRUPO_TODOS, msgParaOtros);
    }

    public String obtenerListaUsuarios(String usernameExcluir) {
        List<String> usuarios = new ArrayList<>();
        String sql = "SELECT username FROM usuarios WHERE username != ?";

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usernameExcluir);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) usuarios.add(rs.getString("username"));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (usuarios.isEmpty()) return "[Info] No hay otros usuarios registrados.";
        return "[Usuarios] " + String.join(", ", usuarios);
    }

    public synchronized boolean estaUsuarioConectado(String username) {
        for (UnCliente cliente : clientesConectados) {
            if (cliente.isLogueado() && cliente.getUsername().equals(username)) return true;
        }
        return false;
    }

    public synchronized void registrarMensajeEnArchivo(int grupoId, String mensajeFormateado) {
        String nombreArchivo = Configuracion.CHAT_LOGS_DIR + "/" + grupoId + ".txt";

        try (FileWriter fw = new FileWriter(nombreArchivo, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw, true)) {

            out.println(mensajeFormateado);

        } catch (IOException e) {
            System.err.println("Error al escribir en el log del grupo " + grupoId + ": " + e.getMessage());
        }
    }
    private int getGrupoIdPorNombre(String nombreGrupo) {
        String sql = "SELECT grupo_id FROM grupos WHERE nombre_grupo = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt("grupo_id") : -1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    private boolean esUsuarioMiembro(int usuarioId, int grupoId) {
        String sql = "SELECT 1 FROM grupo_miembros WHERE usuario_id = ? AND grupo_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.setInt(2, grupoId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public String crearGrupo(UnCliente creador, String nombreGrupo) {
        if (!validarNombreGrupo(nombreGrupo)) return "Shavalon, nombre de grupo no válido.";
        if (getGrupoIdPorNombre(nombreGrupo) != -1) return "Shavalon, el grupo '" + nombreGrupo + "' ya existe.";

        Connection conn = null;
        try {
            conn = conexionBD();
            conn.setAutoCommit(false);
            long grupoId = ejecutarCreacionGrupoTransaccion(conn, creador, nombreGrupo);
            if (grupoId == -1) { conn.rollback(); return "Error al crear el grupo."; }
            conn.commit();
            return "¡Grupo '" + nombreGrupo + "' creado! Usa /grupo " + nombreGrupo + " para unirte.";
        } catch (SQLException | IOException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return "Error en la base de datos o archivos al crear el grupo.";
        } finally {
            cerrarConexion(conn);
        }
    }
    private boolean validarNombreGrupo(String nombreGrupo) {
        return !nombreGrupo.equalsIgnoreCase(Configuracion.NOMBRE_GRUPO_TODOS) &&
                !nombreGrupo.contains("|") &&
                !nombreGrupo.isEmpty();
    }
    private long ejecutarCreacionGrupoTransaccion(Connection conn, UnCliente creador, String nombreGrupo) throws SQLException, IOException {
        long grupoId = insertarGrupo(conn, nombreGrupo, creador.getIdUsuario());
        if (grupoId == -1) return -1;
        crearArchivoLog(grupoId);
        insertarMiembro(conn, grupoId, creador.getIdUsuario());
        return grupoId;
    }
    private long insertarGrupo(Connection conn, String nombreGrupo, int creadorId) throws SQLException {
        String sqlInsertGrupo = "INSERT INTO grupos (nombre_grupo, creador_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsertGrupo, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nombreGrupo);
            pstmt.setInt(2, creadorId);
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : -1;
        }
    }
    private void crearArchivoLog(long grupoId) throws IOException {
        File logGrupo = new File(Configuracion.CHAT_LOGS_DIR + "/" + grupoId + ".txt");
        logGrupo.createNewFile();
    }
    private void insertarMiembro(Connection conn, long grupoId, int usuarioId) throws SQLException {
        String sqlInsertMiembro = "INSERT INTO grupo_miembros (grupo_id, usuario_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsertMiembro)) {
            pstmt.setLong(1, grupoId);
            pstmt.setInt(2, usuarioId);
            pstmt.executeUpdate();
        }
    }
    public String unirseGrupo(UnCliente cliente, String nombreGrupo) {
        int grupoId = getGrupoIdPorNombre(nombreGrupo);
        if (grupoId == -1) return "El grupo '" + nombreGrupo + "' no existe.";
        if (esUsuarioMiembro(cliente.getIdUsuario(), grupoId)) return "Shavalon, ya estás en el grupo '" + nombreGrupo + "'.";

        String sqlInsertMiembro = "INSERT INTO grupo_miembros (grupo_id, usuario_id) VALUES (?, ?)";
        try (Connection conn = conexionBD();
             PreparedStatement pstmtMiembro = conn.prepareStatement(sqlInsertMiembro)) {
            pstmtMiembro.setInt(1, grupoId);
            pstmtMiembro.setInt(2, cliente.getIdUsuario());
            pstmtMiembro.executeUpdate();
            return "Te has unido al grupo '" + nombreGrupo + "'. Usa /grupo " + nombreGrupo + " para hablar.";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al unirse al grupo.";
        }
    }
    public String borrarGrupo(UnCliente cliente, String nombreGrupo) {
        int grupoId = getGrupoIdPorNombre(nombreGrupo);
        if (grupoId == -1) return "El grupo '" + nombreGrupo + "' no existe.";

        try (Connection conn = conexionBD()) {
            return procesarBorradoGrupo(conn, cliente.getIdUsuario(), grupoId, nombreGrupo);
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al intentar borrar el grupo.";
        }
    }
    private String procesarBorradoGrupo(Connection conn, int usuarioId, int grupoId, String nombreGrupo) throws SQLException {
        String sql = "SELECT creador_id, es_borrable FROM grupos WHERE grupo_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                if (rs.getInt("es_borrable") == Configuracion.ES_BORRABLE_NO) {
                    return "Shavalon, no puedes borrar el grupo '" + nombreGrupo + "'.";
                }
                if (rs.getInt("creador_id") != usuarioId) {
                    return "No eres el creador de este grupo.";
                }
                borrarArchivoLog(grupoId);
                ejecutarBorradoGrupoBD(conn, grupoId);
                return "Grupo '" + nombreGrupo + "' y su historial borrados.";
            }
        }
        return "Error al intentar borrar el grupo.";
    }
    private void borrarArchivoLog(int grupoId) {
        File logGrupo = new File(Configuracion.CHAT_LOGS_DIR + "/" + grupoId + ".txt");
        if (!logGrupo.delete()) {
            System.err.println("Advertencia: No se pudo borrar el archivo " + logGrupo.getName());
        }
    }
    private void ejecutarBorradoGrupoBD(Connection conn, int grupoId) throws SQLException {
        String sqlDelete = "DELETE FROM grupos WHERE grupo_id = ?";
        try (PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {
            pstmtDelete.setInt(1, grupoId);
            pstmtDelete.executeUpdate();
        }
    }
    public String listarGrupos(UnCliente cliente) {
        String sql = "SELECT g.nombre_grupo, gm.usuario_id FROM grupos g " +
                "LEFT JOIN grupo_miembros gm ON g.grupo_id = gm.grupo_id AND gm.usuario_id = ?";
        List<String> todosLosGrupos = new ArrayList<>();
        int usuarioId = cliente.getIdUsuario();

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String nombre = rs.getString("nombre_grupo");
                if (rs.getInt("usuario_id") > 0) todosLosGrupos.add(nombre + " (*)");
                else todosLosGrupos.add(nombre);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al listar grupos.";
        }
        return formatarListaGrupos(todosLosGrupos);
    }
    private String formatarListaGrupos(List<String> grupos) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Grupos Disponibles] (* = Eres miembro)\n");
        sb.append(String.join(", ", grupos));
        return sb.toString();
    }
    public String obtenerCabeceraGrupo(int grupoId, String nombreGrupo) {
        String sql = "SELECT u.username FROM grupo_miembros gm JOIN usuarios u ON gm.usuario_id = u.id WHERE gm.grupo_id = ?";
        List<String> miembros = new ArrayList<>();
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) miembros.add(rs.getString("username"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return formatarCabeceraGrupo(nombreGrupo, miembros);
    }
    private String formatarCabeceraGrupo(String nombreGrupo, List<String> miembros) {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------Grupo " + nombreGrupo + "---------------\n");
        sb.append("--Miembros: " + String.join(", ", miembros) + "\n");
        sb.append("----------------------------------------------");
        return sb.toString();
    }
    public void cambiarGrupo(UnCliente cliente, String nombreGrupo) {
        int grupoId = getGrupoIdPorNombre(nombreGrupo);
        if (grupoId == -1) { cliente.out.println("El grupo '" + nombreGrupo + "' no existe."); return; }
        if (!esUsuarioMiembro(cliente.getIdUsuario(), grupoId)) {
            cliente.out.println("No eres miembro de '" + nombreGrupo + "'. Usa /unirse-grupo " + nombreGrupo);
            return;
        }
        cliente.setGrupoActual(grupoId, nombreGrupo);
        cliente.out.println("\n" + obtenerCabeceraGrupo(grupoId, nombreGrupo));
        enviarHistorialCompletoDelGrupo(cliente, grupoId);
    }
    public void enviarHistorialCompletoDelGrupo(UnCliente cliente, int grupoId) {
        String nombreArchivo = Configuracion.CHAT_LOGS_DIR + "/" + grupoId + ".txt";
        boolean hayMensajes = leerYEnviarHistorial(cliente, nombreArchivo);
        if(hayMensajes) cliente.out.println("----------------------------------------------");
        else cliente.out.println("(No hay mensajes en este grupo)");
    }
    private boolean leerYEnviarHistorial(UnCliente cliente, String nombreArchivo) {
        boolean hayMensajes = false;
        try (BufferedReader br = new BufferedReader(new FileReader(nombreArchivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                hayMensajes = true;
                cliente.out.println(linea);
            }
        } catch (IOException e) {
            if (!(e instanceof java.io.FileNotFoundException)) e.printStackTrace();
        }
        return hayMensajes;
    }
}