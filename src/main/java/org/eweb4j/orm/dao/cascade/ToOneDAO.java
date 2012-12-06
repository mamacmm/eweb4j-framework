package org.eweb4j.orm.dao.cascade;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.dao.DAOException;
import org.eweb4j.orm.dao.DAOFactory;
import org.eweb4j.util.ReflectUtil;

public class ToOneDAO {
	private String dsName;
	private Object t;
	private List<Field> fields;
	private ReflectUtil ru;
	private String table;
	private String idColumn;
	private String idField;
	private String idVal;
	private Method idGetter;

	public ToOneDAO(String dsName) {
		this.dsName = dsName;
	}

	/**
	 * 初始化
	 * 
	 * @param t
	 * @param fields
	 * @throws DAOException
	 */
	public void init(Object t, List<Field> fields) throws DAOException {
		this.t = t;
		this.fields = fields;
		this.ru = new ReflectUtil(this.t);
		this.table = ORMConfigBeanUtil.getTable(t.getClass(), true);
		// 主类的ID属性名
		this.idField = ORMConfigBeanUtil.getIdField(this.t.getClass());
		this.idColumn = ORMConfigBeanUtil.getIdColumn(this.t.getClass());
		this.idGetter = ru.getGetter(idField);
//		if (idGetter == null)
//			throw new DAOException("can not find idGetter.", null);

		Object idVal;
		if (idGetter != null) {
			try {
				idVal = idGetter.invoke(this.t);
				this.idVal = idVal == null ? null : String.valueOf(idVal);
			} catch (Exception e) {
	
				throw new DAOException("", e);
			}
		}
	}

//	/**
//	 * 一对一级联更新
//	 */
//	public void update(long newIdVal) {
//		if (newIdVal <= 0 || this.fields == null || this.fields.size() == 0)
//			return;
//		if (idVal == null || "0".equals(idVal) || "".equals(idVal)) {
//			return;
//		} else if (DAOFactory.getSelectDAO(dsName).selectOne(t, this.idField) == null) {
//			// 检查一下当前对象的ID是否存在于数据库
//			return;
//		}
//		// "update {table} set {idCol} = {newIdVal} where {idCol} = {idVal}
//		//; update {tarTable} set {fkCol} = {newIdVal} where {fkCol} = {idVal}"
//		String format = "update %s set %s = %s where %s = %s ;";
//		for (Field f : fields) {
//			Method tarGetter = ru.getGetter(f.getName());
//			if (tarGetter == null)
//				continue;
//
//			OneToOne ann = tarGetter.getAnnotation(OneToOne.class);
//			if (ann == null) 
//				ann = f.getAnnotation(OneToOne.class);
//			
//			if (ann == null)
//				continue;
//			String mappedBy = ann.mappedBy();
//			String fk = null;
//			try {
//				Class<?> tarClass = ann.targetEntity();
//				if (void.class.isAssignableFrom(tarClass))
//					tarClass = f.getType();
//				
//				ReflectUtil tarRu = new ReflectUtil(tarClass);
//				Field[] tarFields = tarRu.getFields();
//				for (Field tarF : tarFields){
//					if (!f.getType().getName().equals(tarF.getType().getName()))
//						continue;
//					
//				    Method tarFGetter = tarRu.getGetter(tarF.getName());
//				    if (tarFGetter == null)
//				    	continue;
//				    
//				    OneToOne oneToOne = tarFGetter.getAnnotation(OneToOne.class);
//				    if (oneToOne == null)
//				    	oneToOne = tarF.getAnnotation(OneToOne.class);
//				    	if (oneToOne == null)
//				    		continue;
//					
//					JoinColumn joinColumn = tarFGetter.getAnnotation(JoinColumn.class);
//					if (joinColumn == null) 
//						joinColumn = tarF.getAnnotation(JoinColumn.class);
//						
//					
//					if (joinColumn == null)
//						fk = tarF.getName() + "_id";
//					else
//						fk = joinColumn.name();
//
//					String tarTable = ORMConfigBeanUtil.getTable(tarClass);
//				
//					// "update {table} set {idCol} = {newIdVal} where {idCol} = {idVal}
//					//; update {tarTable} set {fkCol} = {newIdVal} where {fkCol} = {idVal}"
//					final String sql1 = String.format(format, table, idColumn, newIdVal, idColumn, idVal);
//					final String sql2 = String.format(format, tarTable, fk, newIdVal, fk, idVal);
//					
//					Transaction.execute(new Trans() {
//						
//						@Override
//						public void run(Object... args) throws Exception {
//							DAOFactory.getUpdateDAO(dsName).updateBySQL(sql1);
//							DAOFactory.getUpdateDAO(dsName).updateBySQL(sql2);						
//						}
//					});
//					
//					break;
//				}
//			} catch (Exception e) {
//				throw new DAOException("", e);
//			}
//		}
//	}

	/**
	 * 多对一级联查询 1.获取当前idVal，然后作为条件查询出其外键值，接着通过其外键值查出主对象数据，注入到当前
	 */
	public void select() {
		if (this.fields == null || this.fields.size() == 0)
			return;
		if (idVal == null || "0".equals(idVal) || "".equals(idVal)) {
			return;
		} else if (DAOFactory.getSelectDAO(dsName).selectOne(t, this.idField) == null) {
			// 检查一下当前对象的ID是否存在于数据库
			return;
		}

		for (Field f : fields) {
			Method tarGetter = ru.getGetter(f.getName());
			if (tarGetter == null)
				continue;

			String fk = null;
			OneToOne ann = tarGetter.getAnnotation(OneToOne.class);
			if (ann == null) 
				ann = f.getAnnotation(OneToOne.class);
			
			if (ann == null){
				ManyToOne moAn = tarGetter.getAnnotation(ManyToOne.class);
				if (moAn == null)
					moAn = f.getAnnotation(ManyToOne.class);
				
				if (moAn == null)
					continue;
			}
			String refCol = null;
			JoinColumn joinCol = f.getAnnotation(JoinColumn.class);
			if (joinCol == null)
				joinCol = tarGetter.getAnnotation(JoinColumn.class);
			
			if (joinCol == null)
				fk = f.getName()+"_id";
			else{	
				fk = joinCol.name();
				refCol = joinCol.referencedColumnName();
			}
			
			Class<?> tarClass = f.getType();
			
			if (refCol == null || refCol.trim().length() == 0)
				refCol = ORMConfigBeanUtil.getIdColumn(tarClass);
			
			String refField = ORMConfigBeanUtil.getField(tarClass, refCol);

			try {
				Object _tarObj = tarGetter.invoke(t);
				Object tarObj = null;
				boolean flag = false;
				if (_tarObj != null) {
					Method refFieldGetter = new ReflectUtil(_tarObj).getGetter(refField);
					if (refFieldGetter != null && refFieldGetter.invoke(_tarObj) != null)
						tarObj = DAOFactory.getSelectDAO(dsName).selectOne(_tarObj, refField);
					else
						flag = true;
				} else
					flag = true;

				if (flag) {
					// select * from {tarTable} where {referencedColumn} = (select {fk} from {table} where {idColumn} = {idVal})
					String format = "select %s from %s where %s = (select %s from %s where %s = %s )";
					String tarTable = ORMConfigBeanUtil.getTable(tarClass, true);
					
					
					String sql = String.format(format,ORMConfigBeanUtil.getSelectAllColumn(tarClass),tarTable, refCol, fk, table, idColumn,idVal);
					List<?> tarList = DAOFactory.getSelectDAO(dsName).selectBySQL(tarClass, sql);
					if (tarList == null || tarList.size() == 0)
						continue;
					
					tarObj = tarList.get(0);
				}

				if (tarObj == null)
					continue;

				Method tarSetter = ru.getSetter(f.getName());
				tarSetter.invoke(t, tarObj);
			} catch (Exception e) {
				throw new DAOException("", e);
			}
		}
	}

}
