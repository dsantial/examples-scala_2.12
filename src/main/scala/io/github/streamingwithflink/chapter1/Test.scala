package io.github.streamingwithflink.chapter1


import io.github.streamingwithflink.util.{SensorReading, SensorSource, SensorTimeAssigner}

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.apache.flink.streaming.api.scala._

object Test {

  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    env.getConfig.setAutoWatermarkInterval(1000L)

    val sensorData: DataStream[SensorReading] = env
      .addSource(new SensorSource)
      .assignTimestampsAndWatermarks(new SensorTimeAssigner)

    val avgTemp = sensorData
      .map(r =>
        SensorReading(r.id, r.timestamp, (r.temperature - 32) * (5.0 / 9.0)))
    .keyBy(_.id)
    .timeWindow(Time.seconds(1L))
    .apply(new TempAvg)

    avgTemp.print()
    env.execute("computer avg tempera")
  }

}

class  TempAvg extends WindowFunction[SensorReading,SensorReading,String,TimeWindow] {

  override def apply(
                      sensorId: String,
                      window: TimeWindow,
                      vals: Iterable[SensorReading],
                      out: Collector[SensorReading]): Unit = {
    val (cnt, sum) = vals.foldLeft((0, 0.0))((c, r) => (c._1 + 1, c._2 + r.temperature))
    val avgTemp = sum / cnt

    // emit a SensorReading with the average temperature
    out.collect(SensorReading(sensorId, window.getEnd, avgTemp))
  }
}