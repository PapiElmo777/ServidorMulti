package clientemulti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ParaMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final PrintWriter salida;

    public ParaMandar(Socket s) throws IOException {
        this.salida = new PrintWriter(s.getOutputStream(), true);
    }

    @Override
    public void run() {
        String mensaje;
        try {
            while ((mensaje = teclado.readLine()) != null) {
                salida.println(mensaje);
            }
        } catch (IOException ex) {
            System.out.println("Se te fue el wifi campeon, te desconectaste");
        }
    }
}