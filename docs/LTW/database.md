# Hashiji Cafe - Database Documentation

## 1. Overview

- **DBMS:** PostgreSQL
- **Schema strategy:** JPA `ddl-auto=update`
- **Primary key type:** UUID through `BaseEntity`
- **Audit fields:** `id`, `created_at`, `updated_at`
- **Manual SQL demo:** `src/main/resources/seed-data.sql` is optional and should not be imported together with Java `DataSeeder`.

---

## 2. Tables

### `users`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| username | VARCHAR(50) | Unique login credential |
| password | VARCHAR(100) | BCrypt-hashed |
| full_name | VARCHAR(100) | Nullable |
| role | VARCHAR | Enum: `ADMIN`, `STAFF`, `USER` |
| email | VARCHAR(100) | Nullable |
| phone | VARCHAR(15) | Nullable |
| hourly_rate | DOUBLE | Optional staff/payroll metadata |
| user_code | VARCHAR(20) | Unique display code |
| active | BOOLEAN | Account active flag |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `categories`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| category_code | VARCHAR(64) | Display code, e.g. `CAT-00001` |
| name | VARCHAR(50) | Unique category name |
| description | VARCHAR(255) | Nullable |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `products`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| category_id | UUID FK -> `categories.id` | Many products per category |
| product_code | VARCHAR(96) | Display code, e.g. `PRD-00001` |
| name | VARCHAR(100) | Unique product name |
| description | TEXT | Nullable |
| image | VARCHAR(500) | Local path, upload path, or HTTP(S) URL |
| base_price | NUMERIC(10,2) | Base display price |
| is_available | BOOLEAN | Hide/show from storefront |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `product_sizes`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| product_id | UUID FK -> `products.id` | Many sizes per product |
| size_name | VARCHAR | e.g. `Standard`, `S`, `M`, `L` |
| price | DOUBLE | Size price |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `toppings`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| name | VARCHAR(50) | Unique topping name |
| price | DOUBLE | Added to selected size price |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `orders`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| user_id | UUID FK -> `users.id` | Nullable for anonymous checkout |
| sub_total | NUMERIC(12,2) | Cart subtotal snapshot |
| grand_total | NUMERIC(12,2) | Final total snapshot |
| order_status | VARCHAR(50) | String mirror for view/PDF compatibility |
| customer_name | VARCHAR | Customer snapshot |
| phone | VARCHAR | Phone snapshot |
| address_text | TEXT | Delivery address |
| note | TEXT | Customer note |
| total_amount | DOUBLE | Total used by reports |
| status | VARCHAR | Enum: `PENDING`, `CONFIRMED`, `SHIPPING`, `COMPLETED`, `CANCELLED` |
| tracking_code | VARCHAR | Unique code, e.g. `ORD-000001` |
| order_type | VARCHAR | `Delivery`, `Takeaway`, etc. |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

### `order_items`

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | Auto-generated |
| order_id | UUID FK -> `orders.id` | Many items per order |
| product_id | UUID FK -> `products.id` | Nullable if product is deleted later |
| snapshot_product_name | VARCHAR(255) | Product name at purchase time |
| snapshot_unit_price | NUMERIC(12,2) | Unit price at purchase time |
| quantity | INT | Purchased quantity |
| snapshot_options | JSON | Size, toppings, sugar/ice, note summary |
| sub_total | NUMERIC(12,2) | `snapshot_unit_price * quantity` |
| created_at | TIMESTAMP | From `BaseEntity` |
| updated_at | TIMESTAMP | From `BaseEntity` |

---

## 3. Relationships

```text
users ──< orders

categories ──< products
products ──< product_sizes

orders ──< order_items >── products (nullable)
```

Recruitment/job tables are not present in the current codebase.
