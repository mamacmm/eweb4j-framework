package org.eweb4j.mvc.validator.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Upload {
	
	String maxRequestSize() default "4M";//允许一次上传总共的文件最大大小 default 4M
	
	String maxMemorySize() default "4K";//硬盘缓冲 default 4K
	
	String maxFileSize() default "1M";//允许每个文件最大大小 default 1M
	
	String tmpDir() default "" ;//临时目录
	
	String[] suffix();
	
}
