import java.io.{File, FileInputStream}
import java.util.{Base64, Random, UUID}

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._
import scala.util.parsing.json.JSON


class ConnectionSimulation extends Simulation {

  val AWS = "scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-aws.sysdeseng.com"
  val GCE = "scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-gce.sysdeseng.com"
  var AZR = "scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-azr.sysdeseng.com"
  val PICTURES = Array(
    "src/test/resources/I love coffee.jpg",
    "src/test/resources/book2.jpg",
    "src/test/resources/book3.jpg",
    "src/test/resources/cellphone1.jpg",
    "src/test/resources/cellphone2.jpg",
    "src/test/resources/cellphone4.jpg",
    "src/test/resources/clock1.jpg",
    "src/test/resources/clock2.jpg",
    "src/test/resources/clock4.jpg",
    "src/test/resources/laptop4.jpg",
    "src/test/resources/person1.jpg",
    "src/test/resources/person2.jpg",
    "src/test/resources/person3.jpg",
    "src/test/resources/person4.jpg",
    "src/test/resources/wine.jpg"
  ).map(p => encode(p))
  val TASK_IDS = Array(
    "d6c33a1a-fde7-4134-b058-027395353aac",
    "d056e95e-fada-e7c8-ecfd-96464068ac53",
    "1aea97d6-0661-ee35-507b-29ce3c1ec060",
    "1c94dadc-6d3e-5a22-597c-980d1c970433",
    "999254aa-9021-0eb0-6b58-2107f91ded2b"
  )

  def encode(path: String) = {
    val originalFile = new File(path)
    var encodedBase64 = ""
    try {
      val reader = new FileInputStream(originalFile)
      val bytes = new Array[Byte](originalFile.length.toInt)
      reader.read(bytes)
      encodedBase64 = Base64.getEncoder.encodeToString(bytes)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    encodedBase64
  }

  def getHost(arg: String): String = arg match {
    case "AWS" => AWS
    case "GCE" => GCE
    case "AZR" => AZR
    case _ => arg
  }

  val host: String = getHost(System.getProperty("host", AWS))
  val numUsers: Int = Integer.getInteger("users", 20).toInt
  val numUploads: Int = Integer.getInteger("uploads", 5).toInt

  val protocol: HttpProtocolBuilder = http
    .baseURL("https://" + host)
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Gatling")
    .wsBaseURL("wss://" + host)

  val scn: ScenarioBuilder = scenario("Connection-Scenario")
    .exec(Connection.connect)
    .pause(10)
    .exec(Upload.upload)
    .pause(10)
    .exec(ws("First Closing").close)
    .pause(5)
    .exec(Reconnect.reconnect)
    .pause(10)
    .exec(ws("Final Closing").close)

  setUp(
    scn.inject(rampUsers(numUsers) over (60 seconds)).protocols(protocol)
  )
  

  object Connection {
    val connect: ChainBuilder =
      exec(ws("Connect to /game").open("/game"))
        .pause(1)
        .exec(
          ws("Send 'connection' frame")
            .sendText("""{"type": "connection"}""")
            .check(wsAwait.within(30).until(1)
              .jsonPath("$.playerId")
              .saveAs("playerId")
            )
        )
        .exec(ws("Reconciliate states").reconciliate)
  }


  object Upload {

    private val random = new Random()

    val upload: ChainBuilder =
      repeat(numUploads, "attempts") {
        exec(session => {
          val tx = UUID.randomUUID().toString
          val list = session.get("txs").asOption[List[String]].map(l => tx :: l).getOrElse(List(tx))
          val taskId = TASK_IDS(random.nextInt(TASK_IDS.length))
          val picture = PICTURES(random.nextInt(PICTURES.length))
          session
            .set("tx", tx)
            .set("txs", list)
            .set("taskId", taskId)
            .set("picture", picture)
        })
          .exec(
            ws("Upload a picture")
              .sendText(
                s"""
                   |{
                   |  "type":"picture",
                   |  "transactionId": "$${tx}",
                   |  "playerId": "$${playerId}",
                   |  "taskId": "$${taskId}",
                   |  "picture": "$${picture}",
                   |  "metadata": {"format":"jpg"}
                   |}
                 """.stripMargin)
              .check(wsAwait.within(240).until(1).regex(".*\"type\":\"score\".*").transform((s, session) => {
                val playerId = session("playerId").as[String]
                val txs = session("txs").as[List[String]]
                var success = true
                if (!s.contains("\"type\":\"score\"")) {
                  println("Invalid score frame - bad type: " + s)
                  success = false
                }

                if (success && !s.contains("\"playerId\":\"" + playerId + "\"")) {
                  println("Invalid score frame - wrong playerId: " + s + ", expected " + playerId)
                  success = false
                }

                val transactionID:Option[String] = JSON.parseFull(s).map(x => x.asInstanceOf[Map[String, Any]])
                  .map(j => j("transactionId"))
                  .map(s => s.asInstanceOf[String])
                
                if (success  && transactionID.isEmpty) {
                  println("Invalid score frame - no transactionId: " + s)
                  success = false
                }

                if (success  && ! txs.contains(transactionID.get)) {
                  println("Invalid score frame - wrong transactionId: " + transactionID.get + ", expected on of " + txs)
                  success = false
                }

                if (success && !s.contains("\"score\":")) {
                  println("Invalid score frame - no score: " + s)
                  success = false
                }

                if (success && !s.contains("\"total\":")) {
                  println("Invalid score frame - no total: " + s)
                  success = false
                }

                if (success) "ok" else "ko"

              }).is("ok"))
          )
          .pause(10 + random.nextInt(5))
      }
  }

  object Reconnect {
    val reconnect: ChainBuilder =
      exec(ws("Reconnect to Web Socket").open("/game"))
        .pause(1)
        .exec(
          ws("Send connect frame with id")
            .sendText(s"""{"type": "connection", "playerId": "$${playerId}"}""")
            .check(wsAwait.within(120).until(1).regex(".*\"type\":\"configuration\".*").transform((s, session) => {
              val playerId = session("playerId").as[String]
              var  success = true
              if (success && !s.contains("\"playerId\":\"" + playerId + "\"")) {
                println("Invalid configuration frame - wrong playerId: " + s + ", expected " + playerId)
                success = false
              }
              success
            }))
        )
  }

}
