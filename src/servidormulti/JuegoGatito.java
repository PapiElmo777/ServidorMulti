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
}
