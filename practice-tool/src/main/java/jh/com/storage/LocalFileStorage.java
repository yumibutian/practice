package jh.com.storage;


import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${quiz.storage.local.root}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(InputStream in, String key, long size) {
        Path target = resolve(key);
        Path tmp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                       StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件写入失败", e);
        }
    }

    @Override
    public InputStream read(String key) {
        Path target = resolve(key);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件读取失败：" + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件删除失败：" + key, e);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的存储 key");
        }
        return target;
    }
}
