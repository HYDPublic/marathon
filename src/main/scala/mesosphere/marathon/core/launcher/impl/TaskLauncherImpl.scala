package mesosphere.marathon
package core.launcher.impl

import java.util.Collections

import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.core.launcher.{InstanceOp, TaskLauncher}
import mesosphere.marathon.metrics.{Metrics, ServiceMetric}
import mesosphere.marathon.stream.Implicits._
import org.apache.mesos.Protos.{OfferID, Status}
import org.apache.mesos.{Protos, SchedulerDriver}

private[launcher] class TaskLauncherImpl(
    marathonSchedulerDriverHolder: MarathonSchedulerDriverHolder) extends TaskLauncher with StrictLogging {

  private[this] val usedOffersMeter = Metrics.minMaxCounter(ServiceMetric, getClass, "usedOffers")
  private[this] val launchedTasksMeter = Metrics.minMaxCounter(ServiceMetric, getClass, "launchedTasks")
  private[this] val declinedOffersMeter = Metrics.minMaxCounter(ServiceMetric, getClass, "declinedOffers")

  override def acceptOffer(offerID: OfferID, taskOps: Seq[InstanceOp]): Boolean = {
    val accepted = withDriver(s"launchTasks($offerID)") { driver =>

      //We accept the offer, the rest of the offer is declined automatically with the given filter.
      //The filter duration is set to 0, so we get the same offer in the next allocator cycle.
      val noFilter = Protos.Filters.newBuilder().setRefuseSeconds(0).build()
      val operations = taskOps.flatMap(_.offerOperations)
      logger.debug(s"Operations on $offerID:\n${operations.mkString("\n")}")

      driver.acceptOffers(Collections.singleton(offerID), operations.asJava, noFilter)
    }
    if (accepted) {
      usedOffersMeter.increment()
      val launchCount = taskOps.count {
        case _: InstanceOp.LaunchTask => true
        case _: InstanceOp.LaunchTaskGroup => true
        case _ => false
      }
      launchedTasksMeter.increment(launchCount.toLong)
    }
    accepted
  }

  override def declineOffer(offerID: OfferID, refuseMilliseconds: Option[Long]): Unit = {
    val declined = withDriver(s"declineOffer(${offerID.getValue})") {
      val filters = refuseMilliseconds
        .map(seconds => Protos.Filters.newBuilder().setRefuseSeconds(seconds / 1000.0).build())
        .getOrElse(Protos.Filters.getDefaultInstance)
      _.declineOffer(offerID, filters)
    }
    if (declined) {
      declinedOffersMeter.increment()
    }
  }

  private[this] def withDriver(description: => String)(block: SchedulerDriver => Status): Boolean = {
    marathonSchedulerDriverHolder.driver match {
      case Some(driver) =>
        val status = block(driver)
        logger.debug(s"$description returned status = $status")

        status == Status.DRIVER_RUNNING

      case None =>
        logger.warn(s"Cannot execute '$description', no driver available")
        false
    }
  }
}
