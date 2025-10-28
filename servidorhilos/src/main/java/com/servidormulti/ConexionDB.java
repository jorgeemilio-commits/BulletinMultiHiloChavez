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
            
            // crear las tablas de usuarios, bloqueos y partidas si no existen
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
        // Agrega campos de victorias, derrotas y PUNTOS a la tabla usuarios
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nombre TEXT NOT NULL UNIQUE," +
                     "password TEXT NOT NULL," +
                     "victorias INTEGER DEFAULT 0," + 
                     "derrotas INTEGER DEFAULT 0," +
                     "puntos INTEGER DEFAULT 0" + 
                     ");";
                     
        // Tabla para manejar los bloqueos:
        String sqlBloqueos = "CREATE TABLE IF NOT EXISTS bloqueos (" +
                     "bloqueador_nombre TEXT NOT NULL," +
                     "bloqueado_nombre TEXT NOT NULL," +
                     "PRIMARY KEY (bloqueador_nombre, bloqueado_nombre)" +
                     ");";
                     
        // NUEVA TABLA para registrar partidas de Gato:
        String sqlPartidas = "CREATE TABLE IF NOT EXISTS partidas (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "jugadorX_nombre TEXT NOT NULL," +
                     "jugadorO_nombre TEXT NOT NULL," +
                     "ganador_nombre TEXT," + // Puede ser NULL en caso de EMPATE
                     "perdedor_nombre TEXT," + // Puede ser NULL en caso de EMPATE
                     "resultado TEXT NOT NULL" + // VICTORIA, EMPATE, ABANDONO
                     ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsuarios);
            
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN victorias INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN derrotas INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN puntos INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            
            stmt.execute(sqlBloqueos);
            stmt.execute(sqlPartidas); // Crear tabla de partidas
            
            System.out.println("Tablas 'usuarios', 'bloqueos' y 'partidas' verificadas o creadas.");
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