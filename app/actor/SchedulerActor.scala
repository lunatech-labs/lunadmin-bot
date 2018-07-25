package actor

import java.time.ZonedDateTime

import akka.actor._
import controllers.Starter
import javax.inject._

class SchedulerActor @Inject()(s : Starter) extends Actor{
      def receive = {
        case (taskId: String, taskStatus: String, timeLeftUntilStartDate: Long, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String) =>
      }
}

object SchedulerActor {
  def props = Props[SchedulerActor]

}
