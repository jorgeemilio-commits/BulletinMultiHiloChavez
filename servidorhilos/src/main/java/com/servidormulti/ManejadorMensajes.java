package com.servidormulti;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManejadorMensajes {


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
                    
                    String nombreDestino = clienteDestino.getNombreUsuario();

                    if (bloqueoDB.estaBloqueado(nombreDestino, nombreRemitente)) {
                        continue; 
                    }

                    if (bloqueoDB.estaBloqueado(nombreRemitente, nombreDestino)) {
                        continue; 
                    }

                }

                clienteDestino.salida.writeUTF(msgFormateado);
                
                if (clienteDestino.estaLogueado()) {
                    mensajeDB.actualizarEstadoGrupo(clienteDestino.getNombreUsuario(), grupoId, nuevoMensajeId);
                }
            }
        }
        // Actualiza el estado para el propio remitente (para que no reciba sus propios mensajes)
        mensajeDB.actualizarEstadoGrupo(nombreRemitente, grupoId, nuevoMensajeId);
    }

    private void manejarMensajePrivado(UnCliente remitente, String mensaje) throws IOException {
        int divMensaje = mensaje.indexOf(" ");
        if (divMensaje == -1) {
            remitente.salida.writeUTF("Formato incorrecto. Usa @Nombre o @Nombre1-Nombre2 contenido.");
            return;
        }

        String bloqueDestinos = mensaje.substring(1, divMensaje);
        String contenido = mensaje.substring(divMensaje + 1).trim();
        String nombreRemitente = remitente.getNombreUsuario();
        
        // Dividir el bloque de destinatarios por el guion
        String[] destinatarios = bloqueDestinos.split("-");

        // Iterar sobre cada destinatario
        for (String nombreDestino : destinatarios) {
            
            if (nombreDestino.trim().isEmpty()) continue;

            if (nombreDestino.equalsIgnoreCase(nombreRemitente)) {
                remitente.salida.writeUTF("No puedes enviarte mensajes privados a ti mismo.");
                continue; // Saltar al siguiente destinatario
            }

            // 1. Verificar si el usuario destino existe en la BD
            if (!mensajeDB.existeUsuario(nombreDestino)) {
                remitente.salida.writeUTF("Error: El usuario '" + nombreDestino + "' no existe.");
                continue; // Saltar al siguiente destinatario
            }
            
            // 2. Checar bloqueo (DESTINO bloqueó al REMITENTE)
            if (bloqueoDB.estaBloqueado(nombreDestino, nombreRemitente)) {
                remitente.salida.writeUTF("No se pudo entregar el mensaje a '" + nombreDestino + "' (Bloqueo activo).");
                continue; // Saltar al siguiente destinatario
            }
            
            // --- CAMBIO (Bug 3): Añadir esta verificación ---
            // (REMITENTE bloqueó al DESTINO)
            if (bloqueoDB.estaBloqueado(nombreRemitente, nombreDestino)) {
                remitente.salida.writeUTF("No puedes enviar mensajes a '" + nombreDestino + "' porque lo tienes bloqueado.");
                continue; // Saltar al siguiente destinatario
            }
            // --- FIN DEL CAMBIO ---
            
            // 3. Guardar el mensaje privado en la BD (estado "no visto")
            long nuevoMensajeId = mensajeDB.guardarMensajePrivado(nombreRemitente, nombreDestino, contenido);
            if (nuevoMensajeId == -1) {
                remitente.salida.writeUTF("Error al guardar el mensaje privado para '" + nombreDestino + "'.");
                continue; // Saltar al siguiente destinatario
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
}