package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ServidorMulti {
    private final Map<String, UnCliente> clientesConectados = new ConcurrentHashMap<>();
    private final String URL_SQLITE = "jdbc:sqlite:usuarios.db";

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

    private static Connection conexionBD() throws SQLException {
        return DriverManager.getConnection(URL_SQLITE);
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

        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = conexionBD();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sqlCreateTableUsuarios);
                stmt.execute(sqlCreateTableBloqueados);
                System.out.println("Base de datos SQLite y tablas 'usuarios' y 'bloqueados' listas.");

            } catch (SQLException e) {
                System.err.println("No se pudo inicializar la base de datos: " + e.getMessage());
                System.exit(1);
            }

        } catch (ClassNotFoundException e) {
            System.err.println("No se encontró la clase del driver de SQLite.");
            e.printStackTrace();
            System.exit(1);
        }
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
        String sql = "INSERT INTO usuarios(username, password) VALUES(?, ?)";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                return false;
            }
            e.printStackTrace();
            return false;
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
            return "No se pudo procesar el desbloqueo.";
        }
    }

    public void difundirMensaje(String mensaje, UnCliente remitente) {
        synchronized (clientesConectados) {
            for (UnCliente cliente : clientesConectados) {
                if (!estanBloqueados(remitente.getIdUsuario(), cliente.getIdUsuario())) {
                    cliente.out.println(remitente.getUsername() + ": " + mensaje);
                }
            }
        }
    }

    public void removerCliente(UnCliente cliente) {
        clientesConectados.remove(cliente);
        System.out.println("Cliente " + cliente.getUsername() + " desconectado.");
        difundirMensaje("[Servidor] " + cliente.getUsername() + " ha abandonado el chat.", cliente);
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
                if (cliente.getUsername().equals(usernameDestinatario)) {
                    clienteDestinatario = cliente;
                    break;
                }
            }
        }
        if (clienteDestinatario != null) {
            clienteDestinatario.out.println("[Privado de " + remitente.getUsername() + "]: " + mensaje);
            remitente.out.println("[Privado para " + usernameDestinatario + "]: " + mensaje);
        } else {
            remitente.out.println("Shavalon el usuario '" + usernameDestinatario + "' no está conectado.");
        }


        if (clienteDestinatario != null) {
            clienteDestinatario.out.println("[Privado de " + remitente.getUsername() + "]: " + mensaje);
            remitente.out.println("[Privado para " + usernameDestinatario + "]: " + mensaje);
        } else {
            remitente.out.println("[Info] El usuario '" + usernameDestinatario + "' no está conectado.");
        }
    }
}