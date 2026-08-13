package br.com.topsdojob.v3.importacao.midia;

import java.util.Optional;
import java.util.UUID;

public interface CheckpointMidiaMigracao {

  Optional<Registro> buscar(UUID execucaoId, String itemFingerprint);

  void registrarConcluido(
      UUID execucaoId,
      ManifestoMidiaFaseCinco.Item item,
      String checksum,
      long tamanhoBytes,
      String mimeType);

  record Registro(
      String itemFingerprint,
      String destinoFingerprint,
      String checksum,
      long tamanhoBytes,
      String mimeType) {
  }
}
