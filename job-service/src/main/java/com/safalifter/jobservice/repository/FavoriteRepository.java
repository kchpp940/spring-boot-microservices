package com.safalifter.jobservice.repository;

import com.safalifter.jobservice.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    Optional<Favorite> findByUserIdAndJobId(String userId, String jobId);

    boolean existsByUserIdAndJobId(String userId, String jobId);

    Page<Favorite> findByUserId(String userId, Pageable pageable);

    void deleteByUserIdAndJobId(String userId, String jobId);
}
