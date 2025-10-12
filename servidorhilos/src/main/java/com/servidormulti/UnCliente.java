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

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
    }
    


    @Override
    public void run() {
        String mensaje;
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado. ID de cliente: " + this.clienteID);
        System.out.println("Para enviar mensaje es @id y para multiples es @id-id-id");
        while (true) {
            try {
                mensaje = entrada.readUTF();
                if(mensaje.startsWith("@")){
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
                            if (!cliente.clienteID.equals(this.clienteID)){
                            // Usar this.clienteID para identificar al remitente, no el nombre del hilo
                            cliente.salida.writeUTF(this.clienteID + ": " + contenido);              
                            }
                        }
                   }
                } else {
                    for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                        if (!unCliente.clienteID.equals(this.clienteID)){
                            // Usar this.clienteID para identificar al remitente, no el nombre del hilo
                            unCliente.salida.writeUTF(this.clienteID + ": " + mensaje);              
                            }
                    }
                }
            } catch (IOException ex) { // Capturar específicamente IOException para desconexiones
                System.out.println("Cliente " + this.clienteID + " desconectado. Razón: " + ex.getMessage());
                ServidorMulti.clientes.remove(this.clienteID); // Eliminar el cliente del HashMap
                try {
                    entrada.close();
                    salida.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar streams para cliente " + this.clienteID + ": " + e.getMessage());
                }
                break; // Salir del bucle run() para terminar el hilo
            } catch (Exception ex) { // Capturar otras excepciones inesperadas
                System.err.println("Error inesperado en el hilo del cliente " + this.clienteID + ": " + ex.getMessage());
                // En caso de otros errores graves, también es buena idea eliminar el cliente
                ServidorMulti.clientes.remove(this.clienteID);
                try {
                    entrada.close();
                    salida.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar streams después de error inesperado para cliente " + this.clienteID + ": " + e.getMessage());
                }
                break; // Salir del bucle run()
            }
        }
    }
    
}
