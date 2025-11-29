# Script Demo Project: eKyc_Sim_BankingApp (ZKP Authentication)

**Project Name:** eKyc_Sim_BankingApp (WannaW1n Banking)
**Topic:** Giải pháp định danh điện tử (eKYC) bảo mật quyền riêng tư sử dụng Zero-Knowledge Proofs (ZKP).
**Duration:** 15-20 minutes
**Presenter:** [Tên thành viên]
**Language:** Tiếng Việt

---

## 1. Giới thiệu & Đặt vấn đề (Introduction & Problem Statement) - 0:00 to 3:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **0:00-0:30** | Slide: Tiêu đề Project, Logo "Data For Life 2025". <br> Chủ đề: **"eKYC cho hệ thống ngân hàng tích hợp AI và ZKP"**. | "Xin chào Ban giám khảo và các bạn. Chúng tôi là đội WannaW1n. Hôm nay, chúng tôi mang đến giải pháp eKYC thế hệ mới tích hợp AI và Zero-Knowledge Proofs." | Giọng đọc rõ ràng, tự tin. |
| **0:30-1:15** | Slide: Thách thức Công nghệ & Bảo mật. <br> - Video Deepfake minh họa. <br> - Ảnh CCCD giả. | "Hiện nay, các hệ thống ngân hàng đang đối mặt với những thách thức chưa từng có. <br> Về công nghệ, sự bùng nổ của AI và Deepfake đã vô hiệu hóa các phương pháp xác thực 'Liveness' cổ điển như chớp mắt hay quay đầu. Kẻ gian dễ dàng tạo ra các tài khoản rác để rửa tiền. <br> Bên cạnh đó, công nghệ làm giả giấy tờ ngày càng tinh vi khiến OCR truyền thống dễ bị qua mặt, dẫn đến dữ liệu đầu vào sai lệch." | Nhấn mạnh Deepfake & Spoofing. |
| **1:15-1:45** | Slide: Thách thức Pháp lý & Hạ tầng. <br> - Icon Nghị định 13/2023. <br> - Icon Mạng yếu/Thiết bị cũ. | "Về pháp lý, Nghị định 13 đặt ra áp lực tuân thủ cực lớn về bảo vệ dữ liệu nhạy cảm, khiến doanh nghiệp lúng túng trong cơ chế lưu trữ và xin chấp thuận. <br> Về hạ tầng, kết nối mạng kém ở vùng sâu vùng xa và thiết bị người dùng đời cũ cũng là rào cản lớn cho các giải pháp eKYC đòi hỏi băng thông cao." | |
| **1:45-2:30** | Slide: Vấn đề Người dùng. <br> - Người lớn tuổi gặp khó khăn. <br> - Biểu đồ lo ngại quyền riêng tư. | "Cuối cùng là rào cản từ phía người dùng. Người lớn tuổi thường gặp khó khăn với các thao tác phức tạp, dẫn đến tỷ lệ bỏ ngang cao. <br> Đặc biệt, sau nhiều vụ lộ lọt dữ liệu, khách hàng ngày càng lo sợ khi phải gửi ảnh khuôn mặt hay CCCD lên server. <br><br> Chính vì vậy, chúng tôi đề xuất giải pháp **WannaW1n eKYC** - Giải quyết bài toán niềm tin bằng công nghệ **Zero-Knowledge Proofs**." | Chốt vấn đề -> Chuyển sang giải pháp. |

## 2. Dữ liệu đặc trưng (Data Characteristics) - 3:00 to 5:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **3:00-3:30** | Slide: Nguồn & Loại dữ liệu. <br> - Nguồn: Camera (OCR, Face). <br> - Loại: Unstructured (Image - xử lý tại edge), Structured (ZKP Proofs). | "Về dữ liệu, hệ thống của chúng tôi xử lý hai luồng chính. <br> Một là dữ liệu phi cấu trúc gồm hình ảnh giấy tờ và video khuôn mặt, được xử lý **ngay tại thiết bị (Edge Computing)** để trích xuất thông tin. <br> Hai là dữ liệu có cấu trúc dạng JSON chứa các bằng chứng mật mã được gửi lên server." | |
| **3:30-4:30** | Slide: Bảng cấu trúc dữ liệu cốt lõi (Core Fields). <br> (Hiển thị bảng bên dưới) | "Đây là cấu trúc dữ liệu cốt lõi được lưu trữ trên Server. Thay vì lưu mật khẩu hay ảnh, chúng tôi lưu trữ các tham số mật mã học:" | Show bảng data. |

### Bảng dữ liệu cốt lõi (Core Data Fields)

| Tên trường (Field Name) | Kiểu dữ liệu (Data Type) | Mô tả (Description) |
| :--- | :--- | :--- |
| `id` | Integer | Khóa chính tự tăng. |
| `public_key` | String (Hex) | Khóa công khai của người dùng (tương đương tên đăng nhập). |
| `commitment` | String (Hex) | Cam kết mật mã, liên kết Public Key với ID thật mà không lộ ID. |
| `id_hash` | String (SHA256) | Mã băm của số CCCD/CMND (dùng để đối soát duy nhất). |
| `encrypted_pii` | String (Base64) | Thông tin cá nhân (Tên, ngày sinh) được mã hóa bằng khóa riêng (Server không đọc được). |
| `enrollment_proof` | JSON | Bằng chứng ZKP (Schnorr) chứng minh người dùng sở hữu Private Key. |

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **4:30-5:00** | Slide: Quy mô & Đặc trưng. <br> - Quy mô: 1 triệu user ~ 2GB data (rất nhẹ). <br> - Tính chất: Static (Identity) & Append-only (Logs). | "Với cấu trúc này, dữ liệu cực kỳ gọn nhẹ. 1 triệu người dùng chỉ chiếm khoảng 2GB dung lượng lưu trữ. Dữ liệu có tính chất tĩnh (Static) đối với hồ sơ và chỉ thêm mới (Append-only) đối với nhật ký xác thực, giúp tối ưu hóa hiệu năng truy xuất." | |

## 3. Demo Tính năng & Quy trình nghiệp vụ (Core Features) - 5:00 to 15:00

### Use Case 1: Đăng ký tài khoản (Enrollment) - 5:00 to 9:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **5:00-5:10** | **[Tình huống]**: Người dùng mới tải app và muốn mở tài khoản ngân hàng từ xa. | "Chúng ta hãy đến với Use Case đầu tiên: Một người dùng mới muốn mở tài khoản ngân hàng từ xa." | |
| **5:10-5:40** | **[UI/UX]**: <br> 1. Mở App -> Chọn "Đăng ký". <br> 2. Quét mặt trước/sau CCCD. <br> 3. Hiển thị thông tin OCR trích xuất. | "Trên giao diện ứng dụng, người dùng bắt đầu quy trình eKYC tiêu chuẩn. <br> Hệ thống sử dụng công nghệ OCR để tự động trích xuất thông tin từ giấy tờ tùy thân, giúp giảm thiểu sai sót khi nhập liệu thủ công." | Thao tác chậm, rõ ràng. |
| **5:40-6:40** | **[UI/UX - Liveness Check]**: <br> 4. Chuyển sang màn hình "Face Liveness". <br> 5. App hiển thị khung tròn, yêu cầu người dùng nhìn thẳng/quay trái/phải. <br> 6. Thông báo "Liveness Verified". | "Tiếp theo là bước xác thực người thật. Hệ thống sử dụng **Google ML Kit** để phân tích hình học khuôn mặt và các góc quay (Head Pose). <br> Việc kiểm tra này đảm bảo người dùng đang tương tác trực tiếp với thiết bị, ngăn chặn các hành vi sử dụng ảnh tĩnh để giả mạo." | Nhấn mạnh ML Kit & On-device. |
| **6:40-7:30** | **[System Logic]**: <br> *Đồ họa mô phỏng luồng dữ liệu*: <br> - App sinh cặp khóa (PrivK, PubK). <br> - PrivK lưu vào Keystore. <br> - App tạo Proof = Schnorr(PrivK, Message). <br> - App gửi {PubK, Proof, EncryptedPII} -> Server. | "Sau khi xác thực khuôn mặt thành công, ứng dụng mới tiến hành sinh khóa ZKP. <br> **Private Key** được lưu cứng vào chip bảo mật của điện thoại (Keystore). Chỉ có **Public Key** và bằng chứng ZKP được gửi lên server. Dữ liệu khuôn mặt đã bị hủy ngay sau khi xác thực xong." | |
| **7:30-8:30** | **[Result]**: <br> - App báo "Đăng ký thành công". <br> - Chuyển sang màn hình Dashboard. <br> - *Split Screen*: Show Database (Admin View). | "Kết quả: Tài khoản được tạo thành công. Server chỉ lưu trữ các bằng chứng mật mã, hoàn toàn không có dữ liệu nhạy cảm." | |
| **8:30-9:00** | Tóm tắt Use Case 1. | "Quy trình đăng ký đảm bảo: Người thật (Liveness Check) và Danh tính thật (bảo mật ZKP)." | |

### Use Case 2: Đăng nhập & Xác thực (Authentication) - 9:00 to 12:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **9:00-9:10** | **[Tình huống]**: Người dùng quay lại app để thực hiện giao dịch. | "Use Case thứ hai: Đăng nhập để thực hiện giao dịch." | |
| **9:10-9:40** | **[UI/UX]**: <br> 1. Mở App -> Nhấn "Đăng nhập". <br> 2. Biometric Prompt hiện lên. <br> 3. *Hiệu ứng*: Quét nhanh. <br> 4. Vào Home ngay lập tức. | "Người dùng không cần nhớ mật khẩu. Chỉ cần một chạm vân tay hoặc quét khuôn mặt. <br> Hệ thống xác thực người thật trong chưa đầy 1 giây mà không yêu cầu người dùng phải tương tác phức tạp." | Nhấn mạnh tốc độ & tiện lợi. |
| **9:40-10:40** | **[System Logic]**: <br> *Đồ họa mô phỏng*: <br> - Server gửi `Challenge` (Random nonce). <br> - App dùng PrivK ký `Challenge` -> tạo `Proof`. <br> - App gửi `Proof` -> Server. <br> - Server verify `Proof` với `PubK`. | "Bên dưới giao diện đơn giản đó là một giao thức bảo mật phức tạp. <br> Server gửi một thách thức ngẫu nhiên. Ứng dụng sử dụng Private Key (vừa được mở khóa bằng sinh trắc học) để giải bài toán đó và gửi kết quả lại cho Server. <br> Server kiểm tra kết quả và xác nhận danh tính mà không cần biết Private Key." | |
| **10:40-11:30** | **[Result]**: <br> - *Split Screen*: Terminal Server log. <br> - Log hiện: `Verify Success`, `Nullifier Check OK`. | "Hệ thống cũng tích hợp cơ chế chống tấn công phát lại (Replay Attack) thông qua **Nullifier**. Mỗi phiên đăng nhập là duy nhất. Kể cả khi hacker bắt được gói tin đăng nhập này, họ cũng không thể sử dụng lại nó cho lần sau." | |
| **11:30-12:00** | Tóm tắt Use Case 2. | "Đăng nhập không mật khẩu - Loại bỏ hoàn toàn nguy cơ Phishing và Keylogger." | |

### Use Case 3: Truy vấn Chính phủ (Government Query) - 12:00 to 14:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **12:00-12:15** | **[Tình huống]**: Cơ quan chức năng cần kiểm tra xem đối tượng A có tài khoản tại ngân hàng không (phục vụ điều tra). | "Use Case thứ ba: Một tính năng đặc biệt dành cho quản lý nhà nước - Truy vấn ẩn danh." | |
| **12:15-12:45** | **[UI/UX]**: <br> - Giao diện dòng lệnh (hoặc Web Admin giả lập) của Cảnh sát. <br> - Nhập số CCCD của đối tượng. | "Giả sử cơ quan chức năng cần biết công dân A có tài khoản tại ngân hàng này hay không. Họ sẽ nhập số định danh của công dân đó vào hệ thống." | |
| **12:45-13:30** | **[System Logic]**: <br> - Input: ID Number. <br> - Process: Hash(ID) -> Query DB(id_hash). <br> - Output: Found/Not Found. | "Hệ thống sẽ băm (Hash) số định danh này và so khớp với trường `id_hash` trong cơ sở dữ liệu. <br> Vì `id_hash` là hàm băm một chiều, ngân hàng không thể dịch ngược ra số ID gốc của các khách hàng khác, nhưng vẫn có thể trả lời câu hỏi 'Có hay Không' cho cơ quan chức năng." | Giải thích Privacy-Preserving Linkage. |
| **13:30-14:00** | **[Result]**: <br> - Kết quả trả về: `FOUND` (Có tài khoản) hoặc `NOT FOUND`. | "Kết quả trả về chính xác tức thì, hỗ trợ công tác quản lý mà vẫn tôn trọng cam kết bảo mật thông tin khách hàng." | |

## 4. Kiến trúc & Khả năng mở rộng (Architecture) - 14:00 to 15:30

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **14:00-14:45** | Slide: Kiến trúc hệ thống (System Architecture). <br> - **Mobile App**: ML Kit (Face) + ZKP (Schnorr). <br> - **Server**: ZKP Verifier + Database. | "Kiến trúc hệ thống được tối ưu hóa cho bảo mật và hiệu năng: <br> **Mobile App**: Sử dụng Google ML Kit để xử lý hình ảnh khuôn mặt ngay trên thiết bị, đảm bảo tính riêng tư. <br> **Server**: Chỉ thực hiện xác thực ZKP gọn nhẹ, không lưu trữ dữ liệu sinh trắc học." | |
| **14:45-15:30** | Slide: Khả năng mở rộng (Scalability). <br> - Stateless Server -> Dễ dàng Scale-out. <br> - Database Sharding theo `id_hash`. | "Do đặc thù xác thực ZKP là Stateless (không lưu trạng thái phiên phức tạp), các node server có thể dễ dàng mở rộng ngang (Scale-out) để phục vụ hàng triệu yêu cầu đồng thời. Database có thể được phân mảnh (Sharding) dựa trên `id_hash` để tối ưu tốc độ truy vấn." | |

## 5. Tổng kết (Conclusion) - 15:30 to 16:00

| Thời gian | Hình ảnh (Visual) | Lời bình (Voiceover) | Ghi chú (Notes) |
| :--- | :--- | :--- | :--- |
| **15:30-16:00** | Slide: Tổng kết & Tagline. <br> Tagline: **"WannaW1n - Bảo mật từ gốc, An tâm giao dịch"**. | "Tổng kết lại, WannaW1n eKYC không chỉ là một ứng dụng, mà là một tiêu chuẩn bảo mật mới. Chúng tôi tin rằng quyền riêng tư không phải là tính năng thêm vào, mà là nền tảng cốt lõi của mọi dịch vụ tài chính số. <br> Xin cảm ơn." | Kết thúc dứt khoát. |

---

## Yêu cầu chuẩn bị (Checklist)

1.  **Thiết bị**: Android Phone, Laptop.
2.  **Môi trường**:
    *   Backend chạy Docker/Localhost.
    *   Database sạch (Reset DB).
3.  **Dữ liệu test**:
    *   1 CCCD thật (để OCR).
    *   1 Kịch bản ID cần tra cứu cho phần Government Query.
4.  **Lưu ý quay dựng**:
    *   **KHÔNG chèn nhạc nền**.
    *   Giọng đọc phải là giọng thật của thành viên nhóm.
    *   Quay màn hình rõ nét (1080p).
