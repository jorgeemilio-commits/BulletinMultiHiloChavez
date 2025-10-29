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

    /**
     * Suma una cantidad de puntos al total del usuario.
     */
    public void registrarPuntos(String nombreUsuario, int puntosAAnadir) {
        String sql = "UPDATE usuarios SET puntos = puntos + ? WHERE nombre = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            System.err.println("Error de conexión al registrar puntos para " + nombreUsuario);
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, puntosAAnadir);
            pstmt.setString(2, nombreUsuario);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Error al registrar puntos para " + nombreUsuario + ": " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Registra el resultado de una partida en la tabla 'partidas'.
     */
    public void registrarPartida(String jugadorX, String jugadorO, String ganador, String perdedor, String resultado) {
        String sql = "INSERT INTO partidas (jugadorX_nombre, jugadorO_nombre, ganador_nombre, perdedor_nombre, resultado) VALUES (?, ?, ?, ?, ?)";
        Connection conn = ConexionDB.conectar();
        
        if (conn == null) {
            System.err.println("Error de conexión al registrar la partida.");
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jugadorX);
            pstmt.setString(2, jugadorO);

            // Manejar NULLs en caso de empate
            if (ganador != null) {
                pstmt.setString(3, ganador);
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }
            
            if (perdedor != null) {
                pstmt.setString(4, perdedor);
            } else {
                pstmt.setNull(4, java.sql.Types.VARCHAR);
            }
            
            pstmt.setString(5, resultado);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al registrar partida: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
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