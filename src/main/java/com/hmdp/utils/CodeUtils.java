package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

import java.util.HashMap;
import java.util.Map;

public class CodeUtils {

    /**
     * 将任意对象转换为Map，使用忽略空值和自定义字段值编辑器的CopyOptions
     *
     * @param <T> 泛型类型，表示可转换的对象类型
     * @param object 任意对象
     * @return 转换后的Map对象
     * @throws IllegalArgumentException 如果对象无法转换为Map
     */
    public static <T> Map<String, Object> objectToMap(T object) throws IllegalArgumentException {
        if (object == null) {
            throw new IllegalArgumentException("Input object cannot be null");
        }

        return BeanUtil.beanToMap(
                object,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldKey, fieldValue) -> fieldValue.toString()));
    }
}
