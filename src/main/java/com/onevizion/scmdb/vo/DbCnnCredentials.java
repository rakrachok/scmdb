package com.onevizion.scmdb.vo;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DbCnnCredentials {
    private static final String JDBC_THIN_URL_PREFIX = "jdbc:oracle:thin:@";
    private static final String DB_CNN_STR_ERROR_MESSAGE = "You should specify db connection properties using one of following formats:"
            + " <username>/<password>@<host>:<port>:<SID> or <username>/<password>@<host>:<port>/<service>";
    private static final String USER_SCHEMA_SUFFIX = "_user";

    private String schemaName;
    private String password;
    private String connectionString;
    private String oracleUrl;

    private DbCnnCredentials() {}

    public static DbCnnCredentials create(String ownerCnnStr) {
        if (!isCorrectConnectionString(ownerCnnStr)) {
            throw new IllegalArgumentException(DB_CNN_STR_ERROR_MESSAGE);
        }

        if (isOldJdbcFormat(ownerCnnStr)) {
            ownerCnnStr = convertToNewJdbcFormat(ownerCnnStr);
        }
        DbCnnCredentials cnnCredentials = new DbCnnCredentials();
        cnnCredentials.setConnectionString(ownerCnnStr);

        Pattern p = Pattern.compile("(.+?)/(.+?)@(.+)");
        Matcher m = p.matcher(ownerCnnStr);
        if (m.matches() && m.groupCount() == 3) {
            cnnCredentials.setSchemaName(m.group(1));
            cnnCredentials.setPassword(m.group(2));
            cnnCredentials.setOracleUrl(JDBC_THIN_URL_PREFIX + m.group(3));
        } else {
            throw new IllegalArgumentException(DB_CNN_STR_ERROR_MESSAGE);
        }
        return cnnCredentials;
    }

    public static boolean isCorrectConnectionString(String cnnStr) {
        if (isOldJdbcFormat(cnnStr)) {
            cnnStr = convertToNewJdbcFormat(cnnStr);
        }
        Pattern p = Pattern.compile("(.+?)/(.+?)@(.+)");
        Matcher m = p.matcher(cnnStr);
        return m.matches() && m.groupCount() == 3;
    }

    public static String genUserCnnStr(String ownerCnnStr) {
        String owner = ownerCnnStr.substring(0, ownerCnnStr.indexOf("/"));
        String user = owner + USER_SCHEMA_SUFFIX;
        return user + ownerCnnStr.substring(ownerCnnStr.indexOf("/"));
    }

    private static boolean isUserSchema(String cnnUser) {
        return cnnUser.endsWith(USER_SCHEMA_SUFFIX);
    }

    private static boolean isOldJdbcFormat(String cnnStr) {
        return StringUtils.countMatches(cnnStr, ":") == 2;
    }

    private static String convertToNewJdbcFormat(String oldCnnStr) {
        int i = oldCnnStr.lastIndexOf(":");
        return oldCnnStr.substring(0, i) + "/" + oldCnnStr.substring(i + 1);
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getOracleUrl() {
        return oracleUrl;
    }

    public void setOracleUrl(String oracleUrl) {
        this.oracleUrl = oracleUrl;
    }
}
