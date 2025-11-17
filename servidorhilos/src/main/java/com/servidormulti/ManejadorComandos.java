package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map; 

public class ManejadorComandos {
    
    // --- MANEJADORES DELEGADOS ---
    private final ManejadorRangos manejadorRangos;
    private final ManejadorWinrate manejadorWinrate;
    private final ManejadorAccionesGrupo manejadorAccionesGrupo;

    // --- OBJETOS DB ---
    private final BloqueoDB bloqueoDB;
    private final MensajeDB mensajeDB;

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorComandos(ManejadorRangos mr, ManejadorWinrate mw, ManejadorAccionesGrupo mag, BloqueoDB bdb, MensajeDB mdb, Map<String, UnCliente> clientes) {
        this.manejadorRangos = mr;
        this.manejadorWinrate = mw;
        this.manejadorAccionesGrupo = mag;
        this.bloqueoDB = bdb;
        this.mensajeDB = mdb;
        this.clientesConectados = clientes; 
    }
    
    private boolean existeUsuarioDB(String nombre) {
        return this.mensajeDB.existeUsuario(nombre);
    }

    public void manejarLogout(DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Ya estás desconectado. Tu nombre es: " + cliente.getNombreUsuario());
            return;
        }
        cliente.manejarLogoutInterno();
        salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + cliente.getNombreUsuario() + "'.");
    }

    public void manejarBloqueo(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para bloquear usuarios.");
            return;
        }
        
        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /block nombre_usuario");
            return;
        }

        String aBloquear = partes[1];
        String miNombre = cliente.getNombreUsuario();
        
        if (aBloquear.equalsIgnoreCase(miNombre)) {
            salida.writeUTF("No puedes bloquearte a ti mismo.");
            return;
        }

        if (!existeUsuarioDB(aBloquear)) {
            salida.writeUTF("Error: El usuario '" + aBloquear + "' no existe en el sistema.");
            return;
        }

        String resultado = bloqueoDB.bloquearUsuario(miNombre, aBloquear);
        salida.writeUTF(resultado);
    }

    public void manejarDesbloqueo(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Debes iniciar sesión para desbloquear usuarios.");
            return;
        }

        String[] partes = comando.trim().split(" ");
        if (partes.length != 2) {
            salida.writeUTF("Uso: /unblock nombre_usuario");
            return;
        }

        String aDesbloquear = partes[1];
        String miNombre = cliente.getNombreUsuario();

        if (aDesbloquear.equalsIgnoreCase(miNombre)) {
            salida.writeUTF("No puedes desbloquearte a ti mismo.");
            return;
        }
        
        String resultado = bloqueoDB.desbloquearUsuario(miNombre, aDesbloquear);
        salida.writeUTF(resultado);
    }
    
    
    // --- (Rangos/Winrate) ---

    public void manejarRangos(DataOutputStream salida) throws IOException {
        manejadorRangos.ejecutar(salida);
    }

    public void manejarWinrate(String comando, DataOutputStream salida, UnCliente cliente) throws IOException {
        manejadorWinrate.ejecutar(comando, salida, cliente);
    }

    // -- Conectados --
    public void manejarConectados(DataOutputStream salida) throws IOException {
        StringBuilder lista = new StringBuilder("--- Usuarios Conectados ---\n");
        int contador = 0;
        for (UnCliente cliente : clientesConectados.values()) {
            if (cliente.estaLogueado()) {
                lista.append("- ").append(cliente.getNombreUsuario()).append("\n");
                contador++;
            }
        }

        if (contador == 0) {
            salida.writeUTF("No hay usuarios autenticados conectados en este momento.");
        } else {
            lista.append("Total: ").append(contador);
            salida.writeUTF(lista.toString());
        }
    }

    // ---  (Grupos) ---
    
    public void manejarCrearGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        manejadorAccionesGrupo.crearGrupo(partes, salida, cliente);
    }
    
    public void manejarBorrarGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        manejadorAccionesGrupo.borrarGrupo(partes, salida, cliente);
    }
    
    public void manejarUnirseGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        manejadorAccionesGrupo.unirseGrupo(partes, salida, cliente);
    }

    public void manejarSalirGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        manejadorAccionesGrupo.salirGrupo(partes, salida, cliente);
    }

    public void manejarAyuda(DataOutputStream salida, UnCliente cliente) throws IOException {
        StringBuilder ayuda = new StringBuilder("--- Lista de Comandos Disponibles ---\n");
        

    // -- comandos de ayuda especificados --
    
        // Comandos de Autenticación
        ayuda.append("--- Autenticación ---\n");
        ayuda.append("  /login         - Inicia sesión con tu cuenta.\n");
        ayuda.append("  /registrar     - Crea una nueva cuenta.\n");
        ayuda.append("  /logout        - Cierra tu sesión actual.\n");

        // Comandos Sociales y de Grupos
        ayuda.append("--- Grupos y Social ---\n");
        ayuda.append("  /creargrupo <nombre>   - Crea un nuevo grupo.\n");
        ayuda.append("  /borrargrupo <nombre>  - Borra un grupo, no puedes borrar TODOS.\n");
        ayuda.append("  /unirsegrupo <nombre>  - Te une a un grupo existente.\n");
        ayuda.append("  /salirgrupo <nombre>   - Te saca de un grupo.\n");
        ayuda.append("  /block <usuario>       - Bloquea a un usuario (mensajes y juegos).\n");
        ayuda.append("  /unblock <usuario>     - Desbloquea a un usuario.\n");

        // Comandos de Estadísticas y Retos
        ayuda.append("--- Estadísticas y Juegos ---\n");
        ayuda.append("  /conectados    - Muestra la lista de usuarios conectados.\n");
        ayuda.append("  /rangos        - Muestra el ranking de jugadores.\n");
        ayuda.append("  /winrate <oponente> - Muestra tu H2H contra un oponente.\n");
        ayuda.append("  /jugar <oponente>     - Reta a un jugador a una partida de Gato.\n");
        ayuda.append("  /aceptar <retador>    - Si te enviaron una invitacion de juego la acepta.\n");
        
        // Comandos Contextuales (Solo si está en juego)
        if (cliente.estaEnJuego()) {
            ayuda.append("--- Comandos de Partida (Activos) ---\n");
            ayuda.append("  /jugada <ID_Juego> <fila>,<col> - Realiza un movimiento (Ej: /jugada 12 1,1).\n");
            ayuda.append("  /salirjuego <ID_Juego>        - Si estas en una partida, abandona la partida en curso.\n");
        }

        salida.writeUTF(ayuda.toString());
    }
}