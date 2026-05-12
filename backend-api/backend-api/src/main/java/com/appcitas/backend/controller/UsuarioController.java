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
import java.text.SimpleDateFormat;
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

        String generoOracle = mapearGeneroUsuario(u.getGenero());
        String generoInteresOracle = mapearGeneroBusqueda(u.getQue_busco());
        String ciudadRes = normalizarCiudadResidencia(u.getCiudad_residencia());
        String ciudadBusqueda = normalizarCiudadBusqueda(u.getCiudad_busqueda());
        String calleRes = "";
        java.sql.Date fechaNacimiento = parseFechaNacimiento(u.getFecha_nacimiento());

        if (fechaNacimiento == null) {
            throw new IllegalArgumentException("Fecha de nacimiento inválida: " + u.getFecha_nacimiento());
        }

        Integer edadMin = u.getRango_inicio() != null ? u.getRango_inicio() : 18;
        Integer edadMax = u.getRango_fin() != null ? u.getRango_fin() : 49;

        if (esEdicion) {
            idFinal = u.getId_usuario();
            String sqlUpdate = "UPDATE " + ESQUEMA + "\"USUARIO\" SET datos = " + ESQUEMA + "TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', " + ESQUEMA + "TipoUbicacion(?, ?, 0, 0), " + ESQUEMA + "TipoListaFotos(), 20) WHERE id_usuario = ?";
            jdbcTemplate.update(sqlUpdate, u.getNombre(), u.getCorreo(), passAGuardar, fechaNacimiento.toString(), u.getCarrera(), u.getBio(), generoOracle, calleRes, ciudadRes, idFinal);
        } else {
            idFinal = jdbcTemplate.queryForObject("SELECT " + ESQUEMA + "id_usuario.NEXTVAL FROM DUAL", Long.class);
            String sqlInsert = "INSERT INTO " + ESQUEMA + "\"USUARIO\" (id_usuario, datos) VALUES (?, " + ESQUEMA + "TipoUsuarioFree(?, ?, ?, TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, 'n', " + ESQUEMA + "TipoUbicacion(?, ?, 0, 0), " + ESQUEMA + "TipoListaFotos(), 20))";
            jdbcTemplate.update(sqlInsert, idFinal, u.getNombre(), u.getCorreo(), passAGuardar, fechaNacimiento.toString(), u.getCarrera(), u.getBio(), generoOracle, calleRes, ciudadRes);
        }

        // Preferencias y Fotos
        jdbcTemplate.update("DELETE FROM " + ESQUEMA + "PreferenciaBusqueda WHERE id_usuario = ?", idFinal);
        jdbcTemplate.update("INSERT INTO " + ESQUEMA + "PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, ciudad_interes, distancia_maxima) VALUES (?, ?, ?, ?, ?, ?)",
                idFinal, edadMin, edadMax, generoInteresOracle, ciudadBusqueda, 0);

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
                Usuario usuarioCompleto = obtenerTodos().stream()
                        .filter(u -> loginRequest.getCorreo().equals(u.getCorreo()))
                        .findFirst()
                        .orElse(null);

                if (usuarioCompleto != null) {
                    usuarioCompleto.setContrasena(null);
                    return ResponseEntity.ok(usuarioCompleto);
                }

                usuarioBD.setContrasena(null);
                return ResponseEntity.ok(usuarioBD);
            }
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/recibidos/{id}")
    public List<Usuario> obtenerLikesRecibidos(@PathVariable Long id) {
        String sql = "SELECT DISTINCT u.id_usuario, u.datos.nombre AS nombre, u.datos.correo AS correo, " +
                "u.datos.genero AS genero, u.datos.carrera AS carrera, u.datos.descripcion AS bio, " +
                "u.datos.fecha_nacimiento AS fecha_nacimiento " +
                "FROM " + ESQUEMA + "\"USUARIO\" u " +
                "INNER JOIN " + ESQUEMA + "SWIPE s ON s.id_origen = u.id_usuario " +
                "WHERE s.id_destino = ? AND s.tipo_swipe IN ('LIKE', 'SUPERLIKE') " +
                "ORDER BY s.fecha_swipe DESC";

        List<Usuario> usuarios = jdbcTemplate.query(sql, new Object[]{id}, (rs, rowNum) -> mapearUsuarioBasico(rs));
        enriquecerUsuarios(usuarios);
        return usuarios;
    }

    @GetMapping("/matches/{id}")
    public List<Usuario> obtenerMatches(@PathVariable Long id) {
        String sql = "SELECT DISTINCT u.id_usuario, u.datos.nombre AS nombre, u.datos.correo AS correo, " +
                "u.datos.genero AS genero, u.datos.carrera AS carrera, u.datos.descripcion AS bio, " +
            "u.datos.fecha_nacimiento AS fecha_nacimiento, m.fecha_match AS fecha_match " +
                "FROM " + ESQUEMA + "\"USUARIO\" u " +
                "INNER JOIN " + ESQUEMA + "MATCH m ON (m.id_usuario1 = u.id_usuario OR m.id_usuario2 = u.id_usuario) " +
                "WHERE (m.id_usuario1 = ? OR m.id_usuario2 = ?) AND m.activo = 1 " +
                "AND u.id_usuario <> ? " +
            "ORDER BY fecha_match DESC";

        List<Usuario> usuarios = jdbcTemplate.query(sql, new Object[]{id, id, id}, (rs, rowNum) -> mapearUsuarioBasico(rs));
        enriquecerUsuarios(usuarios);
        return usuarios;
    }

    @GetMapping("/descubrir/{id}")
    public List<Usuario> obtenerUsuariosParaDescubrir(@PathVariable Long id) {
        try {
            System.err.println("🔍 DEBUG: Iniciando descubrimiento para usuario " + id);

            final Integer[] edadMin = {18};
            final Integer[] edadMax = {49};
            final String[] generoInteres = {"A"};
            final String[] ciudadInteres = {"Cádiz (Provincia)"};

            String sqlPref = "SELECT edad_min, edad_max, genero_interes, ciudad_interes FROM " + ESQUEMA + "PreferenciaBusqueda WHERE id_usuario = ?";
            jdbcTemplate.query(sqlPref, new Object[]{id}, rs -> {
                while (rs.next()) {
                    edadMin[0] = rs.getInt("edad_min");
                    edadMax[0] = rs.getInt("edad_max");
                    generoInteres[0] = rs.getString("genero_interes");
                    ciudadInteres[0] = rs.getString("ciudad_interes");
                }
                return null;
            });

            StringBuilder sqlCandidatos = new StringBuilder();
            List<Object> params = new ArrayList<>();
            sqlCandidatos.append("SELECT u.id_usuario, u.datos.nombre, u.datos.correo, ")
                    .append("u.datos.genero, u.datos.carrera, u.datos.descripcion, u.datos.ubicacion.ciudad AS ciudad_residencia ")
                    .append("FROM ").append(ESQUEMA).append("\"USUARIO\" u ")
                    .append("WHERE u.id_usuario != ? ")
                    .append("AND NOT EXISTS (SELECT 1 FROM ").append(ESQUEMA).append("SWIPE s WHERE s.id_origen = ? AND s.id_destino = u.id_usuario) ");
            params.add(id);
            params.add(id);

            if (generoInteres[0] != null && !generoInteres[0].equalsIgnoreCase("A")) {
                sqlCandidatos.append("AND u.datos.genero = ? ");
                params.add(generoInteres[0]);
            }

            sqlCandidatos.append("AND FLOOR(MONTHS_BETWEEN(SYSDATE, u.datos.fecha_nacimiento) / 12) BETWEEN ? AND ? ");
            params.add(edadMin[0]);
            params.add(edadMax[0]);

            boolean filtrarPorCiudad = ciudadInteres[0] != null
                    && !ciudadInteres[0].isBlank()
                    && !"Cádiz (Provincia)".equalsIgnoreCase(ciudadInteres[0])
                    && !"A".equalsIgnoreCase(ciudadInteres[0]);

            if (filtrarPorCiudad) {
                sqlCandidatos.append("AND u.datos.ubicacion.ciudad = ? ");
                params.add(ciudadInteres[0]);
            }

            sqlCandidatos.append("FETCH FIRST 100 ROWS ONLY");

            List<Usuario> candidatos = jdbcTemplate.query(sqlCandidatos.toString(), params.toArray(), (rs, rowNum) -> {
                Usuario u = new Usuario();
                u.setId_usuario(rs.getLong("id_usuario"));
                u.setNombre(rs.getString("nombre"));
                u.setCorreo(rs.getString("correo"));
                String genActual = rs.getString("genero");
                u.setGenero(mapearGenero(genActual));
                u.setCarrera(rs.getString("carrera"));
                u.setBio(rs.getString("descripcion"));
                u.setCiudad_residencia(rs.getString("ciudad_residencia"));
                u.setQue_busco(mapaGeneroInteresATexto(generoInteres[0]));
                u.setRango_inicio(edadMin[0]);
                u.setRango_fin(edadMax[0]);
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

    private Usuario mapearUsuarioBasico(java.sql.ResultSet rs) throws java.sql.SQLException {
        Usuario u = new Usuario();
        u.setId_usuario(rs.getLong("id_usuario"));
        u.setNombre(rs.getString("nombre"));
        u.setCorreo(rs.getString("correo"));
        u.setGenero(mapearGenero(rs.getString("genero")));
        u.setCarrera(rs.getString("carrera"));
        u.setBio(rs.getString("bio"));
        u.setFecha_nacimiento(rs.getString("fecha_nacimiento"));
        return u;
    }

    private void enriquecerUsuarios(List<Usuario> usuarios) {
        for (Usuario u : usuarios) {
            try {
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
                System.err.println("Error enriqueciendo usuario " + u.getId_usuario() + ": " + e.getMessage());
            }
        }
    }

    private String mapearGenero(String codigoOracle) {
        if (codigoOracle == null) return "Otro";
        switch (codigoOracle.trim().toUpperCase()) {
            case "M":
                return "Chico";
            case "F":
                return "Chica";
            case "O":
                return "Otro";
            default:
                return codigoOracle;
        }
    }

    private String mapearGeneroUsuario(String genero) {
        if (genero == null) return "O";
        switch (genero.trim().toLowerCase()) {
            case "chico":
                return "M";
            case "chica":
                return "F";
            case "otro":
                return "O";
            default:
                return "O";
        }
    }

    private String mapearGeneroBusqueda(String queBusco) {
        if (queBusco == null) return "A";
        switch (queBusco.trim().toLowerCase()) {
            case "chicos":
                return "M";
            case "chicas":
                return "F";
            default:
                return "A";
        }
    }

    private String mapaGeneroInteresATexto(String interes) {
        if (interes == null) return "Todos";
        switch (interes.trim().toUpperCase()) {
            case "M":
                return "Chicos";
            case "F":
                return "Chicas";
            default:
                return "Todos";
        }
    }

    private String normalizarCiudadResidencia(String ciudad) {
        return (ciudad == null || ciudad.isBlank()) ? "Cádiz (Ciudad)" : ciudad;
    }

    private String normalizarCiudadBusqueda(String ciudad) {
        return (ciudad == null || ciudad.isBlank()) ? "Cádiz (Provincia)" : ciudad;
    }

    private java.sql.Date parseFechaNacimiento(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String[] formatos = new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "ddMMyyyy", "yyyyMMdd"};
        for (String formato : formatos) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(formato, java.util.Locale.getDefault());
                parser.setLenient(false);
                java.util.Date parsed = parser.parse(valor.trim());
                if (parsed != null) {
                    return new java.sql.Date(parsed.getTime());
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
