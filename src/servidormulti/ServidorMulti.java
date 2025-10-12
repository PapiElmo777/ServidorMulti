package servidormulti;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;

public class ServidorMulti {
    static HashMap<String,UnCliente> clientes = new HashMap<String,UnCliente>();
    static HashMap<String, String> usuariosRegistrados = new HashMap<>();
    private static final String ARCHIVO_USUARIOS = "usuarios.txt";

    public static void main(String[] args) throws IOException{
        ServerSocket servidorSocket = new ServerSocket(8080);
        int contador = 0;
        while(true){
            Socket s = servidorSocket.accept();
            UnCliente unCliente = new UnCliente(s, Integer.toString(contador));
            Thread hilo = new Thread(unCliente);
            clientes.put(Integer.toString(contador), unCliente);
            hilo.start();
            System.out.println("Se conecto el chavalo: #" +  contador + "( Como invitado)");
            contador++;
        }
    }
    public static void cargarUsuarios() {
        try (Scanner scanner = new Scanner(new File(ARCHIVO_USUARIOS))) {
            System.out.println("Cargando usuarios desde el archivo...");
            while (scanner.hasNextLine()) {
                String linea = scanner.nextLine();
                String[] partes = linea.split(":", 2); // Divide en "usuario:contraseña"
                if (partes.length == 2) {
                    usuariosRegistrados.put(partes[0], partes[1]);
                }
            }
            System.out.println(usuariosRegistrados.size() + " usuarios cargados.");
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de usuarios no encontrado. Se creará uno nuevo al registrar el primer usuario.");
        }
    }
}