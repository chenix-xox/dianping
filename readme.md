## 项目小记

### 缓存更新策略

[<img src="https://s21.ax1x.com/2024/05/23/pkQKRmR.png" alt="pkQKRmR.png" style="zoom: 33%;" />](https://imgse.com/i/pkQKRmR)

业务场景：

- 低一致性需求：使用内存淘汰机制。例如店铺类型的查询缓存

- 高一致性需求：主动更新，并以超时剔除作为兜底方案。例如店铺详情查询的缓存