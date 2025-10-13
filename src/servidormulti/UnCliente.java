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
        String mensaje;
        try{
            salida.writeUTF("Que rollo shavalon, eres invitado #" + clienteId);
            salida.writeUTF("Tienes 3 mensajes gratis, despues tendras que registrarte o iniciar sesion.");
            salida.writeUTF("Para registrarte hazlo asi: /registrar <user> <pass>");
            salida.writeUTF("Para loguearte hazlo asi: /login <user> <pass>");
            salida.writeUTF("Para cerrar sesion /logout");
        }catch (IOException e){

        }
        while (true){
            try{
                mensaje = entrada.readUTF();
                if (mensaje.startsWith("/")) {
                    String[] comando = mensaje.split(" ");
                    if (comando[0].equalsIgnoreCase("/logout")) {
                        if (estaAutenticado) {
                            String usuarioAnterior = this.nombreUsuario;
                            this.estaAutenticado = false;
                            this.nombreUsuario = null;
                            this.mensajesEnviados = 0;
                            salida.writeUTF("Adios '" + usuarioAnterior + "'. Ahora eres un invitado.");
                            for (UnCliente cliente : ServidorMulti.clientes.values()) {
                                if (!this.clienteId.equals(cliente.clienteId)) {
                                    cliente.salida.writeUTF(">> El usuario '" + usuarioAnterior + "' ha cerrado sesión. <<");
                                }
                            }
                        } else {
                            salida.writeUTF("No has iniciado sesión, no puedes usar /logout.");
                        }
                        continue;
                    }
                    if (comando[0].equalsIgnoreCase("/registrar") && comando.length == 3) {
                        if (estaAutenticado) {
                            salida.writeUTF("Chavalon estas en una sesión activa no puedes registrar una nueva cuenta.");
                            continue;
                        }
                        String usuario = comando[1];
                        String pass = comando[2];
                        if (ServidorMulti.usuariosRegistrados.containsKey(usuario)) {
                            salida.writeUTF("Error: El nombre de usuario ya existe.");
                        } else {
                            ServidorMulti.usuariosRegistrados.put(usuario, pass);
                            ServidorMulti.guardarUsuarioEnArchivo(usuario, pass);
                            this.nombreUsuario = usuario;
                            this.estaAutenticado = true;
                            salida.writeUTF("Has iniciado sesión como: " + this.nombreUsuario);
                        }
                        continue;
                    }
                    if (comando[0].equalsIgnoreCase("/login") && comando.length == 3) {
                        if (estaAutenticado) {
                            salida.writeUTF("Chavalon estas en una sesión activa no puedes iniciar sesión con otra cuenta.");
                            continue;
                        }
                        String usuario = comando[1];
                        String pass = comando[2];
                        if (ServidorMulti.usuariosRegistrados.containsKey(usuario) && ServidorMulti.usuariosRegistrados.get(usuario).equals(pass)) {
                            this.nombreUsuario = usuario;
                            this.estaAutenticado = true;
                            salida.writeUTF("Bienvenido de nuevo shavalon, " + this.nombreUsuario);
                        } else {
                            salida.writeUTF("Error: Usuario o contraseña incorrectos.");
                        }
                        continue;
                    }
                    salida.writeUTF("Comando en formato incorrecto.");
                    continue;
                }
                if (!estaAutenticado && mensajesEnviados >= 3) {
                    salida.writeUTF("Llegaste al Límite de mensajes shavalon. Por favor, regístrate o inicia sesión para continuar enviando mensajes.");
                    continue;
                }
                String remitente = estaAutenticado ? this.nombreUsuario : "Invitado #" + clienteId;
                if (!estaAutenticado) {
                    mensajesEnviados++;
                }
                if (mensaje.startsWith("@")){
                    String [] partes = mensaje.split(" ", 2);
                    if (partes.length < 2) {
                        salida.writeUTF("Formato de mensaje privado incorrecto. Usa @usuario1,usuario2 mensaje");
                        continue;
                    }
                    String aQuienes = partes[0].substring(1);
                    String[] nombresDestinatarios = aQuienes.split(",");
                    String mensajePrivado = "(Privado) " + remitente + ": " + partes[1];

                    for (String destNombre : nombresDestinatarios) {
                        boolean encontrado = false;
                        for (UnCliente clienteDestino : ServidorMulti.clientes.values()) {
                            if (destNombre.trim().equals(clienteDestino.nombreUsuario) || destNombre.trim().equals(clienteDestino.clienteId)) {
                                clienteDestino.salida.writeUTF(mensajePrivado);
                                encontrado = true;
                                break;
                            }
                        }
                        if (!encontrado) {
                            salida.writeUTF("El usuario '" + destNombre.trim() + "' no fue encontrado o no está conectado.");
                        }
                    }
                } else {
                    String mensajeConRemitente = remitente + ": " + mensaje;
                    for(UnCliente cliente : ServidorMulti.clientes.values()){
                        if (!this.clienteId.equals(cliente.clienteId)) {
                            cliente.salida.writeUTF(mensajeConRemitente);
                        }
                    }
                }
            }catch (IOException ex){
                System.out.println("Shavalon #" + clienteId + " se ha desconectado.");
                ServidorMulti.clientes.remove(this.clienteId);
                break;
            }
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


    public String getClienteId() { return clienteId; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getNombreRemitente() {
        return estaAutenticado ? nombreUsuario : "Invitado #" + clienteId;
    }
    public void enviarMensaje(String mensaje) throws IOException {
        salida.writeUTF(mensaje);
    }
}