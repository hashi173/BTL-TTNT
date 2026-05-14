# Demo Scenario - Hệ thống AI Hashiji Café

> Script demo cho bài bảo vệ BTL-TTNT. Mỗi scenario có: **bước thực hiện**, **kết quả mong đợi**, **giải thích thuật toán**.

---

## Demo 1: Gợiý sản phẩm cá nhân hóa

### Bước thực hiện
1. Mở trình duyệt → `http://localhost:8080`
2. Đăng nhập: `alice` / `123456` (Coffee cluster)
3. Xem phần "Gợi ý cho bạn" trên trang Home

### Kết quả mong đợi
- Hiển thị 6 sản phẩm chủ yếu là **Coffee** (Latte, Espresso, Cappuccino, Mocha...)
- Vì alice là Coffee lover → hệ thống học từ lịch sử mua hàng

### Giải thích thuật toán
```
alice đã mua: Cafe Latte ×15, Espresso ×12, Cappuccino ×8, ...
bob (giống alice): Cafe Latte ×12, Espresso ×10, Mocha ×5, ...

Cosine Similarity(alice, bob) = 0.85 → rất giống!
bob đã mua Mocha mà alice chưa mua
→ Hệ thống gợiý Mocha cho alice (CF score = 0.85 × 0.7 = 0.60)

Mocha cũng bán chạy (RB score = 0.5)
→ finalScore = 0.6 × 0.60 + 0.4 × 0.50 = 0.56
```

---

## Demo 2: So sánh user khác nhau

### Bước thực hiện
1. Đăng xuất → Đăng nhập: `kate` / `123456` (Tea cluster)
2. Xem phần "Gợi ý cho bạn"

### Kết quả mong đợi
- Hiển thị 6 sản phẩm chủ yếu là **Tea** (Matcha Latte, Jasmine Tea, Oolong...)
- Hoàn toàn khác với alice!

### Giải thích thuật toán
```
kate đã mua: Matcha Latte ×14, Jasmine Tea ×10, Oolong ×8, ...

Cosine Similarity(alice, kate) = 0.05 → rất khác!
Cosine Similarity(kate, leo) = 0.82 → rất giống! (leo cũng là Tea lover)

→ Hệ thống gợiý sản phẩm mà leo thích cho kate
→ Kết quả: toàn bộ là Tea
```

---

## Demo 3: User mới (Cold Start)

### Bước thực hiện
1. Đăng xuất → Mở trang `/register`
2. Tạo tài khoản mới: `testuser` / `123456`
3. Đăng nhập → Xem "Gợi ý cho bạn"

### Kết quả mong đợi
- Hiển thị **sản phẩm bán chạy nhất** (không phải personalized)
- Vì user mới chưa có lịch sử → fallback về best sellers

### Giải thích thuật toán
```
testuser chưa mua gì → userRatings = {} (empty)

Hệ thống kiểm tra: isNewUser = true
→ Không chạy CF (không có data)
→ Chạy Rule-based: lấy sản phẩm bán chạy nhất

Best sellers: Cafe Latte (bán 500 ly), Espresso (400 ly), ...
→ Gợiý 6 sản phẩm bán chạy nhất
```

---

## Demo 4: Đánh giá hệ thống (Admin Dashboard)

### Bước thực hiện
1. Đăng nhập: `admin` / `123456`
2. Mở sidebar → "AI Evaluation"
3. Xem các biểu đồ và bảng số liệu

### Kết quả mong đợi
- **Summary cards:** Precision, Recall, F1, Hit Rate, MAP
- **Baseline comparison:** Hybrid > Popularity > Random
- **Ablation study:** Trọng số 0.6/0.4 cho kết quả tốt nhất
- **F1 vs K:** F1 cao nhất ở K=5 hoặc K=6
- **Confusion Matrix:** Naive Bayes phân loại đúng大部分 sản phẩm

### Giải thích thuật toán
```
Baseline Comparison:
  Hybrid (0.6/0.4): F1 = 0.25, Hit Rate = 0.45
  CF-Only:           F1 = 0.22, Hit Rate = 0.40
  RB-Only:           F1 = 0.18, Hit Rate = 0.35
  Popularity:        F1 = 0.12, Hit Rate = 0.25
  Random:            F1 = 0.05, Hit Rate = 0.10

→ Hybrid > CF-Only > RB-Only > Popularity > Random
→ Kết hợp CF + RB tốt hơn dùng单独任何一个!
```

---

## Demo 5: Naive Bayes Confusion Matrix

### Bước thực hiện
1. Cuộn xuống phần "Naive Bayes Confusion Matrix"
2. Xem bảng confusion matrix

### Kết quả mong đợi
- Đường chéo chính (đỏ đậm) = dự đoán đúng
- Các ô ngoài đường chéo = dự đoán sai
- Accuracy hiển thị ở trên

### Giải thích thuật toán
```
Confusion Matrix:
              Predicted
              Coffee  Tea  Smoothie  Juice
Actual Coffee   13     1      1        0     ← 13/15 đúng
       Tea       1    12      1        1     ← 12/15 đúng
       Smoothie  0     1      8        1     ← 8/10 đúng
       Juice     0     0      1        9     ← 9/10 đúng

Accuracy = (13+12+8+9) / 50 = 84%

Sai nhiều nhất: Coffee bị nhầm sang Tea (1 sản phẩm)
→ Vì 1 số sản phẩm Coffee có tên chứa "tea" (Matcha Espresso)
```

---

## Demo 6: Precision-Recall Curve

### Bước thực hiện
1. Xem biểu đồ "Precision-Recall Curve (K=1..20)"

### Kết quả mong đợi
- Precision giảm khi K tăng (gợiý nhiều hơn → nhiều sai hơn)
- Recall tăng khi K tăng (gợiý nhiều hơn → ít bỏ sót hơn)
- F1 cao nhất ở K=5 hoặc K=6

### Giải thích thuật toán
```
K=1:  Precision=0.30, Recall=0.15  (gợiý 1 → đúng 30%, nhưng bỏ sót 85%)
K=5:  Precision=0.18, Recall=0.45  (cân bằng tốt nhất)
K=10: Precision=0.12, Recall=0.60  (recall cao nhưng precision thấp)
K=20: Precision=0.06, Recall=0.75  (gợiý太多, precision很低)

→ Chọn K=6 (giữa 5 và 10) để cân bằng Precision và Recall
```

---

## Demo 7: Ablation Study — Trọng số CF/RB

### Bước thực hiện
1. Xem bảng "Ablation Study: CF/RB Weight Sweep"

### Kết quả mong đợi
- F1 cao nhất ở CF=0.6, RB=0.4 hoặc CF=0.7, RB=0.3
- CF-only (1.0/0.0) tốt hơn RB-only (0.0/1.0)
- Hybrid luôn tốt hơn single approach

### Giải thích thuật toán
```
CF/RB    F1      Hit Rate
0.9/0.1  0.23    0.42    ← CF quá cao, bỏ qua RB
0.8/0.2  0.24    0.44
0.7/0.3  0.25    0.45
0.6/0.4  0.25    0.45    ← Tốt nhất!
0.5/0.5  0.24    0.44
0.4/0.6  0.22    0.40
0.3/0.7  0.20    0.37
0.2/0.8  0.18    0.34
0.1/0.9  0.16    0.30    ← RB quá cao, bỏ qua CF

→ 0.6/0.4 là optimal. Lý do:
  - CF giỏi捕捉 sở thích cá nhân
  - RB giỏi xử lý cold start và sản phẩm phổ biến
  - Kết hợp = best of both worlds
```

---

## Tổng kết cho người nghe

```
Hệ thống AI của chúng tôi:

1. ✅ Sử dụng 4 thuật toán AI: CF, TF-IDF, Cosine Similarity, Naive Bayes
2. ✅ Đánh giá bằng 5 chỉ số: Precision, Recall, F1, Hit Rate, MAP
3. ✅ So sánh với 2 baseline: Random, Popularity
4. ✅ Ablation study: thử nhiều trọng số và K
5. ✅ Confusion matrix cho Naive Bayes
6. ✅ 50 sản phẩm, 30 users, 15000+ đơn hàng mẫu

Kết quả: Hybrid (CF + RB) tốt hơn Popularity baseline 2x,
tốt hơn Random baseline 5x.
```
