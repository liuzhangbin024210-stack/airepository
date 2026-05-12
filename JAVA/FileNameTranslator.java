import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * 文件名翻译器：通过在线翻译将中文名称转换为符合阿里巴巴 Java 类名规范的英文类名。
 */
public class FileNameTranslator {
    private static final String TRANSLATE_API_URL = "https://api.mymemory.translated.net/get";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Set<String> CLASS_NAME_STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "at", "by", "for", "from", "in", "into", "of", "on", "or", "the", "to", "with"
    ));
    private static final Map<String, String> COMMON_ABBREVIATIONS = createCommonAbbreviations();

    /**
     * 构建常见英文单词缩写表，缩写长度控制在 3 到 5 个字母。
     *
     * @return 不可变缩写表。
     */
    private static Map<String, String> createCommonAbbreviations() {
        Map<String, String> abbreviations = new HashMap<>();
        abbreviations.put("account", "Acct");
        abbreviations.put("accounting", "Acct");
        abbreviations.put("application", "App");
        abbreviations.put("amount", "Amt");
        abbreviations.put("asset", "Ast");
        abbreviations.put("audit", "Aud");
        abbreviations.put("balance", "Bal");
        abbreviations.put("bank", "Bank");
        abbreviations.put("bill", "Bill");
        abbreviations.put("budget", "Bdgt");
        abbreviations.put("business", "Biz");
        abbreviations.put("capital", "Cap");
        abbreviations.put("cash", "Cash");
        abbreviations.put("collateral", "Coll");
        abbreviations.put("configuration", "Cfg");
        abbreviations.put("controller", "Ctrl");
        abbreviations.put("cost", "Cost");
        abbreviations.put("credit", "Crdt");
        abbreviations.put("creditor", "Cred");
        abbreviations.put("customer", "Cust");
        abbreviations.put("debit", "Deb");
        abbreviations.put("debt", "Deb");
        abbreviations.put("debtor", "Debtr");
        abbreviations.put("deposit", "Dep");
        abbreviations.put("document", "Doc");
        abbreviations.put("equity", "Eqty");
        abbreviations.put("expense", "Exp");
        abbreviations.put("fee", "Fee");
        abbreviations.put("finance", "Fin");
        abbreviations.put("financial", "Fin");
        abbreviations.put("fund", "Fund");
        abbreviations.put("information", "Info");
        abbreviations.put("income", "Inc");
        abbreviations.put("interest", "Int");
        abbreviations.put("inventory", "Inv");
        abbreviations.put("invoice", "Invc");
        abbreviations.put("journal", "Jrnl");
        abbreviations.put("ledger", "Ledg");
        abbreviations.put("liability", "Liab");
        abbreviations.put("loan", "Loan");
        abbreviations.put("loss", "Loss");
        abbreviations.put("management", "Mgmt");
        abbreviations.put("message", "Msg");
        abbreviations.put("number", "Num");
        abbreviations.put("order", "Ord");
        abbreviations.put("payable", "Payb");
        abbreviations.put("payment", "Pay");
        abbreviations.put("principal", "Prin");
        abbreviations.put("product", "Prod");
        abbreviations.put("profit", "Prof");
        abbreviations.put("receivable", "Recv");
        abbreviations.put("receipt", "Rcpt");
        abbreviations.put("reconcile", "Recon");
        abbreviations.put("reconciliation", "Recon");
        abbreviations.put("refund", "Rfnd");
        abbreviations.put("request", "Req");
        abbreviations.put("response", "Resp");
        abbreviations.put("revenue", "Rev");
        abbreviations.put("right", "Rght");
        abbreviations.put("settlement", "Setl");
        abbreviations.put("service", "Svc");
        abbreviations.put("system", "Sys");
        abbreviations.put("tax", "Tax");
        abbreviations.put("transfer", "Trans");
        abbreviations.put("transaction", "Txn");
        abbreviations.put("user", "Usr");
        abbreviations.put("validation", "Valid");
        abbreviations.put("voucher", "Vchr");
        abbreviations.put("withdrawal", "Wdrw");
        return Collections.unmodifiableMap(abbreviations);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入中文名称：");
        String chineseName = scanner.nextLine();
        try {
            String englishName = translateChineseToEnglish(chineseName);
            String className = convertEnglishToClassName(englishName);
            System.out.println("英文翻译：" + englishName);
            System.out.println("英文类名：" + className);
        } catch (IOException exception) {
            System.out.println("翻译服务请求失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("翻译请求已中断");
        } catch (IllegalArgumentException exception) {
            System.out.println("输入错误：" + exception.getMessage());
        }
    }

    /**
     * 将中文名称翻译为英文后转换为符合 Java 类命名规范的类名。
     *
     * @param chineseName 中文名称。
     * @return UpperCamelCase 风格的英文类名。
     * @throws IOException 当翻译服务请求失败时抛出。
     * @throws InterruptedException 当请求被中断时抛出。
     */
    public static String translateToClassName(String chineseName) throws IOException, InterruptedException {
        return convertEnglishToClassName(translateChineseToEnglish(chineseName));
    }

    /**
     * 调用在线翻译服务，将中文翻译成英文。
     *
     * @param chineseName 中文名称。
     * @return 翻译后的英文文本。
     * @throws IOException 当翻译服务请求失败或响应异常时抛出。
     * @throws InterruptedException 当请求被中断时抛出。
     */
    public static String translateChineseToEnglish(String chineseName) throws IOException, InterruptedException {
        if (chineseName == null || chineseName.trim().isEmpty()) {
            throw new IllegalArgumentException("中文名称不能为空");
        }

        String encodedText = URLEncoder.encode(chineseName.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(TRANSLATE_API_URL + "?q=" + encodedText + "&langpair=zh-CN%7Cen");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP 状态码 " + response.statusCode());
        }

        String translatedText = extractJsonStringValue(response.body(), "translatedText");
        if (translatedText == null || translatedText.trim().isEmpty()) {
            throw new IOException("翻译服务未返回有效英文结果");
        }
        return decodeCommonHtmlEntities(translatedText.trim());
    }

    /**
     * 将英文翻译结果转换为 UpperCamelCase 类名。
     *
     * @param englishName 英文翻译结果。
     * @return 符合 Java 类命名习惯的类名。
     */
    public static String convertEnglishToClassName(String englishName) {
        if (englishName == null || englishName.trim().isEmpty()) {
            throw new IllegalArgumentException("英文翻译结果不能为空");
        }

        StringBuilder className = new StringBuilder();
        String[] words = englishName
                .replaceAll("(?i)'s\\b", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .split("\\s+");
        for (String word : words) {
            String refinedWord = singularizeEnglishWord(word);
            String lowerWord = refinedWord.toLowerCase();
            if (!CLASS_NAME_STOP_WORDS.contains(lowerWord)) {
                className.append(toUpperCamelWord(abbreviateCommonWord(refinedWord)));
            }
        }

        if (className.length() == 0) {
            throw new IllegalArgumentException("翻译结果中没有可用于生成类名的英文字符");
        }
        if (!Character.isJavaIdentifierStart(className.charAt(0))) {
            className.insert(0, "Class");
        }
        return className.toString();
    }

    /**
     * 从简单 JSON 响应中提取指定字符串字段。
     *
     * @param json JSON 文本。
     * @param key 字段名。
     * @return 字段值；未找到时返回 null。
     */
    private static String extractJsonStringValue(String json, String key) {
        String target = "\"" + key + "\"";
        int keyIndex = json.indexOf(target);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + target.length());
        int quoteIndex = json.indexOf('"', colonIndex + 1);
        if (colonIndex < 0 || quoteIndex < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaping) {
                value.append(unescapeJsonChar(current));
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == '"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        return null;
    }

    /**
     * 处理 JSON 字符串里的常见转义字符。
     *
     * @param value 转义标记后的字符。
     * @return 实际字符。
     */
    private static char unescapeJsonChar(char value) {
        switch (value) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            default:
                return value;
        }
    }

    /**
     * 将翻译接口可能返回的 HTML 实体转换为普通文本。
     *
     * @param text 原始文本。
     * @return 解码后的文本。
     */
    private static String decodeCommonHtmlEntities(String text) {
        return text.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    /**
     * 将常见英文复数词压缩为单数，减少类名中的冗余表达。
     *
     * @param word 英文单词。
     * @return 精炼后的英文单词。
     */
    private static String singularizeEnglishWord(String word) {
        if (word == null || word.length() <= 3 || word.matches(".*\\d.*")) {
            return word;
        }

        String lowerWord = word.toLowerCase();
        if (lowerWord.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (lowerWord.endsWith("sses") || lowerWord.endsWith("uses")) {
            return word;
        }
        if (lowerWord.endsWith("ches")
                || lowerWord.endsWith("shes")
                || lowerWord.endsWith("xes")
                || lowerWord.endsWith("zes")) {
            return word.substring(0, word.length() - 2);
        }
        if (lowerWord.endsWith("s") && !lowerWord.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /**
     * 将常见英文单词替换为 3 到 5 个字母的缩写。
     *
     * @param word 英文单词。
     * @return 缩写后的单词；无缩写时返回原词。
     */
    private static String abbreviateCommonWord(String word) {
        if (word == null) {
            return null;
        }
        return COMMON_ABBREVIATIONS.getOrDefault(word.toLowerCase(), word);
    }

    /**
     * 将单词转换成 UpperCamelCase 的一个片段。
     *
     * @param word 原始英文单词。
     * @return 首字母大写、其余字符小写的类名片段。
     */
    private static String toUpperCamelWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        String normalized = word.replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.equals(normalized.toUpperCase())) {
            return normalized;
        }
        return normalized.substring(0, 1).toUpperCase()
                + normalized.substring(1).toLowerCase();
    }
}
