package jh.com.doto;

/**
 * 用户统计
 */
public record UserProfileView(
    long bankCount,
    int practiced,
    int accuracy,
    int streakDays
) {
}
