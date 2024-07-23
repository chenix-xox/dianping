package com.hmdp.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author Sunyur
 */
@Data
public class RedisData<T>{
    private LocalDateTime expireTime;
    private T data;
}
