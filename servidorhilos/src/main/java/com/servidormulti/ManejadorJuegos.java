package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

public class ManejadorJuegos {

    private final Map<String, UnCliente> clientes;

    public ManejadorJuegos(Map<String, UnCliente> clientes) {
        this.clientes = clientes;
    }

    /**
     * Busca un cliente conectado por ID o por Nombre de Usuario.
     */
    private UnCliente buscarCliente(String identificador) {
        // busca por ID numerico
        UnCliente cliente = clientes.get(identificador);
        if (cliente != null) {
            return cliente;
        }

        // si no lo encuentra por ID, lo busca por nombre de usuario
        for (UnCliente c : clientes.values()) {
            if (c.getNombreUsuario().equalsIgnoreCase(identificador)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Maneja el comando /jugar [nombre_oponente]
     */
    public void manejarJugar(String comando, DataOutputStream salida, UnCliente retador) throws IOException {
        if (!retador.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para jugar.");
            return;
        }
        
        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /jugar nombre_usuario");
            return;
        }
        
        String nombreOponente = partes[1];
        
        if (nombreOponente.equalsIgnoreCase(retador.getNombreUsuario())) {
            salida.writeUTF("No puedes jugar contigo mismo.");
            return;
        }

        // Buscar al oponente en la lista de clientes CONECTADOS
        UnCliente oponente = buscarCliente(nombreOponente);
        
        if (oponente == null) {
            salida.writeUTF("El usuario '" + nombreOponente + "' no está conectado.");
            return;
        }
        
        if (oponente.estaEnJuego()) {
            salida.writeUTF("El usuario '" + nombreOponente + "' ya está en un juego.");
            return;
        }

        // Enviar la invitación
        oponente.setOponentePendiente(retador.getNombreUsuario());
        oponente.salida.writeUTF("¡RETO DE GATO! '" + retador.getNombreUsuario() + "' te ha retado a una partida.");
        oponente.salida.writeUTF("Escribe /aceptar para comenzar.");
        
        salida.writeUTF("Invitación enviada a '" + nombreOponente + "'. Esperando respuesta...");
    }

    /**
     * Maneja el comando /aceptar
     */
    public void manejarAceptar(DataOutputStream salida, UnCliente aceptador) throws IOException {
        if (!aceptador.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para aceptar un juego.");
            return;
        }
        
        String nombreRetador = aceptador.getOponentePendiente();

        // Verificar si hay una invitación
        if (nombreRetador == null) {
            salida.writeUTF("No tienes ninguna invitación pendiente.");
            return;
        }
        
        // Buscar al retador original por si no se desconectó
        UnCliente retador = buscarCliente(nombreRetador);
        
        if (retador == null) {
            salida.writeUTF("Tu retador ('" + nombreRetador + "') ya no está conectado.");
            aceptador.setOponentePendiente(null); // Limpiar la invitación
            return;
        }

        // 1. Limpiar invitaciónes
        aceptador.setOponentePendiente(null); 
        
        // 2. Crear la instancia del juego
        JuegoGato nuevoJuego = new JuegoGato(retador, aceptador);
        
        // 3. Poner a ambos jugadores en estado "en juego"
        retador.setJuegoActual(nuevoJuego);
        aceptador.setJuegoActual(nuevoJuego);
        
        // 4. Iniciar el juego
        nuevoJuego.iniciarJuego();
    }
}