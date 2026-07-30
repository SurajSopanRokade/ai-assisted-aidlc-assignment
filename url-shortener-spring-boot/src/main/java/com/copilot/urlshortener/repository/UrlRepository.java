package com.copilot.urlshortener.repository;

import com.copilot.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Increments the click counter in the database rather than in memory.
     *
     * <p>Read-modify-write through the entity
     * ({@code setClickCount(getClickCount() + 1)}) loses increments when
     * requests overlap: two readers both see N and both write N+1. Pushing the
     * arithmetic into SQL makes the update atomic, so the count stays correct
     * under concurrency — verified by {@code RedirectConcurrencyIntegrationTest}.
     *
     * <p>{@code clearAutomatically} discards the stale entity state the bulk
     * update has invalidated, so a later read in the same persistence context
     * cannot return the pre-increment value.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Url u set u.clickCount = u.clickCount + 1 where u.id = :id")
    int incrementClickCount(@Param("id") Long id);
}
