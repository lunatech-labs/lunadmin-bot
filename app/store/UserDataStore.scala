package store

import javax.inject.Inject
import models.{SinglePersonTask, TaskDescription, User, UserDescription}
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, _}

class UserDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext){
  private val userCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("user"))
  import reactivemongo.play.json.ImplicitBSONHandlers._


  def findUserById(id: String): Future[Option[User]] = {
    val query = BSONDocument("_id" -> id)
    val cursor : Future[Cursor[User]] = userCollection.map(f => f.find(query).cursor[User]())
    val futurUser : Future[Option[User]] = cursor.flatMap(_.headOption)
    futurUser
  }

  def findAllUserDescription(page : Int, pageSize : Int) : Future[List[UserDescription]] = {
    val query = BSONDocument()
    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findEveryUserDescription() : Future[List[UserDescription]] = {
    val query = BSONDocument()
    userCollection.flatMap(
      _.find(query)
        .cursor[UserDescription]()
        .collect[List](-1, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByName(nameToLookFor : String, page : Int, pageSize : Int) : Future[List[UserDescription]] = {
    val query = Json.obj(
      "$or" -> Json.arr(
       Json.obj("firstName" -> Json.obj("$regex" -> nameToLookFor)),
       Json.obj("lastName" -> Json.obj("$regex" -> nameToLookFor))
      )
    )

    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByMail(mailToLookFor : String, page : Int, pageSize : Int) : Future[List[UserDescription]] = {
    val query = Json.obj("mail" -> Json.obj("$regex" -> mailToLookFor))

    userCollection.flatMap(
      _.find(query)
        .options(QueryOpts(skipN = page * pageSize, pageSize))
        .cursor[UserDescription]()
        .collect[List](pageSize, Cursor.FailOnError[List[UserDescription]]())
    )
  }

  def findUserDescriptionByUserGroup(userGroupName : String) : Future[List[UserDescription]] = {
    val query = Json.obj("groupName" -> userGroupName)

    userCollection.flatMap(
      _.find(query)
        .cursor[UserDescription]()
        .collect[List](-1, Cursor.FailOnError[List[UserDescription]]())
    )
  }


  def addUser(user : User) = {
    val javaDoc = User.fmt.writes(user)
    userCollection.map(c => c.insert(javaDoc))
  }

  def updateUser(id: String ,user: User) = {
    val selectUpdate = Json.obj("_id" -> id)
    val updateQuery = Json.obj("mail" -> user.mail,
                              "password" -> BSONObjectID.generate().stringify,
                              "lastName" -> user.lastName,
                              "firstName" -> user.firstName,
                              "birthDate" -> user.birthDate,
                              "groupId" -> user.groupName,
                              "status" -> user.status,
                              "hireDate" -> user.hireDate,
                              "phone" -> user.phone,
                              "cloudLinks" -> user.cloudLinks
              )
    userCollection.map(c => c.update(selectUpdate,updateQuery))
  }

  def deleteUser(id : String) = {
    val removeQuery = Json.obj("_id" -> id)
    userCollection.map(c => c.remove(removeQuery))
  }

  def removeUserGroupFromUser(userGroupName : String) = {
    val selectUpdate = Json.obj()
    val updateQuery = Json.obj("$pull" -> Json.obj("groupName" -> userGroupName))
    userCollection.map(c => c.update(selectUpdate,updateQuery,multi = true))
  }

}
