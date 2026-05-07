package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_analysis_result")
public class AiAnalysisResult extends BaseEntity {

    private Long logEntryId;
    private String summary;
    private String rootCause;
    private String suggestion;
    private String modelName;
    private Integer tokensUsed;
}
