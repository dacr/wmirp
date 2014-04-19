package wmirp

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
  var wmiconnect: ActiveXComponent = _     //  SWbemServices

  override def beforeAll() {
    System.setProperty("java.library.path", "src/main/resources/")
    val locator = new ActiveXComponent("WbemScripting.SWbemLocator") //  SWbemLocator
    val swbemServices = locator.invoke("ConnectServer").toDispatch()
    wmiconnect = new ActiveXComponent(swbemServices) 
  }

  override def afterAll() {
    wmiconnect.safeRelease()
  }

  test("query test") {
    val query = "select * from Win32_PerfFormattedData_PerfOS_Processor"
    val vCollection = wmiconnect.invoke("ExecQuery", new Variant(query)) // SWbemObjectSet 
    val enumVariant = new EnumVariant(vCollection.toDispatch())

    while (enumVariant.hasMoreElements()) {
      val item = enumVariant.nextElement().toDispatch()
      val name = Dispatch.call(item, "Name").getString()
      info(s"Found instance name : $name")

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
      info(s"Found instance name : $name ")
    }
  }

  test("get test") {
    val ob = wmiconnect.invoke(
      "Get",
      """\\.\root\cimv2:Win32_PerfFormattedData_PerfOS_Processor.Name="__Total""""
      ).toDispatch() // => SWbemObject
    val name = Dispatch.call(ob, "Name").getString()
    val idle = Dispatch.get(ob, "PercentIdleTime").getString()
    val user = Dispatch.get(ob, "PercentProcessorTime").getString()
    info(s"$name cpu idle=$idle user=$user")
    idle should not equal(user)
  }
  
  test("list classes") {
    
  }
  
}
