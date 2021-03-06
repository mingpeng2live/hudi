/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.execution;

import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.table.timeline.HoodieActiveTimeline;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.queue.BoundedInMemoryQueueConsumer;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.execution.LazyInsertIterable.HoodieInsertValueGenResult;
import org.apache.hudi.testutils.HoodieClientTestHarness;
import org.apache.hudi.testutils.HoodieTestDataGenerator;

import org.apache.avro.generic.IndexedRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import scala.Tuple2;

import static org.apache.hudi.execution.LazyInsertIterable.getTransformFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSparkBoundedInMemoryExecutor extends HoodieClientTestHarness {

  private final String instantTime = HoodieActiveTimeline.createNewInstantTime();

  @BeforeEach
  public void setUp() throws Exception {
    initTestDataGenerator();
  }

  @AfterEach
  public void tearDown() throws Exception {
    cleanupTestDataGenerator();
  }

  @Test
  public void testExecutor() {

    final List<HoodieRecord> hoodieRecords = dataGen.generateInserts(instantTime, 100);

    HoodieWriteConfig hoodieWriteConfig = mock(HoodieWriteConfig.class);
    when(hoodieWriteConfig.getWriteBufferLimitBytes()).thenReturn(1024);
    BoundedInMemoryQueueConsumer<HoodieInsertValueGenResult<HoodieRecord>, Integer> consumer =
        new BoundedInMemoryQueueConsumer<HoodieInsertValueGenResult<HoodieRecord>, Integer>() {

          private int count = 0;

          @Override
          protected void consumeOneRecord(HoodieInsertValueGenResult<HoodieRecord> record) {
            count++;
          }

          @Override
          protected void finish() {
          }

          @Override
          protected Integer getResult() {
            return count;
          }
        };

    SparkBoundedInMemoryExecutor<HoodieRecord, Tuple2<HoodieRecord, Option<IndexedRecord>>, Integer> executor = null;
    try {
      executor = new SparkBoundedInMemoryExecutor(hoodieWriteConfig, hoodieRecords.iterator(), consumer,
          getTransformFunction(HoodieTestDataGenerator.AVRO_SCHEMA));
      int result = executor.execute();
      // It should buffer and write 100 records
      assertEquals(100, result);
      // There should be no remaining records in the buffer
      assertFalse(executor.isRemaining());
    } finally {
      if (executor != null) {
        executor.shutdownNow();
      }
    }
  }
}
