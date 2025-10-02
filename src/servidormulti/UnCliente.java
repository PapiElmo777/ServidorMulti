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
                for(UnCliente cliente : ServidorMulti.clientes.values()){
                    cliente.salida.writeUTF(mensaje);
                }
            }catch (IOException ex){

            }
        }
    }
}
