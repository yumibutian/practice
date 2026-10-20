package jh.com.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描单独成配置类，不放在 QuizApplication 上。
 *
 * <p>原因：{@code @WebMvcTest} 切片测试会以 {@code QuizApplication} 作为配置类，
 * 从而触发其上的 {@code @MapperScan}，注册出需要 {@code SqlSessionFactory} 的
 * MapperFactoryBean。而切片测试不加载数据源，上下文会直接启动失败：
 * <pre>Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required</pre>
 * 挪到独立的纯 {@code @Configuration} 类后，切片测试不会加载它。
 */
@Configuration
@MapperScan("com.*.*")
public class MybatisConfig {
}
