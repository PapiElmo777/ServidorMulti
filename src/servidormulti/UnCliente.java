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
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("Conectado. Puedes enviar 3 mensajes como " + this.guestUsername);
            out.println("Usa /registrar <user> <pass> o /login <user> <pass> para chatear sin límites.");


            String mensaje;
            while ((mensaje = in.readLine()) != null) {

                if (!isLogueado()) {

                    if (mensaje.startsWith("/login ")) {
                        String[] partes = mensaje.split(" ", 3);
                        if (partes.length == 3) {
                            if (servidor.autenticarUsuario(partes[1], partes[2])) {
                                this.username = partes[1];
                                this.idUsuario = servidor.obtenerIdUsuario(this.username);

                                out.println("¡Login exitoso! Bienvenido " + this.username);
                                enviarMenuAyuda();
                                servidor.difundirMensaje("[Servidor] " + this.username + " se ha unido al chat.", this);
                            } else {
                                out.println("Error: Usuario o contraseña incorrectos.");
                            }
                        } else {
                            out.println("Error: Formato incorrecto. Usa /login <user> <pass>");
                        }
                    } else if (mensaje.startsWith("/registrar ")) {
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
                    } else if (mensaje.startsWith("/")) {
                        out.println("Comando no disponible para invitados. Debes usar /login o /registrar.");
                    }
                    else {
                        if (mensajesComoInvitado < 3) {
                            mensajesComoInvitado++;
                            servidor.difundirMensaje(mensaje, this);
                            out.println("[Mensaje " + mensajesComoInvitado + "/3 como " + this.guestUsername + "].");
                        } else {
                            out.println("Límite de 3 mensajes de invitado alcanzado.");
                            out.println("Usa /registrar <user> <pass> o /login <user> <pass>");
                        }
                    }

                } else {

                    if (mensaje.startsWith("/bloquear ")) {
                        String[] partes = mensaje.split(" ", 2);
                        if (partes.length == 2) {
                            String respuesta = servidor.bloquearUsuario(this.idUsuario, partes[1]);
                            out.println(respuesta);
                        } else {
                            out.println("Error: Formato incorrecto. Usa /bloquear <username>");
                        }

                    } else if (mensaje.startsWith("/desbloquear ")) {
                        String[] partes = mensaje.split(" ", 2);
                        if (partes.length == 2) {
                            String respuesta = servidor.desbloquearUsuario(this.idUsuario, partes[1]);
                            out.println(respuesta);
                        } else {
                            out.println("Error: Formato incorrecto. Usa /desbloquear <username>");
                        }

                    } else if (mensaje.startsWith("/privado ")) {
                        String[] partes = mensaje.split(" ", 3);
                        if (partes.length >= 3) {
                            String destinatario = partes[1];
                            String msgPrivado = mensaje.substring(mensaje.indexOf(destinatario) + destinatario.length()).trim();
                            servidor.enviarMensajePrivado(msgPrivado, this, destinatario);
                        } else {
                            out.println("Error: Formato incorrecto. Usa /privado <user> <mensaje>");
                        }

                    } else if (mensaje.equals("/usuarios")) {
                        out.println(servidor.obtenerListaUsuarios(this.username));

                    } else if (mensaje.equals("/ayuda")) {
                        enviarMenuAyuda();

                    } else if (mensaje.equals("/adios")) {
                        break;

                    } else if (mensaje.startsWith("/")) {
                        out.println("Comando no reconocido. Escribe /ayuda para ver la lista.");
                    }
                    else {
                        servidor.difundirMensaje(mensaje, this);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error de IO en UnCliente (" + getUsername() + "): " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
            servidor.removerCliente(this);
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
            if (servidor.autenticarUsuario(partes[1], partes[2])) {
                this.username = partes[1];
                this.idUsuario = servidor.obtenerIdUsuario(this.username);

                out.println("BIENVENIDO SHAVALON " + this.username);
                enviarMenuAyuda();
                servidor.difundirMensaje("[Servidor] " + this.username + " se ha unido al chat.", this);
            } else {
                out.println("Error: Usuario o contraseña incorrectos.");
            }
        } else {
            out.println("Error: Formato incorrecto. Usa /login <user> <pass>");
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
    private void enviarMenuAyuda() {
        out.println("--- MENU DE AYUDA SHAVALON---");
        out.println(" * /privado <usuario> <mensaje> (Envia un mensaje privado.)");
        out.println(" * /bloquear <usuario>          (Bloquea a un usuario.)");
        out.println(" * /desbloquear <usuario>       (Desbloquea a un usuario.)");
        out.println(" * /usuarios                    (Muestra otros usuarios registrados)");
        out.println(" * /ayuda                       (Muestra este menu.)");
        out.println(" * /adios                       (Desconectarse del chat.)");
        out.println(" * (Escribe cualquier otra cosa para un mensaje público)");
        out.println("-------------------------");
    }
}