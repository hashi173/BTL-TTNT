# Hướng dẫn Team - Hệ thống AI Hashiji Café

> Doc này dành cho các thành viên trong team đọc hiểu codebase AI. Mỗi phần giải thích: **chức năng gì**, **code ở đâu**, **áp dụng công thức nào**, **ví dụ dễ hiểu**.

---

## 1. Tổng quan: AI làm gì trong hệ thống?

```
┌─────────────────────────────────────────────────────────────┐
│  User mở trang Home → thấy "Gợi ý cho bạn"                │
│  User đặt hàng → hệ thống học sở thích                     │
│  Admin mở AI Dashboard → xem biểu đồ đánh giá              │
└─────────────────────────────────────────────────────────────┘

Hệ thống AI gồm 5 thành phần:

1. RecommendationService    → Gợiý sản phẩm cho user
2. TextSimilarityService    → So sánh văn bản (TF-IDF)
3. NaiveBayesClassifier     → Phân loại sản phẩm
4. RecommendationEvaluator  → Đánh giá hiệu suất
5. BaselineRecommender      → Baseline để so sánh
```

---

## 2. RecommendationService — Gợiý sản phẩm

**File:** `src/main/java/com/coffeeshop/service/RecommendationService.java`

### 2.1 Nó làm gì?

Khi user mở trang Home, hệ thống gợiý 6 sản phẩm phù hợp với sở thích.

### 2.2 Code ở đâu?

| Method | Dòng | Chức năng |
|--------|------|-----------|
| `getRecommendations(userId)` | ~37 | Entry point — gọi từ HomeController |
| `ensureMatricesBuilt()` | ~137 | Xây dựng ma trận user-product |
| `computeCFScores(userId)` | ~230 | Tính điểm Collaborative Filtering |
| `computeRuleBasedScores(userId)` | ~274 | Tính điểm Rule-based |
| `cosineSimilarity(vec1, vec2)` | ~206 | Công thức Cosine Similarity |

### 2.3 Công thức: Cosine Similarity

```
Đo lường 2 user "giống nhau" bao nhiêu dựa trên lịch sử mua hàng.

Ví dụ:
  User A mua: [Cafe Latte=2, Espresso=1, Peach Tea=0]
  User B mua: [Cafe Latte=1, Espresso=2, Peach Tea=0]
  User C mua: [Cafe Latte=0, Espresso=0, Peach Tea=3]

  sim(A,B) = (2×1 + 1×2 + 0×0) / (√(4+1) × √(1+4))
           = 4 / (√5 × √5) = 4/5 = 0.8  → Rất giống!
  sim(A,C) = 0 / (√5 × √9) = 0          → Hoàn toàn khác!

→ Hệ thống sẽ gợiý sản phẩm mà User B thích cho User A.
```

### 2.4 Công thức: Kết hợp trọng số

```
finalScore = 0.6 × CF_score + 0.4 × RB_score

CF_score:  Điểm từ Collaborative Filtering (user tương tự)
RB_score:  Điểm từ Rule-based (sản phẩm bán chạy / mua thường xuyên)

Ví dụ:
  Sản phẩm "Cappuccino":
    CF_score = 0.7 (user tương tự rất thích)
    RB_score = 0.5 (bán khá chạy)
    finalScore = 0.6 × 0.7 + 0.4 × 0.5 = 0.62

  Sản phẩm "Mango Smoothie":
    CF_score = 0.3
    RB_score = 0.8 (bán rất chạy)
    finalScore = 0.6 × 0.3 + 0.4 × 0.8 = 0.50

  → "Cappuccino" được gợiý trước vì score cao hơn.
```

---

## 3. NaiveBayesClassifier — Phân loại sản phẩm

**File:** `src/main/java/com/coffeeshop/service/ai/NaiveBayesClassifier.java`

### 3.1 Nó làm gì?

Nhận vào 1 câu text (ví dụ: "tôi muốn uống cà phê sữa"), dự đoán category (Coffee/Tea/Smoothie/Juice).

### 3.2 Code ở đâu?

| Method | Dòng | Chức năng |
|--------|------|-----------|
| `train()` | ~108 | Học từ database sản phẩm |
| `predict(text)` | ~199 | Dự đoán category |
| `predictProbabilities(text)` | ~243 | Dự đoán với xác suất |
| `tokenize(text)` | ~312 | Tách từ, bỏ stopwords |

### 3.3 Công thức: Bayes Theorem

```
P(Coffee | "cà phê sữa") = P("cà phê sữa" | Coffee) × P(Coffee)
                            ────────────────────────────────────────
                                      P("cà phê sữa")

Giả định "Naive": Các từ độc lập nhau
→ P("cà phê sữa" | Coffee) = P("cà" | Coffee) × P("phê" | Coffee) × P("sữa" | Coffee)
```

### 3.4 Công thức: Laplace Smoothing

```
Không để xác suất = 0 (sẽ phá hủy toàn bộ phép nhân).

Ví dụ:
  Từ "matcha" chưa xuất hiện trong category Coffee:
    P("matcha" | Coffee) = (0 + 1) / (total_words + vocab_size)
                         = 1 / (100 + 50) = 0.0067  (nhỏ nhưng ≠ 0)

  Nếu không smoothing:
    P("matcha" | Coffee) = 0 → toàn bộ tích = 0  ← Sai!
```

### 3.5 Ví dụ hoạt động

```
Input: "tôi muốn uống cà phê sữa"

Tokenize: ["muon", "uong", "ca", "phe", "sua"]  (bỏ dấu, bỏ stopwords)

Tính score cho mỗi category:
  Coffee:  log P(Coffee) + log P("ca"|Coffee) + log P("phe"|Coffee) + ...
         = -0.5 + (-1.2) + (-1.3) + ... = -4.8

  Tea:     log P(Tea) + log P("ca"|Tea) + log P("phe"|Tea) + ...
         = -0.8 + (-3.5) + (-4.0) + ... = -12.1

  → Coffee score cao hơn (-4.8 > -12.1) → dự đoán "Coffee"
```

---

## 4. TextSimilarityService — So sánh văn bản

**File:** `src/main/java/com/coffeeshop/service/ai/TextSimilarityService.java`

### 4.1 Nó làm gì?

So sánh 2 câu text dựa trên nội dung (không phải ký tự). Dùng để tìm sản phẩm liên quan nhất.

### 4.2 Code ở đâu?

| Method | Dòng | Chức năng |
|--------|------|-----------|
| `computeCosineSimilarity(text1, text2)` | ~85 | So sánh 2 text |
| `findRelevantProducts(query, products, topK)` | ~130 | Tìm top-K sản phẩm liên quan |
| `computeTF(tokens)` | ~224 | Tính Term Frequency |
| `computeIDF(tokens1, tokens2, vocab)` | ~249 | Tính Inverse Document Frequency |

### 4.3 Công thức: TF-IDF

```
TF (từ "cà phê" trong mô tả Cafe Latte):
  TF = số lần "cà phê" xuất hiện / tổng số từ
     = 2 / 10 = 0.2

IDF (từ "cà phê" trong toàn bộ catalog):
  IDF = log(tổng sản phẩm / số sản phẩm chứa "cà phê") + 1
      = log(50 / 15) + 1 = 1.83

TF-IDF = 0.2 × 1.83 = 0.366

→ Từ càng đặc trưng cho sản phẩm đó → TF-IDF càng cao.
→ Từ phổ biến ở mọi sản phẩm (như "đồ uống") → TF-IDF thấp.
```

### 4.4 Công thức: Cosine Similarity (cho text)

```
Query: "cà phê sữa"
Product A: "Cafe Latte - Creamy espresso with steamed milk"
Product B: "Peach Tea - Refreshing peach tea"

Sau tokenize + TF-IDF → 2 vector:
  Query  = [0.5, 0.3, 0.0, 0.0, ...]  (dimensions = vocabulary)
  Prod A = [0.4, 0.2, 0.1, 0.0, ...]
  Prod B = [0.0, 0.0, 0.0, 0.3, ...]

cos(Query, Prod A) = 0.26  → cao hơn → liên quan hơn!
cos(Query, Prod B) = 0.00  → không liên quan
```

---

## 5. RecommendationEvaluator — Đánh giá hiệu suất

**File:** `src/main/java/com/coffeeshop/service/ai/RecommendationEvaluator.java`

### 5.1 Nó làm gì?

Đo lường hệ thống gợiý "đúng" bao nhiêu % bằng cách: ẩn 1 sản phẩm user đã mua, thử xem hệ thống có gợiý lại được không.

### 5.2 Code ở đâu?

| Method | Dòng | Chức năng |
|--------|------|-----------|
| `evaluate(k)` | ~116 | Đánh giá LOO cơ bản |
| `ablationStudyWeights()` | ~162 | Thử nhiều trọng số CF/RB |
| `ablationStudyK()` | ~196 | Thử nhiều giá trị K |
| `compareBaselines()` | ~224 | So sánh vs Random/Popularity |
| `evaluateNaiveBayes(classifier)` | ~262 | Confusion matrix cho NB |
| `precisionRecallCurve()` | ~318 | Dữ liệu cho PR curve |
| `f1ByKCurve()` | ~348 | Dữ liệu cho F1@K curve |

### 5.3 Ví dụ: Leave-One-Out

```
User alice đã mua: [Cafe Latte ×3, Espresso ×2, Cappuccino ×1]

Bước 1: Ẩn "Cafe Latte" (mua nhiều nhất) → test item
Bước 2: Dùng [Espresso, Cappuccino] làm lịch sử
Bước 3: Gợiý top-6 sản phẩm từ hệ thống

  Kết quả gợiý: [Mocha, Americano, Flat White, Cafe Latte, Cold Brew, ...]
                                    ↑
                              Cafe Latte ở vị trí 4 → HIT!

  Precision@6 = 1/6 = 0.167
  Recall@6    = 1/1 = 1.0
  AP          = 1/4 = 0.25  (rank = 4)
```

### 5.4 Các chỉ số đánh giá

```
Precision@K: Trong K gợiý, bao nhiêu % đúng?
  → Cao = gợiý chính xác, ít "rác"

Recall@K: Trong tất cả sản phẩm liên quan, bao nhiêu % được gợiý?
  → Cao = không bỏ sót

F1 = 2 × P × R / (P + R): Cân bằng P và R
  → F1 cao = cả P và R đều tốt

Hit Rate: Bao nhiêu % user có ≥1 gợiý đúng?
  → Quan trọng nhất: user chỉ cần 1 gợiý đúng là hài lòng

MAP: Có xét thứ tự (gợiý đúng ở vị trí 1 tốt hơn vị trí 6)
  → MAP cao = gợiý đúng + đúng ngay đầu
```

---

## 6. BaselineRecommender — Baseline để so sánh

**File:** `src/main/java/com/coffeeshop/service/ai/BaselineRecommender.java`

### 6.1 Nó làm gì?

Tạo 2 baseline đơn giản để so sánh với hệ thống hybrid:
- **Random:** Gợiý ngẫu nhiên → đo "tệ nhất có thể"
- **Popularity:** Gợiý sản phẩm bán chạy nhất → đo "baseline đơn giản"

### 6.2 Tại sao cần baseline?

```
Nếu Precision của hybrid = 0.15:
  - So với Random (0.05) → hybrid tốt hơn 3 lần ✓
  - So với Popularity (0.12) → hybrid tốt hơn 25% ✓
  → Hệ thống AI có giá trị!

Nếu hybrid ≈ Popularity:
  → CF không đóng góp gì → cần cải thiện
```

---

## 7. DataSeeder — Dữ liệu mẫu

**File:** `src/main/java/com/coffeeshop/config/DataSeeder.java`

### 7.1 Nó tạo gì?

| Dữ liệu | Số lượng | Mục đích |
|----------|----------|----------|
| Products | 50 | 15 Coffee + 15 Tea + 10 Smoothie + 10 Juice |
| Users | 31 | 1 admin + 30 users (4 clusters) |
| Orders | ~1500/tháng × 10 tháng | Lịch sử mua hàng |

### 7.2 Preference Clusters

```
Users 0-9  (alice..jack):   Coffee lovers  → 70% mua Coffee
Users 10-19 (kate..tina):   Tea lovers     → 70% mua Tea
Users 20-24 (uma..yuki):    Smoothie fans  → 70% mua Smoothie
Users 25-29 (zo..dung):     Juice fans     → 70% mua Juice

→ Tạo clusters rõ ràng để CF hoạt động tốt.
→ User trong cluster giống nhau → cosine similarity cao.
```

---

## 8. Admin AI Dashboard

**Endpoint:** `GET /admin/ai/dashboard`

Hiển thị:
1. **Summary cards:** Precision, Recall, F1, Hit Rate, MAP
2. **Baseline comparison:** Bảng + biểu đồ cột
3. **Ablation weights:** Thử 9 cặp trọng số CF/RB
4. **Ablation K:** Thử K=1..20
5. **Precision-Recall curve:** Biểu đồ đường
6. **Confusion Matrix:** Naive Bayes phân loại đúng/sai

---

## 9. Thứ tự code khi cần sửa

```
Muốn sửa thuật toán gợiý?  → RecommendationService.java
Muốn sửa chatbot?           → Đã xóa
Muốn thêm sản phẩm mẫu?    → DataSeeder.java → seedProducts()
Muốn thêm user mẫu?        → DataSeeder.java → seedUsers()
Muốn thay đổi đánh giá?    → RecommendationEvaluator.java
Muốn xem kết quả đánh giá?  → /admin/ai/dashboard
Muốn sửa giao diện đánh giá? → templates/admin/ai-dashboard.html
```
