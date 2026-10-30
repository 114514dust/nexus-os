package com.nexus.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 投递详情：记录本体 + 完整时间线。
 *
 * 列表接口不带时间线（几十条记录几百个事件没必要每次都传），
 * 只有点开某一条时才拉详情。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDetail {

    private JobApplication job;

    private List<JobEvent> events;
}
