package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ManejadorComandos {

    /**
     * Procesa el comando /registrar.
     * @param entrada Stream de entrada del cliente.
     * @param salida Stream de salida para el cliente.
     * @param cliente El objeto UnCliente que se está registrando.
     * @return true si el registro fue exitoso (y el cliente hizo login).
     * @throws IOException
     */
    public boolean manejarRegistro(DataInputStream entrada, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Introduce tu nombre de usuario:");
        String nombre = entrada.readUTF();

        salida.writeUTF("Introduce tu contraseña:");
        String password = entrada.readUTF();

        salida.writeUTF("Confirma tu contraseña:");
        String confirmPassword = entrada.readUTF();

        if (!password.equals(confirmPassword)) {
            salida.writeUTF("Las contraseñas no coinciden. Intenta de nuevo.");
            return false;
        }

        Registrar registrar = new Registrar();
        String resultado = registrar.registrarUsuario(nombre, password);
        salida.writeUTF(resultado);
        
        if (resultado.contains("Registro exitoso")) {
            if (cliente.manejarLoginInterno(nombre, password)) {
                salida.writeUTF("Registro exitoso e inicio de sesión automático. Tu nuevo nombre es: " + cliente.getNombreUsuario());
                return true;
            } else {
                salida.writeUTF("Registro exitoso, pero ocurrió un error al iniciar sesión automáticamente.");
            }
        }
        return false;
    }
    
    /**
     * Procesa el comando /login.
     * @param entrada Stream de entrada del cliente.
     * @param salida Stream de salida para el cliente.
     * @param cliente El objeto UnCliente que intenta iniciar sesión.
     * @return true si el login fue exitoso.
     * @throws IOException
     */
    public boolean manejarLogin(DataInputStream entrada, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Introduce tu nombre de usuario:");
        String nombre = entrada.readUTF();

        salida.writeUTF("Introduce tu contraseña:");
        String password = entrada.readUTF();

        if (cliente.manejarLoginInterno(nombre, password)) {
            salida.writeUTF("Inicio de sesión exitoso. Tu nuevo nombre es: " + cliente.getNombreUsuario() + ". Ahora puedes enviar mensajes sin límite.");
            return true;
        } else {
            salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
            return false;
        }
    }
}