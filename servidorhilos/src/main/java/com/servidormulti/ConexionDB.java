package com.servidormulti;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConexionDB {

    private static final String URL = "jdbc:sqlite:usuarios.db";

    public static Connection conectar() {
        Connection conn = null;
        try {
            // Carga el driver JDBC
            Class.forName("org.sqlite.JDBC");
            
            org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
            config.enforceForeignKeys(true);
            
            // Se conecta con la base de datos (crea el archivo si no existe)
            conn = DriverManager.getConnection(URL, config.toProperties());
            
            // Crear las tablas si no existen
            crearTablas(conn); 
            inicializarDatosBase(conn);

        } catch (ClassNotFoundException e) {
            System.err.println("Error: Driver JDBC de SQLite no encontrado.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error al conectar o crear la base de datos: " + e.getMessage());
        }
        return conn;
    }

    private static void crearTablas(Connection conn) {
        

        // Tabla de Usuarios 
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nombre TEXT NOT NULL UNIQUE," +
                     "password TEXT NOT NULL," +
                     "victorias INTEGER DEFAULT 0," + 
                     "derrotas INTEGER DEFAULT 0," +
                     "puntos INTEGER DEFAULT 0" + 
                     ");";
                     
        // Tabla de Bloqueos
        String sqlBloqueos = "CREATE TABLE IF NOT EXISTS bloqueos (" +
                     "bloqueador_nombre TEXT NOT NULL," +
                     "bloqueado_nombre TEXT NOT NULL," +
                     "PRIMARY KEY (bloqueador_nombre, bloqueado_nombre)" +
                     ");";
                     
        // Tabla de Partidas 
        String sqlPartidas = "CREATE TABLE IF NOT EXISTS partidas (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "jugadorX_nombre TEXT NOT NULL," +
                     "jugadorO_nombre TEXT NOT NULL," +
                     "ganador_nombre TEXT," +
                     "perdedor_nombre TEXT," +
                     "resultado TEXT NOT NULL" +
                     ");";

        // Tabla de Grupos
        String sqlGrupos = "CREATE TABLE IF NOT EXISTS grupos (" +
                           "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                           "nombre TEXT NOT NULL UNIQUE" +
                           ");";

        // Tabla de Miembros 
        String sqlGruposMiembros = "CREATE TABLE IF NOT EXISTS grupos_miembros (" +
                                 "grupo_id INTEGER NOT NULL," +
                                 "usuario_nombre TEXT NOT NULL," +
                                 "PRIMARY KEY (grupo_id, usuario_nombre)," +
                                 "FOREIGN KEY (grupo_id) REFERENCES grupos(id) ON DELETE CASCADE" +
                                 ");";
                                 
        // Tabla de Mensajes de Grupo 
        String sqlGruposMensajes = "CREATE TABLE IF NOT EXISTS grupos_mensajes (" +
                                 "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                 "grupo_id INTEGER NOT NULL," +
                                 "remitente_nombre TEXT NOT NULL," +
                                 "contenido TEXT NOT NULL," +
                                 "timestamp INTEGER NOT NULL," + // Para ordenar
                                 "FOREIGN KEY (grupo_id) REFERENCES grupos(id) ON DELETE CASCADE" +
                                 ");";

        // Tabla de Estado (visto/no visto) 
        String sqlGruposEstadoUsuario = "CREATE TABLE IF NOT EXISTS grupos_estado_usuario (" +
                                      "usuario_nombre TEXT NOT NULL," +
                                      "grupo_id INTEGER NOT NULL," +
                                      "ultimo_mensaje_id_visto INTEGER DEFAULT 0," +
                                      "PRIMARY KEY (usuario_nombre, grupo_id)" +
                                      ");";

        // Tabla de Mensajes Privados
        String sqlMensajesPrivados = "CREATE TABLE IF NOT EXISTS mensajes_privados (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                    "remitente_nombre TEXT NOT NULL," +
                                    "destinatario_nombre TEXT NOT NULL," +
                                    "contenido TEXT NOT NULL," +
                                    "timestamp INTEGER NOT NULL," +
                                    "visto INTEGER DEFAULT 0" + // 0 = no visto, 1 = visto
                                    ");";

        try (Statement stmt = conn.createStatement()) {
            
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlBloqueos);
            stmt.execute(sqlPartidas);
            stmt.execute(sqlGrupos);
            stmt.execute(sqlGruposMiembros);
            stmt.execute(sqlGruposMensajes);
            stmt.execute(sqlGruposEstadoUsuario);
            stmt.execute(sqlMensajesPrivados);
            
            // --- Bloque de compatibilidad ---
            // Intenta añadir columnas si la tabla 'usuarios' ya existía
            // pero le faltaban 
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN victorias INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN derrotas INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            try {
                stmt.execute("ALTER TABLE usuarios ADD COLUMN puntos INTEGER DEFAULT 0");
            } catch (SQLException ignore) { /* Columna ya existe */ }
            
            System.out.println("Todas las tablas (usuarios, juegos y grupos) verificadas o creadas.");
        } catch (SQLException e) {
            System.err.println("Error al crear las tablas: " + e.getMessage());
        }
    }

    /**
     * Asegura que los datos mínimos existan.
     */
    private static void inicializarDatosBase(Connection conn) {
        String sqlCheck = "SELECT COUNT(*) FROM grupos WHERE nombre = ?";
        String sqlInsert = "INSERT INTO grupos (nombre) VALUES (?)";

        try (PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, "Todos");
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                // El grupo "Todos" no existe, hay que crearlo
                try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                    insertStmt.setString(1, "Todos");
                    insertStmt.executeUpdate();
                    System.out.println("Grupo 'Todos' inicializado en la base de datos.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al inicializar datos base (Grupo 'Todos'): " + e.getMessage());
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