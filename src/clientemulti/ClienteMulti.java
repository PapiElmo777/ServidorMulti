package clientemulti;

import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {

    public static void main(String[] args) throws IOException{
        Socket s = new Socket("localhost",8080);
        ParaMandar paraMandar = new ParaMandar(s);
        Thread hiloParaMandar = new Thread(paraMandar);
        hiloParaMandar.start();

        ParaRecibir paraRecibir = new ParaRecibir(s);
        Thread hiloParaRecibir = new Thread(paraRecibir);
        hiloParaRecibir.start();
    }


   /* Terminal
   cd /Users/alfredojimenez/IdeaProjects/ServidorMulti/out/production/ServidorMulti/
   java clientemulti.ClienteMulti
   ip
   ifconfig | grep "inet "
    */
}
