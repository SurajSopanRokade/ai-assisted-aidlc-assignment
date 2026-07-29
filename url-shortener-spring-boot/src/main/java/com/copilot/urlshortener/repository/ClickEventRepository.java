package com.copilot.urlshortener.repository;

import com.copilot.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
}
