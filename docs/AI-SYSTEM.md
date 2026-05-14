# Hệ thống AI - Hashiji Café

## Mục lục
1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Hệ thống gợi ý sản phẩm](#2-hệ-thống-gợi-ý-sản-phẩm)
3. [Các thuật toán AI sử dụng](#3-các-thuật-toán-ai-sử-dụng)
4. [Đánh giá hiệu suất](#4-đánh-giá-hiệu-suất)
5. [Baseline & Ablation Study](#5-baseline--ablation-study)
6. [Cài đặt và cấu hình](#6-cài-đặt-và-cấu-hình)

---

## 1. Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────┐
│                    Hashiji Café AI System                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │  Recommendation   │  │  Text Similarity  │  │ Naive Bayes  │  │
│  │  Engine           │  │  (TF-IDF + Cos)   │  │ Classifier   │  │
│  │  CF + Rule-based  │  │                   │  │              │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘  │
│           │                     │                    │          │
│  ┌────────┴─────────────────────┴────────────────────┴───────┐  │
│  │              Evaluation Engine                             │  │
│  │  • Leave-One-Out Cross-Validation                         │  │
│  │  • Precision, Recall, F1, Hit Rate, MAP                   │  │
│  │  • Baseline Comparison (Random, Popularity)               │  │
│  │  • Ablation Study (Weights, K)                            │  │
│  │  • Confusion Matrix (Naive Bayes)                         │  │
│  │  • Precision-Recall Curve, F1@K Curve                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ Synthetic Data    │  │ Baseline          │                    │
│  │ Generator         │  │ Recommender       │                    │
│  │ (100-500 users)   │  │ (Random, Popular) │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Cấu trúc thư mục

```
src/main/java/com/coffeeshop/
├── service/
│   ├── RecommendationService.java      # Hệ thống gợiý chính (CF + RB)
│   └── ai/
│       ├── TextSimilarityService.java   # TF-IDF + Cosine Similarity
│       ├── NaiveBayesClassifier.java    # Naive Bayes classifier
│       ├── RecommendationEvaluator.java # Đánh giá hiệu suất
│       ├── BaselineRecommender.java     # Baseline (Random, Popularity)
│       └── SyntheticDataGenerator.java  # Tạo dữ liệu tổng hợp
├── controller/
│   └── AdminAIController.java          # Admin AI Dashboard + API
```

---

## 2. Hệ thống gợi ý sản phẩm

### 2.1 Collaborative Filtering (CF) - Trọng số 0.6

**Nguyên lý:** Người dùng tương tự nhau sẽ thích sản phẩm tương tự.

```
Bước 1: Xây dựng ma trận User-Product từ đơn hàng已完成
         ┌─────────┬───────┬───────┬───────┬───────┐
         │         │ Prod1 │ Prod2 │ Prod3 │ Prod4 │
         ├─────────┼───────┼───────┼───────┼───────┤
         │ User A  │  1.0  │  0.5  │   0   │   0   │
         │ User B  │  0.8  │   0   │  0.3  │   0   │
         │ User C  │   0   │  0.7  │  0.6  │  1.0  │
         └─────────┴───────┴───────┴───────┴───────┘

Bước 2: Normalize (chia cho max → 0-1 scale)
Bước 3: Tính Cosine Similarity giữa các cặp user
Bước 4: KNN (K=5) - Tìm 5 user tương tự nhất
Bước 5: Dự đoán điểm cho sản phẩm chưa mua
```

### 2.2 Rule-based Recommendation (RB) - Trọng số 0.4

- **Cold Start:** User mới → sản phẩm bán chạy nhất
- **Returning User:** Tăng điểm cho sản phẩm mua thường xuyên

### 2.3 Kết hợp

```
finalScore = 0.6 × CF + 0.4 × RB
```

---

## 3. Các thuật toán AI sử dụng

### 3.1 Cosine Similarity

```
cos(θ) = (A · B) / (|A| × |B|)

Ứng dụng: So sánh user vectors, so sánh text vectors
Kết quả: 0.0 (完全不同) → 1.0 (完全相同)
```

### 3.2 TF-IDF (Term Frequency - Inverse Document Frequency)

```
TF(t, d) = số lần t xuất hiện trong d / tổng số từ trong d
IDF(t) = log(N / df(t)) + 1
TF-IDF(t, d) = TF(t, d) × IDF(t)

Ứng dụng: Tìm sản phẩm liên quan nhất đến query
```

### 3.3 Naive Bayes Classifier

```
P(Class | Features) = P(Features | Class) × P(Class) / P(Features)

Giả định "Naive": Các từ độc lập khi cho trước lớp
Laplace Smoothing: P(word|class) = (count + 1) / (total + |V|)
Log-probabilities để避免 underflow

Ứng dụng: Phân loại sản phẩm vào category
```

---

## 4. Đánh giá hiệu suất

### 4.1 Các chỉ số

| Chỉ số | Công thức | Ý nghĩa |
|--------|-----------|----------|
| **Precision@K** | \|recommended ∩ relevant\| / K | Trong K gợiý, bao nhiêu % đúng? |
| **Recall@K** | \|recommended ∩ relevant\| / \|relevant\| | Bao nhiêu % sản phẩm liên quan được gợiý? |
| **F1-Score** | 2 × (P × R) / (P + R) | Cân bằng Precision và Recall |
| **Hit Rate** | users with hits / total users | Bao nhiêu % user có ≥1 gợiý đúng? |
| **MAP@K** | mean(1/rank for each hit) | Độ chính xác có xét thứ tự |

### 4.2 Leave-One-Out Cross-Validation

```
Với mỗi user có ≥2 sản phẩm:
1. Ẩn 1 sản phẩm (test item)
2. Dùng phần còn lại để gợiý
3. Kiểm tra test item có trong top-K không
4. Tính P, R, F1, AP
5. Trung bình hóa trên tất cả user
```

### 4.3 Admin Dashboard

Truy cập: `GET /admin/ai/dashboard`

Hiển thị:
- Summary metrics (Precision, Recall, F1, Hit Rate, MAP)
- Baseline comparison (Hybrid vs CF-only vs RB-only vs Random vs Popularity)
- Ablation study: CF/RB weights (9 cặp trọng số)
- Ablation study: K sweep (K=1..20)
- Precision-Recall curve
- F1@K curve
- Naive Bayes confusion matrix + per-class metrics

---

## 5. Baseline & Ablation Study

### 5.1 Baselines

| Strategy | Mô tả | Mục đích |
|----------|-------|----------|
| **Random** | Gợiý ngẫu nhiên | Floor (tệ nhất) |
| **Popularity** | Gợiý bán chạy nhất | Simple baseline |
| **CF-Only** | Chỉ dùng CF | Đánh giá CF单独 |
| **RB-Only** | Chỉ dùng Rule-based | Đánh giá RB单独 |
| **Hybrid** | CF + RB kết hợp | Hệ thống hiện tại |

### 5.2 Ablation Study: CF/RB Weights

Thử 9 cặp trọng số: (0.9,0.1), (0.8,0.2), ..., (0.1,0.9)
→ Tìm optimal weights

### 5.3 Ablation Study: K Values

Thử K = 1, 2, 3, 4, 5, 6, 8, 10, 15, 20
→ Tìm optimal top-K

---

## 6. Cài đặt và cấu hình

### 6.1 Seed Data

- **50 sản phẩm:** 15 Coffee + 15 Tea + 10 Smoothie + 10 Juice
- **30 users:** 4 preference clusters (Coffee/Tea/Smoothie/Juice)
- **~15,000 đơn hàng:** 10 tháng lịch sử, 70% preferred + 30% random

### 6.2 Chạy đánh giá

```bash
# Start app với seed data
mvn spring-boot:run -Dspring-boot.run.arguments="--app.seed-data=true"

# Mở dashboard
# Login as admin → sidebar → AI Evaluation
# Hoặc truy cập trực tiếp: /admin/ai/dashboard
```

### 6.3 API Endpoints

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| `/admin/ai/dashboard` | GET | Dashboard HTML |
| `/admin/ai/api/evaluate` | GET | Evaluation metrics JSON |
| `/admin/ai/api/baselines` | GET | Baseline comparison JSON |
| `/admin/ai/api/ablation-weights` | GET | Weight sweep JSON |
| `/admin/ai/api/ablation-k` | GET | K sweep JSON |
| `/admin/ai/api/confusion-matrix` | GET | NB confusion matrix JSON |
| `/admin/ai/api/pr-curve` | GET | PR curve data JSON |
| `/admin/ai/api/f1-curve` | GET | F1@K curve data JSON |
| `/admin/ai/api/keywords` | GET | NB category keywords JSON |
