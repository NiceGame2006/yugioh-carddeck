package com.example.yugioh.controller;

import com.example.yugioh.model.Archetype;
import com.example.yugioh.repository.ArchetypeRepository;
import com.example.yugioh.dto.ResponseEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/archetypes")
public class ArchetypeController {
    private final ArchetypeRepository archetypeRepository;

    public ArchetypeController(ArchetypeRepository archetypeRepository) {
        this.archetypeRepository = archetypeRepository;
    }

    @GetMapping
    public ResponseEntity<ResponseEnvelope<List<Archetype>>> getAll() {
        List<Archetype> archetypes = archetypeRepository.findAll();
        ResponseEnvelope<List<Archetype>> env = ResponseEnvelope.success("Archetypes fetched", archetypes);
        return ResponseEntity.ok(env);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseEnvelope<Archetype>> getById(@PathVariable Long id) {
        return archetypeRepository.findById(id)
            .map(archetype -> {
                ResponseEnvelope<Archetype> env = ResponseEnvelope.success("Archetype fetched", archetype);
                return ResponseEntity.ok(env);
            })
            .orElseGet(() -> {
                ResponseEnvelope<Archetype> env = ResponseEnvelope.failed("Archetype not found");
                return ResponseEntity.status(404).body(env);
            });
    }
}
