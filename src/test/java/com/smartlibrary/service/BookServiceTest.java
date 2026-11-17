package com.smartlibrary.service;

import com.smartlibrary.dto.BookDto;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.Category;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BookService bookService;

    private Category category;
    private Book book;

    @BeforeEach
    void setUp() {
        category = new Category("Software Craft", "SWC", "Software engineering");
        category.setId(1L);

        book = new Book();
        book.setId(10L);
        book.setIsbn("978-0132350884");
        book.setTitle("Clean Code");
        book.setAuthor("Robert C. Martin");
        book.setCategory(category);
        book.setTotalCopies(4);
        book.setAvailableCopies(3);
        book.setIsArchived(false);
    }

    @Test
    @DisplayName("Create new book validates unique ISBN and category")
    void testCreateBookSuccess() {
        BookDto dto = new BookDto();
        dto.setIsbn("978-0132350884");
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        dto.setCategoryId(1L);
        dto.setTotalCopies(5);
        dto.setAvailableCopies(5);

        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> {
            Book b = i.getArgument(0);
            b.setId(20L);
            return b;
        });

        Book created = bookService.createBook(dto, "admin");

        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Clean Code");
        assertThat(created.getAvailableCopies()).isEqualTo(5);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    @DisplayName("Create book fails if available copies exceeds total copies")
    void testCreateBookInvalidCopies() {
        BookDto dto = new BookDto();
        dto.setIsbn("978-0132350884");
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert C. Martin");
        dto.setCategoryId(1L);
        dto.setTotalCopies(2);
        dto.setAvailableCopies(5); // invalid: 5 > 2

        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> bookService.createBook(dto, "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Available copies cannot exceed total copies");
    }

    @Test
    @DisplayName("Archive and restore book functions properly")
    void testArchiveAndRestoreBook() {
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

        bookService.archiveBook(10L, "admin");
        assertThat(book.getIsArchived()).isTrue();

        bookService.restoreBook(10L, "admin");
        assertThat(book.getIsArchived()).isFalse();
    }
}
