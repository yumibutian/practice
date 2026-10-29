package jh.com.parse.anchor;

import java.util.*;

/**
 * 从候选中选出真正的题目边界。
 */
public class AnchorSelector {

    //    排除选项内容里出现的题号
    private static final String OPTION_LINE_PREFIX = "^\\s*[\\(\\[]?\\s*[A-Ha-h]\\s*[\\)\\]]?\\s*[.、．,，:：]";

    //从候选中选出真正的题目边界
    public List<Anchor> select(List<Anchor> candidates, int maxGap, int minAnchors, int minChain) {
        List<Anchor> working = filter(candidates);
        if (working.size() < minAnchors) return List.of();

        List<Anchor> result = new ArrayList<>(working.size());
        while (working.size() >= minChain) {
            int n = working.size();
            int[] dp = new int[n];
            int[] prev = new int[n];
            Arrays.fill(dp, 1);
            Arrays.fill(prev, -1);

            int best = 0;
            for (int i = 1; i < n; i++) {
                for (int j = 0; j < i; j++) {
                    int gap = working.get(i).number() - working.get(j).number();
                    if (gap >= 1 && gap <= maxGap && dp[j] + 1 > dp[i]) {
                        dp[i] = dp[j] + 1;
                        prev[i] = j;
                    }
                }
                if (dp[i] > dp[best]) best = i;
            }
            if (dp[best] < minChain) break;

            LinkedList<Anchor> chain = new LinkedList<>();
            Set<Integer> used = new HashSet<>();
            for (int cur = best; cur != -1; cur = prev[cur]) {
                chain.addFirst(working.get(cur));
                used.add(cur);
            }
            result.addAll(chain);

            List<Anchor> remaining = new ArrayList<>(working.size() - used.size());
            for (int i = 0; i < n; i++) {
                if (!used.contains(i)) remaining.add(working.get(i));
            }
            working = remaining;
        }
        return result;
    }

    private List<Anchor> filter(List<Anchor> candidates) {
        List<Anchor> out = new ArrayList<>(candidates.size());
        for (Anchor a : candidates) {
            if (!a.lineStart()) continue;
            if (a.number() < 1 || a.number() > AnchorScanner.MAX_QUESTION_NUMBER) continue;
            if (a.raw() != null && a.raw().matches(OPTION_LINE_PREFIX + ".*")) continue;
            out.add(a);
        }
        return out;
    }

    /**
     * 链覆盖率
     */
    public double coverage(List<Anchor> chain, List<Anchor> candidates) {
        if (candidates.isEmpty()) return 0.0;
        return (double) chain.size() / candidates.size();
    }
}
