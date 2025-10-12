package com.servidormulti;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Registro {

    private static final String CSV_FILE_PATH = "src/main/resources/usuarios.csv";
    private static final String[] HEADERS = {"id", "usuario", "password"};

    /**
     * Verifica si un nombre de usuario ya existe en el archivo CSV.
     * @param username El nombre de usuario a verificar.
     * @return true si el usuario existe, false en caso contrario.
     * @throws IOException Si ocurre un error al leer el archivo.
     */
    public static boolean usuarioExiste(String username) throws IOException {
        Path path = Paths.get(CSV_FILE_PATH);
        // Si el archivo no existe o está vacío (solo encabezado), el usuario no puede existir.
        if (!Files.exists(path) || Files.size(path) == 0) {
            return false;
        }

        try (Reader in = new FileReader(CSV_FILE_PATH);
             // Usamos withHeader(HEADERS) para asegurar que el parser conoce los nombres de las columnas
             // y setSkipHeaderRecord(true) para ignorar la primera línea (encabezado) al leer los registros.
             CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build())) {
            for (CSVRecord record : parser) {
                if (record.get("usuario").equalsIgnoreCase(username)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Registra un nuevo usuario en el archivo CSV.
     * @param clienteId El ID del cliente (del HashMap) que se usará como ID único en el registro.
     * @param username El nombre de usuario.
     * @param password La contraseña del usuario.
     * @return true si el registro fue exitoso, false si el usuario ya existe.
     * @throws IOException Si ocurre un error al escribir en el archivo.
     */
    public static boolean registrarUsuario(String clienteId, String username, String password) throws IOException {
        if (usuarioExiste(username)) {
            return false; // El nombre de usuario ya está tomado
        }

        Path path = Paths.get(CSV_FILE_PATH);
        boolean fileExists = Files.exists(path) && Files.size(path) > 0;

        try (FileWriter out = new FileWriter(CSV_FILE_PATH, true); // Abrir en modo de añadir (append)
             // Solo imprimir el encabezado si el archivo no existe o está vacío
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(!fileExists).build())) {
            
            // Si el archivo es nuevo o estaba vacío, Commons CSV se encargará de imprimir el encabezado
            // gracias a withHeader y setSkipHeaderRecord(!fileExists) en el constructor del printer.
            // Si el archivo ya tiene contenido, simplemente añadirá el nuevo registro.
            printer.printRecord(clienteId, username, password);
            printer.flush(); // Asegurarse de que los datos se escriban en el disco
        }
        return true;
    }

    /**
     * Procesa un comando de registro recibido de un cliente.
     * Este método encapsula la lógica de parseo y validación del comando /register.
     * @param clientId El ID del cliente que intenta registrarse.
     * @param command El mensaje completo del comando (ej. "/register usuario contrasena confirmar_contrasena").
     * @return Un mensaje de respuesta para el cliente, indicando el resultado del registro.
     * @throws IOException Si ocurre un error al interactuar con el archivo CSV.
     */
    public static String processRegistrationCommand(String clientId, String command) throws IOException {
        String[] parts = command.split(" ");
        if (parts.length == 4) { // Esperamos "/register", "usuario", "contrasena", "confirmar_contrasena"
            String username = parts[1];
            String password = parts[2];
            String confirmPassword = parts[3];

            if (!password.equals(confirmPassword)) {
                return "Error de registro: Las contraseñas no coinciden. Inténtalo de nuevo.";
            } else {
                if (registrarUsuario(clientId, username, password)) {
                    return "Registro exitoso, " + username + ". Ahora puedes enviar mensajes ilimitados.";
                } else {
                    return "Error de registro: El usuario '" + username + "' ya existe. Por favor, elige otro.";
                }
            }
        } else {
            return "Formato de registro incorrecto. Usa: /register <usuario> <contrasena> <confirmar_contrasena>";
        }
    }

    // Opcional: Método para autenticar un usuario (útil para futuras implementaciones de inicio de sesión)
    public static boolean autenticarUsuario(String username, String password) throws IOException {
        Path path = Paths.get(CSV_FILE_PATH);
        if (!Files.exists(path) || Files.size(path) == 0) {
            return false;
        }

        try (Reader in = new FileReader(CSV_FILE_PATH);
             CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.builder().setHeader(HEADERS).setSkipHeaderRecord(true).build())) {
            for (CSVRecord record : parser) {
                if (record.get("usuario").equalsIgnoreCase(username) && record.get("password").equals(password)) {
                    return true;
                }
            }
        }
        return false;
    }
}
