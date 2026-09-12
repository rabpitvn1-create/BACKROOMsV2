package com.rabpit.backroom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * Deterministic pure-Java classifier for player-authored Freedom actions.
 *
 * Outputs only SEARCH, EXECUTE, or EXPLORE. It never reads GameState, performs network I/O,
 * loads a model asset, or introduces a new ActionRuntime kind. A semantic guard preserves
 * first-executable-step and traversal safety rules; remaining text uses a frozen int4
 * logistic-regression surface over 4096 Java-compatible hashed features.
 */
public final class FreedomActionClassifier {
  private static final int N = 4096;
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+|_");
  private static final String[] LABELS = {"EXECUTE","EXPLORE","SEARCH"};
  private static final double[] SCALE = {0.8802305459976196,0.629893958568573,0.6706966161727905};
  private static final double[] BIAS = {0.9822525077932718,-0.98244107129564,0.00018856350352294913};
  private static final String WEIGHTS =
      "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" +
      "X+Ee+y8CWMNI";
  private static final byte[] Q = inflate(decode64(WEIGHTS), 6144);

  private static final String[] ROUTE_VI = "hành lang|lối|đường|tuyến|nhánh|ngách|ngõ|phòng|gian|khu|kho|buồng|khoang|tầng|cầu thang|bậc|cửa|ngưỡng|khe|lỗ|vòm|đường hầm|ống|ống thông|sàn nối|cầu|giếng|lối bảo trì|khu vực|thang|dốc|cổng|rào|chắn|màn|vách".split("\\|",-1);
  private static final String[] ROUTE_ASCII = "hanh lang|loi|duong|tuyen|nhanh|ngach|ngo|phong|gian|khu|kho|buong|khoang|tang|cau thang|bac|cua|nguong|khe|lo|vom|duong ham|ong|ong thong|san noi|cau|gieng|loi bao tri|khu vuc|thang|doc|cong|rao|chan|man|vach".split("\\|",-1);
  private static final String[] ROUTE_EN = "corridor|hallway|hall|passage|route|path|detour|exit|doorway|door|room|chamber|area|section|floor|stairs|stairwell|ladder|opening|gap|archway|hatch|tunnel|shaft|duct|vent|pipe|bridge|catwalk|lane|way|bay|alcove|gate|barrier|threshold|screen|partition|branch|slope".split("\\|",-1);
  private static final String[] META = "tôi không |mình không |không cần |đừng |tôi chưa |mình chưa |tôi chỉ |chỉ nghĩ|tôi đang nghĩ|tôi nhớ|mình nhớ|tôi kể|tôi nói rằng|tôi muốn |tôi định |tôi dự định |toi khong |minh khong |khong can |dung |toi chua |minh chua |toi chi |chi nghi|toi dang nghi|toi nho|minh nho|toi ke|toi noi rang|toi muon |toi dinh |toi du dinh |i do not |i don't |do not |don't |i am not |i'm not |i have not |i haven't |i only |i am only |i'm only |i am thinking about |i'm thinking about |i remember |i plan |i intend |i am considering |i'm considering ".split("\\|",-1);
  private static final String[] EXEC_VI = "đặt |ném |rút |gắn |buộc |tháo |cắt |ấn |bấm |cầm |uống |ăn |mặc |đeo |thay |sửa |quấn |xé |mở |đóng |khóa |mở khóa |đẩy |kéo |nhặt |cất |bỏ |đưa |dùng |nạp |tắt |bật |tấn công|phòng thủ|né |núp |lùi |đứng |ngồi |chờ |nói |hỏi |bảo |ra hiệu|gõ |chạm |bẻ |giữ ".split("\\|",-1);
  private static final String[] EXEC_ASCII = "dat |nem |rut |gan |thao |cat |an |bam |cam |uong |mac |deo |thay |sua |quan |xe |mo |dong |khoa |mo khoa |day |keo |nhat |cat |dua |dung |nap |tat |bat |tan cong|phong thu|ne |nup |lui |ngoi |cho |noi |hoi |bao |ra hieu|cham |be |giu ".split("\\|",-1);
  private static final String[] EXEC_EN = "place |set |throw |toss |remove |take out |insert |attach |tie |untie |cut |press |touch |drink |eat |wear |replace |repair |wrap |tear |drop |open |close |lock |unlock |push |pull |pick up |grab |store |put |give |reload |turn off |turn on |attack |defend |dodge |hide |back away|back up|step back|wait |stay |remain |tell |ask |say |signal |knock |hold ".split("\\|",-1);
  private static final String[] SEARCH_VI = "rà |dõi |thử nghe|nhìn sát|tìm |tìm kiếm|kiểm tra|quan sát|xem |xem kỹ|soi |lắng nghe|nghe |nghe ngóng|dò |quét |đọc |ngửi |đếm |xác định|rọi |nhìn |nhìn qua|ngó |thăm dò|cảm nhận|sờ |áp tai|cúi |ghé sát".split("\\|",-1);
  private static final String[] SEARCH_ASCII = "doi mat theo |doi theo |thu nghe|nhin sat|tim |tim kiem|kiem tra|quan sat|xem |soi |lang nghe|nghe |do |quet |doc |ngui |dem |xac dinh|roi den|nhin |ngo |tham do|cam nhan|ap tai|cui |ghe sat".split("\\|",-1);
  private static final String[] SEARCH_EN = "run my fingers |run my hand |feel along |trace |search |inspect |examine |observe |listen |look |look through|peer |peek |scan |check |read |smell |sniff |count |locate |determine |shine |watch |feel |press my ear |put my ear ".split("\\|",-1);
  private static final String[] EXPLORE_VI = "bò xuống|bò lên|nhảy vào|nhảy qua|nhảy sang|lách vào|trườn vào|chui vào|rẽ |quẹo |vòng sang|đi men |bước vào|bước qua|bước sang|bước xuyên|đi vào|đi qua|đi sang|đi theo|đi tiếp|đi lên|đi xuống|đi dọc|tiến vào|tiến qua|băng qua|vượt qua|chui qua|bò qua|bò vào|luồn qua|lách qua|trượt qua|trườn qua|men theo|sang |qua |xuống |lên |leo |trèo |chuyển sang|đổi sang|tiếp tục ".split("\\|",-1);
  private static final String[] EXPLORE_ASCII = "nhay vao|nhay qua|nhay sang|lach vao|truon vao|chui vao|re |queo |vong sang|di men |buoc vao|buoc qua|buoc sang|buoc xuyen|di vao|di qua|di sang|di theo|di tiep|di len|di xuong|di doc|tien vao|tien qua|bang qua|vuot qua|chui qua|bo qua|bo vao|luon qua|lach qua|truot qua|truon qua|men theo|sang |qua |xuong |len |leo |treo |chuyen sang|doi sang|tiep tuc ".split("\\|",-1);
  private static final String[] EXPLORE_EN = "jump into|jump through|jump across|vault over|duck through|switch into |enter |step into|step through|walk into|walk through|turn into|cross |proceed |continue |move into|move through|go into|go through|go down|go up|head down|head into|switch to|pass through|descend |climb |clamber |crawl |squeeze |slip ".split("\\|",-1);
  private static final String[] CONDITIONAL_EXPLORE = "take it|use it|follow it|choose it|leave through it|pass through it|go through it|go into it|enter it|step through it|crawl through it|cross it|climb it|chọn nó|theo nó|dùng nó|sang đó|qua đó|đi theo nó|đi qua nó|đi vào đó|bước qua nó|chui qua nó|leo lên đó|leo xuống đó|rời qua đó|chon no|theo no|dung no|sang do|qua do|di theo no|di qua no|di vao do|buoc qua no|chui qua no|leo len do|leo xuong do|roi qua do".split("\\|",-1);
  private static final String[] LOCAL_STAY = " stay in this room| stay here| remain in this room| but stay| without leaving| nhưng chưa rời| nhưng không rời| nhưng ở lại| nhung chua roi| nhung khong roi| nhung o lai".split("\\|",-1);
  private static final String[] EN_STOPS = " from | beside | near | on | at | with | by | for | but | and |,".split("\\|",-1);
  private static final String[] VI_STOPS = " cạnh | gần | trên | dưới | với | nhưng | mà | rồi | và | canh | gan | tren | duoi | voi | nhung | ma | roi | va |,".split("\\|",-1);
  private static final String[] STOP_MARKERS = " rồi dừng| và dừng| nhưng không đi qua| nhưng không bước qua| nhưng không chui qua| nhưng không sang| nhưng không đi sang| nhưng không qua| nhưng ở nguyên| roi dung| va dung| nhung khong di qua| nhung khong buoc qua| nhung khong chui qua| nhung khong sang| nhung khong di sang| nhung khong qua| nhung o nguyen| and stop| but do not cross| but do not enter| but do not go through| but do not move past| but stay here".split("\\|",-1);
  private static final String[] APPROACH = "tiến tới|tiến đến|tiến sát|đi tới|đi đến|đi sát|bước tới|bước sát|tien toi|tien den|tien sat|di toi|di den|di sat|buoc toi|buoc sat|walk to|walk closer|move up to|move closer|step closer|approach ".split("\\|",-1);
  private static final String[] USE_ROUTE = "dùng lối |dùng đường |dùng tuyến |dùng cửa |dùng cầu thang |dung loi |dung duong |dung tuyen |dung cua |dung cau thang |use the route |use the path |use the detour |use the exit |use the passage |use the corridor |use the hallway |use the doorway |use the stairs |use the ladder ".split("\\|",-1);

  private FreedomActionClassifier() {}

  public static String classify(String action) {
    String raw = normalize(action);
    String head = headClause(raw);
    String guarded = guard(raw, head);
    return guarded != null ? guarded : linear(raw, head);
  }

  private static String guard(String raw, String head) {
    if (head.isEmpty()) return "EXECUTE";
    if (startsAny(raw, META)) return "EXECUTE";

    if ((raw.startsWith("nếu ") || raw.startsWith("neu ") || raw.startsWith("if "))
        && containsRoute(raw) && startsAny(head, CONDITIONAL_EXPLORE)) return "EXPLORE";

    if (startsAny(head, SEARCH_VI) || startsAny(head, SEARCH_ASCII) || startsAny(head, SEARCH_EN))
      return "SEARCH";

    if (startsAny(head, "bo xuong ", "bo len ") && containsRoute(head)) return "EXPLORE";
    if (startsAny(head, "drop through ", "drop into ", "drop down ") && containsRoute(head)) return "EXPLORE";
    if (head.startsWith("pass ") && containsRoute(head)) return "EXPLORE";

    if (startsAny(head, "follow ", "theo ") && containsAny(raw, LOCAL_STAY)) return "EXECUTE";

    if (startsAny(head, "take ", "follow ", "leave ", "choose ")) {
      String verb = firstPrefix(head, "take ", "follow ", "leave ", "choose ");
      return routeTarget(head, verb, EN_STOPS) ? "EXPLORE" : "EXECUTE";
    }
    if (startsAny(head, "chọn ", "chon ", "theo ")) {
      String verb = firstPrefix(head, "chọn ", "chon ", "theo ");
      return routeTarget(head, verb, VI_STOPS) ? "EXPLORE" : "EXECUTE";
    }
    if (startsAny(head, "rời ", "roi ")) {
      if (startsAny(head, "rời chỗ", "rời đây", "roi cho", "roi day")) return "EXPLORE";
      String verb = head.startsWith("rời ") ? "rời " : "roi ";
      return routeTarget(head, verb, VI_STOPS) ? "EXPLORE" : "EXECUTE";
    }
    if (startsAny(head, "vượt ", "vuot ") && containsRoute(head)) return "EXPLORE";

    if (startsAny(head, "đi quanh ", "di quanh ", "walk around ")
        && containsAny(head, "tìm","xem","quan sát","tim","quan sat","search","look","inspect","check","clue","track"))
      return "SEARCH";

    if (startsAny(head, EXEC_VI) || startsAny(head, EXEC_ASCII) || startsAny(head, EXEC_EN))
      return startsAny(head, USE_ROUTE) ? "EXPLORE" : "EXECUTE";

    if (containsAny(raw, STOP_MARKERS) && startsAny(head, APPROACH)) return "EXECUTE";

    if (startsAny(head, EXPLORE_VI) || startsAny(head, EXPLORE_ASCII) || startsAny(head, EXPLORE_EN))
      return "EXPLORE";

    if (startsAny(head, "đi ","tiến ","di ","tien ","move ") && containsRoute(head)
        && containsAny(head, " vào "," qua "," sang "," theo "," lên "," xuống "," dọc ",
          " tới khu "," tới khu vực "," tới phòng "," tới gian "," tới tầng "," tới đoạn ",
          " vao "," qua "," sang "," theo "," len "," xuong "," doc "," toi khu "," toi phong ",
          " toi gian "," toi tang "," toi doan "," into "," through "," along "," across ",
          " to the next "," beyond ")) return "EXPLORE";
    return null;
  }

  private static String linear(String raw, String head) {
    float[] x = vectorize(raw, head);
    int best = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int c = 0; c < 3; c++) {
      double dot = 0.0;
      int offset = c * N;
      for (int i = 0; i < N; i++) if (x[i] != 0f) dot += q(offset + i) * (double)x[i];
      double score = BIAS[c] + SCALE[c] * dot;
      if (score > bestScore) { bestScore = score; best = c; }
    }
    return LABELS[best];
  }

  private static float[] vectorize(String raw, String head) {
    float[] out = new float[N];
    List<String> tokens = tokens(head);
    for (String t : tokens) add(out, "w:" + t);
    for (int i = 0; i + 1 < tokens.size(); i++) add(out, "b:" + tokens.get(i) + "|" + tokens.get(i + 1));
    String compact = String.join(" ", tokens);
    for (int n = 3; n <= 5; n++) for (int i = 0; i + n <= compact.length(); i++)
      add(out, "c" + n + ":" + compact.substring(i, i + n));
    for (int i = 0; i < Math.min(6, tokens.size()); i++) add(out, "p" + i + ":" + tokens.get(i));
    List<String> rawTokens = tokens(raw);
    for (int i = 0; i < Math.min(8, rawTokens.size()); i++) add(out, "rp" + i + ":" + rawTokens.get(i));

    double ss = 0.0;
    for (float v : out) ss += (double)v * v;
    if (ss > 0.0) {
      float inv = (float)(1.0 / Math.sqrt(ss));
      for (int i = 0; i < out.length; i++) out[i] *= inv;
    }
    return out;
  }

  private static void add(float[] out, String feature) {
    out[(feature.hashCode() & 0x7fffffff) % N] += 1f;
  }

  private static List<String> tokens(String text) {
    ArrayList<String> result = new ArrayList<>();
    Matcher m = TOKEN.matcher(text);
    while (m.find()) result.add(m.group());
    return result;
  }

  private static String headClause(String text) {
    String s = normalize(text);
    for (int pass = 0; pass < 2; pass++) {
      String thenToken = null;
      if (s.startsWith("nếu ")) thenToken = " thì ";
      else if (s.startsWith("neu ")) thenToken = " thi ";
      else if (s.startsWith("if ")) thenToken = " then ";
      else break;
      int then = s.indexOf(thenToken), comma = s.indexOf(',');
      int split = firstPresent(then, comma);
      if (split < 0) break;
      s = s.substring(split + (split == then ? thenToken.length() : 1)).trim();
    }
    String[] separators = {" rồi "," sau đó "," rồi mới "," trước rồi "," roi "," sau do "," roi moi "," truoc roi ",";"," then ",", then "};
    int cut = -1;
    for (String sep : separators) {
      int at = s.indexOf(sep);
      if (at > 0 && (cut < 0 || at < cut)) cut = at;
    }
    if (cut > 0) s = s.substring(0, cut);
    return trimClause(s);
  }

  private static String trimClause(String s) {
    int a = 0, b = s.length();
    while (a < b && trimChar(s.charAt(a))) a++;
    while (b > a && trimChar(s.charAt(b - 1))) b--;
    return s.substring(a, b);
  }
  private static boolean trimChar(char c) { return c == ' ' || c == ',' || c == '.' || c == ';'; }
  private static int firstPresent(int a, int b) { return a < 0 ? b : b < 0 ? a : Math.min(a, b); }
  private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

  private static boolean containsRoute(String s) {
    return containsPhraseAny(s, ROUTE_VI) || containsPhraseAny(s, ROUTE_ASCII) || containsPhraseAny(s, ROUTE_EN);
  }
  private static boolean containsPhraseAny(String s, String[] phrases) {
    for (String p : phrases) if (hasPhrase(s, p)) return true;
    return false;
  }
  private static boolean hasPhrase(String s, String p) {
    for (int from = 0;;) {
      int at = s.indexOf(p, from);
      if (at < 0) return false;
      int end = at + p.length();
      boolean before = at == 0 || !word(s.charAt(at - 1));
      boolean after = end == s.length() || !word(s.charAt(end));
      if (before && after) return true;
      from = at + 1;
    }
  }
  private static boolean word(char c) { return c == '_' || Character.isLetterOrDigit(c); }

  private static boolean routeTarget(String head, String verb, String[] stops) {
    String rest = head.substring(verb.length()).trim();
    int cut = rest.length();
    for (String stop : stops) {
      int at = rest.indexOf(stop);
      if (at >= 0 && at < cut) cut = at;
    }
    return containsRoute(rest.substring(0, cut).trim());
  }
  private static String firstPrefix(String s, String... p) {
    for (String x : p) if (s.startsWith(x)) return x;
    return "";
  }
  private static boolean containsAny(String s, String... needles) {
    for (String n : needles) if (s.contains(n)) return true;
    return false;
  }
  private static boolean startsAny(String s, String... prefixes) {
    for (String p : prefixes) if (s.startsWith(p)) return true;
    return false;
  }
  private static int q(int index) {
    int packed = Q[index >>> 1] & 0xff;
    int nibble = (index & 1) == 0 ? packed >>> 4 : packed & 15;
    return nibble - 8;
  }

  private static byte[] inflate(byte[] compressed, int expected) {
    try {
      Inflater inflater = new Inflater();
      inflater.setInput(compressed);
      byte[] out = new byte[expected];
      int offset = 0;
      while (!inflater.finished() && offset < out.length) {
        int read = inflater.inflate(out, offset, out.length - offset);
        if (read == 0 && inflater.needsInput()) break;
        offset += read;
      }
      inflater.end();
      if (offset != expected) throw new IllegalStateException("Embedded classifier weight length mismatch");
      return out;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static byte[] decode64(String s) {
    if ((s.length() & 3) != 0) throw new IllegalStateException("Invalid embedded classifier weights");
    byte[] out = new byte[(s.length() / 4) * 3];
    int j = 0;
    for (int i = 0; i < s.length(); i += 4) {
      int a = b64(s.charAt(i)), b = b64(s.charAt(i + 1)), c = b64(s.charAt(i + 2)), d = b64(s.charAt(i + 3));
      out[j++] = (byte)((a << 2) | (b >>> 4));
      out[j++] = (byte)((b << 4) | (c >>> 2));
      out[j++] = (byte)((c << 6) | d);
    }
    return out;
  }
  private static int b64(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    throw new IllegalStateException("Invalid embedded classifier weight character");
  }
}
