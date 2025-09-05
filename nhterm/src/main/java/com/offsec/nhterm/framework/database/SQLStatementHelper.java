package com.offsec.nhterm.framework.database;

import com.offsec.nhterm.framework.database.bean.TableInfo;
import com.offsec.nhterm.framework.database.annotation.ID;
import com.offsec.nhterm.framework.database.bean.TableInfo;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * @author kiva
 */
public class SQLStatementHelper {

  /**
   * 构造<b>创建表</b>的语句
   *
   * @param tableInfo 表信息
   * @return 创建表的SQL语句
   */
  public static String createTable(TableInfo tableInfo) {
    StringBuilder statement = new StringBuilder();

    statement.append("CREATE TABLE ").append("'")
      .append(tableInfo.tableName).append("'")
      .append(" (");

    if (tableInfo.containID) {
      DatabaseDataType dataType = SQLTypeParser.getDataType(tableInfo.primaryField);
      if (dataType == null) {
        throw new IllegalArgumentException("Type of " + tableInfo.primaryField.getType().getName() + " is not support in WelikeDB.");
      }
      statement.append("'").append(tableInfo.primaryField.getName()).append("'");
      if (dataType == DatabaseDataType.INTEGER) {
        statement.append(" INTEGER PRIMARY KEY ");
        ID id = tableInfo.primaryField.getAnnotation(ID.class);
        if (id != null && id.autoIncrement()) {
          statement.append("AUTOINCREMENT");
        }
      } else {
        statement
          .append("  ")
          .append(dataType.name())
          .append(" PRIMARY KEY");
      }

      statement.append(",");

    } else {
      statement.append("'_id' INTEGER PRIMARY KEY AUTOINCREMENT,");
    }

    for (Field field : tableInfo.fieldToDataTypeMap.keySet()) {
      DatabaseDataType dataType = tableInfo.fieldToDataTypeMap.get(field);
      assert dataType != null;
      statement.append("'").append(field.getName()).append("'")
        .append(" ")
        .append(dataType.name());
      if (!dataType.nullable) {
        statement.append(" NOT NULL");
      }
      statement.append(",");
    }
    //删掉最后一个逗号
    statement.deleteCharAt(statement.length() - 1);
    statement.append(")");

    return statement.toString();
  }

  /**
   * 构建 插入一个Bean 的语句.
   *
   * @param o
   * @return
   */
  public static String insertIntoTable(Object o) {
    TableInfo tableInfo = TableHelper.from(o.getClass());
    StringBuilder statement = new StringBuilder();
    statement.append("INSERT INTO ").append(tableInfo.tableName).append(" ");
    statement.append("VALUES(");

    if (tableInfo.containID) {
      DatabaseDataType primaryDataType = SQLTypeParser.getDataType(tableInfo.primaryField);
      if (Objects.requireNonNull(primaryDataType) == DatabaseDataType.INTEGER) {
        statement.append("NULL,");
      } else {
        try {
          statement
            .append(ValueHelper.valueToString(primaryDataType, tableInfo.primaryField, o))
            .append(",");
        } catch (IllegalAccessException ignored) {
        }
      }

    } else {
      statement.append("NULL,");
    }

    for (Field field : tableInfo.fieldToDataTypeMap.keySet()) {
      DatabaseDataType dataType = tableInfo.fieldToDataTypeMap.get(field);
      try {
        assert dataType != null;
        statement.append(ValueHelper.valueToString(dataType, field, o)).append(",");
      } catch (IllegalAccessException e) {
        //不会发生...
      }
    }
    statement.deleteCharAt(statement.length() - 1);
    statement.append(")");

    return statement.toString();

  }

  /**
   * 根据where条件创建选择语句
   *
   * @param tableInfo
   * @param where
   * @return
   */
  public static String findByWhere(TableInfo tableInfo, String where) {
    String statement = "SELECT * FROM " +
      tableInfo.tableName +
      " " +
      "WHERE " +
      where;

    return statement;
  }

  /**
   * 根据where条件创建删除语句
   *
   * @param tableInfo
   * @param where
   * @return
   */
  public static String deleteByWhere(TableInfo tableInfo, String where) {
    String statement = "DELETE FROM " +
      tableInfo.tableName +
      " " +
      "WHERE " +
      where;

    return statement;
  }

  /**
   * 根据where条件创建更新语句
   *
   * @param tableInfo
   * @param bean
   * @param where
   * @return
   */
  public static String updateByWhere(TableInfo tableInfo, Object bean, String where) {
    StringBuilder builder = new StringBuilder("UPDATE ");

    builder.append(tableInfo.tableName).append(" SET ");

    for (Field f : tableInfo.fieldToDataTypeMap.keySet()) {

      try {
        builder.append(f.getName())
          .append(" = ")
          .append(ValueHelper.valueToString(
            Objects.requireNonNull(SQLTypeParser.getDataType(f.getType())),
            f.get(bean))).append(",");
      } catch (Throwable ignored) {
      }
    }

    builder.deleteCharAt(builder.length() - 1);//删除最后一个逗号

    builder.append(" WHERE ");
    builder.append(where);
    return builder.toString();
  }

  /**
   * 创建选中table的语句
   *
   * @param tableName
   * @return
   */
  public static String selectTable(String tableName) {
    return "SELECT * FROM " + tableName;
  }

}
