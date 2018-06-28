package com.island.ohara.configurator.data

import com.island.ohara.config.{OharaConfig, OharaJson, OharaProperty}
import com.island.ohara.serialization.DataType

/**
  * a pojo to represent the description of ohara job
  * @param config stores all properties
  */
class OharaSchema(config: OharaConfig) extends OharaData(config) {

  override protected def extraProperties: Seq[OharaProperty[_]] = OharaSchema.properties

  def types: Map[String, DataType] = OharaSchema.columnType.require(config)
  def orders: Map[String, Int] = OharaSchema.columnOrder.require(config)

  def disabled: Boolean = OharaSchema.disabled.require(config)

  override def copy[T](prop: OharaProperty[T], value: T): OharaSchema = {
    val clone = config.snapshot
    prop.set(clone, value)
    new OharaSchema(clone)
  }
}

object OharaSchema {

  /**
    * Create the a ohara schema in json format. This helper method is used to sent the schema request to rest server.
    * NOTED: it is used in testing only
    * @param name name
    * @param types column types
    * @param orders column orders
    * @return json
    */
  def json(name: String, types: Map[String, DataType], orders: Map[String, Int], disabled: Boolean): OharaJson = {
    val config = OharaConfig()
    OharaData.name.set(config, name)
    columnType.set(config, types)
    columnOrder.set(config, orders)
    OharaSchema.disabled.set(config, disabled)
    config.toJson
  }

  /**
    * create a OharaSchema with specified config
    * @param json config in json format
    * @return a new OharaSchema
    */
  def apply(json: OharaJson): OharaSchema = apply(OharaConfig(json))

  /**
    * create a OharaSchema with specified config
    * @param config config
    * @return a new OharaSchema
    */
  def apply(config: OharaConfig): OharaSchema = new OharaSchema(config)

  /**
    * create an new OharaSchema with specified arguments
    * @param uuid uuid
    * @param json remaining options in json format
    * @return an new OharaSchema
    */
  def apply(uuid: String, json: OharaJson): OharaSchema = {
    val oharaConfig = OharaConfig(json)
    OharaData.uuid.set(oharaConfig, uuid)
    new OharaSchema(oharaConfig)
  }

  /**
    * create an new OharaSchema with specified arguments
    * @param uuid uuid
    * @param name target name
    * @param types columnName-type
    * @return an new OharaSchema
    */
  def apply(uuid: String,
            name: String,
            types: Map[String, DataType],
            orders: Map[String, Int],
            disabled: Boolean): OharaSchema = {
    val oharaConfig = OharaConfig()
    OharaData.uuid.set(oharaConfig, uuid)
    OharaData.name.set(oharaConfig, name)
    columnType.set(oharaConfig, types)
    columnOrder.set(oharaConfig, orders)
    OharaSchema.disabled.set(oharaConfig, disabled)
    new OharaSchema(oharaConfig)
  }

  def properties: Seq[OharaProperty[_]] = Array(columnType, columnOrder, disabled)

  val columnType: OharaProperty[Map[String, DataType]] =
    OharaProperty.builder
      .key("types")
      .description("the column type of ohara schema")
      .mapProperty(DataType.of(_), _.name)

  val columnOrder: OharaProperty[Map[String, Int]] =
    OharaProperty.builder.key("orders").description("the column order of ohara schema").mapProperty(_.toInt, _.toString)

  val disabled: OharaProperty[Boolean] =
    OharaProperty.builder
      .key("disabled")
      .description("true if this schema is selectable in UI. otherwise false")
      .booleanProperty
}
