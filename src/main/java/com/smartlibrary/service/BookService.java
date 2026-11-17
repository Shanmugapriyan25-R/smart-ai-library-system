package com.smartlibrary.service;

import com.smartlibrary.dto.BookDto;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.Category;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ResourceNotFoundException;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public BookService(BookRepository bookRepository,
                       CategoryRepository categoryRepository,
                       AuditLogService auditLogService) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Book findById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Book findByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ISBN: " + isbn));
    }

    @Transactional(readOnly = true)
    public Page<Book> searchBooks(String query, Long categoryId, boolean onlyAvailable, Pageable pageable) {
        return bookRepository.searchBooks(query, categoryId, onlyAvailable, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Book> searchBooksForAdmin(String query, Long categoryId, Pageable pageable) {
        return bookRepository.searchBooksForAdmin(query, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Book> findTopMatchesByKeyword(String keyword, Pageable pageable) {
        return bookRepository.findTopMatchesByKeyword(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<Book> getAllActiveBooks() {
        return bookRepository.findByIsArchivedFalse();
    }

    @Transactional
    public Book createBook(BookDto dto, String username) {
        if (bookRepository.existsByIsbn(dto.getIsbn())) {
            throw new BusinessRuleException("A book with ISBN '" + dto.getIsbn() + "' already exists.");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + dto.getCategoryId()));

        if (dto.getAvailableCopies() > dto.getTotalCopies()) {
            throw new BusinessRuleException("Available copies cannot exceed total copies.");
        }

        Book book = new Book();
        book.setIsbn(dto.getIsbn().trim());
        book.setTitle(dto.getTitle().trim());
        book.setAuthor(dto.getAuthor().trim());
        book.setCategory(category);
        book.setPublisher(dto.getPublisher());
        book.setPublicationYear(dto.getPublicationYear());
        book.setEdition(dto.getEdition());
        book.setTotalCopies(dto.getTotalCopies());
        book.setAvailableCopies(dto.getAvailableCopies());
        book.setLocationShelf(dto.getLocationShelf());
        book.setDescription(dto.getDescription());
        book.setCoverImageUrl(dto.getCoverImageUrl());
        book.setIsArchived(false);

        Book savedBook = bookRepository.save(book);
        auditLogService.log(username, "CREATE_BOOK", "Book", savedBook.getId().toString(),
                "Added book '" + savedBook.getTitle() + "' (ISBN: " + savedBook.getIsbn() + ")");
        return savedBook;
    }

    @Transactional
    public Book updateBook(Long id, BookDto dto, String username) {
        Book book = findById(id);

        if (!book.getIsbn().equalsIgnoreCase(dto.getIsbn()) && bookRepository.existsByIsbn(dto.getIsbn())) {
            throw new BusinessRuleException("A book with ISBN '" + dto.getIsbn() + "' already exists.");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + dto.getCategoryId()));

        // Adjust inventory safely: issued copies = totalCopies - availableCopies
        int issuedCopies = Math.max(0, book.getTotalCopies() - book.getAvailableCopies());
        if (dto.getTotalCopies() < issuedCopies) {
            throw new BusinessRuleException("Cannot reduce total copies below currently issued copies (" + issuedCopies + ").");
        }

        book.setIsbn(dto.getIsbn().trim());
        book.setTitle(dto.getTitle().trim());
        book.setAuthor(dto.getAuthor().trim());
        book.setCategory(category);
        book.setPublisher(dto.getPublisher());
        book.setPublicationYear(dto.getPublicationYear());
        book.setEdition(dto.getEdition());
        book.setTotalCopies(dto.getTotalCopies());
        book.setAvailableCopies(dto.getTotalCopies() - issuedCopies);
        book.setLocationShelf(dto.getLocationShelf());
        book.setDescription(dto.getDescription());
        book.setCoverImageUrl(dto.getCoverImageUrl());
        if (dto.getIsArchived() != null) {
            book.setIsArchived(dto.getIsArchived());
        }

        Book updatedBook = bookRepository.save(book);
        auditLogService.log(username, "UPDATE_BOOK", "Book", updatedBook.getId().toString(),
                "Updated book '" + updatedBook.getTitle() + "'");
        return updatedBook;
    }

    @Transactional
    public void archiveBook(Long id, String username) {
        Book book = findById(id);
        book.setIsArchived(true);
        bookRepository.save(book);
        auditLogService.log(username, "ARCHIVE_BOOK", "Book", id.toString(), "Archived book '" + book.getTitle() + "'");
    }

    @Transactional
    public void restoreBook(Long id, String username) {
        Book book = findById(id);
        book.setIsArchived(false);
        bookRepository.save(book);
        auditLogService.log(username, "RESTORE_BOOK", "Book", id.toString(), "Restored book '" + book.getTitle() + "'");
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Category createCategory(String name, String code, String description, String username) {
        if (categoryRepository.existsByName(name)) {
            throw new BusinessRuleException("Category name '" + name + "' already exists.");
        }
        if (categoryRepository.existsByCode(code)) {
            throw new BusinessRuleException("Category code '" + code + "' already exists.");
        }
        Category category = new Category(name.trim(), code.trim(), description);
        Category saved = categoryRepository.save(category);
        auditLogService.log(username, "CREATE_CATEGORY", "Category", saved.getId().toString(), "Created category '" + name + "'");
        return saved;
    }
}
