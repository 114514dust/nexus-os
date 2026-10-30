-- 记录内容本身的发布时间（RSS 的 pubDate / B 站的 pub_ts）。
--
-- received_at 是「抓进系统的时间」：一次性抓 10 篇历史文章，它们全是「刚刚」，
-- 时间线完全失真。分开存之后，列表仍按 received_at 排序（新抓的排前面），
-- 但显示用 published_at，用户一眼能看出这是三个月前的文章。
ALTER TABLE message ADD COLUMN published_at TIMESTAMP;
