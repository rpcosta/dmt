package com.sap.cx.migration.tools;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DataMigrationTools {

    public static final String CMD_COMPARE = "compare";
    public static final String CMD_TEST = "test";
    private Properties properties = new Properties();

    public static void main(String[] args) {

        if (null == args || args.length == 0) {
            System.out.println("Error due argument is missing");
            showUsage();
            System.exit(-1);
        }

        DataMigrationTools main = new DataMigrationTools();
        if (CMD_COMPARE.equals(args[0])) {
            main.compareSourceAndDestination();
        } else if (CMD_TEST.equals(args[0])) {
            if (args.length < 2) {
                System.out.println("Error due missing database name.");
                showUsage();
                System.exit(-1);
            } else if (!"source".equals(args[1]) && !"target".equals(args[1])){
                System.out.println("Invalid Database name " + args[1] + ".");
                showUsage();
                System.exit(-1);
            } else {
                main.testConnection(args[1]);
            }
        }
    }

    public DataMigrationTools(){

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.properties");

        if (null == inputStream){
            System.out.println("File application.properties not found.");
            System.exit(-1);
        }

        try {
            properties.load(inputStream);
        } catch (IOException e) {
            System.out.println("Error to load application.properties file due error : " + e.getMessage());
            System.exit(-1);
        }
    }

    public void compareSourceAndDestination() {

        Map<String, Long> sourceRows = getTableRows("source");
        Map<String, Long> targetRows = getTableRows("target");

        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();

        //Compare source and target
        for (String tableName: sourceRows.keySet()) {
            if (null == targetRows.get(tableName)) {
                errors.add("Missing Target Table - " + tableName + ". " +
                        "Please check version is the same and re-execute deploy with migration option");
            } else {
                final String comparison = sourceRows.get(tableName) + "/" + targetRows.get(tableName);
                if (!sourceRows.get(tableName).equals(targetRows.get(tableName))){
                    errors.add("Rows NOT MATCH - " + tableName + " - " + comparison+ ". " +
                            "Please execute truncate on target and re-execute pipeline");
                } else {
                    successes.add("Rows MATCH - " + tableName + " - " + comparison + ". ");
                }
            }
        }
        System.out.println("\n>>> Table with Success (" + successes.size() + ")");
        for (String success: successes) {
            System.out.println("\t" + success);
        }

        System.out.println("\n>>> Table with Error (" + errors.size() + ")");
        for (String error: errors) {
            System.out.println("\t" + error);
        }

    }

    private void testConnection(final String database){
        try {
            Connection connection = getConnection(database);
            System.out.println("Connected successfully to database " + database + ".");
            System.exit(-1);
        } catch (Exception e){
            System.out.println("Unable to connect to database  " + database + ".");
            System.exit(-1);
        }
    }

    private Map<String, Long> getTableRows(final String database){

        Map<String, Long> tableRows = new HashMap<>();

        final String dbQueryKey = database + ".db.query";
        final String dbQuery = properties.getProperty(dbQueryKey);

        try {

            final Connection connection = getConnection(database);
            final PreparedStatement preparedStatement = connection.prepareStatement(dbQuery);
            final ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()){
                final String tableName = resultSet.getString("table_name");
                final Long quantity = resultSet.getLong("table_rows");
                tableRows.put(tableName, quantity);
            }

            return tableRows;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private Connection getConnection(final String database) {

        final String dbDriverKey = database + ".db.driver";
        final String dbUrlKey = database + ".db.url";
        final String dbUsernameKey = database + ".db.username";
        final String dbPasswordKey = database + ".db.password";

        assertPropertiesSet(dbDriverKey, dbUrlKey, dbUsernameKey, dbPasswordKey);

        final String dbDriver = properties.getProperty(dbDriverKey);
        final String dbUrl = properties.getProperty(dbUrlKey);
        final String dbUsername = properties.getProperty(dbUsernameKey);
        final String dbPassword = properties.getProperty(dbPasswordKey);

        try {
           //Class.forName(dbDriver);
            return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        } catch (SQLException e) {
            System.out.println("Unable to execute statement on DB " + database + " due error: " + e.getMessage());
            System.exit(-1);
//        } catch (ClassNotFoundException e) {
//            System.out.println("Driver " + dbDriver + " required for DB " + database + " not found. " + e.getMessage());
//            System.exit(-1);
        }
        return null;
    }

    private void assertPropertiesSet(final String ... propertyKeys){

        List<String> errors = new ArrayList<>();
        for (String propertyKey: propertyKeys){
            if (!assertPropertySet(propertyKey)) {
                errors.add(propertyKey);
            }
        }

        if (!errors.isEmpty()){
            System.out.println("Properties " +  String.join(", ", errors) + " not set in application.properties file.");
            System.exit(-1);
        }
    }

    private boolean assertPropertySet(final String propertyKey){
        final Object value = properties.get(propertyKey);
        return (value != null);
    }

    public static void showUsage(){
        System.out.println("Usage: \n" +
                "java -jar Main.jar compare\n" +
                "java -jar Main.jar test [source|target]");
    }

}
