package jh.com.mapper;

import jh.com.entity.PracticeTally;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 页顶部统计的只读查询
 */
@Mapper
public interface UserStatsMapper {

    /**
     * 已作答条目数
     */
    PracticeTally practiceTally(@Param("userId") Long userId);

    /**
     * 有练习活动的日期
     */
    List<LocalDate> practiceDays(@Param("userId") Long userId);
}
