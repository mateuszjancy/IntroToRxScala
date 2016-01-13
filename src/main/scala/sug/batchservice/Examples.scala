package sug.batchservice



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

  def delay(d: Long)(block: => Unit) =
    Executors
      .newScheduledThreadPool(1)
      .schedule(
        new Runnable {
          override def run(): Unit = block
        },
        d, TimeUnit.SECONDS
      )

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
    var listeners = new CopyOnWriteArrayList[(Int => Unit)]
    val runnable = new Runnable {
      override def run(): Unit = {
        val next = acc.incrementAndGet()
        log(s"Next: $next")
        listeners.foreach(onNext => onNext.apply(next))
      }
    }

    var block: Seq[ScheduledFuture[_]] = _

    def register(f: Int => Unit) = listeners.add(f)

    def unregister(f: Int => Unit) = listeners.remove(f)

    def start(): Unit =
      block = (0 until 10).map(e.schedule(runnable, _, TimeUnit.SECONDS))

    def stop = block.foreach(x => if(!x.isDone) x.cancel(true))

  }

  def coldExample = {
    val obs = Observable.create[Int] { o =>
      log("Subscribe")
      val src = Source()
      src.start()
      src.register(o.onNext)
      Subscription {
        src.unregister(o.onNext)
        src.stop
      }
    }
    delay(2) {
      val a = obs.subscribe(next("A") _)
        a.unsubscribe()

    }
    delay(5) {
      val b = obs.subscribe(next("B") _)

        b.unsubscribe()

    }
  }

  //coldExample

  def hotExample = {
    val src = Source();
    src.start()
    val obs = Observable.create[Int] { o =>
      src.register(o.onNext)
      Subscription { src.unregister(o.onNext) }
    }
    delay(3) { obs.subscribe(next("A") _)}
    delay(5) { obs.subscribe(next("B") _)}
  }

  //hotExample

  def composingExample = {
    val o = Observable.interval(1.second).takeUntil(_ == 8)
    o.subscribe { i =>
      //l.countDown(); next("B")(i)
    }

    delay(2) {
      o.filter(_ % 2 == 0)
        .map(even => s"[$even]")
        .take(2)
    //    .subscribeOn(ImmediateScheduler())
        .subscribe(next("B"), error, complete)
    }

    //l.await()
  }

  //composingExample

  def nested: Observable[Observable[(Long, Int)]] =
    Observable.interval(100.millisecond).map{ i =>
      def f = Future{
        val delay = Random.nextInt(1000)
        Thread.sleep(delay); (i, delay)
      }
      val f1 = Observable.from(f)
      val f2 = Observable.from(f)

      f1 ++ f2
    }

  nested

  def concatExample = {
    val cdl = new CountDownLatch(10)
    nested.concat.subscribe { el =>
      cdl.countDown()
      log(s"Id: ${el._1}, Delay: ${el._2}")
    }
    cdl.await()
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
  flattenExample

  def schedulerExample = {
    val cdl = new CountDownLatch(2)
    val scheduler = ComputationScheduler()
    val numbers = Observable.just(1, 2, 3)

    numbers.subscribe(x => log(s"$x"))
    numbers.observeOn(scheduler).subscribe { x =>
      log(s"Msg: $x"); cdl.countDown()
    }
    cdl.await()
  }

  schedulerExample

  def subjectExample = {
    val o = Subject[Int]()
    o.subscribe(x => log(s"x:$x"))

    List(1, 2, 3).foreach(it => o.onNext(it))

    Observable.just(4, 5, 6).subscribe(o)
  }

  subjectExample

}
