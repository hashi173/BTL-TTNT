# Hướng dẫn chạy Hashiji Cafe bằng Docker 🐳

Sử dụng Docker là cách nhanh nhất để khởi động toàn bộ hệ thống (App + Database + pgAdmin) mà không cần cài đặt Java hay PostgreSQL thủ công trên máy.

## 1. Yêu cầu hệ thống
- Đã cài đặt **Docker Desktop** (cho Windows/Mac) hoặc **Docker Engine & Compose** (cho Linux).
- Đảm bảo các cổng **8080**, **5432**, và **5050** đang trống.

---

## 2. Các bước khởi chạy

### Bước 1: Khởi động container
Mở Terminal (PowerShell hoặc CMD) tại thư mục gốc của project và chạy lệnh:
```bash
docker compose up -d --build
```
*Lưu ý: Lần đầu chạy sẽ mất khoảng 2-5 phút để tải image và build file JAR.*

### Bước 2: Kiểm tra trạng thái
Kiểm tra xem các service đã chạy chưa:
```bash
docker compose ps
```

### Bước 3: Truy cập các dịch vụ

| Dịch vụ | URL | Thông tin đăng nhập |
| :--- | :--- | :--- |
| **🌐 Website** | [http://localhost:8080](http://localhost:8080) | Tài khoản mặc định: `admin` / `123456` |
| **🐘 pgAdmin** | [http://localhost:5050](http://localhost:5050) | Email: `admin@hashiji.cafe` <br> Pass: `admin123` |
| **🗄️ Database** | `localhost:5432` | User: `cafe_admin` <br> Pass: `123` <br> DB: `cafe_db` |

---

## 3. Quản lý Database trong Docker

### Kết nối pgAdmin với Database
Khi vào pgAdmin (port 5050), để kết nối tới Postgres trong cùng mạng Docker:
1. Chuột phải vào **Servers** -> **Register** -> **Server...**
2. Tab **General**: Đặt tên bất kỳ (vd: `Hashiji DB`)
3. Tab **Connection**:
   - Host name/address: `postgres` (Tên service trong file compose)
   - Port: `5432`
   - Maintenance database: `cafe_db`
   - Username: `cafe_admin`
   - Password: `123`
4. Nhấn **Save**.

---

## 4. Các lệnh hữu ích khác

| Lệnh | Tác dụng |
| :--- | :--- |
| `docker compose logs -f hashiji-app` | Xem log chạy của ứng dụng (Real-time) |
| `docker compose stop` | Tạm dừng các service |
| `docker compose start` | Chạy lại các service đang dừng |
| `docker compose down` | Dừng và xóa toàn bộ container |
| `docker compose down -v` | Dừng và **xóa sạch dữ liệu database** (Volume) |
| `docker compose restart hashiji-app` | Khởi động lại riêng ứng dụng Spring Boot |

---

## 5. Lưu ý về Dữ liệu (Seeding)
Mặc định trong file `docker-compose.yml`, ứng dụng chạy với profile `dev`:
- Hệ thống sẽ tự động tạo bảng và **tự động nạp dữ liệu mẫu** (Categories, Products, Users, Orders) mỗi khi khởi động.
- Nếu bạn muốn dùng bộ dữ liệu SQL thuần (SQL-only demo), hãy đổi `APP_PROFILE: prod` trong file `docker-compose.yml` và thực hiện import SQL thủ công qua pgAdmin.

---
> [!TIP]
> Nếu bạn gặp lỗi "Port already allocated", hãy kiểm tra xem có ứng dụng nào (như PostgreSQL local) đang chiếm cổng 5432 không và tắt nó đi trước khi chạy Docker.
