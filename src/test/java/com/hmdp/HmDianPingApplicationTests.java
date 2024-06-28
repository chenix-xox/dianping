package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void test(){
        shopService.saveShop2Redis(1L,10L);
    }

    @Test
    public void testIdWorker(){
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                long id = redisIdWorker.nexId("order");
                System.out.println(id);
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            executorService.submit(task);
        }

    }
}
