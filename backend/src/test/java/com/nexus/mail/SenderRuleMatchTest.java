package com.nexus.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 发件人规则的匹配口径。
 *
 * 这里的每一条都是「用户会怎么理解自己的规则」：
 * 他写了两条重叠的规则，本意一定是让更具体的那条生效。
 */
class SenderRuleMatchTest {

    @Test
    void 越具体的规则优先() {
        SenderRule broad = rule("edu.cn", SenderRule.IGNORE);
        SenderRule specific = rule("job@school.edu.cn", SenderRule.IMPORTANT);

        assertSame(specific, SenderRuleService.match(List.of(broad, specific),
                "job@school.edu.cn", "就业中心"), "完整地址比域名更具体");
        assertSame(broad, SenderRuleService.match(List.of(broad, specific),
                "notice@school.edu.cn", "教务处"), "只命中域名时退回宽规则");
    }

    @Test
    void 同样具体时忽略优先() {
        SenderRule important = rule("hr@company.com", SenderRule.IMPORTANT);
        SenderRule ignore = rule("HR@Company.com", SenderRule.IGNORE);

        assertSame(ignore, SenderRuleService.match(List.of(important, ignore),
                "hr@company.com", "HR"), "用户说过不想看，就别再提醒");
    }

    @Test
    void 可以按发件人显示名匹配() {
        SenderRule rule = rule("就业中心", SenderRule.IMPORTANT);
        assertSame(rule, SenderRuleService.match(List.of(rule),
                "noreply@somewhere.example.com", "学校就业中心"), "学校通知常以中文名发信");
        assertNull(SenderRuleService.match(List.of(rule),
                "noreply@somewhere.example.com", "某某商城"));
    }

    @Test
    void 大小写与空名单都不该出问题() {
        SenderRule rule = rule("School.Edu.CN", SenderRule.IMPORTANT);
        assertSame(rule, SenderRuleService.match(List.of(rule), "NOTICE@SCHOOL.EDU.CN", null));
        assertNull(SenderRuleService.match(List.of(), "a@b.com", "x"));
        assertNull(SenderRuleService.match(null, "a@b.com", "x"));
    }

    @Test
    void 文件名净化应挡掉路径穿越() {
        assertSafe("../../.pdf");
        assertSafe("/etc/passwd");
        assertSafe("..\\..\\windows\\system32\\cmd.exe");
        assertEquals("attachment", MailAttachmentService.safeName("..."), "只剩点号时给个兜底名");
        assertEquals("简历.pdf", MailAttachmentService.safeName("  简历.pdf  "), "中文文件名要原样保留");
    }

    /** 落盘名必须是「一个名字」，不能带任何目录层级，也不能是隐藏文件 */
    private void assertSafe(String raw) {
        String n = MailAttachmentService.safeName(raw);
        assertFalse(n.contains("/"), raw + " → " + n);
        assertFalse(n.contains("\\"), raw + " → " + n);
        assertFalse(n.startsWith("."), raw + " → " + n);
        assertFalse(n.trim().isEmpty(), raw + " → 空名字");
    }

    private SenderRule rule(String pattern, String action) {
        SenderRule r = new SenderRule();
        r.setId(1L);
        r.setPattern(pattern);
        r.setAction(action);
        return r;
    }
}
