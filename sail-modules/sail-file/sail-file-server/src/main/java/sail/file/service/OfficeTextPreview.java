package sail.file.service;

import cn.hutool.core.util.StrUtil;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;

import java.io.*;
import java.util.Locale;

/** 旧版 Office 与演示文稿提供受限文本预览，不访问外部转换网站或执行宏。 */
@Component
public class OfficeTextPreview {
    /** 按真实扩展名解析正文；限制输出字符和工作表行数，避免一次返回整本工作簿。 */
    public String read(String name, InputStream input) throws IOException {
        String ext = StrUtil.subAfter(name, '.', true).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        switch (ext) {
            case "doc" -> {
                try (var document = new HWPFDocument(input)) {
                    var range = document.getRange();
                    for (int i = 0;
                            i < range.numParagraphs()
                                    && result.length() < FilePolicy.PREVIEW_CHARACTERS;
                            i++)
                        result.append(range.getParagraph(i).text().replace('\u0007', '\t'))
                                .append('\n');
                }
            }
            case "ppt" -> {
                try (var slides = new HSLFSlideShow(input)) {
                    for (var slide : slides.getSlides()) {
                        if (result.length() >= FilePolicy.PREVIEW_CHARACTERS) break;
                        result.append("第 ").append(slide.getSlideNumber()).append(" 页\n");
                        slide.getTextParagraphs()
                                .forEach(
                                        paragraphs ->
                                                paragraphs.forEach(
                                                        paragraph ->
                                                                paragraph
                                                                        .getTextRuns()
                                                                        .forEach(
                                                                                run ->
                                                                                        result.append(
                                                                                                        run
                                                                                                                .getRawText())
                                                                                                .append(
                                                                                                        '\n'))));
                    }
                }
            }
            case "pptx" -> {
                try (var slides = new XMLSlideShow(input)) {
                    for (var slide : slides.getSlides()) {
                        if (result.length() >= FilePolicy.PREVIEW_CHARACTERS) break;
                        result.append("第 ").append(slide.getSlideNumber()).append(" 页\n");
                        for (var shape : slide.getShapes())
                            if (shape instanceof XSLFTextShape text)
                                result.append(text.getText()).append('\n');
                    }
                }
            }
            case "xls" -> {
                try (var workbook = WorkbookFactory.create(input)) {
                    var format = new DataFormatter(Locale.CHINA);
                    for (var sheet : workbook) {
                        if (result.length() >= FilePolicy.PREVIEW_CHARACTERS) break;
                        result.append(sheet.getSheetName()).append('\n');
                        int rows = 0;
                        for (var row : sheet) {
                            if (++rows > FilePolicy.PREVIEW_ROWS
                                    || result.length() >= FilePolicy.PREVIEW_CHARACTERS) break;
                            for (int column = 0;
                                    column < Math.min(row.getLastCellNum(), 100);
                                    column++)
                                result.append(format.formatCellValue(row.getCell(column)))
                                        .append('\t');
                            result.append('\n');
                        }
                    }
                }
            }
            default -> throw new BusinessException("该文件不使用 Office 文本预览");
        }
        // 明确提示文本模式与截断边界，不伪造图片、版式或不存在的正文。
        if (result.length() > FilePolicy.PREVIEW_CHARACTERS)
            return result.substring(0, FilePolicy.PREVIEW_CHARACTERS) + "\n内容较长，仅显示前 200000 字符。";
        return result.toString();
    }
}
