package at.pepe.trader.persistent;

import at.pepe.trader.model.Position;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.util.SerializationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public void delete(Long key) {
        try {
            db.delete((key + "").getBytes());
        } catch (RocksDBException e) {
            log.error("Error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }
}
