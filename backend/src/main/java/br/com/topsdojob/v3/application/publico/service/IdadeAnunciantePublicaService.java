package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdadeAnunciantePublicaService {

  private final UsuarioRepository usuarioRepository;

  public IdadeAnunciantePublicaService(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional(readOnly = true)
  public Resultado resolver(UUID usuarioId, boolean idadeOculta) {
    UsuarioEntity usuario = usuarioRepository.findById(usuarioId).orElse(null);
    if (usuario == null) {
      return new Resultado(null, null, false);
    }
    if (idadeOculta) {
      return new Resultado(usuario.getNome(), null, true);
    }
    Integer idade = usuario.getDataNascimento() == null
        ? null
        : calcular(usuario.getDataNascimento(), LocalDate.now(ZoneOffset.UTC));
    return new Resultado(usuario.getNome(), idade, false);
  }

  static int calcular(LocalDate dataNascimento, LocalDate hoje) {
    if (dataNascimento == null || hoje == null || dataNascimento.isAfter(hoje)) {
      throw new IllegalArgumentException("datas invalidas para calculo de idade");
    }
    return Period.between(dataNascimento, hoje).getYears();
  }

  public record Resultado(String username, Integer idade, boolean idadeOculta) {
  }
}
