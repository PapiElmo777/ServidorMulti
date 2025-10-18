package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ServidorMulti {
    private static final Map<String, UnCliente> clientesConectados = new ConcurrentHashMap<>();
    private static final String URL_SQLITE = "jdbc:sqlite:usuarios.db";

    public static void main(String[] args) throws IOException {
        inicializarBaseDeDatos();
        ServerSocket servidorSocket = new ServerSocket(8080);
        System.out.println("Servidor iniciado en el puerto 8080 y conectado a SQLite.");
        int contador = 0;

        while (true) {
            Socket s = servidorSocket.accept();
            System.out.println("Se conectó un nuevo cliente: #" + contador);
            UnCliente unCliente = new UnCliente(s, Integer.toString(contador));
            agregarCliente(unCliente);

            Thread hilo = new Thread(unCliente);
            hilo.start();
            contador++;
        }
    }

    private static Connection conexionBD() throws SQLException {
        return DriverManager.getConnection(URL_SQLITE);
    }
    private static void inicializarBaseDeDatos() {
        String sqlCreateTable = "CREATE TABLE IF NOT EXISTS usuarios (" +
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

                stmt.execute(sqlCreateTable);
                stmt.execute(sqlCreateTableBloqueados);
                System.out.println("Base de datos SQLite y tabla 'usuarios' listas.");

            } catch (SQLException e) {
                System.err.println("No se pudo inicializar la base de datos shavalon: " + e.getMessage());
                System.exit(1);
            }

        } catch (ClassNotFoundException e) {
            System.err.println("Error no se encontró la clase del driver de SQLite.");
            e.printStackTrace();
            System.exit(1);
        }
    }
    public static boolean autenticarUsuario(String usuario, String password) {
        String sql = "SELECT password FROM usuarios WHERE username = ?";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                return storedPassword.equals(password);
            }
        } catch (SQLException e) {
            System.err.println("Error al autenticar usuario: " + e.getMessage());
        }
        return false;
    }

    public static boolean registrarUsuario(String usuario, String password) {
        String sql = "INSERT INTO usuarios(username, password) VALUES(?, ?)";
        try (Connection conn = conexionBD();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (usuarioYaExiste(conn, usuario)) {
                System.out.println("Intento de registrar un usuario que ya existe: " + usuario);
                return false;
            }
            pstmt.setString(1, usuario);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("Usuario registrado exitosamente en la BD: " + usuario);
            return true;

        } catch (SQLException e) {
            System.err.println("Error al registrar nuevo usuario: " + e.getMessage());
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
                    return "[Info] Ya tenías bloqueado a '" + usernameBloqueado + "'.";
                }
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setInt(1, idBloqueador);
                insertStmt.setInt(2, idBloqueado);
                insertStmt.executeUpdate();
                return "[Éxito] Has bloqueado a '" + usernameBloqueado + "'.";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "[Error] No se pudo procesar el bloqueo.";
        }
    }
    private static boolean usuarioYaExiste(Connection conn, String usuario) throws SQLException {
        String sql = "SELECT id FROM usuarios WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }
    public static void agregarCliente(UnCliente cliente) {
        clientesConectados.put(cliente.getClienteId(), cliente);
    }

    public static void removerCliente(UnCliente cliente) {
        clientesConectados.remove(cliente.getClienteId());
        enviarMensajePublico(cliente, ">> El usuario '" + cliente.getNombreRemitente() + "' se ha desconectado. <<", true);
    }

    public static void enviarMensajePublico(UnCliente remitente, String mensaje, boolean esNotificacion) {
        String mensajeCompleto = esNotificacion ? mensaje : remitente.getNombreRemitente() + ": " + mensaje;
        for (UnCliente cliente : clientesConectados.values()) {
            if (!cliente.equals(remitente)) {
                try {
                    cliente.enviarMensaje(mensajeCompleto);
                } catch (IOException e) {
                    System.err.println("Error al enviar mensaje a " + cliente.getClienteId());
                }
            }
        }
    }
    public static void enviarMensajePrivado(UnCliente remitente, String destinatarios, String mensaje) throws IOException {
        String mensajeCompleto = "(Privado) " + remitente.getNombreRemitente() + ": " + mensaje;
        String[] nombresDestinatarios = destinatarios.split(",");

        for (String destNombre : nombresDestinatarios) {
            boolean encontrado = false;
            for (UnCliente clienteDestino : clientesConectados.values()) {
                if (destNombre.trim().equals(clienteDestino.getNombreUsuario()) || destNombre.trim().equals(clienteDestino.getClienteId())) {
                    clienteDestino.enviarMensaje(mensajeCompleto);
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado) {
                remitente.enviarMensaje("El usuario '" + destNombre.trim() + "' no fue encontrado o no está conectado.");
            }
        }
    }
    public static boolean cuentaYaEnUso(String usuario) {
        for (UnCliente cliente : clientesConectados.values()) {
            if (java.util.Objects.equals(usuario, cliente.getNombreUsuario())) {
                return true;
            }
        }
        return false;
    }
}