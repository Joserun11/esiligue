package com.appcitas.backend.controller;

import com.appcitas.backend.model.Usuario;
import com.appcitas.backend.repository.UsuarioRepository;
import com.appcitas.backend.repository.ArchivoMultimediaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ArchivoMultimediaRepository archivoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<Usuario> obtenerTodos() {
        List<Usuario> usuarios = usuarioRepository.obtenerTodosNativo();

        for (Usuario u : usuarios) {
            // 1. Valores por defecto de seguridad
            u.setRango_inicio(18);
            u.setRango_fin(49);
            u.setQue_busco("Todos");

            try {
                // 2. Cargar ciudad de residencia desde el objeto Oracle
                String sqlCiudad = "SELECT t.datos.ubicacion.ciudad FROM Usuario t WHERE t.id_usuario = ?";
                String ciudadActual = jdbcTemplate.queryForObject(sqlCiudad, String.class, u.getId_usuario());
                u.setCiudad_residencia(ciudadActual != null ? ciudadActual : "Cádiz (Ciudad)");

                // 3. Cargar preferencias de búsqueda
                String sqlPref = "SELECT edad_min, edad_max, genero_interes, ciudad_interes FROM PreferenciaBusqueda WHERE id_usuario = ?";
                jdbcTemplate.query(sqlPref, new Object[]{u.getId_usuario()}, (rs) -> {
                    u.setRango_inicio(rs.getInt("edad_min"));
                    u.setRango_fin(rs.getInt("edad_max"));
                    u.setCiudad_busqueda(rs.getString("ciudad_interes"));

                    String interes = rs.getString("genero_interes");
                    if ("M".equals(interes)) u.setQue_busco("Chicos");
                    else if ("F".equals(interes)) u.setQue_busco("Chicas");
                    else u.setQue_busco("Todos");
                });

                // 4. Cargar fotos
                List<String> urls = archivoRepository.encontrarUrlsPorUsuario(u.getId_usuario());
                if (urls != null) {
                    if (urls.size() >= 1) u.setFoto1(urls.get(0));
                    if (urls.size() >= 2) u.setFoto2(urls.get(1));
                    if (urls.size() >= 3) u.setFoto3(urls.get(2));
                    if (urls.size() >= 4) u.setFoto4(urls.get(3));
                    if (urls.size() >= 5) u.setFoto5(urls.get(4));
                    if (urls.size() >= 6) u.setFoto6(urls.get(5));
                }
            } catch (Exception e) {
                System.err.println("Error cargando datos extra para " + u.getNombre() + ": " + e.getMessage());
            }
        }
        return usuarios;
    }

    @PostMapping
    @Transactional
    public Usuario registrarUsuario(@RequestBody Usuario u) {
        boolean esEdicion = (u.getId_usuario() != null && u.getId_usuario() > 0);
        Long idFinal;

        String passRecibida = u.getContrasena();
        String passAGuardar = (passRecibida != null && passRecibida.startsWith("$2a$"))
                ? passRecibida : passwordEncoder.encode(passRecibida);

        String generoOracle = "Chico".equals(u.getGenero()) ? "M" : ("Chica".equals(u.getGenero()) ? "F" : "O");

        // --- ASEGURAR QUE LA CIUDAD NO LLEGUE NULA ---
        String ciudadRes = (u.getCiudad_residencia() != null && !u.getCiudad_residencia().isEmpty())
                ? u.getCiudad_residencia() : "Cádiz (Ciudad)";

        // Escudo de fecha
        String fechaFija = "2000-01-01";
        try {
            String f = u.getFecha_nacimiento();
            if (f != null && f.contains("/")) {
                fechaFija = f.substring(6, 10) + "-" + f.substring(3, 5) + "-" + f.substring(0, 2);
            }
        } catch (Exception e) { }

        if (esEdicion) {
            idFinal = u.getId_usuario();
            String sqlUpdate = "UPDATE Usuario SET datos = TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', TipoUbicacion(?, 'España', 0, 0), 20) WHERE id_usuario = ?";
            jdbcTemplate.update(sqlUpdate, u.getNombre(), u.getCorreo(), passAGuardar, fechaFija, u.getCarrera(), u.getBio(), generoOracle, ciudadRes, idFinal);
        } else {
            idFinal = jdbcTemplate.queryForObject("SELECT id_usuario.NEXTVAL FROM DUAL", Long.class);
            String sqlInsert = "INSERT INTO Usuario (id_usuario, datos) VALUES (?, TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', TipoUbicacion(?, 'España', 0, 0), 20))";
            jdbcTemplate.update(sqlInsert, idFinal, u.getNombre(), u.getCorreo(), passAGuardar, fechaFija, u.getCarrera(), u.getBio(), generoOracle, ciudadRes);
        }

        // --- GUARDADO DE PREFERENCIAS ---
        try {
            String busquedaOracle = "Chicas".equals(u.getQue_busco()) ? "F" : ("Chicos".equals(u.getQue_busco()) ? "M" : "A");
            int min = u.getRango_inicio() != null ? u.getRango_inicio() : 18;
            int max = u.getRango_fin() != null ? u.getRango_fin() : 49;
            String ciudadBusq = (u.getCiudad_busqueda() != null) ? u.getCiudad_busqueda() : ciudadRes;

            jdbcTemplate.update("DELETE FROM PreferenciaBusqueda WHERE id_usuario = ?", idFinal);
            String sqlPrefInsert = "INSERT INTO PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, ciudad_interes) VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(sqlPrefInsert, idFinal, min, max, busquedaOracle, ciudadBusq);
        } catch (Exception e) {
            System.err.println("Error en Preferencias: " + e.getMessage());
        }

        // --- GUARDADO DE FOTOS (Lo he vuelto a añadir que faltaba) ---
        try {
            jdbcTemplate.update("DELETE FROM ArchivoMultimedia WHERE id_usuario = ?", idFinal);
            String sqlFoto = "INSERT INTO ArchivoMultimedia (id_archivo, id_usuario, tipo_archivo, url, fecha_subida) VALUES (id_archivo.NEXTVAL, ?, 'foto', ?, SYSDATE)";
            String[] fotos = {u.getFoto1(), u.getFoto2(), u.getFoto3(), u.getFoto4(), u.getFoto5(), u.getFoto6()};
            for (String f : fotos) {
                if (f != null && !f.trim().isEmpty()) {
                    jdbcTemplate.update(sqlFoto, idFinal, f);
                }
            }
        } catch (Exception e) {
            System.err.println("Error al guardar Fotos: " + e.getMessage());
        }

        u.setId_usuario(idFinal);
        return u;
    }

    @GetMapping("/descubrir/{miId}")
    public List<Usuario> obtenerUsuariosParaDescubrir(@PathVariable Long miId) {
        try {
            String prefQuery = "SELECT genero_interes, edad_min, edad_max, ciudad_interes FROM PreferenciaBusqueda WHERE id_usuario = ?";
            return jdbcTemplate.queryForObject(prefQuery, new Object[]{miId}, (rs, rowNum) -> {
                String interes = rs.getString("genero_interes");
                int min = rs.getInt("edad_min");
                int max = rs.getInt("edad_max");
                String ciudadBusqueda = rs.getString("ciudad_interes");

                // Filtro Provincia (Global) vs Ciudad (Específico)
                if ("Cádiz (Provincia)".equals(ciudadBusqueda)) {
                    return usuarioRepository.encontrarCandidatosProvinciaCompleta(miId, interes, min, max);
                } else {
                    if ("A".equals(interes)) {
                        return usuarioRepository.encontrarCandidatosCualquierGeneroUbicacion(miId, min, max, ciudadBusqueda);
                    } else {
                        return usuarioRepository.encontrarCandidatosUbicacion(miId, interes, min, max, ciudadBusqueda);
                    }
                }
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // --- ¡MÉTODO DE BORRADO ARREGLADO! ---
    @DeleteMapping("/{correo}")
    @Transactional
    public ResponseEntity<Void> eliminarUsuario(@PathVariable String correo) {
        try {
            String sqlId = "SELECT u.id_usuario FROM Usuario u WHERE u.datos.correo = ?";
            Long idUsuario = jdbcTemplate.queryForObject(sqlId, Long.class, correo);

            if (idUsuario != null) {
                jdbcTemplate.update("DELETE FROM ArchivoMultimedia WHERE id_usuario = ?", idUsuario);
                jdbcTemplate.update("DELETE FROM PreferenciaBusqueda WHERE id_usuario = ?", idUsuario);
                jdbcTemplate.update("DELETE FROM Usuario WHERE id_usuario = ?", idUsuario);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            System.err.println("Error al borrar la cuenta de " + correo + ": " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // --- ¡MÉTODO DE VERIFICAR CORREO ARREGLADO! ---
    @GetMapping("/existe/{correo}")
    public boolean verificarExistencia(@PathVariable String correo) {
        try {
            String sql = "SELECT COUNT(*) FROM Usuario u WHERE u.datos.correo = ?";
            Integer contador = jdbcTemplate.queryForObject(sql, Integer.class, correo);
            return contador != null && contador > 0;
        } catch (Exception e) {
            System.err.println("Aviso al verificar correo " + correo + ": " + e.getMessage());
            return false;
        }
    }

    @GetMapping("/fotos/{id}")
    public List<String> obtenerFotosDeUsuario(@PathVariable Long id) {
        return archivoRepository.encontrarUrlsPorUsuario(id);
    }

    @PostMapping("/login")
    public ResponseEntity<Usuario> login(@RequestBody Usuario loginRequest) {
        try {
            String sql = "SELECT u.id_usuario, u.datos.contrasena AS contrasena FROM Usuario u WHERE u.datos.correo = ?";
            Usuario usuarioBD = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Usuario u = new Usuario();
                u.setId_usuario(rs.getLong("id_usuario"));
                u.setContrasena(rs.getString("contrasena"));
                return u;
            }, loginRequest.getCorreo());

            if (usuarioBD != null && passwordEncoder.matches(loginRequest.getContrasena(), usuarioBD.getContrasena())) {
                Usuario usuarioCompleto = obtenerTodos().stream()
                        .filter(u -> u.getId_usuario().equals(usuarioBD.getId_usuario()))
                        .findFirst()
                        .orElse(null);

                if (usuarioCompleto != null) {
                    return ResponseEntity.ok(usuarioCompleto);
                } else {
                    return ResponseEntity.status(500).build();
                }
            } else {
                return ResponseEntity.status(401).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}