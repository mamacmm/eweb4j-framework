package org.eweb4j.orm.dao.cascade;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.dao.DAOException;
import org.eweb4j.orm.dao.DAOFactory;
import org.eweb4j.orm.jdbc.transaction.Trans;
import org.eweb4j.orm.jdbc.transaction.Transaction;
import org.eweb4j.util.ClassUtil;
import org.eweb4j.util.ReflectUtil;

public class ManyToManyDAO {
	private String dsName;
	private Object t;
	private List<Field> fields;
	private ReflectUtil ru;
	private String table;
	private String idVal;
	

	public ManyToManyDAO(String dsName) {
		this.dsName = dsName;
	}

	public void init(Object t, List<Field> fields) throws DAOException {
		this.t = t;
		this.fields = fields;
		this.ru = new ReflectUtil(this.t);
		this.table = ORMConfigBeanUtil.getTable(this.t.getClass(), true);
		
		String idField = ORMConfigBeanUtil.getIdField(this.t.getClass());
//		Method idSetter = ru.getSetter(idField);
//		if (idSetter == null)
//			throw new DAOException("can not get idSetter.", null);

		Method idGetter = ru.getGetter(idField);
//		if (idGetter == null)
//			throw new DAOException("can not get idGetter.", null);

		if (idGetter != null){
			try {
				Object _idVal = idGetter.invoke(this.t);
				this.idVal = _idVal == null ? null : String.valueOf(_idVal);
			} catch (Exception e) {
				throw new DAOException(idGetter + " invoke exception ", e);
			}
		}
	}

	/**
	 * 多对多级联插入 
	 * 1.如果主对象没有ID值，插入数据库，否则不插入
	 * 2.如果关联对象没有ID值，插入数据库，否则不插入
	 * 3.检查下关系表是否有重复记录 
	 * 4.插入到关系表
	 */
	public void insert() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;
		
		Transaction.execute(new Trans() {
			@Override
			public void run(Object... args) throws Exception {
				//检查主对象是否有ID值，若没有、插入数据库否则不用
				if (idVal == null || "0".equals(idVal) || "".equals(idVal)) {
					Number _idVal = DAOFactory.getInsertDAO(dsName).insert(t);
					if (_idVal == null || _idVal.intValue() <= 0)
						throw new Exception("can not inster the main obj into db");
					idVal = _idVal.toString();
				} else if (DAOFactory.getSelectDAO(dsName).selectOneById(t) == null) {
					throw new Exception("the main object'id val is invalid!");
				}

				// "insert into {relTable}({from},{to}) values({fromRefVal},{toRefVal})"
				// 插入关系表
				String format = "INSERT INTO %s(%s,%s) VALUES(?,?) ";
				
				for (Field f : fields) {
					String name = f.getName();
					Method tarGetter = ru.getGetter(name);
					if (tarGetter == null)
						continue;
		
					ManyToMany ann = tarGetter.getAnnotation(ManyToMany.class);
					if (ann == null) {
						ann = f.getAnnotation(ManyToMany.class);
						if (ann == null)
							continue;
					}
		
					JoinTable join = tarGetter.getAnnotation(JoinTable.class);
					if (join == null) {
						join = f.getAnnotation(JoinTable.class);
						if (join == null)
							continue;
					}
		
					JoinColumn[] froms = join.joinColumns();
					if (froms == null || froms.length == 0)
						continue;
					
					JoinColumn[] tos = join.inverseJoinColumns();
					if (tos == null || tos.length == 0)
						continue;
		
					Class<?> tarClass = ann.targetEntity();
					if (void.class.isAssignableFrom(tarClass)) {
						tarClass = ClassUtil.getGenericType(f);
					}
					
					String relTable = join.name();
					Object _tarObj = null;
					try {
						_tarObj = tarGetter.invoke(t);
					} catch (Exception e) {
						throw new DAOException(tarGetter + " invoke exception ", e);
					}
					if (_tarObj == null)
						continue;
					
					List<?> tarList = (List<?>) _tarObj;
					for (int i = 0; i < tarList.size(); i++) {
						Object tarObj = tarList.get(i);
						String from = froms[0].name();
						String fromRefCol = froms[0].referencedColumnName();
						if (fromRefCol == null || fromRefCol.trim().length() == 0)
							fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
						
						String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
						Method fromRefFieldGetter = ru.getGetter(fromRefField);
						if (fromRefFieldGetter == null)
							throw new Exception("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method");
						
						Object _obj = fromRefFieldGetter.invoke(t);
						if (_obj == null)
							continue;
						String fromRefVal = String.valueOf(_obj);
						
						ReflectUtil tarRu = new ReflectUtil(tarObj);
						
						String to = tos[0].name();
						String toRefCol = tos[0].referencedColumnName();
						if (toRefCol == null || toRefCol.trim().length() == 0)
							toRefCol = ORMConfigBeanUtil.getIdColumn(tarClass);
						String toRefField = ORMConfigBeanUtil.getField(tarClass, toRefCol);
						Method toRefFieldGetter = tarRu.getGetter(toRefField);
						
						if (toRefFieldGetter == null)
							throw new Exception("can not find the 'to ref field -> "+toRefField+"' of "+tarClass + " 's getter method");
						
						Object _obj2 = toRefFieldGetter.invoke(tarObj);;
						if (_obj2 == null)
							continue;
						
						String toRefVal = String.valueOf(_obj2);
					
		
						// 如果目标对象不存在于数据库，则将目标对象插入到数据库
						Object tempObj = DAOFactory.getSelectDAO(dsName).selectOneById(tarObj);
						if (tempObj == null) {
							Number _tarIdVal = DAOFactory.getInsertDAO(dsName).insert(tarObj);
							if (_tarIdVal == null || _tarIdVal.intValue() <= 0)
								throw new Exception("can not insert the " + tarObj + " into db");
						}
		
						// 插入到关系表中
						// 先检查下是否有重复记录
						// "select {from},{to} from {relTable} where {from} = {fromRefVal} and {to} = {toRefVal} "
						String _format = "select %s, %s from %s where %s = ? and %s = ? ";
						String _sql = String.format(_format, from, to, relTable, from, to);
						if (DAOFactory.getSelectDAO(dsName).selectBySQL(Map.class, _sql, fromRefVal, toRefVal) != null)
							continue;
		
						// "INSERT INTO %s(%s,%s) VALUES(?,?) "
						String sql = String.format(format, relTable, from, to);
						DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(sql, fromRefVal, toRefVal);
					}
				}
			}
		});
	}

	/**
	 * 多对多级联删除 
	 * 1.如果主对象不存在与数据库，不处理 
	 * 2.否则，检查当前主对象中的关联对象，如果关联对象为空，则删除所有与主对象有关的关联关系。
	 * 3.如果当前主对象中含有关联对象，则删除这些关联对象与主对象的关系
	 * 4.不会删除主对象
	 * 
	 */
	public void delete() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;

		// "delete from {relTable} WHERE {from} = {fromRefVal} ;"
		String format = "delete from %s WHERE %s = ? ";
		for (Field f : fields) {
			Method tarGetter = ru.getGetter(f.getName());
			if (tarGetter == null)
				continue;

			ManyToMany ann = tarGetter.getAnnotation(ManyToMany.class);
			if (ann == null) {
				ann = f.getAnnotation(ManyToMany.class);
				if (ann == null)
					continue;
			}

			JoinTable join = tarGetter.getAnnotation(JoinTable.class);
			if (join == null) {
				join = f.getAnnotation(JoinTable.class);
				if (join == null)
					continue;
			}

			JoinColumn[] froms = join.joinColumns();
			if (froms == null || froms.length == 0)
				continue;

			JoinColumn[] tos = join.inverseJoinColumns();
			if (tos == null || tos.length == 0)
				continue;

			String relTable = join.name();
			String from = froms[0].name();
			String fromRefCol = froms[0].referencedColumnName();
			if (fromRefCol == null || fromRefCol.trim().length() == 0)
				fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
			
			String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
			Method fromRefFieldGetter = ru.getGetter(fromRefField);
			if (fromRefFieldGetter == null)
				throw new DAOException("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method", null);
			
			String fromRefVal = null;
			
			try{
				Object _obj = fromRefFieldGetter.invoke(t);
				if (_obj == null)
					continue;
				
				fromRefVal = String.valueOf(_obj);
			} catch (Exception e){
				throw new DAOException("can not get the from ref val of " + t.getClass(), e);
			}
			
			List<?> tarList = null;

			try {
				tarList = (List<?>) tarGetter.invoke(t);
			} catch (Exception e) {
				throw new DAOException(tarGetter + " invoke exception, can not get the " + f.getName() + " objs ", e);
			}

			if (tarList == null || tarList.size() == 0) {
				String sql = String.format(format, relTable, from);
				// 删除所有关联记录
				DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(sql, fromRefVal);
			} else {
				// 删除指定关联的记录
				Class<?> tarClass = ann.targetEntity();
				if (void.class.isAssignableFrom(tarClass)) {
					tarClass = ClassUtil.getGenericType(f);
				}
				String to = tos[0].name();
				String toRefCol = tos[0].referencedColumnName();
				if (toRefCol == null || toRefCol.trim().length() == 0)
					toRefCol = ORMConfigBeanUtil.getIdColumn(tarClass);
				String toRefField = ORMConfigBeanUtil.getField(tarClass, toRefCol);
				
				// "delete from {relTable} where {from} = {fromRefVal} and to = {toRefVal}"
				String _format = "delete from %s where %s = ? and %s = ?";
				for (int i = 0; i < tarList.size(); i++) {
					Object tarObj = tarList.get(i);
					if (tarObj == null)
						continue;
					ReflectUtil tarRu = new ReflectUtil(tarObj);
					Method toRefFieldGetter = tarRu.getGetter(toRefField);
					if (toRefFieldGetter == null)
						throw new DAOException("can not find the 'to ref field -> "+toRefField+"' of "+tarClass + " 's getter method", null);
					
					String toRefVal = null;
					try{
						Object _obj = toRefFieldGetter.invoke(tarObj);
						if (_obj == null)
							continue;
						
						toRefVal = String.valueOf(_obj);
					} catch (Exception e){
						throw new DAOException("can not get the to ref val of " + tarClass, e);
					}
					
					if (DAOFactory.getSelectDAO(dsName).selectOne(tarClass, new String[] { toRefField },new String[] { toRefVal }) == null)
						continue;

					String _sql = String.format(_format, relTable, from, to);
					DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(_sql, fromRefVal, toRefVal);
				}
			}
		}
	}


	/**
	 * 多对多级联查询 
	 * 1.当主对象没有包含任何一个关联对象时，默认查询所有与之关联的对象
	 * 2.当主对象中包含了关联对象时（含有其toRefVal值），则只查询这些关联的对象
	 * 
	 */
	public void select() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;
		// select tarClass from {tarTable} t, {relTable} r where r.to = t.toRefCol and r.from = {fromRefVal} order by r.xxx desc
		// select %s from {tarTable} where {toRefCol} in (select {to} from {relTable} where {from} = {fromRefVal} order by xxx desc)
//		String format = "SELECT %s FROM %s WHERE %s IN (SELECT %s FROM %s WHERE %s = ? %s)";
		final String format = "select %s from %s, %s r where r.%s = %s.%s and r.%s = ? %s ";
		for (Field f : fields) {
			Method tarGetter = ru.getGetter(f.getName());
			if (tarGetter == null)
				continue;

			ManyToMany ann = tarGetter.getAnnotation(ManyToMany.class);
			if (ann == null) {
				ann = f.getAnnotation(ManyToMany.class);
				if (ann == null)
					continue;
			}

			JoinTable join = tarGetter.getAnnotation(JoinTable.class);
			if (join == null) {
				join = f.getAnnotation(JoinTable.class);
				if (join == null)
					continue;
			}

			JoinColumn[] froms = join.joinColumns();
			if (froms == null || froms.length == 0)
				continue;

			JoinColumn[] tos = join.inverseJoinColumns();
			if (tos == null || tos.length == 0)
				continue;

			// 多对多关系目标Class
			Class<?> tarClass = ann.targetEntity();
			if (void.class.isAssignableFrom(tarClass)) {
				tarClass = ClassUtil.getGenericType(f);
			}
			
			String tarTable = ORMConfigBeanUtil.getTable(tarClass, true);
			
			OrderBy orderAnn = tarGetter.getAnnotation(OrderBy.class);
			if (orderAnn == null) 
				orderAnn = f.getAnnotation(OrderBy.class);
			
			String orderBy = "";
			if (orderAnn != null)
				orderBy = " ORDER BY "+orderAnn.value().replace("t.", tarClass.getSimpleName().toLowerCase()+".");
			
			// 目标类对应的数据库表Id字段
			String toRefCol = tos[0].referencedColumnName();
			if (toRefCol == null || toRefCol.trim().length() == 0)
				toRefCol = ORMConfigBeanUtil.getIdColumn(tarClass);
			
			String toRefField = ORMConfigBeanUtil.getField(tarClass, toRefCol);
			
			// 目标类在第三方关系表中的字段名
			String to = tos[0].name();
			
			// 第三方关系表
			String relTable = join.name();
			
			// 主类在第三方关系表中的字段名
			String from = froms[0].name();
			String fromRefCol = froms[0].referencedColumnName();
			if (fromRefCol == null || fromRefCol.trim().length() == 0)
				fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
			
			String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
			
			try {
				List<?> tarList = null;
				tarList = (List<?>) tarGetter.invoke(t);
				if (tarList != null && tarList.size() > 0) {
					for (int i = 0; i < tarList.size(); i++) {
						Object tarObj = tarList.get(i);
						ReflectUtil tarRu = new ReflectUtil(tarObj);
						Method toRefFieldGetter = tarRu.getGetter(toRefField);
						if (toRefFieldGetter == null)
							throw new DAOException("can not find the 'to ref field -> "+toRefField+"' of "+tarClass + " 's getter method", null);
						
						Object _obj = toRefFieldGetter.invoke(tarObj);
						if (_obj == null)
							continue;
						
						String toRefVal = String.valueOf(_obj);
						
						// 查询 select %s from {tarTable} where {tarIdColumn} = {tarIdVal}
						tarObj = DAOFactory.getSelectDAO(dsName).selectOne(tarClass, new String[] { toRefField },new String[] { toRefVal });
					}
				} else {
					// "select %s from %s, %s r where r.%s = %s.%s and r.%s = ? %s "
					// select tarClass from {tarTable} t, {relTable} r where r.to = t.toRefCol and r.from = ? order by r.xxx desc
					String sql = String.format(format,
							ORMConfigBeanUtil.getSelectAllColumn(tarClass),
							tarTable, relTable, to, tarClass.getSimpleName().toLowerCase(), toRefCol, from, orderBy);
					// 从数据库中取出与当前主对象fromRefCol关联的所有目标对象，
					Method fromRefFieldGetter = ru.getGetter(fromRefField);
					if (fromRefFieldGetter == null)
						throw new DAOException("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method", null);
					
					Object _obj = fromRefFieldGetter.invoke(t);
					String fromRefVal = null;
					if (_obj != null)
						fromRefVal = String.valueOf(_obj);
					
					tarList = DAOFactory.getSelectDAO(dsName).selectBySQL(tarClass, sql, fromRefVal);
				}

				// 并注入到当前主对象的属性中
				Method tarSetter = ru.getSetter(f.getName());

				tarSetter.invoke(t, tarList);
			} catch (Exception e) {
				e.printStackTrace();
				throw new DAOException("", e);
			}
		}
	}

	/**
	 * 一对多级联更新
	 */
	public void update(Long newFromRefVal) {
		if (this.fields == null || this.fields.size() == 0)
			return;
		
		// "update {table} set {fromRefCol} = {newFromRefVal} where {fromRefCol} = {fromRefVal}
		// ; update {relTable} set {from} = {newFromRefVal} where {from} = {fromRefVal}"
		String format = "update %s set %s = %s where %s = %s ;";
		for (Field f : fields) {
			Method tarGetter = ru.getGetter(f.getName());
			if (tarGetter == null)
				continue;

			ManyToMany ann = tarGetter.getAnnotation(ManyToMany.class);
			if (ann == null) {
				ann = f.getAnnotation(ManyToMany.class);
				if (ann == null)
					continue;
			}

			JoinTable join = tarGetter.getAnnotation(JoinTable.class);
			if (join == null) {
				join = f.getAnnotation(JoinTable.class);
				if (join == null)
					continue;
			}

			JoinColumn[] froms = join.joinColumns();
			if (froms == null || froms.length == 0)
				continue;

			// 第三方关系表
			String relTable = join.name();
			// 主类在第三方关系表中的字段名
			String from = froms[0].name();
			String fromRefCol = froms[0].referencedColumnName();
			if (fromRefCol == null || fromRefCol.trim().length() == 0)
				fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
			
			String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
			
			try {
				Method fromRefFieldGetter = ru.getGetter(fromRefField);
				if (fromRefFieldGetter == null)
					throw new DAOException("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method", null);
				
				Object _obj = fromRefFieldGetter.invoke(t);
				if (_obj == null)
					continue;
				String fromRefVal = String.valueOf(_obj);
				
				// "update {table} set {fromRefCol} = {newFromRefVal} where {fromRefCol} = {fromRefVal}
				// ; update {relTable} set {from} = {newFromRefVal} where {from} = {fromRefVal}"
				final String sql1 = String.format(format, table, fromRefCol, newFromRefVal, fromRefCol, fromRefVal);
				final String sql2 = String.format(format, relTable, from, newFromRefVal, from, fromRefVal);
				Transaction.execute(new Trans() {
					
					@Override
					public void run(Object... args) throws Exception {
						DAOFactory.getUpdateDAO(dsName).updateBySQL(sql1);
						DAOFactory.getUpdateDAO(dsName).updateBySQL(sql2);						
					}
				});
				

			} catch (Exception e) {
				throw new DAOException("", e);
			}
		}
	}
}
