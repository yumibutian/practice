package jh.com.parse.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.parse.model.ParseContext;
import jh.com.parse.model.TextDoc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JSON 读取器
 */
public class JsonFormatReader implements FormatReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Set<String> supportedExts() { return Set.of("json"); }

    @Override
    public int priority() { return 30; }

    @Override
    public TextDoc build(InputStream in, ParseContext ctx) {
        JsonNode root;
        try {
            root = MAPPER.readTree(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARSE_FAILED, "JSON 解析失败", e);
        }

        JsonNode array = root.isArray() ? root : root.path("questions");
        if (!array.isArray()) {
            throw new BizException(ErrorCode.PARSE_FAILED, "JSON 需要是题目数组或含 questions 字段");
        }

        List<String> out = new ArrayList<>();
        int seq = 0;
        for (JsonNode node : array) {
            seq++;
            out.add(seq + ". " + node.path("stem").asText("").strip());

            JsonNode options = node.path("options");
            if (options.isArray()) {
                char key = 'A';
                for (JsonNode opt : options) {
                    if (opt.isObject()) {
                        String k = opt.path("key").asText(String.valueOf(key));
                        out.add(k + ". " + opt.path("text").asText("").strip());
                    } else {
                        out.add(key + ". " + opt.asText("").strip());
                    }
                    key++;
                }
            }

            String answer = joinAnswer(node.path("answer"));
            if (!answer.isEmpty()) out.add("答案：" + answer);

            String analysis = node.path("analysis").asText("").strip();
            if (!analysis.isEmpty()) out.add("解析：" + analysis);

            out.add("");
        }
        return TextDocBuilder.fromLines(out);
    }

    private String joinAnswer(JsonNode answer) {
        if (answer.isMissingNode() || answer.isNull()) return "";
        if (answer.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : answer) sb.append(n.asText(""));
            return sb.toString().toUpperCase();
        }
        return answer.asText("").strip().toUpperCase();
    }
}
