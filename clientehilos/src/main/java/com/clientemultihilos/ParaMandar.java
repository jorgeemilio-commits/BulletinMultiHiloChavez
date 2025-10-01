package com.clientemultihilos;

import java.io.BufferedReader;

public class ParaMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new java.io.InputStreamReader(System.in));

    @Override
    public void run() {
        cliente.mandarMensaje(mensaje);
    }
    
}
