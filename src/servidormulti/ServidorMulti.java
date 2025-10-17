package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ServidorMulti {
    private static final Map<String, UnCliente> clientesConectados = new ConcurrentHashMap<>();


    public static void main(String[] args) throws IOException {
        ServerSocket servidorSocket = new ServerSocket(8080);
        System.out.println("Servidor iniciado en el puerto 8080 y conectado a MySQL.");
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
        String url = "jdbc:mysql://localhost:3306/usuarios?serverTimezone=UTC";
        String user = "root";
        String password = "AE231505";
        return DriverManager.getConnection(url, user, password);
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