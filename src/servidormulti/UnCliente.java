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
    private int grupoActualId = 1;
    private String grupoActualNombre = "Todos";
    public UnCliente(Socket socket, ServidorMulti servidor) {
        this.socket = socket;
        this.servidor = servidor;
        this.guestUsername = "Invitado " + servidor.getSiguienteNumeroInvitado();
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
    public void setGrupoActual(int id, String nombre) {
        this.grupoActualId = id;
        this.grupoActualNombre = nombre;
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
        out.println("Conectado. Estás en el grupo 'Todos'. Puedes enviar " + LIMITE_MENSAJES_INVITADO + " mensajes como " + this.guestUsername);
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
                out.println("\nCargando grupo 'Todos'...");
                servidor.cambiarGrupo(this, "Todos");
                String msgFormateado = "Servidor: " + this.username + " se ha unido al chat.";
                String msgParaOtros = "[Servidor] " + this.username + " se ha unido al chat.";

                servidor.registrarMensajeEnArchivo(this.grupoActualId, msgFormateado);
                servidor.difundirMensajeGrupo(this, this.grupoActualId, msgParaOtros);
            } else{
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
            servidor.difundirMensajeInvitado(mensaje, this);
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
            mensajesComoInvitado++;
            out.println("[Mensaje " + mensajesComoInvitado + "/" + LIMITE_MENSAJES_INVITADO + " como " + this.guestUsername + "].");
            if (mensajesComoInvitado == 3){
                out.println("Límite de " + LIMITE_MENSAJES_INVITADO + " mensajes de invitado alcanzado.");
                out.println("Usa /registrar <user> <pass> o /login <user> <pass>");
            }
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
        if (mensaje.startsWith("/crear-grupo ")) {
            String[] partes = mensaje.split(" ", 2);
            if (partes.length == 2) {
                out.println(servidor.crearGrupo(this, partes[1]));
            } else {
                out.println("Usa /crear-grupo <nombre-grupo>");
            }
        } else if (mensaje.startsWith("/unirse-grupo ")) {
            String[] partes = mensaje.split(" ", 2);
            if (partes.length == 2) {
                out.println(servidor.unirseGrupo(this, partes[1]));
            } else {
                out.println("Usa /unirse-grupo <nombre-grupo>");
            }
        } else if (mensaje.startsWith("/borrar-grupo ")) {
            String[] partes = mensaje.split(" ", 2);
            if (partes.length == 2) {
                out.println(servidor.borrarGrupo(this, partes[1]));
            } else {
                out.println("Usa /borrar-grupo <nombre-grupo>");
            }
        } else if (mensaje.startsWith("/grupo ")) {
            String[] partes = mensaje.split(" ", 2);
            if (partes.length == 2) {
                servidor.cambiarGrupo(this, partes[1]);
            } else {
                out.println("Usa /grupo <nombre-grupo> para cambiar de chat.");
            }
        } else if (mensaje.equals("/lista-grupos")) {
            out.println(servidor.listarGrupos(this));
        } else if (mensaje.equals("/miembros")) {
            out.println(servidor.obtenerCabeceraGrupo(this.grupoActualId, this.grupoActualNombre));
        }
        else if (mensaje.startsWith("/bloquear ")) {
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
            String msgFormateado = this.username + ": " + mensaje;
            servidor.registrarMensajeEnArchivo(this.grupoActualId, msgFormateado);
            servidor.difundirMensajeGrupo(this, this.grupoActualId, msgFormateado);
        }
        return false;
    }
    private void enviarMenuAyuda() {
        out.println("--- MENU DE AYUDA SHAVALON---");
        out.println("--- GRUPOS ---");
        out.println(" * (Cualquier mensaje)          (Envía un mensaje a tu grupo actual.)");
        out.println(" * /grupo <nombre>              (Cambia tu chat al grupo especificado.)");
        out.println(" * /crear-grupo <nombre>        (Crea un nuevo grupo.)");
        out.println(" * /unirse-grupo <nombre>       (Te une a un grupo existente.)");
        out.println(" * /borrar-grupo <nombre>       (Borra un grupo si eres el creador.)");
        out.println(" * /lista-grupos                (Muestra todos los grupos.)");
        out.println(" * /miembros                    (Muestra los miembros de tu grupo actual.)");
        out.println("--- CHAT 1-A-1 ---");
        out.println(" * /privado <usuario> <mensaje> (Envia un mensaje privado.)");
        out.println(" * /bloquear <usuario>          (Bloquea a un usuario.)");
        out.println(" * /desbloquear <usuario>       (Desbloquea a un usuario.)");
        out.println(" * /usuarios                    (Muestra otros usuarios registrados)");
        out.println("--- RANKING ---");
        out.println(" * /ranking                     (Muestra a los mejores 10 Shavalones en el gatito.)");
        out.println(" * /comparar <usuario>          (Compara tus victorias con otro shavalon.)");
        out.println("--- JUEGO DEL GATITO ---");
        out.println(" * /gatito <usuario>            (Propone un juego a otro usuario.)");
        out.println(" * /aceptar <usuario>           (Acepta la propuesta de un usuario.)");
        out.println(" * /rechazar <usuario>          (Rechaza la propuesta de un usuario.)");
        out.println(" * /mover <1-9>                 (Realiza un movimiento en tu juego activo.)");
        out.println("--- GENERAL ---");
        out.println(" * /ayuda                       (Muestra este menu.)");
        out.println(" * /adios                       (Desconectarse del chat. Pierdes si estás jugando.)");
        out.println("-------------------------");
    }
}