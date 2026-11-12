package jh.com.service;

import jh.com.common.BizException;
import jh.com.common.ErrorCode;
import jh.com.doto.FeedbackReq;
import jh.com.entity.Feedback;
import jh.com.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 内容反馈
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    public static final String TYPE_CONTENT_REPORT = "CONTENT_REPORT";
    public static final String TYPE_ADVICE = "ADVICE";
    public static final String TYPE_OTHER = "OTHER";

    private static final int MAX_CONTENT = 1000;
    private static final int MAX_CONTACT = 128;

    private static final Set<String> TYPES = Set.of(TYPE_CONTENT_REPORT, TYPE_ADVICE, TYPE_OTHER);

    private final FeedbackMapper feedbackMapper;

    /**
     * 落库并返回新记录
     */
    @Transactional
    public Long submit(Long userId, FeedbackReq req) {
        String type = normalizeType(req == null ? null : req.type());
        String content = trimToNull(req == null ? null : req.content());
        String contact = trimToNull(req == null ? null : req.contact());

        if (content == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写反馈内容");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "反馈内容请控制在 " + MAX_CONTENT + " 字以内");
        }
        if (contact != null && contact.length() > MAX_CONTACT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "联系方式请控制在 " + MAX_CONTACT + " 字以内");
        }

        Feedback feedback = new Feedback();
        feedback.setTenantId(0L);
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setContent(content);
        feedback.setContact(contact);
        feedback.setStatus(1);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);

        log.info("收到反馈 id={} userId={} type={}", feedback.getId(), userId, type);
        return feedback.getId();
    }

    /**
     * 收敛类型
     */
    private String normalizeType(String raw) {
        String type = trimToNull(raw);
        if (type == null) return TYPE_OTHER;
        type = type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "反馈类型不合法");
        }
        return type;
    }


    private String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
