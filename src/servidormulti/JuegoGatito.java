package servidormulti;

import java.util.Arrays;
import java.util.Random;

public class JuegoGatito {
    private final UnCliente jugadorX;
    private final UnCliente jugadorO;
    private final char[] tablero = new char[9];
    private UnCliente turnoActual;
    private boolean terminado = false;

    public JuegoGatito(UnCliente p1, UnCliente p2) {
        Arrays.fill(tablero, ' ');
        if (new Random().nextBoolean()) {
            this.jugadorX = p1;
            this.jugadorO = p2;
        } else {
            this.jugadorX = p2;
            this.jugadorO = p1;
        }
        this.turnoActual = this.jugadorX;

        notificarInicio(jugadorX.getUsername(), jugadorO.getUsername());
    }

    public UnCliente getOponente(UnCliente jugador) {
        return jugador == jugadorX ? jugadorO : jugadorX;
    }

    public boolean esJugador(UnCliente cliente) {
        return cliente == jugadorX || cliente == jugadorO;
    }

    public boolean involucraA(String username1, String username2) {
        String u1 = jugadorX.getUsername();
        String u2 = jugadorO.getUsername();
        return (u1.equals(username1) && u2.equals(username2)) || (u1.equals(username2) && u2.equals(username1));
    }

    private String dibujarTablero() {
        StringBuilder sb = new StringBuilder("\n--- Tablero Gatito ---\n");
        for (int i = 0; i < 9; i++) {
            sb.append(tablero[i] == ' ' ? i + 1 : tablero[i]);
            if ((i + 1) % 3 == 0) {
                sb.append("\n");
                if (i < 8) sb.append("---------------\n");
            } else {
                sb.append(" | ");
            }
        }
        sb.append("----------------------\n");
        return sb.toString();
    }
    private void notificar(String mensaje) {
        if (jugadorX != null) jugadorX.out.println(mensaje);
        if (jugadorO != null) jugadorO.out.println(mensaje);
    }

    private void notificarInicio(String nombreX, String nombreO) {
        notificar("¡Juego iniciado Shavalon!");

        if (jugadorX != null) {
            jugadorX.out.println("Eres la ficha 'X'. Tu enemigo es " + nombreO + ".");
        }
        if (jugadorO != null) {
            jugadorO.out.println("Eres la ficha 'O'. Tu enemigo es " + nombreX + ".");
        }

    }
}
