package clientemulti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ParaRecibir implements Runnable {
    final BufferedReader entrada;

    public ParaRecibir(Socket s) throws IOException {
        entrada = new BufferedReader(new InputStreamReader(s.getInputStream()));
    }

    @Override
    public void run() {
        String mensaje;
        try {
            while ((mensaje = entrada.readLine()) != null) {
                System.out.println(mensaje);
            }
        } catch (IOException ex) {
            System.out.println("Desconectado del servidor.");
        }
    }
}