package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ManejadorJuegos {

    private final Map<String, UnCliente> clientes;
    private static final AtomicLong gameIdCounter = new AtomicLong(0); // Contador atómico para IDs únicos

    private final BloqueoDB bloqueoDB; 

    public ManejadorJuegos(Map<String, UnCliente> clientes, BloqueoDB bdb) {
        this.clientes = clientes;
        this.bloqueoDB = bdb; 
    }

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
            salida.writeUTF("Uso: /jugar NombreOponente");
            return;
        }
        
        String nombreOponente = partes[1];
        String nombreRetador = retador.getNombreUsuario(); 
        
        if (nombreOponente.equalsIgnoreCase(nombreRetador)) {
            salida.writeUTF("No puedes jugar contigo mismo.");
            return;
        }

        UnCliente oponente = buscarCliente(nombreOponente);
        
        if (oponente == null) {
            salida.writeUTF("El usuario '" + nombreOponente + "' no está conectado.");
            return;
        }
        
        // Verificación de bloqueo (Del paso anterior)
        if (bloqueoDB.estaBloqueado(nombreRetador, nombreOponente)) {
            salida.writeUTF("No puedes retar a '" + nombreOponente + "' porque lo tienes bloqueado.");
            return;
        }
        if (bloqueoDB.estaBloqueado(nombreOponente, nombreRetador)) {
            salida.writeUTF("No puedes retar a '" + nombreOponente + "' porque te ha bloqueado.");
            return;
        }
        
        // Verificar si el oponente ya te invitó a TI
        if (retador.getOponentePendiente() != null) {
            if (retador.getOponentePendiente().equalsIgnoreCase(nombreOponente)) {
                salida.writeUTF("Error: '" + nombreOponente + "' ya te ha enviado una invitación. Escribe /aceptar " + nombreOponente);
                return;
            }
        }

        
        // Enviar la invitación
        oponente.setOponentePendiente(nombreRetador);
        oponente.salida.writeUTF("¡RETO DE GATO! '" + nombreRetador + "' te ha retado a una partida.");
        oponente.salida.writeUTF("Escribe /aceptar " + nombreRetador + " para comenzar.");
        
        salida.writeUTF("Invitación enviada a '" + nombreOponente + "'. Esperando respuesta...");
    }

    /**
     * Maneja el comando /aceptar [nombre_retador]
     */
    public void manejarAceptar(String comando, DataOutputStream salida, UnCliente aceptador) throws IOException {
        if (!aceptador.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para aceptar un juego.");
            return;
        }
        
        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /aceptar NombreRetador");
            return;
        }
        
        String nombreRetador = partes[1];
        
        // 1. Verificar invitación
        if (!nombreRetador.equalsIgnoreCase(aceptador.getOponentePendiente())) {
            salida.writeUTF("No tienes una invitación pendiente de '" + nombreRetador + "'.");
            return;
        }
        
        UnCliente retador = buscarCliente(nombreRetador);
        
        if (retador == null) {
            salida.writeUTF("Tu retador ('" + nombreRetador + "') ya no está conectado.");
            aceptador.setOponentePendiente(null); // Limpiar la invitación
            return;
        }
        
        // 2. Limpiar invitación
        aceptador.setOponentePendiente(null); 
        
        // 3. Crear el ID del juego
        String nuevoJuegoID = String.valueOf(gameIdCounter.incrementAndGet());
        
        // 4. Crear la instancia del juego (Pasando el nuevo ID)
        JuegoGato nuevoJuego = new JuegoGato(retador, aceptador, nuevoJuegoID);
        
        // 5. Agregar el juego al mapa de cada jugador usando el ID
        retador.agregarJuego(nuevoJuegoID, nuevoJuego);
        aceptador.agregarJuego(nuevoJuegoID, nuevoJuego);
        
        // 6. Iniciar el juego
        nuevoJuego.iniciarJuego();
    }
}