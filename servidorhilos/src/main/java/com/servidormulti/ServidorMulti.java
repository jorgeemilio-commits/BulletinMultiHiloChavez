package com.servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorMulti {

    static HashMap<String, UnCliente> clientes = new HashMap<String,UnCliente>();


    public static void main(String[] args) throws IOException {
        ServerSocket servidorSocket = new ServerSocket(8081);
        int contador = 0;
        while (true) {
            Socket s = servidorSocket.accept();
            String clienteId = Integer.toString(contador);
            UnCliente unCliente = new UnCliente(s, clienteId);
            Thread hilo = new Thread(unCliente, "Cliente-" + clienteId);
            clientes.put(clienteId , unCliente);
            hilo.start();
            System.out.println("Se conecto el chango # " + contador );
            contador++;
        }
    }
}