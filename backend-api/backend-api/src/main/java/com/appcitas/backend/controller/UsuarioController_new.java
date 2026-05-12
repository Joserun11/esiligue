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

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ArchivoMultimediaRepository archivoRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    // --- CLAVE: El esquema que creamos en el init.sql ---
    private final String ESQUEMA = "ESILIGUE_ADMIN.";

    @GetMapping
    public List<Usuario> obtenerTodos() {
        List<Usuario> usuarios = usuarioRepository.obtenerTodosNativo();
        for (Usuario u : usuarios) {
            u.setRango_inicio(18);
            u.setRango_fin(49);
            u.setQue_busco("Todos");
            try {
                // Usamos alias 't' para que Oracle no se líe con el objeto 'datos'
                String sqlCiudad = "SELECT t.datos.ubicacion.ciudad FROM " + ESQUEMA + "\"USUARIO\" t WHERE t.id_usuario = ?";
                String ciudadActual = jdbcTemplate.queryForObject(sqlCiudad, String.class, u.getId_usuario());
                u.setCiudad_residencia(ciudadActual != null ? ciudadActual : "Cádiz (Ciudad)");

                String sqlPref = "SELECT edad_min, edad_max, genero_interes, ciudad_interes FROM " + ESQUEMA + "PreferenciaBusqueda WHERE id_usuario = ?";
                jdbcTemplate.query(sqlPref, new Object[]{u.getId_usuario()}, (rs) -> {
                    u.setRango_inicio(rs.getInt("edad_min"));
                    u.setRango_fin(rs.getInt("edad_max"));
                    u.setCiudad_busqueda(rs.getString("ciudad_interes"));
                    String interes = rs.getString("genero_interes");
                    u.setQue_busco("M".equals(interes) ? "Chicos" : ("F".equals(interes) ? "Chicas" : "Todos"));
                });

                List<String> urls = archivoRepository.encontrarUrlsPorUsuario(u.getId_usuario());
                if (urls != null) {
                    if (urls.size() >= 1) u.setFoto1(urls.get(0));
                    if (urls.size() >= 2) u.setFoto2(urls.get(1));
                    if (urls.size() >= 3) u.setFoto3(urls.get(2));
                }
            } catch (Exception e) {
                System.err.println("Error datos extra: " + e.getMessage());
            }
        }
        return usuarios;
    }

    @PostMapping
    @Transactional
    public Usuario registrarUsuario(@RequestBody Usuario u) {
        boolean esEdicion = (u.getId_usuario() != null && u.getId_usuario() > 0);
        Long idFinal;

        String passAGuardar = (u.getContrasena() != null && u.getContrasena().startsWith("$2a$"))
                ? u.getContrasena() : passwordEncoder.encode(u.getContrasena());

        String generoOracle = "Chico".equals(u.getGenero()) ? "M" : ("Chica".equals(u.getGenero()) ? "F" : "O");
        String ciudadRes = (u.getCiudad_residencia() != null) ? u.getCiudad_residencia() : "Cádiz (Ciudad)";
        String fechaFija = "2000-01-01"; // Fallback

        if (esEdicion) {
            idFinal = u.getId_usuario();
            String sqlUpdate = "UPDATE " + ESQUEMA + "\"USUARIO\" SET datos = " + ESQUEMA + "TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', " + ESQUEMA + "TipoUbicacion(?, 'España', 0, 0), " + ESQUEMA + "TipoListaFotos(), 20) WHERE id_usuario = ?";
            jdbcTemplate.update(sqlUpdate, u.getNombre(), u.getCorreo(), passAGuardar, fechaFija, u.getCarrera(), u.getBio(), generoOracle, ciudadRes, idFinal);
        } else {
            idFinal = jdbcTemplate.queryForObject("SELECT " + ESQUEMA + "id_usuario.NEXTVAL FROM DUAL", Long.class);
            String sqlInsert = "INSERT INTO " + ESQUEMA + "\"USUARIO\" (id_usuario, datos) VALUES (?, " + ESQUEMA + "TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', " + ESQUEMA + "TipoUbicacion(?, 'España', 0, 0), " + ESQUEMA + "TipoListaFotos(), 20))";
            jdbcTemplate.update(sqlInsert, idFinal, u.getNombre(), u.getCorreo(), passAGuardar, fechaFija, u.getCarrera(), u.getBio(), generoOracle, ciudadRes);
        }

        // Preferencias y Fotos
        jdbcTemplate.update("DELETE FROM " + ESQUEMA + "PreferenciaBusqueda WHERE id_usuario = ?", idFinal);
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + "PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, ciudad_interes, distancia_maxima) VALUES (?, ?, ?, ?, ?, ?)",
                idFinal, 18, 49, "A", ciudadRes, 0);

        u.setId_usuario(idFinal);
        return u;
    }

    @GetMapping("/existe/{correo}")
    public boolean verificarExistencia(@PathVariable String correo) {
        // AÑADIDO CHIVATO PARA LOGS
        System.out.println("DEBUG: Verificando existencia de " + correo + " en V4");
        try {
            String sql = "SELECT COUNT(*) FROM " + ESQUEMA + "\"USUARIO\" u WHERE u.datos.correo = ?";
            Integer contador = jdbcTemplate.queryForObject(sql, Integer.class, correo);
            return contador != null && contador > 0;
        } catch (Exception e) {
            System.err.println("ERROR REAL EN VERIFICAR: " + e.getMessage());
            return false;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Usuario> login(@RequestBody Usuario loginRequest) {
        try {
            String sql = "SELECT u.id_usuario, u.datos.contrasena AS contrasena FROM " + ESQUEMA + "\"USUARIO\" u WHERE u.datos.correo = ?";
            Usuario usuarioBD = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Usuario u = new Usuario();
                u.setId_usuario(rs.getLong("id_usuario"));
                u.setContrasena(rs.getString("contrasena"));
                return u;
            }, loginRequest.getCorreo());

            if (usuarioBD != null && passwordEncoder.matches(loginRequest.getContrasena(), usuarioBD.getContrasena())) {
                return ResponseEntity.ok(usuarioBD); // Devolvemos el ID para que el móvil cargue el resto
            }
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/descubrir/{id}")
    public List<Usuario> obtenerUsuariosParaDescubrir(@PathVariable Long id) {
        try {
            System.err.println("🔍 DEBUG: Iniciando descubrimiento para usuario " + id);

            // Query simplificada: devolver todos los usuarios menos el actual
            String sqlCandidatos = "SELECT u.id_usuario, u.datos.nombre, u.datos.correo, " +
                    "u.datos.genero, u.datos.carrera, u.datos.bio, u.datos.ubicacion.ciudad AS ciudad_residencia " +
                    "FROM " + ESQUEMA + "\"USUARIO\" u " +
                    "WHERE u.id_usuario != ? " +
                    "AND ROWNUM <= 100";

            List<Usuario> candidatos = jdbcTemplate.query(sqlCandidatos,
                    new Object[]{id},
                    (rs, rowNum) -> {
                        Usuario u = new Usuario();
                        u.setId_usuario(rs.getLong("id_usuario"));
                        u.setNombre(rs.getString("nombre"));
                        u.setCorreo(rs.getString("correo"));
                        String genActual = rs.getString("genero");
                        u.setGenero("M".equals(genActual) ? "Chico" : ("F".equals(genActual) ? "Chica" : "Otro"));
                        u.setCarrera(rs.getString("carrera"));
                        u.setBio(rs.getString("bio"));
                        u.setCiudad_residencia(rs.getString("ciudad_residencia"));
                        u.setQue_busco("Todos");
                        u.setRango_inicio(18);
                        u.setRango_fin(49);
                        System.err.println("  ✅ Candidato: " + u.getId_usuario() + " - " + u.getNombre() + " (" + genActual + ")");
                        return u;
                    });

            System.err.println("✅ Descubrimiento: " + candidatos.size() + " candidatos encontrados para usuario " + id);

            // Cargar fotos para cada candidato
            for (Usuario candidato : candidatos) {
                try {
                    List<String> urls = archivoRepository.encontrarUrlsPorUsuario(candidato.getId_usuario());
                    if (urls != null && !urls.isEmpty()) {
                        candidato.setFoto1(urls.get(0));
                        if (urls.size() > 1) candidato.setFoto2(urls.get(1));
                        if (urls.size() > 2) candidato.setFoto3(urls.get(2));
                    }
                } catch (Exception e) {
                    System.err.println("  ⚠️ Error cargando fotos para " + candidato.getId_usuario() + ": " + e.getMessage());
                }
            }

            return candidatos;
        } catch (Exception e) {
            System.err.println("❌ Error en obtenerUsuariosParaDescubrir: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}




