package org.eweb4j.orm.jdbc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eweb4j.cache.ORMConfigBeanCache;
import org.eweb4j.orm.PropType;
import org.eweb4j.orm.config.bean.ORMConfigBean;
import org.eweb4j.orm.config.bean.Property;
import org.eweb4j.util.ClassUtil;
import org.eweb4j.util.ReflectUtil;


/**
 * 将从数据库查询出来的结果集映射成java对象
 * 
 * @author cfuture.aw
 * @since v1.a.0
 */
@SuppressWarnings("all")
public class RowMapping {

	public static <T> T mapOneRow(ResultSet rs, Class<T> cls) throws Exception {
		return mapOneRow(rs, null, cls);
	}

	public static <T> List<T> mapRows(ResultSet rs, Class<T> cls) throws Exception {
		List<T> list = new ArrayList<T>();
		ResultSetMetaData rsmd = null;
		T t = null;

		if (rs != null) {
			rsmd = rs.getMetaData();
			List<String> columns = new ArrayList<String>();
			for (int i = 1; i <= rsmd.getColumnCount(); ++i) {
				columns.add(rsmd.getColumnLabel(i));
			}
			if (Map.class.isAssignableFrom(cls)) {
				List<Map<String, Object>> _list = new ArrayList<Map<String, Object>>();

				while (rs.next()) {
					Map<String, Object> map = new HashMap<String, Object>();
					for (int i = 1; i <= columns.size(); ++i) {
						String name = columns.get(i - 1);
						name = name.toLowerCase();
						Object obj = rs.getObject(name);
						//System.out.println("rs->"+columns.get(i - 1)+"=>"+obj);
						map.put(name, obj);
					}
					_list.add(map);
				}

				return (List<T>) (_list.isEmpty() ? null : _list);
			} else {
				while (rs.next()) {
					t = cls.newInstance();
					ReflectUtil ru = new ReflectUtil(t);
					ORMConfigBean ormBean = ORMConfigBeanCache.get(cls.getName());

					for (Iterator<Property> it = ormBean.getProperty().iterator(); it.hasNext();) {
						Property p = it.next();
						String type = p.getType();
						if (type == null)
							continue;

						// 如果查询出来的字段名字没有,则不进行值注入
						boolean flag = false;
						for (String col : columns) {
							if (col.equalsIgnoreCase(p.getColumn())) {
								flag = true;
								continue;
							}
						}

						if (!flag)
							continue;

						Method m = ru.getSetter(p.getName());
						if (m == null)
							continue;

						Object value = rs.getObject(p.getColumn());
						if (value == null)
							continue;

						String v = String.valueOf(value);
						if (v == null) {
							v = "";
						}
						
						if ("int".equalsIgnoreCase(type) || "java.lang.Integer".equalsIgnoreCase(type)) {
							if ("".equals(v.trim())) {
								v = "0";
							}
							if (value instanceof Boolean)
								v = ((Boolean)value ? "1" : "0");
							
							m.invoke(t, Integer.parseInt(v));
						} else if ("long".equalsIgnoreCase(type) || "java.lang.Long".equalsIgnoreCase(type)) {
							if ("".equals(v.trim())) {
								v = "0";
							}
							if (value instanceof Boolean)
								v = ((Boolean)value ? "1" : "0");
							
							m.invoke(t, Long.parseLong(v));
						} else if ("float".equalsIgnoreCase(type) || "java.lang.Float".equalsIgnoreCase(type)) {
							if ("".equals(v.trim())) {
								v = "0.0";
							}
							if (value instanceof Boolean)
								v = ((Boolean)value ? "1.0" : "0.0");
							
							m.invoke(t, Float.parseFloat(v));
						} else if ("double".equalsIgnoreCase(type) || "java.lang.Double".equalsIgnoreCase(type)) {
							if ("".equals(v.trim())) {
								v = "0.0";
							}
							if (value instanceof Boolean)
								v = ((Boolean)value ? "1.0" : "0.0");
							m.invoke(t, Float.parseFloat(v));
						} else if ("string".equalsIgnoreCase(type) || "java.lang.String".equalsIgnoreCase(type)) {
							m.invoke(t, v);
						} else if ("boolean".equalsIgnoreCase(type) || "java.lang.Boolean".equalsIgnoreCase(type)){
							if ("1".equals(v.trim()) || "true".equals(v.trim())){
								m.invoke(t, true);
							}else if ("0".equals(v.trim()) || "false".equals(v.trim())){
								m.invoke(t, false);
							}
						} else if ("date".equalsIgnoreCase(type) || "java.sql.Date".equalsIgnoreCase(type) || "java.util.Date".equalsIgnoreCase(type)) {
							m.invoke(t, value);
						} else if (PropType.ONE_ONE.equalsIgnoreCase(type) || PropType.MANY_ONE.equalsIgnoreCase(type)) {
							if ("".equals(v))
								continue;

							Field field = ru.getField(p.getName());
							Class<?> tarClass = field.getType();

							String refField = p.getRelProperty();
							Object tarObj = tarClass.newInstance();
							
							tarObj = ClassUtil.injectFieldValue(tarObj, refField, new String[] { v });
							
							m.invoke(t, tarObj);

						} else if (PropType.ONE_MANY.equalsIgnoreCase(type)) {

						} else if (PropType.MANY_MANY.equalsIgnoreCase(type)) {

						} else if (!"".equals(type)) {
							m.invoke(t, String.valueOf(value));
						}

					}
					
					list.add(t);
				}
			}
		}

		return list.isEmpty() ? null : list;
	}

	public static <T> T mapOneRow(ResultSet rs, ResultSetMetaData rsmd, Class<T> cls) throws Exception {
		List<T> list = mapRows(rs, cls);
		return list == null ? null : list.get(0);
	}

}
