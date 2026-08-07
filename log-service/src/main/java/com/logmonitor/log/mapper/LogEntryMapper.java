package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.LogEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LogEntryMapper extends BaseMapper<LogEntry> {

    /**
     * 按 srcIp 分组统计时间窗口内满足条件的日志数（阈值告警聚合查询）。
     * <p>
     * 安全说明：column / op 由 RuleEngineService 白名单映射后传入，避免 SQL 注入；
     * value 走参数绑定（LIKE 通配符由调用方拼接）。
     */
    @Select("""
            <script>
            SELECT src_ip AS srcIp, COUNT(*) AS cnt
            FROM log_entry
            WHERE log_time &gt;= #{since}
            <if test="column != null and op != null and value != null">
                AND ${column} ${op} #{value}
            </if>
            GROUP BY src_ip
            HAVING COUNT(*) &gt;= #{threshold}
            </script>
            """)
    List<Map<String, Object>> countGroupBySrcIp(@Param("since") LocalDateTime since,
                                                @Param("column") String column,
                                                @Param("op") String op,
                                                @Param("value") String value,
                                                @Param("threshold") int threshold);
}
