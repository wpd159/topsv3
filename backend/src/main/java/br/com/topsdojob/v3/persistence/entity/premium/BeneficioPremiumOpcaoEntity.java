package br.com.topsdojob.v3.persistence.entity.premium;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "beneficio_premium_opcao")
public class BeneficioPremiumOpcaoEntity {

    protected BeneficioPremiumOpcaoEntity() {
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "beneficio_id")
    private UUID beneficioId;

    @Column(name = "duracao_dias")
    private Integer duracaoDias;

    @Column(name = "custo_creditos")
    private Integer custoCreditos;

    @Column(name = "versao_regra")
    private Integer versaoRegra;

    @Column(name = "ativo")
    private Boolean ativo;

    @Column(name = "vigencia_inicio_em")
    private OffsetDateTime vigenciaInicioEm;

    @Column(name = "vigencia_fim_em")
    private OffsetDateTime vigenciaFimEm;

    @Column(name = "ordem_exibicao")
    private Integer ordemExibicao;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    public UUID getId() {
        return id;
    }

    public UUID getBeneficioId() {
        return beneficioId;
    }

    public Integer getDuracaoDias() {
        return duracaoDias;
    }

    public Integer getCustoCreditos() {
        return custoCreditos;
    }

    public Integer getVersaoRegra() {
        return versaoRegra;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public OffsetDateTime getVigenciaInicioEm() {
        return vigenciaInicioEm;
    }

    public OffsetDateTime getVigenciaFimEm() {
        return vigenciaFimEm;
    }

    public Integer getOrdemExibicao() {
        return ordemExibicao;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public boolean vigente(OffsetDateTime agora) {
        return Boolean.TRUE.equals(ativo)
                && (vigenciaInicioEm == null || !vigenciaInicioEm.isAfter(agora))
                && (vigenciaFimEm == null || vigenciaFimEm.isAfter(agora));
    }

    public void atualizar(int custoCreditos, boolean ativo, int ordemExibicao, OffsetDateTime agora) {
        this.custoCreditos = custoCreditos;
        this.ativo = ativo;
        this.ordemExibicao = ordemExibicao;
        this.atualizadoEm = agora;
    }

    public static BeneficioPremiumOpcaoEntity criar(
            UUID id,
            UUID beneficioId,
            int duracaoDias,
            int custoCreditos,
            boolean ativo,
            int ordemExibicao,
            OffsetDateTime agora) {
        BeneficioPremiumOpcaoEntity entity = new BeneficioPremiumOpcaoEntity();
        entity.id = id;
        entity.beneficioId = beneficioId;
        entity.duracaoDias = duracaoDias;
        entity.custoCreditos = custoCreditos;
        entity.versaoRegra = 1;
        entity.ativo = ativo;
        entity.vigenciaInicioEm = null;
        entity.vigenciaFimEm = null;
        entity.ordemExibicao = ordemExibicao;
        entity.criadoEm = agora;
        entity.atualizadoEm = agora;
        return entity;
    }
}
