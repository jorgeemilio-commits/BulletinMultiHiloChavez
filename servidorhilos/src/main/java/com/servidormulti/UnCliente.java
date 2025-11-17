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
    private final EnrutadorComandos enrutadorComandos; 
    
    // Constante para el límite ---
    private static final int LIMITE_MENSAJES_INVITADO = 3;
    
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
     * Aplica el estado de login y sincroniza mensajes.
     */
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        
        try {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            
            new GrupoDB().unirseGrupo("Todos", nombre); 
            
            this.manejadorSincronizacion.sincronizarMensajesPendientes(this);
            
            return true;

        } catch (Exception e) {
            System.err.println("Error en la fase final de login para " + nombre + ": " + e.getMessage());
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
    
    /**
     * MÉTODO RUN() SIMPLIFICADO Y CON LÓGICA DE LÍMITE
     */
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "--- Comandos Básicos ---\n" +
                                 "  Mensaje a 'Todos': Hola a todos\n" +
                                 "  Mensaje a Grupo:   #NombreGrupo Hola grupo\n" +
                                 "  Mensaje Privado:   @Usuario Hola\n" +
                                 "  /login             - Inicia sesión\n" +
                                 "  /registrar         - Crea una nueva cuenta\n" +
                                 "--- Para más comandos, escribe: /ayuda ---");

        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                if (mensaje.startsWith("/")) {
                    
                    // --- Lógica de límite para COMANDOS ---
                    if (!this.logueado) {
                        String comando = mensaje.trim().split(" ", 2)[0].toLowerCase();

                        // Comandos permitidos que NO cuentan para el límite
                        boolean esComandoExcluido = comando.equals("/login") || 
                                                    comando.equals("/registrar") ||
                                                    comando.equals("/ayuda"); // Ayuda tampoco debe contar

                        if (!esComandoExcluido) {
                            // Es un comando restringido (ej. /block, /jugar, /creargrupo)
                            if (this.mensajesEnviados >= LIMITE_MENSAJES_INVITADO) {
                                this.salida.writeUTF("Límite de acciones alcanzado. Por favor, inicia sesión para continuar con /login o /register.");
                                continue;
                            }
                            this.incrementarMensajesEnviados(); // Cuenta como un mensaje
                        }
                    }
                    
                    enrutadorComandos.procesar(this, mensaje, entrada, salida);

                } else {
                    
                    // --- Lógica de límite para MENSAJES ---
                    if (!this.logueado) {
                        if (this.mensajesEnviados >= LIMITE_MENSAJES_INVITADO) {
                            this.salida.writeUTF("Límite de mensajes alcanzado. Por favor, inicia sesión.");
                            continue;
                        }
                        this.incrementarMensajesEnviados(); // Cuenta como un mensaje
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