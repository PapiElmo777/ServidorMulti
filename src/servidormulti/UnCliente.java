package servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteId;
    private int mensajesEnviados = 0;
    private boolean estaAutenticado = false;
    private String nombreUsuario = null;
    UnCliente(Socket s, String clienteId) throws IOException{
        this.clienteId = clienteId;
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        try{
            salida.writeUTF("Que rollo shavalon, eres invitado #" + clienteId);
            salida.writeUTF("Tienes 3 mensajes gratis, despues tendras que registrarte o iniciar sesion.");
            salida.writeUTF("Para registrarte hazlo asi: /registrar <user> <pass>");
            salida.writeUTF("Para loguearte hazlo asi: /login <user> <pass>");
        }catch (IOException e){

        }
        while (true){
            try{
                mensaje = entrada.readUTF();
                if (mensaje.startsWith("/")) {
                    String[] comando = mensaje.split(" ");
                    if (comando[0].equalsIgnoreCase("/registrar") && comando.length == 3) {
                        String usuario = comando[1];
                        String pass = comando[2];
                        if (ServidorMulti.usuariosRegistrados.containsKey(usuario)) {
                            salida.writeUTF("Error: El nombre de usuario ya existe.");
                        } else {
                            ServidorMulti.usuariosRegistrados.put(usuario, pass);
                            this.nombreUsuario = usuario;
                            this.estaAutenticado = true;
                            salida.writeUTF("Has iniciado sesión como: " + this.nombreUsuario);
                        }
                        continue;
                    }

                    if (mensaje.startsWith("@")){
                    String [] partes = mensaje.split(" ", 2);
                    String aQuienes = partes[0].substring(1);
                    String[] idsDestinatarios = aQuienes.split(",");

                    for (String id : idsDestinatarios) {
                        UnCliente clientecito = ServidorMulti.clientes.get(id.trim());
                        if (clientecito != null) {
                            clientecito.salida.writeUTF("(Privado) " + mensajeConRemitente);
                        }
                    }
                }else{
                    for(UnCliente cliente : ServidorMulti.clientes.values()){
                        if (!this.clienteId.equals(cliente.clienteId)){
                            cliente.salida.writeUTF(mensajeConRemitente);
                        }
                    }
                }
            }catch (IOException ex){
                System.out.println("Cliente #" + clienteId + " se ha desconectado.");
                ServidorMulti.clientes.remove(this.clienteId);
                break;
            }
        }
    }
}