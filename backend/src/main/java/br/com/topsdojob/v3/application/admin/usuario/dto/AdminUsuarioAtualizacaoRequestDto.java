package br.com.topsdojob.v3.application.admin.usuario.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

public final class AdminUsuarioAtualizacaoRequestDto {

  private Integer versao;
  private String nome;
  private String nomeCivil;
  private String email;
  private String cpf;
  private String telefone;
  private String dataNascimento;
  private boolean nomeInformado;
  private boolean nomeCivilInformado;
  private boolean emailInformado;
  private boolean cpfInformado;
  private boolean telefoneInformado;
  private boolean dataNascimentoInformada;

  public Integer getVersao() {
    return versao;
  }

  @JsonSetter
  public void setVersao(Integer versao) {
    this.versao = versao;
  }

  public String getNome() {
    return nome;
  }

  @JsonSetter
  public void setNome(String nome) {
    this.nome = nome;
    this.nomeInformado = true;
  }

  public String getNomeCivil() {
    return nomeCivil;
  }

  @JsonSetter
  public void setNomeCivil(String nomeCivil) {
    this.nomeCivil = nomeCivil;
    this.nomeCivilInformado = true;
  }

  public String getEmail() {
    return email;
  }

  @JsonSetter
  public void setEmail(String email) {
    this.email = email;
    this.emailInformado = true;
  }

  public String getCpf() {
    return cpf;
  }

  @JsonSetter
  public void setCpf(String cpf) {
    this.cpf = cpf;
    this.cpfInformado = true;
  }

  public String getTelefone() {
    return telefone;
  }

  @JsonSetter
  public void setTelefone(String telefone) {
    this.telefone = telefone;
    this.telefoneInformado = true;
  }

  public String getDataNascimento() {
    return dataNascimento;
  }

  @JsonSetter
  public void setDataNascimento(String dataNascimento) {
    this.dataNascimento = dataNascimento;
    this.dataNascimentoInformada = true;
  }

  public boolean isNomeInformado() {
    return nomeInformado;
  }

  public boolean isNomeCivilInformado() {
    return nomeCivilInformado;
  }

  public boolean isEmailInformado() {
    return emailInformado;
  }

  public boolean isCpfInformado() {
    return cpfInformado;
  }

  public boolean isTelefoneInformado() {
    return telefoneInformado;
  }

  public boolean isDataNascimentoInformada() {
    return dataNascimentoInformada;
  }

  public boolean possuiCampoInformado() {
    return nomeInformado
        || nomeCivilInformado
        || emailInformado
        || cpfInformado
        || telefoneInformado
        || dataNascimentoInformada;
  }
}
