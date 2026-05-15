# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
mvn compile                          # 编译
mvn exec:java -Dexec.mainClass=com.example.OrderDataGenerator   # 直接运行
mvn package                          # 打包 fat jar
java -jar target/order-data-generator-1.0.0.jar                 # 运行 jar
```

## Architecture

This is a single-module Maven project that generates realistic MySQL order transaction data for CDC / Flink testing.

**`OrderDataGenerator.java`** — the only source file. Top of the class is a configuration block (host, port, database name, table names, row counts). Everything below is data generation and batch insertion.

**Three-table hierarchy:**

```
orders ──< order_items ──< order_item_coupons
  │
  └── related_order_no → orders.order_no (inter-order links)
```

- `orders` — user, status (0=pending…5=refunded), amounts, timestamps
- `order_items` — product details per order line, quantity, pricing
- `order_item_coupons` — per-item coupons (满减/折扣/免邮)

**Data generation design:**
- Time distribution is hour-weighted (peaks at 11–13 and 18–21, trough overnight).
- 30% of users are repeat buyers, 10% are heavy users (≥4 orders).
- Order relationships: ~10% are split orders (same user, <5 min apart, linked via `related_order_no`), ~5% are supplementary orders.
- Payment time, update time are derived from order status and create time.
- All data is generated in memory first, then inserted via JDBC batch operations within a single transaction.

**Java 8 target** — the Maven compiler is set to Java 8. Instance method `.formatted()` is not available; use `String.format()`.

## DDL

`schema.sql` under `src/main/resources/` contains the CREATE TABLE statements. Run it against the target database before executing the generator.
