package com.nexus.subscription;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * RSS/Atom 抓取器（Rome 解析）。
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
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(subscription.getUrl())));
            for (SyndEntry entry : (List<SyndEntry>) feed.getEntries()) {
                String externalId = entry.getUri() != null ? entry.getUri() : entry.getLink();
                if (externalId == null || externalId.isEmpty()) {
                    continue;
                }
                String title = entry.getTitle() != null ? entry.getTitle() : "";
                String link = entry.getLink() != null ? entry.getLink() : "";
                String desc = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                items.add(new FetchedItem(externalId, title, link, desc));
            }
        } catch (Exception e) {
            log.error("RSS 抓取失败: url={}", subscription.getUrl(), e);
        }
        return items;
    }
}
