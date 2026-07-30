package br.com.topsdojob.v3.application.admin.usuario;

import java.util.List;

public class AdminUsuarioExclusaoBloqueadaException extends RuntimeException {

    private final List<String> bloqueios;

    public AdminUsuarioExclusaoBloqueadaException(List<String> bloqueios) {
        super("A conta possui vinculos que precisam ser preservados.");
        this.bloqueios = List.copyOf(bloqueios);
    }

    public List<String> bloqueios() {
        return bloqueios;
    }
}
