package jh.com.storage;

import java.io.InputStream;

public interface FileStorage {

    /**
     * 写入。key 相同则覆盖。返回实际使用的 key。
     */
    String store(InputStream in, String key, long size);

    /**
     * 读取。调用方负责关闭流。
     */
    InputStream read(String key);

    boolean exists(String key);

    void delete(String key);
}
