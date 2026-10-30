package com.nexus.subscription;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * RSS/Atom 抓取器（Rome 解析）。
 *
 * 抓不到就抛异常，不再返回空列表——否则「地址失效」和「真的没有新内容」
 * 在页面上长得一模一样，用户只会觉得这个源废了。
 */
@Slf4j
@Component
public class RssFetcher implements FeedFetcher {

    @Override
    public String type() {
        return "rss";
    }

    @Override
    public List<FetchedItem> fetch(Subscription subscription) {
        List<FetchedItem> items = new ArrayList<>();
        SyndFeed feed;
        try {
            SyndFeedInput input = new SyndFeedInput();
            feed = input.build(new XmlReader(new URL(subscription.getUrl())));
        } catch (Exception e) {
            log.warn("RSS 抓取失败: url={}, {}", subscription.getUrl(), e.getMessage());
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "订阅地址打不开或不是有效的 RSS（检查一下网址是否还能访问）");
        }
        for (SyndEntry entry : (List<SyndEntry>) feed.getEntries()) {
            String externalId = entry.getUri() != null ? entry.getUri() : entry.getLink();
            if (externalId == null || externalId.isEmpty()) {
                continue;
            }
            String title = entry.getTitle() != null ? HtmlText.decode(entry.getTitle()).trim() : "";
            String link = entry.getLink() != null ? entry.getLink() : "";
            String desc = entry.getDescription() != null
                    ? HtmlText.clean(entry.getDescription().getValue(), 300) : "";
            items.add(new FetchedItem(externalId, title, link, desc, toLocal(entry)));
        }
        return items;
    }

    /** 优先用发布时间，其次更新时间；都没有就留空（显示时回退到抓取时间） */
    private LocalDateTime toLocal(SyndEntry entry) {
        java.util.Date date = entry.getPublishedDate() != null
                ? entry.getPublishedDate() : entry.getUpdatedDate();
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
