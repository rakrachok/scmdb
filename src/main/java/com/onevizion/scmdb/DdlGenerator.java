package com.onevizion.scmdb;

import com.onevizion.scmdb.dao.DdlDao;
import com.onevizion.scmdb.vo.DbObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.onevizion.scmdb.ColorLogger.Color.GREEN;
import static com.onevizion.scmdb.ColorLogger.Color.RED;
import static com.onevizion.scmdb.vo.DbObjectType.*;

@Component
public class DdlGenerator {
    public static final String PACKAGE_SPECIFICATION_DDL_FILE_POSTFIX = "_spec";
    @Resource
    private DdlDao ddlDao;

    @Resource
    private AppArguments appArguments;

    @Resource
    private ColorLogger logger;

    private final String[] excludedSequences = {"SEQ_BPD_ITEMS_UNIT_ID"};
    private final String[] excludedPackages = {"PKGR_"};
    private final String[] excludedViews = {"VX_"};

    private static final String PACKAGES_DDL_DIRECTORY_NAME = "packages";
    private static final String TABLES_DDL_DIRECTORY_NAME = "tables";
    private static final String VIEWS_DDL_DIRECTORY_NAME = "views";

    public void executeSettingTransformParams() {
        ddlDao.executeTransformParamStatements();
    }

    public void generatePackageSpecScripts(DbObject pkgSpec) {
        if (!isExcludeObject(pkgSpec.getName(), excludedPackages)) {
            logger.info("Generating DDL for package spec [{}]", GREEN, pkgSpec.getName());
            String ddl = removeSchemaNameInDdl(pkgSpec.getDdl());
            ddl = ddl.trim();
            ddl = ddl.replaceFirst("\\s+/$", "\n/");
            ddl = ddl.replaceAll("\\n", "\r\n");
            pkgSpec.setDdl(ddl);
            writeDdlToFile(pkgSpec, PACKAGES_DDL_DIRECTORY_NAME);
        }
    }

    public void generatePackageBodyScripts(DbObject pkgBody) {
        if (!isExcludeObject(pkgBody.getName(), excludedPackages)) {
            logger.info("Generating DDL for package body [{}]", GREEN, pkgBody.getName());
            String ddl = removeSchemaNameInDdl(pkgBody.getDdl());
            ddl = ddl.trim();
            ddl = ddl.replaceFirst("\\s+/$", "\n/");
            ddl = ddl.replaceAll("\\n", "\r\n");
            pkgBody.setDdl(ddl);
            writeDdlToFile(pkgBody, PACKAGES_DDL_DIRECTORY_NAME);
        }
    }

    public void generateTableScripts(DbObject table) {
        logger.info("Generating DDL for table [{}]", GREEN, table.getName());
        String ddl = removeSchemaNameInDdl(table.getDdl());
        ddl = ddl.trim();
        ddl = ddl.replaceAll("\\s+;", ";");
        ddl = ddl.replaceFirst("\\n\\s+\\(", "(\n");
        ddl = ddl.replaceFirst("\\s+\\)", "\n)");
        ddl = ddl.replaceAll("\\n", "\r\n");
        ddl = ddl.replaceAll("\\t", "    ");
        ddl = ddl.replaceAll("\\r\\n\\s+REFERENCES\\s", " REFERENCES ");
        ddl += generateTableCommentsDdl(table);
        ddl += generateIndexScripts(table);
        ddl += generateSequenceScripts(table);
        ddl += generateTriggerScripts(table);
        table.setDdl(ddl);
        writeDdlToFile(table, TABLES_DDL_DIRECTORY_NAME);
    }

    private void writeDdlToFile(DbObject dbObject, String ddlDirectoryName) {
        String directoryPath = appArguments.getDdlsDirectory().getAbsolutePath() + File.separator + ddlDirectoryName;

        String filePath = directoryPath + File.separator + dbObject.getName().toLowerCase();
        if (dbObject.getType() == PACKAGE_SPEC) {
            filePath += PACKAGE_SPECIFICATION_DDL_FILE_POSTFIX;
        }
        filePath += ".sql";
        File file = new File(filePath);
        try {
            FileUtils.write(file, dbObject.getDdl(), "UTF-8", false);
        } catch (IOException e) {
            throw new RuntimeException("Can't write ddl to file[" + file.getAbsolutePath() + "]", e);
        }
    }

    private String generateTableCommentsDdl(DbObject table) {
        logger.info("Adding comments...");
        List<DbObject> comments = ddlDao.extractTableDependentObjectsDdl(table.getName(), COMMENT);
        StringBuilder commentsDdl = new StringBuilder();
        for (DbObject comment : comments) {
            String ddl = removeSchemaNameInDdl(comment.getDdl());
            ddl = ddl.trim();

            Pattern pattern = Pattern.compile("COMMENT ON TABLE.+", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(ddl);
            if (matcher.find()) {
                String commentStmt = matcher.group();
                ddl = ddl.replaceFirst("COMMENT ON TABLE.+", "");
                ddl = commentStmt + "\r\n" + ddl;
            }

            ddl = ddl.replaceAll("\\n", "");
            ddl = ddl.replaceAll("\\s+COMMENT", "COMMENT");
            ddl = ddl.replaceAll("COMMENT", "\r\nCOMMENT");
            ddl = ddl.trim();
            ddl = "\r\n\r\n" + ddl;
            commentsDdl.append(ddl);
        }
        return commentsDdl.toString();
    }

    private String generateIndexScripts(DbObject table) {
        logger.info("Adding indexes...");
        List<DbObject> indexes = ddlDao.extractTableDependentObjectsDdl(table.getName(), INDEX);
        StringBuilder indexesDdl = new StringBuilder();
        for (int i = 0; i < indexes.size(); i++) {
            DbObject index = indexes.get(i);
            String ddl = removeSchemaNameInDdl(index.getDdl());
            ddl = ddl.trim();
            if (i == 0) {
                ddl = "\r\n\r\n" + ddl;
            } else {
                ddl = "\r\n" + ddl;
            }
            ddl = ddl.replaceAll("\\s+;$", ";");
            indexesDdl.append(ddl);
        }
        return indexesDdl.toString();
    }

    private String generateSequenceScripts(DbObject table) {
        logger.info("Adding sequences...");
        List<DbObject> sequences = ddlDao.extractTableDependentObjectsDdl(table.getName(), SEQUENCE);
        StringBuilder sequencesDdl = new StringBuilder();
        for (DbObject sequence : sequences) {
            String ddl = removeSchemaNameInDdl(sequence.getDdl());
            boolean isExcludable = false;
            for (String exclSeq : excludedSequences) {
                if (ddl.contains(exclSeq)) {
                    isExcludable = true;
                    break;
                }
            }
            if (isExcludable) {
                continue;
            }

            ddl = ddl.trim();
            ddl = "\r\n" + ddl;
            int index = ddl.lastIndexOf("\"");
            if (index != -1) {
                ddl = ddl.substring(0, index + 1);
                ddl += ";";
            } else {
                ddl = ddl.replaceAll("\\s+;$", ";");
            }
            sequencesDdl.append(ddl);
        }
        return sequencesDdl.toString();
    }

    private String generateTriggerScripts(DbObject table) {
        logger.info("Adding triggers...");
        List<DbObject> triggers = ddlDao.extractTableDependentObjectsDdl(table.getName(), TRIGGER);
        StringBuilder triggersDdl = new StringBuilder();
        for (DbObject trigger : triggers) {
            String ddl = removeSchemaNameInDdl(trigger.getDdl());
            ddl = ddl.trim();
            ddl = ddl.replaceAll("\\n", "\r\n");
            ddl = "\r\n" + ddl;
            ddl = ddl.replaceAll("\\s+/", "\r\n/");
            ddl = ddl.replaceFirst("ALTER TRIGGER \"\\w+\" ENABLE;", "");
            triggersDdl.append(ddl);
        }
        return triggersDdl.toString();
    }

    public void generateViewScripts(DbObject view) {
        if (!isExcludeObject(view.getName(), excludedViews)) {
            logger.info("Generating DDL for view [{}]", GREEN, view.getName());
            String ddl = removeSchemaNameInDdl(view.getDdl());
            ddl = ddl.trim();
            ddl = ddl.replaceAll("\\s+;", ";");
            ddl = ddl.replaceAll("\\n", "\r\n");
            ddl += generateViewCommentsScripts(view);
            view.setDdl(ddl);
            writeDdlToFile(view, VIEWS_DDL_DIRECTORY_NAME);
        }
    }

    private String generateViewCommentsScripts(DbObject view) {
        logger.info("Adding views comments...");
        List<DbObject> comments = ddlDao.extractTableDependentObjectsDdl(view.getName(), COMMENT);
        StringBuilder commentsDdl = new StringBuilder();
        for (DbObject comment : comments) {
            String ddl = removeSchemaNameInDdl(comment.getDdl());
            ddl = ddl.replaceAll("\\s+COMMENT", "\r\nCOMMENT");
            ddl = ddl.replaceFirst("COMMENT", "\r\nCOMMENT");
            commentsDdl.append(ddl);
        }
        return commentsDdl.toString();
    }

    public void createDdlsForChangedDbObjects(Set<DbObject> dbObjects) {
        Set<DbObject> tables = new HashSet<>();
        for (DbObject dbObject : dbObjects) {
            if (dbObject.getType() == COMMENT) {
                dbObject.setType(ddlDao.getObjectTypeByName(dbObject.getName()));
            }

            if (dbObject.getType() == INDEX || dbObject.getType() == TRIGGER) {
                String tableName = ddlDao.getTableNameByDepObject(dbObject);
                if (ddlDao.isExist(tableName, TABLE)) {
                    tables.add(new DbObject(tableName, TABLE));
                } else {
                    logger.warn("Parent object not found for {} {}! Please, modify related DDL manually.", RED,
                            dbObject.getType(), dbObject.getName());
                }
            } else if (dbObject.getType() == SEQUENCE) {
                String tableName = ddlDao.getTableNameByDepObject(dbObject);
                if (StringUtils.isNotBlank(tableName)) {
                    tables.add(new DbObject(tableName, TABLE));
                } else {
                    logger.warn("Parent object not found for {} {}! Please, modify related DDL manually.", RED,
                            dbObject.getType(), dbObject.getName());
                }
            } else if (dbObject.getType() == TABLE) {
                if (!checkAndDeleteRedundantDdl(dbObject)) {
                    tables.add(dbObject);
                }
            } else {
                if (!checkAndDeleteRedundantDdl(dbObject)) {
                    dbObject.setDdl(ddlDao.extractDdl(dbObject));
                    if (dbObject.getType() == PACKAGE_BODY) {
                        generatePackageBodyScripts(dbObject);
                    } else if (dbObject.getType() == PACKAGE_SPEC) {
                        generatePackageSpecScripts(dbObject);
                    } else if (dbObject.getType() == VIEW) {
                        generateViewScripts(dbObject);
                    }
                }
            }
        }
        for (DbObject table : tables) {
            table.setDdl(ddlDao.extractDdl(table));
            generateTableScripts(table);
        }
    }

    public String removeSchemaNameInDdl(String ddl) {
        String regexp = String.format("\"%s\".", appArguments.getOwnerCredentials().getSchemaName().toUpperCase());
        ddl = ddl.replaceAll(regexp, "");
        return ddl;
    }

    private boolean isExcludeObject(String objectName, String[] excludedObjects) {
        for (String exclPackageName : excludedObjects) {
            if (objectName.toUpperCase().startsWith(exclPackageName.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkAndDeleteRedundantDdl(DbObject dbObject) {
        String ddlsDirectoryPath = appArguments.getDdlsDirectory().getAbsolutePath();
        boolean isDeletePackageSpecWithBody = false;
        File fileDir = null;
        String fileName = null;
        if (!ddlDao.isExist(dbObject.getName(), dbObject.getType())) {
            if (dbObject.getType() == PACKAGE_BODY) {
                fileDir = new File(appArguments.getDdlsDirectory() + PACKAGES_DDL_DIRECTORY_NAME);
                fileName = dbObject.getName() + ".sql";
            } else if (dbObject.getType() == PACKAGE_SPEC) {
                fileDir = new File(ddlsDirectoryPath + File.separator + PACKAGES_DDL_DIRECTORY_NAME);
                fileName = dbObject.getName() + "_spec.sql";
                isDeletePackageSpecWithBody = true;
            } else if (dbObject.getType() == TABLE) {
                fileDir = new File(ddlsDirectoryPath + File.separator + TABLES_DDL_DIRECTORY_NAME);
                fileName = dbObject.getName() + ".sql";
            } else if (dbObject.getType() == VIEW) {
                fileDir = new File(ddlsDirectoryPath + File.separator + VIEWS_DDL_DIRECTORY_NAME);
                fileName = dbObject.getName() + ".sql";
            }
        }

        if (fileName != null) {
            deleteFilteredFiles(fileDir, fileName);
            if (isDeletePackageSpecWithBody) {
                String packBodyName = dbObject.getName() + ".sql";
                deleteFilteredFiles(fileDir, packBodyName);
            }
            return true;
        }
        return false;
    }

    private void deleteFilteredFiles(File fileDir, String fileName) {
        FileFilter filter = new WildcardFileFilter(fileName, IOCase.INSENSITIVE);
        File[] filteredFiles = fileDir.listFiles(filter);
        if (filteredFiles != null && filteredFiles.length > 0) {
            for (File file : filteredFiles) {
                try {
                    FileUtils.forceDeleteOnExit(file);
                } catch (IOException e) {
                    throw new RuntimeException("Can't delete ddl file [" + file.getAbsolutePath() + "]", e);
                }
            }
        }
    }
}