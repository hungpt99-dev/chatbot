package com.helpdesk.infrastructure.document;

import com.helpdesk.web.exception.DocumentParseException;
import com.helpdesk.web.exception.UnsupportedDocumentTypeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts plain text from an uploaded KB document. Provider/IO detail only
 * (AGENTS.md §3): it knows about PDF (PDFBox) and DOCX (POI) and treats
 * text/FAQ/markdown as UTF-8. The application layer decides what to do with the
 * text (chunk, persist, index). Unknown types are rejected with a clear error.
 */
@Component
public class DocumentContentExtractor {

    public String extract(String filename, String contentType, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (isPdf(contentType, name)) {
            return extractPdf(bytes);
        }
        if (isDocx(contentType, name)) {
            return extractDocx(bytes);
        }
        if (isText(contentType, name)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new UnsupportedDocumentTypeException(
                "Unsupported document type: filename='" + filename + "', contentType='" + contentType + "'");
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse PDF document", e);
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse DOCX document", e);
        }
    }

    private boolean isPdf(String contentType, String name) {
        return "application/pdf".equalsIgnoreCase(contentType) || name.endsWith(".pdf");
    }

    private boolean isDocx(String contentType, String name) {
        return (contentType != null && contentType.contains("wordprocessingml"))
                || name.endsWith(".docx");
    }

    private boolean isText(String contentType, String name) {
        if (contentType != null && (contentType.startsWith("text/")
                || contentType.equals("application/json"))) {
            return true;
        }
        return name.endsWith(".txt") || name.endsWith(".faq") || name.endsWith(".md")
                || name.endsWith(".csv") || name.endsWith(".text");
    }
}
