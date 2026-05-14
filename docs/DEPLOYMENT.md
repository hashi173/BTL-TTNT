# Hashiji Café — Deployment Guide

Hướng dẫn chi tiết từng bước để cài đặt và chạy ứng dụng Hashiji Café trên máy tính cá nhân.

---

## Mục lục

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Cài đặt môi trường](#2-cài-đặt-môi-trường)
3. [Tạo database PostgreSQL](#3-tạo-database-postgresql)
4. [Cấu hình Spring Boot](#4-cấu-hình-spring-boot)
5. [Chạy ứng dụng — Môi trường phát triển (có dữ liệu mẫu)](#5-chạy-ứng-dụng--môi-trường-phát-triển-có-dữ-liệu-mẫu)
6. [Kiểm tra ứng dụng](#6-kiểm-tra-ứng-dụng)
7. [Xử lý lỗi thường gặp](#7-xử-lý-lỗi-thường-gặp)

---

## 1. Yêu cầu hệ thống

| Phần mềm       | Phiên bản tối thiểu | Ghi chú                              |
|-----------------|---------------------|---------------------------------------|
| Java JDK        | 17+                 | Khuyến nghị OpenJDK 17 hoặc 21       |
| PostgreSQL      | 15+                 | Cài bản mới nhất từ trang chủ        |
| Git             | Bất kỳ              | Để clone project                      |
| IDE (tuỳ chọn)  | —                   | IntelliJ IDEA / VS Code              |

> **Lưu ý:** Project đã tích hợp Maven Wrapper (`mvnw`), **không cần** cài Maven riêng.

---

## 2. Cài đặt môi trường

### Windows

#### Bước 2.1: Cài Java JDK 17

1. Truy cập: https://adoptium.net/ → Tải **Temurin JDK 17** (Windows x64 `.msi`)
2. Chạy file `.msi`, chọn **Set JAVA_HOME** trong quá trình cài
3. Mở **PowerShell**, kiểm tra:

```powershell
java -version
```

Kết quả mong đợi: `openjdk version "17.x.x"` hoặc cao hơn.

#### Bước 2.2: Cài PostgreSQL

1. Truy cập: https://www.postgresql.org/download/windows/
2. Tải **PostgreSQL 15+** (Windows x86-64 Installer)
3. Chạy installer:
   - Chọn đường dẫn cài đặt (mặc định OK)
   - Đặt **password cho superuser `postgres`** (ghi nhớ mật khẩu này)
   - Port mặc định: `5432`
   - Chọn cài **pgAdmin 4** (tuỳ chọn, tiện cho quản lý)
4. Thêm `psql` vào PATH (nếu chưa có):

```powershell
# Kiểm tra psql
psql --version
```

Nếu lệnh `psql` không nhận, thêm vào PATH:

```powershell
# Thêm PostgreSQL bin vào PATH (thay đổi đường dẫn nếu cần)
$env:PATH += ";C:\Program Files\PostgreSQL\15\bin"
```

#### Bước 2.3: Clone project

```powershell
git clone https://github.com/hashi173/BTL-LTW.git
cd BTL-LTW
```

---

### macOS / Linux

#### Bước 2.1: Cài Java JDK 17

**macOS (Homebrew):**

```bash
brew install openjdk@17
sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

**Linux (Ubuntu/Debian):**

```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

Kiểm tra:

```bash
java -version
```

#### Bước 2.2: Cài PostgreSQL

**macOS (Homebrew):**

```bash
brew install postgresql@15
brew services start postgresql@15
```

**Linux (Ubuntu/Debian):**

```bash
sudo apt install postgresql postgresql-contrib -y
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

Kiểm tra:

```bash
psql --version
```

#### Bước 2.3: Clone project

```bash
git clone https://github.com/hashi173/BTL-LTW.git
cd BTL-LTW
```

---

## 3. Tạo database PostgreSQL

### Cách 1: Dùng `psql` (Terminal / PowerShell)

#### Windows (PowerShell)

```powershell
# Đăng nhập PostgreSQL bằng superuser
psql -U postgres
```

> Hệ thống sẽ hỏi password → nhập mật khẩu superuser đã đặt khi cài.

#### macOS / Linux

```bash
# macOS (Homebrew) — thường không cần password
psql postgres

# Linux — đăng nhập qua user postgres
sudo -u postgres psql
```

#### Chạy các lệnh SQL sau (giống nhau trên cả 2 hệ điều hành)

```sql
-- Tạo database
CREATE DATABASE cafe_db_ttnt;

-- Tạo user
CREATE USER cafe_admin WITH ENCRYPTED PASSWORD '123';

-- Cấp quyền cho user
GRANT ALL PRIVILEGES ON DATABASE cafe_db_ttnt TO cafe_admin;

-- Chuyển sang database vừa tạo để cấp quyền schema
\c cafe_db_ttnt

-- Cấp quyền trên schema public
GRANT ALL ON SCHEMA public TO cafe_admin;

-- Thoát
\q
```

### Cách 2: Dùng pgAdmin (Giao diện đồ hoạ)

1. Mở **pgAdmin 4**
2. Kết nối đến PostgreSQL server (localhost, port 5432)
3. Click chuột phải vào **Databases** → **Create** → **Database...**
   - Database name: `cafe_db_ttnt`
   - Owner: `postgres`
   - Nhấn **Save**
4. Click chuột phải vào **Login/Group Roles** → **Create** → **Login/Group Role...**
   - Tab **General**: Name = `cafe_admin`
   - Tab **Definition**: Password = `123`
   - Tab **Privileges**: Bật **Can login**
   - Nhấn **Save**
5. Chọn database `cafe_db_ttnt` → Mở **Query Tool** → Chạy:

```sql
GRANT ALL ON SCHEMA public TO cafe_admin;
```

---

## 4. Cấu hình Spring Boot

Ứng dụng đọc cấu hình database từ file `src/main/resources/application.properties`.

Các giá trị mặc định đã được thiết lập sẵn:

| Biến               | Giá trị mặc định                             |
|---------------------|----------------------------------------------|
| `DB_URL`            | `jdbc:postgresql://localhost:5432/cafe_db`    |
| `DB_USERNAME`       | `cafe_admin`                                 |
| `DB_PASSWORD`       | `123`                                        |

> **Nếu bạn dùng đúng các giá trị trên → không cần thay đổi gì.**

Nếu cần thay đổi (ví dụ port khác, password khác), đặt biến môi trường:

**Windows (PowerShell):**

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/cafe_db_ttnt"
$env:DB_USERNAME = "cafe_admin"
$env:DB_PASSWORD = "your_password"
```

**macOS / Linux:**

```bash
export DB_URL="jdbc:postgresql://localhost:5432/cafe_db_ttnt"
export DB_USERNAME="cafe_admin"
export DB_PASSWORD="your_password"
```

---

## 5. Chạy ứng dụng — Môi trường phát triển (có dữ liệu mẫu)

> Chế độ này tự động seed dữ liệu mẫu (sản phẩm, đơn hàng, lịch sử) mỗi lần khởi động.
> **Phù hợp cho:** phát triển, demo UI, kiểm tra dashboard.

### Windows (PowerShell)

```powershell
# Bước 1: Mở PowerShell tại thư mục project
cd đường-dẫn-đến-project\BTL-LTW

# Bước 2: Đặt profile dev
$env:APP_PROFILE = "dev"

# Bước 3: Chạy ứng dụng
.\mvnw.cmd spring-boot:run
```

### macOS / Linux (Terminal)

```bash
# Bước 1: Mở Terminal tại thư mục project
cd đường-dẫn-đến-project/BTL-LTW

# Bước 2: Cấp quyền chạy Maven Wrapper (chỉ lần đầu)
chmod +x mvnw

# Bước 3: Chạy ứng dụng với profile dev
APP_PROFILE=dev ./mvnw spring-boot:run
```

### Dữ liệu được tạo tự động (Dev mode)

**Tài khoản đăng nhập:**

| Username | Password | Vai trò |
|----------|----------|---------|
| `admin`  | `123456` | ADMIN   |

**Dữ liệu mẫu:**

| Loại dữ liệu | Mã định dạng                         | Số lượng         |
|---------------|--------------------------------------|------------------|
| Category      | `CAT-00001`, `CAT-00002`, ...        | 4 loại           |
| Product       | `PRD-00001`, `PRD-00002`, ...        | 6 sản phẩm       |
| Order         | `ORD-000001`, `ORD-000002`, ...      | ~1000+ đơn       |
| Job Posting   | `JOB-000001`, `JOB-000002`, ...      | 3 vị trí         |

**Danh sách sản phẩm mẫu:**
- ☕ Coffee: Cafe Latte, Espresso
- 🍵 Tea: Peach Tea, Sakura Blossom Tea
- 🥤 Smoothie: Strawberry Smoothie
- 🥥 Juice: Coconut Juice

## 6. Kiểm tra ứng dụng

Sau khi khởi chạy thành công, mở trình duyệt truy cập:

| Trang                      | URL                                    |
|----------------------------|----------------------------------------|
| 🏠 Trang chủ               | http://localhost:8080                  |
| 🔑 Đăng nhập               | http://localhost:8080/login            |
| 📦 Theo dõi đơn hàng       | http://localhost:8080/tracking         |
| ⚙️ Admin Dashboard         | http://localhost:8080/admin/dashboard  |
| 📋 Quản lý đơn hàng        | http://localhost:8080/admin/orders     |
| 🍵 Quản lý sản phẩm        | http://localhost:8080/admin/products   |
| 📝 Quản lý tuyển dụng      | http://localhost:8080/admin/recruitment|

---

## 7. Xử lý lỗi thường gặp

### Lỗi: `relation "xxx" does not exist`

**Nguyên nhân:** Bảng chưa được tạo.

**Cách sửa:** Chạy app ở `dev` mode hoặc `prod` mode 1 lần trước để Hibernate tạo bảng, sau đó mới import SQL.

### Lỗi: `password authentication failed for user "cafe_admin"`

**Nguyên nhân:** Sai mật khẩu hoặc user chưa tạo.

**Cách sửa:**

```sql
-- Đăng nhập lại bằng superuser
psql -U postgres

-- Reset password
ALTER USER cafe_admin WITH PASSWORD '123';
\q
```

### Lỗi: `FATAL: database "cafe_db_ttnt" does not exist`

**Nguyên nhân:** Database chưa được tạo.

**Cách sửa:** Quay lại [Bước 3](#3-tạo-database-postgresql) để tạo database.

### Lỗi: `Could not resolve placeholder 'DB_URL'`

**Nguyên nhân:** Biến môi trường chưa được đặt.

**Cách sửa:** Kiểm tra biến môi trường hoặc file `application.properties` đã có giá trị mặc định.

### Lỗi: `mvnw: Permission denied` (macOS/Linux)

**Cách sửa:**

```bash
chmod +x mvnw
```

### Port 8080 đã bị chiếm

**Cách sửa:** Thay đổi port trong `application.properties`:

```properties
server.port=8081
```

Hoặc đặt biến môi trường:

```bash
# macOS/Linux
SERVER_PORT=8081 ./mvnw spring-boot:run
```

```powershell
# Windows
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

---

## Khuyến nghị

| Mục đích                    | Chế độ nên dùng         |
|-----------------------------|-------------------------|
| Phát triển, test UI         | `APP_PROFILE=dev`       |
| Demo sản phẩm hoàn chỉnh   | `APP_PROFILE=dev`       |
