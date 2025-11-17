package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    private final ManejadorMensajes manejadorMensajes;
    private final ManejadorSincronizacion manejadorSincronizacion;
    private final EnrutadorComandos enrutadorComandos; // El nuevo enrutador
    
    // estado del cliente
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    // Campos de Juego
    private volatile String oponentePendiente = null;
    private final ConcurrentHashMap<String, JuegoGato> juegosActivos = new ConcurrentHashMap<>();
    
    /**
     * Recibe el contexto con todos los servicios.
     */
    UnCliente(Socket s, String id, ContextoServidor contexto) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;
        
        // UnCliente "pide" los servicios que necesita del contexto
        this.manejadorMensajes = contexto.getManejadorMensajes();
        this.manejadorSincronizacion = contexto.getManejadorSincronizacion();
        this.enrutadorComandos = contexto.getEnrutadorComandos();
    }

    // --- GETTERS & SETTERS  ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    public synchronized String getOponentePendiente() { return oponentePendiente; }
    public synchronized void setOponentePendiente(String nombre) { this.oponentePendiente = nombre; }
    public synchronized JuegoGato getJuegoConID(String juegoID) { return juegosActivos.get(juegoID); }
    public synchronized void agregarJuego(String juegoID, JuegoGato juego) { juegosActivos.put(juegoID, juego); }
    public synchronized void removerJuego(String juegoID) { juegosActivos.remove(juegoID); }
    public synchronized boolean estaEnJuego() { return !this.juegosActivos.isEmpty(); }
    public synchronized ConcurrentHashMap<String, JuegoGato> getJuegosActivos() { return juegosActivos; }
    
    
    /**
     *  Llama al sincronizador inyectado.
     */
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
       try {
            this.nombreUsuario = nombre; 
            this.logueado = true;
                // Auto-unirse al grupo "Todos"
            new GrupoDB().unirseGrupo("Todos", nombre);
            // Usa el sincronizador inyectado
            this.manejadorSincronizacion.sincronizarMensajesPendientes(this);
            return true;
        } catch (Exception e) {
            // Si la sincronización o unirse a grupo falla, el login falló.
            System.err.println("Error en la fase de login para " + nombre + ": " + e.getMessage());
            // Revertimos el estado si falla
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID;
            return false;
        }
    }
    
    public void manejarLogoutInterno() {
        if (this.logueado) {
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
            this.mensajesEnviados = 0;
            
            juegosActivos.values().forEach(juego -> {
                juego.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' ha cerrado sesión.");
            });
        }
    }
    
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "--- Comandos de Mensajes ---\n" +
                                 "  Mensaje a 'Todos': Hola a todos\n" +
                                 "  Mensaje a Grupo:   #NombreGrupo Hola grupo\n" +
                                 "  Mensaje Privado:   @Usuario Hola\n" +
                                 "--- Comandos de Grupos ---\n" +
                                 "  /creargrupo, /borrargrupo, /unirsegrupo, /salirgrupo\n" +
                                 "--- Otros Comandos ---\n" +
                                 "  /login, /registrar, /logout, /block, /unblock\n" +
                                 "  /rangos, /winrate, /jugar, /aceptar, /jugada, /salirjuego");
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                if (mensaje.startsWith("/")) {
                    
                    // 1. Delega toods los comandos al nuevo Enrutador
                    enrutadorComandos.procesar(this, mensaje, entrada, salida);

                } else {
                    
                    // 2. Delegar todos los mensajes al Manejador de Mensajes
                    
                    // Límite de mensajes para invitados
                    if (!this.logueado && !mensaje.startsWith("@") && this.mensajesEnviados >= 3) {
                        this.salida.writeUTF("Límite de 3 mensajes alcanzado. Por favor, inicia sesión.");
                        continue;
                    }
                    
                    manejadorMensajes.enrutarMensaje(this, mensaje);
                }
                
            } catch (Exception ex) {
                // --- Manejo de desconexion  ---
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                juegosActivos.values().forEach(juego -> {
                    juego.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' se ha desconectado.");
                });
                
                ServidorMulti.clientes.remove(this.clienteID); 
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { e.printStackTrace(); }
                break;
            }
        }
    }
}