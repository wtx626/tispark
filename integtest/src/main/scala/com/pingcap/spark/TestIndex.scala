/*
 *
 * Copyright 2017 PingCAP, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.pingcap.spark

import java.util.Properties

import com.google.common.collect.ImmutableSet

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by birdstorm on 2017/11/5.
  */
class TestIndex(prop: Properties) extends TestCase(prop) {

  private var colList: List[String] = _

  override protected val compareOpList = List("=", "<", ">", "<=", ">=")
  override protected val arithmeticOpList = List()

  protected val ARITHMETIC_CONSTANT: List[String] = List[String](
    java.lang.Long.MAX_VALUE.toString,
    java.lang.Long.MIN_VALUE.toString,
    java.lang.Double.MAX_VALUE.toString,
    java.lang.Double.MIN_VALUE.toString,
    3.14159265358979D.toString,
    "2.34E10",
    java.lang.Integer.MAX_VALUE.toString,
    java.lang.Integer.MIN_VALUE.toString,
    java.lang.Short.MAX_VALUE.toString,
    java.lang.Short.MIN_VALUE.toString,
    java.lang.Byte.MAX_VALUE.toString,
    java.lang.Byte.MIN_VALUE.toString,
    "0",
    BigDecimal.apply(2147868.65536).toString() // Decimal value
  )
  protected val PLACE_HOLDER: List[String] = List[String](
    LITERAL_NULL, // Null
    "'PingCAP'", // a simple test string
    "'2017-10-30'",
    "'0af51329-d907-4199-9bb0-8a99e09b8271'",
    "'2017-11-02 00:00:00'",
    "'2017-11-02'",
    "'2017-11-02 08:47:43'",
    "'2017'",
    "'oHQKaFHQ7N'",
    "'Q1GHo'"
  ) ++ ARITHMETIC_CONSTANT

  // TODO: Eliminate these bugs
  private final val colSkipSet: ImmutableSet[String] =
    ImmutableSet.builder()
      //      .add("tp_datetime") // time zone shift
      .add("tp_year") // year in spark shows extra month and day
      //      .add("tp_time") // Time format is not the same in TiDB and spark
      //      .add("tp_binary")
      //      .add("tp_blob")
      .build()

  private val colSet: mutable.Set[String] = mutable.Set()

  private def testIndex(): Unit = {
    var result = false
    result |= execBothAndJudge("select * from test_index where a < 30")

    result |= execBothAndJudge("select * from test_index where d > \'116.5\'")
    result |= execBothAndJudge("select * from test_index where d < \'116.5\'")
    result |= execBothAndJudge("select * from test_index where d > \'116.3\' and d < \'116.7\'")

    result |= execBothAndJudge("select * from test_index where d = \'116.72873\'")
    result |= execBothAndJudge("select * from test_index where d = \'116.72874\' and e < \'40.0452\'")

    result |= execBothAndJudge("select * from test_index where c > \'2008-02-06 14:03:58\'")
    result |= execBothAndJudge("select * from test_index where c >= \'2008-02-06 14:03:58\'")
    result |= execBothAndJudge("select * from test_index where c < \'2008-02-06 14:03:58\'")
    result |= execBothAndJudge("select * from test_index where c <= \'2008-02-06 14:03:58\'")
    result |= execBothAndJudge("select * from test_index where c = \'2008-02-06 14:03:58\'")

    result |= execBothAndJudge("select * from test_index where c > date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where c >= date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where c < date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where c <= date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where DATE(c) = date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where DATE(c) > date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where DATE(c) >= date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where DATE(c) < date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where DATE(c) <= date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where c <> date \'2008-02-05\'")
    result |= execBothAndJudge("select * from test_index where c > \'2008-02-04 14:00:00\' and d > \'116.5\'")
    result |= execBothAndJudge("select * from test_index where d = \'116.72873\' and c > \'2008-02-04 14:00:00\'")
    result |= execBothAndJudge("select * from test_index where d = \'116.72873\' and c < \'2008-02-04 14:00:00\'")

    result = !result
    logger.warn(s"\n*************** Index Tests result: $result\n\n\n")
  }

  def testFullDataTable(list: List[String]): Unit = {
    var result = false
    val startTime = System.currentTimeMillis()
    var count = 0
    for (sql <- list) {
      try {
        count += 1
        execAllAndJudge(sql)
        logger.info("Running num: " + count + " sql took " + (System.currentTimeMillis() - startTime) / 1000 + "s")
      } catch {
        case _: Throwable => logger.error("result: Run SQL " + sql + " Failed!")
      }
    }
    result = !result
    logger.warn(s"Result: Total Index test run: $inlineSQLNumber of ${list.size}")
    logger.warn(s"Result: Test ignored count:$testsSkipped, failed count:$testsFailed")
  }

  override def run(dbName: String, testCases: ArrayBuffer[(String, String)]): Unit = {
    spark_jdbc.init(dbName)
    spark.init(dbName)
    jdbc.init(dbName)
    colList = jdbc.getTableColumnNames("full_data_type_table")
    prepareTestCol()
    testIndex()
    testFullDataTable(
        createPlaceHolderTest
//        ++ createDoublePlaceHolderTest
        ++ createInTest
        ++ createBetween
        ++ createAggregate
    )
  }

  def prepareTestCol(): Unit = {
    colList.foreach(colSet.add)
    colSkipSet.foreach(colSet.remove)
  }


  def createLogicalAndOr(): List[String] = {
    createLogical("and") ::: createLogical("or")
  }

  private def createLogical(op: String): List[String] = {
    colSet.flatMap((lCol: String) =>
      colSet.map((rCol: String) =>
        select(lCol, rCol) + where(
          binaryOpWithName(
            binaryOpWithName(lCol, rCol, "="),
            binaryOpWithName(lCol, "0", ">"),
            op
          ))
      )).toList
  }

  // ***********************************************************************************************
  // ******************************** Below is test cases generated ********************************

  def createBetween(): List[String] = List(
    select("tp_int") + where(binaryOpWithName("tp_int", "-1202333 and 601508558", "between") + orderBy(ID_COL)),
    select("tp_bigint") + where(binaryOpWithName("tp_bigint", "-2902580959275580308 and 9223372036854775807", "between") + orderBy(ID_COL)),
    select("tp_decimal") + where(binaryOpWithName("tp_decimal", "2 and 200", "between") + orderBy(ID_COL)),
    select("tp_double") + where(binaryOpWithName("tp_double", "0.2054466 and 3.1415926", "between") + orderBy(ID_COL)),
    select("tp_float") + where(binaryOpWithName("tp_double", "-313.1415926 and 30.9412022", "between") + orderBy(ID_COL)),
    select("tp_datetime") + where(binaryOpWithName("tp_datetime", "'2043-11-28 00:00:00' and '2017-09-07 11:11:11'", "between") + orderBy(ID_COL)),
    select("tp_date") + where(binaryOpWithName("tp_date", "'2017-11-02' and '2043-11-28'", "between") + orderBy(ID_COL)),
    select("tp_timestamp") + where(binaryOpWithName("tp_timestamp", "815587200000 and 1511862599000", "between") + orderBy(ID_COL)),
    select("tp_year") + where(binaryOpWithName("tp_year", "1993 and 2017", "between") + orderBy(ID_COL)),
    select("tp_real") + where(binaryOpWithName("tp_real", "4.44 and 0.5194052764001038", "between") + orderBy(ID_COL))
  )

  def createAggregate(): List[String] = colSet.map((str: String) => select(str) + groupBy(str) + orderBy(str)).toList

  def createInTest(): List[String] = List(
    select("tp_int") + where(binaryOpWithName("tp_int", "(2333, 601508558, 4294967296, 4294967295)", "in") + orderBy(ID_COL)),
    select("tp_bigint") + where(binaryOpWithName("tp_bigint", "(122222, -2902580959275580308, 9223372036854775807, 9223372036854775808)", "in") + orderBy(ID_COL)),
    select("tp_varchar") + where(binaryOpWithName("tp_varchar", "('nova', 'a948ddcf-9053-4700-916c-983d4af895ef')", "in") + orderBy(ID_COL)),
    select("tp_decimal") + where(binaryOpWithName("tp_decimal", "(2, 3, 4)", "in") + orderBy(ID_COL)),
    select("tp_double") + where(binaryOpWithName("tp_double", "(0.2054466,3.1415926,0.9412022)", "in") + orderBy(ID_COL)),
    select("tp_float") + where(binaryOpWithName("tp_double", "(0.2054466,3.1415926,0.9412022)", "in") + orderBy(ID_COL)),
    select("tp_datetime") + where(binaryOpWithName("tp_datetime", "('2043-11-28 00:00:00','2017-09-07 11:11:11','1986-02-03 00:00:00')", "in") + orderBy(ID_COL)),
    select("tp_date") + where(binaryOpWithName("tp_date", "('2017-11-02', '2043-11-28 00:00:00')", "in") + orderBy(ID_COL)),
    select("tp_timestamp") + where(binaryOpWithName("tp_timestamp", "('2017-11-02 16:48:01')", "in") + orderBy(ID_COL)),
    select("tp_year") + where(binaryOpWithName("tp_year", "('2017')", "in") + orderBy(ID_COL)),
    select("tp_real") + where(binaryOpWithName("tp_real", "(4.44,0.5194052764001038)", "in") + orderBy(ID_COL))
  )

  /**
    * We create test for each type, each operator
    *
    * @return
    */

  def createPlaceHolderTest: List[String] = {
    var res = ArrayBuffer.empty[String]
    val skipLocalSet = mutable.Set[String]()
    skipLocalSet.add("tp_nvarchar")
    skipLocalSet.add("tp_varchar")
    skipLocalSet.add("tp_char")

    val arithmeticSkipSet = mutable.Set[String]()
    arithmeticSkipSet.add("int")
    arithmeticSkipSet.add("float")
    arithmeticSkipSet.add("decimal")
    arithmeticSkipSet.add("double")
    arithmeticSkipSet.add("real")
    arithmeticSkipSet.add("bit")
    arithmeticSkipSet.add(ID_COL)

    for (op <- compareOpList) {
      for (col <- colSet) {
        if (!skipLocalSet.contains(col))
          for (placeHolder <- PLACE_HOLDER) {
            if (!placeHolder.eq("'PingCAP'") || !arithmeticSkipSet.exists(col.contains(_))) {
              res += select() + where(binaryOpWithName(
                col,
                placeHolder,
                op
              ))
            }
          }
      }
    }

    res.toList
  }

  def createDoublePlaceHolderTest: List[String] = {
    var res = ArrayBuffer.empty[String]
    val skipLocalSet = mutable.Set[String]()
    skipLocalSet.add("tp_nvarchar")
    skipLocalSet.add("tp_varchar")
    skipLocalSet.add("tp_char")

    val arithmeticSkipSet = mutable.Set[String]()
    arithmeticSkipSet.add("int")
    arithmeticSkipSet.add("float")
    arithmeticSkipSet.add("decimal")
    arithmeticSkipSet.add("double")
    arithmeticSkipSet.add("real")
    arithmeticSkipSet.add("bit")
    arithmeticSkipSet.add(ID_COL)

    for (op <- compareOpList) {
      for (col <- colSet) if (!skipLocalSet.contains(col)) {
        val flag = !arithmeticSkipSet.exists(col.contains(_))
        for (placeHolder <- PLACE_HOLDER) if (!placeHolder.eq("'PingCAP'") || flag) {
          for (col2 <- colSet) if (!skipLocalSet.contains(col2)) {
            val flag2 = !arithmeticSkipSet.exists(col2.contains(_))
            for (placeHolder2 <- PLACE_HOLDER) if (!placeHolder2.eq("'PingCAP'") || flag2) {
              res += select() + where(
                binaryOpWithName(
                  col,
                  placeHolder,
                  "=") + " and " +
                binaryOpWithName(
                  col2,
                  placeHolder2,
                  op)
              )
            }
          }
        }
      }
    }

    res.toList
  }

  // ***********************************************************************************************
  // ******************************** Below is SQL build helper ************************************


  def groupBy(cols: String*): String = {
    s" group by $cols ".replace("WrappedArray", "")
  }

  def countId(): String = {
    s" count(1) "
  }

  def select(cols: String*): String = {
    var colList = ""
    for (col <- cols) {
      colList += col + ","
    }

    if (colList.length > 0) {
      colList = colList.substring(0, colList.length - 1)
    } else {
      colList = "*"
    }

    s"select " +
      colList +
      s" from " +
      s"$TABLE_NAME "
  }

  def orderBy(cols: String*): String = {
    s" order by $cols ".replace("WrappedArray", "").replace("(", "").replace(")", "")
  }

  def where(condition: String): String = {
    " where " + condition
  }

  def binaryOpWithName(leftCol: String, rightCol: String, op: String): String = {
    leftCol + " " + op + " " + rightCol
  }

  def arithmeticOp(l: String, r: String, op: String): String = {
    l + " " + op + " " + r
  }

  def limit(num: Int = 20): String = {
    " limit " + num
  }

}
