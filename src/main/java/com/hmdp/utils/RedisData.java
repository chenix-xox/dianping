package com.hmdp.utils;

import lombok.Data;

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
