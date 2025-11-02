package com.servidormulti;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManejadorMensajes {

    // Regex para capturar: @g "Nombre de Grupo" mensaje
    private static final Pattern PATRON_GRUPO = Pattern.compile("^@g\\s+\"([^\"]+)\"\\s+(.+)");

    private final Map<String, UnCliente> clientesConectados;
    private final GrupoDB grupoDB;
    private final MensajeDB mensajeDB;
    private final BloqueoDB bloqueoDB;

    public ManejadorMensajes(Map<String, UnCliente> clientes, GrupoDB gdb, MensajeDB mdb, BloqueoDB bdb) {
        this.clientesConectados = clientes;
        this.grupoDB = gdb;
        this.mensajeDB = mdb;
        this.bloqueoDB = bdb;
    }

    /**
     * Busca un cliente CONECTADO por ID o Nombre.
     */
    private UnCliente buscarClienteConectado(String identificador) {
        // busca por ID numerico
        UnCliente cliente = clientesConectados.get(identificador);
        if (cliente != null) {
            return cliente;
        }
        // si no lo encuentra por ID, lo busca por nombre de usuario
        for (UnCliente c : clientesConectados.values()) {
            if (c.getNombreUsuario().equalsIgnoreCase(identificador)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Enruta el mensaje: lo guarda en la BD y lo distribuye a los usuarios conectados.
     */
    public void enrutarMensaje(UnCliente remitente, String mensaje) throws IOException {
        String nombreRemitente = remitente.getNombreUsuario();
        
        // --- 1. MENSAJE DE GRUPO (@g "grupo" ...) ---
        Matcher matcherGrupo = PATRON_GRUPO.matcher(mensaje);
        if (matcherGrupo.find()) {
            if (!remitente.estaLogueado()) {
                remitente.salida.writeUTF("Error: Debes iniciar sesión para enviar mensajes a grupos.");
                return;
            }
            String nombreGrupo = matcherGrupo.group(1);
            String contenido = matcherGrupo.group(2);
            manejarMensajeGrupo(remitente, nombreGrupo, contenido);
            return;
        }

        // --- 2. MENSAJE PRIVADO (@usuario ...) ---
        if (mensaje.startsWith("@")) {
            if (!remitente.estaLogueado()) {
                remitente.salida.writeUTF("Error: Debes iniciar sesión para enviar mensajes privados.");
                return;
            }
            manejarMensajePrivado(remitente, mensaje);
            return;
        }
        
        // --- 3. MENSAJE A "Todos" (Default) ---
        // Incrementar contador si no está logueado
        if (!remitente.estaLogueado()) {
            remitente.incrementarMensajesEnviados();
        }
        manejarMensajeGrupo(remitente, "Todos", mensaje);
    }

    /**
     * Guarda y distribuye un mensaje de GRUPO.
     */
    private void manejarMensajeGrupo(UnCliente remitente, String nombreGrupo, String contenido) throws IOException {
        String nombreRemitente = remitente.getNombreUsuario();

        Integer grupoId = grupoDB.getGrupoId(nombreGrupo);
        if (grupoId == null) {
            remitente.salida.writeUTF("Error: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }

        // 1. Guardar mensaje en la BD
        long nuevoMensajeId = mensajeDB.guardarMensajeGrupo(grupoId, nombreRemitente, contenido);
        if (nuevoMensajeId == -1) {
            remitente.salida.writeUTF("Error: No se pudo guardar el mensaje en el grupo.");
            return;
        }

        // 2. Obtener lista de miembros (o todos si es "Todos")
        List<String> miembros;
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            // "Todos" incluye invitados (clientes conectados) y usuarios logueados
            miembros = new ArrayList<>(clientesConectados.keySet()); // IDs de conectados
        } else {
            miembros = grupoDB.getMiembrosGrupo(grupoId); // Nombres de usuarios
        }

        // 3. Distribuir a miembros conectados
        String msgFormateado = String.format("<%s> %s: %s", nombreGrupo, nombreRemitente, contenido);
        
        for (String identificadorMiembro : miembros) {
            UnCliente clienteDestino = buscarClienteConectado(identificadorMiembro);
            
            if (clienteDestino != null && !clienteDestino.clienteID.equals(remitente.clienteID)) {
                
                // Checar bloqueo
                if (clienteDestino.estaLogueado()) {
                    if (bloqueoDB.estaBloqueado(clienteDestino.getNombreUsuario(), nombreRemitente)) {
                        continue; // No enviar si el destinatario bloqueó al remitente
                    }
                }
                
                clienteDestino.salida.writeUTF(msgFormateado);
                
                // Actualizar estado de "visto" solo para usuarios logueados
                if (clienteDestino.estaLogueado()) {
                    mensajeDB.actualizarEstadoGrupo(clienteDestino.getNombreUsuario(), grupoId, nuevoMensajeId);
                }
            }
        }
        
        // 4. Actualizar el estado del propio remitente
        mensajeDB.actualizarEstadoGrupo(nombreRemitente, grupoId, nuevoMensajeId);
    }

    /**
     * Guarda y distribuye un mensaje privado.
     */
    private void manejarMensajePrivado(UnCliente remitente, String mensaje) throws IOException {
        int divMensaje = mensaje.indexOf(" ");
        if (divMensaje == -1) {
            remitente.salida.writeUTF("Formato incorrecto. Usa @Nombre_destino contenido.");
            return;
        }

        String nombreDestino = mensaje.substring(1, divMensaje);
        String contenido = mensaje.substring(divMensaje + 1).trim();
        String nombreRemitente = remitente.getNombreUsuario();
        
        if (nombreDestino.equalsIgnoreCase(nombreRemitente)) {
             remitente.salida.writeUTF("No puedes enviarte mensajes privados a ti mismo.");
             return;
        }

        // 1. Verificar si el usuario destino essta en la BD
        if (!mensajeDB.existeUsuario(nombreDestino)) {
            remitente.salida.writeUTF("Error: El usuario '" + nombreDestino + "' no existe.");
            return;
        }
        
        // 2. Checar bloqueo
        if (bloqueoDB.estaBloqueado(nombreDestino, nombreRemitente)) {
            remitente.salida.writeUTF("No se pudo entregar el mensaje a '" + nombreDestino + "' (Bloqueo activo).");
            return;
        }
        
        // 3. Guardar el mensaje privado en la BD
        long nuevoMensajeId = mensajeDB.guardarMensajePrivado(nombreRemitente, nombreDestino, contenido);
        if (nuevoMensajeId == -1) {
            remitente.salida.writeUTF("Error al guardar el mensaje privado.");
            return;
        }
        
        // 4. Intentar entrega si el destino está conectado
        UnCliente clienteDestino = buscarClienteConectado(nombreDestino);
        
        if (clienteDestino != null) {
            String msgFormateado = String.format("[Privado de %s]: %s", nombreRemitente, contenido);
            clienteDestino.salida.writeUTF(msgFormateado);
            
            // 5. Marcar como "visto"
            mensajeDB.marcarPrivadoVisto(nuevoMensajeId);
        } else {
            // No está conectado, lo verá al iniciar sesión.
            remitente.salida.writeUTF("Mensaje enviado (offline) a '" + nombreDestino + "'.");
        }
    }
}