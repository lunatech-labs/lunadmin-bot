package controllers

import java.util.{Calendar, Locale, TimeZone}

import javax.inject._
import models._
import play.Logger
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{email, mapping, number, optional, text}
import play.api.mvc._
import play.api.data.Forms._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration


@Singleton
class HomeController @Inject()(s : Starter,conf : Configuration) (implicit assetsFinder: AssetsFinder, ec : ExecutionContext)
  extends AbstractController(s.controllerComponent) {

  val listOfStatus: List[String] = conf.underlying.getStringList("userStatus.default.tags").asScala.toList
  val listOfPaperName: List[String] = conf.underlying.getStringList("papersCategory.default.tags").asScala.toList
  val listOfTimeZone: List[String] = conf.underlying.getStringList("timeZone.default.tags").asScala.toList

  def index = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def userSignUp() = Action.async { implicit request: Request[AnyContent] =>
    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering()

    existingMails.map { list =>

      val userForm = Form(
        mapping(
          "mail" -> email,
          "password" -> text,
          "firstName" -> text,
          "lastName" -> text
        )(UserSignUpForm.apply)(UserSignUpForm.unapply)
      )
      userForm.bindFromRequest().fold(
        formWithErrors => {
          Ok(views.html.index()).flashing("registerSuccess" -> "someSuccess")
        },
        userData => {
          val user = User(mail = userData.mail, password = userData.password, firstName = userData.firstName, lastName = userData.lastName)
          if (list.contains(user.mail)) {
            Redirect(routes.HomeController.index()).flashing("wrongMail" -> "mail already exist")
          } else {
            s.userDataStore.addUser(user)
            Redirect(routes.HomeController.goToUserProfile()).withSession("firstName" -> user.firstName, "lastName" -> user.lastName, "timeZone" -> user.timeZone, "status" -> user.status.get, "id" -> user._id)
          }
        }
      )
    }
  }

  def goToUserProfile() = Action.async { implicit request: Request[AnyContent] =>
    val idOfUser = request.session.get("id").getOrElse("none")
    val res = s.userDataStore.findUserById(idOfUser)

    res.map{ user =>
      if(user.isDefined){
        Ok(views.html.viewUserProfil(user.get))
      }else{
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }
  }

  def userLogin() = Action.async { implicit request: Request[AnyContent] =>
    var mail = ""
    var password = ""

    val loginForm = Form(
      mapping(
        "mail" -> email,
        "password" -> text
      )(LoginForm.apply)(LoginForm.unapply)
    )
    loginForm.bindFromRequest().fold(
      formWithErrors => {
        Redirect(routes.HomeController.index()).flashing("badForm" -> "badForm")
      },
      userData => {
        mail = mail + userData.mail
        password = password + userData.password
      }
    )
    s.userDataStore.findUserByMail(mail).map { u =>
      if (u.isDefined) {
        if (u.get.password == password) {
          if (u.get.picture.isDefined) {
            Redirect(routes.HomeController.index()).withSession("firstName" -> u.get.firstName, "lastName" -> u.get.lastName, "picture" -> u.get.picture.get, "timeZone" -> u.get.timeZone, "status" -> u.get.status.get, "id" -> u.get._id)
          } else {
            Redirect(routes.HomeController.index()).withSession("firstName" -> u.get.firstName, "lastName" -> u.get.lastName, "timeZone" -> u.get.timeZone, "status" -> u.get.status.get, "id" -> u.get._id)
          }
        } else {
          Redirect(routes.HomeController.index()).flashing("wrongPassword" -> "wrongPassword")
        }
      } else {
        Redirect(routes.HomeController.index()).flashing("notFound" -> "notFound")
      }
    }
  }

  def userLogout() = Action {
    Redirect(routes.HomeController.index()).withNewSession
  }


  def displayTasks(page: Int = 0, pageSize: Int = 10) = Action.async { implicit request: Request[AnyContent] =>
    val idOfUser = request.session.get("id").getOrElse("none")
    val timeZone = request.session.get("timeZone").getOrElse("Europe/Paris")

    val res = for {
      allTaskForAdmin <- s.taskDataStore.findAllTaskDescription(page, pageSize)
      numberOfPageForAdmin <- s.taskDataStore.findNumberOfPage(pageSize)
      allTaskForUser <- {
        val groupName = s.userDataStore.findUserById(idOfUser).map(e => e.flatMap(u => u.groupName))
        groupName.flatMap(e => s.taskDataStore.findTaskOfAUser(idOfUser, e.getOrElse(List()), page, pageSize))
      }
      numberOfPageForUser <- {
        val groupName = s.userDataStore.findUserById(idOfUser).map(e => e.flatMap(u => u.groupName))
        groupName.flatMap(e => s.taskDataStore.findNumberOfPageOfAUser(idOfUser, e.getOrElse(List()), pageSize))
      }
    } yield (allTaskForAdmin, numberOfPageForAdmin, allTaskForUser, numberOfPageForUser)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.displayTasks(e._1, timeZone, page, pageSize, e._2))
        } else {
          Ok(views.html.displayTasks(e._3, timeZone, page, pageSize, e._4))
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }


  def deleteTask(idTask: String, taskType: String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map{ status =>
      if (status == "Admin") {
        s.taskDataStore.deleteTask(idTask, taskType)
        Redirect(routes.HomeController.displayTasks(page, pageSize)).flashing("taskDeleted" -> "The Task Has Been deleted !")
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def goToAddTask() = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      listUser <- s.userDataStore.findEveryUserDescription()
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (listCategory, listUser, listUserGroup)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.taskAddForm(orderListOfCategory(e._1), e._2, e._3, s.listOfTaskStatus))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def orderListOfCategory(listOfTaskCategory: List[TaskCategory]): List[(String, List[String])] = {
    var res: List[(String, List[String])] = List()
    val listOfHeader = listOfTaskCategory.filter(p => p.isHeader)
    listOfHeader.foreach { f =>
      res = res :+ (f.name, listOfTaskCategory.filter(p => p.idOfCategoryParent.isDefined && p.idOfCategoryParent.get == f._id).map(e => e.name))
    }
    val neutralTaskCategory = listOfTaskCategory.filter(p => p.idOfCategoryParent.isEmpty && !p.isHeader)
    res = res :+ ("", neutralTaskCategory.map(p => p.name))

    res
  }


  def goToAddUser() = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield listUserGroup

    res.map { e =>
      request.session.get("status").map { s =>
        if (s == "Admin") {
          Ok(views.html.userAddForm(e, listOfStatus, listOfPaperName))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
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
    val res = for {
      listUser <- s.userDataStore.findAllUserDescription(page, pageSize)
      numberOfPage <- s.userDataStore.findNumberOfPage(pageSize)
    } yield (listUser, numberOfPage)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.displayUsers(e._1, page, pageSize, e._2))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def deleteUser(idUser: String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map{ status =>
      if (status == "Admin") {
        if(idUser == request.session.get("id").getOrElse("none")){
          s.userDataStore.deleteUser(idUser)
          Redirect(routes.HomeController.userLogout())
        }else{
          s.userDataStore.deleteUser(idUser)
          Redirect(routes.HomeController.displayUser(page, pageSize)).flashing("userDeleted" -> "The User Has Been Deleted")
        }

      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def addTask() = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers: List[Int] = List[Int]()
    var listOfAlertSelects: List[String] = List[String]()
    if (alertNumbers.isDefined) {
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map { x => x._2.head.toInt }
    }
    if (alertSelects.isDefined) {
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
        "alertNumbers" -> default(list(number), listOfAlertNumbers),
        "alertSelects" -> default(list(text), listOfAlertSelects)
      )(TaskForm.newFrom)(TaskForm.toTuple)
    )

    taskForm.bindFromRequest().fold(
      formWithErrors => {
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long, String)] = List()
        for (e <- userData.alertNumbers.indices) {
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong, userData.alertSelects(e)).toMillis, userData.alertSelects(e))
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

  def addUser() = Action.async { implicit request: Request[AnyContent] =>
    val papers = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("paper"))
    }

    val potentialUrlPicture = request.body.asFormUrlEncoded.map { x =>
      x.find(p => p._1 == "picture")
    }
    var finalUrlPicture: Option[String] = None
    if (potentialUrlPicture.isDefined) {
      finalUrlPicture = potentialUrlPicture.get.map(x => x._2.head)
    }

    var listOfPapers: List[String] = List()
    var listOfPapersWithNames: List[(String, String)] = List()
    if (papers.isDefined) {
      listOfPapers = listOfPapers ++ papers.get.map(e => e._2.head)
      for (index <- listOfPaperName.indices)
        listOfPapersWithNames = listOfPapersWithNames :+ (listOfPaperName(index), listOfPapers(index))
    }

    val userForm = Form(
      mapping(
        "mail" -> text,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text,
        "birthDate" -> optional(localDate),
        "groupName" -> optional(list(nonEmptyText)),
        "status" -> optional(text),
        "hireDate" -> optional(localDate),
        "picture" -> optional(text),
        "phone" -> optional(text),
        "cloudLinks" -> list(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering()

    existingMails.map { list =>

      userForm.bindFromRequest().fold(
        formWithErrors => {
          Logger.info(formWithErrors.toString)
          Logger.info(formWithErrors.errors.toString)
          Redirect(routes.HomeController.goToAddUser()).flashing("failure" -> "someFailure")
        },
        userData => {
          if(list.contains(userData.mail)){
            Redirect(routes.HomeController.goToAddUser()).flashing("mailAlreadyExist" -> "mailAlreadyExist")
          }else{
            s.userDataStore.addUser(User(userData.mail,
              userData.password,
              userData.firstName,
              userData.lastName,
              birthDate = userData.birthDate,
              groupName = userData.groupName,
              status = userData.status,
              hireDate = userData.hireDate,
              picture = finalUrlPicture,
              phone = userData.phone,
              cloudLinks = Some(listOfPapersWithNames),
              timeZone = userData.timeZone))

            Redirect(routes.HomeController.displayUser(0, 10)).flashing("success" -> "someSucess")
          }

        })
    }
  }


  def displayFullDetailedTask(idOfTask: String) = Action.async { implicit request: Request[AnyContent] =>
    val idOfUser = request.session.get("id").getOrElse("none")
    val timeZone = request.session.get("timeZone").getOrElse("Europe/Paris")

    val res = for {
      listCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      listUser <- s.userDataStore.findEveryUserDescription()
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
      taskDescription <- s.taskDataStore.findTaskDescriptionByID(idOfTask)
      singleTask <- s.taskDataStore.findSinglePersonTaskById(idOfTask)
      groupedTask <- s.taskDataStore.findGroupedPersonTaskById(idOfTask)
      groupNameOfUser <- s.userDataStore.findUserById(idOfUser).map(e => e.flatMap(u => u.groupName))
    } yield (listCategory, listUser, listUserGroup, taskDescription, singleTask, groupedTask, groupNameOfUser)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          if (e._4.isDefined && e._4.get.`type` == TaskType.SINGLE) {
            Ok(views.html.singleTaskDetailAndUpdate(e._5.get, e._1, e._2, e._3, s.listOfTaskStatus, timeZone))
          } else {
            Ok(views.html.groupedTaskDetailAndUpdate(e._6.get, e._1, e._2, e._3, s.listOfTaskStatus, timeZone))
          }
        } else {
          // if the task is a single task
          if (e._4.isDefined && e._4.get.`type` == TaskType.SINGLE) {
            // if the user is assigned to this task
            if (s.userDataStore.checkIfSingleTaskIsAssignedToUser(idOfUser, e._5.get)) {
              Ok(views.html.singleTaskDetailAndUpdate(e._5.get, e._1, e._2, e._3, s.listOfTaskStatus, timeZone))
            }
            // if not , it means he is trying to access a task he has no right on
            else {
              Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
            }
          }
          // if the task is a grouped task
          else {
            val bool = e._7.map { list =>
              s.userDataStore.checkIfUserIsPartOfGroupedTask(list, e._6.get)
            }
            if (bool.getOrElse(false)) {
              Ok(views.html.groupedTaskDetailAndUpdate(e._6.get, e._1, e._2, e._3, s.listOfTaskStatus, timeZone))
            } else {
              Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
            }
          }
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }

  }

  def displayFullDetailedUser(idOfUser: String) = Action.async { implicit request: Request[AnyContent] =>
    val timeZone = request.session.get("timeZone").getOrElse("Europe/Paris")

    val res = for {
      user <- s.userDataStore.findUserById(idOfUser)
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (user, listUserGroup)

    res.map { e =>
      if (e._1.isDefined) {
        request.session.get("status").map { status =>
          if (status == "Admin") {
            Ok(views.html.userDetailAndUpdate(e._1.get, e._2, listOfStatus, listOfPaperName, timeZone, listOfTimeZone))
          } else {
            Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
          }
        }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
      } else {
        Redirect(routes.HomeController.displayUser(0, 10)).flashing("userNotFound" -> "somefail")
      }
    }
  }

  def updateGroupedTask(idOfTask: String) = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers: List[Int] = List[Int]()
    var listOfAlertSelects: List[String] = List[String]()
    if (alertNumbers.isDefined) {
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map { x => x._2.head.toInt }
    }
    if (alertSelects.isDefined) {
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
        "alertNumbers" -> default(list(number), listOfAlertNumbers),
        "alertSelects" -> default(list(text), listOfAlertSelects),
        "isActive" -> boolean
      )(GroupedTaskUpdateForm.newFrom)(GroupedTaskUpdateForm.toTuple)
    )

    updateForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long, String)] = List()
        for (e <- userData.alertNumbers.indices) {
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong, userData.alertSelects(e)).toMillis, userData.alertSelects(e))
        }

        s.taskDataStore.updateGroupedTask(idOfTask, GroupedTask(
          description = userData.description,
          startDate = userData.startDate,
          endDate = userData.endDate,
          status = userData.status,
          groupName = userData.selectGroupedTask,
          category = userData.category,
          alert = finalListOfAlert,
          isActive = userData.isActive))

      })
    Redirect(routes.HomeController.displayTasks(0, 10)).flashing("update" -> "someUpdate")
  }

  def updateSingleTask(idOfTask: String) = Action { implicit request: Request[AnyContent] =>
    val alertNumbers = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertNumber"))
    }

    val alertSelects = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("alertSelect"))
    }

    var listOfAlertNumbers: List[Int] = List[Int]()
    var listOfAlertSelects: List[String] = List[String]()
    if (alertNumbers.isDefined) {
      listOfAlertNumbers = listOfAlertNumbers ++ alertNumbers.get.map { x => x._2.head.toInt }
    }
    if (alertSelects.isDefined) {
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
        "alertNumbers" -> default(list(number), listOfAlertNumbers),
        "alertSelects" -> default(list(text), listOfAlertSelects),
        "isActive" -> boolean
      )(SinglePersonTaskUpdateForm.newFrom)(SinglePersonTaskUpdateForm.toTuple)
    )

    updateForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        var finalListOfAlert: List[(Long, String)] = List()
        for (e <- userData.alertNumbers.indices) {
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong, userData.alertSelects(e)).toMillis, userData.alertSelects(e))
        }

        s.taskDataStore.updateSinglePersonTask(idOfTask, SinglePersonTask(
          description = userData.description,
          startDate = userData.startDate,
          endDate = userData.endDate,
          status = userData.status,
          employeeId = userData.selectSingleTask,
          category = userData.category,
          alert = finalListOfAlert,
          isActive = userData.isActive
        )
        )

      })
    Redirect(routes.HomeController.displayTasks(0, 10)).flashing("update" -> "someUpdate")
  }

  def updateUser(idOfUser: String) = Action.async { implicit request: Request[AnyContent] =>
    val papers = request.body.asFormUrlEncoded.map { x =>
      x.filterKeys(k => k.startsWith("paper"))
    }

    var listOfPapers: List[String] = List()
    var listOfPapersWithNames: List[(String, String)] = List()
    if (papers.isDefined) {
      listOfPapers = listOfPapers ++ papers.get.map(e => e._2.head)
      for (index <- listOfPaperName.indices)
        listOfPapersWithNames = listOfPapersWithNames :+ (listOfPaperName(index), listOfPapers(index))
    }

    val potentialUrlPicture = request.body.asFormUrlEncoded.map { x =>
      x.find(p => p._1 == "picture")
    }
    var finalUrlPicture: Option[String] = None
    if (potentialUrlPicture.isDefined) {
      finalUrlPicture = potentialUrlPicture.get.map(x => x._2.head)
    }

    val userForm = Form(
      mapping(
        "mail" -> text,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text,
        "birthDate" -> optional(localDate),
        "groupName" -> optional(list(nonEmptyText)),
        "status" -> optional(text),
        "hireDate" -> optional(localDate),
        "picture" -> optional(text),
        "phone" -> optional(text),
        "cloudLinks" -> list(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering(Some(idOfUser))

    existingMails.map { list =>
      userForm.bindFromRequest().fold(
        formWithErrors => {
          Logger.info(formWithErrors.errors.toString)
          Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("failure" -> "someFailure")
        },
        userData => {
          if (list.contains(userData.mail)) {
            Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("wrongMail" -> "already exist")
          } else {
            s.userDataStore.updateUser(idOfUser, User(userData.mail,
              userData.password,
              userData.firstName,
              userData.lastName,
              birthDate = userData.birthDate,
              groupName = userData.groupName,
              status = userData.status,
              hireDate = userData.hireDate,
              picture = finalUrlPicture,
              phone = userData.phone,
              cloudLinks = Some(listOfPapersWithNames),
              isActive = userData.isActive,
              timeZone = userData.timeZone))

            if (request.session.get("id").get == idOfUser) {
              if (userData.picture.isDefined) {
                Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "picture" -> userData.picture.get, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
              } else {
                Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
              }
            } else {
              Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate")
            }
          }
        })
    }
  }
}
