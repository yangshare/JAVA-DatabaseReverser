package com.yang.util;

import java.io.IOException;

/**
* @author 杨成 E-mail:yangcheng@wiswit.com
* @version 创建时间：2017年4月5日 下午5:22:42
* 类说明
*/

public class test {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		// MySQL连接
		// String driverName = "com.mysql.jdbc.Driver";
		// String url = "jdbc:mysql://localhost:3306/shop";
		// String username = "root";
		// String password = "yang123";

		// postgres连接
		String driverName = "org.postgresql.Driver";
		String url = "jdbc:postgresql://localhost:5432/wiswit";
		String username = "postgres";
		String password = "yang";

		// SQL Server 链接
		// String driverName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		// String url = "jdbc:sqlserver://localhost:1433;DatabaseName=";
		// String username = "sa";
		// String password = "";

		// ORACLE 链接
		// String driverName = "oracle.jdbc.driver.OracleDriver";
		// String url = "";
		// String username = "";
		// String password = "";

		Config config = new Config();
		config.setDriverName(driverName);
		config.setUrl(url);
		config.setUsername(username);
		config.setPassword(password);
		// config.setGenerateEntityAnnotation(true);
		config.setGenerateEntityRowMapperFile(false);
		config.setGenerateBaseTemplateFile(true);
		config.setTablePrefix("tb_");
		config.setDeleteTablePrefix(true);
		config.setBaseDir("D:\\DatabaseReverse");

		DatabaseReverser reverser = new DatabaseReverser(config);
		reverser.doReverser();

	}
}
