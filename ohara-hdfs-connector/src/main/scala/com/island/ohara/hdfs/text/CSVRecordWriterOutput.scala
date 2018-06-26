package com.island.ohara.hdfs.text

import java.io.OutputStream

import com.island.ohara.core.Row
import com.island.ohara.hdfs.HDFSSinkConnectorConfig
import com.island.ohara.hdfs.storage.Storage
import com.island.ohara.io.ByteUtil
import com.typesafe.scalalogging.Logger

/**
  * Data write to temp file for csv file format
  * @param config
  * @param storage
  */
class CSVRecordWriterOutput(config: HDFSSinkConnectorConfig, storage: Storage, filePath: String)
    extends RecordWriterOutput {
  private[this] lazy val logger = Logger(getClass().getName())
  logger.info("open temp file")
  var outputStream: OutputStream = storage.open(filePath, false)

  /**
    * Write file for csv format
    * @param row
    */
  override def write(row: Row): Unit = {
    val line: String = row.map(x => x.value).mkString(",")
    this.logger.debug(s"data line: ${line}")
    this.outputStream.write(ByteUtil.toBytes(line + "\n"))
  }

  /**
    * close OutputStream object
    */
  override def close(): Unit = {
    logger.info("close temp file")
    this.outputStream.close()
  }
}