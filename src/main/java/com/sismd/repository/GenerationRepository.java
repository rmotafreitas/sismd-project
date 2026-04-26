package com.sismd.repository;

import com.sismd.model.GenerationRecord;

import java.util.List;

public interface GenerationRepository {
    void save(GenerationRecord record);
    List<GenerationRecord> findAll();
    void delete(String uuid);
    void deleteAll();
}
