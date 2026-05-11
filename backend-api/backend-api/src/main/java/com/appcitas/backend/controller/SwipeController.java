package com.appcitas.backend.controller;

import com.appcitas.backend.model.Match;
import com.appcitas.backend.model.Swipe;
import com.appcitas.backend.repository.MatchRepository;
import com.appcitas.backend.repository.SwipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date; // Cambiado a Date para mayor compatibilidad con Oracle DATE

@RestController
@RequestMapping("/api/swipes")
public class SwipeController {

    @Autowired
    private SwipeRepository swipeRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate; // <--- Asegúrate de que esta línea esté así

    @PostMapping("/registrar")
    @Transactional
    public String registrarSwipe(@RequestBody Swipe swipe) {
        try {
            // Usamos una forma más directa de jdbcTemplate
            jdbcTemplate.update(
                    "BEGIN pkg_esiligue.registrar_swipe(?, ?, ?); END;",
                    swipe.getId_origen(),
                    swipe.getId_destino(),
                    swipe.getTipo_swipe()
            );

            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
}