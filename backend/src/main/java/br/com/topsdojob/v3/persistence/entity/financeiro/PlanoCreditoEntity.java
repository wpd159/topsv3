package br.com.topsdojob.v3.persistence.entity.financeiro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plano_credito")
public class PlanoCreditoEntity {

    protected PlanoCreditoEntity() {
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "nome")
    private String nome;

    @Column(name = "quantidade_creditos")
    private Integer quantidadeCreditos;

    @Column(name = "valor")
    private BigDecimal valor;

    @Column(name = "moeda")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String moeda;

    @Column(name = "ativo")
    private Boolean ativo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "ordem_exibicao")
    private Integer ordemExibicao;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public Integer getQuantidadeCreditos() {
        return quantidadeCreditos;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getMoeda() {
        return moeda;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getOrdemExibicao() {
        return ordemExibicao;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void atualizar(
            String nome,
            String descricao,
            int quantidadeCreditos,
            BigDecimal valor,
            boolean ativo,
            int ordemExibicao,
            OffsetDateTime agora) {
        this.nome = nome;
        this.descricao = descricao;
        this.quantidadeCreditos = quantidadeCreditos;
        this.valor = valor;
        this.ativo = ativo;
        this.ordemExibicao = ordemExibicao;
        this.atualizadoEm = agora;
    }
}
