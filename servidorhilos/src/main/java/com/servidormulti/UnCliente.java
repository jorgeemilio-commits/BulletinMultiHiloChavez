package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException; // Importar IOException
import java.io.InputStreamReader;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    final DataInputStream entrada;

    final String clienteID;

    int mensajesEnviados = 0; // Contador de mensajes enviados

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
    }
    


    @Override
    public void run() {
        String mensaje;
        boolean logueado = false; // Estado de inicio de sesión
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado.");
        
        // Mensaje de bienvenida
        try {
            this.salida.writeUTF("Bienvenido al servidor. Puedes hacer lo siguiente:\n" +
                                 "1. Enviar mensajes a otros usuarios usando @id o @id-id-id.\n" +
                                 "2. Usar /registrar para registrarte con un usuario y contraseña.\n" +
                                 "3. Usar /login para iniciar sesión con tu usuario y contraseña.");
        } catch (IOException e) {
            System.err.println("Error al enviar mensaje de bienvenida: " + e.getMessage());
        }

        while (true) {
            try {
                mensaje = entrada.readUTF();
                boolean mensajeEnviado = false;

                // Comando para registrar usuario
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
                    continue;
                }

                // Comando para iniciar sesión
                if (mensaje.equalsIgnoreCase("/login")) {
                    this.salida.writeUTF("Introduce tu nombre de usuario:");
                    String nombre = entrada.readUTF();

                    this.salida.writeUTF("Introduce tu contraseña:");
                    String password = entrada.readUTF();

                    Login login = new Login();
                    if (login.iniciarSesion(nombre, password)) {
                        logueado = true;
                        this.salida.writeUTF("Inicio de sesión exitoso. Ahora puedes enviar mensajes sin límite.");
                    } else {
                        this.salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
                    }
                    continue;
                }

                // Verificar si el usuario está logueado o tiene límite de mensajes
                if (!logueado && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Has enviado 3 mensajes. Por favor, regístrate o inicia sesión para seguir enviando.");
                    continue;
                }

                if (mensaje.startsWith("@")) {
                    int divMensaje = mensaje.indexOf(" ");
                    if (divMensaje == -1) {
                        continue; // no hay espacio, no es un mensaje válido
                    }
                    String destino = mensaje.substring(1, divMensaje);
                    String contenido = mensaje.substring(divMensaje + 1).trim();
                    String[] partes = destino.split("-"); // divide por guión para saber los usuarios

                    for (String aQuien : partes) {
                        UnCliente cliente = ServidorMulti.clientes.get(aQuien);
                        if (cliente != null) { //si el usuario existe
                            if (!cliente.clienteID.equals(this.clienteID)) {
                                cliente.salida.writeUTF(Thread.currentThread().getName() + ": " + contenido + " (" + (this.mensajesEnviados + 1) + ")");
                                mensajeEnviado = true;
                            }
                        }
                    }
                } else {
                    for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                        if (!unCliente.clienteID.equals(this.clienteID)) {
                            unCliente.salida.writeUTF(Thread.currentThread().getName() + ": " + mensaje + " (" + (this.mensajesEnviados + 1) + ")");
                            mensajeEnviado = true;
                        }
                    }
                }

                // Incrementar el contador solo una vez por mensaje enviado por el cliente
                if (mensajeEnviado && !logueado) {
                    this.mensajesEnviados++;
                }

            } catch (Exception ex) {
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
