# 🧠 Hướng Dẫn Tốc Hành Về AI Trong Dự Án (AI Crash Course)
*Tài liệu này được viết theo ngôn ngữ đời thường, dễ hiểu, dành riêng cho bạn để nắm bắt trọn vẹn dự án AI này và tự tin bảo vệ trước giảng viên.*

---

## 1. Bức tranh tổng quan: AI của mình làm cái gì?

Thay vì một cửa hàng hiển thị danh sách sản phẩm nhàm chán ai cũng giống ai, dự án này được tích hợp **Hệ thống gợi ý (Recommendation System)** để "chiều chuộng" từng khách hàng.
 
Nó xuất hiện ở 2 nơi:
1. **Ngoài Trang Chủ (Personalized):** Khách hàng A sẽ thấy gợi ý khác khách hàng B (vì gu khác nhau). Nếu là khách chưa đăng nhập, tự động show Best Seller.
2. **Trong Giỏ Hàng (Cross-selling / Bán chéo):** Khách hàng vừa bấm thêm "Cà phê đen" vào giỏ, hệ thống liền nhắc "Bạn có muốn mua thêm Bánh sừng bò không?" dựa trên thói quen mua chung của những người đi trước.

Hệ thống của bạn là một hệ thống **Hybrid (Lai)**, kết hợp sự thông minh của cộng đồng và thói quen cá nhân.

---

## 2. Giải thích Thuật Toán bằng tiếng Việt "Bình dân"

Hệ thống Hybrid của bạn kết hợp 2 thuật toán chính:

### A. Lọc cộng tác dựa trên người dùng (User-based Collaborative Filtering) - Trọng số: 60%
**Tư tưởng:** "Chơi với bạn như thế nào thì mình như thế ấy".
- **Cách nó hoạt động:** 
  1. Hệ thống phân tích lịch sử mua hàng để tìm ra **5 người dùng khác** có "gu" uống cafe giống hệt bạn (trong thuật toán gọi là tìm K láng giềng gần nhất - **KNN**, với K=5).
  2. Để biết ai giống ai, nó áp dụng công thức toán học gọi là **Cosine Similarity**.
  3. Sau khi tìm được 5 người anh em cốt nhục đó, nó xem 5 người này dạo này hay uống cái gì mà bạn chưa uống, rồi lấy các món đó gợi ý cho bạn.

### B. Luật định sẵn (Rule-based) - Trọng số: 40%
**Tư tưởng:** AI cũng có lúc "bó tay", lúc đó phải dùng logic thực tế để gánh team.
- **Tình huống 1: Người dùng mới (Cold Start Problem):** Khách vừa đăng nhập xong, chưa từng uống ngụm nào. AI Lọc cộng tác bị mù tịt.
  👉 **Luật giải quyết:** Hiển thị luôn danh sách **Best Seller** (Sản phẩm bán chạy nhất lịch sử của quán). Đám đông thích thì khả năng cao người mới cũng sẽ thích.
- **Tình huống 2: Lịch sử cá nhân:** 
  👉 **Luật giải quyết:** Con người hay có thói quen uống đi uống lại một món ruột. Thuật toán này sẽ lục lại lịch sử, món nào người này từng mua nhiều nhất thì cộng thêm điểm để ưu tiên gợi ý lại món đó.

### C. Cơ chế lai (Hybrid) hoạt động ra sao?
Điểm của một món đồ uống được quyết định bởi cả 2 thuật toán:
**Điểm chung cuộc = 60% x Điểm (Lọc cộng tác) + 40% x Điểm (Luật định sẵn)**
Món nào điểm cao nhất sẽ chễm chệ nằm trên màn hình Trang chủ của khách hàng!

---

## 3. Bản đồ Code (Biết chính xác code nằm đâu để chỉ cho Thầy Cô)

Thầy cô rất hay hỏi: *"Em dùng thuật toán gì? Code ở chỗ nào chỉ cho tôi xem?"* 
Bạn hãy mở ngay file này lên:
📍 `src/main/java/com/coffeeshop/service/RecommendationService.java`

Đây là "Bộ não" của hệ thống. Hãy tự tin chỉ vào các hàm sau:

| Tên Hàm trong Code | Nằm ở đâu (Khoảng dòng)? | Chức năng (Nói với thầy cô như thế nào?) |
| --- | --- | --- |
| `getRecommendations(UUID)` | ~Dòng 78 | *"Dạ đây là hàm Tổng. Nó gọi cả hàm CF và hàm Rule-based, nhân trọng số 0.6 và 0.4 rồi chốt ra 6 món cuối cùng ạ."* |
| `getBestSellers()` | ~Dòng 134 | *"Hàm này query Database lấy danh sách bán chạy nhất lịch sử. Em dùng để hiển thị cho khách vãng lai chưa đăng nhập."* |
| `getCrossSellingRecommendations(...)` | ~Dòng 168 | *"Dạ hàm này là chức năng Cross-selling trong giỏ hàng. Nếu khách mua món A, em tìm những ai từng mua A xem họ có hay mua kèm B món B không."* |
| `computeCFScores(UUID)` | ~Dòng 220 | *"Đây là hàm Lọc cộng tác ạ. Nó tìm ra K láng giềng bằng Cosine Similarity, tính điểm dự đoán cho các sản phẩm khách chưa mua."* |
| `computeRuleBasedScores(UUID)` | ~Dòng 252 | *"Đây là thuật toán Base theo Rule. Em đếm số lượng lịch sử mua của user, món nào mua nhiều lần điểm sẽ cao ạ."* |
| `calculateCosineSimilarity(...)` | ~Dòng 296 | *"Công thức toán học tính khoảng cách Cosine giữa 2 vector mua hàng của 2 user ạ."* |

---

## 4. Cẩm nang Trả lời "Vấn đáp" (Bí kíp chống trượt)

Hãy học thuộc lòng các câu hỏi - đáp mồi này, nó bao phủ 90% các câu hỏi khó của hội đồng:

**🛑 Câu 1: Em xử lý vấn đề Cold-start (Người dùng mới tinh, chưa có dữ liệu) như thế nào?**
**✅ Trả lời:** *"Dạ, thuật toán Lọc cộng tác bắt buộc người dùng phải có lịch sử mới chạy được. Nên với user mới tạo tài khoản, hệ thống của em sẽ bắt điều kiện (if) và tự động rẽ nhánh sang cơ chế Rule-Based để hiển thị danh sách **Best Seller toàn thời gian** (Những món bán chạy nhất). Khi họ bắt đầu mua hàng, hệ thống mới từ từ chuyển sang tính toán Cá nhân hóa ạ."*

**🛑 Câu 2: Thuật toán KNN của em dùng K bằng bao nhiêu? Tại sao dùng độ đo Cosine?**
**✅ Trả lời:** *"Dạ em dùng K = 5 (tìm 5 láng giềng giống nhất). Em dùng Cosine Similarity vì độ đo này tính khoảng cách góc của 2 vector, nên nó không bị ảnh hưởng bởi việc khách hàng A mua 100 cốc còn khách hàng B mới mua 5 cốc, miễn là tỉ lệ sở thích giống nhau thì nó vẫn tìm ra được ạ."*

**🛑 Câu 3: Tại sao em lại chọn tỉ lệ 60% cho Lọc cộng tác và 40% cho Rule-based?**
**✅ Trả lời:** *"Dạ vì em muốn ưu tiên thuật toán Lọc cộng tác (60%) để giúp khách hàng khám phá ra các đồ uống mới mẻ dựa trên xu hướng cộng đồng. Tuy nhiên, em vẫn giữ 40% Rule-based để duy trì thói quen mua đồ uống "ruột" của họ. Theo em đây là tỉ lệ phù hợp cho một quán Cafe ạ."*

**🛑 Câu 4: AI của em cứ mỗi lần khách F5 trang chủ lại phải tính ma trận lại từ đầu à? Thế thì sập server mất?**
**✅ Trả lời:** *"Dạ không ạ! Em đã lường trước điều này nên áp dụng cơ chế **Caching** (bộ nhớ đệm) bằng công nghệ `@Cacheable` của Spring Boot. Ma trận đánh giá chỉ tính toán lại sau 30 phút, hoặc khi có đơn hàng mới được đặt. Còn bình thường, tốc độ load trang chủ chưa tới 50 mili-giây ạ!"*

**🛑 Câu 5: Hệ thống hiển thị Top Selling Items ở Admin Dashboard khác gì với Gợi ý cho người dùng không?**
**✅ Trả lời:** *"Dạ không khác ạ. Trước đây Admin có phân theo tháng, nhưng nhóm em đã chuẩn hóa lại để cả Admin Dashboard và phần Best Seller ngoài trang chủ đều dùng chung 1 logic là **Top Selling All-Time (Bán chạy nhất lịch sử)**. Việc này đảm bảo tính nhất quán dữ liệu từ Backend ra Frontend ạ."*
