import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Console;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Standalone Java 11 fixture tool. It never calls application or payment endpoints. */
public final class DemoRankingSeed {
    private static final String PREFIX = "DEMO_RANK_V1_";
    private static final String DEMO_SCHEMA = "daisomall_demo";
    private static final SecureRandom ENTROPY = new SecureRandom();

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5 || !Set.of("preview", "apply", "cleanup").contains(args[0])
                || !Set.of("100", "1500").contains(args[1])) {
            throw new IllegalArgumentException(
                    "Usage: DemoRankingSeed.java preview|apply|cleanup 100|1500 jdbc-url db-user [--approve-isolated-write|--approve-learning-write=<schema>]");
        }
        String action = args[0];
        int members = Integer.parseInt(args[1]);
        if (action.equals("cleanup") && members != 1500) throw new IllegalArgumentException("Use cleanup 1500.");
        String url = args[2];
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{1,5}/[A-Za-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Use a loopback MySQL URL with an explicit port and supported database.");
        }
        boolean preview = action.equals("preview");
        String schema = url.substring(url.lastIndexOf('/') + 1);
        boolean isolated = args.length == 5 && DEMO_SCHEMA.equals(schema) && "--approve-isolated-write".equals(args[4]);
        boolean learningWrite = args.length == 5 && action.equals("apply")
                && !DEMO_SCHEMA.equals(schema) && ("--approve-learning-write=" + schema).equals(args[4]);
        if (!preview && !isolated && !learningWrite) {
            throw new IllegalArgumentException("Writes require explicit approval for the exact learning schema; cleanup is isolated-only.");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", args[3]);
        credentials.setProperty("password", new String(secret("DEMO_DB_PASSWORD", "MySQL password: ")));
        credentials.setProperty("rewriteBatchedStatements", "true");
        try (Connection conn = DriverManager.getConnection(url, credentials)) {
            conn.setReadOnly(preview);
            conn.setAutoCommit(false);
            try {
                if (!preview) requireWritableTarget(conn, schema, isolated);
                if (action.equals("cleanup")) cleanup(conn);
                else {
                    LocalDateTime anchor = anchor(conn);
                    List<Product> products = loadProducts(conn);
                    List<Fixture> plan = plan(members, products, anchor);
                    printPlan(conn, plan, products.size(), anchor);
                    if (!preview) insert(conn, plan, anchor);
                }
                if (preview) conn.rollback();
                else {
                    conn.commit();
                    System.out.println("Committed on " + schema + ".");
                }
            } catch (Exception failure) {
                conn.rollback();
                throw failure;
            }
        }
    }

    private static char[] secret(String variable, String prompt) {
        String fromEnv = System.getenv(variable);
        if (fromEnv != null) return fromEnv.toCharArray();
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Set " + variable + " for a non-interactive session.");
        char[] input = console.readPassword(prompt);
        if (input == null) throw new IllegalArgumentException("Password input cancelled.");
        return input;
    }

    private static void requireWritableTarget(Connection conn, String schema, boolean isolated) throws SQLException {
        if (count(conn, "SELECT DATABASE() = ? AND @@foreign_key_checks = 1 "
                + "AND GET_LOCK(CONCAT('demo-rank-v1:',LEFT(SHA2(DATABASE(),256),40)),10) = 1", schema) != 1) {
            throw new SQLException("Unexpected schema, foreign keys disabled, or another fixture run is active.");
        }
        String tables = "('members','orders','order_items','payment_attempts','product_reviews',"
                + "'product_review_moderation','product_review_answers','product_review_photos')";
        if (count(conn, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                + "AND ENGINE='InnoDB' AND TABLE_NAME IN " + tables) != 8
                || count(conn, "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() "
                + "AND EVENT_OBJECT_TABLE IN " + tables) != 0) {
            throw new SQLException("Expected eight InnoDB tables without triggers.");
        }
        if (count(conn, "SELECT COUNT(*) FROM members WHERE (member_id REGEXP '^DEMO_RANK_V1_' "
                + "OR login_id REGEXP '^demorank[0-9]{4}$' OR email REGEXP '^demorank[0-9]{4}@example[.]invalid$') "
                + "AND NOT (" + OWN_MEMBER + ")") != 0
                || count(conn, "SELECT COUNT(*) FROM orders o JOIN members m USING(member_id) "
                        + "WHERE (o.order_no REGEXP '^DEMO_RANK_V1_' OR o.checkout_token REGEXP '^DEMO-CHECKOUT-') "
                        + "AND NOT (" + OWN_ORDER + ")") != 0
                || count(conn, "SELECT COUNT(*) FROM payment_attempts pa JOIN orders o USING(order_id) "
                        + "JOIN members m USING(member_id) WHERE pa.tid REGEXP '^DEMO-TID-' "
                        + "AND (NOT (" + OWN_ORDER + ") OR pa.tid <> CONCAT('DEMO-TID-',o.order_no))") != 0) {
            throw new SQLException("Existing rows collide with the fixture identifier namespace. Inspect before applying.");
        }
        if (isolated && (count(conn, "SELECT COUNT(*) FROM members WHERE role <> 'ADMIN' AND NOT (" + OWN_MEMBER + ")") != 0
                || count(conn, "SELECT COUNT(*) FROM orders o JOIN members m USING(member_id) WHERE NOT (" + OWN_ORDER + ")") != 0
                || count(conn, "SELECT COUNT(*) FROM payment_attempts pa JOIN orders o USING(order_id) "
                        + "WHERE pa.tid IS NULL OR pa.tid <> CONCAT('DEMO-TID-',o.order_no)") != 0
                || count(conn, "SELECT COUNT(*) FROM product_reviews rv JOIN members m USING(member_id) "
                        + "WHERE NOT (" + OWN_MEMBER + ")") != 0)) {
            throw new SQLException("The demo schema contains non-fixture accounts or transactions. Use a fresh catalog-only copy.");
        }
    }

    private static final String OWN_MEMBER = "member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$' "
            + "AND login_id=CONCAT('demorank',RIGHT(member_id,4)) "
            + "AND email=CONCAT(login_id,'@example.invalid') AND role='MEMBER'";
    private static final String OWN_ORDER = "(" + OWN_MEMBER + ") "
            + "AND o.order_no REGEXP CONCAT('^',member_id,'_O[1-3]$') "
            + "AND o.checkout_token=CONCAT('DEMO-CHECKOUT-',o.order_no)";

    private static int count(Connection conn, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, arguments);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static LocalDateTime anchor(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE((SELECT TIMESTAMPADD(DAY,400,created_at) FROM members "
                + "WHERE member_id=?), CURRENT_TIMESTAMP)";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, memberId(1));
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getObject(1, LocalDateTime.class);
            }
        }
    }

    private static List<Product> loadProducts(Connection conn) throws SQLException {
        String sql = "WITH RECURSIVE visible AS (SELECT category_id,category_id AS root_id,name AS root_name "
                + "FROM categories WHERE parent_id IS NULL AND is_visible=1 UNION ALL "
                + "SELECT c.category_id,v.root_id,v.root_name FROM categories c JOIN visible v "
                + "ON c.parent_id=v.category_id WHERE c.is_visible=1) "
                + "SELECT p.product_id,p.name,p.image_url,p.price,v.root_id,v.root_name FROM products p "
                + "JOIN visible v USING(category_id) WHERE p.status IN ('AVAILABLE','SOLD_OUT') AND p.price>=0 "
                + "ORDER BY p.product_id";
        Map<String, Deque<Product>> groups = new TreeMap<>();
        Map<String, List<Product>> byRoot = new TreeMap<>();
        try (Statement query = conn.createStatement(); ResultSet rows = query.executeQuery(sql)) {
            while (rows.next()) {
                byRoot.computeIfAbsent(rows.getString(5), key -> new ArrayList<>())
                        .add(new Product(rows.getString(1), rows.getString(2), rows.getString(3), rows.getInt(4), rows.getString(6)));
            }
        }
        byRoot.forEach((root, items) -> {
            Collections.shuffle(items, new Random(917L + root.hashCode()));
            groups.put(root, new ArrayDeque<>(items));
        });
        List<Product> result = new ArrayList<>();
        while (!groups.isEmpty()) {
            groups.values().forEach(items -> result.add(items.removeFirst()));
            groups.values().removeIf(Deque::isEmpty);
        }
        if (result.size() < 100) throw new SQLException("Need at least 100 eligible products for the fixture.");
        return result;
    }

    private static List<Fixture> plan(int members, List<Product> products, LocalDateTime anchor) {
        List<Fixture> result = new ArrayList<>();
        int sample = Math.min(2200, products.size() - 7);
        for (int member = 1; member <= members; member++) {
            Random random = new Random(58_327L + member * 104729L);
            int phase = (member - 1) % 100;
            List<Line> items = new ArrayList<>();
            Set<Integer> selected = new HashSet<>();
            for (int slot = 0; slot < 10; slot++) {
                int index;
                int units = 1 + random.nextInt(3);
                int rating;
                if (slot == 0 && member <= 40) {
                    index = (member - 1) / 10;
                    rating = (member - 1) % 10 < index ? 4 : 5;
                } else if (slot == 1 && phase < 82) {
                    index = phase < 45 ? (phase < 39 ? 4 : 5) : phase < 65 ? (phase < 50 ? 4 : 5) : 6;
                    units = phase < 39 ? 12 : phase < 50 ? 1 : phase < 65 ? 15 : 35;
                    rating = sampledRating(random, index);
                } else {
                    do {
                        int pool = random.nextInt(100) < 42 ? Math.min(220, sample) : sample;
                        index = 7 + random.nextInt(pool);
                    } while (selected.contains(index));
                    rating = sampledRating(random, index);
                }
                selected.add(index);
                int order = slot < 4 ? 1 : slot < 7 ? 2 : 3;
                items.add(new Line(products.get(index), units, rating, order, slot));
            }
            long firstHours = phase < 45 ? 3 + random.nextInt(10)
                    : phase < 65 ? 26 + random.nextInt(13)
                    : phase < 82 ? 74 + random.nextInt(72) : 198 + random.nextInt(400);
            LocalDateTime[] dates = {anchor.minusHours(firstHours), anchor.minusDays(9 + random.nextInt(20)),
                    anchor.minusDays(34 + random.nextInt(330))};
            // The fixed first signup anchors a seed series across the two stages and reruns.
            LocalDateTime signup = anchor.minusDays(member == 1 ? 400 : 390 + random.nextInt(330));
            result.add(new Fixture(member, signup, dates, items));
        }
        return result;
    }

    private static int sampledRating(Random random, int productIndex) {
        int value = Math.floorMod(random.nextInt(100) + (productIndex % 13 - 6) * 2, 100);
        // Consistent product-level satisfaction variation, without changing the review text polarity.
        int bias = productIndex % 11 - 5;
        if (value < 3 + Math.max(-2, bias)) return 1;
        if (value < 8 + bias) return 2;
        if (value < 21 + bias * 2) return 3;
        return value < 48 + bias * 3 ? 4 : 5;
    }

    private static String reviewText(Fixture fixture, Line item) {
        String[] openings = {"고를 때", "처음 봤을 때", "구매하기 전", "장바구니에 넣을 때",
                "비슷한 상품과 고민할 때", "용도를 생각해 볼 때", "이번에 고르면서", "상세 정보를 읽을 때"};
        String[] reasons = {"가격을 기준으로 골랐어요.", "필요한 용도에 맞는지 먼저 살폈어요.",
                "상품 안내를 참고해서 결정했어요.", "용도를 정해 두고 골랐어요.",
                "다른 구성과 비교해 보고 골랐어요.", "쓸 만한 상황을 떠올리고 선택했어요."};
        String[][] impressions = {
                {"제 기준에는 맞지 않았어요.", "다음에는 다른 쪽으로 알아볼 것 같아요.", "기대에 못 미쳐서 아쉬웠어요."},
                {"생각한 용도에 쓰기에는 조금 아쉬웠어요.", "가격을 감안해도 고민되는 선택이었어요.", "다시 고른다면 다른 상품도 살펴볼 것 같아요."},
                {"전체적으로 무난했어요.", "특별히 좋거나 나쁜 점 없이 평범했어요.", "제 기준에는 보통이었어요."},
                {"필요한 용도에 잘 맞는 편이었어요.", "가격을 생각하면 만족스러웠어요.", "전반적으로 괜찮은 선택이었어요."},
                {"필요했던 용도에 잘 맞아서 마음에 들어요.", "다음에도 비슷한 상황이면 다시 고를 것 같아요.", "제가 원하던 쪽이라 만족했어요."}
        };
        String[] closing = {" 비슷한 상품을 비교할 때 참고가 되면 좋겠어요.", " 어디에 쓸지 정해 두고 고르면 판단이 쉬울 것 같아요.",
                " 같은 분류라도 고르는 기준은 사람마다 다를 것 같아요.", " 저는 구매 전에 살펴본 기준으로 평가했어요."};
        Random text = new Random(fixture.number * 991L + item.slot * 809L);
        String product = text.nextBoolean() ? item.product.name : item.product.category;
        String body = "[시연용] " + product + " 제품은 " + openings[text.nextInt(openings.length)] + " "
                + reasons[text.nextInt(reasons.length)] + " " + impressions[item.rating - 1][text.nextInt(3)];
        int ending = text.nextInt(4);
        if (ending == 1) body += closing[text.nextInt(closing.length)];
        if (ending >= 2) body += " " + categoryNote(item.product.category, text);
        return body;
    }

    private static String categoryNote(String category, Random text) {
        List<String> notes;
        switch (category) {
            case "주방용품":
                notes = List.of("부엌에 둘 자리를 먼저 생각했어요.", "평소 식사 준비 순서에 맞춰 골랐어요.", "보관하는 방법도 생각하고 비교했어요.");
                break;
            case "문구/팬시":
                notes = List.of("책상에서 바로 쓸 물건을 찾고 있었어요.", "집과 사무실 중 어디에 둘지 먼저 생각했어요.", "매일 쓰는 문구와 함께 두고 살펴봤어요.");
                break;
            case "식품":
                notes = List.of("간식으로 먹을 생각이라 다른 선택지와 비교했어요.", "한 번에 먹을 양을 생각하면서 골랐어요.", "살 때 제품의 원재료 안내도 살펴봤어요.");
                break;
            case "수납/정리":
                notes = List.of("정리하려는 물건을 먼저 정하고 골랐어요.", "놓을 자리를 정해 두고 비교해 봤어요.", "집에 있는 수납용품과 함께 둘 생각이었어요.");
                break;
            case "뷰티/위생":
                notes = List.of("기존에 쓰던 물건과 같이 쓰는 상황을 생각했어요.", "매일 하던 관리 순서에 맞춰 골랐어요.", "욕실에서 쓸 때와 외출할 때를 나눠 비교했어요.");
                break;
            case "청소/욕실":
                notes = List.of("청소할 곳을 먼저 정하고 골랐어요.", "욕실 공간에 둘 물건이라 보관 위치부터 생각했어요.", "자주 손이 가는 위치에 둘 생각으로 비교했어요.");
                break;
            case "공구/디지털":
                notes = List.of("집에 있는 도구와 같이 쓸 상황을 생각했어요.", "필요한 작업을 정해 놓고 고른 제품이에요.", "사기 전에 제품 안내에서 사용 조건을 먼저 봤어요.");
                break;
            case "반려동물":
                notes = List.of("평소 반려동물이 쓰는 공간을 생각했어요.", "쓰던 반려용품과 함께 둘 계획이었어요.", "구매 전에 관리 방법과 사용 안내를 봤어요.");
                break;
            default:
                notes = List.of("집에서 실제로 둘 곳을 먼저 정해 봤어요.", "필요할 때 바로 찾을 수 있는지 생각했어요.", "집에 이미 있는 물건과 같이 쓸 상황을 생각했어요.");
        }
        return notes.get(text.nextInt(notes.size()));
    }

    private static void printPlan(Connection conn, List<Fixture> plan, int eligible, LocalDateTime anchor) throws SQLException {
        int[] ratings = new int[6];
        Set<String> covered = new HashSet<>();
        Map<String, int[]> totals = new TreeMap<>();
        Map<String, int[]> sales = new TreeMap<>();
        int[] orderWindows = new int[5];
        for (Fixture fixture : plan) for (LocalDateTime date : fixture.dates) {
            long hours = java.time.Duration.between(date.plusMinutes(1), anchor).toHours();
            orderWindows[hours < 24 ? 0 : hours < 48 ? 1 : hours < 168 ? 2 : hours < 720 ? 3 : 4]++;
        }
        for (Fixture fixture : plan) for (Line item : fixture.items) {
            covered.add(item.product.id);
            ratings[item.rating]++;
            long hours = java.time.Duration.between(fixture.dates[item.order-1].plusMinutes(1), anchor).toHours();
            int[] units = sales.computeIfAbsent(item.product.id, ignored -> new int[3]);
            if (hours < 24) { units[0] += item.units; units[1] += item.units; }
            else if (hours < 48) units[1] -= item.units;
            if (hours < 168) units[2] += item.units;
            if (fixture.number == 1 && item.slot >= 8) continue;
            int[] stats = totals.computeIfAbsent(item.product.id, key -> new int[2]);
            stats[0] += item.rating;
            stats[1]++;
        }
        Map<String, Integer> bands = new TreeMap<>();
        totals.values().forEach(stats -> {
            String band = stats[0] == 5 * stats[1] ? "5.0" : String.format(Locale.ROOT, "%.1f", (double) stats[0] / stats[1]);
            if (band.equals("5.0") && stats[0] != 5 * stats[1]) band = "rounded-only";
            bands.merge(band, 1, Integer::sum);
        });
        System.out.printf("DB catalog=%d categories=%d eligible=%d anchor=%s%n",
                count(conn,"SELECT COUNT(*) FROM products"), count(conn,"SELECT COUNT(*) FROM categories"), eligible, anchor);
        System.out.printf("Plan members=%d orders=payments=%d lines=reviews=%d reviewed_products=%d ratings_1_to_5=%s%n",
                plan.size(), plan.size()*3, plan.size()*10, covered.size(),
                Arrays.toString(Arrays.copyOfRange(ratings,1,6)));
        System.out.println("Public rating buckets: " + bands);
        System.out.println("Approval windows 0-1/1-2/2-7/7-30/30+ days: " + Arrays.toString(orderWindows));
        for (int metric = 0; metric < 3; metric++) {
            int index = metric;
            sales.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue()[index]))
                    .ifPresent(top -> System.out.printf("%s leader=%s score=%d%n",
                            List.of("Daily","Rising","Weekly").get(index),top.getKey(),top.getValue()[index]));
        }
    }

    private static void insert(Connection conn, List<Fixture> plan, LocalDateTime anchor) throws Exception {
        if (plan.size() == 1500 && count(conn, "SELECT COUNT(*) FROM members WHERE " + OWN_MEMBER
                + " AND RIGHT(member_id,4) BETWEEN '0001' AND '0100'") != 100) {
            throw new SQLException("Apply and verify the 100-member stage first.");
        }
        List<Fixture> pending = new ArrayList<>();
        for (Fixture fixture : plan) {
            String id = memberId(fixture.number);
            if (count(conn, "SELECT COUNT(*) FROM members WHERE member_id=? AND " + OWN_MEMBER, id) == 0) {
                pending.add(fixture);
                continue;
            }
            String ownOrders = " WHERE o.member_id=? AND o.order_no REGEXP CONCAT('^',o.member_id,'_O[1-3]$') "
                    + "AND o.checkout_token=CONCAT('DEMO-CHECKOUT-',o.order_no)";
            if (count(conn, "SELECT COUNT(*) FROM orders o" + ownOrders, id) != 3
                    || count(conn, "SELECT COUNT(*) FROM order_items i JOIN orders o USING(order_id)" + ownOrders, id) != 10
                    || count(conn, "SELECT COUNT(*) FROM payment_attempts p JOIN orders o USING(order_id)" + ownOrders
                            + " AND p.tid=CONCAT('DEMO-TID-',o.order_no)", id) != 3
                    || count(conn, "SELECT COUNT(DISTINCT i.order_item_id) FROM product_reviews rv JOIN order_items i USING(order_item_id) "
                            + "JOIN orders o USING(order_id)" + ownOrders + " AND rv.member_id=o.member_id "
                            + "AND rv.product_id=i.product_id", id) != 10) {
                throw new SQLException("Existing fixture is incomplete or was edited. Inspect or clean up before rerun: " + id);
            }
        }
        if (pending.isEmpty()) {
            System.out.println("No new rows. Existing fixtures were kept.");
            return;
        }
        char[] password = null;
        if (pending.stream().anyMatch(fixture -> fixture.number <= 5)) {
            password = secret("DEMO_RANK_PASSWORD", "Password for demorank0001 through demorank0005: ");
            if (password.length < 12) {
                Arrays.fill(password, '\0');
                throw new IllegalArgumentException("Use a demo-only password of at least 12 characters.");
            }
        }
        String memberSql = "INSERT INTO members(member_id,login_id,email,password_hash,name,role,status,created_at) "
                + "VALUES(?,?,?,?,?,'MEMBER','ACTIVE',?)";
        String orderSql = "INSERT INTO orders(order_no,member_id,checkout_token,status,recipient,phone,postcode,address1,"
                + "subtotal_amount,shipping_fee,total_amount,created_at,stock_deducted) "
                + "VALUES(?,?,?,'PAID','시연 전용','000-0000-0000','00000','가상 시연 주소',?,0,?,?,0)";
        String paymentSql = "INSERT INTO payment_attempts(order_id,method,status,amount,attempted_at,approved_at,tid) "
                + "SELECT order_id,'KAKAOPAY','APPROVED',total_amount,?,?,CONCAT('DEMO-TID-',order_no) FROM orders WHERE order_no=?";
        String itemSql = "INSERT INTO order_items(order_id,product_id,product_name,product_image,unit_price,quantity,item_total) "
                + "SELECT order_id,?,?,?,?,?,? FROM orders WHERE order_no=?";
        String reviewSql = "INSERT INTO product_reviews(member_id,product_id,order_item_id,rating,content,created_at,updated_at,deleted_at) "
                + "SELECT o.member_id,i.product_id,i.order_item_id,?,?,?,?,? FROM orders o JOIN order_items i USING(order_id) "
                + "WHERE o.order_no=? AND i.product_id=?";
        try (PreparedStatement newMember = conn.prepareStatement(memberSql);
                PreparedStatement newOrder = conn.prepareStatement(orderSql);
                PreparedStatement payment = conn.prepareStatement(paymentSql);
                PreparedStatement item = conn.prepareStatement(itemSql);
                PreparedStatement review = conn.prepareStatement(reviewSql)) {
            // Unknown, unreachable passwords on non-login fixtures have the same PBKDF2 storage format.
            String unreachableHash = passwordHash(UUID.randomUUID().toString().toCharArray());
            for (Fixture fixture : pending) {
                String id = memberId(fixture.number);
                String login = "demorank" + suffix(fixture.number);
                bind(newMember, id, login, login + "@example.invalid",
                        fixture.number <= 5 ? passwordHash(password) : unreachableHash,
                        "[시연] 체험회원 " + suffix(fixture.number), fixture.signup);
                newMember.addBatch();
            }
            execute(newMember);
            for (Fixture fixture : pending) {
                for (int order = 1; order <= 3; order++) {
                    int subtotal = 0;
                    for (Line line : fixture.items) if (line.order == order) subtotal += line.total();
                    String orderNo = orderNo(fixture, order);
                    bind(newOrder, orderNo, memberId(fixture.number), "DEMO-CHECKOUT-" + orderNo,
                            subtotal, subtotal, fixture.dates[order-1]);
                    newOrder.addBatch();
                    bind(payment, fixture.dates[order-1], fixture.dates[order-1].plusMinutes(1), orderNo);
                    payment.addBatch();
                }
                for (Line line : fixture.items) {
                    String orderNo = orderNo(fixture, line.order);
                    bind(item, line.product.id, line.product.name, line.product.image, line.product.price,
                            line.units, line.total(), orderNo);
                    item.addBatch();
                    LocalDateTime reviewed = fixture.dates[line.order-1].plusMinutes(13 + line.slot * 4);
                    bind(review, line.rating, reviewText(fixture,line), reviewed, reviewed,
                            fixture.number == 1 && line.slot == 9 ? reviewed.plusMinutes(1) : null, orderNo, line.product.id);
                    review.addBatch();
                }
            }
            execute(newOrder);
            execute(payment);
            execute(item);
            execute(review);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }

        if (pending.get(0).number == 1) {
            Line hidden = pending.get(0).items.get(8);
            update(conn, "INSERT INTO product_review_moderation(review_id,moderation_state,admin_member_id,changed_at) "
                    + "SELECT review_id,'HIDDEN','DEMO_RANK_V1_SYSTEM',TIMESTAMPADD(MINUTE,1,created_at) "
                    + "FROM product_reviews WHERE member_id=? AND product_id=?", memberId(1), hidden.product.id);
        }
        int expected = pending.size() * 10;
        int validReviews = count(conn, "SELECT COUNT(DISTINCT i.order_item_id) FROM product_reviews rv JOIN order_items i USING(order_item_id) "
                + "JOIN orders o USING(order_id) JOIN payment_attempts p USING(order_id) WHERE "
                + "rv.member_id=o.member_id AND rv.product_id=i.product_id AND o.status='PAID' AND o.canceled_at IS NULL "
                + "AND p.status='APPROVED' AND p.method='KAKAOPAY' AND p.tid=CONCAT('DEMO-TID-',o.order_no) "
                + "AND p.amount=o.total_amount AND o.created_at<=p.approved_at AND p.approved_at<rv.created_at "
                + "AND o.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$' AND o.member_id BETWEEN ? AND ? "
                + "AND o.checkout_token=CONCAT('DEMO-CHECKOUT-',o.order_no)", memberId(1), memberId(plan.size()));
        if (validReviews != plan.size() * 10) throw new SQLException("Unexpected validated review count: " + validReviews);
        System.out.printf("New rows: members=%d orders=payments=%d items=reviews=%d (anchor %s)%n",
                pending.size(),pending.size()*3,expected,anchor);
    }

    private static void cleanup(Connection conn) throws SQLException {
        String reviews = " JOIN product_reviews rv USING(review_id) JOIN order_items i USING(order_item_id) "
                + "JOIN orders o USING(order_id) JOIN members m ON m.member_id=o.member_id WHERE " + OWN_ORDER.replace("member_id", "m.member_id")
                + " AND rv.member_id=m.member_id AND rv.product_id=i.product_id";
        String reviewRows = " FROM product_reviews rv JOIN order_items i USING(order_item_id) JOIN orders o USING(order_id) "
                + "JOIN members m ON m.member_id=o.member_id WHERE " + OWN_ORDER.replace("member_id", "m.member_id")
                + " AND rv.member_id=m.member_id AND rv.product_id=i.product_id";
        String orderRows = " JOIN members m USING(member_id) WHERE " + OWN_ORDER;
        for (String child : List.of("product_review_photos", "product_review_answers", "product_review_moderation")) {
            update(conn, "DELETE child FROM " + child + " child" + reviews);
        }
        update(conn, "DELETE rv" + reviewRows);
        update(conn, "DELETE p FROM payment_attempts p JOIN orders o USING(order_id)" + orderRows
                + " AND p.tid=CONCAT('DEMO-TID-',o.order_no)");
        update(conn, "DELETE i FROM order_items i JOIN orders o USING(order_id)" + orderRows);
        update(conn, "DELETE o FROM orders o" + orderRows);
        update(conn, "DELETE FROM members WHERE " + OWN_MEMBER);
    }

    private static void update(Connection conn, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) statement.setObject(index + 1,arguments[index]);
    }

    private static void execute(PreparedStatement statement) throws SQLException {
        for (int result : statement.executeBatch()) {
            if (result != 1 && result != Statement.SUCCESS_NO_INFO) throw new SQLException("Fixture insert did not affect one row: " + result);
        }
    }

    private static String passwordHash(char[] password) throws Exception {
        byte[] salt = new byte[16];
        ENTROPY.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password,salt,120000,256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "120000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    private static String suffix(int member) { return String.format(Locale.ROOT, "%04d", member); }
    private static String memberId(int member) { return PREFIX + "M" + suffix(member); }
    private static String orderNo(Fixture fixture, int order) { return memberId(fixture.number) + "_O" + order; }

    private static final class Product {
        final String id, name, image, category;
        final int price;
        Product(String id, String name, String image, int price, String category) {
            this.id=id; this.name=name; this.image=image; this.price=price; this.category=category;
        }
    }

    private static final class Line {
        final Product product;
        final int units, rating, order, slot;
        Line(Product product, int units, int rating, int order, int slot) {
            this.product=product; this.units=units; this.rating=rating; this.order=order; this.slot=slot;
        }
        int total() { return Math.multiplyExact(product.price,units); }
    }

    private static final class Fixture {
        final int number;
        final LocalDateTime signup;
        final LocalDateTime[] dates;
        final List<Line> items;
        Fixture(int number, LocalDateTime signup, LocalDateTime[] dates, List<Line> items) {
            this.number=number; this.signup=signup; this.dates=dates; this.items=items;
        }
    }
}
