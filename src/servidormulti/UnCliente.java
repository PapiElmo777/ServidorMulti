package servidormulti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class UnCliente implements Runnable {
    private final Socket socket;
    private final ServidorMulti servidor;
    PrintWriter out;
    private BufferedReader in;
    private String username;
    private int idUsuario;
    private String guestUsername;
    private int mensajesComoInvitado = 0;
    private static final int LIMITE_MENSAJES_INVITADO = 3;
    public UnCliente(Socket socket, ServidorMulti servidor) {
        this.socket = socket;
        this.servidor = servidor;
        this.guestUsername = "Invitado-" + socket.getPort();
    }
    public String getUsername() {
        return (this.username != null) ? this.username : this.guestUsername;
    }

    public int getIdUsuario() {
        return idUsuario;
    }
    public boolean isLogueado() {
        return this.username != null;
    }

    @Override
    public void run() {
        try {
            setupStreams();
            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                if (!isLogueado()) {
                    procesarMensajeInvitado(mensaje);
                } else {
                    if (procesarMensajeLogueado(mensaje)) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error de IO en UnCliente (" + getUsername() + "): " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println("Conectado. Puedes enviar " + LIMITE_MENSAJES_INVITADO + " mensajes como " + this.guestUsername);
        out.println("Usa /registrar <user> <pass> o /login <user> <pass> para chatear sin límites.");
    }
    private void cleanup() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar socket para " + getUsername() + ": " + e.getMessage());
        }
        servidor.removerCliente(this);
    }
    private void handleLogin(String mensaje) {
        String[] partes = mensaje.split(" ", 3);
        if (partes.length == 3) {
            String user = partes[1];
            String pass = partes[2];
            if (servidor.estaUsuarioConectado(user)) {
                out.println("Error: La cuenta '" + user + "' ya está en uso.");

            } else if (servidor.autenticarUsuario(user, pass)) {
                this.username = user;
                this.idUsuario = servidor.obtenerIdUsuario(this.username);

                out.println("BIENVENIDO SHAVALON " + this.username);
                enviarMenuAyuda();
                servidor.difundirMensaje("[Servidor] " + this.username + " se ha unido al chat.", this);
            } else {
                out.println("Usuario o contraseña incorrectos.");
            }
        } else {
            out.println("Formato incorrecto. Usa /login <user> <pass>");
        }
    }
    private void handleRegistro(String mensaje) {
        String[] partes = mensaje.split(" ", 3);
        if (partes.length == 3) {
            if (servidor.registrarUsuario(partes[1], partes[2])) {
                out.println("Bienvenido Shavalon. Ahora puedes usar /login " + partes[1] + " <pass>");
            } else {
                out.println("Error: El usuario ya existe o hubo un error.");
            }
        } else {
            out.println("Error: Formato incorrecto. Usa /registrar <user> <pass>");
        }
    }
    private void handleMensajeInvitado(String mensaje) {
        if (mensajesComoInvitado < LIMITE_MENSAJES_INVITADO) {
            mensajesComoInvitado++;
            servidor.difundirMensaje(mensaje, this);
            out.println("[Mensaje " + mensajesComoInvitado + "/" + LIMITE_MENSAJES_INVITADO + " como " + this.guestUsername + "].");
        } else {
            out.println("Límite de " + LIMITE_MENSAJES_INVITADO + " mensajes de invitado alcanzado.");
            out.println("Usa /registrar <user> <pass> o /login <user> <pass>");
        }
    }
    private void procesarMensajeInvitado(String mensaje) {
        if (mensaje.startsWith("/login ")) {
            handleLogin(mensaje);
        } else if (mensaje.startsWith("/registrar ")) {
            handleRegistro(mensaje);
        } else if (mensaje.startsWith("/")) {
            out.println("Comando no disponible para invitados. Debes usar /login o /registrar.");
        } else {
            handleMensajeInvitado(mensaje);
        }
    }
    private void handleBloquear(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            String respuesta = servidor.bloquearUsuario(this.idUsuario, partes[1]);
            out.println(respuesta);
        } else {
            out.println("Error: Formato incorrecto. Usa /bloquear <username>");
        }
    }

    private void handleDesbloquear(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            String respuesta = servidor.desbloquearUsuario(this.idUsuario, partes[1]);
            out.println(respuesta);
        } else {
            out.println("Error: Formato incorrecto. Usa /desbloquear <username>");
        }
    }
    private void handlePrivado(String mensaje) {
        String[] partes = mensaje.split(" ", 3);
        if (partes.length >= 3) {
            String destinatario = partes[1];
            String msgPrivado = partes[2];
            servidor.enviarMensajePrivado(msgPrivado, this, destinatario);
        } else {
            out.println("Error: Formato incorrecto. Usa /privado <user> <mensaje>");
        }
    }
    private void handleProponerGatito(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            servidor.proponerJuego(this, partes[1]);
        } else {
            out.println("Formato incorrecto. Usa /gatito <username>");
        }
    }
    private void handleAceptarGatito(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            servidor.aceptarJuego(this, partes[1]);
        } else {
            out.println("Formato incorrecto. Usa /aceptar <username>");
        }
    }
    private void handleRechazarGatito(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            servidor.rechazarJuego(this, partes[1]);
        } else {
            out.println("Formato incorrecto. Usa /rechazar <username>");
        }
    }

    private void handleMoverGatito(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        String argumentos = (partes.length == 2) ? partes[1] : "";
        servidor.manejarMovimientoGatito(this, argumentos);
    }

    private void handleComparar(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length == 2) {
            servidor.compararEstadisticas(this, partes[1]);
        } else {
            out.println("Formato incorrecto. Usa /comparar <username>");
        }
    }

    public int getCantidadJuegosActivos() {
        return servidor.contarJuegosActivos(this);
    }
    private boolean procesarMensajeLogueado(String mensaje) {
        if (mensaje.startsWith("/bloquear ")) {
            handleBloquear(mensaje);
        } else if (mensaje.startsWith("/desbloquear ")) {
            handleDesbloquear(mensaje);
        } else if (mensaje.startsWith("/privado ")) {
            handlePrivado(mensaje);
        }
        else if (mensaje.startsWith("/gatito ")) {
            handleProponerGatito(mensaje);
        } else if (mensaje.startsWith("/aceptar ")) {
            handleAceptarGatito(mensaje);
        } else if (mensaje.startsWith("/rechazar ")) {
            handleRechazarGatito(mensaje);
        } else if (mensaje.startsWith("/mover ")) {
            handleMoverGatito(mensaje);
        }
        else if (mensaje.equals("/ranking")) {
            servidor.mostrarRanking(this);
        } else if (mensaje.startsWith("/comparar ")) {
            handleComparar(mensaje);
        }
        else if (mensaje.equals("/usuarios")) {
            out.println(servidor.obtenerListaUsuarios(this.username));
        } else if (mensaje.equals("/ayuda")) {
            enviarMenuAyuda();
        } else if (mensaje.equals("/adios")) {
            return true;
        } else if (mensaje.startsWith("/")) {
            out.println("Comando no reconocido. Escribe /ayuda para ver la lista.");
        } else {
            servidor.difundirMensaje(mensaje, this);
        }
        return false;
    }
    private void enviarMenuAyuda() {
        out.println("--- MENU DE AYUDA SHAVALON---");
        out.println(" * /privado <usuario> <mensaje> (Envia un mensaje privado.)");
        out.println(" * /bloquear <usuario>          (Bloquea a un usuario.)");
        out.println(" * /desbloquear <usuario>       (Desbloquea a un usuario.)");
        out.println(" * /usuarios                    (Muestra otros usuarios registrados)");
        out.println("--- JUEGO DEL GATITO ---");
        out.println(" * /gatito <usuario>            (Propone un juego a otro usuario.)");
        out.println(" * /aceptar <usuario>           (Acepta la propuesta de un usuario.)");
        out.println(" * /rechazar <usuario>          (Rechaza la propuesta de un usuario.)");
        out.println(" * /mover <1-9>                 (Realiza un movimiento en tu juego activo.)");
        out.println("--- GENERAL ---");
        out.println(" * /ayuda                       (Muestra este menu.)");
        out.println(" * /adios                       (Desconectarse del chat. Pierdes si estás jugando.)");
        out.println(" * (Escribe cualquier otra cosa para un mensaje público)");
        out.println("-------------------------");
    }
}