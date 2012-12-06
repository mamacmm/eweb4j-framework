package org.eweb4j.orm;

public class Models {
	public static <T> ModelHelper<T> inst(T t){
		return new ModelHelper<T>(t);
	}
	
	public static <T> ModelHelper<T> inst(Class<T> cls){
		try {
			return new ModelHelper<T>(cls.newInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
	}
}
