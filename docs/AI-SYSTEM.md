# Tài liệu Hệ thống AI - Hashiji Café

## 1. Tổng quan hệ thống
Hệ thống Hashiji Café được xây dựng với mục tiêu tích hợp trí tuệ nhân tạo (AI) để gợi ý sản phẩm, giúp tăng trải nghiệm người dùng và doanh số bán hàng.

### Các tính năng chính của hệ thống
- **Người dùng (User)**: Đăng ký, đăng nhập, quản lý hồ sơ (username, địa chỉ), xem menu (menu chung và danh sách được gợi ý riêng), đặt đồ, theo dõi đơn hàng (tracking order), xem lại danh sách tóm tắt các đơn từng đặt (bao gồm mã code, thời gian, món, tiền, và trạng thái đơn hàng như đang chờ duyệt, đang vận chuyển, thành công).
- **Quản trị viên (Admin)**: Đăng nhập, quản lý người dùng, quản lý sản phẩm, quản lý đơn hàng, xem thống kê. Admin chỉ tập trung vào nghiệp vụ quản lý bán hàng và báo cáo tài chính, không yêu cầu các chức năng theo dõi thuật toán AI.

---

## 2. Giao diện và Luồng hoạt động (UI/UX)
Hệ thống hiển thị kết quả của thuật toán AI trên giao diện người dùng theo các quy tắc sau:

- **Bố cục trang Menu**: 
  - Phần Menu danh sách toàn bộ sản phẩm sẽ được đặt ở phía trên.
  - Phần "Gợi ý cho bạn" (Recommended for you) sẽ được đặt ở phía dưới.
- **Người dùng mới (New User)**: 
  - Do người dùng mới tinh chưa có lịch sử mua hàng để phân tích, mục gợi ý sẽ không hiển thị "Dựa trên sở thích của bạn" mà thay bằng **"Best Seller"** (Các sản phẩm bán chạy nhất).
- **Gợi ý Cross-selling (Bán chéo)**: 
  - Khi người dùng thêm một món cụ thể vào giỏ hàng, hệ thống sẽ hiển thị một popup nhỏ gợi ý thêm khoảng 3 món với tiêu đề **"Bạn có thể cũng thích"** (You may also like) để kích thích mua sắm.

---

## 3. Kiến trúc Thuật toán Gợi ý
Hệ thống chỉ tập trung vào việc **recommend (gợi ý) sản phẩm cho người dùng**. Logic của hệ gợi ý được viết bằng backend, không sử dụng các model bên ngoài. Thuật toán là sự kết hợp (hybrid) giữa hai phương pháp chính.

### 3.1. Thuật toán chính: Collaborative Filtering (Lọc cộng tác)
- **Mục đích**: Gợi ý sản phẩm dựa trên hành vi của nhiều người dùng có sở thích tương đồng.
- **Điều kiện hoạt động**: Người dùng đã có dữ liệu mua hàng trên hệ thống.
- **Các thuật toán áp dụng**:
  1. **Cosine Similarity**: Dùng để so sánh và tính toán độ giống nhau giữa các người dùng (dựa trên lịch sử mua các món giống nhau).
  2. **K-Nearest Neighbors (KNN)**: Sau khi tính toán độ tương đồng bằng Cosine Similarity, hệ thống sử dụng KNN để chọn ra $K$ người dùng gần nhất (giống nhất) để làm cơ sở gợi ý các sản phẩm mà họ đã mua cho người dùng hiện tại.
- **Ví dụ thực tế**: Người dùng A từng mua Cafe và Matcha. Các người dùng B, C, D cũng từng mua Cafe và Matcha, nhưng họ còn mua thêm Mocha và Trà. Hệ thống sẽ chọn B, C, D (những người giống A nhất) và từ lịch sử của họ, hệ thống đưa ra kết quả gợi ý Mocha hoặc Trà cho A.

### 3.2. Thuật toán bổ trợ: Rule-based Recommendation (Gợi ý dựa trên tập luật)
- **Mục đích**: Tự đặt ra các quy tắc (rule) cho hệ thống để xử lý các ngoại lệ của thuật toán Lọc cộng tác và bổ sung độ chính xác.
- **Các tập luật**:
  - **Luật 1 (Dành cho User mới)**: Nếu là tài khoản mới chưa có lịch sử, tự động gợi ý các sản phẩm **Best Seller** (Bán chạy nhất).
  - **Luật 2 (Dựa trên lịch sử cá nhân)**: Gợi ý theo lịch sử mua hàng của chính người đó, ưu tiên gợi ý lại những món mà người đó đã từng order nhiều lần hoặc có điểm rating cao.

### 3.3. Cách kết hợp kết quả (Hybrid Approach)
Hệ thống tạo ra danh sách gợi ý cuối cùng qua các bước:
1. Tính điểm gợi ý (Recommendation Score) từ thuật toán Collaborative Filtering.
2. Tính điểm gợi ý từ tập luật Rule-based Recommendation.
3. Áp dụng hệ số tính toán (Weight - Trọng số) cho mỗi kết quả (ví dụ CF chiếm 60%, Rule-based chiếm 40%).
4. Tổng hợp điểm số, sắp xếp và tạo danh sách sản phẩm gợi ý cuối cùng trả về cho người dùng.

---

## 4. Kế hoạch Thực nghiệm và Đánh giá 
*(Mục này dành cho báo cáo và slide bảo vệ, không đưa vào giao diện hệ thống)*

Để chứng minh cho giảng viên thấy thuật toán có hiệu quả, nhóm sẽ chuẩn bị phần kết quả thực nghiệm trong báo cáo. 
- **Phương pháp đo lường**: 
  - Hệ thống tự động tạo ra một số lượng tài khoản mẫu nhất định (ví dụ 100-500 users giả lập có hành vi mua hàng cụ thể).
  - Tiến hành ẩn đi một phần lịch sử mua hàng của họ và yêu cầu thuật toán dự đoán các món bị ẩn đó.
- **Các chỉ số báo cáo**:
  - So sánh độ chính xác của thuật toán gợi ý hệ thống đang dùng so với việc gợi ý ngẫu nhiên (Random).
  - Kết quả cuối cùng sẽ cho thấy tỷ lệ gợi ý trúng đích (Precision) và tỷ lệ bao phủ (Recall) khi áp dụng thuật toán kết hợp.
