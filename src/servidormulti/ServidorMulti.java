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
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
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
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
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

public class ServidrMulti {

    public String obtenerCabeceraGrupo(int grupoId, String nombreGrupo) {
        String sql = "SELECT u.username FROM grupo_miembros gm JOIN usuarios u ON gm.usuario_id = u.id WHERE gm.grupo_id = ?";
        List<String> miembros = new ArrayList<>();
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                miembros.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--------------Grupo " + nombreGrupo + "---------------\n");
        sb.append("--Miembros: " + String.join(", ", miembros) + "\n");
        sb.append("----------------------------------------------");
        return sb.toString();
    }
    public void cambiarGrupo(UnCliente cliente, String nombreGrupo) {
        int grupoId = getGrupoIdPorNombre(nombreGrupo);
        if (grupoId == -1) {
            cliente.out.println("El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        if (!esUsuarioMiembro(cliente.getIdUsuario(), grupoId)) {
            cliente.out.println("No eres miembro de '" + nombreGrupo + "'. Usa /unirse-grupo " + nombreGrupo);
            return;
        }
        cliente.setGrupoActual(grupoId, nombreGrupo);
        cliente.out.println("\n" + obtenerCabeceraGrupo(grupoId, nombreGrupo));
        enviarHistorialCompletoDelGrupo(cliente, grupoId);
    }

    public void enviarHistorialCompletoDelGrupo(UnCliente cliente, int grupoId) {
        String nombreArchivo = CHAT_LOGS_DIR + "/" + grupoId + ".txt";
        boolean hayMensajes = false;

        try (BufferedReader br = new BufferedReader(new FileReader(nombreArchivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                hayMensajes = true;
                cliente.out.println(linea);
            }
        } catch (IOException e) {
            if (!(e instanceof java.io.FileNotFoundException)) {
                e.printStackTrace();
                cliente.out.println("Error al cargar historial del grupo");
            }
        }
        if(hayMensajes) {
            cliente.out.println("----------------------------------------------");
        } else {
            cliente.out.println("(No hay mensajes en este grupo)");
        }
    }
    //juego gato
    private UnCliente obtenerClientePorUsername(String username) {
        synchronized (clientesConectados) {
            for (UnCliente cliente : clientesConectados) {
                if (cliente.isLogueado() && cliente.getUsername().equals(username)) {
                    return cliente;
                }
            }
            return null;
        }
    }
    private JuegoGatito encontrarJuegoActivo(String username1, String username2) {
        synchronized (juegosActivos) {
            for (JuegoGatito juego : juegosActivos) {
                if (juego.involucraA(username1, username2) && !juego.haTerminado()) {
                    return juego;
                }
            }
            return null;
        }
    }
    public void proponerJuego(UnCliente proponente, String oponenteUsername) {
        if (proponente.getUsername().equals(oponenteUsername)) {
            proponente.out.println("No puedes jugar Gatito contigo mismo, socializa!!!");
            return;
        }
        if (!existeUsuario(oponenteUsername)) {
            proponente.out.println("El shavalon '" + oponenteUsername + "' no está registrado.");
            return;
        }
        UnCliente oponente = obtenerClientePorUsername(oponenteUsername);
        if (oponente == null) {
            proponente.out.println("El shavalon '" + oponenteUsername + "' no está conectado en este momento.");
            return;
        }
        if (proponente.getGrupoActualId() != oponente.getGrupoActualId()) {
            proponente.out.println("El shavalon '" + oponenteUsername + "' no está en tu grupo actual.");
            return;
        }
        if (estanBloqueados(proponente.getIdUsuario(), oponente.getIdUsuario())) {
            proponente.out.println("Shavalon, no puedes juagar con '" + oponenteUsername + "' porque estan enojados.");
            return;
        }
        if (encontrarJuegoActivo(proponente.getUsername(), oponenteUsername) != null) {
            proponente.out.println("Ya tienes un juego activo con '" + oponenteUsername + "'.");
            return;
        }
        if (propuestasPendientes.containsKey(proponente.getUsername())) {
            proponente.out.println("Ya tienes una propuesta pendiente enviada a '" + propuestasPendientes.get(proponente.getUsername()) + "'.");
            return;
        }
        for (Map.Entry<String, String> entry : propuestasPendientes.entrySet()) {
            if (entry.getValue().equals(proponente.getUsername())) {
                proponente.out.println("El shavalon '" + entry.getKey() + "' ya te propuso un juego. Acepta o rechaza primero.");
                return;
            }
        }
        if (propuestasPendientes.containsKey(oponenteUsername)) {
            proponente.out.println(oponenteUsername + " ya tiene una propuesta pendiente enviada a otro usuario.");
            return;
        }

        propuestasPendientes.put(proponente.getUsername(), oponenteUsername);

        proponente.out.println("Propuesta de juego enviada a '" + oponenteUsername + "'.");
        oponente.out.println(proponente.getUsername() + " te ha retado a una partida de Gatito.");
        oponente.out.println("Usa /aceptar " + proponente.getUsername() + " para empezar o /rechazar " + proponente.getUsername() + ".");
    }
    public void aceptarJuego(UnCliente aceptante, String proponenteUsername) {
        if (!propuestasPendientes.containsKey(proponenteUsername) ||
                !propuestasPendientes.get(proponenteUsername).equals(aceptante.getUsername())) {
            aceptante.out.println("No tienes una propuesta pendiente de '" + proponenteUsername + "'.");
            return;
        }

        UnCliente proponente = obtenerClientePorUsername(proponenteUsername);
        if (proponente == null) {
            aceptante.out.println("El proponente se ha huido.");
            propuestasPendientes.remove(proponenteUsername);
            return;
        }
        if (estanBloqueados(aceptante.getIdUsuario(), proponente.getIdUsuario())) {
            aceptante.out.println("Shavalon, no puedes juagar con '" + proponenteUsername + "' porque estan enojados.");
            propuestasPendientes.remove(proponenteUsername);
            return;
        }
        propuestasPendientes.remove(proponenteUsername);
        JuegoGatito nuevoJuego = new JuegoGatito(proponente, aceptante);
        juegosActivos.add(nuevoJuego);
    }
    public void rechazarJuego(UnCliente rechazante, String proponenteUsername) {
        if (!propuestasPendientes.containsKey(proponenteUsername) ||
                !propuestasPendientes.get(proponenteUsername).equals(rechazante.getUsername())) {
            rechazante.out.println("Shavalon, no tienes propuestas pendientes de '" + proponenteUsername + "'.");
            return;
        }

        UnCliente proponente = obtenerClientePorUsername(proponenteUsername);
        propuestasPendientes.remove(proponenteUsername);

        rechazante.out.println("No quisiste jugar Gatito con '" + proponenteUsername + "'.");

        if (proponente != null) {
            proponente.out.println("Shavalon " + rechazante.getUsername() + " ha rechazado tu partidita, ff.");
        }
    }

    private void removerJuego(JuegoGatito juego) {
        synchronized (juegosActivos) {
            juegosActivos.remove(juego);
        }
    }
    private void forzarFinDeJuego(UnCliente perdedor) {
        synchronized (juegosActivos) {
            List<JuegoGatito> juegosARemover = new ArrayList<>();
            for (JuegoGatito juego : juegosActivos) {
                if (juego.esJugador(perdedor)) {
                    juego.forzarTerminacion(perdedor);
                    UnCliente ganador = juego.getGanador();
                    if (ganador != null) {
                        registrarVictoria(ganador.getIdUsuario());
                    }
                    registrarDerrota(perdedor.getIdUsuario());
                    juegosARemover.add(juego);
                }
            }
            juegosActivos.removeAll(juegosARemover);
        }
        String disconnectedUser = perdedor.getUsername();
        propuestasPendientes.remove(disconnectedUser);
        propuestasPendientes.values().removeIf(opponent -> opponent.equals(disconnectedUser));
    }
    public synchronized List<JuegoGatito> getJuegosActivos(UnCliente cliente) {
        List<JuegoGatito> lista = new ArrayList<>();
        synchronized (juegosActivos) {
            for (JuegoGatito juego : juegosActivos) {
                if (!juego.haTerminado() && juego.esJugador(cliente)) {
                    lista.add(juego);
                }
            }
        }
        return lista;
    }
    public synchronized int contarJuegosActivos(UnCliente cliente) {
        return getJuegosActivos(cliente).size();
    }
    public synchronized void manejarMovimientoGatito(UnCliente cliente, String argumentos) {
        List<JuegoGatito> juegosDelCliente = getJuegosActivos(cliente);
        int cantidadJuegos = juegosDelCliente.size();

        if (cantidadJuegos == 0) {
            cliente.out.println("No estás participando en ningún juego.");
            return;
        }

        JuegoGatito juegoParaMover = null;
        String posStr = "";

        if (cantidadJuegos == 1) {
            juegoParaMover = juegosDelCliente.get(0);
            posStr = argumentos.trim();
        } else {
            String[] partes = argumentos.split(" ", 2);
            if (partes.length < 2) {
                cliente.out.println("Tienes " + cantidadJuegos + " partidas activas.");
                cliente.out.println("Usa /mover <oponente> <1-9> para especificar en cuál mover.");
                return;
            }
            String oponenteNombre = partes[0];
            posStr = partes[1].trim();
            for (JuegoGatito juego : juegosDelCliente) {
                UnCliente oponente = juego.getOponente(cliente);
                if (oponente.getUsername().equalsIgnoreCase(oponenteNombre)) {
                    juegoParaMover = juego;
                    break;
                }
            }
            if (juegoParaMover == null) {
                cliente.out.println("No se encontró una partida activa con '" + oponenteNombre + "'.");
                return;
            }
        }
        try {
            int posicion = Integer.parseInt(posStr);
            if (juegoParaMover.realizarMovimiento(cliente, posicion)) {
                if (juegoParaMover.esEmpate()) {
                    registrarEmpate(juegoParaMover.getJugadorX().getIdUsuario());
                    registrarEmpate(juegoParaMover.getJugadorO().getIdUsuario());
                } else {
                    UnCliente ganador = juegoParaMover.getGanador();
                    UnCliente perdedor = juegoParaMover.getOponente(ganador);

                    if (ganador != null) registrarVictoria(ganador.getIdUsuario());
                    if (perdedor != null) registrarDerrota(perdedor.getIdUsuario());
                }
                removerJuego(juegoParaMover);
            }

        } catch (NumberFormatException e) {
            cliente.out.println("Error: La posición '" + posStr + "' no es un número válido (1-9).");
        }
    }

    //ranking
    public synchronized void registrarVictoria(int usuarioId) {
        String sql = "INSERT INTO ranking (usuario_id, victorias, derrotas, empates, puntaje) " +
                "VALUES (?, 1, 0, 0, 2) " +
                "ON CONFLICT(usuario_id) DO UPDATE SET victorias = victorias + 1, puntaje = puntaje + 2";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar victoria: " + e.getMessage());
        }
    }
    public synchronized void registrarDerrota(int usuarioId) {
        String sql = "INSERT INTO ranking (usuario_id, victorias, derrotas, empates, puntaje) " +
                "VALUES (?, 0, 1, 0, 0) " +
                "ON CONFLICT(usuario_id) DO UPDATE SET derrotas = derrotas + 1";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar derrota: " + e.getMessage());
        }
    }
    public synchronized void registrarEmpate(int usuarioId) {
        String sql = "INSERT INTO ranking (usuario_id, victorias, derrotas, empates, puntaje) " +
                "VALUES (?, 0, 0, 1, 1) " +
                "ON CONFLICT(usuario_id) DO UPDATE SET empates = empates + 1, puntaje = puntaje + 1";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar empate: " + e.getMessage());
        }
    }
    public synchronized int[] getEstadisticas(int usuarioId) {
        String sql = "SELECT victorias, derrotas, empates, puntaje FROM ranking WHERE usuario_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new int[]{
                        rs.getInt("victorias"),
                        rs.getInt("derrotas"),
                        rs.getInt("empates"),
                        rs.getInt("puntaje")
                };
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener estadísticas: " + e.getMessage());
        }
        return new int[]{0, 0, 0, 0};
    }
    public void mostrarRanking(UnCliente solicitante) {
        String sql = "SELECT u.username, r.victorias, r.derrotas, r.empates, r.puntaje " +
                "FROM ranking r JOIN usuarios u ON r.usuario_id = u.id " +
                "ORDER BY r.puntaje DESC, r.victorias DESC, r.derrotas ASC " +
                "LIMIT 10";

        StringBuilder ranking = new StringBuilder("\n--- RANKING DE SHAVALONES (TOP 10) ---\n");
        ranking.append(String.format("%-4s %-15s %-6s %-4s %-4s %-4s\n", "Pos", "Usuario", "Puntos", "V", "D", "E"));
        ranking.append("-----------------------------------------------\n");

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();
            int pos = 1;
            boolean hayDatos = false;
            while (rs.next()) {
                hayDatos = true;
                ranking.append(String.format("%-4d %-15s %-6d %-4d %-4d %-4d\n",
                        pos++,
                        rs.getString("username"),
                        rs.getInt("puntaje"),
                        rs.getInt("victorias"),
                        rs.getInt("derrotas"),
                        rs.getInt("empates")));
            }

            if (!hayDatos) {
                ranking.append("       Aún no hay shavalones en el ranking. ¡A jugar!\n");
            }

            ranking.append("-----------------------------------------------\n");
            solicitante.out.println(ranking.toString());

        } catch (SQLException e) {
            solicitante.out.println("Error al obtener el ranking.");
            e.printStackTrace();
        }
    }
    public void compararEstadisticas(UnCliente solicitante, String oponenteUsername) {
        int idOponente = obtenerIdUsuario(oponenteUsername);
        if (idOponente == -1) {
            solicitante.out.println("Error: El usuario '" + oponenteUsername + "' no existe.");
            return;
        }

        int[] statsSolicitante = getEstadisticas(solicitante.getIdUsuario());
        int[] statsOponente = getEstadisticas(idOponente);

        int victoriasTu = statsSolicitante[0];
        int victoriasEl = statsOponente[0];

        double totalVictorias = victoriasTu + victoriasEl;

        if (totalVictorias == 0) {
            solicitante.out.println("[Comparativa] Ambos tienen 0 victorias totales. ¡A jugar!");
            return;
        }

        double porcTu = (victoriasTu / totalVictorias) * 100.0;
        double porcEl = (victoriasEl / totalVictorias) * 100.0;

        String comparativa = String.format("[Comparativa de Victorias] %s (%.0f%%) vs. %s (%.0f%%)",
                solicitante.getUsername(), porcTu,
                oponenteUsername, porcEl);

        solicitante.out.println(comparativa);
    }
}