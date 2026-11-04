package sail.system.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 当前账号个人中心资料，不暴露内部登录状态或凭据。 */
public record ProfileView(UserView user, String department, List<String> posts, LocalDateTime createdAt) {}
