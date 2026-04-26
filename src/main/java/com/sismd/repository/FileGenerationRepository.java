package com.sismd.repository;

import com.sismd.model.GenerationRecord;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileGenerationRepository implements GenerationRepository {

    private final Path historyFile;
    private final List<GenerationRecord> cache = new ArrayList<>();

    public FileGenerationRepository(Path uploadsBase) {
        this.historyFile = uploadsBase.resolve("history.dat");
        load();
    }

    @Override
    public synchronized void save(GenerationRecord record) {
        cache.add(record);
        persist();
    }

    @Override
    public synchronized List<GenerationRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(cache));
    }

    @Override
    public synchronized void delete(String uuid) {
        cache.removeIf(r -> r.getUuid().equals(uuid));
        persist();
    }

    @Override
    public synchronized void deleteAll() {
        cache.clear();
        persist();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(historyFile)) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(historyFile.toFile()))) {
            List<GenerationRecord> loaded = (List<GenerationRecord>) in.readObject();
            cache.addAll(loaded);
        } catch (Exception e) {
            // corrupt or incompatible file — start fresh
            cache.clear();
        }
    }

    private void persist() {
        try {
            Files.createDirectories(historyFile.getParent());
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(historyFile.toFile()))) {
                out.writeObject(new ArrayList<>(cache));
            }
        } catch (IOException e) {
            // non-fatal — history just won't survive this session
        }
    }
}
