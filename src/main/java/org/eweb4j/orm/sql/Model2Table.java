package org.eweb4j.orm.sql;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;

import org.eweb4j.cache.ORMConfigBeanCache;
import org.eweb4j.config.ConfigConstant;
import org.eweb4j.config.LogFactory;
import org.eweb4j.orm.PropType;
import org.eweb4j.orm.config.ORMConfigBeanUtil;
import org.eweb4j.orm.config.bean.ORMConfigBean;
import org.eweb4j.orm.config.bean.Property;
import org.eweb4j.util.ClassUtil;
import org.eweb4j.util.CommonUtil;
import org.eweb4j.util.ReflectUtil;

/**
 * 
 * @author weiwei
 *
 */
public class Model2Table {

	final static String create_table_script = "\nDROP TABLE IF EXISTS %s;\nCREATE TABLE %s(\n%s \n) ENGINE=InnoDB DEFAULT CHARSET=utf8;\n"; 
	
	public static String write(final String dataBase) {
		StringBuilder sql = new StringBuilder();
		StringBuilder manyMany = new StringBuilder();
		for (Iterator<Entry<Object, ORMConfigBean>> it = ORMConfigBeanCache.entrySet().iterator(); it.hasNext();) {
			final Entry<Object, ORMConfigBean> e = it.next();
			final ORMConfigBean ocb = e.getValue();
			final String table = ocb.getTable();
			
//			sql.append( "\n-- ----------------------------" +
//						"\n-- Created by "+ EWeb4JConfig.about() + 
//						"\n-- at "+ StringUtil.getNowTime()  +
//						"\n-- Model "+ ocb.getClazz()  +
//						"\n-- Record "+ table +
//						"\n-- ----------------------------\n");
			
			List<Property> properties = ocb.getProperty();
			StringBuilder sb = new StringBuilder();
			StringBuilder fkSb = new StringBuilder();
			final String fk = ", \n\tKEY %s (%s), \n\tCONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s)";
			StringBuilder pkSb = new StringBuilder();
			final String pk = ", \n\tPRIMARY KEY (%s)";
			StringBuilder uniqueSb = new StringBuilder();
			String unique = ", \n\tUNIQUE KEY %s (%s)";
			final String idCol = ORMConfigBeanUtil.getIdColumn(properties);
			
			for (Property p : properties) {
				if (sb.length() > 0)
					sb.append(", \n");
				
				String col = p.getColumn();
				
				String type = p.getType();
				String size = null;
				
				if (p.getSize().trim().length() > 0 && CommonUtil.isNumeric(p.getSize().trim()))
					size = String.format(" (%s) ", p.getSize());
				
				if ("1".equals(p.getUnique()) || "true".equalsIgnoreCase(p.getUnique()))
					uniqueSb.append(String.format(unique, col, col));
				
				String notNull = "";
				if ("1".equals(p.getNotNull()) || "true".equalsIgnoreCase(p.getNotNull()))
					notNull = " NOT NULL ";
				
				if ("1".equals(p.getPk()) || "true".equalsIgnoreCase(p.getPk())){
					if (pkSb.length() > 0)
						pkSb.append(",");
					
					pkSb.append(col);
				}
				
				String auto  = "";
				if ("1".equals(p.getAutoIncrement()) || "true".equalsIgnoreCase(p.getAutoIncrement()))
					auto =  " AUTO_INCREMENT ";
				
				if (PropType.ONE_ONE.equals(p.getType()) || PropType.MANY_ONE.equals(p.getType())){
					final String relTable = ORMConfigBeanUtil.getTable(p.getRelClass(), false);
					if (p.getRelProperty() == null || p.getRelProperty().trim().length() == 0)
						p.setRelProperty(ORMConfigBeanUtil.getIdField(p.getRelClass()));
					
					final String relColumn = ORMConfigBeanUtil.getColumn(p.getRelClass(), p.getRelProperty());
					fkSb.append(String.format(fk, col, col, table+"_"+col, col, relTable, relColumn));
				}
				
				sb.append("\t").append(col).append(" ").append(getType(type)).append(size == null ? "" : size).append(notNull).append(auto);
			}
			sb.append(uniqueSb.toString());
			if (pkSb.length() > 0)
				sb.append(String.format(pk, pkSb.toString()));
			
			sb.append(fkSb.toString());
			final String _sql = String.format(create_table_script, table, table, sb.toString());
			sql.append(_sql);
			try {
				Class<?> clazz = Class.forName((String)e.getKey());
				ReflectUtil ru = new ReflectUtil(clazz);
				for (Field f : ru.getFields()){
					if (!ClassUtil.isListClass(f))
						continue;
					
					String name = f.getName();
					Method getter = ru.getGetter(name);
					if (getter == null)
						continue;
					
					ManyToMany mmAnn = getter.getAnnotation(ManyToMany.class);
					
					if (mmAnn == null)
						mmAnn = f.getAnnotation(ManyToMany.class);
					
					if (mmAnn != null){
						JoinTable join = getter.getAnnotation(JoinTable.class);
						if (join == null) {
							join = f.getAnnotation(JoinTable.class);
							if (join == null)
								continue;
						}
			
						JoinColumn[] froms = join.joinColumns();
						if (froms == null || froms.length == 0)
							continue;
			
						JoinColumn[] tos = join.inverseJoinColumns();
						if (tos == null || tos.length == 0)
							continue;
						
						final String relTable = join.name();
						final String relFrom = froms[0].name();
						final String relTo = tos[0].name();
						final Class<?> targetClass = ClassUtil.getGenericType(f);
						
//						manyMany.append( "\n-- ----------------------------" +
//								"\n-- Created by "+ EWeb4JConfig.about() + 
//								"\n-- at "+ StringUtil.getNowTime()  +
//								"\n-- Records of "+ relTable +
//								"\n-- ----------------------------\n");
//						
						StringBuilder manyManyField = new StringBuilder();
						//handle the many to many 
						manyManyField.append("\tid ").append(getType("long")).append(" (20) NOT NULL AUTO_INCREMENT,");
						manyManyField.append("\n\t").append(relFrom).append(" ").append(getType("long")).append(" (20) ,");
						manyManyField.append("\n\t").append(relTo).append(" ").append(getType("long")).append(" (20) ,");
						manyManyField.append("\n\t").append("PRIMARY KEY (id)");
						String tarTable = ORMConfigBeanUtil.getTable(targetClass, false);
						String tarIdCol = ORMConfigBeanUtil.getIdColumn(targetClass);
						
						String fk1 = String.format(fk, relFrom, relFrom, relTable + "_" + relFrom, relFrom, table, idCol);
						manyManyField.append(fk1);
						
						String fk2 = String.format(fk, relTo, relTo, relTable + "_" + relTo, relTo, tarTable, tarIdCol);
						manyManyField.append(fk2);
						
						manyMany.append(String.format(create_table_script, relTable, relTable, manyManyField.toString()));
					}
				}
			} catch (Exception e1) {
				continue;
			} 
		}
		
		sql.append(manyMany.toString());
		
		File file = new File(ConfigConstant.CONFIG_BASE_PATH()+ dataBase + "-create.sql");
		try {
			
			FileWriter writer = new FileWriter(file);
			// "DROP DATABASE IF EXISTS %s;\nCREATE DATABASE %s DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;\n"
			String script = String.format("SET FOREIGN_KEY_CHECKS=0;\n%s", sql.toString());
			writer.write(script);
			writer.flush();
			writer.close();
			LogFactory.getConfigLogger(Model2Table.class).debug("create models sql script file success -> " + file.getAbsoluteFile());
			
		} catch (IOException e1) {
			return CommonUtil.getExceptionString(e1);
		} 
		
		return null;
	}

	private static String getType(String type) {
		if ("int".equalsIgnoreCase(type) || "java.lang.Integer".equalsIgnoreCase(type)) 
			return "tinyint";
		
		if ("long".equalsIgnoreCase(type) || "java.lang.Long".equalsIgnoreCase(type)) 
			return "bigint";
		
		if ("float".equalsIgnoreCase(type) || "java.lang.Float".equalsIgnoreCase(type)) 
			return "float";
		
		if ("double".equalsIgnoreCase(type) || "java.lang.Double".equalsIgnoreCase(type)) 
			return "double";
		
		if ("string".equalsIgnoreCase(type) || "java.lang.String".equalsIgnoreCase(type)) 
			return "varchar";
		
		if ("boolean".equalsIgnoreCase(type) || "java.lang.Boolean".equalsIgnoreCase(type))
			return "boolean";
		
		if ("date".equalsIgnoreCase(type) || "java.sql.Date".equalsIgnoreCase(type) || "java.util.Date".equalsIgnoreCase(type)) 
			return "datetime";
		
		if (PropType.MANY_ONE.equals(type))
			return "bigint";
		
		if (PropType.ONE_ONE.equals(type))
			return "bigint";

		return type;
	}

}
