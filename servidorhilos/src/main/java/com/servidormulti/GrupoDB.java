package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GrupoDB {

    /**
     * Obtiene el ID numérico de un grupo a partir de su nombre.
     */
    public Integer getGrupoId(String nombreGrupo) {
        String sql = "SELECT id FROM grupos WHERE nombre = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return null;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener ID de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return null;
    }

    /**
     * Crea un nuevo grupo en la base de datos.
     */
    public String crearGrupo(String nombreGrupo) {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            return "Error: No se puede crear un grupo llamado 'Todos'.";
        }
        
        Integer id = getGrupoId(nombreGrupo);
        if (id != null) {
            return "Error: El grupo '" + nombreGrupo + "' ya existe.";
        }

        String sqlInsert = "INSERT INTO grupos (nombre) VALUES (?)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
            pstmt.setString(1, nombreGrupo);
            pstmt.executeUpdate();
            return "Grupo '" + nombreGrupo + "' creado exitosamente.";
        } catch (SQLException e) {
            System.err.println("Error al crear grupo: " + e.getMessage());
            return "Error interno al crear grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Borra un grupo de la base de datos.
     */
    public String borrarGrupo(String nombreGrupo) {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            return "Error: No se puede borrar el grupo 'Todos'.";
        }
        
        Integer id = getGrupoId(nombreGrupo);
        if (id == null) {
            return "Error: El grupo '" + nombreGrupo + "' no existe.";
        }

        // Gracias a 'ON DELETE CASCADE' en ConexionDB, al borrar el grupo,
        // se borrarán automáticamente sus miembros, mensajes e historial de estado.
        String sqlDelete = "DELETE FROM grupos WHERE id = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            return "Grupo '" + nombreGrupo + "' borrado exitosamente.";
        } catch (SQLException e) {
            System.err.println("Error al borrar grupo: " + e.getMessage());
            return "Error interno al borrar grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Une un usuario a un grupo.
     */
    public String unirseGrupo(String nombreGrupo, String nombreUsuario) {
        Integer grupoId = getGrupoId(nombreGrupo);
        if (grupoId == null) {
            return "Error: El grupo '" + nombreGrupo + "' no existe.";
        }
        
        String sqlInsert = "INSERT OR IGNORE INTO grupos_miembros (grupo_id, usuario_nombre) VALUES (?, ?)";
        String sqlEstado = "INSERT OR IGNORE INTO grupos_estado_usuario (usuario_nombre, grupo_id, ultimo_mensaje_id_visto) VALUES (?, ?, 0)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert);
             PreparedStatement pstmtEstado = conn.prepareStatement(sqlEstado)) {
            
            // Unir al grupo
            pstmt.setInt(1, grupoId);
            pstmt.setString(2, nombreUsuario);
            int filasAfectadas = pstmt.executeUpdate();

            // Checar estado (para mensajes no leídos)
            pstmtEstado.setString(1, nombreUsuario);
            pstmtEstado.setInt(2, grupoId);
            pstmtEstado.executeUpdate();

            if (filasAfectadas > 0) {
                return "Te has unido al grupo '" + nombreGrupo + "'.";
            } else {
                return "Ya eras miembro del grupo '" + nombreGrupo + "'.";
            }
        } catch (SQLException e) {
            System.err.println("Error al unirse a grupo: " + e.getMessage());
            return "Error interno al unirse a grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Obtiene una lista de todos los miembros de un grupo.
     */
    public List<String> getMiembrosGrupo(int grupoId) {
        List<String> miembros = new ArrayList<>();
        String sql = "SELECT usuario_nombre FROM grupos_miembros WHERE grupo_id = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return miembros;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                miembros.add(rs.getString("usuario_nombre"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener miembros de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return miembros;
    }
}