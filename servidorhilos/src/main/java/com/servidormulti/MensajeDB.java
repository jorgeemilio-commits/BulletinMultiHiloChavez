package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MensajeDB {

    /**
     * Guarda un mensaje de grupo y obtiene el ID usando last_insert_rowid().
     */
    public long guardarMensajeGrupo(int grupoId, String remitente, String contenido) {
        String sqlInsert = "INSERT INTO grupos_mensajes (grupo_id, remitente_nombre, contenido, timestamp) VALUES (?, ?, ?, ?)";
        String sqlGetId = "SELECT last_insert_rowid()";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return -1;

        PreparedStatement pstmt = null;
        Statement stmt = null;
        ResultSet rs = null;
        long nuevoId = -1;

        try {
            // 1. Desactivar Auto-commit para transacción
            conn.setAutoCommit(false);
            
            // 2. Ejecutar el INSERT
            pstmt = conn.prepareStatement(sqlInsert);
            pstmt.setInt(1, grupoId);
            pstmt.setString(2, remitente);
            pstmt.setString(3, contenido);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
            
            // 3. Obtener el ID recién insertado
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sqlGetId);
            if (rs.next()) {
                nuevoId = rs.getLong(1);
            }
            
            // 4. Confirmar transacción
            conn.commit();
            
            return nuevoId;

        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje de grupo: " + e.getMessage());
            // 5. Revertir en caso de error
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return -1;
        } finally {
            // 6. Cerrar todo manualmente
            try { if (rs != null) rs.close(); } catch (SQLException e) { /* ign */ }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { /* ign */ }
            try { if (pstmt != null) pstmt.close(); } catch (SQLException e) { /* ign */ }
            try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Guarda un mensaje privado y obtiene el ID usando last_insert_rowid().
     */
    public long guardarMensajePrivado(String remitente, String destinatario, String contenido) {
        String sqlInsert = "INSERT INTO mensajes_privados (remitente_nombre, destinatario_nombre, contenido, timestamp, visto) VALUES (?, ?, ?, ?, 0)";
        String sqlGetId = "SELECT last_insert_rowid()";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return -1;

        PreparedStatement pstmt = null;
        Statement stmt = null;
        ResultSet rs = null;
        long nuevoId = -1;

        try {
            // 1. Transacción
            conn.setAutoCommit(false);
            
            // 2. INSERT
            pstmt = conn.prepareStatement(sqlInsert);
            pstmt.setString(1, remitente);
            pstmt.setString(2, destinatario);
            pstmt.setString(3, contenido);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
            
            // 3. GET ID
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sqlGetId);
            if (rs.next()) {
                nuevoId = rs.getLong(1);
            }
            
            // 4. Commit
            conn.commit();
            return nuevoId;

        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje privado: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return -1;
        } finally {
            // 5. Cerrar todo
            try { if (rs != null) rs.close(); } catch (SQLException e) { /* ign */ }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { /* ign */ }
            try { if (pstmt != null) pstmt.close(); } catch (SQLException e) { /* ign */ }
            try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Actualiza el último mensaje que un usuario vio en un grupo.
     */
    public void actualizarEstadoGrupo(String usuario, int grupoId, long ultimoMsgIdVisto) {
        String sql = "INSERT OR REPLACE INTO grupos_estado_usuario (usuario_nombre, grupo_id, ultimo_mensaje_id_visto) VALUES (?, ?, ?)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.setInt(2, grupoId);
            pstmt.setLong(3, ultimoMsgIdVisto);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al actualizar estado de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Marca un mensaje privado como "visto" (visto = 1).
     */
    public void marcarPrivadoVisto(long mensajeId) {
        String sql = "UPDATE mensajes_privados SET visto = 1 WHERE id = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, mensajeId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al marcar mensaje privado como visto: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
    
    /**
     * Verifica si un usuario existe en la tabla 'usuarios'.
     */
    public boolean existeUsuario(String nombre) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE nombre = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Error al verificar existencia de usuario: " + e.getMessage());
            return false;
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}