package com.appcitas.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String ESQUEMA = "ESILIGUE_ADMIN.";

    @PostMapping("/enviar")
    @Transactional
    public ResponseEntity<String> enviarMensaje(@RequestBody MensajeChatRequest req) {
        if (req == null || req.id_emisor == null || req.id_receptor == null || req.texto == null || req.texto.isBlank()) {
            return ResponseEntity.badRequest().body("ERROR: Datos inválidos");
        }

        try {
            String sqlMatch = "SELECT m.id_match FROM " + ESQUEMA + "\"MATCH\" m " +
                    "WHERE m.activo = 1 AND m.id_usuario1 = LEAST(?, ?) AND m.id_usuario2 = GREATEST(?, ?)";

            Long idMatch = jdbcTemplate.queryForObject(
                    sqlMatch,
                    Long.class,
                    req.id_emisor, req.id_receptor, req.id_emisor, req.id_receptor
            );

            if (idMatch == null) {
                return ResponseEntity.badRequest().body("ERROR: No existe match entre usuarios");
            }

            String sqlInsert = "INSERT INTO " + ESQUEMA + "Mensaje (id_mensaje, id_match, id_emisor, texto, fecha_envio) " +
                    "VALUES (" + ESQUEMA + "id_mensaje.NEXTVAL, ?, ?, ?, SYSDATE)";
            jdbcTemplate.update(sqlInsert, idMatch, req.id_emisor, req.texto.trim());

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("ERROR: " + e.getMessage());
        }
    }

    @GetMapping("/conversacion/{idA}/{idB}")
    public List<MensajeChatDto> obtenerConversacion(@PathVariable Long idA, @PathVariable Long idB) {
        String sql = "SELECT ms.id_mensaje, ms.id_emisor, ms.texto, " +
                "TO_CHAR(ms.fecha_envio, 'YYYY-MM-DD\"T\"HH24:MI:SS') AS fecha_envio " +
                "FROM " + ESQUEMA + "Mensaje ms " +
                "INNER JOIN " + ESQUEMA + "\"MATCH\" mt ON mt.id_match = ms.id_match " +
                "WHERE mt.activo = 1 AND mt.id_usuario1 = LEAST(?, ?) AND mt.id_usuario2 = GREATEST(?, ?) " +
                "ORDER BY ms.fecha_envio ASC";

        return jdbcTemplate.query(sql, new Object[]{idA, idB, idA, idB}, (rs, rowNum) -> {
            MensajeChatDto m = new MensajeChatDto();
            m.id_mensaje = rs.getLong("id_mensaje");
            m.id_emisor = rs.getLong("id_emisor");
            m.texto = rs.getString("texto");
            m.fecha_envio = rs.getString("fecha_envio");
            return m;
        });
    }

    public static class MensajeChatRequest {
        public Long id_emisor;
        public Long id_receptor;
        public String texto;
    }

    public static class MensajeChatDto {
        public Long id_mensaje;
        public Long id_emisor;
        public String texto;
        public String fecha_envio;
    }
}
