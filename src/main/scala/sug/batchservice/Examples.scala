package sug.batchservice

import rx.lang.scala.Observer


object Examples extends App {

  import java.util.concurrent.{CopyOnWriteArrayList, CountDownLatch}
  import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}
  import java.util.concurrent.atomic.AtomicInteger

  import rx.lang.scala.schedulers.ComputationScheduler
  import rx.lang.scala.{Observable, Subject, Subscription}

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  import scala.concurrent.duration._
  import scala.util.Random

  def delay(d: Long)(block: => Unit) = {
    val e = Executors
      .newScheduledThreadPool(1)

    e.schedule(
      new Runnable {
        override def run(): Unit = block
      },
      d, TimeUnit.SECONDS
    )
    e.shutdown()
  }

  def log[T](msg: T): Unit = println(s"${Thread.currentThread().getName}: $msg")

  def error(ex: Throwable): Unit = log(ex.getMessage)

  def next[T](name: String)(x: T): Unit = log(s"$name -> $x")

  def complete(): Unit = log("Complete")

  def exceptionsExample = {
    val o = Observable.just(1, 2, 3)
    val e = Observable.error(new Exception("Boom!!"))
    val j = Observable.just(4, 5, 6)
    val r = o ++ e ++ j

    r.subscribe(next("A"), error)
  }

  exceptionsExample

  def subscriptionExample = {
    val o = Observable.create[Int] { o =>
      o.onNext(1);
      o.onNext(2)
      o.onCompleted()
      Subscription {
        println("exit")
      }
    }

    o.subscribe(next("A"), error, complete)
    o.subscribe(next("B"), error, complete)
  }

  subscriptionExample

  /** */
  object Source {
    def apply() = new Source
  }

  class Source {
    import scala.collection.JavaConversions._
    val e = Executors.newScheduledThreadPool(1)
    val acc = new AtomicInteger(0)
    var listeners = new CopyOnWriteArrayList[Observer[Int]]
    val runnable = new Runnable {
      override def run(): Unit = {
        val next = acc.incrementAndGet(); println(s"Next: $next")
        listeners.foreach(_.onNext(next))
      }
    }
    def register(o: Observer[Int]) = listeners.add(o)
    def unregister(o: Observer[Int]) = {
      listeners.remove(o); o.onCompleted()
    }
    def start() = e.scheduleWithFixedDelay(runnable, 1, 1, TimeUnit.SECONDS)
    def stop() = e.shutdownNow()
  }

  def coldExample = {
    val obs = Observable.create[Int] { o =>
      log("Subscribe")
      val src = Source()
      src.start()
      src.register(o)
      Subscription {
        log("Unsubscribe")
        src.unregister(o)
        src.stop()
      }
    }
    delay(2) {
      val a = obs.subscribe(next("A"), error, complete)
      delay(5) { a.unsubscribe() }
    }
    delay(4) {
      val b = obs.subscribe(next("B"), error, complete)
      delay(5) { b.unsubscribe() }
    }
  }

  //coldExample

  def hotExample = {
    val src = Source()
    src.start()
    val obs = Observable.create[Int] { o =>
      src.register(o)
      Subscription {
        log("Unsubscribe")
        src.unregister(o)
      }
    }

    delay(2) {
      val a = obs.subscribe(next("A"), error, complete)
      delay(5) {
        a.unsubscribe()
      }
    }
    delay(4) {
      val b = obs.subscribe(next("B"), error, complete)
      delay(5) {
        b.unsubscribe()
      }
    }

    delay(15) {
      log("Source stop")
      src.stop()
    }
  }

  //hotExample

  def composingExample = {
    Observable
      .just(1, 2, 3, 4)
      .filter(_ % 2 == 0)
      .map(even => s"[$even]")
      .take(2)
      .subscribe(next("B"), error, complete)
  }

  //composingExample

  def nested: Observable[Observable[(Long, Int)]] =
    Observable.interval(100.millisecond).map { i =>
      def f = Future {
        val delay = Random.nextInt(1000)
        Thread.sleep(delay);
        (i, delay)
      }
      val f1 = Observable.from(f)
      val f2 = Observable.from(f)

      f1 ++ f2
    }

  nested

  def concatExample = {
    val cdl = new CountDownLatch(10)
    val s = nested.concat.subscribe { el =>
      cdl.countDown()
      log(s"Id: ${el._1}, Delay: ${el._2}")
    }
    cdl.await()
    s.unsubscribe()
  }

  concatExample


  def flattenExample = {
    val cdl = new CountDownLatch(10)
    nested.flatten.subscribe { el =>
      cdl.countDown()
      log(s"Id: ${el._1}, Delay: ${el._2}")
    }
    cdl.await()
  }

  //flattenExample

  def schedulerExample = {
    val l = new CountDownLatch(1)
    val scheduler = ComputationScheduler()
    val numbers = Observable.just(1, 2, 3)

    numbers.subscribe(x => log(s"Subscribe: $x"))
    numbers.subscribeOn(scheduler).subscribe { x =>
      log(s"SubscribeOn: $x");l.countDown()
    }
    l.await()
  }

  //schedulerExample

  def subjectExample = {
    val o = Subject[Int]()
    o.subscribe(x => log(s"Subject:$x"))
    List(1, 2, 3).foreach(it => o.onNext(it))
    Observable.just(4, 5, 6).subscribe(o)
  }

  //subjectExample

}
