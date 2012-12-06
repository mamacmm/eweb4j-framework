package org.eweb4j.orm.dao.cascade;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.eweb4j.orm.Models;
import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.dao.DAOException;
import org.eweb4j.orm.dao.DAOFactory;
import org.eweb4j.orm.jdbc.transaction.Trans;
import org.eweb4j.orm.jdbc.transaction.Transaction;
import org.eweb4j.util.ClassUtil;
import org.eweb4j.util.ReflectUtil;

/**
 * 
 * @author weiwei
 * 
 */
public class OneToManyDAO {
	private String dsName;
	private Object t;
	private List<Field> fields;
	private ReflectUtil ru;
	private String idVal;
	private String table;

	public OneToManyDAO(String dsName) {
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
		this.table = ORMConfigBeanUtil.getTable(this.t.getClass(), true);
		// 主类的ID属性名
		String idField = ORMConfigBeanUtil.getIdField(this.t.getClass());
		Method idGetter = ru.getGetter(idField);
//		if (idGetter == null)
//			throw new DAOException("can not find idGetter.", null);

		Object idVal = null;
		if (idGetter != null) {
			try {
				idVal = idGetter.invoke(this.t);
				this.idVal = idVal == null ? null : String.valueOf(idVal);
			} catch (Exception e) {
				throw new DAOException(idGetter + " invoke exception ", e);
			}
		}
	}

	/**
	 * 一对多（主从）级联插入 。 
	 * 1. 如果主对象id值没有，将主对象插入数据库,否则不插入 
	 * 2. 遍历从对象，找到mappedBy 
	 * 3. 注入主对象，插入关系
	 * 4. 如果找不到mappedBy，则先找@JoinTable，然后判断次对象是否id值，如果没有就插入次对象，否则不插入，
	 * 8. 检查下是否有重复关系，接着插入关系表
	 * 5. 如果找不到@JoinTable，则根据主对象 class 在从对象属性中找。然后注入主对象，插入关系。
	 */
	public void insert() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;

		final Class<?> ownClass = ru.getObject().getClass();
		Transaction.execute(new Trans() {

			@Override
			public void run(Object... args) throws Exception {
				//检查主对象是否有ID值，若没有、插入数据库否则不用
				if (idVal == null || "0".equals(idVal) || "".equals(idVal)) {
					Number _idVal = DAOFactory.getInsertDAO(dsName).insert(t);
					if (_idVal == null || _idVal.intValue() <= 0)
						throw new Exception("can not inster the main obj into db");
				} else if (DAOFactory.getSelectDAO(dsName).selectOneById(t) == null) {
					throw new Exception("the main object'id val is invalid!");
				}
				
				String fromRefVal = null;
				for (Field f : fields) {
					Method fGetter = ru.getGetter(f.getName());
					if (fGetter == null)
						continue;

					OneToMany oneToMany = null;
					if (f.isAnnotationPresent(OneToMany.class)) {
						oneToMany = f.getAnnotation(OneToMany.class);
					} else if (fGetter.isAnnotationPresent(OneToMany.class)) {
						oneToMany = fGetter.getAnnotation(OneToMany.class);
					} else {
						continue;
					}

					Class<?> tarClass = oneToMany.targetEntity();
					if (void.class.isAssignableFrom(tarClass)) {
						tarClass = ClassUtil.getGenericType(f);
					}

					List<?> fList = null;

					try {
						fList = (List<?>) fGetter.invoke(t);
					} catch (Exception e) {
						throw new DAOException(fGetter + " invoke exception ",e);
					}

					if (fList == null)
						continue;

					for (int i = 0; i < fList.size(); i++) {
						Object tarObj = fList.get(i);
						if (tarObj == null)
							continue;

						ReflectUtil tarRu = new ReflectUtil(tarObj);
						String mappedBy = oneToMany.mappedBy();
						if (mappedBy != null && mappedBy.trim().length() > 0) {
							Method ownFieldSetter = tarRu.getSetter(mappedBy);
							if (ownFieldSetter == null)
								continue;

							// finished
							ownFieldSetter.invoke(tarObj,ru.getObject());
							Models.inst(tarObj).create();
						} else {
							JoinTable joinTable = null;
							if (f.isAnnotationPresent(JoinTable.class)) {
								joinTable = f.getAnnotation(JoinTable.class);
							} else if (fGetter.isAnnotationPresent(JoinTable.class)) {
								joinTable = fGetter.getAnnotation(JoinTable.class);
							} else {
								// find ownclass in tarObj fields
								for (Field tarObjField : tarRu.getFields()) {
									if (tarObjField.getType().getName().equals(ownClass.getName())) {
										Method ownFieldSetter = tarRu.getSetter(tarObjField.getName());
										if (ownFieldSetter == null)
											continue;

										// finished
										ownFieldSetter.invoke(tarObj,ru.getObject());
										Models.inst(tarObj).create();
										break;
									}
								}
							}
							if (joinTable != null){
								
								JoinColumn[] froms = joinTable.joinColumns();
								if (froms == null || froms.length == 0)
									continue;
								String from = froms[0].name();
								
								JoinColumn[] tos = joinTable.inverseJoinColumns();
								if (tos == null || tos.length == 0)
									continue;
								String to = tos[0].name();
								
								String relTable = joinTable.name();
								
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
								
								fromRefVal = String.valueOf(_obj);
								
								String toRefCol = tos[0].referencedColumnName();
								if (toRefCol == null || toRefCol.trim().length() == 0)
									toRefCol = ORMConfigBeanUtil.getIdColumn(tarClass);
								String toRefField = ORMConfigBeanUtil.getField(tarClass, toRefCol);
								Method toRefFieldGetter = tarRu.getGetter(toRefField);
								if (toRefFieldGetter == null)
									throw new Exception("can not find the 'to ref field -> "+toRefField+"' of "+tarClass + " 's getter method");
								
								Object _obj2 = toRefFieldGetter.invoke(t);
								if (_obj2 == null)
									continue;
								
								String	toRefVal = String.valueOf(_obj2);
								
								// 插入到关系表中
								// 先检查下是否有重复记录
								// "select {from},{to} from {relTable} where {from} = {fromRefVal} and {to} = {toRefVal} "
								String _format = "select %s, %s from %s where %s = ? and %s = ? ";
								String _sql = String.format(_format, from, to, relTable, from, to);
								if (DAOFactory.getSelectDAO(dsName).selectBySQL(Map.class, _sql, fromRefVal, toRefVal) != null)
									continue;
	
								// insert into relTable (from, to) values(?, ?) ;
								String format = "insert into %s(%s, %s) values(?, ?) ;";
								String sql = String.format(format, relTable, from, to);
								
								DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(sql, fromRefVal, toRefVal);
							}
						}
					}
				}
			}
		});

	}

	/**
	 * 
	 * 一对多（主从）级联删除 
	 * 1.前提条件必须主对象要存在于数据库中
	 * 2.检查当前主对象中的关联对象，如果关联对象为空，则删除所有与主对象有关的关联关系。
	 * 3.如果当前主对象中含有关联对象，则删除这些关联对象与主对象的关系
	 * 4.不会删除主对象
	 */
	public void delete() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;

		final Class<?> ownClass = ru.getObject().getClass();
		Transaction.execute(new Trans() {

			@Override
			public void run(Object... args) throws Exception {
				for (Field f : fields) {
					Method tarGetter = ru.getGetter(f.getName());
					if (tarGetter == null)
						continue;

					OneToMany ann = tarGetter.getAnnotation(OneToMany.class);
					if (ann == null) {
						ann = f.getAnnotation(OneToMany.class);
						if (ann == null)
							continue;
					}
					String mappedBy = ann.mappedBy();

					Class<?> tarClass = ann.targetEntity();
					if (void.class.isAssignableFrom(tarClass))
						tarClass = ClassUtil.getGenericType(f);

					List<?> tarList = null;

					try {
						tarList = (List<?>) tarGetter.invoke(t);
					} catch (Exception e) {
						throw new DAOException(
								tarGetter + " invoke exception ", e);
					}
					
					if (tarList == null || tarList.size() == 0) {
						// 当关联对象为空的时候，删除所有关联对象
						ReflectUtil tarRu = new ReflectUtil(tarClass);
						if (mappedBy == null || mappedBy.trim().length() == 0) {
							for (Field tarObjField : tarRu.getFields()) {
								if (!tarObjField.getType().getName().equals(ownClass.getName()))
									continue;
								
								Method tarObjFieldGetter = tarRu.getGetter(tarObjField.getName());
								if (tarObjFieldGetter == null)
									continue;
								
								ManyToOne manyToOne = tarObjField.getAnnotation(ManyToOne.class);
								if (manyToOne == null)
									manyToOne = tarObjFieldGetter.getAnnotation(ManyToOne.class);
								
								if (manyToOne == null)
									continue;
								
								mappedBy = tarObjField.getName(); 
								
								String fromRefCol = null;
								JoinColumn joinCol = tarObjField.getAnnotation(JoinColumn.class);
								if (joinCol == null)
									joinCol = tarObjFieldGetter.getAnnotation(JoinColumn.class);
								if (joinCol != null)
									fromRefCol = joinCol.referencedColumnName();
								
								if (fromRefCol == null || fromRefCol.trim().length() == 0)
									fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
								
								String fromRefField = ORMConfigBeanUtil.getField(ownClass, fromRefCol);
								Method fromRefFieldGetter = ru.getGetter(fromRefField);
								if (fromRefFieldGetter == null)
									throw new Exception("can not find the 'from ref field field -> "+fromRefField+"' of "+ownClass + " 's getter method");
								
								String fromRefVal = null;
								Object _obj = fromRefFieldGetter.invoke(t);
								if (_obj != null)
								  fromRefVal = String.valueOf(_obj);
							
								DAOFactory.getDeleteDAO(dsName).deleteByFieldIsValue(tarClass, new String[]{tarObjField.getName()}, new String[]{fromRefVal});
								
								break;
							}
						}

					} else {
						
						// 当关联对象不为空的时候，删除这些关联对象
						for (int i = 0; i < tarList.size(); i++) {
							Object tarObj = tarList.get(i);
							if (tarObj == null)
								continue;
							
							//如果这些对象没有ID值，跳过
							Object tarObjIdVal = ORMConfigBeanUtil.getIdVal(tarObj);
							if (tarObjIdVal == null)
								continue;
							
							ReflectUtil tarRu = new ReflectUtil(tarObj);

							if (mappedBy != null && mappedBy.trim().length() > 0) {
								Method ownFieldSetter = tarRu.getSetter(mappedBy);
								if (ownFieldSetter == null)
									continue;

								// finished
								DAOFactory.getDeleteDAO(dsName).deleteById(tarObj);
							} else {
								JoinTable joinTable = null;
								if (f.isAnnotationPresent(JoinTable.class)) {
									joinTable = f.getAnnotation(JoinTable.class);
								} else if (tarGetter.isAnnotationPresent(JoinTable.class)) {
									joinTable = tarGetter.getAnnotation(JoinTable.class);
								} else {
									// find ownclass in tarObj fields
									for (Field tarObjField : tarRu.getFields()) {
										if (!tarObjField.getType().getName().equals(ownClass.getName()))
											continue;
										
										Method tarObjFieldGetter = tarRu.getGetter(tarObjField.getName());
										if (tarObjFieldGetter == null)
											continue;
										
										ManyToOne manyToOne = tarObjField.getAnnotation(ManyToOne.class);
										if (manyToOne == null)
											manyToOne = tarObjFieldGetter.getAnnotation(ManyToOne.class);
										if (manyToOne == null)
											continue;
										
										String fromRefCol = null;
										JoinColumn joinCol = tarObjField.getAnnotation(JoinColumn.class);
										if (joinCol == null)
											joinCol = tarObjFieldGetter.getAnnotation(JoinColumn.class);
										if (joinCol != null)
											fromRefCol = joinCol.referencedColumnName();
										
										if (fromRefCol == null || fromRefCol.trim().length() == 0)
											fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
										
										String fromRefField = ORMConfigBeanUtil.getField(ownClass, fromRefCol);
										Method fromRefFieldGetter = ru.getGetter(fromRefField);
										if (fromRefFieldGetter == null)
											throw new Exception("can not find the 'from ref field field -> "+fromRefField+"' of "+ownClass + " 's getter method");
										
										String fromRefVal = null;
										Object _obj = fromRefFieldGetter.invoke(t);
										if (_obj != null)
											fromRefVal = String.valueOf(_obj);
									
										DAOFactory.getDeleteDAO(dsName).deleteByFieldIsValue(tarClass, new String[]{tarObjField.getName()}, new String[]{fromRefVal});
										
										
										break;
									}
								}
								
								if (joinTable != null){
									JoinColumn[] froms = joinTable.joinColumns();
									if (froms == null || froms.length == 0)
										continue;
									String from = froms[0].name();
									
									JoinColumn[] tos = joinTable.inverseJoinColumns();
									if (tos == null || tos.length == 0)
										continue;
									String to = tos[0].name();
									
									String relTable = joinTable.name();
									
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
									
									String toRefCol = tos[0].referencedColumnName();
									if (toRefCol == null || toRefCol.trim().length() == 0)
										toRefCol = ORMConfigBeanUtil.getIdColumn(tarClass);
									String toRefField = ORMConfigBeanUtil.getField(tarClass, toRefCol);
									Method toRefFieldGetter = tarRu.getGetter(toRefField);
									if (toRefFieldGetter == null)
										throw new Exception("can not find the 'to ref field -> "+toRefField+"' of "+tarClass + " 's getter method");
									
									Object _obj2 = toRefFieldGetter.invoke(tarObj);
									if (_obj2 == null)
										continue;
									
									String toRefVal = String.valueOf(_obj2);
	
									// delete from relTable where from = ? and to = ? ;
									String format = "delete from %s where %s = ? and %s = ? ;";
									String sql = String.format(format, relTable,from, to);
	
									// finished
									DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(sql, fromRefVal, toRefVal);
								}
							}
						}
					}

				}
			}
		});
	}

	/**
	 * 一对多（主从）级联查询
	 */
	public void select() throws DAOException {
		if (this.fields == null || this.fields.size() == 0)
			return;
		
		Class<?> ownClass = ru.getObject().getClass();
		String fromRefVal = null;
		for (Field f : fields) {
			Method tarGetter = ru.getGetter(f.getName());
			if (tarGetter == null)
				continue;

			OneToMany ann = tarGetter.getAnnotation(OneToMany.class);
			if (ann == null) {
				ann = f.getAnnotation(OneToMany.class);
				if (ann == null)
					continue;
			}
			
			OrderBy orderAnn = tarGetter.getAnnotation(OrderBy.class);
			if (orderAnn == null) 
				orderAnn = f.getAnnotation(OrderBy.class);
			
			String orderBy = "";
			if (orderAnn != null)
				orderBy = " ORDER BY "+orderAnn.value();
			
			String mappedBy = ann.mappedBy();
			
			Class<?> tarClass = ann.targetEntity();
			if (void.class.isAssignableFrom(tarClass))
				tarClass = ClassUtil.getGenericType(f);
			try {
				ReflectUtil tarRu = new ReflectUtil(tarClass);
				
				List<?> tarList = null;
				
				JoinTable joinTable = null;
				if (f.isAnnotationPresent(JoinTable.class)) {
					joinTable = f.getAnnotation(JoinTable.class);
				} else if (tarGetter.isAnnotationPresent(JoinTable.class)) {
					joinTable = tarGetter.getAnnotation(JoinTable.class);
				}
				
				// 如果用户填写了 JoinTable注解，说明是第三方表建立的关联关系，需要查询第三方表和字段才能获取到targetList
				if (joinTable != null){
					JoinColumn[] froms = joinTable.joinColumns();
					if (froms == null || froms.length == 0)
						continue;
	
					String tarTable = joinTable.name();
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
					
					fromRefVal = String.valueOf(_obj);
					
					String format = "select %s from %s where %s = ?  ;";
					String sql = String.format(format, ORMConfigBeanUtil.getSelectAllColumn(tarClass), tarTable, from) + orderBy;
	
					// finished
					tarList = DAOFactory.getSelectDAO(dsName).selectBySQL(tarClass, sql, fromRefVal);
				}else {
					// 否则的话按照ManyToOne去查询
					// 如果给定了 mappedBy，直接用这个mappedBy来获取filed，否则遍历。
					Field mappedField = null;
					if (mappedBy != null && mappedBy.trim().length() > 0){
						mappedField = tarRu.getField(mappedBy);
					}else{
						for (Field field : tarRu.getFields()) {
							if (!field.getType().getName().equals(ownClass.getName()))
								continue;
							mappedField = field;
							mappedBy = mappedField.getName(); 
							break;
						}
					}
					
					if (mappedField == null)
						throw new Exception("mapped field of " + tarClass + " not found");
					
					Method tarObjFieldGetter = tarRu.getGetter(mappedBy);
					if (tarObjFieldGetter == null)
						continue;
					
					ManyToOne manyToOne = mappedField.getAnnotation(ManyToOne.class);
					if (manyToOne == null)
						manyToOne = tarObjFieldGetter.getAnnotation(ManyToOne.class);
					if (manyToOne == null)
						continue;
					
					String fromRefCol = null;
					JoinColumn joinCol = mappedField.getAnnotation(JoinColumn.class);
					if (joinCol == null)
						joinCol = tarObjFieldGetter.getAnnotation(JoinColumn.class);
					if (joinCol != null)
						fromRefCol = joinCol.referencedColumnName();
					
					if (fromRefCol == null || fromRefCol.trim().length() == 0)
						fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
					
					String fromRefField = ORMConfigBeanUtil.getField(ownClass, fromRefCol);
					Method fromRefFieldGetter = ru.getGetter(fromRefField);
					if (fromRefFieldGetter == null)
						throw new Exception("can not find the 'from ref field field -> "+fromRefField+"' of "+ownClass + " 's getter method");
					
					Object _obj = fromRefFieldGetter.invoke(t);
					if (_obj != null)
						fromRefVal = String.valueOf(_obj);
					
					String format = "select %s from %s where %s = ?  ;";
					String sql = String.format(format, ORMConfigBeanUtil.getSelectAllColumn(tarClass), ORMConfigBeanUtil.getTable(tarClass, true), ORMConfigBeanUtil.getColumn(tarClass, mappedBy)) + orderBy;
					// finished
					tarList = DAOFactory.getSelectDAO(dsName).selectBySQL(tarClass, sql, fromRefVal);
				}
				
				if (tarList == null)
					continue;
	
				Method tarSetter = ru.getSetter(f.getName());
				if (tarSetter == null)
					continue;
				
				tarSetter.invoke(t, tarList);
			} catch (Exception e) {

				throw new DAOException("", e);
			}
		}
	}

	/**
	 * 一对多级联更新
	 */
	public void update(final long newFromRefVal) {
		if (this.fields == null || this.fields.size() == 0)
			return;

		try{
			Transaction.execute(new Trans() {
				@Override
				public void run(Object... args) throws Exception {
					Class<?> ownClass = ru.getObject().getClass();
		
					// "update {table} set {fromRefCol} = {newFromRefVal} where {fromRefCol} = {fromRefVal}
					// ; update {tarTable} set {fkCol} = {newFromRefVal} where {fkCol} = {fromRefVal}"
					String format = "update %s set %s = %s where %s = %s ;";
					String fromRefCol = null;
					String fromRefVal = null;
					
					for (Field f : fields) {
						Method tarGetter = ru.getGetter(f.getName());
						if (tarGetter == null)
							continue;

						OneToMany ann = tarGetter.getAnnotation(OneToMany.class);
						if (ann == null)
							ann = f.getAnnotation(OneToMany.class);

						if (ann == null)
							continue;
			
						Class<?> tarClass = ann.targetEntity();
						if (void.class.isAssignableFrom(tarClass))
							tarClass = ClassUtil.getGenericType(f);
			
						String mappedBy = ann.mappedBy();
			
						ReflectUtil tarRu = new ReflectUtil(tarClass);
						if (mappedBy != null && mappedBy.trim().length() > 0) {
							Method ownFieldSetter = tarRu.getSetter(mappedBy);
							if (ownFieldSetter == null)
								continue;
						} else {
							JoinTable joinTable = null;
							if (f.isAnnotationPresent(JoinTable.class)) {
								joinTable = f.getAnnotation(JoinTable.class);
							} else if (tarGetter.isAnnotationPresent(JoinTable.class)) {
								joinTable = tarGetter.getAnnotation(JoinTable.class);
							} else {
								// find ownclass in tarObj fields
								for (Field tarObjField : tarRu.getFields()) {
									if (tarObjField.getType().getName().equals(ownClass.getName())) {
										// finished
										mappedBy = tarObjField.getName(); 
										break;
									}
								}
							}
							if (joinTable != null){
								JoinColumn[] froms = joinTable.joinColumns();
								if (froms == null || froms.length == 0)
									continue;
	
								JoinColumn[] tos = joinTable.inverseJoinColumns();
								if (tos == null || tos.length == 0)
									continue;
	
								String relTable = joinTable.name();
								String from = froms[0].name();
								fromRefCol = froms[0].referencedColumnName();
								if (fromRefCol == null || fromRefCol.trim().length() == 0)
									fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
								
								String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
								Method fromRefFieldGetter = ru.getGetter(fromRefField);
								if (fromRefFieldGetter == null)
									throw new Exception("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method");
								
								Object _obj = fromRefFieldGetter.invoke(t);
								if (_obj == null)
									continue;
								
								fromRefVal = String.valueOf(_obj);
								
								// update relTable set from = ? where from = ? ;
								String _format = "update %s set %s = ? where %s = ? ;" ;
								String sql = String.format(_format, relTable, from, from);
								DAOFactory.getUpdateDAO(dsName).updateBySQLWithArgs(sql, newFromRefVal, fromRefVal);
								
								continue;
							}
						}
			
						if (fromRefCol == null || fromRefCol.trim().length() == 0)
							fromRefCol = ORMConfigBeanUtil.getIdColumn(t);
						
						String fromRefField = ORMConfigBeanUtil.getField(t.getClass(), fromRefCol);
						Method fromRefFieldGetter = ru.getGetter(fromRefField);
						if (fromRefFieldGetter == null)
							throw new Exception("can not find the 'from ref field -> "+fromRefField+"' of "+t.getClass() + " 's getter method");
						
						Object _obj = fromRefFieldGetter.invoke(t);
						if (_obj == null)
							continue;
						
						fromRefVal = String.valueOf(_obj);
						
						final String sql = String.format(format, table, fromRefCol, newFromRefVal, fromRefCol, fromRefVal);
						
						DAOFactory.getUpdateDAO(dsName).updateBySQL(sql);
						DAOFactory.getDAO(tarClass, dsName).update().set(new String[]{mappedBy}, newFromRefVal).where().field(mappedBy).equal(idVal).execute();
					}
				}
			});
		} catch (Exception e) {
			throw new DAOException("", e);
		}
	}
}
