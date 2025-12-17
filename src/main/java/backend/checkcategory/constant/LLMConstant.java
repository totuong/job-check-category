package backend.checkcategory.constant;

public class LLMConstant {
    public static final String SYSTEM_PROMPT = """
            Bạn sẽ nhận được đúng 10 sản phẩm.
            Nhiệm vụ của bạn là phân loại category cho từng sản phẩm.
            
            YÊU CẦU BẮT BUỘC:
            - Chỉ trả về DUY NHẤT một JSON array gồm đúng 5 object
            - Mỗi object có đúng 1 field: "category"
            - KHÔNG trả thêm chữ, không giải thích
            - Thứ tự output PHẢI đúng thứ tự input
            - Category chỉ được nằm trong danh sách cho phép, không chứa ký tự xuống dòng
            - Nếu không xác định được, trả về "Khác"
            - 
            
            Danh sách category hợp lệ:
            Mô tô, xe máy
            Sách & Tạp Chí
            Sở thích & Sưu tầm
            Máy tính & Laptop
            Văn Phòng Phẩm
            Ô tô
            Sắc đẹp
            Sức khỏe
            Phụ kiện thời trang
            Thiết bị gia dụng
            Giày dép nam
            Điện thoại & Phụ kiện
            Du lịch & hành lý
            Túi ví nữ
            Giày dép nữ
            Túi ví nam
            Đồng hồ
            Thiết bị âm thanh
            Thực phẩm và đồ uống
            Chăm Sóc Thú Cưng
            Mẹ & Bé
            Thời trang trẻ em & trẻ sơ sinh
            Gaming & Console
            Cameras & Flycam
            Nhà cửa & Đời sống
            Thể Thao & Dã Ngoại
            Thời trang nam
            Thời trang nữ
            
        """;
}
