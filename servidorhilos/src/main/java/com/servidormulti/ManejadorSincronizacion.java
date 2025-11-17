package com.servidormulti;

import java.io.DataOutputStream;
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
     * Se llama al unirse a un grupo. Descarga todo el historial del grupo.
     */
    public void sincronizarHistorialGrupo(UnCliente cliente, String nombreGrupo) throws IOException {
        cliente.salida.writeUTF("--- Sincronizando historial de '" + nombreGrupo + "'... ---");
        
        String sqlGetGrupoId = "SELECT id FROM grupos WHERE nombre = ?";
        String sqlUpdateStatus = "INSERT OR REPLACE INTO grupos_estado_usuario (usuario_nombre, grupo_id, ultimo_mensaje_id_visto) VALUES (?, ?, ?)";

        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            System.err.println("Error de conexión al sincronizar historial de grupo.");
            return;
        }

        try (PreparedStatement getGrupoIdPstmt = conn.prepareStatement(sqlGetGrupoId);
             PreparedStatement updateStatusPstmt = conn.prepareStatement(sqlUpdateStatus)) {
            
            conn.setAutoCommit(false); // 1. Iniciar transacción

            // 2. Obtener el ID del grupo
            getGrupoIdPstmt.setString(1, nombreGrupo);
            int grupoId = -1;
            try (ResultSet rs = getGrupoIdPstmt.executeQuery()) {
                if (rs.next()) {
                    grupoId = rs.getInt("id");
                }
            }

            if (grupoId == -1) {
                cliente.salida.writeUTF("Error: El grupo '" + nombreGrupo + "' no fue encontrado durante la sincronización.");
                conn.rollback();
                return;
            }

            // 3. Descargar mensajes
            long ultimoIdEnviado = descargarMensajes(conn, cliente.salida, grupoId, nombreGrupo, 0);

            // 4. Actualizar el estado
            if (ultimoIdEnviado > 0) {
                updateStatusPstmt.setString(1, cliente.getNombreUsuario());
                updateStatusPstmt.setInt(2, grupoId);
                updateStatusPstmt.setLong(3, ultimoIdEnviado);
                updateStatusPstmt.executeUpdate();
            }

            // 5. Confirmar transacción
            conn.commit();
            cliente.salida.writeUTF("--- Historial de '" + nombreGrupo + "' sincronizado. ---");

        } catch (SQLException | IOException e) {
            System.err.println("Error al sincronizar historial de grupo, revirtiendo: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ex) { /* ign */ }
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Sincroniza todos los mensajes de GRUPO no leídos.
     */
    private void sincronizarGrupos(UnCliente cliente) throws IOException {
        String nombreUsuario = cliente.getNombreUsuario();
        
        String sqlSelectGrupos = "SELECT g.nombre, g.id, COALESCE(e.ultimo_mensaje_id_visto, 0) AS ultimo_visto, " +
                     "(SELECT MAX(m.id) FROM grupos_mensajes m WHERE m.grupo_id = g.id) AS ultimo_existente " +
                     "FROM grupos g " +
                     "JOIN grupos_miembros gm ON g.id = gm.grupo_id " +
                     "LEFT JOIN grupos_estado_usuario e ON g.id = e.grupo_id AND e.usuario_nombre = ? " +
                     "WHERE gm.usuario_nombre = ?";
        
        String sqlUpdateStatus = "INSERT OR REPLACE INTO grupos_estado_usuario (usuario_nombre, grupo_id, ultimo_mensaje_id_visto) VALUES (?, ?, ?)";

        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            System.err.println("Error de conexión al sincronizar grupos.");
            return;
        }

        try (PreparedStatement selectGruposPstmt = conn.prepareStatement(sqlSelectGrupos);
             PreparedStatement updateStatusPstmt = conn.prepareStatement(sqlUpdateStatus)) {
            
            conn.setAutoCommit(false); // 1. Iniciar transacción

            selectGruposPstmt.setString(1, nombreUsuario);
            selectGruposPstmt.setString(2, nombreUsuario);
            
            try (ResultSet rsGrupos = selectGruposPstmt.executeQuery()) {
                
                // 2. Iterar sobre cada GRUPO al que pertenece el usuario
                while (rsGrupos.next()) {
                    String nombreGrupo = rsGrupos.getString("nombre");
                    int grupoId = rsGrupos.getInt("id");
                    long ultimoVisto = rsGrupos.getLong("ultimo_visto");
                    long ultimoExistente = rsGrupos.getLong("ultimo_existente");

                    // 3. Si este grupo necesita sincronización...
                    if (ultimoVisto < ultimoExistente) {
                        
                        // 4. Descargar mensajes nuevos
                        long ultimoIdEnviado = descargarMensajes(conn, cliente.salida, grupoId, nombreGrupo, ultimoVisto);

                        // 5. Actualizar el estado (solo si se enviaron mensajes)
                        if (ultimoIdEnviado > ultimoVisto) {
                            updateStatusPstmt.setString(1, nombreUsuario);
                            updateStatusPstmt.setInt(2, grupoId);
                            updateStatusPstmt.setLong(3, ultimoIdEnviado);
                            updateStatusPstmt.addBatch(); 
                        }
                    }
                }
            } 

            // 6. Ejecutar todos los updates de estado de una vez
            updateStatusPstmt.executeBatch();

            // 7. Confirmar la transacción
            conn.commit();

        } catch (SQLException | IOException e) {
            System.err.println("Error al sincronizar grupos (transacción), revirtiendo: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ex) { /* ign */ }
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Descarga mensajes de un grupo y los envía.
     * Retorna el ID del último mensaje enviado.
     */
    private long descargarMensajes(Connection conn, DataOutputStream salida, int grupoId, String nombreGrupo, long ultimoIdVisto) throws SQLException, IOException {
        String sqlSelectMensajes = "SELECT id, remitente_nombre, contenido FROM grupos_mensajes " +
                                 "WHERE grupo_id = ? AND id > ? " +
                                 "ORDER BY timestamp ASC";
        
        long ultimoIdEnviado = ultimoIdVisto;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelectMensajes)) {
            pstmt.setInt(1, grupoId);
            pstmt.setLong(2, ultimoIdVisto);
            
            try (ResultSet rsMsj = pstmt.executeQuery()) {
                while (rsMsj.next()) {
                    long msgId = rsMsj.getLong("id");
                    String remitente = rsMsj.getString("remitente_nombre");
                    String contenido = rsMsj.getString("contenido");
                    
                    String msgFormateado = String.format("<%s> %s: %s", nombreGrupo, remitente, contenido);
                    salida.writeUTF(msgFormateado);
                    
                    ultimoIdEnviado = msgId; // Actualizar el ID más alto enviado
                }
            }
        }
        return ultimoIdEnviado;
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
            
            conn.setAutoCommit(false); // 1. Iniciar transacción
            selectPstmt.setString(1, nombreUsuario);

            try (ResultSet rs = selectPstmt.executeQuery()) {
                while (rs.next()) {
                    long msgId = rs.getLong("id");
                    String remitente = rs.getString("remitente_nombre");
                    String contenido = rs.getString("contenido");
                    
                    String msgFormateado = String.format("[Privado de %s]: %s", remitente, contenido);
                    
                    cliente.salida.writeUTF(msgFormateado); // 3a. Enviar
                    
                    updatePstmt.setLong(1, msgId); // 3b. Añadir al lote
                    updatePstmt.addBatch();
                }
            }
            
            updatePstmt.executeBatch(); // 4. Ejecutar lote
            conn.commit(); // 5. Confirmar

        } catch (SQLException | IOException e) {
            System.err.println("Error al sincronizar privados (batch), revirtiendo: " + e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException ex) {
                System.err.println("Error al hacer rollback: " + ex.getMessage());
            }
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { /* ign */ }
            ConexionDB.cerrarConexion(conn);
        }
    }
}