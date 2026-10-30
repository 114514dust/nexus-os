package com.nexus.mail;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.config.AppProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 附件：收信时只记元信息，用户点下载时才回 IMAP 取字节并存到本地。
 *
 * 三个约束：
 * 1. **惰性下载**。订阅真实邮箱时，营销彩页、几十兆的宣讲手册远多于真正要看的
 *    面试须知/笔试题；收信阶段无脑落盘只会在几周内把磁盘填满。
 * 2. **文件名必须净化**。邮件里的文件名是外部输入，`../../` 这类构造能写到任意目录，
 *    所以落盘时只保留基名并过滤路径分隔符与控制字符。
 * 3. **原子落盘**。先写 `.tmp-*` 再改名：下载中断时不会留下一个「看起来下载好了」
 *    的半截 PDF，下次点开还能重来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailAttachmentService {

    private static final int FILENAME_MAX = 80;

    private final MailAttachmentRepository attachmentRepository;
    private final MailMessageRepository mailRepository;
    private final MailSettingsService settingsService;
    private final MailFetcher fetcher;
    private final AppProperties props;

    /** 收信时把附件元信息落库（字节不落盘） */
    @Transactional
    public void saveAll(Long mailId, List<FetchedMail.AttachmentMeta> metas) {
        if (metas == null || metas.isEmpty()) {
            return;
        }
        List<MailAttachment> rows = new ArrayList<>();
        for (FetchedMail.AttachmentMeta meta : metas) {
            MailAttachment a = new MailAttachment();
            a.setMailId(mailId);
            a.setPartIndex(meta.getPartIndex());
            a.setFilename(meta.getFilename() == null || meta.getFilename().isBlank()
                    ? "附件" + (meta.getPartIndex() + 1) : meta.getFilename());
            a.setContentType(meta.getContentType());
            a.setSizeBytes(Math.max(0, meta.getSize()));
            rows.add(a);
        }
        attachmentRepository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<MailAttachmentView> listByMail(Long mailId) {
        return attachmentRepository.findByMailIdOrderByPartIndexAsc(mailId).stream()
                .map(MailAttachmentService::toView)
                .collect(Collectors.toList());
    }

    /** 列表页一次把多封邮件的附件查出来，避免逐封查库 */
    @Transactional(readOnly = true)
    public Map<Long, List<MailAttachmentView>> mapByMails(List<Long> mailIds) {
        if (mailIds == null || mailIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<MailAttachmentView>> map = new HashMap<>();
        for (MailAttachment a : attachmentRepository.findByMailIdInOrderByMailIdAscPartIndexAsc(mailIds)) {
            map.computeIfAbsent(a.getMailId(), k -> new ArrayList<>()).add(toView(a));
        }
        return map;
    }

    /**
     * 取附件文件（必要时现从 IMAP 下载一次）。
     * 不加 @Transactional：中途要连外网，把网络 IO 包在数据库事务里会长时间占着连接。
     */
    public Payload open(Long attachmentId) {
        MailAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "附件不存在"));
        MailMessage mail = mailRepository.findById(att.getMailId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "邮件已被删除"));

        Path dir = baseDir();
        Path target = dir.resolve(targetName(mail.getId(), att));

        Path existing = existingPath(att);
        if (existing != null) {
            return new Payload(existing, att.getFilename(), contentTypeOf(att, existing));
        }

        MailSettingsDto cfg = settingsService.load();
        if (!cfg.isConfigured()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "邮箱还没配置，无法取回附件");
        }
        long maxBytes = Math.max(1, props.getMail().getAttachmentMaxMb()) * 1024L * 1024L;
        Path tmp = dir.resolve(".tmp-" + UUID.randomUUID() + ".part");
        long size;
        try (OutputStream out = Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            size = fetcher.fetchAttachment(cfg, mail.getImapUid(), att.getPartIndex(), out, maxBytes);
        } catch (Exception e) {
            deleteQuietly(tmp);
            log.warn("[mail] 下载附件失败 id={} 邮件={} 原因={}", att.getId(), mail.getId(), e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "下载附件失败：" + MailErrors.friendly(e));
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            deleteQuietly(tmp);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "保存附件失败：" + e.getMessage());
        }

        att.setStoredPath(target.toString());
        att.setDownloadedAt(LocalDateTime.now());
        // 收信时拿到的是「编码后大小」估算值（base64 会比原始字节大 1/3），
        // 真正下载过一次后以实际字节数为准，免得界面上的大小对不上文件
        if (size > 0) {
            att.setSizeBytes(size);
        }
        attachmentRepository.save(att);
        log.info("[mail] 附件已存本地：{}（{} KB）", target.getFileName(), Math.max(1, size / 1024));
        return new Payload(target, att.getFilename(), contentTypeOf(att, target));
    }

    /** 删邮件时连带删附件记录与已存的文件 */
    @Transactional
    public void deleteForMail(Long mailId) {
        for (MailAttachment a : attachmentRepository.deleteByMailId(mailId)) {
            Path p = existingPath(a);
            if (p != null) {
                deleteQuietly(p);
            }
        }
    }

    private Path existingPath(MailAttachment att) {
        if (att.getStoredPath() == null || att.getStoredPath().isBlank()) {
            return null;
        }
        try {
            Path p = Paths.get(att.getStoredPath());
            return Files.isReadable(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Path baseDir() {
        Path dir = Paths.get(props.getMail().getAttachmentDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "创建附件目录失败：" + e.getMessage());
        }
        return dir;
    }

    private static String targetName(Long mailId, MailAttachment att) {
        return "m" + mailId + "-" + att.getId() + "-" + safeName(att.getFilename());
    }

    /**
     * 文件名来自外部输入，必须当成不可信数据处理：
     * 去掉路径分隔符与控制字符、去掉开头的点（避免 .htaccess / 隐藏文件），
     * 过长时保留扩展名截断。
     */
    static String safeName(String raw) {
        String n = raw == null ? "" : raw.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t\\u0000-\\u001f]", "_");
        n = n.replaceAll("^\\.+", "").trim();
        if (n.isEmpty()) {
            n = "attachment";
        }
        if (n.length() > FILENAME_MAX) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 12 ? n.substring(dot) : "";
            n = n.substring(0, Math.max(1, FILENAME_MAX - ext.length())) + ext;
        }
        return n;
    }

    private static String contentTypeOf(MailAttachment att, Path file) {
        if (att.getContentType() != null && !att.getContentType().isBlank()) {
            return att.getContentType();
        }
        try {
            String probed = Files.probeContentType(file);
            return probed == null ? "application/octet-stream" : probed;
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (Exception e) {
            log.debug("[mail] 删除文件失败 {}：{}", p, e.getMessage());
        }
    }

    private static MailAttachmentView toView(MailAttachment a) {
        MailAttachmentView v = new MailAttachmentView();
        v.setId(a.getId());
        v.setFilename(a.getFilename());
        v.setContentType(a.getContentType());
        v.setSizeBytes(a.getSizeBytes());
        v.setDownloaded(a.getStoredPath() != null && !a.getStoredPath().isBlank());
        v.setPreviewable(isPreviewable(a.getContentType(), a.getFilename()));
        return v;
    }

    private static boolean isPreviewable(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/") || ct.startsWith("text/") || ct.contains("pdf")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".txt");
    }

    /** 交给 controller 落响应头用 */
    @Data
    public static class Payload {
        private final Path path;
        private final String filename;
        private final String contentType;
    }
}
