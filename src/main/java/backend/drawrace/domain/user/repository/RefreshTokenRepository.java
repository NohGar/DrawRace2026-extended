package backend.drawrace.domain.user.repository;

import org.springframework.data.repository.CrudRepository;

import backend.drawrace.domain.user.entity.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {}
