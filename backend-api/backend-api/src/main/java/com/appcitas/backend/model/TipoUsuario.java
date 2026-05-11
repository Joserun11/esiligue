package com.appcitas.backend.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class TipoUsuario {
    private String nombre;
    private String correo;
    private String genero;
    private String es_premium;
}