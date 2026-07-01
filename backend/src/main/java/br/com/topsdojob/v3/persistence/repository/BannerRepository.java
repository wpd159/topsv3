package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.banner.BannerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<BannerEntity, UUID> {
}
