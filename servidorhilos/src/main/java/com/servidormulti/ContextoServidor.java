package com.servidormulti;

import java.util.Map;

public class ContextoServidor {

    // --- CAMPOS PRIVADOS ---
    
    private final Map<String, UnCliente> clientesConectados; 

    // Instancias Únicas de Servicios (Singleton)
    private final GrupoDB grupoDB;
    private final MensajeDB mensajeDB;
    private final BloqueoDB bloqueoDB;
    
    private final ManejadorComandos manejadorComandos;
    private final ManejadorJuegos manejadorJuegos;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSincronizacion manejadorSincronizacion;
    private final ManejadorMensajes manejadorMensajes;
    private final EnrutadorComandos enrutadorComandos;

    public ContextoServidor(Map<String, UnCliente> clientesConectados) {
        // Ahora esta línea es válida
        this.clientesConectados = clientesConectados; 

        // 1. Inicializar objetos DB
        this.grupoDB = new GrupoDB();
        this.mensajeDB = new MensajeDB();
        this.bloqueoDB = new BloqueoDB();

        // 2. Inicializar manejadores de acciones
        ManejadorRangos manejadorRangos = new ManejadorRangos();
        ManejadorWinrate manejadorWinrate = new ManejadorWinrate();
        
        // 3. Crear el sincronizador
        this.manejadorSincronizacion = new ManejadorSincronizacion(mensajeDB);

        // 4. Crear el manejador de acciones de grupo 
        ManejadorAccionesGrupo manejadorAccionesGrupo = new ManejadorAccionesGrupo(grupoDB, mensajeDB, this.manejadorSincronizacion);

        // 5. Inicializar manejadores principales
        this.manejadorComandos = new ManejadorComandos(manejadorRangos, manejadorWinrate, manejadorAccionesGrupo, bloqueoDB, mensajeDB);
        this.manejadorJuegos = new ManejadorJuegos(clientesConectados, this.bloqueoDB);
        this.manejadorAutenticacion = new ManejadorAutenticacion(clientesConectados);
        this.manejadorMensajes = new ManejadorMensajes(clientesConectados, grupoDB, mensajeDB, bloqueoDB);
        
        // 6. Inicializar el nuevo enrutador
        this.enrutadorComandos = new EnrutadorComandos(
            this.manejadorComandos, 
            this.manejadorJuegos, 
            this.manejadorAutenticacion
        );
    }

    // --- Getters ---

    public ManejadorMensajes getManejadorMensajes() { return manejadorMensajes; }
    public ManejadorSincronizacion getManejadorSincronizacion() { return manejadorSincronizacion; }
    public EnrutadorComandos getEnrutadorComandos() { return enrutadorComandos; }
}