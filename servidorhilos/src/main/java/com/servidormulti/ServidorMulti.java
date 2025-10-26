package com.servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorMulti {

    static ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<String,UnCliente>();


    public static void main(String[] args) throws IOException {
        try (ServerSocket servidorSocket = new ServerSocket(8081)) {
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
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }
}