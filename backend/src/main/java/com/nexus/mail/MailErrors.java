package com.nexus.mail;

import javax.mail.AuthenticationFailedException;
import java.util.Locale;

/**
 * 把 JavaMail 的英文异常翻成用户能照着修的话。
 *
 * 单独成类是因为收信、测试连接、下载附件三条链路都要用同一套翻译：
 * 「A3 NO LOGIN failed」这种原文摆在界面上，等于什么都没说；
 * 而三处各写一份翻译，迟早会说出三种不同的话。
 */
final class MailErrors {

    private MailErrors() {
    }

    static String friendly(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (t instanceof AuthenticationFailedException
                || lower.contains("login failed") || lower.contains("authentication") || lower.contains("auth")) {
            return "登录失败：QQ 邮箱要用 IMAP 授权码（不是登录密码），并确认已开启 IMAP 服务";
        }
        if (lower.contains("unknownhost") || lower.contains("nodename") || lower.contains("no such host")) {
            return "找不到邮件服务器：检查 IMAP 地址（QQ 邮箱为 imap.qq.com）";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "连接超时：网络不通或被防火墙拦了 993 端口";
        }
        if (lower.contains("connect")) {
            return "连不上邮件服务器：检查网络后重试（QQ 邮箱为 imap.qq.com:993）";
        }
        if (lower.contains("folder") || lower.contains("not found")) {
            return "找不到收件文件夹：" + msg;
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
