package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;

    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    final DataInputStream entrada;

    UnCliente(Socket s) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }
    

    @Override
    public void run() {
        String mensaje;
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
                cliente.salida.writeUTF(contenido + " enviado por " + Thread.currentThread().getName());
                }
                }
                } else {
                for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                    unCliente.salida.writeUTF(mensaje + " enviado por " + Thread.currentThread().getName());
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
    
}
