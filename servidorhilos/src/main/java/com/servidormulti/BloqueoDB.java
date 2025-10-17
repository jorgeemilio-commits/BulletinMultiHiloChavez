package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BloqueoDB {

    /**
     * Bloquea a un usuario. Retorna un mensaje con el resultado.
     */
    public String bloquearUsuario(String bloqueador_nombre, String bloqueado_nombre) {
        // INSERT OR IGNORE previene el error si el bloqueo ya existe.
        String sqlInsert = "INSERT OR IGNORE INTO bloqueos (bloqueador_nombre, bloqueado_nombre) VALUES (?, ?)";
        Connection conn = ConexionDB.conectar();

        if (conn == null) {
            return "Error de conexión a la base de datos.";
        }

        try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
            insertStmt.setString(1, bloqueador_nombre);
            insertStmt.setString(2, bloqueado_nombre);
            int filasAfectadas = insertStmt.executeUpdate();

            if (filasAfectadas > 0) {
                return "Usuario '" + bloqueado_nombre + "' bloqueado exitosamente.";
            } else {
                return "Usuario '" + bloqueado_nombre + "' ya estaba bloqueado.";
            }

        } catch (SQLException e) {
            System.err.println("Error al bloquear usuario: " + e.getMessage());
            return "Error interno al bloquear usuario.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Desbloquea a un usuario. Retorna un mensaje con el resultado.
     */
    public String desbloquearUsuario(String bloqueador_nombre, String bloqueado_nombre) {
        String sqlDelete = "DELETE FROM bloqueos WHERE bloqueador_nombre = ? AND bloqueado_nombre = ?";
        Connection conn = ConexionDB.conectar();

        if (conn == null) {
            return "Error de conexión a la base de datos.";
        }

        try (PreparedStatement deleteStmt = conn.prepareStatement(sqlDelete)) {
            deleteStmt.setString(1, bloqueador_nombre);
            deleteStmt.setString(2, bloqueado_nombre);
            int filasAfectadas = deleteStmt.executeUpdate();

            if (filasAfectadas > 0) {
                return "Usuario '" + bloqueado_nombre + "' desbloqueado exitosamente.";
            } else {
                return "Usuario '" + bloqueado_nombre + "' no estaba bloqueado.";
            }

        } catch (SQLException e) {
            System.err.println("Error al desbloquear usuario: " + e.getMessage());
            return "Error interno al desbloquear usuario.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Verifica si el bloqueador ha bloqueado al bloqueado.
     */
    public boolean estaBloqueado(String bloqueador_nombre, String bloqueado_nombre) {
        String sqlCheck = "SELECT 1 FROM bloqueos WHERE bloqueador_nombre = ? AND bloqueado_nombre = ?";
        Connection conn = ConexionDB.conectar();

        if (conn == null) {
            // Asumir que no hay bloqueo si hay error de conexión.
            return false;
        }

        try (PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, bloqueador_nombre);
            checkStmt.setString(2, bloqueado_nombre);
            
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("Error al verificar bloqueo: " + e.getMessage());
            return false;
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}