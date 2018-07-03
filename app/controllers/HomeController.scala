package controllers

import javax.inject._
import java.text.SimpleDateFormat
import java.util.Calendar

import akka.http.scaladsl.model.headers.Date
import models.{LoginForm, User, UserForm, UserGroup}
import play.Logger
import play.api.data.Form
import play.api.data.Forms.{email, mapping, number, optional, text}
import play.api.mvc._


@Singleton
class HomeController @Inject()(s : Starter) (implicit assetsFinder: AssetsFinder)
  extends AbstractController(s.controllerComponent) {

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def userSignUp() = Action { implicit request: Request[AnyContent] =>
    val userForm = Form(
      mapping(
        "mail" -> email,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text
      )(UserForm.apply)(UserForm.unapply)
    )
    userForm.bindFromRequest().fold(
      formWithErrors => {
        Ok("bad form !")
      },
      userData => {
        Ok(views.html.index())
      }
    )
  }

  def userLogin() = Action { implicit request: Request[AnyContent] =>
    val loginForm = Form(
      mapping(
        "mail" -> email,
        "password" -> text
      )(LoginForm.apply)(LoginForm.unapply)
    )
    loginForm.bindFromRequest().fold(
      formWithErrors => {
        Ok("bad form !")
      },
      userData => {
        Redirect(routes.HomeController.index()).withSession("mail" -> userData.mail)
      }
    )
  }

  def userLogout() = Action {
    Redirect(routes.HomeController.index()).withNewSession
  }

  def displayTasks() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.displayTasks(s.listOfTask,s.listOfUsers,s.listOfGroup))
  }

  def deleteTask(idTask : Int) = Action { implicit request: Request[AnyContent] =>
    s.listOfTask = s.listOfTask.filter(t => t.getId() != idTask)
    Redirect(routes.HomeController.displayTasks())
  }

  def goToAddTask() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.taskForm(s.listOfUsers,s.listOfGroup))
  }

}
