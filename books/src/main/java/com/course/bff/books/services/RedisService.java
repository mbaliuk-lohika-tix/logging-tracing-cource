package com.course.bff.books.services;

import com.course.bff.books.responses.BookResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;



@Service
public class RedisService {
    private final static Logger logger = LoggerFactory.getLogger(RedisService.class);
    private final RedisTemplate<String, Object> redisTemplate;
    @Value("${redis.topic}")
    private String redisTopic;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SpanName("Send notification to redis")
    @NewSpan
    public void sendPushNotification(BookResponse bookResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            redisTemplate.convertAndSend(redisTopic, gson.toJson(bookResponse));
        } catch (Exception e) {
            logger.error("Push Notification Error", e);
        }
    }
}
