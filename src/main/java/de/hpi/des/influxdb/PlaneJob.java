package de.hpi.des.influxdb;

import static org.apache.flink.table.api.Expressions.$;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.sink.SinkWriter;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.influxdb.common.DataPoint;
import org.apache.flink.streaming.connectors.influxdb.sink.InfluxDBSink;
import org.apache.flink.streaming.connectors.influxdb.sink.writer.InfluxDBSchemaSerializer;
import org.apache.flink.streaming.connectors.influxdb.source.InfluxDBSource;
import org.apache.flink.streaming.connectors.influxdb.source.reader.deserializer.InfluxDBDataPointDeserializer;
import org.apache.flink.streaming.util.FiniteTestSource;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaneJob {
	private static final Logger LOG = LoggerFactory.getLogger(PlaneJob.class);

	public static void main(String[] args) throws Exception {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
		env.setParallelism(1);

		EnvironmentSettings bsSettings = EnvironmentSettings.newInstance().useBlinkPlanner().inStreamingMode().build();
		StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, bsSettings);

		SourceFunction<Airline> airlinesSource = new FiniteTestSource<>(readAirlines());
		DataStream<Airline> airlinesStream = env.addSource(airlinesSource, TypeInformation.of(Airline.class));
		tableEnv.createTemporaryView("airlines", airlinesStream, $("icaoCode"), $("name"));

		InfluxDBSource<Flight> flightsSource = InfluxDBSource.<Flight>builder()
                        .setDeserializer(new InfluxDBFlightDeserializer())
						.setEnqueueWaitTime(1000)
                        .build();
		DataStream<Flight> flightsStream = env.fromSource(flightsSource, WatermarkStrategy.noWatermarks(), "InfluxDBSource");
		tableEnv.createTemporaryView("flights", flightsStream, $("flightNumber"), $("airlineIcaoCode"), $("isOnGround"), $("seenAt"));

		tableEnv.executeSql("CREATE VIEW rich_flights AS SELECT flights.flightNumber, flights.isOnGround, flights.seenAt, flights.airlineIcaoCode, airlines.name FROM flights LEFT JOIN airlines ON flights.airlineIcaoCode = airlines.icaoCode");

		final InfluxDBSink<Flight> influxDBSink =
				InfluxDBSink.<Flight>builder()
					    .setInfluxDBUrl("http://influxdb-influxdb2")
						.setInfluxDBUsername("admin")
						.setInfluxDBPassword("adminadmin")
						.setInfluxDBBucket("default")
						.setInfluxDBOrganization("influxdata")
						.setInfluxDBSchemaSerializer(new InfluxDBFlightSerializer())
						.setWriteBufferSize(1)
						.build();

		DataStream<Tuple2<Boolean,Row>> dsRow = tableEnv.toRetractStream(tableEnv.from("rich_flights"), Row.class);
		dsRow.process(new ProcessFunction<Tuple2<Boolean,Row>,Flight>(){
			private static final long serialVersionUID = 1L;

			@Override
			public void processElement(Tuple2<Boolean, Row> value, Context ctx,
					Collector<Flight> out) throws Exception {
				Row row = value.f1;
				Flight flight = new Flight();
				flight.flightNumber = (String)row.getField(0);
				flight.isOnGround = (Boolean)row.getField(1);
				flight.seenAt = (Long)row.getField(2);
				flight.airlineIcaoCode = (String)row.getField(3);
				Object airlineName = row.getField(4);
				if(airlineName instanceof String) {
					flight.airlineName = (String)airlineName;
				}
				LOG.info("Processing flight {}", flight);
				out.collect(flight);
			}
		}).sinkTo(influxDBSink);

		env.execute("Airplanes");
	}

	private static class InfluxDBFlightDeserializer implements InfluxDBDataPointDeserializer<Flight> {
		private static final long serialVersionUID = 1L;

		@Override
		public Flight deserialize(final DataPoint point) {
			Flight flight = new Flight();
			flight.flightNumber = point.getTag("flight");
			if(flight.flightNumber != null && flight.flightNumber.length() >= 3) {
				flight.airlineIcaoCode = flight.flightNumber.substring(0, 3);
			}
			Object groundValue = point.getField("ground");
			flight.isOnGround = (groundValue instanceof String && (String) groundValue == "true") ? true : false;
			flight.seenAt = (Long)point.getTimestamp();
			return flight;
		}
	}

	private static class InfluxDBFlightSerializer implements InfluxDBSchemaSerializer<Flight> {
		private static final long serialVersionUID = 1L;

		@Override
		public Point serialize(final Flight elem, final SinkWriter.Context context) {
			final Point dataPoint = new Point("flight");
			dataPoint.addTag("flightNumber", elem.flightNumber);
			dataPoint.addField("isOnGround", elem.isOnGround);
			dataPoint.time(elem.seenAt, WritePrecision.MS);
			if(elem.airlineName != null) {
				dataPoint.addTag("airlineName", elem.airlineName);
			}
			return dataPoint;
		}
	}

	// https://www.baeldung.com/java-csv-file-array
	private static List<Airline> readAirlines() throws UnsupportedEncodingException {
		InputStream stream = PlaneJob.class.getResourceAsStream("/airlines.dat");
		BufferedReader r = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
		return r.lines()
                .skip(1)        // skip header
                .map(line -> line.split(","))
                .map(snippets -> {
					Airline airline = new Airline();
					airline.name = snippets[1].replace("\"", "");
					airline.icaoCode = snippets[4].replace("\"", "");
					return airline;
				})
                .collect(Collectors.toList());
	}
}
