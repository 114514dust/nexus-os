package com.nexus.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * IMAP 收信器。
 *
 * 三条原则：
 * 1. **只读登录**。folder 用 READ_ONLY 打开，并且设置 mail.imaps.peek=true ——
 *    后者让 JavaMail 用 BODY.PEEK 代替 BODY 取正文，不会把用户邮箱里的未读邮件
 *    标成已读。邮件客户端把别人的未读状态改掉，是最讨人厌的副作用。
 * 2. **只取最近 N 封**。首次同步时邮箱里可能有几万封历史邮件，全量拉下来既慢又没用；
 *    增量靠 UID 去重，所以取最近 N 封就够覆盖「上次抓取之后的新邮件」这个场景。
 * 3. **单封解析失败不中断整批**。一封格式诡异的营销邮件不该让今天所有邮件都收不进来。
 */
@Slf4j
@Component
public class MailFetcher {

    /** 正文入库上限。再长的正文对「判断重要 + 看个大概」没有增量信息 */
    private static final int MAX_BODY = 20000;

    /** 列表页预览长度 */
    private static final int MAX_SNIPPET = 200;

    /**
     * 抓取最近 limit 封里尚未入库的邮件。
     *
     * @param knownUids 已入库的 IMAP UID，调用方一次查出，避免逐封查库
     */
    public List<FetchedMail> fetch(MailSettingsDto cfg, Set<Long> knownUids) throws Exception {
        Store store = null;
        Folder folder = null;
        try {
            store = connect(cfg);
            folder = store.getFolder(cfg.getFolder());
            folder.open(Folder.READ_ONLY);
            if (!(folder instanceof UIDFolder)) {
                throw new IllegalStateException("该邮箱不支持 IMAP UID，无法做增量去重");
            }
            UIDFolder uidFolder = (UIDFolder) folder;
            int total = folder.getMessageCount();
            if (total <= 0) {
                return Collections.emptyList();
            }
            int limit = Math.max(1, cfg.getFetchLimit());
            int start = Math.max(1, total - limit + 1);
            Message[] messages = folder.getMessages(start, total);

            List<FetchedMail> result = new ArrayList<>();
            for (Message message : messages) {
                long uid;
                try {
                    uid = uidFolder.getUID(message);
                } catch (Exception e) {
                    log.warn("[mail] 读取 UID 失败，跳过一封：{}", e.getMessage());
                    continue;
                }
                if (uid <= 0 || knownUids.contains(uid)) {
                    continue;
                }
                try {
                    result.add(parse(message, uid));
                } catch (Exception e) {
                    log.warn("[mail] 解析邮件失败 uid={} 原因={}", uid, e.getMessage());
                }
            }
            return result;
        } finally {
            closeQuietly(folder, store);
        }
    }

    /** 测试连接：能打开文件夹并读到总数就算通 */
    public int probe(MailSettingsDto cfg) throws Exception {
        Store store = null;
        Folder folder = null;
        try {
            store = connect(cfg);
            folder = store.getFolder(cfg.getFolder());
            folder.open(Folder.READ_ONLY);
            return folder.getMessageCount();
        } finally {
            closeQuietly(folder, store);
        }
    }

    private Store connect(MailSettingsDto cfg) throws Exception {
        // 协议跟着 SSL 开关走：993 一般是 imaps，143 是 imap（本地/内网测试服务器也走这条）
        String protocol = cfg.isSsl() ? "imaps" : "imap";
        String prefix = "mail." + protocol + ".";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put(prefix + "host", cfg.getImapHost());
        props.put(prefix + "port", String.valueOf(cfg.getImapPort()));
        if (cfg.isSsl()) {
            props.put(prefix + "ssl.enable", "true");
        }
        // 超时给足：QQ 邮箱国际链路偶尔慢，但也不能无限等，否则定时任务会被一个连接挂死
        props.put(prefix + "connectiontimeout", "12000");
        props.put(prefix + "timeout", "25000");
        props.put(prefix + "writetimeout", "25000");
        // 关键：取正文用 BODY.PEEK，不改动服务端已读状态。
        // 两种协议各配一份 —— 属性名带协议前缀，配错前缀不生效（这条踩过一次）。
        props.put("mail.imap.peek", "true");
        props.put("mail.imaps.peek", "true");
        // 一次取完整邮件，避免 partial fetch 在大正文上反复往返
        props.put("mail.imap.partialfetch", "false");
        props.put("mail.imaps.partialfetch", "false");

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(cfg.getImapHost(), cfg.getImapPort(), cfg.getAddress(), cfg.getAuthCode());
        return store;
    }

    private FetchedMail parse(Message message, long uid) throws Exception {
        FetchedMail mail = new FetchedMail();
        mail.setImapUid(uid);
        mail.setMessageId(cut(firstHeader(message, "Message-ID"), 250));

        Address[] from = message.getFrom();
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress) {
            InternetAddress addr = (InternetAddress) from[0];
            mail.setSenderAddr(cut(addr.getAddress(), 255));
            mail.setSenderName(cut(decode(addr.getPersonal()), 128));
        } else if (from != null && from.length > 0) {
            mail.setSenderAddr(cut(from[0].toString(), 255));
        }

        mail.setSubject(cut(decode(message.getSubject()), 500));
        mail.setSentAt(toLocal(message.getSentDate()));
        LocalDateTime received = toLocal(message.getReceivedDate());
        mail.setReceivedAt(received != null ? received : mail.getSentAt());
        mail.setRead(message.isSet(Flags.Flag.SEEN));

        Body body = new Body();
        walk(message, body);
        String text = body.plain != null && !body.plain.trim().isEmpty()
                ? body.plain
                : htmlToText(body.html);
        text = normalize(text);

        mail.setBody(cut(text, MAX_BODY));
        mail.setSnippet(cut(text, MAX_SNIPPET));
        mail.setHasAttachment(body.attachment);
        // 没有发件人地址的邮件（极少见）也不能让入库失败，NOT NULL 列给个占位
        if (mail.getSenderAddr() == null || mail.getSenderAddr().isEmpty()) {
            mail.setSenderAddr("unknown");
        }
        if (mail.getSubject() == null || mail.getSubject().isEmpty()) {
            mail.setSubject("（无主题）");
        }
        if (mail.getReceivedAt() == null) {
            mail.setReceivedAt(LocalDateTime.now());
        }
        return mail;
    }

    private static String firstHeader(Message message, String name) {
        try {
            String[] values = message.getHeader(name);
            return values != null && values.length > 0 ? values[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 正文提取 ----

    private static final class Body {
        String plain;
        String html;
        boolean attachment;
    }

    /**
     * 递归遍历 MIME 结构。
     *
     * 优先 text/plain：很多营销邮件同时给 plain 和 html 两版，plain 版更干净；
     * 只有拿不到 plain 时才退化到 html 去标签。
     */
    private void walk(Part part, Body out) throws Exception {
        if (part == null) {
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart) {
                Multipart mp = (Multipart) content;
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    walk(bp, out);
                }
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part) {
                walk((Part) content, out);
            }
            return;
        }
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            out.attachment = true;
            // 附件正文不参与重要性判定，但如果它是纯文本附件也不该当作邮件正文
            if (!part.isMimeType("text/plain")) {
                return;
            }
        }
        if (part.isMimeType("text/plain") && out.plain == null) {
            out.plain = asText(part);
        } else if (part.isMimeType("text/html") && out.html == null) {
            out.html = asText(part);
        }
    }

    private static String asText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof InputStream) {
            try (InputStream in = (InputStream) content) {
                byte[] buf = in.readAllBytes();
                String charset = charsetOf(part.getContentType());
                return new String(buf, charset);
            }
        }
        return content == null ? "" : content.toString();
    }

    /** 从 Content-Type 里取 charset，缺省 UTF-8（国内邮件几乎都是 UTF-8/GBK 两种） */
    private static String charsetOf(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8.name();
        }
        String lower = contentType.toLowerCase();
        int idx = lower.indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8.name();
        }
        String cs = contentType.substring(idx + 8).split("[;\\s]")[0].replace("\"", "").trim();
        if (cs.isEmpty()) {
            return StandardCharsets.UTF_8.name();
        }
        // GB2312 的字符集名在部分 JDK 上会抛错，统一映射到 GBK
        if ("gb2312".equalsIgnoreCase(cs) || "gb_2312-80".equalsIgnoreCase(cs)) {
            return "GBK";
        }
        return cs;
    }

    /** HTML 转纯文本：够用就好，不引 Jsoup —— 与岗位解析那边保持同一取舍 */
    static String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|li|h[1-6]|table|blockquote)>", "\n");
        s = s.replaceAll("(?i)</t[dh]>", " ");
        s = s.replaceAll("(?s)<[^>]+>", " ");
        // &amp; 必须最后替换，否则 &amp;lt; 会被二次解码成 <
        s = s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
        return decodeNumericEntities(s);
    }

    private static String decodeNumericEntities(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&' && i + 3 < s.length() && s.charAt(i + 1) == '#') {
                int end = s.indexOf(';', i);
                if (end > i + 2 && end - i <= 9) {
                    String num = s.substring(i + 2, end);
                    try {
                        int code = num.startsWith("x") || num.startsWith("X")
                                ? Integer.parseInt(num.substring(1), 16)
                                : Integer.parseInt(num);
                        sb.appendCodePoint(code);
                        i = end + 1;
                        continue;
                    } catch (Exception ignored) {
                        // 解析不了就原样输出
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 统一换行、压掉大段空白、去掉零宽字符（很多邮件模板里塞了 &zwnj;） */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n')
                .replace("\u200b", "").replace("\u200c", "").replace("\u200d", "");
        StringBuilder sb = new StringBuilder(s.length());
        int blankRun = 0;
        for (String line : s.split("\n", -1)) {
            String t = line.replaceAll("[ \\t\\u00a0]+", " ").trim();
            if (t.isEmpty()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
            } else {
                blankRun = 0;
            }
            sb.append(t).append('\n');
        }
        return sb.toString().trim();
    }

    // ---- 工具 ----

    private static String decode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private static LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static void closeQuietly(Folder folder, Store store) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception ignored) {
                // 关闭失败不影响已经取到的数据
            }
        }
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (Exception ignored) {
                // 同上
            }
        }
    }
}
