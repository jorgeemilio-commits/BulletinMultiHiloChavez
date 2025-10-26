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
    
    // --- MANEJADORES SEPARADOS ---
    private final ManejadorMensajes manejadorMensajes; 
    private final ManejadorComandos manejadorComandos; 
    private final ManejadorJuegos manejadorJuegos;
    private final ManejadorAutenticacion manejadorAutenticacion; 
    // --- FIN MANEJADORES ---
    
    // estado del cliente
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    // Campos de Juego
    private volatile String oponentePendiente = null;
    // La clave es el ID del juego (String)
    private final ConcurrentHashMap<String, JuegoGato> juegosActivos = new ConcurrentHashMap<>();
    

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;
        
        this.manejadorMensajes = new ManejadorMensajes(ServidorMulti.clientes); 
        this.manejadorComandos = new ManejadorComandos();
        this.manejadorJuegos = new ManejadorJuegos(ServidorMulti.clientes);
        this.manejadorAutenticacion = new ManejadorAutenticacion();

    }

    // --- GETTERS & SETTERS MODIFICADOS ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    public synchronized String getOponentePendiente() { return oponentePendiente; }
    public synchronized void setOponentePendiente(String nombre) { this.oponentePendiente = nombre; }
    
    // Métodos para manejar la colección de juegos usando ID
    public synchronized JuegoGato getJuegoConID(String juegoID) {
        return juegosActivos.get(juegoID);
    }
    public synchronized void agregarJuego(String juegoID, JuegoGato juego) {
        juegosActivos.put(juegoID, juego);
    }
    public synchronized void removerJuego(String juegoID) {
        juegosActivos.remove(juegoID);
    }
    public synchronized boolean estaEnJuego() { 
        return !this.juegosActivos.isEmpty(); 
    }
    public synchronized ConcurrentHashMap<String, JuegoGato> getJuegosActivos() {
        return juegosActivos;
    }
    
    // --- MÉTODOS INTERNOS  ---
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        Login login = new Login();
        if (login.iniciarSesion(nombre, password)) {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            return true;
        }
        return false;
    }
    
    public void manejarLogoutInterno() {
        if (this.logueado) {
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
            this.mensajesEnviados = 0;
            
            // Terminar todos los juegos al cerrar sesión
            for(JuegoGato juego : juegosActivos.values()) {
                juego.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' ha cerrado sesión.");
            }
        }
    }
    
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "1. Enviar mensajes usando @ID o @NombreUsuario.\n" +
                                 "2. Usar /registrar, /login, /logout, /block, /unblock.\n" +
                                 "3. Para juegos: /jugar NombreOponente, /aceptar NombreRetador.");
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                // --- MANEJO DE COMANDOS ESPECIALES Y JUEGO ---
                String[] partes = mensaje.trim().split(" ", 3); // Dividir el mensaje en hasta 3 partes: comando, arg1, arg2

                if (mensaje.startsWith("/")) {
                    String comando = partes[0].toLowerCase();

                    // --- Comandos de Juego Modificados (ID-based) ---
                    if (comando.equals("/jugada") && partes.length == 3) {
                        // Formato: /jugada ID Fila,Columna
                        String juegoID = partes[1];
                        String jugada = partes[2];
                        
                        JuegoGato juego = this.getJuegoConID(juegoID);
                        if (juego != null) {
                            juego.manejarJugada(this, jugada);
                        } else {
                            this.salida.writeUTF("No tienes una partida activa con el ID '" + juegoID + "'.");
                        }
                        continue;
                    }
                    if (comando.equals("/salirjuego") && partes.length == 2) {
                        // Formato: /salirjuego ID
                        String juegoID = partes[1];
                        
                        JuegoGato juego = this.getJuegoConID(juegoID);
                        if (juego != null) {
                            juego.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' ha abandonado la partida con ID " + juegoID + ".");
                        } else {
                            this.salida.writeUTF("No tienes una partida activa con el ID '" + juegoID + "'.");
                        }
                        continue;
                    }
                    
                    // Comandos de Autenticación
                    if (comando.equals("/registrar")) {
                        manejadorAutenticacion.manejarRegistro(entrada, salida, this);
                        continue;
                    }
                    if (comando.equals("/login")) {
                        manejadorAutenticacion.manejarLogin(entrada, salida, this);
                        continue;
                    }
                    
                    // Comandos de Usuario
                    if (comando.equals("/logout")) {
                        manejadorComandos.manejarLogout(salida, this);
                        continue;
                    }
                    if (comando.startsWith("/block")) {
                        manejadorComandos.manejarBloqueo(mensaje, salida, this);
                        continue;
                    }
                    if (comando.startsWith("/unblock")) {
                        manejadorComandos.manejarDesbloqueo(mensaje, salida, this);
                        continue;
                    }

                    // Comandos de Juego (No usan ID, solo NombreOponente)
                    if (comando.equals("/jugar") && partes.length == 2) { 
                        manejadorJuegos.manejarJugar(mensaje, salida, this); // Uso: /jugar NombreOponente
                        continue;
                    }
                    if (comando.equals("/aceptar") && partes.length == 2) {
                        manejadorJuegos.manejarAceptar(mensaje, salida, this); // Uso: /aceptar NombreRetador
                        continue;
                    }
                }
                
                // --- 2. MANEJO DE MENSAJES (Si no es un comando) ---
                if (!this.logueado && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Límite de 3 mensajes alcanzado. Por favor, inicia sesión.");
                    continue;
                }
                
                manejadorMensajes.enrutarMensaje(this, mensaje, this.logueado);

            } catch (Exception ex) {
                // --- MANEJO DE DESCONEXIÓN  ---
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                // Terminar todas las partidas activas
                for(JuegoGato juego : this.juegosActivos.values()) {
                    juego.terminarJuego(this, "El oponente '" + this.getNombreUsuario() + "' se ha desconectado.");
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