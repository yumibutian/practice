package jh.com.doto;

/**
 * @param counted       本次中断是否已计入「已练 N 次」
 * @param answeredCount 服务端从 progress 里数出的已答题数（0 表示这次没作答）
 */
public record InterruptResp(boolean counted, int answeredCount) {}
