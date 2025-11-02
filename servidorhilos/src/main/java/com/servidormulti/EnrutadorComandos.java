package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Reemplaza el bloque if/else de UnCliente.run().
 * Recibe un comando y lo delega al servicio correspondiente.
 */
public class EnrutadorComandos {

    private final ManejadorComandos manejadorComandos;
    private final ManejadorJuegos manejadorJuegos;
    private final ManejadorAutenticacion manejadorAutenticacion;

    public EnrutadorComandos(ManejadorComandos mc, ManejadorJuegos mj, ManejadorAutenticacion ma) {
        this.manejadorComandos = mc;
        this.manejadorJuegos = mj;
        this.manejadorAutenticacion = ma;
    }

    /**
     * Procesa un mensaje que ya se sabe que es un comando (inicia con "/").
     */
    
    public void procesar(UnCliente cliente, String mensaje, DataInputStream entrada, DataOutputStream salida) throws IOException {
        
        String[] partes = mensaje.trim().split(" ", 3);
        String comando = partes[0].toLowerCase();

        // --- Comandos de Juego (ID-based) ---

        if (comando.equals("/jugada") && partes.length == 3) {
            JuegoGato juego = cliente.getJuegoConID(partes[1]);
            if (juego != null) juego.manejarJugada(cliente, partes[2]);
            else salida.writeUTF("Partida no encontrada.");
            return;
        }
        if (comando.equals("/salirjuego") && partes.length == 2) {
            JuegoGato juego = cliente.getJuegoConID(partes[1]);
            if (juego != null) juego.terminarJuego(cliente, "Has abandonado la partida.");
            else salida.writeUTF("Partida no encontrada.");
            return;
        }
        
        // --- Comandos de Autenticación ---
        if (comando.equals("/registrar")) {
            manejadorAutenticacion.manejarRegistro(entrada, salida, cliente);
            return;
        }
        if (comando.equals("/login")) {
            manejadorAutenticacion.manejarLogin(entrada, salida, cliente);
            return;
        }
        
        // --- Comandos de Usuario/Bloqueo (Delega a ManejadorComandos) ---
        if (comando.equals("/logout")) {
            manejadorComandos.manejarLogout(salida, cliente);
            return;
        }
        if (comando.equals("/block")) {
            manejadorComandos.manejarBloqueo(mensaje, salida, cliente);
            return;
        }
        if (comando.equals("/unblock")) {
            manejadorComandos.manejarDesbloqueo(mensaje, salida, cliente);
            return;
        }

        // --- Comandos de Ranking (Delega a ManejadorComandos) ---
        if (comando.equals("/rangos")) {
            manejadorComandos.manejarRangos(salida);
            return;
        }
        if (comando.equals("/winrate")) {
            manejadorComandos.manejarWinrate(mensaje, salida, cliente);
            return;
        }
        
        // --- Comandos de Grupo (Delega a ManejadorComandos) ---
        if (comando.equals("/creargrupo")) {
            manejadorComandos.manejarCrearGrupo(partes, salida, cliente);
            return;
        }
        if (comando.equals("/borrargrupo")) {
            manejadorComandos.manejarBorrarGrupo(partes, salida, cliente);
            return;
        }
        if (comando.equals("/unirsegrupo")) {
            manejadorComandos.manejarUnirseGrupo(partes, salida, cliente);
            return;
        }
        if (comando.equals("/saligrupo")) {
            manejadorComandos.manejarSalirGrupo(partes, salida, cliente);
            return;
        }

        // --- Comandos de Invitación a Juego (Delega a ManejadorJuegos) ---
        if (comando.equals("/jugar") && partes.length == 2) { 
            manejadorJuegos.manejarJugar(mensaje, salida, cliente);
            return;
        }
        if (comando.equals("/aceptar") && partes.length == 2) {
            manejadorJuegos.manejarAceptar(mensaje, salida, cliente);
            return;
        }
        
        // Comando desconocido
        salida.writeUTF("Comando '" + comando + "' no reconocido.");
    }
}
