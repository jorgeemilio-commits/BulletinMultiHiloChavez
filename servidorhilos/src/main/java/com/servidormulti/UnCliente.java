package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    // ATRIBUTOS FINALES
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    // NUEVOS OBJETOS AUXILIARES
    private final ManejadorMensajes manejadorMensajes; 
    private final ManejadorComandos manejadorComandos; 
    
    // ESTADO DEL CLIENTE
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

    // --- GETTERS & SETTERS (Para uso de Clases Auxiliares) ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    
    // Método interno para el login (llamado por ManejadorComandos)
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        Login login = new Login();
        if (login.iniciarSesion(nombre, password)) {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            return true;
        }
        return false;
    }
    
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "1. Enviar mensajes usando @ID o @NombreUsuario.\n" +
                                 "2. Usar /registrar para crear una cuenta.\n" +
                                 "3. Usar /login para iniciar sesión.");
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