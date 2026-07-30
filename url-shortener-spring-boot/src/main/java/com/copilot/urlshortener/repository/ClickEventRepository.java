package com.copilot.urlshortener.repository;

import com.copilot.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /**
     * Latest click timestamp for a link, computed by the database.
     *
     * <p>Loading the {@code clickEvents} collection and streaming it for a
     * maximum would be O(clicks) in both rows fetched and heap, for a single
     * scalar — and a popular link is exactly the one with the most rows, so
     * that approach degrades precisely where it matters most. An aggregate
     * returns one value regardless of volume.
     */
    @Query("select max(c.clickedAt) from ClickEvent c where c.url.id = :urlId")
    Optional<LocalDateTime> findLastClickedAt(@Param("urlId") Long urlId);
}
