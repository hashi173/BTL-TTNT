# Câu hỏi bảo vệ - Hệ thống AI Hashiji Café

> Tổng hợp câu hỏi hội đồng có thể hỏi, kèm câu trả lời gợi ý. Sắp xếp theo 6 nhóm chủ đề.

---

## Nhóm 1: Tổng quan hệ thống

### Q1: Hệ thống AI của bạn gồm những thành phần nào? Mỗi thành phần làm gì?

**Trả lời:**
Hệ thống gồm 5 thành phần chính:

1. **RecommendationService** — Gợi ý sản phẩm cho user dựa trên Collaborative Filtering (Cosine Similarity + KNN) kết hợp Rule-based (best sellers + purchase frequency). Trọng số CF=0.6, RB=0.4.

2. **NaiveBayesClassifier** — Phân loại sản phẩm vào category (Coffee/Tea/Smoothie/Juice) từ văn bản đầu vào. Sử dụng Bayes theorem với Laplace smoothing.

3. **TextSimilarityService** — So sánh văn bản sử dụng TF-IDF và Cosine Similarity. Dùng để tìm sản phẩm liên quan nhất đến query.

4. **RecommendationEvaluator** — Đánh giá hiệu suất bằng Leave-One-Out cross-validation với 5 chỉ số: Precision, Recall, F1, Hit Rate, MAP. Có ablation study và baseline comparison.

5. **BaselineRecommender** — Tạo baseline (Random, Popularity) để so sánh với hệ thống hybrid.

---

### Q2: Tại sao bạn chọn hybrid approach (CF + Rule-based) mà không dùng một approach duy nhất?

**Trả lời:**
Vì mỗi approach có điểm mạnh riêng:

- **CF** giỏi nắm bắt sở thích cá nhân (user tương tự → gợi ý tương tự), nhưng yếu với user mới (cold start).
- **Rule-based** giỏi xử lý cold start (best sellers cho user mới) và sản phẩm phổ biến, nhưng không cá nhân hóa.

Kết hợp cả hai → **"best of both worlds"**: CF đảm bảo cá nhân hóa, RB đảm bảo cold start. Kết quả ablation study cho thấy Hybrid (F1=0.25) tốt hơn CF-only (0.22) và RB-only (0.18).

---

### Q3: Tại sao trọng số là 0.6 CF và 0.4 RB? Không phải 0.5/0.5 hay 0.7/0.3?

**Trả lời:**
Đây là hyperparameter, được chọn thông qua ablation study (grid search). Chúng tôi thử 9 cặp trọng số từ (0.9,0.1) đến (0.1,0.9) và đo F1-score.

Kết quả cho thấy F1 cao nhất ở CF=0.6, RB=0.4 (hoặc CF=0.7, RB=0.3 — xấp xỉ). Lý do: CF đóng góp nhiều hơn vì cá nhân hóa là yếu tố chính trong recommendation, nhưng RB vẫn cần thiết cho cold start và sản phẩm phổ biến.

---

### Q4: Nếu tăng lên 1000 users, hệ thống có scale được không?

**Trả lời:**
Có giới hạn. Cosine similarity tính pairwise → O(n²) với n users. Với 30 users OK, nhưng 1000 users → 500,000 cặp → chậm.

Giải pháp (chưa implement nhưng có thể mở rộng):
1. **Dimensionality reduction** (PCA/SVD) giảm chiều vector
2. **Locality-Sensitive Hashing (LSH)** approximate nearest neighbors
3. **Matrix Factorization** (ALS, SVD++) thay vì memory-based CF

Hiện tại dùng caching (ma trận cache 30 phút) để giảm tải.

---

## Nhóm 2: Collaborative Filtering

### Q5: Cosine Similarity hoạt động như thế nào? Tại sao chọn Cosine mà không dùng Euclidean Distance?

**Trả lời:**
Cosine Similarity đo **góc** giữa 2 vector, không đo **khoảng cách**:

```
cos(A,B) = (A · B) / (|A| × |B|)
```

Tại sao chọn Cosine:
- **Không bị ảnh hưởng bởi magnitude**: User A mua 100 ly cafe, User B mua 10 ly → Euclidean nói họ khác nhau, nhưng Cosine nói họ giống nhau (cùng thích cafe).
- **Phù hợp với sparse vectors**: Ma trận user-product rất thưa (nhiều ô = 0), Cosine xử lý tốt.
- **Output chuẩn hóa**: 0-1, dễ interpret.

Euclidean bị ảnh hưởng bởi scale → không phù hợp khi số lượng mua khác nhau giữa các user.

---

### Q6: KNN với K=5 hoạt động như thế nào? Tại sao chọn K=5?

**Trả lời:**
KNN (K-Nearest Neighbors) tìm 5 user tương tự nhất:

1. Tính cosine similarity giữa user A với tất cả user khác
2. Sắp xếp giảm dần, lấy top 5
3. Với mỗi sản phẩm user A chưa mua:
   ```
   score(product) = Σ(similarity × neighbor_rating) / Σ|similarity|
   ```
4. Trả về top 6 sản phẩm có score cao nhất

Tại sao K=5:
- Qua ablation study (K=1..20), F1 cao nhất ở K=5-6.
- K quá nhỏ (1-2): noise cao, không đủ data.
- K quá lớn (15-20): bao gồm user khôngtương tự → giảm chất lượng.
- Với 30 users, K=5 nghĩa là lấy ~17% users → hợp lý.

---

### Q7: Ma trận user-product được xây dựng như thế nào? Xử lý sparse matrix ra sao?

**Trả lời:**
1. Lấy tất cả order items đã hoàn thành từ database
2. Với mỗi (userId, productId), cộng dồn quantity
3. Normalize: chia mỗi user vector cho max value → scale 0-1

Sparse matrix: phần lớn ô = 0 (user chỉ mua vài sản phẩm trong 50 sản phẩm). Xử lý:
- Dùng `Map<UUID, Map<UUID, Double>>` (thưa, chỉ lưu ô ≠ 0)
- Cosine similarity chỉ tính trên key chung (intersection) → không nhân với 0

---

### Q8: Cold start problem được giải quyết như thế nào?

**Trả lời:**
User mới chưa có lịch sử mua hàng → CF không hoạt động (không có data).

Giải pháp: **Rule-based fallback**
- isNewUser = true → trả về best sellers (sản phẩm bán chạy nhất)
- Best sellers = sản phẩm có tổng số lượng bán cao nhất trong tất cả đơn hàng

Đây là reason tại sao RB weight = 0.4 (không phải 0) — RB đóng vai trò quan trọng cho cold start.

---

## Nhóm 3: Naive Bayes

### Q9: Naive Bayes có giả định gì? Giả định đó có đúng trong bài toán này không?

**Trả lời:**
Giả định "Naive": **Các từ độc lập với nhau khi cho trước lớp**.

```
P("cà phê sữa" | Coffee) = P("cà"|Coffee) × P("phê"|Coffee) × P("sữa"|Coffee)
```

Trong thực tế, giả định này **không đúng hoàn toàn** — "cà" và "phê" luôn xuất hiện cùng nhau, không độc lập.

Tuy nhiên, Naive Bayes vẫn hoạt động tốt trong practice vì:
1. Classification chỉ cần **argmax** (so sánh tương đối), không cần xác suấttuyệt đối đúng
2. Sai lệch của các từ bù trừ nhau
3. Với dataset nhỏ (50 sản phẩm), NB đơn giản hơn các model phức tạp

---

### Q10: Laplace Smoothing là gì? Tại sao cần nó?

**Trả lời:**
Laplace Smoothing (α=1) tránh xác suất = 0:

```
Không smoothing: P("matcha"|Coffee) = 0/100 = 0
→ Toàn bộ tích = 0 → Bayes theorem không dùng được

Có smoothing:   P("matcha"|Coffee) = (0+1)/(100+50) = 0.0067
→ Xác suất nhỏ nhưng ≠ 0 → Bayes theorem hoạt động
```

Tại sao α=1: Đây là giá trị mặc định, hoạt động tốt với vocabulary nhỏ. α lớn hơn → xác suất mượt hơn nhưng kém chính xác.

---

### Q11: Tại sao dùng log-probabilities thay vì xác suất thường?

**Trả lời:**
Vì **underflow số học**:

```
P(Coffee) = P(w1|Coffee) × P(w2|Coffee) × ... × P(wn|Coffee)

Với n=10 từ, mỗi P ≈ 0.01:
  0.01^10 = 10^(-20) → quá nhỏ, máy tính làm tròn thành 0

Dùng log:
  log P = log P(w1) + log P(w2) + ... + log P(wn)
  = (-4.6) + (-4.6) + ... = -46  → cộng, không nhân → không underflow
```

---

### Q12: Confusion matrix cho thấy category nào bị nhầm nhiều nhất? Tại sao?

**Trả lời:**
Thường bị nhầm nhiều nhất: **Coffee ↔ Tea** (vì có 1 số sản phẩm lai như "Matcha Espresso" chứa từ của cả 2 category).

Hoặc **Smoothie ↔ Juice** (vì cả 2 đều là đồ uống trái cây).

Lý do: Naive Bayes dựa trên từ, không dựa trên ngữ cảnh. Sản phẩm có tên chứa từ từ 2 category → NB bị confused.

---

## Nhóm 4: Đánh giá

### Q13: Precision, Recall, F1 khác gì nhau? Tại sao cần cả 3?

**Trả lời:**
```
Precision = "Trong K gợi ý, bao nhiêu % đúng?"
  → Cao = ít gợi ý sai, user không bị làm phiền

Recall = "Trong tất cả sản phẩm liên quan, bao nhiêu % được gợi ý?"
  → Cao = không bỏ sót, user thấy đủ lựa chọn

F1 = 2 × P × R / (P + R)
  → Cân bằng P và R
```

Tại sao cần cả 3:
- Precision cao, Recall thấp → gợi ý chính xác nhưng bỏ sót nhiều
- Recall cao, Precision thấp → không bỏ sót nhưng gợi ý quá nhiều "rác"
- F1 = harmonic mean → penalize nếu 1 trong 2 thấp

---

### Q14: Hit Rate khác Precision như thế nào?

**Trả lời:**
```
Precision@K = (số gợi ý đúng) / K
  → User có 2 gợi ý đúng trong 6 → Precision = 2/6 = 0.33

Hit Rate = (số user có ≥1 gợi ý đúng) / (tổng user)
  → 9/20 user có ít nhất 1 gợi ý đúng → Hit Rate = 0.45
```

Hit Rate quan trọng hơn trong thực tế: user chỉ cần **1 gợi ý đúng** là hài lòng, không cần tất cả 6 đều đúng.

---

### Q15: MAP@K khác Precision@K như thế nào?

**Trả lời:**
MAP có xét **thứ tự** gợi ý:

```
User A: gợi ý đúng ở vị trí 1 → AP = 1/1 = 1.0
User B: gợi ý đúng ở vị trí 6 → AP = 1/6 = 0.17

Cùng 1 gợi ý đúng, nhưng MAP đánh giá User A cao hơn
vì gợi ý đúng ngay đầu → trải nghiệm người dùng tốt hơn
```

---

### Q16: Leave-One-Out hoạt động như thế nào? Tại sao dùng LOO mà không dùng k-fold?

**Trả lời:**
LOO:
1. Chọn 1 sản phẩm user đã mua làm test item
2. Ẩn test item, dùng phần còn lại để gợi ý
3. Kiểm tra test item có trong top-K không
4. Lặp cho tất cả user

Tại sao LOO thay vì k-fold:
- Dataset nhỏ (30 users) → k-fold mỗi fold chỉ 6 users → không đủ data train
- LOO dùng tối đa data cho training (n-1 users)
- LOO phù hợp với evaluation cho recommendation systems

---

### Q17: Kết quả evaluation có ý nghĩa thống kê không với 30 users?

**Trả lời:**
Thành thật mà nói, 30 users là nhỏ cho statistical significance. Tuy nhiên:

1. **Relative comparison vẫncó ý nghĩa**: Hybrid > Popularity > Random dù dataset nhỏ
2. **Synthetic data**: Có thể tạo 100-500 users đểkiểm chứng kết quả
3. **Đồ án đại học**: Focus vào thuật toán và implementation, không phải production-grade evaluation
4. **Kết quả reproducible**: Dùng seed cố định (Random(42), Random(20260501L)) → kết quảnhất quán khi chạy lại

---

## Nhóm 5: Thiết kế hệ thống

### Q18: Tại sao dùng Spring Cache mà không dùng Redis?

**Trả lời:**
Hiện tại dùng `spring.cache.type=simple` (in-memory cache) vì:
1. Đơn giản, không cần cài thêm Redis
2. Đủ cho đồ án đại học (low traffic)
3. Ma trận cache 30 phút → giảm tải tính toán

Nếu cần scale: có thể chuyển sang Redis (đã có spring-boot-starter-data-redis trong dependencies).

---

### Q19: Ma trận user-product được cache như thế nào? Cache invalidation ra sao?

**Trả lời:**
```
Cache strategy:
1. Ma trận user-product: volatile field, TTL 30 phút
   - ensureMatricesBuilt() kiểm tra TTL
   - Nếu hết hạn → rebuild từ database

2. Kết quả gợi ý: @Cacheable("recommendations")
   - Key = userId
   - Mỗi user có cache riêng

3. Cache eviction khi đặt hàng:
   - OrderService.placeOrder() gọi evictCacheForUser(userId)
   - @CacheEvict xóa cache của user đó
   - matrixLastBuilt = 0 → lần sau sẽ rebuild ma trận
```

---

### Q20: Tại sao normalize user vector bằng max value mà không dùng z-score?

**Trả lời:**
```
Normalize bằng max: v' = v / max(v)
  → Scale về 0-1, giữ nguyên distribution shape
  → Đơn giản, không cần tính mean/std

Z-score: v' = (v - mean) / std
  → Có thể ra giá trị âm
  → Không phù hợp với cosine similarity (âm → cosine < 0)
```

Normalize bằng max phù hợp với recommendation vì:
- Giữ nguyên thứ tự (mua nhiều nhất = 1.0)
- Không có giá trị âm
- Đơn giản, hiệu quả

---

### Q21: Tại sao dùng UUID cho productId và userId?

**Trả lời:**
1. **Unique globally**: Không cần auto-increment, an toàn khi merge data
2. **Security**: Không đoán được (không thể thử user 1, 2, 3...)
3. **Distributed-safe**: Nếu sau này cần shard database → UUID OK
4. **Spring Data JPA**: Hỗ trợ UUID native, không cần custom converter

---

## Nhóm 6: Đánh giá tổng thể

### Q22: Nếu được làm lại, bạn sẽ thay đổi gì?

**Trả lời:**
1. **Dùng Matrix Factorization** (SVD/ALS) thay vì memory-based CF → scale tốt hơn
2. **Thêm content-based features** (giá, mô tả, hình ảnh) vào hybrid
3. **Dùng dataset công khai** (MovieLens, Amazon Reviews) để benchmark
4. **A/B testing** thay vì offline evaluation
5. **Deep learning** (Neural Collaborative Filtering) nếu dataset lớn hơn

---

### Q23: Điểm yếu lớn nhất của hệ thống là gì?

**Trả lời:**
**Dataset nhỏ và synthetic**. 30 users với preference rõ ràng là ideal case cho CF. Trong thực tế:
- Users không có cluster rõ ràng → cosine similarity thấp hơn
- Data sparse hơn (ít người mua hàng)
- Metrics sẽ thấp hơn

Tuy nhiên, thuật toán đúng → khi có đủ data sẽ hoạt động tốt.

---

### Q24: Tại sao không dùng deep learning (Neural CF, Transformer)?

**Trả lời:**
1. **Dataset quá nhỏ**: Deep learning cần hàng nghìn users, hàng chục nghìn interactions
2. **Overfitting**: 30 users + 50 products → model sẽ memorize thay vì generalize
3. **Complexity**: Thêm complexity không thêm accuracy với dataset nhỏ
4. **Interpretability**: CF + NB dễ giải thích hơn neural network (black-box)
5. **Đồ án đại học**: Focus vào hiểu thuật toán cơ bản, không phải SOTA

---

### Q25: Hệ thống này có thể ứng dụng thực tế không?

**Trả lời:**
Có thể, nhưng cần cải thiện:
1. **Tăng data**: Thu thập data thật từ users
2. **Scale**: Dùng Matrix Factorization thay vì memory-based CF
3. **Real-time**: Dùng event-driven (Kafka) thay vì batch processing
4. **A/B testing**: Đo lường impact thực tế
5. **Monitoring**: Thêm logging, metrics, alerting

Hiện tại: phù hợp cho đồ án đại học, prototype, hoặc quán cafe nhỏ (<100 users).

---

### Q26: Bạn học được gì từ dự án này?

**Trả lời:**
1. **Hybrid approach** luôn tốt hơn từng approach riêng trong recommendation
2. **Evaluation quan trọng hơn algorithm**: Không có baseline → không biết algorithm tốt bao nhiêu
3. **Dataset size matters**: CF cần đủ data, NB cần đủ training samples
4. **Caching là cần thiết**: Ma trận similarity tính chậm → cache 30 phút
5. **Code clean + documentation**: Dễ maintain, dễ explain khi bảo vệ
