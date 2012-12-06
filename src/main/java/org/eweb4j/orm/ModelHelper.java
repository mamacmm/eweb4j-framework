package org.eweb4j.orm;

import java.lang.reflect.Method;
import java.util.List;

import org.eweb4j.orm.annotation.Ignore;
import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.dao.DAO;
import org.eweb4j.orm.dao.DAOFactory;
import org.eweb4j.util.ReflectUtil;

@SuppressWarnings("all")
public class ModelHelper<T> implements IModel<T>{

	private T model;
	private ReflectUtil ru ;
	
	public ModelHelper(T model){
		this.model = model;
		this.ru = new ReflectUtil(this.model);
	}
	
	@Ignore
	private String dsName = null;

	public void setDataSourceName(String dsName) {
		this.dsName = dsName;
	}

	public DAO dao() {
		return DAOFactory.getDAO(this.model.getClass(), this.dsName);
	}
	
	public boolean create(String... field) {
//		Long _id = _getId();
//		if (_id != null && _id > 0)
//			return false;
		Number id;
		if (field != null && field.length > 0)
			id = DAOFactory.getInsertDAO(this.dsName).insertByField(this.model, field);
		else
			id = DAOFactory.getInsertDAO(this.dsName).insert(this.model);
		
		if (id == null || (Integer) id == -1)
			return false;

		_setId(Long.parseLong(id + ""));
		return true;
	}

	public T save(String... field) {
		Long id = _getId();
		if (id != null && id > 0)
			if (field != null && field.length > 0)
				DAOFactory.getUpdateDAO(this.dsName).updateByFields(this.model, field);
			else
				DAOFactory.getUpdateDAO(this.dsName).update(this.model);
		else
			create(field);

		return model;
	}

	/**
	 * 根据当前实体的ID值来删除自己
	 */
	public boolean delete() {
		Long id = _getId();
		if (id == null || id <= 0)
			return false;

		Number rows = DAOFactory.getDeleteDAO(dsName).deleteById(this.model);
		if (rows == null || (Integer) rows == -1)
			return false;

		return true;
	}

	/**
	 * 根据当前实体ID值去查询数据库
	 */
	public void load() {
		Long id = _getId();
		if (id == null || id <= 0)
			return ;

		ReflectUtil ru = new ReflectUtil(this.model);
		T _model = DAOFactory.getSelectDAO(this.dsName).selectOneById(this.model);
		if (_model == null)
			return ;

		ReflectUtil _ru = new ReflectUtil(_model);
		for (String field : ru.getFieldsName()) {
			Method setter = ru.getSetter(field);
			if (setter == null)
				continue;

			Method _getter = _ru.getGetter(field);
			if (_getter == null)
				continue;

			try {
				setter.invoke(this.model, _getter.invoke(_model));
			} catch (Exception e) {
				continue;
			}
		}

		// ToOne relation class cascade select
		final String[] fields = ORMConfigBeanUtil.getToOneField(this.model.getClass());
		if (fields != null && fields.length > 0)
			DAOFactory.getCascadeDAO(this.dsName).select(this.model, fields);
	}

	public int delete(String query, Object... params) {
		return (Integer) DAOFactory.getDeleteDAO(this.dsName).deleteWhere(this.model.getClass(), query, params);
	}

	public int deleteAll() {
		return DAOFactory.getDAO(this.model.getClass(), this.dsName).delete().execute();
	}

	public T findById(long id) {
		T t = (T) DAOFactory.getSelectDAO(this.dsName).selectOneById(this.model.getClass(), id);
		
		// ToOne relation class cascade select
		final String[] fields = ORMConfigBeanUtil.getToOneField(this.model.getClass());
		if (fields != null && fields.length > 0)
			DAOFactory.getCascadeDAO(this.dsName).select(t, fields);
		
		return t;
	}

	public Query find() {
		Class<?> clazz = this.model.getClass();
		DAO dao = DAOFactory.getDAO(clazz, this.dsName);
		dao.selectAll();
		Query _query = new QueryImpl(dao);

		return _query;
	}

	public Query find(String query, Object... params) {
		Class<?> clazz = this.model.getClass();
		DAO dao = DAOFactory.getDAO(clazz, this.dsName);

		dao.selectAll().where().append(query).fillArgs(params);
		Query _query = new QueryImpl(dao);

		return _query;
	}

	public List<T> findAll() {
		return (List<T>) find().fetch();
	}

	public long count() {
		return DAOFactory.getSelectDAO().selectCount(this.model.getClass());
	}

	public long count(String query, Object... params) {
		return DAOFactory.getSelectDAO(this.dsName).selectCount(this.model.getClass(), query,params);
	}
	
	public Cascade cascade(){
		return new CascadeImpl<T>(DAOFactory.getCascadeDAO(dsName), this.model);
	}

	public Long _getId() {
		try {
			Object _id = ORMConfigBeanUtil.getIdVal(model);
			if (_id == null)
				return null;
			return (Long)_id ;
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		return null;
	}

	public void _setId(long id) {
		String idField = ORMConfigBeanUtil.getIdField(model);
		if (idField == null)
			return ;
		try {
			ru.getSetter(idField).invoke(model, id);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	public T getModel() {
		return model;
	}

	public void setModel(T model) {
		this.model = model;
	}

	public String getDsName() {
		return dsName;
	}

	public void setDsName(String dsName) {
		this.dsName = dsName;
	}
}
