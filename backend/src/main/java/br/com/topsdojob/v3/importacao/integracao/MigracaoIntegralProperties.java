package br.com.topsdojob.v3.importacao.integracao;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.migracao.integral")
public class MigracaoIntegralProperties {

  private boolean enabled;
  private Path pacote;
  private ModoMigracaoIntegral modo;
  private int tamanhoLote;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Path getPacote() {
    return pacote;
  }

  public void setPacote(Path pacote) {
    this.pacote = pacote;
  }

  public ModoMigracaoIntegral getModo() {
    return modo;
  }

  public void setModo(ModoMigracaoIntegral modo) {
    this.modo = modo;
  }

  public int getTamanhoLote() {
    return tamanhoLote;
  }

  public void setTamanhoLote(int tamanhoLote) {
    this.tamanhoLote = tamanhoLote;
  }

  public void validar() {
    if (!enabled) {
      throw new IllegalStateException("migracao integral nao foi habilitada explicitamente");
    }
    if (pacote == null) {
      throw new IllegalStateException("pacote da migracao integral nao foi informado");
    }
    if (modo == null) {
      throw new IllegalStateException("modo da migracao integral nao foi informado");
    }
    if (tamanhoLote < 1 || tamanhoLote > 10_000) {
      throw new IllegalStateException("tamanho de lote fora do intervalo permitido");
    }
  }
}
