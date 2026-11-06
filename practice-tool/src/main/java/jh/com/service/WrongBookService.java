package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jh.com.doto.WrongItemView;
import jh.com.entity.Question;
import jh.com.entity.WrongBook;
import jh.com.mapper.QuestionMapper;
import jh.com.mapper.WrongBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本
 */
@Slf4j
@Service
public class WrongBookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WrongBookMapper wrongMapper;

    private final QuestionMapper questionMapper;

    private final BankService bankService;

    private final int masteredStreak;

    public WrongBookService(WrongBookMapper wrongMapper,
                            QuestionMapper questionMapper,
                            BankService bankService,
                            @Value("${quiz.wrong.mastered-streak}") int masteredStreak) {
        this.wrongMapper = wrongMapper;
        this.questionMapper = questionMapper;
        this.bankService = bankService;
        this.masteredStreak = masteredStreak;
    }

    /**
     * 记录一次作答结果。答错累加次数并清零连对；答对累加连对，达到阈值标为已掌握。
     */
    @Transactional
    public void record(Long userId, Long bankId, Long questionId, boolean wrong) {
        WrongBook existing = wrongMapper.selectOne(Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getUserId, userId)
                .eq(WrongBook::getQuestionId, questionId)
                .last("limit 1"));

        if (existing == null) {
            if (!wrong) return;
            WrongBook w = new WrongBook();
            w.setUserId(userId);
            w.setQuestionId(questionId);
            w.setBankId(bankId);
            w.setWrongCount(1);
            w.setRightStreak(0);
            w.setMastered(0);
            w.setLastWrongAt(LocalDateTime.now());
            wrongMapper.insert(w);
            return;
        }

        WrongBook update = new WrongBook();
        update.setId(existing.getId());
        if (wrong) {
            update.setWrongCount((existing.getWrongCount() == null ? 0 : existing.getWrongCount()) + 1);
            update.setRightStreak(0);
            update.setMastered(0);
            update.setLastWrongAt(LocalDateTime.now());
        } else {
            int streak = (existing.getRightStreak() == null ? 0 : existing.getRightStreak()) + 1;
            update.setRightStreak(streak);
            update.setMastered(streak >= masteredStreak ? 1 : 0);
        }
        wrongMapper.updateById(update);
    }

    /**
     * 未掌握的错题题号集合，供随机抽题（scope=WRONG）使用
     */
    public Set<Long> wrongQuestionIds(Long userId, Long bankId) {
        return wrongMapper.selectList(Wrappers.<WrongBook>lambdaQuery()
                        .eq(WrongBook::getUserId, userId)
                        .eq(WrongBook::getBankId, bankId)
                        .eq(WrongBook::getMastered, 0))
                .stream().map(WrongBook::getQuestionId).collect(Collectors.toSet());
    }

    /**
     * 错题本列表
     */
    public List<WrongItemView> list(Long userId, Long bankId, Integer mastery) {
        var query = Wrappers.<WrongBook>lambdaQuery()
                .eq(WrongBook::getUserId, userId)
                .orderByDesc(WrongBook::getLastWrongAt);
        if (bankId != null) query.eq(WrongBook::getBankId, bankId);
        if (mastery != null) query.eq(WrongBook::getMastered, mastery);

        List<WrongBook> wrongs = wrongMapper.selectList(query);
        if (wrongs.isEmpty()) return List.of();

        Map<Long, Question> questions = new HashMap<>();
        Map<Long, String> bankNames = new HashMap<>();
        List<Long> ids = wrongs.stream().map(WrongBook::getQuestionId).toList();
        for (Question q : questionMapper.selectBatchIds(ids)) {
            questions.put(q.getId(), q);
        }

        List<WrongItemView> result = new ArrayList<>();
        for (WrongBook w : wrongs) {
            Question q = questions.get(w.getQuestionId());
            if (q == null) continue;
            String bankName = bankNames.computeIfAbsent(w.getBankId(), id -> {
                try {
                    return bankService.requireOwned(userId, id).getName();
                } catch (Exception e) {
                    return "题库已删除";
                }
            });
            result.add(new WrongItemView(q.getId(), q.getStem(), q.getType(),
                    w.getBankId(), bankName, w.getWrongCount(), w.getRightStreak(),
                    Integer.valueOf(1).equals(w.getMastered()), w.getLastWrongAt(),
                    readAnswers(q.getAnswerJson())));
        }
        return result;
    }

    private List<String> readAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
