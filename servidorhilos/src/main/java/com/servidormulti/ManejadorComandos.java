package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ManejadorComandos {
    
    private boolean existeUsuarioDB(String nombre) {
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

    public void manejarLogout(DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Ya estás desconectado. Tu nombre es: " + cliente.getNombreUsuario());
            return;
        }
        cliente.manejarLogoutInterno();
        salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + cliente.getNombreUsuario() + "'.");
    }

    public void manejarBloqueo(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para bloquear usuarios.");
            return;
        }
        
        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /block nombre_usuario");
            return;
        }

        String aBloquear = partes[1];
        String miNombre = cliente.getNombreUsuario();
        
        if (aBloquear.equalsIgnoreCase(miNombre)) {
            salida.writeUTF("No puedes bloquearte a ti mismo.");
            return;
        }

        if (!existeUsuarioDB(aBloquear)) {
            salida.writeUTF("Error: El usuario '" + aBloquear + "' no existe en el sistema.");
            return;
        }

        BloqueoDB bloqueoDB = new BloqueoDB();
        String resultado = bloqueoDB.bloquearUsuario(miNombre, aBloquear);
        salida.writeUTF(resultado);
    }

    public void manejarDesbloqueo(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para desbloquear usuarios.");
            return;
        }

        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /unblock nombre_usuario");
            return;
        }

        String aDesbloquear = partes[1];
        String miNombre = cliente.getNombreUsuario();

        if (aDesbloquear.equalsIgnoreCase(miNombre)) {
            salida.writeUTF("No puedes desbloquearte a ti mismo.");
            return;
        }
        
        BloqueoDB bloqueoDB = new BloqueoDB();
        String resultado = bloqueoDB.desbloquearUsuario(miNombre, aDesbloquear);
        salida.writeUTF(resultado);
    }
}