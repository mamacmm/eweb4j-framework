package org.eweb4j.orm;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.eweb4j.orm.dao.DAO;

@SuppressWarnings("all")
@MappedSuperclass
public class Model<T>{
	
	@Id
	@Column
	@GeneratedValue
	protected Long id = null;
	
	protected ModelHelper<T> helper = new ModelHelper(this);

	public void setDataSourceName(String dsName) {
		this.helper.setDataSourceName(dsName);
	}

	public DAO dao() {
		return this.helper.dao();
	}
	
	public boolean create(String... field) {
		return this.helper.create(field);
	}

	public T save(String... field) {
		return this.helper.save(field);
	}

	/**
	 * 根据当前实体的ID值来删除自己
	 */
	public boolean delete() {
		return this.helper.delete();
	}

	/**
	 * 根据当前实体ID值去查询数据库
	 */
	public void load() {
		this.helper.load();
	}

	public int delete(String query, Object... params) {
		return this.helper.delete(query, params);
	}

	public int deleteAll() {
		return this.helper.deleteAll();
	}

	public T findById(long id) {
		return this.helper.findById(id);
	}

	public Query find() {
		return this.helper.find();
	}

	public Query find(String query, Object... params) {
		return this.helper.find(query, params);
	}

	public List<T> findAll() {
		return this.helper.findAll();
	}

	public long count() {
		return this.helper.count();
	}

	public long count(String query, Object... params) {
		return this.helper.count(query, params);
	}
	
	public Cascade cascade(){
		return this.helper.cascade();
	}

	public Long getId() {
		return this.id;
	}

	public void setId(long id) {
		this.id = id;
	}

}
