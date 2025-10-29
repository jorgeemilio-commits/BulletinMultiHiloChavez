package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;

public class ManejadorWinrate {

    /**
     * Maneja el comando /winrate jugador1 jugador2.
     * Muestra las estadísticas H2H (Head-to-Head) entre dos jugadores.
     */
    public void ejecutar(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        String[] partes = comando.trim().split(" ");
        
        String jugador1;
        String jugador2;

        if (partes.length == 3) {
            jugador1 = partes[1];
            jugador2 = partes[2];
        } else if (partes.length == 2) {
            // Si solo da un nombre, compara contra sí mismo (el emisor)
            jugador1 = cliente.getNombreUsuario();
            jugador2 = partes[1];
        } else {
            salida.writeUTF("Uso: /winrate [TuNombre] NombreOponente");
            return;
        }
        
        if (jugador1.equalsIgnoreCase(jugador2)) {
            salida.writeUTF("No puedes compararte contigo mismo.");
            return;
        }

        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            salida.writeUTF("Error de conexión a la base de datos.");
            return;
        }
        
        try {
            // 1. Victorias de Jugador 1 sobre Jugador 2
            int victoriasJ1 = contarVictorias(conn, jugador1, jugador2);
            
            // 2. Victorias de Jugador 2 sobre Jugador 1
            int victoriasJ2 = contarVictorias(conn, jugador2, jugador1);
            
            // 3. Empates
            int empates = contarEmpates(conn, jugador1, jugador2);

            int totalPartidas = victoriasJ1 + victoriasJ2 + empates;

            if (totalPartidas == 0) {
                salida.writeUTF("No hay historial de partidas registradas entre '" + jugador1 + "' y '" + jugador2 + "'.");
                return;
            }

            // Calcular porcentajes
            DecimalFormat df = new DecimalFormat("0.0#");
            double porcJ1 = (double) victoriasJ1 / totalPartidas * 100.0;
            double porcJ2 = (double) victoriasJ2 / totalPartidas * 100.0;
            double porcEmpate = (double) empates / totalPartidas * 100.0;
            
            // Enviar resultado
            String resultado = String.format(
                "--- H2H: %s vs %s ---\n" +
                "Total Partidas: %d\n" +
                "Victorias %s: %d (%s%%)\n" +
                "Victorias %s: %d (%s%%)\n" +
                "Empates: %d (%s%%)",
                jugador1, jugador2,
                totalPartidas,
                jugador1, victoriasJ1, df.format(porcJ1),
                jugador2, victoriasJ2, df.format(porcJ2),
                empates, df.format(porcEmpate)
            );
            salida.writeUTF(resultado);

        } catch (SQLException e) {
            System.err.println("Error al calcular winrate: " + e.getMessage());
            salida.writeUTF("Error interno al calcular estadísticas.");
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    // Métodos auxiliares para /winrate
    
    private int contarVictorias(Connection conn, String ganador, String perdedor) throws SQLException {
        // Cuenta cuando J1 fue X y J2 fue O, O viceversa.
        String sql = "SELECT COUNT(*) FROM partidas " +
                     "WHERE (jugadorX_nombre = ? AND jugadorO_nombre = ? AND ganador_nombre = ?) " +
                     "OR (jugadorX_nombre = ? AND jugadorO_nombre = ? AND ganador_nombre = ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // (J1=X, J2=O, Gana=J1)
            pstmt.setString(1, ganador);
            pstmt.setString(2, perdedor);
            pstmt.setString(3, ganador);
            // (J1=O, J2=X, Gana=J1)
            pstmt.setString(4, perdedor);
            pstmt.setString(5, ganador);
            pstmt.setString(6, ganador);
            
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int contarEmpates(Connection conn, String j1, String j2) throws SQLException {
        String sql = "SELECT COUNT(*) FROM partidas " +
                     "WHERE resultado = 'EMPATE' " +
                     "AND ((jugadorX_nombre = ? AND jugadorO_nombre = ?) OR (jugadorX_nombre = ? AND jugadorO_nombre = ?))";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, j1);
            pstmt.setString(2, j2);
            pstmt.setString(3, j2);
            pstmt.setString(4, j1);
            
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}