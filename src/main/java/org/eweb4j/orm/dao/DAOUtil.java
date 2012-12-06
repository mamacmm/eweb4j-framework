package org.eweb4j.orm.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.jdbc.JdbcUtil;
import org.eweb4j.orm.sql.OrderColumnUtil;

@SuppressWarnings("all")
public class DAOUtil {
	public static <T> Number selectMaxId(T t, Connection con, String dbType)
			throws SQLException {
		String idColumn;
		String table;
		Class<?> clazz;
		if (t instanceof Class) {
			clazz = (Class<?>) t;
		} else {
			clazz = t.getClass();
			
		}
		
		if (!(t instanceof Class) && Map.class.isAssignableFrom(clazz)) {
			HashMap<String, Object> map = (HashMap<String, Object>) t;
			idColumn = (String) map.get("idColumn");
			table = (String) map.get("table");
		} else {
			idColumn = ORMConfigBeanUtil.getIdColumn(clazz);
			table = ORMConfigBeanUtil.getTable(clazz, true);
		}
		
		if (idColumn == null)
			return 0;
		
		idColumn = OrderColumnUtil.getOrderColumn(idColumn, dbType);
		String format = "select max(%s) from %s ";
		String sql = String.format(format, idColumn, table);
		Number id = JdbcUtil.getInteger(con, sql);// 获得最新插入的ID值

		return id;
	}

	public static void main(String[] args) {
		System.out.println(Class.class.getClass());
	}

}
