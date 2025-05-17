package cc.ddrpa.dorian.norbo.mabtisplus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface MPTypeHandler {

    String value() default "";

    /**
     * 显式指定生成类的包名，也可通过 value 指定
     * <p>
     * 默认与被修饰类位于同一个包下
     */
    String packageName() default "";
}