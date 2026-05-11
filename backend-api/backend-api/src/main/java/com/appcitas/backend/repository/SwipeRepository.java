package com.appcitas.backend.repository;

import com.appcitas.backend.model.Swipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SwipeRepository extends JpaRepository<Swipe, Long> {

    // Cambiamos el nombre para que sea más descriptivo y coincida con nuestro controlador
    @Query("SELECT s FROM Swipe s WHERE s.id_origen = :idDestino AND s.id_destino = :idOrigen AND s.tipo_swipe IN ('LIKE', 'SUPERLIKE')")
    Optional<Swipe> buscarSwipeContrario(@Param("idOrigen") Long idOrigen, @Param("idDestino") Long idDestino);
}