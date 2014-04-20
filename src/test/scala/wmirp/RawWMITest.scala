package  fr.janalyse.wmirp

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

class RawWMITest extends FunSuite with ShouldMatchers with BeforeAndAfterAll {
  var wmiconnect: ActiveXComponent = _ //  SWbemServices

  override def beforeAll() {
    System.setProperty("java.library.path", "src/main/resources/")
    val locator = new ActiveXComponent("WbemScripting.SWbemLocator") //  SWbemLocator
    val swbemServices = locator.invoke("ConnectServer").toDispatch()
    wmiconnect = new ActiveXComponent(swbemServices)
  }

  override def afterAll() {
    // wmiconnect.safeRelease()
  }

  test("query test") {
  }

  
  test("dump object text (properties and values)") {
    val query = "select * from Win32_PerfFormattedData_PerfOS_System"
    val vCollection = wmiconnect.invoke("ExecQuery", new Variant(query)) // SWbemObjectSet 
    val enumVariant = new EnumVariant(vCollection.toDispatch())

    val system = enumVariant.nextElement().toDispatch()
    info(Dispatch.call(system, "GetObjectText_").toString.take(200)+"...")
    val processes = Dispatch.get(system, "Processes")
    info(s"Processes count $processes")
    processes.toInt should be > (10)
    
  }
  
  test("list object properties") {
    val query = "select * from Win32_PerfFormattedData_PerfOS_System"
    val vCollection = wmiconnect.invoke("ExecQuery", new Variant(query)) // SWbemObjectSet 
    val enumVariant = new EnumVariant(vCollection.toDispatch())
    val system = enumVariant.nextElement().toDispatch()

    val props = Dispatch.call(system, "Properties_").toDispatch
    val enumProps = new EnumVariant(props)
    while(enumProps.hasMoreElements()) {
      val item = enumProps.nextElement.toDispatch()
      val key = Dispatch.get(item, "Name")
      val value = Dispatch.get(item, "Value")
      if (key.getString() contains "File")info(s"$key = $value")
    }    
  }
  
  test("instancesof test") {
    val obs = wmiconnect.invoke(
      "InstancesOf",
      new Variant("Win32_PerfFormattedData_PerfOS_Processor")) // => SWbemObjectSet

    val enumVariant = new EnumVariant(obs.toDispatch())
    while (enumVariant.hasMoreElements()) {
      val item = enumVariant.nextElement().toDispatch()
      val name = Dispatch.call(item, "Name").getString()
      val idle = Dispatch.get(item, "PercentIdleTime")
      info(s"Found instance name : $name - PercentIdleTime=$idle")
    }
  }

  test("get test") {
    val tmp = wmiconnect.invoke("Get",new Variant("""\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_Processor.Name="_Total"""")) // => SWbemObject
    val ob = tmp.toDispatch()
    val name = Dispatch.call(ob, "Name").getString()
    val idle = Dispatch.call(ob, "PercentIdleTime").getString()
    val user = Dispatch.call(ob, "PercentProcessorTime").getString()
    info(s"$name cpu idle=$idle user=$user")
    idle.toInt should be >(0)
  }

  test("get test 2") {
    val ob = wmiconnect.invoke(
      "Get",
      """\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_Processor.Name="_Total"""").toDispatch() // => SWbemObject
    val name = Dispatch.call(ob, "Name").getString()
    val idle = Dispatch.get(ob, "PercentIdleTime")
    val user = Dispatch.get(ob, "PercentProcessorTime")
    val int = Dispatch.get(ob, "InterruptsPersec")
    info(idle.toJavaObject().getClass().getName())
    info(s"$name cpu idle=$idle user=$user ints=$int")
    idle.toInt should be >(0)
  }

  test("get test 3") {
    val ob = wmiconnect.invoke("Get","""\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_Processor.Name="_Total"""").toDispatch() // => SWbemObject
    val proc = new ActiveXComponent(ob)

    val name = proc.getProperty("Name") //Dispatch.call(ob, "Name").getString()
    val idle = proc.getProperty("PercentIdleTime").toInt
    val user = proc.getProperty("PercentProcessorTime").toInt
    info(s"$name cpu idle=$idle user=$user")
    idle.toInt should be >(0)
  }

  test("get singleton") {
    val tmp = wmiconnect.invoke("Get",new Variant(
        """\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_System=@""")) // => SWbemObject
    val ob = tmp.toDispatch()
    val processes = Dispatch.call(ob, "Processes").toString()
    info(s"system singleton processes=$processes")
    processes.toInt should be >(0)
  }
  
  test("list classes") {
    val classes = wmiconnect.invoke("SubclassesOf")
    val enumVariant = new EnumVariant(classes.toDispatch())
    while(enumVariant.hasMoreElements()) {
      val item = enumVariant.nextElement().toDispatch()
      val pathd = Dispatch.call(item, "Path_").toDispatch()
      val path = Dispatch.get(pathd, "Path").toString
      if (path.contains("PerfFormattedData_PerfOS")) info(path)
    }
  }

}
