package controllers

import javax.inject._
import java.text.SimpleDateFormat
import java.util.Calendar

import play.Logger
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) (implicit assetsFinder: AssetsFinder)
  extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    val today = Calendar.getInstance.getTime

    // create the date/time formatters
    val minuteFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")

    val currentMinute = minuteFormat.format(today)
    Logger.info(s"-- $currentMinute --")
    Ok(views.html.index("Your new application is ready."))
  }

}
