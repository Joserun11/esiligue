package com.appcitas.backend.repository;

import com.appcitas.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query(value = "SELECT u.ID_USUARIO AS ID_USUARIO, " +
            "u.DATOS.NOMBRE AS NOMBRE, " +
            "u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, " +
            "u.DATOS.GENERO AS GENERO, " +
            "u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, " +
            "u.DATOS.DESCRIPCION AS BIO, " +
            "u.DATOS.FECHA_NACIMIENTO AS FECHA_NACIMIENTO, " +
            "u.DATOS.CIUDAD AS CIUDAD " +
            "FROM USUARIO u", nativeQuery = true)
    List<Usuario> obtenerTodosNativo();

    @Query(value = "SELECT CASE WHEN COUNT(u.ID_USUARIO) > 0 THEN 1 ELSE 0 END FROM USUARIO u WHERE u.DATOS.CORREO = :correo", nativeQuery = true)
    int comprobarSiExisteCorreoNativo(@Param("correo") String correo);

    @Modifying
    @Transactional
    @Query(value = "BEGIN " +
            "  DELETE FROM ArchivoMultimedia WHERE id_usuario = (SELECT id_usuario FROM USUARIO WHERE DATOS.CORREO = :correo); " +
            "  DELETE FROM PreferenciaBusqueda WHERE id_usuario = (SELECT id_usuario FROM USUARIO WHERE DATOS.CORREO = :correo); " +
            "  DELETE FROM USUARIO WHERE DATOS.CORREO = :correo; " +
            "END;", nativeQuery = true)
    void eliminarPorCorreoNativo(@Param("correo") String correo);

    // --- 1. CONSULTA PARA "CÁDIZ (PROVINCIA)" (IGNORA EL FILTRO DE CIUDAD ESPECÍFICA) ---

    @Query(value = "SELECT u.ID_USUARIO, u.DATOS.NOMBRE AS NOMBRE, u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, u.DATOS.GENERO AS GENERO, u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, u.DATOS.DESCRIPCION AS BIO, u.DATOS.FECHA_NACIMIENTO " +
            "FROM USUARIO u WHERE u.ID_USUARIO != :miId " +
            "AND u.ID_USUARIO NOT IN (SELECT s.ID_DESTINO FROM SWIPE s WHERE s.ID_ORIGEN = :miId) " +
            "AND (:genero = 'A' OR u.DATOS.GENERO = :genero) " +
            "AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.DATOS.FECHA_NACIMIENTO) / 12) BETWEEN :min AND :max", nativeQuery = true)
    List<Usuario> encontrarCandidatosProvinciaCompleta(@Param("miId") Long miId, @Param("genero") String genero, @Param("min") Integer min, @Param("max") Integer max);

    // --- 2. CONSULTAS PARA CIUDADES ESPECÍFICAS (INCLUYENDO "CÁDIZ (CIUDAD)") ---

    @Query(value = "SELECT u.ID_USUARIO, u.DATOS.NOMBRE AS NOMBRE, u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, u.DATOS.GENERO AS GENERO, u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, u.DATOS.DESCRIPCION AS BIO, u.DATOS.FECHA_NACIMIENTO " +
            "FROM USUARIO u WHERE u.ID_USUARIO != :miId " +
            "AND u.ID_USUARIO NOT IN (SELECT s.ID_DESTINO FROM SWIPE s WHERE s.ID_ORIGEN = :miId) " +
            "AND u.DATOS.GENERO = :genero " +
            "AND u.DATOS.CIUDAD = :ciudad " +
            "AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.DATOS.FECHA_NACIMIENTO) / 12) BETWEEN :min AND :max", nativeQuery = true)
    List<Usuario> encontrarCandidatosUbicacion(@Param("miId") Long miId, @Param("genero") String genero, @Param("min") Integer min, @Param("max") Integer max, @Param("ciudad") String ciudad);

    @Query(value = "SELECT u.ID_USUARIO, u.DATOS.NOMBRE AS NOMBRE, u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, u.DATOS.GENERO AS GENERO, u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, u.DATOS.DESCRIPCION AS BIO, u.DATOS.FECHA_NACIMIENTO " +
            "FROM USUARIO u WHERE u.ID_USUARIO != :miId " +
            "AND u.ID_USUARIO NOT IN (SELECT s.ID_DESTINO FROM SWIPE s WHERE s.ID_ORIGEN = :miId) " +
            "AND u.DATOS.CIUDAD = :ciudad " +
            "AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.DATOS.FECHA_NACIMIENTO) / 12) BETWEEN :min AND :max", nativeQuery = true)
    List<Usuario> encontrarCandidatosCualquierGeneroUbicacion(@Param("miId") Long miId, @Param("min") Integer min, @Param("max") Integer max, @Param("ciudad") String ciudad);

    // --- MÉTODOS DE COMPATIBILIDAD ---

    @Query(value = "SELECT u.ID_USUARIO, u.DATOS.NOMBRE AS NOMBRE, u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, u.DATOS.GENERO AS GENERO, u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, u.DATOS.DESCRIPCION AS BIO, u.DATOS.FECHA_NACIMIENTO " +
            "FROM USUARIO u WHERE u.ID_USUARIO != :miId " +
            "AND u.ID_USUARIO NOT IN (SELECT s.ID_DESTINO FROM SWIPE s WHERE s.ID_ORIGEN = :miId) " +
            "AND u.DATOS.GENERO = :genero " +
            "AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.DATOS.FECHA_NACIMIENTO) / 12) BETWEEN :min AND :max", nativeQuery = true)
    List<Usuario> encontrarCandidatos(@Param("miId") Long miId, @Param("genero") String genero, @Param("min") Integer min, @Param("max") Integer max);

    @Query(value = "SELECT u.ID_USUARIO, u.DATOS.NOMBRE AS NOMBRE, u.DATOS.CORREO AS CORREO, " +
            "u.DATOS.CONTRASENA AS CONTRASENA, u.DATOS.GENERO AS GENERO, u.DATOS.ES_PREMIUM AS ES_PREMIUM, " +
            "u.DATOS.CARRERA AS CARRERA, u.DATOS.DESCRIPCION AS BIO, u.DATOS.FECHA_NACIMIENTO " +
            "FROM USUARIO u WHERE u.ID_USUARIO != :miId " +
            "AND u.ID_USUARIO NOT IN (SELECT s.ID_DESTINO FROM SWIPE s WHERE s.ID_ORIGEN = :miId) " +
            "AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.DATOS.FECHA_NACIMIENTO) / 12) BETWEEN :min AND :max", nativeQuery = true)
    List<Usuario> encontrarCandidatosCualquierGenero(@Param("miId") Long miId, @Param("min") Integer min, @Param("max") Integer max);
}