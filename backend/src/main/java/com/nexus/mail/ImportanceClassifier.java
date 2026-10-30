package com.nexus.mail;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件重要性打分（规则层）。
 *
 * 为什么规则先行、LLM 只兜灰区：
 * - 规则零成本、零延迟、结果可解释（用户能看懂「命中面试」为什么算重要）；
 * - 但规则不懂上下文：一封「回复确认」可能是面试确认，也可能是问卷；
 * - 所以把「明确信号」交给规则快速定性，把「像又不像」的灰区交给 LLM，
 *   LLM 不在线时整条链路照样能跑（降级为纯规则），这是可用的下限。
 *
 * 评分口径：0-100，>=70 高置信度（自动转待办），45-69 中置信度（只提示）。
 */
@Slf4j
@Component
public class ImportanceClassifier {

    public static final String HIGH = "high";
    public static final String MEDIUM = "medium";
    public static final String LOW = "low";

    /** 高置信度阈值：达到就自动建待办 */
    public static final int HIGH_THRESHOLD = 70;

    /** 中置信度阈值：达到就进「重要」列表 */
    public static final int MEDIUM_THRESHOLD = 45;

    /** 一眼就知道要行动的词 */
    private static final List<String> STRONG = Arrays.asList(
            "面试", "笔试", "测评", "录用", "offer", "录取", "签约", "三方协议", "政审",
            "体检", "资格审查", "资格复审", "复试", "终面", "意向书", "入职", "报到",
            "宣讲会", "简历通过", "进入下一轮", "面试邀请", "面试通知", "笔试通知", "录用通知");

    /** 钱和期限相关：漏掉代价大 */
    private static final List<String> MONEY = Arrays.asList(
            "账单", "还款", "扣款", "退款", "发票", "报销", "续费", "欠费", "缴费",
            "合同", "到期", "逾期", "支付");

    /** 需要我做点什么，但不一定紧急 */
    private static final List<String> ACTION = Arrays.asList(
            "截止", "报名", "确认", "回复", "提交", "补充材料", "审核", "待办",
            "deadline", "办理", "上传", "签署", "回执", "预约", "领取", "填写");

    /** 只是知会我一声 */
    private static final List<String> INFO = Arrays.asList(
            "通知", "提醒", "结果", "成绩", "公示", "进度", "更新", "安排", "日程", "会议");

    /** 营销噪音：命中就直接压下去，避免「限时通知」这类话术把分数抬起来 */
    private static final List<String> NOISE = Arrays.asList(
            "退订", "unsubscribe", "促销", "优惠券", "限时", "折扣", "抢购", "抽奖",
            "会员日", "广告", "推广", "低至", "包邮", "秒杀", "砍价", "红包", "返现",
            "特惠", "新品上市", "直播预告", "专属福利");

    /** 招聘平台与公司招聘域名：这类来信大多跟求职进度有关 */
    private static final List<String> RECRUIT_DOMAINS = Arrays.asList(
            "nowcoder.com", "zhipin.com", "lagou.com", "51job.com", "liepin.com",
            "shixiseng.com", "yingjiesheng.com", "linkedin.com", "maimai.cn");

    private static final Pattern P_ISO = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern P_CN = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]");
    private static final Pattern P_SHORT = Pattern.compile("(?<![\\d.:])(\\d{1,2})[./](\\d{1,2})(?![\\d.:])");

    @Data
    public static class Verdict {
        private int score;
        private String level = LOW;
        private String reason = "";
        /** 规则从正文里抠出来的日期，作为「待办到期日」的兜底 */
        private LocalDate dueDate;
        /** 命中的发件人规则（没有则 null），由调用方回写命中计数 */
        private Long ruleId;
    }

    public Verdict classify(FetchedMail mail, MailSettingsDto cfg) {
        return classify(mail, cfg, java.util.Collections.emptyList());
    }

    /**
     * @param rules 用户配置的发件人规则。一次收取里对所有邮件复用同一份列表，
     *              避免每封信查一次库
     */
    public Verdict classify(FetchedMail mail, MailSettingsDto cfg, List<SenderRule> rules) {
        Verdict v = new Verdict();
        String subject = lower(mail.getSubject());
        String body = lower(tail(mail.getBody(), 4000));
        String text = subject + "\n" + body;
        String sender = lower(mail.getSenderAddr());

        List<String> reasons = new ArrayList<>();

        // 0. 发件人规则：用户自己定的判断，优先级高于一切
        SenderRule rule = SenderRuleService.match(rules, mail.getSenderAddr(), mail.getSenderName());
        if (rule != null) {
            v.setRuleId(rule.getId());
            if (SenderRule.IGNORE.equals(rule.getAction())) {
                v.setScore(0);
                v.setLevel(LOW);
                v.setReason("发件人规则「" + rule.getPattern() + "」：不提醒");
                return v;
            }
        }

        // 1. 忽略名单优先：命中了就没什么好算的，用户已经明确表示不想看
        String ignored = matchIgnored(sender, cfg.getIgnoreSenders());
        if (ignored != null) {
            v.setScore(0);
            v.setLevel(LOW);
            v.setReason("发件人在忽略名单（" + ignored + "）");
            return v;
        }

        int score = 0;

        // 2. 用户自定义关注词：这是他自己的判断，权重给到最高档
        String extraHit = firstHit(text, splitKeywords(cfg.getExtraKeywords()));
        if (extraHit != null) {
            score += 30;
            reasons.add("命中关注词「" + extraHit + "」");
        }

        // 3. 关键词分级
        String strongHit = firstHit(text, STRONG);
        if (strongHit != null) {
            score += 45;
            reasons.add("含「" + strongHit + "」");
        }
        String moneyHit = firstHit(text, MONEY);
        if (moneyHit != null) {
            score += 25;
            reasons.add("涉及「" + moneyHit + "」");
        }
        String actionHit = firstHit(text, ACTION);
        if (actionHit != null) {
            score += 22;
            reasons.add("需要「" + actionHit + "」");
        }
        String infoHit = firstHit(text, INFO);
        if (infoHit != null) {
            score += 10;
        }

        // 4. 发件人身份：学校就业中心/教务、招聘平台、HR 系统
        if (sender.endsWith(".edu.cn") || sender.contains("@edu.") || sender.contains(".edu.cn")) {
            score += 18;
            reasons.add("学校邮箱");
        } else if (RECRUIT_DOMAINS.stream().anyMatch(sender::contains)
                || sender.contains("hr@") || sender.contains("recruit") || sender.contains("zhaopin")) {
            score += 12;
            reasons.add("招聘方来信");
        }

        // 5. 结构信号
        if (mail.isHasAttachment()) {
            score += 6;
        }
        if (!mail.isRead()) {
            score += 4;
        }
        LocalDate due = extractDueDate(text, mail.getReceivedAt());
        if (due != null) {
            score += 8;
            reasons.add("含明确日期");
        }

        // 6. 噪音压制。命中用户规则时跳过：他已经明确表态「这个发件人对我不一样」，
        //    就不该被正文里的一个「退订」把整封邮件压成广告
        if (rule == null) {
            String noiseHit = firstHit(text, NOISE);
            if (noiseHit != null) {
                score -= 40;
                reasons.clear();
                reasons.add("疑似营销内容（「" + noiseHit + "」）");
            }
        }

        // 7. 验证码类：需要立刻看，但转成待办没意义，固定停在中档
        boolean codeMail = text.contains("验证码") || text.contains("校验码") || text.contains("verification code");
        if (codeMail && score < HIGH_THRESHOLD) {
            score = Math.max(score, MEDIUM_THRESHOLD);
            reasons.add("验证码类，看完即弃");
        }

        // 8. 发件人规则收尾：important 直接顶到 100，watch 至少进「可关注」。
        //    放在最后是为了让规则能覆盖上面所有的启发式判断 —— 用户说了算。
        if (rule != null) {
            if (SenderRule.IMPORTANT.equals(rule.getAction())) {
                score = 100;
                reasons.add(0, "规则「" + rule.getPattern() + "」：一律重要");
            } else if (SenderRule.WATCH.equals(rule.getAction())) {
                score = Math.max(score, 60);
                reasons.add(0, "规则「" + rule.getPattern() + "」：至少可关注");
            }
        }

        score = Math.max(0, Math.min(100, score));
        v.setScore(score);
        v.setLevel(levelOf(score));
        v.setDueDate(due);
        v.setReason(reasons.isEmpty() ? "未命中重要信号" : joinReasons(reasons));
        return v;
    }

    public static String levelOf(int score) {
        if (score >= HIGH_THRESHOLD) {
            return HIGH;
        }
        return score >= MEDIUM_THRESHOLD ? MEDIUM : LOW;
    }

    /** 灰区：规则说不清、值得花一次 LLM 调用的区间 */
    public static boolean inGrayZone(int score) {
        return score >= MEDIUM_THRESHOLD && score < HIGH_THRESHOLD;
    }

    private static String joinReasons(List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size() && i < 3; i++) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(reasons.get(i));
        }
        if (reasons.size() > 3) {
            sb.append(" 等");
        }
        return sb.toString();
    }

    private static String firstHit(String text, List<String> keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return k;
            }
        }
        return null;
    }

    /** 用户填的关注词用逗号（中英文）或空格分隔 */
    private static List<String> splitKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String part : raw.split("[,，;；\\s]+")) {
            String k = part.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) {
                list.add(k);
            }
        }
        return list;
    }

    /** 逗号/空格分隔的忽略名单，支持完整地址或域名片段 */
    private static String matchIgnored(String sender, String ignoreList) {
        if (ignoreList == null || ignoreList.trim().isEmpty()) {
            return null;
        }
        for (String raw : ignoreList.split("[,，\\s]+")) {
            String item = raw.trim().toLowerCase(Locale.ROOT);
            if (!item.isEmpty() && sender.contains(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 从文本里找第一个「看起来像截止日期」的日期。
     *
     * 只认合理区间内的（今天往前 1 天到往后 1 年）：邮件正文里常出现
     * 版权年份、版本号、注册日期，那些不该被当成待办到期日。
     */
    public LocalDate extractDueDate(String text, LocalDateTime base) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        LocalDate today = base == null ? LocalDate.now() : base.toLocalDate();
        List<Hit> hits = new ArrayList<>();

        Matcher m = P_ISO.matcher(text);
        while (m.find()) {
            addIfValid(hits, m.start(), toDate(m.group(1), m.group(2), m.group(3)), today);
        }
        m = P_CN.matcher(text);
        while (m.find()) {
            addIfValid(hits, m.start(), toDate(null, m.group(1), m.group(2)), today);
        }
        m = P_SHORT.matcher(text);
        while (m.find()) {
            addIfValid(hits, m.start(), toDate(null, m.group(1), m.group(2)), today);
        }
        // 相对日期只在前文没有任何绝对日期时才用，否则「今天」这种签名档里的词会抢答
        if (hits.isEmpty()) {
            int idx = firstIndexOf(text, "后天", "明天", "今天");
            if (idx >= 0) {
                String word = text.startsWith("后天", idx) ? "后天" : text.startsWith("明天", idx) ? "明天" : "今天";
                LocalDate d = "后天".equals(word) ? today.plusDays(2) : "明天".equals(word) ? today.plusDays(1) : today;
                return d;
            }
            return null;
        }
        hits.sort(Comparator.comparingInt(Hit::getPos));
        return hits.get(0).getDate();
    }

    private static int firstIndexOf(String text, String... words) {
        int best = -1;
        for (String w : words) {
            int i = text.indexOf(w);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private static void addIfValid(List<Hit> hits, int pos, LocalDate date, LocalDate today) {
        if (date == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(today, date);
        if (days >= -1 && days <= 365) {
            hits.add(new Hit(pos, date));
        }
    }

    private static LocalDate toDate(String year, String month, String day) {
        try {
            int mo = Integer.parseInt(month);
            int da = Integer.parseInt(day);
            if (mo < 1 || mo > 12 || da < 1 || da > 31) {
                return null;
            }
            int yr = year == null ? LocalDate.now().getYear() : Integer.parseInt(year);
            LocalDate d = LocalDate.of(yr, mo, da);
            // 省略年份的「1月5日」如果已经过去，几乎一定是明年的
            if (year == null && d.isBefore(LocalDate.now().minusDays(30))) {
                d = d.plusYears(1);
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Hit {
        private final int pos;
        private final LocalDate date;

        private Hit(int pos, LocalDate date) {
            this.pos = pos;
            this.date = date;
        }

        private int getPos() {
            return pos;
        }

        private LocalDate getDate() {
            return date;
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String tail(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
