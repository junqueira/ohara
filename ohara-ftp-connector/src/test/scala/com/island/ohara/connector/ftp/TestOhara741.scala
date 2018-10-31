package com.island.ohara.connector.ftp
import com.island.ohara.integration.FtpServer
import com.island.ohara.io.CloseOnce
import com.island.ohara.kafka.connector.TaskConfig
import com.island.ohara.rule.SmallTest
import org.junit.{After, Test}
import com.island.ohara.client.FtpClient
import org.scalatest.Matchers

class TestOhara741 extends SmallTest with Matchers {

  private[this] val ftpServer = FtpServer.local(0, 0)

  @Test
  def testAutoCreateOutput(): Unit = {
    val props = FtpSinkProps(
      output = "/output",
      needHeader = false,
      user = ftpServer.user,
      password = ftpServer.password,
      host = ftpServer.host,
      port = ftpServer.port,
      encode = Some("UTF-8")
    )

    val taskConfig = TaskConfig(
      name = "aa",
      topics = Seq.empty,
      schema = Seq.empty,
      options = props.toMap
    )

    val sink = new FtpSink
    sink._start(taskConfig)

    val ftpClient = FtpClient
      .builder()
      .host(ftpServer.host)
      .port(ftpServer.port)
      .user(ftpServer.user)
      .password(ftpServer.password)
      .build()
    try ftpClient.exist("/output") shouldBe true
    finally ftpClient.close()
  }

  @After
  def tearDown(): Unit = {
    CloseOnce.close(ftpServer)
  }
}