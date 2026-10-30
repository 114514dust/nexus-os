-- 消息流可读性改造：把「标题 + 链接」从 content 里拆出来，并记录已读状态。
--
-- 之前 message 只有一坨 content（标题换行接 URL），前端只能当纯文本渲染，
-- 链接点不开、也无法区分哪条是新来的。拆成独立列后：
--   item_title / item_link 可直接点击打开；
--   msg_read 记录是否读过（列名避开 H2 的 READ 关键字）。
ALTER TABLE message ADD COLUMN item_title VARCHAR(512);
ALTER TABLE message ADD COLUMN item_link VARCHAR(1024);
ALTER TABLE message ADD COLUMN msg_read BOOLEAN NOT NULL DEFAULT FALSE;

-- 消息流按「来源 + 时间」翻页，补一个联合索引
CREATE INDEX IF NOT EXISTS idx_message_source_time ON message (source, received_at);
