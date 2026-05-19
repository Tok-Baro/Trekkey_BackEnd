package com.api.template.global.swagger;

import com.api.template.global.response.code.BaseResponseCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodeExamples.Container.class)
public @interface ApiErrorCodeExamples {

    Class<? extends BaseResponseCode> value();

    String[] codes() default {};

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        ApiErrorCodeExamples[] value();
    }
}
