package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.ClassificacaoUsuarioStaging;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioStagingLegado;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClassificadorUsuariosStaging {

  public List<UsuarioStagingLegado> classificar(
      List<IdentidadeUsuario> usuarios,
      List<IdentidadeUsuario> staging,
      OffsetDateTime capturadoEm) {
    if (capturadoEm == null) {
      throw new IllegalArgumentException("capturadoEm deve ser informado");
    }
    List<IdentidadeUsuario> canonicos = ordenar(usuarios);
    List<IdentidadeUsuario> candidatos = ordenar(staging);
    validarIdsUnicos(canonicos, "usuarios");

    Map<String, Set<String>> usuariosPorIdentidade = indexarCanonicos(canonicos);
    Map<String, List<IdentidadeUsuario>> stagingPorFingerprint = new HashMap<>();
    for (IdentidadeUsuario item : candidatos) {
      stagingPorFingerprint.computeIfAbsent(fingerprint(item), ignored -> new ArrayList<>()).add(item);
    }

    List<UsuarioStagingLegado> resultado = new ArrayList<>();
    for (IdentidadeUsuario item : candidatos) {
      String fingerprint = fingerprint(item);
      List<IdentidadeUsuario> repetidos = stagingPorFingerprint.get(fingerprint);
      if (repetidos.size() > 1 && !repetidos.get(0).idOrigem().equals(item.idOrigem())) {
        resultado.add(new UsuarioStagingLegado(
            item.idOrigem(),
            ClassificacaoUsuarioStaging.DUPLICADO,
            unicoCandidato(item, usuariosPorIdentidade),
            fingerprint,
            "IDENTIDADE_EXATA_DUPLICADA_CONSOLIDADA",
            capturadoEm));
        continue;
      }

      Set<String> correspondencias = candidatos(item, usuariosPorIdentidade);
      if (correspondencias.size() == 1) {
        resultado.add(new UsuarioStagingLegado(
            item.idOrigem(),
            ClassificacaoUsuarioStaging.CORRESPONDENCIA_CANONICA,
            correspondencias.iterator().next(),
            fingerprint,
            "CORRESPONDENCIA_EXATA_INEQUIVOCA",
            capturadoEm));
      } else if (correspondencias.size() > 1) {
        resultado.add(new UsuarioStagingLegado(
            item.idOrigem(),
            ClassificacaoUsuarioStaging.AMBIGUO,
            null,
            fingerprint,
            "MULTIPLAS_CORRESPONDENCIAS_EXATAS",
            capturadoEm));
      } else if (tokensPersistidos(item).isEmpty()) {
        resultado.add(new UsuarioStagingLegado(
            item.idOrigem(),
            ClassificacaoUsuarioStaging.SEM_IDENTIDADE,
            null,
            fingerprint,
            "IDENTIDADE_PERSISTIDA_INSUFICIENTE",
            capturadoEm));
      } else {
        resultado.add(new UsuarioStagingLegado(
            item.idOrigem(),
            ClassificacaoUsuarioStaging.STAGING_ONLY,
            null,
            fingerprint,
            "SEM_CORRESPONDENCIA_CANONICA_EXATA",
            capturadoEm));
      }
    }
    return List.copyOf(resultado);
  }

  private static Map<String, Set<String>> indexarCanonicos(List<IdentidadeUsuario> usuarios) {
    Map<String, Set<String>> indice = new LinkedHashMap<>();
    for (IdentidadeUsuario usuario : usuarios) {
      for (String identificador : tokens(usuario, true)) {
        indice.computeIfAbsent(identificador, ignored -> new LinkedHashSet<>()).add(usuario.idOrigem());
      }
    }
    return indice;
  }

  private static Set<String> candidatos(
      IdentidadeUsuario item,
      Map<String, Set<String>> usuariosPorIdentidade) {
    Set<String> resultado = new LinkedHashSet<>();
    for (String identificador : tokens(item, true)) {
      resultado.addAll(usuariosPorIdentidade.getOrDefault(identificador, Set.of()));
    }
    return resultado;
  }

  private static String unicoCandidato(
      IdentidadeUsuario item,
      Map<String, Set<String>> usuariosPorIdentidade) {
    Set<String> candidatos = candidatos(item, usuariosPorIdentidade);
    return candidatos.size() == 1 ? candidatos.iterator().next() : null;
  }

  private static String fingerprint(IdentidadeUsuario item) {
    List<String> tokens = new ArrayList<>(tokensPersistidos(item));
    if (tokens.isEmpty()) {
      tokens.add("ID_TECNICO:" + item.idOrigem());
    }
    tokens.sort(String::compareTo);
    return FingerprintMigracaoIntegral.sha256(
        String.join("|", tokens).getBytes(StandardCharsets.UTF_8));
  }

  private static Set<String> tokensPersistidos(IdentidadeUsuario item) {
    return tokens(item, false);
  }

  private static Set<String> tokens(IdentidadeUsuario item, boolean incluirId) {
    Set<String> tokens = new HashSet<>();
    if (incluirId) {
      tokens.add("ID:" + item.idOrigem());
    }
    adicionar(tokens, "EMAIL:", minusculo(item.email()));
    adicionar(tokens, "CPF:", digitos(item.cpf()));
    adicionar(tokens, "TELEFONE:", digitos(item.telefone()));
    adicionar(tokens, "USERNAME:", minusculo(item.username()));
    return Set.copyOf(tokens);
  }

  private static void adicionar(Set<String> tokens, String prefixo, String valor) {
    if (valor != null) {
      tokens.add(prefixo + valor);
    }
  }

  private static List<IdentidadeUsuario> ordenar(List<IdentidadeUsuario> valores) {
    return (valores == null ? List.<IdentidadeUsuario>of() : valores).stream()
        .sorted(Comparator.comparing(IdentidadeUsuario::idOrigem))
        .toList();
  }

  private static void validarIdsUnicos(List<IdentidadeUsuario> usuarios, String origem) {
    Set<String> ids = new HashSet<>();
    if (usuarios.stream().map(IdentidadeUsuario::idOrigem).anyMatch(id -> !ids.add(id))) {
      throw new IllegalArgumentException("id duplicado em " + origem);
    }
  }

  private static String minusculo(String valor) {
    String normalizado = opcional(valor);
    return normalizado == null ? null : normalizado.toLowerCase(Locale.ROOT);
  }

  private static String digitos(String valor) {
    String normalizado = opcional(valor);
    if (normalizado == null) {
      return null;
    }
    String resultado = normalizado.replaceAll("\\D", "");
    return resultado.isBlank() ? null : resultado;
  }

  private static String opcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }

  public record IdentidadeUsuario(
      String idOrigem,
      String email,
      String cpf,
      String telefone,
      String username) {

    public IdentidadeUsuario {
      idOrigem = opcional(idOrigem);
      if (idOrigem == null) {
        throw new IllegalArgumentException("idOrigem deve ser informado");
      }
    }
  }
}
