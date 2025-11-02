package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;

public class ManejadorAccionesGrupo {

    private final GrupoDB grupoDB;
    private final MensajeDB mensajeDB;

    public ManejadorAccionesGrupo(GrupoDB grupoDB, MensajeDB mensajeDB) {
        this.grupoDB = grupoDB;
        this.mensajeDB = mensajeDB;
    }

    /**
     * Validador simple para comandos de grupo.
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
     * Maneja /creargrupo <nombre>
     */
    public void crearGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /creargrupo <nombre_grupo>")) return;
        
        String nombreGrupo = partes[1];
        String resultado = grupoDB.crearGrupo(nombreGrupo);
        salida.writeUTF(resultado);
        
        // Si se crea exitosamente, se une el creador al grupo
        if (resultado.contains("exitosamente")) {
            unirseGrupo(partes, salida, cliente);
        }
    }

    /**
     * Maneja /borrargrupo <nombre>
     */
    public void borrarGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /borrargrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String resultado = grupoDB.borrarGrupo(nombreGrupo);
        salida.writeUTF(resultado);
    }

    /**
     * Maneja /unirsegrupo <nombre>
     */
    public void unirseGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /unirsegrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String nombreUsuario = cliente.getNombreUsuario();
        
        String resultado = grupoDB.unirseGrupo(nombreGrupo, nombreUsuario);
        salida.writeUTF(resultado);
    }
    
    /**
     * Maneja /saligrupo <nombre>
     */
    public void salirGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!validarComando(partes, 2, salida, cliente, "Uso: /saligrupo <nombre_grupo>")) return;

        String nombreGrupo = partes[1];
        String nombreUsuario = cliente.getNombreUsuario();
        
        String resultado = grupoDB.salirGrupo(nombreGrupo, nombreUsuario);
        salida.writeUTF(resultado);
    }
}