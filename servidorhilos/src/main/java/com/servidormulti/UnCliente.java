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
                    String[] partes = mensaje.split(" ");
                    String aQuien = partes[0].substring(1);
                    UnCliente cliente = ServidorMulti.clientes.get(aQuien);
                    cliente.salida.writeUTF("Mensaje privado: " + partes[1]);;
                } else {
                for (UnCliente unCliente : ServidorMulti.clientes.values()) {
                    unCliente.salida.writeUTF(mensaje);
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
    
}
