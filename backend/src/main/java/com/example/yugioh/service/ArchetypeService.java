package com.example.yugioh.service;

import com.example.yugioh.model.Archetype;
import com.example.yugioh.repository.ArchetypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Service for managing card archetype entities and their lifecycle.
 * 
 * Purpose:
 * - Ensures archetypes exist before cards reference them (prevents foreign key violations)
 * - Handles concurrent archetype creation gracefully (upsert-like behavior)
 * - Used during: Bulk API import, manual card creation, card updates
 * 
 * Database Constraints:
 * - Archetype.name has UNIQUE constraint
 * - Card.archetype_id is foreign key to Archetype.id
 * - Cannot create card with non-existent archetype
 * 
 * Concurrency Handling:
 * - Multiple threads may try to create same archetype simultaneously
 * - Strategy: Try to create, catch DataIntegrityViolationException, re-query
 * - Result: Only one creation succeeds, others get existing record
 * 
 * Called By:
 * - CardDataLoader.loadCardData() - bulk import creates all archetypes first
 * - CardCacheService.createCard() - ensures archetype exists before creating card
 * - CardCacheService.updateCard() - ensures new archetype exists when card changes archetype
 */
@Service
public class ArchetypeService {

    private static final Logger logger = LoggerFactory.getLogger(ArchetypeService.class);
    private final ArchetypeRepository archetypeRepository;

    public ArchetypeService(ArchetypeRepository archetypeRepository) {
        this.archetypeRepository = archetypeRepository;
    }

    // Called by CardDataLoader (bulk API import) and CardCacheService (single card create/update)
    // Ensures archetypes exist before cards reference them to prevent foreign key violations
    @Transactional
    public Map<String, Archetype> ensureArchetypes(Collection<String> rawNames) {
        Set<String> names = new HashSet<>();
        if (rawNames != null) {
            for (String n : rawNames) {
                if (n != null && !n.trim().isEmpty()) names.add(n);
            }
        }

        Map<String, Archetype> archetypeMap = new HashMap<>();
        if (names.isEmpty()) return archetypeMap;

        List<Archetype> existing = archetypeRepository.findAllByNameIn(names);
        for (Archetype a : existing) {
            archetypeMap.put(a.getName(), a);
            names.remove(a.getName());
        }

        if (!names.isEmpty()) {
            List<Archetype> toCreate = new ArrayList<>();
            for (String n : names) {
                Archetype a = new Archetype();
                a.setName(n);
                toCreate.add(a);
            }
            try {
                List<Archetype> saved = archetypeRepository.saveAll(toCreate);
                for (Archetype a : saved) {
                    archetypeMap.put(a.getName(), a);
                    logger.info("Created archetype: {}", a.getName());
                }
            } catch (DataIntegrityViolationException ex) {
                logger.warn("Concurrent insert conflict when saving archetypes; re-querying existing ones", ex);

                List<Archetype> reFetched = archetypeRepository.findAllByNameIn(names);
                for (Archetype a : reFetched) {
                    archetypeMap.put(a.getName(), a);
                }

                Set<String> remaining = new HashSet<>(names);
                for (Archetype a : reFetched) remaining.remove(a.getName());
                for (String rn : remaining) {
                    try {
                        Archetype single = new Archetype();
                        single.setName(rn);
                        Archetype savedSingle = archetypeRepository.save(single);
                        archetypeMap.put(savedSingle.getName(), savedSingle);
                        logger.info("Created archetype (retry): {}", savedSingle.getName());
                    } catch (DataIntegrityViolationException ex2) {
                        Archetype existingA = archetypeRepository.findByName(rn);
                        if (existingA != null) {
                            archetypeMap.put(existingA.getName(), existingA);
                        } else {
                            logger.error("Failed to save or find archetype {} after concurrent conflict", rn, ex2);
                        }
                    }
                }
            }
        }

        logger.info("Ensured {} archetypes in DB.", archetypeMap.size());
        return archetypeMap;
    }
}
