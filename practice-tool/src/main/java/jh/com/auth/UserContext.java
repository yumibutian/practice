package jh.com.auth;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;

public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long userId, Long tenantId) {
        USER_ID.set(userId);
        TENANT_ID.set(tenantId == null ? 0L : tenantId);
    }

    public static void clear() {
        USER_ID.remove();
        TENANT_ID.remove();
    }

    public static Long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return id;
    }

    public static Long requireTenantId() {
        Long id = TENANT_ID.get();
        return id == null ? 0L : id;
    }
}
