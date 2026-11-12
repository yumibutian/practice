package jh.com.entity;

import lombok.Data;

/**
 * 累计练习
 */
@Data
public class PracticeTally {
    // 已作答的题目条目数
    private Integer answered;
    //上述条目里答对的条数
    private Integer correct;
}
