package com.servidormulti;

import java.io.IOException;

public class JuegoGato {

    private final UnCliente playerX;
    private final UnCliente playerO;
    private UnCliente jugadorActual;
    private final char[][] tablero;
    private final String juegoID; // Nuevo campo

    public JuegoGato(UnCliente retador, UnCliente aceptador, String id) { // ID añadido al constructor
        this.playerX = retador; // el retador es X
        this.playerO = aceptador; // el que acepta es O
        this.jugadorActual = playerX; // empieza el X
        this.tablero = new char[3][3];
        this.juegoID = id; // Asignación del ID
        inicializarTablero();
    }

    private void inicializarTablero() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tablero[i][j] = ' ';
            }
        }
    }

    private void enviarMensajeAmbos(String mensaje) throws IOException {
        playerX.salida.writeUTF(mensaje);
        playerO.salida.writeUTF(mensaje);
    }

    private void dibujarTablero() throws IOException {
        // Muestra el ID del juego
        playerX.salida.writeUTF("\n--- PARTIDA DE GATO [ID: " + juegoID + "] contra " + playerO.getNombreUsuario() + " (Tú eres X) ---");
        playerO.salida.writeUTF("\n--- PARTIDA DE GATO [ID: " + juegoID + "] contra " + playerX.getNombreUsuario() + " (Tú eres O) ---");
        
        enviarMensajeAmbos("    1   2   3");
        for (int i = 0; i < 3; i++) {
            String fila = (i + 1) + " | " + tablero[i][0] + " | " + tablero[i][1] + " | " + tablero[i][2];
            enviarMensajeAmbos(fila);
            if (i < 2) {
                enviarMensajeAmbos("  ---|---|---");
            }
        }
        
        enviarMensajeAmbos("\nTurno de: " + jugadorActual.getNombreUsuario() + " (" + (jugadorActual == playerX ? 'X' : 'O') + ")");
        
        // Instrucciones modificadas para usar el ID del juego
        jugadorActual.salida.writeUTF("Usa: /jugada " + juegoID + " Fila,Columna (ej: /jugada " + juegoID + " 1,1).");
        jugadorActual.salida.writeUTF("Para abandonar: /salirjuego " + juegoID);
        
        UnCliente esperando = (jugadorActual == playerX) ? playerO : playerX;
        esperando.salida.writeUTF("Esperando jugada de " + jugadorActual.getNombreUsuario() + ". Para abandonar: /salirjuego " + juegoID);
    }

    public void iniciarJuego() throws IOException {
        enviarMensajeAmbos("¡Juego iniciado! " + playerX.getNombreUsuario() + " (X) vs " + playerO.getNombreUsuario() + " (O).");
        dibujarTablero();
    }

    private void cambiarTurno() {
        jugadorActual = (jugadorActual == playerX) ? playerO : playerX;
    }

    public void terminarJuego(UnCliente jugadorAbandona, String razon) {
        try {
            enviarMensajeAmbos("--- Juego Terminado ---");
            if (jugadorAbandona != null) {
                enviarMensajeAmbos(razon);
                // Si alguien abandona, el otro gana
                UnCliente ganador = (jugadorAbandona == playerX) ? playerO : playerX;
                registrarResultado(ganador, jugadorAbandona, "ABANDONO");
            }
        } catch (IOException e) {
            System.err.println("Error al notificar fin de juego: " + e.getMessage());
        } finally {
            // Elimina el juego del mapa de juegos activos en ambos clientes (lógica para múltiples juegos)
            playerX.removerJuego(this.juegoID); 
            playerO.removerJuego(this.juegoID);
        }
    }
    
    // NUEVO MÉTODO PARA REGISTRAR VICTORIAS/DERROTAS
    private void registrarResultado(UnCliente ganador, UnCliente perdedor, String tipoResultado) {
        // Solo registra si están logueados
        if (ganador.estaLogueado()) {
            new EstadisticasDB().registrarVictoria(ganador.getNombreUsuario());
        }
        if (perdedor.estaLogueado()) {
            new EstadisticasDB().registrarDerrota(perdedor.getNombreUsuario());
        }
        // No se registran empates por simplicidad, solo victorias/derrotas.
    }

    private boolean esMovimientoValido(int fila, int col) {
        return fila >= 1 && fila <= 3 && col >= 1 && col <= 3 && tablero[fila - 1][col - 1] == ' ';
    }

    private String verificarEstadoJuego() {
        char simbolo = (jugadorActual == playerX) ? 'X' : 'O';

        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo) return "GANADOR";
            if (tablero[0][i] == simbolo && tablero[1][i] == simbolo && tablero[2][i] == simbolo) return "GANADOR";
        }
        if (tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo) return "GANADOR";
        if (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo) return "GANADOR";

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == ' ') {
                    return "CONTINUA";
                }
            }
        }
        return "EMPATE";
    }

    public void manejarJugada(UnCliente jugador, String input) throws IOException {
        if (jugador != jugadorActual) {
            jugador.salida.writeUTF("¡Espera! No es tu turno.");
            return;
        }

        String[] coords = input.trim().split(",");
        if (coords.length != 2) {
            jugador.salida.writeUTF("Formato incorrecto. Usa Fila,Columna (ej: 1,2) después del comando /jugada " + juegoID + ".");
            return;
        }

        try {
            int fila = Integer.parseInt(coords[0]);
            int col = Integer.parseInt(coords[1]);

            if (esMovimientoValido(fila, col)) {
                char simbolo = (jugadorActual == playerX) ? 'X' : 'O';
                tablero[fila - 1][col - 1] = simbolo;

                String estado = verificarEstadoJuego();

                if (estado.equals("GANADOR")) {
                    dibujarTablero();

                    UnCliente perdedor = (jugadorActual == playerX) ? playerO : playerX;
                    jugadorActual.salida.writeUTF("🎉 ¡Felicidades " + jugadorActual.getNombreUsuario() + "! Has ganado contra " + perdedor.getNombreUsuario() + ". 🎉");
                    perdedor.salida.writeUTF("😞 Has perdido contra " + jugadorActual.getNombreUsuario() + ".");

                    registrarResultado(jugadorActual, perdedor, "GANADOR");
                    terminarJuego(null, "Ganador: " + jugadorActual.getNombreUsuario());

                } else if (estado.equals("EMPATE")) {
                    dibujarTablero();
                    enviarMensajeAmbos("🤝 ¡Empate! Buen juego entre " + playerX.getNombreUsuario() + " y " + playerO.getNombreUsuario() + ".");
                    
                    terminarJuego(null, "Resultado: Empate");

                } else {
                    cambiarTurno();
                    dibujarTablero();
                }

            } else {
                jugador.salida.writeUTF("Movimiento inválido. Esa casilla ya está ocupada o fuera del tablero (usa 1–3).");
            }
        } catch (NumberFormatException e) {
            jugador.salida.writeUTF("Input inválido. Debes enviar números (ej: 1,2).");
        }
    }
}