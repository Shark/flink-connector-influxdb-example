/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hpi.des.influxdb;

import com.influxdb.client.write.Point;
import java.util.Arrays;
import java.util.List;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.connector.sink.SinkWriter;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.influxdb.common.InfluxDBConfig;
import org.apache.flink.streaming.connectors.influxdb.sink.InfluxDBSink;
import org.apache.flink.streaming.connectors.influxdb.sink.writer.InfluxDBSchemaSerializer;
import org.apache.flink.streaming.util.FiniteTestSource;

import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
@Log4j2
public class StreamingJob {

	private static final List<Long> SOURCE_DATA = Arrays.asList(1L, 2L, 3L);

	public static void main(String[] args) throws Exception {
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		/*
		 * Here, you can start creating your execution plan for Flink.
		 *
		 * Start with getting some data from the environment, like
		 * 	env.readTextFile(textPath);
		 *
		 * then, transform the resulting DataStream<String> using operations
		 * like
		 * 	.filter()
		 * 	.flatMap()
		 * 	.join()
		 * 	.coGroup()
		 *
		 * and many more.
		 * Have a look at the programming guide for the Java API:
		 *
		 * https://flink.apache.org/docs/latest/apis/streaming/index.html
		 *
		 */

		env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
		env.setParallelism(1);
		env.enableCheckpointing(100);

		final InfluxDBConfig influxDBConfig =
				InfluxDBConfig.builder()
						.url("http://influxdb-influxdb2")
						.username("admin")
						.password("adminadmin")
						.bucket("default")
						.organization("influxdata")
						.build();

		final InfluxDBSink<Long> influxDBSink =
				InfluxDBSink.<Long>builder()
						.influxDBSchemaSerializer(new InfluxDBTestSerializer())
						.influxDBConfig(influxDBConfig)
						.build();

		env.addSource(new FiniteTestSource(SOURCE_DATA), BasicTypeInfo.LONG_TYPE_INFO)
				.sinkTo(influxDBSink);

		log.info("About to start! ✨");

		// execute program
		env.execute("Flink InfluxDB Test Job");
	}

	private static class InfluxDBTestSerializer implements InfluxDBSchemaSerializer<Long> {
		@Override
		public Point serialize(final Long element, final SinkWriter.Context context) {
			final Point dataPoint = new Point("test");
			dataPoint.addTag("longValue", String.valueOf(element));
			dataPoint.addField("fieldKey", "fieldValue");
			return dataPoint;
		}
	}
}
