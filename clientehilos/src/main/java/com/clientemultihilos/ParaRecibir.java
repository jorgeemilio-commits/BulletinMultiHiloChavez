package com.clientemultihilos;

public class ParaRecibir implements Runnable {
    private ClienteMultiHilos cliente;

    public ParaRecibir(ClienteMultiHilos cliente) {
        this.cliente = cliente;
    }

    @Override
    public void run() {
        cliente.recibirMensaje();
    }
    
}
