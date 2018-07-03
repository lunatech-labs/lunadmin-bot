package controllers

import java.time.{ZoneId, ZonedDateTime}

import reactivemongo.play.json.ImplicitBSONHandlers._
import com.google.inject.Singleton
import controllers.AssetsFinder
import javax.inject.Inject
import models._
import play.Logger
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.libs.json._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json._
import play.modules.reactivemongo._
import play.modules.reactivemongo.json._
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json.collection.JSONCollection
import store.{TaskDataStore, UserDataStore}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Starter @Inject()(userDataStore : UserDataStore, taskDataStore: TaskDataStore,configuration : Configuration, cc: ControllerComponents,val reactiveMongoApi: ReactiveMongoApi)(implicit assetsFinder: AssetsFinder, ec: ExecutionContext)
  extends AbstractController(cc) with MongoController with ReactiveMongoComponents {

  private val taskCollection = reactiveMongoApi.database.map(_.collection[JSONCollection]("taskCategory"))
  private val taskCollectionTest = reactiveMongoApi.database.map(_.collection[JSONCollection]("task"))

  private val ZONEID = configuration.underlying.getString("ZONEID")

  val controllerComponent = cc
  var listOfTask = List[Task]()
  var listOfUsers = List[User]()
  var listOfGroup = List[UserGroup]()
  var listOfPaper = List[AdministrativPaper]()
  var listOfTaskCategory = List[TaskCategory]()

  main()

  def main(): Unit ={
//    val query = BSONDocument(/*"group" -> BSONDocument("$exists" -> true)*/)
//
//    import reactivemongo.play.json.ImplicitBSONHandlers._
//    val cursor : Future[Cursor[TaskCategory]] = taskCollection.map(f => f.find(query).cursor[TaskCategory]())
//    val futurList : Future[List[TaskCategory]] = cursor.flatMap(_.collect[List](-1,Cursor.FailOnError[List[TaskCategory]]()))
//
//    futurList.map(x => Logger.info(x.toString()))


//    taskDataStore.findAllTaskDescription(0,10).map { listForTest =>
//      Logger.info(listForTest.toString())
//    }


  }
}



