package mesosphere.marathon
package core.task.tracker.impl

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.storage.repository.InstanceRepository
import mesosphere.marathon.stream.Sink
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
  * Loads all task data into an [[InstanceTracker.InstancesBySpec]] from an [[InstanceRepository]].
  */
private[tracker] class InstancesLoaderImpl(repo: InstanceRepository)(implicit val mat: Materializer)
  extends InstancesLoader {
  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] val log = LoggerFactory.getLogger(getClass.getName)

  private val ConcurrentCallLimit = 8

  override def load(): Future[InstanceTracker.InstancesBySpec] = {
    for {
      names <- repo.ids().runWith(Sink.seq)
      _ = log.info(s"About to load ${names.size} tasks")
      instances <- Source(names).mapAsync(ConcurrentCallLimit)(repo.get).mapConcat(_.toList).runWith(Sink.seq)
    } yield {
      log.info(s"Loaded ${instances.size} tasks")
      InstanceTracker.InstancesBySpec.forInstances(instances)
    }
  }
}
