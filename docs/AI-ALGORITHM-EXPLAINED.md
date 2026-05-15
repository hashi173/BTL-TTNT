# 🧮 Giải Mã Toán Học & Thuật Toán AI (Algorithm Explained)
*Tài liệu này đi sâu vào "phần ruột" khoa học của dự án. Cung cấp công thức toán, ví dụ cụ thể và cách tính toán thủ công để bạn hoàn toàn làm chủ thuật toán khi đứng trước Hội đồng.*

---

## 1. Collaborative Filtering (Lọc Cộng Tác Dựa Trên Người Dùng)

### A. Khái niệm khoa học
Dự án sử dụng phương pháp **User-based Collaborative Filtering** (Lọc cộng tác lấy người dùng làm trung tâm) kết hợp thuật toán **K-Nearest Neighbors (KNN)**. 
- **Định nghĩa:** Dự đoán sở thích của một người (User A) đối với một món hàng (Item X) dựa trên sở thích của một nhóm người dùng (K-Neighbors) có hành vi mua sắm tương đồng nhất với User A.

### B. Công thức Toán học: Cosine Similarity (Độ tương đồng Cosine)
Để máy tính biết được "User A có giống User B không", ta phải mã hóa lịch sử mua hàng của họ thành các **Vector toán học** và đo góc giữa hai Vector đó.

**Công thức Cosine:**
`Similarity(A, B) = cos(θ) = (A • B) / (||A|| × ||B||)`
*Trong đó:*
- `A • B`: Tích vô hướng của 2 vector (Nhân số lượng mua của từng món tương ứng rồi cộng lại).
- `||A||` và `||B||`: Độ dài của mỗi vector (Căn bậc hai của tổng bình phương).
- Kết quả chạy từ `0` (Hoàn toàn không giống) đến `1` (Giống hệt nhau).

### C. Ví dụ tính toán thực tế bằng tay
Giả sử quán cafe có 3 món: `[Đen Đá, Bạc Xỉu, Trà Đào]`.
- Khách hàng A từng mua: 2 Đen Đá, 1 Bạc Xỉu, 0 Trà Đào. => **Vector A = [2, 1, 0]**
- Khách hàng B từng mua: 3 Đen Đá, 2 Bạc Xỉu, 1 Trà Đào. => **Vector B = [3, 2, 1]**

**Bước 1: Tính tử số (Tích vô hướng)**
A • B = (2 × 3) + (1 × 2) + (0 × 1) = 6 + 2 + 0 = **8**

**Bước 2: Tính độ dài từng Vector (Mẫu số)**
- ||A|| = √(2² + 1² + 0²) = √(4 + 1) = **√5 ≈ 2.236**
- ||B|| = √(3² + 2² + 1²) = √(9 + 4 + 1) = **√14 ≈ 3.741**

**Bước 3: Tính Cosine Similarity**
Cosine(A, B) = 8 / (2.236 × 3.741) = 8 / 8.364 ≈ **0.956**
> **Kết luận:** Điểm tương đồng là `0.956` (gần bằng 1). Máy tính kết luận Khách A và Khách B có gu rất giống nhau! Vì B từng uống Trà Đào, thuật toán sẽ lấy "Trà Đào" gợi ý cho A.

### D. Áp dụng vào Code
Bạn có thể tìm thấy công thức toán học này được triển khai trực tiếp bằng Java tại hàm `calculateCosineSimilarity()` trong file `RecommendationService.java`.

---

## 2. Thuật toán Luật định sẵn (Rule-based Recommendation)

### A. Khái niệm khoa học
Mô hình toán học đôi khi thất bại nếu không có dữ liệu (Ví dụ: Cosine của Vector `[0, 0, 0]` là không thể tính toán vì chia cho 0). Thuật toán Rule-based ra đời để bao bọc và sửa lỗi cho mô hình toán học.

### B. Cách làm và Công thức
- **Rule 1 (Giải quyết bài toán Cold-Start - Khởi đầu lạnh):** 
  - Nếu `Tổng số lượng mua của User = 0`.
  - Thuật toán chuyển sang truy vấn cơ sở dữ liệu (SQL): `ORDER BY SUM(quantity) DESC`. Trả về Top Best Seller.
- **Rule 2 (Dựa trên lịch sử / Tần suất cá nhân):** 
  - Gọi tập hợp lịch sử mua hàng của User là `H`. 
  - `Score_RB(Item X) = Tần suất xuất hiện của X trong H`. Món nào mua càng nhiều lần, điểm Rule-based càng cao.

---

## 3. Hệ Thống Lai (Hybrid Recommendation System)

### A. Khái niệm khoa học
Dự án không dùng đơn lẻ 1 thuật toán mà ghép Lọc cộng tác (CF) và Tập luật (RB) lại bằng **Mô hình Trọng số tuyến tính (Linear Weighted Hybrid Model)**.

### B. Công thức kết hợp
`FinalScore(Item X) = (W_CF × Score_CF) + (W_RB × Score_RB)`
Trong dự án này, nhóm thiết lập trọng số cố định:
- `W_CF = 0.6` (60% cho cộng đồng)
- `W_RB = 0.4` (40% cho thói quen cá nhân)

### C. Ví dụ tính toán tổng quát hệ thống
Hệ thống cần xem xét có nên gợi ý "Trà Vải" cho Khách A không.
- Thuật toán Cosine Similarity chấm điểm "Trà Vải" đạt **0.8 điểm** (Vì rất nhiều người giống Khách A thích Trà Vải).
- Khách A chưa từng tự mua Trà Vải trong quá khứ -> Điểm Rule-based = **0 điểm**.
  
👉 **Tính điểm chung cuộc:** `FinalScore = (0.6 × 0.8) + (0.4 × 0) = 0.48`.
Hệ thống sẽ tính điểm chung cuộc cho *TẤT CẢ* đồ uống, sau đó xếp hạng từ cao xuống thấp và nhặt ra đúng 6 món điểm cao nhất đẩy ra Trang Chủ.

---

## 4. Cross-Selling (Gợi ý bán chéo)
*Hiển thị ở màn hình Giỏ Hàng dưới dạng "Bạn có thể cũng thích".*

### Khái niệm & Cách tính
Dùng thuật toán **Item Co-occurrence (Đồng xuất hiện của sản phẩm)**.
- Khi người dùng thêm "Cà Phê Đen" (Món X) vào giỏ.
- Thuật toán lục tìm tất cả những người trong lịch sử *từng mua* Món X.
- Đếm xem trong giỏ hàng của những người đó, món Y (ví dụ Bánh Ngọt) xuất hiện bao nhiêu lần.
- Món nào xuất hiện cùng Món X nhiều nhất sẽ được gợi ý lên.
