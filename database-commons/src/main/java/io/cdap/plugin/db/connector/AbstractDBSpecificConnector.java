/*
 * Copyright © 2021 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.db.connector;

import com.google.common.collect.Maps;
import io.cdap.cdap.api.data.batch.InputFormatProvider;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.batch.BatchConnector;
import io.cdap.cdap.etl.api.connector.ConnectorContext;
import io.cdap.cdap.etl.api.connector.SampleRequest;
import io.cdap.plugin.common.SourceInputFormatProvider;
import io.cdap.plugin.common.db.AbstractDBConnector;
import io.cdap.plugin.common.db.DBConnectorPath;
import io.cdap.plugin.common.util.ExceptionUtils;
import io.cdap.plugin.db.CommonSchemaReader;
import io.cdap.plugin.db.ConnectionConfig;
import io.cdap.plugin.db.ConnectionConfigAccessor;
import io.cdap.plugin.db.SchemaReader;
import io.cdap.plugin.db.batch.source.DataDrivenETLDBInputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.lib.db.DBConfiguration;
import org.apache.hadoop.mapreduce.lib.db.DBWritable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * An Abstract DB Specific Connector those specific DB connectors can inherits
 * @param <T> the Record type that specific DB Record Reader may return while sample the data with InputFormat
 */
public abstract class AbstractDBSpecificConnector<T extends DBWritable> extends AbstractDBConnector
  implements BatchConnector<LongWritable, T> {

  private final AbstractDBConnectorConfig config;

  protected AbstractDBSpecificConnector(AbstractDBConnectorConfig config) {
    super(config);
    this.config = config;
  }

  public abstract boolean supportSchema();

  protected abstract Class<? extends DBWritable> getDBRecordType();

  protected SchemaReader getSchemaReader() {
    return new CommonSchemaReader();
  }

  @Override
  protected DBConnectorPath getDBConnectorPath(String path) throws IOException {
    return DBSpecificPath.of(path, supportSchema());
  }

  @Override
  public InputFormatProvider getInputFormatProvider(ConnectorContext context, SampleRequest request)
    throws IOException {
    DBConnectorPath path = getDBConnectorPath(request.getPath());
    if (path.getTable() == null) {
      throw new IllegalArgumentException(
        String.format("Path %s cannot be sampled. Must have table name in the path.", request.getPath()));
    }

    ConnectionConfigAccessor connectionConfigAccessor = new ConnectionConfigAccessor();
    if (config.getUser() == null && config.getPassword() == null) {
      DBConfiguration.configureDB(connectionConfigAccessor.getConfiguration(), driverClass.getName(),
                     getConnectionString(path.getDatabase()));
    } else {
      DBConfiguration.configureDB(connectionConfigAccessor.getConfiguration(), driverClass.getName(),
                     getConnectionString(path.getDatabase()), config.getUser(), config.getPassword());
    }
    String tableQuery = getTableQuery(path.getDatabase(), path.getSchema(), path.getTable(), request.getLimit());
    DataDrivenETLDBInputFormat.setInput(connectionConfigAccessor.getConfiguration(), getDBRecordType(),
                                        tableQuery, null, false);
    connectionConfigAccessor.setConnectionArguments(Maps.fromProperties(config.getConnectionArgumentsProperties()));
    connectionConfigAccessor.getConfiguration().setInt(MRJobConfig.NUM_MAPS, 1);
    Map<String, String> additionalArguments = config.getAdditionalArguments();
    for (Map.Entry<String, String> argument : additionalArguments.entrySet()) {
      connectionConfigAccessor.getConfiguration().set(argument.getKey(), argument.getValue());
    }
    try {
      connectionConfigAccessor.setSchema(loadTableSchema(getConnection(path),  tableQuery).toString());
    } catch (SQLException e) {
      throw new IOException(String.format("Failed to get table schema due to: %s.",
                                          ExceptionUtils.getRootCauseMessage(e)), e);
    }


    return new SourceInputFormatProvider(DataDrivenETLDBInputFormat.class, connectionConfigAccessor.getConfiguration());
  }

  protected Connection getConnection(DBConnectorPath path) {
    return getConnection(getConnectionString(path.getDatabase()) , config.getConnectionArgumentsProperties());
  }

  protected String getConnectionString(String database) {
    return config.getConnectionString();
  }

  protected String getTableQuery(String database, String schema, String table) {
    return schema == null ? String.format("SELECT * FROM \"%s\".\"%s\"", database, table)
      : String.format("SELECT * FROM \"%s\".\"%s\".\"%s\"", database, schema, table);
  }

  protected String getTableQuery(String database, String schema, String table, int limit) {
    return schema == null ?
      String.format("SELECT * FROM \"%s\".\"%s\" LIMIT %d", database, table, limit) :
      String.format(
        "SELECT * FROM \"%s\".\"%s\".\"%s\" LIMIT %d", database, schema, table, limit);
  }

  protected Schema loadTableSchema(Connection connection, String query) throws SQLException {
    Statement statement = connection.createStatement();
    statement.setMaxRows(1);
    ResultSet resultSet = statement.executeQuery(query);
    return Schema.recordOf("outputSchema", getSchemaReader().getSchemaFields(resultSet));
  }

  protected void setConnectionProperties(Map<String, String> properties) {
    Map<String, String> rawProperties = config.getRawProperties().getProperties();
    properties.put(ConnectionConfig.HOST, rawProperties.get(ConnectionConfig.HOST));
    properties.put(ConnectionConfig.PORT, rawProperties.get(ConnectionConfig.PORT));
    properties.put(ConnectionConfig.JDBC_PLUGIN_NAME, rawProperties.get(ConnectionConfig.JDBC_PLUGIN_NAME));
    properties.put(ConnectionConfig.USER, rawProperties.get(ConnectionConfig.USER));
    properties.put(ConnectionConfig.PASSWORD, rawProperties.get(ConnectionConfig.PASSWORD));
    properties.put(ConnectionConfig.CONNECTION_ARGUMENTS, rawProperties.get(ConnectionConfig.CONNECTION_ARGUMENTS));
  }

  @Override
  protected Schema getTableSchema(Connection connection, String database,
                                  String schema, String table) throws SQLException {

    return loadTableSchema(getConnection(),  getTableQuery(database, schema, table));
  }
}
