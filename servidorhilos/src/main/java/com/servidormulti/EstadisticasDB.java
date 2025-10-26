package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EstadisticasDB {
    
    /**
     * Incrementa las victorias de un usuario por 1.
     */
    public void registrarVictoria(String nombreUsuario) {
        String sql = "UPDATE usuarios SET victorias = victorias + 1 WHERE nombre = ?";
        actualizarEstadistica(nombreUsuario, sql, "victoria");
    }

    /**
     * Incrementa las derrotas de un usuario por 1.
     */
    public void registrarDerrota(String nombreUsuario) {
        String sql = "UPDATE usuarios SET derrotas = derrotas + 1 WHERE nombre = ?";
        actualizarEstadistica(nombreUsuario, sql, "derrota");
    }

    private void actualizarEstadistica(String nombreUsuario, String sql, String tipo) {
        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            System.err.println("Error de conexión al registrar " + tipo + " para " + nombreUsuario);
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreUsuario);
            int filasAfectadas = pstmt.executeUpdate();
            
            if (filasAfectadas == 0) {
                 System.err.println("Advertencia: No se encontró al usuario " + nombreUsuario + " para registrar " + tipo + ".");
            }
        } catch (SQLException e) {
            System.err.println("Error al registrar " + tipo + " para " + nombreUsuario + ": " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}
