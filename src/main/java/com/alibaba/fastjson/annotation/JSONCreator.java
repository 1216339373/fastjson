package com.alibaba.fastjson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
//该注解用于指定构造方法
@Target({ ElementType.CONSTRUCTOR, ElementType.METHOD })
public @interface JSONCreator {

}
