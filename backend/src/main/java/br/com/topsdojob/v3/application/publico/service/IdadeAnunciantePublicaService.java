package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  @Transactional(readOnly = true)
  public Map<UUID, Resultado> resolverPorAnuncios(
      Collection<AnuncioEntity> anuncios,
      Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio) {
    if (anuncios == null || anuncios.isEmpty()) {
      return Map.of();
    }
    Map<UUID, UsuarioEntity> usuarios = usuarioRepository.findAllById(anuncios.stream()
            .map(AnuncioEntity::getUsuarioId)
            .distinct()
            .toList()).stream()
        .collect(Collectors.toMap(UsuarioEntity::getId, Function.identity()));
    LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
    return anuncios.stream().collect(Collectors.toMap(
        AnuncioEntity::getId,
        anuncio -> resultado(
            usuarios.get(anuncio.getUsuarioId()),
            premiumPorAnuncio != null
                && premiumPorAnuncio.getOrDefault(anuncio.getId(), PremiumPublicoFlagsDto.vazio()).idadeOculta(),
            hoje),
        (primeiro, ignorado) -> primeiro));
  }

  public Map<UUID, Resultado> resolverPorUsuarios(
      Collection<UsuarioEntity> usuarios,
      Set<UUID> usuariosComIdadeOculta) {
    if (usuarios == null || usuarios.isEmpty()) {
      return Map.of();
    }
    Set<UUID> ocultos = usuariosComIdadeOculta == null ? Set.of() : usuariosComIdadeOculta;
    LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
    return usuarios.stream()
        .filter(java.util.Objects::nonNull)
        .filter(usuario -> usuario.getId() != null)
        .collect(Collectors.toMap(
            UsuarioEntity::getId,
            usuario -> resultado(usuario, ocultos.contains(usuario.getId()), hoje),
            (primeiro, ignorado) -> primeiro));
  }

  private Resultado resultado(UsuarioEntity usuario, boolean idadeOculta, LocalDate hoje) {
    if (usuario == null) {
      return new Resultado(null, null, false);
    }
    if (idadeOculta) {
      return new Resultado(usuario.getNome(), null, true);
    }
    Integer idade = usuario.getDataNascimento() == null
        ? null
        : calcular(usuario.getDataNascimento(), hoje);
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
