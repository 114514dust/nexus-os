package com.nexus.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IMAP 收信链路测试。
 *
 * 用 GreenMail 起一个真的 IMAP 服务，而不是 mock JavaMail 的 API：
 * 这条链路真正容易出错的地方是「协议交互」和「MIME 解析」——
 * 中文主题的 encoded-word、HTML 正文、附件、已读标记，只有真连一次才验证得到。
 */
class MailFetcherTest {

    private static final String ADDRESS = "tester@qq.com";
    private static final String AUTH_CODE = "authcode123";

    private GreenMail greenMail;
    private GreenMailUser user;
    private MailSettingsDto cfg;

    @BeforeEach
    void setUp() {
        // 端口 0 让系统挑一个空闲端口，避免和本机其它服务冲突
        greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP));
        greenMail.start();
        // 直接把邮件投进该用户的收件箱，省去 SMTP 这一跳（本测试只关心 IMAP 读取）
        user = greenMail.setUser(ADDRESS, ADDRESS, AUTH_CODE);

        cfg = new MailSettingsDto();
        cfg.setAddress(ADDRESS);
        cfg.setAuthCode(AUTH_CODE);
        cfg.setImapHost("127.0.0.1");
        cfg.setImapPort(greenMail.getImap().getPort());
        cfg.setSsl(false);
        cfg.setFolder("INBOX");
        cfg.setFetchLimit(20);
    }

    @AfterEach
    void tearDown() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void 应该解析出中文主题_正文_附件且不改变已读状态() throws Exception {
        deliverInterviewMail();

        List<FetchedMail> mails = new MailFetcher().fetch(cfg, Collections.emptySet());

        assertEquals(1, mails.size());
        FetchedMail mail = mails.get(0);
        assertTrue(mail.getSubject().contains("面试通知"), "中文主题应正确解码：" + mail.getSubject());
        assertEquals("hr@bytedance.com", mail.getSenderAddr());
        assertTrue(mail.getSenderName().contains("字节跳动"), "发件人昵称应正确解码：" + mail.getSenderName());
        assertTrue(mail.getBody().contains("10月31日"), "正文应取到 plain 版本：" + mail.getBody());
        assertTrue(mail.isHasAttachment(), "带附件应被标记");
        assertFalse(mail.isRead(), "抓取前邮件是未读");
        assertTrue(mail.getImapUid() > 0);
    }

    @Test
    void 再次抓取时已入库的UID应该被跳过() throws Exception {
        deliverInterviewMail();
        MailFetcher fetcher = new MailFetcher();

        List<FetchedMail> first = fetcher.fetch(cfg, Collections.emptySet());
        assertEquals(1, first.size());

        java.util.Set<Long> known = new HashSet<>();
        known.add(first.get(0).getImapUid());
        List<FetchedMail> second = fetcher.fetch(cfg, known);
        assertTrue(second.isEmpty(), "同一封邮件不应重复入库");
    }

    @Test
    void 抓取不应该把邮箱里的邮件标成已读() throws Exception {
        deliverInterviewMail();
        MailFetcher fetcher = new MailFetcher();

        fetcher.fetch(cfg, Collections.emptySet());
        // 再抓一次，若上一轮把 \\Seen 打上了，这里 readFlag 会变 true
        List<FetchedMail> again = fetcher.fetch(cfg, Collections.emptySet());
        assertEquals(1, again.size());
        assertFalse(again.get(0).isRead(), "取正文必须用 BODY.PEEK，不能改动服务端已读状态");
    }

    @Test
    void 只有HTML正文时应该转成纯文本() throws Exception {
        MimeMessage msg = newMessage("【限时】校园招聘会员日 5 折起", "ad@zhaopin-push.com");
        msg.setContent(
                "<html><head><style>.a{color:red}</style></head><body>"
                        + "<p>亲爱的用户&nbsp;您好</p><p>限时<strong>5 折</strong>优惠，速来领取 &amp; 抢购！</p>"
                        + "</body></html>",
                "text/html; charset=UTF-8");
        user.deliver(msg);

        List<FetchedMail> mails = new MailFetcher().fetch(cfg, Collections.emptySet());
        assertEquals(1, mails.size());
        String body = mails.get(0).getBody();
        assertTrue(body.contains("限时"), body);
        assertTrue(body.contains("5 折"), body);
        assertTrue(body.contains("&"), "HTML 实体应被还原：" + body);
        assertFalse(body.contains("<p>"), "标签应被去掉：" + body);
        assertFalse(body.contains("color:red"), "style 块应被去掉：" + body);
    }

    // ---- helpers ----

    /** 一封典型的面试通知：alternative（plain + html）+ 一个附件 */
    private void deliverInterviewMail() throws Exception {
        MimeMessage msg = newMessage("面试通知：Java 后端二面", "hr@bytedance.com");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("您好：\n请于 10月31日 14:00 参加二面，会议链接稍后发送。\n回复「确认」视为接受。", "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>您好：请于 <b>10月31日 14:00</b> 参加二面。</p>", "text/html; charset=UTF-8");
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(plain);
        alternative.addBodyPart(html);

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(alternative);

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setFileName("面试须知.pdf");
        attachment.setContent("dummy", "application/pdf");

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(textPart);
        mixed.addBodyPart(attachment);
        msg.setContent(mixed);
        user.deliver(msg);
    }

    private MimeMessage newMessage(String subject, String from) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from, "字节跳动 HR", "UTF-8"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(ADDRESS));
        msg.setSubject(subject, "UTF-8");
        msg.setSentDate(new Date());
        return msg;
    }
}
