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

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
    }
    


    @Override
    public void run() {
        String mensaje;
        System.out.println("Hilo " + Thread.currentThread().getName() + " iniciado.");
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
                            cliente.salida.writeUTF(Thread.currentThread().getName() + ": " + contenido);              
                            }
                        }
                   }
                } else {
                    for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                        if (!unCliente.clienteID.equals(this.clienteID)){
                            unCliente.salida.writeUTF(Thread.currentThread().getName() + ": " + mensaje);              
                            }
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
    
}
