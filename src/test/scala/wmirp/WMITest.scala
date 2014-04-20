package fr.janalyse.wmirp

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import java.lang.management.ManagementFactory
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent._
import scala.concurrent.duration._

import com.jacob.activeX.ActiveXComponent
import com.jacob.com.Dispatch
import com.jacob.com.EnumVariant
import com.jacob.com.Variant

class WMITest extends FunSuite with ShouldMatchers with BeforeAndAfterAll {
  implicit var wmi: WMI = _

  override def beforeAll() {
    wmi = new WMI {}
  }

  override def afterAll() {
    wmi.close()
  }

  test("Browsing test 1") {
    val all = wmi.getClasses()
    all.size should be > (200)
    info(s"found ${all.size} com classes")
  }
  
  test("Browsing test 2") {
    val perfs = wmi.getPerfClasses()
    perfs.size should be > (20)
    info(s"found ${perfs.size} performances com classes")
    val processor = perfs.find(_.name contains "PerfOS_Processor")
    processor should be ('defined)
  }

  test("Get class instances") {
    val processors = wmi.getInstances("Win32_PerfFormattedData_PerfOS_Processor")
    processors.size should be > (0)
    processors.map(_.name.id.get) should contain ("_Total")
  }

  test("Get instance values first CPU") {
    val cpu = wmi.getInstance("Win32_PerfFormattedData_PerfOS_Processor", "0")
    cpu should be ('defined)
    val entries=cpu.get.entries
    val idle = entries.get("PercentIdleTime").map(_.getString.toInt)
    val user = entries.get("PercentUserTime").map(_.getString.toInt)
    info(s"CPU Usage : idle=$idle user=$user")
    idle.get should be >(0)
  }

  test("Get instance values Total 1") {
    val cpu = wmi.getInstance("""Win32_PerfFormattedData_PerfOS_Processor""", "_Total")
    cpu should be ('defined)
    val entries=cpu.get.entries
    val idle = entries.get("PercentIdleTime").map(_.getString.toInt)
    val user = entries.get("PercentUserTime").map(_.getString.toInt)
    info(s"CPU Usage : idle=$idle user=$user")
    idle.get should be >(0)
  }
  
  test("Get instance values Total 2 full path") {
    val cpu = wmi.getInstance("""\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_Processor""", "_Total")
    cpu should be ('defined)
    val entries=cpu.get.entries
    val idle = entries.get("PercentIdleTime").map(_.getString.toInt)
    val user = entries.get("PercentUserTime").map(_.getString.toInt)
    info(s"CPU Usage : idle=$idle user=$user")
    idle.get should be >(0)
  }

  test("Get standalone instance") {
    val sys = wmi.getInstance("Win32_PerfFormattedData_PerfOS_System")
    sys should be ('defined)
    val entries = sys.get.entries
    val processes = entries.get("Processes").map(_.getString.toInt)
    val threads = entries.get("Threads").map(_.getString.toInt)
    info(s"System : Processes=$processes threads=$threads")
    processes.get should be >(0)
    threads.get should be > (0)
  }
  
  test("Get unknown instance") {
    val cpu = wmi.getInstance("Win32_PerfFormattedData_PerfOS_Processor", "trucmuche")
    cpu should be ('empty)
  }

  
  ignore("Performance walk - search metrics") {
    val numRE = """(\d+(?:[.,]\d+)?)""".r
    val found = for {
      perfclass <- wmi.getClasses
      instance <- perfclass.instances
      (key,numRE(value)) <- instance.entries
      clname = perfclass.name
      iname = instance.name.id.getOrElse("default")
    } yield {
      s"$clname/$iname.$key=$value"
    }
    found.size should be >(50)
    found.filter(_ contains "PercentProcessor").foreach(info(_))
  }
  
}
