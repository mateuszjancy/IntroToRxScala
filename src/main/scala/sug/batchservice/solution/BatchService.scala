package sug.batchservice.solution

import java.io.File

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.monitor.{FileAlterationListenerAdaptor, FileAlterationMonitor, FileAlterationObserver}
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.duration._

object BatchService extends App with StrictLogging {

  def processor(person: Person): Person = {
    val fn = person.firstName.toUpperCase
    val ln = person.lastName.toUpperCase
    val transformedPerson = Person(fn, ln)
    logger.info(s"from $person to $transformedPerson")
    transformedPerson
  }

  case class Person(firstName: String, lastName: String) {
    override def toString: String = s"firstName: $firstName lastName: $lastName"
  }

  def monitor(directory: String, interval: Long = 1000) = {
    val fileMonitor = new FileAlterationMonitor(interval)
    val fileObserver = new FileAlterationObserver(directory)
    fileMonitor.addObserver(fileObserver)
    fileMonitor.start()

    Observable.create[String] { o =>
      val fileListener = new FileAlterationListenerAdaptor {
        override def onFileChange(file: File): Unit = {
          logger.info(s"File changed ${file.getName}.")
          o.onNext(file.getAbsolutePath)
        }
      }
      fileObserver.addListener(fileListener)

      Subscription {
        fileObserver.removeListener(fileListener)
      }
    }
  }

  def reader(absolutePath: String): Observable[List[String]] = {
    val reader = CSVReader.open(new File(absolutePath))
    val l = reader.all()
    logger.info(s"Read ${l.size} row.")
    Observable.from(l).map(_.map(_.trim))
  }

  def converter(raw: List[String]): Either[List[String], Person] = raw match {
    case firstName :: lastName :: Nil if lastName.nonEmpty && firstName.nonEmpty =>
      Right(Person(firstName, lastName))
    case row => Left(row)
  }

  def errorWriter(err: List[String]): Unit = {
    logger.info(s"Err: $err")
  }

  def okWriter(person: Person): Unit = {
    logger.info(s"Writer: $person")
  }


  val stream = monitor("/home/mateusz/test/")
    .filter(_.endsWith(".csv"))
    .flatMap(reader)
    .map(converter)

  stream
    .tumbling(10.seconds, 4).flatten
    .subscribe(_.fold(errorWriter, okWriter))
}

