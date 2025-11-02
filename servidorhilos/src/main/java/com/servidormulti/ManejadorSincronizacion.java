package com.servidormulti;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ManejadorSincronizacion {

    private final MensajeDB mensajeDB;
    
    public ManejadorSincronizacion(MensajeDB mensajeDB) {
        this.mensajeDB = mensajeDB;
    }

    /**
     * Se llama al hacer login. Descarga todos los mensajes no leídos.
     */
    public void sincronizarMensajesPendientes(UnCliente cliente) throws IOException {
        cliente.salida.writeUTF("--- Sincronizando mensajes no leídos... ---");
        
        sincronizarGrupos(cliente);
        sincronizarPrivados(cliente);
        
        cliente.salida.writeUTF("--- Sincronización completada. ---");
    }

    /**
     * Sincroniza todos los mensajes de GRUPO no leídos.
     */
    private void sincronizarGrupos(UnCliente cliente) throws IOException {
        String nombreUsuario = cliente.getNombreUsuario();
        
        // Esta SQL compleja obtiene:
        // 1. El nombre del grupo.
        // 2. El último ID que el usuario vio.
        // 3. El ID del último mensaje enviado a ese grupo.
        // 4. Une solo los grupos donde el usuario es miembro.
        String sql = "SELECT g.nombre, COALESCE(e.ultimo_mensaje_id_visto, 0) AS ultimo_visto, " +
                     "(SELECT MAX(m.id) FROM grupos_mensajes m WHERE m.grupo_id = g.id) AS ultimo_existente " +
                     "FROM grupos g " +
                     "JOIN grupos_miembros gm ON g.id = gm.grupo_id " +
                     "LEFT JOIN grupos_estado_usuario e ON g.id = e.grupo_id AND e.usuario_nombre = ? " +
                     "WHERE gm.usuario_nombre = ?";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreUsuario);
            pstmt.setString(2, nombreUsuario);
            ResultSet rsGrupos = pstmt.executeQuery();

            while (rsGrupos.next()) {
                String nombreGrupo = rsGrupos.getString("nombre");
                long ultimoVisto = rsGrupos.getLong("ultimo_visto");
                long ultimoExistente = rsGrupos.getLong("ultimo_existente");

                if (ultimoVisto < ultimoExistente) {
                    // Hay mensajes nuevos en este grupo
                    descargarMensajesGrupo(cliente, nombreGrupo, ultimoVisto);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al sincronizar grupos: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Descarga y envía los mensajes de un grupo específico.
     */
    private void descargarMensajesGrupo(UnCliente cliente, String nombreGrupo, long ultimoIdVisto) throws IOException {
        String sql = "SELECT id, remitente_nombre, contenido FROM grupos_mensajes " +
                     "WHERE grupo_id = (SELECT id FROM grupos WHERE nombre = ?) AND id > ? " +
                     "ORDER BY timestamp ASC";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        long ultimoIdEnviado = ultimoIdVisto;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            pstmt.setLong(2, ultimoIdVisto);
            ResultSet rsMsj = pstmt.executeQuery(); //rsMsj Resultado Mensajes
            
            while (rsMsj.next()) {
                long msgId = rsMsj.getLong("id");
                String remitente = rsMsj.getString("remitente_nombre");
                String contenido = rsMsj.getString("contenido");
                
                String msgFormateado = String.format("<%s> %s: %s", nombreGrupo, remitente, contenido);
                cliente.salida.writeUTF(msgFormateado);
                
                ultimoIdEnviado = msgId;
            }
            
            // Actualizar el estado del usuario para este grupo
            if (ultimoIdEnviado > ultimoIdVisto) {
                Integer grupoId = new GrupoDB().getGrupoId(nombreGrupo);
                if (grupoId != null) {
                    mensajeDB.actualizarEstadoGrupo(cliente.getNombreUsuario(), grupoId, ultimoIdEnviado);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al descargar mensajes de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Sincroniza todos los mensajes privados no leídos.
     */
    private void sincronizarPrivados(UnCliente cliente) throws IOException {
        String nombreUsuario = cliente.getNombreUsuario();
        String sql = "SELECT id, remitente_nombre, contenido FROM mensajes_privados " +
                     "WHERE destinatario_nombre = ? AND visto = 0 " +
                     "ORDER BY timestamp ASC";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreUsuario);
            ResultSet rs = pstmt.executeQuery(); //rs Resultado
            
            while (rs.next()) {
                long msgId = rs.getLong("id");
                String remitente = rs.getString("remitente_nombre");
                String contenido = rs.getString("contenido");

                String msgFormateado = String.format("[Privado de %s]: %s", remitente, contenido);
                cliente.salida.writeUTF(msgFormateado);
                
                // Marcar como visto después de enviarlo
                mensajeDB.marcarPrivadoVisto(msgId);
            }
        } catch (SQLException e) {
            System.err.println("Error al sincronizar privados: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}