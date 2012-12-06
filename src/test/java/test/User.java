package test;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.eweb4j.orm.Model;

import test.po.Pet;

@Entity
@Table(name = "t_user")
public class User extends Model<User> {
	
	public final static User inst = new User();
	
	private String account;
	private String password;
	
	@OneToMany(mappedBy="user")
	private List<Pet> pets = new ArrayList<Pet>();

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public List<Pet> getPets() {
		return pets;
	}

	public void setPets(List<Pet> pets) {
		this.pets = pets;
	}

	@Override
	public String toString() {
		return "User [account=" + account + ", password=" + password + ", id="
				+ id + "]";
	}

}
