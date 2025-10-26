package com.servidormulti;

import java.io.IOException;

// NUEVA CLASE
public class JuegoGato {

    private final UnCliente playerX;
    private final UnCliente playerO;
    private UnCliente jugadorActual;
    private final char[][] tablero;

    public JuegoGato(UnCliente retador, UnCliente aceptador) {
        this.playerX = retador; // el retador es X
        this.playerO = aceptador; // el que acepta es O
        this.jugadorActual = playerX; // empieza el X
        this.tablero = new char[3][3];
        inicializarTablero();
    }

    private void inicializarTablero() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tablero[i][j] = ' ';
            }
        }
    }

    // Envía un mensaje a ambos jugadores
    private void enviarMensajeAmbos(String mensaje) throws IOException {
        playerX.salida.writeUTF(mensaje);
        playerO.salida.writeUTF(mensaje);
    }
    
    // Envía el tablero a ambos jugadores
    private void dibujarTablero() throws IOException {
        enviarMensajeAmbos("\nTablero de Gato:");
        // Añadir coordenadas de columnas (arriba)
        enviarMensajeAmbos("    1   2   3");
        for (int i = 0; i < 3; i++) {
            // Añadir coordenada de fila (izquierda) + contenido
            String fila = " " + (i + 1) + " | " + tablero[i][0] + " | " + tablero[i][1] + " | " + tablero[i][2];
            enviarMensajeAmbos(fila);
            if (i < 2) {
                enviarMensajeAmbos("   ---|---|---");
            }
        }
        enviarMensajeAmbos("\nTurno de: " + jugadorActual.getNombreUsuario() + " (" + (jugadorActual == playerX ? 'X' : 'O') + ")");
        // Mensaje de ayuda actualizado a 1-based
        enviarMensajeAmbos("Usa Fila,Columna (ej: 1,1 o 2,3). Escribe /salirjuego para abandonar.");
    }

    public void iniciarJuego() throws IOException {
        enviarMensajeAmbos("¡Juego iniciado! " + playerX.getNombreUsuario() + " (X) vs " + playerO.getNombreUsuario() + " (O).");
        dibujarTablero();
    }
    
    private void cambiarTurno() {
        jugadorActual = (jugadorActual == playerX) ? playerO : playerX;
    }

    // Termina el juego y saca a los jugadores del estado "en juego"
    public void terminarJuego(UnCliente jugadorAbandona, String razon) {
        try {
            enviarMensajeAmbos("--- Juego Terminado ---");
            if (jugadorAbandona != null) {
                enviarMensajeAmbos(razon);
            }
        } catch (IOException e) {
            System.err.println("Error al notificar fin de juego: " + e.getMessage());
        } finally {
            // Limpia el estado del juego en ambos clientes
            playerX.setJuegoActual(null);
            playerO.setJuegoActual(null);
        }
    }

    // Verifica si el movimiento es válido
    // (Esta función no cambia, ya que recibe la fila/col en 0-based)
    private boolean esMovimientoValido(int fila, int col) {
        return fila >= 0 && fila < 3 && col >= 0 && col < 3 && tablero[fila][col] == ' ';
    }
    
    // Comprueba si hay un ganador o empate
    private String verificarEstadoJuego() {
        char simbolo = (jugadorActual == playerX) ? 'X' : 'O';

        // Verificar filas y columnas
        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo) return "GANADOR";
            if (tablero[0][i] == simbolo && tablero[1][i] == simbolo && tablero[2][i] == simbolo) return "GANADOR";
        }
        // Verificar diagonales
        if (tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo) return "GANADOR";
        if (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo) return "GANADOR";

        // Verificar empate (tablero lleno)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == ' ') {
                    return "CONTINUA"; // Todavía hay espacios
                }
            }
        }
        return "EMPATE"; // No hay espacios y no hay ganador
    }
    

    // Procesa el input del jugador actual
    public void manejarJugada(UnCliente jugador, String input) throws IOException {
        if (jugador != jugadorActual) {
            jugador.salida.writeUTF("¡Espera! No es tu turno.");
            return;
        }

        String[] coords = input.trim().split(",");
        if (coords.length != 2) {
            // Mensaje de error actualizado
            jugador.salida.writeUTF("Formato incorrecto. Usa Fila,Columna (ej: 1,2)");
            return;
        }

        try {
            // Convertir de 1-based (input usuario) a 0-based (índice array)
            int fila = Integer.parseInt(coords[0]) - 1;
            int col = Integer.parseInt(coords[1]) - 1;

            if (esMovimientoValido(fila, col)) {
                char simbolo = (jugadorActual == playerX) ? 'X' : 'O';
                tablero[fila][col] = simbolo;
                
                String estado = verificarEstadoJuego();

                if (estado.equals("GANADOR")) {
                    dibujarTablero(); // Muestra el tablero final
                    enviarMensajeAmbos("¡Felicidades " + jugadorActual.getNombreUsuario() + "! Has ganado.");
                    terminarJuego(null, ""); // Termina el juego
                } else if (estado.equals("EMPATE")) {
                    dibujarTablero(); // Muestra el tablero final
                    enviarMensajeAmbos("¡Es un empate!");
                    terminarJuego(null, ""); // Termina el juego
                } else {
                    cambiarTurno();
                    dibujarTablero(); // Muestra el tablero actualizado y el siguiente turno
                }
                
            } else {
                // Mensaje de error actualizado
                jugador.salida.writeUTF("Movimiento inválido. Esa casilla ya está ocupada o fuera del tablero (Usa 1, 2 o 3).");
            }
        } catch (NumberFormatException e) {
            // Mensaje de error actualizado
            jugador.salida.writeUTF("Input inválido. Debes enviar números (ej: 1,2).");
        }
    }
}