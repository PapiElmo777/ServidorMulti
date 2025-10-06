package servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {
    static HashMap<String,UnCliente> clientes = new HashMap<String,UnCliente>();

    public static void main(String[] args) throws IOException{
        ServerSocket servidorSocket = new ServerSocket(8080);
        int contador = 0;
        while(true){
            Socket s = servidorSocket.accept();
            UnCliente unCliente = new UnCliente(s);
            Thread hilo = new Thread(unCliente);
            clientes.put(Integer.toString(contador), unCliente);
            hilo.start();
            System.out.println("Se conecto el chavalo: #" +  contador);
            contador++;
        }
    }
}
