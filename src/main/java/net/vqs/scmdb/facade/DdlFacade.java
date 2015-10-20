package net.vqs.scmdb.facade;

import net.vqs.maven.plugin.dbschema.ScriptsGenerator;
import net.vqs.scmdb.vo.DbScriptVo;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Resource;

@Component
public class DdlFacade {
    @Resource
    private ScriptsGenerator scriptsGenerator;

    private final String DDL_FOLDER_NAME = "ddl";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void generateDdl(List<DbScriptVo> newDbScripts, File scriptDir) {
        String ddlDir = scriptDir.getParentFile().getAbsolutePath() + File.separator + DDL_FOLDER_NAME;
        String[] newDbObjectArr = findChangedDbObjects(newDbScripts, scriptDir);
        if (newDbObjectArr == null) {
            return;
        }
        try {
            scriptsGenerator.setConCacheProperties("connectionCacheProperties.xml");
            scriptsGenerator.executeSettingTransformParams();
            scriptsGenerator.generateDbObjectScripts(ddlDir, newDbObjectArr);
        } catch (IOException e) {
            this.logger.error(e.toString());
        }
    }

    private String[] findChangedDbObjects(List<DbScriptVo> newDbScripts, File scriptDir) {
        List<String> cats = new ArrayList<String>();
        for (DbScriptVo newDbScript : newDbScripts) {
            File f = new File(scriptDir.getAbsolutePath() + File.separator + newDbScript.getName());
            try {
                String content = FileUtils.readFileToString(f);
                cats.add(content);
            } catch (IOException e) {
                logger.warn("Can't read file [{}]", f.getName(), e);
            }
        }

        if (cats.isEmpty()) {
            return null;
        }

        String[] objTypes = new String[] {"package body", "package", "view", "comment", "table", "index", "trigger", "sequence"};
        String[] keywords = new String[] {"create package body", "replace package body", "drop package body", "create package", "replace package",
                "drop package", "package", "create view", "replace view", "drop view", "create force view", "replace force view",
                "create table", "alter table", "drop table", "create index", "create unique index", "drop index",
                "create trigger", "replace trigger", "drop trigger", "alter trigger", "create sequence", "drop sequence", "comment on table",
                "comment on column"};
        List<String> dbObjectsNames = new ArrayList<String>();
        List<String> dbObjectsTypes = new ArrayList<String>();
        List<String> delDbObjectsNames = new ArrayList<String>();
        List<String> delDbObjectsTypes = new ArrayList<String>();

        for (String cat : cats) {
            cat = cat.replaceAll("--.*\r*\n", "");
            cat = cat.replaceAll("/\\*([\\s\\S]*?)\\*/", "");
            cat = cat.replaceAll("\n+", " ");
            cat = cat.replaceAll("\\s\\s+", " ");
            cat = cat.replaceAll("\"", "");
            cat = cat.toLowerCase();

            for (String keyword : keywords) {
                String regexp = keyword + "\\s+\\w+";
                Pattern pattern = Pattern.compile(regexp);
                Matcher matcher = pattern.matcher(cat);
                if (!matcher.find()) {
                    continue;
                }
                matcher.reset();
                cat = cat.replaceFirst(regexp, "");
                regexp = keyword + "\\s";
                while (matcher.find()) {
                    String name = matcher.group().replaceFirst(regexp, "");
                    if (!"BODY".equals(name.toUpperCase())) {
                        if ("drop package body".equals(keyword) || "drop package".equals(keyword)
                                || "drop table".equals(keyword) || "drop view".equals(keyword)
                                || "drop index".equals(keyword) || "drop trigger".equals(keyword)
                                || "drop sequence".equals(keyword)) {
                            delDbObjectsNames.add(name.replaceFirst("\"", ""));
                            delDbObjectsTypes.add(keyword);
                        } else {
                            dbObjectsNames.add(name.replace("\"", ""));
                            dbObjectsTypes.add(keyword);
                        }
                    }
                }
            }
        }

        String packageBody = "package_body";
        String packageSpec = "package_spec";
        String columnCommentKw = "comment on column";
        String tableCommentKw = "comment on table";

        for (String objType : objTypes) {
            String regexp = ".*?" + objType;
            for (int i = 0; i < dbObjectsTypes.size(); i++) {
                String dbObjectsType = dbObjectsTypes.get(i);
                if (dbObjectsType.matches(regexp)) {
                    dbObjectsType = dbObjectsType.replaceFirst(regexp, objType);
                } else if (columnCommentKw.equals(dbObjectsType) || tableCommentKw.equals(dbObjectsType)) {
                    dbObjectsType = "comment";
                } else {
                    continue;
                }

                if ("package body".equals(dbObjectsType)) {
                    dbObjectsType = packageBody;
                } else if ("package".equals(dbObjectsType)) {
                    dbObjectsType = packageSpec;
                }
                dbObjectsTypes.set(i, dbObjectsType);
            }

            for (int i = 0; i < delDbObjectsTypes.size(); i++) {
                String delDbObjectsType = delDbObjectsTypes.get(i);
                if (delDbObjectsType.matches(regexp)) {
                    delDbObjectsType = delDbObjectsType.replaceFirst(regexp, objType);
                } else {
                    continue;
                }

                if ("package body".equals(delDbObjectsType)) {
                    delDbObjectsType = packageBody;
                } else if ("package".equals(delDbObjectsType)) {
                    delDbObjectsType = packageSpec;
                }
                delDbObjectsTypes.set(i, delDbObjectsType);
            }
        }

        List<String> uniqueObjNames = new ArrayList<String>();
        List<String> dbTypes = new ArrayList<String>();
        boolean isUniqueObj;
        for (int i = 0; i < dbObjectsNames.size(); i++) {
            isUniqueObj = true;
            for (int j = i + 1; j < dbObjectsNames.size(); j++) {
                if (dbObjectsNames.get(i).equals(dbObjectsNames.get(j))
                        && dbObjectsTypes.get(i).equals(dbObjectsTypes.get(j))) {
                    isUniqueObj = false;
                    break;
                }
            }

            if (isUniqueObj) {
                uniqueObjNames.add(dbObjectsNames.get(i));
                dbTypes.add(dbObjectsTypes.get(i));
            }
        }

        List<String> extractDdlArr = new ArrayList<String>();
        for (int i = 0; i < uniqueObjNames.size(); i++) {
            extractDdlArr.add(uniqueObjNames.get(i) + "|" + dbTypes.get(i));
        }

        for (int i = 0; i < delDbObjectsNames.size(); i++) {
            extractDdlArr.add(delDbObjectsNames.get(i) + "|" + delDbObjectsTypes.get(i));
        }

        return extractDdlArr.toArray(new String[extractDdlArr.size()]);
    }
}