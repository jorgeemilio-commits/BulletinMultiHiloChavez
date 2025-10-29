package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ManejadorRangos {

    /**
     * Maneja el comando /rangos.
     * Muestra el Top 10 de jugadores por puntos.
     */
    public void ejecutar(DataOutputStream salida) throws IOException {
        String sql = "SELECT nombre, puntos FROM usuarios WHERE puntos > 0 ORDER BY puntos DESC LIMIT 10";
        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            salida.writeUTF("Error de conexión a la base de datos.");
            return;
        }

        StringBuilder ranking = new StringBuilder("--- RANKING DE JUGADORES (Top 10) ---\n");
        int pos = 1;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (!rs.isBeforeFirst()) { // Verifica si el ResultSet está vacío
                salida.writeUTF("Aún no hay jugadores con puntuación.");
                return;
            }
            
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                int puntos = rs.getInt("puntos");
                ranking.append(String.format("%d. %s - %d puntos\n", pos, nombre, puntos));
                pos++;
            }
            salida.writeUTF(ranking.toString());

        } catch (SQLException e) {
            System.err.println("Error al consultar rangos: " + e.getMessage());
            salida.writeUTF("Error interno al consultar el ranking.");
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}