package com.island.ohara.configurator.call

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentSkipListMap, CountDownLatch, Executors, TimeUnit}

import com.island.ohara.config.UuidUtil
import com.island.ohara.data.{OharaData, OharaException}
import com.island.ohara.io.CloseOnce
import com.island.ohara.kafka.{Consumer, KafkaUtil, Producer}
import com.island.ohara.serialization.Serializer
import com.typesafe.scalalogging.Logger
import org.apache.kafka.common.errors.WakeupException

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

/**
  * A request-> response based call queue. This implementation is based on kafka topic. It have a kafka consumer and a
  * kafka producer internally. The conumser is used to receive the RESPONSE or OharaException from call queue server.
  * The producer is used send the REQUEST to call queue server.
  *
  * @param brokers               KAFKA server
  * @param topicName             topic name. the topic will be created automatically if it doesn't exist
  * @param pollTimeout           the specified waiting time elapses to poll the consumer
  * @param initializationTimeout the specified waiting time to initialize this call queue client
  * @param expirationCleanupTime the time to call the lease dustman
  * @tparam Request  the supported request type
  * @tparam Response the supported response type
  */
private class CallQueueClientImpl[Request <: OharaData, Response <: OharaData: ClassTag](
  brokers: String,
  topicName: String,
  pollTimeout: Duration,
  initializationTimeout: Duration,
  expirationCleanupTime: Duration)
    extends CallQueueClient[Request, Response] {

  private[this] val logger = Logger(getClass.getName)

  /**
    * responseWorker thread + expiredRequestDustman thread
    */
  private[this] implicit val executor =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  /**
    * We have to trace each request so we need a incrementable index.
    */
  private[this] val indexer = new AtomicLong(0)

  /**
    * the uuid for this instance
    */
  private[this] val uuid: String = UuidUtil.uuid

  if (!KafkaUtil.exist(brokers, topicName, initializationTimeout))
    throw new IllegalArgumentException(s"The topic:$topicName doesn't exist")

  /**
    * used to publish the request.
    */
  private[this] val producer = newOrClose {
    Producer.builder(Serializer.OHARA_DATA, Serializer.OHARA_DATA).brokers(brokers).build()
  }

  /**
    * used to receive the response.
    */
  private[this] val consumer = newOrClose {
    Consumer
      .builder(Serializer.OHARA_DATA, Serializer.OHARA_DATA)
      .brokers(brokers)
      // the uuid of requestConsumer is random since we want to check all response.
      .groupId(uuid)
      .offsetAfterLatest()
      .topicName(topicName)
      .build()
  }

  /**
    * Used to check whether consumer have completed the first poll.
    */
  private[this] val initializationLatch = new CountDownLatch(1)

  /**
    * store the response handler against the OharaResponse.
    * We use ConcurrentSkipListMap rather than ConcurrentHashMap because we need the method of polling the first element.
    */
  private[this] val responseReceivers = new ConcurrentSkipListMap[String, ResponseReceiver]()

  /**
    * 2) OharaResponse -> OharaData
    * this group is generated by handler. It show that handler have done for the request, and it works. the response is passed
    * to the internal response handler used to accept the response and notify the user who is waiting for the response
    */
  private[this] val responseWorker = Future[Unit] {
    try {
      while (!this.isClosed) {
        try {
          val records = consumer.poll(pollTimeout)
          initializationLatch.countDown()
          records
            .filter(_.topic.equals(topicName))
            .foreach(record => {
              record.key.foreach(k =>
                k match {
                  case internalResponse: OharaResponse =>
                    record.value.foreach(v =>
                      v match {
                        case response: Response if (responseReceivers.containsKey(internalResponse.requestId)) => {
                          // NOTED: the uuid we record is OharaRequest'd uuid
                          responseReceivers.remove(internalResponse.requestId).complete(response)
                        }
                        case exception: OharaException
                            if (responseReceivers.containsKey(internalResponse.requestId)) => {
                          // NOTED: the uuid we record is OharaRequest'd uuid
                          responseReceivers.remove(internalResponse.requestId).complete(exception)
                        }
                        case _ => // this response is not for this client
                    })
                  case _: OharaRequest => // This is call queue server's job
                  case _ =>
                    logger.error(s"unsupported key:$k. The supported key by call queue client is OharaResponse")
              })
            })
        } catch {
          case _: WakeupException => logger.debug("interrupted by ourself")
        }
      }
    } catch {
      case e: Throwable => logger.error("failure when running the responseWorker", e)
    } finally {
      initializationLatch.countDown()
      close()
    }
  }

  /**
    * used to notify the dustman to do its job
    */
  private[this] val notifierOfDustman = new Object
  private[this] val expiredRequestDustman = Future[Unit] {
    try {
      while (!isClosed) {
        try {
          import scala.collection.JavaConverters._
          val expiredNotifiers = responseReceivers.values().asScala.filter(_.isTimeout).toArray
          expiredNotifiers.foreach(notifier => {
            val expired = responseReceivers.remove(notifier.requestUuid)
            if (expired != null) {
              expired.complete(OharaException.apply(CallQueue.EXPIRED_REQUEST_EXCEPTION))
            }
          })
          notifierOfDustman.synchronized {
            notifierOfDustman.wait(expirationCleanupTime.toMillis)
          }
        } catch {
          case _: InterruptedException => logger.debug("interrupt the dustman by ourself")
        }
      }
    } catch {
      case e: Throwable => logger.error("Failed to run the dustman", e)
    } finally close()

  }

  def wakeupDustman(): Unit = notifierOfDustman.synchronized {
    notifierOfDustman.notifyAll()
  }

  if (!initializationLatch.await(initializationTimeout.toMillis, TimeUnit.MILLISECONDS)) {
    close()
    throw new IllegalArgumentException(s"timeout to initialize the call queue server")
  }

  private[this] def requestUuid = s"$uuid-request-${indexer.getAndIncrement()}"

  override def request(request: Request, timeout: Duration): Future[Either[OharaException, Response]] = {
    checkClose()
    val lease = timeout + (System.currentTimeMillis() milliseconds)
    val internalRequest = OharaRequest.apply(requestUuid, lease)
    val receiver = new ResponseReceiver(internalRequest.uuid, lease)
    responseReceivers.put(internalRequest.uuid, receiver)
    try {
      producer.sender().topic(topicName).key(internalRequest).value(request).send()
      producer.flush()
    } catch {
      case exception: Throwable => {
        responseReceivers.remove(internalRequest.uuid).complete(OharaException.apply(exception))
      }
    }
    // an new request so it is time to invoke dustman to check the previous requests
    wakeupDustman()
    receiver.future
  }

  override protected def doClose(): Unit = {
    import scala.concurrent.duration._
    wakeupDustman()
    // release all notifiers
    Iterator
      .continually(responseReceivers.pollFirstEntry())
      .takeWhile(_ != null)
      .map(_.getValue)
      .foreach(_.complete(CallQueue.TERMINATE_TIMEOUT_EXCEPTION))
    if (consumer != null) consumer.wakeup()
    if (responseWorker != null) Await.result(responseWorker, 60 seconds)
    if (consumer != null) CloseOnce.close(consumer)
    if (producer != null) producer.close()
    if (expiredRequestDustman != null) Await.result(expiredRequestDustman, 60 seconds)
    if (executor != null) {
      executor.shutdownNow()
      executor.awaitTermination(60, TimeUnit.SECONDS)
    }
  }

  /**
    * Used to notify the request with a response or exception
    *
    * @param requestUuid the request uuid
    * @param lease       lease
    */
  private class ResponseReceiver(val requestUuid: String, lease: Duration) {
    private[this] val promise = Promise[Either[OharaException, Response]]()

    /**
      * complete the request with a response
      *
      * @param response response
      */
    def complete(response: Response): Unit = if (promise.isCompleted)
      throw new RuntimeException("You have completed this request")
    else promise success Right(response)

    /**
      * complete the request with a exception
      *
      * @param exception exception
      */
    def complete(exception: OharaException): Unit = if (promise.isCompleted)
      throw new RuntimeException("You have completed this request")
    else promise success Left(exception)

    /**
      * complete the request with a exception
      *
      * @param exception exception
      */
    def complete(exception: Throwable): Unit = complete(OharaException(exception))

    /**
      * @return true if this request is expired. false otherwise.
      */
    def isTimeout: Boolean = lease.toMillis <= System.currentTimeMillis

    def future: Future[Either[OharaException, Response]] = promise.future
  }

}
