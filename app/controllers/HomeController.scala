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
  extends AbstractController(s.controllerComponent){

  val listOfStatus : List[String] = conf.underlying.getStringList("userStatus.default.tags").asScala.toList
  val listOfPaperName : List[String] = conf.underlying.getStringList("papersCategory.default.tags").asScala.toList


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
      )(UserSignUpForm.apply)(UserSignUpForm.unapply)
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
    s.userDataStore.findUserByMail(mail).map{u =>
      if(u.isDefined){
        if(u.get.password == password){
          if(u.get.picture.isDefined){
          Redirect(routes.HomeController.index()).withSession("firstName" -> u.get.firstName,"lastName" -> u.get.lastName,"picture" -> u.get.picture.get,"timeZone" -> u.get.timeZone,"status" -> u.get.status.get,"id" -> u.get._id)
          }else{
            Redirect(routes.HomeController.index()).withSession("firstName" -> u.get.firstName,"lastName" -> u.get.lastName,"timeZone" -> u.get.timeZone,"status" -> u.get.status.get,"id" -> u.get._id)
          }
        }else{
          Redirect(routes.HomeController.index()).flashing("wrongPassword" -> "wrongPassword")
        }
      }else{
        Redirect(routes.HomeController.index()).flashing("notFound" -> "notFound")
      }
    }
  }

  def userLogout() = Action {
    Redirect(routes.HomeController.index()).withNewSession
  }

  def displayTasks(page: Int = 0, pageSize: Int = 10) = Action.async { implicit request: Request[AnyContent] =>
    s.taskDataStore.findAllTaskDescription(page, pageSize).map(e =>
      Ok(views.html.displayTasks(e,getLocalTimeZoneId(request), page, pageSize)))
  }

  def deleteTask(idTask: String,taskType : String, page: Int, pageSize: Int) = Action { implicit request: Request[AnyContent] =>
    s.taskDataStore.deleteTask(idTask,taskType)
    Redirect(routes.HomeController.displayTasks(page, pageSize))
  }

  def goToAddTask() = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      listCategory <- s.taskCategoryDataStore.findAllTaskCategory()
      listUser <- s.userDataStore.findEveryUserDescription()
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (listCategory, listUser, listUserGroup)


    res.map(e => Ok(views.html.taskAddForm(orderListOfCategory(e._1), e._2, e._3, s.listOfTaskStatus)))
  }

  def orderListOfCategory(listOfTaskCategory : List[TaskCategory]) : List[(String,List[String])] = {
    var res : List[(String,List[String])] = List()
    val listOfHeader = listOfTaskCategory.filter(p => p.isHeader == true)
    listOfHeader.foreach{ f =>
      res = res :+ (f.name,listOfTaskCategory.filter(p => p.idOfCategoryParent.isDefined && p.idOfCategoryParent.get == f._id).map(e => e.name))
    }
    val neutralTaskCategory = listOfTaskCategory.filter(p => p.idOfCategoryParent == None && p.isHeader == false)
    res = res :+ ("",neutralTaskCategory.map(p => p.name))

    res
  }


  def goToAddUser() = Action.async {implicit request: Request[AnyContent] =>
    val res = for {
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield listUserGroup

    res.map{e =>
      request.session.get("status").map { s =>
        if(s == "Admin"){
          Ok(views.html.userAddForm(e, listOfStatus, listOfPaperName))
        }else{
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
    s.userDataStore.findAllUserDescription(page, pageSize).map{e =>
      request.session.get("status").map { s =>
        if(s == "Admin"){
          Ok(views.html.displayUsers(e, page, pageSize))
        }else{
          Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page")
        }
      }.getOrElse(Redirect(routes.HomeController.index()).flashing("notAdmin" -> "You can't access this page"))
    }
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

  def addUser() = Action { implicit request: Request[AnyContent] =>
    val papers = request.body.asFormUrlEncoded.map{ x =>
      x.filterKeys(k => k.startsWith("paper"))
    }

    val potentialUrlPicture = request.body.asFormUrlEncoded.map{ x =>
      x.find(p => p._1 == "picture")
    }
    var finalUrlPicture : Option[String] = None
    if(potentialUrlPicture.isDefined){
      finalUrlPicture = potentialUrlPicture.get.map(x => x._2.head)
    }

    var listOfPapers : List[String] = List()
    var listOfPapersWithNames : List[(String,String)] = List()
    if(papers.isDefined){
      listOfPapers = listOfPapers ++ papers.get.map(e => e._2.head)
      for(index <- listOfPaperName.indices)
      listOfPapersWithNames = listOfPapersWithNames :+ (listOfPaperName(index),listOfPapers(index))
    }

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
        "picture" -> optional(text),
        "phone" -> optional(text),
        "cloudLinks" -> list(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    userForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.toString)
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.goToAddUser()).flashing("failure" -> "someFailure")
      },
      userData => {
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
                                     timeZone = userData.timeZone ))

        Redirect(routes.HomeController.displayUser(0,10)).flashing("success" -> "someSucess")
      })
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
        Ok(views.html.singleTaskDetailAndUpdate(e._5.get, e._1, e._2, e._3, s.listOfTaskStatus, getLocalTimeZoneId(request)))
      } else {
        Ok(views.html.groupedTaskDetailAndUpdate(e._6.get, e._1, e._2, e._3, s.listOfTaskStatus, getLocalTimeZoneId(request)))
      }
    }
  }

  def displayFullDetailedUser(idOfUser: String) = Action.async { implicit request: Request[AnyContent] =>
    val res = for {
      user <- s.userDataStore.findUserById(idOfUser)
      listUserGroup <- s.userGroupDataStore.findEveryUserGroup()
    } yield (user,listUserGroup)

    res.map { e =>
      if(e._1.isDefined){
        Ok(views.html.userDetailAndUpdate(e._1.get,e._2,listOfStatus,listOfPaperName, getLocalTimeZoneId(request)))
      }else{
        Redirect(routes.HomeController.displayUser(0,10)).flashing("userNotFound" -> "somefail")
      }
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
        "alertSelects" -> default(list(text),listOfAlertSelects),
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
        var finalListOfAlert: List[(Long,String)] = List()
        for(e <- userData.alertNumbers.indices){
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong,userData.alertSelects(e)).toMillis,userData.alertSelects(e))
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
        "alertSelects" -> default(list(text),listOfAlertSelects),
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
        var finalListOfAlert: List[(Long,String)] = List()
        for(e <- userData.alertNumbers.indices){
          finalListOfAlert = finalListOfAlert :+ (Duration(userData.alertNumbers(e).toLong,userData.alertSelects(e)).toMillis,userData.alertSelects(e))
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
    Redirect(routes.HomeController.displayTasks(0,10)).flashing("update" -> "someUpdate")
  }

  def updateUser(idOfUser : String) = Action { implicit request: Request[AnyContent] =>
    val papers = request.body.asFormUrlEncoded.map{ x =>
      x.filterKeys(k => k.startsWith("paper"))
    }

    var listOfPapers : List[String] = List()
    var listOfPapersWithNames : List[(String,String)] = List()
    if(papers.isDefined){
      listOfPapers = listOfPapers ++ papers.get.map(e => e._2.head)
      for(index <- listOfPaperName.indices)
        listOfPapersWithNames = listOfPapersWithNames :+ (listOfPaperName(index),listOfPapers(index))
    }

    val potentialUrlPicture = request.body.asFormUrlEncoded.map{ x =>
      x.find(p => p._1 == "picture")
    }
    var finalUrlPicture : Option[String] = None
    if(potentialUrlPicture.isDefined){
      finalUrlPicture = potentialUrlPicture.get.map(x => x._2.head)
    }

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
        "picture" -> optional(text),
        "phone" -> optional(text),
        "cloudLinks" -> list(text),
        "isActive" -> boolean,
        "timeZone" -> text
      )(UserAddForm.apply)(UserAddForm.unapply)
    )

    userForm.bindFromRequest().fold(
      formWithErrors => {
        Logger.info(formWithErrors.errors.toString)
        Redirect(routes.HomeController.displayFullDetailedUser(idOfUser)).flashing("failure" -> "someFailure")
      },
      userData => {
        s.userDataStore.updateUser(idOfUser,User(userData.mail,
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

        if(request.session.get("id").get == idOfUser){
          if(userData.picture.isDefined){
            Redirect(routes.HomeController.displayUser(0,10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName,"lastName" -> userData.lastName,"picture" -> userData.picture.get,"timeZone" -> userData.timeZone,"status" -> userData.status.get,"id" -> idOfUser)
          }else{
            Redirect(routes.HomeController.displayUser(0,10)).flashing("update" -> "someUpdate").withSession("firstName" -> userData.firstName,"lastName" -> userData.lastName,"timeZone" -> userData.timeZone,"status" -> userData.status.get,"id" -> idOfUser)
          }
        }else{
          Redirect(routes.HomeController.displayUser(0,10)).flashing("update" -> "someUpdate")
        }
      })
  }

  def getLocalTimeZoneId(request : Request[AnyContent]) : String = {
    val acceptLanguage = request.headers.get("Accept-Language")
    val stringLocale = acceptLanguage.get.substring(0,acceptLanguage.get.indexOf(","))
    val locale : Locale = new Locale(stringLocale.substring(0,stringLocale.indexOf("-")),stringLocale.substring(stringLocale.indexOf("-")+1,stringLocale.length))
    val calendar : Calendar = Calendar.getInstance(locale)
    val clientTimeZone : TimeZone = calendar.getTimeZone
    val localTimeZoneId : String = clientTimeZone.getID
    localTimeZoneId
  }
}
