package com.servidormulti;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ConexionDB {

    private static final String URL = "jdbc:sqlite:usuarios.db"; // El archivo se creará en la raíz del proyecto

    /**
     * Establece la conexión con la base de datos SQLite y crea la tabla si no existe.
     * @return Objeto Connection si la conexión es exitosa, null en caso contrario.
     */
    public static Connection conectar() {
        Connection conn = null;
        try {
            // carga el driver JDBC
            Class.forName("org.sqlite.JDBC"); 
            
            // se conecta con la base de datos (crea el archivo 'usuarios.db' si no existe)
            conn = DriverManager.getConnection(URL);
            
            // crear la tabla de usuarios si no existe
            crearTabla(conn); 

        } catch (ClassNotFoundException e) {
            System.err.println("Error: Driver JDBC de SQLite no encontrado.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error al conectar o crear la base de datos: " + e.getMessage());
        }
        return conn;
    }

    private static void crearTabla(Connection conn) {
        String sql = "CREATE TABLE IF NOT EXISTS usuarios (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nombre TEXT NOT NULL UNIQUE," +
                     "password TEXT NOT NULL" +
                     ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Tabla 'usuarios' verificada o creada.");
        } catch (SQLException e) {
            System.err.println("Error al crear la tabla: " + e.getMessage());
        }
    }

    /**
     * Cierra la conexión.
     */
    public static void cerrarConexion(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ex) {
            System.err.println("Error al cerrar la conexión: " + ex.getMessage());
        }
    }
}