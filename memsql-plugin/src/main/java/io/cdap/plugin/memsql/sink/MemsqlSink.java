/*
 * Copyright © 2019 Cask Data, Inc.
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

package io.cdap.plugin.memsql.sink;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.batch.BatchSink;
import io.cdap.plugin.db.batch.sink.AbstractDBSink;
import io.cdap.plugin.db.batch.sink.FieldsValidator;
import io.cdap.plugin.memsql.MemsqlConstants;

/**
 * Sink support for a MemSQL database.
 */
@Plugin(type = BatchSink.PLUGIN_TYPE)
@Name(MemsqlConstants.PLUGIN_NAME)
@Description("Writes records to a MemSQL table. Each record will be written in a row in the table")
public class MemsqlSink extends AbstractDBSink<MemsqlSinkConfig> {

  private final MemsqlSinkConfig memsqlSinkConfig;

  public MemsqlSink(MemsqlSinkConfig memsqlSinkConfig) {
    super(memsqlSinkConfig);
    this.memsqlSinkConfig = memsqlSinkConfig;
  }

  @Override
  protected FieldsValidator getFieldsValidator() {
    return new MemsqlFieldsValidator();
  }
}
