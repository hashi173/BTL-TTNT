-- =========================================================================
-- SQL SEED DATA CHO BÁO CÁO MÔN DBMS (HASHIJI-CAFE)
-- File này dùng để import thủ công cho DBMS demo / SQL-only demo mode.
-- Không dùng cùng lúc với Java DataSeeder ở profile dev để tránh seed chồng.
-- Tất cả các khóa chính đều sử dụng chuẩn UUID thay cho Long/BigInt.
-- =========================================================================

-- 1. Xóa dữ liệu cũ (để tránh lỗi trùng lặp khi chạy lại)
TRUNCATE TABLE order_items CASCADE;
TRUNCATE TABLE orders CASCADE;
TRUNCATE TABLE products CASCADE;
TRUNCATE TABLE categories CASCADE;
TRUNCATE TABLE users CASCADE;

INSERT INTO users (id, created_at, updated_at, full_name, username, password, email, phone, role, active) VALUES 
('11111111-1111-1111-1111-111111111111', NOW(), NOW(), 'Admin Hashiji', 'admin', '$2a$12$AT3UvnzVgdS3aoDp9/g4jupjKovp8E52BNupKtuPWNOxOCA5Wipa6', 'admin@hashiji.cafe', '0901234567', 'ADMIN', true),
('22222222-2222-2222-2222-222222222222', NOW(), NOW(), 'Nguyễn Văn Khách', 'user1', '$2a$12$AT3UvnzVgdS3aoDp9/g4jupjKovp8E52BNupKtuPWNOxOCA5Wipa6', 'khachhang@gmail.com', '0988776655', 'USER', true),
('33333333-3333-3333-3333-333333333333', NOW(), NOW(), 'Trần Lệ Xuân', 'user2', '$2a$12$AT3UvnzVgdS3aoDp9/g4jupjKovp8E52BNupKtuPWNOxOCA5Wipa6', 'lexuan@gmail.com', '0912345678', 'USER', true),
('44444444-4444-4444-4444-444444444444', NOW(), NOW(), 'Nhân viên Phục vụ', 'staff1', '$2a$12$AT3UvnzVgdS3aoDp9/g4jupjKovp8E52BNupKtuPWNOxOCA5Wipa6', 'staff@hashiji.cafe', '0902223333', 'STAFF', true);

-- 3. Khởi tạo dữ liệu Categories (Danh mục sản phẩm)
INSERT INTO categories (id, created_at, updated_at, name, description) VALUES 
('c0000000-0000-0000-0000-000000000001', NOW(), NOW(), 'Coffee', 'Các loại cà phê pha máy và pha phin'),
('c0000000-0000-0000-0000-000000000002', NOW(), NOW(), 'Tea', 'Các loại trà thanh mát'),
('c0000000-0000-0000-0000-000000000003', NOW(), NOW(), 'Smoothie', 'Sinh tố trái cây tươi');

-- 4. Khởi tạo dữ liệu Products (Sản phẩm)
INSERT INTO products (id, created_at, updated_at, name, category_id, base_price, is_available, image) VALUES 
('f0000000-0000-0000-0000-000000000001', NOW(), NOW(), 'Espresso', 'c0000000-0000-0000-0000-000000000001', 35000, true, 'Espresso.png'),
('f0000000-0000-0000-0000-000000000002', NOW(), NOW(), 'Latte', 'c0000000-0000-0000-0000-000000000001', 45000, true, 'CaffeLatte.png'),
('f0000000-0000-0000-0000-000000000003', NOW(), NOW(), 'Peach Tea', 'c0000000-0000-0000-0000-000000000002', 40000, true, 'PeachTea.png'),
('f0000000-0000-0000-0000-000000000004', NOW(), NOW(), 'Cappuccino', 'c0000000-0000-0000-0000-000000000001', 50000, true, 'CaffeLatte.png'),
('f0000000-0000-0000-0000-000000000005', NOW(), NOW(), 'Americano', 'c0000000-0000-0000-0000-000000000001', 30000, true, 'Espresso.png'),
('f0000000-0000-0000-0000-000000000006', NOW(), NOW(), 'Matcha Latte', 'c0000000-0000-0000-0000-000000000002', 55000, true, 'SakuraBlossomTea.png'),
('f0000000-0000-0000-0000-000000000007', NOW(), NOW(), 'Mango Smoothie', 'c0000000-0000-0000-0000-000000000003', 45000, true, 'StrawberrySmoothie.png');

-- 4a. Khởi tạo Kích thước sản phẩm (Product Sizes) - QUAN TRỌNG ĐỂ FIX LỖI sizeId
INSERT INTO product_sizes (id, created_at, updated_at, product_id, size_name, price) VALUES 
-- Espresso
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000001', 'S', 35000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000001', 'M', 40000),
-- Latte
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000002', 'M', 45000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000002', 'L', 55000),
-- Peach Tea
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000003', 'M', 40000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000003', 'L', 50000),
-- Cappuccino
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000004', 'M', 50000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000004', 'L', 60000),
-- Americano
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000005', 'S', 30000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000005', 'M', 35000),
-- Matcha Latte
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000006', 'M', 55000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000006', 'L', 65000),
-- Mango Smoothie
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000007', 'M', 45000),
(uuid_generate_v4(), NOW(), NOW(), 'f0000000-0000-0000-0000-000000000007', 'L', 55000);

-- 5. Khởi tạo dữ liệu Orders (Đơn hàng)
INSERT INTO orders (id, created_at, updated_at, user_id, order_status, grand_total, tracking_code, customer_name, phone, order_type) VALUES 
('d0000000-0000-0000-0000-000000000001', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', '22222222-2222-2222-2222-222222222222', 'COMPLETED', 115000, 'TRK-ORDER-001', 'Nguyễn Văn Khách', '0988776655', 'DINE_IN'),
('d0000000-0000-0000-0000-000000000002', NOW(), NOW(), '22222222-2222-2222-2222-222222222222', 'PENDING', 80000, 'TRK-ORDER-002', 'Nguyễn Văn Khách', '0988776655', 'TAKEAWAY'),
('d0000000-0000-0000-0000-000000000003', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', '33333333-3333-3333-3333-333333333333', 'COMPLETED', 105000, 'TRK-ORDER-003', 'Trần Lệ Xuân', '0912345678', 'DELIVERY'),
('d0000000-0000-0000-0000-000000000004', NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours', '33333333-3333-3333-3333-333333333333', 'COMPLETED', 165000, 'TRK-ORDER-004', 'Trần Lệ Xuân', '0912345678', 'TAKEAWAY'),
('d0000000-0000-0000-0000-000000000005', NOW() - INTERVAL '1 hour', NOW(), '22222222-2222-2222-2222-222222222222', 'CANCELLED', 50000, 'TRK-ORDER-005', 'Nguyễn Văn Khách', '0988776655', 'DINE_IN');

-- 6. Khởi tạo dữ liệu Order Items (Chi tiết đơn hàng)
-- Đơn 1: 1 Latte (45k) + 2 Espresso (35k * 2 = 70k) -> Tổng 115k
INSERT INTO order_items (id, created_at, updated_at, order_id, product_id, snapshot_product_name, snapshot_unit_price, quantity, sub_total) VALUES 
(uuid_generate_v4(), NOW(), NOW(), 'd0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000002', 'Latte', 45000, 1, 45000),
(uuid_generate_v4(), NOW(), NOW(), 'd0000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'Espresso', 35000, 2, 70000);

-- Đơn 2: 2 Peach Tea (40k * 2 = 80k) -> Tổng 80k
INSERT INTO order_items (id, created_at, updated_at, order_id, product_id, snapshot_product_name, snapshot_unit_price, quantity, sub_total) VALUES 
(uuid_generate_v4(), NOW(), NOW(), 'd0000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000003', 'Peach Tea', 40000, 2, 80000);

INSERT INTO order_items (id, created_at, updated_at, order_id, product_id, snapshot_product_name, snapshot_unit_price, quantity, sub_total) VALUES 
(uuid_generate_v4(), NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 'd0000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000007', 'Mango Smoothie', 45000, 1, 45000);

-- Đơn 4: 3 Matcha Latte (55k * 3 = 165k)
INSERT INTO order_items (id, created_at, updated_at, order_id, product_id, snapshot_product_name, snapshot_unit_price, quantity, sub_total) VALUES 
(uuid_generate_v4(), NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours', 'd0000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000006', 'Matcha Latte', 55000, 3, 165000);

-- Đơn 5: 1 Cappuccino (50k)
INSERT INTO order_items (id, created_at, updated_at, order_id, product_id, snapshot_product_name, snapshot_unit_price, quantity, sub_total) VALUES 
(uuid_generate_v4(), NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', 'd0000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000004', 'Cappuccino', 50000, 1, 50000);

-- =========================================================================
-- HẾT SCRIPT SEED DATA
-- Hệ thống đã có đủ dữ liệu cho demo SQL-only cơ bản.
-- =========================================================================
