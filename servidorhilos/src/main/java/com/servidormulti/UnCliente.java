package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    private final ManejadorMensajes manejadorMensajes; 
    private final ManejadorComandos manejadorComandos; 
    private final ManejadorJuegos manejadorJuegos;
    
    // estado del cliente
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    // --- CAMPOS DE JUEGO ---
    private volatile String oponentePendiente = null;
    private volatile JuegoGato juegoActual = null;
    // --- FIN CAMPOS DE JUEGO ---

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;
        
        this.manejadorMensajes = new ManejadorMensajes(ServidorMulti.clientes); 
        this.manejadorComandos = new ManejadorComandos();
        this.manejadorJuegos = new ManejadorJuegos(ServidorMulti.clientes); 

    }

    // --- GETTERS & SETTERS ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    
    // --- NUEVOS GETTERS & SETTERS (JUEGO) ---
    public synchronized String getOponentePendiente() { return oponentePendiente; }
    public synchronized void setOponentePendiente(String nombre) { this.oponentePendiente = nombre; }
    
    public synchronized JuegoGato getJuegoActual() { return juegoActual; }
    public synchronized void setJuegoActual(JuegoGato juego) { this.juegoActual = juego; }
    
    public synchronized boolean estaEnJuego() { return this.juegoActual != null; }
    
    // login
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        Login login = new Login();
        if (login.iniciarSesion(nombre, password)) {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            return true;
        }
        return false;
    }
    
    // logout
    public void manejarLogoutInterno() {
        if (this.logueado) {
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
            this.mensajesEnviados = 0;
        }
    }
    
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "1. Enviar mensajes usando @ID o @NombreUsuario.\n" +
                                 "2. Usar /registrar, /login, /logout, /block, /unblock.\n" +
                                 "3. Usar /jugar nombre, /aceptar, /salirjuego.");
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                // --- MANEJO DE JUEGO ---
                if (this.estaEnJuego()) {
                    if (mensaje.equalsIgnoreCase("/salirjuego")) {
                        this.juegoActual.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' ha abandonado la partida.");
                    } else {
                        this.juegoActual.manejarJugada(this, mensaje);
                    }
                    continue; 
                }
                // --- FIN MANEJO DE JUEGO ---


                // 1. Manejar Comandos
                if (mensaje.equalsIgnoreCase("/registrar")) {
                    manejadorComandos.manejarRegistro(entrada, salida, this);
                    continue;
                }
                if (mensaje.equalsIgnoreCase("/login")) {
                    manejadorComandos.manejarLogin(entrada, salida, this);
                    continue;
                }
                if (mensaje.equalsIgnoreCase("/logout")) {
                    manejadorComandos.manejarLogout(salida, this);
                    continue;
                }
                if (mensaje.startsWith("/block")) {
                    manejadorComandos.manejarBloqueo(mensaje, salida, this);
                    continue;
                }
                if (mensaje.startsWith("/unblock")) {
                    manejadorComandos.manejarDesbloqueo(mensaje, salida, this);
                    continue;
                }

                // --- COMANDOS DE JUEGO (AHORA USAN 'manejadorJuegos') ---
                if (mensaje.startsWith("/jugar ")) { 
                    manejadorJuegos.manejarJugar(mensaje, salida, this); // ACTUALIZADO
                    continue;
                }
                if (mensaje.equalsIgnoreCase("/aceptar")) {
                    manejadorJuegos.manejarAceptar(salida, this); // ACTUALIZADO
                    continue;
                }
                // --- FIN COMANDOS DE JUEGO ---

                // 2. Verificar Límite y Enviar Mensajes
                if (!this.logueado && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Límite de 3 mensajes alcanzado. Por favor, inicia sesión.");
                    continue;
                }
                
                manejadorMensajes.enrutarMensaje(this, mensaje, this.logueado);

            } catch (Exception ex) {
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                if (this.estaEnJuego()) {
                    this.juegoActual.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' se ha desconectado.");
                }
                
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