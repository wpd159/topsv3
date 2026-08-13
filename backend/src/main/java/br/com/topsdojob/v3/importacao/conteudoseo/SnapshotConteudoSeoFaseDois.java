package br.com.topsdojob.v3.importacao.conteudoseo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotConteudoSeoFaseDois {
  private SnapshotConteudoSeoFaseDois() {
  }

  public record Snapshot(
      String snapshotId,
      OffsetDateTime capturadoEm,
      UUID atorSistemaV3Id,
      Map<String, UUID> atoresV3,
      List<FaqLegada> faqs,
      List<AvisoLegado> avisos,
      List<BlogCategoriaLegada> categoriasBlog,
      List<BlogPostLegado> postsBlog,
      List<ConteudoInstitucionalLegado> conteudosInstitucionais,
      List<LocalidadeSeoLegada> localidadesSeo,
      List<ReferenciaAnuncioSeo> anunciosSeo,
      List<RedirectLegado> redirects) {

    public Snapshot {
      snapshotId = textoObrigatorio(snapshotId, "snapshotId");
      if (capturadoEm == null) {
        throw new IllegalArgumentException("capturadoEm deve ser informado");
      }
      if (atorSistemaV3Id == null) {
        throw new IllegalArgumentException("atorSistemaV3Id deve ser informado");
      }
      atoresV3 = Map.copyOf(atoresV3 == null ? Map.of() : atoresV3);
      faqs = List.copyOf(faqs == null ? List.of() : faqs);
      avisos = List.copyOf(avisos == null ? List.of() : avisos);
      categoriasBlog = List.copyOf(categoriasBlog == null ? List.of() : categoriasBlog);
      postsBlog = List.copyOf(postsBlog == null ? List.of() : postsBlog);
      conteudosInstitucionais = List.copyOf(
          conteudosInstitucionais == null ? List.of() : conteudosInstitucionais);
      localidadesSeo = List.copyOf(localidadesSeo == null ? List.of() : localidadesSeo);
      anunciosSeo = List.copyOf(anunciosSeo == null ? List.of() : anunciosSeo);
      redirects = List.copyOf(redirects == null ? List.of() : redirects);
    }
  }

  public record FaqLegada(
      String idOrigem,
      String pergunta,
      String resposta,
      String categoria,
      String status,
      boolean ativa,
      Integer ordem,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      OffsetDateTime publicadoEm) {

    public FaqLegada {
      idOrigem = textoObrigatorio(idOrigem, "faq.idOrigem");
      pergunta = textoObrigatorio(pergunta, "faq.pergunta");
      resposta = textoObrigatorio(resposta, "faq.resposta");
      categoria = textoObrigatorio(categoria, "faq.categoria");
      status = textoObrigatorio(status, "faq.status");
      criadoEm = dataObrigatoria(criadoEm, "faq.criadoEm");
      atualizadoEm = dataObrigatoria(atualizadoEm, "faq.atualizadoEm");
    }
  }

  public record AvisoLegado(
      String idOrigem,
      String titulo,
      String descricao,
      String localExibicao,
      String frequenciaExibicao,
      String status,
      Boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      String atorOrigemId,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      OffsetDateTime publicadoEm) {

    public AvisoLegado {
      idOrigem = textoObrigatorio(idOrigem, "aviso.idOrigem");
      titulo = textoObrigatorio(titulo, "aviso.titulo");
      descricao = textoObrigatorio(descricao, "aviso.descricao");
      localExibicao = textoOpcional(localExibicao);
      frequenciaExibicao = textoOpcional(frequenciaExibicao);
      status = textoObrigatorio(status, "aviso.status");
      atorOrigemId = textoOpcional(atorOrigemId);
      criadoEm = dataObrigatoria(criadoEm, "aviso.criadoEm");
      atualizadoEm = dataObrigatoria(atualizadoEm, "aviso.atualizadoEm");
    }
  }

  public record BlogCategoriaLegada(
      String idOrigem,
      String nome,
      String slug,
      Integer ordem,
      boolean ativa,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm) {

    public BlogCategoriaLegada {
      idOrigem = textoObrigatorio(idOrigem, "blogCategoria.idOrigem");
      nome = textoObrigatorio(nome, "blogCategoria.nome");
      slug = textoObrigatorio(slug, "blogCategoria.slug");
      criadoEm = dataObrigatoria(criadoEm, "blogCategoria.criadoEm");
      atualizadoEm = dataObrigatoria(atualizadoEm, "blogCategoria.atualizadoEm");
    }
  }

  public record BlogPostLegado(
      String idOrigem,
      String categoriaOrigemId,
      String titulo,
      String slug,
      String resumo,
      String conteudo,
      String autorNomePublico,
      String status,
      String seoTitleLegado,
      String seoDescriptionLegada,
      UUID imagemCapaV3Id,
      UUID imagemOgV3Id,
      String atorOrigemId,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      OffsetDateTime publicadoEm,
      OffsetDateTime arquivadoEm) {

    public BlogPostLegado {
      idOrigem = textoObrigatorio(idOrigem, "blogPost.idOrigem");
      categoriaOrigemId = textoObrigatorio(
          categoriaOrigemId, "blogPost.categoriaOrigemId");
      titulo = textoObrigatorio(titulo, "blogPost.titulo");
      slug = textoObrigatorio(slug, "blogPost.slug");
      resumo = textoObrigatorio(resumo, "blogPost.resumo");
      conteudo = textoObrigatorio(conteudo, "blogPost.conteudo");
      autorNomePublico = textoOpcional(autorNomePublico);
      status = textoObrigatorio(status, "blogPost.status");
      seoTitleLegado = textoOpcional(seoTitleLegado);
      seoDescriptionLegada = textoOpcional(seoDescriptionLegada);
      atorOrigemId = textoOpcional(atorOrigemId);
      criadoEm = dataObrigatoria(criadoEm, "blogPost.criadoEm");
      atualizadoEm = dataObrigatoria(atualizadoEm, "blogPost.atualizadoEm");
    }
  }

  public record ConteudoInstitucionalLegado(
      String idOrigem,
      String chavePublica,
      String titulo,
      String corpo,
      String status,
      String atorOrigemId,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      OffsetDateTime publicadoEm) {

    public ConteudoInstitucionalLegado {
      idOrigem = textoObrigatorio(idOrigem, "institucional.idOrigem");
      chavePublica = textoObrigatorio(chavePublica, "institucional.chavePublica");
      titulo = textoObrigatorio(titulo, "institucional.titulo");
      corpo = textoObrigatorio(corpo, "institucional.corpo");
      status = textoObrigatorio(status, "institucional.status");
      atorOrigemId = textoOpcional(atorOrigemId);
      criadoEm = dataObrigatoria(criadoEm, "institucional.criadoEm");
      atualizadoEm = dataObrigatoria(atualizadoEm, "institucional.atualizadoEm");
    }
  }

  public enum TipoLocalidade {
    ESTADO,
    CIDADE,
    BAIRRO
  }

  public record LocalidadeSeoLegada(
      String idOrigem,
      TipoLocalidade tipo,
      String caminhoPublico,
      String nomeLocalidade,
      String nomeCidade,
      String uf,
      boolean localidadeExiste,
      boolean rotaCanonica,
      boolean conteudoValido,
      boolean inventarioPublicoSuficiente,
      boolean relevanciaRealComprovada,
      boolean duplicada,
      boolean vazia,
      boolean filtroTemporario,
      long anunciosPublicosIndexaveis,
      String introducao,
      List<LinkInterno> linksInternos,
      OffsetDateTime atualizadoEm) {

    public LocalidadeSeoLegada {
      idOrigem = textoObrigatorio(idOrigem, "localidadeSeo.idOrigem");
      if (tipo == null) {
        throw new IllegalArgumentException("localidadeSeo.tipo deve ser informado");
      }
      caminhoPublico = textoObrigatorio(
          caminhoPublico, "localidadeSeo.caminhoPublico");
      nomeLocalidade = textoObrigatorio(
          nomeLocalidade, "localidadeSeo.nomeLocalidade");
      nomeCidade = textoOpcional(nomeCidade);
      uf = textoObrigatorio(uf, "localidadeSeo.uf");
      introducao = textoOpcional(introducao);
      linksInternos = List.copyOf(linksInternos == null ? List.of() : linksInternos);
      atualizadoEm = dataObrigatoria(atualizadoEm, "localidadeSeo.atualizadoEm");
    }
  }

  public record LinkInterno(String caminho, String rotulo) {
    public LinkInterno {
      caminho = textoObrigatorio(caminho, "linkInterno.caminho");
      rotulo = textoObrigatorio(rotulo, "linkInterno.rotulo");
    }
  }

  public record ReferenciaAnuncioSeo(
      String idOrigem,
      UUID anuncioV3Id,
      String slug,
      boolean shouldIndexAnuncio,
      OffsetDateTime atualizadoEm) {

    public ReferenciaAnuncioSeo {
      idOrigem = textoObrigatorio(idOrigem, "anuncioSeo.idOrigem");
      if (anuncioV3Id == null) {
        throw new IllegalArgumentException("anuncioSeo.anuncioV3Id deve ser informado");
      }
      slug = textoObrigatorio(slug, "anuncioSeo.slug");
      atualizadoEm = dataObrigatoria(atualizadoEm, "anuncioSeo.atualizadoEm");
    }
  }

  public record RedirectLegado(
      String idOrigem,
      String origemCaminho,
      String destinoCaminho,
      boolean compatibilidadeSemantica,
      String classificacaoQuandoInvalido,
      OffsetDateTime atualizadoEm) {

    public RedirectLegado {
      idOrigem = textoObrigatorio(idOrigem, "redirect.idOrigem");
      origemCaminho = textoObrigatorio(origemCaminho, "redirect.origemCaminho");
      destinoCaminho = textoObrigatorio(destinoCaminho, "redirect.destinoCaminho");
      classificacaoQuandoInvalido = textoOpcional(classificacaoQuandoInvalido);
      atualizadoEm = dataObrigatoria(atualizadoEm, "redirect.atualizadoEm");
    }
  }

  private static OffsetDateTime dataObrigatoria(OffsetDateTime valor, String campo) {
    if (valor == null) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor;
  }

  private static String textoObrigatorio(String valor, String campo) {
    String normalizado = textoOpcional(valor);
    if (normalizado == null) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return normalizado;
  }

  private static String textoOpcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
