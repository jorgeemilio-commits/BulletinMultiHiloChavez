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
     * NUEVO: Se llama al unirse a un grupo. Descarga TODO el historial.
     */
    public void sincronizarHistorialGrupo(UnCliente cliente, String nombreGrupo) throws IOException {
        cliente.salida.writeUTF("--- Sincronizando historial de '" + nombreGrupo + "'... ---");
        // Llamamos al método existente empezando desde el mensaje ID 0
        descargarMensajesGrupo(cliente, nombreGrupo, 0);
        cliente.salida.writeUTF("--- Historial de '" + nombreGrupo + "' sincronizado. ---");
    }

    /**
     * Sincroniza todos los mensajes de GRUPO no leídos.
     */
    private void sincronizarGrupos(UnCliente cliente) throws IOException {
        String nombreUsuario = cliente.getNombreUsuario();
        
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
            ResultSet rsMsj = pstmt.executeQuery();
            
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
     * Sincroniza todos los mensajes PRIVADOS no leídos.
     */

    private void sincronizarPrivados(UnCliente cliente) throws IOException {
        String nombreUsuario = cliente.getNombreUsuario();
        String sqlSelect = "SELECT id, remitente_nombre, contenido FROM mensajes_privados " +
                           "WHERE destinatario_nombre = ? AND visto = 0 ORDER BY timestamp ASC";
        String sqlUpdate = "UPDATE mensajes_privados SET visto = 1 WHERE id = ?";

        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            System.err.println("Error de conexión al sincronizar privados.");
        return;
        }

         try (PreparedStatement selectPstmt = conn.prepareStatement(sqlSelect);
             PreparedStatement updatePstmt = conn.prepareStatement(sqlUpdate)) {
        
             conn.setAutoCommit(false); 
             
             // 1. Iniciar transacción
             selectPstmt.setString(1, nombreUsuario);

            // 2. Leer los mensajes no leídos
            try (ResultSet rs = selectPstmt.executeQuery()) {
            
            // 3. Recorrer los mensajes
                while (rs.next()) {
                    long msgId = rs.getLong("id");
                    String remitente = rs.getString("remitente_nombre");
                    String contenido = rs.getString("contenido");
                
                    String msgFormateado = String.format("[Privado de %s]: %s", remitente, contenido);

                // 3a. Enviarlos al cliente
                cliente.salida.writeUTF(msgFormateado);
                
                // 3b. Declarar el mensaje como visto
                updatePstmt.setLong(1, msgId);
                updatePstmt.addBatch();
            }
        }
        
        // 4. Ejecuta todas las actualizaciones de una vez
        updatePstmt.executeBatch();
        
        // 5. Confirmar la transacción
        conn.commit(); 

    } catch (SQLException | IOException e) { // Capturamos cualquier error 
        System.err.println("Error al sincronizar privados, revirtiendo: " + e.getMessage());
        try {
            conn.rollback(); // Si algo falla, revertimos todo
        } catch (SQLException ex) {
            System.err.println("Error al hacer rollback: " + ex.getMessage());
        }
    } finally {
        // Restauramos el estado de la conexión y la cerramos
        try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
        ConexionDB.cerrarConexion(conn);
    }
  }
}