package com.hmdp.entity;

import lombok.Data;

/**
 * @author chentianhai.cth
 * @createTime 2024.07.29 14:19
 * @description 乐观锁 - 版本号
 */
@Data
public class VersionParam {
    /**
     * 版本号
     */
    private String version;
}
