package at.pepe.trader.persistent;

import at.pepe.trader.model.Position;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements KeyValueRepository<Long, Position> {
    private final static String NAME = "position-db";

    private final ObjectMapper objectMapper;

    File dbDir;
    RocksDB db;

    @PostConstruct
    void initialize() {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        dbDir = new File("./pepe-trader/rocks-db", NAME);
        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            db = RocksDB.open(options, dbDir.getAbsolutePath());
        } catch(IOException | RocksDBException ex) {
            log.error("Error initializng RocksDB, check configurations and permissions: ", ex);
        }
        log.info("RocksDB for {} initialized and ready to use", NAME);
    }

    @Override
    @Async
    public synchronized void save(Long key, Position value) {
        try {
            db.put((key + "").getBytes(), objectMapper.writeValueAsBytes(value));
        } catch (RocksDBException | JsonProcessingException e) {
            log.error("Error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    @Override
    public Position find(Long key) {
        Position result = null;
        try {
            byte[] bytes = db.get((key + "").getBytes());
            if(bytes == null) return null;
            result = objectMapper.readValue(bytes, Position.class);
        } catch (RocksDBException | IOException e) {
            log.error("Error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", key, e.getCause(), e.getMessage());
        }
        return result;
    }

    public List<Position> findAllSince(OffsetDateTime offsetDateTime) {
        RocksIterator rocksIterator = db.newIterator();
        rocksIterator.seekToLast();
        List<Position> completed = new ArrayList<>();

        while (rocksIterator.isValid()) {
            try {
                byte[] bytes = rocksIterator.value();
                if(bytes == null) continue;
                Position result = objectMapper.readValue(bytes, Position.class);
                if(result == null) {
                    continue;
                }

                OffsetDateTime timestamp = getFirstTimestamp(result.getClosedAt(), result.getCreatedAt());
                if (timestamp != null && timestamp.isAfter(offsetDateTime)) {
                    completed.add(result);
                }
            } catch (IOException e) {
                log.error("Error retrieving the entry in RocksDB cause: {}, message: {}", e.getCause(), e.getMessage());
            }
        }

        return completed;
    }

    private OffsetDateTime getFirstTimestamp(OffsetDateTime first, OffsetDateTime second) {
        return Optional.ofNullable(first).orElse(second);
    }

    @Override
    public void delete(Long key) {
        try {
            db.delete((key + "").getBytes());
        } catch (RocksDBException e) {
            log.error("Error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }
}
