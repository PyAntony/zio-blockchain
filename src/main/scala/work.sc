
import zio._
import zio.console._
import zio.stm._
import zio.duration._

import scala.util.Try
import scala.util.matching.Regex

List(1 , 2 , 3).flatMap(a => List(a))