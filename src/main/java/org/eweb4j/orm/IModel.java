package org.eweb4j.orm;

import java.util.List;

import org.eweb4j.orm.dao.DAO;

public interface IModel<T> {

	public void setDataSourceName(String dsName) ;

	public DAO dao() ;
	
	public boolean create(String... field) ;

	public T save(String... field);

	/**
	 * 根据当前实体的ID值来删除自己
	 */
	public boolean delete() ;

	/**
	 * 根据当前实体ID值去查询数据库
	 */
	public void load() ;

	public int delete(String query, Object... params) ;

	public int deleteAll() ;

	public T findById(long id) ;

	public Query find() ;

	public Query find(String query, Object... params) ;

	public List<T> findAll() ;

	public long count() ;

	public long count(String query, Object... params) ;
	
	public Cascade cascade() ;

}
