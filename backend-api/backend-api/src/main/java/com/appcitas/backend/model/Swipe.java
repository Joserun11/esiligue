package com.appcitas.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "SWIPE") // Tu tabla se llama SWIPE (en singular)
@Data
public class Swipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_swipe;

    private Long id_origen;
    private Long id_destino;

    @Column(name = "TIPO_SWIPE")
    private String tipo_swipe; // 'LIKE', 'PASS', 'SUPERLIKE'

    @Column(name = "FECHA_SWIPE", insertable = false, updatable = false)
    private java.util.Date fecha_swipe;
}