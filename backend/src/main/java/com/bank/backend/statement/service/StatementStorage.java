package com.bank.backend.statement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where statement PDFs live on disk and how their paths are computed.
 *
 * Layout:  <statement-root>/yyyy-MM/{accountNumber}.pdf
 *
 * In production this would be S3 or GCS; the interface stays the same.
 * Migration is "swap the implementation," not "rewrite the callers."
 */
@Service
public class StatementStorage {

    private static final Logger log = LoggerFactory.getLogger(StatementStorage.class);

    private final Path root;

    public StatementStorage(@Value("${bank.statements.dir:statements}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        log.info("Statement storage root: {}", root);
    }

    /**
     * Computes the file path for a statement and ensures the parent dir exists.
     */
    public Path pathFor(String accountNumber, int year, int month) {
        String monthDir = String.format("%04d-%02d", year, month);
        Path dir = root.resolve(monthDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create statement dir " + dir, e);
        }
        return dir.resolve(accountNumber + ".pdf");
    }

    public void write(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + path, e);
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}