package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.io.IOException;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    final DataInputStream entrada;

    final String clienteID;

    private int mensajesEnviados;

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.mensajesEnviados = 0;
    }
    


    @Override
    public void run() {
        String mensaje;
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado.");
        System.out.println("Para enviar mensaje es @id y para multiples es @id-id-id");
        while (true && this.mensajesEnviados < 3) {
            try {
                mensaje = entrada.readUTF();
                boolean mensajeEnviado = false;
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
                            cliente.salida.writeUTF(Thread.currentThread().getName() + ": " + contenido + " (" + (this.mensajesEnviados + 1) + ")");
                            mensajeEnviado = true;            
                            }
                        }
                   }
                } else {
                    for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                        if (!unCliente.clienteID.equals(this.clienteID)){
                            unCliente.salida.writeUTF(Thread.currentThread().getName() + ": " + mensaje + " (" + (this.mensajesEnviados + 1) +")");  
                            mensajeEnviado = true;            
                        }
                    }
                }

                // incrementa el contador solo una vez por mensaje enviado
                if(mensajeEnviado){
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
