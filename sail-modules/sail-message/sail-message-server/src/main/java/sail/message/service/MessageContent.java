package sail.message.service;

import cn.hutool.core.util.StrUtil;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;

import java.util.*;

/** 消息富文本白名单净化，只保留排版、受限链接与真实附件图片引用。 */
@Component
public class MessageContent {
    /** 服务端移除脚本、事件、样式和外部图片，图片只能引用本消息的附件。 */
    public String clean(String source, Set<Long> attachments) {
        var safe =
                Safelist.none()
                        .addTags(
                                "p",
                                "br",
                                "strong",
                                "b",
                                "em",
                                "i",
                                "u",
                                "s",
                                "ul",
                                "ol",
                                "li",
                                "blockquote",
                                "h2",
                                "h3",
                                "hr",
                                "a",
                                "img")
                        .addAttributes("a", "href", "title")
                        .addProtocols("a", "href", "https", "http")
                        .addEnforcedAttribute("a", "rel", "noopener noreferrer")
                        .addAttributes("img", "data-file-id", "alt");
        var document = Jsoup.parseBodyFragment(Jsoup.clean(source, safe));
        document.outputSettings().prettyPrint(false);
        for (var image : document.select("img")) {
            String id = image.attr("data-file-id");
            if (!id.matches("[1-9][0-9]{0,17}") || !attachments.contains(Long.parseLong(id)))
                throw new BusinessException("正文图片必须来自本消息已上传附件");
        }
        return document.body().html();
    }

    /** 发布不能只有空白排版；正文图片引用本身可以构成实际内容。 */
    public boolean empty(String html) {
        var doc = Jsoup.parseBodyFragment(html);
        return StrUtil.isBlank(doc.text()) && doc.select("img").isEmpty();
    }

    /** 列表摘要按纯文本生成，不能把 HTML 标签直接显示为正文。 */
    public String summary(String html) {
        return StrUtil.sub(Jsoup.parseBodyFragment(html).text(), 0, 160);
    }
}
