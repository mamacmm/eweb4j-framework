package test.props;

import junit.framework.Assert;

import org.eweb4j.cache.Props;
import org.eweb4j.config.EWeb4JConfig;
import org.junit.BeforeClass;
import org.junit.Test;


public class TestProps {
	
	@BeforeClass
	public static void prepare() throws Exception {
		String err = EWeb4JConfig.start("start.eweb.xml");
		if (err != null){
			System.out.println(">>>EWeb4J Start Error --> " + err);
			System.exit(-1);
		}
	}

	@Test
	public void testVarable() throws Exception{
		Assert.assertEquals("eweb4j/hello", Props.get("TEST"));
		//测试变量嵌套引用
		Assert.assertEquals("FUCK!", Props.get("K4"));
		Assert.assertEquals("FUCK!+3", Props.get("K3"));
		Assert.assertEquals("FUCK!+3+2", Props.get("K2"));
		Assert.assertEquals("FUCK!+3+2+1", Props.get("K1"));
	}
}
