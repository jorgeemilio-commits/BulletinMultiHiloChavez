package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataInputStream entrada;
    final String clienteID; 
    
    private final ManejadorMensajes manejadorMensajes; 
    
    // ESTADO DEL CLIENTE
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;

        // Inicializa el manejador de mensajes
        this.manejadorMensajes = new ManejadorMensajes(ServidorMulti.clientes); 
    }

    // getters y setters para mensajesEnviados

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public int getMensajesEnviados() {
        return mensajesEnviados;
    }

    public void incrementarMensajesEnviados() {
        this.mensajesEnviados++;
    }
    
    // logica de login y registro

    /**
     * Intenta iniciar sesión y actualiza el estado del cliente.
     */
    private boolean manejarLogin(String nombre, String password) throws IOException {
        Login login = new Login();
        if (login.iniciarSesion(nombre, password)) {

            // Se actualiza el estado
            this.nombreUsuario = nombre; 
            this.logueado = true;
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        String mensaje;
        
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado.");
        
        try {
            this.salida.writeUTF("Bienvenido al servidor. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "1. Enviar mensajes usando @ID o @NombreUsuario o @ID-Nombre-ID.\n" +
                                 "2. Usar /registrar para crear una cuenta (inicia sesión automáticamente).\n" +
                                 "3. Usar /login para iniciar sesión.");
        } catch (IOException e) {
            System.err.println("Error al enviar mensaje de bienvenida: " + e.getMessage());
        }

        while (true) {
            try {
                mensaje = entrada.readUTF();

                // comandos
                
                // registrar
                if (mensaje.equalsIgnoreCase("/registrar")) {
                    this.salida.writeUTF("Introduce tu nombre de usuario:");
                    String nombre = entrada.readUTF();

                    this.salida.writeUTF("Introduce tu contraseña:");
                    String password = entrada.readUTF();

                    this.salida.writeUTF("Confirma tu contraseña:");
                    String confirmPassword = entrada.readUTF();

                    if (!password.equals(confirmPassword)) {
                        this.salida.writeUTF("Las contraseñas no coinciden. Intenta de nuevo.");
                        continue;
                    }

                    Registrar registrar = new Registrar();
                    String resultado = registrar.registrarUsuario(nombre, password);
                    this.salida.writeUTF(resultado);
                    
                    if (resultado.contains("Registro exitoso")) {
                        if (manejarLogin(nombre, password)) {
                            this.salida.writeUTF("Registro exitoso e inicio de sesión automático. Tu nuevo nombre es: " + this.nombreUsuario);
                        } else {
                            this.salida.writeUTF("Registro exitoso, pero ocurrió un error al iniciar sesión automáticamente.");
                        }
                    }
                    continue;
                }

                // login
                if (mensaje.equalsIgnoreCase("/login")) {
                    this.salida.writeUTF("Introduce tu nombre de usuario:");
                    String nombre = entrada.readUTF();

                    this.salida.writeUTF("Introduce tu contraseña:");
                    String password = entrada.readUTF();

                    if (manejarLogin(nombre, password)) {
                        this.salida.writeUTF("Inicio de sesión exitoso. Tu nuevo nombre es: " + this.nombreUsuario + ". Ahora puedes enviar mensajes sin límite.");
                    } else {
                        this.salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
                    }
                    continue;
                }

                // logica de envio de mensajes

                // checa limite
                if (!this.logueado && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Has enviado 3 mensajes. Por favor, regístrate o inicia sesión para seguir enviando.");
                    continue;
                }
                
                // logica de tageo
                manejadorMensajes.enrutarMensaje(this, mensaje, this.logueado);


            } catch (Exception ex) {
                // desconexión del cliente
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                ServidorMulti.clientes.remove(this.clienteID); 
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }
}