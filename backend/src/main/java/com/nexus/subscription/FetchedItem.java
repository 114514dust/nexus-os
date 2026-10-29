package com.nexus.subscription;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 抓取器产出的标准化条目（与具体来源无关）。
 */
@Data
@AllArgsConstructor
public class FetchedItem {

    /** 条目唯一标识（RSS 的 guid/link、B 站的 dynamic_id），用于去重 */
    private String externalId;
    private String title;
    private String link;
    private String content;
}
