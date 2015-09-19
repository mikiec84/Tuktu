package tuktu.api

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import akka.actor.ActorRef
import play.api.libs.json.JsObject
import play.api.cache.Cache
import play.api.Application

case class DataPacket(
        data: List[Map[String, Any]]
) extends java.io.Serializable

case class DispatchRequest(
        configName: String,
        config: Option[JsValue],
        isRemote: Boolean,
        returnRef: Boolean,
        sync: Boolean,
        sourceActor: Option[ActorRef]
)

case class InitPacket()

case class StopPacket()

case class ResponsePacket(
        json: JsValue
)

case class HealthCheck()
case class HealthReply()

case class ClusterNode(
        host: String,
        akkaPort: Int,
        UIPort: Int
)

/**
 * Monitor stuff
 */

sealed abstract class MPType
case object BeginType extends MPType
case object EndType extends MPType
case object CompleteType extends MPType

case class MonitorPacket(
        typeOf: MPType,
        uuid: String,
        branch: String,
        amount: Integer
)

case class MonitorOverviewPacket()
case class MonitorOverviewResult(
        runningJobs: Map[String, AppMonitorObject],
        finishedJobs: Map[String, (Long, Long, Int, Int)],
        monitorData: Map[String, collection.mutable.Map[MPType, collection.mutable.Map[String, Int]]]
)

case class AppMonitorObject(
        actor: ActorRef,
        instances: Int,
        var finished_instances: Int = 0,
        startTime: Long = System.currentTimeMillis
)

case class AppMonitorPacket(
        val actor: ActorRef,
        val status: String,
        val timestamp: Long = System.currentTimeMillis
) {
    def getName = actor.path.toStringWithoutAddress
    def getParentName = actor.path.parent.toStringWithoutAddress 
}

case class AddMonitorEventListener()
case class RemoveMonitorEventListener()

case class ActorIdentifierPacket(
        uuid: String,
        instanceCount: Int,
        mailbox: ActorRef
)
case class ErorNotificationPacket(
        uuid: String
)
/**
 * End monitoring stuff
 */

abstract class BaseProcessor(resultName: String) {
    def initialize(config: JsObject): Unit = {}
    def processor(): Enumeratee[DataPacket, DataPacket] = ???
}

/**
 * Definition of a processor
 */
case class ProcessorDefinition(
        id: String,
        name: String,
        config: JsObject,
        resultName: String,
        next: List[String]
)

abstract class BufferProcessor(genActor: ActorRef, resultName: String) extends BaseProcessor(resultName: String) {}

abstract class BaseGenerator(resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef]) extends Actor with ActorLogging {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)
    val (enumerator, channel) = Concurrent.broadcast[DataPacket]

    // Set up pipeline, either one that sends back the result, or one that just sinks
    val sinkIteratee: Iteratee[DataPacket, Unit] = Iteratee.ignore
    senderActor match {
        case Some(ref) => {
            // Set up enumeratee that sends the result back to sender
            val sendBackEnumeratee: Enumeratee[DataPacket, DataPacket] = Enumeratee.map(dp => {
                ref ! dp
                dp
            })
            processors.foreach(processor => enumerator |>> (processor compose sendBackEnumeratee) &>> sinkIteratee)
        }
        case None => processors.foreach(processor => enumerator |>> processor &>> sinkIteratee)
    }

    def cleanup() = {
        // Send message to the monitor actor
        Akka.system.actorSelection("user/TuktuMonitor") ! new AppMonitorPacket(
                self,                
                "done"
        )

        channel.eofAndEnd
        //context.stop(self)
        self ! PoisonPill
    }

    def setup() = {}

    def receive() = {
        case ip: InitPacket => setup
        case config: JsValue => ???
        case sp: StopPacket => cleanup
        case _ => {}
    }
}

abstract class DataMerger() {
    def merge(packets: List[DataPacket]): DataPacket = ???
}

abstract class TuktuGlobal() {
    def onStart(app: Application): Unit = {}
    def onStop(app: Application): Unit = {}
}