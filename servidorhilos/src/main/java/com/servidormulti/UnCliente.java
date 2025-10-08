package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;

    final DataInputStream entrada;

    final String clienteID;

    private int mensajesEnviados;
    private boolean registrado = false; 

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.mensajesEnviados = 0;
    }
    

    @Override
    public void run() {
        String mensaje;
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado. ID de cliente: " + this.clienteID);
        System.out.println("Para enviar mensaje es @id y para multiples es @id-id-id");
        System.out.println("Para registrarse: /register <usuario> <contrasena> <confirmar_contrasena>"); // Informar al cliente sobre el comando de registro

        while (true) { 
            try {
                mensaje = entrada.readUTF(); 

                // 
                if (!registrado && this.mensajesEnviados >= 3) {
                    // el cliente alcanzo el límite de mensajes y no está registrado
                    if (mensaje.startsWith("/register ")) {
                        // se envia a registro.java
                        String responseMessage = Registro.processRegistrationCommand(this.clienteID, mensaje);
                        salida.writeUTF(responseMessage);

                        // si el registro fue exitoso, actualizar el estado del cliente
                        if (responseMessage.startsWith("Registro exitoso")) {
                            this.registrado = true;
                            this.mensajesEnviados = 0; // resetear contador al registrarse
                            System.out.println("Cliente " + this.clienteID + " registrado.");
                        }
                    } else {
                        // si no es un comando, y está en el límite, informar al cliente
                        salida.writeUTF("Has enviado 3 mensajes. Por favor, regístrate o inicia sesión para seguir enviando. Usa: /register <usuario> <contrasena> <confirmar_contrasena>");
                    }
                    continue; 
                }

                    boolean mensajeEnv = false; 

                    if(mensaje.startsWith("@")){
                        int divMensaje = mensaje.indexOf(" ");
                        if (divMensaje == -1) {
                            salida.writeUTF("Formato de mensaje directo incorrecto. Usa: @id <mensaje>");
                            continue; // no es un mensaje válido
                        }
                        String destino = mensaje.substring(1, divMensaje); 
                        String contenido = mensaje.substring(divMensaje + 1).trim();
                        String[] partes = destino.split("-"); // divide por guión para saber los usuarios

                        for (String aQuien : partes) {
                            UnCliente cliente = ServidorMulti.clientes.get(aQuien);
                                if (cliente != null) { //si el usuario existe
                                    if (!cliente.clienteID.equals(this.clienteID)){
                                    cliente.salida.writeUTF(this.clienteID + ": " + contenido + " (" + (this.mensajesEnviados + 1) + ")");              
                                    mensajeEnv = true; // Se marcó que se envió a al menos uno
                                    }
                                } else {
                                    salida.writeUTF("Error: El cliente con ID '" + aQuien + "' no existe.");
                                }
                           }
                        } else { 
                            for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                                if (!unCliente.clienteID.equals(this.clienteID)){
                                    unCliente.salida.writeUTF(this.clienteID + ": " + mensaje + " (" + (this.mensajesEnviados + 1) +")");  
                                    mensajeEnv = true; 
                                }
                            }
                        }

                        // incrementar el contador 
                        if(mensajeEnv){
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
        
        // el cliente ha enviado 3 mensajes, se le envía este mensaje.
        if (this.mensajesEnviados >= 3) {
            try {
                this.salida.writeUTF("Has enviado 3 mensajes. Por favor, regístrate o inicia sesión para seguir enviando.");
                System.out.println("Cliente " + this.clienteID + " ha alcanzado el límite de mensajes. Mensaje de registro enviado.");
                } catch (IOException e) {
                    System.err.println("Error al enviar mensaje de límite al cliente " + this.clienteID + ": " + e.getMessage());
                }
            }
        }
    }
}
