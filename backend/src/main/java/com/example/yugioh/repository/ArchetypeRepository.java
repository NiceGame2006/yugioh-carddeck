package com.example.yugioh.repository;

import com.example.yugioh.model.Archetype;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ArchetypeRepository extends JpaRepository<Archetype, Long> {
    Archetype findByName(String name);
    List<Archetype> findAllByNameIn(Collection<String> names);
}
