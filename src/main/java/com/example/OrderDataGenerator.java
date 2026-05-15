package com.example;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MySQL 订单交易数据生成器。
 * 修改下方【配置区】的数据库连接参数和表名后直接运行即可。
 *
 * 生成的三表关系:
 *   orders (1) ──< order_items (N) ──< order_item_coupons (N)
 *
 * 订单间关联:
 *   - 同一用户多次下单（复购）
 *   - 拆单场景（同一用户短时间内多笔订单，通过 related_order_no 关联）
 *   - 补单场景（主订单完成后跟进的补发/补差价订单）
 */
public class OrderDataGenerator {

    // ==================== 配置区 —— 只改这里 ====================
    private static final String HOST     = "shufang103";
    private static final int    PORT     = 3306;
    private static final String DATABASE = "flink_cdc1";
    private static final String USER     = "root";
    private static final String PASSWORD = "123456";

    // 表名（如与实际不同请修改）
    private static final String TABLE_ORDERS           = "orders";
    private static final String TABLE_ORDER_ITEMS      = "order_items";
    private static final String TABLE_ORDER_ITEM_COUPONS = "order_item_coupons";

    // 数据量（按需调整）
    private static final int TOTAL_ORDERS      = 10_000;  // 订单总数
    private static final int USER_POOL_SIZE    = 2_000;   // 用户池大小
    private static final int PRODUCT_POOL_SIZE = 200;     // 商品池大小
    private static final int DAYS_SPAN         = 1;      // 时间跨度（天）
    // ==========================================================

    private static final String JDBC_URL = String.format(
        "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true",
        HOST, PORT, DATABASE
    );

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Random RNG = ThreadLocalRandom.current();

    // ---------- 数据模型 ----------

    static class OrderItem {
        long tmpItemId; // 临时 ID，仅内存使用
        long productId;
        String productName;
        int quantity;
        double unitPrice;
        double totalPrice;
        List<OrderItemCoupon> coupons = new ArrayList<>();
    }

    static class OrderItemCoupon {
        long couponId;
        int couponType;   // 1-满减券 2-折扣券 3-免邮券
        double couponAmount;
    }

    static class Order {
        String orderNo;
        long userId;
        int orderStatus;
        double totalAmount;
        double discountAmount;
        double paymentAmount;
        String relatedOrderNo; // 关联订单号，可为 null
        LocalDateTime createTime;
        LocalDateTime payTime;
        LocalDateTime updateTime;
        List<OrderItem> items = new ArrayList<>();
    }

    // ---------- 商品池 / 用户池 ----------

    private static List<Product> productPool;
    private static List<Long> userPool;

    static class Product {
        long id;
        String name;
        double price;
    }

    // ---------- 主流程 ----------

    public static void main(String[] args) {
        log("===== 订单数据生成器 =====");
        initPools();

        log("生成订单数据中...");
        List<Order> orders = generateOrders();
        log(String.format("共生成 %,d 笔订单", orders.size()));

        log("连接数据库...");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD)) {
            conn.setAutoCommit(false);
            insertAll(conn, orders);
            conn.commit();
            log("全部插入完成！");
        } catch (SQLException e) {
            log("数据库错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------- 初始化 ----------

    private static void initPools() {
        // 商品池
        productPool = new ArrayList<>(PRODUCT_POOL_SIZE);
        String[] categories = {
            "手机", "笔记本电脑", "蓝牙耳机", "机械键盘", "鼠标", "显示器", "平板电脑",
            "充电宝", "数据线", "手机壳", "钢化膜", "手表", "音箱", "路由器",
            "T恤", "牛仔裤", "运动鞋", "羽绒服", "双肩包", "太阳镜", "帽子",
            "洗面奶", "面膜", "防晒霜", "洗发水", "牙膏", "毛巾", "保温杯",
            "方便面", "牛奶", "坚果", "巧克力", "茶叶", "咖啡豆", "矿泉水",
            "螺丝刀套装", "电钻", "台灯", "收纳箱", "拖把", "垃圾桶", "衣架"
        };
        String[] brands = {
            "华为", "小米", "苹果", "索尼", "三星", "联想", "戴尔", "罗技",
            "安踏", "李宁", "耐克", "阿迪达斯", "欧莱雅", "资生堂", "良品铺子",
            "三只松鼠", "德芙", "立顿", "星巴克", "博世", "公牛", "得力", "宜家"
        };
        for (int i = 1; i <= PRODUCT_POOL_SIZE; i++) {
            Product p = new Product();
            p.id = 1000 + i;
            p.name = brands[RNG.nextInt(brands.length)]
                   + categories[RNG.nextInt(categories.length)]
                   + (RNG.nextBoolean() ? " " + pick(new String[]{"Pro","Max","Lite","Plus","经典款","升级版","旗舰版"}) : "");
            p.price = round(priceForCategory(p.name));
            productPool.add(p);
        }

        // 用户池
        userPool = new ArrayList<>(USER_POOL_SIZE);
        for (long i = 1; i <= USER_POOL_SIZE; i++) {
            userPool.add(1_000_000L + i);
        }
    }

    private static double priceForCategory(String name) {
        if (name.contains("手机") || name.contains("笔记本") || name.contains("平板") || name.contains("显示器"))
            return 999 + RNG.nextDouble() * 8000;
        if (name.contains("蓝牙") || name.contains("键盘") || name.contains("手表") || name.contains("音箱") || name.contains("电钻"))
            return 99 + RNG.nextDouble() * 1500;
        if (name.contains("羽绒服") || name.contains("运动鞋"))
            return 199 + RNG.nextDouble() * 1200;
        if (name.contains("T恤") || name.contains("牛仔裤") || name.contains("双肩包"))
            return 49 + RNG.nextDouble() * 400;
        if (name.contains("洗面奶") || name.contains("面膜") || name.contains("防晒霜") || name.contains("洗发水"))
            return 29 + RNG.nextDouble() * 300;
        return 5 + RNG.nextDouble() * 100;
    }

    // ---------- 数据生成 ----------

    private static List<Order> generateOrders() {
        List<Order> orders = new ArrayList<>(TOTAL_ORDERS);

        // split: 30% 的用户会有第二单，10% 的用户为高频用户(>=4单)
        int repeatUsers = (int)(USER_POOL_SIZE * 0.30);
        int heavyUsers  = (int)(USER_POOL_SIZE * 0.10);
        Set<Integer> repeatIdxSet = new HashSet<>();
        Set<Integer> heavyIdxSet  = new HashSet<>();
        while (repeatIdxSet.size() < repeatUsers) repeatIdxSet.add(RNG.nextInt(USER_POOL_SIZE));
        while (heavyIdxSet.size()  < heavyUsers)  heavyIdxSet.add(RNG.nextInt(USER_POOL_SIZE));
        heavyIdxSet.retainAll(repeatIdxSet); // heavy 是 repeat 的子集

        int[] userOrderCount = new int[USER_POOL_SIZE];
        // 给每个用户分配基础订单数
        for (int i = 0; i < TOTAL_ORDERS; i++) {
            int ui;
            if (i < USER_POOL_SIZE) {
                ui = i; // 前 N 个订单确保覆盖所有用户
            } else if (RNG.nextDouble() < 0.4) {
                ui = pickFromSet(heavyIdxSet);
            } else if (RNG.nextDouble() < 0.5) {
                ui = pickFromSet(repeatIdxSet);
            } else {
                ui = RNG.nextInt(USER_POOL_SIZE);
            }
            userOrderCount[ui]++;
        }

        // 记录每个用户最近一次订单时间，用于生成合理时间线
        LocalDateTime[] lastOrderTime = new LocalDateTime[USER_POOL_SIZE];
        String[] lastOrderNo = new String[USER_POOL_SIZE];

        int orderNoSeq = 1;
        Map<Integer, Integer> userOrderSeq = new HashMap<>(); // userId -> 该用户第几单

        for (int ui = 0; ui < USER_POOL_SIZE; ui++) {
            int count = userOrderCount[ui];
            if (count == 0) continue;

            long userId = userPool.get(ui);
            userOrderSeq.clear(); // per-user sequence counter

            for (int j = 0; j < count; j++) {
                Order order = new Order();
                order.userId = userId;

                // 订单号: ORD + 日期 + 序号
                LocalDateTime baseTime = randomTime();
                if (lastOrderTime[ui] != null) {
                    // 同一用户的订单时间应有先后
                    baseTime = lastOrderTime[ui].plusMinutes(10 + RNG.nextInt(DAYS_SPAN * 24 * 60 / count));
                    if (baseTime.isAfter(LocalDateTime.now())) {
                        baseTime = LocalDateTime.now().minusMinutes(RNG.nextInt(120));
                    }
                }
                lastOrderTime[ui] = baseTime;

                order.orderNo = "ORD" + baseTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                              + String.format("%04d", orderNoSeq++);
                order.createTime = baseTime;

                // 拆单: 10% 的概率，与上一单间隔 < 5 分钟
                String relatedOrderNo = null;
                if (j > 0 && RNG.nextDouble() < 0.10 && lastOrderNo[ui] != null) {
                    relatedOrderNo = lastOrderNo[ui];
                    order.createTime = baseTime.minusMinutes(1 + RNG.nextInt(4));
                }
                // 补单: 5% 概率，引用更早的订单
                else if (j > 1 && RNG.nextDouble() < 0.05 && lastOrderNo[ui] != null) {
                    relatedOrderNo = lastOrderNo[ui];
                }
                order.relatedOrderNo = relatedOrderNo;

                // 状态分配（权重: 已完成 55% / 已发货 15% / 已支付 10% / 待支付 8% / 已取消 7% / 已退款 5%）
                int statusRoll = RNG.nextInt(100);
                if (statusRoll < 55)       order.orderStatus = 3; // 已完成
                else if (statusRoll < 70)  order.orderStatus = 2; // 已发货
                else if (statusRoll < 80)  order.orderStatus = 1; // 已支付
                else if (statusRoll < 88)  order.orderStatus = 0; // 待支付
                else if (statusRoll < 95)  order.orderStatus = 4; // 已取消
                else                       order.orderStatus = 5; // 已退款

                // 支付时间
                if (order.orderStatus >= 1) {
                    int payDelayMin = 1 + RNG.nextInt(30);
                    order.payTime = order.createTime.plusMinutes(payDelayMin);
                }

                // updateTime
                if (order.orderStatus == 0) {
                    order.updateTime = order.createTime;
                } else if (order.orderStatus == 4 || order.orderStatus == 5) {
                    order.updateTime = (order.payTime != null ? order.payTime : order.createTime)
                                     .plusMinutes(10 + RNG.nextInt(24 * 60));
                } else {
                    order.updateTime = (order.payTime != null ? order.payTime : order.createTime)
                                     .plusHours(1 + RNG.nextInt(72));
                }
                if (order.updateTime.isAfter(LocalDateTime.now())) {
                    order.updateTime = LocalDateTime.now();
                }

                // 商品明细
                int itemCount = weightedItemCount();
                double totalAmount = 0;
                long tmpIdBase = (long) ui * 10000 + j * 100;

                for (int k = 0; k < itemCount; k++) {
                    OrderItem item = new OrderItem();
                    item.tmpItemId = tmpIdBase + k;

                    Product p = productPool.get(RNG.nextInt(productPool.size()));
                    item.productId = p.id;
                    item.productName = p.name;
                    item.quantity = weightedQuantity();
                    item.unitPrice = p.price;
                    item.totalPrice = round(item.quantity * item.unitPrice);
                    totalAmount += item.totalPrice;

                    // 优惠券: 30% 的商品使用优惠券
                    if (RNG.nextDouble() < 0.30) {
                        int couponCount = RNG.nextBoolean() ? 1 : 2;
                        for (int c = 0; c < couponCount; c++) {
                            OrderItemCoupon coupon = new OrderItemCoupon();
                            coupon.couponId = 200_000L + RNG.nextInt(500);
                            int ct = 1 + RNG.nextInt(3);
                            coupon.couponType = ct;
                            if (ct == 1)      coupon.couponAmount = round(5 + RNG.nextDouble() * 50);   // 满减
                            else if (ct == 2) coupon.couponAmount = round(item.totalPrice * (0.05 + RNG.nextDouble() * 0.2)); // 折扣
                            else              coupon.couponAmount = round(6 + RNG.nextDouble() * 12);   // 免邮
                            if (coupon.couponAmount > item.totalPrice) coupon.couponAmount = round(item.totalPrice * 0.5);
                            item.coupons.add(coupon);
                        }
                    }
                    order.items.add(item);
                }

                // 汇总金额
                order.totalAmount = round(totalAmount);
                double couponSum = 0;
                for (OrderItem item : order.items) {
                    for (OrderItemCoupon c : item.coupons) {
                        couponSum += c.couponAmount;
                    }
                }
                order.discountAmount = round(Math.min(couponSum, order.totalAmount * 0.5));
                order.paymentAmount = round(order.totalAmount - order.discountAmount);
                if (order.paymentAmount < 0.01) order.paymentAmount = 0.01;

                orders.add(order);
                lastOrderNo[ui] = order.orderNo;
            }
        }

        // 全局打乱（让不同用户的订单交叠，更真实）
        Collections.shuffle(orders, RNG);
        return orders;
    }

    // ---------- 数据库插入 ----------

    private static void insertAll(Connection conn, List<Order> orders) throws SQLException {
        // 准备三张表的 prepared statements
        String sqlOrder = "INSERT INTO " + TABLE_ORDERS
            + " (order_no, user_id, order_status, total_amount, discount_amount, payment_amount,"
            + "  related_order_no, pay_time, create_time, update_time)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)";

        String sqlItem = "INSERT INTO " + TABLE_ORDER_ITEMS
            + " (order_id, order_no, product_id, product_name, quantity, unit_price, total_price, create_time)"
            + " VALUES (?,?,?,?,?,?,?,?)";

        String sqlCoupon = "INSERT INTO " + TABLE_ORDER_ITEM_COUPONS
            + " (order_id, order_no, item_id, coupon_id, coupon_type, coupon_amount, create_time)"
            + " VALUES (?,?,?,?,?,?,?)";

        try (PreparedStatement psOrder  = conn.prepareStatement(sqlOrder, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psItem   = conn.prepareStatement(sqlItem, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psCoupon = conn.prepareStatement(sqlCoupon)) {

            int total = orders.size();
            int progressStep = Math.max(1, total / 20); // 每 5% 打一次进度

            for (int i = 0; i < total; i++) {
                Order order = orders.get(i);

                // 1. 插入订单
                psOrder.setString(1, order.orderNo);
                psOrder.setLong(2, order.userId);
                psOrder.setInt(3, order.orderStatus);
                psOrder.setDouble(4, order.totalAmount);
                psOrder.setDouble(5, order.discountAmount);
                psOrder.setDouble(6, order.paymentAmount);
                if (order.relatedOrderNo != null) psOrder.setString(7, order.relatedOrderNo);
                else                              psOrder.setNull(7, Types.VARCHAR);
                if (order.payTime != null) psOrder.setString(8, order.payTime.format(DT_FMT));
                else                       psOrder.setNull(8, Types.VARCHAR);
                psOrder.setString(9, order.createTime.format(DT_FMT));
                psOrder.setString(10, order.updateTime.format(DT_FMT));
                psOrder.executeUpdate();

                long orderId;
                try (ResultSet rs = psOrder.getGeneratedKeys()) {
                    rs.next();
                    orderId = rs.getLong(1);
                }

                // 2. 批量插入商品明细
                for (OrderItem item : order.items) {
                    psItem.setLong(1, orderId);
                    psItem.setString(2, order.orderNo);
                    psItem.setLong(3, item.productId);
                    psItem.setString(4, item.productName);
                    psItem.setInt(5, item.quantity);
                    psItem.setDouble(6, item.unitPrice);
                    psItem.setDouble(7, item.totalPrice);
                    psItem.setString(8, order.createTime.format(DT_FMT));
                    psItem.addBatch();
                }
                psItem.executeBatch();

                // 取回 item 生成的 ID
                long[] itemIds;
                try (ResultSet rs = psItem.getGeneratedKeys()) {
                    itemIds = new long[order.items.size()];
                    int idx = 0;
                    while (rs.next()) {
                        itemIds[idx++] = rs.getLong(1);
                    }
                }

                // 3. 批量插入优惠券
                int itemIdx = 0;
                for (OrderItem item : order.items) {
                    for (OrderItemCoupon coupon : item.coupons) {
                        psCoupon.setLong(1, orderId);
                        psCoupon.setString(2, order.orderNo);
                        psCoupon.setLong(3, itemIds[itemIdx]);
                        psCoupon.setLong(4, coupon.couponId);
                        psCoupon.setInt(5, coupon.couponType);
                        psCoupon.setDouble(6, coupon.couponAmount);
                        psCoupon.setString(7, order.createTime.format(DT_FMT));
                        psCoupon.addBatch();
                    }
                    itemIdx++;
                }
                if (!order.items.stream().allMatch(it -> it.coupons.isEmpty())) {
                    psCoupon.executeBatch();
                }

                // 进度
                if ((i + 1) % progressStep == 0) {
                    log(String.format("进度: %d/%d (%.0f%%), 当前订单: %s",
                        i + 1, total, (i + 1) * 100.0 / total, order.orderNo));
                }
            }
        }
    }

    // ---------- 时间生成 ----------

    /**
     * 生成随机时间，权重向营业高峰倾斜。
     * 高峰: 11-13 点（午间）、18-21 点（晚间）
     * 平峰: 9-11、14-18
     * 低谷: 0-7、22-24
     */
    private static LocalDateTime randomTime() {
        // 小时权重 [0..23]
        int[] hourWeights = {
            1, 1, 1, 1, 1, 1, 2,  // 0-6 深夜/凌晨
            4, 6, 8,                // 7-9 早间
            10, 12, 11,             // 10-12 午高峰
            8, 6, 7, 7, 8,          // 13-17 下午
            13, 14, 12, 8,          // 18-21 晚高峰
            4, 2                     // 22-23 深夜
        };
        int totalW = Arrays.stream(hourWeights).sum();
        int roll = RNG.nextInt(totalW);
        int hour = 0;
        int acc = 0;
        for (int h = 0; h < hourWeights.length; h++) {
            acc += hourWeights[h];
            if (roll < acc) { hour = h; break; }
        }

        int dayOffset = RNG.nextInt(DAYS_SPAN);
        LocalDate day = LocalDate.now().minusDays(dayOffset);
        int minute = RNG.nextInt(60);
        int second = RNG.nextInt(60);
        return LocalDateTime.of(day, LocalTime.of(hour, minute, second));
    }

    // ---------- 随机辅助 ----------

    private static int weightedItemCount() {
        int r = RNG.nextInt(100);
        if (r < 20) return 1;       // 20% 单件
        if (r < 55) return 2;       // 35% 两件
        if (r < 80) return 3;       // 25% 三件
        if (r < 92) return 4;       // 12% 四件
        return 5 + RNG.nextInt(4);   // 8%  5-8 件
    }

    private static int weightedQuantity() {
        int r = RNG.nextInt(100);
        if (r < 70) return 1;
        if (r < 88) return 2;
        if (r < 95) return 3;
        return 4 + RNG.nextInt(6);  // 4-9
    }

    @SafeVarargs
    private static <T> T pick(T... items) {
        return items[RNG.nextInt(items.length)];
    }

    private static int pickFromSet(Set<Integer> set) {
        int idx = RNG.nextInt(set.size());
        int i = 0;
        for (int v : set) {
            if (i++ == idx) return v;
        }
        return set.iterator().next();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static void log(String msg) {
        System.out.println(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + msg);
    }
}
