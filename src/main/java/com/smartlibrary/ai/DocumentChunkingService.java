package com.smartlibrary.ai;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.DigitalResource;
import com.smartlibrary.entity.DocumentChunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);
    private static final int TARGET_CHUNK_WORDS = 150;

    /**
     * Chunks a Book entity into one or more structured metadata and summary chunks.
     */
    public List<DocumentChunk> chunkBook(Book book) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        sb.append("Book Title: ").append(book.getTitle()).append(". ");
        sb.append("Author: ").append(book.getAuthor()).append(". ");
        sb.append("Category: ").append(book.getCategory().getName()).append(". ");
        sb.append("ISBN: ").append(book.getIsbn()).append(". ");
        if (book.getPublisher() != null) {
            sb.append("Publisher: ").append(book.getPublisher()).append(". ");
        }
        if (book.getPublicationYear() != null) {
            sb.append("Year: ").append(book.getPublicationYear()).append(". ");
        }
        if (book.getLocationShelf() != null) {
            sb.append("Physical Location: ").append(book.getLocationShelf()).append(". ");
        }
        sb.append("Library Inventory: ").append(book.getAvailableCopies()).append(" copies currently available out of ")
          .append(book.getTotalCopies()).append(" total copies. ");
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            sb.append("Summary: ").append(book.getDescription());
        }

        DocumentChunk chunk = new DocumentChunk(book.getTitle(), 0, 1, sb.toString());
        chunk.setBook(book);
        chunks.add(chunk);
        return chunks;
    }

    /**
     * Extracts text from a PDF file using Apache PDFBox and chunks into text segments.
     */
    public List<DocumentChunk> chunkDigitalResource(DigitalResource resource) {
        List<DocumentChunk> chunks = new ArrayList<>();
        File file = new File(resource.getFilePath());

        if (!file.exists() || !file.canRead()) {
            File fallback = new File("uploads/pdf", resource.getFileName());
            if (fallback.exists() && fallback.canRead()) {
                file = fallback;
            } else {
                File fallback2 = new File("src/main/resources/data", resource.getFileName());
                if (fallback2.exists() && fallback2.canRead()) {
                    file = fallback2;
                }
            }
        }

        if (!file.exists() || !file.canRead()) {
            log.warn("Cannot read file for digital resource: {}", resource.getFilePath());
            return chunks;
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            int chunkIndex = 0;

            for (int page = 1; page <= Math.min(totalPages, 50); page++) {
                textStripper.setStartPage(page);
                textStripper.setEndPage(page);
                String pageText = textStripper.getText(document).trim();

                if (pageText.isBlank()) continue;

                List<String> pageChunks = splitTextIntoWordChunks(pageText, TARGET_CHUNK_WORDS);
                for (String text : pageChunks) {
                    DocumentChunk chunk = new DocumentChunk(resource.getTitle(), chunkIndex++, page, text);
                    chunk.setDigitalResource(resource);
                    chunks.add(chunk);
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract text from PDF for {}: {}", resource.getTitle(), e.getMessage());
        }

        return chunks;
    }

    private List<String> splitTextIntoWordChunks(String text, int maxWords) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            result.add(text);
            return result;
        }

        StringBuilder current = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            current.append(word).append(" ");
            wordCount++;
            if (wordCount >= maxWords) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                wordCount = 0;
            }
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }
}
