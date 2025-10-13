package servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    private final String clienteId;
    private String nombreUsuario = null;
    private int mensajesEnviados = 0;
    private boolean estaAutenticado = false;
    private final DataOutputStream salida;
    private final DataInputStream entrada;

    public UnCliente(Socket s, String clienteId) throws IOException {
        this.clienteId = clienteId;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            enviarMensajesDeBienvenida();
            String mensajeEntrante;
            while ((mensajeEntrante = entrada.readUTF()) != null) {
                if (mensajeEntrante.startsWith("/")) {
                    procesarComando(mensajeEntrante);
                } else {
                    procesarMensajeChat(mensajeEntrante);
                }
            }
        } catch (IOException ex) {
            System.out.println("El cliente " + getNombreRemitente() + " ha perdido la conexión.");
        } finally {
            ServidorMulti.removerCliente(this);
        }
    }
    private void registrar(String usuario, String pass) throws IOException {
        if (estaAutenticado) {
            enviarMensaje("Ya tienes una sesión activa. Usa /logout para registrar una nueva cuenta.");
            return;
        }
        if (ServidorMulti.registrarUsuario(usuario, pass)) {
            this.nombreUsuario = usuario;
            this.estaAutenticado = true;
            enviarMensaje("¡Registro exitoso! Has iniciado sesión como: " + this.nombreUsuario);
        } else {
            enviarMensaje("Error: El nombre de usuario ya existe.");
        }
    }
    private void login(String usuario, String pass) throws IOException {
        if (estaAutenticado) {
            enviarMensaje("Ya tienes una sesión activa. Usa /logout para iniciar sesión con otra cuenta.");
            return;
        }
        if (ServidorMulti.autenticarUsuario(usuario, pass)) {
            this.nombreUsuario = usuario;
            this.estaAutenticado = true;
            enviarMensaje("Bienvenido de nuevo shavalon, " + this.nombreUsuario);
        } else {
            enviarMensaje("Error: Usuario o contraseña incorrectos.");
        }
    }
    private void logout() throws IOException {
        if (!estaAutenticado) {
            enviarMensaje("No has iniciado sesión, no puedes usar /logout.");
            return;
        }
        String usuarioAnterior = this.nombreUsuario;
        this.estaAutenticado = false;
        this.nombreUsuario = null;
        this.mensajesEnviados = 0;
        enviarMensaje("Adios '" + usuarioAnterior + "'. Ahora eres un invitado.");
        ServidorMulti.enviarMensajePublico(this, ">> El usuario '" + usuarioAnterior + "' ha cerrado sesión. <<", true);
    }
    private void procesarComando(String linea) throws IOException {
        String[] partes = linea.split(" ", 3);
        String comando = partes[0].toLowerCase();

        switch (comando) {
            case "/registrar":
                if (partes.length == 3) registrar(partes[1], partes[2]);
                else enviarMensaje("Formato incorrecto. Usa: /registrar <user> <pass>");
                break;
            case "/login":
                if (partes.length == 3) login(partes[1], partes[2]);
                else enviarMensaje("Formato incorrecto. Usa: /login <user> <pass>");
                break;
            case "/logout":
                logout();
                break;
            default:
                enviarMensaje("Comando no reconocido.");
        }
    }
    private void procesarMensajeChat(String mensaje) throws IOException {
        if (!estaAutenticado && mensajesEnviados >= 3) {
            enviarMensaje("Llegaste al Límite de mensajes shavalon. Por favor, regístrate o inicia sesión.");
            return;
        }
        if (!estaAutenticado) {
            mensajesEnviados++;
        }
        if (mensaje.startsWith("@")) {
            String[] partes = mensaje.split(" ", 2);
            if (partes.length < 2) {
                enviarMensaje("Formato de mensaje privado incorrecto. Usa @usuario1,usuario2 mensaje");
                return;
            }
            ServidorMulti.enviarMensajePrivado(this, partes[0].substring(1), partes[1]);
        } else {
            ServidorMulti.enviarMensajePublico(this, mensaje, false);
        }
    }
    private void enviarMensajesDeBienvenida() throws IOException {
        enviarMensaje("Que rollo shavalon, eres invitado #" + clienteId);
        enviarMensaje("Tienes 3 mensajes gratis, despues tendras que registrarte o iniciar sesion.");
        enviarMensaje("--> /registrar <user> <pass>");
        enviarMensaje("--> /login <user> <pass>");
        enviarMensaje("--> /logout");
    }


    public String getClienteId() { return clienteId; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getNombreRemitente() {
        return estaAutenticado ? nombreUsuario : "Invitado #" + clienteId;
    }
    public void enviarMensaje(String mensaje) throws IOException {
        salida.writeUTF(mensaje);
    }
}