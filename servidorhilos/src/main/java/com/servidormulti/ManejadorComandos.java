package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ManejadorComandos {
    
    // --- MANEJADORES DELEGADOS ---
    private final ManejadorRangos manejadorRangos;
    private final ManejadorWinrate manejadorWinrate;
    private final ManejadorAccionesGrupo manejadorAccionesGrupo; // NUEVO

    // --- OBJETOS DB  ---
    private final BloqueoDB bloqueoDB;
    private final MensajeDB mensajeDB; // Para 'existeUsuarioDB'

    public ManejadorComandos() {
        this.manejadorRangos = new ManejadorRangos();
        this.manejadorWinrate = new ManejadorWinrate();
        
        // Instanciar los objetos DB necesarios
        GrupoDB grupoDB = new GrupoDB();
        this.mensajeDB = new MensajeDB();
        this.bloqueoDB = new BloqueoDB();
        this.manejadorAccionesGrupo = new ManejadorAccionesGrupo(grupoDB, mensajeDB);
    }
    
    /**
     *  Usa la instancia de MensajeDB
     */
    private boolean existeUsuarioDB(String nombre) {
        // Llama a MensajeDB
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
}