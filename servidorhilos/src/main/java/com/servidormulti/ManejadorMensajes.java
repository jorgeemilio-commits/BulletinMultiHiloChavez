package com.servidormulti;

import java.util.Map;
import java.io.IOException;

public class ManejadorMensajes {

    private final Map<String, UnCliente> clientes;

    public ManejadorMensajes(Map<String, UnCliente> clientes) {
        this.clientes = clientes;
    }

    /**
     * Busca un cliente en el HashMap por ID numérico (clave) o por Nombre de Usuario.
     * @param identificador ID numérico o nombre de usuario.
     * @return Cliente UnCliente encontrado, o null.
     */
    private UnCliente buscarCliente(String identificador) {
        // busca por ID numerico
        UnCliente cliente = clientes.get(identificador);
        if (cliente != null) {
            return cliente;
        }

        // si no lo encuentra por ID, lo busca por nombre de usuario
        for (UnCliente c : clientes.values()) {
            if (c.getNombreUsuario().equalsIgnoreCase(identificador)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Procesa y enruta el mensaje
     * @param remitenteCliente El objeto UnCliente que envía el mensaje.
     * @param mensaje El contenido completo del mensaje.
     * @param logueado Indica si el remitente está logueado (para el contador).
     * @return true si el mensaje fue enviado a al menos un destinatario.
     * @throws IOException
     */
    public boolean enrutarMensaje(UnCliente remitenteCliente, String mensaje, boolean logueado) throws IOException {
        
        String remitente = remitenteCliente.getNombreUsuario();
        boolean mensajeEnviado = false;
        int mensajesEnviados = remitenteCliente.getMensajesEnviados(); // Obtener el contador

        if (mensaje.startsWith("@")) {
            // @tageo
            int divMensaje = mensaje.indexOf(" ");
            if (divMensaje == -1) {
                remitenteCliente.salida.writeUTF("Formato de mensaje incorrecto. Usa @ID_o_Nombre_destino contenido.");
                return false;
            }

            String destino = mensaje.substring(1, divMensaje);
            String contenido = mensaje.substring(divMensaje + 1).trim();
            String[] partes = destino.split("-");

            for (String aQuien : partes) {
                UnCliente cliente = buscarCliente(aQuien); 
                
                if (cliente != null) { 
                    // no se envia a si mismo
                    if (!cliente.clienteID.equals(remitenteCliente.clienteID)) { 
                        cliente.salida.writeUTF(remitente + ": " + contenido + " (" + (mensajesEnviados + 1) + ")");
                        mensajeEnviado = true;
                    }
                }
            }
            
            if (!mensajeEnviado) {
                remitenteCliente.salida.writeUTF("Ningún destinatario fue encontrado con ese(esos) identificador(es).");
            }

        } else {
            // mensaje general
            for (UnCliente unCliente : clientes.values()) {
                // no se envia a si mismo
                if (!unCliente.clienteID.equals(remitenteCliente.clienteID)) { 
                    unCliente.salida.writeUTF(remitente + ": " + mensaje + " (" + (mensajesEnviados + 1) + ")");
                    mensajeEnviado = true;
                }
            }
        }
        
        // si se envio y no esta logueado, incrementa el contador
        if (mensajeEnviado && !logueado) {
            remitenteCliente.incrementarMensajesEnviados();
        }

        return mensajeEnviado;
    }
}