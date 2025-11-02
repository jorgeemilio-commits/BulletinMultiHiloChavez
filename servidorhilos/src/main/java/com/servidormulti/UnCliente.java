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
    private final ManejadorMensajes manejadorMensajes; // El nuevo manejador persistente
    private final ManejadorComandos manejadorComandos; 
    private final ManejadorJuegos manejadorJuegos;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSincronizacion manejadorSincronizacion; // NUEVO
    // --- FIN MANEJADORES ---
    
    // estado del cliente
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    // Campos de Juego
    private volatile String oponentePendiente = null;
    private final ConcurrentHashMap<String, JuegoGato> juegosActivos = new ConcurrentHashMap<>();
    
    UnCliente(Socket s, String id) throws java.io.IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;
        
        // 1. Inicializar objetos DB
        GrupoDB grupoDB = new GrupoDB();
        MensajeDB mensajeDB = new MensajeDB();
        BloqueoDB bloqueoDB = new BloqueoDB();

        // 2. Inicializar manejadores de acciones
        this.manejadorComandos = new ManejadorComandos(); 
        this.manejadorJuegos = new ManejadorJuegos(ServidorMulti.clientes);
        this.manejadorAutenticacion = new ManejadorAutenticacion();
        
        // 3. Inicializar manejadores de mensajería
        this.manejadorSincronizacion = new ManejadorSincronizacion(mensajeDB);
        this.manejadorMensajes = new ManejadorMensajes(ServidorMulti.clientes, grupoDB, mensajeDB, bloqueoDB);
    }

    // --- GETTERS & SETTERS ---
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
    
    
    // --- MÉTODOS INTERNOS MODIFICADOS ---
    
    /**
     * MODIFICADO: Llama al sincronizador después de un login exitoso.
     */
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        Login login = new Login();
        if (login.iniciarSesion(nombre, password)) {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            
            // NUEVO: Asegurarse de que el usuario esté en "Todos"
            // (GrupoDB es instanciado en el constructor)
            new GrupoDB().unirseGrupo("Todos", nombre); 
            
            // NUEVO: Sincronizar mensajes no leídos
            this.manejadorSincronizacion.sincronizarMensajesPendientes(this);
            
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
                                 "  Mensaje a Grupo:   @g \"NombreGrupo\" Hola grupo\n" +
                                 "  Mensaje Privado:   @Usuario Hola\n" +
                                 "--- Comandos de Grupos ---\n" +
                                 "  /creargrupo, /borrargrupo, /unirsegrupo, /saligrupo\n" +
                                 "--- Otros Comandos ---\n" +
                                 "  /login, /registrar, /logout, /block, /unblock\n" +
                                 "  /rangos, /winrate, /jugar, /aceptar, /jugada, /salirjuego");
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                // --- 1. MANEJO DE COMANDOS ESPECIALES ---
                String[] partes = mensaje.trim().split(" ", 3);
                
                if (mensaje.startsWith("/")) {
                    String comando = partes[0].toLowerCase();

                    // --- Comandos de Juego (ID-based) ---
                    if (comando.equals("/jugada") && partes.length == 3) {
                        JuegoGato juego = this.getJuegoConID(partes[1]);
                        if (juego != null) juego.manejarJugada(this, partes[2]);
                        else this.salida.writeUTF("Partida no encontrada.");
                        continue;
                    }
                    if (comando.equals("/salirjuego") && partes.length == 2) {
                        JuegoGato juego = this.getJuegoConID(partes[1]);
                        if (juego != null) juego.terminarJuego(this, "Has abandonado la partida.");
                        else this.salida.writeUTF("Partida no encontrada.");
                        continue;
                    }
                    
                    // --- Comandos de Autenticación ---
                    if (comando.equals("/registrar")) {
                        manejadorAutenticacion.manejarRegistro(entrada, salida, this);
                        continue;
                    }
                    if (comando.equals("/login")) {
                        manejadorAutenticacion.manejarLogin(entrada, salida, this);
                        continue;
                    }
                    
                    // --- Comandos de Usuario/Bloqueo ---
                    if (comando.equals("/logout")) {
                        manejadorComandos.manejarLogout(salida, this);
                        continue;
                    }
                    if (comando.equals("/block")) {
                        manejadorComandos.manejarBloqueo(mensaje, salida, this);
                        continue;
                    }
                    if (comando.equals("/unblock")) {
                        manejadorComandos.manejarDesbloqueo(mensaje, salida, this);
                        continue;
                    }

                    // --- Comandos de Ranking ---
                    if (comando.equals("/rangos")) {
                        manejadorComandos.manejarRangos(salida);
                        continue;
                    }
                    if (comando.equals("/winrate")) {
                        manejadorComandos.manejarWinrate(mensaje, salida, this);
                        continue;
                    }
                    
                    // --- NUEVOS Comandos de Grupo ---
                    if (comando.equals("/creargrupo")) {
                        manejadorComandos.manejarCrearGrupo(partes, salida, this);
                        continue;
                    }
                    if (comando.equals("/borrargrupo")) {
                        manejadorComandos.manejarBorrarGrupo(partes, salida, this);
                        continue;
                    }
                    if (comando.equals("/unirsegrupo")) {
                        manejadorComandos.manejarUnirseGrupo(partes, salida, this);
                        continue;
                    }
                    if (comando.equals("/saligrupo")) {
                        manejadorComandos.manejarSalirGrupo(partes, salida, this);
                        continue;
                    }

                    // --- Comandos de Invitación a Juego ---
                    if (comando.equals("/jugar") && partes.length == 2) { 
                        manejadorJuegos.manejarJugar(mensaje, salida, this);
                        continue;
                    }
                    if (comando.equals("/aceptar") && partes.length == 2) {
                        manejadorJuegos.manejarAceptar(mensaje, salida, this);
                        continue;
                    }
                    
                    // Comando desconocido
                    salida.writeUTF("Comando '" + comando + "' no reconocido.");
                    continue;
                }
                
                // --- 2. MANEJO DE MENSAJES (Si no es un comando) ---
                
                // Límite de mensajes para invitados (solo aplica a mensajes a "Todos")
                if (!this.logueado && !mensaje.startsWith("@") && this.mensajesEnviados >= 3) {
                    this.salida.writeUTF("Límite de 3 mensajes alcanzado. Por favor, inicia sesión.");
                    continue;
                }
                
                // Enviar al nuevo manejador persistente
                manejadorMensajes.enrutarMensaje(this, mensaje);

            } catch (Exception ex) {
                // --- MANEJO DE DESCONEXIÓN  ---
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                // Terminar todas las partidas activas
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