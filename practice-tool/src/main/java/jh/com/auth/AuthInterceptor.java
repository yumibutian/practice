package jh.com.auth;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验 + 把用户身份写入 UserContext。
 *
 * <p>preHandle 设置、afterCompletion 清理必须成对：Tomcat 复用线程处理请求，
 * 不清理会让 ThreadLocal 残留上一个用户的 id，任何绕过本拦截器却读取
 * UserContext 的代码都会拿到别人的身份。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        StpUtil.checkLogin();
        // P0 单租户恒为 0；P1 多租户时改为从 StpUtil.getSession() 读取
        UserContext.set(StpUtil.getLoginIdAsLong(), 0L);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
