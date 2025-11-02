package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;

public class ManejadorAccionesGrupo {

    private final GrupoDB grupoDB;
    private final MensajeDB mensajeDB;
    private final ManejadorSincronizacion manejadorSincronizacion; // NUEVO

    /**
     * MODIFICADO: Se añade el sincronizador
     */
    public ManejadorAccionesGrupo(GrupoDB grupoDB, MensajeDB mensajeDB, ManejadorSincronizacion manejadorSincronizacion) {
        this.grupoDB = grupoDB;
        this.mensajeDB = mensajeDB;
        this.manejadorSincronizacion = manejadorSincronizacion; // NUEVO
    }

    /**
     * Validador simple (sin cambios)
     */
    private boolean validarComando(String[] partes, int longitudEsperada, DataOutputStream salida, UnCliente cliente, String uso) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Error: Debes iniciar sesión para gestionar grupos.");
            return false;
        }
        if (partes.length != longitudEsperada) {
            salida.writeUTF("Uso incorrecto. " + uso);
            return false;
        }
        return true;
    }

    /**
     * Maneja /creargrupo <nombre> (sin cambios)
     */
    public void crearGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /creargrupo <nombre_grupo>")) return;
        
        String nombreGrupo = partes[1];
        String resultado = grupoDB.crearGrupo(nombreGrupo);
        salida.writeUTF(resultado);
        
        // Si se crea exitosamente, unir automáticamente al creador
        if (resultado.contains("exitosamente")) {
            unirseGrupo(partes, salida, cliente);
        }
    }

    /**
     * Maneja /borrargrupo <nombre> (sin cambios)
     */
    public void borrarGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /borrargrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String resultado = grupoDB.borrarGrupo(nombreGrupo);
        salida.writeUTF(resultado);
    }

    /**
     * Maneja /unirsegrupo <nombre>
     * MODIFICADO: Ahora llama al sincronizador.
     */
    public void unirseGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /unirsegrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String nombreUsuario = cliente.getNombreUsuario();
        
        String resultado = grupoDB.unirseGrupo(nombreGrupo, nombreUsuario);
        salida.writeUTF(resultado);
        
        // --- NUEVO ---
        // Si el resultado fue exitoso (o si ya era miembro), sincroniza el historial.
        if (resultado.contains("Te has unido") || resultado.contains("Ya eras miembro")) {
            manejadorSincronizacion.sincronizarHistorialGrupo(cliente, nombreGrupo);
        }
        // --- FIN NUEVO ---
    }
    
    /**
     * Maneja /saligrupo <nombre> (sin cambios)
     */
    public void salirGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /saligrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String nombreUsuario = cliente.getNombreUsuario();
        
        String resultado = grupoDB.salirGrupo(nombreGrupo, nombreUsuario);
        salida.writeUTF(resultado);
    }
}