package br.com.topsdojob.v3.domain.usuario;

public final class UsuarioTipos {
  private UsuarioTipos() {
  }

  public enum Status {
    ATIVO,
    PENDENTE,
    SUSPENSO,
    DESATIVADO,
    IMPORTADO
  }

  public enum TipoConta {
    ANUNCIANTE,
    STAFF,
    SISTEMA
  }

  public enum Papel {
    ADMIN,
    MODERADOR,
    COMERCIAL,
    USUARIO
  }

  public enum TipoTokenSeguranca {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA,
    CONVITE,
    REAUTENTICACAO
  }
}
