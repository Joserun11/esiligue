package com.appcitas.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Table(name = "ARCHIVOMULTIMEDIA")
@Data
public class ArchivoMultimedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ARCHIVO")
    private Long id_archivo;

    @Column(name = "ID_USUARIO")
    private Long id_usuario;

    @Column(name = "TIPO_ARCHIVO")
    private String tipo_archivo; // Guardará 'foto' o 'video'

    @Column(name = "URL")
    private String url; // Aquí va el enlace de la imagen

    @Column(name = "FECHA_SUBIDA", insertable = false, updatable = false)
    private Date fecha_subida;
}