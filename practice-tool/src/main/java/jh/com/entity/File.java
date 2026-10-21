package jh.com.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qb_file")
public class File {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String fileName;
    private String fileExt;
    private Long fileSize;
    private String fileHash;
    private String storageKey;
    private Integer status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
