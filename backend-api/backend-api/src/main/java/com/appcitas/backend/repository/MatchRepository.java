package com.appcitas.backend.repository;

import com.appcitas.backend.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    // Aquí puedes añadir búsquedas por ID de usuario más adelante
}