package com.nexus.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.nexus.todo.Todo;
import com.nexus.todo.TodoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收信 → 判定 → 建待办 的端到端测试。
 *
 * 用内存 H2 + 真 IMAP 服务器，把整条链路跑一遍：
 * 这条链路的价值不在任何单个类，而在「抓下来 → 打分 → 该建的待办建了、不该建的没建」。
 * 顺带验证 V13 迁移能在空库上从零建起来（Flyway 会跑全部 12 个迁移）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mailtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 定时任务在本测试里没有意义，且会和手动 sync 抢数据
        "nexus.mail.cron=-"
})
class MailSyncIntegrationTest {

    private static final String ADDRESS = "tester@qq.com";
    private static final String AUTH_CODE = "authcode123";
    private static final String MARK = "[itest]";

    private static GreenMail greenMail;
    private static GreenMailUser user;

    @Autowired
    private MailService mailService;

    @Autowired
    private MailMessageRepository mailRepository;

    @Autowired
    private TodoRepository todoRepository;

    @BeforeEach
    void setUp() throws Exception {
        if (greenMail == null) {
            greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP));
            greenMail.start();
            user = greenMail.setUser(ADDRESS, ADDRESS, AUTH_CODE);
        }
        // 每个用例从干净状态开始：库里的邮件与待办都清掉，邮箱也倒空，
        // 否则上一个用例投递的邮件会被算进这个用例的 fetched
        mailRepository.deleteAll();
        todoRepository.deleteAll();
        greenMail.purgeEmailFromAllMailboxes();
    }

    @AfterAll
    static void tearDown() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    void 面试邮件应自动建待办_广告邮件不应建待办() throws Exception {
        saveSettings(true, false);
        user.deliver(interviewMail());
        user.deliver(adMail());

        MailSyncResult result = mailService.sync();

        assertTrue(result.isOk(), result.getMessage());
        assertEquals(2, result.getFetched(), "两封新邮件都应被读到");
        assertEquals(1, result.getHigh(), "面试邮件应为高置信度");
        assertEquals(1, result.getTodos(), "只有高置信度邮件会自动建待办");

        List<Todo> todos = todoRepository.findAll();
        assertEquals(1, todos.size());
        Todo todo = todos.get(0);
        assertTrue(todo.getContent().contains("面试"), "待办内容应来自邮件主题：" + todo.getContent());
        assertEquals(LocalDate.of(2026, 11, 2), todo.getDueDate(), "应从正文里提取出面试日期");

        MailStats stats = mailService.stats();
        assertEquals(1, stats.getHigh());
        assertEquals(1, stats.getToTodo());
        assertTrue(stats.getLastSyncAt() != null && !stats.getLastSyncAt().isEmpty());
    }

    @Test
    void 重复收取不应重复建待办() throws Exception {
        saveSettings(true, false);
        user.deliver(interviewMail());

        mailService.sync();
        MailSyncResult second = mailService.sync();

        assertTrue(second.isOk());
        assertEquals(0, second.getFetched(), "同一封邮件不应被重复读到");
        assertEquals(1, todoRepository.findAll().size(), "待办也不应重复创建");
    }

    @Test
    void 关闭自动建待办时只入库不打扰用户() throws Exception {
        saveSettings(false, false);
        user.deliver(interviewMail());

        MailSyncResult result = mailService.sync();

        assertEquals(1, result.getFetched());
        assertEquals(1, result.getHigh(), "判定结果不受开关影响");
        assertEquals(0, result.getTodos());
        assertTrue(todoRepository.findAll().isEmpty());
    }

    @Test
    void 未配置授权码时应给出可读的失败原因() {
        MailSettingsDto dto = new MailSettingsDto();
        dto.setEnabled(true);
        dto.setAddress("");
        mailService.saveSettings(dto);

        MailSyncResult result = mailService.sync();

        assertTrue(!result.isOk());
        assertTrue(result.getMessage().contains("授权码"), result.getMessage());
    }

    private void saveSettings(boolean autoTodo, boolean llmJudge) {
        MailSettingsDto dto = new MailSettingsDto();
        dto.setEnabled(true);
        dto.setAddress(ADDRESS);
        dto.setAuthCode(AUTH_CODE);
        dto.setImapHost("127.0.0.1");
        dto.setImapPort(greenMail.getImap().getPort());
        dto.setSsl(false);
        dto.setFolder("INBOX");
        dto.setFetchLimit(20);
        dto.setAutoTodo(autoTodo);
        dto.setLlmJudge(llmJudge);
        mailService.saveSettings(dto);
    }

    private MimeMessage interviewMail() throws Exception {
        MimeMessage msg = baseMessage(MARK + " 面试通知：Java 后端二面", "hr@bytedance.com", "字节跳动 HR");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("同学你好：\n请于 2026年11月2日 14:00 参加二面，会议链接稍后发送。\n回复「确认」视为接受。", "UTF-8");
        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(plain);
        msg.setContent(multipart);
        return msg;
    }

    private MimeMessage adMail() throws Exception {
        MimeMessage msg = baseMessage(MARK + "【限时】会员日 5 折起，优惠券速抢", "ad@shop.example.com", "某某商城");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("恭喜您获得专属优惠券，限时 3 天，退订请回复 TD。", "UTF-8");
        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(plain);
        msg.setContent(multipart);
        return msg;
    }

    private MimeMessage baseMessage(String subject, String from, String personal) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from, personal, "UTF-8"));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(ADDRESS));
        msg.setSubject(subject, "UTF-8");
        msg.setSentDate(new Date());
        return msg;
    }
}
