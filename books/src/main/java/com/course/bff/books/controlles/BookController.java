package com.course.bff.books.controlles;

import com.course.bff.books.models.Book;
import com.course.bff.books.requests.CreateBookCommand;
import com.course.bff.books.responses.BookResponse;
import com.course.bff.books.services.BookService;
import com.course.bff.books.services.RedisService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("api/v1/books")
public class BookController {

    private final static Logger logger = LoggerFactory.getLogger(BookController.class);
    private final BookService bookService;
    private final RedisService redisService;
    private final MeterRegistry meterRegistry;
    private Counter requestCounter;
    private Counter errorCounter;

    public BookController(BookService bookService, RedisService redisService, MeterRegistry meterRegistry) {
        this.bookService = bookService;
        this.redisService = redisService;
        this.meterRegistry = meterRegistry;
        requestCounter = Counter.builder("request_count")
                .tag("ControllerName", "BookController")
                .tag("ServiceName", "BookService")
                .register(meterRegistry);
        errorCounter = Counter.builder("error_count")
                .tag("ControllerName", "BookController")
                .tag("ServiceName", "BookService")
                .register(meterRegistry);
    }

    @GetMapping()
    @Timed
    public Collection<BookResponse> getBooks() throws InterruptedException {
        requestCounter.increment();
        logger.info("Get book list");
        List<BookResponse> bookResponses = new ArrayList<>();
        this.bookService.getBooks().forEach(book -> {
            BookResponse bookResponse = createBookResponse(book);
            bookResponses.add(bookResponse);
        });
        Thread.sleep(5000);

        //throw new RuntimeException("Book isn't found");
        return bookResponses;
    }

    @GetMapping("/{id}")
    @Timed
    public BookResponse getById(@PathVariable UUID id) {
        requestCounter.increment();
        logger.info(String.format("Find book by id %s", id));
        Optional<Book> bookSearch = this.bookService.findById(id);
        if (bookSearch.isEmpty()) {
            throw new RuntimeException("Book isn't found");
        }

        return createBookResponse(bookSearch.get());
    }

    @PostMapping()
    @Timed
    public BookResponse createBooks(@RequestBody CreateBookCommand createBookCommand) {
        requestCounter.increment();
        logger.info("Create books");
        Book book = this.bookService.create(createBookCommand);
        BookResponse authorResponse = createBookResponse(book);
        redisService.sendPushNotification(authorResponse);
        return authorResponse;
    }

    private BookResponse createBookResponse(Book book) {
        BookResponse bookResponse = new BookResponse();
        bookResponse.setId(book.getId());
        bookResponse.setAuthorId(book.getAuthorId());
        bookResponse.setPages(book.getPages());
        bookResponse.setTitle(book.getTitle());
        return bookResponse;
    }

    @ExceptionHandler
    ResponseEntity<String> handleExceptions(Throwable ex) {
        errorCounter.increment();
        return new ResponseEntity<>("Error :" + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
