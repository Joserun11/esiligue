package com.appcitas.backend.repository;

import com.appcitas.backend.model.ArchivoMultimedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ArchivoMultimediaRepository extends JpaRepository<ArchivoMultimedia, Long> {

    // Añadimos ORDER BY a.id_archivo ASC para que la primera de la lista sea la primera que se subió
    @Query("SELECT a.url FROM ArchivoMultimedia a WHERE a.id_usuario = :idUsuario AND a.tipo_archivo = 'foto' ORDER BY a.id_archivo ASC")
    List<String> encontrarUrlsPorUsuario(@Param("idUsuario") Long idUsuario);
}