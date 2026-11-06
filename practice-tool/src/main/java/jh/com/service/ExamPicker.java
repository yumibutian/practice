package jh.com.service;


import jh.com.entity.Question;

import java.util.*;

/**
 * 随机抽题的算法
 */
public final class ExamPicker {

    /**
     * 题型的规范顺序
     */
    public static final List<String> TYPE_ORDER =
            List.of("single", "multiple", "judge", "fill", "essay");

    private ExamPicker() {
    }

    /**
     * 把总题数按各题型的可用题数等比例分配到题型上
     */
    public static Map<String, Integer> allocate(Map<String, Long> available, int total) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (available == null || available.isEmpty() || total <= 0) return result;

        Map<String, Long> usable = new LinkedHashMap<>();
        long sum = 0;
        for (var e : available.entrySet()) {
            long n = e.getValue() == null ? 0L : e.getValue();
            if (n <= 0) continue;
            usable.put(e.getKey(), n);
            sum += n;
        }
        if (sum <= 0) return result;

        long n = Math.min(total, sum);

        Map<String, Long> remainder = new HashMap<>();
        long assigned = 0;
        for (var e : usable.entrySet()) {
            long raw = e.getValue() * n;
            long base = raw / sum;
            result.put(e.getKey(), (int) base);
            remainder.put(e.getKey(), raw % sum);
            assigned += base;
        }

        long left = n - assigned;
        if (left > 0) {
            List<String> byRemainder = new ArrayList<>(usable.keySet());
            byRemainder.sort(Comparator
                    .comparingLong((String t) -> remainder.getOrDefault(t, 0L)).reversed()
                    .thenComparingInt(ExamPicker::canonicalIndex)
                    .thenComparing(Comparator.naturalOrder()));
            for (String type : byRemainder) {
                if (left <= 0) break;
                if (result.get(type) >= usable.get(type)) continue;
                result.put(type, result.get(type) + 1);
                left--;
            }
        }

        result.values().removeIf(v -> v <= 0);
        return result;
    }

    /**
     * 按题型比例随机抽取
     */
    public static List<Question> pick(List<Question> all, int requested, Random random) {
        if (all == null || all.isEmpty() || requested <= 0) return List.of();

        Map<String, List<Question>> buckets = new LinkedHashMap<>();
        Map<String, Long> countByType = new LinkedHashMap<>();
        for (Question q : all) {
            String type = typeOf(q);
            buckets.computeIfAbsent(type, k -> new ArrayList<>()).add(q);
            countByType.merge(type, 1L, Long::sum);
        }

        Map<String, Integer> alloc = allocate(countByType, requested);
        List<Question> picked = new ArrayList<>();
        for (String type : orderedTypes(alloc.keySet())) {
            List<Question> bucket = buckets.get(type);
            int want = alloc.getOrDefault(type, 0);
            if (bucket == null || want <= 0) continue;

            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < bucket.size(); i++) idx.add(i);
            Collections.shuffle(idx, random);
            List<Integer> take = new ArrayList<>(idx.subList(0, Math.min(want, idx.size())));
            Collections.sort(take);
            for (int i : take) picked.add(bucket.get(i));
        }
        return picked;
    }

    private static List<String> orderedTypes(Iterable<String> types) {
        List<String> ordered = new ArrayList<>();
        for (String t : types) ordered.add(t);
        ordered.sort(Comparator.comparingInt(ExamPicker::canonicalIndex)
                .thenComparing(Comparator.naturalOrder()));
        return ordered;
    }

    private static int canonicalIndex(String type) {
        int i = TYPE_ORDER.indexOf(type);
        return i < 0 ? TYPE_ORDER.size() : i;
    }

    private static String typeOf(Question q) {
        return q.getType() == null ? "" : q.getType();
    }
}
