package servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    final DataOutputStream salida;
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataInputStream entrada;

    UnCliente(Socket s) throws IOException{
        salida = new DataOutputStream(s.getOutputStream());
        entrada = new DataInputStream(s.getInputStream());
    }
    @Override
    public void run() {
        String mensaje;
        while (true){
            try{
                mensaje = entrada.readUTF();
                if (mensaje.startsWith("@")){
                    String [] partes = mensaje.split(" ", 2); // Divide en dos partes: destinatarios y mensaje
                    String aQuienes = partes[0].substring(1); // Remueve el "@"
                    String[] idsDestinatarios = aQuienes.split(","); // Separa los IDs por comas

                    for (String id : idsDestinatarios) {
                        UnCliente clientecito = ServidorMulti.clientes.get(id.trim());
                        if (clientecito != null) {
                            clientecito.salida.writeUTF(partes[1]);
                        }
                    }
                }else{
                    for(UnCliente cliente : ServidorMulti.clientes.values()){
                        cliente.salida.writeUTF(mensaje);
                    }
                }
            }catch (IOException ex){

            }
        }
    }
}
