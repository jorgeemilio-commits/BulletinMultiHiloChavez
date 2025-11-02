package com.servidormulti;

import java.util.Map;

/**
 * Esta clase crea todas las instancias de servicios una sola vez.
 * Luego, las pasa a las clases que las necesiten.
 */
public class ContextoServidor {

    // Instancias únicas de todos los servicios
    private final ManejadorComandos manejadorComandos;
    private final ManejadorJuegos manejadorJuegos;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSincronizacion manejadorSincronizacion;
    private final ManejadorMensajes manejadorMensajes;
    private final EnrutadorComandos enrutadorComandos; 

    public ContextoServidor(Map<String, UnCliente> clientesConectados) {
        
        // 1. Inicializar objetos DB
        GrupoDB grupoDB = new GrupoDB();
        MensajeDB mensajeDB = new MensajeDB();
        BloqueoDB bloqueoDB = new BloqueoDB();

        // 2. Inicializar manejadores de acciones
        ManejadorRangos manejadorRangos = new ManejadorRangos();
        ManejadorWinrate manejadorWinrate = new ManejadorWinrate();
        ManejadorAccionesGrupo manejadorAccionesGrupo = new ManejadorAccionesGrupo(grupoDB, mensajeDB);

        // 3. Inicializar manejadores principales
        this.manejadorComandos = new ManejadorComandos(manejadorRangos, manejadorWinrate, manejadorAccionesGrupo, bloqueoDB, mensajeDB);
        this.manejadorJuegos = new ManejadorJuegos(clientesConectados);
        this.manejadorAutenticacion = new ManejadorAutenticacion();
        this.manejadorSincronizacion = new ManejadorSincronizacion(mensajeDB);
        this.manejadorMensajes = new ManejadorMensajes(clientesConectados, grupoDB, mensajeDB, bloqueoDB);
        
        // 4. Inicializar el nuevo enrutador
        this.enrutadorComandos = new EnrutadorComandos(this.manejadorComandos, this.manejadorJuegos, this.manejadorAutenticacion);
    }

    // Getters para que UnCliente pueda obtener los servicios
    public ManejadorMensajes getManejadorMensajes() { return manejadorMensajes; }
    public ManejadorSincronizacion getManejadorSincronizacion() { return manejadorSincronizacion; }
    public EnrutadorComandos getEnrutadorComandos() { return enrutadorComandos; }
}