package servidormulti;

import java.io.IOException;
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
    private static final String URL_BD = "jdbc:sqlite:usuarios.db";
    private final Map<String, String> propuestasPendientes = Collections.synchronizedMap(new HashMap<>());
    private final List<JuegoGatito> juegosActivos = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        ServidorMulti servidor = new ServidorMulti();
        servidor.iniciarServidor();
    }
    public void iniciarServidor() {
        inicializarBaseDeDatos();

        System.out.println("Servidor iniciado en el puerto 8080 y conectado a SQLite.");

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            while (true) {
                Socket socket = serverSocket.accept();
                UnCliente nuevoCliente = new UnCliente(socket, this);
                clientesConectados.add(nuevoCliente);
                new Thread(nuevoCliente).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
    private static void inicializarBaseDeDatos() {
        String sqlCreateTableUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    username VARCHAR(15) NOT NULL UNIQUE," +
                "    password VARCHAR(15) NOT NULL" +
                ");";
        String sqlCreateTableBloqueados = "CREATE TABLE IF NOT EXISTS bloqueados (" +
                "    bloqueador_id INTEGER NOT NULL," +
                "    bloqueado_id INTEGER NOT NULL," +
                "    PRIMARY KEY (bloqueador_id, bloqueado_id)," +
                "    FOREIGN KEY (bloqueador_id) REFERENCES usuarios(id)," +
                "    FOREIGN KEY (bloqueado_id) REFERENCES usuarios(id)" +
                ");";
        String sqlCreateTableRanking = "CREATE TABLE IF NOT EXISTS ranking (" +
                "    usuario_id INTEGER PRIMARY KEY," +
                "    victorias INTEGER NOT NULL DEFAULT 0," +
                "    derrotas INTEGER NOT NULL DEFAULT 0," +
                "    empates INTEGER NOT NULL DEFAULT 0," +
                "    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE" +
                ");";

        try {
            Class.forName("org.sqlite.JDBC");

            try (Connection conn = conexionBD();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sqlCreateTableUsuarios);
                stmt.execute(sqlCreateTableBloqueados);
                stmt.execute(sqlCreateTableRanking);
                System.out.println("Base de datos SQLite y tablas 'usuarios' y 'bloqueados' listas.");

            } catch (SQLException e) {
                System.err.println("No se pudo inicializar la base de datos: " + e.getMessage());
                System.exit(1);
            }

        } catch (ClassNotFoundException e) {
            System.err.println("Error CRÍTICO: No se encontró la clase del driver de SQLite.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Connection conexionBD() throws SQLException {
        return DriverManager.getConnection(URL_BD);
    }

    public boolean autenticarUsuario(String username, String password) {
        String sql = "SELECT password FROM usuarios WHERE username = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error al autenticar: " + e.getMessage());
            return false;
        }
    }

    public boolean registrarUsuario(String username, String password) {
        String sqlInsertUsuario = "INSERT INTO usuarios(username, password) VALUES(?, ?)";
        String sqlInsertRanking = "INSERT INTO ranking(usuario_id, victorias, derrotas, empates) VALUES (?, 0, 0, 0)";

        Connection conn = null;
        try {
            conn = conexionBD();
            conn.setAutoCommit(false);
            long idUsuario = -1;
            try (PreparedStatement pstmtUsuario = conn.prepareStatement(sqlInsertUsuario, Statement.RETURN_GENERATED_KEYS)) {
                pstmtUsuario.setString(1, username);
                pstmtUsuario.setString(2, password);
                pstmtUsuario.executeUpdate();
                ResultSet rs = pstmtUsuario.getGeneratedKeys();
                if (rs.next()) {
                    idUsuario = rs.getLong(1);
                }
            }
            if (idUsuario == -1) {
                conn.rollback();
                return false;
            }
            try (PreparedStatement pstmtRanking = conn.prepareStatement(sqlInsertRanking)) {
                pstmtRanking.setLong(1, idUsuario);
                pstmtRanking.executeUpdate();
            }
            conn.commit();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            if (e.getErrorCode() == 19) {
                return false;
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public int obtenerIdUsuario(String username) {
        String sql = "SELECT id FROM usuarios WHERE username = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            return -1;
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

            ResultSet rs = pstmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String bloquearUsuario(int idBloqueador, String usernameBloqueado) {
        if (!existeUsuario(usernameBloqueado)) {
            return "Shavalon el usuario '" + usernameBloqueado + "' no existe.";
        }

        int idBloqueado = obtenerIdUsuario(usernameBloqueado);
        if (idBloqueador == idBloqueado) {
            return "Shavalon no puedes bloquearte a ti mismo.";
        }
        String checkSql = "SELECT 1 FROM bloqueados WHERE bloqueador_id = ? AND bloqueado_id = ?";
        String insertSql = "INSERT INTO bloqueados(bloqueador_id, bloqueado_id) VALUES(?, ?)";

        try (Connection conn = conexionBD()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, idBloqueador);
                checkStmt.setInt(2, idBloqueado);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return "Shavalon ya tenías bloqueado a '" + usernameBloqueado + "'.";
                }
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setInt(1, idBloqueador);
                insertStmt.setInt(2, idBloqueado);
                insertStmt.executeUpdate();
                return "Has bloqueado de tu vida a '" + usernameBloqueado + "'.";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "Shavalon no se pudo procesar el bloqueo.";
        }
    }

    public String desbloquearUsuario(int idBloqueador, String usernameDesbloqueado) {
        if (!existeUsuario(usernameDesbloqueado)) {
            return "El usuario '" + usernameDesbloqueado + "' no existe.";
        }

        int idBloqueado = obtenerIdUsuario(usernameDesbloqueado);
        String sql = "DELETE FROM bloqueados WHERE bloqueador_id = ? AND bloqueado_id = ?";

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idBloqueador);
            pstmt.setInt(2, idBloqueado);

            int filasAfectadas = pstmt.executeUpdate();

            if (filasAfectadas > 0) {
                return "Has perdonado a '" + usernameDesbloqueado + "'.";
            } else {
                return "No estabas enojado con '" + usernameDesbloqueado + "'.";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "[Error] No se pudo procesar el desbloqueo.";
        }
    }

    public void difundirMensaje(String mensaje, UnCliente remitente) {
        synchronized (clientesConectados) {
            for (UnCliente cliente : clientesConectados) {
                if (cliente != remitente && !estanBloqueados(remitente.getIdUsuario(), cliente.getIdUsuario())) {
                    cliente.out.println(remitente.getUsername() + ": " + mensaje);
                }
            }
        }
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

        UnCliente clienteDestinatario = null;
        synchronized (clientesConectados) {
            for (UnCliente cliente : clientesConectados) {
                if (cliente.getUsername() != null && cliente.getUsername().equals(usernameDestinatario)) {
                    clienteDestinatario = cliente;
                    break;
                }
            }
        }

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
            difundirMensaje("[Servidor] " + cliente.getUsername() + " ha abandonado el chat.", cliente);
        }
    }

    public String obtenerListaUsuarios(String usernameExcluir) {
        List<String> usuarios = new ArrayList<>();
        String sql = "SELECT username FROM usuarios WHERE username != ?";

        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usernameExcluir);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                usuarios.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (usuarios.isEmpty()) {
            return "[Info] No hay otros usuarios registrados.";
        }
        return "[Usuarios] " + String.join(", ", usuarios);
    }
    public synchronized boolean estaUsuarioConectado(String username) {
        for (UnCliente cliente : clientesConectados) {
            if (cliente.isLogueado() && cliente.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
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
        String sql = "UPDATE ranking SET victorias = victorias + 1 WHERE usuario_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar victoria: " + e.getMessage());
        }
    }
    public synchronized void registrarDerrota(int usuarioId) {
        String sql = "UPDATE ranking SET derrotas = derrotas + 1 WHERE usuario_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar derrota: " + e.getMessage());
        }
    }
    public synchronized void registrarEmpate(int usuarioId) {
        String sql = "UPDATE ranking SET empates = empates + 1 WHERE usuario_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar empate: " + e.getMessage());
        }
    }
    public synchronized int[] getEstadisticas(int usuarioId) {
        String sql = "SELECT victorias, derrotas, empates FROM ranking WHERE usuario_id = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, usuarioId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new int[]{rs.getInt("victorias"), rs.getInt("derrotas"), rs.getInt("empates")};
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener estadísticas: " + e.getMessage());
        }
        return new int[]{0, 0, 0};
    }
}