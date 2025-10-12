package com.servidormulti;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Registrar {
    private static final Path USUARIOS_FILE;

    static {
        Path tempPath = null;
        try {
            tempPath = Paths.get(Registrar.class.getResource("/usuario.txt").toURI());
            System.out.println("Ruta del archivo de usuarios (Registrar): " + tempPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error al cargar el archivo de usuarios: " + e.getMessage());
        }
        USUARIOS_FILE = tempPath;
    }

    /**
     * Registra un nuevo usuario si el nombre no está en uso.
     *
     * @param nombre El nombre del usuario.
     * @param password La contraseña del usuario.
     * @return Mensaje indicando el resultado del registro.
     */
    public String registrarUsuario(String nombre, String password) {
        try {

            if (!Files.exists(USUARIOS_FILE)) {
                Files.createFile(USUARIOS_FILE);
            }

            // Leer todos los usuarios existentes
            List<String> usuarios = Files.readAllLines(USUARIOS_FILE);

            // Verificar si el nombre ya existe
            for (String usuario : usuarios) {
                String[] partes = usuario.split(",");
                if (partes.length > 0 && partes[0].equals(nombre)) {
                    return "El nombre de usuario ya está en uso."; // El nombre ya está en uso
                }
            }

            // Agregar el nuevo usuario al archivo
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(USUARIOS_FILE.toString(), true))) {
                writer.write(nombre + "," + password);
                writer.newLine();
            }

            return "Registro exitoso."; 
        } catch (IOException e) {
            System.err.println("Error al registrar usuario en el archivo: " + USUARIOS_FILE);
            e.printStackTrace();
            return "Error al registrar usuario. Verifica los permisos o la ruta del archivo.";
        }
    }
}
