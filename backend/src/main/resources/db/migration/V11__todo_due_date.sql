-- 待办到期日（Phase 14）
--
-- 只加一列，但这一列把待办从「想起来才看的清单」变成了「会主动找上门的事」：
-- 有了日期才谈得上提前一天提醒、逾期标红、按紧迫度排序。
--
-- 用 DATE 而不是 TIMESTAMP，理由同 job_application.next_at：
-- 个人待办的粒度就是「哪天要做完」，精确到时分是伪需求，反而增加录入负担。
-- 允许为 NULL：绝大多数随手记的待办本来就没有日期。
ALTER TABLE todo ADD COLUMN due_date DATE;
