package tasks

import java.io

import akka.actor.ActorSystem
import play.api.ApplicationLoader.Context
import play.api.Logger
import datasource.SearchDataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import java.io.FileNotFoundException

import com.google.common.base.Charsets
import fileutils.FileUtils._
import models.CodeSourceModel
import com.google.common.hash.Hashing

class RepositoryScannerTask(actorSystem: ActorSystem, repo: SearchDataSource, implicit val ec: ExecutionContext) {

  def scan(directory: String, repository: String) = {


    def processFile(directory: String, path: Path, repository: String) = {
      Logger.info("Indexing file at path: %s for repository: %s".format(path.toString, repository))
      val content = readFile(path)
      val filename = path.normalize.toString.stripPrefix(directory).stripPrefix("/")
      val id = Hashing.sha256.hashString(repository + "/" + path.toString, Charsets.UTF_8).toString
      content match {
        case Some(s) =>
          repo.indexCode(new CodeSourceModel(id, repository, filename, s)) match {
            case Left(failure) => Logger.warn(failure.toString)
            case Right(_) =>
          }
        case None =>
      }
    }

    Logger.info("Scanning directory: %s for repository: %s".format(directory, repository))
    listFiles(directory) match {
      case Some(files) => files.filter(!_.toString.contains(".git/"))
        .forEach((path) => Future {
          processFile(directory, path, repository)
        })
      case _ =>
    }

  }

  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 5.minutes) {
    repo.getAvailableRepositories match {
      case Left(err) => Logger.error("Error retrieving list of repositories: %s".format(err.toString))
      case Right(res) => res.foreach((r) => scan(r.path, r.repository))
    }
  }

}