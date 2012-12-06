package test.cascade;

import java.util.List;

import org.eweb4j.config.EWeb4JConfig;
import org.eweb4j.orm.dao.DAOException;
import org.eweb4j.orm.dao.DAOFactory;
import org.eweb4j.orm.jdbc.transaction.Trans;
import org.eweb4j.orm.jdbc.transaction.Transaction;
import org.junit.Test;

import test.po.Master;
import test.po.Pet;


public class TestCascadeDAO {

	
	public static void testOneSelect() {
		List<Pet> petList;
		try {
			petList = DAOFactory.getSelectDAO().selectAll(Pet.class);
			if (petList != null) {
				for (Pet p : petList) {
					System.out.println(p + "|" + p.getMaster());
					DAOFactory.getCascadeDAO().select(p, "master");
					System.out.println(p.getMaster());
					// break;
				}
			}
		} catch (DAOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void testOneUpdate() {
		Transaction.execute(new Trans() {
			
			@Override
			public void run(Object... args) throws Exception {
				Master master = new Master();
				master.setId(1L);
				DAOFactory.getCascadeDAO().update(master, "pets", 2);
				//throw new Exception();
			}
			
		});
		
	}

	public static void testManyInsert() {
		Master master = new Master();
		master.setName("小日主人1");
		master.setGender("boy");
		long id = (Integer) DAOFactory.getInsertDAO().insert(master);
		master.setId(id);
		Pet pet = new Pet();
		pet.setName("小日1");
		pet.setType("dog");
		master.getPets().add(pet);

		pet = new Pet();
		pet.setName("小日2");
		pet.setType("cat");
		master.getPets().add(pet);

		DAOFactory.getCascadeDAO().insert(master);
	}

	public static void testManySelect() {
		List<Master> masterList;
		try {
			masterList = DAOFactory.getSelectDAO().selectAll(Master.class);
			if (masterList != null) {
				for (Master m : masterList) {
					System.out.println(m + "|" + m.getPets());
					DAOFactory.getCascadeDAO().select(m);
					System.out.println(m.getPets());
					// break;
				}
			}
		} catch (DAOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void testManyDelete() {
		List<Master> masterList;
		try {
			masterList = DAOFactory.getSelectDAO().selectAll(Master.class);
			if (masterList != null) {
				for (Master m : masterList) {
					System.out.println(m + "|" + m.getPets());
					DAOFactory.getCascadeDAO().delete(m, "pets");

				}
			}
		} catch (DAOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void testManyManyInsert() {
		// Pet pet = new Pet();
		// pet.setName("test");
		// pet.setType("dog");
		// pet.setAge(3);
		//
		// Master master = new Master();
		// master.setName("_test");
		// master.setGender("man");
		//
		// pet.getMasters().add(master);
		//
		// master = new Master();
		// master.setName("_test1");
		// master.setGender("man1");
		//
		// pet.getMasters().add(master);
		//
		// master = new Master();
		// master.setName("_test2");
		// master.setGender("man2");
		//
		// pet.getMasters().add(master);
		//
		// master = new Master();
		// master.setName("_test3");
		// master.setGender("man3");
		//
		// pet.getMasters().add(master);
		// boolean flag = DAOFactory.getCascadeDAO().insert(pet);
		// System.out.println(flag);

		Master master = new Master();
		// master.setId(36);
		master.setName("日本人");
		master.setGender("boy");
		Pet pet = new Pet();
		pet.setName("小日1");
		pet.setType("dog");
		pet.setPetId(6490L);
		master.getPets().add(pet);

		pet = new Pet();
		pet.setName("小日2");
		pet.setType("cat");
		master.getPets().add(pet);

		DAOFactory.getCascadeDAO().insert(master);
	}

	/**
	 * 测试多对多更新
	 */
	public static void testManyManyUpdate() {
		Master master = new Master().find().first();
		master.cascade().refresh(999, "pets");
	}

	public static void testManyManySelect() {
		// List<Master> masterList = DAOFactory.getSelectDAO().selectAll(
		// Master.class);
		// if (masterList != null) {
		// for (Master m : masterList) {
		// System.out.println(m + "|" + m.getPets());
		// DAOFactory.getCascadeDAO().select(m, "pets");
		// System.out.println(m.getPets());
		// break;
		// }
		// }

		// List<Pet> petList;
		// try {
		// petList = DAOFactory.getSelectDAO().selectAll(Pet.class);
		// if (petList != null) {
		// for (Pet p : petList) {
		// System.out.println(p + "|" + p.getMasters());
		// DAOFactory.getCascadeDAO().select(p);
		// System.out.println(p.getMasters());
		// break;
		// }
		// }
		// } catch (DAOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		List<Master> masterList = DAOFactory.getSelectDAO().selectAll(
				Master.class);
		if (masterList != null) {
			for (Master m : masterList) {
				System.out.println(m + "|" + m.getPets());
				DAOFactory.getCascadeDAO().select(m);
				System.out.println(m + "|" + m.getPets());
				break;
			}

		}

	}

	public static void testManyManyDelete() {
		List<Master> masterList;
		try {
			masterList = DAOFactory.getSelectDAO().selectAll(Master.class);
			if (masterList != null) {
				for (Master m : masterList) {
					System.out.println(m + "|" + m.getPets());
					Pet p = new Pet();
					p.setPetId(5L);
					m.getPets().add(p);
					DAOFactory.getCascadeDAO().delete(m, "pets");
					break;
				}
			}
		} catch (DAOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		String err = EWeb4JConfig.start("start.eweb.xml");
		if (err != null)
			throw new Exception(err);
		
		// TestCascadeDAO.testOneSelect();
		// TestCascadeDAO.testOneUpdate();
		// TestCascadeDAO.testManyInsert();
		// TestCascadeDAO.testManySelect();
		// TestCascadeDAO.testManyDelete();
		// TestCascadeDAO.testManyManyInsert();
		// TestCascadeDAO.testManyManySelect();
		// TestCascadeDAO.testManyManyDelete();
		// TestCascadeDAO.testManyManyUpdate();
		Master master = new Master().findById(1);
		System.out.println("=============== " + master);
		master.cascade().fetch("pets");
		System.out.println("===after=="+master);
		
//		master.cascade().refresh(2, "pets");
		
//		master = new Master();
//		master.setId(1);
//		Pet pet = new Pet();
//		pet.setName("edit_fuck_name");
//		pet.setNumber("testnumber");
//		master.getPets().add(pet);
//		master.cascade().persist("pets");
		Pet pet = new Pet().find().first();
		pet.setName("change_" + pet.getName());
		pet.save();
		System.out.println(pet);
		
		//master.cascade().remove("pets");
	}
	
	public void testManyOne(){
		
	}
}
