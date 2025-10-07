package servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteId;
    UnCliente(Socket s, String clienteId) throws IOException{
        this.clienteId = clienteId;
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        while (true){
            try{
                mensaje = entrada.readUTF();
                String mensajeConRemitente = "Cliente #" + clienteId + ": " + mensaje;

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
                break;
            }
        }
    }
}