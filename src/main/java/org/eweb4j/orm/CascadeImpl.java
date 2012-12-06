package org.eweb4j.orm;

import org.eweb4j.orm.dao.cascade.CascadeDAO;

public class CascadeImpl<T> implements Cascade<T>{

	private CascadeDAO cascadeDAO;
	private T model;
	
	CascadeImpl(CascadeDAO cascadeDAO, T model){
		this.cascadeDAO = cascadeDAO;
		this.model = model;
	}
	
	public T fetch(String... fields) {
		this.cascadeDAO.select(model, fields);
		return model;
	}

	public void refresh(long newIdVal, String... fields) {
		this.cascadeDAO.update(model, newIdVal, fields);
	}

	public void remove(String... fields) {
		this.cascadeDAO.delete(model, fields);
	}

	public void persist(String... fields) {
		this.cascadeDAO.insert(model, fields);
	}

}
