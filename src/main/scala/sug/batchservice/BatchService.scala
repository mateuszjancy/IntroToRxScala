package sug.batchservice

import java.io.File

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.monitor.{FileAlterationListenerAdaptor, FileAlterationMonitor, FileAlterationObserver}

object BatchService extends App with StrictLogging {

  def processor(person: Person): Person = person match {
    case Person(fn, ln) => Person(fn.toUpperCase,ln.toUpperCase)
  }

  case class Person(firstName: String, lastName: String) {
    override def toString: String = s"firstName: $firstName lastName: $lastName"
  }

  def monitor(directory: String, interval: Long = 1000) = {
    val m = new FileAlterationMonitor(interval)
    val o = new FileAlterationObserver(directory)
    m.addObserver(o)
    m.start()

    val l = new FileAlterationListenerAdaptor {
      override def onFileChange(file: File): Unit = {
        logger.info(s"File changed ${file.getName}.")
        //TODO
      }
    }
    o.addListener(l)
  }

  def reader(absolutePath: String): List[List[String]] = {
    val reader = CSVReader.open(new File(absolutePath))
    val rows = reader.all()
    logger.info(s"Read ${rows.size} row.")
    rows.map(_.map(_.trim))
  }

  def converter(raw: List[String]): Either[List[String], Person] = raw match {
    case firstName :: lastName :: Nil if lastName.nonEmpty && firstName.nonEmpty =>
      Right(Person(firstName, lastName))
    case row => Left(row)
  }

  def errorWriter(err: List[String]): Unit =
    logger.info(s"Err: $err")


  def okWriter(person: Person): Unit =
    logger.info(s"Writer: $person")
}

