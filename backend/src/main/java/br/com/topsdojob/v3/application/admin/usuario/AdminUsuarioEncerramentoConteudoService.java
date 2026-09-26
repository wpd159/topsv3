package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeStoryRegistroService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminUsuarioEncerramentoConteudoService {

    private final AnuncioRepository anuncios;
    private final AnuncioStatusHistoricoRepository historicos;
    private final AnuncioLocalizacaoRepository localizacoes;
    private final DocumentoBuscaAnuncioRepository busca;
    private final StoryAnuncioRepository stories;
    private final StorySelecaoAdministrativaRepository storyAdministrativo;
    private final ArquivoPublicidadeRegistroService arquivoPublicidade;
    private final ArquivoPublicidadeStoryRegistroService arquivoStory;

    public AdminUsuarioEncerramentoConteudoService(
            AnuncioRepository anuncios,
            AnuncioStatusHistoricoRepository historicos,
            AnuncioLocalizacaoRepository localizacoes,
            DocumentoBuscaAnuncioRepository busca,
            StoryAnuncioRepository stories,
            StorySelecaoAdministrativaRepository storyAdministrativo,
            ArquivoPublicidadeRegistroService arquivoPublicidade,
            ArquivoPublicidadeStoryRegistroService arquivoStory) {
        this.anuncios = anuncios;
        this.historicos = historicos;
        this.localizacoes = localizacoes;
        this.busca = busca;
        this.stories = stories;
        this.storyAdministrativo = storyAdministrativo;
        this.arquivoPublicidade = arquivoPublicidade;
        this.arquivoStory = arquivoStory;
    }

    public Resultado encerrar(UUID usuarioId, UUID atorId, OffsetDateTime agora) {
        var anunciosUsuario = anuncios.findByUsuarioIdForLegalBlock(usuarioId);
        Set<UUID> anuncioIds = anunciosUsuario.stream()
                .map(item -> item.getId())
                .collect(Collectors.toSet());
        int removidos = 0;
        for (var anuncio : anunciosUsuario) {
            // A captura prospectiva ja ocorreu na entrada em veiculacao.
            // A retirada nao depende de nova leitura do storage privado.
            StatusAnuncio anterior = anuncio.getStatus();
            if (anuncio.removerPorExclusaoDaConta(agora)) {
                removidos++;
                historicos.save(AnuncioStatusHistoricoEntity.registrar(
                        UUID.randomUUID(),
                        anuncio.getId(),
                        anterior,
                        anuncio.getStatus(),
                        "CONTA_EXCLUIDA",
                        atorId,
                        agora));
            }
            arquivoPublicidade.registrarEstado(
                    anuncio.getId(), "CONTA_EXCLUIDA", null, agora);
        }
        anuncios.saveAll(anunciosUsuario);

        if (!anuncioIds.isEmpty()) {
            var documentos = busca.findAllById(anuncioIds);
            documentos.forEach(item -> item.removerDaBusca(agora));
            busca.saveAll(documentos);

            var locais = localizacoes.findByAnuncioIdIn(anuncioIds);
            locais.forEach(item -> item.removerDadosPrecisos(agora));
            localizacoes.saveAll(locais);
        }

        var storiesUsuario = stories.findByCriadoPorForUpdate(usuarioId);
        var storiesEncerrados = storiesUsuario.stream()
                .filter(item -> item.suspenderPorBloqueio(agora))
                .toList();
        stories.saveAll(storiesEncerrados);
        arquivoStory.registrarEstadoPorUsuario(
                usuarioId, "CONTA_EXCLUIDA_STORY", null, agora);

        storyAdministrativo.bloquearOperacao();
        var selecoes = anuncioIds.isEmpty()
                ? List.<br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity>of()
                : storyAdministrativo.bloquearAtivasDosAnuncios(anuncioIds);
        boolean storyAdministrativoEncerrado = !selecoes.isEmpty();
        if (storyAdministrativoEncerrado) {
            selecoes.forEach(selecao -> selecao.desativar(agora));
            storyAdministrativo.saveAll(selecoes);
        }
        return new Resultado(
                List.copyOf(anuncioIds),
                removidos,
                storiesEncerrados.size(),
                storyAdministrativoEncerrado);
    }

    public record Resultado(
            List<UUID> anuncioIds,
            int anunciosRemovidos,
            int storiesEncerrados,
            boolean storyAdministrativoEncerrado) {
    }
}
