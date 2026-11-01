package sail.message.mapper;

import org.apache.ibatis.annotations.*;

import sail.message.entity.UserMessage;

import java.util.List;

/** 站内消息使用单次批量写入，避免逐个接收人往返数据库。 */
@Mapper
public interface MessageBatchMapper {
    @Insert(
            "<script>INSERT INTO"
                + " message_record(recipient_id,sender_id,template_code,title,content,read_flag,created_at,updated_at)"
                + " VALUES <foreach collection='messages' item='m'"
                + " separator=','>(#{m.recipientId},#{m.senderId},#{m.templateCode},#{m.title},#{m.content},FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)</foreach></script>")
    int insertAll(@Param("messages") List<UserMessage> messages);
}
