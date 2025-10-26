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
            if (tablero[i] == ' ') {
                sb.append(i + 1);
            } else {
                sb.append(tablero[i]);
            }
            if ((i + 1) % 3 == 0) {
                sb.append("\n");
            if (i < 8) sb.append("--------\n");
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
        notificarTablero();
        notificarTurno();
    }
    private void notificarTablero() {
        String tableroStr = dibujarTablero();
        notificar(tableroStr);
    }

    private void notificarTurno() {
        if (terminado) return;
        char ficha = turnoActual == jugadorX ? 'X' : 'O';
        turnoActual.out.println("[Gatito] Es tu turno (" + ficha + "). Usa /mover <1-9>.");
        UnCliente oponente = getOponente(turnoActual);
        if (oponente != null) {
            oponente.out.println("[Gatito] Es el turno de " + turnoActual.getUsername() + ".");
        }
    }
    public boolean realizarMovimiento(UnCliente cliente, int posicion) {
        if (terminado) {
            cliente.out.println("Error: El juego ha terminado.");
            return true;
        }
        if (cliente != turnoActual) {
            cliente.out.println("Error: No es tu turno.");
            return false;
        }
        if (posicion < 1 || posicion > 9) {
            cliente.out.println("Error: Posición inválida. Debe ser un número entre 1 y 9. Ejemplo: /mover 5");
            return false;
        }
        if (tablero[posicion - 1] != ' ') {
            cliente.out.println("Error: La casilla " + posicion + " ya está ocupada.");
            return false;
        }

        char ficha = cliente == jugadorX ? 'X' : 'O';
        tablero[posicion - 1] = ficha;

        notificarTablero();

        if (verificarGanador(ficha)) {
            notificar("¡FIN DEL JUEGO! el shavalon " + cliente.getUsername() + " (" + ficha + ") ha ganado.");
            terminado = true;
            return true;
        }
        if (verificarEmpate()) {
            notificar("¡FIN DEL JUEGO! que pros son, es un empate.");
            terminado = true;
            return true;
        }
        turnoActual = getOponente(cliente);
        notificarTurno();
        return false;
    }
    private boolean verificarGanador(char ficha) {
        int[][] lineasGanadoras = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Filas
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Columnas
                {0, 4, 8}, {2, 4, 6}             // Diagonales
        };

        for (int[] linea : lineasGanadoras) {
            if (tablero[linea[0]] == ficha &&
                    tablero[linea[1]] == ficha &&
                    tablero[linea[2]] == ficha) {
                return true;
            }
        }
        return false;
    }

    private boolean verificarEmpate() {
        for (char c : tablero) {
            if (c == ' ') return false;
        }
        return true;
    }

    public boolean haTerminado() {
        return terminado;
    }

    public void forzarTerminacion(UnCliente perdedor) {
        if (terminado) return;
        terminado = true;

        UnCliente ganador = getOponente(perdedor);

        if (ganador != null) {
            ganador.out.println("¡VICTORIA SHAVALON! " + perdedor.getUsername() + " ha huido.");
        }

        if (perdedor != null) {
            perdedor.out.println("Has perdido el juego por abandono/desconexión.");
        }
    }
}
