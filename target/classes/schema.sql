-- ============================================================
-- 建表 SQL —— 执行前请根据实际情况修改库名
-- ============================================================

CREATE TABLE IF NOT EXISTS `orders` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(32)     NOT NULL COMMENT '订单号',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `order_status`  TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 5-已退款',
    `total_amount`  DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '商品总金额',
    `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠总金额',
    `payment_amount` DECIMAL(12,2)  NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `related_order_no` VARCHAR(32)  NULL COMMENT '关联订单号(拆单/补单)',
    `pay_time`      DATETIME        NULL COMMENT '支付时间',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_related_order_no` (`related_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


CREATE TABLE IF NOT EXISTS `order_items` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    `order_no`      VARCHAR(32)     NOT NULL COMMENT '订单号',
    `product_id`    BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    `product_name`  VARCHAR(128)    NOT NULL COMMENT '商品名称',
    `quantity`      INT             NOT NULL DEFAULT 1 COMMENT '购买数量',
    `unit_price`    DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '单价',
    `total_price`   DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '小计',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品明细表';


CREATE TABLE IF NOT EXISTS `order_item_coupons` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    `order_no`      VARCHAR(32)     NOT NULL COMMENT '订单号',
    `item_id`       BIGINT UNSIGNED NOT NULL COMMENT '订单商品明细ID',
    `coupon_id`     BIGINT UNSIGNED NOT NULL COMMENT '优惠券ID',
    `coupon_type`   TINYINT         NOT NULL DEFAULT 1 COMMENT '类型: 1-满减券 2-折扣券 3-免邮券',
    `coupon_amount` DECIMAL(12,2)   NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_item_id` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细优惠券表';
