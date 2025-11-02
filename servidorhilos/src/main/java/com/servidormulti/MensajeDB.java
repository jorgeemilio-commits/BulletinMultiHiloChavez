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
     * Guarda un mensaje de grupo en la BD y retorna su nuevo ID.
     */
    public long guardarMensajeGrupo(int grupoId, String remitente, String contenido) {
        String sql = "INSERT INTO grupos_mensajes (grupo_id, remitente_nombre, contenido, timestamp) VALUES (?, ?, ?, ?)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return -1;

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, grupoId);
            pstmt.setString(2, remitente);
            pstmt.setString(3, contenido);
            pstmt.setLong(4, System.currentTimeMillis());
            
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1); // Retorna el ID del mensaje recién creado
            }
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return -1;
    }

    /**
     * Guarda un mensaje privado en la BD y retorna su nuevo ID.
     */
    public long guardarMensajePrivado(String remitente, String destinatario, String contenido) {
        String sql = "INSERT INTO mensajes_privados (remitente_nombre, destinatario_nombre, contenido, timestamp, visto) VALUES (?, ?, ?, ?, 0)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return -1;

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, remitente);
            pstmt.setString(2, destinatario);
            pstmt.setString(3, contenido);
            pstmt.setLong(4, System.currentTimeMillis());
            
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1); // Retorna el ID del mensaje
            }
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje privado: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return -1;
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