package jh.com.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jh.com.doto.UserProfileView;
import jh.com.entity.PracticeTally;
import jh.com.entity.QuestionBank;
import jh.com.mapper.QuestionBankMapper;
import jh.com.mapper.UserStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 统计汇总
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int PERCENT = 100;

    private final QuestionBankMapper bankMapper;

    private final UserStatsMapper statsMapper;

    public UserProfileView profile(Long userId) {
        long bankCount = bankMapper.selectCount(Wrappers.<QuestionBank>lambdaQuery()
                .eq(QuestionBank::getUserId, userId)
                .eq(QuestionBank::getStatus, 1));

        PracticeTally tally = statsMapper.practiceTally(userId);
        int answered = tally == null || tally.getAnswered() == null ? 0 : tally.getAnswered();
        int correct = tally == null || tally.getCorrect() == null ? 0 : tally.getCorrect();

        return new UserProfileView(
                bankCount,
                answered,
                accuracy(correct, answered),
                streakDays(statsMapper.practiceDays(userId))
        );
    }

    /**
     * 正确率
     */
    private int accuracy(int correct, int answered) {
        if (answered <= 0) return 0;
        return (int) Math.round(correct * (double) PERCENT / answered);
    }

    /**
     * 连续打卡天数
     */
    private int streakDays(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return 0;
        List<LocalDate> desc = days.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        if (desc.isEmpty()) return 0;

        int streak = 1;
        LocalDate cursor = desc.get(0);
        for (int i = 1; i < desc.size(); i++) {
            LocalDate day = desc.get(i);
            if (!cursor.minusDays(1).equals(day)) break;
            streak++;
            cursor = day;
        }
        return streak;
    }
}
