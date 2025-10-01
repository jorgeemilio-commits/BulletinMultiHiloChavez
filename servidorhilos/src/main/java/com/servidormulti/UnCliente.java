package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
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
                for (UnCliente unCliente : ServidorMultiHilos.clientes.values()) {
                    unCliente.salida.writeUTF(mensaje);
                }
            } catch (Exception x) {
            }
        }
    }
    
}
