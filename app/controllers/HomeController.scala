package controllers

import java.nio.file.Paths
import javax.inject._
import models._
import play.Logger
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{email, mapping, number, optional, text}
import play.api.mvc._
import play.api.data.Forms._
import reactivemongo.bson.BSONObjectID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import better.files._
import services.TaskScheduler


@Singleton
class HomeController @Inject()(s: Starter, conf: Configuration, taskScheduler: TaskScheduler)(implicit assetsFinder: AssetsFinder, ec: ExecutionContext)
  extends AbstractController(s.controllerComponent) {

  private val listOfStatus: List[String] = conf.underlying.getStringList("userStatus.default.tags").asScala.toList
  private val listOfPaperName: List[String] = conf.underlying.getStringList("papersCategory.default.tags").asScala.toList
  private val listOfTimeZone: List[String] = conf.underlying.getStringList("timeZone.default.tags").asScala.toList
  private val localStorageDirectory = "userDataStorage/"
  private val localAssetDirectory = "public/"
  private val pictureExtensionAccepted = Seq(Some("image/jpeg"), Some("image/png"))
  private val administrativPapersExtensionAccepted = Seq(Some("image/jpeg"), Some("image/png"), Some("application/pdf"), Some("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))

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
          Redirect(routes.HomeController.index()).flashing("registerSuccess" -> "someSuccess")
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

    res.map { user =>
      if (user.isDefined) {
        Ok(views.html.viewUserProfile(user.get)).flashing(request.flash)
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }
  }

  def goToEditUserProfile() = Action.async { implicit request: Request[AnyContent] =>
    val idOfUser = request.session.get("id").getOrElse("none")
    val timeZone = request.session.get("timeZone").getOrElse("Europe/Paris")

    val res = for {
      user <- s.userDataStore.findUserById(idOfUser)
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (user, listUserGroup)

    res.map { e =>
      if (e._1.isDefined) {
        Ok(views.html.editUserProfile(e._1.get, e._2, listOfStatus, listOfPaperName, timeZone, listOfTimeZone)).flashing(request.flash)
      } else {
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
      listOfTaskCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      allTaskForUser <- {
        val groupName = s.userDataStore.findUserById(idOfUser).map(e => e.flatMap(u => u.groupName))
        groupName.flatMap(e => s.taskDataStore.findTaskOfAUser(idOfUser, e.getOrElse(List()), page, pageSize))
      }
      numberOfPageForUser <- {
        val groupName = s.userDataStore.findUserById(idOfUser).map(e => e.flatMap(u => u.groupName))
        groupName.flatMap(e => s.taskDataStore.findNumberOfPageOfAUser(idOfUser, e.getOrElse(List()), pageSize))
      }
    } yield (allTaskForAdmin, numberOfPageForAdmin, allTaskForUser, numberOfPageForUser, listOfTaskCategory)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.displayTasks(e._1, e._5, timeZone, page, pageSize, e._2))
        } else {
          Ok(views.html.displayTasks(e._3, e._5, timeZone, page, pageSize, e._4))
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def deleteTask(idTask: String, taskType: String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        s.taskDataStore.setTaskToInactive(idTask)
        taskScheduler.deleteTask(idTask)
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

  private def orderListOfCategory(listOfTaskCategory: List[TaskCategory]): List[(String, List[TaskCategory])] = {
    var res: List[(String, List[TaskCategory])] = List()
    val listOfHeader = listOfTaskCategory.filter(p => p.isHeader)
    listOfHeader.foreach { f =>
      res = res :+ (f.name, listOfTaskCategory.filter(p => p.idOfCategoryParent.isDefined && p.idOfCategoryParent.get == f._id))
    }
    val neutralTaskCategory = listOfTaskCategory.filter(p => p.idOfCategoryParent.isEmpty && !p.isHeader)
    res = res :+ ("", neutralTaskCategory)

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
    request.session.get("status").map { status =>
      if (status == "Admin") {
        if (idUser == request.session.get("id").getOrElse("none")) {
          s.userDataStore.deleteUser(idUser)
          Redirect(routes.HomeController.userLogout())
        } else {
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
          finalListOfAlert = finalListOfAlert :+ (createDurationFromAlert(userData.alertNumbers(e).toLong, userData.alertSelects(e)), userData.alertSelects(e))
        }

        if (userData.taskChoice == "single") {
          val task: SinglePersonTask = SinglePersonTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            employeeId = userData.selectSingleTask,
            category = userData.category,
            alert = finalListOfAlert
          )

          s.taskDataStore.addSinglePersonTask(task)
          taskScheduler.setSlackBotMessageForSingleTask(task)

        } else if (userData.taskChoice == "grouped") {
          val task: GroupedTask = GroupedTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            groupName = userData.selectGroupedTask,
            category = userData.category,
            alert = finalListOfAlert)

          s.taskDataStore.addGroupedTask(task)
          taskScheduler.setSlackBotMessageForGroupedTask(task)
        }
        Redirect(routes.HomeController.displayTasks(0, 10)).flashing("success" -> "someSucess")
      }
    )
  }

  def addUser() = Action.async { implicit request: Request[AnyContent] =>

    val userForm = Form(
      mapping(
        "mail" -> email,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text,
        "birthDate" -> localDate,
        "groupName" -> optional(list(nonEmptyText)),
        "status" -> optional(text),
        "hireDate" -> localDate,
        "phone" -> optional(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering()

    existingMails.flatMap { list =>

      userForm.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(
            Redirect(routes.HomeController.goToAddUser()).flashing("failure" -> "someFailure")
          )
        },
        userData => {
          if (list.contains(userData.mail)) {
            Future.successful(
              Redirect(routes.HomeController.goToAddUser()).flashing("mailAlreadyExist" -> "mailAlreadyExist")
            )
          } else {
            // creation of id explicitly to be able to create a personal unique folder
            val id: String = BSONObjectID.generate().stringify

            val res = request.body.asMultipartFormData.map { body =>
              val picture = body.file("picture").filter(p => p.filename.nonEmpty)
              val papers = body.files.filter(p => p.key.startsWith("paper") && p.filename.nonEmpty)

              if (picture.forall(p => pictureExtensionAccepted.contains(p.contentType))
                && papers.forall(p => administrativPapersExtensionAccepted.contains(p.contentType))) {

                var picturePath: String = ""
                picture.map { picture =>
                  s"$localAssetDirectory$localStorageDirectory/$id".toFile.createIfNotExists(asDirectory = true, createParents = true)
                  val filename = Paths.get(picture.filename).getFileName
                  picturePath = picturePath + s"$localStorageDirectory$id/$filename"
                  picture.ref.moveTo(Paths.get(localAssetDirectory + picturePath), replace = true)
                }

                var listOfPapers: List[(String, String)] = List()
                papers.map { paper =>
                  s"$localAssetDirectory$localStorageDirectory/$id".toFile.createIfNotExists(asDirectory = true, createParents = true)
                  val filePath: String = s"$localStorageDirectory$id/${paper.filename}"
                  listOfPapers = listOfPapers :+ (paper.key.drop(5), filePath)
                  paper.ref.moveTo(Paths.get(localAssetDirectory + filePath), replace = true)
                }

                s.userDataStore.addUser(User(userData.mail,
                  userData.password,
                  userData.firstName,
                  userData.lastName,
                  _id = id,
                  birthDate = Some(userData.birthDate),
                  groupName = userData.groupName,
                  status = userData.status,
                  hireDate = Some(userData.hireDate),
                  picture = Some(picturePath),
                  phone = userData.phone,
                  cloudPaths = Some(listOfPapers),
                  timeZone = userData.timeZone))

                Redirect(routes.HomeController.displayUser(0, 10)).flashing("success" -> "someSucess")
              } else {
                Redirect(routes.HomeController.goToAddUser()).flashing("badFileFormat" -> "badFileFormat")
              }
            }

            val futureRes: Future[Result] = res.fold({
              s.userDataStore.addUser(User(userData.mail,
                userData.password,
                userData.firstName,
                userData.lastName,
                _id = id,
                birthDate = Some(userData.birthDate),
                groupName = userData.groupName,
                status = userData.status,
                hireDate = Some(userData.hireDate),
                picture = None,
                phone = userData.phone,
                cloudPaths = None,
                timeZone = userData.timeZone))

              Future.successful(
                Redirect(routes.HomeController.displayUser(0, 10)).flashing("success" -> "someSucess")
              )

            })(x => Future.successful(x))
            futureRes
          }
        }
      )
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
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        request.session.get("id").map { id =>
          var finalListOfAlert: List[(Long, String)] = List()
          for (e <- userData.alertNumbers.indices) {
            finalListOfAlert = finalListOfAlert :+ (createDurationFromAlert(userData.alertNumbers(e).toLong, userData.alertSelects(e)), userData.alertSelects(e))
          }

          val task: GroupedTask = GroupedTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            groupName = userData.selectGroupedTask,
            category = userData.category,
            alert = finalListOfAlert,
            isActive = userData.isActive)

          taskScheduler.updateGroupedTask(idOfTask, task)
          s.taskDataStore.updateGroupedTask(idOfTask, task)

          Redirect(routes.HomeController.displayTasks(0, 10)).flashing("update" -> "someUpdate")
        }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "you can't access this page"))
      })
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
        Redirect(routes.HomeController.goToAddTask()).flashing("failure" -> "someFailure")
      },
      userData => {
        request.session.get("id").map { id =>
          var finalListOfAlert: List[(Long, String)] = List()
          for (e <- userData.alertNumbers.indices) {
            finalListOfAlert = finalListOfAlert :+ (createDurationFromAlert(userData.alertNumbers(e).toLong, userData.alertSelects(e)), userData.alertSelects(e))
          }

          val task: SinglePersonTask = SinglePersonTask(
            description = userData.description,
            startDate = userData.startDate,
            endDate = userData.endDate,
            status = userData.status,
            employeeId = userData.selectSingleTask,
            category = userData.category,
            alert = finalListOfAlert,
            isActive = userData.isActive)

          taskScheduler.updateSingleTask(idOfTask, task)
          s.taskDataStore.updateSinglePersonTask(idOfTask, task)

          Redirect(routes.HomeController.displayTasks(0, 10)).flashing("update" -> "someUpdate")
        }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "you can't access this page"))
      })
  }

  def updateUser(idOfUser: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val userForm = Form(
      mapping(
        "mail" -> text,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text,
        "birthDate" -> localDate,
        "groupName" -> optional(list(nonEmptyText)),
        "status" -> optional(text),
        "hireDate" -> localDate,
        "phone" -> optional(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering(Some(idOfUser))

    existingMails.flatMap { list =>

      userForm.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(
            Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("failure" -> "someFailure")
          )
        },
        userData => {
          if (list.contains(userData.mail)) {
            Future.successful(
              Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("mailAlreadyExist" -> "mailAlreadyExist")
            )
          } else {
            request.session.get("id").map { id =>
              val res = request.body.asMultipartFormData.map { body =>
                val picture = body.file("picture").filter(p => p.filename.nonEmpty)
                val papers = body.files.filter(p => p.key.startsWith("paper") && p.filename.nonEmpty)

                if (picture.forall(p => pictureExtensionAccepted.contains(p.contentType))
                  && papers.forall(p => administrativPapersExtensionAccepted.contains(p.contentType))) {

                  var picturePath: Option[String] = None
                  var finalListOfPapers: List[(String, String)] = List()

                  s.userDataStore.findUserById(idOfUser).map { optUser =>
                    optUser.foreach { user =>

                      picturePath = user.picture
                      picture.map { picture =>
                        s"$localAssetDirectory$localStorageDirectory/$idOfUser".toFile.createIfNotExists(asDirectory = true, createParents = true)
                        val filename = Paths.get(picture.filename).getFileName
                        picturePath = Some(s"$localStorageDirectory$idOfUser/$filename")
                        picture.ref.moveTo(Paths.get(localAssetDirectory + picturePath.get), replace = true)
                      }

                      var listOfPapers: List[(String, String)] = List()
                      papers.map { paper =>
                        s"$localAssetDirectory$localStorageDirectory/$idOfUser".toFile.createIfNotExists(asDirectory = true, createParents = true)
                        val filePath: String = s"$localStorageDirectory/$idOfUser/${paper.filename}"
                        listOfPapers = listOfPapers :+ (paper.key.drop(5), filePath)
                        paper.ref.moveTo(Paths.get(localAssetDirectory + filePath), replace = true)
                      }

                      user.cloudPaths.foreach { list =>
                        for (paper <- listOfPaperName) {
                          listOfPapers.find(p => p._1 == paper).map { e =>
                            finalListOfPapers = finalListOfPapers :+ e
                            e
                          }.getOrElse {
                            list.find(p => p._1 == paper).map { e =>
                              finalListOfPapers = finalListOfPapers :+ e
                              e
                            }
                          }
                        }
                      }
                    }
                    s.userDataStore.updateUser(idOfUser, User(userData.mail,
                      userData.password,
                      userData.firstName,
                      userData.lastName,
                      birthDate = Some(userData.birthDate),
                      groupName = userData.groupName,
                      status = userData.status,
                      hireDate = Some(userData.hireDate),
                      picture = picturePath,
                      phone = userData.phone,
                      cloudPaths = Some(finalListOfPapers),
                      isActive = userData.isActive,
                      timeZone = userData.timeZone))

                    if (id == idOfUser) {
                      if (picturePath.isDefined) {
                        Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "picture" -> picturePath.get, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
                      } else {
                        Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
                      }
                    } else {
                      Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate")
                    }
                  }
                } else {
                  Future.successful(
                    Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("badFileFormat" -> "badFileFormat")
                  )
                }
              }

              val futureRes: Future[Result] = res.fold({
                s.userDataStore.findUserById(idOfUser).map { optUser =>
                  optUser.map { user =>

                    s.userDataStore.updateUser(idOfUser, User(userData.mail,
                      userData.password,
                      userData.firstName,
                      userData.lastName,
                      birthDate = Some(userData.birthDate),
                      groupName = userData.groupName,
                      status = userData.status,
                      hireDate = Some(userData.hireDate),
                      picture = user.picture,
                      phone = userData.phone,
                      cloudPaths = user.cloudPaths,
                      isActive = userData.isActive,
                      timeZone = userData.timeZone))

                    if (id == idOfUser) {
                      if (user.picture.isDefined) {
                        Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "picture" -> user.picture.get, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
                      } else {
                        Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> idOfUser)
                      }
                    } else {
                      Redirect(routes.HomeController.displayUser(0, 10)).flashing("update" -> "someUpdate")
                    }
                  }.getOrElse(Redirect(routes.HomeController.displayUser(0, 10)).flashing("userNotFound" -> "someUpdate"))
                }
              })(x => x)

              futureRes
            }.getOrElse(
              Future.successful(
                Redirect(routes.HomeController.index()).flashing("notAdmin" -> "you can't access this page")
              )
            )
          }
        }
      )
    }
  }

  def updateProfile(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val userForm = Form(
      mapping(
        "mail" -> text,
        "password" -> text,
        "firstName" -> text,
        "lastName" -> text,
        "birthDate" -> localDate,
        "groupName" -> optional(list(nonEmptyText)),
        "status" -> optional(text),
        "hireDate" -> localDate,
        "phone" -> optional(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    val existingMails = s.userDataStore.findEveryExistingMailToCheckForRegistering(request.session.get("id"))

    existingMails.flatMap { list =>
      userForm.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(
            Redirect(routes.HomeController.goToEditUserProfile()).flashing("failure" -> "someFailure")
          )
        },
        userData => {
          if (list.contains(userData.mail)) {
            Future.successful(
              Redirect(routes.HomeController.goToEditUserProfile()).flashing("wrongMail" -> "mailAlreadyExist")
            )
          } else {
            request.session.get("id").map { id =>
              val res = request.body.asMultipartFormData.map { body =>
                val picture = body.file("picture").filter(p => p.filename.nonEmpty)
                val papers = body.files.filter(p => p.key.startsWith("paper") && p.filename.nonEmpty)

                if (picture.forall(p => pictureExtensionAccepted.contains(p.contentType))
                  && papers.forall(p => administrativPapersExtensionAccepted.contains(p.contentType))) {

                  var picturePath: Option[String] = None
                  var finalListOfPapers: List[(String, String)] = List()

                  s.userDataStore.findUserById(id).map { optUser =>
                    optUser.foreach { user =>

                      picturePath = user.picture
                      picture.map { picture =>
                        s"$localAssetDirectory$localStorageDirectory/$id".toFile.createIfNotExists(asDirectory = true, createParents = true)
                        val filename = Paths.get(picture.filename).getFileName
                        picturePath = Some(s"$localStorageDirectory$id/$filename")
                        picture.ref.moveTo(Paths.get(localAssetDirectory + picturePath.get), replace = true)
                      }

                      var listOfPapers: List[(String, String)] = List()
                      papers.map { paper =>
                        s"$localStorageDirectory/$id".toFile.createIfNotExists(asDirectory = true, createParents = true)
                        val filePath: String = s"$localStorageDirectory/$id/${paper.filename}"
                        listOfPapers = listOfPapers :+ (paper.key.drop(5), filePath)
                        paper.ref.moveTo(Paths.get(localAssetDirectory + filePath), replace = true)
                      }

                      user.cloudPaths.foreach { list =>
                        for (paper <- listOfPaperName) {
                          listOfPapers.find(p => p._1 == paper).map { e =>
                            finalListOfPapers = finalListOfPapers :+ e
                            e
                          }.getOrElse {
                            list.find(p => p._1 == paper).map { e =>
                              finalListOfPapers = finalListOfPapers :+ e
                              e
                            }
                          }
                        }
                      }
                    }
                    s.userDataStore.updateUser(id, User(userData.mail,
                      userData.password,
                      userData.firstName,
                      userData.lastName,
                      birthDate = Some(userData.birthDate),
                      groupName = userData.groupName,
                      status = userData.status,
                      hireDate = Some(userData.hireDate),
                      picture = picturePath,
                      phone = userData.phone,
                      cloudPaths = Some(finalListOfPapers),
                      isActive = userData.isActive,
                      timeZone = userData.timeZone))

                    if (picturePath.isDefined) {
                      Redirect(routes.HomeController.goToUserProfile()).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "picture" -> picturePath.get, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> id)
                    } else {
                      Redirect(routes.HomeController.goToUserProfile()).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> id)
                    }
                  }
                } else {
                  Future.successful(
                    Redirect(routes.HomeController.goToUserProfile()).flashing("badFileFormat" -> "badFileFormat")
                  )
                }
              }

              val futurRes: Future[Result] = res.fold({
                s.userDataStore.findUserById(id).map { optUser =>
                  optUser.map { user =>
                    s.userDataStore.updateUser(id, User(userData.mail,
                      userData.password,
                      userData.firstName,
                      userData.lastName,
                      birthDate = Some(userData.birthDate),
                      groupName = userData.groupName,
                      status = userData.status,
                      hireDate = Some(userData.hireDate),
                      picture = user.picture,
                      phone = userData.phone,
                      cloudPaths = user.cloudPaths,
                      isActive = userData.isActive,
                      timeZone = userData.timeZone))

                    if (user.picture.isDefined) {
                      Redirect(routes.HomeController.goToUserProfile()).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "picture" -> user.picture.get, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> id)
                    } else {
                      Redirect(routes.HomeController.goToUserProfile()).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName, "lastName" -> userData.lastName, "timeZone" -> userData.timeZone, "status" -> userData.status.get, "id" -> id)
                    }
                  }.getOrElse {
                    Redirect(routes.HomeController.index).flashing("accountNotAvailable" -> "").withNewSession
                  }
                }
              })(x => x)

              futurRes
            }.getOrElse {
              Future.successful(
                Redirect(routes.HomeController.index()).flashing("notAdmin" -> "you can't access this page")
              )
            }
          }
        }
      )
    }
  }

  def goToUserGroups(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listOfUserGroup <- s.userGroupDataStore.findEveryUserGroup()
      listOfUsers <- s.userDataStore.findEveryActiveUser()
    } yield (listOfUserGroup, listOfUsers)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.userGroupsManagment(e._1, e._2))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def goToUserGroupDetail(userGroupName: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listOfUserAlreadyInGroup <- s.userDataStore.findUsersByUserGroup(userGroupName)
      listOfUsers <- s.userDataStore.findEveryActiveUser()
    } yield (listOfUserAlreadyInGroup, listOfUsers)

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.userGroupDetailAndManagment(userGroupName, e._1, e._2))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def deleteUserGroup(name: String) = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        s.removeUserGroup(name)
        Redirect(routes.HomeController.goToUserGroups()).flashing("successDelete" -> "")
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def updateUserGroup() = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val userForm = Form(
          mapping(
            "updateId" -> text,
            "updateName" -> nonEmptyText
          )(UserGroup.apply)(UserGroup.unapply)
        )

        userForm.bindFromRequest().fold(
          formWithErrors => {
            Redirect(routes.HomeController.goToUserGroups()).flashing("failure" -> "someFailure")
          },
          userData => {
            s.updateUserGroup(userData._id, userData.name)
            Redirect(routes.HomeController.goToUserGroups()).flashing("successUpdate" -> "")
          }
        )
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def addUserGroup() = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val userForm = Form(
          mapping(
            "addName" -> nonEmptyText
          )(UserGroupAddForm.apply)(UserGroupAddForm.unapply)
        )

        userForm.bindFromRequest().fold(
          formWithErrors => {
            Redirect(routes.HomeController.goToUserGroups()).flashing("failure" -> "someFailure")
          },
          userData => {
            s.userGroupDataStore.insertUserGroup(
              UserGroup(name = userData.name)
            )
            Redirect(routes.HomeController.goToUserGroups()).flashing("successAdd" -> "")
          }
        )
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def goToTaskCategory(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listOfTaskCategory <- s.taskCategoryDataStore.findAllTaskCategory()
    } yield listOfTaskCategory

    res.map { e =>
      request.session.get("status").map { status =>
        if (status == "Admin") {
          Ok(views.html.taskCategoryManagment(orderListOfCategory(e)))
        } else {
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
  }

  def deleteTaskCategory(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val taskForm = Form(
          mapping(
            "deleteName" -> text
          )(TaskCategoryDeleteForm.apply)(TaskCategoryDeleteForm.unapply)
        )

        taskForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.HomeController.goToTaskCategory()).flashing("failure" -> "someFailure")
            )
          },
          userData => {
            s.taskCategoryDataStore.findTaskCategoryByName(userData.name).flatMap { tC =>
              if (!tC.isHeader) {
                s.removeTaskCategory(tC._id)
                Future.successful(
                  Redirect(routes.HomeController.goToTaskCategory()).flashing("successDelete" -> "")
                )
              } else {
                s.taskCategoryDataStore.findHeaderTaskChildren(tC._id).flatMap { cList =>
                  cList.foreach { e =>
                    s.removeTaskCategory(e._id)
                  }
                  s.removeTaskCategory(tC._id)
                  Future.successful(
                    Redirect(routes.HomeController.goToTaskCategory()).flashing("successDelete" -> "")
                  )
                }
              }
            }
          })

      } else {
        Future.successful(
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        )
      }
    }.getOrElse(
      Future.successful(
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    )
  }

  def addTaskCategory() = Action { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val taskForm = Form(
          mapping(
            "addName" -> nonEmptyText,
            "addIsHeader" -> boolean,
            "addIdOfParent" -> optional(text)
          )(TaskCategoryAddForm.apply)(TaskCategoryAddForm.unapply)
        )

        taskForm.bindFromRequest().fold(
          formWithErrors => {
            Redirect(routes.HomeController.goToTaskCategory()).flashing("failure" -> "someFailure")
          },
          userData => {
            userData.idOfParent.map { name =>
              if (name != "none") {
                s.taskCategoryDataStore.findTaskCategoryByName(name).map { tC =>
                  s.taskCategoryDataStore.insertTaskCategory(
                    TaskCategory(name = userData.name, isHeader = userData.isHeader, idOfCategoryParent = Some(tC._id))
                  )
                }
              } else {
                s.taskCategoryDataStore.insertTaskCategory(
                  TaskCategory(name = userData.name, isHeader = userData.isHeader)
                )
                name
              }
            }.getOrElse {
              s.taskCategoryDataStore.insertTaskCategory(
                TaskCategory(name = userData.name, isHeader = userData.isHeader)
              )
            }
            Redirect(routes.HomeController.goToTaskCategory()).flashing("successAdd" -> "")
          }
        )
      } else {
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      }
    }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
  }

  def updateTaskCategoryName(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val taskForm = Form(
          mapping(
            "updateSelectName" -> text,
            "updateName" -> nonEmptyText
          )(TaskCategoryUpdateNameForm.apply)(TaskCategoryUpdateNameForm.unapply)
        )

        taskForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.HomeController.goToTaskCategory()).flashing("failure" -> "someFailure")
            )
          },
          userData => {
            s.taskCategoryDataStore.findTaskCategoryByName(userData.oldName).map { tC =>
              s.taskCategoryDataStore.updateTaskCategory(tC._id,
                TaskCategory(name = userData.newName, idOfCategoryParent = tC.idOfCategoryParent, isHeader = tC.isHeader)
              )
              Redirect(routes.HomeController.goToTaskCategory()).flashing("successUpdate" -> "")
            }
          }
        )
      } else {
        Future.successful(
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        )
      }
    }.getOrElse(
      Future.successful(
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      )
    )
  }

  def updateTaskCategoryLink(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val taskForm = Form(
          mapping(
            "updateSelectLink" -> text,
            "updateLink" -> optional(nonEmptyText)
          )(TaskCategoryUpdateLinkForm.apply)(TaskCategoryUpdateLinkForm.unapply)
        )

        taskForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.HomeController.goToTaskCategory()).flashing("failure" -> "someFailure")
            )
          },
          userData => {
            s.taskCategoryDataStore.findTaskCategoryByName(userData.name).map { tC =>
              s.taskCategoryDataStore.updateTaskCategory(tC._id,
                TaskCategory(name = tC.name, idOfCategoryParent = tC.idOfCategoryParent, isHeader = tC.isHeader, link = userData.newLink)
              )
              Redirect(routes.HomeController.goToTaskCategory()).flashing("successUpdate" -> "")
            }
          }
        )
      } else {
        Future.successful(
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        )
      }
    }.getOrElse(
      Future.successful(
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      )
    )
  }

  def addUserToUserGroup(userGroupName: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val userForm = Form(
          mapping(
            "addId" -> text
          )(UserAddAndRemoveUserGroupForm.apply)(UserAddAndRemoveUserGroupForm.unapply)
        )

        userForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.HomeController.goToUserGroups()).flashing("failure" -> "someFailure")
            )
          },
          userData => {
            s.userDataStore.findUserById(userData.id).map { optUser =>
              optUser.map { user =>
                user.groupName.map { l =>
                  if (!l.contains(userGroupName)) {
                    s.userDataStore.addUserToUserGroup(user, userGroupName)
                  }
                  Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("successAdd" -> "")
                }.getOrElse {
                  s.userDataStore.addUserToUserGroup(user, userGroupName)
                  Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("successAdd" -> "")
                }
              }.getOrElse {
                Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("userNotFound" -> "")
              }
            }
          }
        )
      } else {
        Future.successful(
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        )
      }
    }.getOrElse(
      Future.successful(
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      )
    )
  }

  def removeUserFromUserGroup(userGroupName: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    request.session.get("status").map { status =>
      if (status == "Admin") {
        val userForm = Form(
          mapping(
            "deleteId" -> text
          )(UserAddAndRemoveUserGroupForm.apply)(UserAddAndRemoveUserGroupForm.unapply)
        )

        userForm.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(
              Redirect(routes.HomeController.goToUserGroups()).flashing("failure" -> "someFailure")
            )
          },
          userData => {
            s.userDataStore.findUserById(userData.id).map { optUser =>
              optUser.map { user =>
                user.groupName.map { l =>
                  if (l.contains(userGroupName)) {
                    s.userDataStore.removeUserFromUserGroup(user._id, userGroupName)
                  }
                  Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("successdelete" -> "")
                }.getOrElse {
                  Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("successdelete" -> "")
                }
              }.getOrElse {
                Redirect(routes.HomeController.goToUserGroupDetail(userGroupName)).flashing("userNotFound" -> "")
              }
            }
          }
        )
      } else {
        Future.successful(
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        )
      }
    }.getOrElse(
      Future.successful(
        Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
      )
    )
  }

  private def createDurationFromAlert(alertNumber: Long, alertChoice: String): Long = {
    alertChoice match {
      case "minute" => Duration(alertNumber, alertChoice).toMillis
      case "hour" => Duration(alertNumber, alertChoice).toMillis
      case "day" => Duration(alertNumber, alertChoice).toMillis
      case "week" => Duration(alertNumber * 7, "day").toMillis
      case "month" => Duration(alertNumber * 30, "day").toMillis
      case "year" => Duration(alertNumber * 365, "day").toMillis
    }
  }
}
