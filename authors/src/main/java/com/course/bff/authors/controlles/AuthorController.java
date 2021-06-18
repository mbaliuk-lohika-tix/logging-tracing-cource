package com.course.bff.authors.controlles;

import com.course.bff.authors.models.Author;
import com.course.bff.authors.requests.CreateAuthorCommand;
import com.course.bff.authors.responses.AuthorResponse;
import com.course.bff.authors.services.AuthorService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.util.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("api/v1/authors")
public class AuthorController {

    @Value("${redis.topic}")
    private String redisTopic;

    private final static Logger logger = LoggerFactory.getLogger(AuthorController.class);
    private final AuthorService authorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private Counter requestCounter;
    private Counter errorCounter;

    public AuthorController(AuthorService authorService, RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.authorService = authorService;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        requestCounter = Counter.builder("request_count")
                .tag("ControllerName", "AuthorController")
                .tag("ServiceName", "AuthorService")
                .register(meterRegistry);
        errorCounter = Counter.builder("error_count")
                .tag("ControllerName", "AuthorController")
                .tag("ServiceName", "AuthorService")
                .register(meterRegistry);
    }

    @GetMapping()
    @Timed
    public Collection<AuthorResponse> getAuthors() {
        requestCounter.increment();
        logger.info("Get authors");
        List<AuthorResponse> authorResponses = new ArrayList<>();
        this.authorService.getAuthors().forEach(author -> {
            AuthorResponse authorResponse = createAuthorResponse(author);
            authorResponses.add(authorResponse);
        });

        return authorResponses;
    }

    @GetMapping("/{id}")
    @Timed
    public AuthorResponse getById(@PathVariable UUID id) {
        requestCounter.increment();
        logger.info(String.format("Find authors by %s", id));
        Optional<Author> authorSearch = this.authorService.findById(id);
        if (authorSearch.isEmpty()) {
            throw new RuntimeException("Author isn't found");
        }

        return createAuthorResponse(authorSearch.get());
    }

    @PostMapping()
    @Timed
    public AuthorResponse createAuthors(@RequestBody CreateAuthorCommand createAuthorCommand) {
        requestCounter.increment();
        logger.info("Create authors");
        Author author = this.authorService.create(createAuthorCommand);
        AuthorResponse authorResponse = createAuthorResponse(author);
        this.sendPushNotification(authorResponse);
        return authorResponse;
    }


    private void sendPushNotification(AuthorResponse authorResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            redisTemplate.convertAndSend(redisTopic, gson.toJson(authorResponse));
        } catch (Exception e) {
            logger.error("Push Notification Error", e);
        }
    }

    private AuthorResponse createAuthorResponse(Author author) {
        AuthorResponse authorResponse = new AuthorResponse();
        authorResponse.setId(author.getId());
        authorResponse.setFirstName(author.getFirstName());
        authorResponse.setLastName(author.getLastName());
        authorResponse.setAddress(author.getAddress());
        authorResponse.setLanguage(author.getLanguage());
        return authorResponse;
    }

    @ExceptionHandler
    ResponseEntity<String> handleExceptions(Throwable ex) {
        errorCounter.increment();
        return new ResponseEntity<>("Error:" + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
