package jh.com.parse.task;

/**
 * 解析任务的四阶段进度区间
 */
public enum ParseStage {
    QUEUED(0, 0),
    NORMALIZE(0, 30),
    SPLIT(30, 70),
    VALIDATE(70, 90),
    PREVIEW(90, 100),
    DONE(100, 100);

    private final int from;
    private final int to;

    ParseStage(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int progressAt(double ratioInStage) {
        double clamped = Math.max(0, Math.min(1, ratioInStage));
        int value = (int) Math.round(from + (to - from) * clamped);
        return Math.max(from, Math.min(to, value));
    }
}
