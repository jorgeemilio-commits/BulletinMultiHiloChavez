package com.servidormulti;

import java.io.IOException;

public class JuegoGato {

    private final UnCliente playerX;
    private final UnCliente playerO;
    private UnCliente jugadorActual;
    private final TableroGato tableroJuego; // Uso de la nueva clase TableroGato
    private final String juegoID; 
    private final EstadisticasDB statsDB = new EstadisticasDB(); // Reutiliza una instancia

    // --- CONSTANTES DE PUNTUACIÓN ---
    private static final int PUNTOS_VICTORIA = 2;
    private static final int PUNTOS_EMPATE = 1;
    private static final int PUNTOS_DERROTA = 0;


    public JuegoGato(UnCliente retador, UnCliente aceptador, String id) {
        this.playerX = retador; // el retador es X
        this.playerO = aceptador; // el que acepta es O
        this.jugadorActual = playerX; // empieza el X
        this.tableroJuego = new TableroGato(); // Inicializa el tablero
        this.juegoID = id; 
    }

    private void enviarMensajeAmbos(String mensaje) throws IOException {
        playerX.salida.writeUTF(mensaje);
        playerO.salida.writeUTF(mensaje);
    }

    /**
     * Responsabilidad: Dibuja el estado actual del Tablero de Juego a ambos clientes.
     */
    private void dibujarTablero() throws IOException {
        char simboloActual = tableroJuego.getSimboloActual();
        
        // Encabezado (ID y Oponente)
        playerX.salida.writeUTF("\n--- PARTIDA DE GATO [ID: " + juegoID + "] contra " + playerO.getNombreUsuario() + " (Tú eres X) ---");
        playerO.salida.writeUTF("\n--- PARTIDA DE GATO [ID: " + juegoID + "] contra " + playerX.getNombreUsuario() + " (Tú eres O) ---");
        
        // Cuerpo del Tablero
        enviarMensajeAmbos("    1   2   3");
        char[][] tablero = tableroJuego.getTablero();
        for (int i = 0; i < 3; i++) {
            String fila = (i + 1) + " | " + tablero[i][0] + " | " + tablero[i][1] + " | " + tablero[i][2];
            enviarMensajeAmbos(fila);
            if (i < 2) {
                enviarMensajeAmbos("  --------------");
            }
        }
        
        // Pie de página (Instrucciones)
        enviarMensajeAmbos("\nTurno de: " + jugadorActual.getNombreUsuario() + " (" + simboloActual + ")");
        
        jugadorActual.salida.writeUTF("Usa: /jugada " + juegoID + " Fila,Columna (ej: /jugada " + juegoID + "(ID)" + " 1,1).");
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
        tableroJuego.setSiguienteSimbolo(); // Mueve el símbolo de juego en el Tablero
    }

    /**
     * Responsabilidad: Terminar el juego, notificar a los clientes y registrar resultados.
     */
    public void terminarJuego(UnCliente jugadorAbandona, String razon) {
        try {
            enviarMensajeAmbos("--- Juego Terminado ---");
            if (jugadorAbandona != null) {
                enviarMensajeAmbos(razon);
                // Si alguien abandona, el otro gana
                UnCliente ganador = (jugadorAbandona == playerX) ? playerO : playerX;
                
                // MODIFICADO: Registrar resultado de abandono
                registrarResultado(ganador, jugadorAbandona, "ABANDONO");
            }
        } catch (IOException e) {
            System.err.println("Error al notificar fin de juego: " + e.getMessage());
        } finally {
            // Elimina el juego del mapa de juegos activos en ambos clientes
            playerX.removerJuego(this.juegoID); 
            playerO.removerJuego(this.juegoID);
        }
    }
    
    /**
     * MODIFICADO: Registra el resultado (puntos y partida) en la base de datos.
     * @param ganador El cliente ganador (null si es empate).
     * @param perdedor El cliente perdedor (null si es empate).
     * @param tipoResultado "VICTORIA", "EMPATE", o "ABANDONO".
     */
    private void registrarResultado(UnCliente ganador, UnCliente perdedor, String tipoResultado) {
        
        String nombreGanador = (ganador != null) ? ganador.getNombreUsuario() : null;
        String nombrePerdedor = (perdedor != null) ? perdedor.getNombreUsuario() : null;

        // 1. Registrar Puntos y Estadísticas (Victorias/Derrotas)
        if (tipoResultado.equals("VICTORIA") || tipoResultado.equals("ABANDONO")) {
            
            if (ganador != null && ganador.estaLogueado()) {
                statsDB.registrarVictoria(nombreGanador);
                statsDB.registrarPuntos(nombreGanador, PUNTOS_VICTORIA);
            }
            if (perdedor != null && perdedor.estaLogueado()) {
                statsDB.registrarDerrota(nombrePerdedor);
                statsDB.registrarPuntos(nombrePerdedor, PUNTOS_DERROTA);
            }

        } else if (tipoResultado.equals("EMPATE")) {
            
            // Ambos jugadores (X y O) reciben puntos por empate
            if (playerX.estaLogueado()) {
                statsDB.registrarPuntos(playerX.getNombreUsuario(), PUNTOS_EMPATE);
            }
            if (playerO.estaLogueado()) {
                statsDB.registrarPuntos(playerO.getNombreUsuario(), PUNTOS_EMPATE);
            }
        }
        
        // 2. Registrar la Partida en la tabla 'partidas'
        // Solo registrar si ambos están logueados (para estadísticas H2H)
        if (playerX.estaLogueado() && playerO.estaLogueado()) {
            statsDB.registrarPartida(
                playerX.getNombreUsuario(),
                playerO.getNombreUsuario(),
                nombreGanador,
                nombrePerdedor,
                tipoResultado
            );
        }
    }

    /**
     * Responsabilidad: Recibir y procesar la entrada de un cliente.
     */
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

            // Delegar el movimiento y la lógica de validación a TableroGato
            if (tableroJuego.hacerMovimiento(fila, col)) {
                
                String estado = tableroJuego.verificarEstadoJuego();

                if (estado.equals("GANADOR")) {
                    dibujarTablero(); // Dibuja el tablero final

                    UnCliente perdedor = (jugadorActual == playerX) ? playerO : playerX;
                    jugadorActual.salida.writeUTF("¡Felicidades " + jugadorActual.getNombreUsuario() + "! Has ganado contra " + perdedor.getNombreUsuario() + ". 🎉");
                    perdedor.salida.writeUTF(" Has perdido contra " + jugadorActual.getNombreUsuario() + ".");

                    // MODIFICADO: Registrar resultado de victoria
                    registrarResultado(jugadorActual, perdedor, "VICTORIA");
                    terminarJuego(null, "Ganador: " + jugadorActual.getNombreUsuario());

                } else if (estado.equals("EMPATE")) {
                    dibujarTablero();
                    enviarMensajeAmbos("¡Empate! Buen juego entre " + playerX.getNombreUsuario() + " y " + playerO.getNombreUsuario() + ".");
                    
                    // MODIFICADO: Registrar resultado de empate
                    registrarResultado(null, null, "EMPATE");
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