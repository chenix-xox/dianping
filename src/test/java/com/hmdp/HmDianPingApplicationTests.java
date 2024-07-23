package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void test(){
        // 提前缓存所有店铺信息
        List<Shop> shops = shopMapper.selectList(null);
        List<Long> ids = shops.stream().map(Shop::getId).collect(Collectors.toList());
        for (long id : ids){
            shopService.saveShop2Redis(id,10L);
        }
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        long count = latch.getCount();
        Runnable task = () -> {
            System.out.println(Thread.currentThread().getName());
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nexId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            executorService.submit(task);
        }
        latch.await();
    }
}
