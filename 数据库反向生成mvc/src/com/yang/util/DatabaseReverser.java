package com.yang.util;

/**
* @author 杨成 E-mail:yangcheng@wiswit.com
* @version 创建时间：2017年4月5日 下午3:52:31
* 类说明
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 通过数据库表结构生成表对应的实体类以及根据action、service和dao模板生成基本的相关文件
 */
public class DatabaseReverser {

	private Config config; // 配置类, 必须

	private String LINE_SEPARATOR; // 换行符

	public DatabaseReverser(Config config) {
		this.config = config;
		this.LINE_SEPARATOR = System.getProperty("line.separator");
	}

	/**
	 * 主方法，反转生成相关文件
	 * 
	 * @throws IOException
	 */
	public void doReverser() throws IOException {
		System.out.println("Generating...");

		List<Table> tables = getAllTables();
		Table table = null;
		for (int i = 0; i < tables.size(); i++) {
			table = tables.get(i);

			// 生成实体类
			generateEntityFile(table);

			// 判断生成RowMapper文件
			if (config.isGenerateEntityRowMapperFile()) {
				generateEntityRowMapperFile(table);
			}

			// 判断生成Action、Service、Dao文件
			if (config.isGenerateBaseTemplateFile()) {
				generateASDFiles(table.getTableName());
			}
		}

		System.out.println("Generate Success!");
		System.out.println("Please check: " + config.getBaseDir());
	}

	/*
	 * 连接数据库获取所有表信息
	 */
	private List<Table> getAllTables() {
		List<Table> tables = new ArrayList<Table>();

		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			Class.forName(config.getDriverName());
			con = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());

			// 获取所有表名
			String showTablesSql = "";
			if (!"".equals(config.getQueryTableNamesSql().trim())) {
				showTablesSql = config.getQueryTableNamesSql();
			} else if (config.getDriverName().toLowerCase().indexOf("mysql") != -1) {
				showTablesSql = "show tables"; // MySQL查询所有表格名称命令
			} else if (config.getDriverName().toLowerCase().indexOf("sqlserver") != -1) {
				showTablesSql = "SELECT TABLE_NAME FROM edp.INFORMATION_SCHEMA.TABLES Where TABLE_TYPE='BASE TABLE'"; // SQLServer查询所有表格名称命令
			} else if (config.getDriverName().toLowerCase().indexOf("oracle") != -1) {
				showTablesSql = "select table_name from user_tables"; // ORACLE查询所有表格名称命令
			}else if(config.getDriverName().toLowerCase().indexOf("postgresql") != -1){
				showTablesSql = "SELECT tablename FROM pg_tables WHERE tablename NOT LIKE 'pg%' AND tablename NOT LIKE 'sql_%'"; // postgresql查询所有表格名称命令
			}

			ps = con.prepareStatement(showTablesSql);
			rs = ps.executeQuery();

			// 循环生成所有表的表信息
			while (rs.next()) {
				if (rs.getString(1) == null)
					continue;
				tables.add(getTable(rs.getString(1).trim(), con));
			}

			rs.close();
			ps.close();
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return tables;
	}

	/*
	 * 获取指定表信息并封装成Table对象
	 * 
	 * @param tableName
	 * 
	 * @param con
	 */
	private Table getTable(String tableName, Connection con) throws SQLException {
		Table table = new Table();
		table.setTableName(
				config.isDeleteTablePrefix() ? tableName.replaceFirst(config.getTablePrefix(), "") : tableName);

		PreparedStatement ps = con.prepareStatement(" SELECT * FROM " + tableName);
		ResultSet rs = ps.executeQuery();
		ResultSetMetaData rsmd = rs.getMetaData();

		int columCount = rsmd.getColumnCount();
		for (int i = 1; i <= columCount; i++) {
			table.getColumNames().add(rsmd.getColumnName(i).trim());
			table.getColumTypes().add(rsmd.getColumnTypeName(i).trim());
		}

		rs.close();
		ps.close();

		return table;
	}

	/*
	 * 将数据库的数据类型转换为java的数据类型
	 */
	private String convertType(String databaseType) {
		String javaType = "";

		String databaseTypeStr = databaseType.trim().toLowerCase();
		if (databaseTypeStr.equals("int")) {
			javaType = "Integer";
		}else if (databaseTypeStr.equals("int4")) {
			javaType = "Integer";
		}  else if (databaseTypeStr.equals("char")) {
			javaType = "String";
		} else if (databaseTypeStr.equals("text")) {
			javaType = "String";
		}  else if (databaseTypeStr.equals("number")) {
			javaType = "Integer";
		} else if (databaseTypeStr.indexOf("varchar") != -1) {
			javaType = "String";
		} else if (databaseTypeStr.equals("blob")) {
			javaType = "Byte[]";
		} else if (databaseTypeStr.equals("float")) {
			javaType = "Float";
		} else if (databaseTypeStr.equals("double")) {
			javaType = "Double";
		} else if (databaseTypeStr.equals("decimal")) {
			javaType = "BigDecimal";
		} else if (databaseTypeStr.equals("bigint")) {
			javaType = "Long";
		} else if (databaseTypeStr.equals("date")) {
			javaType = "String";
		} else if (databaseTypeStr.equals("time")) {
			javaType = "String";
		} else if (databaseTypeStr.equals("datetime")) {
			javaType = "String";
		} else if (databaseTypeStr.equals("timestamp")) {
			javaType = "String";
		} else if (databaseTypeStr.equals("year")) {
			javaType = "String";
		} else {
			javaType = "[unconverted]" + databaseType;
		}

		return javaType;
	}

	/*
	 * 生成指定表对象对应的类文件
	 * 
	 * @param table
	 */
	private void generateEntityFile(Table table) {
		String tableName = table.getTableName();
		List<String> columNames = table.getColumNames();
		List<String> columTypes = table.getColumTypes();

		// 表名对应的实体类名
		String entityName = convertToCamelCase(tableName);

		// 生成无参构造方法
		String constructorStr = "\t" + "public " + entityName + "() {}" + LINE_SEPARATOR; // 无参构造方法

		// 生成重写的toString方法
		String propertyName = ""; // 属性名
		String toStringStr = "\tpublic String toString() { " + LINE_SEPARATOR + "\t\treturn "; // toString方法
		for (int i = 0; i < columNames.size(); i++) {
			propertyName = convertToFirstLetterLowerCaseCamelCase(columNames.get(i));
			if (i == 0) {
				toStringStr += "\"" + propertyName + ":\" + " + propertyName;
			} else {
				toStringStr += "\", " + propertyName + ":\" + " + propertyName;
			}
			if ((i + 1) != columNames.size()) {
				toStringStr += " + ";
			}
		}
		toStringStr += ";" + LINE_SEPARATOR + "\t}" + LINE_SEPARATOR;

		// 生成私有属性和get、set方法
		String propertiesStr = ""; // 私有属性字符串
		String getterSetterStr = ""; // get、set方法字符串
		String getterAnnotation = ""; // 申明在getter方法上的属性与数据表列对应的注解
		String getterSetterPropertyNameStr = "";
		String javaType = ""; // 数据库对应的java类型
		for (int i = 0; i < columNames.size(); i++) {
			String columName = columNames.get(i);
			String columType = columTypes.get(i);

			propertyName = convertToFirstLetterLowerCaseCamelCase(columName);
			getterSetterPropertyNameStr = convertToCamelCase(columName);
			javaType = convertType(columType);

			propertiesStr += "\t" + "private " + javaType + " " + propertyName + ";" + LINE_SEPARATOR;

			if (config.isGenerateEntityAnnotation())
				getterAnnotation = LINE_SEPARATOR + "\t" + "@Column(name = \"" + columName + "\")" + LINE_SEPARATOR;

			getterSetterStr += getterAnnotation + "\t" + "public " + javaType + " get" + getterSetterPropertyNameStr
					+ "() {" + LINE_SEPARATOR + "\t\t" + "return this." + propertyName + ";" + LINE_SEPARATOR + "\t}"
					+ LINE_SEPARATOR + LINE_SEPARATOR + "\t" + "public void set" + getterSetterPropertyNameStr + "("
					+ javaType + " " + propertyName + ") {" + LINE_SEPARATOR + "\t\t" + "this." + propertyName + " = "
					+ propertyName + ";" + LINE_SEPARATOR + LINE_SEPARATOR + "\t}" + LINE_SEPARATOR + LINE_SEPARATOR;
		}

		// 生成实体类文件
		String entitySaveDir = config.getBaseDir() + File.separator + "src" + File.separator + "entity"
				+ File.separator;
		File folder = new File(entitySaveDir);
		if (!folder.exists()) {
			folder.mkdirs();
		}

		String realTableName = config.isDeleteTablePrefix() ? config.getTablePrefix() + tableName : tableName;
		File entityFile = new File(entitySaveDir + entityName + ".java");
		BufferedWriter bw;
		try {
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entityFile)));
			if (config.isGenerateEntityAnnotation())
				bw.write("@Entity" + LINE_SEPARATOR + "@Table(name = \"" + realTableName + "\")" + LINE_SEPARATOR);
			bw.write("public class " + entityName + " {" + LINE_SEPARATOR);
			bw.write(propertiesStr);
			bw.write(LINE_SEPARATOR);
			bw.write(constructorStr);
			bw.write(LINE_SEPARATOR);
			bw.write(toStringStr);
			bw.write(LINE_SEPARATOR);
			bw.write(getterSetterStr);
			bw.write(LINE_SEPARATOR);
			bw.write("}");
			bw.flush();
			bw.close();
		} catch (Exception e) {
			System.out.println("生成类文件(" + entityName + ")出错！");
			e.printStackTrace();
		}

	}

	/*
	 * 生成指定表对象对应的类文件的RowMapper文件
	 * 
	 * @param table
	 */
	private void generateEntityRowMapperFile(Table table) {
		String tableName = table.getTableName();
		List<String> columNames = table.getColumNames();
		List<String> columTypes = table.getColumTypes();

		// 表名对应的实体类RowMapper名
		String entityName = convertToCamelCase(tableName);
		String entityNameLC = convertToFirstLetterLowerCaseCamelCase(tableName); // LC:
																					// first
																					// letter
																					// lower
																					// case
		String rowMapperName = entityName + "RowMapper";

		// 生成set方法
		String setterStr = ""; // get、set方法字符串
		String setterPropertyNameStr = "";
		String javaType = ""; // 数据库对应的java类型
		for (int i = 0; i < columNames.size(); i++) {
			String columName = columNames.get(i);
			String columType = columTypes.get(i);

			setterPropertyNameStr = convertToCamelCase(columName);
			javaType = convertType(columType);

			setterStr += "\t\t" + entityNameLC + ".set" + setterPropertyNameStr + "(rs.get" + javaType + "(\""
					+ columName + "\"));" + LINE_SEPARATOR;
		}

		// 生成实体类文件
		String entitySaveDir = config.getBaseDir() + File.separator + "src" + File.separator + "entity"
				+ File.separator;
		File folder = new File(entitySaveDir);
		if (!folder.exists()) {
			folder.mkdirs();
		}

		File entityFile = new File(entitySaveDir + rowMapperName + ".java");
		BufferedWriter bw;
		try {
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entityFile)));
			bw.write("public class " + rowMapperName + " implements RowMapper {" + LINE_SEPARATOR + LINE_SEPARATOR);
			bw.write("\tpublic Object mapRow(ResultSet rs, int index) throws SQLException {" + LINE_SEPARATOR);
			bw.write("\t\t" + entityName + " " + entityNameLC + " = new " + entityName + "();" + LINE_SEPARATOR);
			bw.write(setterStr);
			bw.write(LINE_SEPARATOR);
			bw.write("\t\treturn " + entityNameLC + ";" + LINE_SEPARATOR);
			bw.write("\t}" + LINE_SEPARATOR + LINE_SEPARATOR);
			bw.write("}" + LINE_SEPARATOR);
			bw.flush();
			bw.close();
		} catch (Exception e) {
			System.out.println("生成RowMapper文件(" + rowMapperName + ")出错！");
			e.printStackTrace();
		}

	}

	/*
	 * 生成Action、Service、Dao文件
	 */
	private void generateASDFiles(String tableName) throws IOException {
		String entityName = convertToCamelCase(tableName);

		Map<String, String> replaces = new HashMap<String, String>();
		replaces.put("#REPLACE_TABLE_NAME#", tableName);
		replaces.put("#REPLACE_TABLE_PREFIX#", config.getTablePrefix());
		replaces.put("#REPLACE_ENTITY_NAME#", entityName);
		replaces.put("#REPLACE_ENTITY_NAME_LC#", convertToFirstLetterLowerCaseCamelCase(tableName));
		replaces.put("#REPLACE_NOW_TIME#", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

		String templatePathName;
		String newFilePath;
		String newFileName;

		// 生成action
		templatePathName = config.getTemplateDir() + File.separator + config.getActionTemplateName();
		newFilePath = config.getBaseDir() + File.separator + "src" + File.separator + "action" + File.separator;
		newFileName = config.getNewActionFileName().replaceAll("#REPLACE_ENTITY_NAME#", entityName); // entityName
																										// +
																										// "Action.java";
		replaceFileContent(templatePathName, replaces, newFilePath, newFileName);

		// 生成servicd接口
		templatePathName = config.getTemplateDir() + File.separator + config.getServiceInterfaceTemplateName();
		newFilePath = config.getBaseDir() + File.separator + "src" + File.separator + "service" + File.separator;
		newFileName = config.getNewServiceInterfaceFileName().replaceAll("#REPLACE_ENTITY_NAME#", entityName); // "I"
																												// +
																												// entityName
																												// +
																												// "Service.java";
		replaceFileContent(templatePathName, replaces, newFilePath, newFileName);

		// 生成servic
		templatePathName = config.getTemplateDir() + File.separator + config.getServiceImplTemplateName();
		newFilePath = config.getBaseDir() + File.separator + "src" + File.separator + "service" + File.separator
				+ "impl" + File.separator;
		newFileName = config.getNewServiceImplFileName().replaceAll("#REPLACE_ENTITY_NAME#", entityName); // entityName
																											// +
																											// "ServiceImpl.java";
		replaceFileContent(templatePathName, replaces, newFilePath, newFileName);

		// 生成dao接口
		templatePathName = config.getTemplateDir() + File.separator + config.getDaoInterfaceTemplateName();
		newFilePath = config.getBaseDir() + File.separator + "src" + File.separator + "dao" + File.separator;
		newFileName = config.getNewDaoInterfaceFileName().replaceAll("#REPLACE_ENTITY_NAME#", entityName); // "I"
																											// +
																											// entityName
																											// +
																											// "Dao.java";
		replaceFileContent(templatePathName, replaces, newFilePath, newFileName);

		// 生成dao
		templatePathName = config.getTemplateDir() + File.separator + config.getDaoImplTemplateName();
		newFilePath = config.getBaseDir() + File.separator + "src" + File.separator + "dao" + File.separator + "impl"
				+ File.separator;
		newFileName = config.getNewDaoImplFileName().replaceAll("#REPLACE_ENTITY_NAME#", entityName); // entityName
																										// +
																										// "DaoJdbcImpl.java";
		replaceFileContent(templatePathName, replaces, newFilePath, newFileName);

	}

	/*
	 * 替换文件内容并保存
	 * 
	 * @param oldFilePathName
	 * 
	 * @param replaces
	 * 
	 * @param newFilePathName
	 * 
	 * @throws IOException
	 */
	private void replaceFileContent(String oldFilePathName, Map<String, String> replaces, String newFilePath,
			String newFileName) throws IOException {
		// 读取文件内容
		StringBuffer contentBuffer = new StringBuffer("");
		@SuppressWarnings("resource")
		BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(oldFilePathName), config.getEncode()));
		String line = "";
		while ((line = br.readLine()) != null) {
			contentBuffer.append(line + System.getProperty("line.separator"));
		}

		String content = contentBuffer.toString(); // 原内容

		// 替换内容
		if (replaces.keySet() != null) {
			@SuppressWarnings("rawtypes")
			Iterator it = replaces.keySet().iterator();
			String oldStr = "";
			String newStr = "";
			while (it.hasNext()) {
				oldStr = (String) it.next();
				newStr = replaces.get(oldStr);
				content = content.replaceAll(oldStr, newStr);
			}
		}

		// 创建路径
		File folder = new File(newFilePath);
		if (!folder.exists()) {
			folder.mkdirs();
		}

		// 保存替换后的文件
		FileWriter writer = new FileWriter(newFilePath + File.separator + newFileName, false);
		writer.write(content);
		writer.flush();
		writer.close();
	}

	/*
	 * 表名转换为驼峰命名
	 */
	private String convertToCamelCase(String str) {
		String result = "";

		String[] strArr = str.trim().split("_");
		for (String s : strArr) {
			if (s.length() > 1) {
				result += s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
			} else {
				result += s.toUpperCase();
			}
		}

		return result;
	}

	/*
	 * 表名转换为首字母小写的驼峰命名
	 */
	private String convertToFirstLetterLowerCaseCamelCase(String str) {
		String resultCamelCase = convertToCamelCase(str);

		String result = "";
		if (resultCamelCase.length() > 1) {
			result = resultCamelCase.substring(0, 1).toLowerCase() + resultCamelCase.substring(1);
		} else {
			result = resultCamelCase.toLowerCase();
		}

		return result;
	}


}

/**
 * 配置类, 包含程序所需的相关信息
 * 
 * @author yanhang0610
 */
class Config {
	private String encode = "UTF-8"; // 字符编码

	private String baseDir = System.getProperty("user.dir") + File.separator + "DatabaseReverser" + File.separator; // 文件生成保存的根目录

	private String databaseType = ""; // 数据库类型 mysql oracle等
	private String driverName = ""; // 数据库驱动名
	private String url = ""; // 数据库连接地址
	private String username = "";
	private String password = "";
	private String queryTableNamesSql = ""; // 查询所有表名的SQL语句，musql和oracle可不设置，系统自动判断

	private String tablePrefix = ""; // 表前缀
	private boolean deleteTablePrefix = false; // 是否删除表前缀

	private boolean generateEntityAnnotation = false; // 是否生成实体类对应的注解
	private boolean generateEntityRowMapperFile = false; // 是否生成实体类对应的RowMapper文件

	private boolean generateBaseTemplateFile = false; // 是否生成基础action、service、dao文件
	private String templateDir; // 模板所在目录
	private String actionTemplateName = "ActionTemplate.java"; // action模板名称
	private String serviceInterfaceTemplateName = "ServiceInterfaceTemplate.java"; // serviceInterface模板名称
	private String serviceImplTemplateName = "ServiceImplTemplate.java"; // serviceImpl模板名称
	private String daoInterfaceTemplateName = "DaoInterfaceTemplate.java"; // daoInterface模板名称
	private String daoImplTemplateName = "DaoImplTemplate.java"; // daoImpl模板名称

	private String newActionFileName = "#REPLACE_ENTITY_NAME#Action.java"; // action模板名称
	private String newServiceInterfaceFileName = "I#REPLACE_ENTITY_NAME#Service.java"; // serviceInterface模板名称
	private String newServiceImplFileName = "#REPLACE_ENTITY_NAME#ServiceImpl.java"; // serviceImpl模板名称
	private String newDaoInterfaceFileName = "I#REPLACE_ENTITY_NAME#Dao.java"; // daoInterface模板名称
	private String newDaoImplFileName = "#REPLACE_ENTITY_NAME#DaoImpl.java"; // daoImpl模板名称

	// getter setter

	public String getEncode() {
		return encode;
	}

	public void setEncode(String encode) {
		this.encode = encode;
	}

	/**
	 * 文件生成保存的根目录
	 * 
	 * @return
	 */
	public String getBaseDir() {
		return baseDir;
	}

	public void setBaseDir(String baseDir) {
		this.baseDir = baseDir;
		this.templateDir = baseDir + File.separator + "template"; // 设置默认模板目录
	}

	public String getDriverName() {
		return driverName;
	}

	public void setDriverName(String driverName) {
		this.driverName = driverName;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * 查询所有表名的SQL语句，musql和oracle可不设置，系统自动判断
	 * 
	 * @return
	 */
	public String getQueryTableNamesSql() {
		return queryTableNamesSql;
	}

	/**
	 * 查询所有表名的SQL语句，musql和oracle可不设置，系统自动判断
	 * 
	 * @return
	 */
	public void setQueryTableNamesSql(String queryTableNamesSql) {
		this.queryTableNamesSql = queryTableNamesSql;
	}

	public String getDatabaseType() {
		return databaseType;
	}

	public void setDatabaseType(String databaseType) {
		this.databaseType = databaseType;
	}

	public String getTemplateDir() {
		return templateDir;
	}

	public void setTemplateDir(String templateDir) {
		this.templateDir = templateDir;
	}

	public String getActionTemplateName() {
		return actionTemplateName;
	}

	public void setActionTemplateName(String actionTemplateName) {
		this.actionTemplateName = actionTemplateName;
	}

	public String getServiceInterfaceTemplateName() {
		return serviceInterfaceTemplateName;
	}

	public void setServiceInterfaceTemplateName(String serviceInterfaceTemplateName) {
		this.serviceInterfaceTemplateName = serviceInterfaceTemplateName;
	}

	public String getServiceImplTemplateName() {
		return serviceImplTemplateName;
	}

	public void setServiceImplTemplateName(String serviceImplTemplateName) {
		this.serviceImplTemplateName = serviceImplTemplateName;
	}

	public String getDaoInterfaceTemplateName() {
		return daoInterfaceTemplateName;
	}

	public void setDaoInterfaceTemplateName(String daoInterfaceTemplateName) {
		this.daoInterfaceTemplateName = daoInterfaceTemplateName;
	}

	public String getDaoImplTemplateName() {
		return daoImplTemplateName;
	}

	public void setDaoImplTemplateName(String daoImplTemplateName) {
		this.daoImplTemplateName = daoImplTemplateName;
	}

	public boolean isGenerateBaseTemplateFile() {
		return generateBaseTemplateFile;
	}

	public void setGenerateBaseTemplateFile(boolean generateBaseTemplateFile) {
		this.generateBaseTemplateFile = generateBaseTemplateFile;
	}

	/**
	 * 是否生成实体类注解
	 * 
	 * @return
	 */
	public boolean isGenerateEntityAnnotation() {
		return generateEntityAnnotation;
	}

	/**
	 * 设置是否生成实体类注解
	 * 
	 * @return
	 */
	public void setGenerateEntityAnnotation(boolean generateEntityAnnotation) {
		this.generateEntityAnnotation = generateEntityAnnotation;
	}

	public String getNewActionFileName() {
		return newActionFileName;
	}

	/**
	 * 设置要保存的Action名称
	 * 
	 * @param newActionFileName
	 *            新名称，可用变量：#REPLACE_ENTITY_NAME#，表示实体类名
	 */
	public void setNewActionFileName(String newActionFileName) {
		this.newActionFileName = newActionFileName;
	}

	public String getNewServiceInterfaceFileName() {
		return newServiceInterfaceFileName;
	}

	/**
	 * 设置要保存的Service接口名称
	 * 
	 * @param newServiceInterfaceFileName
	 *            新名称，可用变量：#REPLACE_ENTITY_NAME#，表示实体类名
	 */
	public void setNewServiceInterfaceFileName(String newServiceInterfaceFileName) {
		this.newServiceInterfaceFileName = newServiceInterfaceFileName;
	}

	public String getNewServiceImplFileName() {
		return newServiceImplFileName;
	}

	/**
	 * 设置要保存的Service实现类名称
	 * 
	 * @param newServiceImplFileName
	 *            新名称，可用变量：#REPLACE_ENTITY_NAME#，表示实体类名
	 */
	public void setNewServiceImplFileName(String newServiceImplFileName) {
		this.newServiceImplFileName = newServiceImplFileName;
	}

	public String getNewDaoInterfaceFileName() {
		return newDaoInterfaceFileName;
	}

	/**
	 * 设置要保存的Dao接口名称
	 * 
	 * @param newDaoInterfaceFileName
	 *            新名称，可用变量：#REPLACE_ENTITY_NAME#，表示实体类名
	 */
	public void setNewDaoInterfaceFileName(String newDaoInterfaceFileName) {
		this.newDaoInterfaceFileName = newDaoInterfaceFileName;
	}

	public String getNewDaoImplFileName() {
		return newDaoImplFileName;
	}

	/**
	 * 设置要保存的Dao实现类名称
	 * 
	 * @param newDaoImplFileName
	 *            新名称，可用变量：#REPLACE_ENTITY_NAME#，表示实体类名
	 */
	public void setNewDaoImplFileName(String newDaoImplFileName) {
		this.newDaoImplFileName = newDaoImplFileName;
	}

	public String getTablePrefix() {
		return tablePrefix;
	}

	/**
	 * 设置表前缀
	 * 
	 * @param tablePrefix
	 */
	public void setTablePrefix(String tablePrefix) {
		this.tablePrefix = tablePrefix;
	}

	public boolean isDeleteTablePrefix() {
		return deleteTablePrefix;
	}

	/**
	 * 设置是否删除表前缀
	 * 
	 * @param deleteTablePrefix
	 */
	public void setDeleteTablePrefix(boolean deleteTablePrefix) {
		this.deleteTablePrefix = deleteTablePrefix;
	}

	public boolean isGenerateEntityRowMapperFile() {
		return generateEntityRowMapperFile;
	}

	public void setGenerateEntityRowMapperFile(boolean generateEntityRowMapperFile) {
		this.generateEntityRowMapperFile = generateEntityRowMapperFile;
	}

}

/**
 * 表对象, 对应数据库表信息, 原汁原味, 未做处理
 */
class Table {
	private String tableName; // 表名
	private List<String> columNames = new ArrayList<String>(); // 列名集合
	private List<String> columTypes = new ArrayList<String>(); // 列类型集合，列类型严格对应java类型，如String不能写成string，与列名一一对应

	public String toString() {
		String tableStr = "";
		tableStr = tableStr + tableName + "\r\n";

		// 遍历列集合
		for (int i = 0; i < columNames.size(); i++) {
			String columName = columNames.get(i);
			String columType = columTypes.get(i);
			tableStr += "  " + columName + ":  " + columType + "\r\n";
		}

		return tableStr;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public List<String> getColumNames() {
		return columNames;
	}

	public void setColumNames(List<String> columNames) {
		this.columNames = columNames;
	}

	public List<String> getColumTypes() {
		return columTypes;
	}

	public void setColumTypes(List<String> columTypes) {
		this.columTypes = columTypes;
	}

}
