package com.servidormulti;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManejadorMensajes {

    // --- Regex MODIFICADO ---
    // Antes: ^@g\s+\"([^\"]+)\"\s+(.+)
    // Ahora: ^#([\w\-]+)\s+(.+)
    // Captura: #NombreDeGrupo-123 Hola qué tal
    private static final Pattern PATRON_GRUPO = Pattern.compile("^#([\\w\\-]+)\\s+(.+)");

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

    // ... (buscarClienteConectado sigue igual) ...
    private UnCliente buscarClienteConectado(String identificador) {
        UnCliente cliente = clientesConectados.get(identificador);
        if (cliente != null) return cliente;
        for (UnCliente c : clientesConectados.values()) {
            if (c.getNombreUsuario().equalsIgnoreCase(identificador)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Enruta el mensaje: lo guarda en la BD y lo distribuye.
     */
    public void enrutarMensaje(UnCliente remitente, String mensaje) throws IOException {
        String nombreRemitente = remitente.getNombreUsuario();
        
        // --- 1. MENSAJE DE GRUPO (#grupo ...) ---
        Matcher matcherGrupo = PATRON_GRUPO.matcher(mensaje);
        if (matcherGrupo.find()) {
            if (!remitente.estaLogueado()) {
                remitente.salida.writeUTF("Error: Debes iniciar sesión para enviar mensajes a grupos.");
                return;
            }
            String nombreGrupo = matcherGrupo.group(1); // Grupo 1 es el nombre
            String contenido = matcherGrupo.group(2); // Grupo 2 es el mensaje
            manejarMensajeGrupo(remitente, nombreGrupo, contenido);
            return;
        }

        // --- 2. MENSAJE PRIVADO (@usuario ...) ---
        // (Esta lógica ahora es 100% limpia, no hay conflicto)
        if (mensaje.startsWith("@")) {
            if (!remitente.estaLogueado()) {
                remitente.salida.writeUTF("Error: Debes iniciar sesión para enviar mensajes privados.");
                return;
            }
            manejarMensajePrivado(remitente, mensaje);
            return;
        }
        
        // --- 3. MENSAJE A "Todos" (Default) ---
        if (!remitente.estaLogueado()) {
            remitente.incrementarMensajesEnviados();
        }
        manejarMensajeGrupo(remitente, "Todos", mensaje);
    }

    // ... (manejarMensajeGrupo sigue igual) ...
    private void manejarMensajeGrupo(UnCliente remitente, String nombreGrupo, String contenido) throws IOException {
        String nombreRemitente = remitente.getNombreUsuario();
        Integer grupoId = grupoDB.getGrupoId(nombreGrupo);
        if (grupoId == null) {
            remitente.salida.writeUTF("Error: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        long nuevoMensajeId = mensajeDB.guardarMensajeGrupo(grupoId, nombreRemitente, contenido);
        if (nuevoMensajeId == -1) {
            remitente.salida.writeUTF("Error: No se pudo guardar el mensaje en el grupo.");
            return;
        }
        List<String> miembros;
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            miembros = new ArrayList<>(clientesConectados.keySet());
        } else {
            miembros = grupoDB.getMiembrosGrupo(grupoId);
        }
        String msgFormateado = String.format("<%s> %s: %s", nombreGrupo, nombreRemitente, contenido);
        for (String identificadorMiembro : miembros) {
            UnCliente clienteDestino = buscarClienteConectado(identificadorMiembro);
            if (clienteDestino != null && !clienteDestino.clienteID.equals(remitente.clienteID)) {
                if (clienteDestino.estaLogueado()) {
                    if (bloqueoDB.estaBloqueado(clienteDestino.getNombreUsuario(), nombreRemitente)) {
                        continue;
                    }
                }
                clienteDestino.salida.writeUTF(msgFormateado);
                if (clienteDestino.estaLogueado()) {
                    mensajeDB.actualizarEstadoGrupo(clienteDestino.getNombreUsuario(), grupoId, nuevoMensajeId);
                }
            }
        }
        mensajeDB.actualizarEstadoGrupo(nombreRemitente, grupoId, nuevoMensajeId);
    }

    // ... (manejarMensajePrivado sigue igual) ...
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
        if (!mensajeDB.existeUsuario(nombreDestino)) {
            remitente.salida.writeUTF("Error: El usuario '" + nombreDestino + "' no existe.");
            return;
        }
        if (bloqueoDB.estaBloqueado(nombreDestino, nombreRemitente)) {
            remitente.salida.writeUTF("No se pudo entregar el mensaje a '" + nombreDestino + "' (Bloqueo activo).");
            return;
        }
        long nuevoMensajeId = mensajeDB.guardarMensajePrivado(nombreRemitente, nombreDestino, contenido);
        if (nuevoMensajeId == -1) {
            remitente.salida.writeUTF("Error al guardar el mensaje privado.");
            return;
        }
        UnCliente clienteDestino = buscarClienteConectado(nombreDestino);
        if (clienteDestino != null) {
            String msgFormateado = String.format("[Privado de %s]: %s", nombreRemitente, contenido);
            clienteDestino.salida.writeUTF(msgFormateado);
            mensajeDB.marcarPrivadoVisto(nuevoMensajeId);
        } else {
            remitente.salida.writeUTF("Mensaje enviado (offline) a '" + nombreDestino + "'.");
        }
    }
}