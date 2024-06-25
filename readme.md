## 项目小记

### 缓存更新策略

[<img src="https://s21.ax1x.com/2024/05/23/pkQKRmR.png" alt="pkQKRmR.png" style="zoom: 33%;" />](https://imgse.com/i/pkQKRmR)









业务场景：

- 低一致性需求：使用内存淘汰机制。例如店铺类型的查询缓存

- 高一致性需求：主动更新，并以超时剔除作为兜底方案。例如店铺详情查询的缓存



#### 主动更新策略

1. 【推荐】缓存的调用者，在更新数据库的同时更新缓存

   > **详解：**
   >
   > 之考虑三个问题（更新数据库时）
   >
   > 1. **删除缓存还是更新缓存？**
   >
   >    - 更新缓存：每次更新数据库都更新缓存，无效写入较多
   >    - 【推荐】删除缓存：更新数据库时让缓存失效，查询时再更新
   >
   > 2. **如何保证缓存与数据库的操作同时成功or失败？**
   >
   >    - 单体系统：将缓存与数据库操作放在一个事务里
   >    - 分布式系统：利用TCC等分布式事务方案
   >
   > 3. **先操作缓存还是先操作数据库？**
   >
   >    ​	....

2. 缓存与数据库整合为一个服务，由服务维护一致性。

   调用者调用该服务即可

3. 调用者只操作缓存，将缓存数据持久化到数据库由**其他线程异步执行**



### 缓存穿透

客户端请求的数据在缓存和数据库都不存在，缓存永远无法生效，请求都会打到数据库 

**解决方案：**

1. 缓存空对象

   简单、维护方便；但是会造成额外的内存消耗及短期的不一致

2. 布隆过滤器

3. 增加ID的复杂度，避免被猜测ID的规律

4. 做好数据的基础格式校验

5. 加强用户权限校验

6. 做好热点参数的限流



### 缓存雪崩

在同一个时间段，大量的缓存key同时失效或Redis服务器宕机，导致大量请求到达数据库，带来巨大压力

**解决方案：**

1. 给不同Key的TTL添加随机值
2. 利用Redis集群提高服务的可用性
3. 给缓存业务添加降级限流策略
4. 给业务添加多级缓存



### 缓存击穿

缓存击穿问题也称为热点key问题，就是一个被**高并发访问**并且**缓存重建业务较复杂**的key突然失效了，无数的请求访问会在瞬间给数据库带来巨大压力。

通俗易懂的解释就是，一个要处理很久的请求，可能需要3s才能缓存完毕，而3s内有无数的请求过来....

**解决方案：**

- 互斥锁
- 逻辑过期

#### 互斥锁

**执行流程：** 

查询缓存 

**->** 获取互斥锁 

**->** 获取成功就查询数据库，开始缓存数据 

**->** 缓存成功后，释放锁

失效后的第一个线程进来，获取互斥锁

该线程执行完毕写入缓存之前，其他线程都是无法拿到互斥锁的

拿不到互斥锁就不能进行后续操作（如读取数据库...）

这些线程将休眠一会，重新从 **查询缓存**开始运行



#### 逻辑过期

**执行流程：** 

查询缓存，发现逻辑时间已过期 

**—>** 获取互斥锁成功，开启新线程（新线程进行查询数据库，重建缓存数据的操作，并重置最新缓存数据的时间，最后释放锁） 

**—>** 在释放锁之前，直接返回过期的数据

如果有其他线程在上面线程持有释放锁的时候请求，获取互斥锁失败，将直接返回过期数据



| 解决方案 |                       优点                       |                       缺点                       |
| :------: | :----------------------------------------------: | :----------------------------------------------: |
|  互斥锁  | 没有额外的内存消耗<br />保证一致性<br />实现简单 |  线程需要等待，性能受到影响<br />可能有死锁风险  |
| 逻辑过期 |              线程无需等待，性能较好              | 不保证一致性<br />有额外的内存消耗<br />实现复杂 |



#### 互斥锁解决方案

执行流程在上方

**简单思路：** 使用redis的setnx，表示只有当key不存在的时候，才能set成功

**一定要设置过期时间**

例如： 获取锁，使用setnx，返回1表明set（获取锁）成功，返回0表明set（获取锁）失败

```bash
- setnx lock 1      --成功，返回1
- setnx lock 2      --失败，返回0
- setnx lock 3      --失败，返回0
- get lock          --返回1（setnx lock 1 的值）
- del lock			--释放锁
```



#### 逻辑过期解决方案

执行流程在上方

添加过期时间字段，为了避免对原来的对象实体做修改，创建一个新的对象实体类

> **组合优先于继承？ - 尽量不要使用extend**

```java
// 组合版
@Data
public class RedisData{
    private LocalDateTime expireTime;
    private Object data;
}
```

```java
// 泛型版
@Data
public class RedisData<T>{
    private LocalDateTime expireTime;
    private T data;
}
```









### 死锁

**互斥锁 - 死锁::产生条件** ：若干个线程，需要同时使用相同的若干个变量（这若干个变量都有自己相应的锁）。当线程A要访问变量m和变量n，线程B也要访问变量m和变量n，A拿到了变量m的锁，B拿到了n的锁，相互等待对方释放后获取第二把锁，就造成了死锁问题。

彼此都占用对方所需资源



> **死锁产生的必要条件：**
>
> 1. **互斥：** 某种资源一次只允许一个进程访问，即该资源一旦分配给某个进程，其他进程就不能再访问，直到该进程访问结束。   
>
> 2. **占有且等待：** 一个进程本身占有资源（一种或多种），同时还有资源未得到满足，正在等待其他进程释放该资源。                
>
> 3. **不可抢占：** 别人已经占有了某项资源，你不能因为自己也需要该资源，就去把别人的资源抢过来。  
>
> 4. **循环等待：** 存在一个进程链，使得每个进程都占有下一个进程所需的至少一种资源。      
>
> 当以上四个条件均满足，必然会造成死锁，发生死锁的进程无法进行下去，它们所持有的资源也无法释放。这样会导致CPU 的吞吐量下降。所以死锁情况是会浪费系统资源和影响计算机的使用性能的。那么，解决死锁问题就是相当有必要的了。



#### 破坏死锁的条件

1. 破坏“占有且等待”

   ① 所有进程开始运行之前，一次性申请其在整个运行过程中所需要的全部资源

   优点：简单实施且安全

   缺点：【饥饿现象】因某项资源不满足，导致进程无法启动，而其他已经满足了的资源也不会得到利用，严重降低资源利用率

   ② 对①的改进，允许线程只获得运行初期所需资源就可以运行，运行过程中逐步释放掉使用完毕的资源，再去请求新的资源

2. 破坏“不可抢占”

   > [互斥锁、死锁及死锁产生条件及其其解决方法_互斥锁死锁-CSDN博客](https://blog.csdn.net/qq_44045338/article/details/104769194)
   >
   > 当一个已经持有了一些资源的进程在提出新的资源请求没有得到满足时，它必须释放已经保持的所有资源，待以后需要使用 的时候再重新申请。这就意味着进程已占有的资源会被短暂地释放或者说是被抢占了。该种方法实现起来比较复杂，且代价也比 较大。释放已经保持的资源很有可能会导致进程之前的工作实效等，反复的申请和释放资源会导致进程的执行被无限的推迟，这 不仅会延长进程的周转周期，还会影响系统的吞吐量。

3. 破坏“循环等待”

    可以通过定义资源类型的线性顺序来预防，可将每个资源编号，当一个进程占有编号为i的资源时，那么它下一次申请资源只 能申请编号大于i的资源。



## 缓存工具封装

**基于StringRedisTemplate封装一个缓存工具类，满足下列需求：**

```java
 private final StringRedisTemplate stringRedisTemplate;
 public RedisUtil(StringRedisTemplate stringRedisTemplate) {
     this.stringRedisTemplate = stringRedisTemplate;
 }
```

- **方法1：**将任意Java对象序列化为json并存储在string类型的key中，并且可以设置**TTL过期时间**

  ```java
  /**
   * @param key  键
   * @param data 值
   * @param time TTL过期时间
   * @param unit 单位
   * @description 【TTL过期set】方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
   * @author chentianhai.cth
   * @date 2024/6/24 17:45
   */
  public void set(String key, Object data, Long time, TimeUnit unit) {
      stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(data), time, unit);
  }
  ```

- **方法2：**将任意Java对象序列化为json并存储在string类型的key中，并且可以**设置逻辑过期时间**，用于**处理缓存击穿**问题

  ```java
  /**
   * @param key  键
   * @param data 数据
   * @param time 基于当前时间，多久后过期（秒）？
   * @description 【逻辑过期set】方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
   * @author chentianhai.cth
   * @date 2024/6/24 17:48
   */
  public <T> void setWithLogicalExpire(String key, T data, Long time, TimeUnit unit) {
      RedisData<T> redisData = new RedisData<>();
      redisData.setData(data);
      redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
      stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));
  }
  ```

- **方法3：**根据指定的ky查询缓存，并反序列化为指定类型，利用**缓存空值**的方式**解决缓存穿透**问题

  ```java
  /**
    * @param keyPrefix  key的前缀
    * @param id         ID值
    * @param type       转换数据类型
    * @param dbFallback 从redis查询失败时，执行的方法 - 从数据库查询
    * @return R
    * @description 方法3：根据指定的ky查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    * @author chentianhai.cth
    * @date 2024/6/24 18:32
  */
  public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
      String key = keyPrefix + id;
      // 从Redis中查询缓存json
      String json = stringRedisTemplate.opsForValue().get(key);
      // 判断缓存是否命中 -> 命中，直接返回
      if (StrUtil.isNotBlank(json)) {
          return JSONUtil.toBean(json, type);
      }
      // 不为null，说明redis中还是查到了，只是会被判断为blank，那就说明是空字符串
      if (json != null) {
          return null;
      }
      // 未命中， 查数据库 -> 如果数据库中为空，缓存空值，解决缓存穿透，返回fail；
      R r = dbFallback.apply(id);
      if (r == null) {
          stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
          return null;
      }
      this.set(key, r, time, unit);
      return r;
  }
  ```

- **方法4：**根据指定的ky查询缓存，并反序列化为指定类型，需要**利用逻辑过期解决缓存击穿**问题

  ```java
  /**
   * @param key 键
   * @param <T> 泛型
   * @return T
   * @description 方法4：根据指定的ky查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
   * @author chentianhai.cth
   * @date 2024/6/24 17:49
   */
  public <R, ID> R queryWithLogicalExpire(
          String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
          Long time, TimeUnit unit, String lockKeyPrefix) {
      /*
       * step1: 提交商铺id，从redis查询商铺缓存
       * step2: 判断缓存是否命中
       *  - 未命中：返回空
       *  - 命中：判断缓存是否过期
       *     - 未过期：返回数据
       *     - 过期：尝试获取互斥锁是否成功
       *        - 成功：开启独立线程，从数据库中查询，并缓存新数据，然后释放锁
       *        - 失败：返回数据（过期数据）
       */
      String key = keyPrefix + id;
      // 提交商铺id，从Redis中查询商铺缓存
      String json = stringRedisTemplate.opsForValue().get(key);
  
      // 判断缓存是否命中 -> 未命中，直接返回为空
      if (StrUtil.isBlank(json)) {
          return null;
      }
  
      // 命中 -> 判断缓存是否过期
      RedisData redisData = JSONUtil.toBean(json, RedisData.class);
      R r = JSONUtil.toBean(JSON.toJSONString(redisData.getData()), type);
  
      if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
          // 未过期，直接返回商铺信息
          System.out.println("未过期");
          return r;
      }
  
      System.out.println("已过期");
      // 已过期
      if (!tryLock(lockKeyPrefix + id)) {
          // 获取锁失败，返回过期数据
          System.out.println("获取锁失败");
          return r;
      }
      CACHE_REBUILD_EXECUTOR.submit(() -> {
          try {
              // 重建缓存
              R rr = dbFallback.apply(id);
              this.setWithLogicalExpire(key, rr, time, unit);
          } catch (Exception e) {
              throw new RuntimeException(e);
          } finally {
              unLock(lockKeyPrefix + id);
          }
      });
      return r;
  }
  
  /**
   * @param key 锁的key
   * @return boolean
   * @description 获取互斥锁，10s过期
   * @author chentianhai.cth
   * @date 2024/5/30 14:03
   */
  private boolean tryLock(String key) {
      Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
      return Boolean.TRUE.equals(flag);
  }
  
  /**
   * @param key 钥匙放的锁key
   * @description 释放锁
   * @author chentianhai.cth
   * @date 2024/5/30 14:04
   */
  private void unLock(String key) {
      stringRedisTemplate.delete(key);
  }
  ```



## 优惠券秒杀

用户抢购，生成订单保存早order表，如果订单表使用数据库自增ID存在如下问题：

- id规律性明显，容易被用户猜测..
- 受单表数据量的限制，如果数据过多，是需要分表存储的，分表存储如果各自使用自增的ID，那么就会存在重复ID的情况



### 全局ID生成器

在分布式系统下用来生成全局唯一ID的工具

**要求：**

1. 唯一
2. 高可用
3. 高性能
4. 递增：利于数据库创建索引
5. 安全



























































