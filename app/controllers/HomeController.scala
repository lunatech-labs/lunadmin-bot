package controllers

import javax.inject._
import models._
import play.Logger
import play.api.data.Form
import play.api.data.Forms.{email, mapping, number, optional, text}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration


@Singleton
class HomeController @Inject()(s : Starter) (implicit assetsFinder: AssetsFinder, ec : ExecutionContext)
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

  def displayTasks(page: Int = 0, pageSize: Int = 10) = Action.async { implicit request: Request[AnyContent] =>
    s.taskDataStore.findAllTaskDescription(page, pageSize).map(e =>
      Ok(views.html.displayTasks(e, page, pageSize)))
  }

  def deleteTask(idTask: String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    s.taskDataStore.deleteTask(idTask)
    Redirect(routes.HomeController.displayTasks(page, pageSize))
  }

  def goToAddTask() = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      listUser <- s.userDataStore.findEveryUserDescription()
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (listCategory, listUser, listUserGroup)


    res.map(e => Ok(views.html.taskForm(e._1, e._2, e._3, s.listOfTaskStatus)))
  }

  def paginationDisplayTask() = Action { implicit request: Request[AnyContent] =>
    val pagiForm = Form(
      mapping(
        "page" -> number,
        "pageSize" -> number
      )(paginationForm.apply)(paginationForm.unapply)
    )
    pagiForm.bindFromRequest().fold(
      formWithErrors => {
        Redirect(routes.HomeController.displayTasks(0, 10))
      },
      userData => {
        Redirect(routes.HomeController.displayTasks(userData.page, userData.pageSize))
      }
    )
  }

  def paginationDisplayUser() = Action { implicit request: Request[AnyContent] =>
    val pagiForm = Form(
      mapping(
        "page" -> number,
        "pageSize" -> number
      )(paginationForm.apply)(paginationForm.unapply)
    )
    pagiForm.bindFromRequest().fold(
      formWithErrors => {
        Redirect(routes.HomeController.displayUser(0, 10))
      },
      userData => {
        Redirect(routes.HomeController.displayUser(userData.page, userData.pageSize))
      }
    )
  }

  def displayUser(page: Int = 0, pageSize: Int = 10) = Action.async { implicit request: Request[AnyContent] =>
    s.userDataStore.findAllUserDescription(page, pageSize).map(e =>
      Ok(views.html.displayUsers(e, page, pageSize)))
  }

  def deleteUser(idUser: String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    s.userDataStore.deleteUser(idUser)
    Redirect(routes.HomeController.displayUser(page, pageSize))
  }

  def addTask() = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map{ x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers : List[Int] = List[Int]()
    var listOfAlertSelects : List[String] = List[String]()
    if(alertNumbers.isDefined){
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map{x => x._2.head.toInt}
    }
    if(alertSelects.isDefined){
      listOfAlertSelects = listOfAlertSelects ++ alertSelects.get.map(x => x._2.head)
    }

    val taskForm = Form(
      mapping(
        "description" -> text,
        "startDate" -> of(tools.DateUtils.dateTimeLocal),
        "endDate" -> of(tools.DateUtils.dateTimeLocal),
        "category" -> text,
        "status" -> text,
        "taskChoice" -> text,
        "selectSingleTask" -> text,
        "selectGroupedTask" -> list(nonEmptyText),
        "alertNumbers" -> default(list(number),listOfAlertNumbers),
        "alertSelects" -> default(list(text),listOfAlertSelects)
      )(TaskForm.newFrom)(TaskForm.toTuple)
    )
    taskForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long,String)] = List()
        for(e <- userData.alertNumbers.indices){
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong,userData.alertSelects(e)).toMillis,userData.alertSelects(e))
        }

        if (userData.taskChoice == "single") {
          s.taskDataStore.addSinglePersonTask(SinglePersonTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            employeeId = userData.selectSingleTask,
            category = userData.category,
            alert = finalListOfAlert
          ))
        } else if (userData.taskChoice == "grouped") {
          s.taskDataStore.addGroupedTask(GroupedTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            groupName = userData.selectGroupedTask,
            category = userData.category,
            alert = finalListOfAlert))
        }


        Redirect(routes.HomeController.displayTasks(0, 10)).flashing("success" -> "someSucess")
      }
    )
  }

  def displayFullDetailedTask(idOfTask: String) = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      listUser <- s.userDataStore.findEveryUserDescription()
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
      taskDescription <- s.taskDataStore.findTaskDescriptionByID(idOfTask)
      singleTask <- s.taskDataStore.findSinglePersonTaskById(idOfTask)
      groupedTask <- s.taskDataStore.findGroupedPersonTaskById(idOfTask)
    } yield (listCategory, listUser, listUserGroup, taskDescription, singleTask, groupedTask)

    res.map { e =>
      if (e._4.isDefined && e._4.get.`type` == TaskType.SINGLE) {
        Ok(views.html.singleTaskDetailAndUpdate(e._5.get, e._1, e._2, e._3, s.listOfTaskStatus))
      } else {
        Ok(views.html.groupedTaskDetailAndUpdate(e._6.get, e._1, e._2, e._3, s.listOfTaskStatus))
      }
    }
  }

  def displayFullDetailedUser(idOfUser: String) = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      user <- s.userDataStore.findUserById(idOfUser)
    } yield user

    res.map { e =>
      Ok(views.html.index())
    }
  }

  def updateGroupedTask(idOfTask: String) = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map{ x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers : List[Int] = List[Int]()
    var listOfAlertSelects : List[String] = List[String]()
    if(alertNumbers.isDefined){
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map{x => x._2.head.toInt}
    }
    if(alertSelects.isDefined){
      listOfAlertSelects = listOfAlertSelects ++ alertSelects.get.map(x => x._2.head)
    }

    val updateForm = Form(
      mapping(
        "description" -> text,
        "startDate" -> of(tools.DateUtils.dateTimeLocal),
        "endDate" -> of(tools.DateUtils.dateTimeLocal),
        "category" -> text,
        "status" -> text,
        "selectGroupedTask" -> list(nonEmptyText),
        "alertNumbers" -> default(list(number),listOfAlertNumbers),
        "alertSelects" -> default(list(text),listOfAlertSelects)
      )(GroupedTaskUpdateForm.newFrom)(GroupedTaskUpdateForm.toTuple)
    )

    updateForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long,String)] = List()
        for(e <- userData.alertNumbers.indices){
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong,userData.alertSelects(e)).toMillis,userData.alertSelects(e))
        }

        Logger.info(tools.DateUtils.dateTimeUTC.format(userData.startDate))
        s.taskDataStore.updateGroupedTask(idOfTask, GroupedTask(
          description = userData.description,
          startDate = userData.startDate,
          endDate = userData.endDate,
          status = userData.status,
          groupName = userData.selectGroupedTask,
          category = userData.category,
          alert = finalListOfAlert))

      })
    Redirect(routes.HomeController.displayTasks(0,10)).flashing("update" -> "someUpdate")
  }

  def updateSingleTask(idOfTask: String) = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map{ x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers : List[Int] = List[Int]()
    var listOfAlertSelects : List[String] = List[String]()
    if(alertNumbers.isDefined){
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map{x => x._2.head.toInt}
    }
    if(alertSelects.isDefined){
      listOfAlertSelects = listOfAlertSelects ++ alertSelects.get.map(x => x._2.head)
    }

    val updateForm = Form(
      mapping(
        "description" -> text,
        "startDate" -> of(tools.DateUtils.dateTimeLocal),
        "endDate" -> of(tools.DateUtils.dateTimeLocal),
        "category" -> text,
        "status" -> text,
        "selectSingleTask" -> text,
        "alertNumbers" -> default(list(number),listOfAlertNumbers),
        "alertSelects" -> default(list(text),listOfAlertSelects)
      )(SinglePersonTaskUpdateForm.newFrom)(SinglePersonTaskUpdateForm.toTuple)
    )

    updateForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long,String)] = List()
        for(e <- userData.alertNumbers.indices){
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong,userData.alertSelects(e)).toMillis,userData.alertSelects(e))
        }

        Logger.info(tools.DateUtils.dateTimeUTC.format(userData.startDate))
        s.taskDataStore.updateSinglePersonTask(idOfTask, SinglePersonTask(
          description = userData.description,
          startDate = userData.startDate,
          endDate = userData.endDate,
          status = userData.status,
          employeeId = userData.selectSingleTask,
          category = userData.category,
          alert = finalListOfAlert))

      })
    Redirect(routes.HomeController.displayTasks(0,10)).flashing("update" -> "someUpdate")
  }

}
