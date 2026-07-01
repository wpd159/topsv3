package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtivacaoBeneficioRepository extends JpaRepository<AtivacaoBeneficioEntity, UUID> {
}
