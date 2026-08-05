package br.com.topsdojob.v3.application.admin.stories;

import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoriesPaginaDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryGerenciadoDto;
import br.com.topsdojob.v3.application.publico.anunciante.MeusStoriesConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuStoryGerenciadoDto;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStoriesGestaoService {

  private final StoryAnuncioRepository storyRepository;
  private final UsuarioRepository usuarioRepository;
  private final MeusStoriesConsultaService consultaService;

  public AdminStoriesGestaoService(
      StoryAnuncioRepository storyRepository,
      UsuarioRepository usuarioRepository,
      MeusStoriesConsultaService consultaService) {
    this.storyRepository = storyRepository;
    this.usuarioRepository = usuarioRepository;
    this.consultaService = consultaService;
  }

  @Transactional(readOnly = true)
  public AdminStoriesPaginaDto listar(int pagina, int tamanho) {
    Page<StoryAnuncioEntity> stories = storyRepository.findAllByOrderByCriadoEmDescIdDesc(
        PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanho))));
    List<MeuStoryGerenciadoDto> estados = consultaService.mapear(stories.getContent());
    Map<UUID, MeuStoryGerenciadoDto> estadoPorId = estados.stream()
        .collect(Collectors.toMap(MeuStoryGerenciadoDto::id, Function.identity()));
    Map<UUID, UsuarioEntity> usuarios = usuarioRepository.findAllById(stories.getContent().stream()
            .map(StoryAnuncioEntity::getCriadoPor)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList()).stream()
        .collect(Collectors.toMap(UsuarioEntity::getId, Function.identity()));
    List<AdminStoryGerenciadoDto> itens = stories.getContent().stream()
        .map(story -> mapear(story, estadoPorId.get(story.getId()), usuarios.get(story.getCriadoPor())))
        .toList();
    return new AdminStoriesPaginaDto(
        itens,
        stories.getNumber(),
        stories.getSize(),
        stories.getTotalElements(),
        stories.getTotalPages());
  }

  private AdminStoryGerenciadoDto mapear(
      StoryAnuncioEntity story,
      MeuStoryGerenciadoDto estado,
      UsuarioEntity usuario) {
    return new AdminStoryGerenciadoDto(
        story.getId(),
        usuario == null ? null : usuario.getNome(),
        estado.modoConteudo(),
        estado.status(),
        estado.publicadoEm(),
        estado.expiraEm(),
        estado.anuncioSlug(),
        estado.anuncioTitulo(),
        estado.estadoMidia(),
        estado.falhaTecnica(),
        story.getEncerradoEm(),
        story.getOrigemEncerramento() == null ? null : story.getOrigemEncerramento().name(),
        story.getMotivoEncerramento(),
        story.isDireitoPreservado());
  }
}
