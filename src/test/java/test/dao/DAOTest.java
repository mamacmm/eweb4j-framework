package test.dao;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.eweb4j.config.ConfigConstant;
import org.eweb4j.config.EWeb4JConfig;
import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.dao.DAO;
import org.eweb4j.orm.dao.DAOFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import test.po.Master;
import test.po.Pet;

@SuppressWarnings("all")
public class DAOTest {
	private static DAO dao = null;

	@BeforeClass
	public static void prepare() throws Exception {
		String err = EWeb4JConfig.start("start.eweb.xml");
		if (err != null) {
			System.out.println(">>>EWeb4J Start Error --> " + err);
			System.exit(-1);
		}

		System.out.println(ConfigConstant.START_FILE_PATH());
		dao = DAOFactory.getDAO(Map.class);
	}

	@Test
	public void testCol() throws Exception{
		Assert.assertEquals("pet.master_id", ORMConfigBeanUtil.getColumn(Master.class, "pets.master"));
//		Collection<Object> ms = DAOFactory.getDAO(Master.class).enableExpress(false).select("*").join("pets").where().field("pet.name").equal("xiaohei").groupBy("pet.name").query();
//		System.out.println(ms);
		
		DAO dao = DAOFactory.getDAO(Pet.class).alias("p");
		Pet pet = dao
						.join("master", "m")
						.join("user", "u")
						.unfetch("user")
						.fetch("master")
						.select(Pet.class)
						.where()
							.field("m.name").like("wei")
							.and("p.name").likeLeft("xiao")
							.and("u.account").equal("admin")
						.groupBy("u.account")
						.queryOne();
		
		System.out.println("pet -> "+pet);
		if (pet != null){
			System.out.println("master->"+pet.getMaster());
			System.out.println("count->"+dao.count());
		}
		String sql = dao.toSql();
		
		List<Map> maps = DAOFactory.getSelectDAO().selectBySQL(Map.class, sql);
		if (maps != null) {
			for (Map<String, Object> map : maps){
				for (String key : map.keySet()){
					System.out.println(key + "=>" + map.get(key));
				}
			}
		}
	}
	
	public void testUpdate() throws Exception{
		int i = DAOFactory.getDAO(Pet.class)
				.update("name", "age")
				.set("fuckyou!", 4)
				.where()
					.field("id").equal(1)
				.execute();
		
		System.out.println("~~~~ update i = > " + i);
	}
	
	public void test() throws Exception {
		dao.setTable("t_pet");
		
		Map<String, Object> map = dao.selectAll().queryOne();
		if (map == null)
			return ;
		
		for (Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			System.out.print(key + "(" + value + "), ");
		}
		// List<Map<String, Object>> maps = dao.selectAll().where()
		// .field("department_id").equal(8).and("user_id").equal(126)
		// .query();
		//
		// for (Map<String, Object> m : maps) {
		// for (Entry<String, Object> entry : m.entrySet()) {
		// String key = entry.getKey();
		// Object value = entry.getValue();
		// System.out.print(key + "(" + value + "), ");
		// }
		// System.out.println("");
		// }
	}
	
	public void testDAO(){
		DAO dao = DAOFactory.getDAO(Pet.class);
		Pet pet = dao.clear()
					.unfetch("user")
					.selectAll()
					.where()
						.field("id").equal(5)
					.queryOne();
		//System.out.println(pet);
		System.out.println("fck!");
	}
}
