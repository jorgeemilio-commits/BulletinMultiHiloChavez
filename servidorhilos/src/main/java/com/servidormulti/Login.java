package com.servidormulti;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Login {
    private static final Path USUARIOS_FILE;

    static {
        Path tempPath = null;
        try {
            tempPath = Paths.get(Login.class.getResource("/usuario.txt").toURI());
            System.out.println("Ruta del archivo de usuarios (Login): " + tempPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error al cargar el archivo de usuarios: " + e.getMessage());
        }
        USUARIOS_FILE = tempPath;
    }

    /**
     * Verifica las credenciales de un usuario.
     *
     * @param nombre El nombre del usuario.
     * @param password La contraseña del usuario.
     * @return true si las credenciales son correctas, false en caso contrario.
     */
    
    public boolean iniciarSesion(String nombre, String password) {
        try {
            // Leer todos los usuarios 
            List<String> usuarios = Files.readAllLines(USUARIOS_FILE);

            // Verificar si los datos coinciden
            for (String usuario : usuarios) {
                String[] partes = usuario.split(",");
                if (partes.length == 2 && partes[0].equals(nombre) && partes[1].equals(password)) {
                    return true; 
                }
            }
        } catch (IOException e) {
            System.err.println("Error al verificar credenciales: " + e.getMessage());
        }
        return false; 
    }
}
