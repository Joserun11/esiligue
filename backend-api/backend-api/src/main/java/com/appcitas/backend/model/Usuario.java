package com.appcitas.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "USUARIO")
@Data
public class Usuario {

    @Id
    @Column(name = "ID_USUARIO")
    @JsonProperty("id_usuario")
    private Long id_usuario;

    @Column(name = "NOMBRE") // Forzamos el nombre de la columna que devuelve el SELECT
    private String nombre;

    @Column(name = "CORREO")
    private String correo;

    @Column(name = "CONTRASENA")
    private String contrasena;

    @Column(name = "GENERO")
    private String genero;

    @Column(name = "ES_PREMIUM")
    private String es_premium;

    @Column(name = "FECHA_NACIMIENTO")
    private String fecha_nacimiento;

    @Column(name = "CARRERA")
    private String carrera;

    @Column(name = "BIO") // Debe coincidir con el alias AS BIO del Repository
    private String bio;

    @Column(name = "CIUDAD")
    private String ciudad;

    // --- CAMPOS TRANSITORIOS (IMPORTANTÍSIMO EL @Transient) ---
    // Estos campos NO existen en la tabla USUARIO, por eso llevan @Transient
    // para que Hibernate no los busque en el SELECT y no de error 17006.

    @Transient
    @JsonProperty("apellido")
    private String apellido;

    @Transient
    @JsonProperty("edad")
    private Integer edad;

    @Transient
    @JsonProperty("que_busco")
    private String que_busco;

    @Transient
    @JsonProperty("rango_inicio")
    private Integer rango_inicio;

    @Transient
    @JsonProperty("rango_fin")
    private Integer rango_fin;

    @Transient
    @JsonProperty("ciudad_residencia")
    private String ciudad_residencia;

    @Transient
    @JsonProperty("ciudad_busqueda")
    private String ciudad_busqueda;

    @Transient
    @JsonProperty("foto1")
    private String foto1;

    @Transient
    @JsonProperty("foto2")
    private String foto2;

    @Transient
    @JsonProperty("foto3")
    private String foto3;

    @Transient
    @JsonProperty("foto4")
    private String foto4;

    @Transient
    @JsonProperty("foto5")
    private String foto5;

    @Transient
    @JsonProperty("foto6")
    private String foto6;
}