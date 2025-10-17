package com.servidormulti;

import java.util.Map;
import java.io.IOException;

public class ManejadorMensajes {

    private final Map<String, UnCliente> clientes;

    public ManejadorMensajes(Map<String, UnCliente> clientes) {
        this.clientes = clientes;
    }

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

    public boolean enrutarMensaje(UnCliente remitenteCliente, String mensaje, boolean logueado) throws IOException {
        
        String remitente = remitenteCliente.getNombreUsuario();
        boolean mensajeEnviado = false;
        
        // Se elimina la impresión del contador en el mensaje, solo se usa para el límite.
        
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
            
            BloqueoDB bloqueoDB = new BloqueoDB();

            for (String aQuien : partes) {
                UnCliente cliente = buscarCliente(aQuien); 
                
                if (cliente != null) { 
                    // 1. checar bloqueo
                    if (bloqueoDB.estaBloqueado(cliente.getNombreUsuario(), remitente)) {
                        remitenteCliente.salida.writeUTF("No se pudo entregar el mensaje a '" + cliente.getNombreUsuario() + "' (Bloqueo activo).");
                        continue; 
                    }
                    
                    // 2. no se envia a si mismo
                    if (!cliente.clienteID.equals(remitenteCliente.clienteID)) { 
                        cliente.salida.writeUTF(remitente + ": " + contenido);
                        mensajeEnviado = true;
                    }
                }
            }
            
            if (!mensajeEnviado) {
                remitenteCliente.salida.writeUTF("Ningún destinatario fue encontrado con ese(esos) identificador(es).");
            }

        } else {
            // mensaje general
            BloqueoDB bloqueoDB = new BloqueoDB();

            for (UnCliente unCliente : clientes.values()) {
                // no se envia a si mismo
                if (!unCliente.clienteID.equals(remitenteCliente.clienteID)) { 
                    
                    // checar bloqueo
                    if (bloqueoDB.estaBloqueado(unCliente.getNombreUsuario(), remitente)) {
                        // si esta bloqueado, no enviar
                        continue;
                    }

                    unCliente.salida.writeUTF(remitente + ": " + mensaje);
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