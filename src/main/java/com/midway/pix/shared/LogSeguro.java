package com.midway.pix.shared;

public final class LogSeguro {

    private LogSeguro() {
    }

    public static String mascarar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "não informado";
        }
        if (valor.length() <= 4) {
            return "****";
        }
        return valor.substring(0, 2) + "***" + valor.substring(valor.length() - 2);
    }
}
