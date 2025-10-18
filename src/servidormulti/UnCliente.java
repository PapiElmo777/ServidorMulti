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

            out.println("Conectado al servidor. Usa /registrar <user> <pass> o /login <user> <pass>");

            String mensaje;
            while ((mensaje = in.readLine()) != null) {

                if (username == null) {
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
                    } else {
                        out.println("Comando no reconocido. Debes usar /login o /registrar.");
                    }
                }
                else {
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

                    } else {
                        servidor.difundirMensaje(mensaje, this);
                    }
                }
            }
        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
            if (username != null) {
                servidor.removerCliente(this);
            }
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