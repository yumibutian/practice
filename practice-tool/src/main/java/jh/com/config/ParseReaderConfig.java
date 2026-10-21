package jh.com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 解析读取器装配。parse/ 包保持零 Spring 依赖，注册表在这里组装成 bean
 * ——新增格式只需两步：写 reader 实现 + 在下面 List 里加一项。
 */
@Configuration
public class ParseReaderConfig {

//    @Bean
//    public FormatReaderRegistry formatReaderRegistry() {
//        return new FormatReaderRegistry(List.of(
//            new ExcelFormatReader(),
//            new MarkdownFormatReader(),
//            new CsvFormatReader(),
//            new JsonFormatReader(),
//            new PdfFormatReader(),
//            new WordFormatReader()
//        ));
//    }
}
