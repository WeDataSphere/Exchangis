/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.exchangis.datasource.linkis.request

import org.apache.linkis.datasource.client.config.DatasourceClientConfig.METADATA_OLD_SERVICE_MODULE
import org.apache.linkis.datasource.client.request.DataSourceAction
import org.apache.linkis.httpclient.request.GetAction

/**
 * Action to check whether the table exists(校验数据表是否存在)
 */
class MetadataExistsTableAction extends GetAction with DataSourceAction{
  /**
   * Data source id
   */
  private var dataSourceId: Long = _

  /**
   * Database
   */
  private var database: String = _

  /**
   * Table
   */
  private var table: String = _

  override def suffixURLs: Array[String] = Array(METADATA_OLD_SERVICE_MODULE.getValue, "exists", dataSourceId.toString, "db", database, "table", table)

  private var user: String = _

  override def setUser(user: String): Unit = this.user = user

  override def getUser: String = this.user

  /**
   * Just use the constructor instead of builder
   * @param dataSourceId data source id
   * @param database database
   * @param table table
   * @param system system
   */
  def this(dataSourceId: Long, database: String, table: String, system: String){
    this()
    this.dataSourceId = dataSourceId
    this.database = database
    this.table = table
    setParameter("system", system)
  }
}
