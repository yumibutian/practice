package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.FileInitReq;
import jh.com.doto.FileInitResp;
import jh.com.entity.File;
import jh.com.mapper.FileMapper;
import jh.com.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileService {

    /** 仅 init 建了记录、字节还没落盘。 */
    private static final int STATUS_PENDING_UPLOAD = 0;
    /** 字节已落盘。由上传接口在写入成功后置位。 */
    private static final int STATUS_UPLOADED = 1;
    private static final int STATUS_PARSED = 2;

    private final FileMapper fileMapper;
    private final FileStorage storage;
    private final long maxSize;
    private final int expireDays;
    private final Set<String> allowedExt;

    public FileService(FileMapper fileMapper,
                       FileStorage storage,
                       @Value("${quiz.file.max-size}") long maxSize,
                       @Value("${quiz.file.expire-days}") int expireDays,
                       @Value("${quiz.file.allowed-ext}") String allowedExt) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.maxSize = maxSize;
        this.expireDays = expireDays;
        // 构造时解析一次
        this.allowedExt = Arrays.stream(allowedExt.split(","))
            .map(String::trim).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
    }

    public FileInitResp init(Long userId, FileInitReq req) {
        String ext = req.ext().toLowerCase().replace(".", "");
        if (!allowedExt.contains(ext)) {
            throw new BizException(ErrorCode.FILE_EXT_UNSUPPORTED, "不支持的文件类型：" + ext);
        }
        if (req.fileSize() > maxSize) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "单个文件请小于 " + (maxSize / 1024 / 1024) + "MB");
        }

        File existing = findByHash(userId, req.fileHash());
        if (existing != null) {
            if (existing.getStatus() == STATUS_PENDING_UPLOAD) {
                return FileInitResp.needUpload(existing.getId());
            }
            return FileInitResp.reused(existing.getId(), existing.getStatus() >= STATUS_PARSED);
        }

        File qbFile = new File();
        qbFile.setTenantId(0L);
        qbFile.setUserId(userId);
        qbFile.setFileName(req.fileName());
        qbFile.setFileExt(ext);
        qbFile.setFileSize(req.fileSize());
        qbFile.setFileHash(req.fileHash());
        qbFile.setStorageKey("user/" + userId + "/" + req.fileHash() + "." + ext);
        qbFile.setStatus(STATUS_PENDING_UPLOAD);
        qbFile.setExpireAt(LocalDateTime.now().plusDays(expireDays));
        try {
            fileMapper.insert(qbFile);
        } catch (DuplicateKeyException e) {
            File concurrent = findByHash(userId, req.fileHash());
            if (concurrent != null) {
                return FileInitResp.reused(concurrent.getId(),
                                           concurrent.getStatus() >= STATUS_PARSED);
            }
            throw new BizException(ErrorCode.CONFLICT, "文件记录创建失败，请重试");
        }

        return FileInitResp.needUpload(qbFile.getId());
    }

     //查同一用户下未删除的同 hash 记录
    private File findByHash(Long userId, String fileHash) {
        return fileMapper.selectOne(Wrappers.<File>lambdaQuery()
            .eq(File::getUserId, userId)
            .eq(File::getFileHash, fileHash)
            .ne(File::getStatus, 9)
            .last("limit 1"));
    }

    /**
     * 接收上传的字节并落盘，成功后把记录置为已上传。
     */
    public void upload(Long userId, Long fileId, InputStream in, long contentLength) {
        File file = requireOwned(userId, fileId);

        if (file.getStatus() != STATUS_PENDING_UPLOAD) {
            if (file.getStatus() == STATUS_PARSED) {
                throw new BizException(ErrorCode.CONFLICT, "该文件已解析完成，如需重新上传请先删除");
            }
            throw new BizException(ErrorCode.CONFLICT, "该文件已上传，无需重复上传");
        }
        if (contentLength <= 0) {
            throw new BizException(ErrorCode.FILE_EMPTY, "文件内容为空");
        }
        if (contentLength > maxSize) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                "单个文件请小于 " + (maxSize / 1024 / 1024) + "MB");
        }

        MessageDigest digest = newSha256();
        storage.store(new DigestInputStream(in, digest), file.getStorageKey(), contentLength);

        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!actualHash.equalsIgnoreCase(file.getFileHash())) {
            storage.delete(file.getStorageKey());
            throw new BizException(ErrorCode.PARAM_INVALID, "文件内容与声明的哈希不一致，请重新上传");
        }

        File update = new File();
        update.setId(file.getId());
        update.setFileSize(contentLength);
        update.setStatus(STATUS_UPLOADED);
        update.setExpireAt(LocalDateTime.now().plusDays(expireDays));
        fileMapper.updateById(update);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    public File requireOwned(Long userId, Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || file.getStatus() == 9) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该文件");
        }
        return file;
    }
}
