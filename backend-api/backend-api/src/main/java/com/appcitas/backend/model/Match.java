package com.appcitas.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Table(name = "MATCH") // Coincide con tu tabla de Oracle
@Data
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MATCH")
    private Long id_match;

    @Column(name = "ID_USUARIO1")
    private Long id_usuario1;

    @Column(name = "ID_USUARIO2")
    private Long id_usuario2;

    @Column(name = "FECHA_MATCH")
    private Date fecha_match;

    @Column(name = "ACTIVO")
    private Integer activo; // 1 para activo, 0 para desactivado
}