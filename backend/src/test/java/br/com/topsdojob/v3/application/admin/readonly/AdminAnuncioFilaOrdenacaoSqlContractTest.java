package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AdminAnuncioFilaOrdenacaoSqlContractTest {

    @Test
    void ordenaEPaginaNoPostgresqlComMetricasCanonicas() throws Exception {
        String repository = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "AnuncioRepository.java"));

        assertThat(repository)
                .contains("Page<AnuncioEntity> findFilaAdministrativa")
                .contains("agregado_visualizacao_inicial")
                .contains("evento_visualizacao")
                .contains("e.criado_em > corte.snapshot_corte_em")
                .contains("c.permitido = true")
                .contains("legado.anuncio_id is not null")
                .contains("vi.id is null")
                .contains("MAIS_RECENTES")
                .contains("MAIS_ANTIGOS")
                .contains("MAIS_VISUALIZACOES")
                .contains("MENOS_VISUALIZACOES")
                .contains("MAIS_CLIQUES_WHATSAPP")
                .contains("MENOS_CLIQUES_WHATSAPP")
                .contains(":situacao = 'PENDENTES_MODERACAO'")
                .contains(":situacao = 'APROVADOS'")
                .contains("a.status_moderacao = 'APROVADO'")
                .contains("a.status = 'PAUSADO'")
                .contains(":situacao = 'REJEITADOS'")
                .contains("cast(a.id as text)")
                .doesNotContain("a.status_moderacao = 'BLOQUEADO'")
                .doesNotContain("agregado_visualizacao_diaria");
    }

    @Test
    void pendentesExigemProprietarioAtivoNoConteudoContagemEPaginacao() throws Exception {
        Method method = AnuncioRepository.class.getMethod(
                "findFilaAdministrativa",
                String.class,
                boolean.class,
                Collection.class,
                String.class,
                String.class,
                Pageable.class);
        Query query = method.getAnnotation(Query.class);

        assertFilaExcluiProprietarioSuspenso(query.value());
        assertFilaExcluiProprietarioSuspenso(query.countQuery());

        Method countMethod = AnuncioRepository.class.getMethod(
                "countPendentesModeracaoComProprietarioAtivo");
        assertFilaExcluiProprietarioSuspenso(countMethod.getAnnotation(Query.class).value());
    }

    private void assertFilaExcluiProprietarioSuspenso(String query) {
        assertThat(query)
                .contains("usuario u")
                .contains("a.status = 'PENDENTE_REVISAO'")
                .contains("a.status_moderacao = 'PENDENTE'")
                .contains("u.status = 'ATIVO'")
                .contains("u.tipo_conta = 'ANUNCIANTE'")
                .contains("u.desativado_em is null")
                .contains("u.excluido_em is null");
    }

    @Test
    void controllerNaoReintroduzFiltroDeEstadoEDefineTrintaItens() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "web", "admin", "readonly", "AdminAnuncioDetalhadoController.java"));

        assertThat(controller)
                .contains("@RequestParam(defaultValue = \"30\") int size")
                .contains("AdminAnuncioSituacao situacao")
                .contains("AdminAnuncioOrdenacao ordenacao")
                .contains("return semCache(service.listar")
                .doesNotContain("StatusModeracaoAnuncio statusModeracao")
                .doesNotContain("StatusAnuncio status");
    }

    @Test
    void contadorAdministrativoNaoIncluiMidiaRemovida() throws Exception {
        String repository = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "AnuncioMidiaRepository.java"));
        String service = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "application", "admin", "readonly", "AdminAnuncioDetalhadoConsultaService.java"));

        assertThat(repository)
                .contains("and m.status <> :statusExcluido")
                .contains("countByAnuncioIdInAndTipoNotAndStatusNot");
        assertThat(service)
                .contains("StatusAnuncioMidia.REMOVIDA")
                .contains("countByAnuncioIdInAndTipoNotAndStatusNot");
    }
}
