package com.alibaba.fastjson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.alibaba.fastjson.PropertyNamingStrategy;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializeFilter;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })//@JSONType是配置在类上的
public @interface JSONType {

    boolean asm() default true;

    String[] orders() default {};//顺序

    /**
     * @since 1.2.6
     */
    //配置在类上的就会只装配列举的字段，
    String[] includes() default {};//@JSONType(includes = {"name","sex"})
    //配置在类上的就会只装配列举的字段，
    String[] ignores() default {};//@JSONType(ignores ={"id", "sex"}) 

    SerializerFeature[] serialzeFeatures() default {};
    Feature[] parseFeatures() default {};
    
    boolean alphabetic() default true;
    
    Class<?> mappingTo() default Void.class;
    
    Class<?> builder() default Void.class;
    
    /**
     * @since 1.2.11
     */
    String typeName() default "";//类型名称

    /**
     * @since 1.2.32
     */
    String typeKey() default "";
    
    /**
     * @since 1.2.11
     */
    Class<?>[] seeAlso() default{};
    
    /**
     * @since 1.2.14
     */
    Class<?> serializer() default Void.class;
    
    /**
     * @since 1.2.14
     */
    Class<?> deserializer() default Void.class;

    boolean serializeEnumAsJavaBean() default false;

    PropertyNamingStrategy naming() default PropertyNamingStrategy.CamelCase;

    /**
     * @since 1.2.49
     */
    Class<? extends SerializeFilter>[] serialzeFilters() default {};
}
