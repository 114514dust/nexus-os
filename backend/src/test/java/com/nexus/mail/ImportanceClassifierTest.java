package com.nexus.mail;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则打分与日期提取的测试。
 *
 * 这些用例是「判定口径」的活文档：改动关键词表时，先看它们还对不对。
 */
class ImportanceClassifierTest {

    private final ImportanceClassifier classifier = new ImportanceClassifier();
    private final MailSettingsDto cfg = new MailSettingsDto();
    private final LocalDateTime receivedAt = LocalDateTime.of(2026, 10, 30, 20, 0);

    @Test
    void 面试邀约应判为高置信度() {
        FetchedMail mail = mail("【面试通知】字节跳动 Java 后端二面",
                "hr@bytedance.com",
                "同学你好，请于 2026年11月2日 14:00 参加视频面试，届时请提前调试设备。");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg);
        assertEquals(ImportanceClassifier.HIGH, v.getLevel(), v.getReason());
        assertTrue(v.getScore() >= 70);
        assertEquals(LocalDate.of(2026, 11, 2), v.getDueDate());
    }

    @Test
    void 营销邮件应判为低置信度() {
        FetchedMail mail = mail("【限时】校园招聘会员日 5 折起，优惠券速抢",
                "ad@shop.example.com",
                "恭喜您获得专属优惠券，限时 3 天，赶快下单，退订请回复 TD。");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg);
        assertEquals(ImportanceClassifier.LOW, v.getLevel(), v.getReason());
    }

    @Test
    void 验证码邮件应停在中档且不建待办() {
        FetchedMail mail = mail("您的登录验证码", "noreply@passport.example.com",
                "验证码 823194，5 分钟内有效。如非本人操作请忽略。");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg);
        assertEquals(ImportanceClassifier.MEDIUM, v.getLevel(), v.getReason());
        assertTrue(v.getScore() < ImportanceClassifier.HIGH_THRESHOLD);
    }

    @Test
    void 忽略名单中的发件人直接判零分() {
        cfg.setIgnoreSenders("zhaopin-push.com, noreply@shop.example.com");
        FetchedMail mail = mail("面试通知", "hr@zhaopin-push.com", "请参加面试");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg);
        assertEquals(0, v.getScore());
        assertEquals(ImportanceClassifier.LOW, v.getLevel());
    }

    @Test
    void 自定义关注关键词应该抬分() {
        FetchedMail mail = mail("关于启动「星火计划」的通知", "office@somewhere.example.com",
                "请相关同学查看附件。");
        int base = classifier.classify(mail, cfg).getScore();

        MailSettingsDto custom = new MailSettingsDto();
        custom.setExtraKeywords("星火计划");
        int boosted = classifier.classify(mail, custom).getScore();

        assertTrue(boosted > base, "命中关注词后分数应上升：" + base + " -> " + boosted);
    }

    @Test
    void 日期提取应覆盖常见写法() {
        assertEquals(LocalDate.of(2026, 11, 5), due("请于 2026-11-05 前提交材料"));
        assertEquals(LocalDate.of(2026, 11, 15), due("报名截止：11月15日"));
        assertEquals(LocalDate.of(2026, 11, 15), due("请在 11.15 之前完成"));
        assertEquals(LocalDate.of(2026, 10, 31), due("明天 14:00 参加笔试"));
        assertEquals(LocalDate.of(2026, 11, 1), due("后天进行资格复审"));
    }

    @Test
    void 明显不属于截止时间的日期应被忽略() {
        assertNull(due("请尽快回复确认"));
        // 版权年份之类离当前太远的日期不能当成截止日
        assertNull(due("© 2019-2021 Example Inc. All rights reserved."));
    }


    @Test
    void 发件人规则可以覆盖正文里的营销信号() {
        SenderRule rule = rule(1L, "school.edu.cn", SenderRule.IMPORTANT);
        FetchedMail mail = mail("【限时】毕业生服务周，专属福利领取",
                "notice@school.edu.cn", "退订请回复 TD。");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg, List.of(rule));

        assertEquals(ImportanceClassifier.HIGH, v.getLevel(), v.getReason());
        assertEquals(100, v.getScore(), "规则说重要，正文里的「退订」不该把它压回去");
        assertEquals(Long.valueOf(1L), v.getRuleId(), "命中的规则要回传给调用方计次");
        assertTrue(v.getReason().contains("一律重要"), v.getReason());
    }

    @Test
    void 可关注规则只保底到中档() {
        SenderRule rule = rule(2L, "教务", SenderRule.WATCH);
        FetchedMail mail = mail("选课系统维护", "office@somewhere.example.com", "本周末例行维护。");
        mail.setSenderName("教务处");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg, List.of(rule));

        assertEquals(ImportanceClassifier.MEDIUM, v.getLevel(), v.getReason());
        assertTrue(v.getScore() >= 60 && v.getScore() < ImportanceClassifier.HIGH_THRESHOLD,
                "保底 60，但不能顶到会建待办的档位：" + v.getScore());
    }

    @Test
    void 不提醒规则直接沉底且不送进可关注列表() {
        SenderRule rule = rule(3L, "noreply@shop.example.com", SenderRule.IGNORE);
        FetchedMail mail = mail("面试通知", "noreply@shop.example.com", "请参加面试");
        ImportanceClassifier.Verdict v = classifier.classify(mail, cfg, List.of(rule));

        assertEquals(0, v.getScore());
        assertEquals(ImportanceClassifier.LOW, v.getLevel());
        assertTrue(v.getReason().contains("不提醒"), v.getReason());
    }

    @Test
    void 发件人显示名也能命中规则() {
        SenderRule rule = rule(4L, "就业中心", SenderRule.IMPORTANT);
        FetchedMail mail = mail("关于领取材料的说明", "xyz123@random-mail.example.com", "请到 A 楼 302 领取。");
        mail.setSenderName("就业中心办公室");

        assertEquals(ImportanceClassifier.HIGH,
                classifier.classify(mail, cfg, List.of(rule)).getLevel());
        assertEquals(ImportanceClassifier.LOW, classifier.classify(mail, cfg).getLevel(),
                "没有规则时同一封邮件不该重要，否则用例证明不了规则起了作用");
    }

    private SenderRule rule(Long id, String pattern, String action) {
        SenderRule r = new SenderRule();
        r.setId(id);
        r.setPattern(pattern);
        r.setAction(action);
        return r;
    }

    private LocalDate due(String text) {
        return classifier.extractDueDate(text, receivedAt);
    }

    private FetchedMail mail(String subject, String sender, String body) {
        FetchedMail m = new FetchedMail();
        m.setSubject(subject);
        m.setSenderAddr(sender);
        m.setBody(body);
        m.setReceivedAt(receivedAt);
        m.setRead(false);
        return m;
    }
}
