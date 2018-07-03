package store

import javax.inject.Inject
import models.{SinglePersonTask, TaskDescription, User, UserDescription}
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

class UserDataStore @Inject()(val reactiveMongoApi: ReactiveMongoApi ,conf :Configuration)(implicit ec: ExecutionContext){
  private val userCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("user"))
  import reactivemongo.play.json.ImplicitBSONHandlers._


  def findById(id: String): Future[Option[User]] = {
    val query = BSONDocument("_id" -> id)
    val cursor : Future[Cursor[User]] = userCollection.map(f => f.find(query).cursor[User]())
    val futurUser : Future[Option[User]] = cursor.flatMap(_.headOption)
    futurUser
  }

  def findByFirstName(firstName : String) = {
    val query = BSONDocument("firstName" -> BSONDocument("$regex" -> firstName))
    val cursor : Future[Cursor[User]] = userCollection.map(f => f.find(query).cursor[User]())
    val futurUser : Future[List[User]] = cursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]()))
    futurUser
  }

  def findByLastName(lastName : String) = {
    val query = BSONDocument("lastName" -> BSONDocument("$regex" -> lastName))
    val cursor : Future[Cursor[User]] = userCollection.map(f => f.find(query).cursor[User]())
    val futurUser : Future[List[User]] = cursor.flatMap(_.collect[List](-1,Cursor.FailOnError[List[User]]()))
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
                              "groupId" -> user.groupId,
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

}
