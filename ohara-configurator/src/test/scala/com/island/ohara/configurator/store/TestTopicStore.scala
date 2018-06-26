package com.island.ohara.configurator.store

import com.island.ohara.integration.OharaTestUtil
import com.island.ohara.io.CloseOnce.{close, _}
import com.island.ohara.rule.MediumTest
import com.island.ohara.serialization.StringSerializer
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
class TestTopicStore extends MediumTest with Matchers {

  private[this] val testUtil = new OharaTestUtil(3, 3)
  private[this] var store: Store[String, String] = _

  @Test
  def testRestart(): Unit = {
    store.update("aa", "bb") shouldBe None
    store.get("aa") shouldBe Some("bb")
    store.close()
    val another = Store
      .builder(StringSerializer, StringSerializer)
      .brokers(testUtil.brokersString)
      .topicName(testName.getMethodName)
      .build()
    try {
      testUtil.await(() => another.get("aa").isDefined, 10 seconds)
      another.get("aa") shouldBe Some("bb")
    } finally another.close()

  }

  /**
    * In this test we create extra 10 stores to test the data synchronization. All of them are based on the same kafka topic so any change
    * to one of them should be synced to other stores.
    */
  @Test
  def testMultiStore(): Unit = {
    val numberOfStore = 5
    val stores = 0 until numberOfStore map (index =>
      Store
        .builder(StringSerializer, StringSerializer)
        .brokers(testUtil.brokersString)
        .topicName(testName.getMethodName)
        .build())
    0 until 10 foreach (index => store.update(index.toString, index.toString))
    store.size shouldBe 10

    // make sure all stores have synced the updated data
    testUtil.await(() => stores.filter(_.size == 10).size == numberOfStore, 30 second)

    stores.foreach(s => {
      0 until 10 foreach (index => s.get(index.toString) shouldBe Some(index.toString))
    })

    // remove all data
    val randomStore = stores.iterator.next()
    0 until 10 foreach (index => randomStore.remove(index.toString) shouldBe Some(index.toString))

    // make sure all stores have synced the updated data
    testUtil.await(() => stores.filter(_.isEmpty).size == numberOfStore, 30 second)

    // This store is based on another topic so it should have no data
    val anotherStore = Store
      .builder(StringSerializer, StringSerializer)
      .brokers(testUtil.brokersString)
      .topicName(testName.getMethodName + "copy")
      .build()
    anotherStore.size shouldBe 0
  }
  @Test
  def testTake(): Unit = {
    0 until 10 foreach (index => store.update(index.toString, index.toString))
    store.size shouldBe 10
    var count = 0
    var done = false
    while (!done) {
      val data = store.take()
      if (data.isEmpty) done = true
      else
        data.foreach {
          case (key, value) => {
            key shouldBe count.toString
            value shouldBe count.toString
            count += 1
          }
        }
    }
    count shouldBe 10

    doClose(
      Store
        .builder(StringSerializer, StringSerializer)
        .brokers(testUtil.brokersString)
        .topicName(s"${testName.getMethodName}-copy")
        .build()) { another =>
      {
        another.size shouldBe 0
      }
    }
  }

  @Test
  def testUpdate() = {
    0 until 10 foreach (index => store.update(index.toString, index.toString) shouldBe None)
    0 until 10 foreach (index => store.update(index.toString, index.toString) shouldBe Some(index.toString))
    0 until 10 foreach (index => store.get(index.toString) shouldBe Some(index.toString))
  }

  @Test
  def testRemove(): Unit = {
    0 until 10 foreach (index => store.update(index.toString, index.toString))

    val removed = new ArrayBuffer[String]
    for (index <- 0 until 10) {
      store.get(index.toString) shouldBe Some(index.toString)
      if (index % 2 == 0) {
        store.remove(index.toString)
        removed += index.toString
      }
    }
    removed.foreach(store.get(_) shouldBe None)
  }

  @Test
  def testIterable(): Unit = {
    0 until 10 foreach (index => store.update(index.toString, index.toString))
    store.size shouldBe 10
    store.foreach {
      case (k, v) => k shouldBe v
    }
  }

  @Before
  def before(): Unit = {
    store = Store
      .builder(StringSerializer, StringSerializer)
      .brokers(testUtil.brokersString)
      .topicName(testName.getMethodName)
      .build()
  }
  @After
  def tearDown(): Unit = {
    close(store)
    close(testUtil)
  }
}