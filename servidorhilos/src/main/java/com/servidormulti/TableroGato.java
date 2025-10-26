package com.servidormulti;

public class TableroGato {
    private final char[][] tablero;
    private char simboloActual; // 'X' o 'O'

    public TableroGato() {
        this.tablero = new char[3][3];
        inicializarTablero();
        this.simboloActual = 'X'; // Siempre empieza X
    }

    private void inicializarTablero() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tablero[i][j] = ' ';
            }
        }
    }

    public char[][] getTablero() {
        return tablero;
    }

    public char getSimboloActual() {
        return simboloActual;
    }

    public void setSiguienteSimbolo() {
        this.simboloActual = (this.simboloActual == 'X') ? 'O' : 'X';
    }

    /**
     * Intenta realizar un movimiento y retorna true si es válido y exitoso.
     */
    public boolean hacerMovimiento(int fila, int col) {
        if (esMovimientoValido(fila, col)) {
            tablero[fila - 1][col - 1] = this.simboloActual;
            return true;
        }
        return false;
    }

    private boolean esMovimientoValido(int fila, int col) {
        return fila >= 1 && fila <= 3 && col >= 1 && col <= 3 && tablero[fila - 1][col - 1] == ' ';
    }

    /**
     * Retorna "GANADOR", "EMPATE", o "CONTINUA".
     */
    public String verificarEstadoJuego() {
        // Verificar filas y columnas
        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == simboloActual && tablero[i][1] == simboloActual && tablero[i][2] == simboloActual) return "GANADOR";
            if (tablero[0][i] == simboloActual && tablero[1][i] == simboloActual && tablero[2][i] == simboloActual) return "GANADOR";
        }
        // Verificar diagonales
        if (tablero[0][0] == simboloActual && tablero[1][1] == simboloActual && tablero[2][2] == simboloActual) return "GANADOR";
        if (tablero[0][2] == simboloActual && tablero[1][1] == simboloActual && tablero[2][0] == simboloActual) return "GANADOR";

        // Verificar si quedan espacios (continúa)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == ' ') {
                    return "CONTINUA";
                }
            }
        }
        return "EMPATE"; // No hay espacios y nadie ha ganado
    }
}