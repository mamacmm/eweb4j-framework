package org.eweb4j.orm.sql;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import org.eweb4j.orm.DBType;
import org.eweb4j.orm.LikeType;
import org.eweb4j.orm.OrderType;
import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.util.ClassUtil;
import org.eweb4j.util.ReflectUtil;

/**
 * 创建查询的SQL语句
 * 
 * @author cfuture.aw
 * @since v1.a.0
 * @param <T>
 */
@SuppressWarnings("all")
public class SelectSqlCreator<T> {
	private T t;
	private Class<?> clazz;
	private String table;
	private String idColumn;
	private String idField;
	private String dbType;
	private String selectAllColumn;

	public void setSelectAllColumn(String selectAllColumn) {
		this.selectAllColumn = selectAllColumn;
	}

	public SelectSqlCreator(T t, String dbType) {
		this.t = t;
		this.clazz = t.getClass();
		if (Map.class.isAssignableFrom(clazz)) {
			Map<String, Object> map = (HashMap<String, Object>) t;
			table = String.valueOf(map.get("table"));
			if (map.containsKey("idColumn"))
				idColumn = (String) map.get("idColumn");
			else
				idColumn = "id";

			this.idField = idColumn;
			if (map.containsKey("columns")) {
				StringBuilder sb = new StringBuilder();
				for (String column : (String[]) map.get("columns")) {
					if (sb.length() > 0)
						sb.append(", ");
					sb.append(column);
				}

				selectAllColumn = sb.toString();
			} else {
				selectAllColumn = "*";
			}
		} else {
			this.selectAllColumn = ORMConfigBeanUtil.getSelectAllColumn(clazz);
			this.table = ORMConfigBeanUtil.getTable(clazz, true);
			this.idColumn = ORMConfigBeanUtil.getIdColumn(clazz);
			this.idField = ORMConfigBeanUtil.getIdField(clazz);
		}

		this.dbType = dbType;
	}

	public String select(String condition) {

		return String.format("SELECT %s FROM %s WHERE %s ;", selectAllColumn,
				table, condition);
	}

	public String selectCount(String condition) {
		if (condition == null) {
			return String.format("SELECT COUNT(*) FROM %s ;", table);
		}
		return String.format("SELECT COUNT(*) FROM %s WHERE %s ;", table,
				condition);
	}

	/**
	 * 各种条件查询
	 * 
	 * @param fields
	 *            构成条件的属性名数组(填写的应该是对象属性名，不是数据库字段名)
	 * @param values
	 *            构成条件的字段值
	 * @param likeType
	 *            匹配类型 -1左匹配 0全匹配 1右匹配
	 * @param isLike
	 *            是否模糊查询
	 * @param orderField
	 *            对哪个字段进行排序
	 * @param oType
	 *            升序还是降序
	 * @return 字符串
	 * @param currentPage
	 *            第几页
	 * @param numPerPage
	 *            每页显示多少条
	 * @return
	 * @throws SqlCreateException
	 */
	public String selectWhere(String[] fields, String[] values, int likeType,
			boolean isLike, boolean isNot, boolean isOR, String orderField,
			int oType, int currentPage, int numPerPage)
			throws SqlCreateException {
		String oper = " = ";
		String connector = " AND ";
		String left = " '";
		String right = "' ";

		if (isNot)
			oper = "<>";

		if (isLike) {
			oper = " LIKE ";
			left = " '%";
			right = "%' ";
			if (isNot)
				oper = " NOT LIKE ";

			switch (likeType) {
			case LikeType.LEFT_LIKE:
				left = " '";
				break;
			case LikeType.RIGHT_LIKE:
				right = "' ";
				break;
			case LikeType.ALL_LIKE:

			}
		}

		if (isOR)
			connector = " OR ";

		String[] type = { oper, connector, left, right };

		if (orderField == null) {
			orderField = idColumn;
			orderField = OrderColumnUtil.getOrderColumn(orderField, dbType);
		} else
			orderField = ORMConfigBeanUtil.getColumn(clazz, orderField);

		StringBuilder condition = new StringBuilder();
		String[] columns = ORMConfigBeanUtil.getColumns(clazz, fields);
		for (int i = 0; i < columns.length; ++i) {
			if (values != null)
				if (condition.length() > 0)
					condition.append(type[1]).append(columns[i]).append(" ")
							.append(type[0]).append(type[2]).append(values[i])
							.append(type[3]);
				else
					condition.append(columns[i]).append(" ").append(type[0])
							.append(type[2]).append(values[i]).append(type[3]);

			else {
				ReflectUtil ru = new ReflectUtil(t);
				Method getter = ru.getGetter(fields[i]);
				if (getter == null)
					continue;
				try {
					Object _value = getter.invoke(t);

					if (_value == null)
						continue;

					Object value = null;
					if (ClassUtil.isPojo(_value.getClass())) {
						Field f = ru.getField(fields[i]);
						OneToOne oneAnn = getter.getAnnotation(OneToOne.class);
						if (oneAnn == null)
							oneAnn = f.getAnnotation(OneToOne.class);

						ManyToOne manyToOneAnn = null;
						if (oneAnn == null){
							manyToOneAnn = getter.getAnnotation(ManyToOne.class);
							if (manyToOneAnn == null)
								manyToOneAnn = f.getAnnotation(ManyToOne.class);
							
						}
						
						if (oneAnn != null || manyToOneAnn != null){ 
							JoinColumn joinColAnn = getter.getAnnotation(JoinColumn.class);
							if (joinColAnn == null)
								joinColAnn = f.getAnnotation(JoinColumn.class);
							
							if (joinColAnn != null && joinColAnn.referencedColumnName().trim().length() > 0){
								String refCol = joinColAnn.referencedColumnName();
								String refField = ORMConfigBeanUtil.getColumn(_value.getClass(), refCol);
								ReflectUtil tarRu = new ReflectUtil(_value);
								Method tarFKGetter = tarRu.getGetter(refField);
								
								value = tarFKGetter.invoke(_value);
							}else{
								ReflectUtil tarRu = new ReflectUtil(_value);
								String tarFKField = ORMConfigBeanUtil.getIdField(_value.getClass());
								if (tarFKField != null){
									Method tarFKGetter = tarRu.getGetter(tarFKField);
									value = tarFKGetter.invoke(_value);
								}
							}
						}
						
						if (value == null)
							continue;
					}else
						value = _value;

					if (condition.length() > 0)
						condition.append(type[1]).append(columns[i]).append(" ").append(type[0]).append(type[2]).append(value).append(type[3]);
					else
						condition.append(columns[i]).append(" ").append(type[0]).append(type[2]).append(value).append(type[3]);
				} catch (Exception e) {
					e.printStackTrace();
					throw new SqlCreateException(getter + " invoke exception "+ e.toString());
				}
			}
		}
		String orderType = OrderType.ASC_ORDER == oType ? "ASC" : "DESC";

		if (currentPage > 0 && numPerPage > 0)
			return divPage(currentPage, numPerPage, orderField, oType, condition.toString());
		else {

			return String.format("SELECT %s FROM %s WHERE %s ORDER BY %s %s ;",
					selectAllColumn, table, condition.toString(), orderField,
					orderType);
		}
	}

	public String selectWhere(String[] fields, String[] values, int likeType,
			boolean isLike, boolean isNot, String orderField, int oType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, isLike, isNot, false,
				orderField, oType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, int likeType, boolean isLike,
			boolean isNot, boolean isOR, String orderField, int oType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, isLike, isNot, isOR,
				orderField, oType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, String[] values, int likeType,
			boolean isLike, String orderField, int oType, int currentPage,
			int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, isLike, false,
				orderField, oType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, String[] values,
			String orderField, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, orderField,
				orderType, -1, -1);
	}

	public String selectWhereNot(String[] fields, String[] values,
			String orderField, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, orderField,
				orderType, -1, -1);
	}

	public String selectWhere(String[] fields, String[] values,
			String orderField, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, orderField,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, String[] values,
			String orderField, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, orderField,
				orderType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, String orderField, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, orderField,
				orderType, -1, -1);
	}

	public String selectWhereNot(String[] fields, String orderField,
			int orderType) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, orderField,
				orderType, -1, -1);
	}

	public String selectWhere(String[] fields, String orderField,
			int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, orderField,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, String orderField,
			int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, orderField,
				orderType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, String[] values, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, null,
				orderType, -1, -1);
	}

	public String selectWhereNot(String[] fields, String[] values, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, null,
				orderType, -1, -1);
	}

	public String selectWhere(String[] fields, String[] values, int orderType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, String[] values,
			int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, null, orderType,
				-1, -1);
	}

	public String selectWhereNot(String[] fields, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, null, orderType,
				-1, -1);
	}

	public String selectWhere(String[] fields, int orderType, int currentPage,
			int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, null, orderType,
				currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, int orderType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, null, orderType,
				currentPage, numPerPage);
	}

	public String selectWhere(String[] fields, String[] values)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNot(String[] fields, String[] values)
			throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhere(String[] fields, String[] values,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, false, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, String[] values,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, 0, false, true, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhere(String... fields) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNot(String... fields) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhere(String[] fields, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, false, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereNot(String[] fields, int currentPage,
			int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, 0, false, true, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, String[] values,
			int likeType, String orderField, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false,
				orderField, orderType, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType, String orderField, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true,
				orderField, orderType, -1, -1);
	}

	public String selectWhereLike(String[] fields, String[] values,
			int likeType, String orderField, int orderType, int currentPage,
			int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false,
				orderField, orderType, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType, String orderField, int orderType, int currentPage,
			int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true,
				orderField, orderType, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, int likeType,
			String orderField, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false,
				orderField, orderType, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, int likeType,
			String orderField, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, orderField,
				orderType, -1, -1);
	}

	public String selectWhereLike(String[] fields, int likeType,
			String orderField, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false,
				orderField, orderType, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, int likeType,
			String orderField, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, orderField,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, String[] values,
			int likeType, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false, null,
				orderType, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType, int orderType) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true, null,
				orderType, -1, -1);
	}

	public String selectWhereLike(String[] fields, String[] values,
			int likeType, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType, int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, int likeType, int orderType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false, null,
				orderType, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, int likeType,
			int orderType) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, null,
				orderType, -1, -1);
	}

	public String selectWhereLike(String[] fields, int likeType, int orderType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, int likeType,
			int orderType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, null,
				orderType, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, String[] values, int likeType)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType) throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereLike(String[] fields, String[] values,
			int likeType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, false, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, String[] values,
			int likeType, int currentPage, int numPerPage)
			throws SqlCreateException {
		return this.selectWhere(fields, values, likeType, true, true, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereLike(String[] fields, int likeType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNotLike(String[] fields, int likeType)
			throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, null,
				OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereLike(String[] fields, int likeType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, false, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereNotLike(String[] fields, int likeType,
			int currentPage, int numPerPage) throws SqlCreateException {
		return this.selectWhere(fields, null, likeType, true, true, null,
				OrderType.DESC_ORDER, currentPage, numPerPage);
	}

	public String selectWhereLike(String... fields) throws SqlCreateException {
		return this.selectWhere(fields, null, LikeType.ALL_LIKE, true, false,
				null, OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNotLike(String... fields)
			throws SqlCreateException {
		return this.selectWhere(fields, null, LikeType.ALL_LIKE, true, true,
				null, OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereLike(int currentPage, int numPerPage,
			String[] fields) throws SqlCreateException {
		return this.selectWhere(fields, null, LikeType.ALL_LIKE, true, false,
				null, OrderType.DESC_ORDER, -1, -1);
	}

	public String selectWhereNotLike(int currentPage, int numPerPage,
			String[] fields) throws SqlCreateException {
		return this.selectWhere(fields, null, LikeType.ALL_LIKE, true, true,
				null, OrderType.DESC_ORDER, -1, -1);
	}

	public String selectAll(String orderField, int oType) {
		if (orderField == null) {
			orderField = idColumn;
			orderField = OrderColumnUtil.getOrderColumn(orderField, dbType);
		} else {
			orderField = ORMConfigBeanUtil.getColumn(clazz, orderField);
		}

		String orderType = OrderType.ASC_ORDER == oType ? "ASC" : "DESC";

		return String.format("SELECT %s FROM %s ORDER BY %s %s ;",
				selectAllColumn, table, orderField, orderType);
	}

	public String selectAll(int oType) {
		return selectAll(null, oType);
	}

	public String selectAll() {
		return selectAll(null, OrderType.DESC_ORDER);
	}

	public String selectOne() throws SqlCreateException {
		// 反射工具
		ReflectUtil ru = new ReflectUtil(this.t);
		StringBuilder condition = new StringBuilder();

		Method m = ru.getGetter(idField);
		if (m == null) {
			throw new SqlCreateException("can not find id getter");
		}
		try {
			condition.append(idColumn).append(" = '").append(m.invoke(this.t))
					.append("'");
		} catch (Exception e) {
			throw new SqlCreateException(m + " invoke exception "
					+ e.toString());
		}

		return String.format("SELECT * FROM %s WHERE %s ;", selectAllColumn,
				table, condition.toString());
	}

	public String nextOne() throws SqlCreateException {
		return this.nextOrPre(1, true);
	}

	public String nextOne(String orderField, int value)
			throws SqlCreateException {
		return this.nextOrPre(orderField, value, 1, true);
	}

	public String preOne(String orderField, int value)
			throws SqlCreateException {
		return this.nextOrPre(orderField, value, 1, false);
	}

	public String preOne() throws SqlCreateException {
		return this.nextOrPre(1, false);
	}

	public String nextOrPre(int num, boolean isNext) throws SqlCreateException {
		Method m = new ReflectUtil(t).getGetter(idField);
		if (m == null)
			throw new SqlCreateException("can not fine id getter");
		String idVal = "";
		try {
			idVal = String.valueOf(m.invoke(t));
		} catch (Exception e) {
			throw new SqlCreateException(m + " invoke exception "
					+ e.toString());
		}
		return nextOrPre(null, Integer.parseInt(idVal), num, isNext);
	}

	public String nextOrPre(String orderField, int offset, int num,
			boolean isNext) throws SqlCreateException {
		String oType = isNext ? "ASC" : "DESC";
		String type = isNext == true ? ">" : "<";
		if (orderField == null) {
			orderField = idColumn;
			orderField = OrderColumnUtil.getOrderColumn(orderField, dbType);
		} else {
			orderField = ORMConfigBeanUtil.getColumn(clazz, orderField);
		}

		if (DBType.MYSQL_DB.equalsIgnoreCase(dbType)) {
			return String
					.format("SELECT %s FROM %s WHERE %s %s %s ORDER BY %s %s LIMIT %s;",
							selectAllColumn, table, orderField, type, offset,
							orderField, oType, String.valueOf(num));
		} else if (DBType.MSSQL2000_DB.equalsIgnoreCase(dbType)
				|| DBType.MSSQL2005_DB.equalsIgnoreCase(dbType)) {
			return String
					.format("SELECT TOP (%s) %s FROM %s WHERE %s IN (SELECT %s FROM %s  WHERE %s %s %s ) ;",
							String.valueOf(num), selectAllColumn, table,
							orderField, orderField, table, orderField, type,
							offset);
		} else if (DBType.ORACLE_DB.equalsIgnoreCase(dbType)) {
			return String
					.format("SELECT %s FROM ( SELECT A.%s, ROWNUM RN FROM (SELECT %s FROM %s WHERE %s %s %s ORDER BY %s %s) A WHERE ROWNUM <= %s ) WHERE RN >= 1 ;",
							selectAllColumn, selectAllColumn, selectAllColumn,
							table, orderField, type, offset, orderField, oType,
							num, 1);
		} else
			throw new SqlCreateException(
					"do not support dataBase. only mysql | mssql");
	}

	public String divPage(int currPage, int numPerPage, String orderField, int oType, String condition) throws SqlCreateException {
		return this.divPage(currPage, numPerPage, orderField, clazz, oType, condition);
	}
	
	public String divPage(int currPage, int numPerPage, String orderField, Class<?> orderFieldCls, int oType, String condition) throws SqlCreateException {
		String sql = null;
		if (orderField == null) {
			orderField = idColumn;
			orderField = OrderColumnUtil.getOrderColumn(orderField, dbType);
		} else {
			orderField = ORMConfigBeanUtil.getColumn(orderFieldCls, orderField);
		}

		String orderType = OrderType.ASC_ORDER == oType ? "ASC" : "DESC";

		if (DBType.MYSQL_DB.equalsIgnoreCase(dbType)) {
			sql = "SELECT ${allColumn} FROM ${table} ${condition} ORDER BY ${orderField} ${orderType} LIMIT ${first}, ${numPerPage} ;";
			if (currPage > 0 && numPerPage > 0) {
				sql = sql.replace("${first}", String.valueOf((currPage - 1) * numPerPage));
				sql = sql.replace("${numPerPage}", String.valueOf(numPerPage));
			}
		} else if (DBType.MSSQL2000_DB.equalsIgnoreCase(dbType) || DBType.MSSQL2005_DB.equalsIgnoreCase(dbType)) {
			sql = "SELECT TOP ${numPerPage} ${allColumn} FROM ${table} WHERE ${orderField} NOT IN (SELECT TOP ${first} ${orderField} FROM ${table} ORDER BY ${orderField} ${orderType}) ${condition} ORDER BY ${orderField} ${orderType} ;";
			if (currPage > 0 && numPerPage > 0) {
				sql = sql.replace("${numPerPage}", String.valueOf(numPerPage));
				sql = sql.replace("${first}", String.valueOf(numPerPage * (currPage - 1)));
			}
		} else if (DBType.ORACLE_DB.equalsIgnoreCase(dbType)) {
			if (currPage > 0 && numPerPage > 0) {
				int max = currPage * numPerPage;
				int min = (currPage - 1) * numPerPage + 1;
				String format = "SELECT ${allColumn} FROM ( SELECT A.${allColumn}, ROWNUM RN FROM (SELECT ${allColumn} FROM ${table} ${condition} ORDER BY ${orderField} ${orderType}) A WHERE ROWNUM <= %s ) WHERE RN >= %s ;";
				sql = String.format(format, max, min);
			}
		} else {
			throw new SqlCreateException(
					"can not support dataBase. only mysql mssql");
		}

		if (currPage <= 0 || numPerPage <= 0)
			sql = "SELECT ${allColumn} FROM ${table} ${condition} ORDER BY ${orderField} ${orderType} ;";

		sql = sql.replace("${allColumn}", selectAllColumn);
		sql = sql.replace("${table}", table);
		sql = sql.replace("${orderField}", orderField);
		sql = sql.replace("${orderType}", orderType);
		if (condition != null && condition.trim().length() > 0) {
			if (DBType.MYSQL_DB.equalsIgnoreCase(dbType) || DBType.ORACLE_DB.equalsIgnoreCase(dbType)) {
				sql = sql.replace("${condition}", " WHERE " + condition);
			} else {
				sql = sql.replace("${condition}", " AND " + condition);
			}
		} else {
			sql = sql.replace("${condition}", "");
		}

		return sql;
	}

	/**
	 * 分页查询 默认id排序
	 * 
	 * @param currPage
	 * @param numPerPage
	 * @param oType
	 * @return
	 * @throws SqlCreateException
	 */
	public String divPage(int currPage, int numPerPage, int oType)
			throws SqlCreateException {
		return this.divPage(currPage, numPerPage, null, oType, null);
	}

	public String divPage(int currPage, int numPerPage, String orderField,
			int oType) throws SqlCreateException {
		return this.divPage(currPage, numPerPage, orderField, oType, null);
	}

	public String divPage(int currPage, int numPerPage, int oType,
			String condition) throws SqlCreateException {
		return this.divPage(currPage, numPerPage, null, oType, condition);
	}

	/**
	 * 分页查询 默认id排序 且降序
	 * 
	 * @param currPage
	 * @param numPerPage
	 * @return
	 * @throws SqlCreateException
	 */
	public String divPage(int currPage, int numPerPage)
			throws SqlCreateException {
		return this.divPage(currPage, numPerPage, null, OrderType.DESC_ORDER,
				null);
	}

	public String divPage(int currPage, int numPerPage, String condition)
			throws SqlCreateException {
		return this.divPage(currPage, numPerPage, null, OrderType.DESC_ORDER,
				condition);
	}

	public T getT() {
		return t;
	}

	public void setT(T t) {
		this.t = t;
	}
	
	public void setTable(String table) {
		this.table = table;
	}
}
