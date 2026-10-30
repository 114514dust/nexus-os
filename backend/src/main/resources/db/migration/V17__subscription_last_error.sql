-- 让「订阅抓取失败」对用户可见：卡片上直接显示错误原因
ALTER TABLE subscription ADD COLUMN last_error VARCHAR(500);
