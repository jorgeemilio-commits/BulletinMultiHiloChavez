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
    
    // estado del cliente
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;
        this.manejadorMensajes = new ManejadorMensajes(ServidorMulti.clientes); 
        this.manejadorComandos = new ManejadorComandos(); // Inicialización
    }

    // --- GETTERS & SETTERS ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    
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
            // Reinicia el nombre a invitado, conservando su ID numérico original.
            this.nombreUsuario = "Invitado-" + this.clienteID; 
            // Reinicia el contador de mensajes
            this.mensajesEnviados = 0;
        }
    }
    
    @Override
    public void run() {
        

        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "1. Enviar mensajes usando @ID o @NombreUsuario.\n" +
                                 "2. Usar /registrar, /login, /logout, /block nombre, /unblock nombre.");
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

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

                // 2. Verificar Límite y Enviar Mensajes
                if (!this.logueado && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Límite de 3 mensajes alcanzado. Por favor, inicia sesión.");
                    continue;
                }
                
                manejadorMensajes.enrutarMensaje(this, mensaje, this.logueado);

            } catch (Exception ex) {
                // Manejar desconexión
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
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