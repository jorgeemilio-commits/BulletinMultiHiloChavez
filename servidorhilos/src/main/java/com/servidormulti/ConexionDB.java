package com.servidormulti;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ConexionDB {

    private static final String URL = "jdbc:sqlite:usuarios.db";

    public static Connection conectar() {
        Connection conn = null;
        try {
            // carga el driver JDBC
            Class.forName("org.sqlite.JDBC"); 
            
            // se conecta con la base de datos (crea el archivo 'usuarios.db' si no existe)
            conn = DriverManager.getConnection(URL);
            
            // crear las tablas de usuarios y bloqueos si no existen
            crearTablas(conn); 

        } catch (ClassNotFoundException e) {
            System.err.println("Error: Driver JDBC de SQLite no encontrado.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error al conectar o crear la base de datos: " + e.getMessage());
        }
        return conn;
    }

    private static void crearTablas(Connection conn) {
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nombre TEXT NOT NULL UNIQUE," +
                     "password TEXT NOT NULL" +
                     ");";
                     
        // NUEVA TABLA para manejar los bloqueos:
        String sqlBloqueos = "CREATE TABLE IF NOT EXISTS bloqueos (" +
                     "bloqueador_nombre TEXT NOT NULL," +
                     "bloqueado_nombre TEXT NOT NULL," +
                     "PRIMARY KEY (bloqueador_nombre, bloqueado_nombre)" +
                     ");";
                     
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlBloqueos);
            System.out.println("Tablas 'usuarios' y 'bloqueos' verificadas o creadas.");
        } catch (SQLException e) {
            System.err.println("Error al crear las tablas: " + e.getMessage());
        }
    }

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